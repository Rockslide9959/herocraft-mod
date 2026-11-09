package com.projecthero.mod.andy;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.andy.data.AndyState;
import com.projecthero.mod.andy.entity.AndyPartEntity;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.power.PowerToggles;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * The single server-side API for Andy (Undead Unluck), the v0.17.3 Hero-Tier power. Nothing else writes
 * {@link AndyState}. Moves in {@link AndyAbilities} and {@link AndyFlight}, damage rules in {@link AndyDamage}, numbers in
 * {@link AndyConfig}.
 *
 * <h2>A body made of pixels</h2>
 * Six parts (head, body, both arms, both legs), each with its own HP. Every hit is placed on a part by where it lands
 * ({@link AndyParts}); a part at 0 HP breaks ({@link #breakPart}) -- every client bursts it into one cube per skin texel --
 * and is gone until it regrows: after {@link AndyConfig#REGROW_DELAY} it grows back from the joint outward over
 * {@link AndyConfig#REGROW_TICKS} (the bone pushes out, the muscle builds round it, the skin closes over), costing hunger. A
 * tap of V cycles the speed (Normal / Fast / Burst; starving, half Normal). With only the head left everything grows back in a set order
 * ({@link #planHeadOnly}) and he gets about however that lets him ({@link AndyStages}).
 *
 * <h2>Vanilla health</h2>
 * Vanilla health stays the player's real life pool and takes every hit as usual. The part system runs beside it and only
 * decides what breaks. Health reaching zero is the Undead passive ({@link #shatter}): the body bursts apart except the head,
 * health is refilled, and the rest grows back from the head by the staged regrowth. Only {@code /kill}, the void and other
 * {@code BYPASSES_INVULNERABILITY} damage really kill.
 */
public final class Andy {
	public static final String KEY = "andy";

	private static final ResourceLocation MELEE_ID = PowerToggles.id("andy_bloody_arts");
	private static final ResourceLocation REFORM_ID = PowerToggles.id("andy_reform");
	private static final ResourceLocation REFORM_JUMP_ID = PowerToggles.id("andy_reform_jump");
	/** v0.17.3: what the body is left to get about with (rolling head ... walking), by stage. */
	private static final ResourceLocation STAGE_ID = PowerToggles.id("andy_stage");
	private static final ResourceLocation STAGE_JUMP_ID = PowerToggles.id("andy_stage_jump");

	public static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.72f, 0.03f, 0.06f), 1.1f);
	public static final DustParticleOptions BLOOD_BIG = new DustParticleOptions(new Vector3f(0.8f, 0.05f, 0.08f), 2.4f);
	public static final DustParticleOptions BLOOD_DARK = new DustParticleOptions(new Vector3f(0.45f, 0.0f, 0.03f), 1.6f);

	private static final Map<UUID, Long> LAST_MESSAGE = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();
	/** Food points a regrowth still owes the hunger bar, below one whole point (taken with the next part). */
	private static final Map<UUID, Float> FOOD_OWED = new ConcurrentHashMap<>();
	/** The stage last reconciled (the speed and jump modifiers follow it). */
	private static final Map<UUID, Integer> LAST_STAGE = new ConcurrentHashMap<>();

	private Andy() {
	}

	public static void clearSessionState() {
		LAST_MESSAGE.clear();
		LAST_TICK.clear();
		AndyAutoEat.clearSessionState();
		FOOD_OWED.clear();
		LAST_STAGE.clear();
		AndyAbilities.clearSessionState();
		AndyAbilityManager.clearSessionState();
		AndyDamage.clearSessionState();
		AndyFlight.clearSessionState();
	}

	static void forget(UUID id) {
		LAST_MESSAGE.remove(id);
		LAST_TICK.remove(id);
		AndyAutoEat.forget(id);
		FOOD_OWED.remove(id);
		LAST_STAGE.remove(id);
		AndyAbilities.forget(id);
		AndyAbilityManager.forget(id);
		AndyDamage.forget(id);
		AndyFlight.forget(id);
	}

	// ---------------------------------------------------------------- state

	public static AndyState state(ServerPlayer player) {
		return player.getAttachedOrCreate(ModAttachments.ANDY_STATE);
	}

	static void save(ServerPlayer player, AndyState state) {
		player.setAttached(ModAttachments.ANDY_STATE, state);
	}

	public static AndyState peek(Player player) {
		return player.getAttachedOrElse(ModAttachments.ANDY_STATE, null);
	}

	public static boolean hasPower(Player player) {
		AndyState s = peek(player);
		return s != null && s.hasPower;
	}

	/** The part is gone or still re-forming (client-safe). */
	public static boolean missing(Player player, int part) {
		AndyState s = peek(player);
		return s != null && s.hasPower && s.broken(part);
	}

	/** The arm that holds the main hand is gone. */
	public static boolean mainArmMissing(Player player) {
		return missing(player, mainArm(player));
	}

	public static boolean offArmMissing(Player player) {
		return missing(player, otherArm(mainArm(player)));
	}

	public static int mainArm(Player player) {
		return player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT ? AndyState.RIGHT_ARM : AndyState.LEFT_ARM;
	}

	public static int otherArm(int arm) {
		return arm == AndyState.RIGHT_ARM ? AndyState.LEFT_ARM : AndyState.RIGHT_ARM;
	}

	public static boolean bothLegsMissing(Player player) {
		return missing(player, AndyState.RIGHT_LEG) && missing(player, AndyState.LEFT_LEG);
	}

	/** The leg on the main hand's side. */
	public static int mainLeg(Player player) {
		return mainArm(player) == AndyState.RIGHT_ARM ? AndyState.RIGHT_LEG : AndyState.LEFT_LEG;
	}

	/**
	 * The arm a finger shot or the stretch punch comes from (client-safe): the main arm, or the other one when the main is
	 * gone, still growing or used up; -1 with neither.
	 */
	public static int shootingArm(Player player) {
		AndyState s = peek(player);
		if (s == null || !s.hasPower) {
			return -1;
		}
		float now = player.level().getGameTime();
		int main = mainArm(player);
		int other = otherArm(main);
		boolean mainOk = s.armReady(main, now), otherOk = s.armReady(other, now);
		if (mainOk && otherOk) {
			// v0.17.3 (tester: "Alternation between left and right hands instead of just one"): both there, the one not used last
			return s.flag(AndyState.FLAG_NEXT_OFF) ? other : main; // the main arm first
		}
		return mainOk ? main : otherOk ? other : -1;
	}

	/** What he gets about with right now ({@link AndyStages}), client-safe. */
	public static int stage(Player player) {
		AndyState s = peek(player);
		return s == null || !s.hasPower ? AndyStages.NORMAL : AndyStages.stage(s, player.level().getGameTime());
	}

	public static boolean victor(Player player) {
		AndyState s = peek(player);
		return AndyConfig.VICTOR_ENABLED && s != null && s.hasPower && s.victorUntil > player.level().getGameTime();
	}

	public static boolean reforming(Player player) {
		AndyState s = peek(player);
		return s != null && s.hasPower && s.reformUntil > player.level().getGameTime();
	}

	public static int regenSpeed(Player player) {
		AndyState s = peek(player);
		return s == null ? AndyConfig.SPEED_NORMAL : s.regenSpeed;
	}

	public static int cooldownRemaining(Player player, String abilityId) {
		AndyState s = peek(player);
		if (s == null) {
			return 0;
		}
		Long ready = s.abilityReadyAt.get(abilityId);
		return ready == null ? 0 : (int) Math.max(0L, ready - player.level().getGameTime());
	}

	static void say(ServerPlayer player, String key, ChatFormatting colour, Object... args) {
		long now = player.level().getGameTime();
		Long last = LAST_MESSAGE.get(player.getUUID());
		if (last != null && now - last < 10 && now >= last) {
			return;
		}
		LAST_MESSAGE.put(player.getUUID(), now);
		player.displayClientMessage(Component.translatable(key, args).withStyle(colour), true);
	}

	// ---------------------------------------------------------------- the card and the power

	/** The Undead Card (or the command). False if the player already holds the power or another holds it. */
	public static boolean grant(ServerPlayer player) {
		if (state(player).hasPower) {
			return false;
		}
		if (com.projecthero.mod.hero.UniquePowers.refuse(player, KEY)) {
			return false;
		}
		HeroTiers.claimPrimary(player, KEY);
		AndyState s = new AndyState();
		s.hasPower = true;
		save(player, s);
		reconcile(player);
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.getEyePosition();
		level.playSound(null, c.x, c.y, c.z, SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.35f, 1.6f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0f, 0.6f);
		level.sendParticles(BLOOD, c.x, c.y + 0.6, c.z, 8, 0.4, 0.2, 0.4, 0.0);
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
				Component.translatable("message.projecthero.andy.title").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)));
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
				Component.translatable("message.projecthero.andy.subtitle").withStyle(ChatFormatting.RED)));
		player.displayClientMessage(Component.translatable("message.projecthero.andy.acquired").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), false);
		player.displayClientMessage(Component.translatable("message.projecthero.andy.acquired_hint").withStyle(ChatFormatting.GRAY), false);
		com.projecthero.mod.andy.item.AndySuitItem.giveFirstTime(player); // v0.17.3: his suit, the first time only
		return true;
	}

	public static void revoke(ServerPlayer player) {
		AndyAbilities.clear(player);
		save(player, new AndyState());
		reconcile(player);
		PowerToggles.clearEffect(player, MobEffects.BLINDNESS);
		player.refreshDimensions();
		forget(player.getUUID());
	}

	/** Death (a real one), a new world, leaving: running moves end and the body is made whole again. */
	public static void clearTransient(ServerPlayer player) {
		AndyAbilities.clear(player);
		AndyState s = peek(player);
		if (s != null && s.hasPower && (s.anyBroken() || s.reformUntil > 0L || s.flags != 0 || s.extra.stream().anyMatch(v -> v != 0L))) {
			AndyState n = s.copy();
			n.partHp = AndyState.full();
			for (int i = 0; i < AndyState.PARTS; i++) {
				n.setTimes(i, 0L, 0L, 0L);
			}
			n.kinds = 0;
			n.reformUntil = 0L;
			n.flags = s.flags & AndyState.FLAG_REGEN_OFF; // v0.17.3: Off stays Off
			clearExtra(n);
			save(player, n);
			reconcile(player);
		}
	}

	/** Nothing cut off, burnt or used up. */
	static void clearExtra(AndyState n) {
		for (int i = 0; i < AndyState.EXTRA; i++) {
			n.setEx(i, 0L);
		}
	}

	public static void onPlayerJoin(ServerPlayer player) {
		reconcile(player);
	}

	// ---------------------------------------------------------------- the body

	/**
	 * Takes {@code amount} off a part. Returns true if that broke it. A part already gone takes nothing (the hit lands on the
	 * player's health all the same).
	 */
	public static boolean damagePart(ServerPlayer player, int part, float amount) {
		AndyState s = state(player);
		if (!s.hasPower || s.broken(part) || amount <= 0f) {
			return false;
		}
		AndyState n = s.copy();
		float hp = n.hp(part) - amount;
		n.lastHitAt = player.level().getGameTime();
		if (hp > 0.001f) {
			n.partHp.set(part, hp);
			save(player, n);
			return false;
		}
		save(player, n);
		breakPart(player, part, AndyState.KIND_BURST);
		return true;
	}

	/**
	 * The part is gone: burst ({@code KIND_BURST}), launched as a projectile ({@code KIND_LAUNCHED}), used up by finger shots
	 * ({@code KIND_SPENT}) or burnt away in a boost ({@code KIND_BURNT}).
	 */
	public static void breakPart(ServerPlayer player, int part, int kind) {
		AndyState s = state(player);
		if (!s.hasPower || s.broken(part)) {
			return;
		}
		long now = Math.max(1L, player.level().getGameTime());
		AndyState n = s.copy();
		n.partHp.set(part, 0f);
		n.setTimes(part, now, 0L, 0L);
		n.setKind(part, kind);
		if (part == AndyState.RIGHT_ARM || part == AndyState.LEFT_ARM) {
			// whatever bite the finger shots took goes with the arm
			n.setEx(part == AndyState.RIGHT_ARM ? AndyState.X_EATEN_R : AndyState.X_EATEN_L, 0L);
		}
		if (part == mainArm(player)) {
			// a held move from that hand stops
			n.setFlag(AndyState.FLAG_DEAD_LINE, false);
			n.setFlag(AndyState.FLAG_DEAD_ROAD, false);
		}
		save(player, n);
		reconcile(player);
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = AndyParts.centre(player, part);
		if (kind == AndyState.KIND_BURST) {
			AndySfx.partBreak(level, c); // v0.17.3 Andy sounds: wet tear + crunch, a spurt of blood
			level.sendParticles(BLOOD, c.x, c.y, c.z, 3, 0.1, 0.1, 0.1, 0.0);
		}
		if (part == AndyState.HEAD && kind == AndyState.KIND_BURST) {
			AndyAbilities.repairHeadbutt(player, true);
		}
	}

	/**
	 * Undead: what would have killed him shatters the whole body into pixels except the head. Health refills; for
	 * {@link AndyConfig#REFORM_GRACE} he is untouchable and can't use moves, and the rest grows back from the head by the
	 * staged regrowth ({@link #planHeadOnly}: rolling head, belly crawl, hand walk, stump shuffle, wobbly walk).
	 */
	public static void shatter(ServerPlayer player) {
		AndyState s = state(player);
		if (!s.hasPower) {
			return;
		}
		AndyAbilities.clear(player);
		long now = Math.max(1L, player.level().getGameTime());
		AndyState n = state(player).copy();
		for (int i = AndyState.BODY; i < AndyState.PARTS; i++) {
			if (!n.broken(i) || n.kind(i) != AndyState.KIND_LAUNCHED) {
				n.setKind(i, AndyState.KIND_SHATTER);
			}
			n.setTimes(i, n.broken(i) ? n.brokenAt(i) : now, 0L, 0L);
			n.partHp.set(i, 0f);
		}
		if (n.broken(AndyState.HEAD)) {
			// the head was already off (launched or burst): it re-forms first, then the rest
			long start = now + 2;
			n.setTimes(AndyState.HEAD, n.brokenAt(AndyState.HEAD), start, start + 14);
		}
		n.reformUntil = now + AndyConfig.REFORM_GRACE;
		n.flags = 0;
		clearExtra(n);
		n.animId = AndyState.ANIM_NONE;
		save(player, n);
		player.setHealth(player.getMaxHealth());
		player.clearFire();
		player.setAirSupply(player.getMaxAirSupply());
		player.removeEffect(MobEffects.POISON);
		player.removeEffect(MobEffects.WITHER);
		player.setDeltaMovement(Vec3.ZERO);
		player.hurtMarked = true;
		reconcile(player);
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position().add(0, 1.0, 0);
		AndySfx.shatter(level, c); // v0.17.3 Andy sounds: the whole body bursts
		level.sendParticles(BLOOD, c.x, c.y, c.z, 6, 0.3, 0.5, 0.3, 0.0);
		say(player, "message.projecthero.andy.undead", ChatFormatting.DARK_RED);
	}

	/** Regrows every part on the spot (operator command, tests). */
	public static void restore(ServerPlayer player) {
		AndyState n = state(player).copy();
		n.partHp = AndyState.full();
		for (int i = 0; i < AndyState.PARTS; i++) {
			n.setTimes(i, 0L, 0L, 0L);
		}
		n.kinds = 0;
		n.reformUntil = 0L;
		clearExtra(n);
		save(player, n);
		reconcile(player);
	}

	// ---------------------------------------------------------------- V: the speed

	public static void cycleSpeed(ServerPlayer player) {
		AndyState n = state(player).copy();
		// Shift+V: Normal -> Fast -> Burst -> Off (v0.17.3, tester: "the option to choose not to regenerate") -> Normal
		if (n.flag(AndyState.FLAG_REGEN_OFF)) {
			n.setFlag(AndyState.FLAG_REGEN_OFF, false);
			n.regenSpeed = AndyConfig.SPEED_NORMAL;
		} else if (n.regenSpeed >= AndyConfig.SPEED_BURST) {
			n.setFlag(AndyState.FLAG_REGEN_OFF, true);
		} else {
			n.regenSpeed = n.regenSpeed + 1;
		}
		save(player, n);
		String[] names = { "slow", "normal", "fast", "burst" };
		LAST_MESSAGE.remove(player.getUUID());
		String name = n.flag(AndyState.FLAG_REGEN_OFF) ? "off" : names[n.regenSpeed];
		say(player, "message.projecthero.andy.speed", ChatFormatting.RED, Component.translatable("projecthero.andy.speed." + name));
		player.level().playSound(null, player.getX(), player.getY() + 1, player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
				0.8f, 0.7f + 0.3f * n.regenSpeed);
	}

	static boolean regrowingAny(AndyState s, long now) {
		for (int i = 0; i < AndyState.PARTS; i++) {
			if (s.broken(i) && s.regrowStart(i) > 0L && s.regrowStart(i) <= now && now < s.regrowEnd(i)) {
				return true;
			}
		}
		return false;
	}

	public static void setSpeed(ServerPlayer player, int speed) {
		AndyState n = state(player).copy();
		n.regenSpeed = Math.floorMod(speed, AndyConfig.SPEEDS);
		n.setFlag(AndyState.FLAG_REGEN_OFF, false);
		save(player, n);
	}

	/** v0.17.3: a lost part put straight back (the thrown head picked up): whole at once, nothing to regrow or pay. */
	public static void reattach(ServerPlayer player, int part) {
		AndyState s = state(player);
		if (!s.hasPower || !s.broken(part)) {
			return;
		}
		AndyState n = s.copy();
		n.setTimes(part, 0L, 0L, 0L);
		n.setKind(part, AndyState.KIND_NONE);
		n.partHp.set(part, AndyConfig.PART_HP[part]);
		save(player, n);
		reconcile(player);
	}

	/** v0.17.3: regeneration Off (or back on at the current speed). */
	public static void setRegenOff(ServerPlayer player, boolean off) {
		AndyState n = state(player).copy();
		n.setFlag(AndyState.FLAG_REGEN_OFF, off);
		save(player, n);
	}

	public static boolean regenOff(Player player) {
		AndyState s = peek(player);
		return s != null && s.flag(AndyState.FLAG_REGEN_OFF);
	}

	// ---------------------------------------------------------------- stats

	public static void reconcile(ServerPlayer player) {
		AndyState s = peek(player);
		// v0.17.3: the old reform root and one-leg Slowness are gone (the stage modifiers below replace them)
		PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, REFORM_ID);
		PowerToggles.clearModifier(player, Attributes.JUMP_STRENGTH, REFORM_JUMP_ID);
		AndyBoneSword.attributes(player, s); // v0.17.3 Shift+V: the sword's damage / reach / speed while it is out
		if (s == null || !s.hasPower) {
			PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, MELEE_ID);
			PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, STAGE_ID);
			PowerToggles.clearModifier(player, Attributes.JUMP_STRENGTH, STAGE_JUMP_ID);
			return;
		}
		PowerToggles.modifier(player, Attributes.ATTACK_DAMAGE, MELEE_ID, victor(player) ? AndyConfig.VICTOR_MELEE : AndyConfig.BLOODY_ARTS_MELEE,
				AttributeModifier.Operation.ADD_VALUE);
		int stage = AndyStages.stage(s, player.level().getGameTime());
		LAST_STAGE.put(player.getUUID(), stage);
		if (stage == AndyStages.NORMAL) {
			PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, STAGE_ID);
			PowerToggles.clearModifier(player, Attributes.JUMP_STRENGTH, STAGE_JUMP_ID);
		} else {
			double speed = AndyStages.SPEED[stage];
			if (stage == AndyStages.CRAWL) {
				speed /= 0.3; // lying flat is vanilla's crawl, which already slows the input to the sneaking speed (0.3)
			}
			PowerToggles.modifier(player, Attributes.MOVEMENT_SPEED, STAGE_ID, speed - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			PowerToggles.modifier(player, Attributes.JUMP_STRENGTH, STAGE_JUMP_ID, AndyStages.JUMP[stage] - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		}
		if (s.broken(AndyState.HEAD)) {
			PowerToggles.effect(player, MobEffects.BLINDNESS, 0, false);
		} else {
			PowerToggles.clearEffect(player, MobEffects.BLINDNESS);
		}
	}

	/** Moves are allowed: has the power, alive, not just shattered. Says why not. */
	static boolean canAct(ServerPlayer player) {
		AndyState s = state(player);
		if (!s.hasPower || !player.isAlive() || player.isSpectator()) {
			return false;
		}
		if (s.reformUntil > player.level().getGameTime()) {
			say(player, "message.projecthero.andy.reforming", ChatFormatting.GRAY);
			return false;
		}
		return true;
	}

	// ---------------------------------------------------------------- tick

	public static void tick(ServerPlayer player) {
		AndyState s = player.getAttachedOrElse(ModAttachments.ANDY_STATE, null);
		if (s == null || !s.hasPower) {
			return;
		}
		long now = player.level().getGameTime();
		Long prev = LAST_TICK.put(player.getUUID(), now);
		if (prev != null && prev == now) {
			return;
		}
		// v0.17.3 (user: "remove the players passive auto feeding since this armour will do it for him"): only Andy's Suit's
		// chestplate feeds him
		if (com.projecthero.mod.andy.item.AndySuitItem.feedsHim(player)) {
			AndyAutoEat.tick(player);
		}
		AndyState n = null;
		boolean whole = false;
		// nothing starts growing back mid-flight: the hands, feet and whatever burnt off wait for the landing
		boolean flying = s.flag(AndyState.FLAG_PROPELLING);
		boolean halted = regrowthHalted(player, s);
		if (!flying && !halted && needsHeadOnlyPlan(s)) {
			n = s.copy();
			planHeadOnly(player, n, now);
		}
		for (int i = 0; i < AndyState.PARTS; i++) {
			AndyState cur = n != null ? n : s;
			if (!cur.broken(i)) {
				continue;
			}
			boolean growing = cur.regrowStart(i) > 0L && cur.regrowStart(i) < now;
			if (halted && i != AndyState.HEAD && (s.flag(AndyState.FLAG_REGEN_OFF) || !growing)) {
				// v0.17.3 Off: nothing grows back -- what was growing (or planned) falls away again and waits. An empty stomach:
				// nothing new starts (a part already growing, paid for, finishes). The head still comes back: it is free.
				if (cur.regrowStart(i) > 0L) {
					if (n == null) {
						n = s.copy();
					}
					n.setTimes(i, cur.brokenAt(i), 0L, 0L);
					if (cur.regrowStart(i) <= now) {
						Vec3 c = AndyParts.centre(player, i);
						((ServerLevel) player.level()).sendParticles(BLOOD, c.x, c.y, c.z, 6, 0.15, 0.2, 0.15, 0.0);
					}
				}
				continue;
			}
			if (cur.regrowStart(i) <= 0L) {
				if (flying) {
					continue;
				}
				long start = scheduleRegrow(player, cur, i, now);
				if (start > 0L) {
					if (n == null) {
						n = s.copy();
					}
					// paid up front: at the chosen speed; not paid in full, at half Normal
					boolean paid = payForPart(player, cur.regenSpeed);
					n.setTimes(i, cur.brokenAt(i), now, now + (paid ? AndyConfig.regrowTicks(i, cur.regenSpeed) : starvedTicks(i)));
					startedRegrowing(player, i, cur.kind(i));
				}
			} else if (cur.regrowStart(i) == now && cur.regrowEnd(i) > now) {
				// a planned (head-only) part starting: it pays its share now (the head re-forming after a shatter is free)
				if (i != AndyState.HEAD && !payForPart(player, cur.regenSpeed)) {
					if (n == null) {
						n = s.copy();
					}
					n.setTimes(i, cur.brokenAt(i), now, now + starvedTicks(i));
				}
				startedRegrowing(player, i, cur.kind(i)); // its left-behind piece goes
			} else if (cur.regrowEnd(i) > 0L && now >= cur.regrowEnd(i)) {
				if (n == null) {
					n = s.copy();
				}
				n.setTimes(i, 0L, 0L, 0L);
				n.setKind(i, AndyState.KIND_NONE);
				n.partHp.set(i, AndyConfig.PART_HP[i]);
				whole = true;
				// v0.17.3 Andy sounds: the wet snap of a finished part is client-side (AndySounds.regrowth -> andy.regrow.finish)
			}
		}
		n = alignToTorso(s, n, now); // an unpaid torso grows slower: the planned limbs follow its marks
		AndyState cur = n != null ? n : s;
		// the bites the finger shots took have grown back: forget them
		for (int arm = AndyState.RIGHT_ARM; arm <= AndyState.LEFT_ARM; arm++) {
			if (cur.eaten0(arm) > 0f && cur.eatenRegrow(arm, now) >= 1f) {
				if (n == null) {
					n = s.copy();
					cur = n;
				}
				n.setEx(arm == AndyState.RIGHT_ARM ? AndyState.X_EATEN_R : AndyState.X_EATEN_L, 0L);
			}
		}
		// the hands and feet (and anything half burnt) have grown back after the flight
		if (!flying && cur.ex(AndyState.X_CUT_REGROW) > 0L && now >= cur.ex(AndyState.X_CUT_REGROW) + AndyFlight.cutRegrowTicks(cur)) {
			if (n == null) {
				n = s.copy();
				cur = n;
			}
			n.setEx(AndyState.X_CUT_REGROW, 0L);
			n.setEx(AndyState.X_BURN_TICKS, 0L);
			n.setEx(AndyState.X_BURN_SEG, 0L);
		}
		// parts knit back slowly between fights
		// v0.17.3 (tester: "Fast regen doesnt feel fast enough when being hurt"): the speed sets how soon after a hit and how
		// fast hurt parts knit and his health comes back
		int hs = healSpeed(player, cur);
		if (hs >= 0 && now % 10 == 0 && now - cur.lastHitAt > AndyConfig.KNIT_DELAY_BY_SPEED[hs]) {
			for (int i = 0; i < AndyState.PARTS; i++) {
				if (!cur.broken(i) && cur.hp(i) < AndyConfig.PART_HP[i]) {
					if (n == null) {
						n = s.copy();
						cur = n;
					}
					n.partHp.set(i, Math.min(AndyConfig.PART_HP[i], n.hp(i) + AndyConfig.KNIT_PER_SECOND[hs] * 0.5f));
				}
			}
		}
		if (hs >= 0 && now % 10 == 0 && now - cur.lastHitAt > AndyConfig.HEAL_DELAY[hs] && AndyConfig.HEAL_PER_SECOND[hs] > 0f
				&& player.getHealth() < player.getMaxHealth() && player.isAlive()) {
			float amount = Math.min(AndyConfig.HEAL_PER_SECOND[hs] * 0.5f, player.getMaxHealth() - player.getHealth());
			player.heal(amount);
			if (!player.getAbilities().instabuild) {
				player.causeFoodExhaustion(amount * AndyConfig.HEAL_EXHAUSTION);
			}
		}
		// v0.17.3 (tester: "A way people can naturally pick up the head"): carried by another player while only a head; once his
		// body is back he climbs down
		if (player.getVehicle() instanceof Player && !cur.broken(AndyState.BODY)) {
			player.stopRiding();
		}
		if (cur.reformUntil > 0L && now >= cur.reformUntil) {
			if (n == null) {
				n = s.copy();
				cur = n;
			}
			n.reformUntil = 0L;
		}
		if (cur.victorUntil > 0L && (now >= cur.victorUntil || !AndyConfig.VICTOR_ENABLED)) {
			if (n == null) {
				n = s.copy();
				cur = n;
			}
			n.victorUntil = 0L;
			n.animId = AndyState.ANIM_CARD_BACK;
			n.animStart = now;
			n.setFlag(AndyState.FLAG_DEAD_ROAD, false);
			player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.ARMOR_EQUIP_ELYTRA.value(), SoundSource.PLAYERS, 0.8f, 1.4f);
			say(player, "message.projecthero.andy.card_back", ChatFormatting.GRAY);
		}
		if (n != null) {
			save(player, n);
		}
		Integer lastStage = LAST_STAGE.get(player.getUUID());
		boolean stageChanged = lastStage == null || lastStage != AndyStages.stage(cur, now);
		if (whole || stageChanged || player.tickCount % 10 == 0 || n != null) {
			reconcile(player);
		}
		if (victor(player) && player.tickCount % 4 == 0) {
			((ServerLevel) player.level()).sendParticles(BLOOD, player.getX(), player.getEyeY() + 0.2, player.getZ(), 1, 0.25, 0.1, 0.25, 0.0);
		}
		AndyAbilityManager.tickHold(player);
		AndyAbilities.tick(player);
		AndyFlight.tick(player);
	}

	/** Only the head is left and nothing of the rest has been planned to grow back yet. */
	public static boolean needsHeadOnlyPlan(AndyState s) {
		if (s.broken(AndyState.HEAD)) {
			return false;
		}
		for (int i = AndyState.BODY; i < AndyState.PARTS; i++) {
			if (!s.broken(i) || s.regrowStart(i) > 0L) {
				return false;
			}
		}
		return true;
	}

	/**
	 * v0.17.3 (user: "torso until theres space for arms and then both arms at the same time and then once torso is done both
	 * legs at the same time"): with only the head left, every part is planned at once by the same rule as {@link #scheduleRegrow}
	 * (numbers in {@link AndyConfig}): the torso starts, from the neck down; both arms start together once it passes the
	 * shoulders ({@link AndyConfig#SHOULDER_SHARE}); both legs together once it is done ({@link AndyConfig#HIP_SHARE}). The
	 * clients and the box ({@link AndyStages}) read the stages straight off these times. Each part pays its food as it starts;
	 * one that can not grows at half Normal (and {@link #alignToTorso} keeps the planned limbs on the torso's marks).
	 */
	static void planHeadOnly(ServerPlayer player, AndyState n, long now) {
		int speed = Math.floorMod(n.regenSpeed, AndyConfig.SPEEDS);
		long delay = canPay(player, speed) ? AndyConfig.STAGE_DELAY[speed] : 2L * AndyConfig.STAGE_DELAY[AndyConfig.SPEED_NORMAL];
		if (n.slowRegenUntil > now) {
			delay = (long) (delay * AndyConfig.CRIMSON_SLOW_FACTOR);
		}
		long[][] plan = headOnlyPlan(speed, now + delay);
		int main = mainArm(player);
		int leg = mainLeg(player);
		int[] order = { AndyState.BODY, main, otherArm(main), leg, leg == AndyState.RIGHT_LEG ? AndyState.LEFT_LEG : AndyState.RIGHT_LEG };
		for (int k = 0; k < order.length; k++) {
			n.setTimes(order[k], n.brokenAt(order[k]), plan[k][0], plan[k][1]);
		}
		player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.6f, 0.9f);
	}

	/**
	 * The head-only plan from {@code start} at {@code speed}: {start, end} for the body, the main arm, the other arm, the
	 * main-side leg and the other leg, in that order (the two arms share their times, and so do the two legs).
	 */
	public static long[][] headOnlyPlan(int speed, long start) {
		long torso = AndyConfig.regrowTicks(AndyState.BODY, speed);
		int limb = AndyConfig.regrowTicks(AndyState.RIGHT_ARM, speed);
		long bodyEnd = start + torso;
		long arms = torsoMark(start, bodyEnd, AndyConfig.SHOULDER_SHARE);
		long legs = torsoMark(start, bodyEnd, AndyConfig.HIP_SHARE);
		return new long[][] { { start, bodyEnd }, { arms, arms + limb }, { arms, arms + limb }, { legs, legs + limb }, { legs, legs + limb } };
	}

	/** The tick at which a torso regrowing over [{@code start}, {@code end}) passes {@code share}. */
	public static long torsoMark(long start, long end, float share) {
		return start + (long) Math.ceil(share * (end - start));
	}

	/** The torso is regrowing and has passed {@code share} (a share of 1 is never "passed": the torso must be whole). */
	public static boolean torsoPast(AndyState s, float share, long now) {
		long st = s.regrowStart(AndyState.BODY);
		return share < 1f && st > 0L && now >= torsoMark(st, s.regrowEnd(AndyState.BODY), share);
	}

	/**
	 * When a broken part may start regrowing, or 0 if not yet. With the torso gone the limbs wait for something to grow from:
	 * the arms for the shoulders ({@link AndyConfig#SHOULDER_SHARE} of the torso's regrowth), the legs (and a head) for the
	 * whole torso. With the torso there they go after their own delay, all in parallel.
	 */
	private static long scheduleRegrow(ServerPlayer player, AndyState s, int part, long now) {
		if (s.boneSwordArm() == part) {
			return 0L; // v0.17.3: the arm is the Bone Sword in his hand -- it grows back (and is paid for) once the sword is put away
		}
		if (part != AndyState.BODY && s.broken(AndyState.BODY) && !torsoPast(s, AndyConfig.torsoShareFor(part), now)) {
			return 0L;
		}
		if (s.kind(part) == AndyState.KIND_SPENT && now < s.ex(AndyState.X_LAST_SHOT) + AndyConfig.EATEN_REGROW_WAIT) {
			return 0L; // an arm used up by finger shots waits until he stops shooting
		}
		float delay = canPay(player, s.regenSpeed) ? AndyConfig.REGROW_DELAY[Math.floorMod(s.regenSpeed, AndyConfig.SPEEDS)] : AndyConfig.starvedDelay();
		if (s.slowRegenUntil > now) {
			delay *= AndyConfig.CRIMSON_SLOW_FACTOR;
		}
		return now >= s.brokenAt(part) + (long) delay ? now : 0L;
	}

	/**
	 * v0.17.3 (user: "lets go back to flat values, regenerating drains saturation first and then hunger. normal mode: Full body
	 * regen drains 10 food energy, Fast mode: 15, Burst mode: 20"): every part pays its share ({@link AndyConfig#partFood}: Normal
	 * 2, Fast 3, Burst 4) the moment it starts regrowing, out of the saturation first, then the hunger bar. True if it was paid
	 * in full; if not, whatever there was is taken and the part regrows at half Normal ({@link #starvedTicks}, user: "you need
	 * hunger/saturation in order to regrow otherwise you regenerate 50% slower than normal speed"). Creative pays nothing.
	 */
	static boolean payForPart(ServerPlayer player, int speed) {
		if (player.getAbilities().instabuild) {
			return true;
		}
		float share = AndyConfig.partFood(speed);
		var food = player.getFoodData();
		float sat = food.getSaturationLevel();
		float fromSat = Math.min(sat, share);
		food.setSaturation(sat - fromSat);
		float rest = share - fromSat;
		if (rest <= 1.0e-4f) {
			return true;
		}
		// the bar goes in whole points: a part of a point left over is owed to the next part
		float owed = FOOD_OWED.getOrDefault(player.getUUID(), 0f) + rest;
		int whole = (int) Math.floor(owed + 1.0e-4f);
		int have = food.getFoodLevel();
		food.setFoodLevel(Math.max(0, have - whole));
		FOOD_OWED.put(player.getUUID(), Math.max(0f, owed - whole));
		return have >= whole;
	}

	/**
	 * v0.17.3: nothing but the head grows back right now -- regeneration set to Off (Shift+V, tester: "the option to choose not
	 * to regenerate"), or a completely empty stomach, no hunger and no saturation left (tester: "Make regeneration slow to
	 * impossible when completely starving, leaving only the head until you eat"). Low but not empty still regrows, at half Normal.
	 */
	public static boolean regrowthHalted(ServerPlayer player, AndyState s) {
		return s.flag(AndyState.FLAG_REGEN_OFF) || emptyStomach(player);
	}

	/** No hunger and no saturation at all (creative never). */
	public static boolean emptyStomach(Player player) {
		return !player.getAbilities().instabuild && player.getFoodData().getFoodLevel() <= 0 && player.getFoodData().getSaturationLevel() <= 0f;
	}

	/** The speed slot that drives his healing: his speed, the starved slot when he can't pay, -1 when it is halted. */
	static int healSpeed(ServerPlayer player, AndyState s) {
		if (regrowthHalted(player, s)) {
			return -1;
		}
		return canPay(player, s.regenSpeed) ? Math.floorMod(s.regenSpeed, AndyConfig.SPEEDS) : AndyConfig.SPEED_SLOW;
	}

	/** Enough food for a part's share right now (otherwise the wait doubles and it will grow at half Normal). */
	static boolean canPay(ServerPlayer player, int speed) {
		return player.getAbilities().instabuild
				|| player.getFoodData().getSaturationLevel() + player.getFoodData().getFoodLevel() >= AndyConfig.partFood(speed) - 1.0e-4f;
	}

	/** A part's regrowth time when it could not be paid for: half the Normal speed, whatever the mode. */
	public static int starvedTicks(int part) {
		return 2 * AndyConfig.regrowTicks(part, AndyConfig.SPEED_NORMAL);
	}

	/** The part is regrowing at the unpaid, half-Normal rate (client-safe: read off the synced times). */
	public static boolean starvedRegrowth(AndyState s, int part) {
		long st = s.regrowStart(part), end = s.regrowEnd(part);
		return s.broken(part) && st > 0L && end - st >= starvedTicks(part) && end - st > AndyConfig.regrowTicks(part, s.regenSpeed);
	}

	/**
	 * Planned limbs (not started yet) follow the torso's marks when it grows at half Normal (not paid for): the arms start at
	 * the shoulders, the legs when it is done.
	 */
	private static AndyState alignToTorso(AndyState s, AndyState n, long now) {
		AndyState cur = n != null ? n : s;
		long bs = cur.regrowStart(AndyState.BODY), be = cur.regrowEnd(AndyState.BODY);
		if (!cur.broken(AndyState.BODY) || bs <= 0L) {
			return n;
		}
		for (int i = AndyState.RIGHT_ARM; i < AndyState.PARTS; i++) {
			long st = cur.regrowStart(i);
			if (!cur.broken(i) || st <= now) {
				continue; // started already, or not planned
			}
			long want = Math.max(now + 1L, torsoMark(bs, be, AndyConfig.torsoShareFor(i)));
			if (want != st) {
				long len = cur.regrowEnd(i) - st;
				if (n == null) {
					n = s.copy();
					cur = n;
				}
				n.setTimes(i, cur.brokenAt(i), want, want + len);
			}
		}
		return n;
	}

	private static void startedRegrowing(ServerPlayer player, int part, int kind) {
		// v0.17.3 Andy sounds: the regrowth itself is heard client-side (AndySounds: bone, muscle, skin beds)
		if (kind == AndyState.KIND_LAUNCHED) {
			// the launched part (still lying about, or stuck in a wall) bursts as its replacement grows
			ServerLevel level = (ServerLevel) player.level();
			for (AndyPartEntity e : level.getEntitiesOfClass(AndyPartEntity.class, new AABB(player.blockPosition()).inflate(256.0),
					e -> e.ownerIs(player) && e.bodyPart() == part)) {
				e.discard();
			}
		}
	}
}
