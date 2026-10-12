package com.projecthero.mod.client.ultron;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.client.darkseid.BeamDraw;
import com.projecthero.mod.ultron.entity.UltronFistRocketEntity;
import com.projecthero.mod.ultron.entity.UltronSentryEntity;
import com.projecthero.mod.ultron.entity.UltronSkin;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: the Sentry's Fist Rocket -- his own right forearm (the slim player arm in the Sentry's skin, at his 3.4x scale)
 * flying fist-first along its heading, its red eye texels glowing, a thruster flare out of the elbow. Hurt, it flashes like
 * any mob.
 */
public class UltronFistRocketRenderer extends EntityRenderer<UltronFistRocketEntity> {
	private final ModelPart arm;
	private final ModelPart sleeve;

	public UltronFistRocketRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
		PlayerModel<?> model = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true);
		this.arm = model.rightArm;
		this.sleeve = model.rightSleeve;
		this.shadowRadius = 0.4f;
	}

	@Override
	public void render(UltronFistRocketEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
		float y = Mth.rotLerp(pt, e.yRotO, e.getYRot());
		float p = Mth.lerp(pt, e.xRotO, e.getXRot());
		float s = UltronSentryEntity.SCALE * 0.9375f;
		int overlay = OverlayTexture.pack(0, e.hurtTime > 0);
		pose.pushPose();
		pose.translate(0, e.getBbHeight() * 0.5, 0);
		pose.mulPose(Axis.YP.rotationDegrees(-y));
		pose.mulPose(Axis.XP.rotationDegrees(p));
		pose.mulPose(Axis.XP.rotationDegrees(90f)); // the arm's length (+Y in model space) points along the heading
		pose.scale(s, s, s);
		pose.translate(0.5 / 16.0, -4.0 / 16.0, 0);
		for (ModelPart part : new ModelPart[] { arm, sleeve }) {
			part.setPos(0, 0, 0);
			part.setRotation(0, 0, 0);
			part.visible = true;
		}
		UltronSkin skin = UltronSkin.SENTRY;
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(skin.texture()));
		arm.render(pose, vc, light, overlay);
		sleeve.render(pose, vc, light, overlay);
		VertexConsumer eyes = buffers.getBuffer(RenderType.eyes(skin.eyes()));
		arm.render(pose, eyes, 0xF000F0, OverlayTexture.NO_OVERLAY);
		pose.popPose();

		// the thruster flare out of the elbow end (glow first, core second)
		Vec3 back = Vec3.directionFromRotation(p, y).scale(-1);
		Vec3 elbow = new Vec3(0, e.getBbHeight() * 0.5, 0).add(back.scale(0.45 * UltronSentryEntity.SCALE * 0.4));
		Vec3 tail = elbow.add(back.scale(0.9 + 0.25 * Mth.sin((e.tickCount + pt) * 1.7f)));
		Vec3 base = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
		Vec3 cam = entityRenderDispatcher.camera.getPosition().subtract(base);
		PoseStack.Pose last = pose.last();
		BeamDraw.segment(buffers.getBuffer(RenderType.debugQuads()), last, elbow, tail, cam, 0.32f, 0.04f, UltronBeamClient.GLOW, 0.8f, 0.0f);
		BeamDraw.segment(buffers.getBuffer(RenderType.lightning()), last, elbow, tail, cam, 0.14f, 0.02f, UltronBeamClient.CORE, 0.9f, 0.0f);
		super.render(e, yaw, pt, pose, buffers, light);
	}

	@Override
	public ResourceLocation getTextureLocation(UltronFistRocketEntity entity) {
		return UltronSkin.SENTRY.texture();
	}
}
