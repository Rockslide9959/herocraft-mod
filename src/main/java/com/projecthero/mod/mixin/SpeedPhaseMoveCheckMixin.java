package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/**
 * v0.17.7 (player report: "i dont think the speed force speedster phase works"): the server rejects a move that ends inside a block
 * the player was not already in ("moved wrongly" -- it snaps them back), so a phasing speedster's client walked into the wall and
 * the server pulled him straight back out. The Omnitrix (Ghostfreak) and Vision already wave that one check through while phasing
 * ({@code OmnitrixPhaseMoveCheckMixin}, {@code VisionPhaseMoveCheckMixin}); this does the same for the other wall-phasers whose
 * collide mixins let the move itself pass through blocks: the Speed Force Speedster ({@code SpeedForcePhaseCollideMixin}), the
 * plain Super Speed mutation ({@code SuperSpeedPhaseCollideMixin}) and the Silver Surfer ({@code SilverSurferPhaseCollideMixin}).
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class SpeedPhaseMoveCheckMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "isPlayerCollidingWithAnythingNew", at = @At("HEAD"), cancellable = true)
	private void projecthero$speedPhaseThroughWalls(CallbackInfoReturnable<Boolean> cir) {
		if (com.projecthero.mod.speedforce.SpeedForceSpeedster.phasing(this.player)
				|| com.projecthero.mod.hero.power.p04.SuperSpeedHandlers.phasing(this.player)
				|| com.projecthero.mod.silversurfer.SilverSurfer.phasing(this.player)) {
			cir.setReturnValue(false);
		}
	}
}
