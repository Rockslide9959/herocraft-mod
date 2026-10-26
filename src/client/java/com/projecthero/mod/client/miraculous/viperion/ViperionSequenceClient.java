package com.projecthero.mod.client.miraculous.viperion;

import static com.projecthero.mod.miraculous.viperion.ViperionSequence.BRACELET;
import static com.projecthero.mod.miraculous.viperion.ViperionSequence.COIL;
import static com.projecthero.mod.miraculous.viperion.ViperionSequence.COIL_END;
import static com.projecthero.mod.miraculous.viperion.ViperionSequence.LYRE;
import static com.projecthero.mod.miraculous.viperion.ViperionSequence.POSE;
import static com.projecthero.mod.miraculous.viperion.ViperionSequence.SCALES;
import static com.projecthero.mod.miraculous.viperion.ViperionSequence.SCALES_END;
import static com.projecthero.mod.miraculous.viperion.ViperionSequence.TOTAL;
import static com.projecthero.mod.miraculous.viperion.ViperionSequence.VISOR;
import static com.projecthero.mod.miraculous.viperion.ViperionSequence.VISOR_END;

import org.joml.Vector3f;

import com.projecthero.mod.client.miraculous.MiraculousSequenceClient;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.data.MiraculousState;
import com.projecthero.mod.miraculous.viperion.ViperionSequence;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: the client half of Viperion's long transformation ({@link ViperionSequence}) on the shared frame
 * ({@link MiraculousSequenceClient}): Sass coiling into the Snake Bracelet, a translucent snake of teal light winding up
 * his raised arm and round his body with the suit forming in its wake, scale patterns rippling up over the suit, the
 * Lyre forming in his hand ({@link #lyreShown}, {@link #lyreInHand}), the visor sliding down over his eyes, and the
 * final pose with the Lyre and a snake-eye glint.
 */
public final class ViperionSequenceClient {
	private static final Vector3f TEAL = new Vector3f(0.18f, 0.72f, 0.69f);
	private static final Vector3f AQUA = new Vector3f(0.55f, 1.0f, 0.92f);
	private static final Vector3f GREEN = new Vector3f(0.3f, 0.75f, 0.35f);
	private static final Vector3f SLIT = new Vector3f(0.95f, 0.9f, 0.3f);
	private static final int GLOW = 0x2FB7B0;
	private static final int CORE = 0xC8FFF4;

	/** Pose keys: head x, right arm x / z, left arm x / z, right leg x / z, left leg x / z, body x. */
	private static final float[] STAND = { 0f, 0f, 0.1f, 0f, -0.1f, 0f, 0.05f, 0f, -0.05f, 0f };
	/** The right wrist up with the Bracelet, the left hand to it. */
	private static final float[] WRIST_P = { 0.25f, -1.5f, 0.1f, -1.3f, 0.5f, 0f, 0.05f, 0f, -0.05f, 0f };
	/** The right arm raised up and out: the snake coils up it. */
	private static final float[] ARM_UP = { -0.4f, -2.6f, 0.6f, -0.2f, -0.7f, 0f, 0.05f, 0f, -0.05f, 0f };
	/** Arms a little out while it winds round him. */
	private static final float[] COILED = { -0.3f, -0.25f, 0.75f, -0.25f, -0.75f, 0f, 0.04f, 0f, -0.04f, 0f };
	private static final float[] SCALES_P = { -0.1f, 0.1f, 0.35f, 0.1f, -0.35f, 0f, 0.05f, 0f, -0.05f, -0.05f };
	/** The Lyre out in the right hand, the left ready to strum. */
	private static final float[] LYRE_P = { 0f, -1.2f, 0.15f, -1.1f, 0.4f, -0.1f, 0.06f, 0.05f, -0.05f, 0f };
	/** The left hand up at the brow, drawing the visor down. */
	private static final float[] VISOR_P = { 0.15f, -1.2f, 0.15f, -2.55f, 0.55f, -0.1f, 0.06f, 0.05f, -0.05f, 0f };
	/** The Lyre held out to the side, weight on one leg. */
	private static final float[] POSE_P = { 0.05f, -1.6f, 0.95f, -0.3f, -0.4f, -0.2f, 0.1f, 0.2f, -0.05f, 0f };

	private static final int[] AT = { 0, 6, COIL, COIL + 30, SCALES, LYRE, VISOR, POSE };
	private static final int[] BL = { 6, 8, 10, 8, 6, 6, 4, 1 };
	private static final float[][] KEYS = { STAND, WRIST_P, ARM_UP, COILED, SCALES_P, LYRE_P, VISOR_P, POSE_P };

	public static final MiraculousSequenceClient.Scene SCENE = new MiraculousSequenceClient.Scene() {
		@Override
		public float[] pose(Player p, MiraculousState s, float t) {
			float[] pose = MiraculousSequenceClient.keyed(t, AT, BL, KEYS);
			if (t >= VISOR && t < POSE) {
				// the hand draws down from the brow to the eyes with the visor
				pose = pose.clone();
				pose[3] = Mth.lerp(MiraculousSequenceClient.smooth((t - VISOR) / (VISOR_END - VISOR)), -2.55f, -2.0f);
			}
			return pose;
		}

		@Override
		public void extraPose(Player p, HumanoidModel<?> model, float t, float w) {
			float k = w * MiraculousSequenceClient.smooth((t - POSE) / 6f);
			if (k > 0f) {
				model.head.yRot = Mth.lerp(k, model.head.yRot, 0.3f);
			}
		}

		@Override
		public void fx(ClientLevel level, Player p, MiraculousState s, int t, float yaw) {
			ViperionSequenceClient.fx(level, p, t, yaw);
		}

		@Override
		public float[][] cam() {
			return CAM;
		}

		@Override
		public void world(WorldRenderContext ctx, Player p, MiraculousState s, float t, float partial) {
			ViperionSequenceClient.world(ctx, p, s, t);
		}
	};

	private ViperionSequenceClient() {
	}

	/** Whether the Lyre shows at all: not in the long transformation until it forms on its beat. */
	public static boolean lyreShown(Player player, float partial) {
		float t = MiraculousSequenceClient.age(player, Kwami.SASS, partial);
		return t < 0f || t >= LYRE;
	}

	/** The Lyre is in his hand from its beat to the end (null = not playing: his own rule). */
	public static Boolean lyreInHand(Player player, float partial) {
		float t = MiraculousSequenceClient.age(player, Kwami.SASS, partial);
		return t < 0f ? null : t >= LYRE;
	}

	/** The snake's route at {@code u} 0..1: up the raised arm from the Bracelet to the shoulder, then round and down him. */
	private static Vec3 route(Vec3 feet, double h, Vec3 fwd, Vec3 right, double u) {
		Vec3 wrist = feet.add(0, h * 1.22, 0).add(right.scale(0.72)).add(fwd.scale(0.05));
		Vec3 shoulder = feet.add(0, h * 0.76, 0).add(right.scale(0.34));
		if (u < 0.25) {
			double f = u / 0.25;
			Vec3 axis = shoulder.subtract(wrist).normalize();
			Vec3 a = axis.cross(new Vec3(0, 1, 0)).normalize();
			Vec3 b = axis.cross(a).normalize();
			double ang = f * Math.PI * 5;
			return wrist.lerp(shoulder, f).add(a.scale(Math.cos(ang) * 0.13)).add(b.scale(Math.sin(ang) * 0.13));
		}
		double f = (u - 0.25) / 0.75;
		double ang0 = Math.atan2(shoulder.z - feet.z, shoulder.x - feet.x);
		double ang = ang0 + f * Math.PI * 2 * 2.5;
		double y = h * 0.76 - f * (h * 0.7);
		double r = 0.4 + 0.12 * f;
		return feet.add(Math.cos(ang) * r, y, Math.sin(ang) * r);
	}

	// ---------------------------------------------------------------- the light show

	private static void fx(ClientLevel level, Player p, int t, float yaw) {
		RandomSource rnd = level.random;
		double h = p.getBbHeight();
		Vec3 feet = p.position();
		Vec3 fwd = MiraculousSequenceClient.forward(yaw);
		Vec3 right = MiraculousSequenceClient.right(fwd);
		Vec3 wrist = feet.add(0, h * 0.72, 0).add(fwd.scale(0.6)).add(right.scale(0.3));
		DustParticleOptions teal = new DustParticleOptions(TEAL, 1.0f);
		DustParticleOptions aqua = new DustParticleOptions(AQUA, 0.7f);
		DustParticleOptions green = new DustParticleOptions(GREEN, 0.9f);
		DustParticleOptions slit = new DustParticleOptions(SLIT, 0.55f);
		if (t < BRACELET) {
			// Sass coils in: a tightening helix of teal round the wrist
			float k = t / (float) BRACELET;
			double a = t * 0.9;
			double r = 0.55 * (1 - k) + 0.06;
			Vec3 q = wrist.add(right.scale(Math.cos(a) * r)).add(0, Math.sin(a) * r + 0.3 * (1 - k), 0).add(fwd.scale(0.2 * (1 - k)));
			level.addParticle(teal, q.x, q.y, q.z, 0, 0, 0);
			level.addParticle(aqua, q.x, q.y, q.z, 0, 0, 0);
		} else if (t < COIL + 4) {
			if (t == BRACELET) {
				level.addParticle(ParticleTypes.FLASH, wrist.x, wrist.y, wrist.z, 0, 0, 0);
			}
			level.addParticle(t % 2 == 0 ? aqua : teal, wrist.x, wrist.y, wrist.z, (rnd.nextDouble() - 0.5) * 0.1, (rnd.nextDouble() - 0.5) * 0.1,
					(rnd.nextDouble() - 0.5) * 0.1);
		} else if (t < COIL_END) {
			// the snake's head: two little yellow eyes, and teal scales shed in its wake
			Vec3 head = route(feet, h, fwd, right, ViperionSequence.coil(t));
			level.addParticle(slit, head.x, head.y + 0.05, head.z, 0, 0, 0);
			if (t % 2 == 0) {
				level.addParticle(green, head.x + (rnd.nextDouble() - 0.5) * 0.1, head.y, head.z + (rnd.nextDouble() - 0.5) * 0.1, 0, -0.01, 0);
			}
		} else if (t >= SCALES && t < SCALES_END) {
			// the scale ripple: staggered rows of teal climbing the suit
			float k = (t - SCALES) / (float) (SCALES_END - SCALES);
			for (int row = 0; row < 2; row++) {
				double y = h * Mth.clamp(k * 1.2 - row * 0.15, 0, 1);
				for (int i = 0; i < 12; i++) {
					double a = (i + (row + t) % 2 * 0.5) * Math.PI * 2 / 12 + yaw * Mth.DEG_TO_RAD;
					level.addParticle(i % 2 == 0 ? teal : aqua, feet.x + Math.cos(a) * 0.36, feet.y + y, feet.z + Math.sin(a) * 0.36, 0, 0, 0);
				}
			}
		} else if (t >= LYRE && t < LYRE + 6) {
			// the Lyre forms out of teal light in his hand
			Vec3 hand = feet.add(0, h * 0.62, 0).add(fwd.scale(0.6)).add(right.scale(0.35));
			for (int i = 0; i < 6; i++) {
				level.addParticle(i % 2 == 0 ? aqua : teal, hand.x + (rnd.nextDouble() - 0.5) * 0.5, hand.y + (rnd.nextDouble() - 0.3) * 0.6,
						hand.z + (rnd.nextDouble() - 0.5) * 0.5, 0, 0.01, 0);
			}
			if (t == LYRE) {
				level.addParticle(ParticleTypes.NOTE, hand.x, hand.y + 0.5, hand.z, 0.45, 0, 0);
			}
		} else if (t == LYRE + 4 || t == LYRE + 8) {
			Vec3 hand = feet.add(0, h * 0.62, 0).add(fwd.scale(0.6)).add(right.scale(0.35));
			level.addParticle(ParticleTypes.NOTE, hand.x, hand.y + 0.5, hand.z, t == LYRE + 4 ? 0.55 : 0.7, 0, 0);
		}
		if (t >= POSE + 2 && t < POSE + 14) {
			// the snake eyes: upright yellow slits glinting in the visor
			Vec3 face = feet.add(0, h * 0.875, 0).add(fwd.scale(0.3));
			for (int side = -1; side <= 1; side += 2) {
				Vec3 eye = face.add(right.scale(0.11 * side));
				for (int k = -1; k <= 1; k++) {
					level.addParticle(slit, eye.x, eye.y + k * 0.04, eye.z, 0, 0, 0);
				}
			}
			if (t == POSE + 2) {
				for (int i = 0; i < 40; i++) {
					double a = rnd.nextDouble() * Math.PI * 2;
					double r = 0.3 + rnd.nextDouble() * 0.4;
					Vec3 dir = right.scale(Math.cos(a)).add(0, Math.sin(a), 0);
					Vec3 c = feet.add(0, h * 0.6, 0).subtract(fwd.scale(0.6));
					level.addParticle(i % 3 == 0 ? aqua : teal, c.x + dir.x * r, c.y + dir.y * r, c.z + dir.z * r, dir.x * 0.2, dir.y * 0.2, dir.z * 0.2);
				}
			}
		}
	}

	private static void world(WorldRenderContext ctx, Player p, MiraculousState s, float t) {
		Vec3 feet = MiraculousSequenceClient.origin(s, t);
		double h = p.getBbHeight();
		float yaw = MiraculousSequenceClient.yaw(s, t);
		Vec3 fwd = MiraculousSequenceClient.forward(yaw);
		Vec3 right = MiraculousSequenceClient.right(fwd);
		if (t >= COIL && t < COIL_END + 10) {
			// the translucent snake of light: a tapering body behind its head, winding up the arm and round him
			float head = ViperionSequence.coil(t);
			float fade = MiraculousSequenceClient.smooth((t - COIL) / 4f) * (1f - MiraculousSequenceClient.smooth((t - COIL_END) / 10f));
			float tail = Math.max(0f, head - 0.42f);
			int n = 40;
			Vec3[] body = new Vec3[n + 1];
			for (int i = 0; i <= n; i++) {
				body[i] = route(feet, h, fwd, right, Mth.lerp(i / (float) n, tail, head));
			}
			MiraculousSequenceClient.ribbon(ctx, body, 0.085f, GLOW, CORE, 0.7f * fade);
			// the front third a little thicker: the snake's neck and head
			Vec3[] neck = new Vec3[9];
			for (int i = 0; i <= 8; i++) {
				neck[i] = route(feet, h, fwd, right, Mth.lerp(i / 8f, Math.max(tail, head - 0.06f), head));
			}
			MiraculousSequenceClient.ribbon(ctx, neck, 0.13f, GLOW, CORE, 0.6f * fade);
		}
		if (t >= SCALES && t < SCALES_END + 4) {
			// the ripple's crest
			float k = Mth.clamp((t - SCALES) / (float) (SCALES_END - SCALES), 0f, 1f);
			float fade = 1f - MiraculousSequenceClient.smooth((t - SCALES_END) / 4f);
			MiraculousSequenceClient.ring(ctx, feet.add(0, h * k, 0), 0.3f, 0.5f, GLOW, 0.7f * fade);
		}
		if (t >= VISOR && t < VISOR_END + 4) {
			// the visor sliding down over his eyes: a band of teal light from the brow to the eyes
			float k = MiraculousSequenceClient.smooth((t - VISOR) / (VISOR_END - VISOR));
			float fade = 1f - MiraculousSequenceClient.smooth((t - VISOR_END) / 4f);
			Vec3 c = feet.add(0, h * (1.0 - 0.12 * k), 0).add(fwd.scale(0.29));
			Vec3[] band = { c.subtract(right.scale(0.27)), c, c.add(right.scale(0.27)) };
			MiraculousSequenceClient.ribbon(ctx, band, 0.05f, GLOW, CORE, 0.9f * fade);
		}
		if (t >= POSE + 2 && t < POSE + 12) {
			// the glint: a four-point star off one eye
			float k = (t - (POSE + 2)) / 10f;
			float r = 0.28f * Mth.sin(Mth.PI * k);
			Vec3 eye = feet.add(0, h * 0.875, 0).add(fwd.scale(0.36)).add(right.scale(0.11));
			MiraculousSequenceClient.ribbon(ctx, new Vec3[] { eye.add(0, r, 0), eye, eye.subtract(0, r, 0) }, 0.02f, 0xF2E870, 0xFFFFFF, 1f);
			MiraculousSequenceClient.ribbon(ctx, new Vec3[] { eye.add(right.scale(r)), eye, eye.subtract(right.scale(r)) }, 0.02f, 0xF2E870, 0xFFFFFF, 1f);
		}
	}

	// ---------------------------------------------------------------- the camera

	private static final float[][] CAM = {
			{ 0, 2.2f, 10, 1.5f, 1.4f },
			{ 14, 1.4f, 40, 1.3f, 1.2f },
			{ 22, 2.0f, 50, 1.6f, 1.6f },
			{ 36, 2.5f, 60, 1.8f, 1.9f },
			{ 60, 3.6f, 200, 1.2f, 1.0f },
			{ 90, 3.4f, 520, 1.3f, 1.1f },
			{ 104, 2.4f, 540, 0.8f, 1.0f },
			{ 116, 2.2f, 548, 1.2f, 1.1f },
			{ 130, 1.45f, 540, 1.72f, 1.7f },
			{ 144, 1.5f, 535, 1.72f, 1.7f },
			{ 152, 1.35f, 520, 1.72f, 1.7f },
			{ TOTAL, 3.4f, 505, 1.0f, 1.2f },
	};
}
