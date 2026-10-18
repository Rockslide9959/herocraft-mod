package com.projecthero.mod.client.mutation.wind;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.hero.power.p24.WindBladeEntity;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.5 Wind Blade: a swept crescent of translucent air, its horns trailing back, flying flat along its path (tilted
 * by the synced roll), with three fading after-images behind it and streaks running out along its edge.
 */
public class WindBladeRenderer extends EntityRenderer<WindBladeEntity> {
	private static final int STEPS = 16;
	/** Half-angle of the arc (just short of 90 degrees, so the horns come to points). */
	private static final float ARC = 1.42f;

	public WindBladeRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.0f;
	}

	@Override
	public void render(WindBladeEntity e, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
		Vec3 v = e.getDeltaMovement();
		if (v.lengthSqr() < 1.0e-6) {
			return;
		}
		float time = e.tickCount + partialTick;
		float grow = Mth.clamp(time / 2.5f, 0.35f, 1.0f);
		float size = e.size() * grow;
		float yaw = (float) Mth.atan2(v.x, v.z);
		float pitch = (float) Mth.atan2(v.y, v.horizontalDistance());
		int bright = LightTexture.pack(15, Math.max(10, LightTexture.sky(light)));
		if (e.cyclone()) {
			cyclone(e, time, yaw, pitch, pose, buffers, bright);
			super.render(e, entityYaw, partialTick, pose, buffers, light);
			return;
		}
		pose.pushPose();
		pose.translate(0, 0.1, 0);
		pose.mulPose(Axis.YP.rotation(yaw));
		pose.mulPose(Axis.XP.rotation(-pitch));
		pose.mulPose(Axis.ZP.rotation(e.roll()));
		// the solid body of compressed air, densest along the cutting edge
		VertexConsumer body = buffers.getBuffer(RenderType.entityTranslucent(WindMesh.SOLID));
		crescent(body, pose.last(), size * 1.6f, size * 0.8f, 0, 0, 150, bright, 0.0f);
		// the streaked skin on it, then after-images trailing behind, smaller and fainter
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(WindMesh.STREAK));
		for (int k = 0; k < 4; k++) {
			float s = size * (1.0f - 0.13f * k);
			float back = -0.6f * k * e.size();
			float fade = k == 0 ? 1.0f : 0.6f / k;
			crescent(vc, pose.last(), s * 1.6f, s * 0.8f, back, time * 0.25f + k * 0.3f, (int) (235 * fade), bright, 0.01f);
			if (k == 0) {
				// a thin bright core and a faint wide halo give it depth
				crescent(vc, pose.last(), s * 1.5f, s * 0.55f, back + 0.06f, -time * 0.4f, 200, bright, 0.04f);
				crescent(vc, pose.last(), s * 1.8f, s * 1.0f, back - 0.1f, time * 0.15f, 110, bright, -0.04f);
			}
		}
		pose.popPose();
		super.render(e, entityYaw, partialTick, pose, buffers, light);
	}

	/**
	 * v0.16.6 Cyclone Blade (R hold): a 3-block whirling disc of air flying flat along its path -- three swept crescent
	 * arms spinning round a churning core (two counter-turning layers), a bright rim band and a short spinning drum of
	 * rings in the middle, tipped a little toward the viewer so it never vanishes edge-on.
	 */
	private static void cyclone(WindBladeEntity e, float time, float yaw, float pitch, PoseStack pose, MultiBufferSource buffers, int light) {
		float grow = Mth.clamp(time / 3.0f, 0.4f, 1.0f);
		float rad = (float) e.halfWidth() * grow;
		pose.pushPose();
		pose.translate(0, 0.1, 0);
		pose.mulPose(Axis.YP.rotation(yaw));
		pose.mulPose(Axis.XP.rotation(-pitch));
		pose.mulPose(Axis.XP.rotation(-0.6f)); // tipped back, so the disc reads from the side and from behind
		VertexConsumer body = buffers.getBuffer(RenderType.entityTranslucent(WindMesh.SOLID));
		pose.pushPose();
		pose.mulPose(Axis.YP.rotation(time * 0.9f));
		for (int k = 0; k < 3; k++) {
			pose.pushPose();
			pose.mulPose(Axis.YP.rotation(k * Mth.TWO_PI / 3));
			crescent(body, pose.last(), rad * 0.7f, rad * 0.45f, rad * 0.6f, 0, 190, light, 0.0f);
			pose.popPose();
		}
		pose.popPose();
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(WindMesh.STREAK));
		// the spinning arms (streaked skin), then a second counter-turning set a little above
		for (int layer = 0; layer < 2; layer++) {
			pose.pushPose();
			pose.translate(0, layer * 0.18f - 0.09f, 0);
			pose.mulPose(Axis.YP.rotation((layer == 0 ? 1 : -0.7f) * time * 0.9f + layer));
			for (int k = 0; k < 3; k++) {
				pose.pushPose();
				pose.mulPose(Axis.YP.rotation(k * Mth.TWO_PI / 3));
				crescent(vc, pose.last(), rad * 0.66f, rad * 0.46f, rad * 0.6f, time * 0.3f + k, layer == 0 ? 235 : 150, light, 0.01f);
				pose.popPose();
			}
			pose.popPose();
		}
		// the rim: a bright band racing round the edge, a second one a little inside it, and the churning air between
		pose.pushPose();
		pose.mulPose(Axis.YP.rotation(time * 1.3f));
		WindMesh.band(vc, pose.last(), -0.22f, rad * 0.95f, 0.22f, rad, 36, 4, time * 0.1f, 0.15f, 0.85f, 255, 255, light);
		WindMesh.band(vc, pose.last(), -0.14f, rad * 0.72f, 0.14f, rad * 0.75f, 32, 3, -time * 0.15f, 0.2f, 0.8f, 200, 200, light);
		WindMesh.flatRing(vc, pose.last(), 0.02f, rad * 0.2f, rad * 0.98f, 36, time * 0.2f, 200, 120, light);
		WindMesh.flatRing(vc, pose.last(), -0.02f, rad * 0.2f, rad * 0.98f, 36, -time * 0.25f, 150, 90, light);
		pose.popPose();
		// the core: a short drum of rings whirling the other way
		for (int k = 0; k < 3; k++) {
			float y = (k - 1) * 0.16f;
			float r = rad * (0.3f - Math.abs(k - 1) * 0.06f);
			pose.pushPose();
			pose.translate(0, y, 0);
			pose.mulPose(Axis.YP.rotation(-time * 1.6f + k));
			WindMesh.band(vc, pose.last(), -0.07f, r, 0.07f, r * 1.05f, 20, 2, 0, 0.2f, 0.8f, 230, 230, light);
			pose.popPose();
		}
		pose.popPose();
	}

	/**
	 * One crescent in the local xz plane (+z = forward): the outer edge bulges {@code depth} forward, the inner edge
	 * only a third as far, so the band is thickest in the middle and the horns sweep back to points.
	 */
	private static void crescent(VertexConsumer vc, PoseStack.Pose pose, float width, float depth, float z, float scroll, int alpha,
			int light, float y) {
		for (int i = 0; i < STEPS; i++) {
			float t0 = -ARC + 2 * ARC * i / STEPS;
			float t1 = -ARC + 2 * ARC * (i + 1) / STEPS;
			float s0 = Mth.sin(t0), c0 = Mth.cos(t0), s1 = Mth.sin(t1), c1 = Mth.cos(t1);
			float zo0 = z + c0 * depth - depth * 0.5f, zo1 = z + c1 * depth - depth * 0.5f;
			float zi0 = z + c0 * depth * 0.32f - depth * 0.5f, zi1 = z + c1 * depth * 0.32f - depth * 0.5f;
			// horns fade out, the leading edge is the densest
			int ao0 = (int) (alpha * Math.pow(c0, 0.55)), ao1 = (int) (alpha * Math.pow(c1, 0.55));
			int ai0 = ao0 / 4, ai1 = ao1 / 4;
			float u0 = (float) i / STEPS * 1.5f + scroll, u1 = (float) (i + 1) / STEPS * 1.5f + scroll;
			WindMesh.vertex(vc, pose, s0 * width * 0.96f, y, zi0, u0, 0.15f, WindMesh.R, WindMesh.G, WindMesh.B, ai0, light, 0, 1, 0);
			WindMesh.vertex(vc, pose, s1 * width * 0.96f, y, zi1, u1, 0.15f, WindMesh.R, WindMesh.G, WindMesh.B, ai1, light, 0, 1, 0);
			WindMesh.vertex(vc, pose, s1 * width, y, zo1, u1, 0.85f, WindMesh.R, WindMesh.G, WindMesh.B, ao1, light, 0, 1, 0);
			WindMesh.vertex(vc, pose, s0 * width, y, zo0, u0, 0.85f, WindMesh.R, WindMesh.G, WindMesh.B, ao0, light, 0, 1, 0);
		}
	}

	@Override
	public ResourceLocation getTextureLocation(WindBladeEntity entity) {
		return WindMesh.STREAK;
	}

	@Override
	protected boolean shouldShowName(WindBladeEntity entity) {
		return false;
	}
}
