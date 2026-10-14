package com.projecthero.mod.client.maxsteel;

import com.projecthero.mod.maxsteel.entity.SteelEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;

import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Renders the Steel Ultralink through GeckoLib. v0.16.4 (user): the new show-accurate model -- white egg shell over a dark
 * core, the cyan ring eye, V chin with a vent grille, side fins and two long thin arms -- with {@code steel_glowmask.png}
 * lighting the eye and the cyan seams in the dark. No shadow -- Steel floats, it does not stand on the ground.
 */
public class SteelRenderer extends GeoEntityRenderer<SteelEntity> {
	public SteelRenderer(EntityRendererProvider.Context context) {
		super(context, new SteelModel());
		this.shadowRadius = 0f;
		withScale(0.78f);
		addRenderLayer(new AutoGlowingGeoLayer<>(this));
	}
}
