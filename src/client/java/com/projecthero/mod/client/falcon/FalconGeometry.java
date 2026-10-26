package com.projecthero.mod.client.falcon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.falcon.FalconConfig;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import org.joml.Matrix4f;

/**
 * v0.17.1: the Falcon's hand-built 3D parts, all box geometry on a plain white texture tinted per box. Units are model
 * pixels (1/16 block) in the body part's space (+y down, the back is +z, the wearer's left is +x).
 *
 * <h2>The EXO-7 Wing Pack (second pass, after the MCU rig)</h2>
 * A slim armoured backpack (hinge housings at the shoulders, vents, a red spine, straps over the shoulders) with twin jet
 * nozzles under it, and two long segmented wings. Each wing is a three-joint arm -- shoulder, elbow, wrist -- carrying
 * fifteen long steel flight feathers (four tertials, five secondaries, six fanned primaries that split apart at the tip)
 * under a row of dark coverts, each feather a blade with a raised ridge, a bright leading edge and a carbon tip, layered
 * like shingles. Folded, the arm zig-zags down the pack's sides and every blade retracts and hangs straight down; spread,
 * the wings open to a full ~5-block span with a slight upward dihedral. The unfold is staggered joint by joint (shoulder,
 * then elbow, then wrist), and in flight the tips flex. The Wing Shield wraps both wings round in front, and the Wing
 * Slash swings the right wing round from his back across the front with a sweep trail.
 */
public final class FalconGeometry {
	public static final ResourceLocation WHITE = ProjectHeroMod.id("textures/entity/falcon/white.png");

	private static final int PACK = 0xFF262A31;
	private static final int PACK_PLATE = 0xFF3A4049;
	private static final int PACK_RED = 0xFFB8142E;
	private static final int VENT = 0xFF121418;
	private static final int STRAP = 0xFF1E2126;
	private static final int SPAR = 0xFF3C424C;
	private static final int SPAR_LIGHT = 0xFF6A727E;
	private static final int JOINT = 0xFF23272D;
	private static final int FEATHER = 0xFFA9B2BE;
	private static final int FEATHER_LIGHT = 0xFFC4CBD5;
	private static final int FEATHER_EDGE = 0xFFEEF1F5;
	private static final int FEATHER_RIDGE = 0xFF7E8794;
	private static final int FEATHER_TIP = 0xFF3E434B;
	private static final int COVERT = 0xFF66707C;
	private static final int COVERT_TIP = 0xFF8E1426;
	private static final int NOZZLE = 0xFF1A1C21;
	private static final int NOZZLE_RIM = 0xFF5A616B;
	private static final int NOZZLE_HOT = 0xFFFF9A3A;

	/** The wing root (the hinge in the pack's shoulder housing), left wing. */
	private static final float ROOT_X = 3.7f, ROOT_Y = 1.5f, ROOT_Z = 3.8f;
	/** The three arm segments: upper arm, forearm, hand. */
	private static final float[] SEG_LEN = { 9f, 10f, 7.5f };
	/** Spread: relative joint angles (degrees about +z: 0 = straight out, + = toward the feet). */
	private static final float[] SPREAD_REL = { -17f, 7f, 11f };
	/** Folded: the arm zig-zags down the pack's side (down, back up, down). */
	private static final float[] FOLD_REL = { 96f, -181f, 178f };

	/** One flight feather: its segment, where along it (0..1), its spread angle (absolute, degrees) and length. */
	private record Feather(int seg, float u, float angle, float length) {
	}

	private static final Feather[] FEATHERS = {
			// tertials (by the body, hanging straight back)
			new Feather(0, 0.08f, 95f, 13f), new Feather(0, 0.33f, 93f, 14f), new Feather(0, 0.58f, 91f, 14.5f), new Feather(0, 0.83f, 89f, 15f),
			// secondaries
			new Feather(1, 0.04f, 87f, 16f), new Feather(1, 0.24f, 84f, 16.5f), new Feather(1, 0.44f, 81f, 17f), new Feather(1, 0.64f, 77f, 17.5f),
			new Feather(1, 0.84f, 73f, 18f),
			// primaries: fanned out, separating at the tip
			new Feather(2, 0.0f, 66f, 19f), new Feather(2, 0.2f, 56f, 19.5f), new Feather(2, 0.4f, 46f, 19f), new Feather(2, 0.6f, 35f, 18f),
			new Feather(2, 0.8f, 24f, 16.5f), new Feather(2, 1.0f, 12f, 14.5f) };

