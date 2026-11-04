package com.projecthero.mod.client.omnitrix.alien.ditto;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.client.omnitrix.AlienParts;
import com.projecthero.mod.client.omnitrix.OmnitrixLayer;
import com.projecthero.mod.client.omnitrix.alien.DittoClient;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.ditto.DittoCopyEntity;
import com.projecthero.mod.omnitrix.alien.ditto.DittoKit;
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
 * v0.17.2: one of Ditto's copies -- the wide player model in Ditto's skin at exactly his size, the Omnitrix badge on its forehead
 * like his (in the show every copy wears the symbol), running when it moves, swinging when it punches and copying his big move poses
 * (the pack splits, spins and points with him).
 */
public class DittoCopyRenderer extends EntityRenderer<DittoCopyEntity> {
	private final PlayerModel<AbstractClientPlayer> model;

	public DittoCopyRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
		this.model = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false);
		this.shadowRadius = 0.2f;
	}

	@Override
	public ResourceLocation getTextureLocation(DittoCopyEntity e) {
		return DittoClient.SKIN;
	}

	private void resetPose() {
		model.young = false; // a model's young flag starts TRUE (only the living renderer clears it): left on, a copy is drawn as a baby
		for (ModelPart part : new ModelPart[] { model.head, model.hat, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg,
				model.jacket, model.leftSleeve, model.rightSleeve, model.leftPants, model.rightPants }) {
			part.resetPose();
			part.visible = true;
		}
	}

	@Override
	public void render(DittoCopyEntity e, float entityYaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		resetPose();
		float age = e.tickCount + partial;
		double dx = e.getX() - e.xo, dz = e.getZ() - e.zo;
		float speed = (float) Math.min(1.0, Math.sqrt(dx * dx + dz * dz) * 5.0);
		float phase = age * 0.9f;
		model.rightArm.xRot = Mth.cos(phase + Mth.PI) * 1.3f * speed;
		model.leftArm.xRot = Mth.cos(phase) * 1.3f * speed;
		model.rightLeg.xRot = Mth.cos(phase) * 1.4f * speed;
		model.leftLeg.xRot = Mth.cos(phase + Mth.PI) * 1.4f * speed;
		model.rightArm.zRot = Mth.cos(age * 0.09f) * 0.05f + 0.05f;
		model.leftArm.zRot = -(Mth.cos(age * 0.09f) * 0.05f + 0.05f);
		if (e.holding() || !e.onGround() && Math.abs(e.getY() - e.yo) > 0.05) {
			// holding something up (Copy Carry), leaping or climbing: arms up
			model.rightArm.xRot = -2.6f;
			model.leftArm.xRot = -2.6f;
		}
		// a punch: the right arm jabs forward (6 ticks)
		float swing = e.level().getGameTime() - e.swingAt + partial;
		if (swing >= 0 && swing < 6) {
			float k = Mth.sin(swing / 6f * Mth.PI);
			model.rightArm.xRot = -1.6f * k;
			model.rightArm.yRot = 0.2f * k;
			model.body.yRot = 0.25f * k;
		}
		Player owner = e.owner();
		if (owner instanceof AbstractClientPlayer cp && Omnitrix.hasPower(cp)) {
			OmnitrixState s = Omnitrix.state(cp);
			if (DittoKit.ID.equals(s.active) && s.transformed() && copied(s.animId)) {
				DittoClient.posed(model, s.animId, cp.level().getGameTime() - s.animStart + partial);
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
		float s = (owner != null && DittoKit.isDitto(owner) ? owner.getScale() : DittoClient.SIZE) * 0.9375f;
		pose.scale(-s, -s, s);
		pose.translate(0.0, -1.501, 0.0);
		int overlay = e.hurtTime() > 0 ? OverlayTexture.pack(0, 3) : OverlayTexture.NO_OVERLAY;
		model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(DittoClient.SKIN)), light, overlay, -1);
		// the Omnitrix badge on its forehead, like his (green on the recalibrated watch)
		boolean recal = owner != null && Omnitrix.recalibrated(owner);
		float[] b = DittoClient.BADGE;
		AlienParts.on(pose, model.head, () -> {
			var vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(recal ? OmnitrixLayer.BADGE_RECAL : OmnitrixLayer.BADGE));
			float h = b[3] / 2f / 16f, y = b[1] / 16f, x = b[0] / 16f, z = b[2] / 16f;
			AlienParts.quad(vc, pose.last(), 0xFFFFFFFF, LightTexture.FULL_BRIGHT, x - h, y - h, z, x + h, y - h, z, x + h, y + h, z, x - h, y + h, z, 0, 0, -1,
					0f, 0f, 1f, 1f);
		});
		pose.popPose();
		super.render(e, entityYaw, partial, pose, buffers, light);
	}

	/** The owner's poses the pack copies (the rest are his alone). */
	private static boolean copied(int animId) {
		return animId == DittoKit.ANIM_KICK || animId == DittoKit.ANIM_SPLIT || animId == DittoKit.ANIM_ARMY || animId == DittoKit.ANIM_BURST
				|| animId == DittoKit.ANIM_MERGE;
	}
}
