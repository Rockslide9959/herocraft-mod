package com.projecthero.mod.hero.power.p02;

import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandler;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.PowerPassives;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.Handlers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Power 02 — Laser Vision. "heat" resource 0..100: drains while beaming, regens otherwise. */
public final class LaserVisionHandlers {
	private static final String KEY = "power_02_laser_vision";
	private static final float MAX_HEAT = 500.0f;

	private LaserVisionHandlers() {
	}

	public static void register() {
		// "heat" builds UP while beaming (0 = cool). At MAX it overheats and forces a cooldown.
		AbilityHandlers.register(KEY, "heat_vision", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (ctx.resource("heat") >= MAX_HEAT * 0.9f) {
					ctx.actionBar("message.projecthero.laser.overheated");
					return;
				}
				ctx.setResource("beaming", 1, 1);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				ctx.setResource("beaming", 0, 1);
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				boolean beaming = ctx.resource("beaming") > 0.5f;
				if (beaming) {
					fireBeam(ctx, 18.0, 4.0f, false);
					ctx.addResource("heat", 2.0f, MAX_HEAT);
					if (ctx.resource("heat") >= MAX_HEAT) {
						ctx.setResource("beaming", 0, 1);
						ctx.actionBar("message.projecthero.laser.overheated");
					}
				} else if (ctx.resource("heat") > 0.0f) {
					ctx.addResource("heat", -4.0f, MAX_HEAT);
				}
			}
		});

		AbilityHandlers.register(KEY, "focused_beam", Handlers.instant(ctx -> {
			// The focus beam spends a full 100 of the heat meter.
			if (ctx.resource("heat") > MAX_HEAT - 100.0f) {
				ctx.actionBar("message.projecthero.laser.overheated");
				return;
			}
			ctx.addResource("heat", 100.0f, MAX_HEAT);
			fireBeam(ctx, 30.0, 18.0f, true);
			AbilityHelpers.sound(ctx.player(), SoundEvents.BLAZE_SHOOT, 1.0f, 0.6f);
			ctx.triggerCooldown();
		}));

		AbilityHandlers.register(KEY, "heat_burst", Handlers.instant(ctx -> {
			if (!spendHeat(ctx, 60.0f)) {
				return;
			}
			ServerPlayer p = ctx.player();
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.getEyePosition(), 3.5)) {
				AbilityHelpers.hurt(p, e, AbilityHelpers.fire(p), 6.0f);
				AbilityHelpers.knockbackFrom(e, p.position(), 1.1);
				e.setRemainingFireTicks(40);
			}
			AbilityHelpers.burst(ctx.level(), p.getEyePosition().add(p.getLookAngle()), ParticleTypes.FLAME, 30, 0.6);
			AbilityHelpers.sound(p, SoundEvents.FIRECHARGE_USE, 1.0f, 0.8f);
			ctx.triggerCooldown();
		}));

		AbilityHandlers.register(KEY, "maximum_output", Handlers.instantTicking(ctx -> {
			if (!spendHeat(ctx, 250.0f)) {
				return;
			}
			ctx.setResource("max_ticks", 60, 60);
			AbilityHelpers.sound(ctx.player(), SoundEvents.BLAZE_SHOOT, 1.2f, 0.4f);
			ctx.triggerCooldown();
		}, ctx -> {
			int t = (int) ctx.resource("max_ticks");
			if (t <= 0) {
				return;
			}
			ctx.setResource("max_ticks", t - 1, 60);
			fireBeam(ctx, 40.0, 12.0f, true);
			if (t % 10 == 0) {
				Vec3 impact = AbilityHelpers.aimPoint(ctx.player(), 40.0);
				for (LivingEntity e : AbilityHelpers.enemiesAround(ctx.player(), impact, 5.0)) {
					AbilityHelpers.hurt(ctx.player(), e, AbilityHelpers.fire(ctx.player()), 24.0f);
					e.setRemainingFireTicks(120);
				}
				ctx.level().sendParticles(ParticleTypes.EXPLOSION, impact.x, impact.y, impact.z, 1, 0, 0, 0, 0);
			}
		}));

		AbilityHandlers.register(KEY, "precision_vision", Handlers.instant(ctx -> {
			if (!spendHeat(ctx, 20.0f)) {
				return;
			}
			ServerPlayer p = ctx.player();
			ServerLevel level = ctx.level();
			BlockHitResult hit = AbilityHelpers.raycastBlock(p, 18.0);
			if (hit.getType() == HitResult.Type.BLOCK) {
				BlockPos pos = hit.getBlockPos();
				BlockState st = level.getBlockState(pos);
				if (st.is(Blocks.TNT)) {
					level.removeBlock(pos, false);
					net.minecraft.world.entity.item.PrimedTnt tnt = new net.minecraft.world.entity.item.PrimedTnt(
							level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, p);
					level.addFreshEntity(tnt);
				} else if (AbilityHelpers.canGrief() && (st.is(Blocks.ICE) || st.is(Blocks.SNOW)
						|| st.is(Blocks.SNOW_BLOCK) || st.is(Blocks.GLASS) || st.is(Blocks.POWDER_SNOW))) {
					level.destroyBlock(pos, false);
				} else if (AbilityHelpers.canGrief() && level.getBlockState(hit.getBlockPos().relative(hit.getDirection())).isAir()) {
					level.setBlockAndUpdate(hit.getBlockPos().relative(hit.getDirection()), Blocks.FIRE.defaultBlockState());
				}
				AbilityHelpers.line(level, p.getEyePosition(), Vec3.atCenterOf(pos), ParticleTypes.SMALL_FLAME, 2.0);
			}
			ctx.triggerCooldown();
		}));

		AbilityHandlers.register(KEY, "thermal_vision", Handlers.toggle(Handlers.noop(), Handlers.noop(), ctx -> {
			// v0.10.10: no mode aura. Thermal Vision is something you do behind your own eyes -- the
			// flame particles it used to trail made the user visibly light up for everybody else, which
			// broadcast a purely private power (and, worse, told the room you were scanning them).
			// running the thermal overlay costs a slow trickle of heat
			ctx.addResource("heat", 0.5f, MAX_HEAT);
			if (ctx.resource("heat") >= MAX_HEAT) {
				ctx.setToggled(false);
				ctx.actionBar("message.projecthero.laser.overheated");
				return;
			}
			// The thermal outline itself is drawn client-side for the viewer only (EntityGlowMixin) --
			// no GLOWING effect is applied here, so other players never see the highlighted entities.
		}));

		PowerPassives.registerTick(KEY, player -> {
			var blind = player.getEffect(MobEffects.BLINDNESS);
			if (blind != null && blind.getDuration() > 20) {
				player.removeEffect(MobEffects.BLINDNESS);
				player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20, 0));
			}
		});
	}

	/** Charge {@code amount} of heat, or refuse (with an overheat message) if there is not room. */
	private static boolean spendHeat(AbilityContext ctx, float amount) {
		if (ctx.resource("heat") > MAX_HEAT - amount) {
			ctx.actionBar("message.projecthero.laser.overheated");
			return false;
		}
		ctx.addResource("heat", amount, MAX_HEAT);
		return true;
	}

	private static void fireBeam(AbilityContext ctx, double range, float damage, boolean cutBlocks) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		Vec3 start = p.getEyePosition();
		LivingEntity target = AbilityHelpers.raycastEntity(p, range);
		Vec3 end;
		if (target != null) {
			end = target.position().add(0, target.getBbHeight() * 0.5, 0);
			AbilityHelpers.hurt(p, target, AbilityHelpers.fire(p), damage);
			target.setRemainingFireTicks(60);
		} else {
			BlockHitResult bhr = AbilityHelpers.raycastBlock(p, range);
			end = bhr.getType() == HitResult.Type.BLOCK ? bhr.getLocation() : start.add(p.getLookAngle().scale(range));
			if (cutBlocks && bhr.getType() == HitResult.Type.BLOCK && AbilityHelpers.canGrief()) {
				BlockPos bp = bhr.getBlockPos();
				if (level.getBlockState(bp).getDestroySpeed(level, bp) < 3.0f && level.getBlockState(bp).getDestroySpeed(level, bp) >= 0) {
					level.destroyBlock(bp, false, p);
				}
			}
		}
		// Two beams, one from roughly where each eye sits, converging on the target point.
		Vec3 look = p.getLookAngle();
		Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
		right = right.lengthSqr() < 1.0e-6 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
		Vec3 eyeLine = start.add(0.0, -0.1, 0.0).add(look.scale(0.35));
		Vec3 leftEye = eyeLine.add(right.scale(-0.13));
		Vec3 rightEye = eyeLine.add(right.scale(0.13));
		AbilityHelpers.line(level, leftEye, end, ParticleTypes.FLAME, 2.5);
		AbilityHelpers.line(level, rightEye, end, ParticleTypes.FLAME, 2.5);
		AbilityHelpers.line(level, leftEye, end, ParticleTypes.SMALL_FLAME, 1.5);
		AbilityHelpers.line(level, rightEye, end, ParticleTypes.SMALL_FLAME, 1.5);
	}
}