	/** The coverts: short dark blades over the roots of the flight feathers. */
	private static final Feather[] COVERTS = {
			new Feather(0, 0.15f, 93f, 5f), new Feather(0, 0.45f, 91f, 5.5f), new Feather(0, 0.75f, 89f, 5.5f),
			new Feather(1, 0.1f, 86f, 6f), new Feather(1, 0.35f, 82f, 6f), new Feather(1, 0.6f, 78f, 6f), new Feather(1, 0.85f, 74f, 6f),
			new Feather(2, 0.15f, 62f, 5.5f), new Feather(2, 0.45f, 50f, 5f) };

	/** Folded, the blades retract so their tips end about here (body y, px): just past the hips. */
	private static final float FOLDED_TIP_Y = 15.5f;

	private FalconGeometry() {
	}

	// ---------------------------------------------------------------- boxes

	/** An axis-aligned box from (x0, y0, z0) to (x1, y1, z1), every face {@code argb}. */
	public static void box(VertexConsumer vc, PoseStack.Pose pose, int light, float x0, float y0, float z0, float x1, float y1, float z1, int argb) {
		Matrix4f m = pose.pose();
		face(vc, m, pose, light, argb, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, 0f, 0f, -1f);
		face(vc, m, pose, light, argb, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1, 0f, 0f, 1f);
		face(vc, m, pose, light, argb, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, -1f, 0f, 0f);
		face(vc, m, pose, light, argb, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, 1f, 0f, 0f);
		face(vc, m, pose, light, argb, x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0, 0f, -1f, 0f);
		face(vc, m, pose, light, argb, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0f, 1f, 0f);
	}

	private static void face(VertexConsumer vc, Matrix4f m, PoseStack.Pose pose, int light, int argb, float ax, float ay, float az, float bx,
			float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz, float nx, float ny, float nz) {
		vc.addVertex(m, ax, ay, az).setColor(argb).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
		vc.addVertex(m, bx, by, bz).setColor(argb).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
		vc.addVertex(m, cx, cy, cz).setColor(argb).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
		vc.addVertex(m, dx, dy, dz).setColor(argb).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
	}

	public static VertexConsumer solid(MultiBufferSource buffers) {
		return buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
	}

	private static float smooth(float t) {
		t = Mth.clamp(t, 0f, 1f);
		return t * t * (3f - 2f * t);
	}

	// ---------------------------------------------------------------- the Wing Slash's swing

	/** The slashing wing's yaw about the root (degrees; 0 = straight out to the side, + = round to the front) at progress t. */
	public static float slashYaw(float t) {
		if (t < 0f) {
			return 0f;
		}
		if (t <= 1f) {
			float e = 1f - (1f - t) * (1f - t) * (1f - t); // ease out: a fast whip that slows at the end
			return Mth.lerp(e, -70f, 135f);
		}
		return Mth.lerp(smooth((t - 1f) / 0.55f), 135f, 0f); // and back
	}

	/** The swing's progress for a Wing Slash {@code age} ticks old: 0..1 the cut, 1..1.55 the recovery, -1 = no slash. */
	public static float slashProgress(float age) {
		float swing = FalconConfig.SLASH_SWING_TICKS;
		if (age < 0f || age > swing * 1.55f) {
			return -1f;
		}
		return age / swing;
	}

	// ---------------------------------------------------------------- the Wing Pack

