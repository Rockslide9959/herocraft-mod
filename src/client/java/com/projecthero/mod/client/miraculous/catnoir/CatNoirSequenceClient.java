package com.projecthero.mod.client.miraculous.catnoir;

import static com.projecthero.mod.miraculous.catnoir.CatNoirSequence.BATON;
import static com.projecthero.mod.miraculous.catnoir.CatNoirSequence.MASK;
import static com.projecthero.mod.miraculous.catnoir.CatNoirSequence.MASK_END;
import static com.projecthero.mod.miraculous.catnoir.CatNoirSequence.POSE;
import static com.projecthero.mod.miraculous.catnoir.CatNoirSequence.RING;
import static com.projecthero.mod.miraculous.catnoir.CatNoirSequence.SWEEP;
import static com.projecthero.mod.miraculous.catnoir.CatNoirSequence.TAIL;
import static com.projecthero.mod.miraculous.catnoir.CatNoirSequence.TOTAL;
import static com.projecthero.mod.miraculous.catnoir.CatNoirSequence.WAVE;
import static com.projecthero.mod.miraculous.catnoir.CatNoirSequence.WAVE_END;

import org.joml.Vector3f;

import com.projecthero.mod.client.miraculous.MiraculousSequenceClient;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.catnoir.CatNoirSequence;
import com.projecthero.mod.miraculous.data.MiraculousState;

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
 * v0.17.1: the client half of Cat Noir's long transformation ({@link CatNoirSequence}) on the shared frame
 * ({@link MiraculousSequenceClient}): Plagg spiralling into the Ring, the Ring's green paw print, the sweep of the hand
 * and the ripple of green-black energy running out from the Ring over him as the suit forms (his own texel order),
 * the slide of the hand across the eyes as the ears pop up, the belt-tail's whip, the Baton extending and twirling
 * ({@link #baton}), and the crouched cat pose with a flash of green slit eyes.
 */
public final class CatNoirSequenceClient {
	private static final Vector3f GREEN = new Vector3f(0.49f, 0.89f, 0.23f);
	private static final Vector3f LIME = new Vector3f(0.72f, 1.0f, 0.45f);
	private static final Vector3f BLACK = new Vector3f(0.03f, 0.03f, 0.04f);
	private static final int GLOW = 0x5ED62A;
	private static final int CORE = 0xD8FFB8;
	private static final int DARK = 0x0A140A;

	/** Pose keys: head x, right arm x / z, left arm x / z, right leg x / z, left leg x / z, body x. */
	private static final float[] STAND = { 0f, 0f, 0.1f, 0f, -0.1f, 0f, 0.05f, 0f, -0.05f, 0f };
	/** The ringed fist up before his face, watching Plagg go in. */
	private static final float[] FIST = { -0.1f, -2.05f, -0.35f, 0.1f, -0.2f, 0f, 0.05f, 0f, -0.05f, 0f };
	/** The sweep: from across the body ... */
	private static final float[] SWEEP_A = { 0f, -1.55f, -0.95f, -0.2f, -0.5f, 0f, 0.05f, 0f, -0.05f, 0f };
	/** ... out wide to the right. */
	private static final float[] SWEEP_B = { -0.1f, -1.35f, 1.45f, -0.3f, -0.9f, -0.1f, 0.08f, 0.05f, -0.05f, 0f };
	/** Arms out, head back: the wave runs over him. */
	private static final float[] WAVE_P = { -0.35f, -0.4f, 1.35f, -0.4f, -1.35f, -0.25f, 0.12f, 0.15f, -0.1f, -0.05f };
	/** Two fingers to the left of the eyes ... */
	private static final float[] MASK_A = { 0.05f, -2.2f, -0.6f, 0.15f, -0.45f, 0f, 0.06f, 0f, -0.06f, 0f };
	/** ... slid across to the right. */
	private static final float[] MASK_B = { 0.05f, -2.1f, 0.45f, 0.15f, -0.45f, 0f, 0.06f, 0f, -0.06f, 0f };
	/** A glance back as the tail whips, the hand on the hip. */
	private static final float[] TAIL_P = { -0.1f, 0.25f, 0.55f, -0.4f, -0.75f, 0f, 0.1f, 0.2f, -0.1f, 0.05f };
	/** The Baton out before him, the wrist working it. */
	private static final float[] BATON_P = { 0f, -1.35f, 0.25f, -0.55f, -0.85f, -0.2f, 0.1f, 0.2f, -0.1f, 0f };
	/** The crouched cat: one hand down to the ground with the Baton, the other clawed up, looking up out of it. */
	private static final float[] CROUCH = { -0.55f, -0.45f, 0.4f, -1.15f, -0.25f, -1.05f, 0.12f, 0.35f, -0.12f, 0.5f };

	private static final int[] AT = { 0, 4, SWEEP - 2, SWEEP + 8, WAVE + 6, MASK, MASK_END - 4, TAIL - 2, BATON, POSE };
	private static final int[] BL = { 4, 8, 6, 6, 8, 6, 12, 4, 6, 1 };
	private static final float[][] KEYS = { STAND, FIST, SWEEP_A, SWEEP_B, WAVE_P, MASK_A, MASK_B, TAIL_P, BATON_P, CROUCH };

	public static final MiraculousSequenceClient.Scene SCENE = new MiraculousSequenceClient.Scene() {
		@Override
		public float[] pose(Player p, MiraculousState s, float t) {
			float[] pose = MiraculousSequenceClient.keyed(t, AT, BL, KEYS);
			if (t >= BATON + 6 && t < POSE - 2) {
				pose = pose.clone();
				// the twirl: the wrist turns the spinning Baton round in big circles
				pose[1] += 0.3f * Mth.sin(t * 0.9f);
				pose[2] += 0.25f * Mth.cos(t * 0.9f);
			}
			return pose;
		}

		@Override
		public void extraPose(Player p, HumanoidModel<?> model, float t, float w) {
			if (t >= TAIL - 2 && t < BATON) {
				// he looks back over his shoulder at the tail
				float k = Mth.sin(Mth.PI * Mth.clamp((t - (TAIL - 2)) / (BATON - TAIL + 2f), 0f, 1f));
				model.head.yRot = Mth.lerp(w, model.head.yRot, 0.95f * k);
			}
			MiraculousSequenceClient.crouch(model, w * MiraculousSequenceClient.smooth((t - (POSE - 2)) / 8f));
		}

		@Override
		public void fx(ClientLevel level, Player p, MiraculousState s, int t, float yaw) {
			CatNoirSequenceClient.fx(level, p, t, yaw);
		}

		@Override
		public float[][] cam() {
			return CAM;
		}

		@Override
		public void world(WorldRenderContext ctx, Player p, MiraculousState s, float t, float partial) {
			CatNoirSequenceClient.world(ctx, p, s, t, partial);
		}
	};

	private CatNoirSequenceClient() {
	}

	/** The Baton during the sequence (null = not playing: his own rule). */
	public static CatNoirRender.Baton baton(Player player, float partial) {
		float t = MiraculousSequenceClient.age(player, Kwami.PLAGG, partial);
		if (t < 0f) {
			return null;
		}
		if (t < MASK_END) {
			return new CatNoirRender.Baton(CatNoirRender.BatonAt.NONE, 0f);
		}
		if (t < BATON) {
			return new CatNoirRender.Baton(CatNoirRender.BatonAt.BACK, 0f);
		}
		float e = CatNoirSequence.baton(t);
		if (t >= BATON + 8 && t < POSE - 2) {
			return new CatNoirRender.Baton(CatNoirRender.BatonAt.SPIN, 1f);
		}
		if (t >= POSE) {
			return new CatNoirRender.Baton(CatNoirRender.BatonAt.HAND, 0.55f);
		}
		return new CatNoirRender.Baton(CatNoirRender.BatonAt.HAND, Math.max(0.05f, e));
	}

	// ---------------------------------------------------------------- the light show

	private static void fx(ClientLevel level, Player p, int t, float yaw) {
		RandomSource rnd = level.random;
		double h = p.getBbHeight();
		Vec3 feet = p.position();
		Vec3 fwd = MiraculousSequenceClient.forward(yaw);
		Vec3 right = MiraculousSequenceClient.right(fwd);
		DustParticleOptions green = new DustParticleOptions(GREEN, 1.1f);
		DustParticleOptions lime = new DustParticleOptions(LIME, 0.7f);
		DustParticleOptions black = new DustParticleOptions(BLACK, 1.3f);
		// the Ring, on the raised fist before his face
		Vec3 ring = feet.add(0, h * 0.9, 0).add(fwd.scale(0.42)).add(right.scale(-0.12));
		if (t < RING) {
			// Plagg is sucked in, spinning: a tightening spiral of black and green
			float k = t / (float) RING;
			for (int i = 0; i < 3; i++) {
				double a = (t + i / 3.0) * 1.1;
				double r = 0.9 * (1 - k) + 0.04;
				Vec3 q = ring.add(right.scale(Math.cos(a) * r)).add(0, Math.sin(a) * r * 0.7 + 0.35 * (1 - k), 0).add(fwd.scale(0.3 * (1 - k)));
				level.addParticle(i == 0 ? black : green, q.x, q.y, q.z, 0, 0, 0);
			}
		} else if (t < SWEEP) {
			// the Ring turns black and its green paw print lights up
			if (t == RING) {
				level.addParticle(ParticleTypes.FLASH, ring.x, ring.y, ring.z, 0, 0, 0);
			}
			paw(level, ring.add(fwd.scale(0.12)), right, 0.11, t % 2 == 0 ? green : lime);
			level.addParticle(black, ring.x, ring.y, ring.z, 0, 0, 0);
		} else if (t >= MASK && t < MASK_END) {
			// the mask: green sparkles along the slide across the eyes; the ears pop up
			float k = (t - MASK) / (float) (MASK_END - MASK);
			Vec3 eyes = feet.add(0, h * 0.88, 0).add(fwd.scale(0.32)).add(right.scale(-0.3 + 0.6 * k));
			if (t % 2 == 0) {
				level.addParticle(lime, eyes.x, eyes.y, eyes.z, 0, 0.01, 0);
			}
			if (t == MASK + 4) {
				for (int side = -1; side <= 1; side += 2) {
					Vec3 ear = feet.add(0, h + 0.12, 0).add(right.scale(0.18 * side));
					for (int i = 0; i < 8; i++) {
						level.addParticle(i % 2 == 0 ? black : green, ear.x, ear.y, ear.z, (rnd.nextDouble() - 0.5) * 0.1, 0.06 + rnd.nextDouble() * 0.06,
								(rnd.nextDouble() - 0.5) * 0.1);
					}
				}
			}
		} else if (t >= TAIL && t < TAIL + 8) {
			// the belt-tail whips round behind him: a crack of green
			double a = (t - TAIL) / 8.0 * Math.PI;
			Vec3 tip = feet.add(0, h * 0.35 + 0.25 * Math.sin(a * 2), 0).subtract(fwd.scale(0.55)).add(right.scale(Math.cos(a) * 0.65));
			level.addParticle(green, tip.x, tip.y, tip.z, 0, 0, 0);
			level.addParticle(black, tip.x, tip.y, tip.z, 0, 0, 0);
			if (t == TAIL + 4) {
				level.addParticle(ParticleTypes.CRIT, tip.x, tip.y, tip.z, 0, 0.05, 0);
			}
		} else if (t >= BATON && t < POSE) {
			// sparks off the spinning Baton
			Vec3 hand = feet.add(0, h * 0.62, 0).add(fwd.scale(0.55)).add(right.scale(0.3));
			double a = t * 0.82;
			Vec3 tip = hand.add(right.scale(Math.cos(a) * 1.0)).add(0, Math.sin(a) * 1.0, 0);
			level.addParticle(t % 2 == 0 ? lime : green, tip.x, tip.y, tip.z, 0, 0, 0);
		} else if (t == POSE + 2) {
			// green slit eyes flash out of the crouch
			Vec3 face = feet.add(0, h * 0.62, 0).add(fwd.scale(0.55));
			level.addParticle(ParticleTypes.FLASH, face.x, face.y, face.z, 0, 0, 0);
			for (int i = 0; i < 70; i++) {
				double a = rnd.nextDouble() * Math.PI * 2;
				double r = 0.3 + rnd.nextDouble() * 0.5;
				Vec3 dir = right.scale(Math.cos(a)).add(0, Math.sin(a), 0);
				Vec3 c = face.subtract(fwd.scale(0.7));
				level.addParticle(i % 3 == 0 ? black : i % 2 == 0 ? lime : green, c.x + dir.x * r, c.y + dir.y * r, c.z + dir.z * r, dir.x * 0.22,
						dir.y * 0.22, dir.z * 0.22);
			}
		}
		if (t >= POSE + 2 && t < POSE + 16) {
			// the slits: two thin upright green lines where his eyes are
			Vec3 face = feet.add(0, h * 0.86 - 0.42, 0).add(fwd.scale(0.5));
			for (int side = -1; side <= 1; side += 2) {
				Vec3 eye = face.add(right.scale(0.1 * side));
				for (int k = -1; k <= 1; k++) {
					level.addParticle(lime, eye.x, eye.y + k * 0.045, eye.z, 0, 0, 0);
				}
			}
		}
		if (t >= SWEEP && t < WAVE_END + 2 && t % 2 == 0) {
			// black motes thrown off the wave
			float front = wave(t);
			double y = h * (0.78 + (rnd.nextBoolean() ? 1 : -1) * front * 0.8);
			double a = rnd.nextDouble() * Math.PI * 2;
			level.addParticle(black, feet.x + Math.cos(a) * 0.45, feet.y + Mth.clamp(y, 0, h), feet.z + Math.sin(a) * 0.45, Math.cos(a) * 0.03, 0.01,
					Math.sin(a) * 0.03);
		}
	}

	/** A paw print of dust in the plane facing forward: a pad and four toes. */
	private static void paw(ClientLevel level, Vec3 c, Vec3 right, double s, DustParticleOptions d) {
		level.addParticle(d, c.x, c.y - s * 0.3, c.z, 0, 0, 0);
		level.addParticle(d, c.x + right.x * s * 0.3, c.y - s * 0.35, c.z + right.z * s * 0.3, 0, 0, 0);
		level.addParticle(d, c.x - right.x * s * 0.3, c.y - s * 0.35, c.z - right.z * s * 0.3, 0, 0, 0);
		double[][] toes = { { -0.75, 0.45 }, { -0.28, 0.85 }, { 0.28, 0.85 }, { 0.75, 0.45 } };
		for (double[] toe : toes) {
			level.addParticle(d, c.x + right.x * s * toe[0], c.y + s * toe[1], c.z + right.z * s * toe[0], 0, 0, 0);
		}
	}

	/** How far the wave has run from the Ring, 0..1 (it leads the suit a little). */
	private static float wave(float t) {
		return MiraculousSequenceClient.smooth((t - SWEEP) / (float) (WAVE_END - SWEEP - 4));
	}

	private static void world(WorldRenderContext ctx, Player p, MiraculousState s, float t, float partial) {
		Vec3 feet = MiraculousSequenceClient.origin(s, t);
		double h = p.getBbHeight();
		float yaw = MiraculousSequenceClient.yaw(s, t);
		Vec3 fwd = MiraculousSequenceClient.forward(yaw);
		Vec3 right = MiraculousSequenceClient.right(fwd);
		if (t >= RING - 2 && t < SWEEP + 6) {
			// the black Ring's glow
			float a = MiraculousSequenceClient.smooth((t - (RING - 2)) / 4f) * (1f - MiraculousSequenceClient.smooth((t - SWEEP) / 6f));
			Vec3 ring = feet.add(0, h * 0.9, 0).add(fwd.scale(0.42)).add(right.scale(-0.12));
			Vec3[] halo = MiraculousSequenceClient.helix(ring.subtract(0, 0.0, 0), 0, 0, 0, Math.PI * 2, 0.09, 0.09, 16);
			MiraculousSequenceClient.ribbon(ctx, halo, 0.035f, GLOW, CORE, a);
		}
		if (t >= SWEEP && t < SWEEP + 10) {
			// the sweep: a slash of green light following the hand across
			float k = MiraculousSequenceClient.smooth((t - SWEEP) / 8f);
			Vec3 c = feet.add(0, h * 0.82, 0);
			int n = 10;
			Vec3[] arc = new Vec3[n + 1];
			for (int i = 0; i <= n; i++) {
				double a = Mth.lerp((float) (i / (double) n * k), -1.1f, 1.3f);
				arc[i] = c.add(fwd.scale(Math.cos(a) * 0.75)).add(right.scale(Math.sin(a) * 0.75));
			}
			MiraculousSequenceClient.ribbon(ctx, arc, 0.08f, GLOW, CORE, 1f - MiraculousSequenceClient.smooth((t - (SWEEP + 6)) / 4f));
		}
		if (t >= SWEEP + 2 && t < WAVE_END + 4) {
			// the ripple: a green-black ring running out from the Ring's height, up to the head and down to the boots
			float front = wave(t);
			float fade = 1f - MiraculousSequenceClient.smooth((t - WAVE_END) / 4f);
			double ringY = h * 0.78;
			double up = Math.min(h + 0.05, ringY + front * (h - ringY + 0.1) * 1.6);
			double down = Math.max(0.02, ringY - front * ringY * 1.1);
			for (double y : new double[] { up, down }) {
				Vec3 c = feet.add(0, y, 0);
				float ripple = 0.06f * Mth.sin(t * 0.9f);
				MiraculousSequenceClient.ring(ctx, c, 0.34f + ripple, 0.58f + ripple, GLOW, 0.75f * fade);
				MiraculousSequenceClient.ring(ctx, c.add(0, 0.05, 0), 0.4f, 0.5f, DARK, 0.6f * fade);
				MiraculousSequenceClient.band(ctx, c.subtract(0, y == up ? 0.25 : 0.0, 0), 0.42f, 0.25f, GLOW, 0.35f * fade);
			}
			// and up the arm from the Ring to the shoulder, early on
			if (front < 0.35f) {
				Vec3 hand = feet.add(0, h * 0.8, 0).add(right.scale(0.75));
				Vec3 shoulder = feet.add(0, h * 0.76, 0).add(right.scale(0.32));
				Vec3[] arm = { hand, hand.lerp(shoulder, 0.5).add(0, 0.05 * Mth.sin(t), 0), shoulder };
				MiraculousSequenceClient.ribbon(ctx, arm, 0.09f, GLOW, CORE, 1f - front / 0.35f);
			}
		}
	}

	// ---------------------------------------------------------------- the camera

	private static final float[][] CAM = {
			{ 0, 2.3f, 10, 1.55f, 1.5f },
			{ 16, 1.25f, 28, 1.62f, 1.62f },
			{ 28, 1.5f, 40, 1.6f, 1.55f },
			{ 38, 3.5f, 75, 1.1f, 1.0f },
			{ 68, 3.6f, 210, 1.4f, 1.1f },
			{ 96, 3.2f, 360, 1.5f, 1.3f },
			{ 102, 1.4f, 360, 1.72f, 1.68f },
			{ 118, 1.5f, 340, 1.7f, 1.65f },
			{ 122, 2.2f, 220, 1.0f, 0.8f },
			{ 128, 2.9f, 300, 1.2f, 1.0f },
			{ 146, 3.0f, 335, 1.0f, 0.9f },
			{ 152, 3.4f, 355, 0.25f, 0.7f },
			{ TOTAL, 3.8f, 362, 0.3f, 0.75f },
	};
}
