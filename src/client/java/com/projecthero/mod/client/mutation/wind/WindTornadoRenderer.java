package com.projecthero.mod.client.mutation.wind;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.hero.power.p24.WindTornadoEntity;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.16.5 Wind Manipulation tornadoes: a funnel built of stacked translucent rings of streaked air, each ring spinning
 * at its own speed (faster near the ground) and snaking side to side, inside an outer funnel skin and over a flat swirl
 * of dust on the ground. The travelling tornado (Z) stands about 7 blocks tall; the rideable one (C) is a squat 3.6-block
 * column with a wide flat top the rider stands on. It grows in over its first half second and fades over its last.
 */
public class WindTornadoRenderer extends EntityRenderer<WindTornadoEntity> {
	private static final int SEGMENTS = 22;

	public WindTornadoRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 1.0f;
		this.shadowStrength = 0.35f;
	}

	@Override
	public void render(WindTornadoEntity e, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
		float time = e.tickCount + partialTick;
		boolean ride = e.mode() == WindTornadoEntity.RIDE;
		float fadeOut = ride ? 1.0f : Mth.clamp(e.life() / 20.0f, 0.0f, 1.0f);
		float grow = Mth.clamp(time / 10.0f, 0.0f, 1.0f);
		float fade = fadeOut * grow;
		if (fade <= 0.01f) {
			return;
		}
		float height = (ride ? 3.6f : 7.0f) * (0.4f + 0.6f * grow);
		int bright = LightTexture.pack(15, Math.max(9, LightTexture.sky(light)));
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(WindMesh.STREAK));
		// v0.16.6 Twin Tornadoes (Z hold): the travelling funnel's model at 72% -- a 5-block twister circling the hero
		boolean orbit = e.mode() == WindTornadoEntity.ORBIT;
		pose.pushPose();
		if (orbit) {
			pose.scale(0.72f, 0.72f, 0.72f);
		}

		// the dust swirl on the ground
		pose.pushPose();
		pose.mulPose(Axis.YP.rotation(time * 0.12f));
		WindMesh.flatRing(vc, pose.last(), 0.05f, 0.3f, ride ? 2.0f : 2.8f, SEGMENTS, time * 0.05f, (int) (110 * fade), 0, bright);
		pose.popPose();

		// the outer skin of the funnel (two counter-turning layers)
		skin(vc, pose, time, height, ride, 1.0f, time * 0.06f, (int) (95 * fade), bright);
		skin(vc, pose, time + 40, height, ride, 0.72f, -time * 0.09f, (int) (75 * fade), bright);

		// the stacked rings
		int rings = ride ? 6 : 11;
		for (int i = 0; i < rings; i++) {
			float h = height * (i + 0.5f) / rings;
			float r = radius(h / height, ride);
			float spin = time * (0.55f - 0.035f * i) + i * 1.3f;
			float half = height / rings * 0.32f;
			pose.pushPose();
			pose.translate(wobbleX(h, time, height), h, wobbleZ(h, time, height));
			pose.mulPose(Axis.YP.rotation(spin));
			pose.mulPose(Axis.XP.rotation(0.12f * Mth.sin(time * 0.1f + i)));
			int a = (int) (150 * fade);
			WindMesh.band(vc, pose.last(), -half, r * 1.06f, half, r * 1.12f, SEGMENTS, 3, 0, 0.2f, 0.8f, a / 3, a, bright);
			WindMesh.band(vc, pose.last(), -half * 0.6f, r * 0.9f, half * 0.6f, r * 0.94f, SEGMENTS, 2, 0.5f, 0.3f, 0.7f, a / 2, a / 2, bright);
			pose.popPose();
		}
		if (ride) {
			// the rider's platform: a dense flat swirl at the top
			pose.pushPose();
			pose.translate(wobbleX(height, time, height), height - 0.05f, wobbleZ(height, time, height));
			pose.mulPose(Axis.YP.rotation(-time * 0.4f));
			WindMesh.flatRing(vc, pose.last(), 0, 0.0f, radius(1, true) * 1.15f, SEGMENTS, time * 0.1f, (int) (150 * fade), (int) (60 * fade),
					bright);
			pose.popPose();
		}
		pose.popPose();
		super.render(e, entityYaw, partialTick, pose, buffers, light);
	}

	/** Funnel radius at {@code t} (0 = ground, 1 = top). */
	private static float radius(float t, boolean ride) {
		return ride ? 0.45f + t * t * 1.0f + t * 0.35f : 0.35f + t * t * 1.9f + t * 0.45f;
	}

	private static float wobbleX(float h, float time, float height) {
		return Mth.sin(time * 0.17f + h * 0.8f) * 0.28f * h / height;
	}

	private static float wobbleZ(float h, float time, float height) {
		return Mth.cos(time * 0.13f + h * 0.6f) * 0.28f * h / height;
	}

	private static void skin(VertexConsumer vc, PoseStack pose, float time, float height, boolean ride, float scale, float scroll,
			int alpha, int light) {
		int slices = 10;
		for (int i = 0; i < slices; i++) {
			float h0 = height * i / slices;
			float h1 = height * (i + 1) / slices;
			float r0 = radius(h0 / height, ride) * scale;
			float r1 = radius(h1 / height, ride) * scale;
			float dx0 = wobbleX(h0, time, height), dz0 = wobbleZ(h0, time, height);
			float dx1 = wobbleX(h1, time, height), dz1 = wobbleZ(h1, time, height);
			int a0 = i == 0 ? alpha / 3 : alpha;
			int a1 = i == slices - 1 ? alpha / 3 : alpha;
			PoseStack.Pose last = pose.last();
			for (int s = 0; s < SEGMENTS; s++) {
				float t0 = Mth.TWO_PI * s / SEGMENTS;
				float t1 = Mth.TWO_PI * (s + 1) / SEGMENTS;
				float c0 = Mth.cos(t0), s0 = Mth.sin(t0), c1 = Mth.cos(t1), s1 = Mth.sin(t1);
				float u0 = (float) s / SEGMENTS * 2 + scroll, u1 = (float) (s + 1) / SEGMENTS * 2 + scroll;
				float v0 = (float) i / slices, v1 = (float) (i + 1) / slices;
				WindMesh.vertex(vc, last, dx0 + c0 * r0, h0, dz0 + s0 * r0, u0, v0, WindMesh.R, WindMesh.G, WindMesh.B, a0, light, c0, 0, s0);
				WindMesh.vertex(vc, last, dx0 + c1 * r0, h0, dz0 + s1 * r0, u1, v0, WindMesh.R, WindMesh.G, WindMesh.B, a0, light, c1, 0, s1);
				WindMesh.vertex(vc, last, dx1 + c1 * r1, h1, dz1 + s1 * r1, u1, v1, WindMesh.R, WindMesh.G, WindMesh.B, a1, light, c1, 0, s1);
				WindMesh.vertex(vc, last, dx1 + c0 * r1, h1, dz1 + s0 * r1, u0, v1, WindMesh.R, WindMesh.G, WindMesh.B, a1, light, c0, 0, s0);
			}
		}
	}

	@Override
	public ResourceLocation getTextureLocation(WindTornadoEntity entity) {
		return WindMesh.STREAK;
	}

	@Override
	protected boolean shouldShowName(WindTornadoEntity entity) {
		return false;
	}
}
