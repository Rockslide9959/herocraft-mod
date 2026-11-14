package com.projecthero.mod.client.omnitrix.alien.arctiguana;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.client.omnitrix.alien.ArctiguanaClient;
import com.projecthero.mod.omnitrix.alien.arctiguana.ArctiguanaDecoyEntity;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.4 Arctiguana's Ice Decoy: the wide player model in his skin, frozen mid-crouch (his low reptile stance, claws up), drawn as
 * translucent pale-blue ice with a glassy full-bright sheen over it. It forms up out of nothing over its first few ticks and flashes when
 * hit.
 */
public class ArctiguanaDecoyRenderer extends EntityRenderer<ArctiguanaDecoyEntity> {
	private final PlayerModel<AbstractClientPlayer> model;

	public ArctiguanaDecoyRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
		this.model = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false);
		this.shadowRadius = 0.4f;
	}

	@Override
	public ResourceLocation getTextureLocation(ArctiguanaDecoyEntity e) {
		return ArctiguanaClient.SKIN;
	}

	@Override
	public void render(ArctiguanaDecoyEntity e, float entityYaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		model.young = false;
		for (ModelPart part : new ModelPart[] { model.head, model.hat, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg, model.jacket,
				model.leftSleeve, model.rightSleeve, model.leftPants, model.rightPants }) {
			part.resetPose();
			part.visible = true;
		}
		// his stance, frozen: crouched, claws raised
		model.rightArm.xRot = -1.1f;
		model.rightArm.zRot = 0.25f;
		model.leftArm.xRot = -0.8f;
		model.leftArm.zRot = -0.3f;
		model.rightLeg.xRot = -0.25f;
		model.leftLeg.xRot = 0.25f;
		model.head.xRot = -0.2f;
		model.body.xRot = 0.5f;
		model.body.y = 3.2f;
		model.head.y = 4.2f;
		model.rightArm.y = 5.2f;
		model.leftArm.y = 5.2f;
		model.rightLeg.z = 4.0f;
		model.leftLeg.z = 4.0f;
		model.rightLeg.y = 12.2f;
		model.leftLeg.y = 12.2f;
		model.hat.copyFrom(model.head);
		model.jacket.copyFrom(model.body);
		model.leftSleeve.copyFrom(model.leftArm);
		model.rightSleeve.copyFrom(model.rightArm);
		model.leftPants.copyFrom(model.leftLeg);
		model.rightPants.copyFrom(model.rightLeg);

		float age = e.tickCount + partial;
		float form = Mth.clamp(age / 6f, 0f, 1f);
		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(180f - e.statueYaw()));
		pose.scale(-0.9375f, -0.9375f, 0.9375f);
		pose.translate(0.0, -1.501, 0.0);
		int hurt = e.hurtTime > 0 ? OverlayTexture.pack(0, 10) : OverlayTexture.NO_OVERLAY;
		int a = (int) (170 * form);
		// the ice: his skin washed pale blue and see-through, then a glassy sheen over it
		model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(ArctiguanaClient.SKIN)), light, hurt, (a << 24) | 0x9FDCFF);
		int sheen = (int) (60 * form * (0.8f + 0.2f * Mth.sin(age * 0.25f)));
		model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(ArctiguanaClient.SKIN)), LightTexture.FULL_BRIGHT, hurt,
				(sheen << 24) | 0xE8F8FF);
		pose.popPose();
		super.render(e, entityYaw, partial, pose, buffers, light);
	}
}
