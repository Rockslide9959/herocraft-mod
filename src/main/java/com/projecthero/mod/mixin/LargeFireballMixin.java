package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.p08.PyrokinesisHandlers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.HitResult;

/**
 * Pyrokinesis' R "Fireball" is a genuine ghast fireball. This mixin does two things for a low-power
 * fireball thrown by a pyrokinetic:
 * <ul>
 *   <li>raises the direct-hit damage to {@link PyrokinesisHandlers#fireballImpactDamage}
 *       (12 + Nether / Blue Flame bonuses); and</li>
 *   <li>on impact scatters a small spread of fire around it (v0.10.13: no forced crater -- the
 *       vanilla explosion, which honours the mobGriefing gamerule, is left as-is).</li>
 * </ul>
 *
 * <p>The {@code explosionPower < 5} guard keeps this off the Inferno ultimate's own big fireball
 * (power 5-6), whose damage is meant to come from its blast, and off every vanilla ghast/blaze
 * fireball (never player-owned).
 */
@Mixin(LargeFireball.class)
public abstract class LargeFireballMixin {
	@Shadow
	private int explosionPower;

	@ModifyArg(method = "onHitEntity",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"),
			index = 1)
	private float projecthero$pyroFireballDirectHit(float original) {
		if (projecthero$pyroFireball() != null) {
			return PyrokinesisHandlers.fireballImpactDamage(projecthero$pyroFireball());
		}
		return original;
	}

	@Inject(method = "onHit", at = @At("TAIL"))
	private void projecthero$pyroFireballCrater(HitResult result, CallbackInfo ci) {
		LargeFireball self = (LargeFireball) (Object) this;
		if (self.level().isClientSide || !(self.level() instanceof ServerLevel level)) {
			return;
		}
		if (projecthero$pyroFireball() == null) {
			return;
		}
		double x = self.getX();
		double y = self.getY();
		double z = self.getZ();
		// v0.10.13: no forced crater any more. The vanilla fireball explosion (which honours the
		// mobGriefing gamerule) is left to do its own thing -- a "decent explosion", not a huge pit.
		// A small spread of fire around the impact is all that is added on top.
		if (com.projecthero.mod.hero.HeroConfig.get().abilityFireSpread && AbilityHelpers.canGrief()) {
			BlockPos centre = BlockPos.containing(x, y, z);
			for (int i = 0; i < 5; i++) {
				BlockPos bp = centre.offset(level.random.nextInt(5) - 2,
						level.random.nextInt(3) - 1, level.random.nextInt(5) - 2);
				if (level.getBlockState(bp).isAir()
						&& level.getBlockState(bp.below()).isFaceSturdy(level, bp.below(), net.minecraft.core.Direction.UP)) {
					level.setBlockAndUpdate(bp, BaseFireBlock.getState(level, bp));
				}
			}
		}
		level.sendParticles(ParticleTypes.LAVA, x, y, z, 16, 1.2, 0.6, 1.2, 0.0);
		level.sendParticles(ParticleTypes.FLAME, x, y, z, 30, 1.4, 0.7, 1.4, 0.03);
	}

	/** The pyrokinetic owner iff this is one of their low-power (non-Inferno) fireballs, else null. */
	private ServerPlayer projecthero$pyroFireball() {
		if (explosionPower >= 5) {
			return null;
		}
		if (((LargeFireball) (Object) this).getOwner() instanceof ServerPlayer sp
				&& PyrokinesisHandlers.ownsPyrokinesis(sp)) {
			return sp;
		}
		return null;
	}
}
