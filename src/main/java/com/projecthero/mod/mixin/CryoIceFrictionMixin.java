package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.projecthero.mod.hero.power.p09.CryokinesisHandlers;

import net.minecraft.world.entity.LivingEntity;

/**
 * v0.16.5 Cryokinesis passive: no slipping on ice. A cryokinetic's feet grip ice, packed ice, blue ice and frosted ice
 * like ordinary ground (vanilla friction 0.6). Runs on both sides -- the local player's movement is simulated on the
 * client, which reads ownership from the synced attachment.
 */
@Mixin(LivingEntity.class)
public abstract class CryoIceFrictionMixin {
	@ModifyExpressionValue(method = "travel", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;getFriction()F"))
	private float projecthero$cryoGrip(float friction) {
		return friction > 0.6f && CryokinesisHandlers.surefooted((LivingEntity) (Object) this) ? 0.6f : friction;
	}
}