	/**
	 * The pack and both wings, in the body part's space (already {@code body.translateAndRotate}d, in blocks).
	 *
	 * @param spread 0 = folded down the pack's sides, 1 = spread for flight (eased by the caller; staggered per joint here)
	 * @param wrap   0..1 swings them round in front (the Wing Shield)
	 * @param jets   0..1 the jet glow and flame
	 * @param slash  the Wing Slash's progress ({@link #slashProgress}), -1 = none: the right wing sweeps round in front
	 * @param flex   0..1 how much the wingtips flex (flying)
	 */
	public static void wingPack(PoseStack pose, MultiBufferSource buffers, int light, float spread, float wrap, float jets, float slash,
			float flex, float age) {
		pose.pushPose();
		pose.scale(1f / 16f, 1f / 16f, 1f / 16f);
		pack(pose, buffers, light, jets);
		for (int k = -1; k <= 1; k += 2) {
			boolean slashing = k < 0 && slash >= 0f;
			pose.pushPose();
			pose.translate(ROOT_X * k, ROOT_Y, ROOT_Z);
			if (k < 0) {
				pose.scale(-1f, 1f, 1f);
			}
			if (slashing) {
				float t = slash;
				// during the cut the wing is fully out, swept level; the recovery eases it back to whatever it was
				float out = t <= 1f ? 1f : 1f - smooth((t - 1f) / 0.55f);
				float yaw = slashYaw(t);
				if (t <= 1.15f) {
					trail(pose, buffers, t);
				}
				wing(pose, buffers, light, Math.max(spread, out), wrap * (1f - out), flex, age, yaw, out, k);
			} else {
				wing(pose, buffers, light, spread, wrap, flex, age, 0f, 0f, k);
			}
			pose.popPose();
		}
		if (jets > 0.05f) {
			for (int k = -1; k <= 1; k += 2) {
				pose.pushPose();
				// the flame leans back off the pack (up, once he lies flat) so it streams clear of his legs
				pose.translate(1.9f * k, 12.9f, 3.3f);
				pose.mulPose(Axis.XP.rotationDegrees(22f));
				jet(pose, buffers, 0f, 0f, 0f, 1.4f, 8f + 18f * jets, age + k * 3f);
				pose.popPose();
			}
		}
		pose.popPose();
	}

	/** The backpack: shell, plates, hinge housings, vents, the red spine, shoulder straps and the twin nozzles. */
	private static void pack(PoseStack pose, MultiBufferSource buffers, int light, float jets) {
		VertexConsumer vc = solid(buffers);
		PoseStack.Pose p = pose.last();
		// the shell, tapering a touch toward the bottom
		box(vc, p, light, -3.5f, 0.4f, 2.05f, 3.5f, 7.5f, 4.0f, PACK);
		box(vc, p, light, -3.0f, 7.5f, 2.05f, 3.0f, 10.4f, 3.8f, PACK);
		// raised armour plates either side of the spine
		box(vc, p, light, -3.0f, 1.2f, 4.0f, -0.9f, 6.8f, 4.45f, PACK_PLATE);
		box(vc, p, light, 0.9f, 1.2f, 4.0f, 3.0f, 6.8f, 4.45f, PACK_PLATE);
		box(vc, p, light, -2.5f, 7.6f, 3.8f, 2.5f, 9.8f, 4.2f, PACK_PLATE);
		// the red spine
		box(vc, p, light, -0.6f, 0.8f, 4.0f, 0.6f, 9.6f, 4.6f, PACK_RED);
		// vents on the plates
		for (int i = 0; i < 3; i++) {
			float y = 2.2f + i * 1.5f;
			box(vc, p, light, -2.6f, y, 4.45f, -1.3f, y + 0.5f, 4.55f, VENT);
			box(vc, p, light, 1.3f, y, 4.45f, 2.6f, y + 0.5f, 4.55f, VENT);
		}
		// the hinge housings at the shoulders, where the wings come out
		for (int k = -1; k <= 1; k += 2) {
			float x0 = k < 0 ? -4.5f : 2.5f, x1 = k < 0 ? -2.5f : 4.5f;
			box(vc, p, light, x0, -0.2f, 2.3f, x1, 3.0f, 4.9f, SPAR);
			box(vc, p, light, x0 + 0.3f, 0.3f, 4.9f, x1 - 0.3f, 2.5f, 5.1f, SPAR_LIGHT);
			// straps: over the shoulder and down the chest
			float sx0 = k < 0 ? -3.4f : 2.2f, sx1 = k < 0 ? -2.2f : 3.4f;
			box(vc, p, light, sx0, -0.35f, -2.3f, sx1, 0.15f, 2.3f, STRAP);
			box(vc, p, light, sx0, 0.0f, -2.35f, sx1, 6.5f, -2.05f, STRAP);
		}
		// chest buckle joining the straps
		box(vc, p, light, -2.4f, 4.6f, -2.45f, 2.4f, 5.6f, -2.1f, STRAP);
		box(vc, p, light, -0.7f, 4.4f, -2.6f, 0.7f, 5.8f, -2.3f, PACK_RED);
		// the twin jet nozzles under the pack
		int hot = jets > 0.05f ? NOZZLE_HOT : 0xFF2C1A12;
		int hotLight = jets > 0.05f ? LightTexture.FULL_BRIGHT : light;
		for (int k = -1; k <= 1; k += 2) {
			float cx = 1.9f * k;
			box(vc, p, light, cx - 1.1f, 10.3f, 2.3f, cx + 1.1f, 12.4f, 4.3f, NOZZLE);
			box(vc, p, light, cx - 0.75f, 10.3f, 1.95f, cx + 0.75f, 12.4f, 4.65f, NOZZLE);
			box(vc, p, light, cx - 1.3f, 12.4f, 2.1f, cx + 1.3f, 12.9f, 4.5f, NOZZLE_RIM);
			box(solid(buffers), p, hotLight, cx - 0.7f, 12.9f, 2.6f, cx + 0.7f, 13.0f, 4.0f, hot);
		}
	}

