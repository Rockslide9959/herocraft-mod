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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.HitResult;

/**
 * Pyrokinesis' R "Fireball" is a genuine ghast fireball. This mixin does two things for a low-power
 * fireball thrown by a pyrokinetic:
 * <ul>
 *   <li>raises the direct-hit damage to {@link PyrokinesisHandlers#fireballImpactDamage}
 *       (12 + Nether / Blue Flame bonuses); and</li>
 *   <li>on impact (v0.10.12) gouges a proper crater and scatters a spread of fire around it, so the
 *       basic attack reads like the old, un-nerfed fireball again.</li>
 * </ul>
 *
 * <p>The {@code explosionPower < 5} guard keeps this off the Inferno ultimate's own big fireball
 * (power 5-6), whose damage is meant to come from its blast, and off every vanilla ghast/blaze
 * fireball (never player-owned).
 */
@Mixin(LargeFireball.class)
public abstract class LargeFireballMixin {
	/** Our supplementary crater blast breaks blocks only -- the vanilla explosion already hurt entities. */
	private static final net.minecraft.world.level.ExplosionDamageCalculator PROJECTHERO$BLOCKS_ONLY =
			new net.minecraft.world.level.ExplosionDamageCalculator() {
				@Override
				public boolean shouldDamageEntity(net.minecraft.world.level.Explosion explosion,
						net.minecraft.world.entity.Entity entity) {
					return false;
				}

				@Override
				public float getKnockbackMultiplier(net.minecraft.world.entity.Entity entity) {
					return 0.0f;
				}
			};

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
		// A second, block-only blast to carve the crater even where the vanilla explosion above was
		// held back by the mobGriefing gamerule -- only when the mod's own terrain-damage config allows.
		if (AbilityHelpers.canGrief()) {
			level.explode(self, null, PROJECTHERO$BLOCKS_ONLY, x, y, z, 3.5f, true,
					Level.ExplosionInteraction.MOB,
					ParticleTypes.EXPLOSION, ParticleTypes.EXPLOSION_EMITTER,
					net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE);
		}
		// A spread of fire around the impact.
		if (com.projecthero.mod.hero.HeroConfig.get().abilityFireSpread && AbilityHelpers.canGrief()) {
			BlockPos centre = BlockPos.containing(x, y, z);
			for (int i = 0; i < 14; i++) {
				BlockPos bp = centre.offset(level.random.nextInt(7) - 3,
						level.random.nextInt(3) - 1, level.random.nextInt(7) - 3);
				if (level.getBlockState(bp).isAir()
						&& level.getBlockState(bp.below()).isFaceSturdy(level, bp.below(), net.minecraft.core.Direction.UP)) {
					level.setBlockAndUpdate(bp, BaseFireBlock.getState(level, bp));
				}
			}
		}
		level.sendParticles(ParticleTypes.LAVA, x, y, z, 30, 2.0, 1.0, 2.0, 0.0);
		level.sendParticles(ParticleTypes.FLAME, x, y, z, 60, 2.5, 1.2, 2.5, 0.05);
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
