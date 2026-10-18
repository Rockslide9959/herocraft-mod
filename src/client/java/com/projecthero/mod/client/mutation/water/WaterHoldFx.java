package com.projecthero.mod.client.mutation.water;

import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.hero.power.p25.RainCallEntity;
import com.projecthero.mod.hero.power.p25.WaterDragonEntity;
import com.projecthero.mod.hero.power.p25.WaterHandlers;
import com.projecthero.mod.hero.power.p25.WhirlpoolEntity;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6 Water Manipulation hold moves -- the water models of the new bodies, built from {@link WaterMesh} like the
 * rest of the power's (drawn by {@link WaterFxRenderer}):
 * <ul>
 * <li>the Water Dragon -- a serpent of water whose segmented body follows the head's own flight path, banded with foam
 * rings, rippling as it swims, with a horned head, snapping jaws, glowing eyes, trailing whiskers and a dorsal fin row;
 * it collapses into spray where it strikes;</li>
 * <li>the Whirlpool -- a spinning bowl of water heaped up at the rim and sunk to a churning eye, four foam spiral arms
 * winding in, a foaming rim and the outer wall;</li>
 * <li>Rain Call -- a heavy cloud ceiling over the circle, rain streaks falling from it to the ground under each one, and
 * the splash rings where they land.</li>
 * </ul>
 */
public final class WaterHoldFx {
	private WaterHoldFx() {
	}

	// ---------------------------------------------------------------- hold R: the dragon

	/** The polyline head -> trail, resampled to {@code n} points spread over at most {@code maxLen} blocks. */
	private static Vec3[] resample(Vec3[] path, int count, int n, double maxLen) {
		double total = 0;
		for (int i = 1; i < count; i++) {
			total += path[i].distanceTo(path[i - 1]);
		}
		double len = Math.min(total, maxLen);
		Vec3[] out = new Vec3[n];
		int seg = 1;
		double acc = 0;
		for (int k = 0; k < n; k++) {
			double want = len * k / (n - 1);
			while (seg < count - 1 && acc + path[seg].distanceTo(path[seg - 1]) < want) {
				acc += path[seg].distanceTo(path[seg - 1]);
				seg++;
			}
			double d = path[seg].distanceTo(path[seg - 1]);
			double f = d < 1.0e-6 ? 0 : Mth.clamp((want - acc) / d, 0, 1);
			out[k] = path[seg - 1].lerp(path[seg], f);
		}
		return out;
	}

