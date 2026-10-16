package com.projecthero.mod.client.revamp.batcha;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.client.greenlantern.HardLightRibbon;
import com.projecthero.mod.client.mutation.MutationRender;
import com.projecthero.mod.hero.revamp.batcha.FlightFxEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.16.5 Flight rework: the bursts of air as models, every one drawn in the effect's own frame (local +Z along the
 * synced yaw / pitch -- up for the ground effects):
 * <ul>
 * <li><b>Sonic Boom</b> -- the white vapour cone round the flier where the barrier breaks, then four shock rings racing out
 * along a 10-block cone with a faint sheet between them;</li>
 * <li><b>Dive Bomb crater</b> -- a shock ring sweeping out over the ground, a scorched crater with radial cracks and a dust
 * rim that fade slowly, and chunks of the ground block thrown up and tumbling back down;</li>
 * <li><b>Takeoff Blast</b> -- two ground rings and a column of wind shooting up after the flier;</li>
 * <li><b>Updraft</b> -- a twisting column of wind ribbons six blocks round, spiralling upward;</li>
 * <li><b>punch / dash</b> -- an impact ring with flared shock rings, the vapour ring a dash leaves behind.</li>
 * </ul>
 */
public class FlightFxRenderer extends EntityRenderer<FlightFxEntity> {
	private static final int AIR = 0xEAF4FF;
	private static final int SHOCK = 0xBFE2FF;
	private static final int DUST = 0x8C7A62;
	private static final int SCORCH = 0x241E1A;

