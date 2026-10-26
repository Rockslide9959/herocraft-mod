package com.projecthero.mod.client.miraculous.multimouse;

import static com.projecthero.mod.miraculous.multimouse.MultimouseSequence.COPIES;
import static com.projecthero.mod.miraculous.multimouse.MultimouseSequence.EARS;
import static com.projecthero.mod.miraculous.multimouse.MultimouseSequence.HOOD;
import static com.projecthero.mod.miraculous.multimouse.MultimouseSequence.HOOD_END;
import static com.projecthero.mod.miraculous.multimouse.MultimouseSequence.MERGE;
import static com.projecthero.mod.miraculous.multimouse.MultimouseSequence.MERGE_END;
import static com.projecthero.mod.miraculous.multimouse.MultimouseSequence.MERGE_STEP;
import static com.projecthero.mod.miraculous.multimouse.MultimouseSequence.PENDANT;
import static com.projecthero.mod.miraculous.multimouse.MultimouseSequence.POSE;
import static com.projecthero.mod.miraculous.multimouse.MultimouseSequence.ROPE;
import static com.projecthero.mod.miraculous.multimouse.MultimouseSequence.SKIP;
import static com.projecthero.mod.miraculous.multimouse.MultimouseSequence.SPLIT;
import static com.projecthero.mod.miraculous.multimouse.MultimouseSequence.TOTAL;
import static com.projecthero.mod.miraculous.multimouse.MultimouseSequence.WIND;
import static com.projecthero.mod.miraculous.multimouse.MultimouseSequence.WIND_END;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.client.miraculous.MiraculousSequenceClient;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.data.MiraculousState;
import com.projecthero.mod.miraculous.multimouse.MultimouseSequence;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: the client half of Multimouse's long transformation ({@link MultimouseSequence}) on the shared frame
 * ({@link MiraculousSequenceClient}): Mullo zipping into the Mouse Pendant and a pink flash glinting on it; a ring of
 * small pink-lit copies of her (drawn like her Multitude minis: her own skin and the suit) spinning round her and
 * converging back one by one, the suit forming out from the pendant with each; the hood rising over her head
 * ({@link #hood}) and the ears popping up with a squeak-sparkle; the jump rope off her belt, skipped once round her
 * body and wound back so the tail swings out ({@link #extras}); and the final pose -- a peace sign, a hand on her hip, a
 * pink star-glint.
 */
public final class MultimouseSequenceClient {
	private static final Vector3f PINK = new Vector3f(0.84f, 0.31f, 0.6f);
	private static final Vector3f PINK_L = new Vector3f(0.98f, 0.62f, 0.82f);
	private static final Vector3f SILVER = new Vector3f(0.86f, 0.88f, 0.94f);
	private static final Vector3f GREY = new Vector3f(0.55f, 0.57f, 0.64f);
	private static final int GLOW = 0xE0559E;
	private static final int CORE = 0xFFE4F2;
	private static final int ROPE_C = 0xD64E98;
	private static final int ROPE_CORE = 0xF8A8D4;
	private static final int FULL_BRIGHT = 0xF000F0;

	/** Pose keys: head x, right arm x / z, left arm x / z, right leg x / z, left leg x / z, body x. */
	private static final float[] STAND = { 0f, 0f, 0.1f, 0f, -0.1f, 0f, 0.05f, 0f, -0.05f, 0f };
	/** Both hands up to the pendant at her collar, looking down at it. */
	private static final float[] PENDANT_P = { 0.45f, -1.45f, -0.4f, -1.45f, 0.4f, 0f, 0.05f, 0f, -0.05f, 0f };
	/** Flung wide as she splits. */
	private static final float[] SPLIT_P = { -0.25f, -0.2f, 1.45f, -0.2f, -1.45f, 0f, 0.25f, 0f, -0.25f, -0.05f };
	/** Arms out while the ring spins round her. */
	private static final float[] RING_P = { -0.1f, -0.1f, 1.05f, -0.1f, -1.05f, 0f, 0.15f, 0f, -0.15f, 0f };
	/** Drawing them back in: hands to the chest. */
	private static final float[] MERGE_P = { 0.15f, -1.1f, -0.25f, -1.1f, 0.25f, 0f, 0.05f, 0f, -0.05f, 0f };
	/** Both hands up behind her head, pulling the hood up. */
	private static final float[] HOOD_P = { 0.2f, -2.85f, -0.35f, -2.85f, 0.35f, 0f, 0.05f, 0f, -0.05f, 0f };
	/** The ears pop: hands down a little out, chin up. */
	private static final float[] EARS_P = { -0.2f, -0.3f, 0.45f, -0.3f, -0.45f, 0f, 0.05f, 0f, -0.05f, 0f };
	/** Hands to the belt for the rope. */
	private static final float[] ROPE_P = { 0.15f, -0.55f, 0.05f, -0.55f, -0.05f, 0f, 0.05f, 0f, -0.05f, 0f };
	/** The handles out at her sides for the skip. */
	private static final float[] SKIP_P = { 0f, -0.3f, 0.9f, -0.3f, -0.9f, 0f, 0.03f, 0f, -0.03f, 0f };
	/** Winding it back to the belt. */
	private static final float[] WIND_P = { 0.15f, -0.65f, -0.15f, -0.65f, 0.15f, 0f, 0.05f, 0f, -0.05f, 0f };
	/** The peace sign up by her face (right), the left hand on her hip, weight on one leg. */
	private static final float[] POSE_P = { 0.05f, -2.5f, 0.2f, 0.25f, -0.55f, -0.12f, 0.08f, 0.1f, -0.15f, 0f };

	private static final int[] AT = { 0, 4, SPLIT, SPLIT + 12, MERGE, HOOD, EARS, ROPE, SKIP, WIND, POSE };
	private static final int[] BL = { 4, 5, 8, 8, 6, 5, 6, 5, 5, 5, 1 };
	private static final float[][] KEYS = { STAND, PENDANT_P, SPLIT_P, RING_P, MERGE_P, HOOD_P, EARS_P, ROPE_P, SKIP_P, WIND_P, POSE_P };

	private static PlayerModel<LivingEntity> wideCopy;
	private static PlayerModel<LivingEntity> slimCopy;

	public static final MiraculousSequenceClient.Scene SCENE = new MiraculousSequenceClient.Scene() {
		@Override
		public float[] pose(Player p, MiraculousState s, float t) {
			float[] pose = MiraculousSequenceClient.keyed(t, AT, BL, KEYS);
			if (t >= SKIP && t < WIND) {
				// the skip: the hands turn the rope round, a little hop as it passes under her feet
				pose = pose.clone();
				float phi = skipAngle(t);
				pose[1] += -0.35f * Mth.sin(phi);
				pose[3] += -0.35f * Mth.sin(phi);
				float hop = Math.max(0f, -Mth.sin(phi));
				pose[5] += 0.45f * hop;
				pose[7] += 0.45f * hop;
			}
			return pose;
		}

		@Override
		public void extraPose(Player p, HumanoidModel<?> model, float t, float w) {
			float k = w * MiraculousSequenceClient.smooth((t - POSE) / 6f);
			if (k > 0f) {
				// a tilt of the head toward the peace sign
				model.head.yRot = Mth.lerp(k, model.head.yRot, 0.2f);
				model.head.zRot = Mth.lerp(k, model.head.zRot, 0.14f);
			}
			float e = w * (MiraculousSequenceClient.smooth((t - EARS) / 4f) - MiraculousSequenceClient.smooth((t - (EARS + 8)) / 4f));
			if (e > 0f) {
				model.head.zRot = Mth.lerp(e, model.head.zRot, -0.12f);
			}
		}

		@Override
		public void fx(ClientLevel level, Player p, MiraculousState s, int t, float yaw) {
			MultimouseSequenceClient.fx(level, p, s, t, yaw);
		}

		@Override
		public float[][] cam() {
			return CAM;
		}

		@Override
		public void world(WorldRenderContext ctx, Player p, MiraculousState s, float t, float partial) {
			MultimouseSequenceClient.world(ctx, p, s, t);
		}
	};

	private MultimouseSequenceClient() {
	}

	// ---------------------------------------------------------------- hooks for her renderer

	/** How far up the hood is: 1 outside the long transformation (the suit's own rule then). */
	public static float hood(Player player, float partial) {
		float t = MiraculousSequenceClient.age(player, Kwami.MULLO, partial);
		return t < 0f ? 1f : MultimouseSequence.hood(t);
	}

	/**
	 * The extras' timing in the long transformation -- {ears' size, belt on (1) or off (0), tail length, tail kick} -- or
	 * null when it isn't playing.
	 */
	public static float[] extras(Player player, float partial) {
		float t = MiraculousSequenceClient.age(player, Kwami.MULLO, partial);
		if (t < 0f) {
			return null;
		}
		float ears = 0f;
		if (t >= EARS) {
			float u = t - EARS;
			// a pop: up past full size and back, wobbling once
			ears = MiraculousSequenceClient.smooth(u / 3f) * (1f + 0.35f * Mth.sin(u * 0.9f) * (float) Math.exp(-u / 4f));
		}
		boolean belt = t >= MERGE_END - 4 && (t < ROPE || t >= WIND_END);
		float tail = 0f;
		float kick = 0f;
		if (t >= WIND_END) {
			float u = t - WIND_END;
			tail = MiraculousSequenceClient.smooth(u / 5f);
			kick = 0.9f * Mth.sin(u * 0.7f) * (float) Math.exp(-u / 7f);
		}
		return new float[] { ears, belt ? 1f : 0f, tail, kick };
	}

	// ---------------------------------------------------------------- geometry

	/** The rope's turn in the skip: hanging (-90 deg) round behind, over, in front, under her and back to hanging. */
	private static float skipAngle(float t) {
		return -Mth.HALF_PI + Mth.TWO_PI * MiraculousSequenceClient.smooth((t - SKIP) / (WIND - SKIP));
	}

	/** The pendant at her collar. */
	private static Vec3 pendant(Vec3 feet, double h, Vec3 fwd) {
		return feet.add(0, h * 0.745, 0).add(fwd.scale(0.17));
	}

	/**
	 * Where a hand is for arm keys {@code xr} / {@code zr}: x swings it forward first (negative = forward / up), then z
	 * takes it out to the side (positive = out to her right for either arm, as the pose keys are).
	 */
	private static Vec3 hand(Vec3 feet, double h, Vec3 fwd, Vec3 right, boolean rightArm, float xr, float zr) {
		Vec3 shoulder = feet.add(0, h * 0.764, 0).add(right.scale(rightArm ? 0.31 : -0.31));
		double u = -Math.cos(xr);
		double f = -Math.sin(xr);
		double side = -u * Math.sin(zr);
		double up = u * Math.cos(zr);
		return shoulder.add(right.scale(side * 0.62)).add(0, up * 0.62, 0).add(fwd.scale(f * 0.62));
	}

	private static Vec3[] hands(Vec3 feet, double h, Vec3 fwd, Vec3 right, float[] pose) {
		return new Vec3[] { hand(feet, h, fwd, right, true, pose[1], pose[2]), hand(feet, h, fwd, right, false, pose[3], pose[4]) };
	}

	/** The skipping rope from the left hand to the right, bulged {@code len} in the direction of {@code phi}. */
	private static Vec3[] rope(Vec3 l, Vec3 r, Vec3 fwd, float phi, double up, double down, double depth) {
		int n = 24;
		Vec3[] out = new Vec3[n + 1];
		double s = Math.sin(phi);
		double vy = s >= 0 ? s * up : s * down;
		Vec3 dir = fwd.scale(-Math.cos(phi) * depth).add(0, vy, 0);
		for (int i = 0; i <= n; i++) {
			double k = i / (double) n;
			out[i] = l.lerp(r, k).add(dir.scale(Math.sin(Math.PI * k)));
		}
		return out;
	}

	/** Copy {@code i}'s place and size at {@code t}, or null when it isn't out: {x, y, z, scale, facing yaw}. */
	private static double[] copy(Vec3 feet, double h, int i, float t, float yaw0) {
		if (t < SPLIT || t >= MERGE + (i + 1) * MERGE_STEP) {
			return null;
		}
		float out = MiraculousSequenceClient.smooth((t - SPLIT) / 10f);
		float home = MERGE + (i + 1) * MERGE_STEP;
		float in = MiraculousSequenceClient.smooth((t - (home - 7f)) / 7f);
		double u = t - SPLIT;
		double ang = i * Math.PI * 2 / COPIES + 0.11 * u + 0.0012 * u * u + yaw0 * Mth.DEG_TO_RAD;
		double r = 1.45 * out * (1 - in);
		double y = h * 0.18 + 0.08 * Math.sin(t * 0.3 + i) + in * (h * 0.745 - h * 0.18 - 0.15);
		double scale = 0.34 * out * (1 - 0.75 * in);
		// running round her, facing along the ring
		float face = (float) Math.toDegrees(ang);
		return new double[] { feet.x + Math.cos(ang) * r, feet.y + y, feet.z + Math.sin(ang) * r, scale, face };
	}

	// ---------------------------------------------------------------- the light show

	private static void fx(ClientLevel level, Player p, MiraculousState s, int t, float yaw) {
		RandomSource rnd = level.random;
		double h = p.getBbHeight();
		Vec3 feet = p.position();
		Vec3 fwd = MiraculousSequenceClient.forward(yaw);
		Vec3 right = MiraculousSequenceClient.right(fwd);
		Vec3 pend = pendant(feet, h, fwd);
		DustParticleOptions pink = new DustParticleOptions(PINK, 1.0f);
		DustParticleOptions pinkL = new DustParticleOptions(PINK_L, 0.7f);
		DustParticleOptions silver = new DustParticleOptions(SILVER, 0.6f);
		DustParticleOptions grey = new DustParticleOptions(GREY, 0.8f);
		if (t < PENDANT) {
			// Mullo zips in: a tightening spiral of pink round her, into the pendant
			float k = t / (float) PENDANT;
			double a = t * 1.1;
			double r = 1.1 * (1 - k) + 0.04;
			Vec3 q = pend.add(right.scale(Math.cos(a) * r)).add(fwd.scale(Math.sin(a) * r)).add(0, 0.5 * (1 - k), 0);
			level.addParticle(pink, q.x, q.y, q.z, 0, 0, 0);
			level.addParticle(silver, q.x, q.y, q.z, 0, 0, 0);
		} else if (t < SPLIT) {
			if (t == PENDANT) {
				level.addParticle(ParticleTypes.FLASH, pend.x, pend.y, pend.z, 0, 0, 0);
				for (int i = 0; i < 14; i++) {
					level.addParticle(i % 2 == 0 ? pink : pinkL, pend.x, pend.y, pend.z, (rnd.nextDouble() - 0.5) * 0.25, (rnd.nextDouble() - 0.5) * 0.25,
							(rnd.nextDouble() - 0.5) * 0.25);
				}
			}
			level.addParticle(pinkL, pend.x, pend.y, pend.z, (rnd.nextDouble() - 0.5) * 0.06, (rnd.nextDouble() - 0.5) * 0.06, (rnd.nextDouble() - 0.5) * 0.06);
		} else if (t < MERGE_END) {
			if (t == SPLIT) {
				// she splits: a burst out to where the copies stand
				for (int i = 0; i < 32; i++) {
					double a = i * Math.PI * 2 / 32;
					level.addParticle(i % 2 == 0 ? pink : silver, feet.x, feet.y + h * 0.45, feet.z, Math.cos(a) * 0.22, 0.02, Math.sin(a) * 0.22);
				}
			}
			for (int i = 0; i < COPIES; i++) {
				double[] c = copy(feet, h, i, t, MiraculousSequenceClient.yaw(s, SPLIT));
				if (c != null && (t + i) % 2 == 0) {
					level.addParticle(i % 2 == 0 ? pinkL : grey, c[0], c[1] + 0.05, c[2], 0, 0.005, 0);
				}
			}
			if (t >= MERGE && (t - MERGE) % MERGE_STEP == MERGE_STEP - 1) {
				// a copy merges back: a pop of pink at the pendant, the suit stepping out from it
				for (int i = 0; i < 10; i++) {
					level.addParticle(i % 2 == 0 ? pink : pinkL, pend.x, pend.y, pend.z, (rnd.nextDouble() - 0.5) * 0.2, (rnd.nextDouble() - 0.5) * 0.2,
							(rnd.nextDouble() - 0.5) * 0.2);
				}
			}
		} else if (t >= HOOD && t < HOOD_END) {
			Vec3 crest = feet.add(0, h * (0.8 + 0.2 * MultimouseSequence.hood(t)), 0).subtract(fwd.scale(0.25 * (1 - MultimouseSequence.hood(t))));
			level.addParticle(silver, crest.x + (rnd.nextDouble() - 0.5) * 0.5, crest.y, crest.z + (rnd.nextDouble() - 0.5) * 0.5, 0, 0.01, 0);
		} else if (t >= EARS && t < EARS + 6) {
			// the squeak-sparkle off each ear
			for (int side = -1; side <= 1; side += 2) {
				Vec3 ear = feet.add(0, h * 0.833 + 0.7, 0).add(right.scale(0.24 * side));
				for (int i = 0; i < 3; i++) {
					level.addParticle(i == 0 ? silver : pinkL, ear.x, ear.y, ear.z, right.x * side * 0.06 + (rnd.nextDouble() - 0.5) * 0.08,
							0.04 + rnd.nextDouble() * 0.05, right.z * side * 0.06 + (rnd.nextDouble() - 0.5) * 0.08);
				}
				if (t == EARS + 1) {
					level.addParticle(ParticleTypes.END_ROD, ear.x, ear.y + 0.1, ear.z, right.x * side * 0.04, 0.05, right.z * side * 0.04);
				}
			}
		} else if (t >= SKIP && t < WIND && t % 2 == 0) {
			Vec3 waist = feet.add(0, h * 0.5, 0);
			level.addParticle(pinkL, waist.x + (rnd.nextDouble() - 0.5) * 1.6, waist.y + (rnd.nextDouble() - 0.5) * 1.8, waist.z + (rnd.nextDouble() - 0.5) * 0.4,
					0, 0, 0);
		} else if (t == WIND_END) {
			Vec3 knot = feet.add(0, h * 0.45, 0).subtract(fwd.scale(0.25));
			for (int i = 0; i < 12; i++) {
				level.addParticle(i % 2 == 0 ? pink : pinkL, knot.x, knot.y, knot.z, -fwd.x * 0.12 + (rnd.nextDouble() - 0.5) * 0.1,
						(rnd.nextDouble() - 0.6) * 0.1, -fwd.z * 0.12 + (rnd.nextDouble() - 0.5) * 0.1);
			}
		}
		if (t == POSE + 2) {
			// the pose: a ring of pink and silver behind her
			for (int i = 0; i < 40; i++) {
				double a = rnd.nextDouble() * Math.PI * 2;
				double r = 0.3 + rnd.nextDouble() * 0.4;
				Vec3 dir = right.scale(Math.cos(a)).add(0, Math.sin(a), 0);
				Vec3 c = feet.add(0, h * 0.6, 0).subtract(fwd.scale(0.6));
				level.addParticle(i % 3 == 0 ? silver : pink, c.x + dir.x * r, c.y + dir.y * r, c.z + dir.z * r, dir.x * 0.2, dir.y * 0.2, dir.z * 0.2);
			}
		}
	}

	/** A four-point star at {@code c}, {@code r} across. */
	private static void star(WorldRenderContext ctx, Vec3 c, Vec3 right, float r, float alpha) {
		if (r <= 0.002f) {
			return;
		}
		MiraculousSequenceClient.ribbon(ctx, new Vec3[] { c.add(0, r, 0), c, c.subtract(0, r, 0) }, 0.022f, GLOW, 0xFFFFFF, alpha);
		MiraculousSequenceClient.ribbon(ctx, new Vec3[] { c.add(right.scale(r)), c, c.subtract(right.scale(r)) }, 0.022f, GLOW, 0xFFFFFF, alpha);
	}

	private static void world(WorldRenderContext ctx, Player p, MiraculousState s, float t) {
		Vec3 feet = MiraculousSequenceClient.origin(s, t);
		double h = p.getBbHeight();
		float yaw = MiraculousSequenceClient.yaw(s, t);
		Vec3 fwd = MiraculousSequenceClient.forward(yaw);
		Vec3 right = MiraculousSequenceClient.right(fwd);
		Vec3 pend = pendant(feet, h, fwd);
		if (t < PENDANT + 1) {
			// Mullo's streak as she zips round and into the pendant
			Vec3[] trail = new Vec3[9];
			for (int i = 0; i < trail.length; i++) {
				float u = Math.max(0f, t - i * 0.6f);
				float k = Math.min(1f, u / PENDANT);
				double a = u * 1.1;
				double r = 1.1 * (1 - k) + 0.04;
				trail[i] = pend.add(right.scale(Math.cos(a) * r)).add(fwd.scale(Math.sin(a) * r)).add(0, 0.5 * (1 - k), 0);
			}
			MiraculousSequenceClient.ribbon(ctx, trail, 0.05f, GLOW, CORE, 0.8f * (1f - MiraculousSequenceClient.smooth((t - PENDANT + 1) / 2f)));
		}
		if (t >= PENDANT && t < PENDANT + 10) {
			// the flash glinting on the pendant
			float k = (t - PENDANT) / 10f;
			star(ctx, pend.add(fwd.scale(0.04)), right, 0.34f * Mth.sin(Mth.PI * k), 1f);
		}
		if (t >= SPLIT && t < MERGE_END) {
			// the ring of copies, each on a little pink halo
			float yaw0 = MiraculousSequenceClient.yaw(s, SPLIT);
			for (int i = 0; i < COPIES; i++) {
				double[] c = copy(feet, h, i, t, yaw0);
				if (c == null) {
					continue;
				}
				Vec3 at = new Vec3(c[0], c[1], c[2]);
				renderCopy(ctx, p, at, (float) c[3], (float) c[4], t + i * 3f);
				MiraculousSequenceClient.ring(ctx, at.add(0, 0.02, 0), 0.04f, (float) (0.75 * c[3]), GLOW, 0.55f);
			}
			// the ring's path, faint
			float fade = MiraculousSequenceClient.smooth((t - SPLIT) / 8f) * (1f - MiraculousSequenceClient.smooth((t - MERGE) / 10f));
			MiraculousSequenceClient.ring(ctx, feet.add(0, h * 0.18, 0), 1.38f, 1.52f, GLOW, 0.35f * fade);
		}
		if (t >= HOOD && t < HOOD_END + 4) {
			// the hood's edge: an arc of light rising from the back of her neck up over her head
			float k = MultimouseSequence.hood(Math.min(t, HOOD_END));
			float fade = 1f - MiraculousSequenceClient.smooth((t - HOOD_END) / 4f);
			Vec3 neck = feet.add(0, h * 0.833, 0);
			double a0 = Math.atan2(0.29, 0.53);
			double a = a0 - 2.1 * (1 - k);
			Vec3 edge = neck.add(0, Math.cos(a) * 0.62, 0).add(fwd.scale(Math.sin(a) * 0.62));
			Vec3[] arc = new Vec3[9];
			for (int i = 0; i < arc.length; i++) {
				double sx = -1 + 2 * i / (double) (arc.length - 1);
				arc[i] = edge.add(right.scale(0.3 * sx)).subtract(0, 0.14 * sx * sx, 0);
			}
			MiraculousSequenceClient.ribbon(ctx, arc, 0.05f, 0xC4C8D4, CORE, 0.85f * fade);
		}
		if (t >= EARS && t < EARS + 9) {
			// the squeak-sparkle: a little star off each ear
			float k = (t - EARS) / 9f;
			for (int side = -1; side <= 1; side += 2) {
				Vec3 ear = feet.add(0, h * 0.833 + 0.78, 0).add(right.scale(0.34 * side)).add(fwd.scale(0.05));
				star(ctx, ear, right, 0.18f * Mth.sin(Mth.PI * k), 1f);
			}
		}
		float[] pose = SCENE.pose(p, s, Math.min(t, TOTAL));
		if (t >= ROPE && t < WIND_END) {
			Vec3[] hd = hands(feet, h, fwd, right, pose);
			Vec3 rh = hd[0];
			Vec3 lh = hd[1];
			if (t < SKIP) {
				// it unspools off the belt: hanging lower between her hands as they draw it out
				float k = MiraculousSequenceClient.smooth((t - ROPE) / (SKIP - ROPE));
				Vec3[] r = rope(lh, rh, fwd, -Mth.HALF_PI, 1.25, 0.25 + 1.0 * k, 0.75);
				MiraculousSequenceClient.ribbon(ctx, r, 0.035f, ROPE_C, ROPE_CORE, 1f);
			} else if (t < WIND) {
				// the skip, with a faint arc ribbon where the rope has just swept
				float phi = skipAngle(t);
				for (int j = 3; j >= 0; j--) {
					Vec3[] r = rope(lh, rh, fwd, phi - 0.22f * j, 1.25, 1.25, 0.75);
					MiraculousSequenceClient.ribbon(ctx, r, j == 0 ? 0.035f : 0.06f, ROPE_C, j == 0 ? ROPE_CORE : CORE, j == 0 ? 1f : 0.35f - 0.08f * j);
				}
			} else {
				// wound back: a coil drawing in round her waist from her hands
				float k = MiraculousSequenceClient.smooth((t - WIND) / (WIND_END - WIND));
				Vec3 waist = feet.add(0, h * 0.47, 0);
				Vec3[] coil = MiraculousSequenceClient.helix(waist, 0.32 * (1 - k), 0.0, 0, Math.PI * 2 * (2.0 - 1.5 * k), 0.62 - 0.3 * k, 0.32, 28);
				MiraculousSequenceClient.ribbon(ctx, coil, 0.035f, ROPE_C, ROPE_CORE, 1f);
			}
			if (t < WIND) {
				handleGlow(ctx, rh);
				handleGlow(ctx, lh);
			}
		}
		if (t >= POSE && t < TOTAL + MiraculousSequenceClient.EASE_OUT) {
			// the peace sign: two fingers of light up from her raised hand, and the pink star-glint
			Vec3 hand = hand(feet, h, fwd, right, true, pose[1], pose[2]);
			float in = MiraculousSequenceClient.smooth((t - POSE) / 4f) * (1f - MiraculousSequenceClient.smooth((t - TOTAL) / MiraculousSequenceClient.EASE_OUT));
			Vec3 base = hand.subtract(0, 0.04, 0).add(fwd.scale(0.04));
			for (int side = -1; side <= 1; side += 2) {
				Vec3 tip = base.add(0, 0.2, 0).add(right.scale(0.06 * side));
				MiraculousSequenceClient.ribbon(ctx, new Vec3[] { base, tip }, 0.022f, GLOW, CORE, 0.8f * in);
			}
			if (t >= POSE + 3 && t < POSE + 14) {
				float k = (t - (POSE + 3)) / 11f;
				star(ctx, base.add(0, 0.24, 0).add(right.scale(0.09)).add(fwd.scale(0.05)), right, 0.3f * Mth.sin(Mth.PI * k), 1f);
			}
		}
	}

	private static void handleGlow(WorldRenderContext ctx, Vec3 hand) {
		MiraculousSequenceClient.ribbon(ctx, new Vec3[] { hand.add(0, 0.09, 0), hand.subtract(0, 0.09, 0) }, 0.04f, ROPE_C, 0xFCC4E2, 1f);
	}

	// ---------------------------------------------------------------- the copies

	private static PlayerModel<LivingEntity> copyModel(boolean slim) {
		if (slim) {
			if (slimCopy == null) {
				slimCopy = new PlayerModel<>(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, true), 64, 64).bakeRoot(), true);
			}
			return slimCopy;
		}
		if (wideCopy == null) {
			wideCopy = new PlayerModel<>(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64).bakeRoot(), false);
		}
		return wideCopy;
	}

	/** One small copy of her at {@code at} (feet), {@code scale} of her size, running, lit pink. */
	private static void renderCopy(WorldRenderContext ctx, Player p, Vec3 at, float scale, float faceYaw, float run) {
		if (scale <= 0.01f || !(p instanceof AbstractClientPlayer cp)) {
			return;
		}
		boolean slim = MultimouseRender.slim(p);
		ResourceLocation skin = cp.getSkin().texture();
		PlayerModel<LivingEntity> m = copyModel(slim);
		m.setAllVisible(true);
		m.hat.visible = false;
		m.jacket.visible = false;
		m.leftSleeve.visible = false;
		m.rightSleeve.visible = false;
		m.leftPants.visible = false;
		m.rightPants.visible = false;
		m.young = false;
		m.crouching = false;
		m.riding = false;
		float sw = Mth.sin(run * 0.7f);
		m.head.setRotation(0.1f, 0f, 0f);
		m.body.setRotation(0.1f, 0f, 0f);
		m.rightArm.setRotation(-sw * 1.0f, 0f, 0.1f);
		m.leftArm.setRotation(sw * 1.0f, 0f, -0.1f);
		m.rightLeg.setRotation(sw * 0.9f, 0f, 0f);
		m.leftLeg.setRotation(-sw * 0.9f, 0f, 0f);
		PoseStack ps = ctx.matrixStack();
		MultiBufferSource buffers = ctx.consumers();
		Vec3 rel = at.subtract(ctx.camera().getPosition());
		ps.pushPose();
		ps.translate(rel.x, rel.y, rel.z);
		ps.mulPose(Axis.YP.rotationDegrees(180f - faceYaw));
		ps.scale(-scale, -scale, scale);
		ps.translate(0f, -1.501f, 0f);
		// lit pink: her own skin tinted, the suit full bright over it
		m.renderToBuffer(ps, buffers.getBuffer(RenderType.entityTranslucent(skin)), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0xFFFFC8E4);
		MultimouseRender.renderSuit(ps, buffers, FULL_BRIGHT, m, slim, 1f, OverlayTexture.NO_OVERLAY);
		MultimouseRender.renderExtras(ps, buffers, FULL_BRIGHT, m, 1f, run, 0.6f, run * 0.7f);
		ps.popPose();
	}

	// ---------------------------------------------------------------- the camera

	private static final float[][] CAM = {
			{ 0, 1.6f, 0, 1.45f, 1.3f },
			{ 16, 1.25f, 10, 1.4f, 1.35f },
			{ 24, 3.4f, 25, 1.9f, 0.8f },
			{ 46, 4.2f, 110, 2.4f, 0.6f },
			{ 62, 4.0f, 240, 2.1f, 0.8f },
			{ 86, 2.6f, 355, 1.4f, 1.1f },
			{ 94, 1.75f, 345, 1.95f, 1.75f },
			{ 112, 1.7f, 340, 1.9f, 1.8f },
			{ 118, 3.4f, 330, 1.2f, 0.9f },
			{ 142, 3.5f, 320, 1.1f, 0.9f },
			{ 150, 2.1f, 340, 1.55f, 1.45f },
			{ TOTAL, 2.7f, 338, 1.35f, 1.35f },
	};
}
