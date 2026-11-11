package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.undead.UndeadHeadThrowRender;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Undead hold X (Head Throw) in first person: the view lifts as the neck stretches while he tears at his head, and once
 * it is off and held back behind his shoulder the camera is the held head ({@link UndeadHeadThrowRender#cameraPos}).
 */
@Mixin(Camera.class)
public abstract class UndeadHeadCameraMixin {
	@Shadow
	protected abstract void setPosition(Vec3 pos);

	@Shadow
	public abstract Vec3 getPosition();

	@Inject(method = "setup", at = @At("TAIL"))
	private void projecthero$undeadHeadCamera(BlockGetter level, Entity entity, boolean detached, boolean reverse, float partialTick, CallbackInfo ci) {
		if (detached || !(entity instanceof Player p) || entity != Minecraft.getInstance().player) {
			return;
		}
		Vec3 to = UndeadHeadThrowRender.cameraPos(p, partialTick, getPosition());
		if (to != null) {
			setPosition(to);
		}
	}
}
