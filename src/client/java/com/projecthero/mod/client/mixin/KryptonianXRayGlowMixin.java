package com.projecthero.mod.client.mixin;

import com.projecthero.mod.kryptonian.Kryptonian;
import com.projecthero.mod.kryptonian.KryptonianConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * v0.14.8: the Kryptonian's X-Ray Vision -- while it is on, every living thing within {@link KryptonianConfig#XRAY_RANGE}
 * blocks is outlined through walls, for the Kryptonian alone.
 *
 * <p>v0.14.16: moved from {@code Entity#isCurrentlyGlowing} to {@code Minecraft#shouldEntityAppearGlowing}, which only
 * the renderer of this client asks. The old hook also answered the integrated server: in single-player / LAN the host's
 * server thread reads {@code isCurrentlyGlowing} to set the entity's shared glowing flag, so while the host had X-Ray on
 * every nearby mob was flagged as glowing and every other player saw the outlines too. Now nothing about the entity
 * changes anywhere; the server only syncs the owner's own on/off state and only the owner's client draws it.
 */
@Mixin(Minecraft.class)
public abstract class KryptonianXRayGlowMixin {
	@Shadow
	public LocalPlayer player;

	@Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
	private void projecthero$kryptonianXRay(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		LocalPlayer viewer = this.player;
		if (viewer == null || entity == viewer || !(entity instanceof LivingEntity) || entity instanceof ArmorStand
				|| entity instanceof com.projecthero.mod.oathbreaker.entity.OathbreakerEntity) { // the Oathbreaker never glows
			return;
		}
		if (!Kryptonian.xrayActive(viewer) || !Kryptonian.empowered(viewer)) {
			return;
		}
		if (entity.distanceToSqr(viewer) <= KryptonianConfig.XRAY_RANGE * KryptonianConfig.XRAY_RANGE) {
			cir.setReturnValue(true);
		}
	}
}
