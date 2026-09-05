package com.herocraft.mod.client.mixin;

import com.herocraft.mod.client.gui.RaidSkyTint;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The upper-sky half of the dark-purple raid sky: after vanilla has computed the sky-dome colour for
 * the frame, blend it toward violet by {@link RaidSkyTint#strength()}. Paired with
 * {@code FogRendererMixin}, which handles the horizon and ambient wash.
 */
@Mixin(ClientLevel.class)
public class ClientLevelMixin {
	@Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
	private void herocraft$raidSky(Vec3 pos, float partialTick, CallbackInfoReturnable<Vec3> cir) {
		float s = RaidSkyTint.strength();
		if (s <= 0.0f) {
			return;
		}
		Vec3 purple = new Vec3(0.16, 0.03, 0.24);
		cir.setReturnValue(cir.getReturnValue().lerp(purple, Math.min(1.0f, s)));
	}
}