	/**
	 * One wing (the left; the right is drawn mirrored), its root at the origin. {@code slashYaw} / {@code slashOut}: the
	 * Wing Slash's swing about the root and how far it overrides the normal pose.
	 */
	private static void wing(PoseStack pose, MultiBufferSource buffers, int light, float spread, float wrap, float flex, float age,
			float slashYaw, float slashOut, int side) {
		// the joints unfold one after the other
		float sA = smooth(spread / 0.7f);
		float sB = smooth((spread - 0.15f) / 0.7f);
		float sC = smooth((spread - 0.3f) / 0.7f);
		float[] segS = { sA, sB, sC };
		float wave = Mth.sin(age * 0.22f + side * 0.7f) * flex;
		float[] rel = {
				Mth.lerp(sA, FOLD_REL[0], SPREAD_REL[0]) + 2.5f * wave + 12f * slashOut,
				Mth.lerp(sB, FOLD_REL[1], SPREAD_REL[1]) - 1.5f * wave,
				Mth.lerp(sC, FOLD_REL[2], SPREAD_REL[2]) + 3.5f * wave - 6f * slashOut };
		// the dihedral: spread, the wings sweep a little back (toward +z, which is "up" once he lies flat in flight)
		float yaw = Mth.lerp(sA, 0f, -9f) + 50f * wrap + slashYaw;
		pose.mulPose(Axis.YP.rotationDegrees(yaw));

		// walk the chain once to know each segment's absolute angle and (folded) height, for the feathers
		float[] abs = new float[3];
		float a = 0f;
		for (int i = 0; i < 3; i++) {
			a += rel[i];
			abs[i] = a;
		}
		float[][] foldedRootY = foldedSegmentRoots();

		VertexConsumer vc = solid(buffers);
		for (int seg = 0; seg < 3; seg++) {
			if (seg > 0) {
				pose.translate(SEG_LEN[seg - 1], 0f, 0f);
				// the shield curls the outer wing round him
				if (wrap > 0.001f) {
					pose.mulPose(Axis.YP.rotationDegrees(55f * wrap));
				}
			}
			pose.mulPose(Axis.ZP.rotationDegrees(rel[seg]));
			// folded, the segments stack outward so they never z-fight
			float zLayer = (1f - segS[seg]) * 0.9f * seg;
			pose.pushPose();
			pose.translate(0f, 0f, zLayer);
			PoseStack.Pose sp = pose.last();
			float len = SEG_LEN[seg];
			// the arm: a dark spar with a lighter leading edge, a red stripe on the upper arm, and a joint at each end
			box(vc, sp, light, 0f, -1.1f, -0.75f, len, 0.9f, 0.75f, SPAR);
			box(vc, sp, light, 0.4f, -1.35f, -0.5f, len - 0.4f, -1.0f, 0.5f, SPAR_LIGHT);
			if (seg == 0) {
				box(vc, sp, light, 1.2f, -1.45f, -0.25f, len - 1.6f, -1.25f, 0.25f, PACK_RED);
			}
			box(vc, sp, light, len - 1.3f, -1.4f, -1.0f, len + 1.1f, 1.1f, 1.0f, JOINT);
			if (seg == 2) {
				// the hand's tip guard
				box(vc, sp, light, len + 1.1f, -0.9f, -0.6f, len + 2.4f, 0.5f, 0.6f, SPAR_LIGHT);
			}
			// the flight feathers on this segment, then the coverts over them
			int idx = 0;
			for (Feather f : FEATHERS) {
				if (f.seg == seg) {
					feather(pose, vc, light, f, abs[seg], segS[seg], foldedRootY[seg], idx, flex, age, side, false);
				}
				idx++;
			}
			idx = 0;
			for (Feather f : COVERTS) {
				if (f.seg == seg) {
					feather(pose, vc, light, f, abs[seg], segS[seg], foldedRootY[seg], idx, flex, age, side, true);
				}
				idx++;
			}
			pose.popPose();
		}
	}

