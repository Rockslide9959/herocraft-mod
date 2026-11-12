package com.projecthero.mod.client.shazam;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.shazam.entity.WizardShazamEntity;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.17.3 Shazam: Wizard Shazam, after the user's comic and film references. The player model in his own detailed skin (dark aged
 * skin, deep-set eyes, grey hair, a deep red inner robe under rust outer robes, the gold bolt on his chest, bare arms with gold
 * cuffs, bare feet), posed by {@link WizardShazamModel} (hunched, leaning on his staff) and dressed by {@link WizardShazamLayer}
 * (hair, beard, headband, mantle, drape, skirt, the staff, and the glow). Skin and atlas from scratchpad/shazam/gen_wizard_textures.js.
 */
public class WizardShazamRenderer extends HumanoidMobRenderer<WizardShazamEntity, PlayerModel<WizardShazamEntity>> {
	private static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/shazam/wizard_shazam.png");

	public WizardShazamRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new WizardShazamModel(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5f);
		addLayer(new WizardShazamLayer(this));
	}

	@Override
	public ResourceLocation getTextureLocation(WizardShazamEntity entity) {
		return SKIN;
	}
}
