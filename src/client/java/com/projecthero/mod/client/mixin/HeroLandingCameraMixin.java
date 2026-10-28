package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.flight.HeroLandingClient;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: the first-person superhero landing -- the view dips toward the ground with the body and nods down through the
 * impact ({@link HeroLandingClient#cameraDip} / {@link HeroLandingClient#cameraNod}), then rises with it. First person on
 * your own player only; scaled by the vanilla "Distortion Effects" slider like the Quirk camera shake.
 */
@Mixin(Camera.class)
public abstract class HeroLandingCameraMixin {
	@Shadow
	protected abstract void setPosition(Vec3 pos);

	@Shadow
	public abstract Vec3 getPosition();

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Shadow
	public abstract float getYRot();

	@Shadow
	public abstract float getXRot();

	@Inject(method = "setup", at = @At("TAIL"))
	private void projecthero$heroLandingDip(BlockGetter level, Entity entity, boolean detached, boolean thirdPersonReverse, float partialTick,
			CallbackInfo ci) {
		if (detached || !(entity instanceof Player p) || p != Minecraft.getInstance().player) {
			return;
		}
		float dip = HeroLandingClient.cameraDip(p, partialTick);
		if (dip <= 0.001f) {
			return;
		}
		float scale = 0.4f + 0.6f * Minecraft.getInstance().options.screenEffectScale().get().floatValue();
		setPosition(getPosition().add(0, -dip * scale, 0));
		setRotation(getYRot(), Math.min(90f, getXRot() + HeroLandingClient.cameraNod(p, partialTick) * scale));
	}
}