	public FlightFxRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.0f;
	}

	@Override
	public ResourceLocation getTextureLocation(FlightFxEntity entity) {
		return MutationRender.WHITE;
	}

	@Override
	public void render(FlightFxEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		float age = e.tickCount + partial;
		int life = FlightFxEntity.life(e.kind());
		float t = Mth.clamp(age / life, 0f, 1f);
		if (t >= 1f) {
			return;
		}
		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
		pose.mulPose(Axis.XP.rotationDegrees(e.getXRot()));
		PoseStack.Pose last = pose.last();
		float r = e.radius();
		switch (e.kind()) {
			case FlightFxEntity.SONIC_CONE -> sonicCone(buffers, last, r, t, age);
			case FlightFxEntity.CRATER -> crater(buffers, pose, light, e.block(), r, t, age);
			case FlightFxEntity.TAKEOFF -> takeoff(buffers, last, r, t, age);
			case FlightFxEntity.UPDRAFT -> updraft(buffers, last, r, t, age);
			case FlightFxEntity.PUNCH -> punch(buffers, last, r, t);
			default -> vapourRing(buffers, last, r, t);
		}
		pose.popPose();
	}

	/** Fetched afresh for every primitive: asking for the other buffer ends this one ("Not building!"). */
	private static VertexConsumer T(MultiBufferSource b) {
		return HardLightRibbon.translucent(b);
	}

	private static VertexConsumer A(MultiBufferSource b) {
		return HardLightRibbon.additive(b);
	}

	// ================================================================ kinds

	private static void sonicCone(MultiBufferSource b, PoseStack.Pose p, float range, float t, float age) {
		float tan = 0.84f; // ~40 degrees
		// the vapour cone (Prandtl-Glauert) round the flier: a white shell flaring back from the nose, gone by 60%
		float va = 0.6f * (1f - Mth.clamp(t / 0.6f, 0f, 1f));
		frustum(T(b), p, -0.2f, -2.6f, 0.25f, 1.9f + t * 1.5f, AIR, va, va * 0.15f, 28, 0f);
		frustum(T(b), p, -0.1f, -1.4f, 0.15f, 1.1f + t, 0xFFFFFF, va * 0.8f, 0f, 28, 0.4f);
		// the four shock rings racing out along the cone
		for (int i = 0; i < 4; i++) {
			float k = Mth.clamp(t * 1.6f - i * 0.16f, 0f, 1f);
			if (k <= 0f || k >= 1f) {
				continue;
			}
			float z = 0.6f + k * range;
			float a = 0.75f * (1f - k);
			ring(T(b), p, z, z * tan, 0.22f + k * 0.35f, SHOCK, a, 40, age * 0.1f);
			ring(A(b), p, z, z * tan, 0.08f, 0xC8E6FF, a * 0.6f, 40, 0f);
		}
		// the sheet of the cone, out to the leading ring
		float front = 0.6f + Mth.clamp(t * 1.6f, 0f, 1f) * range;
		float sa = 0.16f * (1f - t);
		frustum(T(b), p, 0.6f, front, 0.5f, front * tan, SHOCK, sa * 0.4f, sa, 32, 0f);
	}

	private static void crater(MultiBufferSource b, PoseStack pose, int light,
			BlockState block, float radius, float t, float age) {
		PoseStack.Pose p = pose.last();
		float fade = 1f - Mth.clamp((t - 0.7f) / 0.3f, 0f, 1f);
		// the scorched crater and its radial cracks, lying a hair above the ground
		disc(T(b), p, 0.02f, radius * 0.55f, SCORCH, 0.62f * fade, 0.3f * fade, 28);
		for (int i = 0; i < 11; i++) {
			float a = i * Mth.TWO_PI / 11f + (i % 3) * 0.21f;
			float len = radius * (0.75f + 0.35f * ((i * 37) % 7) / 7f);
			crack(T(b), p, a, radius * 0.3f, len, 0.06f + 0.04f * (i % 2), 0.03f, SCORCH, 0.7f * fade);
		}
		// the dust rim: a low wall of dust round the crater, slumping as it fades
		float rimH = 0.55f * (1f - t * 0.8f);
		frustumZ(T(b), p, 0.0f, rimH, radius * 0.62f, radius * 0.78f, DUST, 0.45f * fade, 0f, 32);
		// the shock ring sweeping out over the ground (first 30%)
		float k = Mth.clamp(t / 0.3f, 0f, 1f);
		if (k < 1f) {
			float rr = 0.6f + k * radius * 1.35f;
			ring(T(b), p, 0.08f, rr, 0.35f + k * 0.6f, AIR, 0.7f * (1f - k), 48, 0f);
			ring(A(b), p, 0.1f, rr, 0.12f, 0xC0DCFF, 0.5f * (1f - k), 48, 0f);
			frustumZ(T(b), p, 0.05f, 0.9f * (1f - k), rr, rr * 1.05f, AIR, 0.35f * (1f - k), 0f, 48);
		}
		// chunks of the ground thrown up, tumbling back down
		if (block.getRenderShape() == RenderShape.MODEL) {
			var dispatcher = Minecraft.getInstance().getBlockRenderer();
			for (int i = 0; i < 14; i++) {
				float tick = age - (i % 3);
				if (tick <= 0f) {
					continue;
				}
				float ang = i * 2.399f; // golden angle
				float vh = 0.12f + 0.06f * ((i * 53) % 5) / 5f;
				float vz = 0.45f + 0.2f * ((i * 31) % 7) / 7f;
				float x = Mth.cos(ang) * (radius * 0.35f + vh * tick);
				float y = Mth.sin(ang) * (radius * 0.35f + vh * tick);
				float z = vz * tick - 0.045f * tick * tick;
				if (z < -0.2f) {
					continue;
				}
				float s = 0.22f + 0.12f * ((i * 17) % 4) / 4f;
				pose.pushPose();
				pose.translate(x, y, z);
				pose.mulPose(Axis.XP.rotationDegrees(tick * (20f + i * 7f)));
				pose.mulPose(Axis.ZP.rotationDegrees(tick * (13f + i * 5f)));
				pose.scale(s, s, s);
				pose.translate(-0.5f, -0.5f, -0.5f);
				dispatcher.renderSingleBlock(block, pose, b, light, OverlayTexture.NO_OVERLAY);
				pose.popPose();
			}
		}
	}

	private static void takeoff(MultiBufferSource b, PoseStack.Pose p, float radius, float t, float age) {
		for (int i = 0; i < 2; i++) {
			float k = Mth.clamp(t * 2.2f - i * 0.25f, 0f, 1f);
			if (k <= 0f || k >= 1f) {
				continue;
			}
			float rr = 0.5f + k * radius * 1.2f;
			ring(T(b), p, 0.08f + i * 0.04f, rr, 0.3f + k * 0.5f, AIR, 0.7f * (1f - k), 44, 0f);
			frustumZ(T(b), p, 0.05f, 0.7f * (1f - k), rr, rr * 1.04f, AIR, 0.3f * (1f - k), 0f, 44);
		}
		// the column of wind shooting up after the flier
		float h = 4f + Mth.clamp(t * 3f, 0f, 1f) * 16f;
		float a = 0.42f * (1f - t);
		frustum(T(b), p, 0.0f, h, 1.1f, 0.35f, AIR, a, 0f, 24, age * 0.2f);
		helix(T(b), p, 0.0f, h, 1.25f, 0.4f, 3, 2.5f, age * 0.35f, 0.12f, 0xFFFFFF, a * 1.2f);
		disc(A(b), p, 0.06f, 1.4f * (1f - t), 0xA0C8F0, 0.4f * (1f - t), 0f, 24);
	}

	private static void updraft(MultiBufferSource b, PoseStack.Pose p, float radius, float t, float age) {
		float in = Mth.clamp(t / 0.15f, 0f, 1f);
		float out = 1f - Mth.clamp((t - 0.6f) / 0.4f, 0f, 1f);
		float a = in * out;
		float h = 2f + in * 7f;
		// the wind ribbons spiralling up round the column, wide at the base and drawing in at the top
		helix(T(b), p, 0.0f, h, radius * 0.95f, radius * 0.55f, 5, 1.6f, age * 0.25f, 0.28f, AIR, 0.5f * a);
		helix(T(b), p, 0.0f, h * 0.9f, radius * 0.6f, radius * 0.3f, 4, 2.2f, -age * 0.3f, 0.2f, SHOCK, 0.4f * a);
		frustumZ(T(b), p, 0.0f, h, radius, radius * 0.6f, SHOCK, 0.13f * a, 0f, 40);
		ring(T(b), p, 0.06f, radius, 0.35f, AIR, 0.55f * a, 48, 0f);
		ring(A(b), p, 0.08f, radius, 0.12f, 0xA8D4FF, 0.45f * a, 48, 0f);
	}

	private static void punch(MultiBufferSource b, PoseStack.Pose p, float size, float t) {
		float k = t;
		ring(T(b), p, 0f, 0.3f + k * size * 1.4f, 0.12f + k * 0.25f, AIR, 0.85f * (1f - k), 32, 0f);
		ring(A(b), p, 0f, 0.3f + k * size * 1.4f, 0.06f, 0xFFFFFF, 0.7f * (1f - k), 32, 0f);
		for (int i = 1; i <= 2; i++) {
			float z = k * (0.7f + i * 0.6f);
			ring(T(b), p, z, (0.2f + k * size) * (1f - i * 0.2f), 0.08f, SHOCK, 0.6f * (1f - k), 28, 0f);
		}
		disc(A(b), p, 0f, size * 0.7f * (1f - k), 0xFFFFFF, 0.6f * (1f - k), 0f, 20);
	}

	private static void vapourRing(MultiBufferSource b, PoseStack.Pose p, float size, float t) {
		float rr = size * (0.4f + t * 1.1f);
		ring(T(b), p, -t * 0.6f, rr, 0.16f + t * 0.3f, AIR, 0.7f * (1f - t), 32, 0f);
		disc(T(b), p, -t * 0.6f, rr, AIR, 0.0f, 0.18f * (1f - t), 32);
		ring(A(b), p, -t * 0.6f, rr, 0.05f, 0xD8ECFF, 0.4f * (1f - t), 32, 0f);
	}

	// ================================================================ primitives (in the effect's frame, +Z forward)

	/** A flat annulus round the Z axis at depth {@code z}: radius {@code r}, {@code w} wide, fading to its edges. */
	public static void ring(VertexConsumer vc, PoseStack.Pose p, float z, float r, float w, int rgb, float alpha, int segs, float spin) {
		float r0 = Math.max(0f, r - w * 0.5f);
		float r1 = r + w * 0.5f;
		for (int j = 0; j < segs; j++) {
			float a0 = spin + Mth.TWO_PI * j / segs;
			float a1 = spin + Mth.TWO_PI * (j + 1) / segs;
			float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
			float rm = (r0 + r1) * 0.5f;
			// inner half fades in, outer half fades out: a soft-edged band
			HardLightRibbon.quad(vc, p, c0 * r0, s0 * r0, z, c1 * r0, s1 * r0, z, c1 * rm, s1 * rm, z, c0 * rm, s0 * rm, z, rgb,
					alpha * 0.2f, alpha * 0.2f, alpha, alpha);
			HardLightRibbon.quad(vc, p, c0 * rm, s0 * rm, z, c1 * rm, s1 * rm, z, c1 * r1, s1 * r1, z, c0 * r1, s0 * r1, z, rgb,
					alpha, alpha, 0f, 0f);
		}
	}

	/** A filled disc round the Z axis at depth {@code z}, {@code aCentre} in the middle to {@code aEdge} at the rim. */
	public static void disc(VertexConsumer vc, PoseStack.Pose p, float z, float r, int rgb, float aCentre, float aEdge, int segs) {
		for (int j = 0; j < segs; j++) {
			float a0 = Mth.TWO_PI * j / segs;
			float a1 = Mth.TWO_PI * (j + 1) / segs;
			HardLightRibbon.quad(vc, p, 0f, 0f, z, 0f, 0f, z, Mth.cos(a1) * r, Mth.sin(a1) * r, z, Mth.cos(a0) * r, Mth.sin(a0) * r, z,
					rgb, aCentre, aCentre, aEdge, aEdge);
		}
	}

	/** A cone frustum shell along Z from {@code (z0, r0)} to {@code (z1, r1)}, alpha {@code a0} to {@code a1}. */
	public static void frustum(VertexConsumer vc, PoseStack.Pose p, float z0, float z1, float r0, float r1, int rgb, float a0,
			float a1, int segs, float spin) {
		for (int j = 0; j < segs; j++) {
			float t0 = spin + Mth.TWO_PI * j / segs;
			float t1 = spin + Mth.TWO_PI * (j + 1) / segs;
			float c0 = Mth.cos(t0), s0 = Mth.sin(t0), c1 = Mth.cos(t1), s1 = Mth.sin(t1);
			// alternate faint streaks so the shell reads as moving air, not glass
			float k = j % 3 == 0 ? 1.0f : 0.6f;
			HardLightRibbon.quad(vc, p, c0 * r0, s0 * r0, z0, c1 * r0, s1 * r0, z0, c1 * r1, s1 * r1, z1, c0 * r1, s0 * r1, z1, rgb,
					a0 * k, a0 * k, a1 * k, a1 * k);
		}
	}

	/** {@link #frustum} for a wall standing up from the ground (z is up in a ground effect's frame). */
	private static void frustumZ(VertexConsumer vc, PoseStack.Pose p, float z0, float z1, float r0, float r1, int rgb, float a0,
			float a1, int segs) {
		frustum(vc, p, z0, z1, r0, r1, rgb, a0, a1, segs, 0f);
	}

	/** One radial crack in the ground from {@code from} to {@code to} blocks out at angle {@code a}. */
	private static void crack(VertexConsumer vc, PoseStack.Pose p, float a, float from, float to, float w0, float w1, int rgb, float alpha) {
		float c = Mth.cos(a), s = Mth.sin(a);
		float px = -s, py = c;
		float mid = (from + to) * 0.5f;
		float bend = 0.18f * Mth.sin(a * 3.7f);
		float mx = c * mid + px * bend, my = s * mid + py * bend;
		HardLightRibbon.quad(vc, p, c * from + px * w0, s * from + py * w0, 0.03f, mx + px * w0 * 0.8f, my + py * w0 * 0.8f, 0.03f,
				mx - px * w0 * 0.8f, my - py * w0 * 0.8f, 0.03f, c * from - px * w0, s * from - py * w0, 0.03f, rgb, alpha, alpha, alpha, alpha);
		HardLightRibbon.quad(vc, p, mx + px * w0 * 0.8f, my + py * w0 * 0.8f, 0.03f, c * to + px * w1, s * to + py * w1, 0.03f,
				c * to - px * w1, s * to - py * w1, 0.03f, mx - px * w0 * 0.8f, my - py * w0 * 0.8f, 0.03f, rgb, alpha, alpha * 0.2f,
				alpha * 0.2f, alpha);
	}

	/**
	 * {@code strands} ribbons spiralling along Z from {@code z0} to {@code z1}, radius {@code r0} to {@code r1}, {@code turns}
	 * turns each, rotated by {@code phase}, {@code width} wide.
	 */
	public static void helix(VertexConsumer vc, PoseStack.Pose p, float z0, float z1, float r0, float r1, int strands, float turns,
			float phase, float width, int rgb, float alpha) {
		int steps = 28;
		for (int s = 0; s < strands; s++) {
			float off = phase + s * Mth.TWO_PI / strands;
			for (int i = 0; i < steps; i++) {
				float f0 = i / (float) steps, f1 = (i + 1) / (float) steps;
				float zz0 = Mth.lerp(f0, z0, z1), zz1 = Mth.lerp(f1, z0, z1);
				float rr0 = Mth.lerp(f0, r0, r1), rr1 = Mth.lerp(f1, r0, r1);
				float a0 = off + f0 * turns * Mth.TWO_PI, a1 = off + f1 * turns * Mth.TWO_PI;
				// fade in at the bottom and out at the top
				float al0 = alpha * Mth.sin(f0 * Mth.PI), al1 = alpha * Mth.sin(f1 * Mth.PI);
				float hw = width * 0.5f;
				HardLightRibbon.quad(vc, p, Mth.cos(a0) * rr0, Mth.sin(a0) * rr0, zz0 - hw, Mth.cos(a1) * rr1, Mth.sin(a1) * rr1, zz1 - hw,
						Mth.cos(a1) * rr1, Mth.sin(a1) * rr1, zz1 + hw, Mth.cos(a0) * rr0, Mth.sin(a0) * rr0, zz0 + hw, rgb, al0, al1, al1,
						al0);
			}
		}
	}
}
