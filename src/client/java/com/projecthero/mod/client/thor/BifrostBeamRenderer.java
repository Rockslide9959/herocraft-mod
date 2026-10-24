package com.projecthero.mod.client.thor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.client.maxsteel.TurboDraw;
import com.projecthero.mod.stormbreaker.BifrostEntities;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.1: the Bifrost as the films show it -- a column of swirling rainbow light with a blinding white core. On arrival it
 * crashes down out of the sky in a few ticks, flares, and a shockwave ring rolls out over the ground; where the travellers
 * left it lingers a moment and then pulls back up into the sky. Spiral bands of colour climb the column as it burns.
 */
public class BifrostBeamRenderer extends EntityRenderer<BifrostEntities.Beam> {
	private static final int[] RAINBOW = { 0xFF3B3B, 0xFF9A2E, 0xFFE83A, 0x45F06A, 0x3AD8FF, 0x4A6BFF, 0xB84AFF };
	private static final float HEIGHT = 64f;
	private static final float RADIUS = 1.15f;

	public BifrostBeamRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
	}

	@Override
	public void render(BifrostEntities.Beam e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		float t = e.tickCount + partial;
		float life = BifrostEntities.BEAM_TICKS;
		float bottom;
		float alpha;
		float width;
		if (e.arrival()) {
			bottom = HEIGHT * Math.max(0f, 1f - t / 4f); // the crash down
			alpha = t < 22f ? 1f : Math.max(0f, 1f - (t - 22f) / (life - 22f));
			width = t < 6f ? 1.35f - (t / 6f) * 0.35f : 1f - Math.max(0f, t - 22f) / (life - 22f) * 0.7f;
		} else {
			bottom = t < 10f ? 0f : (t - 10f) / (life - 10f) * HEIGHT; // pulls back up
			alpha = Math.max(0f, 1f - Math.max(0f, t - 16f) / (life - 16f));
			width = 1f;
		}
		if (alpha <= 0.01f || bottom >= HEIGHT - 0.5f) {
			return;
		}
		VertexConsumer vc = TurboDraw.buffer(buffers);
		float r = RADIUS * width;
		// the white core and its glow
		float mid = (bottom + HEIGHT) / 2f;
		float half = (HEIGHT - bottom) / 2f;
		pose.pushPose();
		pose.translate(0f, mid, 0f);
		TurboDraw.box(vc, pose.last(), r * 0.32f, half, r * 0.32f, 0xFFFFFF, 0.95f * alpha);
		TurboDraw.box(vc, pose.last(), r * 0.6f, half, r * 0.6f, 0xEAF6FF, 0.35f * alpha);
		pose.popPose();
		// the rainbow sheath: spiral bands of colour climbing the column
		int sides = 12;
		float seg = Mth.TWO_PI * r / sides * 0.55f;
		for (float y = bottom; y < HEIGHT; y += 1.0f) {
			for (int k = 0; k < sides; k++) {
				float ang = k * Mth.TWO_PI / sides + t * 0.18f;
				int band = Math.floorMod((int) (y * 0.45f - t * 0.9f) + k, RAINBOW.length);
				pose.pushPose();
				pose.translate(Mth.cos(ang) * r, y + 0.5f, Mth.sin(ang) * r);
				pose.mulPose(Axis.YP.rotation(-ang));
				TurboDraw.box(vc, pose.last(), 0.05f, 0.5f, seg, RAINBOW[band], 0.55f * alpha);
				pose.popPose();
			}
		}
		// the flare and the shockwave on the ground
		if (bottom <= 0.01f) {
			float sinceLand = e.arrival() ? t - 4f : t;
			if (sinceLand < 14f) {
				float k = sinceLand / 14f;
				float rr = 1.4f + k * 6f;
				int n = 32;
				for (int i = 0; i < n; i++) {
					float ang = i * Mth.TWO_PI / n;
					pose.pushPose();
					pose.translate(Mth.cos(ang) * rr, 0.08f, Mth.sin(ang) * rr);
					pose.mulPose(Axis.YP.rotation(-ang));
					TurboDraw.box(vc, pose.last(), 0.12f, 0.06f, Mth.TWO_PI * rr / n * 0.55f, RAINBOW[i % RAINBOW.length], (1f - k) * 0.8f * alpha);
					pose.popPose();
				}
				pose.pushPose();
				pose.translate(0f, 0.3f, 0f);
				TurboDraw.box(vc, pose.last(), r * 1.8f * (1f - k * 0.5f), 0.3f, r * 1.8f * (1f - k * 0.5f), 0xFFFFFF, (1f - k) * 0.6f * alpha);
				pose.popPose();
			}
		}
	}

	@Override
	public boolean shouldRender(BifrostEntities.Beam e, Frustum frustum, double x, double y, double z) {
		return frustum.isVisible(e.getBoundingBoxForCulling());
	}

	@Override
	public ResourceLocation getTextureLocation(BifrostEntities.Beam e) {
		return TurboDraw.WHITE;
	}
}
