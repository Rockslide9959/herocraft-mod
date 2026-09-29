package com.projecthero.mod.hero.power.p04;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandler;
import com.projecthero.mod.hero.AbilityHandlers;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Power 04 — Super Speed (v0.13.22 revamp: <b>momentum builds while you run</b>).
 *
 * <p>Sprinting on the ground fills a 0..115 Momentum gauge (twice as fast in Speed Mode, three times in
 * Overdrive); it bleeds away a second after you stop. Momentum adds hits to Rapid Assault, distance to Momentum
 * Dash, and is the ammunition for Lightning Throw.
 *
 * <p>R Rapid Assault, G Speed Carry, X Momentum Dash, Z Overdrive ("time slows": everything near you is slowed,
 * your attacks hit twice as hard), V Vortex (run circles that drag mobs in), C Speed Mode, H Phase Vibrate (pass
 * through up to two blocks of wall), N Lightning Throw.
 */
public final class SuperSpeedHandlers {
	public static final String KEY = "power_04_super_speed";

	/** Absolute game time until which Overdrive is running (0 = off). Persisted; read client-side too. */
	public static final String OVERDRIVE_UNTIL = "overdrive_until";
	private static final int OVERDRIVE_TICKS = 30 * 20;
	/** Countdown mirror of {@link #OVERDRIVE_UNTIL} purely so the ability HUD can draw an Overdrive bar. */
	private static final String OVERDRIVE_LEFT = "overdrive_ticks";

	public static final String MOMENTUM = "momentum";
	public static final float MAX_MOMENTUM = 115f;
	private static final int VORTEX_TICKS = 160;
	private static final double VORTEX_RADIUS = 3.5;

	private static final ResourceLocation PASSIVE_STEP = com.projecthero.mod.ProjectHeroMod.id("speed_passive_step");

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

	// --- v0.10.15: Wall Running ---
	private static final ResourceLocation WALLRUN_STEP = com.projecthero.mod.ProjectHeroMod.id("wallrun_step");
	/** How steeply up you have to look (degrees, negative = up) before a wall in front becomes runnable. */
	private static final float WALLRUN_PITCH = -50.0f;
	private static final double WALLRUN_REACH = 0.7;
	private static final double WALLRUN_CLIMB_SPEED = 0.30;

	private SuperSpeedHandlers() {
	}

	private static float res(ServerPlayer p, String name) {
		return BatchA.res(p, KEY, name);
	}

	private static void set(ServerPlayer p, String name, float v) {
		BatchA.set(p, KEY, name, v, 1e12f);
	}

	public static float momentum(ServerPlayer p) {
		return res(p, MOMENTUM);
	}

