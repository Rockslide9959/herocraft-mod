package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.quirk.engine.Engine;
import com.projecthero.mod.quirk.engine.EngineConfig;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7: Engine's gears multiply movement speed up to ~4x, and vanilla widens the view with movement speed -- at
 * Recipro Turbo it would nearly double. The view still widens for the sense of speed, but never past
 * {@link EngineConfig#FOV_CAP}.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class QuirkEngineFovMixin {
	@Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
	private void projecthero$capEngineFov(CallbackInfoReturnable<Float> cir) {
		if (cir.getReturnValue() > EngineConfig.FOV_CAP && Engine.is((Player) (Object) this)) {
			cir.setReturnValue(EngineConfig.FOV_CAP);
		}
	}
}