	public static void dragon(WaterDragonEntity e, float t, float partial, PoseStack pose, VertexConsumer vc, int light) {
		TextureAtlasSprite still = WaterMesh.still();
		TextureAtlasSprite flow = WaterMesh.flow();
		Vec3 origin = new Vec3(Mth.lerp(partial, e.xo, e.getX()), Mth.lerp(partial, e.yo, e.getY()), Mth.lerp(partial, e.zo, e.getZ()));
		int hit = e.hitFor();
		float fall = hit < 0 ? 0f : Math.min(1f, (hit + partial) / WaterDragonEntity.COLLAPSE);
		float alpha = 1f - fall * fall;
		float grow = Math.min(1f, t / 5f);
		// the path: the head, then where it has been
		int count = 1 + e.trailCount;
		Vec3[] path = new Vec3[Math.max(2, count)];
		path[0] = Vec3.ZERO;
		for (int i = 0; i < e.trailCount; i++) {
			path[i + 1] = e.trail[i].subtract(origin);
		}
		if (count < 2) {
			path[1] = new Vec3(0, -0.3, 0);
			count = 2;
		}
		int n = 30;
		Vec3[] spine = resample(path, count, n, 9.0 * grow);
		Vec3 dir = spine[0].subtract(spine[1]);
		dir = dir.lengthSqr() < 1.0e-6 ? Vec3.directionFromRotation(0, e.getYRot()) : dir.normalize();
		// it swims: a travelling side-to-side and up-and-down wave along the body, and it collapses when it strikes
		Vec3 side = dir.cross(new Vec3(0, 1, 0));
		side = side.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : side.normalize();
		Vec3 upv = side.cross(dir).normalize();
		float[] radii = new float[n];
		for (int i = 0; i < n; i++) {
			float s = i / (float) (n - 1);
			float sway = Mth.sin(s * 9f - t * 0.9f) * 0.35f * s;
			float bob = Mth.cos(s * 7f - t * 0.7f) * 0.2f * s;
			spine[i] = spine[i].add(side.scale(sway)).add(upv.scale(bob)).add(0, -fall * fall * 2.5f * (0.3f + s), 0);
			float body = s < 0.08f ? 0.42f + s * 1.6f : 0.55f * (float) Math.pow(1f - s, 0.75) + 0.05f;
			float bulge = 1f + 0.12f * Mth.sin(s * 26f - t * 1.4f); // the rippling segments
			radii[i] = body * bulge * grow * (1f + fall * 0.8f);
		}
		int bodyCol = WaterMesh.fade(WaterMesh.BODY, alpha);
		int foamCol = WaterMesh.fade(WaterMesh.FOAM, alpha);
		int lightCol = WaterMesh.fade(WaterMesh.LIGHT, alpha);
		WaterMesh.tube(vc, pose, spine, radii, 12, 0.14f, t * 2f, bodyCol, light, still);
		float[] core = new float[n];
		for (int i = 0; i < n; i++) {
			core[i] = radii[i] * 0.5f;
		}
		WaterMesh.tube(vc, pose, spine, core, 8, 0.2f, t * 3f, lightCol, light, flow);
		// segment bands and the dorsal fins
		for (int i = 3; i < n - 3; i += 3) {
			Vec3 tan = spine[i - 1].subtract(spine[i + 1]);
			if (tan.lengthSqr() < 1.0e-6) {
				continue;
			}
			tan = tan.normalize();
			pose.pushPose();
			pose.translate(spine[i].x, spine[i].y, spine[i].z);
			pose.mulPose(new Quaternionf().rotationTo(0f, 1f, 0f, (float) tan.x, (float) tan.y, (float) tan.z));
			WaterMesh.ring(vc, pose, radii[i] * 1.02f, 0.045f + radii[i] * 0.06f, 14, 0.3f, t * 2f + i, foamCol, light, flow);
			pose.popPose();
			// a fin: a curved blade of water standing up off the back
			Vec3 back = spine[i].add(upv.scale(radii[i] * 0.9f));
			Vec3[] fin = { back, back.add(upv.scale(0.32f * grow)).add(tan.scale(-0.12f)), back.add(upv.scale(0.5f * grow)).add(tan.scale(-0.4f)) };
			float sz = Math.max(0.03f, radii[i] * 0.28f);
			WaterMesh.tube(vc, pose, fin, new float[] { sz, sz * 0.6f, 0.015f }, 5, 0.1f, t, lightCol, light, flow);
		}
		// the head, facing along its flight
		pose.pushPose();
		float yawR = (float) Math.atan2(dir.x, dir.z);
		float pitchR = (float) -Math.asin(Mth.clamp(dir.y, -1, 1));
		pose.mulPose(Axis.YP.rotation(yawR));
		pose.mulPose(Axis.XP.rotation(pitchR));
		float hs = grow * (1f + fall * 0.9f);
		pose.scale(hs, hs, hs);
		// skull
		pose.pushPose();
		pose.translate(0, 0.08f, 0.15f);
		pose.scale(1f, 0.8f, 1.5f);
		WaterMesh.sphere(vc, pose, 0.48f, 1f, 0.08f, t * 2f, bodyCol, light, still);
		pose.popPose();
		// upper jaw / snout
		pose.pushPose();
		pose.translate(0, 0.05f, 0.75f);
		pose.scale(0.75f, 0.42f, 1.3f);
		WaterMesh.sphere(vc, pose, 0.4f, 1f, 0.08f, t * 2.5f, bodyCol, light, still);
		pose.popPose();
		// lower jaw, snapping
		float open = 0.25f + 0.22f * Mth.sin(t * 0.55f) + (hit >= 0 ? 0.5f : 0f);
		pose.pushPose();
		pose.translate(0, -0.18f, 0.25f);
		pose.mulPose(Axis.XP.rotation(open));
		pose.translate(0, 0, 0.45f);
		pose.scale(0.62f, 0.26f, 1.25f);
		WaterMesh.sphere(vc, pose, 0.38f, 1f, 0.08f, t * 2.5f, bodyCol, light, still);
		pose.popPose();
		// fangs of foam along the jaw line
		for (int k = -1; k <= 1; k += 2) {
			for (int f = 0; f < 3; f++) {
				pose.pushPose();
				pose.translate(k * (0.17f - f * 0.03f), -0.08f, 0.62f + f * 0.2f);
				WaterMesh.sphere(vc, pose, 0.045f, 2.0f, 0.0f, t, foamCol, light, still);
				pose.popPose();
			}
		}
		// eyes
		for (int k = -1; k <= 1; k += 2) {
			pose.pushPose();
			pose.translate(k * 0.3f, 0.25f, 0.45f);
			WaterMesh.sphere(vc, pose, 0.085f, 1f, 0.0f, t, WaterMesh.fade(0xF0F4FCFF, alpha), 0xF000F0, still);
			pose.popPose();
		}
		// horns sweeping back, and whiskers trailing from the snout
		for (int k = -1; k <= 1; k += 2) {
			Vec3[] horn = { new Vec3(k * 0.24f, 0.35f, 0.2f), new Vec3(k * 0.36f, 0.62f, -0.25f), new Vec3(k * 0.42f, 0.78f, -0.75f) };
			WaterMesh.tube(vc, pose, horn, new float[] { 0.09f, 0.06f, 0.015f }, 6, 0.05f, t, foamCol, light, flow);
			int wn = 8;
			Vec3[] whisker = new Vec3[wn];
			float[] wr = new float[wn];
			for (int j = 0; j < wn; j++) {
				float s = j / (float) (wn - 1);
				whisker[j] = new Vec3(k * (0.25f + s * 0.5f), -0.02f + Mth.sin(s * 5f - t * 0.8f + k) * 0.18f * s, 1.05f - s * 1.9f);
				wr[j] = 0.035f * (1f - s) + 0.01f;
			}
			WaterMesh.tube(vc, pose, whisker, wr, 5, 0.1f, t, lightCol, light, flow);
		}
		// the burst where it strikes
		if (hit >= 0) {
			WaterMesh.sphere(vc, pose, 0.6f + 2.2f * fall, 0.7f, 0.3f, t * 4f, WaterMesh.fade(WaterMesh.FOAM, 1f - fall), light, flow);
		}
		pose.popPose();
	}

