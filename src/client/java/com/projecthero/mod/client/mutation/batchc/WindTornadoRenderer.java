package com.projecthero.mod.client.mutation.batchc;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.hero.power.p24.WindTornadoEntity;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Wind Manipulation's rideable tornado (v0.13.22): a procedural funnel -- two translucent, counter-rotating cones of
 * streaked air ({@code textures/entity/mutation/p24_tornado.png}, from {@code scratchpad/gen_v01322_c_textures.js})
 * that widen toward the top and snake gently from side to side. The streaks scroll around the funnel (the spin) and
 * the whole thing fades over its last second. No model file.
 */
public class WindTornadoRenderer extends EntityRenderer<WindTornadoEntity> {
	private static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/mutation/p24_tornado.png");
	private static final int SEGMENTS = 18;
	private static final int RINGS = 12;
	private static final float HEIGHT = 5.2f;

	public WindTornadoRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.9f;
		this.shadowStrength = 0.4f;
	}

	@Override
	public void render(WindTornadoEntity e, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
			int light) {
		float time = e.tickCount + partialTick;
		float fade = Mth.clamp(e.life() / 20.0f, 0.0f, 1.0f) * Mth.clamp(time / 8.0f, 0.0f, 1.0f);
		if (fade <= 0.01f) {
			return;
		}
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
		PoseStack.Pose last = pose.last();
		int bright = LightTexture.pack(15, Math.max(8, LightTexture.sky(light)));
		funnel(vc, last, time, 1.0f, time * 0.055f, (int) (170 * fade), bright);
		funnel(vc, last, time + 30, 0.68f, -time * 0.08f, (int) (120 * fade), bright);
		super.render(e, entityYaw, partialTick, pose, buffers, light);
	}

	private static float radius(float h, float scale) {
		float t = h / HEIGHT;
		return (0.3f + t * t * 1.5f + t * 0.5f) * scale;
	}

	private static float wobbleX(float h, float time) {
		return Mth.sin(time * 0.17f + h * 0.9f) * 0.12f * h / HEIGHT * 2;
	}

	private static float wobbleZ(float h, float time) {
		return Mth.cos(time * 0.13f + h * 0.7f) * 0.12f * h / HEIGHT * 2;
	}

	private static void funnel(VertexConsumer vc, PoseStack.Pose pose, float time, float scale, float scroll, int alpha,
			int light) {
		for (int r = 0; r < RINGS; r++) {
			float h0 = HEIGHT * r / RINGS;
			float h1 = HEIGHT * (r + 1) / RINGS;
			float r0 = radius(h0, scale);
			float r1 = radius(h1, scale);
			float cx0 = wobbleX(h0, time);
			float cz0 = wobbleZ(h0, time);
			float cx1 = wobbleX(h1, time);
			float cz1 = wobbleZ(h1, time);
			float v0 = (float) r / RINGS;
			float v1 = (float) (r + 1) / RINGS;
			int a0 = r == 0 ? alpha / 3 : alpha;
			int a1 = r == RINGS - 1 ? alpha / 3 : alpha;
			for (int s = 0; s < SEGMENTS; s++) {
				float t0 = (float) (Math.PI * 2 * s / SEGMENTS);
				float t1 = (float) (Math.PI * 2 * (s + 1) / SEGMENTS);
				float u0 = (float) s / SEGMENTS * 2 + scroll;
				float u1 = (float) (s + 1) / SEGMENTS * 2 + scroll;
				float c0 = Mth.cos(t0);
				float s0 = Mth.sin(t0);
				float c1 = Mth.cos(t1);
				float s1 = Mth.sin(t1);
				vertex(vc, pose, cx0 + c0 * r0, h0, cz0 + s0 * r0, u0, v0, a0, light, c0, s0);
				vertex(vc, pose, cx0 + c1 * r0, h0, cz0 + s1 * r0, u1, v0, a0, light, c1, s1);
				vertex(vc, pose, cx1 + c1 * r1, h1, cz1 + s1 * r1, u1, v1, a1, light, c1, s1);
				vertex(vc, pose, cx1 + c0 * r1, h1, cz1 + s0 * r1, u0, v1, a1, light, c0, s0);
			}
		}
	}

	private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v, int alpha,
			int light, float nx, float nz) {
		vc.addVertex(pose, x, y, z)
				.setColor(235, 244, 255, Mth.clamp(alpha, 0, 255))
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light)
				.setNormal(pose, nx, 0.0f, nz);
	}

	@Override
	public ResourceLocation getTextureLocation(WindTornadoEntity entity) {
		return TEXTURE;
	}

	@Override
	protected boolean shouldShowName(WindTornadoEntity entity) {
		return false;
	}
}
