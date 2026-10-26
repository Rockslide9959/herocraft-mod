package com.projecthero.mod.speedforce;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.flash.FlashSuit;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.hero.power.p04.SuperSpeedTimeSlow;
import com.projecthero.mod.speedforce.data.SpeedForceState;
import com.projecthero.mod.speedforce.network.SpeedForceFxPayload;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * The single server-side API for the <b>Speed Force Speedster</b> (the DC Flash), the v0.17.1 Hero-Tier upgrade of the
 * plain Speedster (the Super Speed mutation, which stays exactly as it is). Nothing else touches {@link SpeedForceState};
 * every change re-saves through {@link ServerPlayer#setAttached}. The moves live in {@link SpeedForceAbilities}, the
 * awakening in {@link SpeedForceUpgrade}, the damage rules in {@link SpeedForceDamage}, the numbers in
 * {@link SpeedForceConfig}.
 *
 * <h2>Momentum</h2>
 * Slow off the line, then faster the longer the run stays unbroken: 0 to 100% (3 s to 25%, 10 s to 60%, 20 s to 100%).
 * The sprint speed follows it ({@link SpeedForceConfig#sprintBps}): 17 to 167 blocks a second, and 25 to 250 in the full
 * Flash Suit (+50%). Stopping loses it in 1.5 s, walking bleeds it away, sharp turns cost a little, hits a chunk and
 * walls most of it. It is measured here, on the server, from where the client says the runner is.
 *
 * <h2>The Speed Force</h2>
 * 0..100, one point for every 20 blocks run (and a surge from lightning or Charged Copper Plates); the moves spend it.
 */
public final class SpeedForceSpeedster {
	public static final String KEY = "speed_force_speedster";

	public static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1.0f, 0.78f, 0.18f), 1.2f);
	public static final DustParticleOptions GOLD_BIG = new DustParticleOptions(new Vector3f(1.0f, 0.85f, 0.3f), 2.2f);
	public static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(0.85f, 0.08f, 0.06f), 1.2f);

	private static final ResourceLocation SPEED_ID = ProjectHeroMod.id("speed_force_speed");
	private static final ResourceLocation STEP_ID = ProjectHeroMod.id("speed_force_step");
	private static final ResourceLocation ATTACK_SPEED_ID = ProjectHeroMod.id("speed_force_attack_speed");
	private static final ResourceLocation SWIM_ID = ProjectHeroMod.id("speed_force_swim");
	private static final ResourceLocation MINE_ID = ProjectHeroMod.id("speed_force_mine");
	/** v0.17.1 (user): drained after the Speed Force -- 50% slower, 50% weaker. */
	private static final ResourceLocation DRAINED_SPEED_ID = ProjectHeroMod.id("speed_force_drained_speed");
	private static final ResourceLocation DRAINED_DAMAGE_ID = ProjectHeroMod.id("speed_force_drained_damage");

	/** Vanilla flat-ground blocks a second per point of MOVEMENT_SPEED (see {@code SuperSpeedHandlers#BLOCKS_PER_SECOND_PER_SPEED}). */
	public static final double BLOCKS_PER_SECOND_PER_SPEED = 0.98 / (1.0 - 0.6 * 0.91) * 20.0;

	/** The run as the server sees it, per player. */
	static final class Motion {
		Vec3 last;
		double raw1;
		double raw2;
		double ema;
		Vec3 dir;
		double distance;
		boolean sonicArmed = true;
		/** v0.17.1: the HORIZONTAL speed, smoothed (the sound barrier reads only this) and how long it has stayed past 100. */
		double hEma;
		int sonicTicks;
		/** Ticks to ignore the speed readings (a zip / teleport just happened). */
		int grace;
		long copperReady;
		/** v0.17.1: the game time the drain last counted down. */
		long drainTick = -1L;
	}

	/** Where a runner was, a tick at a time (Rewind, the Time Remnant). */
	record Snap(Vec3 pos, float yRot, float xRot, float health) {
	}

	private static final Map<UUID, Long> LAST_MESSAGE = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> LAST_TICK = new ConcurrentHashMap<>();
	static final Map<UUID, Motion> MOTION = new ConcurrentHashMap<>();
	static final Map<UUID, Deque<Snap>> HISTORY = new ConcurrentHashMap<>();

	private SpeedForceSpeedster() {
	}

	public static void clearSessionState() {
		LAST_MESSAGE.clear();
		MOVE_HOLD.clear();
		LAST_TICK.clear();
		MOTION.clear();
		HISTORY.clear();
		SpeedForceAbilities.clearSessionState();
		SpeedForceAbilityManager.clearSessionState();
		SpeedForceUpgrade.clearSessionState();
		SpeedForceDamage.clearSessionState();
		SpeedForceRealm.clearSessionState(); // v0.17.1: the Speed Force trips (the memory road's doorways and echoes)
		SpeedForceMemory.clearSessionState(); // v0.17.1: the echoes' recorded runs
		BlackFlash.clearSessionState(); // v0.17.1: the Black Flash fights
	}

	// ---------------------------------------------------------------- state

	public static SpeedForceState state(ServerPlayer player) {
		return player.getAttachedOrCreate(SpeedForceAttachments.STATE);
	}

	static void save(ServerPlayer player, SpeedForceState state) {
		player.setAttached(SpeedForceAttachments.STATE, state);
	}

	public static SpeedForceState peek(Player player) {
		return player.getAttachedOrElse(SpeedForceAttachments.STATE, null);
	}

	public static boolean hasPower(Player player) {
		SpeedForceState s = peek(player);
		return s != null && s.hasPower && powerLost(player) <= 0;
	}

	/** v0.17.1 (user): ticks left without the Speed Force after the Black Flash killed them (client-safe; 0 = none). */
	public static int powerLost(Player player) {
		Integer t = player.getAttached(SpeedForceAttachments.POWER_LOST);
		return t == null ? 0 : t;
	}

	/**
	 * v0.17.1 (user: "if the black flash kills the player they lose their powers for 20 minutes"): the Speed Force goes
	 * quiet -- no Speed Mode, no momentum, no moves, no passives -- for {@code ticks} (the power itself is kept).
	 */
	public static void losePower(ServerPlayer player, int ticks) {
		if (SuperSpeedTimeSlow.isCasting(player)) {
			SuperSpeedTimeSlow.end(player, false);
		}
		SpeedForceState n = state(player).copy();
		n.speedMode = false;
		n.momentum = 0f;
		n.chargeStart = 0L;
		n.phasing = false;
		n.flashLeft = 0;
		n.tornadoLeft = 0;
		n.throwLeft = 0;
		n.carryId = 0;
		n.animId = SpeedForceState.ANIM_NONE;
		n.holdAnim = false;
		n.drained = 0;
		save(player, n);
		clearModifiers(player);
		PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, DRAINED_SPEED_ID);
		PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, DRAINED_DAMAGE_ID);
		player.setAttached(SpeedForceAttachments.POWER_LOST, Math.max(1, ticks));
		player.displayClientMessage(Component.translatable("message.projecthero.speed_force_speedster.power_lost", ticks / 1200)
				.withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), false);
	}

	/** One tick without the Speed Force: counted a second at a time (rare syncs), a reminder now and then, then it returns. */
	private static void tickPowerLost(ServerPlayer player) {
		if (player.tickCount % 20 != 0) {
			return;
		}
		int left = powerLost(player) - 20;
		if (left <= 0) {
			player.removeAttached(SpeedForceAttachments.POWER_LOST);
			player.displayClientMessage(Component.translatable("message.projecthero.speed_force_speedster.power_back")
					.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
			return;
		}
		player.setAttached(SpeedForceAttachments.POWER_LOST, left);
		clearModifiers(player);
		MobEffectInstance regen = player.getEffect(MobEffects.REGENERATION);
		if (regen != null && regen.isAmbient() && !regen.isVisible()) {
			player.removeEffect(MobEffects.REGENERATION); // the Speed Force's own healing goes with it
		}
		if (left % (60 * 20) < 20) {
			int min = (left + 1199) / 1200;
			player.displayClientMessage(Component.translatable("message.projecthero.speed_force_speedster.power_lost_left", min)
					.withStyle(ChatFormatting.DARK_RED), true);
		}
	}

	public static float momentum(Player player) {
		SpeedForceState s = peek(player);
		return s == null || !s.hasPower ? 0f : s.momentum;
	}

	/** v0.17.1 (user): C -- Speed Mode is on (client-safe). Off, no momentum, trail, lightning, skate or step assist. */
	public static boolean speedActive(Player player) {
		SpeedForceState s = peek(player);
		return s != null && s.hasPower && s.speedMode;
	}

	/** Hold C: the charge stance started at this game time, or 0 (client-safe). */
	public static long chargeStart(Player player) {
		SpeedForceState s = peek(player);
		return s == null || !s.hasPower ? 0L : s.chargeStart;
	}

	/** v0.17.1 (user): ticks left of the drain after leaving the Speed Force (client-safe; 0 = none). */
	public static int drained(Player player) {
		SpeedForceState s = peek(player);
		return s == null || !s.hasPower ? 0 : s.drained;
	}

	/**
	 * The Speed Force Speedster's blows: v0.17.1 (user) they scale with the momentum -- x1.0 at 0%, x2.0 at 100%, linear
	 * ({@link SpeedForceConfig#damageMultiplier}) -- halved while drained after the Speed Force. Anyone else: 1.
	 */
	public static float damageScale(Player player) {
		if (!hasPower(player)) {
			return 1.0f;
		}
		float drain = drained(player) > 0 ? (float) (1.0 + SpeedForceConfig.DRAINED_DAMAGE) : 1.0f;
		return SpeedForceConfig.damageMultiplier(momentum(player)) * drain;
	}

	/** v0.17.1: starts (or restarts) the 2-minute drain. */
	public static void drain(ServerPlayer player, int ticks) {
		SpeedForceState n = state(player).copy();
		n.drained = Math.max(0, Math.min(0xFFFF, ticks));
		save(player, n);
		applyDrain(player);
	}

	/** The drain's attribute half: -50% movement speed, -50% attack damage while it lasts. */
	static void applyDrain(ServerPlayer player) {
		if (state(player).drained > 0) {
			PowerToggles.modifier(player, Attributes.MOVEMENT_SPEED, DRAINED_SPEED_ID, SpeedForceConfig.DRAINED_SPEED,
					AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			PowerToggles.modifier(player, Attributes.ATTACK_DAMAGE, DRAINED_DAMAGE_ID, SpeedForceConfig.DRAINED_DAMAGE,
					AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		} else {
			PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, DRAINED_SPEED_ID);
			PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, DRAINED_DAMAGE_ID);
		}
	}

	/** One tick of the drain (the game's clock: it never outlasts its 2 minutes). Counted 10 at a time to keep syncs rare. */
	private static void tickDrain(ServerPlayer player) {
		SpeedForceState s = state(player);
		long now = player.level().getGameTime();
		Motion mo = MOTION.computeIfAbsent(player.getUUID(), k -> new Motion());
		if (s.drained > 0 && now % 10 == 0 && mo.drainTick != now) {
			mo.drainTick = now; // once per game tick (Flash Time's extra caster ticks share one)
			SpeedForceState n = s.copy();
			n.drained = Math.max(0, s.drained - 10);
			save(player, n);
			if (n.drained == 0) {
				player.displayClientMessage(Component.translatable("message.projecthero.speed_force_speedster.drain_over")
						.withStyle(ChatFormatting.GOLD), true);
			}
		}
		applyDrain(player);
	}

	/**
	 * v0.17.1: "running at max" for the Speed Force portal -- in Speed Mode at 100% momentum (or locked there) and still
	 * running flat out: sprinting (the client drops the sprint the moment the runner lets go, slows to a walk or hits a wall).
	 * Flash Time freezes the momentum, and the slowed world sends no new chunks, so a runner held at the edge of the loaded
	 * world by {@code SpeedForceLocalPlayerMixin} still counts as running while Flash Time holds; outside it the smoothed
	 * horizontal speed must be at least {@link SpeedForceConfig#PORTAL_RUN_FRACTION} of the top speed too.
	 */
	public static boolean atMaxRun(ServerPlayer player) {
		SpeedForceState s = state(player);
		if (!s.speedMode || !(s.momentum >= 0.999f || surging(player))) {
			return false;
		}
		Motion mo = MOTION.get(player.getUUID());
		double bps = mo == null ? 0.0 : mo.hEma * 20.0;
		if (!player.isSprinting()) {
			// too hungry to sprint (vanilla drops the sprint at 6 food -- at these speeds that comes in seconds): still running
			// counts, as long as they really are moving
			return player.getFoodData().getFoodLevel() <= 6 && bps >= SpeedForceConfig.WALK_BPS * 0.4;
		}
		// v0.17.1 (user): while Z charges the portal the momentum is held at 100% (runStep) and the client may ease the run
		// down while the chunks ahead load (SpeedForceLocalPlayerMixin): still sprinting and moving counts
		if (SuperSpeedTimeSlow.isCasting(player) || SpeedForceAbilities.portalCharging(player)) {
			return bps >= SpeedForceConfig.WALK_BPS * 0.4;
		}
		return bps >= SpeedForceConfig.PORTAL_RUN_FRACTION * SpeedForceConfig.topBps(suited(player));
	}

	public static float meter(Player player) {
		SpeedForceState s = peek(player);
		return s == null ? 0f : s.meter;
	}

	/**
	 * v0.17.1: the meter's ceiling -- {@link SpeedForceConfig#METER_MAX}, +{@link SpeedForceConfig#SHARD_DEVICE_METER_BONUS}
	 * while a Speed Force Tachyon Device is held in either hand (client-safe).
	 */
	public static float meterMax(Player player) {
		boolean held = SpeedForceMod.SPEED_FORCE_TACHYON_DEVICE != null
				&& (player.getMainHandItem().is(SpeedForceMod.SPEED_FORCE_TACHYON_DEVICE) || player.getOffhandItem().is(SpeedForceMod.SPEED_FORCE_TACHYON_DEVICE));
		return SpeedForceConfig.METER_MAX + (held ? SpeedForceConfig.SHARD_DEVICE_METER_BONUS : 0f);
	}

	/** C: Speed Force Surge is running (client-safe). */
	public static boolean surging(Player player) {
		SpeedForceState s = peek(player);
		return s != null && s.hasPower && s.surgeUntil > player.level().getGameTime();
	}

	/** Shift+C Phase is held (client-safe: the collision mixin reads it on both sides). */
	public static boolean phasing(Player player) {
		SpeedForceState s = peek(player);
		return s != null && s.hasPower && s.phasing;
	}

	public static int cooldownRemaining(Player player, String abilityId) {
		SpeedForceState s = peek(player);
		if (s == null) {
			return 0;
		}
		Long ready = s.abilityReadyAt.get(abilityId);
		return ready == null ? 0 : (int) Math.max(0L, ready - player.level().getGameTime());
	}

	/** Whether the full Flash Suit is on (+50% speed). */
	public static boolean suited(Player player) {
		return FlashSuit.wearsFull(player);
	}

	/** The sprint speed the runner's momentum is worth right now, blocks a second. */
	public static double targetBps(Player player) {
		return SpeedForceConfig.sprintBps(momentum(player), suited(player));
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

	// ---------------------------------------------------------------- momentum + the meter

	/** v0.17.1: player -> the tick count until which a move holds their momentum. */
	private static final Map<UUID, Integer> MOVE_HOLD = new java.util.concurrent.ConcurrentHashMap<>();

	/** A move key was used: the momentum holds while it plays out (and while any move key is held). */
	public static void markMove(ServerPlayer player) {
		MOVE_HOLD.put(player.getUUID(), player.tickCount + SpeedForceConfig.MOVE_MOMENTUM_HOLD_TICKS);
	}

	/** A move is being used right now (a key held, or one used in the last 2 s). */
	public static boolean inMove(ServerPlayer player) {
		if (SpeedForceAbilityManager.anyDown(player)) {
			markMove(player);
			return true;
		}
		Integer until = MOVE_HOLD.get(player.getUUID());
		if (until == null) {
			return false;
		}
		if (player.tickCount > until || until - player.tickCount > SpeedForceConfig.MOVE_MOMENTUM_HOLD_TICKS) {
			MOVE_HOLD.remove(player.getUUID());
			return false;
		}
		return true;
	}

	public static void setMomentum(ServerPlayer player, float value) {
		SpeedForceState s = state(player);
		float v = Math.max(0f, Math.min(1f, value));
		if (v != s.momentum) {
			SpeedForceState n = s.copy();
			n.momentum = v;
			save(player, n);
		}
	}

	/** Takes {@code amount} (0..1) of momentum away -- not while a Surge holds it at 100%. */
	public static void loseMomentum(ServerPlayer player, double amount) {
		if (surging(player)) {
			return;
		}
		setMomentum(player, (float) (state(player).momentum - amount));
	}

	public static boolean spendMeter(ServerPlayer player, float cost) {
		if (cost <= 0f) {
			return true;
		}
		SpeedForceState s = state(player);
		if (s.meter + 1.0e-3f < cost) {
			return false;
		}
		SpeedForceState n = s.copy();
		n.meter = Math.max(0f, Math.min(meterMax(player), s.meter) - cost);
		save(player, n);
		return true;
	}

	public static void addMeter(ServerPlayer player, float amount) {
		SpeedForceState s = state(player);
		if (amount > 0f && s.drained > 0) {
			return; // v0.17.1 (user): drained after the Speed Force -- no Speed Force gain
		}
		float v = Math.max(0f, Math.min(meterMax(player), s.meter + amount));
		if (v != s.meter) {
			SpeedForceState n = s.copy();
			n.meter = v;
			save(player, n);
		}
	}

	/** A zip / teleport is about to move the runner: don't read it as running (or a wall). */
	public static void graceMotion(ServerPlayer player, int ticks) {
		Motion mo = MOTION.computeIfAbsent(player.getUUID(), k -> new Motion());
		mo.grace = Math.max(mo.grace, ticks);
		mo.last = null;
	}

	// ---------------------------------------------------------------- grant / revoke

	/**
	 * The awakening (or the command): the Speed Force takes the player. Claims the one Primary slot (which strips the Super
	 * Speed mutation along with every other mutation), fills the Speed Force, and the lightning moment plays. False if
	 * already a Speed Force Speedster or the power is taken (unique powers).
	 */
	public static boolean grant(ServerPlayer player) {
		if (state(player).hasPower) {
			return false;
		}
		if (com.projecthero.mod.hero.UniquePowers.refuse(player, KEY)) {
			return false;
		}
		HeroTiers.claimPrimary(player, KEY);
		long now = player.level().getGameTime();
		SpeedForceState s = new SpeedForceState();
		s.hasPower = true;
		s.meter = SpeedForceConfig.METER_MAX;
		s.momentum = 0.25f;
		s.speedMode = true; // the awakening comes mid-Overdrive: you come out of it running
		// v0.17.1 (user: "when they first get the power they get a random colour"); Shift+N changes it
		s.colour = player.getRandom().nextInt(SpeedForceColours.COUNT);
		s.animId = SpeedForceState.ANIM_AWAKEN;
		s.animStart = now;
		save(player, s);
		awakenFx(player);
		player.connection.send(new ClientboundSetTitlesAnimationPacket(6, 70, 20));
		player.connection.send(new ClientboundSetSubtitleTextPacket(
				Component.translatable("message.projecthero.speed_force_speedster.subtitle").withStyle(ChatFormatting.YELLOW)));
		player.connection.send(new ClientboundSetTitleTextPacket(
				Component.translatable("message.projecthero.speed_force_speedster.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
		player.displayClientMessage(Component.translatable("message.projecthero.speed_force_speedster.acquired")
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		player.displayClientMessage(Component.translatable("message.projecthero.speed_force_speedster.acquired_hint")
				.withStyle(ChatFormatting.GRAY), false);
		return true;
	}

	/** The Speed Force takes him: a white flash, a column of gold lightning, thunder and a ring of sparks. */
	private static void awakenFx(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position().add(0, 1.0, 0);
		level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 3, 0.2, 0.2, 0.2, 0);
		level.sendParticles(GOLD_BIG, c.x, c.y, c.z, 80, 0.7, 1.3, 0.7, 0.0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 120, 0.8, 1.4, 0.8, 0.6);
		for (int i = 0; i < 40; i++) {
			level.sendParticles(GOLD, c.x, c.y + i * 0.6, c.z, 2, 0.2, 0.1, 0.2, 0.0);
		}
		for (int k = 0; k < 36; k++) {
			double a = k * Math.PI * 2 / 36;
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x + Math.cos(a) * 2.5, player.getY() + 0.1, c.z + Math.sin(a) * 2.5, 2,
					0.05, 0.05, 0.05, 0.1);
		}
		level.playSound(null, c.x, c.y, c.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.2f, 1.3f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.2f, 1.0f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0f, 1.9f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.7f, 1.8f);
		fx(player, SpeedForceFxPayload.AWAKEN, c, c.add(0, 30, 0));
		player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false, false));
	}

	public static void revoke(ServerPlayer player) {
		SpeedForceAbilities.clear(player);
		save(player, new SpeedForceState());
		clearModifiers(player);
		MobEffectInstance regen = player.getEffect(MobEffects.REGENERATION);
		if (regen != null && regen.getAmplifier() == SpeedForceConfig.REGEN_AMPLIFIER && !regen.isVisible()) {
			player.removeEffect(MobEffects.REGENERATION);
		}
		forget(player.getUUID());
	}

	private static void clearModifiers(ServerPlayer player) {
		PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, DRAINED_SPEED_ID);
		PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, DRAINED_DAMAGE_ID);
		PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID);
		PowerToggles.clearModifier(player, Attributes.STEP_HEIGHT, STEP_ID);
		PowerToggles.clearModifier(player, Attributes.WATER_MOVEMENT_EFFICIENCY, SWIM_ID);
		PowerToggles.clearModifier(player, Attributes.BLOCK_BREAK_SPEED, MINE_ID);
		PowerToggles.clearModifier(player, Attributes.ATTACK_SPEED, ATTACK_SPEED_ID);
	}

	private static void forget(UUID id) {
		LAST_MESSAGE.remove(id);
		LAST_TICK.remove(id);
		MOTION.remove(id);
		HISTORY.remove(id);
		SpeedForceMemory.forgetPlayer(id);
		SpeedForceAbilityManager.forget(id);
		SpeedForceAbilities.forget(id);
	}

	static boolean canAct(ServerPlayer player) {
		SpeedForceState s = state(player);
		return s.hasPower && player.isAlive() && !player.isSpectator();
	}

	// ---------------------------------------------------------------- fx

	/** Sends one Speed Force visual to the runner and everyone tracking them. */
	public static void fx(ServerPlayer player, int kind, Vec3 from, Vec3 to) {
		SpeedForceFxPayload payload = new SpeedForceFxPayload(player.getId(), kind, from.x, from.y, from.z, to.x, to.y, to.z);
		if (ServerPlayNetworking.canSend(player, SpeedForceFxPayload.TYPE)) {
			ServerPlayNetworking.send(player, payload);
		}
		for (ServerPlayer viewer : PlayerLookup.tracking(player)) {
			if (viewer != player && ServerPlayNetworking.canSend(viewer, SpeedForceFxPayload.TYPE)) {
				ServerPlayNetworking.send(viewer, payload);
			}
		}
	}

	static void setAnim(ServerPlayer player, int animId) {
		SpeedForceState n = state(player).copy();
		n.animId = animId;
		n.animStart = player.level().getGameTime();
		save(player, n);
	}

	// ---------------------------------------------------------------- tick

	/** Every tick, for every player (from {@code AbilityRouter.serverTick}). */
	public static void tick(ServerPlayer player) {
		SpeedForceState s = peek(player);
		if (s == null || !s.hasPower) {
			SpeedForceUpgrade.tickCandidate(player);
			return;
		}
		if (powerLost(player) > 0) {
			tickPowerLost(player);
			return;
		}
		// once per player tick -- the tick count (not the game time), so Flash Time's extra caster ticks run too
		Integer prev = LAST_TICK.put(player.getUUID(), player.tickCount);
		if (prev != null && prev == player.tickCount) {
			return;
		}
		tickMotion(player);
		tickPassives(player);
		applySpeed(player);
		tickDrain(player);
		recordHistory(player);
		SpeedForceAbilities.tick(player, player.level().getGameTime());
		SpeedForceRealm.tick(player); // v0.17.1: inside the Speed Force
		SpeedForceMemory.record(player); // v0.17.1: the runs the echoes replay
	}

	/** Flash Time: the runner's own clocks run at their pace while the world's crawls (one tick pulled in per extra tick). */
	public static void advanceClocks(ServerPlayer player, int ticks) {
		SpeedForceState s = peek(player);
		if (s == null || !s.hasPower) {
			return;
		}
		long now = player.level().getGameTime();
		SpeedForceState n = s.copy();
		boolean changed = false;
		for (Map.Entry<String, Long> e : n.abilityReadyAt.entrySet()) {
			if (e.getValue() > now) {
				e.setValue(Math.max(now, e.getValue() - ticks));
				changed = true;
			}
		}
		if (n.surgeUntil > now) {
			n.surgeUntil = Math.max(now, n.surgeUntil - ticks);
			changed = true;
		}
		if (n.remnantUntil > now) {
			n.remnantUntil = Math.max(now, n.remnantUntil - ticks);
			changed = true;
		}
		if (n.animStart != 0L) {
			n.animStart -= ticks;
			changed = true;
		}
		if (changed) {
			save(player, n);
		}
	}

	/**
	 * The run as the server sees it: how far the runner moved since last tick (packets arrive unevenly, so a short average
	 * too), and from that the momentum -- built while flat out, held while slowed by terrain, lost stopping / walking /
	 * turning hard / hitting a wall -- the Speed Force from the distance, and the sonic boom.
	 */
	static void tickMotion(ServerPlayer player) {
		Motion mo = MOTION.computeIfAbsent(player.getUUID(), k -> new Motion());
		Vec3 now = player.position();
		Vec3 last = mo.last;
		mo.last = now;
		if (mo.grace > 0) {
			mo.grace--;
			return;
		}
		if (last == null) {
			return;
		}
		// v0.17.1 (user): Speed Mode off -- an ordinary runner: no momentum to build or keep, nothing drawn
		SpeedForceState cur = state(player);
		if (!cur.speedMode) {
			mo.ema = 0.0;
			mo.raw1 = 0.0;
			mo.raw2 = 0.0;
			if (cur.momentum != 0f) {
				SpeedForceState n = cur.copy();
				n.momentum = 0f;
				save(player, n);
			}
			return;
		}
		runStep(player, mo, last, now);
	}

	/**
	 * Gametests: one tick of the run reading as if the runner had just moved by {@code delta} (mock players never move on
	 * their own; the cage is 8 blocks wide).
	 */
	public static void stepRun(ServerPlayer player, Vec3 delta) {
		Motion mo = MOTION.computeIfAbsent(player.getUUID(), k -> new Motion());
		Vec3 now = player.position();
		mo.last = now;
		mo.grace = 0;
		runStep(player, mo, now.subtract(delta), now);
	}

	/** Gametests: forget the run's readings (a fresh start). */
	public static void resetRun(ServerPlayer player) {
		MOTION.remove(player.getUUID());
	}

	private static void runStep(ServerPlayer player, Motion mo, Vec3 last, Vec3 now) {
		Vec3 d = now.subtract(last);
		double raw = d.length();
		if (raw > 40.0) {
			mo.raw1 = 0.0; // a teleport, not running (and the readings start over)
			mo.raw2 = 0.0;
			mo.ema = 0.0;
			return;
		}
		double horiz = d.horizontalDistance();
		mo.ema = mo.ema * 0.5 + raw * 0.5;
		SpeedForceState s = state(player);
		boolean suit = suited(player);
		boolean wallRun = wallRunning(player, s.momentum);
		long gameTime = player.level().getGameTime();
		boolean surge = s.surgeUntil > gameTime;
		// v0.17.1 (user): a Z portal charge holds the momentum at 100% while it forms
		// v0.17.1 (user: "when the player builds up momentum and uses an ability such as speed sweep we can lose momentum"):
		// a move in use (and a moment after it) holds the momentum -- the moves throw the runner about and stop them dead,
		// which read as standing still or a wall
		boolean moving = inMove(player);
		boolean frozen = surge || SuperSpeedTimeSlow.isCasting(player) || s.phasing || s.tornadoLeft > 0
				|| s.flashLeft == SpeedForceState.Z_PORTAL || moving;
		double m = s.momentum;

		// a wall: flat out one tick, (almost) nothing the next two -- a released key only slows by half a tick -- AND a
		// solid block right in front along the run (a lag spike also reads as "stopped dead", but there is no wall)
		boolean slam = mo.raw2 > 1.5 && mo.raw1 < 0.15 * mo.raw2 && raw < 0.15 * mo.raw2 && !wallRun && !s.phasing
				&& !player.isPassenger() && !player.getAbilities().flying && mo.dir != null && blockedAhead(player, mo.dir);
		mo.raw2 = mo.raw1;
		mo.raw1 = raw;
		if (slam && !surge && !moving) {
			m *= SpeedForceConfig.WALL_KEEP;
			slamFx(player);
		} else if (!frozen) {
			double expected = SpeedForceConfig.sprintBps(m, suit) / 20.0;
			if (mo.ema < 0.15 && !wallRun) {
				m -= SpeedForceConfig.STOP_DECAY_PER_TICK;
			} else if (!SpeedsterPrefs.pushing(player) && !wallRun) {
				// v0.17.1 (user): momentum only BUILDS while Ctrl is held -- physically, so Toggle Sprint doesn't count; let
				// go and you cruise at that speed (it holds) -- at any momentum (user: "player should even be able to stop it
				// at 5%"), so only stopping loses it
				if (m < SpeedForceConfig.CRUISE_MOMENTUM) {
					m -= SpeedForceConfig.WALK_DECAY_PER_TICK;
				}
			} else if (!wallRun && mo.ema >= expected * SpeedForceConfig.BUILD_SPEED_FRACTION) {
				m += SpeedForceConfig.buildPerTick(m);
			}
			// a sharp turn costs a little
			if (horiz > 0.5 && mo.dir != null) {
				Vec3 dir = new Vec3(d.x, 0, d.z).normalize();
				double cos = Math.max(-1.0, Math.min(1.0, dir.dot(mo.dir)));
				double angle = Math.toDegrees(Math.acos(cos));
				if (angle > 45.0) {
					m -= SpeedForceConfig.TURN_LOSS_MAX * (angle - 45.0) / 135.0;
				}
			}
		}
		if (surge) {
			m = 1.0;
		}
		if (horiz > 0.5) {
			mo.dir = new Vec3(d.x, 0, d.z).normalize();
		}
		m = Math.max(0.0, Math.min(1.0, m));

		// the Speed Force: one point every 20 blocks run
		mo.distance += raw;
		float meterGain = 0f;
		while (mo.distance >= SpeedForceConfig.BLOCKS_PER_POINT) {
			mo.distance -= SpeedForceConfig.BLOCKS_PER_POINT;
			meterGain += 1f;
		}
		if (s.drained > 0) {
			meterGain = 0f; // v0.17.1 (user): drained after the Speed Force
		}
		float meter = Math.min(meterMax(player), s.meter + meterGain);
		if ((float) m != s.momentum || meter != s.meter) {
			SpeedForceState n = s.copy();
			n.momentum = (float) m;
			n.meter = meter;
			save(player, n);
		}

		// Charged Copper Plates run over: a jolt of Speed Force
		if (raw > 0.05 && gameTime >= mo.copperReady && SpeedForceUpgrade.crossesPlate(player, last, now)) {
			mo.copperReady = gameTime + SpeedForceConfig.COPPER_COOLDOWN;
			addMeter(player, SpeedForceConfig.COPPER_GAIN);
			((ServerLevel) player.level()).sendParticles(ParticleTypes.ELECTRIC_SPARK, now.x, now.y + 0.3, now.z, 20, 0.4, 0.2, 0.4, 0.2);
		}
		// breaking the sound barrier -- v0.17.1 (user): read from the smoothed HORIZONTAL speed held past 100 b/s for a few
		// ticks, so a Ground-Stick pull down a slope (a big vertical step in one tick) never sets it off; re-armed only well
		// under it
		mo.hEma = mo.hEma * 0.7 + horiz * 0.3;
		double bps = mo.hEma * 20.0;
		mo.sonicTicks = bps >= SpeedForceConfig.SONIC_BOOM_BPS ? mo.sonicTicks + 1 : 0;
		if (mo.sonicArmed && mo.sonicTicks >= SpeedForceConfig.SONIC_HOLD_TICKS) {
			mo.sonicArmed = false;
			sonicBoom(player);
		} else if (!mo.sonicArmed && bps < SpeedForceConfig.SONIC_REARM_BPS) {
			mo.sonicArmed = true;
		}
		// sparks at the heels for everyone (the gold lightning off the body is drawn by mod clients)
		if (raw > 0.4 && player.tickCount % 2 == 0 && player.level() instanceof ServerLevel sl) {
			Vec3 back = horiz > 1.0e-3 ? new Vec3(d.x, 0, d.z).normalize().scale(-1.2) : Vec3.ZERO;
			sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, now.x + back.x, now.y + 0.2, now.z + back.z, 1 + (int) (m * 4), 0.25, 0.15, 0.25, 0.1);
		}
		// non-Flash armour can't take this speed: 10 durability a second per piece above 60% momentum
		if (m >= SpeedForceConfig.ARMOUR_BURN_MOMENTUM && raw > 0.5 && player.tickCount % 20 == 0 && !player.getAbilities().instabuild) {
			com.projecthero.mod.hero.power.p04.SuperSpeedHandlers.wearArmour(player);
		}
	}

	/** A solid block right in front of the runner along {@code dir}, at body height (a step the assist climbs doesn't count). */
	static boolean blockedAhead(Player p, Vec3 dir) {
		AABB probe = p.getBoundingBox().move(dir.scale(0.4)).deflate(0.02, 0.0, 0.02);
		probe = new AABB(probe.minX, probe.minY + 0.6, probe.minZ, probe.maxX, probe.maxY - 0.05, probe.maxZ);
		return !p.level().noCollision(p, probe);
	}

	/** Whether the runner is set up to run up a wall: some momentum, looking steeply up, a solid block right in front. */
	public static boolean wallRunning(Player p, float momentum) {
		if (!speedActive(p) || momentum < SpeedForceConfig.WALL_RUN_MOMENTUM || p.getXRot() > -60.0f || p.isShiftKeyDown()
				|| p.getAbilities().flying || p.isPassenger() || phasing(p)) {
			return false;
		}
		Vec3 look = p.getLookAngle();
		Vec3 f = new Vec3(look.x, 0, look.z);
		if (f.lengthSqr() < 1.0e-6) {
			return false;
		}
		AABB probe = p.getBoundingBox().move(f.normalize().scale(0.3)).deflate(0.02, 0.1, 0.02);
		return !p.level().noCollision(p, probe);
	}

	private static void slamFx(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		level.sendParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0.2, 0.2, 0.2, 0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.4, 0.6, 0.4, 0.2);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.6f, 1.6f);
		say(player, "message.projecthero.speed_force_speedster.wall", ChatFormatting.GOLD);
	}

	/** Passing 100 b/s: a ring of air blasts out and knocks back whatever stands near (2 damage). */
	static void sonicBoom(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position().add(0, 1.0, 0);
		level.sendParticles(ParticleTypes.SONIC_BOOM, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.CLOUD, c.x, player.getY() + 0.2, c.z, 30, 1.5, 0.1, 1.5, 0.25);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.0f, 1.4f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8f, 1.8f);
		fx(player, SpeedForceFxPayload.BOOM, c, c);
		for (LivingEntity e : SpeedForceCombat.within(player, c, SpeedForceConfig.SONIC_RADIUS)) {
			SpeedForceCombat.strike(player, e, c, SpeedForceConfig.SONIC_DAMAGE, 1.0, 0.25);
		}
	}

	/** Regeneration II, never tires, no fall damage at speed ({@link SpeedForceDamage}). */
	private static void tickPassives(ServerPlayer player) {
		// v0.17.1 (user): Speed Mode brings the plain Speedster's passives -- Regeneration III (II otherwise), +30% swimming
		boolean speed = state(player).speedMode;
		// v0.17.1: the Speed Force Tachyon Device put away -- the meter back under its ordinary ceiling
		float cap = meterMax(player);
		if (state(player).meter > cap) {
			SpeedForceState c = state(player).copy();
			c.meter = cap;
			save(player, c);
		}
		int amp = speed ? SpeedForceConfig.SPEED_MODE_REGEN_AMPLIFIER : SpeedForceConfig.REGEN_AMPLIFIER;
		MobEffectInstance regen = player.getEffect(MobEffects.REGENERATION);
		if (regen != null && regen.getAmplifier() > amp && regen.isAmbient() && !speed) {
			player.removeEffect(MobEffects.REGENERATION); // Speed Mode's III steps back down to II
			regen = null;
		}
		if (regen == null || regen.getAmplifier() < amp
				|| (regen.getAmplifier() == amp && regen.getDuration() < SpeedForceConfig.REGEN_REFRESH)) {
			player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, SpeedForceConfig.REGEN_REFRESH * 2, amp, true, false, true));
		}
		if (speed) {
			PowerToggles.modifier(player, Attributes.WATER_MOVEMENT_EFFICIENCY, SWIM_ID, SpeedForceConfig.SPEED_MODE_SWIM_EFFICIENCY,
					AttributeModifier.Operation.ADD_VALUE);
		} else {
			PowerToggles.clearModifier(player, Attributes.WATER_MOVEMENT_EFFICIENCY, SWIM_ID);
		}
		if (wallRunning(player, state(player).momentum)) {
			player.resetFallDistance();
		}
	}

	/** The bonus (ADD_MULTIPLIED_BASE on a 0.1 base) that runs at {@code bps} on flat ground. */
	public static double bonusFor(double bps, boolean sprinting) {
		return bps / BLOCKS_PER_SECOND_PER_SPEED / (sprinting ? 1.3 : 1.0) / 0.1 - 1.0;
	}

	/** The single authority for the movement attributes: the sprint speed follows the momentum, the step assist too. */
	static void applySpeed(ServerPlayer player) {
		SpeedForceState s = state(player);
		PowerToggles.modifier(player, Attributes.ATTACK_SPEED, ATTACK_SPEED_ID, 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		if (!s.speedMode) {
			// v0.17.1 (user): Speed Mode off -- vanilla movement and step height
			PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID);
			PowerToggles.clearModifier(player, Attributes.STEP_HEIGHT, STEP_ID);
			PowerToggles.clearModifier(player, Attributes.BLOCK_BREAK_SPEED, MINE_ID);
			return;
		}
		// v0.17.1 (user): Speed Mode breaks blocks 75% faster
		PowerToggles.modifier(player, Attributes.BLOCK_BREAK_SPEED, MINE_ID, SpeedForceConfig.SPEED_MODE_MINING, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		boolean sprint = player.isSprinting();
		boolean pushing = SpeedsterPrefs.pushing(player);
		// v0.17.1 (user): letting go of Ctrl keeps the speed (a cruise), at any momentum
		// -- with Toggle Sprint on too (still "sprinting" with the key up; bonusFor divides the vanilla sprint 1.3x back out)
		boolean cruise = !pushing && s.momentum >= SpeedForceConfig.CRUISE_MOMENTUM;
		double bps = pushing || cruise ? SpeedForceConfig.sprintBps(s.momentum, suited(player)) : SpeedForceConfig.WALK_BPS;
		bps = Math.round(bps * 2.0) / 2.0; // half-block steps: the attribute (and its sync) changes rarely
		PowerToggles.modifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID, bonusFor(bps, sprint), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		double step = Math.max(SpeedForceConfig.STEP_SPEED_MODE,
				SpeedForceConfig.STEP_BASE + SpeedForceConfig.STEP_PER_MOMENTUM * Math.round(s.momentum * 10.0) / 10.0);
		PowerToggles.modifier(player, Attributes.STEP_HEIGHT, STEP_ID, step - 0.6, AttributeModifier.Operation.ADD_VALUE);
	}

	private static void recordHistory(ServerPlayer player) {
		Deque<Snap> q = HISTORY.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>());
		q.addLast(new Snap(player.position(), player.getYRot(), player.getXRot(), player.getHealth()));
		while (q.size() > SpeedForceConfig.REWIND_TICKS + 1) {
			q.removeFirst();
		}
	}

	/** Where the runner stood {@code ticksAgo} ticks ago (the oldest kept if the history is shorter), or null. */
	static Snap history(ServerPlayer player, int ticksAgo) {
		Deque<Snap> q = HISTORY.get(player.getUUID());
		if (q == null || q.isEmpty()) {
			return null;
		}
		int skip = Math.max(0, q.size() - 1 - ticksAgo);
		int i = 0;
		for (Snap s : q) {
			if (i++ == skip) {
				return s;
			}
		}
		return q.peekFirst();
	}

	// ---------------------------------------------------------------- lifecycle

	public static void onPlayerJoin(ServerPlayer player) {
		SpeedForceState s = state(player);
		if (!s.hasPower) {
			return;
		}
		long now = player.level().getGameTime();
		SpeedForceState n = s.copy();
		resetTransient(n);
		n.meter = Float.isNaN(n.meter) ? 0f : Math.max(0f, Math.min(meterMax(player), n.meter));
		n.momentum = 0f;
		long longest = Math.max(SpeedForceConfig.SURGE_COOLDOWN, SpeedForceConfig.PORTAL_COOLDOWN);
		long bound = Math.max(longest, com.projecthero.mod.hero.HeroConfig.get().scaledCooldown((int) longest));
		n.abilityReadyAt.entrySet().removeIf(e -> e.getValue() > now + bound + 20L);
		save(player, n);
		applyDrain(player);
		SpeedForceRealm.onJoin(player); // v0.17.1: logged out inside the Speed Force -- out, where the run had taken them
	}

	public static void onPlayerRespawn(ServerPlayer player) {
		SpeedForceState s = state(player);
		if (!s.hasPower) {
			return;
		}
		SpeedForceState n = s.copy();
		resetTransient(n);
		n.momentum = 0f;
		n.speedMode = false;
		n.meter = Math.max(n.meter, SpeedForceConfig.METER_MAX * 0.5f);
		n.abilityReadyAt.clear();
		save(player, n);
	}

	private static void resetTransient(SpeedForceState n) {
		n.surgeUntil = 0L;
		n.flashLeft = 0;
		n.flashTotal = 0;
		n.phasing = false;
		n.carryId = 0;
		n.tornadoLeft = 0;
		n.throwLeft = 0;
		n.remnantUntil = 0L;
		n.chargeStart = 0L;
		n.animId = SpeedForceState.ANIM_NONE;
		n.holdAnim = false;
	}

	/** Death / logout / dimension change: drop everything transient (the power and the Speed Force stay). */
	public static void clearTransient(ServerPlayer player) {
		SpeedForceAbilities.clear(player);
		forget(player.getUUID());
		SpeedForceState s = peek(player);
		if (s != null && s.hasPower) {
			SpeedForceState n = s.copy();
			resetTransient(n);
			n.momentum = 0f;
			save(player, n);
		}
	}

	/** Used by the HUD-less paths (gametests): flat-out run speed for this momentum, blocks a tick. */
	public static double targetPerTick(Player player) {
		return targetBps(player) / 20.0;
	}

	/** Small helper for the moves: a sound at the runner. */
	static void sound(ServerPlayer player, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
		AbilityHelpers.sound(player, sound, volume, pitch);
	}
}
