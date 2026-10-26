package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.projecthero.mod.speedforce.SpeedForceSpeedster;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/**
 * v0.17.1: at full momentum the Speed Force Speedster covers 12.5 blocks a tick -- past vanilla's "moved too quickly"
 * limit (about 10 blocks a tick: 100 squared), which would snap a runner on a dedicated server back every packet. Only that
 * one check is waved through, and only for a Speed Force Speedster (singleplayer never runs it anyway); the "moved wrongly"
 * collision check still applies to them like everyone else.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class SpeedForceMoveCheckMixin {
	@Shadow
	public ServerPlayer player;

	@ModifyExpressionValue(method = "handleMovePlayer", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;isSingleplayerOwner()Z"))
	private boolean projecthero$speedForceFastMove(boolean original) {
		return original || SpeedForceSpeedster.hasPower(this.player);
	}
}
