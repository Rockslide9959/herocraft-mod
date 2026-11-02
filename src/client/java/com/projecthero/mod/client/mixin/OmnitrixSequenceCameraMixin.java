package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.omnitrix.OmnitrixSequenceClient;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2: the long Omnitrix transformation's camera ({@link OmnitrixSequenceClient#camera}) -- the holder's view leaves their eyes
 * for the episode's shots (the dial, the swelling arm, the face, behind, the whip pan, the roar, the stance). Presentation only:
 * their own look is never touched, and it hands straight back when the sequence ends.
 */
@Mixin(Camera.class)
public abstract class OmnitrixSequenceCameraMixin {
	@Shadow
	private boolean detached;

	@Shadow
	protected abstract void setPosition(Vec3 pos);

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Inject(method = "setup", at = @At("TAIL"))
	private void projecthero$omnitrixSequence(BlockGetter level, Entity entity, boolean isDetached, boolean reverse, float partialTick, CallbackInfo ci) {
		if (!(entity instanceof Player player) || entity != Minecraft.getInstance().player) {
			return;
		}
		double[] cam = OmnitrixSequenceClient.camera(player, partialTick);
		if (cam == null) {
			return;
		}
		this.detached = true; // so their own body is drawn
		setRotation((float) cam[3], (float) cam[4]);
		setPosition(new Vec3(cam[0], cam[1], cam[2]));
	}
}
