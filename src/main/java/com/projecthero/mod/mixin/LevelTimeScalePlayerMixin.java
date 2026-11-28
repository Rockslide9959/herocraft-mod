package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.time.LevelTimeScale;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/**
 * v0.17.7 per-dimension time scale ({@link LevelTimeScale}): a player in a held dimension (not the caster) sits out their
 * connection tick too -- their living tick (effects, food, mining progress, hurt time) and the movement bookkeeping (the
 * "floating too long" counter would otherwise count a slow-motion jump twenty times too fast) -- exactly as when the whole
 * server ran slowed. Their own client runs at the scaled rate, so their movement already crawls. Packets are still handled
 * as they arrive.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class LevelTimeScalePlayerMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void projecthero$levelTimeScalePlayer(CallbackInfo ci) {
		if (LevelTimeScale.holds(this.player)) {
			ci.cancel();
		}
	}
}
