package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.projecthero.mod.hero.power.p08.PyrokinesisHandlers;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.LargeFireball;

/**
 * Pyrokinesis' R "Fireball" is a genuine ghast fireball now (v0.10.11), not a hitscan flame beam.
 * A ghast fireball's own direct hit is a flat 6; this raises it to
 * {@link PyrokinesisHandlers#fireballImpactDamage} (12 + Nether / Blue Flame bonuses) when the
 * fireball is a low-power one thrown by a pyrokinetic.
 *
 * <p>The {@code explosionPower <= 2} guard keeps this off the Inferno ultimate's own big fireball
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
		if (explosionPower <= 2
				&& ((LargeFireball) (Object) this).getOwner() instanceof ServerPlayer sp
				&& PyrokinesisHandlers.ownsPyrokinesis(sp)) {
			return PyrokinesisHandlers.fireballImpactDamage(sp);
		}
		return original;
	}
}
