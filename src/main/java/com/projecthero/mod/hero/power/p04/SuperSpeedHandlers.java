package com.projecthero.mod.hero.power.p04;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.PowerPassives;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.data.ExperimentalState;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.Handlers;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.hero.revamp.batcha.BatchA;
import com.projecthero.mod.hero.visual.MutationVisuals;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Power 04 — Super Speed (v0.14.5 rework).
 *
 * <p>v0.14.7: the six keys plus N (no H -- H stays the power wheel). R Rapid Assault (4 punches of 5, each landing in full) / Shift+R Mach Punch, G Blitz /
 * Shift+G Speed Vortex, X Momentum Dash (along the full look vector) / Shift+X Speed Sweep, Z Time Slow (see
 * {@link SuperSpeedTimeSlow}), V Overdrive, C Speed Mode — and Shift+C Phase (hold C: walk through walls on your
 * own level; see {@link #startPhase}), N Speed Carry (carry anything overhead; no fall damage
 * while carried or for 3 s after). The new moves live in {@link SuperSpeedMoves}.
 *
 * <p>Passives: +30% walking / sprinting / swimming speed, eating / drinking 50% faster
 * ({@code SuperSpeedEatMixin}) and a permanent Regeneration II. In Speed Mode / Overdrive you also run on water and
 * run straight up walls (look up while running into one).
 *
 * <p>Speed Mode and Overdrive leave a trail of after-images behind a running speedster (yellow / red) -- the
 * {@code p04.trail} visual flag, rendered client-side.
 */
public final class SuperSpeedHandlers {
	public static final String KEY = "power_04_super_speed";

	/** Absolute game time until which Overdrive is running (0 = off). Persisted; read client-side too. */
	public static final String OVERDRIVE_UNTIL = "overdrive_until";
	private static final int OVERDRIVE_TICKS = 30 * 20;
	/** Countdown mirror of {@link #OVERDRIVE_UNTIL} purely so the ability HUD can draw an Overdrive bar. */
	public static final String OVERDRIVE_LEFT = "overdrive_ticks";
	/**
	 * v0.14.16: 1 once V has been let go since Overdrive started -- only a press after that ends it early (the OS key
	 * repeat of the press that started it arrives as more "pressed" packets; same rule as {@link #TS_RELEASED}).
	 */
	public static final String OD_RELEASED = "od_released";

	/** 1 while Shift+C Phase is held. Owner-synced, so the local player's collision mixin can read it. */
	public static final String PHASING = "phasing";
	/** Carried entity id (0 = nothing). v0.14.8: no time limit -- the carry lasts until you set it down. */
	private static final String CARRY_ID = "carry_id";

	/** v0.14.8 Time Slow: Z must be HELD this long to fire; releasing earlier cancels at no cost. */
	public static final int TS_CHARGE_TICKS = 5 * 20;
	/** Ticks Z has been held so far (the HUD charge bar); 0 when not charging. */
	public static final String TS_CHARGE = "ts_charge";
	/** 1 while Z is being held to charge Time Slow. */
	public static final String TS_CHARGING = "ts_charging";
	/** v0.14.9: 1 once Z has been let go since Time Slow fired -- only a press after that ends it early. */
	public static final String TS_RELEASED = "ts_released";
	/** v0.14.8: after a Time Slow the speedster is spent for this long (the HUD bar counts it down). */
	public static final int EXHAUST_TICKS = 30 * 20;
	public static final String EXHAUST = "exhaust_ticks";
	private static final net.minecraft.core.particles.DustParticleOptions CHARGE_SPARK =
			new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1.0f, 0.92f, 0.45f), 0.9f);
	/** After a carry ends the creature keeps its fall / suffocation immunity this long. */
	public static final int CARRY_GRACE_TICKS = 3 * 20;

	/** R: damage per punch, and punches per press. */
	public static final float PUNCH_DAMAGE = 5.0f;
	public static final int PUNCHES = 4;

	/** Passive: +30% movement (walk + sprint) and roughly +30% swim speed. */
	public static final double PASSIVE_SPEED_BONUS = 0.30;
	public static final ResourceLocation PASSIVE_SPEED = com.projecthero.mod.ProjectHeroMod.id("speed_passive_speed");
	private static final ResourceLocation PASSIVE_SWIM = com.projecthero.mod.ProjectHeroMod.id("speed_passive_swim");
	private static final double PASSIVE_SWIM_EFFICIENCY = 0.10;
	/** v0.14.6 passive step id -- v0.14.9: never applied any more, only cleared from older saves. */
	private static final ResourceLocation PASSIVE_STEP = com.projecthero.mod.ProjectHeroMod.id("speed_passive_step");
	/** v0.14.9: step assist only in the modes -- Speed Mode 3 blocks (base 0.6 + 2.4), Overdrive 10 (0.6 + 9.4). */
	public static final double SM_STEP_BONUS = 2.4;
	/**
	 * v0.14.11: the modes' sprint speed, as a bonus on base movement speed (with the passive +30% and sprinting's x1.3).
	 * Calibrated on the old values (4.7 ran ~32 blocks/s, 10.5 ~64): Speed Mode now ~40 blocks/s, Overdrive ~100.
	 */
	public static final double SPEED_MODE_BONUS = 6.15;
	/**
	 * v0.14.13: Speed Mode only runs flat out while you SPRINT (~40 blocks/s); plain walking in it is ~20 blocks/s, so it
	 * stays manageable in a fight. (0.1 x (1 + 0.3 + 3.54) = 0.484, half the sprinting 0.1 x 7.45 x 1.3.)
	 */
	public static final double SPEED_MODE_WALK_BONUS = 3.54;

	/**
	 * v0.14.16: blocks per second per point of the MOVEMENT_SPEED attribute, running on flat ground. Each ground tick a
	 * player gains {@code speed x 0.98} (the 0.98-long input vector; {@code 0.216 / friction^3} is exactly 1 on a
	 * 0.6-friction block) and keeps {@code 0.6 x 0.91 = 0.546} of last tick's velocity, so the steady state is
	 * {@code speed x 0.98 / (1 - 0.546)} = 2.159 x speed blocks/tick = 43.17 x speed blocks/s. Check: vanilla walking
	 * (0.1) gives 4.317 b/s and sprinting (0.13) 5.612 -- the published figures -- and the old calibration (bonus 4.7 ran
	 * ~32 b/s sprinting) fits too: 0.1 x (1 + 0.3 + 4.7) x 1.3 x 43.17 = 33.7.
	 */
	public static final double BLOCKS_PER_SECOND_PER_SPEED = 0.98 / (1.0 - 0.6 * 0.91) * 20.0;
	/** Vanilla base MOVEMENT_SPEED of a player, and sprinting's own x1.3 (an ADD_MULTIPLIED_TOTAL of +0.3). */
	public static final double BASE_MOVEMENT_SPEED = 0.1;
	public static final double SPRINT_MULTIPLIER = 1.3;

	/**
	 * v0.14.16: flat-ground blocks/s for a mode bonus (an ADD_MULTIPLIED_BASE on movement speed, stacked with the
	 * passive +30%): {@code 0.1 x (1 + 0.3 + bonus) [x 1.3 sprinting] x 43.17}. Not counting the Flash Suit's own x1.5.
	 */
	public static double blocksPerSecond(double bonus, boolean sprinting) {
		return BASE_MOVEMENT_SPEED * (1.0 + PASSIVE_SPEED_BONUS + bonus) * (sprinting ? SPRINT_MULTIPLIER : 1.0)
				* BLOCKS_PER_SECOND_PER_SPEED;
	}

	/** v0.14.16: the inverse of {@link #blocksPerSecond} -- the mode bonus that runs at {@code blocksPerSecond}. */
	public static double bonusFor(double blocksPerSecond, boolean sprinting) {
		return blocksPerSecond / BLOCKS_PER_SECOND_PER_SPEED / (sprinting ? SPRINT_MULTIPLIER : 1.0) / BASE_MOVEMENT_SPEED
				- 1.0 - PASSIVE_SPEED_BONUS;
	}

	/** v0.14.16: Overdrive's target speeds, walking and sprinting (was ~104 b/s sprinting, the same bonus walking). */
	public static final double OVERDRIVE_WALK_BPS = 32.0;
	public static final double OVERDRIVE_SPRINT_BPS = 100.0;
	/** v0.14.16: Overdrive walking -- 0.1 x (1.3 + 6.112) x 43.17 = 32 blocks/s. */
	public static final double OVERDRIVE_WALK_BONUS = bonusFor(OVERDRIVE_WALK_BPS, false);
	/** Overdrive sprinting -- v0.14.16: 0.1 x (1.3 + 16.519) x 1.3 x 43.17 = 100 blocks/s (was 17.3, ~104). */
	public static final double OVERDRIVE_BONUS = bonusFor(OVERDRIVE_SPRINT_BPS, true);
	public static final double OD_STEP_BONUS = 9.4;

	private static final ResourceLocation SM_SPEED = com.projecthero.mod.ProjectHeroMod.id("speed_mode_speed");
	private static final ResourceLocation SM_ATTACK = com.projecthero.mod.ProjectHeroMod.id("speed_mode_attack_speed");
	private static final ResourceLocation SM_STEP = com.projecthero.mod.ProjectHeroMod.id("speed_mode_step");
	private static final ResourceLocation SM_WATER = com.projecthero.mod.ProjectHeroMod.id("speed_mode_water");
	private static final ResourceLocation SM_FALL = com.projecthero.mod.ProjectHeroMod.id("speed_mode_fall");

	private static final ResourceLocation OD_SPEED = com.projecthero.mod.ProjectHeroMod.id("overdrive_speed");
	private static final ResourceLocation OD_ATTACK = com.projecthero.mod.ProjectHeroMod.id("overdrive_attack_speed");
	private static final ResourceLocation OD_DAMAGE = com.projecthero.mod.ProjectHeroMod.id("overdrive_attack_damage");
	private static final ResourceLocation OD_STEP = com.projecthero.mod.ProjectHeroMod.id("overdrive_step");
	private static final ResourceLocation OD_FALL = com.projecthero.mod.ProjectHeroMod.id("overdrive_fall");

	private static final net.minecraft.core.particles.DustParticleOptions OVERDRIVE_SPARK =
			new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1.0f, 0.25f, 0.2f), 0.8f);

	/** Horizontal blocks/tick above which Overdrive's burst effects fire (normal sprint is ~0.28). */
	private static final double RUNNING_SPEED = 0.3;

	/** Where each speedster was last tick (server), to tell running from standing without trusting velocity. */
	private static final Map<UUID, Vec3> LAST_POS = new HashMap<>();
	/** Entities set down from a carry -> game time their fall / suffocation immunity ends. */
	private static final Map<UUID, Long> CARRY_GRACE = new HashMap<>();
	/** Recent horizontal speed per speedster (blocks/tick, a decaying peak -- movement packets arrive unevenly). */
	private static final Map<UUID, Double> RECENT_SPEED = new HashMap<>();

	/** v0.14.13 passive: Regeneration III (amplifier 2; was II since v0.14.7). */
	public static final int REGEN_AMPLIFIER = 2;
	/** Water running: the soft splash plays at most this often (ticks), this loud. */
	public static final int WATER_SOUND_EVERY = 8;
	public static final float WATER_SOUND_VOLUME = 0.15f;

	private SuperSpeedHandlers() {
	}

	private static float res(ServerPlayer p, String name) {
		return BatchA.res(p, KEY, name);
	}

	private static void set(ServerPlayer p, String name, float v) {
		BatchA.set(p, KEY, name, v, 1e12f);
	}

	public static void register() {
		// R -- Rapid Assault: four punches of 5 on everything in front of you.
		// v0.14.7: R, G and X are HOLD slots so the router does not gate their Shift variants (Mach Punch, Speed
		// Vortex, Speed Sweep -- each on its own cooldown) on the main move's cooldown; both check their own.
		AbilityHandlers.register(KEY, "rapid_assault", Handlers.hold(ctx -> {
			if (ctx.player().isShiftKeyDown()) {
				SuperSpeedMoves.machPunch(ctx);
				return;
			}
			if (!ctx.cooldownReady()) {
				cooldownMessage(ctx);
				return;
			}
			rapidAssault(ctx);
		}, ctx -> { }));

		// G -- Blitz: zip to the enemy under the crosshair and hit it for 20. Shift+G -- Speed Vortex.
		AbilityHandlers.register(KEY, "blitz", Handlers.hold(ctx -> {
			if (ctx.player().isShiftKeyDown()) {
				SuperSpeedMoves.startVortex(ctx);
				return;
			}
			SuperSpeedMoves.blitz(ctx);
		}, ctx -> { }));

		// N -- Speed Carry (G before v0.14.7; Super Speed has no H move -- H stays the power wheel): snatch up the creature or player you are looking at and carry it
		// overhead. A second press sets it down in front of you. Carried things take no fall damage and never suffocate.
		AbilityHandlers.register(KEY, "speed_carry", Handlers.instantTicking(SuperSpeedHandlers::carryPress,
				ctx -> carryTick(ctx.player())));

		// X -- Momentum Dash: a burst along exactly where you are looking (up, down or level). Shift+X -- Speed Sweep.
		AbilityHandlers.register(KEY, "momentum_dash", Handlers.hold(ctx -> {
			ServerPlayer p = ctx.player();
			// v0.14.16: X during a sweep (Shift or not) goes to the sweep too -- a fresh press calls it off
			if (p.isShiftKeyDown() || SuperSpeedMoves.sweeping(p)) {
				SuperSpeedMoves.startSweep(ctx);
				return;
			}
			if (!ctx.cooldownReady()) {
				cooldownMessage(ctx);
				return;
			}
			float m = selfMult(overdriveMult(p));
			Vec3 dir = dashVelocity(p.getLookAngle(), p.onGround(), 1.7 * m);
			Vec3 from = p.position();
			AbilityHelpers.launchSelf(p, dir);
			afterimage(ctx.level(), from, p.getLookAngle(), 3.0 * m);
			BatchA.play(p, KEY, "dash_forward", 10);
			AbilityHelpers.sound(p, SoundEvents.BREEZE_SHOOT, 0.6f, 1.8f);
			ctx.triggerCooldown();
		}, ctx -> SuperSpeedMoves.sweepKeyReleased(ctx.player())));

		// Z -- Time Slow: 45 s (v0.14.7) of everything else at 5%. v0.14.8: HOLD Z for 5 s to charge it (it fires by
		// itself when full; letting go early cancels at no cost). Press Z during it to end it early; the 300 s cooldown
		// starts at the end, and so do 30 s of exhaustion.
		AbilityHandlers.register(KEY, "time_slow", new com.projecthero.mod.hero.AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (SuperSpeedTimeSlow.isCasting(p)) {
					// v0.14.9: only a fresh press ends it. Z is usually still held when the charge fires, and the OS
					// key-repeat arrives here as more "pressed" packets -- those used to end Time Slow at once.
					if (res(p, TS_RELEASED) > 0.5f) {
						SuperSpeedTimeSlow.end(p, true);
					}
					return;
				}
				if (timeSlowCharging(p)) {
					return;
				}
				if (!ctx.cooldownReady()) {
					cooldownMessage(ctx);
					return;
				}
				if (SuperSpeedTimeSlow.anyActive()) {
					p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.speed.time_taken"), true);
					return;
				}
				startTimeSlowCharge(p);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				if (timeSlowCharging(ctx.player())) {
					cancelTimeSlowCharge(ctx.player());
				} else if (SuperSpeedTimeSlow.isCasting(ctx.player())) {
					set(ctx.player(), TS_RELEASED, 1);
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				timeSlowChargeTick(ctx.player());
			}
		});

		// V -- Overdrive: 30 s of the fastest tier; your blows land twice as hard and a red trail follows you.
		// v0.14.16: V again while it runs ends it early (see interceptDispatch -- the INSTANT slot's cooldown gate would
		// swallow that second press before it got here).
		AbilityHandlers.register(KEY, "overdrive", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			set(p, OVERDRIVE_UNTIL, p.level().getGameTime() + OVERDRIVE_TICKS);
			set(p, OVERDRIVE_LEFT, OVERDRIVE_TICKS);
			set(p, OD_RELEASED, 0);
			reconcileSpeed(p);
			p.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, OVERDRIVE_TICKS, 2, false, true, true));
			BatchA.ring(ctx.level(), p.position().add(0, 0.2, 0), 0.6, ParticleTypes.ELECTRIC_SPARK, 30, 0.8);
			BatchA.play(p, KEY, "power_up", 20);
			AbilityHelpers.sound(p, SoundEvents.WARDEN_SONIC_BOOM, 1.4f, 1.5f);
			AbilityHelpers.sound(p, SoundEvents.GENERIC_EXPLODE, 1.2f, 1.3f);
			ctx.triggerCooldown();
		}));

		// C -- Speed Mode (toggle). Shift+C never reaches this: SuperSpeedAbilityRouterMixin routes it to Phase.
		AbilityHandlers.register(KEY, "speed_mode", Handlers.toggle(
				ctx -> {
					reconcileSpeed(ctx.player());
					BatchA.play(ctx.player(), KEY, "dash_forward", 10);
					AbilityHelpers.sound(ctx.player(), SoundEvents.BEACON_POWER_SELECT, 0.6f, 2.0f);
				},
				ctx -> reconcileSpeed(ctx.player()),
				ctx -> reconcileSpeed(ctx.player())));

		PowerPassives.register(KEY, (player, active) -> {
			if (active) {
				PowerToggles.modifier(player, Attributes.MOVEMENT_SPEED, PASSIVE_SPEED, PASSIVE_SPEED_BONUS,
						AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
				PowerToggles.modifier(player, Attributes.WATER_MOVEMENT_EFFICIENCY, PASSIVE_SWIM, PASSIVE_SWIM_EFFICIENCY,
						AttributeModifier.Operation.ADD_VALUE);
				PowerToggles.clearModifier(player, Attributes.STEP_HEIGHT, PASSIVE_STEP); // v0.14.9: no step assist outside the modes
				applyRegen(player);
				// join / respawn: a phase saved mid-hold (C is not held any more) must not linger
				if (phasing(player) && !player.noPhysics) {
					endPhase(player);
				}
				// ... nor a Time Slow charge (Z is not held any more either)
				if (timeSlowCharging(player)) {
					cancelTimeSlowCharge(player);
				}
				// v0.14.16: ... and V is not held either, so the next press may end an Overdrive saved mid-run
				if (overdrive(player)) {
					set(player, OD_RELEASED, 1);
				}
			} else {
				PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, PASSIVE_SPEED);
				PowerToggles.clearModifier(player, Attributes.WATER_MOVEMENT_EFFICIENCY, PASSIVE_SWIM);
				PowerToggles.clearModifier(player, Attributes.STEP_HEIGHT, PASSIVE_STEP);
				clearRegen(player);
				SuperSpeedMoves.stopAll(player);
				speedModeClear(player);
				clearOverdrive(player);
				if (phasing(player)) {
					endPhase(player);
				}
				int carried = (int) res(player, CARRY_ID);
				if (carried != 0) {
					releaseCarry(player, stillOurs(player, player.level().getEntity(carried)));
				}
				SuperSpeedTimeSlow.end(player, false);
				cancelTimeSlowCharge(player);
				clearExhaustion(player);
				set(player, OVERDRIVE_UNTIL, 0);
				set(player, OVERDRIVE_LEFT, 0);
				LAST_POS.remove(player.getUUID());
				RECENT_SPEED.remove(player.getUUID());
			}
		});
		PowerPassives.registerTick(KEY, SuperSpeedHandlers::serverTick);
	}

	/** The dash impulse: the look vector at {@code speed}, with a little lift off the floor so friction can't eat it. */
	public static Vec3 dashVelocity(Vec3 look, boolean onGround, double speed) {
		Vec3 v = look.normalize().scale(speed);
		if (onGround && v.y < 0.25) {
			v = new Vec3(v.x, 0.25, v.z);
		}
		return v;
	}

	// ---- R: Rapid Assault ------------------------------------------------------------------------

	/** Four punches of 5 on everything in front of you; every blow lands in full, even on a player or a mob just hit. */
	public static void rapidAssault(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		float m = overdriveMult(p);
		int struck = 0;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.getEyePosition().add(p.getLookAngle().scale(1.5)), 3.5)) {
			if (!SuperSpeedMoves.validFoe(p, e)) {
				continue;
			}
			for (int i = 0; i < PUNCHES; i++) {
				SuperSpeedMoves.punchThrough(p, e, PUNCH_DAMAGE * m);
			}
			AbilityHelpers.knockbackFrom(e, p.position(), 0.35 * m);
			ctx.level().sendParticles(ParticleTypes.CRIT, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(),
					PUNCHES * 3, 0.35, 0.35, 0.35, 0.3);
			ctx.level().sendParticles(ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(),
					PUNCHES, 0.3, 0.3, 0.3, 0.2);
			struck++;
		}
		BatchA.play(p, KEY, "p04.flurry", 12);
		AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 1.8f);
		if (struck > 0) {
			AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 2.0f);
		}
		ctx.triggerCooldown();
	}

	private static void cooldownMessage(AbilityContext ctx) {
		ctx.actionBar("message.projecthero.ability.on_cooldown", net.minecraft.network.chat.Component.translatable(ctx.ability().nameKey()),
				String.format(java.util.Locale.ROOT, "%.1f", ctx.cooldownRemaining() / 20.0f));
	}

	// ---- passive: Regeneration III ----------------------------------------------------------------

	/** v0.14.7: owning Super Speed keeps a permanent, hidden Regeneration III (v0.14.13) on you (never weakens a stronger regen). */
	public static void applyRegen(ServerPlayer p) {
		MobEffectInstance cur = p.getEffect(MobEffects.REGENERATION);
		if (cur == null || cur.getAmplifier() < REGEN_AMPLIFIER
				|| (cur.getAmplifier() == REGEN_AMPLIFIER && !cur.isInfiniteDuration())) {
			if (cur != null) {
				p.removeEffect(MobEffects.REGENERATION);
			}
			p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, MobEffectInstance.INFINITE_DURATION, REGEN_AMPLIFIER,
					false, false, true));
		}
	}

	public static void clearRegen(ServerPlayer p) {
		MobEffectInstance cur = p.getEffect(MobEffects.REGENERATION);
		if (cur != null && cur.isInfiniteDuration() && cur.getAmplifier() == REGEN_AMPLIFIER) {
			p.removeEffect(MobEffects.REGENERATION);
		}
	}

	/**
	 * Time Slow caster: pulls Super Speed's running absolute-time clocks (Overdrive, the Shift-move cooldowns) in by
	 * {@code ticks}, one per extra full-speed tick, so they run at the caster's pace while the world's clock crawls.
	 */
	public static void advanceClocks(ServerPlayer p, int ticks) {
		long now = p.level().getGameTime();
		for (String name : new String[] { OVERDRIVE_UNTIL, SuperSpeedMoves.MACH_READY, SuperSpeedMoves.VORTEX_READY,
				SuperSpeedMoves.SWEEP_READY }) {
			float v = res(p, name);
			if (v > now) {
				set(p, name, Math.max(now, v - ticks));
			}
		}
	}

	/** Recent horizontal speed in blocks per tick (a peak that decays), for the Mach Punch. */
	public static double recentSpeed(ServerPlayer p) {
		return RECENT_SPEED.getOrDefault(p.getUUID(), 0.0);
	}

	// ---- Speed Mode: wall run (server half) ------------------------------------------------------

	/** Pitch at or above which (looking up) a speedster against a wall runs up it. */
	public static final float WALL_RUN_PITCH = -60.0f;

	/**
	 * Whether {@code p} is set up to run up a wall: Speed Mode or Overdrive on, looking steeply up, and a solid
	 * block right in front of them at body height. The climb itself is simulated by the player's own client
	 * ({@code LocalPlayerMixin}); the server only keeps them safe from fall damage while they do it.
	 */
	public static boolean wallRunReady(ServerPlayer p) {
		if (!(speedMode(p) || overdrive(p)) || p.getXRot() > WALL_RUN_PITCH || p.isShiftKeyDown()
				|| p.getAbilities().flying || p.isPassenger() || phasing(p)) {
			return false;
		}
		Vec3 f = BatchA.flatLook(p);
		AABB probe = p.getBoundingBox().move(f.scale(0.3)).deflate(0.02, 0.1, 0.02);
		return !p.level().noCollision(p, probe);
	}

	/** Server upkeep for a wall run: no fall damage accumulates while climbing. */
	public static void wallRunTick(ServerPlayer p) {
		if (wallRunReady(p)) {
			p.resetFallDistance();
		}
	}

	// ---- G: Speed Carry ------------------------------------------------------------------------

	private static void carryPress(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		int id = (int) res(p, CARRY_ID);
		if (id != 0) {
			releaseCarry(p, stillOurs(p, ctx.level().getEntity(id)));
			AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7f, 1.2f);
			return; // v0.14.17: no cooldown -- grab the next one straight away
		}
		LivingEntity target = AbilityHelpers.raycastEntity(p, 6.0);
		if (target == null || !AbilityHelpers.isValidGrabTarget(target, p) || target.getVehicle() == p) {
			return;
		}
		if (target.isPassenger()) {
			target.stopRiding();
		}
		if (!target.startRiding(p, true)) {
			return;
		}
		syncPassengersToSelf(p);
		target.fallDistance = 0;
		set(p, CARRY_ID, target.getId());
		ctx.actionBar("message.projecthero.ability.grabbed");
		BatchA.play(p, KEY, "grab_pull", 8);
		AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 1.2f);
	}

	private static void carryTick(ServerPlayer p) {
		int id = (int) res(p, CARRY_ID);
		if (id == 0) {
			MutationVisuals.stopIf(p, "p04.carry");
			return;
		}
		Entity e = p.level().getEntity(id);
		// v0.14.8: no time limit any more -- it lasts until you set it down (or die, lose the power, or are exhausted)
		if (!(e instanceof LivingEntity le) || !le.isAlive() || le.getVehicle() != p || !p.isAlive() || exhausted(p)) {
			releaseCarry(p, stillOurs(p, e));
			return;
		}
		if (p.tickCount % 20 == 0) {
			syncPassengersToSelf(p); // belt and braces (a relog, a dimension change)
		}
		le.fallDistance = 0;
		le.resetFallDistance();
		BatchA.stance(p, KEY, "p04.carry");
	}

	/**
	 * The creature a stale carry id still points at, if it really is the one we were carrying (riding us, or just
	 * hopped off right beside us) -- never some unrelated entity that reused the id after a relog.
	 */
	private static LivingEntity stillOurs(ServerPlayer p, Entity e) {
		if (e instanceof LivingEntity le && le.isAlive()
				&& (le.getVehicle() == p || (le.getVehicle() == null && le.distanceToSqr(p) < 9.0))) {
			return le;
		}
		return null;
	}

	/** Ends a carry: sets the creature down in front of you (or at your feet) with a short immunity window. */
	public static void releaseCarry(ServerPlayer p, LivingEntity le) {
		set(p, CARRY_ID, 0);
		MutationVisuals.stopIf(p, "p04.carry");
		if (le != null && le.getVehicle() == p) {
			le.stopRiding();
		}
		// v0.14.9: ALWAYS tell the carrier's own client. A carried player who hops off (Sneak) has already left by the
		// time we get here, and vanilla never sends the carrier that change -- their client kept the ghost on their head.
		syncPassengersToSelf(p);
		if (le == null) {
			return;
		}
		Vec3 f = BatchA.flatLook(p);
		Vec3 front = p.position().add(f.scale(1.2));
		AABB box = le.getDimensions(le.getPose()).makeBoundingBox(front);
		Vec3 at = p.level().noCollision(le, box) ? front : p.position();
		le.teleportTo(at.x, at.y, at.z);
		le.setDeltaMovement(Vec3.ZERO);
		le.resetFallDistance();
		le.hurtMarked = true;
		CARRY_GRACE.put(le.getUUID(), p.level().getGameTime() + CARRY_GRACE_TICKS);
	}

	/**
	 * v0.14.8 fix ("the carrier can't see what they carry"): vanilla only ever tells the players <em>tracking</em> an entity
	 * about its passengers ({@code ServerEntity.sendChanges} broadcasts {@code ClientboundSetPassengersPacket} without the
	 * "and self" variant), because vanilla never seats anything on a player. So everyone else saw the creature ride
	 * the speedster, but the speedster's own client never learned it was a passenger -- and since the server stops
	 * sending a passenger's own position, it stayed frozen, invisible to them, wherever it was snatched up. Telling the
	 * carrier directly makes their client seat it on them (and position it from their own movement, smoothly).
	 */
	public static void syncPassengersToSelf(ServerPlayer p) {
		p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetPassengersPacket(p));
	}

	/** Whether {@code e} is being carried by a speedster right now, or was set down less than 3 s ago. */
	public static boolean carryProtected(Entity e) {
		if (e.getVehicle() instanceof ServerPlayer carrier && (int) res(carrier, CARRY_ID) == e.getId()
				&& owns(carrier)) {
			return true;
		}
		Long until = CARRY_GRACE.get(e.getUUID());
		if (until == null) {
			return false;
		}
		if (until <= e.level().getGameTime()) {
			CARRY_GRACE.remove(e.getUUID());
			return false;
		}
		return true;
	}

	/** Whether {@code attacker} is the creature {@code carrier} is carrying (it can't hurt the one holding it). */
	public static boolean isCarriedBy(Entity attacker, ServerPlayer carrier) {
		return attacker != null && attacker.getVehicle() == carrier && (int) res(carrier, CARRY_ID) == attacker.getId();
	}

	// ---- Shift+C: Phase --------------------------------------------------------------------------

	/** True while Shift+C Phase is held -- read from the synced attachment, so it works on both sides for the owner. */
	public static boolean phasing(Player p) {
		ExperimentalState st = p.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st != null && st.ownedPowers.contains(KEY) && st.resources.getOrDefault(KEY + "/" + PHASING, 0f) > 0.5f;
	}

	public static void startPhase(ServerPlayer p) {
		if (phasing(p)) {
			return;
		}
		set(p, PHASING, 1);
		p.noPhysics = true;
		ServerLevel level = p.serverLevel();
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1, p.getZ(), 20, 0.3, 0.6, 0.3, 0.1);
		AbilityHelpers.sound(p, SoundEvents.BEACON_ACTIVATE, 0.6f, 2.0f);
		p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.speed.phase_hint"), true);
	}

	public static void endPhase(ServerPlayer p) {
		if (!phasing(p)) {
			return;
		}
		set(p, PHASING, 0);
		if (!p.isSpectator()) {
			p.noPhysics = false;
		}
		settleOutsideBlocks(p);
		p.serverLevel().sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1, p.getZ(), 20, 0.3, 0.6, 0.3, 0.1);
		AbilityHelpers.sound(p, SoundEvents.BEACON_DEACTIVATE, 0.6f, 2.0f);
	}

	/** If Phase ended inside a wall, step out to the nearest spot on the same level where you fit. */
	public static void settleOutsideBlocks(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		AABB box = p.getBoundingBox();
		if (level.noCollision(p, box)) {
			return;
		}
		int bx = p.getBlockX();
		int bz = p.getBlockZ();
		for (int r = 1; r <= 8; r++) {
			Vec3 best = null;
			double bestD = Double.MAX_VALUE;
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					double cx = bx + dx + 0.5;
					double cz = bz + dz + 0.5;
					AABB moved = box.move(cx - p.getX(), 0, cz - p.getZ());
					double d = (cx - p.getX()) * (cx - p.getX()) + (cz - p.getZ()) * (cz - p.getZ());
					if (d < bestD && level.noCollision(p, moved)) {
						best = new Vec3(cx, p.getY(), cz);
						bestD = d;
					}
				}
			}
			if (best != null) {
				p.teleportTo(best.x, best.y, best.z);
				return;
			}
		}
		for (int up = 1; up <= 16; up++) {
			if (level.noCollision(p, box.move(0, up, 0))) {
				p.teleportTo(p.getX(), p.getY() + up, p.getZ());
				return;
			}
		}
	}

	/**
	 * Called by {@code SuperSpeedAbilityRouterMixin} at the head of {@code AbilityRouter.handleInput}: while
	 * phasing every key is swallowed (no other ability can fire), and releasing C ends the phase.
	 */
	public static boolean interceptInput(ServerPlayer p, int slotNumber, boolean pressed) {
		if (!phasing(p)) {
			return false;
		}
		if (slotNumber == AbilitySlot.SLOT_6.number() && !pressed) {
			endPhase(p);
		}
		return true;
	}

	/**
	 * Called at the head of {@code AbilityRouter.dispatchExperimental}: Shift+C on Super Speed starts Phase, and
	 * (v0.14.8) while exhausted after a Time Slow every Super Speed key is refused.
	 */
	public static boolean interceptDispatch(ServerPlayer p, AbilitySlot slot, boolean pressed) {
		Power active = ExperimentalPowers.getActive(p);
		if (active == null || !KEY.equals(active.key()) || !ExperimentalPowers.owns(p, active)) {
			return false;
		}
		if (exhausted(p)) {
			if (pressed) {
				p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.speed.exhausted",
						String.format(java.util.Locale.ROOT, "%.0f", Math.ceil(res(p, EXHAUST) / 20.0))), true);
			}
			return true;
		}
		// v0.14.16: V while Overdrive runs -- a fresh press (V let go since it started) ends it early; the key repeat of
		// the starting press is swallowed
		if (slot == AbilitySlot.SLOT_5 && overdrive(p)) {
			if (!pressed) {
				set(p, OD_RELEASED, 1);
			} else if (res(p, OD_RELEASED) > 0.5f) {
				endOverdrive(p);
			}
			return true;
		}
		if (slot != AbilitySlot.SLOT_6 || !pressed || !p.isShiftKeyDown()) {
			return false;
		}
		startPhase(p);
		return true;
	}

	/**
	 * v0.14.16: Overdrive ended early by a second V. The speed tier, haste and attack bonuses go at once; the cooldown
	 * that started when it was switched on keeps running unchanged (it is counted from activation, so ending early
	 * neither refunds nor lengthens it).
	 */
	public static void endOverdrive(ServerPlayer p) {
		if (!overdrive(p)) {
			return;
		}
		set(p, OVERDRIVE_UNTIL, 0);
		set(p, OVERDRIVE_LEFT, 0);
		set(p, OD_RELEASED, 0);
		MobEffectInstance haste = p.getEffect(MobEffects.DIG_SPEED);
		if (haste != null && haste.getAmplifier() == 2) {
			p.removeEffect(MobEffects.DIG_SPEED); // Overdrive's
		}
		reconcileSpeed(p);
		if (p.level() instanceof ServerLevel sl) {
			sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1.0, p.getZ(), 16, 0.35, 0.6, 0.35, 0.05);
		}
		AbilityHelpers.sound(p, SoundEvents.BEACON_DEACTIVATE, 0.7f, 1.6f);
		p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.speed.overdrive_ended"), true);
	}

	// ---- Z: Time Slow charge-up (v0.14.8) --------------------------------------------------------

	public static boolean timeSlowCharging(Player p) {
		ExperimentalState st = p.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st != null && st.resources.getOrDefault(KEY + "/" + TS_CHARGING, 0f) > 0.5f;
	}

	/** Z pressed: start holding the charge. The body braces, vibrates and crackles while it builds (see the tick). */
	public static void startTimeSlowCharge(ServerPlayer p) {
		set(p, TS_CHARGING, 1);
		set(p, TS_CHARGE, 0);
		MutationVisuals.play(p, ANIM_TS_CHARGE);
		AbilityHelpers.sound(p, SoundEvents.BEACON_POWER_SELECT, 0.7f, 0.5f);
	}

	/** Z let go early (or the charge was interrupted): nothing happens, no cooldown. */
	public static void cancelTimeSlowCharge(ServerPlayer p) {
		if (res(p, TS_CHARGING) <= 0.5f && res(p, TS_CHARGE) <= 0f) {
			return;
		}
		set(p, TS_CHARGING, 0);
		set(p, TS_CHARGE, 0);
		MutationVisuals.stopIf(p, ANIM_TS_CHARGE);
	}

	/** Per tick while Z is held: build the charge; at {@link #TS_CHARGE_TICKS} Time Slow fires by itself. */
	public static void timeSlowChargeTick(ServerPlayer p) {
		if (res(p, TS_CHARGING) <= 0.5f) {
			return;
		}
		if (!p.isAlive() || SuperSpeedTimeSlow.anyActive() || exhausted(p)) {
			cancelTimeSlowCharge(p);
			return;
		}
		int held = (int) res(p, TS_CHARGE) + 1;
		set(p, TS_CHARGE, held);
		float progress = Math.min(1f, held / (float) TS_CHARGE_TICKS);
		if (!MutationVisuals.state(p).anim().equals(ANIM_TS_CHARGE)) {
			MutationVisuals.play(p, ANIM_TS_CHARGE);
		}
		if (p.level() instanceof ServerLevel sl && held % 2 == 0) {
			int n = 1 + Math.round(progress * 7);
			double r = 0.35 + 0.35 * progress;
			sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1.0, p.getZ(), n, r, 0.7, r, 0.08 + 0.2 * progress);
			sl.sendParticles(CHARGE_SPARK, p.getX(), p.getY() + 1.0, p.getZ(), 1 + n / 2, r, 0.6, r, 0.0);
		}
		if (held % 10 == 0) {
			// a rising electric hum
			AbilityHelpers.sound(p, SoundEvents.BEACON_AMBIENT, 0.5f + 0.5f * progress, 0.6f + 1.4f * progress);
		}
		if (held >= TS_CHARGE_TICKS) {
			set(p, TS_CHARGING, 0);
			set(p, TS_CHARGE, 0);
			set(p, TS_RELEASED, 0);
			MutationVisuals.stopIf(p, ANIM_TS_CHARGE);
			SuperSpeedTimeSlow.start(p);
			if (SuperSpeedTimeSlow.isCasting(p) && p.level() instanceof ServerLevel sl) {
				// the release: a shockwave ring of sparks and a flash
				BatchA.ring(sl, p.position().add(0, 0.2, 0), 0.6, ParticleTypes.ELECTRIC_SPARK, 48, 1.4);
				sl.sendParticles(ParticleTypes.FLASH, p.getX(), p.getY() + 1.0, p.getZ(), 1, 0, 0, 0, 0);
				sl.sendParticles(ParticleTypes.SONIC_BOOM, p.getX(), p.getY() + 1.0, p.getZ(), 1, 0, 0, 0, 0);
				AbilityHelpers.sound(p, SoundEvents.WARDEN_SONIC_BOOM, 0.9f, 1.6f);
			}
		}
	}

	/** The charging pose (registered client-side by {@code SuperSpeedClientV0145}). */
	public static final String ANIM_TS_CHARGE = "p04.ts_charge";

	// ---- exhaustion after a Time Slow (v0.14.8) --------------------------------------------------

	/** Whether {@code p} is exhausted after a Time Slow (either side, from the synced attachment). */
	public static boolean exhausted(Player p) {
		ExperimentalState st = p.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st != null && st.ownedPowers.contains(KEY) && st.resources.getOrDefault(KEY + "/" + EXHAUST, 0f) > 0.5f;
	}

	/**
	 * A Time Slow just ended: 30 s spent. Speed Mode and Overdrive are switched off (and can't come back), the carry
	 * is set down, every Super Speed key is refused ({@link #interceptDispatch}) and the speed passives are off, with
	 * a Slowness I and some panting for flavour.
	 */
	public static void startExhaustion(ServerPlayer p) {
		Power power = Powers.byKey(KEY);
		if (power == null || !ExperimentalPowers.owns(p, power)) {
			return;
		}
		set(p, EXHAUST, EXHAUST_TICKS);
		cancelTimeSlowCharge(p);
		com.projecthero.mod.hero.Ability sm = power.ability(AbilitySlot.SLOT_6);
		if (sm != null && ExperimentalPowers.isToggled(p, power, sm)) {
			ExperimentalPowers.setToggled(p, power, sm, false);
		}
		set(p, OVERDRIVE_UNTIL, 0);
		set(p, OVERDRIVE_LEFT, 0);
		MobEffectInstance haste = p.getEffect(MobEffects.DIG_SPEED);
		if (haste != null && haste.getAmplifier() == 2) {
			p.removeEffect(MobEffects.DIG_SPEED); // Overdrive's
		}
		SuperSpeedMoves.stopAll(p);
		if (phasing(p)) {
			endPhase(p);
		}
		int carried = (int) res(p, CARRY_ID);
		if (carried != 0) {
			releaseCarry(p, stillOurs(p, p.level().getEntity(carried)));
		}
		reconcileSpeed(p);
		applySpeedPassives(p, false);
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, EXHAUST_TICKS, 0, false, false, true));
		p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.speed.exhausted_start"), true);
	}

	/** Ends exhaustion now (death, the power going away). */
	public static void clearExhaustion(ServerPlayer p) {
		if (res(p, EXHAUST) <= 0f) {
			return;
		}
		set(p, EXHAUST, 0);
		MobEffectInstance slow = p.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
		if (slow != null && slow.getAmplifier() == 0 && !slow.isVisible()) {
			p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
		}
	}

	private static void exhaustionTick(ServerPlayer p) {
		int left = (int) res(p, EXHAUST);
		if (left <= 0) {
			return;
		}
		set(p, EXHAUST, left - 1);
		if (left % 30 == 0 && p.level() instanceof ServerLevel sl) {
			// panting
			Vec3 mouth = p.getEyePosition().add(p.getLookAngle().scale(0.4)).subtract(0, 0.15, 0);
			sl.sendParticles(ParticleTypes.CLOUD, mouth.x, mouth.y, mouth.z, 2, 0.05, 0.02, 0.05, 0.01);
			AbilityHelpers.sound(p, SoundEvents.PLAYER_BREATH, 0.5f, 1.3f);
		}
		if (left - 1 <= 0) {
			p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.speed.exhausted_end"), true);
		}
	}

	/** Movement passives (+30% speed, swim): off while Time Slow runs and while exhausted. */
	private static void applySpeedPassives(ServerPlayer p, boolean on) {
		if (on) {
			PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, PASSIVE_SPEED, PASSIVE_SPEED_BONUS,
					AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			PowerToggles.modifier(p, Attributes.WATER_MOVEMENT_EFFICIENCY, PASSIVE_SWIM, PASSIVE_SWIM_EFFICIENCY,
					AttributeModifier.Operation.ADD_VALUE);
		} else {
			PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, PASSIVE_SPEED);
			PowerToggles.clearModifier(p, Attributes.WATER_MOVEMENT_EFFICIENCY, PASSIVE_SWIM);
			PowerToggles.clearModifier(p, Attributes.STEP_HEIGHT, PASSIVE_STEP);
		}
	}

	/** v0.14.8: whether the speed boosts are suspended right now (Time Slow running, or exhausted afterwards). */
	public static boolean speedSuspended(ServerPlayer p) {
		return SuperSpeedTimeSlow.isCasting(p) || exhausted(p);
	}

	// ---- per-tick upkeep -------------------------------------------------------------------------

	private static void serverTick(ServerPlayer player) {
		com.projecthero.mod.hero.power.PowerCombos.speedGeneratesCharge(player);

		Power power = Powers.byKey(KEY);
		float until = ExperimentalPowers.getResource(player, power, OVERDRIVE_UNTIL);
		long now = player.level().getGameTime();
		boolean overdrive = until > now;
		if (overdrive) {
			set(player, OVERDRIVE_LEFT, Math.max(0.0f, until - now));
		} else if (until > 0.0f) {
			set(player, OVERDRIVE_UNTIL, 0);
			set(player, OVERDRIVE_LEFT, 0);
		}
		exhaustionTick(player);
		reconcileSpeed(player);
		// the passive is re-asserted every tick (cheap: PowerToggles only touches the attribute on a change);
		// v0.14.8: suspended while Time Slow runs and while exhausted -- the caster moves at a normal player's speed
		applySpeedPassives(player, !speedSuspended(player));
		applyRegen(player);
		SuperSpeedMoves.tick(player);
		wallRunTick(player);

		if (phasing(player)) {
			if (!player.isAlive()) {
				endPhase(player);
			} else {
				// Player.tick resets noPhysics every tick; re-assert it before the next movement packets arrive
				player.noPhysics = true;
				if (player.tickCount % 4 == 0 && player.level() instanceof ServerLevel sl) {
					sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1, player.getZ(),
							3, 0.3, 0.6, 0.3, 0.05);
				}
			}
		}

		if (!(player.level() instanceof ServerLevel sl)) {
			return;
		}

		Vec3 last = LAST_POS.put(player.getUUID(), player.position());
		double speed = last == null ? 0.0 : Math.sqrt(sq(player.getX() - last.x) + sq(player.getZ() - last.z));
		if (speed > 8.0) {
			speed = 0.0; // a teleport, not running
		}
		RECENT_SPEED.put(player.getUUID(), Math.max(speed, recentSpeed(player) * 0.85));
		boolean speedMode = speedMode(player);
		boolean moving = speed > 0.08 || player.isSprinting();

		// Speed Mode burns hunger 50% faster than normal as a balancing cost.
		if (speedMode && !player.getAbilities().instabuild && moving) {
			player.getFoodData().addExhaustion(0.05f);
		}

		// Overdrive: the speed-explosion bursts go off only while you are actually running, and always behind you
		// so they never fill your own view.
		if (overdrive && speed > RUNNING_SPEED && player.tickCount % 3 == 0 && last != null) {
			Vec3 dir = new Vec3(player.getX() - last.x, 0, player.getZ() - last.z).normalize();
			overdriveBurst(sl, player, dir);
		}
		// v0.14.12: ordinary armour can't take Overdrive -- every second of running costs each worn piece 10 durability.
		// Only the Flash Suit is built for it.
		if (overdrive && speed > RUNNING_SPEED && player.tickCount % 20 == 0 && !player.getAbilities().instabuild) {
			wearArmour(player);
		}
		// v0.14.7: the Overdrive trail crackles -- small spark bursts at your heels (the arcs between the red
		// after-images are drawn client-side)
		if (overdrive && speed > 0.15 && player.tickCount % 2 == 1 && last != null) {
			Vec3 dir = new Vec3(player.getX() - last.x, 0, player.getZ() - last.z).normalize();
			Vec3 heel = player.position().subtract(dir.scale(1.3));
			sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, heel.x, heel.y + 0.15, heel.z, 3, 0.25, 0.1, 0.25, 0.12);
			sl.sendParticles(OVERDRIVE_SPARK, heel.x, heel.y + 0.6, heel.z, 2, 0.3, 0.4, 0.3, 0.0);
		}

		// Running on water: splash and footfall feedback (the client mixin keeps the player on the surface).
		if ((speedMode || overdrive) && moving && !player.isShiftKeyDown() && !player.getAbilities().flying) {
			BlockPos feet = player.blockPosition();
			var atFeet = sl.getFluidState(feet);
			var below = sl.getFluidState(feet.below());
			boolean onWaterSurface = (atFeet.is(FluidTags.WATER) || below.is(FluidTags.WATER)) && !player.isInWater();
			if (onWaterSurface) {
				double surfaceY = player.getY();
				sl.sendParticles(ParticleTypes.SPLASH, player.getX(), surfaceY + 0.05, player.getZ(), 8, 0.35, 0.02, 0.35, 0.12);
				// v0.14.7: a soft patter, not a loud splash every few ticks (vanilla's own splash / swim sounds are
				// muted while water running -- SuperSpeedWaterSoundMixin)
				if (player.tickCount % WATER_SOUND_EVERY == 0) {
					sl.playSound(null, player.blockPosition(), SoundEvents.PLAYER_SWIM,
							net.minecraft.sounds.SoundSource.PLAYERS, WATER_SOUND_VOLUME, 1.3f + sl.random.nextFloat() * 0.3f);
				}
			}
		}
	}

	private static double sq(double d) {
		return d * d;
	}

	/** The speed-explosion burst, placed 2-3 blocks behind a runner moving along {@code dir}. */
	private static void overdriveBurst(ServerLevel level, ServerPlayer p, Vec3 dir) {
		double h = p.getBbHeight();
		Vec3 at = p.position().subtract(dir.scale(2.6)).add(0, h * 0.45, 0);
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0.3, 0.3, 0.3, 0.0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 10, 0.5, h * 0.4, 0.5, 0.25);
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 8, 0.5, h * 0.4, 0.5, 0.2);
		if (p.tickCount % 15 == 0) {
			AbilityHelpers.sound(p, SoundEvents.FIREWORK_ROCKET_BLAST, 0.5f, 0.7f);
		}
	}

	/** A streak of sparks along a dash. */
	private static void afterimage(ServerLevel level, Vec3 from, Vec3 dir, double length) {
		for (double s = 0; s <= length; s += 0.6) {
			Vec3 at = from.add(dir.scale(s));
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 1.0, at.z, 2, 0.15, 0.5, 0.15, 0.01);
			level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.1, at.z, 1, 0.1, 0.02, 0.1, 0.005);
		}
	}

	/**
	 * The mining / mode-eating multiplier of the current speed mode, read from the synced attachment so it works on
	 * both sides. 1.0 = no boost (the base eat-faster passive is SuperSpeedEatMixin, not this).
	 */
	public static float speedFactor(Player player) {
		ExperimentalState st = player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		if (st == null || !st.ownedPowers.contains(KEY)) {
			return 1.0f;
		}
		Float until = st.resources.get(KEY + "/" + OVERDRIVE_UNTIL);
		boolean overdrive = until != null && until > player.level().getGameTime();
		boolean speedMode = st.activeToggles.contains(KEY + "/speed_mode");
		if (overdrive) {
			return 8.0f;
		}
		return speedMode ? 5.0f : 1.0f;
	}

	/** Whether this player owns Super Speed (either side). */
	public static boolean owns(Player player) {
		ExperimentalState st = player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st != null && st.ownedPowers.contains(KEY);
	}

	/** Whether Overdrive is running (server). */
	public static boolean overdrive(ServerPlayer p) {
		return res(p, OVERDRIVE_UNTIL) > p.level().getGameTime();
	}

	/** Whether Speed Mode is on (server). */
	public static boolean speedMode(ServerPlayer p) {
		return ExperimentalPowers.state(p).activeToggles.contains(KEY + "/speed_mode");
	}

	/** v0.14.12: Overdrive's toll on armour that isn't the Flash Suit (per second of running). */
	public static final int OVERDRIVE_ARMOUR_WEAR = 10;

	/** One second of Overdrive running: every worn piece that can wear (and isn't Flash Suit) loses {@link #OVERDRIVE_ARMOUR_WEAR}. */
	public static void wearArmour(ServerPlayer player) {
		boolean worn = false;
		for (net.minecraft.world.entity.EquipmentSlot slot : new net.minecraft.world.entity.EquipmentSlot[] {
				net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
				net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET }) {
			net.minecraft.world.item.ItemStack stack = player.getItemBySlot(slot);
			if (stack.isEmpty() || !stack.isDamageableItem() || com.projecthero.mod.flash.FlashSuit.isSuit(stack)) {
				continue;
			}
			stack.hurtAndBreak(OVERDRIVE_ARMOUR_WEAR, player, slot);
			worn = true;
		}
		if (worn && player.tickCount % 100 == 0) {
			player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.speed.armour_tearing")
					.withStyle(net.minecraft.ChatFormatting.RED), true);
		}
	}

	/**
	 * Single authority for the movement modifiers. Overdrive and Speed Mode never stack -- Overdrive is a strictly
	 * faster tier that replaces Speed Mode while it runs. Called every server tick plus on every state change.
	 */
	private static void reconcileSpeed(ServerPlayer p) {
		boolean overdrive = overdrive(p);
		boolean speedMode = speedMode(p);
		if (exhausted(p)) {
			speedModeClear(p);
			clearOverdrive(p);
			return;
		}
		if (overdrive) {
			applyOverdrive(p);
			speedModeClear(p);
		} else if (speedMode) {
			speedModeApply(p);
			clearOverdrive(p);
		} else {
			speedModeClear(p);
			clearOverdrive(p);
		}
	}

	private static float overdriveMult(ServerPlayer p) {
		return overdrive(p) ? 2.0f : 1.0f;
	}

	private static float selfMult(float overdriveMult) {
		return 1.0f + (overdriveMult - 1.0f) * 0.5f;
	}

	/**
	 * v0.14.8: while Time Slow runs only the movement boosts go (the extra ticks are the advantage) -- the modes' attack
	 * speed / damage / fall protection stay -- and they come back the tick it ends.
	 */
	private static void movementBoost(ServerPlayer p, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
			ResourceLocation id, double amount, AttributeModifier.Operation op) {
		if (SuperSpeedTimeSlow.isCasting(p)) {
			PowerToggles.clearModifier(p, attr, id);
		} else {
			PowerToggles.modifier(p, attr, id, amount, op);
		}
	}

	private static void speedModeApply(ServerPlayer p) {
		movementBoost(p, Attributes.MOVEMENT_SPEED, SM_SPEED, p.isSprinting() ? SPEED_MODE_BONUS : SPEED_MODE_WALK_BONUS,
				AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		PowerToggles.modifier(p, Attributes.ATTACK_SPEED, SM_ATTACK, 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		movementBoost(p, Attributes.STEP_HEIGHT, SM_STEP, SM_STEP_BONUS, AttributeModifier.Operation.ADD_VALUE); // v0.14.9: 3 blocks, Speed Mode only
		movementBoost(p, Attributes.WATER_MOVEMENT_EFFICIENCY, SM_WATER, 1.0, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.FALL_DAMAGE_MULTIPLIER, SM_FALL, -0.8, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	private static void speedModeClear(ServerPlayer p) {
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, SM_SPEED);
		PowerToggles.clearModifier(p, Attributes.ATTACK_SPEED, SM_ATTACK);
		PowerToggles.clearModifier(p, Attributes.STEP_HEIGHT, SM_STEP);
		PowerToggles.clearModifier(p, Attributes.WATER_MOVEMENT_EFFICIENCY, SM_WATER);
		PowerToggles.clearModifier(p, Attributes.FALL_DAMAGE_MULTIPLIER, SM_FALL);
	}

	private static void applyOverdrive(ServerPlayer p) {
		// v0.14.16: walking and sprinting are separate tiers, like Speed Mode's -- ~32 blocks/s walking, ~100 sprinting
		movementBoost(p, Attributes.MOVEMENT_SPEED, OD_SPEED, p.isSprinting() ? OVERDRIVE_BONUS : OVERDRIVE_WALK_BONUS,
				AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		PowerToggles.modifier(p, Attributes.ATTACK_SPEED, OD_ATTACK, 1.5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		// "your attacks x2": melee doubles while Overdrive runs (the Super Speed moves double through overdriveMult)
		PowerToggles.modifier(p, Attributes.ATTACK_DAMAGE, OD_DAMAGE, 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		movementBoost(p, Attributes.STEP_HEIGHT, OD_STEP, OD_STEP_BONUS, AttributeModifier.Operation.ADD_VALUE); // v0.14.9: 10 blocks
		PowerToggles.modifier(p, Attributes.FALL_DAMAGE_MULTIPLIER, OD_FALL, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	private static void clearOverdrive(ServerPlayer p) {
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, OD_SPEED);
		PowerToggles.clearModifier(p, Attributes.ATTACK_SPEED, OD_ATTACK);
		PowerToggles.clearModifier(p, Attributes.ATTACK_DAMAGE, OD_DAMAGE);
		PowerToggles.clearModifier(p, Attributes.STEP_HEIGHT, OD_STEP);
		PowerToggles.clearModifier(p, Attributes.FALL_DAMAGE_MULTIPLIER, OD_FALL);
	}

	/**
	 * Whether {@code player} is running across water right now (either side, for the owner): Speed Mode / Overdrive
	 * on, moving, not sneaking, head above the surface. Vanilla's splash / swim sounds are muted while this holds.
	 */
	public static boolean waterRunning(Player player) {
		if (speedFactor(player) <= 1.0f || player.isShiftKeyDown() || player.isUnderWater()
				|| player.getAbilities().flying) {
			return false;
		}
		Vec3 v = player.getDeltaMovement();
		double moved = Math.max(v.horizontalDistance(), Math.sqrt(sq(player.getX() - player.xo) + sq(player.getZ() - player.zo)));
		return moved > 0.08 || player.isSprinting();
	}

	/** Drops the static scratch maps (server stop). */
	public static void clearSessionState() {
		LAST_POS.clear();
		CARRY_GRACE.clear();
		RECENT_SPEED.clear();
		SuperSpeedMoves.clearSessionState();
	}

	/** Slow sweep: forget expired carry-grace entries and players who left. */
	public static void prune(net.minecraft.server.MinecraftServer server) {
		long now = server.overworld().getGameTime();
		CARRY_GRACE.values().removeIf(t -> t <= now);
		LAST_POS.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
		RECENT_SPEED.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
		SuperSpeedMoves.prune(server);
	}
}