	// ---------------------------------------------------------------- hold G: the whirlpool

	public static void whirlpool(WhirlpoolEntity e, float t, PoseStack pose, VertexConsumer vc, int light) {
		TextureAtlasSprite still = WaterMesh.still();
		TextureAtlasSprite flow = WaterMesh.flow();
		float open = Math.min(1f, t / WhirlpoolEntity.OPEN) * Math.min(1f, Math.max(0f, (WaterHandlers.WHIRLPOOL_TICKS + 4 - t) / WhirlpoolEntity.OPEN));
		if (open <= 0.01f) {
			return;
		}
		open = open * open * (3f - 2f * open);
		float r = e.size() * open;
		float rim = 1.4f * open; // heaped up at the edge, sunk to the eye
		float spin = t * 0.22f;
		WaterMesh.Profile surface = (x, s) -> {
			float a = x - spin * (1f + (1f - s) * 2.2f); // the inside turns faster
			float rr = r * (0.06f + 0.94f * s);
			float y = 0.06f + rim * (float) Math.pow(s, 1.7) + 0.05f * Mth.sin(a * 5f + s * 9f) * s;
			return new Vec3(Mth.cos(a) * rr, y, Mth.sin(a) * rr);
		};
		WaterMesh.sheet(vc, pose, Mth.PI, 36, 10, surface, WaterMesh.BODY, light, flow);
		// the outer wall down to the ground
		WaterMesh.Profile wall = (x, s) -> {
			float a = x - spin * 0.8f;
			float rr = r * (1f + 0.03f * Mth.sin(a * 7f + t * 0.4f));
			return new Vec3(Mth.cos(a) * rr, s * (rim + 0.06f), Mth.sin(a) * rr);
		};
		WaterMesh.sheet(vc, pose, Mth.PI, 36, 2, wall, WaterMesh.LIGHT, light, still);
		// four foam arms spiralling into the eye
		for (int arm = 0; arm < 4; arm++) {
			int n = 18;
			Vec3[] pts = new Vec3[n];
			float[] radii = new float[n];
			for (int i = 0; i < n; i++) {
				float s = 1f - i / (float) (n - 1);
				float a = arm * Mth.HALF_PI + s * 3.2f - spin * (1f + (1f - s) * 2.2f);
				float rr = r * (0.08f + 0.88f * s);
				float y = 0.1f + rim * (float) Math.pow(s, 1.7);
				pts[i] = new Vec3(Mth.cos(a) * rr, y, Mth.sin(a) * rr);
				radii[i] = (0.08f + 0.16f * s) * open;
			}
			WaterMesh.tube(vc, pose, pts, radii, 6, 0.3f, t * 2f + arm, WaterMesh.FOAM, light, flow);
		}
		// the foaming rim, and the churning eye
		pose.pushPose();
		pose.translate(0, rim + 0.06f, 0);
		pose.mulPose(Axis.YP.rotation(-spin * 0.8f));
		WaterMesh.ring(vc, pose, r, 0.16f * open, 40, 0.35f, t * 2f, WaterMesh.FOAM, light, flow);
		pose.popPose();
		pose.pushPose();
		pose.translate(0, 0.08f, 0);
		pose.mulPose(Axis.YP.rotation(-spin * 3f));
		WaterMesh.sphere(vc, pose, 0.45f * open, 0.35f, 0.3f, t * 4f, WaterMesh.FOAM, light, flow);
		pose.popPose();
		// spray flung off the rim
		for (int k = 0; k < 8; k++) {
			float a = -spin * 0.8f + k * Mth.TWO_PI / 8f;
			float ph = (t * 0.06f + k * 0.37f) % 1f;
			pose.pushPose();
			pose.translate(Mth.cos(a) * r * (1f + ph * 0.15f), rim + 0.1f + Mth.sin(ph * Mth.PI) * 0.7f, Mth.sin(a) * r * (1f + ph * 0.15f));
			WaterMesh.sphere(vc, pose, 0.1f * (1f - ph) + 0.03f, 1f, 0.1f, t, WaterMesh.FOAM, light, still);
			pose.popPose();
		}
	}

