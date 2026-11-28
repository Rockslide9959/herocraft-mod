package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.time.LevelTimeScale;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.level.ServerPlayer;

/**
 * v0.17.7 per-dimension time scale ({@link LevelTimeScale}): vanilla sends its (server-wide) tick state to everyone on a
 * {@code /tick} change and to a joining player; a player standing in a scaled dimension is then sent that dimension's rate
 * again, so {@code /tick} keeps working everywhere else.
 */
@Mixin(ServerTickRateManager.class)
public abstract class LevelTimeScaleTickRateMixin {
	@Shadow
	@Final
	private MinecraftServer server;

	@Inject(method = "updateStateToClients", at = @At("TAIL"))
	private void projecthero$levelTimeScaleResync(CallbackInfo ci) {
		LevelTimeScale.afterVanillaBroadcast(this.server);
	}

	@Inject(method = "updateJoiningPlayer", at = @At("TAIL"))
	private void projecthero$levelTimeScaleJoin(ServerPlayer player, CallbackInfo ci) {
		if (LevelTimeScale.scaled(player.level())) {
			LevelTimeScale.syncClient(player);
		}
	}
}
