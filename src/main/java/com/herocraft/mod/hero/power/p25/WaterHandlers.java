package com.herocraft.mod.hero.power.p25;

import com.herocraft.mod.hero.AbilityContext;
import com.herocraft.mod.hero.AbilityHandler;
import com.herocraft.mod.hero.AbilityHandlers;
import com.herocraft.mod.hero.HeroConfig;
import com.herocraft.mod.hero.power.AbilityHelpers;
import com.herocraft.mod.hero.power.Handlers;
import com.herocraft.mod.hero.power.TempBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Power 25 — Water Manipulation. */
public final class WaterHandlers {
	private static final String KEY = "power_25_water_manipulation";

	private WaterHandlers() {
	}

	/** +5 to every Water ability's damage while the caster is in water, rain or snow. */
	private static float waterBonus(ServerPlayer p) {
		boolean snow = p.isInPowderSnow
				|| (p.level().getBlockState(p.blockPosition()).is(Blocks.SNOW))
				|| p.level().getBlockState(p.blockPosition().below()).is(Blocks.SNOW);
		return (p.isInWaterOrRain() || snow) ? 5.0f : 0.0f;
	}

	public static void register() {
		// Water Shot: tap to fire; sneak-and-hold for 4 s to place a water source where you look.
		AbilityHandlers.register(KEY, "water_shot", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown()) {
					ctx.setResource("place_ticks", 80, 80);
					return;
				}
				if (!ctx.cooldownReady()) {
					return;
				}
				LivingEntity t = AbilityHelpers.raycastEntity(p, 24.0);
				AbilityHelpers.line(ctx.level(), p.getEyePosition(), AbilityHelpers.aimPoint(p, 24.0), ParticleTypes.FALLING_WATER, 4.0);
				if (t != null) {
					AbilityHelpers.hurt(p, t, 8.0f + waterBonus(p));
					AbilityHelpers.knockbackFrom(t, p.position(), 1.0);
					t.setRemainingFireTicks(0);
					markWet(ctx, t);
				}
				AbilityHelpers.sound(p, SoundEvents.PLAYER_SPLASH, 1.0f, 0.9f);
				ctx.triggerCooldown();
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				ctx.setResource("place_ticks", 0, 80);
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				float pt = ctx.resource("place_ticks");
				if (pt <= 0.5f) {
					return;
				}
				ServerPlayer p = ctx.player();
				if (!p.isShiftKeyDown()) {
					ctx.setResource("place_ticks", 0, 80);
					return;
				}
				pt -= 1.0f;
				ctx.setResource("place_ticks", pt, 80);
				if (p.tickCount % 4 == 0) {
					Vec3 aim = AbilityHelpers.aimPoint(p, 12.0);
					ctx.level().sendParticles(ParticleTypes.SPLASH, aim.x, aim.y, aim.z, 4, 0.2, 0.2, 0.2, 0.0);
				}
				if (pt <= 0.5f && HeroConfig.get().abilityTerrainDamage) {
					BlockHitResult hit = AbilityHelpers.raycastBlock(p, 12.0);
					BlockPos target = hit.getType() == HitResult.Type.BLOCK
							? hit.getBlockPos().relative(hit.getDirection())
							: BlockPos.containing(AbilityHelpers.aimPoint(p, 12.0));
					if (ctx.level().getBlockState(target).canBeReplaced()) {
						ctx.level().setBlockAndUpdate(target, Blocks.WATER.defaultBlockState());
						AbilityHelpers.sound(p, SoundEvents.BUCKET_EMPTY, 1.0f, 1.0f);
					}
				}
			}
		});

		AbilityHandlers.register(KEY, "water_whip", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			Vec3 front = p.getEyePosition().add(p.getLookAngle().scale(4));
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, front, 4.0)) {
				AbilityHelpers.hurt(p, e, 16.0f + waterBonus(p));
				AbilityHelpers.push(e, p.position().subtract(e.position()).normalize().scale(1.2));
				markWet(ctx, e);
			}
			AbilityHelpers.burst(ctx.level(), front, ParticleTypes.SPLASH, 20, 0.6);
			AbilityHelpers.sound(p, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1.0f, 1.0f);
			ctx.triggerCooldown();
		}));

		// Riptide: fire yourself along a jet of water -- a fast forward dash, longer while wet.
		AbilityHandlers.register(KEY, "riptide", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			ServerLevel level = ctx.level();
			Vec3 look = p.getLookAngle();
			boolean wet = p.isInWaterOrRain() || p.isInWater();
			double strength = wet ? 3.4 : 2.5;
			// drive mostly along the look vector, with a little lift on a flat/upward dash so you
			// skim forward instead of nosing into the ground
			Vec3 impulse = look.scale(strength).add(0.0, look.y < -0.1 ? 0.05 : 0.4, 0.0);
			AbilityHelpers.addImpulse(p, impulse);
			p.resetFallDistance();
			p.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 80, 1, false, false, false));
			AbilityHelpers.line(level, p.getEyePosition(), p.getEyePosition().add(look.scale(6.0)),
					ParticleTypes.SPLASH, 3.0);
			AbilityHelpers.burst(level, p.position(), ParticleTypes.FALLING_WATER, 24, 0.5);
			// bowl over and soak anything you dash into
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position().add(look.scale(2.0)), 2.5)) {
				AbilityHelpers.hurt(p, e, 6.0f + waterBonus(p));
				AbilityHelpers.push(e, look.scale(1.2).add(0, 0.2, 0));
				markWet(ctx, e);
			}
			AbilityHelpers.sound(p, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1.0f, 1.1f);
			ctx.triggerCooldown();
		}));

		AbilityHandlers.register(KEY, "tidal_wave", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			Vec3 dir = p.getLookAngle().multiply(1, 0, 1).normalize();
			for (int d = 2; d <= 10; d += 2) {
				Vec3 at = p.position().add(dir.scale(d));
				for (LivingEntity e : AbilityHelpers.enemiesAround(p, at, 3.0)) {
					AbilityHelpers.hurt(p, e, 24.0f + waterBonus(p));
					AbilityHelpers.push(e, dir.scale(2.0).add(0, 0.5, 0));
					markWet(ctx, e);
				}
				ctx.level().sendParticles(ParticleTypes.SPLASH, at.x, at.y + 1, at.z, 30, 1.5, 1.5, 1.5, 0.1);
			}
			AbilityHelpers.sound(p, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1.4f, 0.5f);
			ctx.triggerCooldown();
		}));

		// Water Prison: encase a target in water for 20 s. They cannot be knocked or dragged out.
		// Press again to free them early; the cooldown only begins once they are free, and the water
		// vanishes with them.
		AbilityHandlers.register(KEY, "water_prison", Handlers.instantTicking(ctx -> {
			ServerPlayer p = ctx.player();
			if (ctx.resource("prison_ticks") > 0.5f) {
				releasePrison(ctx);
				return;
			}
			LivingEntity t = AbilityHelpers.raycastEntity(p, 16.0);
			if (t == null) {
				return;
			}
			ctx.setResource("prison_id", t.getId(), 1e9f);
			ctx.setResource("prison_ticks", 400, 400);
			ctx.setResource("prison_x", (float) t.getX(), 1e9f);
			ctx.setResource("prison_y", (float) t.getY(), 1e9f);
			ctx.setResource("prison_z", (float) t.getZ(), 1e9f);
			AbilityHelpers.sound(p, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, 1.0f, 0.8f);
		}, WaterHandlers::prisonTick));

		// Aquatic Mode: the old aquatic buffs are now passive; this doubles them and marks you with
		// a water aura, the way Flame Body does with fire.
		AbilityHandlers.register(KEY, "aquatic_form", Handlers.toggle(Handlers.noop(), Handlers.noop(), ctx -> {
			ServerPlayer p = ctx.player();
			p.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 20, 2, false, false, false));
			p.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, 20, 0, false, false, false));
			if (p.isInWater()) {
				p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20, 2, false, false, false));
			}
			AbilityHelpers.modeAura(p, ParticleTypes.FALLING_WATER, 4);
			if (p.tickCount % 10 == 0) {
				AbilityHelpers.modeAura(p, ParticleTypes.BUBBLE, 3);
			}
		}));

		// Passive: the old Aquatic Mode buffs, always on while Water Manipulation is active.
		com.herocraft.mod.hero.PowerPassives.registerTick(KEY, player -> {
			player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 25, 0, false, false, false));
			player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 25, 0, false, false, false));
			if (player.isInWater()) {
				player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, false, false, false));
				player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 25, 1, false, false, false));
				player.setAirSupply(player.getMaxAirSupply());
			}
			if (player.isInWater() && player.getAirSupply() < player.getMaxAirSupply()) {
				player.setAirSupply(Math.min(player.getMaxAirSupply(), player.getAirSupply() + 4));
			}
		});
	}

	// ---------------- water prison ----------------

	private static void prisonTick(AbilityContext ctx) {
		float ticks = ctx.resource("prison_ticks");
		if (ticks <= 0.5f) {
			return;
		}
		ServerPlayer p = ctx.player();
		Entity ent = ctx.level().getEntity((int) ctx.resource("prison_id"));
		if (!(ent instanceof LivingEntity t) || !t.isAlive()) {
			releasePrison(ctx);
			return;
		}
		ticks -= 1.0f;
		ctx.setResource("prison_ticks", ticks, 400);

		double px = ctx.resource("prison_x");
		double py = ctx.resource("prison_y");
		double pz = ctx.resource("prison_z");
		boolean isPlayer = t instanceof net.minecraft.world.entity.player.Player;
		t.setDeltaMovement(0, 0, 0);
		t.hurtMarked = true;
		t.fallDistance = 0;
		t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, isPlayer ? 3 : 6, false, false, false));
		t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 10, 2, false, false, false));
		if (!isPlayer) {
			// hard-pin a mob to where it was caught -- knockback, dashes, nothing moves it out
			if (t.distanceToSqr(px, py, pz) > 0.02) {
				t.teleportTo(px, py, pz);
			}
			t.setAirSupply(Math.max(0, t.getAirSupply() - 4));
			if (HeroConfig.get().abilityTerrainDamage) {
				BlockPos base = BlockPos.containing(px, py, pz);
				for (Direction d : Direction.values()) {
					TempBlocks.placeStatic(ctx.level(), base.relative(d), Blocks.WATER.defaultBlockState(), 15);
					TempBlocks.placeStatic(ctx.level(), base.above().relative(d), Blocks.WATER.defaultBlockState(), 15);
				}
			}
		}
		if (p.tickCount % 4 == 0) {
			ctx.level().sendParticles(ParticleTypes.BUBBLE, t.getX(), t.getY() + 1, t.getZ(), 20, 0.5, 1.0, 0.5, 0.0);
		}
		if (ticks <= 0.5f) {
			releasePrison(ctx);
		}
	}

	private static void releasePrison(AbilityContext ctx) {
		ctx.setResource("prison_ticks", 0, 400);
		ctx.setResource("prison_id", 0, 1e9f);
		ctx.triggerCooldown();
	}

	/** Combo: Water + Electrokinesis — mark a target "wet" for bonus electrical damage. */
	private static void markWet(AbilityContext ctx, LivingEntity e) {
		e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0, false, false, false));
		if (e.level() instanceof ServerLevel sl) {
			sl.sendParticles(ParticleTypes.FALLING_WATER, e.getX(), e.getY() + e.getBbHeight(), e.getZ(), 8, 0.3, 0.1, 0.3, 0.0);
		}
	}
}
