package com.projecthero.mod.client.thoracquire;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.client.maxsteel.TurboDraw;
import com.projecthero.mod.client.thor.ThorDraw;
import com.projecthero.mod.thoracquire.entity.StormSpiritEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7: a Storm Spirit -- no body, just lightning: a white-hot core inside two pale-blue shells (spinning boxes of light),
 * a short tail of arcs and three sparks lashing out from it, re-shaped every couple of ticks.
 */
public class StormSpiritRenderer extends EntityRenderer<StormSpiritEntity> {
	public StormSpiritRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.0f;
	}

	@Override
	public ResourceLocation getTextureLocation(StormSpiritEntity entity) {
		return TurboDraw.WHITE;
	}

	@Override
	public void render(StormSpiritEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		float time = e.tickCount + partial;
		VertexConsumer vc = TurboDraw.buffer(buffers);
		float pulse = 0.85f + 0.15f * (float) Math.sin(time * 0.9);
		pose.pushPose();
		pose.translate(0, 0.3, 0);
		pose.mulPose(Axis.YP.rotationDegrees(time * 17f));
		pose.mulPose(Axis.XP.rotationDegrees(time * 11f));
		TurboDraw.box(vc, pose.last(), 0.09f, 0.09f, 0.09f, 0xF4FAFF, 0.95f);
		TurboDraw.box(vc, pose.last(), 0.17f * pulse, 0.17f * pulse, 0.17f * pulse, ThorDraw.GLOW, 0.45f);
		TurboDraw.box(vc, pose.last(), 0.3f * pulse, 0.3f * pulse, 0.3f * pulse, ThorDraw.HALO, 0.18f);
		pose.popPose();

		Vec3 off = e.getPosition(partial).subtract(entityRenderDispatcher.camera.getPosition());
		pose.pushPose();
		pose.translate(-off.x, -off.y, -off.z);
		Vec3 c = off.add(0, 0.3, 0);
		double seed = Math.floor(time / 2.0) * 5.3 + e.getId();
		for (int i = 0; i < 3; i++) {
			double a = ThorDraw.hash(seed + i) * Math.PI * 2;
			double b = (ThorDraw.hash(seed + i + 9) - 0.5) * Math.PI;
			Vec3 tip = c.add(Math.cos(a) * Math.cos(b) * 0.7, Math.sin(b) * 0.7, Math.sin(a) * Math.cos(b) * 0.7);
			ThorDraw.bolt(vc, pose.last(), ThorDraw.jagged(c, tip, 4, 0.12, seed + i), 0.35f, 0.9f);
		}
		Vec3 v = e.getDeltaMovement();
		if (v.lengthSqr() > 1.0e-4) {
			Vec3 tail = c.subtract(v.normalize().scale(1.1));
			ThorDraw.bolt(vc, pose.last(), ThorDraw.jagged(c, tail, 5, 0.15, seed + 77), 0.5f, 0.7f);
		}
		pose.popPose();
		super.render(e, yaw, partial, pose, buffers, light);
	}
}
