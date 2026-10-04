package com.projecthero.mod.client.render;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.ironman.fabricator.StarkFabricatorBlockEntity;

import net.minecraft.resources.ResourceLocation;

import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * v0.14.21: the Stark Fabricator's animated rig, drawn on top of the block's static JSON workbench model -- the
 * robotic welding arm, the holographic helmet + scan rings over the glass work plate, and emissive overlays for the
 * front light strip, the gantry strip and the display. Everything that glows lives in
 * {@code stark_fabricator_rig_glowmask.png} (fullbright via {@link AutoGlowingGeoLayer}); the hologram has no base
 * texels at all, so it is pure translucent light. The block's {@code facing} turns it (GeoBlockRenderer reads
 * {@code HorizontalDirectionalBlock.FACING}); the {@code working} state picks idle vs working animation (see
 * {@link StarkFabricatorBlockEntity#registerControllers}). Assets: {@code scratchpad/gen_v01421_ironman_blocks.js}.
 */
public class StarkFabricatorRenderer extends GeoBlockRenderer<StarkFabricatorBlockEntity> {
	public StarkFabricatorRenderer() {
		super(new Model());
		addRenderLayer(new AutoGlowingGeoLayer<>(this));
	}

	static final class Model extends GeoModel<StarkFabricatorBlockEntity> {
		private static final ResourceLocation GEO = ProjectHeroMod.id("geo/stark_fabricator.geo.json");
		private static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/machine/stark_fabricator_rig.png");
		private static final ResourceLocation ANIMATION = ProjectHeroMod.id("animations/stark_fabricator.animation.json");

		@Override
		public ResourceLocation getModelResource(StarkFabricatorBlockEntity be) {
			return GEO;
		}

		@Override
		public ResourceLocation getTextureResource(StarkFabricatorBlockEntity be) {
			return TEXTURE;
		}

		@Override
		public ResourceLocation getAnimationResource(StarkFabricatorBlockEntity be) {
			return ANIMATION;
		}
	}
}
