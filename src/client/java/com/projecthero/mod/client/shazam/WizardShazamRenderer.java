package com.projecthero.mod.client.shazam;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.shazam.entity.WizardShazamEntity;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** v0.17.3 Shazam: Wizard Shazam on the player model in his own skin (long white beard, white robes, red mantle). */
public class WizardShazamRenderer extends HumanoidMobRenderer<WizardShazamEntity, PlayerModel<WizardShazamEntity>> {
	private static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/shazam/wizard_shazam.png");

	public WizardShazamRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
	}

	@Override
	public ResourceLocation getTextureLocation(WizardShazamEntity entity) {
		return SKIN;
	}
}
