package com.projecthero.mod.client.carnage;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.carnage.entity.CarnageAttackEntity;
import com.projecthero.mod.carnage.entity.CarnageEntity;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.15: the geometry of Carnage's newer moves -- the whip tendrils, the axe arm and the goo glob in his hand (drawn
 * from his {@code Blades} layer in model space), and the {@link CarnageAttackEntity} pieces out in the world (spikes
 * with their warning cracks, the cleave's shard ring, the lobbed glob, the snare coils).
 */
final class CarnageMoveRenderer {
	private static final ResourceLocation CRIMSON = CarnageRenderer.BLADE;
	private static final int EDGE = 0xFFFF6A6A;
	private static final int FULL_BRIGHT = 0xF000F0;

	private CarnageMoveRenderer() {
	}

	// ======================================================================== on Carnage (model space: y down, -z forward)

	/**
	 * Tendril Whip Sweep: two long segmented tendrils grow from his back, rear up behind his right shoulder during the
	 * wind-up, then whip round in a 180-degree arc in front of him (his right to his left) and shrink back.
	 */
	static void whip(PoseStack pose, VertexConsumer vc, int light, float elapsed, float age) {
		for (int k = 0; k < 2; k++) {
			float e = elapsed - k * 1.5f;
			if (e < 0) {
				continue;
			}
			float grow = Math.min(1f, e / 6f);
			float sweepStart = CarnageEntity.WHIP_WINDUP, sweepEnd = CarnageEntity.WHIP_WINDUP + CarnageEntity.WHIP_SWEEP;
			float theta, phi, lagScale, length = 1f;
			if (e < sweepStart) {
				// reared up and back over his right shoulder, coiling
				theta = -135f - k * 15f + Mth.sin(age * 0.5f + k) * 8f;
				phi = 50f - k * 10f;
				lagScale = 0.4f;
			} else if (e < sweepEnd) {
				float p = (e - sweepStart) / (sweepEnd - sweepStart);
				p = p * p * (3 - 2 * p);
				theta = Mth.lerp(p, -120f, 110f);
				phi = Mth.lerp(Math.min(1f, p * 2.5f), 50f - k * 10f, -3f - k * 3f);
				lagScale = 1f;
			} else {
				theta = 110f;
				phi = -3f - k * 3f;
				lagScale = 0.3f;
				length = Math.max(0f, 1f - (e - sweepEnd) / 8f);
			}
			if (length * grow <= 0.02f) {
				continue;
			}
			Vec3 shoulder = new Vec3(k == 0 ? -0.22 : 0.22, 0.12 + k * 0.1, 0.12);
			int n = 26;
			Vec3[] pts = new Vec3[n];
			float[] radii = new float[n];
			double reach = 7.1 * grow * length;
			for (int i = 0; i < n; i++) {
				double s = i / (double) (n - 1);
				double th = Math.toRadians(theta - 55.0 * s * lagScale + 7.0 * Math.sin(s * 8.0 - age * 0.6 + k * 2));
				double ph = Math.toRadians(phi - 7.0 * s + 5.0 * Math.cos(s * 6.0 - age * 0.4));
				Vec3 dir = new Vec3(Math.sin(th) * Math.cos(ph), -Math.sin(ph), -Math.cos(th) * Math.cos(ph));
				// the first stretch climbs out of his back before it fans out
				pts[i] = shoulder.add(dir.scale(reach * s)).add(0, 0, 0.18 * Math.sin(Math.min(1.0, s * 6.0) * Math.PI));
				double bead = 1.0 + 0.28 * Math.max(0.0, Math.sin(s * Math.PI * 16.0));
				radii[i] = (float) ((0.13 - 0.10 * s) * bead);
			}
			radii[n - 1] = 0.01f;
			CarnageMesh.tube(vc, pose.last(), light, CarnageMesh.WHITE, pts, radii, 6);
			// a barbed tip: three hooks flaring back
			Vec3 tip = pts[n - 1];
			Vec3 back = pts[n - 3].subtract(tip).normalize();
			Vec3 side = back.cross(new Vec3(0, 1, 0));
			side = side.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : side.normalize();
			Vec3 up = side.cross(back).normalize();
			for (int b = 0; b < 3; b++) {
				double a = Math.PI * 2 * b / 3.0 + k;
				Vec3 out = side.scale(Math.cos(a)).add(up.scale(Math.sin(a)));
				CarnageMesh.cone(vc, pose.last(), light, CarnageMesh.WHITE, tip.add(back.scale(0.1)), tip.add(back.scale(0.35)).add(out.scale(0.22)), 0.045f, 4);
			}
		}
	}

	/**
	 * Axe-Arm Cleave: the right forearm swells into a long haft and a huge crescent axe head, edge leading the swing
	 * (the arm's +z face). Drawn in the arm's own space after {@code translateAndRotate}.
	 */
	static void axe(PoseStack pose, VertexConsumer vc, int light, ModelPart arm, float grow, float age) {
		if (grow <= 0.01f) {
			return;
		}
		pose.pushPose();
		arm.translateAndRotate(pose);
		float cx = -0.0625f; // the wide right arm's centre line
		pose.translate(cx, 0.55f, 0f);
		pose.scale(grow, grow, grow);
		pose.translate(-cx, -0.55f, 0f);
		PoseStack.Pose last = pose.last();
		// the haft: a knotted crimson shaft running out of the fist
		Vec3[] haft = new Vec3[7];
		float[] hr = new float[7];
		for (int i = 0; i < 7; i++) {
			double t = i / 6.0;
			haft[i] = new Vec3(cx + 0.02 * Math.sin(t * 7 + age * 0.2), 0.3 + t * 1.05, 0.0);
			hr[i] = (float) (0.075 - 0.02 * t + 0.02 * Math.max(0, Math.sin(t * Math.PI * 5)));
		}
		CarnageMesh.tube(vc, last, light, CarnageMesh.WHITE, haft, hr, 6);
		// the head: a crescent, extruded either side of the haft
		int n = 10;
		float th = 0.05f;
		Vec3[] inner = new Vec3[n + 1];
		Vec3[] outer = new Vec3[n + 1];
		for (int i = 0; i <= n; i++) {
			double t = i / (double) n;
			// a narrow neck on the haft flaring into a long curved bearded blade
			inner[i] = new Vec3(0, 0.98 + 0.26 * t, 0.05);
			double flare = Math.sin(Math.PI * t);
			outer[i] = new Vec3(0, 0.5 + 1.12 * t + 0.1 * Math.sin(Math.PI * 2 * t), 0.5 + 0.3 * flare + 0.04 * Math.sin(t * 11));
		}
		for (int i = 0; i < n; i++) {
			float v0 = i / (float) n, v1 = (i + 1) / (float) n;
			for (int s = -1; s <= 1; s += 2) {
				// each face, thinning toward the edge (u along the blade, v from the haft out, so no stripes)
				Vec3 a = inner[i].add(cx + s * th, 0, 0), b = inner[i + 1].add(cx + s * th, 0, 0);
				Vec3 c = outer[i + 1].add(cx + s * th * 0.3, 0, 0), d = outer[i].add(cx + s * th * 0.3, 0, 0);
				CarnageMesh.quad(vc, last, light, CarnageMesh.WHITE, a, b, c, d, v0 * 0.5f, 0f, v1 * 0.5f, 1f);
			}
			// the bright, glowing cutting edge
			Vec3 e0 = outer[i].add(cx, 0, 0), e1 = outer[i + 1].add(cx, 0, 0);
			for (int s = -1; s <= 1; s += 2) {
				CarnageMesh.quad(vc, last, FULL_BRIGHT, EDGE, e0.add(s * 0.02, 0, -0.07), e1.add(s * 0.02, 0, -0.07), e1.add(0, 0, 0.03), e0.add(0, 0, 0.03),
						0f, v0, 1f, v1);
			}
			// the back of the head
			Vec3 i0 = inner[i].add(cx, 0, 0), i1 = inner[i + 1].add(cx, 0, 0);
			CarnageMesh.quad(vc, last, light, CarnageMesh.WHITE, i0.add(-th, 0, 0), i1.add(-th, 0, 0), i1.add(th, 0, 0), i0.add(th, 0, 0), 0f, v0, 1f, v1);
		}
		// a hooked spike off the back of the head
		CarnageMesh.cone(vc, last, light, CarnageMesh.WHITE, new Vec3(cx, 1.05, 0.0), new Vec3(cx, 1.18, -0.42), 0.08f, 5);
		// a few tendrils binding head to haft
		for (int i = 0; i < 3; i++) {
			double y = 0.9 + i * 0.16;
			CarnageMesh.tube(vc, last, light, CarnageMesh.WHITE,
					new Vec3[]{new Vec3(cx, y, -0.06), new Vec3(cx + 0.09, y + 0.05, 0.12), new Vec3(cx, y + 0.1, 0.3)},
					new float[]{0.035f, 0.03f, 0.01f}, 4);
		}
		pose.popPose();
	}

	/** Symbiote Snare wind-up: a glob of goo swelling in his right fist. */
	static void handGlob(PoseStack pose, VertexConsumer vc, int light, ModelPart arm, float size, float age) {
		if (size <= 0.01f) {
			return;
		}
		pose.pushPose();
		arm.translateAndRotate(pose);
		CarnageMesh.blob(vc, pose.last(), light, CarnageMesh.WHITE, new Vec3(-0.0625, 0.8, 0.0), 0.34f * size, age, 3);
		pose.popPose();
	}

	// ======================================================================== in the world (y up)

	/** The {@link CarnageAttackEntity} renderer. */
	static final class Attack extends EntityRenderer<CarnageAttackEntity> {
		Attack(EntityRendererProvider.Context ctx) {
			super(ctx);
			this.shadowRadius = 0f;
		}

		@Override
		public boolean shouldRender(CarnageAttackEntity e, Frustum frustum, double x, double y, double z) {
			return e.shouldRenderAtSqrDistance(e.distanceToSqr(x, y, z));
		}

		@Override
		public void render(CarnageAttackEntity e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
			float age = e.tickCount + partialTick;
			switch (e.kind()) {
				case CarnageAttackEntity.KIND_SPIKE -> spike(e, age, pose, buffers, light);
				case CarnageAttackEntity.KIND_SHOCKWAVE -> shockwave(e, age, pose, buffers, light);
				case CarnageAttackEntity.KIND_GLOB -> glob(e, age, partialTick, pose, buffers, light);
				case CarnageAttackEntity.KIND_SNARE -> snare(e, age, partialTick, pose, buffers, light);
				default -> {
				}
			}
			super.render(e, yaw, partialTick, pose, buffers, light);
		}

		/** Red cracks spreading from the spot (the warning), then a cluster of spikes bursting up and sinking back. */
		private static void spike(CarnageAttackEntity e, float age, PoseStack pose, MultiBufferSource buffers, int light) {
			int delay = e.delay();
			RandomSource r = RandomSource.create(e.getId() * 7919L);
			float crackGrow = Math.min(1f, age / Math.max(1f, delay * 0.8f));
			float fade = age < delay ? 1f : Math.max(0f, 1f - (age - delay) / 20f);
			if (fade > 0f) {
				cracks(pose, buffers, r, 5, 1.35f * crackGrow, (int) (255 * fade), age < delay && ((int) age / 2) % 2 == 0);
			}
			if (age < delay) {
				return;
			}
			float t = age - delay;
			float up = CarnageAttackEntity.SPIKE_UP_TICKS, hold = CarnageAttackEntity.SPIKE_HOLD_TICKS, down = CarnageAttackEntity.SPIKE_DOWN_TICKS;
			float h;
			if (t < up) {
				float p = t / up;
				h = 1f - (1f - p) * (1f - p);
			} else if (t < up + hold) {
				h = 1f;
			} else {
				h = Math.max(0f, 1f - (t - up - hold) / down);
			}
			if (h <= 0.01f) {
				return;
			}
			VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(CRIMSON));
			PoseStack.Pose last = pose.last();
			r = RandomSource.create(e.getId() * 31L);
			double lean = r.nextDouble() * Math.PI * 2;
			Vec3 tilt = new Vec3(Math.cos(lean) * 0.25, 0, Math.sin(lean) * 0.25);
			CarnageMesh.cone(vc, last, light, CarnageMesh.WHITE, new Vec3(0, -0.4, 0), new Vec3(0, 2.5 * h, 0).add(tilt.scale(h)), 0.34f, 7);
			for (int i = 0; i < 4; i++) {
				double a = lean + Math.PI * 0.5 * i + r.nextDouble() * 0.6;
				double len = 1.1 + r.nextDouble() * 0.6;
				Vec3 base = new Vec3(Math.cos(a) * 0.3, -0.3, Math.sin(a) * 0.3);
				Vec3 tip = base.add(Math.cos(a) * 0.55 * h, len * h, Math.sin(a) * 0.55 * h);
				CarnageMesh.cone(vc, last, light, CarnageMesh.WHITE, base, tip, 0.17f, 5);
			}
			// a goo collar where it broke through the ground
			for (int i = 0; i < 6; i++) {
				double a = Math.PI * 2 * i / 6 + lean;
				CarnageMesh.tube(vc, last, light, CarnageMesh.WHITE,
						new Vec3[]{new Vec3(Math.cos(a) * 0.15, 0.25 * h, Math.sin(a) * 0.15), new Vec3(Math.cos(a) * 0.55, 0.12, Math.sin(a) * 0.55),
								new Vec3(Math.cos(a) * 0.75, 0.0, Math.sin(a) * 0.75)},
						new float[]{0.08f, 0.06f, 0.015f}, 4);
			}
		}

		/** A ring of jagged shards racing outward along the ground, with the cracks it leaves behind. */
		private static void shockwave(CarnageAttackEntity e, float age, PoseStack pose, MultiBufferSource buffers, int light) {
			double radius = CarnageAttackEntity.shockRadius(age);
			float life = CarnageAttackEntity.SHOCK_TICKS + 4;
			float fade = Math.max(0f, 1f - age / life);
			RandomSource r = RandomSource.create(e.getId() * 131L);
			cracks(pose, buffers, r, 8, (float) radius, (int) (255 * fade), false);
			if (fade <= 0f || radius < 0.3) {
				return;
			}
			VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(CRIMSON));
			PoseStack.Pose last = pose.last();
			int n = Math.max(8, (int) (radius * 5.5));
			float height = 1.1f * Math.min(1f, fade * 1.6f);
			for (int i = 0; i < n; i++) {
				double a = Math.PI * 2 * i / n + (r.nextDouble() - 0.5) * 0.15;
				double rr = radius + (r.nextDouble() - 0.5) * 0.4;
				double hh = height * (0.6 + r.nextDouble() * 0.6);
				Vec3 base = new Vec3(Math.cos(a) * rr, -0.2, Math.sin(a) * rr);
				Vec3 tip = base.add(Math.cos(a) * hh * 0.5, hh, Math.sin(a) * hh * 0.5);
				CarnageMesh.cone(vc, last, light, CarnageMesh.WHITE, base, tip, 0.16f, 4);
			}
		}

		private static void glob(CarnageAttackEntity e, float age, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
			VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(CRIMSON));
			PoseStack.Pose last = pose.last();
			Vec3 v = e.getDeltaMovement();
			Vec3 c = new Vec3(0, 0.15, 0);
			CarnageMesh.blob(vc, last, light, CarnageMesh.WHITE, c, 0.34f, age, e.getId());
			Vec3 trail = v.lengthSqr() < 1.0e-6 ? new Vec3(0, -1, 0) : v.normalize().scale(-1);
			CarnageMesh.blob(vc, last, light, CarnageMesh.WHITE, c.add(trail.scale(0.45)), 0.17f, age + 3, e.getId() + 1);
			CarnageMesh.blob(vc, last, light, CarnageMesh.WHITE, c.add(trail.scale(0.75)), 0.09f, age + 6, e.getId() + 2);
			// writhing feelers
			for (int i = 0; i < 4; i++) {
				double a = Math.PI * 0.5 * i + age * 0.3;
				Vec3 out = new Vec3(Math.cos(a), Math.sin(age * 0.4 + i) * 0.5, Math.sin(a)).normalize();
				CarnageMesh.tube(vc, last, light, CarnageMesh.WHITE,
						new Vec3[]{c.add(out.scale(0.25)), c.add(out.scale(0.45)).add(0, 0.08, 0), c.add(out.scale(0.6))},
						new float[]{0.06f, 0.04f, 0.01f}, 4);
			}
		}

		/** A cocoon of tendrils coiling up round the victim from a goo splat at their feet. */
		private static void snare(CarnageAttackEntity e, float age, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
			Entity t = e.level().getEntity(e.targetId());
			if (t == null) {
				return;
			}
			Vec3 offset = t.getPosition(partialTick).subtract(e.getPosition(partialTick));
			float life = CarnageAttackEntity.SNARE_TICKS;
			float g = Math.min(1f, age / 6f) * Math.min(1f, Math.max(0f, (life - age) / 4f));
			if (g <= 0.01f) {
				return;
			}
			VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(CRIMSON));
			pose.pushPose();
			pose.translate(offset.x, offset.y, offset.z);
			PoseStack.Pose last = pose.last();
			double h = t.getBbHeight() * 0.95 * g;
			double w = t.getBbWidth() * 0.55 + 0.12;
			int strands = 5, steps = 18;
			for (int k = 0; k < strands; k++) {
				double a0 = Math.PI * 2 * k / strands;
				Vec3[] pts = new Vec3[steps];
				float[] radii = new float[steps];
				for (int i = 0; i < steps; i++) {
					double s = i / (double) (steps - 1);
					double a = a0 + s * Math.PI * 2 * 1.2 * (k % 2 == 0 ? 1 : -1);
					double rr = w * (1.0 + 0.12 * Math.sin(s * 9 + age * 0.4 + k)) * (0.85 + 0.3 * Math.sin(s * Math.PI));
					pts[i] = new Vec3(Math.cos(a) * rr, 0.02 + s * h, Math.sin(a) * rr);
					radii[i] = (float) (0.075 * (1.0 - 0.6 * s));
				}
				radii[steps - 1] = 0.01f;
				CarnageMesh.tube(vc, last, light, CarnageMesh.WHITE, pts, radii, 5);
			}
			// the splat it grows from
			for (int i = 0; i < 8; i++) {
				double a = Math.PI * 2 * i / 8;
				CarnageMesh.tube(vc, last, light, CarnageMesh.WHITE,
						new Vec3[]{new Vec3(Math.cos(a) * w, 0.05, Math.sin(a) * w), new Vec3(Math.cos(a) * (w + 0.5 * g), 0.02, Math.sin(a) * (w + 0.5 * g))},
						new float[]{0.08f, 0.02f}, 4);
			}
			pose.popPose();
		}

		/** Jagged glowing red cracks radiating from the origin on the ground. */
		private static void cracks(PoseStack pose, MultiBufferSource buffers, RandomSource r, int count, float length, int alpha, boolean pulse) {
			if (length < 0.05f || alpha <= 4) {
				return;
			}
			VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
			PoseStack.Pose last = pose.last();
			int red = pulse ? 255 : 220;
			for (int i = 0; i < count; i++) {
				double a = Math.PI * 2 * i / count + r.nextDouble() * 0.5;
				Vec3 prev = new Vec3(0, 0.03, 0);
				int segs = 4;
				for (int s = 1; s <= segs; s++) {
					double d = length * s / segs;
					double jag = (r.nextDouble() - 0.5) * 0.5;
					Vec3 next = new Vec3(Math.cos(a + jag) * d, 0.03, Math.sin(a + jag) * d);
					float w0 = 0.09f * (1f - (s - 1) / (float) segs) + 0.01f, w1 = 0.09f * (1f - s / (float) segs) + 0.01f;
					CarnageMesh.glowStrip(vc, last, prev, next, w0, w1, red, 20, 10, alpha);
					prev = next;
				}
			}
		}

		@Override
		public ResourceLocation getTextureLocation(CarnageAttackEntity e) {
			return CRIMSON;
		}
	}
}
