package com.projecthero.mod.client.omnitrix.alien.echoecho;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.client.omnitrix.alien.EchoEchoClient;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.echoecho.EchoCloneEntity;
import com.projecthero.mod.omnitrix.alien.echoecho.EchoEchoKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.2: an Echo Echo duplicate -- the wide player model in Echo Echo's skin at his size, walking when it moves and copying its
 * owner's move poses (so the clones scream, ring and brace with him). v0.17.2 revamp: no ear discs -- only the skin (user: "no extra things untill i say").
 */
public class EchoCloneRenderer extends EntityRenderer<EchoCloneEntity> {
	private final PlayerModel<AbstractClientPlayer> model;

	public EchoCloneRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
		this.model = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false);
		this.shadowRadius = 0.25f;
	}

	@Override
	public ResourceLocation getTextureLocation(EchoCloneEntity e) {
		return EchoEchoClient.SKIN;
	}

	private void resetPose() {
		for (ModelPart part : new ModelPart[] { model.head, model.hat, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg,
				model.jacket, model.leftSleeve, model.rightSleeve, model.leftPants, model.rightPants }) {
			part.resetPose();
			part.visible = true;
		}
	}

	@Override
	public void render(EchoCloneEntity e, float entityYaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		resetPose();
		float age = e.tickCount + partial;
		double dx = e.getX() - e.xo, dz = e.getZ() - e.zo;
		float speed = (float) Math.min(1.0, Math.sqrt(dx * dx + dz * dz) * 5.0);
		float phase = age * 0.75f;
		model.rightArm.xRot = Mth.cos(phase + Mth.PI) * 1.2f * speed;
		model.leftArm.xRot = Mth.cos(phase) * 1.2f * speed;
		model.rightLeg.xRot = Mth.cos(phase) * 1.3f * speed;
		model.leftLeg.xRot = Mth.cos(phase + Mth.PI) * 1.3f * speed;
		model.rightArm.zRot = Mth.cos(age * 0.09f) * 0.05f + 0.05f;
		model.leftArm.zRot = -(Mth.cos(age * 0.09f) * 0.05f + 0.05f);
		model.head.xRot = Mth.lerp(partial, e.xRotO, e.getXRot()) * Mth.DEG_TO_RAD;
		// the owner's move pose, so every clone moves with him
		Player owner = e.owner();
		if (owner instanceof AbstractClientPlayer cp && Omnitrix.hasPower(cp)) {
			OmnitrixState s = Omnitrix.state(cp);
			if (EchoEchoKit.ID.equals(s.active) && s.transformed()) {
				float ticks = cp.level().getGameTime() - s.animStart + partial;
				EchoEchoClient.posed(cp, model, s.animId, ticks);
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
		float s = EchoEchoClient.SIZE * 0.9375f;
		pose.scale(-s, -s, s);
		pose.translate(0.0, -1.501, 0.0);
		model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(EchoEchoClient.SKIN)), light, OverlayTexture.NO_OVERLAY, -1);
		pose.popPose();
		super.render(e, entityYaw, partial, pose, buffers, light);
	}
}
