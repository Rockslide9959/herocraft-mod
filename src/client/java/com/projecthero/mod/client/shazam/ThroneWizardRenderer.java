package com.projecthero.mod.client.shazam;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.shazam.entity.WizardShazamEntity;
import com.projecthero.mod.shazam.journey.ThroneWizardEntity;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.17.7 Shazam acquisition: the Wizard on his throne in the Rock of Eternity -- the same detailed skin and the same robes, beard
 * and staff ({@link WizardShazamLayer}) as the old visitor, posed seated / rising by {@link ThroneWizardModel} and lowered a
 * little onto the seat while he sits.
 */
public class ThroneWizardRenderer extends HumanoidMobRenderer<WizardShazamEntity, PlayerModel<WizardShazamEntity>> {
	private static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/shazam/wizard_shazam.png");

	public ThroneWizardRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new ThroneWizardModel(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5f);
		addLayer(new WizardShazamLayer(this));
	}

	@Override
	public ResourceLocation getTextureLocation(WizardShazamEntity entity) {
		return SKIN;
	}

	@Override
	public void render(WizardShazamEntity entity, float yaw, float partialTick, PoseStack ps, MultiBufferSource buffers, int light) {
		float sit = entity instanceof ThroneWizardEntity t ? t.sitAmount(partialTick) : 0f;
		ps.pushPose();
		ps.translate(0, -0.12 * sit, 0);
		super.render(entity, yaw, partialTick, ps, buffers, light);
		ps.popPose();
	}
}