	private static float[][] foldedRoots;

	/** Folded: each segment's root height and angle (body y relative to the wing root; degrees), worked out once. */
	private static float[][] foldedSegmentRoots() {
		if (foldedRoots == null) {
			float[][] r = new float[3][];
			float x = 0f, y = 0f, a = 0f;
			for (int i = 0; i < 3; i++) {
				a += FOLD_REL[i];
				r[i] = new float[] { y, a };
				x += SEG_LEN[i] * Mth.cos(a * Mth.DEG_TO_RAD);
				y += SEG_LEN[i] * Mth.sin(a * Mth.DEG_TO_RAD);
			}
			foldedRoots = r;
		}
		return foldedRoots;
	}

	/**
	 * One blade on the current segment (the pose is at the segment's root, x along it). Spread it points at its own
	 * absolute angle; folded it hangs straight down, retracted so its tip ends at {@link #FOLDED_TIP_Y}.
	 */
	private static void feather(PoseStack pose, VertexConsumer vc, int light, Feather f, float segAbs, float s, float[] foldedRoot, int idx,
			float flex, float age, int side, boolean covert) {
		float segLen = SEG_LEN[f.seg];
		float along = f.u * segLen;
		// where it hangs from when folded (body y below the root), so the folded blade can be cut to length
		float foldedAttachY = foldedRoot[0] + along * Mth.sin(foldedRoot[1] * Mth.DEG_TO_RAD);
		float foldedLen = covert ? Math.min(f.length, 5f) : Mth.clamp(FOLDED_TIP_Y - ROOT_Y - foldedAttachY, 3.5f, f.length);
		float len = Mth.lerp(s, foldedLen, f.length);
		float foldedAngle = 90f + (covert ? 0f : (idx - 7f) * 0.9f);
		float target = Mth.lerp(s, foldedAngle, f.angle);
		if (!covert && f.seg == 2) {
			// the primaries flutter
			target += Mth.sin(age * 0.31f + idx * 0.8f + side) * 1.8f * flex;
		}
		float relAngle = target - segAbs;
		pose.pushPose();
		// flight feathers hang from the trailing side of the spar; coverts sit over them, toward the back (+z)
		float z = covert ? 0.55f + 0.06f * idx : -0.25f + 0.11f * idx;
		pose.translate(along, 0.3f, z * Mth.lerp(s, 0.6f, 1f));
		pose.mulPose(Axis.ZP.rotationDegrees(relAngle));
		// shingled: each blade turned a little about its own length, so the rows overlap and catch the light
		pose.mulPose(Axis.XP.rotationDegrees(covert ? -12f : -9f * s));
		PoseStack.Pose fp = pose.last();
		float w = covert ? 2.9f : 2.6f;
		float t = covert ? 0.45f : 0.36f;
		float hw = w * 0.5f;
		if (covert) {
			box(vc, fp, light, 0f, -hw, -t, len * 0.82f, hw, t, COVERT);
			box(vc, fp, light, len * 0.82f, -hw * 0.75f, -t, len, hw * 0.75f, t, (idx & 1) == 0 ? COVERT_TIP : COVERT);
		} else {
			int base = (idx & 1) == 0 ? FEATHER : FEATHER_LIGHT;
			float body = len * 0.8f;
			box(vc, fp, light, 0f, -hw, -t, body, hw, t, base);
			// the bright leading edge and the raised ridge down the middle
			box(vc, fp, light, 0.6f, hw - 0.45f, -t - 0.02f, body, hw + 0.05f, t + 0.02f, FEATHER_EDGE);
			box(vc, fp, light, 0f, -0.25f, t, body * 0.95f, 0.25f, t + 0.18f, FEATHER_RIDGE);
			// the carbon tip, narrowing to a point
			box(vc, fp, light, body, -hw * 0.8f, -t * 0.9f, len - 1.2f, hw * 0.8f, t * 0.9f, FEATHER_TIP);
			box(vc, fp, light, len - 1.2f, -hw * 0.45f, -t * 0.8f, len, hw * 0.45f, t * 0.8f, FEATHER_TIP);
		}
		pose.popPose();
	}

