package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.omnitrix.alien.AlienKit;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/**
 * v0.17.1 (user: "ghost freaks phase ability doesnt seem to work"): the server rejects a move that ends inside a block the
 * player was not already in ("moved wrongly" -- it snaps them back), so an intangible alien's client walked into the wall and
 * the server pulled him straight back out. While {@link AlienKit#phasing} (Ghostfreak's Phase, XLR8's) that one check is
 * waved through; {@code OmnitrixAlienCollideMixin} already lets the move itself pass through blocks.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class OmnitrixPhaseMoveCheckMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "isPlayerCollidingWithAnythingNew", at = @At("HEAD"), cancellable = true)
	private void projecthero$alienPhaseThroughWalls(CallbackInfoReturnable<Boolean> cir) {
		AlienKit kit = AlienKit.activeKit(this.player);
		if (kit != null && kit.phasing(this.player)) {
			cir.setReturnValue(false);
		}
	}
}
