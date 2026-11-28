package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.projecthero.mod.time.LevelTimeScale;

import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;

/**
 * v0.17.7 per-dimension time scale ({@link LevelTimeScale}): the chunk tick asks the (server-wide) tick rate whether to spawn
 * mobs and run random ticks / lightning; on a held tick of a scaled dimension it doesn't. Chunk loading and entity tracking
 * (positions sent to clients) carry on every tick, so the caster's movement still reaches everyone in real time.
 */
@Mixin(ServerChunkCache.class)
public abstract class LevelTimeScaleChunkCacheMixin {
	@Shadow
	@Final
	ServerLevel level;

	@ModifyExpressionValue(method = "tickChunks", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/TickRateManager;runsNormally()Z"))
	private boolean projecthero$levelTimeScaleChunks(boolean runsNormally) {
		return runsNormally && !LevelTimeScale.heldNow(this.level);
	}
}
