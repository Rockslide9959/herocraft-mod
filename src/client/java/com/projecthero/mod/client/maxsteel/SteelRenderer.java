package com.projecthero.mod.client.maxsteel;

import com.projecthero.mod.maxsteel.entity.SteelEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;

import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Renders the Steel Ultralink through GeckoLib with the supplied {@code steel_entity.geo.json}. Scale
 * comes from {@code STEEL_MODEL_SPEC.json} ({@code recommended_scale: 0.78}). No shadow -- Steel
 * floats, it does not stand on the ground.
 */
public class SteelRenderer extends GeoEntityRenderer<SteelEntity> {
	public SteelRenderer(EntityRendererProvider.Context context) {
		super(context, new SteelModel());
		this.shadowRadius = 0f;
		withScale(0.78f);
	}
}
