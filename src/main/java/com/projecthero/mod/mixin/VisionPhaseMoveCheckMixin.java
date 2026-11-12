package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.vision.Vision;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/**
 * v0.17.3 Vision's Intangibility: the server rejects a move that ends inside a block the player wasn't already in (it snaps him back),
 * so while he is wall-phasing that one check is waved through ({@code OmnitrixPhaseMoveCheckMixin}'s fix for Ghostfreak);
 * {@code VisionPhaseCollideMixin} lets the move itself pass through blocks.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class VisionPhaseMoveCheckMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "isPlayerCollidingWithAnythingNew", at = @At("HEAD"), cancellable = true)
	private void projecthero$visionPhaseThroughWalls(CallbackInfoReturnable<Boolean> cir) {
		if (Vision.wallPhasing(this.player)) {
			cir.setReturnValue(false);
		}
	}
}
