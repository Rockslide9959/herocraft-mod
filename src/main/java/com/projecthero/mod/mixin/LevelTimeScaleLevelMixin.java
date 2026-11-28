package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.time.LevelTimeScale;

import net.minecraft.world.level.Level;

/** v0.17.7 per-dimension time scale ({@link LevelTimeScale}): block entities (furnaces, hoppers, pistons) hold on a held tick. */
@Mixin(Level.class)
public abstract class LevelTimeScaleLevelMixin {
	@Inject(method = "tickBlockEntities", at = @At("HEAD"), cancellable = true)
	private void projecthero$levelTimeScaleBlockEntities(CallbackInfo ci) {
		if (LevelTimeScale.heldNow((Level) (Object) this)) {
			ci.cancel();
		}
	}
}