	// ---------------------------------------------------------------- the slash trail

	/** Where the wing reaches in its own plane (spread, level for the cut): the inner and outer edge of the sweep. */
	private static float[][] slashReach() {
		float x = 0f, y = 0f, a = 0f;
		float[][] pts = new float[2][];
		float[] rel = { SPREAD_REL[0] + 12f, SPREAD_REL[1], SPREAD_REL[2] - 6f };
		for (int i = 0; i < 3; i++) {
			a += rel[i];
			x += SEG_LEN[i] * Mth.cos(a * Mth.DEG_TO_RAD);
			y += SEG_LEN[i] * Mth.sin(a * Mth.DEG_TO_RAD);
			if (i == 0) {
				pts[0] = new float[] { x, y + 6f };
			}
		}
		// past the hand, out along the outermost primary
		Feather tip = FEATHERS[FEATHERS.length - 2];
		pts[1] = new float[] { x + tip.length * Mth.cos(tip.angle * Mth.DEG_TO_RAD), y + tip.length * Mth.sin(tip.angle * Mth.DEG_TO_RAD) };
		return pts;
	}

	/** The sweep's trail: a fading ribbon over the arc the wing has just cut (root frame, before the wing's own yaw). */
	private static void trail(PoseStack pose, MultiBufferSource buffers, float t) {
		float[][] reach = slashReach();
		VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
		Matrix4f m = pose.last().pose();
		int steps = 14;
		float span = 0.45f;
		float alphaScale = t <= 1f ? 1f : Math.max(0f, 1f - (t - 1f) / 0.15f);
		for (int i = 0; i < steps; i++) {
			float t0 = Math.max(0f, Math.min(1f, t) - span * (i + 1) / steps);
			float t1 = Math.max(0f, Math.min(1f, t) - span * i / steps);
			if (t1 <= t0) {
				continue;
			}
			// the same yaw the wing gets (its swing plus the spread wing's sweep back)
			float y0 = (slashYaw(t0) - 9f) * Mth.DEG_TO_RAD, y1 = (slashYaw(t1) - 9f) * Mth.DEG_TO_RAD;
			float a1 = (1f - (float) i / steps) * 0.85f * alphaScale, a0 = (1f - (float) (i + 1) / steps) * 0.85f * alphaScale;
			// rotate (x, y, 0) about +y by the yaw: x' = x cos, z' = -x sin
			float ix0 = reach[0][0] * Mth.cos(y0), iz0 = -reach[0][0] * Mth.sin(y0), ix1 = reach[0][0] * Mth.cos(y1), iz1 = -reach[0][0] * Mth.sin(y1);
			float ox0 = reach[1][0] * Mth.cos(y0), oz0 = -reach[1][0] * Mth.sin(y0), ox1 = reach[1][0] * Mth.cos(y1), oz1 = -reach[1][0] * Mth.sin(y1);
			float iy = reach[0][1], oy = reach[1][1];
			ribbon(vc, m, ix0, iy, iz0, ox0, oy, oz0, ox1, oy, oz1, ix1, iy, iz1, a0, a1);
		}
	}

