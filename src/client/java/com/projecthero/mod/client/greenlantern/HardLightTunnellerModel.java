package com.projecthero.mod.client.greenlantern;

import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.client.maxsteel.TurboDraw;
import com.projecthero.mod.greenlantern.GreenLanternConfig;
import com.projecthero.mod.greenlantern.entity.HardLightConstructEntity;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: the Hard-Light Tunneller drawn as an animated-show mole machine -- a chunky armoured drill car on two tracks,
 * a big segmented drill cone with two spiral flutes out front on a collar held by pistons, a bubble canopy, flank vents,
 * the emblem on each side and twin exhaust stacks at the back. It faces +Z (the bore), sits centred in the 3x3 (2.6
 * blocks across and tall at most) and glides with the entity's interpolated position. The drill spins up while it is
 * biting rock and idles when it is not, the track cleats and road wheels roll with the distance travelled, and the body
 * shudders while it cuts.
 */
final class HardLightTunnellerModel {
	private static final int GREEN = HardLightDraw.GREEN;
	private static final int PALE = HardLightDraw.PALE;
	private static final int DEEP = HardLightDraw.DEEP;
	/** Per car: {last age, drill angle (deg), drill speed (deg / tick)} -- the spin eases between idle and cutting. */
	private static final Map<HardLightConstructEntity, float[]> SPIN = new WeakHashMap<>();

	// the track loop (local frame): straight runs between the end sprockets, radius R
	private static final float TRACK_X = 0.98f;
	private static final float TRACK_FRONT = 0.2f;
	private static final float TRACK_BACK = -1.2f;
	private static final float TRACK_Y = -1.0f;
	private static final float TRACK_R = 0.28f;

	private HardLightTunnellerModel() {
	}

	private static void p(VertexConsumer vc, PoseStack pose, float x, float y, float z, float hx, float hy, float hz, int rgb, float a) {
		HardLightDraw.part(vc, pose, x, y, z, hx, hy, hz, rgb, a);
	}

	static void render(HardLightConstructEntity e, VertexConsumer vc, PoseStack pose, float age, float alpha, float partial) {
		pose.mulPose(Axis.YP.rotationDegrees(-e.getViewYRot(partial)));
		pose.mulPose(Axis.XP.rotationDegrees(e.getViewXRot(partial)));
		Vec3 fwd = e.getViewVector(partial);
		// distance down the bore so far (END holds the first slice's centre): drives the tracks
		Vec3 pos = e.getPosition(partial);
		float travelled = (float) pos.subtract(e.end()).dot(fwd);
		boolean cutting = (age - e.actionTick()) <= GreenLanternConfig.TUNNEL_TICKS_PER_SLICE + 1.5f && alpha > 0.95f
				&& !e.level().getBlockState(BlockPos.containing(pos.add(fwd.scale(1.6)))).isAir();
		float spin = spin(e, age, cutting);
		float grow = Math.min(1f, age / 4f);
		pose.scale(grow, grow, grow);
		pose.translate(0f, -0.15f, -0.12f); // tracks just off the bore floor, drill tip on the face
		if (cutting) { // the whole machine shudders against the rock
			pose.translate(0.018f * Mth.sin(age * 9.1f), 0.022f * Mth.sin(age * 7.3f + 1f), 0.012f * Mth.sin(age * 11.7f));
		}
		tracks(vc, pose, travelled, alpha);
		hull(vc, pose, age, alpha, cutting);
		drill(e, vc, pose, age, alpha, spin, cutting);
	}

	/** Eases the drill between idle and cutting speed and integrates its angle. */
	private static float spin(HardLightConstructEntity e, float age, boolean cutting) {
		float[] s = SPIN.computeIfAbsent(e, k -> new float[] { age, 0f, 10f });
		float dt = Mth.clamp(age - s[0], 0f, 2f);
		s[0] = age;
		float target = cutting ? 46f : 12f;
		s[2] += (target - s[2]) * Math.min(1f, dt * 0.25f);
		s[1] = (s[1] + s[2] * dt) % 360f;
		return s[1];
	}

	// ---------------------------------------------------------------- tracks

