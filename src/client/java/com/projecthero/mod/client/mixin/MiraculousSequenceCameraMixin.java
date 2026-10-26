package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.miraculous.MiraculousSequenceClient;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: the cinematic camera of every hero's long transformation -- for the holder, the view leaves their eyes and
 * flies round them through the beats ({@link MiraculousSequenceClient#camera}, each hero's own path: Ladybug's close-up
 * on the earrings, orbit through the twirl, the face for the mask, the hip for the yo-yo and a low hero shot; the
 * others' likewise round their jewel, their change and their weapon). Presentation only: their own look (and aim) is
 * never touched, and it hands straight back when the sequence ends.
 */
@Mixin(Camera.class)
public abstract class MiraculousSequenceCameraMixin {
	@Shadow
	private boolean detached;

	@Shadow
	protected abstract void setPosition(Vec3 pos);

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Inject(method = "setup", at = @At("TAIL"))
	private void projecthero$miraculousSequence(BlockGetter level, Entity entity, boolean isDetached, boolean reverse, float partialTick,
			CallbackInfo ci) {
		if (!(entity instanceof Player player) || entity != Minecraft.getInstance().player) {
			return;
		}
		double[] cam = MiraculousSequenceClient.camera(player, partialTick);
		if (cam == null) {
			return;
		}
		this.detached = true; // so the holder's own body is drawn
		setRotation((float) cam[3], (float) cam[4]);
		setPosition(new Vec3(cam[0], cam[1], cam[2]));
	}
}