	private static void ribbon(VertexConsumer vc, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
			float dx, float dy, float dz, float a0, float a1) {
		// inner edge faint, outer edge bright, both ways round
		float r = 0.86f, g = 0.92f, b = 1f;
		vc.addVertex(m, ax, ay, az).setColor(r, g, b, a0 * 0.25f);
		vc.addVertex(m, bx, by, bz).setColor(r, g, b, a0);
		vc.addVertex(m, cx, cy, cz).setColor(r, g, b, a1);
		vc.addVertex(m, dx, dy, dz).setColor(r, g, b, a1 * 0.25f);
		vc.addVertex(m, dx, dy, dz).setColor(r, g, b, a1 * 0.25f);
		vc.addVertex(m, cx, cy, cz).setColor(r, g, b, a1);
		vc.addVertex(m, bx, by, bz).setColor(r, g, b, a0);
		vc.addVertex(m, ax, ay, az).setColor(r, g, b, a0 * 0.25f);
	}

	// ---------------------------------------------------------------- the jets

	/** A jet flame out of point ({@code x}, {@code y}, {@code z}) along +y: crossed tapering sheets, orange round a pale core. */
	private static void jet(PoseStack pose, MultiBufferSource buffers, float x, float y, float z, float width, float length, float age) {
		VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
		Matrix4f m = pose.last().pose();
		float flick = 0.85f + 0.15f * Mth.sin(age * 4.3f + x);
		for (int layer = 0; layer < 3; layer++) {
			float lw = layer == 0 ? width * 1.2f : layer == 1 ? width * 0.75f : width * 0.35f;
			float len = (layer == 0 ? length : layer == 1 ? length * 0.75f : length * 0.4f) * flick;
			float r = layer == 2 ? 0.75f : 1f, g = layer == 0 ? 0.45f : layer == 1 ? 0.75f : 0.9f, b = layer == 0 ? 0.12f : layer == 1 ? 0.35f : 1f;
			float al = layer == 0 ? 0.5f : layer == 1 ? 0.75f : 0.95f;
			for (int k = 0; k < 2; k++) {
				float ox = k == 0 ? lw : 0f, oz = k == 0 ? 0f : lw;
				sheet(vc, m, x - ox, y, z - oz, x + ox, y, z + oz, x, y + len, z, r, g, b, al);
				sheet(vc, m, x + ox, y, z + oz, x - ox, y, z - oz, x, y + len, z, r, g, b, al);
			}
		}
	}

	private static void sheet(VertexConsumer vc, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz, float tx, float ty, float tz,
			float r, float g, float b, float a) {
		vc.addVertex(m, ax, ay, az).setColor(r, g, b, a);
		vc.addVertex(m, bx, by, bz).setColor(r, g, b, a);
		vc.addVertex(m, tx, ty, tz).setColor(r, g, b, 0f);
		vc.addVertex(m, tx, ty, tz).setColor(r, g, b, 0f);
	}

	// ---------------------------------------------------------------- the wrist launchers