	private static void tracks(VertexConsumer vc, PoseStack pose, float travelled, float a) {
		float straight = TRACK_FRONT - TRACK_BACK;
		float perimeter = 2f * straight + 2f * Mth.PI * TRACK_R;
		int cleats = 22;
		float gap = perimeter / cleats;
		float wheelSpin = travelled / 0.2f * Mth.RAD_TO_DEG;
		for (int s = -1; s <= 1; s += 2) {
			float x = s * TRACK_X;
			// the track's inner band and side plate
			p(vc, pose, x, TRACK_Y, (TRACK_FRONT + TRACK_BACK) / 2f, 0.2f, 0.17f, straight / 2f + 0.05f, DEEP, a * 0.85f);
			p(vc, pose, x - s * 0.17f, TRACK_Y + 0.02f, (TRACK_FRONT + TRACK_BACK) / 2f, 0.025f, 0.22f, straight / 2f + 0.12f, GREEN, a);
			// end sprockets
			for (float z : new float[] { TRACK_FRONT, TRACK_BACK }) {
				pose.pushPose();
				pose.translate(x, TRACK_Y, z);
				pose.mulPose(Axis.XP.rotationDegrees(travelled / TRACK_R * Mth.RAD_TO_DEG));
				for (int k = 0; k < 3; k++) {
					HardLightDraw.part(vc, pose, 0f, 0f, 0f, 0.19f, TRACK_R * 0.82f, 0.09f, DEEP, a);
					pose.mulPose(Axis.XP.rotationDegrees(60f));
				}
				HardLightDraw.glow(vc, pose, s * 0.2f, 0f, 0f, 0.02f, 0.07f, 0.07f, a);
				pose.popPose();
			}
			// road wheels between them
			for (int w = 0; w < 3; w++) {
				float z = Mth.lerp((w + 1) / 4f, TRACK_BACK, TRACK_FRONT);
				pose.pushPose();
				pose.translate(x + s * 0.04f, TRACK_Y - 0.03f, z);
				pose.mulPose(Axis.XP.rotationDegrees(wheelSpin));
				HardLightDraw.part(vc, pose, 0f, 0f, 0f, 0.18f, 0.17f, 0.05f, GREEN, a);
				HardLightDraw.part(vc, pose, 0f, 0f, 0f, 0.18f, 0.05f, 0.17f, GREEN, a);
				HardLightDraw.glow(vc, pose, s * 0.19f, 0f, 0f, 0.015f, 0.045f, 0.045f, a);
				pose.popPose();
			}
			// the cleats running round the loop: back along the bottom, forward along the top
			for (int i = 0; i < cleats; i++) {
				float d = ((i * gap + travelled) % perimeter + perimeter) % perimeter;
				float y;
				float z;
				float ang;
				if (d < straight) { // bottom run, front -> back
					z = TRACK_FRONT - d;
					y = TRACK_Y - TRACK_R;
					ang = 0f;
				} else if (d < straight + Mth.PI * TRACK_R) { // round the back sprocket, up
					float t = (d - straight) / TRACK_R;
					z = TRACK_BACK - TRACK_R * Mth.sin(t);
					y = TRACK_Y - TRACK_R * Mth.cos(t);
					ang = t * Mth.RAD_TO_DEG;
				} else if (d < 2f * straight + Mth.PI * TRACK_R) { // top run, back -> front
					z = TRACK_BACK + (d - straight - Mth.PI * TRACK_R);
					y = TRACK_Y + TRACK_R;
					ang = 180f;
				} else { // round the front sprocket, down
					float t = (d - 2f * straight - Mth.PI * TRACK_R) / TRACK_R;
					z = TRACK_FRONT + TRACK_R * Mth.sin(t);
					y = TRACK_Y + TRACK_R * Mth.cos(t);
					ang = 180f + t * Mth.RAD_TO_DEG;
				}
				pose.pushPose();
				pose.translate(x, y, z);
				pose.mulPose(Axis.XP.rotationDegrees(-ang));
				HardLightDraw.part(vc, pose, 0f, 0f, 0f, 0.24f, 0.035f, 0.055f, i % 2 == 0 ? PALE : GREEN, a);
				pose.popPose();
			}
			// the armoured fender over the track, with a bright edge and a headlight up front
			p(vc, pose, x, -0.6f, -0.5f, 0.27f, 0.05f, 0.98f, GREEN, a);
			p(vc, pose, x + s * 0.27f, -0.66f, -0.5f, 0.02f, 0.04f, 0.92f, PALE, a);
			pose.pushPose();
			pose.translate(x, -0.68f, 0.56f);
			pose.mulPose(Axis.XP.rotationDegrees(35f));
			HardLightDraw.part(vc, pose, 0f, 0f, 0f, 0.27f, 0.04f, 0.16f, GREEN, a);
			pose.popPose();
			HardLightDraw.glow(vc, pose, x, -0.6f, 0.49f, 0.1f, 0.04f, 0.02f, a);
		}
	}

	// ---------------------------------------------------------------- hull, canopy, vents, stacks

