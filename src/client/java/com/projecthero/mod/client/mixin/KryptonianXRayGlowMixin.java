package com.projecthero.mod.client.mixin;

import com.projecthero.mod.kryptonian.Kryptonian;
import com.projecthero.mod.kryptonian.KryptonianConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * v0.14.8: the Kryptonian's X-Ray Vision -- while it runs, every living thing within
 * {@link KryptonianConfig#XRAY_RANGE} blocks is outlined through walls, in the Kryptonian's own client only (nothing is
 * set on the entity, so nobody else sees the outlines).
 */
@Mixin(Entity.class)
public abstract class KryptonianXRayGlowMixin {
	@Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true)
	private void projecthero$kryptonianXRay(CallbackInfoReturnable<Boolean> cir) {
		Entity self = (Entity) (Object) this;
		if (!(self instanceof LivingEntity) || self instanceof LocalPlayer || self instanceof ArmorStand
				|| self instanceof com.projecthero.mod.oathbreaker.entity.OathbreakerEntity) { // the Oathbreaker never glows
			return;
		}
		LocalPlayer viewer = Minecraft.getInstance().player;
		if (viewer == null || !Kryptonian.xrayActive(viewer)) {
			return;
		}
		if (self.distanceToSqr(viewer) <= KryptonianConfig.XRAY_RANGE * KryptonianConfig.XRAY_RANGE) {
			cir.setReturnValue(true);
		}
	}
}
