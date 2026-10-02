package com.projecthero.mod.hero.power.p03;

import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandler;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.PowerPassives;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.Handlers;
import com.projecthero.mod.hero.power.HeroFlight;
import com.projecthero.mod.hero.revamp.batcha.BatchA;
import com.projecthero.mod.hero.visual.MutationVisuals;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Power 03 — Flight (v0.13.22 revamp: <b>speed tiers + the sonic boom</b>). Uses {@link HeroFlight}
 * (independent of Thor flight). Flight itself never runs out.
 *
 * <p>Sprinting while flying climbs three speed tiers (2 s each: ~25 / 32 / 39 / 46 blocks/s -- the client's
 * directional flight caps read the synced {@code speed_tier}, see {@code DirectionalFlightModel#flightPower}); hitting the top tier cracks the sound barrier
 * with a sonic boom ring. Flight toggles on X or by double-tapping jump (client, {@code RevampClientA}).
 *
 * <p>R Air Dash strike, G Dive Bomb, X Flight, Z Orbital Drop, V Carry, C Sonic Flight, H Slipstream (allies are
 * towed along behind you with Slow Falling), N Barrel Roll (sideways dodge with half a second of i-frames).
 */
public final class FlightHandlers {
	public static final String KEY = "power_03_flight";
	/** Flying speed ability value per tier: vanilla sprint-flight terminal speed scales with it. */
	private static final float[] TIER_FLY_SPEED = { HeroFlight.HERO_FLYING_SPEED, 0.075f, 0.09f, 0.105f };
	private static final int TIER_TICKS = 40;
	private static final int ORBIT_RISE_MAX = 14;
	private static final int ORBIT_APEX = 6;
	private static final int SLIP_TICKS = 240;
	private static final int ROLL_IFRAMES = 10;

	private FlightHandlers() {
	}

	private static float res(ServerPlayer p, String name) {
		return BatchA.res(p, KEY, name);
	}

	private static void set(ServerPlayer p, String name, float v) {
		BatchA.set(p, KEY, name, v, 1e9f);
	}

	/** True during a Barrel Roll's i-frame window (see {@code RevampBatchA}'s damage veto). */
	public static boolean rolling(ServerPlayer p) {
		return ExperimentalPowers.owns(p, KEY) && res(p, "roll_ticks") > 0.5f;
	}

	/** Stop Sonic Flight (manual or timed) and only now start its cooldown. */
	private static void endSonicFlight(AbilityContext ctx) {
		if (ctx.resource("sonic_ticks") <= 0.0f) {
			return;
		}
		ctx.setResource("sonic_ticks", 0, 25 * 20);
		AbilityHelpers.sound(ctx.player(), SoundEvents.WARDEN_SONIC_BOOM, 0.5f, 0.9f);
		ctx.triggerCooldown();
	}

	public static void register() {
		// R -- Air Dash: a burst forward that shoulder-checks anything you dash into.
		AbilityHandlers.register(KEY, "air_dash", Handlers.instantTicking(ctx -> {
			ServerPlayer p = ctx.player();
			double power = HeroFlight.isFlying(p) ? 2.1 : 1.25;
			AbilityHelpers.addImpulse(p, p.getLookAngle().scale(power));
			AbilityHelpers.burst(ctx.level(), p.position(), ParticleTypes.CLOUD, 16, 0.3);
			BatchA.ring(ctx.level(), p.position().add(0, 1, 0), 0.4, ParticleTypes.CLOUD, 12, 0.25);
			AbilityHelpers.sound(p, SoundEvents.BREEZE_SHOOT, 0.8f, 1.4f);
			BatchA.play(p, KEY, "dash_forward", 12);
			set(p, "dash_ticks", 6);
			ctx.triggerCooldown();
		}, ctx -> {
			ServerPlayer p = ctx.player();
			int t = (int) res(p, "dash_ticks");
			if (t <= 0) {
				return;
			}
			set(p, "dash_ticks", t - 1);
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position().add(p.getLookAngle().scale(1.0)), 1.9)) {
				if (e.invulnerableTime <= 0) {
					AbilityHelpers.hurt(p, e, 10.0f);
					AbilityHelpers.knockbackFrom(e, p.position(), 1.4);
					ctx.level().sendParticles(ParticleTypes.CRIT, e.getX(), e.getY() + 1, e.getZ(), 8, 0.3, 0.3, 0.3, 0.2);
				}
			}
		}));

		// G -- Dive Bomb: drop like a stone -- dead straight down, all the way to the ground -- then detonate.
		AbilityHandlers.register(KEY, "dive_bomb", Handlers.instantTicking(ctx -> {
			ServerPlayer p = ctx.player();
			p.setDeltaMovement(0.0, -2.8, 0.0);
			p.hurtMarked = true;
			p.hasImpulse = true;
			p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(p));
			set(p, "diving", 1);
			set(p, "dive_from_y", (float) (p.getY() + 1000));
			MutationVisuals.play(p, "p03.dive");
			AbilityHelpers.sound(p, SoundEvents.BREEZE_JUMP, 0.9f, 0.6f);
			ctx.triggerCooldown();
		}, ctx -> {
			ServerPlayer p = ctx.player();
			if (res(p, "diving") < 0.5f) {
				return;
			}
			if (!p.onGround() && !p.isInWater()) {
				p.setDeltaMovement(0.0, Math.min(p.getDeltaMovement().y, -2.8), 0.0);
				p.hurtMarked = true;
				p.hasImpulse = true;
				p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(p));
				p.resetFallDistance();
				MutationVisuals.ensure(p, "p03.dive");
				ctx.level().sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 1.0, p.getZ(), 6, 0.2, 0.4, 0.2, 0.02);
				return;
			}
			double height = Math.max(0.0, res(p, "dive_from_y") - 1000 - p.getY());
			float meteor = com.projecthero.mod.hero.power.PowerCombos.meteorSlamBonus(p, KEY);
			// 18 at a short drop, climbing ~1.9/block to an 80 cap.
			float dmg = Math.min(80.0f, 18.0f + (float) (height * 1.9)) + meteor;
			double r = Math.min(12.0, 3.0 + height * 0.18);
			impact(ctx, dmg, r, 1.0 + height * 0.05);
			set(p, "diving", 0);
			set(p, "dive_from_y", 0);
		}));

		// X -- Flight (also toggled by double-tapping jump). Sprinting while flying climbs the speed tiers.
		AbilityHandlers.register(KEY, "flight_toggle", new AbilityHandler() {
			@Override
			public void onToggleOn(AbilityContext ctx) {
				if (!HeroFlight.startFlying(ctx.player(), ctx.power())) {
					ctx.setToggled(false);
					return;
				}
				set(ctx.player(), "tier_ticks", 0);
				set(ctx.player(), "speed_tier", 0);
			}

			@Override
			public void onToggleOff(AbilityContext ctx) {
				HeroFlight.setFlying(ctx.player(), false);
				set(ctx.player(), "tier_ticks", 0);
				set(ctx.player(), "speed_tier", 0);
			}

			@Override
			public void onToggleTick(AbilityContext ctx) {
				ServerPlayer pl = ctx.player();
				if (!HeroFlight.isFlying(pl)) {
					ctx.setToggled(false);
					set(pl, "tier_ticks", 0);
					set(pl, "speed_tier", 0);
					return;
				}
				speedTiers(ctx);
				if (pl.tickCount % 2 == 0 && pl.getDeltaMovement().lengthSqr() > 0.35
						&& com.projecthero.mod.hero.power.PowerCombos.flameFlightTrail(pl)) {
					ctx.level().sendParticles(ParticleTypes.FLAME, pl.getX(), pl.getY() + 0.3, pl.getZ(), 3, 0.15, 0.15, 0.15, 0.01);
				}
			}
		});

		// Z -- Orbital Drop: rocket straight up, hang at the top for a heartbeat, then come down like a meteor.
		AbilityHandlers.register(KEY, "orbital_drop", Handlers.instantTicking(ctx -> {
			ServerPlayer p = ctx.player();
			if (res(p, "orbit_phase") > 0.5f) {
				return;
			}
			set(p, "orbit_phase", 1);
			set(p, "orbit_ticks", 0);
			set(p, "orbit_last_y", (float) (p.getY() + 1000));
			set(p, "no_fall_until", p.level().getGameTime() + 400);
			AbilityHelpers.launchSelf(p, new Vec3(0, 3.0, 0));
			ServerLevel level = ctx.level();
			level.sendParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY(), p.getZ(), 1, 0, 0, 0, 0);
			BatchA.ring(level, p.position().add(0, 0.1, 0), 0.5, ParticleTypes.CLOUD, 24, 0.45);
			AbilityHelpers.sound(p, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.4f, 0.6f);
			AbilityHelpers.sound(p, SoundEvents.BREEZE_JUMP, 1.0f, 0.5f);
			MutationVisuals.play(p, "p03.rise");
			ctx.triggerCooldown();
		}, FlightHandlers::orbitTick));

		// V -- Carry: pick up the creature/player you are looking at and fly them around.
		AbilityHandlers.register(KEY, "carry", new AbilityHandler() {
			@Override
			public void onToggleOn(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				LivingEntity t = AbilityHelpers.raycastEntity(p, 8.0);
				if (t == null || !AbilityHelpers.isValidGrabTarget(t, p)) {
					ctx.setToggled(false);
					ctx.actionBar("message.projecthero.ability.grabbed");
					return;
				}
				ctx.setResource("carry_id", t.getId(), 1.0e9f);
				BatchA.play(p, KEY, "grab_pull", 10);
				AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7f, 0.8f);
			}

			@Override
			public void onToggleOff(AbilityContext ctx) {
				drop(ctx);
			}

			@Override
			public void onToggleTick(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				Entity e = ctx.level().getEntity((int) ctx.resource("carry_id"));
				if (!(e instanceof LivingEntity le) || !le.isAlive() || p.distanceToSqr(e) > 400) {
					ctx.setToggled(false);
					drop(ctx);
					return;
				}
				double y = p.getY() - le.getBbHeight() - 0.15;
				le.setPos(p.getX(), y, p.getZ());
				le.setDeltaMovement(Vec3.ZERO);
				le.fallDistance = 0;
				le.hurtMarked = true;
				le.resetFallDistance();
			}

			private void drop(AbilityContext ctx) {
				Entity e = ctx.level().getEntity((int) ctx.resource("carry_id"));
				ctx.setResource("carry_id", 0, 1.0e9f);
				MutationVisuals.stopIf(ctx.player(), "p03.carry");
				if (e instanceof LivingEntity le && le.isAlive() && !le.onGround()) {
					le.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 0, false, false, false));
				}
			}
		});

		// C -- Sonic Flight: up to 25 seconds of max-speed flight throwing 11-damage shockwaves. Press again to end
		// it early; the cooldown only begins once the ability actually ends.
		AbilityHandlers.register(KEY, "sonic_flight", Handlers.instantTicking(ctx -> {
			ServerPlayer p = ctx.player();
			if (ctx.resource("sonic_ticks") > 0.5f) {
				endSonicFlight(ctx); // manual early stop
				return;
			}
			if (!HeroFlight.isFlying(p)) {
				HeroFlight.setFlying(p, true);
			}
			ctx.setResource("sonic_ticks", 25 * 20, 25 * 20);
			sonicBoom(ctx.level(), p, 4.0f);
			BatchA.play(p, KEY, "power_up", 10);
		}, ctx -> {
			int t = (int) ctx.resource("sonic_ticks");
			if (t <= 0) {
				return;
			}
			ctx.setResource("sonic_ticks", t - 1, 25 * 20);
			ServerPlayer p = ctx.player();
			// v0.14.16: the client's directional flight drives Sonic Flight along the look at 50 b/s
			// (flight.DirectionalFlightModel#flightPower). This used to add a 0.9 impulse a tick and push it to the client,
			// which piled up into a huge stale server velocity; now the server just keeps its own copy in step (no packet)
			// for the shockwave check and the trail below.
			p.setDeltaMovement(p.getLookAngle().scale(com.projecthero.mod.flight.DirectionalFlightModel.FLIGHT_POWER_SONIC_SPEED));
			Vec3 behind = p.position().subtract(p.getDeltaMovement().normalize().scale(0.8));
			ctx.level().sendParticles(ParticleTypes.CLOUD, behind.x, behind.y + 0.3, behind.z, 4, 0.15, 0.15, 0.15, 0.01);
			ctx.level().sendParticles(ParticleTypes.SONIC_BOOM, behind.x, behind.y + 0.3, behind.z, 1, 0.0, 0.0, 0.0, 0.0);
			if (t % 10 == 0 && p.getDeltaMovement().lengthSqr() > 0.2) {
				for (LivingEntity e : AbilityHelpers.hostilesAround(p, p.position(), 4.5)) {
					AbilityHelpers.hurt(p, e, 11.0f);
					AbilityHelpers.knockbackFrom(e, p.position(), 1.6);
				}
				ctx.level().sendParticles(ParticleTypes.SONIC_BOOM, p.getX(), p.getY() + 0.5, p.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
				ctx.level().sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.5, p.getZ(), 30, 2.5, 1.0, 2.5, 0.15);
				AbilityHelpers.sound(p, SoundEvents.WARDEN_SONIC_BOOM, 0.6f, 1.8f);
			}
			if (t - 1 <= 0) {
				endSonicFlight(ctx); // ran its full course
			}
		}));

		// H -- Slipstream: for 12 s squad-mates, your pets and villagers near you are pulled along in your wake.
		AbilityHandlers.register(KEY, "slipstream", Handlers.instantTicking(ctx -> {
			ServerPlayer p = ctx.player();
			set(p, "slip_ticks", SLIP_TICKS);
			BatchA.play(p, KEY, "p03.beckon", 14);
			AbilityHelpers.sound(p, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 0.8f, 1.2f);
			BatchA.ring(ctx.level(), p.position().add(0, 0.2, 0), 1.0, ParticleTypes.CLOUD, 20, 0.25);
			ctx.triggerCooldown();
		}, FlightHandlers::slipTick));

		// N -- Barrel Roll: a snap roll to the side (alternating) with half a second of i-frames.
		AbilityHandlers.register(KEY, "barrel_roll", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			float side = res(p, "roll_side") > 0.5f ? -1f : 1f;
			set(p, "roll_side", side > 0 ? 1 : 0);
			Vec3 look = p.getLookAngle();
			Vec3 right = look.cross(new Vec3(0, 1, 0));
			right = right.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : right.normalize();
			AbilityHelpers.addImpulse(p, right.scale(side * 1.7).add(look.scale(0.3)).add(0, 0.15, 0));
			set(p, "roll_ticks", ROLL_IFRAMES);
			BatchA.play(p, KEY, side > 0 ? "p03.roll_right" : "p03.roll_left", 12);
			ServerLevel level = ctx.level();
			for (int i = 0; i < 12; i++) {
				double a = i / 12.0 * Math.PI * 2;
				Vec3 at = p.position().add(0, 1.0, 0).add(right.scale(Math.cos(a) * 0.8)).add(0, Math.sin(a) * 0.8, 0);
				level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 1, 0, 0, 0, 0.0);
			}
			AbilityHelpers.sound(p, SoundEvents.BREEZE_SLIDE, 1.0f, 1.3f);
			ctx.triggerCooldown();
		}));

		PowerPassives.register(KEY, (player, active) -> {
			if (!active) {
				set(player, "speed_tier", 0);
				set(player, "tier_ticks", 0);
				set(player, "orbit_phase", 0);
				set(player, "slip_ticks", 0);
			}
		});
		PowerPassives.registerTick(KEY, FlightHandlers::passiveTick);
	}

	// ---- flight upkeep ---------------------------------------------------------------------------

	private static void passiveTick(ServerPlayer p) {
		BatchA.countDown(p, KEY, "roll_ticks");
		// the stance pose: diving / orbiting poses are owned by those moves; otherwise carry, superman or hover
		boolean flying = HeroFlight.isFlying(p);
		if (res(p, "diving") > 0.5f || res(p, "orbit_phase") > 0.5f) {
			return;
		}
		boolean carrying = res(p, "carry_id") > 0.5f;
		boolean fast = res(p, "sonic_ticks") > 0.5f || (flying && p.isSprinting());
		if (carrying) {
			BatchA.stance(p, KEY, "p03.carry");
			set(p, "stance_on", 1);
		} else if (flying && fast) {
			BatchA.stance(p, KEY, "p03.superman");
			set(p, "stance_on", 1);
		} else if (flying) {
			BatchA.stance(p, KEY, "float_arms");
			set(p, "stance_on", 1);
		} else if (res(p, "stance_on") > 0.5f) {
			// only take down a stance Flight itself put on (float_arms is shared with other powers)
			set(p, "stance_on", 0);
			MutationVisuals.stopIf(p, "p03.superman");
			MutationVisuals.stopIf(p, "float_arms");
			MutationVisuals.stopIf(p, "p03.carry");
		}
	}

	/** Sprinting while flying climbs a tier every 2 s; letting go of sprint bleeds them off fast. */
	private static void speedTiers(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		float ticks = res(p, "tier_ticks");
		boolean pushing = p.isSprinting() || res(p, "sonic_ticks") > 0.5f;
		ticks = pushing ? Math.min(ticks + 1, TIER_TICKS * 3 + 1) : Math.max(0, ticks - 4);
		set(p, "tier_ticks", ticks);
		int tier = (int) Math.min(3, ticks / TIER_TICKS);
		int was = (int) res(p, "speed_tier");
		if (tier != was) {
			set(p, "speed_tier", tier);
			if (!p.getAbilities().instabuild) {
				p.getAbilities().setFlyingSpeed(TIER_FLY_SPEED[tier]);
				p.onUpdateAbilities();
			}
			if (tier == 3 && was < 3) {
				sonicBoom(ctx.level(), p, 6.0f); // breaking the sound barrier
			} else if (tier > was) {
				AbilityHelpers.sound(p, SoundEvents.BREEZE_SHOOT, 0.6f, 0.8f + tier * 0.2f);
			}
		}
		if (tier >= 2 && p.tickCount % 2 == 0) {
			Vec3 v = p.getLookAngle();
			Vec3 behind = p.position().add(0, 0.9, 0).subtract(v.scale(1.2));
			ctx.level().sendParticles(ParticleTypes.CLOUD, behind.x, behind.y, behind.z, tier, 0.2, 0.2, 0.2, 0.0);
		}
	}

	/** A ring of shock that bursts out around you: small damage and a hard shove to anything close. */
	private static void sonicBoom(ServerLevel level, ServerPlayer p, float damage) {
		Vec3 c = p.position().add(0, 1.0, 0);
		Vec3 look = p.getLookAngle();
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		right = right.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : right.normalize();
		Vec3 up = right.cross(look).normalize();
		for (int i = 0; i < 24; i++) {
			double a = i / 24.0 * Math.PI * 2;
			Vec3 dir = right.scale(Math.cos(a)).add(up.scale(Math.sin(a)));
			Vec3 at = c.add(dir.scale(1.6));
			level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 1, dir.x * 0.6, dir.y * 0.6, dir.z * 0.6, 1.0);
			if (i % 6 == 0) {
				level.sendParticles(ParticleTypes.SONIC_BOOM, at.x, at.y, at.z, 1, 0, 0, 0, 0);
			}
		}
		level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.3f);
		level.playSound(null, p.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1.8f);
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, c, 5.0)) {
			AbilityHelpers.hurt(p, e, damage);
			AbilityHelpers.knockbackFrom(e, p.position(), 1.4);
		}
	}

	/** The landing blast shared by Dive Bomb and Orbital Drop. */
	private static void impact(AbilityContext ctx, float dmg, double r, double kb) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), r)) {
			AbilityHelpers.hurt(p, e, dmg);
			AbilityHelpers.knockbackFrom(e, p.position(), kb);
			AbilityHelpers.push(e, new Vec3(0, 0.5, 0));
		}
		p.resetFallDistance();
		BatchA.play(p, KEY, "hero_landing", 20);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, p.getX(), p.getY(), p.getZ(), 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY(), p.getZ(), 60, r / 2, 0.1, r / 2, 0.05);
		BatchA.ring(level, p.position().add(0, 0.2, 0), 0.8, ParticleTypes.CLOUD, 32, 0.6);
		BatchA.debrisRing(level, p.position(), r * 0.5, 20);
		BatchA.debrisRing(level, p.position(), r * 0.9, 28);
		AbilityHelpers.sound(p, SoundEvents.GENERIC_EXPLODE.value(), 1.0f, 0.9f);
	}

	private static void orbitTick(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		int phase = (int) res(p, "orbit_phase");
		if (phase <= 0) {
			return;
		}
		ServerLevel level = ctx.level();
		float t = res(p, "orbit_ticks") + 1;
		set(p, "orbit_ticks", t);
		p.resetFallDistance();
		if (!p.isAlive() || t > 200) {
			set(p, "orbit_phase", 0);
			return;
		}
		if (phase == 1) {
			// ---- rising: straight up for up to 14 ticks (~30 blocks), stopping at a ceiling ----
			float lastY = res(p, "orbit_last_y") - 1000;
			boolean blocked = t > 2 && p.getY() - lastY < 0.2;
			set(p, "orbit_last_y", (float) (p.getY() + 1000));
			if (t >= ORBIT_RISE_MAX || blocked) {
				set(p, "orbit_phase", 2);
				set(p, "orbit_ticks", 0);
				AbilityHelpers.launchSelf(p, Vec3.ZERO);
				return;
			}
			AbilityHelpers.launchSelf(p, new Vec3(0, 3.0, 0));
			MutationVisuals.ensure(p, "p03.rise");
			level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() - 0.5, p.getZ(), 5, 0.2, 0.4, 0.2, 0.02);
			level.sendParticles(ParticleTypes.FIREWORK, p.getX(), p.getY() - 0.3, p.getZ(), 2, 0.1, 0.2, 0.1, 0.02);
		} else if (phase == 2) {
			// ---- the apex: hang for a heartbeat, aiming ----
			p.setDeltaMovement(0, 0.02, 0);
			p.hurtMarked = true;
			MutationVisuals.ensure(p, "float_arms");
			level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 2, 0.4, 0.4, 0.4, 0.02);
			if (t >= ORBIT_APEX) {
				set(p, "orbit_phase", 3);
				set(p, "orbit_ticks", 0);
				set(p, "orbit_drop_y", (float) (p.getY() + 1000));
				AbilityHelpers.sound(p, SoundEvents.WARDEN_SONIC_BOOM, 0.7f, 0.7f);
			}
		} else {
			// ---- the drop ----
			if (!p.onGround() && !p.isInWater() && t < 120) {
				Vec3 look = p.getLookAngle();
				AbilityHelpers.launchSelf(p, new Vec3(look.x * 0.25, -3.4, look.z * 0.25));
				MutationVisuals.ensure(p, "p03.dive");
				level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 1.5, p.getZ(), 6, 0.2, 0.5, 0.2, 0.02);
				level.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() - 0.2, p.getZ(), 2, 0.2, 0.1, 0.2, 0.01);
				return;
			}
			double fell = Math.max(0, res(p, "orbit_drop_y") - 1000 - p.getY());
			float meteor = com.projecthero.mod.hero.power.PowerCombos.meteorSlamBonus(p, KEY);
			float dmg = Math.min(72f, 24f + (float) fell * 1.2f) + meteor;
			impact(ctx, dmg, 7.0, 1.8);
			sonicBoom(level, p, 0f);
			set(p, "orbit_phase", 0);
			set(p, "orbit_ticks", 0);
		}
	}

	private static void slipTick(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		float t = res(p, "slip_ticks");
		if (t <= 0.5f) {
			return;
		}
		set(p, "slip_ticks", t - 1);
		ServerLevel level = ctx.level();
		Vec3 behind = p.position().subtract(BatchA.flatLook(p).scale(2.5)).add(0, 0.4, 0);
		for (LivingEntity e : AbilityHelpers.living(level, p.position(), 16.0, e -> BatchA.isAlly(p, e))) {
			Vec3 to = behind.subtract(e.position());
			if (to.lengthSqr() > 9.0) {
				Vec3 pull = to.scale(0.22);
				if (pull.length() > 1.6) {
					pull = pull.normalize().scale(1.6);
				}
				e.setDeltaMovement(e.getDeltaMovement().scale(0.5).add(pull));
				e.hurtMarked = true;
				e.hasImpulse = true;
			}
			e.fallDistance = 0;
			if ((int) t % 20 == 0) {
				e.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false, true));
			}
			if ((int) t % 4 == 0) {
				AbilityHelpers.line(level, p.position().add(0, 1, 0), e.position().add(0, e.getBbHeight() * 0.5, 0),
						ParticleTypes.CLOUD, 0.6);
			}
		}
	}
}