	private static void hull(VertexConsumer vc, PoseStack pose, float age, float a, boolean cutting) {
		float lit = Math.min(1f, a);
		// the lower hull between the tracks and the wider upper deck
		p(vc, pose, 0f, -0.5f, -0.5f, 0.66f, 0.32f, 0.85f, GREEN, a);
		p(vc, pose, 0f, -0.86f, -0.45f, 0.5f, 0.05f, 0.7f, DEEP, a);
		p(vc, pose, 0f, 0.0f, -0.6f, 0.72f, 0.2f, 0.72f, GREEN, a);
		// the sloped glacis plate running down to the drill collar
		pose.pushPose();
		pose.translate(0f, -0.1f, 0.25f);
		pose.mulPose(Axis.XP.rotationDegrees(38f));
		HardLightDraw.part(vc, pose, 0f, 0f, 0f, 0.7f, 0.05f, 0.3f, GREEN, a);
		HardLightDraw.part(vc, pose, 0f, 0.055f, 0f, 0.08f, 0.01f, 0.28f, PALE, a);
		pose.popPose();
		for (int s = -1; s <= 1; s += 2) {
			// bright seams along the deck edges
			p(vc, pose, s * 0.72f, 0.2f, -0.6f, 0.02f, 0.02f, 0.72f, PALE, a);
			// three flank vent slats, glowing harder while it cuts
			for (int k = 0; k < 3; k++) {
				p(vc, pose, s * 0.73f, 0.04f, -0.95f + k * 0.17f, 0.012f, 0.13f, 0.045f, PALE, a);
			}
			HardLightDraw.glow(vc, pose, s * 0.735f, 0.04f, -0.78f, 0.006f, 0.1f, 0.24f,
					a * (cutting ? 0.55f + 0.3f * Mth.sin(age * 1.7f) : 0.3f));
			// the emblem on each flank
			pose.pushPose();
			pose.translate(s * 0.675f, -0.47f, -0.55f);
			pose.mulPose(Axis.YP.rotationDegrees(s * 90f));
			HardLightDraw.emblem(vc, pose, 0.44f, lit);
			pose.popPose();
		}
		// the bubble canopy over the cockpit, with its frame, windscreen and a dash glow
		pose.pushPose();
		pose.translate(0f, 0.36f, -0.42f);
		TurboDraw.box(vc, pose.last(), 0.38f, 0.17f, 0.42f, HardLightDraw.tint(PALE), 0.34f * lit);
		TurboDraw.box(vc, pose.last(), 0.4f, 0.025f, 0.44f, HardLightDraw.tint(GREEN), 0.7f * lit);
		TurboDraw.box(vc, pose.last(), 0.03f, 0.18f, 0.43f, HardLightDraw.tint(GREEN), 0.55f * lit);
		pose.popPose();
		pose.pushPose();
		pose.translate(0f, 0.37f, 0.06f);
		pose.mulPose(Axis.XP.rotationDegrees(-40f));
		TurboDraw.box(vc, pose.last(), 0.36f, 0.16f, 0.015f, HardLightDraw.tint(PALE), 0.3f * lit);
		TurboDraw.box(vc, pose.last(), 0.38f, 0.02f, 0.03f, HardLightDraw.tint(GREEN), 0.85f * lit);
		pose.popPose();
		HardLightDraw.glow(vc, pose, 0f, 0.27f, -0.05f, 0.18f, 0.02f, 0.03f, a * 0.8f);
		// the engine block at the back, its grille glowing
		p(vc, pose, 0f, -0.25f, -1.38f, 0.58f, 0.36f, 0.12f, DEEP, a);
		for (int k = 0; k < 3; k++) {
			HardLightDraw.glow(vc, pose, 0f, -0.42f + k * 0.15f, -1.51f, 0.4f, 0.025f, 0.01f, a * 0.7f);
		}
		// twin exhaust stacks puffing light
		float puff = cutting ? 0.5f + 0.5f * Mth.sin(age * 2.2f) : 0.25f;
		for (int s = -1; s <= 1; s += 2) {
			p(vc, pose, s * 0.46f, 0.42f, -1.15f, 0.075f, 0.24f, 0.075f, GREEN, a);
			p(vc, pose, s * 0.46f, 0.67f, -1.15f, 0.1f, 0.03f, 0.1f, PALE, a);
			HardLightDraw.glow(vc, pose, s * 0.46f, 0.72f + 0.04f * puff, -1.15f, 0.05f + 0.03f * puff, 0.025f,
					0.05f + 0.03f * puff, a * (0.5f + 0.5f * puff));
		}
		// a stubby antenna on the deck
		p(vc, pose, 0.42f, 0.32f, -0.95f, 0.015f, 0.14f, 0.015f, PALE, a);
		HardLightDraw.glow(vc, pose, 0.42f, 0.48f, -0.95f, 0.03f, 0.03f, 0.03f, a * (0.6f + 0.4f * Mth.sin(age * 0.6f)));
	}

