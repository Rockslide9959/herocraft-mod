package com.projecthero.mod.client.omnitrix.alien.buzzshock;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.client.omnitrix.AlienParts;
import com.projecthero.mod.client.omnitrix.OmnitrixLayer;
import com.projecthero.mod.client.omnitrix.alien.BuzzshockClient;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.buzzshock.BuzzshockCopyEntity;
import com.projecthero.mod.omnitrix.alien.buzzshock.BuzzshockKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

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
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.2: one of Buzzshock's copies -- the wide player model in his skin at exactly his size, with his electric glow pass and the
 * Omnitrix badge on its chest like his, hovering (arms out, legs dangling, bobbing), thrusting an arm when it shocks, and copying his
 * split / merge / swarm poses.
 */
public class BuzzshockCopyRenderer extends EntityRenderer<BuzzshockCopyEntity> {
	private final PlayerModel<AbstractClientPlayer> model;

	public BuzzshockCopyRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
		this.model = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false);
		this.shadowRadius = 0.12f;
	}

	@Override
	public ResourceLocation getTextureLocation(BuzzshockCopyEntity e) {
		return BuzzshockClient.SKIN;
	}

	private void resetPose() {
		model.young = false; // a model's young flag starts TRUE (only the living renderer clears it)
		for (ModelPart part : new ModelPart[] { model.head, model.hat, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg,
				model.jacket, model.leftSleeve, model.rightSleeve, model.leftPants, model.rightPants }) {
			part.resetPose();
			part.visible = true;
		}
	}

	@Override
	public void render(BuzzshockCopyEntity e, float entityYaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		resetPose();
		float age = e.tickCount + partial;
		// hovering: arms a little out and forward, legs dangling and swaying
		model.rightArm.xRot = -0.35f + Mth.sin(age * 0.2f) * 0.1f;
		model.leftArm.xRot = -0.35f - Mth.sin(age * 0.2f) * 0.1f;
		model.rightArm.zRot = 0.45f;
		model.leftArm.zRot = -0.45f;
		model.rightLeg.xRot = 0.2f + Mth.sin(age * 0.25f) * 0.15f;
		model.leftLeg.xRot = 0.2f - Mth.sin(age * 0.25f) * 0.15f;
		float zap = e.level().getGameTime() - e.zapAt + partial;
		if (zap >= 0 && zap < 6) {
			float k = Mth.sin(zap / 6f * Mth.PI);
			model.rightArm.xRot = -1.6f * k;
			model.rightArm.zRot = 0.1f;
		}
		Player owner = e.owner();
		if (owner instanceof AbstractClientPlayer cp && Omnitrix.hasPower(cp)) {
			OmnitrixState s = Omnitrix.state(cp);
			if (BuzzshockKit.ID.equals(s.active) && s.transformed() && copied(s.animId)) {
				BuzzshockClient.posed(model, s.animId, cp.level().getGameTime() - s.animStart + partial);
			}
		}
		model.hat.copyFrom(model.head);
		model.jacket.copyFrom(model.body);
		model.leftSleeve.copyFrom(model.leftArm);
		model.rightSleeve.copyFrom(model.rightArm);
		model.leftPants.copyFrom(model.leftLeg);
		model.rightPants.copyFrom(model.rightLeg);

		pose.pushPose();
		float yaw = Mth.rotLerp(partial, e.yRotO, e.getYRot());
		pose.mulPose(Axis.YP.rotationDegrees(180f - yaw));
		float s = (owner != null && BuzzshockKit.isBuzzshock(owner) ? owner.getScale() : BuzzshockClient.SIZE) * 0.9375f;
		pose.scale(-s, -s, s);
		pose.translate(0.0, -1.501, 0.0);
		int overlay = e.hurtTime() > 0 ? OverlayTexture.pack(0, 3) : OverlayTexture.NO_OVERLAY;
		model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(BuzzshockClient.SKIN)), light, overlay, -1);
		BuzzshockClient.glow(pose, buffers, model, age, e.getId(), false);
		boolean recal = owner != null && Omnitrix.recalibrated(owner);
		float[] b = BuzzshockClient.BADGE;
		AlienParts.on(pose, model.body, () -> {
			var vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(recal ? OmnitrixLayer.BADGE_RECAL : OmnitrixLayer.BADGE));
			float h = b[3] / 2f / 16f, y = b[1] / 16f, x = b[0] / 16f, z = b[2] / 16f;
			AlienParts.quad(vc, pose.last(), 0xFFFFFFFF, LightTexture.FULL_BRIGHT, x - h, y - h, z, x + h, y - h, z, x + h, y + h, z, x - h, y + h, z, 0, 0, -1,
					0f, 0f, 1f, 1f);
		});
		pose.popPose();
		super.render(e, entityYaw, partial, pose, buffers, light);
	}

	/** The owner's poses the swarm copies (the rest are his alone). */
	private static boolean copied(int animId) {
		return animId == BuzzshockKit.ANIM_SPLIT || animId == BuzzshockKit.ANIM_SWARM || animId == BuzzshockKit.ANIM_MERGE || animId == BuzzshockKit.ANIM_OVERLOAD;
	}
}