	// ---------------------------------------------------------------- hold C: the rain

	private static final int STREAKS = 380;
	private static final int PUFFS = 22;
	private static final int CLOUD = 0xB0727C8C;
	private static final int CLOUD_DARK = 0xC0555E6E;
	private static final int RAIN = 0x9AA8CCF0;

	public static void rain(RainCallEntity e, float t, float partial, PoseStack pose, VertexConsumer vc, int light) {
		float k = e.strength(partial);
		if (k <= 0.01f) {
			return;
		}
		TextureAtlasSprite still = WaterMesh.still();
		TextureAtlasSprite flow = WaterMesh.flow();
		float r = e.size();
		float cy = (float) RainCallEntity.CLOUD_HEIGHT;
		int skyLight = 0x00A000A0;
		// the cloud ceiling: a dark underside and heaped puffs over it, drifting slowly round
		pose.pushPose();
		pose.translate(0, cy, 0);
		pose.mulPose(Axis.YP.rotation(t * 0.002f));
		WaterMesh.disc(vc, pose, r * 1.02f * (0.6f + 0.4f * k), -0.3f, t * 0.1f, WaterMesh.fade(CLOUD_DARK, k), skyLight, still);
		for (int i = 0; i < PUFFS; i++) {
			float a = i * 2.39996f;
			float rr = r * 0.92f * Mth.sqrt((i + 0.5f) / PUFFS);
			float size = 2.4f + 1.4f * ((i * 7) % 5) / 4f;
			pose.pushPose();
			pose.translate(Mth.cos(a) * rr, 0.4f + 0.5f * Mth.sin(i * 1.7f + t * 0.01f), Mth.sin(a) * rr);
			WaterMesh.sphere(vc, pose, size * (0.5f + 0.5f * k), 0.42f, 0.12f, t * 0.05f + i, WaterMesh.fade(i % 3 == 0 ? CLOUD_DARK : CLOUD, k),
					skyLight, still);
			pose.popPose();
		}
		pose.popPose();
		// the streaks: each falls from the cloud to the ground under it, over and over
		PoseStack.Pose last = pose.last();
		double ex = Mth.lerp(partial, e.xo, e.getX());
		double ey = Mth.lerp(partial, e.yo, e.getY());
		double ez = Mth.lerp(partial, e.zo, e.getZ());
		int rainCol = WaterMesh.fade(RAIN, k);
		for (int i = 0; i < STREAKS; i++) {
			float a = i * 2.39996f;
			float rr = r * Mth.sqrt(((i * 0.618034f) % 1f));
			float x = Mth.cos(a) * rr;
			float z = Mth.sin(a) * rr;
			int ground = e.level().getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(ex + x), (int) Math.floor(ez + z));
			float gy = (float) (ground - ey);
			float top = cy - 0.3f;
			if (gy > top - 1f) {
				continue;
			}
			float ph = ((i * 0.3711f) + t * 0.075f) % 1f;
			float y = top - ph * (top - gy);
			float len = 0.9f;
			float y0 = Math.max(gy, y - len);
			float w = 0.04f;
			Vec3 n = new Vec3(0, 0, 1);
			WaterMesh.quad(vc, last, new Vec3(x - w, y, z), new Vec3(x - w, y0, z), new Vec3(x + w, y0, z), new Vec3(x + w, y, z), still, 0f, 0f,
					0.1f, 1f, rainCol, light, n);
			WaterMesh.quad(vc, last, new Vec3(x, y, z - w), new Vec3(x, y0, z - w), new Vec3(x, y0, z + w), new Vec3(x, y, z + w), still, 0f, 0f,
					0.1f, 1f, rainCol, light, n);
			// the splash ring where it lands
			if (i % 3 == 0 && ph > 0.86f) {
				float s = (ph - 0.86f) / 0.14f;
				pose.pushPose();
				pose.translate(x, gy + 0.04f, z);
				WaterMesh.ring(vc, pose, 0.1f + 0.35f * s, 0.025f, 10, 0f, t, WaterMesh.fade(WaterMesh.FOAM, k * (1f - s)), light, flow);
				pose.popPose();
			}
		}
	}
}
