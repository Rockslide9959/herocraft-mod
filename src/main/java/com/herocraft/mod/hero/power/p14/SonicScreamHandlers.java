package com.herocraft.mod.hero.power.p14;

import com.herocraft.mod.hero.AbilityHandlers;
import com.herocraft.mod.hero.power.AbilityHelpers;
import com.herocraft.mod.hero.power.Handlers;
import com.herocraft.mod.hero.power.TempBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Power 14 — Sonic Scream. */
public final class SonicScreamHandlers {
	private static final String KEY = "power_14_sonic_scream";

	private SonicScreamHandlers() {
	}

	private static boolean inCone(ServerPlayer p, LivingEntity e, double angleDot) {
		Vec3 to = e.position().subtract(p.getEyePosition()).normalize();
		return to.dot(p.getLookAngle()) > angleDot;
	}

	public static void register() {
		AbilityHandlers.register(KEY, "sonic_blast", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.getEyePosition().add(p.getLookAngle().scale(3)), 4.0)) {
				if (inCone(p, e, 0.5)) {
					AbilityHelpers.hurt(p, e, 10.0f);
					AbilityHelpers.knockbackFrom(e, p.position(), 1.4);
				}
			}
			cone(ctx.level(), p, 4);
			AbilityHelpers.sound(p, SoundEvents.WARDEN_SONIC_BOOM, 0.7f, 1.6f);
			ctx.triggerCooldown();
		}));

		AbilityHandlers.register(KEY, "focused_scream", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			LivingEntity t = AbilityHelpers.raycastEntity(p, 30.0);
			AbilityHelpers.line(ctx.level(), p.getEyePosition(), AbilityHelpers.aimPoint(p, 30.0), ParticleTypes.SONIC_BOOM, 1.0);
			if (t != null) {
				AbilityHelpers.hurt(p, t, 16.0f);
				AbilityHelpers.knockbackFrom(t, p.position(), 1.0);
				AbilityHelpers.applyControl(t, MobEffects.CONFUSION, 80, 0);
			}
			AbilityHelpers.sound(p, SoundEvents.WARDEN_SONIC_BOOM, 1.0f, 1.0f);
			ctx.triggerCooldown();
		}));

		AbilityHandlers.register(KEY, "sonic_jump", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			AbilityHelpers.launchSelf(p, new Vec3(p.getDeltaMovement().x, 1.3, p.getDeltaMovement().z));
			// no fall damage from this launch -- protected until shortly after the next landing
			ctx.setResource("no_fall_until", p.level().getGameTime() + 200, 1.0e12f);
			ctx.level().sendParticles(ParticleTypes.SONIC_BOOM, p.getX(), p.getY(), p.getZ(), 2, 0.2, 0.0, 0.2, 0.0);
			AbilityHelpers.sound(p, SoundEvents.WARDEN_SONIC_BOOM, 0.8f, 1.9f);
			ctx.triggerCooldown();
		}));

		// Supersonic Scream: a devastating 20-block cone of sound ahead of you for 35 damage.
		AbilityHandlers.register(KEY, "supersonic_scream", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			ServerLevel level = ctx.level();
			double r = 20.0;
			Vec3 look = p.getLookAngle();
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.getEyePosition().add(look.scale(r * 0.5)), r * 0.55)) {
				Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(p.getEyePosition());
				if (to.length() > r || to.normalize().dot(look) < 0.6) {
					continue; // outside the ~53° forward cone
				}
				AbilityHelpers.hurt(p, e, 35.0f);
				AbilityHelpers.knockbackFrom(e, p.position(), 3.0);
				AbilityHelpers.applyControl(e, MobEffects.CONFUSION, 120, 0);
			}
			if (AbilityHelpers.canGrief()) {
				for (int i = 1; i <= (int) r; i++) {
					BlockPos step = BlockPos.containing(p.getEyePosition().add(look.scale(i)));
					for (BlockPos bp : BlockPos.betweenClosed(step.offset(-1, -1, -1), step.offset(1, 1, 1))) {
						if (isFragile(level, bp)) {
							level.destroyBlock(bp, false, p);
						}
					}
				}
			}
			for (int i = 1; i <= (int) r; i++) {
				Vec3 pt = p.getEyePosition().add(look.scale(i));
				level.sendParticles(ParticleTypes.SONIC_BOOM, pt.x, pt.y, pt.z, 1, 0.12 * i, 0.12 * i, 0.12 * i, 0.0);
			}
			level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.6f, 0.6f);
			ctx.triggerCooldown();
		}));

		AbilityHandlers.register(KEY, "resonance", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			ServerLevel level = ctx.level();
			var hit = AbilityHelpers.raycastBlock(p, 12.0);
			if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK && AbilityHelpers.canGrief()) {
				for (BlockPos bp : BlockPos.betweenClosed(hit.getBlockPos().offset(-1, -1, -1), hit.getBlockPos().offset(1, 1, 1))) {
					if (isFragile(level, bp)) {
						level.destroyBlock(bp, true, p);
					}
				}
			}
			ctx.level().sendParticles(ParticleTypes.NOTE, Vec3.atCenterOf(hit.getBlockPos()).x,
					Vec3.atCenterOf(hit.getBlockPos()).y, Vec3.atCenterOf(hit.getBlockPos()).z, 10, 0.5, 0.5, 0.5, 1.0);
			AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 1.5f);
			ctx.triggerCooldown();
		}));

		// Echolocation: the outline is drawn client-side for the echolocator only (see EntityGlowMixin),
		// so no other player benefits. The toggle just pulses a sonar ping.
		AbilityHandlers.register(KEY, "echolocation", Handlers.toggle(Handlers.noop(), Handlers.noop(), ctx -> {
			if (ctx.player().tickCount % 30 != 0) {
				return;
			}
			ctx.level().sendParticles(ParticleTypes.SONIC_BOOM, ctx.player().getX(), ctx.player().getY() + 1,
					ctx.player().getZ(), 1, 0, 0, 0, 0);
		}));
	}

	private static void cone(ServerLevel level, ServerPlayer p, int dist) {
		Vec3 look = p.getLookAngle();
		Vec3 origin = p.getEyePosition();
		for (int i = 1; i <= dist; i++) {
			Vec3 pt = origin.add(look.scale(i));
			level.sendParticles(ParticleTypes.SONIC_BOOM, pt.x, pt.y, pt.z, 1, 0.1 * i, 0.1 * i, 0.1 * i, 0.0);
		}
	}

	private static boolean isFragile(ServerLevel level, BlockPos pos) {
		BlockState st = level.getBlockState(pos);
		if (st.isAir()) {
			return false;
		}
		if (st.is(Blocks.GLASS) || st.is(Blocks.GLASS_PANE) || st.is(Blocks.TINTED_GLASS)
				|| st.is(Blocks.ICE) || st.is(Blocks.GLOWSTONE) || st.is(Blocks.SEA_LANTERN)
				|| st.is(net.minecraft.tags.BlockTags.IMPERMEABLE)) {
			return true;
		}
		float hardness = st.getDestroySpeed(level, pos);
		return hardness >= 0.0f && hardness <= 0.35f && !st.is(net.minecraft.tags.BlockTags.LEAVES);
	}
}
