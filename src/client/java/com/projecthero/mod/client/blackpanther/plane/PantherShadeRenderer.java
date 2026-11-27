package com.projecthero.mod.client.blackpanther.plane;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.blackpanther.plane.PantherShadeEntity;
import com.projecthero.mod.blackpanther.plane.ShadeTrial;

import net.minecraft.client.model.OcelotModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

/**
 * v0.17.7 Black Panther Ancestral Plane: the panther shades and Bast's spirit -- the vanilla ocelot model (no custom body) at
 * 1.6x in a dark, translucent, violet-rimmed hide ({@code textures/entity/ancestral/panther_shade.png}), materialising (growing in)
 * as it appears; its eyes glow violet and flare white-violet as it crouches for the pounce (the tell). Bast's spirit is the same
 * at {@link PantherShadeEntity#size} times, in a gold-and-violet starlit hide with burning gold eyes.
 */
public class PantherShadeRenderer extends MobRenderer<PantherShadeEntity, OcelotModel<PantherShadeEntity>> {
	private static final ResourceLocation HIDE = ProjectHeroMod.id("textures/entity/ancestral/panther_shade.png");
	private static final ResourceLocation SPIRIT = ProjectHeroMod.id("textures/entity/ancestral/bast_spirit.png");
	private static final ResourceLocation EYES = ProjectHeroMod.id("textures/entity/ancestral/panther_shade_eyes.png");
	private static final ResourceLocation SPIRIT_EYES = ProjectHeroMod.id("textures/entity/ancestral/bast_spirit_eyes.png");
	private static final float SHADE_SCALE = 1.6f;

	public PantherShadeRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new OcelotModel<>(ctx.bakeLayer(ModelLayers.OCELOT)), 0.0f);
		addLayer(new Eyes(this));
	}

	@Override
	public ResourceLocation getTextureLocation(PantherShadeEntity entity) {
		return entity.spirit() ? SPIRIT : HIDE;
	}

	@Override
	protected RenderType getRenderType(PantherShadeEntity entity, boolean bodyVisible, boolean translucent, boolean glowing) {
		return entity.spirit() ? RenderType.entityTranslucentEmissive(getTextureLocation(entity)) : RenderType.entityTranslucent(getTextureLocation(entity));
	}

	@Override
	protected void scale(PantherShadeEntity entity, PoseStack pose, float partialTick) {
		float s = SHADE_SCALE * entity.size();
		float grow = Mth.clamp((entity.tickCount + partialTick) / (float) ShadeTrial.APPEAR_TICKS, 0f, 1f);
		s *= 0.55f + 0.45f * grow * grow * (3 - 2 * grow);
		pose.scale(s, s, s);
	}

	@Override
	protected boolean shouldShowName(PantherShadeEntity entity) {
		return false;
	}

	/** The glowing eyes: violet, flaring in the crouch before the pounce; gold for Bast. */
	static final class Eyes extends RenderLayer<PantherShadeEntity, OcelotModel<PantherShadeEntity>> {
		Eyes(PantherShadeRenderer parent) {
			super(parent);
		}

		@Override
		public void render(PoseStack pose, MultiBufferSource buffers, int light, PantherShadeEntity e, float limbSwing, float limbSwingAmount,
				float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
			VertexConsumer vc = buffers.getBuffer(RenderType.eyes(e.spirit() ? SPIRIT_EYES : EYES));
			float k = 0.7f;
			if (!e.spirit() && e.phase() == PantherShadeEntity.WINDUP) {
				k = 0.85f + 0.15f * Mth.sin(ageInTicks * 1.2f);
			}
			int c = (int) (k * 255);
			getParentModel().renderToBuffer(pose, vc, 0xF000F0, OverlayTexture.NO_OVERLAY, FastColor.ARGB32.color(255, c, c, c));
		}
	}
}