	/**
	 * A wrist launcher on the outside of the forearm, in the arm part's space (already {@code arm.translateAndRotate}d, in
	 * blocks): a dark housing and a short barrel at the wrist, and the muzzle flash ({@code flash} 0..1) out of it along the
	 * arm.
	 */
	public static void wristLauncher(PoseStack pose, MultiBufferSource buffers, int light, boolean right, boolean slim, float flash, int seed) {
		float outer = right ? (slim ? -2f : -3f) : (slim ? 2f : 3f);
		float dir = right ? -1f : 1f;
		float x0 = Math.min(outer, outer + 0.95f * dir), x1 = Math.max(outer, outer + 0.95f * dir);
		float bx = outer + 0.45f * dir;
		pose.pushPose();
		pose.scale(1f / 16f, 1f / 16f, 1f / 16f);
		VertexConsumer vc = solid(buffers);
		PoseStack.Pose p = pose.last();
		box(vc, p, light, x0, 4.6f, -1.2f, x1, 9.3f, 1.2f, STRAP);
		box(vc, p, light, x0 - 0.05f, 5.2f, -1.25f, x1 + 0.05f, 5.8f, 1.25f, PACK_RED);
		box(vc, p, light, bx - 0.4f, 9.3f, -0.4f, bx + 0.4f, 10.5f, 0.4f, NOZZLE);
		pose.popPose();
		if (flash > 0.01f) {
			pose.pushPose();
			pose.translate(bx / 16f, 10.6f / 16f, 0f);
			pose.mulPose(Axis.XP.rotationDegrees(90f)); // the flash fires along its -z: turn that down the arm (+y)
			pose.scale(0.9f, 0.9f, 0.9f);
			com.projecthero.mod.client.firearm.MuzzleFlash.drawBare(pose, buffers, 1.3f, flash, seed);
			pose.popPose();
		}
	}

	// ---------------------------------------------------------------- Redwing

	/** The Redwing drone centred on the origin, nose along +z, upright (y up), in blocks. {@code attack}: its lights burn. */
	public static void redwing(PoseStack pose, MultiBufferSource buffers, int light, float age, boolean attack) {
		pose.pushPose();
		pose.scale(1f / 16f, 1f / 16f, 1f / 16f); // model pixels, y up (entity space)
		VertexConsumer vc = solid(buffers);
		PoseStack.Pose last = pose.last();
		int red = 0xFFC8102E, dark = 0xFF1E2026, steel = 0xFF8C95A3;
		// the body: a sleek bird -- a dark fuselage with a red back, a beak-like nose and a tail
		box(vc, last, light, -1.5f, -1.2f, -4f, 1.5f, 1.0f, 3.5f, dark);
		box(vc, last, light, -1.2f, 1.0f, -3.5f, 1.2f, 1.6f, 3.0f, red);
		box(vc, last, light, -0.8f, -0.6f, 3.5f, 0.8f, 0.6f, 5.5f, steel);
		box(vc, last, light, -2.0f, -0.3f, -6.5f, 2.0f, 0.3f, -4f, red);
		// the wings, swept back, flexing a little
		float flex = 6f * Mth.sin(age * 0.6f);
		for (int k = -1; k <= 1; k += 2) {
			pose.pushPose();
			pose.translate(1.4f * k, 0.6f, 0.5f);
			if (k < 0) {
				pose.scale(-1f, 1f, 1f);
			}
			pose.mulPose(Axis.ZP.rotationDegrees(flex));
			pose.mulPose(Axis.YP.rotationDegrees(18f)); // swept back (the nose is +z)
			PoseStack.Pose wp = pose.last();
			box(vc, wp, light, 0f, -0.3f, -1.5f, 8.5f, 0.3f, 1.5f, red);
			box(vc, wp, light, 0f, -0.35f, 1.5f, 7.5f, 0.25f, 2.2f, dark);
			box(vc, wp, light, 8.5f, -0.3f, -1.2f, 9.5f, 0.3f, 1.0f, steel);
			pose.popPose();
		}
		// the eye / laser lens and the running light
		int lens = attack ? 0xFFFF2A2A : 0xFFFF7070;
		box(vc, last, LightTexture.FULL_BRIGHT, -0.6f, -0.4f, 5.5f, 0.6f, 0.4f, 6.0f, lens);
		boolean blink = attack || ((int) age / 10) % 2 == 0;
		box(vc, last, blink ? LightTexture.FULL_BRIGHT : light, -0.4f, 1.6f, 0f, 0.4f, 2.0f, 0.8f, blink ? 0xFFFF3030 : 0xFF601010);
		pose.popPose();
	}
}
