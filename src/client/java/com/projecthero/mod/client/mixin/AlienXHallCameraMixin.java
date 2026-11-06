package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.omnitrix.alien.alienx.AlienXHall;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 round 4 (user: "a screen where the player can see a third person view of themselves and even turn and look around"): while
 * Alien X's hall is up the world is drawn for its screen, from a camera on an orbit round the body, which the player turns at the
 * hall's console. His real rotation is never touched.
 */
@Mixin(Camera.class)
public abstract class AlienXHallCameraMixin {
	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Shadow
	protected abstract void setPosition(double x, double y, double z);

	@Inject(method = "setup", at = @At("TAIL"))
	private void projecthero$hallOrbit(BlockGetter level, Entity entity, boolean detached, boolean mirrored, float partial, CallbackInfo ci) {
		if (AlienXHall.orbiting()) {
			Vec3 at = AlienXHall.worldCamera(partial);
			setRotation(AlienXHall.orbitYaw(), AlienXHall.orbitPitch());
			setPosition(at.x, at.y, at.z);
		}
	}
}
