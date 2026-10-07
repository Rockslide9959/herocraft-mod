package com.projecthero.mod.client.nova;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.nova.entity.NovaCenturionEntity;
import com.projecthero.mod.nova.item.NovaItems;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.15.13: the dying Nova Corps Centurion -- a player-shaped man (the vanilla default skin underneath) in the same Nova
 * Corps uniform as the player's ({@link NovaSuitRender}), sitting slumped against his pod: legs out in front, one arm
 * limp at his side, the other hand pressed to his chest, head hanging but turning to whoever comes near. While he fades
 * after the hand-off the body turns ghostly and the uniform fades out with him.
 */
public class NovaCenturionRenderer extends HumanoidMobRenderer<NovaCenturionEntity, NovaCenturionRenderer.Model> {
	public NovaCenturionRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new Model(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5f);
		addLayer(new SuitLayer(this));
	}

	public static void initialize() {
		EntityRendererRegistry.register(NovaItems.CENTURION, NovaCenturionRenderer::new);
	}

	@Override
	public ResourceLocation getTextureLocation(NovaCenturionEntity entity) {
		return DefaultPlayerSkin.getDefaultTexture();
	}

	@Override
	protected boolean isBodyVisible(NovaCenturionEntity entity) {
		return !entity.fading() && super.isBodyVisible(entity);
	}

	@Override
	protected void scale(NovaCenturionEntity entity, PoseStack pose, float partialTick) {
		super.scale(entity, pose, partialTick);
		pose.scale(0.9375f, 0.9375f, 0.9375f);
		// sat down on the ground, leaning back against the hull (+Y is down here: the model space is flipped)
		pose.translate(0.0f, 0.72f, 0.0f);
		pose.mulPose(Axis.XP.rotationDegrees(-12f));
	}


	/** The slumped sitting pose. */
	public static class Model extends PlayerModel<NovaCenturionEntity> {
		public Model(ModelPart root) {
			super(root, false);
		}

		@Override
		public void setupAnim(NovaCenturionEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
				float headPitch) {
			super.setupAnim(entity, 0f, 0f, ageInTicks, netHeadYaw, headPitch);
			float breath = Mth.sin(ageInTicks * 0.06f) * 0.03f;
			rightLeg.xRot = -1.45f;
			rightLeg.yRot = 0.28f;
			rightLeg.zRot = 0.05f;
			leftLeg.xRot = -1.35f;
			leftLeg.yRot = -0.18f;
			leftLeg.zRot = -0.05f;
			rightArm.xRot = -0.15f + breath;
			rightArm.yRot = 0f;
			rightArm.zRot = 0.32f;
			leftArm.xRot = -1.05f + breath;
			leftArm.yRot = 0.62f;
			leftArm.zRot = 0.1f;
			head.xRot = Mth.clamp(head.xRot + 0.38f, -0.2f, 0.75f);
			head.zRot = 0.12f;
			hat.copyFrom(head);
			leftPants.copyFrom(leftLeg);
			rightPants.copyFrom(rightLeg);
			leftSleeve.copyFrom(leftArm);
			rightSleeve.copyFrom(rightArm);
			jacket.copyFrom(body);
			// the uniform covers him: the skin's outer layer would only poke through it
			hat.visible = false;
			jacket.visible = false;
			leftSleeve.visible = false;
			rightSleeve.visible = false;
			leftPants.visible = false;
			rightPants.visible = false;
		}
	}

	/** The uniform, fading out with him. */
	private static class SuitLayer extends RenderLayer<NovaCenturionEntity, Model> {
		SuitLayer(NovaCenturionRenderer parent) {
			super(parent);
		}

		@Override
		public void render(PoseStack pose, MultiBufferSource buffers, int light, NovaCenturionEntity entity, float limbSwing,
				float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
			NovaSuitRender.render(pose, buffers, light, getParentModel(), 1f, entity.presence(partialTick), false);
		}
	}
}
