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
 * <p>v0.14.7: the six keys plus N (no H -- H stays the power wheel). R Rapid Assault (4 punches of 8, each landing in full) / Shift+R Mach Punch, G Blitz /
 * Shift+G Speed Vortex, X Momentum Dash (along the full look vector) / Shift+X Speed Sweep, Z Time Slow (see
 * {@link SuperSpeedTimeSlow}), V Overdrive, C Speed Mode — and Shift+C Phase (hold C: walk through walls on your
 * own level; see {@link #startPhase}), N Speed Carry (carry anything overhead; no fall damage
 * while carried or for 3 s after). The new moves live in {@link SuperSpeedMoves}.
 *
 * <p>Passives: +30% walking / sprinting / swimming speed, a 3-block step assist, eating / drinking 50% faster
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

	/** 1 while Shift+C Phase is held. Owner-synced, so the local player's collision mixin can read it. */
	public static final String PHASING = "phasing";
	/** Carried entity id (0 = nothing) and the ticks left on the carry. */
	private static final String CARRY_ID = "carry_id";
	private static final String CARRY_TICKS = "carry_ticks";
	private static final int CARRY_MAX_TICKS = 30 * 20;
	/** After a carry ends the creature keeps its fall / suffocation immunity this long. */
	public static final int CARRY_GRACE_TICKS = 3 * 20;

	/** R: damage per punch, and punches per press. */
	public static final float PUNCH_DAMAGE = 8.0f;
	public static final int PUNCHES = 4;

	/** Passive: +30% movement (walk + sprint) and roughly +30% swim speed. */
	public static final double PASSIVE_SPEED_BONUS = 0.30;
	public static final ResourceLocation PASSIVE_SPEED = com.projecthero.mod.ProjectHeroMod.id("speed_passive_speed");
	private static final ResourceLocation PASSIVE_SWIM = com.projecthero.mod.ProjectHeroMod.id("speed_passive_swim");
	private static final double PASSIVE_SWIM_EFFICIENCY = 0.10;
	/** v0.14.6: a permanent 3-block step assist (base 0.6 + 2.4); Overdrive tops it up to 10. */
	private static final ResourceLocation PASSIVE_STEP = com.projecthero.mod.ProjectHeroMod.id("speed_passive_step");
	private static final double PASSIVE_STEP_BONUS = 2.4;

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

	/** v0.14.7 passive: Regeneration II (amplifier 1). */
	public static final int REGEN_AMPLIFIER = 1;
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
		// R -- Rapid Assault: four punches of 8 on everything in front of you.
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
			if (p.isShiftKeyDown()) {
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
		}, ctx -> { }));

		// Z -- Time Slow: 45 s (v0.14.7) of everything else at 5%. Press again to end it early; the cooldown starts at the end.
		AbilityHandlers.register(KEY, "time_slow", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			if (SuperSpeedTimeSlow.isCasting(p)) {
				SuperSpeedTimeSlow.end(p, true);
			} else {
				SuperSpeedTimeSlow.start(p);
			}
		}));

		// V -- Overdrive: 30 s of the fastest tier; your blows land twice as hard and a red trail follows you.
		AbilityHandlers.register(KEY, "overdrive", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			set(p, OVERDRIVE_UNTIL, p.level().getGameTime() + OVERDRIVE_TICKS);
			set(p, OVERDRIVE_LEFT, OVERDRIVE_TICKS);
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
				PowerToggles.modifier(player, Attributes.STEP_HEIGHT, PASSIVE_STEP, PASSIVE_STEP_BONUS,
						AttributeModifier.Operation.ADD_VALUE);
				applyRegen(player);
				// join / respawn: a phase saved mid-hold (C is not held any more) must not linger
				if (phasing(player) && !player.noPhysics) {
					endPhase(player);
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

	/** Four punches of 8 on everything in front of you; every blow lands in full, even on a player or a mob just hit. */
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

	// ---- passive: Regeneration II ----------------------------------------------------------------

	/** v0.14.7: owning Super Speed keeps a permanent, hidden Regeneration II on you (never weakens a stronger regen). */
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
			ctx.triggerCooldown();
			return;
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
		target.fallDistance = 0;
		set(p, CARRY_ID, target.getId());
		set(p, CARRY_TICKS, CARRY_MAX_TICKS);
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
		int ticks = (int) res(p, CARRY_TICKS) - 1;
		if (!(e instanceof LivingEntity le) || !le.isAlive() || le.getVehicle() != p || ticks <= 0 || !p.isAlive()) {
			releaseCarry(p, stillOurs(p, e));
			return;
		}
		set(p, CARRY_TICKS, ticks);
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
		set(p, CARRY_TICKS, 0);
		MutationVisuals.stopIf(p, "p04.carry");
		if (le == null) {
			return;
		}
		if (le.getVehicle() == p) {
			le.stopRiding();
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

	/** Called at the head of {@code AbilityRouter.dispatchExperimental}: Shift+C on Super Speed starts Phase. */
	public static boolean interceptDispatch(ServerPlayer p, AbilitySlot slot, boolean pressed) {
		if (slot != AbilitySlot.SLOT_6 || !pressed || !p.isShiftKeyDown()) {
			return false;
		}
		Power active = ExperimentalPowers.getActive(p);
		if (active == null || !KEY.equals(active.key()) || !ExperimentalPowers.owns(p, active)) {
			return false;
		}
		startPhase(p);
		return true;
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
		reconcileSpeed(player);
		// the passive is re-asserted every tick (cheap: PowerToggles only touches the attribute on a change)
		PowerToggles.modifier(player, Attributes.MOVEMENT_SPEED, PASSIVE_SPEED, PASSIVE_SPEED_BONUS,
				AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		PowerToggles.modifier(player, Attributes.WATER_MOVEMENT_EFFICIENCY, PASSIVE_SWIM, PASSIVE_SWIM_EFFICIENCY,
				AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(player, Attributes.STEP_HEIGHT, PASSIVE_STEP, PASSIVE_STEP_BONUS, AttributeModifier.Operation.ADD_VALUE);
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

	/**
	 * Single authority for the movement modifiers. Overdrive and Speed Mode never stack -- Overdrive is a strictly
	 * faster tier that replaces Speed Mode while it runs. Called every server tick plus on every state change.
	 */
	private static void reconcileSpeed(ServerPlayer p) {
		boolean overdrive = overdrive(p);
		boolean speedMode = speedMode(p);
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

	private static void speedModeApply(ServerPlayer p) {
		PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, SM_SPEED, 4.7, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		PowerToggles.modifier(p, Attributes.ATTACK_SPEED, SM_ATTACK, 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		PowerToggles.clearModifier(p, Attributes.STEP_HEIGHT, SM_STEP); // v0.14.6: the 3-block passive step covers Speed Mode
		PowerToggles.modifier(p, Attributes.WATER_MOVEMENT_EFFICIENCY, SM_WATER, 1.0, AttributeModifier.Operation.ADD_VALUE);
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
		PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, OD_SPEED, 10.5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		PowerToggles.modifier(p, Attributes.ATTACK_SPEED, OD_ATTACK, 1.5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		// "your attacks x2": melee doubles while Overdrive runs (the Super Speed moves double through overdriveMult)
		PowerToggles.modifier(p, Attributes.ATTACK_DAMAGE, OD_DAMAGE, 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		PowerToggles.modifier(p, Attributes.STEP_HEIGHT, OD_STEP, 7.0, AttributeModifier.Operation.ADD_VALUE); // 3-block passive + 7 = 10
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
