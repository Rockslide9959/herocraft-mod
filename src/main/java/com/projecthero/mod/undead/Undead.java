package com.projecthero.mod.undead;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.undead.data.UndeadState;
import com.projecthero.mod.undead.entity.UndeadPartEntity;
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
 * The single server-side API for Undead (Undead Unluck), the v0.17.3 Hero-Tier power. Nothing else writes
 * {@link UndeadState}. Moves in {@link UndeadAbilities} and {@link UndeadFlight}, damage rules in {@link UndeadDamage}, numbers in
 * {@link UndeadConfig}.
 *
 * <h2>A body made of pixels</h2>
 * Six parts (head, body, both arms, both legs), each with its own HP. Every hit is placed on a part by where it lands
 * ({@link UndeadParts}); a part at 0 HP breaks ({@link #breakPart}) -- every client bursts it into one cube per skin texel --
 * and is gone until it regrows. v0.17.3 (user: "build our undead power the same way theyve built theirs ... the regeneration to
 * work the same ... at the same speed"): the regrowth is NBC Heroes' Regenerative Segmentation -- ten segments (the six parts
 * plus the hands and feet), each growing one frame a tick ({@link UndeadConfig#FRAMES}: about a second a part) in NBC's order
 * (the body once the head has 4 frames, the limbs once the body has 7, each hand / foot once its limb has 5), free, starting
 * at once. Shift+V turns auto-regrowth off (Toggle Regen); the Regen Menu ({@link UndeadRegenMenu}) grows segments a stage at a
 * time (bone, flesh, skin) or strips them. He gets about however what is left lets him ({@link UndeadStages}); worn armour falls
 * off the parts it sat on ({@link UndeadArmour}).
 *
 * <h2>Vanilla health</h2>
 * Vanilla health stays the player's real life pool and takes every hit as usual. The part system runs beside it and only
 * decides what breaks. Health reaching zero is the Undead passive ({@link #shatter}): the body bursts apart except the head,
 * health is refilled, and the rest grows back from the head by the staged regrowth. Only {@code /kill}, the void and other
 * {@code BYPASSES_INVULNERABILITY} damage really kill.
 */
public final class Undead {
	public static final String KEY = "undead";

	private static final ResourceLocation MELEE_ID = PowerToggles.id("undead_bloody_arts");
	private static final ResourceLocation REFORM_ID = PowerToggles.id("undead_reform");
	private static final ResourceLocation REFORM_JUMP_ID = PowerToggles.id("undead_reform_jump");
	/** v0.17.3: what the body is left to get about with (rolling head ... walking), by stage. */
	private static final ResourceLocation STAGE_ID = PowerToggles.id("undead_stage");
	private static final ResourceLocation STAGE_JUMP_ID = PowerToggles.id("undead_stage_jump");

	public static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.72f, 0.03f, 0.06f), 1.1f);
	public static final DustParticleOptions BLOOD_BIG = new DustParticleOptions(new Vector3f(0.8f, 0.05f, 0.08f), 2.4f);
	public static final DustParticleOptions BLOOD_DARK = new DustParticleOptions(new Vector3f(0.45f, 0.0f, 0.03f), 1.6f);

	private static final Map<UUID, Long> LAST_MESSAGE = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();
	/** v0.17.3 Regen Menu "Regenerate Whole Body": everything grows back even with auto-regrowth off, until he is whole. */
	private static final java.util.Set<UUID> GROW_ALL = ConcurrentHashMap.newKeySet();
	/** The stage last reconciled (the speed and jump modifiers follow it). */
	private static final Map<UUID, Integer> LAST_STAGE = new ConcurrentHashMap<>();

	private Undead() {
	}

	public static void clearSessionState() {
		LAST_MESSAGE.clear();
		LAST_TICK.clear();
		GROW_ALL.clear();
		UndeadArmour.clearSessionState();
		UndeadRegenMenu.clearSessionState();
		LAST_STAGE.clear();
		UndeadAbilities.clearSessionState();
		UndeadAbilityManager.clearSessionState();
		UndeadDamage.clearSessionState();
		UndeadFlight.clearSessionState();
	}

	static void forget(UUID id) {
		LAST_MESSAGE.remove(id);
		LAST_TICK.remove(id);
		GROW_ALL.remove(id);
		UndeadArmour.forget(id);
		UndeadRegenMenu.forget(id);
		LAST_STAGE.remove(id);
		UndeadAbilities.forget(id);
		UndeadAbilityManager.forget(id);
		UndeadDamage.forget(id);
		UndeadFlight.forget(id);
	}

	// ---------------------------------------------------------------- state

	public static UndeadState state(ServerPlayer player) {
		return player.getAttachedOrCreate(ModAttachments.UNDEAD_STATE);
	}

	static void save(ServerPlayer player, UndeadState state) {
		player.setAttached(ModAttachments.UNDEAD_STATE, state);
	}

	public static UndeadState peek(Player player) {
		return player.getAttachedOrElse(ModAttachments.UNDEAD_STATE, null);
	}

	public static boolean hasPower(Player player) {
		UndeadState s = peek(player);
		return s != null && s.hasPower;
	}

	/** The part is gone or still re-forming (client-safe). */
	public static boolean missing(Player player, int part) {
		UndeadState s = peek(player);
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
		return player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT ? UndeadState.RIGHT_ARM : UndeadState.LEFT_ARM;
	}

	public static int otherArm(int arm) {
		return arm == UndeadState.RIGHT_ARM ? UndeadState.LEFT_ARM : UndeadState.RIGHT_ARM;
	}

	public static boolean bothLegsMissing(Player player) {
		return missing(player, UndeadState.RIGHT_LEG) && missing(player, UndeadState.LEFT_LEG);
	}

	/** The leg on the main hand's side. */
	public static int mainLeg(Player player) {
		return mainArm(player) == UndeadState.RIGHT_ARM ? UndeadState.RIGHT_LEG : UndeadState.LEFT_LEG;
	}

	/**
	 * The arm a finger shot or the stretch punch comes from (client-safe): the main arm, or the other one when the main is
	 * gone, still growing or used up; -1 with neither.
	 */
	public static int shootingArm(Player player) {
		UndeadState s = peek(player);
		if (s == null || !s.hasPower) {
			return -1;
		}
		float now = player.level().getGameTime();
		int main = mainArm(player);
		int other = otherArm(main);
		boolean mainOk = s.armReady(main, now), otherOk = s.armReady(other, now);
		if (mainOk && otherOk) {
			// v0.17.3 (tester: "Alternation between left and right hands instead of just one"): both there, the one not used last
			return s.flag(UndeadState.FLAG_NEXT_OFF) ? other : main; // the main arm first
		}
		return mainOk ? main : otherOk ? other : -1;
	}

	/** What he gets about with right now ({@link UndeadStages}), client-safe. */
	public static int stage(Player player) {
		UndeadState s = peek(player);
		return s == null || !s.hasPower ? UndeadStages.NORMAL : UndeadStages.stage(s, player.level().getGameTime());
	}

	public static boolean victor(Player player) {
		UndeadState s = peek(player);
		return UndeadConfig.VICTOR_ENABLED && s != null && s.hasPower && s.victorUntil > player.level().getGameTime();
	}

	public static boolean reforming(Player player) {
		UndeadState s = peek(player);
		return s != null && s.hasPower && s.reformUntil > player.level().getGameTime();
	}

	public static int cooldownRemaining(Player player, String abilityId) {
		UndeadState s = peek(player);
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
		UndeadState s = new UndeadState();
		s.hasPower = true;
		save(player, s);
		reconcile(player);
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.getEyePosition();
		level.playSound(null, c.x, c.y, c.z, SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.35f, 1.6f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0f, 0.6f);
		level.sendParticles(BLOOD, c.x, c.y + 0.6, c.z, 8, 0.4, 0.2, 0.4, 0.0);
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
				Component.translatable("message.projecthero.undead.title").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)));
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
				Component.translatable("message.projecthero.undead.subtitle").withStyle(ChatFormatting.RED)));
		player.displayClientMessage(Component.translatable("message.projecthero.undead.acquired").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), false);
		player.displayClientMessage(Component.translatable("message.projecthero.undead.acquired_hint").withStyle(ChatFormatting.GRAY), false);
		return true;
	}

	public static void revoke(ServerPlayer player) {
		UndeadAbilities.clear(player);
		save(player, new UndeadState());
		reconcile(player);
		PowerToggles.clearEffect(player, MobEffects.BLINDNESS);
		player.refreshDimensions();
		forget(player.getUUID());
	}

	/** Death (a real one), a new world, leaving: running moves end and the body is made whole again. */
	public static void clearTransient(ServerPlayer player) {
		UndeadAbilities.clear(player);
		UndeadState s = peek(player);
		if (s != null && s.hasPower && (s.anyBroken() || s.reformUntil > 0L || s.flags != 0 || s.extra.stream().anyMatch(v -> v != 0L))) {
			UndeadState n = s.copy();
			n.partHp = UndeadState.full();
			for (int i = 0; i < UndeadState.SEGS; i++) {
				n.setTimes(i, 0L, 0L, 0L);
			}
			n.kinds = 0;
			n.reformUntil = 0L;
			n.flags = s.flags & UndeadState.FLAG_REGEN_OFF; // v0.17.3: auto-regrowth off stays off
			clearExtra(n);
			save(player, n);
			reconcile(player);
		}
	}

	/** Nothing cut off, burnt or used up. */
	static void clearExtra(UndeadState n) {
		for (int i = 0; i < UndeadState.EXTRA; i++) {
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
		UndeadState s = state(player);
		if (!s.hasPower || s.broken(part) || amount <= 0f) {
			return false;
		}
		UndeadState n = s.copy();
		float hp = n.hp(part) - amount;
		n.lastHitAt = player.level().getGameTime();
		if (hp > 0.001f) {
			n.partHp.set(part, hp);
			save(player, n);
			return false;
		}
		save(player, n);
		breakPart(player, part, UndeadState.KIND_BURST);
		return true;
	}

	/**
	 * The part is gone: burst ({@code KIND_BURST}), launched as a projectile ({@code KIND_LAUNCHED}), used up by finger shots
	 * ({@code KIND_SPENT}) or burnt away in a boost ({@code KIND_BURNT}).
	 */
	public static void breakPart(ServerPlayer player, int part, int kind) {
		UndeadState s = state(player);
		if (!s.hasPower || (s.broken(part) && s.regrowStart(part) <= 0L)) {
			return;
		}
		long now = Math.max(1L, player.level().getGameTime());
		UndeadState n = s.copy();
		if (part < UndeadState.PARTS) {
			n.partHp.set(part, 0f);
		}
		n.setTimes(part, now, 0L, 0L);
		n.setKind(part, kind);
		// v0.17.3 (NBC's segments): a limb takes its hand / foot with it
		int end = UndeadState.extremity(part);
		if (end >= 0 && !(n.broken(end) && n.regrowStart(end) <= 0L)) {
			n.setTimes(end, now, 0L, 0L);
			n.setKind(end, kind);
		}
		if (part == UndeadState.RIGHT_ARM || part == UndeadState.LEFT_ARM) {
			// whatever bite the finger shots took goes with the arm
			n.setEx(part == UndeadState.RIGHT_ARM ? UndeadState.X_EATEN_R : UndeadState.X_EATEN_L, 0L);
		}
		if (part == mainArm(player)) {
			// a held move from that hand stops
			n.setFlag(UndeadState.FLAG_DEAD_LINE, false);
			n.setFlag(UndeadState.FLAG_DEAD_ROAD, false);
		}
		save(player, n);
		reconcile(player);
		UndeadArmour.partLost(player, part, kind); // v0.17.3 (user): the armour on it falls off / the shoulder pad goes
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = UndeadParts.centre(player, UndeadState.limbOf(part));
		if (kind == UndeadState.KIND_BURST) {
			UndeadSfx.partBreak(level, c); // v0.17.3 Undead sounds: wet tear + crunch, a spurt of blood
			level.sendParticles(BLOOD, c.x, c.y, c.z, 3, 0.1, 0.1, 0.1, 0.0);
		}
		if (part == UndeadState.HEAD && kind == UndeadState.KIND_BURST) {
			UndeadAbilities.repairHeadbutt(player, true);
		}
	}

	/**
	 * Undead: what would have killed him shatters the whole body into pixels except the head. Health refills; for
	 * {@link UndeadConfig#REFORM_GRACE} he is untouchable and can't use moves, and the rest grows back from the head by the
	 * staged regrowth ({@link #planHeadOnly}: rolling head, belly crawl, hand walk, stump shuffle, wobbly walk).
	 */
	public static void shatter(ServerPlayer player) {
		UndeadState s = state(player);
		if (!s.hasPower) {
			return;
		}
		UndeadAbilities.clear(player);
		long now = Math.max(1L, player.level().getGameTime());
		UndeadState n = state(player).copy();
		for (int i = UndeadState.BODY; i < UndeadState.SEGS; i++) {
			if (!n.broken(i) || n.kind(i) != UndeadState.KIND_LAUNCHED) {
				n.setKind(i, UndeadState.KIND_SHATTER);
			}
			n.setTimes(i, n.broken(i) ? n.brokenAt(i) : now, 0L, 0L);
			if (i < UndeadState.PARTS) {
				n.partHp.set(i, 0f);
			}
		}
		if (n.broken(UndeadState.HEAD)) {
			// the head was already off (launched or burst): it re-forms first, then the rest (whatever auto-regrowth is set to)
			long start = now + 2;
			n.setTimes(UndeadState.HEAD, n.brokenAt(UndeadState.HEAD), start, start + UndeadConfig.FRAMES[UndeadState.HEAD]);
		}
		n.reformUntil = now + UndeadConfig.REFORM_GRACE;
		n.flags = 0;
		clearExtra(n);
		n.animId = UndeadState.ANIM_NONE;
		save(player, n);
		player.setHealth(player.getMaxHealth());
		player.clearFire();
		player.setAirSupply(player.getMaxAirSupply());
		player.removeEffect(MobEffects.POISON);
		player.removeEffect(MobEffects.WITHER);
		player.setDeltaMovement(Vec3.ZERO);
		player.hurtMarked = true;
		reconcile(player);
		for (int i = UndeadState.BODY; i < UndeadState.SEGS; i++) {
			UndeadArmour.partLost(player, i, UndeadState.KIND_SHATTER);
		}
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position().add(0, 1.0, 0);
		UndeadSfx.shatter(level, c); // v0.17.3 Undead sounds: the whole body bursts
		level.sendParticles(BLOOD, c.x, c.y, c.z, 6, 0.3, 0.5, 0.3, 0.0);
		say(player, "message.projecthero.undead.undead", ChatFormatting.DARK_RED);
	}

	/** Regrows every part on the spot (operator command, tests). */
	public static void restore(ServerPlayer player) {
		UndeadState n = state(player).copy();
		n.partHp = UndeadState.full();
		for (int i = 0; i < UndeadState.SEGS; i++) {
			n.setTimes(i, 0L, 0L, 0L);
		}
		n.kinds = 0;
		n.reformUntil = 0L;
		clearExtra(n);
		save(player, n);
		reconcile(player);
	}

	// ---------------------------------------------------------------- Shift+V: Toggle Regen

	/**
	 * v0.17.3 Shift+V (NBC's Toggle Regen): auto-regrowth on or off. Off, nothing starts growing back on its own (a segment
	 * already growing finishes); the Regen Menu grows segments a stage at a time. Healing his health and parts goes on either way.
	 */
	public static void toggleRegen(ServerPlayer player) {
		UndeadState n = state(player).copy();
		boolean off = !n.flag(UndeadState.FLAG_REGEN_OFF);
		n.setFlag(UndeadState.FLAG_REGEN_OFF, off);
		save(player, n);
		if (!off) {
			GROW_ALL.remove(player.getUUID());
		}
		LAST_MESSAGE.remove(player.getUUID());
		say(player, off ? "message.projecthero.undead.regen_auto_off" : "message.projecthero.undead.regen_auto_on", ChatFormatting.RED);
		player.level().playSound(null, player.getX(), player.getY() + 1, player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
				0.8f, off ? 0.6f : 1.2f);
	}

	static boolean regrowingAny(UndeadState s, long now) {
		for (int i = 0; i < UndeadState.SEGS; i++) {
			if (s.broken(i) && s.regrowStart(i) > 0L && s.regrowStart(i) <= now && now < s.regrowEnd(i)) {
				return true;
			}
		}
		return false;
	}

	/** v0.17.3: a lost part put straight back (the thrown head picked up): whole at once, nothing to regrow or pay. */
	public static void reattach(ServerPlayer player, int part) {
		UndeadState s = state(player);
		if (!s.hasPower || !s.broken(part)) {
			return;
		}
		UndeadState n = s.copy();
		n.setTimes(part, 0L, 0L, 0L);
		n.setKind(part, UndeadState.KIND_NONE);
		if (part < UndeadState.PARTS) {
			n.partHp.set(part, UndeadConfig.PART_HP[part]);
		}
		save(player, n);
		reconcile(player);
	}

	/** v0.17.3: auto-regrowth off (Shift+V, Toggle Regen) or back on. */
	public static void setRegenOff(ServerPlayer player, boolean off) {
		UndeadState n = state(player).copy();
		n.setFlag(UndeadState.FLAG_REGEN_OFF, off);
		save(player, n);
	}

	public static boolean regenOff(Player player) {
		UndeadState s = peek(player);
		return s != null && s.flag(UndeadState.FLAG_REGEN_OFF);
	}

	// ---------------------------------------------------------------- tap V: Reform

	/**
	 * v0.17.3 tap V (user: ripping off your arm is useless, V should be another utility): Reform -- he forces his body back
	 * together at once. Every part that is gone or regrowing is whole on the spot (the finger-shot bites and burnt hands and feet
	 * too), he heals {@link UndeadConfig#REFORM_HEAL}, and the fire and every harmful effect on him go. Free (v0.17.3: the
	 * regrowth is free now); works with auto-regrowth off (a deliberate choice). Not while the Bone
	 * Sword is out (V puts it away then, {@link UndeadBoneSword#handle}). {@link UndeadConfig#REFORM_COOLDOWN}.
	 */
	public static boolean reform(ServerPlayer player) {
		if (!canAct(player)) {
			return false;
		}
		UndeadState s = state(player);
		if (s.boneSword()) {
			return false;
		}
		String id = UndeadAbilities.REFORM;
		int cd = cooldownRemaining(player, id);
		if (cd > 0) {
			say(player, "message.projecthero.undead.cooldown", ChatFormatting.GRAY, Component.translatable("projecthero.undead.ability." + id),
					String.format(java.util.Locale.ROOT, "%.1f", cd / 20.0f));
			return false;
		}
		ServerLevel level = (ServerLevel) player.level();
		long now = Math.max(1L, level.getGameTime());
		UndeadState n = s.copy();
		long mask = 0L;
		for (int i = 0; i < UndeadState.SEGS; i++) {
			if (n.broken(i)) {
				mask |= 1L << UndeadState.limbOf(i);
				n.setTimes(i, 0L, 0L, 0L);
				n.setKind(i, UndeadState.KIND_NONE);
				if (i < UndeadState.PARTS) {
					n.partHp.set(i, UndeadConfig.PART_HP[i]);
				}
			}
		}
		n.setEx(UndeadState.X_EATEN_R, 0L);
		n.setEx(UndeadState.X_EATEN_L, 0L);
		if (!n.flag(UndeadState.FLAG_PROPELLING)) {
			n.setEx(UndeadState.X_CUT_REGROW, 0L);
			n.setEx(UndeadState.X_BURN_TICKS, 0L);
			n.setEx(UndeadState.X_BURN_SEG, 0L);
		}
		n.setEx(UndeadState.X_REFORMED, mask);
		n.abilityReadyAt.put(id, now + UndeadConfig.REFORM_COOLDOWN);
		n.animId = UndeadState.ANIM_REFORM;
		n.animStart = now;
		save(player, n);
		reconcile(player);
		// the launched parts still lying about burst, as when their replacement grows
		for (int i = 0; i < UndeadState.PARTS; i++) {
			if ((mask & (1L << i)) != 0L && s.kind(i) == UndeadState.KIND_LAUNCHED) {
				dropLaunched(player, i, false); // every one, the thrown ones too (the part is whole again now)
			}
		}
		player.heal(UndeadConfig.REFORM_HEAL);
		player.clearFire();
		for (net.minecraft.world.effect.MobEffectInstance e : java.util.List.copyOf(player.getActiveEffects())) {
			if (e.getEffect().value().getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL) {
				player.removeEffect(e.getEffect());
			}
		}
		if (player.getVehicle() instanceof Player) {
			player.stopRiding(); // carried as a head: he has a body again
		}
		UndeadSfx.reform(level, player.position().add(0, 1.0, 0), mask != 0L);
		return true;
	}

	// ---------------------------------------------------------------- stats

	public static void reconcile(ServerPlayer player) {
		UndeadState s = peek(player);
		// v0.17.3: the old reform root and one-leg Slowness are gone (the stage modifiers below replace them)
		PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, REFORM_ID);
		PowerToggles.clearModifier(player, Attributes.JUMP_STRENGTH, REFORM_JUMP_ID);
		UndeadBoneSword.attributes(player, s); // v0.17.3 Shift+V: the sword's damage / reach / speed while it is out
		if (s == null || !s.hasPower) {
			PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, MELEE_ID);
			PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, STAGE_ID);
			PowerToggles.clearModifier(player, Attributes.JUMP_STRENGTH, STAGE_JUMP_ID);
			return;
		}
		PowerToggles.modifier(player, Attributes.ATTACK_DAMAGE, MELEE_ID, victor(player) ? UndeadConfig.VICTOR_MELEE : UndeadConfig.BLOODY_ARTS_MELEE,
				AttributeModifier.Operation.ADD_VALUE);
		int stage = UndeadStages.stage(s, player.level().getGameTime());
		LAST_STAGE.put(player.getUUID(), stage);
		if (stage == UndeadStages.NORMAL) {
			PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, STAGE_ID);
			PowerToggles.clearModifier(player, Attributes.JUMP_STRENGTH, STAGE_JUMP_ID);
		} else {
			double speed = UndeadStages.SPEED[stage];
			if (stage == UndeadStages.CRAWL) {
				speed /= 0.3; // lying flat is vanilla's crawl, which already slows the input to the sneaking speed (0.3)
			}
			PowerToggles.modifier(player, Attributes.MOVEMENT_SPEED, STAGE_ID, speed - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			PowerToggles.modifier(player, Attributes.JUMP_STRENGTH, STAGE_JUMP_ID, UndeadStages.JUMP[stage] - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		}
		if (s.broken(UndeadState.HEAD)) {
			PowerToggles.effect(player, MobEffects.BLINDNESS, 0, false);
		} else {
			PowerToggles.clearEffect(player, MobEffects.BLINDNESS);
		}
	}

	/** Moves are allowed: has the power, alive, not just shattered. Says why not. */
	static boolean canAct(ServerPlayer player) {
		UndeadState s = state(player);
		if (!s.hasPower || !player.isAlive() || player.isSpectator()) {
			return false;
		}
		if (s.reformUntil > player.level().getGameTime()) {
			say(player, "message.projecthero.undead.reforming", ChatFormatting.GRAY);
			return false;
		}
		return true;
	}

	// ---------------------------------------------------------------- tick

	public static void tick(ServerPlayer player) {
		UndeadState s = player.getAttachedOrElse(ModAttachments.UNDEAD_STATE, null);
		if (s == null || !s.hasPower) {
			return;
		}
		long now = player.level().getGameTime();
		Long prev = LAST_TICK.put(player.getUUID(), now);
		if (prev != null && prev == now) {
			return;
		}
		UndeadState n = null;
		boolean whole = false;
		// v0.17.3 NBC's Regen_Loop: every segment that may grow (its parent far enough along, auto-regrowth on) starts at once
		// and grows one frame a tick to whole; nothing starts mid-flight (the hands and feet wait for the landing)
		// (and nothing while a launch is still in the air: the left-behind parts stay until he lands)
		boolean flying = s.flag(UndeadState.FLAG_PROPELLING) || UndeadLaunch.flying(player);
		boolean auto = !s.flag(UndeadState.FLAG_REGEN_OFF) || GROW_ALL.contains(player.getUUID());
		for (int i = 0; i < UndeadState.SEGS; i++) {
			UndeadState cur = n != null ? n : s;
			if (!cur.broken(i)) {
				continue;
			}
			long st = cur.regrowStart(i), end = cur.regrowEnd(i);
			if (st <= 0L) {
				if (flying || !auto || !mayStart(cur, i, now)) {
					continue;
				}
				if (n == null) {
					n = s.copy();
				}
				n.setTimes(i, cur.brokenAt(i), now, now + UndeadConfig.FRAMES[i]);
				startedRegrowing(player, i, cur.kind(i));
			} else if (auto && end - st < UndeadConfig.FRAMES[i]) {
				// auto-regrowth on (or Regenerate Whole Body): a segment the menu held at a stage grows on to whole, from where it is
				if (n == null) {
					n = s.copy();
				}
				long from = now - Math.min(now - st, end - st);
				n.setTimes(i, cur.brokenAt(i), from, from + UndeadConfig.FRAMES[i]);
			} else if (end - st >= UndeadConfig.FRAMES[i] && now >= end) {
				if (n == null) {
					n = s.copy();
				}
				n.setTimes(i, 0L, 0L, 0L);
				n.setKind(i, UndeadState.KIND_NONE);
				if (i < UndeadState.PARTS) {
					n.partHp.set(i, UndeadConfig.PART_HP[i]);
				}
				whole = true;
				// v0.17.3 Undead sounds: the wet snap of a finished part is client-side (UndeadSounds.regrowth -> undead.regrow.finish)
			}
		}
		if (GROW_ALL.contains(player.getUUID()) && !(n != null ? n : s).anyBroken()) {
			GROW_ALL.remove(player.getUUID());
		}
		UndeadState cur = n != null ? n : s;
		// the bites the finger shots took		UndeadState cur = n != null ? n : s;
		// the bites the finger shots took have grown back: forget them
		for (int arm = UndeadState.RIGHT_ARM; arm <= UndeadState.LEFT_ARM; arm++) {
			if (cur.eaten0(arm) > 0f && cur.eatenRegrow(arm, now) >= 1f) {
				if (n == null) {
					n = s.copy();
					cur = n;
				}
				n.setEx(arm == UndeadState.RIGHT_ARM ? UndeadState.X_EATEN_R : UndeadState.X_EATEN_L, 0L);
			}
		}
		// (an old save's flight-cut regrowth: the hands and feet are segments of their own now)
		if (!flying && cur.ex(UndeadState.X_CUT_REGROW) > 0L) {
			if (n == null) {
				n = s.copy();
				cur = n;
			}
			n.setEx(UndeadState.X_CUT_REGROW, 0L);
			n.setEx(UndeadState.X_BURN_TICKS, 0L);
			n.setEx(UndeadState.X_BURN_SEG, 0L);
		}
		// parts knit back between fights, and his health comes back (v0.17.3: one rate now, free)
		if (now % 10 == 0 && now - cur.lastHitAt > UndeadConfig.KNIT_DELAY) {
			for (int i = 0; i < UndeadState.PARTS; i++) {
				if (!cur.broken(i) && cur.hp(i) < UndeadConfig.PART_HP[i]) {
					if (n == null) {
						n = s.copy();
						cur = n;
					}
					n.partHp.set(i, Math.min(UndeadConfig.PART_HP[i], n.hp(i) + UndeadConfig.KNIT_PER_SECOND * 0.5f));
				}
			}
		}
		if (now % 10 == 0 && now - cur.lastHitAt > UndeadConfig.HEAL_DELAY && player.getHealth() < player.getMaxHealth() && player.isAlive()) {
			player.heal(Math.min(UndeadConfig.HEAL_PER_SECOND * 0.5f, player.getMaxHealth() - player.getHealth()));
		}
		// v0.17.3 (tester: "A way people can naturally pick up the head"): carried by another player while only a head; once his
		// body is back he climbs down
		if (player.getVehicle() instanceof Player && !cur.broken(UndeadState.BODY)) {
			player.stopRiding();
		}
		if (cur.reformUntil > 0L && now >= cur.reformUntil) {
			if (n == null) {
				n = s.copy();
				cur = n;
			}
			n.reformUntil = 0L;
		}
		if (cur.victorUntil > 0L && (now >= cur.victorUntil || !UndeadConfig.VICTOR_ENABLED)) {
			if (n == null) {
				n = s.copy();
				cur = n;
			}
			n.victorUntil = 0L;
			n.animId = UndeadState.ANIM_CARD_BACK;
			n.animStart = now;
			n.setFlag(UndeadState.FLAG_DEAD_ROAD, false);
			player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.ARMOR_EQUIP_ELYTRA.value(), SoundSource.PLAYERS, 0.8f, 1.4f);
			say(player, "message.projecthero.undead.card_back", ChatFormatting.GRAY);
		}
		if (n != null) {
			save(player, n);
		}
		Integer lastStage = LAST_STAGE.get(player.getUUID());
		boolean stageChanged = lastStage == null || lastStage != UndeadStages.stage(cur, now);
		if (whole || stageChanged || player.tickCount % 10 == 0 || n != null) {
			reconcile(player);
		}
		if (victor(player) && player.tickCount % 4 == 0) {
			((ServerLevel) player.level()).sendParticles(BLOOD, player.getX(), player.getEyeY() + 0.2, player.getZ(), 1, 0.25, 0.1, 0.25, 0.0);
		}
		UndeadAbilityManager.tickHold(player);
		UndeadAbilities.tick(player);
		UndeadFlight.tick(player);
		UndeadArmour.tick(player);
	}

	/**
	 * NBC's order: a broken segment may start growing once the one it grows from ({@link UndeadState#parentOf}) has enough frames
	 * ({@link UndeadConfig#BODY_NEEDS_HEAD} ...), or is whole. Not the arm (and hand) that is the Bone Sword in his hand, nor an arm
	 * used up by finger shots until he has stopped shooting for {@link UndeadConfig#EATEN_REGROW_WAIT}.
	 */
	public static boolean mayStart(UndeadState s, int seg, long now) {
		int sword = s.boneSwordArm();
		if (sword >= 0 && UndeadState.limbOf(seg) == sword) {
			return false; // v0.17.3: it grows back once the sword is put away
		}
		if (s.kind(seg) == UndeadState.KIND_SPENT && now < s.ex(UndeadState.X_LAST_SHOT) + UndeadConfig.EATEN_REGROW_WAIT) {
			return false;
		}
		int parent = UndeadState.parentOf(seg);
		return parent < 0 || s.frame(parent, now) >= needs(seg);
	}

	/** The frames {@code seg}'s parent must have before it starts (NBC's numbers). */
	public static int needs(int seg) {
		return switch (seg) {
			case UndeadState.HEAD -> 0;
			case UndeadState.BODY -> UndeadConfig.BODY_NEEDS_HEAD;
			case UndeadState.RIGHT_ARM, UndeadState.LEFT_ARM, UndeadState.RIGHT_LEG, UndeadState.LEFT_LEG -> UndeadConfig.LIMB_NEEDS_BODY;
			case UndeadState.RIGHT_HAND, UndeadState.LEFT_HAND -> UndeadConfig.HAND_NEEDS_ARM;
			default -> UndeadConfig.FOOT_NEEDS_LEG;
		};
	}

	/** Regen Menu: Regenerate Whole Body -- everything grows back now, auto-regrowth on or off. */
	static void growAll(ServerPlayer player) {
		GROW_ALL.add(player.getUUID());
	}

	static void startedRegrowing(ServerPlayer player, int part, int kind) {
		// v0.17.3 Undead sounds: the regrowth itself is heard client-side (UndeadSounds: bone, muscle, skin beds)
		if (kind == UndeadState.KIND_LAUNCHED) {
			// a piece left standing where he was (the Leg / Head Launch) bursts as its replacement grows. NOT a thrown one (the
			// stretch punch's arm, the Vortex Bullet, the Parts Bullet Head): since the NBC regrowth a launched arm or head starts
			// growing back the very next tick, so bursting it here deleted the arm a tick after it left the shoulder -- nothing
			// flew and nothing was hit. A thrown piece bursts when the part is whole again (UndeadPartEntity#tick).
			dropLaunched(player, part, true);
		}
	}

	/** The launched pieces of {@code part} lying about burst ({@code leftBehindOnly}: only the X launches' standing pieces). */
	static void dropLaunched(ServerPlayer player, int part, boolean leftBehindOnly) {
		ServerLevel level = (ServerLevel) player.level();
		for (UndeadPartEntity e : level.getEntitiesOfClass(UndeadPartEntity.class, new AABB(player.blockPosition()).inflate(256.0),
				e -> e.ownerIs(player) && e.bodyPart() == part && (!leftBehindOnly || e.isLeftBehind()))) {
			e.discard();
		}
	}
}
