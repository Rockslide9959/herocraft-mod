package com.projecthero.mod.client.spider;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.spider.data.SpiderManState;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector4f;

/**
 * Draws nothing. It exists to read where a player's fists really are (v0.12.20): at layer time the pose stack
 * maps the posed player model into camera-relative world space, so pushing the arm's own transform and taking
 * a point at the end of the fist gives the true hand position -- raised swing arm, sneaking, swimming and all.
 * {@link SpiderStrands} then ties every web strand to that point. Only players that currently have a web line
 * or strand pay for the two matrix multiplies.
 */
public class SpiderHandTrackerLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	/** Arm-local Y (blocks) of the fist: the arm box is 12 px tall from the shoulder pivot, minus a little. */
	private static final float FIST_Y = 0.6f;

	public SpiderHandTrackerLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int packedLight, AbstractClientPlayer player,
			float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		SpiderManState s = player.getAttachedOrElse(ModAttachments.SPIDER_MAN_STATE, null);
		if (s == null || !s.hasPower) {
			return;
		}
		if (!s.swinging && !SpiderStrands.wantsCapture(player.getId())) {
			return;
		}
		Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
		PlayerModel<AbstractClientPlayer> model = getParentModel();
		SpiderStrands.captureHands(player.getId(), fist(pose, model.rightArm, cam), fist(pose, model.leftArm, cam));
	}

	private static Vec3 fist(PoseStack pose, ModelPart arm, Vec3 cam) {
		pose.pushPose();
		arm.translateAndRotate(pose);
		Vector4f v = new Vector4f(0.0f, FIST_Y, 0.0f, 1.0f);
		v.mul(pose.last().pose());
		pose.popPose();
		return new Vec3(cam.x + v.x, cam.y + v.y, cam.z + v.z);
	}
}
