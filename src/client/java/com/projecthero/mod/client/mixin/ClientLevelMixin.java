package com.projecthero.mod.client.mixin;

import com.projecthero.mod.client.gui.RaidSkyTint;

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
	private void projecthero$raidSky(Vec3 pos, float partialTick, CallbackInfoReturnable<Vec3> cir) {
		float s = RaidSkyTint.strength();
		if (s <= 0.0f) {
			return;
		}
		// v0.13.18: the raid's own colour (violet for the Zombie Raid, reds for Apokolips), the dome a touch brighter
		Vec3 tint = new Vec3(Math.min(1.0, RaidSkyTint.red() * 1.2), Math.min(1.0, RaidSkyTint.green() * 1.2),
				Math.min(1.0, RaidSkyTint.blue() * 1.2));
		cir.setReturnValue(cir.getReturnValue().lerp(tint, Math.min(1.0f, s)));
	}
}