	// ---------------------------------------------------------------- the drill

	private static void drill(HardLightConstructEntity e, VertexConsumer vc, PoseStack pose, float age, float a, float spin,
			boolean cutting) {
		float collarZ = 0.46f;
		float baseR = 0.98f;
		float coneLen = 1.2f;
		float z0 = collarZ + 0.07f;
		// the collar the drill turns in, and the gearbox behind it
		p(vc, pose, 0f, -0.08f, collarZ - 0.12f, 0.55f, 0.55f, 0.08f, DEEP, a);
		ring(vc, pose, -0.08f, collarZ, baseR + 0.02f, 0.08f, 0.07f, GREEN, a);
		// pistons from the hull to the collar, pumping while it cuts
		float pump = cutting ? 0.04f * Mth.sin(age * 3.1f) : 0f;
		for (int s = -1; s <= 1; s += 2) {
			p(vc, pose, s * 0.6f, -0.55f, -0.05f, 0.06f, 0.06f, 0.2f, DEEP, a);
			p(vc, pose, s * 0.6f, -0.55f, 0.24f + pump, 0.035f, 0.035f, 0.18f, PALE, a);
			p(vc, pose, s * 0.5f, 0.2f, -0.05f, 0.06f, 0.06f, 0.2f, DEEP, a);
			p(vc, pose, s * 0.5f, 0.2f, 0.24f - pump, 0.035f, 0.035f, 0.18f, PALE, a);
		}
		pose.pushPose();
		pose.translate(0f, -0.08f, 0f);
		pose.mulPose(Axis.ZP.rotationDegrees(spin));
		PoseStack.Pose m = pose.last();
		// the cone: four segments, each flaring a touch wider at its back than the last one ends (the stepped,
		// segmented bit), a solid green core with a faint pale shell round it like every other hard-light part
		int segs = 4;
		for (int i = 0; i < segs; i++) {
			float fa = i / (float) segs;
			float fb = (i + 1) / (float) segs;
			float ra = cone(baseR, fa) + (i == 0 ? 0f : 0.07f);
			float rb = cone(baseR, fb);
			float za = z0 + fa * coneLen;
			float zb = z0 + fb * coneLen;
			frustum(vc, m, za, ra, zb, rb, GREEN, 0.5f * a);
			frustum(vc, m, za, ra + 0.03f, zb, rb + 0.03f, PALE, 0.16f * a);
			if (i > 0) { // the bright lip where a segment steps out
				disc(vc, m, za, cone(baseR, fa), ra + 0.01f, PALE, 0.7f * a);
			}
		}
		disc(vc, m, z0, 0.3f, baseR, DEEP, 0.55f * a); // the back plate behind the collar
		// three spiral flutes standing proud of the cone, winding to the point
		for (int fl = 0; fl < 3; fl++) {
			flute(vc, m, z0, coneLen, baseR, fl * 120f, fl == 0 ? HardLightDraw.WHITE : PALE, a);
		}
		// the white-hot tip
		HardLightDraw.glow(vc, pose, 0f, 0f, z0 + coneLen + 0.02f, 0.05f, 0.05f, 0.08f, a);
		pose.popPose();
		// while it bites: a ring of light flaring round the cutting face as each slice gives way
		float k = (age - e.actionTick()) / 3f;
		if (cutting && k >= 0f && k < 1f) {
			pose.pushPose();
			pose.translate(0f, -0.08f, 0f);
			disc(vc, pose.last(), z0 + coneLen * 0.55f, 0.55f + 0.5f * k, 0.62f + 0.55f * k, HardLightDraw.WHITE, (1f - k) * 0.55f * a);
			pose.popPose();
		}
	}

	/** The cone's radius {@code f} of the way from its base to its point. */
	private static float cone(float baseR, float f) {
		return baseR * (1f - f) * (1f - 0.18f * f) + 0.03f;
	}

	private static final int SIDES = 16;