	public static void register() {
		// R -- Rapid Assault: a blur of blows on everything in front; every 25 Momentum adds another hit.
		AbilityHandlers.register(KEY, "rapid_assault", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			float m = overdriveMult(p);
			int hits = 4 + (int) (momentum(p) / 25f);
			int struck = 0;
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.getEyePosition().add(p.getLookAngle().scale(1.5)), 3.5)) {
				for (int i = 0; i < hits; i++) {
					AbilityHelpers.hurtBurst(p, e, 2.4f * m);
				}
				AbilityHelpers.knockbackFrom(e, p.position(), 0.35 * m);
				ctx.level().sendParticles(ParticleTypes.CRIT, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(),
						hits * 3, 0.35, 0.35, 0.35, 0.3);
				ctx.level().sendParticles(ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(),
						hits, 0.3, 0.3, 0.3, 0.2);
				struck++;
			}
			BatchA.play(p, KEY, "p04.flurry", 12);
			trail(ctx.level(), p);
			AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 1.8f);
			if (struck > 0) {
				AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 2.0f);
			}
			ctx.triggerCooldown();
		}));

		// G -- Speed Carry: snatch up the creature or player you are looking at and run with them. A second press
		// sets them down unharmed.
		AbilityHandlers.register(KEY, "speed_carry", Handlers.instantTicking(ctx -> {
			ServerPlayer p = ctx.player();
			if (com.projecthero.mod.hero.power.GrabHelper.isHolding(ctx)) {
				com.projecthero.mod.hero.power.GrabHelper.dropHeld(ctx);
				trail(ctx.level(), p);
				MutationVisuals.stopIf(p, "p04.carry");
				AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7f, 1.2f);
				ctx.triggerCooldown();
			} else if (com.projecthero.mod.hero.power.GrabHelper.tryGrab(ctx, 6.0, 300)) {
				ctx.actionBar("message.projecthero.ability.grabbed");
				BatchA.play(p, KEY, "grab_pull", 8);
				AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 1.2f);
			}
		}, ctx -> {
			com.projecthero.mod.hero.power.GrabHelper.tick(ctx, 1.8);
			if (com.projecthero.mod.hero.power.GrabHelper.isHolding(ctx)) {
				BatchA.stance(ctx.player(), KEY, "p04.carry");
			} else {
				MutationVisuals.stopIf(ctx.player(), "p04.carry");
			}
		}));

		// X -- Momentum Dash: a directional burst; Momentum carries it up to 80% further.
		AbilityHandlers.register(KEY, "momentum_dash", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			float m = selfMult(overdriveMult(p)) * (1f + 0.8f * momentum(p) / MAX_MOMENTUM);
			Vec3 v = p.getDeltaMovement();
			Vec3 dir = (v.horizontalDistanceSqr() > 0.01) ? new Vec3(v.x, 0, v.z).normalize() : p.getLookAngle();
			Vec3 from = p.position();
			AbilityHelpers.launchSelf(p, new Vec3(dir.x * 1.7 * m, 0.25, dir.z * 1.7 * m));
			afterimage(ctx.level(), from, dir, 3.0 * m);
			BatchA.play(p, KEY, "dash_forward", 10);
			AbilityHelpers.sound(p, SoundEvents.BREEZE_SHOOT, 0.6f, 1.8f);
			ctx.triggerCooldown();
		}));

		// Z -- Overdrive: 30 s of the fastest tier; the world around you crawls and your blows land twice as hard.
		AbilityHandlers.register(KEY, "overdrive", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			set(p, OVERDRIVE_UNTIL, p.level().getGameTime() + OVERDRIVE_TICKS);
			set(p, OVERDRIVE_LEFT, OVERDRIVE_TICKS);
			reconcileSpeed(p);
			p.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, OVERDRIVE_TICKS, 2, false, true, true));
			overdriveBurst(ctx.level(), p);
			BatchA.ring(ctx.level(), p.position().add(0, 0.2, 0), 0.6, ParticleTypes.ELECTRIC_SPARK, 30, 0.8);
			BatchA.play(p, KEY, "power_up", 20);
			AbilityHelpers.sound(p, SoundEvents.WARDEN_SONIC_BOOM, 1.4f, 1.5f);
			AbilityHelpers.sound(p, SoundEvents.GENERIC_EXPLODE, 1.2f, 1.3f);
			ctx.triggerCooldown();
		}));

		// V -- Vortex: hold to run tight circles; the whirlwind drags everything nearby into its eye.
		AbilityHandlers.register(KEY, "vortex", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (res(p, "vortex_ticks") > 0.5f) {
					return;
				}
				if (!ctx.cooldownReady()) {
					ctx.actionBar("message.projecthero.ability.on_cooldown",
							net.minecraft.network.chat.Component.translatable(ctx.ability().nameKey()),
							String.format(java.util.Locale.ROOT, "%.1f", ctx.cooldownRemaining() / 20.0f));
					return;
				}
				set(p, "vortex_ticks", VORTEX_TICKS);
				// start on the circle: the eye of the vortex sits one radius in front of you
				Vec3 f = BatchA.flatLook(p);
				double a = Math.atan2(-f.z, -f.x);
				set(p, "vortex_angle", (float) ((a + Math.PI * 4) % (Math.PI * 2)));
				MutationVisuals.play(p, "spin_arms");
				AbilityHelpers.sound(p, SoundEvents.WIND_CHARGE_THROW, 1.0f, 1.2f);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				endVortex(ctx);
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				float t = res(ctx.player(), "vortex_ticks");
				if (t <= 0.5f) {
					return;
				}
				vortexTick(ctx);
				t -= 1.0f;
				set(ctx.player(), "vortex_ticks", t);
				if (t <= 0.5f) {
					endVortex(ctx);
				}
			}
		});

		AbilityHandlers.register(KEY, "speed_mode", Handlers.toggle(
				ctx -> {
					reconcileSpeed(ctx.player());
					BatchA.play(ctx.player(), KEY, "dash_forward", 10);
					AbilityHelpers.sound(ctx.player(), SoundEvents.BEACON_POWER_SELECT, 0.6f, 2.0f);
				},
				ctx -> reconcileSpeed(ctx.player()),
				ctx -> {
					reconcileSpeed(ctx.player());
					AbilityHelpers.modeAura(ctx.player(), ParticleTypes.ELECTRIC_SPARK, 3);
				}));

		// H -- Phase Vibrate: vibrate your molecules and step through up to two blocks of wall.
		AbilityHandlers.register(KEY, "phase_vibrate", Handlers.instant(SuperSpeedHandlers::phaseVibrate));

		// N -- Lightning Throw: spend your Momentum as a hurled bolt of static.
		AbilityHandlers.register(KEY, "lightning_throw", Handlers.instant(SuperSpeedHandlers::lightningThrow));

		PowerPassives.register(KEY, (player, active) -> {
			if (active) {
				PowerToggles.modifier(player, Attributes.STEP_HEIGHT, PASSIVE_STEP, 0.6, AttributeModifier.Operation.ADD_VALUE);
			} else {
				PowerToggles.clearModifier(player, Attributes.STEP_HEIGHT, PASSIVE_STEP);
				speedModeClear(player);
				clearOverdrive(player);
				PowerToggles.clearModifier(player, Attributes.STEP_HEIGHT, WALLRUN_STEP);
				set(player, OVERDRIVE_UNTIL, 0);
				set(player, OVERDRIVE_LEFT, 0);
				set(player, "wallrun", 0);
				set(player, "vortex_ticks", 0);
			}
		});
		PowerPassives.registerTick(KEY, SuperSpeedHandlers::serverTick);
	}

	// ---- Vortex ------------------------------------------------------------------------------

	private static void endVortex(AbilityContext ctx) {
		if (res(ctx.player(), "vortex_ticks") > 0.5f) {
			set(ctx.player(), "vortex_ticks", 0);
			MutationVisuals.stopIf(ctx.player(), "spin_arms");
			ctx.triggerCooldown();
		}
	}

	/**
	 * One step around the circle. The eye is recovered from where you are and the current angle, so it needs no
	 * stored position (resources cannot hold negative coordinates).
	 */
	private static void vortexTick(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		float m = overdriveMult(p);
		double a = res(p, "vortex_angle");
		Vec3 center = p.position().subtract(Math.cos(a) * VORTEX_RADIUS, 0, Math.sin(a) * VORTEX_RADIUS);
		double step = 0.34;
		double next = (a + step) % (Math.PI * 2);
		set(p, "vortex_angle", (float) next);
		Vec3 target = center.add(Math.cos(next) * VORTEX_RADIUS, 0, Math.sin(next) * VORTEX_RADIUS);
		Vec3 v = target.subtract(p.position());
		p.setDeltaMovement(v.x, Math.min(p.getDeltaMovement().y, 0.1), v.z);
		p.hurtMarked = true;
		p.hasImpulse = true;
		p.resetFallDistance();
		MutationVisuals.ensure(p, "spin_arms");
		int t = (int) res(p, "vortex_ticks");
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, center, 10.0)) {
			Vec3 in = center.subtract(e.position());
			double d = Math.max(0.1, in.horizontalDistance());
			Vec3 pull = new Vec3(in.x / d, 0, in.z / d).scale(Math.min(0.45, 0.12 + d * 0.04));
			// a touch of swirl so they spiral in rather than walk straight
			Vec3 swirl = new Vec3(-in.z / d, 0, in.x / d).scale(0.12);
			e.setDeltaMovement(e.getDeltaMovement().scale(0.6).add(pull).add(swirl).add(0, d < 4 ? 0.09 : 0.0, 0));
			e.hurtMarked = true;
			if (d < 4.5 && t % 10 == 0) {
				AbilityHelpers.hurt(p, e, 3.6f * m);
			}
		}
		for (var proj : level.getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile.class,
				new AABB(center, center).inflate(6.0), pr -> pr.getOwner() != p)) {
			proj.setDeltaMovement(proj.getDeltaMovement().reverse().scale(0.6));
		}
		p.clearFire();
		if (p.tickCount % 2 == 0) {
			BatchA.ring(level, center.add(0, 0.3 + (t % 6) * 0.3, 0), VORTEX_RADIUS * (0.6 + (t % 3) * 0.2),
					ParticleTypes.CLOUD, 10, 0.05);
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, center.x, center.y + 1, center.z, 2, 1.6, 0.5, 1.6, 0.1);
		}
		if (p.tickCount % 5 == 0) {
			AbilityHelpers.sound(p, SoundEvents.WIND_CHARGE_THROW, 0.5f, 1.6f);
		}
	}

	// ---- Phase Vibrate -----------------------------------------------------------------------

	private static void phaseVibrate(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		Vec3 d = BatchA.flatLook(p);
		Vec3 from = p.position();
		boolean wall = false;
		int solidColumns = 0;
		BlockPos lastColumn = null;
		Vec3 dest = null;
		for (double dist = 0.5; dist <= 4.0; dist += 0.25) {
			Vec3 at = from.add(d.scale(dist));
			BlockPos feet = BlockPos.containing(at.x, from.y + 0.1, at.z);
			BlockPos head = feet.above();
			boolean solid = !level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
					|| !level.getBlockState(head).getCollisionShape(level, head).isEmpty();
			if (solid) {
				if (level.getBlockState(feet).getDestroySpeed(level, feet) < 0 || level.getBlockState(head).getDestroySpeed(level, head) < 0) {
					break; // bedrock, barriers, end portal frames: never
				}
				if (!feet.equals(lastColumn)) {
					solidColumns++;
					lastColumn = feet;
				}
				if (solidColumns > 2) {
					break;
				}
				wall = true;
				continue;
			}
			if (!wall) {
				if (dist > 1.5) {
					break; // no wall right in front of you
				}
				continue;
			}
			AABB box = p.getBoundingBox().move(at.x - from.x, 0, at.z - from.z);
			if (level.noCollision(p, box)) {
				dest = at;
				break;
			}
		}
		if (dest == null) {
			ctx.actionBar("message.projecthero.speed.cannot_phase");
			return;
		}
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, from.x, from.y + 1, from.z, 20, 0.3, 0.6, 0.3, 0.1);
		afterimage(level, from, d, from.distanceTo(dest));
		p.teleportTo(dest.x, dest.y, dest.z);
		p.resetFallDistance();
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, dest.x, dest.y + 1, dest.z, 20, 0.3, 0.6, 0.3, 0.1);
		BatchA.play(p, KEY, "p04.vibrate", 10);
		AbilityHelpers.sound(p, SoundEvents.BEACON_DEACTIVATE, 0.7f, 2.0f);
		AbilityHelpers.sound(p, SoundEvents.CHORUS_FRUIT_TELEPORT, 0.5f, 1.6f);
		ctx.triggerCooldown();
	}

	// ---- Lightning Throw ---------------------------------------------------------------------

	private static void lightningThrow(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		float mom = momentum(p);
		if (mom < 30f) {
			ctx.actionBar("message.projecthero.speed.no_momentum");
			return;
		}
		set(p, MOMENTUM, 0);
		float dmg = (8f + mom * 0.14f) * overdriveMult(p);
		LivingEntity target = AbilityHelpers.raycastEntity(p, 32.0);
		Vec3 start = AbilityHelpers.handPosition(p);
		Vec3 end = target != null ? target.position().add(0, target.getBbHeight() * 0.5, 0) : AbilityHelpers.aimPoint(p, 32.0);
		zigzag(level, start, end);
		if (target != null) {
			AbilityHelpers.hurtBurst(p, target, dmg);
			AbilityHelpers.applyControl(target, MobEffects.MOVEMENT_SLOWDOWN, 30, 2);
			// the static arcs on to one more creature close by
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, end, 5.0)) {
				if (e != target) {
					zigzag(level, end, e.position().add(0, e.getBbHeight() * 0.5, 0));
					AbilityHelpers.hurt(p, e, dmg * 0.5f);
					break;
				}
			}
		} else {
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, end, 2.0)) {
				AbilityHelpers.hurt(p, e, dmg * 0.6f);
			}
		}
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z, 25, 0.3, 0.3, 0.3, 0.3);
		level.sendParticles(ParticleTypes.FLASH, end.x, end.y, end.z, 1, 0, 0, 0, 0);
		BatchA.play(p, KEY, "throw_right", 12);
		AbilityHelpers.sound(p, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.8f, 1.6f);
		AbilityHelpers.sound(p, SoundEvents.BEACON_POWER_SELECT, 0.6f, 2.0f);
		ctx.triggerCooldown();
	}

	/** A jagged electric line from {@code a} to {@code b}. */
	private static void zigzag(ServerLevel level, Vec3 a, Vec3 b) {
		Vec3 prev = a;
		int segs = Math.max(3, (int) (a.distanceTo(b) / 1.5));
		java.util.Random r = new java.util.Random((long) (a.x * 31 + b.z * 17));
		for (int i = 1; i <= segs; i++) {
			Vec3 at = a.lerp(b, i / (double) segs);
			if (i < segs) {
				at = at.add((r.nextDouble() - 0.5) * 0.7, (r.nextDouble() - 0.5) * 0.7, (r.nextDouble() - 0.5) * 0.7);
			}
			AbilityHelpers.line(level, prev, at, ParticleTypes.ELECTRIC_SPARK, 4.0);
			prev = at;
		}
		AbilityHelpers.line(level, a, b, ParticleTypes.WAX_OFF, 1.0);
	}

	/** A streak of fading after-images along a dash. */
	private static void afterimage(ServerLevel level, Vec3 from, Vec3 dir, double length) {
		for (double s = 0; s <= length; s += 0.6) {
			Vec3 at = from.add(dir.scale(s));
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 1.0, at.z, 2, 0.15, 0.5, 0.15, 0.01);
			level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.1, at.z, 1, 0.1, 0.02, 0.1, 0.005);
		}
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

		boolean speedMode = ExperimentalPowers.isToggled(player, power,
				power.ability(com.projecthero.mod.hero.AbilitySlot.SLOT_6));

		// ---- Momentum: builds while sprinting on the ground, bleeds away a second after you stop ----
		float mom = momentum(player);
		if (player.isSprinting() && (player.onGround() || speedMode || overdrive)) {
			float gain = overdrive ? 1.5f : speedMode ? 1.0f : 0.5f;
			set(player, MOMENTUM, Math.min(MAX_MOMENTUM, mom + gain));
			set(player, "mom_idle", 20);
		} else if (mom > 0f) {
			float idle = res(player, "mom_idle");
			if (idle > 0.5f) {
				set(player, "mom_idle", idle - 1);
			} else {
				set(player, MOMENTUM, Math.max(0f, mom - 1.2f));
			}
		}

		if (!(player.level() instanceof ServerLevel sl)) {
			return;
		}

		Vec3 v = player.getDeltaMovement();
		boolean moving = v.horizontalDistanceSqr() > 0.02 || player.isSprinting();

		// Speed Mode burns hunger 50% faster than normal as a balancing cost.
		if (speedMode && !player.getAbilities().instabuild && moving) {
			player.getFoodData().addExhaustion(0.05f);
		}

		// Overdrive: bursting-with-power visuals, and "time slows" -- everything hostile near you crawls.
		if (overdrive) {
			if (player.tickCount % 2 == 0) {
				overdriveBurst(sl, player);
			}
			if (player.tickCount % 5 == 0) {
				for (LivingEntity e : AbilityHelpers.enemiesAround(player, player.position(), 12.0)) {
					AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 14, 3);
					if (!(e instanceof Player)) {
						e.setDeltaMovement(e.getDeltaMovement().scale(0.5));
					}
				}
			}
		}

		// Run THROUGH living things: anything within ~2 blocks while you're moving fast takes a hit.
		if ((speedMode || overdrive) && moving) {
			for (LivingEntity e : AbilityHelpers.enemiesAround(player, player.position(), 2.0)) {
				if (e.invulnerableTime <= 0) {
					AbilityHelpers.hurt(player, e, 6.0f * overdriveMult(player));
					AbilityHelpers.knockbackFrom(e, player.position(), 0.5);
				}
			}
		}

		// Running on water: splash and footfall feedback (the client mixin keeps the player on the surface).
		if ((speedMode || overdrive) && moving && !player.isShiftKeyDown() && !player.getAbilities().flying) {
			BlockPos feet = player.blockPosition();
			var atFeet = sl.getFluidState(feet);
			var below = sl.getFluidState(feet.below());
			boolean onWaterSurface = (atFeet.is(FluidTags.WATER) || below.is(FluidTags.WATER))
					&& !player.isInWater();
			if (onWaterSurface) {
				double surfaceY = player.getY();
				sl.sendParticles(ParticleTypes.SPLASH,
						player.getX(), surfaceY + 0.05, player.getZ(), 12, 0.35, 0.02, 0.35, 0.12);
				Vec3 back = new Vec3(v.x, 0, v.z);
				back = back.lengthSqr() > 1.0e-4 ? back.normalize() : player.getLookAngle();
				sl.sendParticles(ParticleTypes.BUBBLE,
						player.getX() - back.x * 0.6, surfaceY, player.getZ() - back.z * 0.6,
						6, 0.2, 0.02, 0.2, 0.02);
				if (player.tickCount % 3 == 0) {
					sl.playSound(null, player.blockPosition(), SoundEvents.PLAYER_SPLASH_HIGH_SPEED,
							net.minecraft.sounds.SoundSource.PLAYERS, 0.9f, 1.2f + sl.random.nextFloat() * 0.3f);
				}
			}
		}

		// Sprint trail only while Speed Mode / Overdrive is on -- not on every ordinary sprint.
		if ((speedMode || overdrive) && player.isSprinting() && player.tickCount % 3 == 0) {
			bodyTrail(sl, player, 1);
			if (momentum(player) > MAX_MOMENTUM * 0.6f) {
				sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.0, player.getZ(), 2, 0.3, 0.5, 0.3, 0.05);
			}
		}

		wallRunTick(player, sl);
	}

	/**
	 * Wall Running: sprint into a wall while looking steeply up it and you run straight up instead of
	 * stalling against it; topping out gives one mantle nudge over the edge.
	 */
	private static void wallRunTick(ServerPlayer p, ServerLevel sl) {
		boolean wasClimbing = res(p, "wallrun") > 0.5f;
		boolean climbing = p.isSprinting() && p.horizontalCollision && !p.isInWater()
				&& !p.getAbilities().flying && p.getXRot() < WALLRUN_PITCH && wallAhead(p);
		if (climbing) {
			Vec3 v = p.getDeltaMovement();
			p.setDeltaMovement(v.x, WALLRUN_CLIMB_SPEED, v.z);
			p.resetFallDistance();
			p.hasImpulse = true;
			p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(p));
			PowerToggles.modifier(p, Attributes.STEP_HEIGHT, WALLRUN_STEP, 1.2, AttributeModifier.Operation.ADD_VALUE);
			set(p, "wallrun", 1);
			if (p.tickCount % 2 == 0) {
				sl.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.2, p.getZ(), 3, 0.2, 0.05, 0.2, 0.01);
			}
			return;
		}
		if (wasClimbing) {
			Vec3 look = p.getLookAngle();
			Vec3 v = p.getDeltaMovement();
			p.setDeltaMovement(v.x + look.x * 0.5, Math.max(v.y, 0.35), v.z + look.z * 0.5);
			p.hasImpulse = true;
			p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(p));
		}
		set(p, "wallrun", 0);
		PowerToggles.clearModifier(p, Attributes.STEP_HEIGHT, WALLRUN_STEP);
	}

	/** True if there is a solid block directly ahead (horizontally) at eye height, within wall-run reach. */
	private static boolean wallAhead(ServerPlayer p) {
		Vec3 look = new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z);
		if (look.lengthSqr() < 1.0e-4) {
			return false;
		}
		look = look.normalize();
		BlockPos bp = BlockPos.containing(p.getEyePosition().add(look.scale(WALLRUN_REACH)));
		return !p.level().getBlockState(bp).getCollisionShape(p.level(), bp).isEmpty();
	}

	private static void overdriveBurst(ServerLevel level, ServerPlayer p) {
		double h = p.getBbHeight();
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + h * 0.5, p.getZ(),
				14, 0.5, h * 0.5, 0.5, 0.25);
		level.sendParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY() + h * 0.6, p.getZ(),
				1, 0.4, 0.4, 0.4, 0.0);
		level.sendParticles(ParticleTypes.CRIT, p.getX(), p.getY() + h * 0.5, p.getZ(),
				10, 0.5, h * 0.5, 0.5, 0.2);
	}

	/**
	 * The movement/mining/eating multiplier of the current Super Speed state, read from the synced
	 * attachment so it works on both sides. 1.0 = no boost.
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
		Power power = Powers.byKey(KEY);
		boolean overdrive = ExperimentalPowers.getResource(p, power, OVERDRIVE_UNTIL) > p.level().getGameTime();
		boolean speedMode = ExperimentalPowers.isToggled(p, power,
				power.ability(com.projecthero.mod.hero.AbilitySlot.SLOT_6));
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
		PowerToggles.modifier(p, Attributes.STEP_HEIGHT, SM_STEP, 0.8, AttributeModifier.Operation.ADD_VALUE);
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
		PowerToggles.modifier(p, Attributes.STEP_HEIGHT, OD_STEP, 10.0, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.FALL_DAMAGE_MULTIPLIER, OD_FALL, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	private static void clearOverdrive(ServerPlayer p) {
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, OD_SPEED);
		PowerToggles.clearModifier(p, Attributes.ATTACK_SPEED, OD_ATTACK);
		PowerToggles.clearModifier(p, Attributes.ATTACK_DAMAGE, OD_DAMAGE);
		PowerToggles.clearModifier(p, Attributes.STEP_HEIGHT, OD_STEP);
		PowerToggles.clearModifier(p, Attributes.FALL_DAMAGE_MULTIPLIER, OD_FALL);
	}

	private static void trail(ServerLevel level, ServerPlayer p) {
		bodyTrail(level, p, 3);
	}

	/** The speed trail: a low scuff of particles right at the player's feet, trailing behind the direction of travel. */
	private static void bodyTrail(ServerLevel level, ServerPlayer p, int density) {
		Vec3 v = p.getDeltaMovement();
		Vec3 dir = v.horizontalDistanceSqr() > 1.0e-4 ? new Vec3(v.x, 0, v.z).normalize() : p.getLookAngle();
		double bx = p.getX() - dir.x * 0.5;
		double bz = p.getZ() - dir.z * 0.5;
		double y = p.getY() + 0.05;
		level.sendParticles(ParticleTypes.CLOUD, bx, y, bz, density, 0.15, 0.02, 0.15, 0.004);
		level.sendParticles(ParticleTypes.CRIT, bx, y + 0.1, bz, density, 0.15, 0.06, 0.15, 0.02);
	}
}
