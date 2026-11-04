package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.galactus.GalactusCinematicClient;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 Galactus raid v2: the cutscene camera ({@link GalactusCinematicClient#camera}) -- the view leaves your eyes and
 * follows the scene's path; your body is drawn unless the shot is first person. Presentation only.
 */
@Mixin(Camera.class)
public abstract class GalactusCinematicCameraMixin {
	@Shadow
	private boolean detached;

	@Shadow
	protected abstract void setPosition(Vec3 pos);

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Inject(method = "setup", at = @At("TAIL"))
	private void projecthero$galactusCinematic(BlockGetter level, Entity entity, boolean isDetached, boolean reverse, float partialTick,
			CallbackInfo ci) {
		if (!(entity instanceof Player player) || entity != Minecraft.getInstance().player) {
			return;
		}
		double[] cam = GalactusCinematicClient.camera(player, partialTick);
		if (cam == null) {
			return;
		}
		this.detached = !GalactusCinematicClient.pov();
		setRotation((float) cam[3], (float) cam[4]);
		setPosition(new Vec3(cam[0], cam[1], cam[2]));
	}
}
