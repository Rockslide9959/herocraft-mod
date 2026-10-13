package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.spider.SpiderPose;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;

/** v0.16.1: Spider-Man's ledge perch in first person -- the eye sinks a little lower and tips toward the drop. */
@Mixin(Camera.class)
public abstract class SpiderPerchCameraMixin {
	@Shadow
	public abstract Vec3 getPosition();

	@Shadow
	public abstract float getXRot();

	@Shadow
	public abstract float getYRot();

	@Shadow
	protected abstract void setPosition(Vec3 pos);

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Inject(method = "setup", at = @At("TAIL"))
	private void projecthero$spiderPerch(BlockGetter level, Entity entity, boolean detached, boolean reverse, float partialTick,
			CallbackInfo ci) {
		if (detached || !(entity instanceof Player player) || entity != Minecraft.getInstance().player) {
			return;
		}
		float k = SpiderPose.perchAmount(player);
		if (k <= 0f) {
			return;
		}
		// v0.16.1: from the (editable) perch pose
		com.projecthero.mod.client.spider.PerchPose pose = com.projecthero.mod.client.spider.PerchPose.current();
		setPosition(getPosition().add(0.0, -pose.cameraDrop * k, 0.0));
		setRotation(getYRot(), Math.min(90f, getXRot() + pose.cameraTilt * k));
	}
}