	/** A 16-sided frustum round the Z axis from radius {@code ra} at {@code za} to {@code rb} at {@code zb}. */
	private static void frustum(VertexConsumer vc, PoseStack.Pose m, float za, float ra, float zb, float rb, int rgb, float a) {
		for (int j = 0; j < SIDES; j++) {
			float t0 = j * Mth.TWO_PI / SIDES;
			float t1 = (j + 1) * Mth.TWO_PI / SIDES;
			float c0 = Mth.cos(t0), s0 = Mth.sin(t0), c1 = Mth.cos(t1), s1 = Mth.sin(t1);
			float nz = (ra - rb) / Math.max(0.01f, zb - za);
			quad(vc, m, rgb, a, (c0 + c1) / 2f, (s0 + s1) / 2f, nz,
					ra * c0, ra * s0, za, ra * c1, ra * s1, za, rb * c1, rb * s1, zb, rb * c0, rb * s0, zb);
		}
	}

	/** A flat ring between radii {@code r0} and {@code r1} at {@code z}, facing +Z. */
	private static void disc(VertexConsumer vc, PoseStack.Pose m, float z, float r0, float r1, int rgb, float a) {
		for (int j = 0; j < SIDES; j++) {
			float t0 = j * Mth.TWO_PI / SIDES;
			float t1 = (j + 1) * Mth.TWO_PI / SIDES;
			float c0 = Mth.cos(t0), s0 = Mth.sin(t0), c1 = Mth.cos(t1), s1 = Mth.sin(t1);
			quad(vc, m, rgb, a, 0f, 0f, 1f, r0 * c0, r0 * s0, z, r1 * c0, r1 * s0, z, r1 * c1, r1 * s1, z, r0 * c1, r0 * s1, z);
		}
	}

	/**
	 * One spiral flute: a blade of light standing up off the cone's surface, winding one and a half turns from the base
	 * to the point and narrowing as it goes.
	 */
	private static void flute(VertexConsumer vc, PoseStack.Pose m, float z0, float len, float baseR, float phase, int rgb, float a) {
		int steps = 28;
		float turns = 1.35f;
		float[] prev = null;
		for (int k = 0; k <= steps; k++) {
			float f = k / (float) steps;
			float t = (phase + f * turns * 360f) * Mth.DEG_TO_RAD;
			float r = cone(baseR, f) - 0.01f;
			float h = 0.13f * (1f - f * 0.75f);
			float z = z0 + f * len;
			float c = Mth.cos(t), s = Mth.sin(t);
			float[] cur = { r * c, r * s, z, (r + h) * c, (r + h) * s, z + h * 0.35f };
			if (prev != null) {
				quad(vc, m, rgb, 0.75f * a, -s, c, 0f, prev[0], prev[1], prev[2], prev[3], prev[4], prev[5], cur[3], cur[4], cur[5],
						cur[0], cur[1], cur[2]);
			}
			prev = cur;
		}
	}

	private static void quad(VertexConsumer vc, PoseStack.Pose m, int rgb, float alpha, float nx, float ny, float nz,
			float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3) {
		int c = HardLightDraw.tint(rgb);
		int r = (c >> 16) & 0xFF;
		int g = (c >> 8) & 0xFF;
		int b = c & 0xFF;
		int al = Math.max(0, Math.min(255, Math.round(alpha * 255)));
		float l = Mth.sqrt(nx * nx + ny * ny + nz * nz);
		if (l > 1.0e-4f) {
			nx /= l;
			ny /= l;
			nz /= l;
		}
		v(vc, m, x0, y0, z0, 0f, 0f, r, g, b, al, nx, ny, nz);
		v(vc, m, x1, y1, z1, 0f, 1f, r, g, b, al, nx, ny, nz);
		v(vc, m, x2, y2, z2, 1f, 1f, r, g, b, al, nx, ny, nz);
		v(vc, m, x3, y3, z3, 1f, 0f, r, g, b, al, nx, ny, nz);
	}

	private static void v(VertexConsumer vc, PoseStack.Pose m, float x, float y, float z, float u, float vv, int r, int g, int b, int a,
			float nx, float ny, float nz) {
		vc.addVertex(m, x, y, z).setColor(r, g, b, a).setUv(u, vv).setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(LightTexture.FULL_BRIGHT).setNormal(m, nx, ny, nz);
	}

	/** An octagonal hoop of radius {@code r} round the Z axis (raised {@code y}) at {@code z}. */
	private static void ring(VertexConsumer vc, PoseStack pose, float y, float z, float r, float thick, float depth, int rgb, float a) {
		pose.pushPose();
		pose.translate(0f, y, 0f);
		for (int i = 0; i < 8; i++) {
			pose.pushPose();
			pose.mulPose(Axis.ZP.rotationDegrees(i * 45f + 22.5f));
			HardLightDraw.part(vc, pose, 0f, r, z, r * 0.42f, thick, depth, rgb, a);
			pose.popPose();
		}
		pose.popPose();
	}
}
