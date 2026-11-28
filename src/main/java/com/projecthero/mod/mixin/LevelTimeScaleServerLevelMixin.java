package com.projecthero.mod.mixin;

import java.util.function.BooleanSupplier;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.projecthero.mod.time.LevelTimeScale;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.ServerLevelData;

/**
 * v0.17.7 per-dimension time scale ({@link LevelTimeScale}): on a held tick this level's own "runs normally" switch is off
 * (weather, world border, scheduled block / fluid ticks, random ticks and spawning, raids, block events, the clock -- the
 * switches vanilla's {@code /tick freeze} uses, for this level only), every entity but the anchor's group skips its tick,
 * and the Overworld still counts the shared game clock (every dimension's cooldowns and scheduled ticks run on it) while
 * its day time holds. Inert (one map check) whenever nothing is scaled.
 */
@Mixin(ServerLevel.class)
public abstract class LevelTimeScaleServerLevelMixin {
	@Shadow
	@Final
	private boolean tickTime;

	@Shadow
	@Final
	private ServerLevelData serverLevelData;

	@Shadow
	@Final
	private MinecraftServer server;

	@ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/TickRateManager;runsNormally()Z"))
	private boolean projecthero$levelTimeScale(boolean runsNormally) {
		return runsNormally && !LevelTimeScale.heldNow((ServerLevel) (Object) this);
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void projecthero$levelTimeScaleClock(BooleanSupplier haveTime, CallbackInfo ci) {
		ServerLevel self = (ServerLevel) (Object) this;
		if (this.tickTime && LevelTimeScale.heldNow(self) && this.server.tickRateManager().runsNormally()) {
			// the game clock is shared by every dimension: only the day / night cycle of this one holds
			long t = self.getLevelData().getGameTime() + 1L;
			this.serverLevelData.setGameTime(t);
			this.serverLevelData.getScheduledEvents().tick(this.server, t);
		}
	}

	@Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
	private void projecthero$levelTimeScaleEntity(Entity entity, CallbackInfo ci) {
		if (LevelTimeScale.holds(entity)) {
			ci.cancel();
		}
	}
}
