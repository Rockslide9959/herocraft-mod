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
 * tap of V cycles the speed (Slow / Normal / Burst). With only the head left everything grows back in a set order
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
	/** The stage last reconciled (the speed and jump modifiers follow it). */
	private static final Map<UUID, Integer> LAST_STAGE = new ConcurrentHashMap<>();

	private Andy() {
	}

	public static void clearSessionState() {
		LAST_MESSAGE.clear();
		LAST_TICK.clear();
		LAST_STAGE.clear();
		AndyAbilities.clearSessionState();
		AndyAbilityManager.clearSessionState();
		AndyDamage.clearSessionState();
		AndyFlight.clearSessionState();
	}

	static void forget(UUID id) {
		LAST_MESSAGE.remove(id);
		LAST_TICK.remove(id);
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

	/** The leg on the main hand's side (it regrows first after the arms). */
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
		if (s.armReady(main, now)) {
			return main;
		}
		return s.armReady(otherArm(main), now) ? otherArm(main) : -1;
	}

	/** What he gets about with right now ({@link AndyStages}), client-safe. */
	public static int stage(Player player) {
		AndyState s = peek(player);
		return s == null || !s.hasPower ? AndyStages.NORMAL : AndyStages.stage(s, player.level().getGameTime());
	}

	public static boolean victor(Player player) {
		AndyState s = peek(player);
		return s != null && s.hasPower && s.victorUntil > player.level().getGameTime();
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
			n.flags = 0;
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
	 * staged regrowth ({@link #planHeadOnly}: rolling head, arm drag, hand walk, hop, limp). No hunger.
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
		n.regenSpeed = (n.regenSpeed + 1) % 3;
		save(player, n);
		String[] names = { "slow", "normal", "burst" };
		LAST_MESSAGE.remove(player.getUUID());
		say(player, "message.projecthero.andy.speed", ChatFormatting.RED, Component.translatable("projecthero.andy.speed." + names[n.regenSpeed]));
		player.level().playSound(null, player.getX(), player.getY() + 1, player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
				0.8f, 0.7f + 0.3f * n.regenSpeed);
	}

	public static void setSpeed(ServerPlayer player, int speed) {
		AndyState n = state(player).copy();
		n.regenSpeed = Math.floorMod(speed, 3);
		save(player, n);
	}

	// ---------------------------------------------------------------- stats

	public static void reconcile(ServerPlayer player) {
		AndyState s = peek(player);
		// v0.17.3: the old reform root and one-leg Slowness are gone (the stage modifiers below replace them)
		PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, REFORM_ID);
		PowerToggles.clearModifier(player, Attributes.JUMP_STRENGTH, REFORM_JUMP_ID);
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
			if (stage == AndyStages.DRAG) {
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
		AndyState n = null;
		boolean whole = false;
		// nothing starts growing back mid-flight: the hands, feet and whatever burnt off wait for the landing
		boolean flying = s.flag(AndyState.FLAG_PROPELLING);
		if (!flying && needsHeadOnlyPlan(s)) {
			n = s.copy();
			planHeadOnly(player, n, now);
		}
		for (int i = 0; i < AndyState.PARTS; i++) {
			AndyState cur = n != null ? n : s;
			if (!cur.broken(i)) {
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
					int speed = starving(player, cur, i) ? AndyConfig.SPEED_SLOW : cur.regenSpeed;
					payFood(player, cur, i);
					n.setTimes(i, cur.brokenAt(i), now, now + AndyConfig.REGROW_TICKS[speed]);
					startedRegrowing(player, i, cur.kind(i));
				}
			} else if (cur.regrowStart(i) == now && cur.regrowEnd(i) > now) {
				startedRegrowing(player, i, cur.kind(i)); // a planned (head-only) part starting: its left-behind piece goes
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
		if (now % 20 == 0 && now - cur.lastHitAt > AndyConfig.KNIT_DELAY) {
			for (int i = 0; i < AndyState.PARTS; i++) {
				if (!cur.broken(i) && cur.hp(i) < AndyConfig.PART_HP[i]) {
					if (n == null) {
						n = s.copy();
						cur = n;
					}
					n.partHp.set(i, Math.min(AndyConfig.PART_HP[i], n.hp(i) + AndyConfig.PART_KNIT));
				}
			}
		}
		if (cur.reformUntil > 0L && now >= cur.reformUntil) {
			if (n == null) {
				n = s.copy();
				cur = n;
			}
			n.reformUntil = 0L;
		}
		if (cur.victorUntil > 0L && now >= cur.victorUntil) {
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
	 * v0.17.3 (user): with only the head left, every part is planned at once, in this order (numbers in {@link AndyConfig}):
	 * the body starts; the main arm starts early (before the body is a fifth done); when it is done the other arm; the body
	 * ends {@link AndyConfig#STAGE_TORSO_TAIL} after that; then the main-side leg; then the other leg. The clients and the
	 * box ({@link AndyStages}) read the stages straight off these times.
	 */
	static void planHeadOnly(ServerPlayer player, AndyState n, long now) {
		boolean shattered = n.kind(AndyState.BODY) == AndyState.KIND_SHATTER;
		int speed = n.regenSpeed;
		if (!shattered && !player.getAbilities().instabuild) {
			// it costs what the five parts would (the Undead shatter is free); short of food it all goes at the slow speed
			int cost = 0;
			for (int i = AndyState.BODY; i < AndyState.PARTS; i++) {
				cost += foodCost(n, i);
			}
			var food = player.getFoodData();
			if (food.getFoodLevel() < cost) {
				speed = AndyConfig.SPEED_SLOW;
			}
			food.setFoodLevel(Math.max(0, food.getFoodLevel() - cost));
			food.setSaturation(Math.min(food.getSaturationLevel(), food.getFoodLevel()));
		}
		long delay = AndyConfig.STAGE_DELAY[speed];
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
	 * The head-only plan from {@code start}: {start, end} for the body, the main arm, the other arm, the main-side leg and the
	 * other leg, in that order.
	 */
	public static long[][] headOnlyPlan(int speed, long start) {
		int limb = AndyConfig.STAGE_LIMB[speed];
		long mainArm = start + AndyConfig.STAGE_ARM_LEAD[speed];
		long offArm = mainArm + limb;
		long armsDone = offArm + limb;
		long bodyEnd = armsDone + AndyConfig.STAGE_TORSO_TAIL;
		long leg1 = bodyEnd + AndyConfig.STAGE_LEG_GAP[speed];
		long leg2 = leg1 + limb + AndyConfig.STAGE_LEG_GAP[speed];
		return new long[][] { { start, bodyEnd }, { mainArm, mainArm + limb }, { offArm, armsDone }, { leg1, leg1 + limb }, { leg2, leg2 + limb } };
	}

	/** When a broken part may start regrowing, or 0 if not yet (the body regrows before anything else). */
	private static long scheduleRegrow(ServerPlayer player, AndyState s, int part, long now) {
		if (part != AndyState.BODY && s.broken(AndyState.BODY)) {
			return 0L;
		}
		if (s.kind(part) == AndyState.KIND_SPENT && now < s.ex(AndyState.X_LAST_SHOT) + AndyConfig.EATEN_REGROW_WAIT) {
			return 0L; // an arm used up by finger shots waits until he stops shooting
		}
		int speed = starving(player, s, part) ? AndyConfig.SPEED_SLOW : s.regenSpeed;
		float delay = AndyConfig.REGROW_DELAY[speed];
		if (s.slowRegenUntil > now) {
			delay *= AndyConfig.CRIMSON_SLOW_FACTOR;
		}
		return now >= s.brokenAt(part) + (long) delay ? now : 0L;
	}

	private static int foodCost(AndyState s, int part) {
		return AndyConfig.REGROW_FOOD[s.regenSpeed] + (part == AndyState.BODY ? 1 : 0);
	}

	/** Not enough food for this speed: the part still regrows, at the slow speed, for whatever food is left. */
	private static boolean starving(ServerPlayer player, AndyState s, int part) {
		return !player.getAbilities().instabuild && player.getFoodData().getFoodLevel() < foodCost(s, part);
	}

	private static void payFood(ServerPlayer player, AndyState s, int part) {
		if (player.getAbilities().instabuild) {
			return;
		}
		var food = player.getFoodData();
		food.setFoodLevel(Math.max(0, food.getFoodLevel() - foodCost(s, part)));
		food.setSaturation(Math.min(food.getSaturationLevel(), food.getFoodLevel()));
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
