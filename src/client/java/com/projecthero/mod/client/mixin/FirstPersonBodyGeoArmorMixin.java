package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.projecthero.mod.client.fpbody.CullingBufferSource;
import com.projecthero.mod.client.fpbody.FirstPersonBody;

import net.minecraft.client.renderer.MultiBufferSource;

import software.bernie.geckolib.renderer.GeoArmorRenderer;

/**
 * v0.15.15 full-body first person ({@link FirstPersonBody}): GeckoLib's armour renderer draws into the game's main buffer
 * source (or the outline one) rather than the source the armour layer hands it, so while the wearer's own body is drawn
 * the culling source is swapped in -- otherwise every GeckoLib helmet (Iron Man, Max Steel, Thor ...) sat over the lens.
 */
@Mixin(GeoArmorRenderer.class)
public abstract class FirstPersonBodyGeoArmorMixin {
	@ModifyVariable(method = "renderToBuffer", at = @At("STORE"))
	private MultiBufferSource projecthero$fpBodySource(MultiBufferSource source) {
		CullingBufferSource culled = FirstPersonBody.renderingSource();
		return culled != null ? culled : source;
	}
}
