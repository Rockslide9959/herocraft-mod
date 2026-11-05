package com.projecthero.mod.client.miraculous.queenbee;

import static com.projecthero.mod.miraculous.queenbee.QueenBeeSequence.BANDS;
import static com.projecthero.mod.miraculous.queenbee.QueenBeeSequence.BANDS_END;
import static com.projecthero.mod.miraculous.queenbee.QueenBeeSequence.BAND_COUNT;
import static com.projecthero.mod.miraculous.queenbee.QueenBeeSequence.BAND_SWEEP;
import static com.projecthero.mod.miraculous.queenbee.QueenBeeSequence.CATCH;
import static com.projecthero.mod.miraculous.queenbee.QueenBeeSequence.COMB;
import static com.projecthero.mod.miraculous.queenbee.QueenBeeSequence.HIP;
import static com.projecthero.mod.miraculous.queenbee.QueenBeeSequence.MASK;
import static com.projecthero.mod.miraculous.queenbee.QueenBeeSequence.MASK_END;
import static com.projecthero.mod.miraculous.queenbee.QueenBeeSequence.POSE;
import static com.projecthero.mod.miraculous.queenbee.QueenBeeSequence.THROW;
import static com.projecthero.mod.miraculous.queenbee.QueenBeeSequence.TOP;
import static com.projecthero.mod.miraculous.queenbee.QueenBeeSequence.TOTAL;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.miraculous.MiraculousSequenceClient;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.data.MiraculousState;
import com.projecthero.mod.miraculous.queenbee.QueenBeeSequence;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2: the client half of Queen Bee's long transformation ({@link QueenBeeSequence}) on the shared frame
 * ({@link MiraculousSequenceClient}): Pollen's streak spiralling into the Bee Comb and a gold flash on it; five bands of
 * gold and black coiling down her from the comb as she turns, the suit following them; her hand sweeping across her eyes
 * as the mask forms; the top forming in her hand, circling her on its string and snapping to her hip (the suit layer
 * leaves it off her hip until then, {@link #topOnHip}); and the final pose in front of a flash of honeycomb.
 */
public final class QueenBeeSequenceClient {
	private static final Vector3f GOLD = new Vector3f(0.98f, 0.78f, 0.1f);
	private static final Vector3f GOLD_L = new Vector3f(1f, 0.92f, 0.45f);
	private static final Vector3f BLACK = new Vector3f(0.09f, 0.08f, 0.07f);
	private static final int GLOW = 0xF5C518;
	private static final int CORE = 0xFFF4C0;

	/** Pose keys: head x, right arm x / z, left arm x / z, right leg x / z, left leg x / z, body x. */
	private static final float[] STAND = { 0f, 0f, 0.1f, 0f, -0.1f, 0f, 0.05f, 0f, -0.05f, 0f };
	/** Both hands up behind her head, at the comb, chin down. */
	private static final float[] COMB_P = { 0.3f, -2.85f, -0.4f, -2.85f, 0.4f, 0f, 0.05f, 0f, -0.05f, 0f };
	/** Thrown open as the comb flashes, head back. */
	private static final float[] FLING_P = { -0.3f, -0.25f, 1.5f, -0.25f, -1.5f, 0f, 0.2f, 0f, -0.2f, -0.05f };
	/** Arms a little out and legs together while the bands wrap her. */
	private static final float[] WRAP_P = { -0.05f, -0.1f, 0.6f, -0.1f, -0.6f, 0f, 0.03f, 0f, -0.03f, 0f };
	/** Her right hand up at her eyes (the sweep itself is {@link #sweep}), the left on her hip. */
	private static final float[] MASK_P = { 0f, -2.2f, 0f, 0.25f, -0.55f, 0f, 0.05f, 0f, -0.05f, 0f };
	/** Her open right hand held out for the top. */
	private static final float[] TOP_P = { 0.1f, -1.55f, 0.15f, 0.25f, -0.55f, 0f, 0.05f, 0f, -0.05f, 0f };
	/** Out to the side, the string running from her fist. */
	private static final float[] THROW_P = { -0.05f, -1.2f, 0.75f, 0.25f, -0.55f, -0.08f, 0.08f, 0.08f, -0.08f, 0f };
	/** The fist drawn back down to her hip. */
	private static final float[] CATCH_P = { 0.1f, -0.2f, 0.28f, 0.25f, -0.55f, -0.08f, 0.08f, 0.08f, -0.08f, 0f };
	/** Weight on one leg, the left hand on her hip, the right pointing high, her chin up. */
	private static final float[] POSE_P = { -0.2f, -2.35f, 0.3f, 0.25f, -0.55f, -0.14f, 0.08f, 0.1f, -0.15f, -0.04f };

	private static final int[] AT = { 0, 8, 20, 30, MASK, MASK_END, TOP, THROW, CATCH, POSE };
	private static final int[] BL = { 6, 4, 6, 7, 6, 6, 6, 5, 6, 1 };
	private static final float[][] KEYS = { STAND, COMB_P, FLING_P, WRAP_P, MASK_P, MASK_P, TOP_P, THROW_P, CATCH_P, POSE_P };

	public static final MiraculousSequenceClient.Scene SCENE = new MiraculousSequenceClient.Scene() {
		@Override
		public float[] pose(Player p, MiraculousState s, float t) {
			return MiraculousSequenceClient.keyed(t, AT, BL, KEYS);
		}

		@Override
		public void extraPose(Player p, HumanoidModel<?> model, float t, float w) {
			float e = w * (MiraculousSequenceClient.smooth((t - (MASK - 6f)) / 6f) - MiraculousSequenceClient.smooth((t - MASK_END) / 6f));
			if (e > 0f) {
				model.rightArm.yRot = Mth.lerp(e, model.rightArm.yRot, sweep(t));
			}
			float k = w * MiraculousSequenceClient.smooth((t - POSE) / 6f);
			if (k > 0f) {
				// a haughty tilt of the head, a queen looking down at the room
				model.head.yRot = Mth.lerp(k, model.head.yRot, -0.25f);
				model.head.zRot = Mth.lerp(k, model.head.zRot, -0.1f);
			}
		}

		@Override
		public void fx(ClientLevel level, Player p, MiraculousState s, int t, float yaw) {
			QueenBeeSequenceClient.fx(level, p, t, yaw);
		}

		@Override
		public float[][] cam() {
			return CAM;
		}

		@Override
		public void world(WorldRenderContext ctx, Player p, MiraculousState s, float t, float partial) {
			QueenBeeSequenceClient.world(ctx, p, s, t);
		}
	};

	private QueenBeeSequenceClient() {
	}

	// ---------------------------------------------------------------- hooks for her renderer

	/** Is the top at her hip yet? True outside the long transformation (the layer's own rule then). */
	public static boolean topOnHip(Player player, float partial) {
		float t = MiraculousSequenceClient.age(player, Kwami.POLLEN, partial);
		return t < 0f || t >= HIP;
	}

	// ---------------------------------------------------------------- geometry

	/** The mask's sweep: the raised right arm's yaw, out past her right eye, across to her left (negative = in across her). */
	private static float sweep(float t) {
		return Mth.lerp(MiraculousSequenceClient.smooth((t - (MASK + 2f)) / (MASK_END - MASK - 4f)), 0.3f, -0.95f);
	}

	/** The comb at the back of her head. */
	private static Vec3 comb(Vec3 feet, double h, Vec3 fwd) {
		return feet.add(0, h * 0.95, 0).subtract(fwd.scale(0.28));
	}

	/** Where the right hand is for arm keys {@code xr} / {@code zr}, as Multimouse's scene works it out. */
	private static Vec3 hand(Vec3 feet, double h, Vec3 fwd, Vec3 right, float xr, float zr) {
		Vec3 shoulder = feet.add(0, h * 0.764, 0).add(right.scale(0.31));
		double u = -Math.cos(xr);
		double f = -Math.sin(xr);
		double side = -u * Math.sin(zr);
		double up = u * Math.cos(zr);
		return shoulder.add(right.scale(side * 0.62)).add(0, up * 0.62, 0).add(fwd.scale(f * 0.62));
	}

	/** Her right hip, where the top ends up. */
	private static Vec3 hip(Vec3 feet, double h, Vec3 right) {
		return feet.add(0, h * 0.44, 0).add(right.scale(0.32));
	}

	/** Where the top is at {@code t} (TOP .. HIP): in her hand, circling her twice, then home to her hip. */
	private static Vec3 topAt(Vec3 feet, double h, Vec3 fwd, Vec3 right, Vec3 handAt, float t) {
		if (t < THROW) {
			return handAt.add(fwd.scale(0.08));
		}
		float out = MiraculousSequenceClient.smooth((t - THROW) / 5f);
		float turn = (Math.min(t, CATCH) - THROW) / (float) (CATCH - THROW);
		double a = Math.atan2(right.z, right.x) + Math.PI * 4 * turn;
		Vec3 orbit = feet.add(Math.cos(a) * 1.5, h * 0.55 + 0.15 * Math.sin(a * 2), Math.sin(a) * 1.5);
		Vec3 at = handAt.lerp(orbit, out);
		return t < CATCH ? at : at.lerp(hip(feet, h, right), MiraculousSequenceClient.smooth((t - CATCH) / (float) (HIP - CATCH)));
	}

	// ---------------------------------------------------------------- the light show

	private static void fx(ClientLevel level, Player p, int t, float yaw) {
		RandomSource rnd = level.random;
		double h = p.getBbHeight();
		Vec3 feet = p.position();
		Vec3 fwd = MiraculousSequenceClient.forward(yaw);
		Vec3 right = MiraculousSequenceClient.right(fwd);
		Vec3 comb = comb(feet, h, fwd);
		DustParticleOptions gold = new DustParticleOptions(GOLD, 1.0f);
		DustParticleOptions goldL = new DustParticleOptions(GOLD_L, 0.7f);
		DustParticleOptions black = new DustParticleOptions(BLACK, 0.8f);
		if (t < COMB) {
			float k = t / (float) COMB;
			double a = t * 1.1;
			double r = 1.1 * (1 - k) + 0.04;
			Vec3 q = comb.add(right.scale(Math.cos(a) * r)).add(fwd.scale(Math.sin(a) * r)).add(0, 0.5 * (1 - k), 0);
			level.addParticle(gold, q.x, q.y, q.z, 0, 0, 0);
		} else if (t == COMB) {
			level.addParticle(ParticleTypes.FLASH, comb.x, comb.y, comb.z, 0, 0, 0);
			for (int i = 0; i < 14; i++) {
				level.addParticle(i % 2 == 0 ? gold : goldL, comb.x, comb.y, comb.z, (rnd.nextDouble() - 0.5) * 0.25, (rnd.nextDouble() - 0.5) * 0.25,
						(rnd.nextDouble() - 0.5) * 0.25);
			}
		} else if (t >= BANDS && t < BANDS_END) {
			for (int i = 0; i < BAND_COUNT; i++) {
				if (t == QueenBeeSequence.bandStart(i) + BAND_SWEEP) {
					// a band lands: a ring of sparks round the height it stops at
					double y = bandRest(h, i);
					for (int j = 0; j < 12; j++) {
						double a = j * Math.PI * 2 / 12;
						level.addParticle(i % 2 == 0 ? goldL : black, feet.x + Math.cos(a) * 0.36, feet.y + y, feet.z + Math.sin(a) * 0.36, Math.cos(a) * 0.1, 0.01,
								Math.sin(a) * 0.1);
					}
				}
			}
		} else if (t >= THROW && t < HIP && t % 2 == 0) {
			level.addParticle(goldL, feet.x + (rnd.nextDouble() - 0.5) * 3.0, feet.y + h * (0.3 + rnd.nextDouble() * 0.5), feet.z + (rnd.nextDouble() - 0.5) * 3.0, 0, 0, 0);
		}
		if (t == HIP) {
			Vec3 at = hip(feet, h, right);
			for (int i = 0; i < 12; i++) {
				level.addParticle(i % 2 == 0 ? gold : goldL, at.x, at.y, at.z, (rnd.nextDouble() - 0.5) * 0.2, (rnd.nextDouble() - 0.5) * 0.2, (rnd.nextDouble() - 0.5) * 0.2);
			}
		}
	}

	/** How high band {@code i} comes to rest: the first at her collar, the last at her boots. */
	private static double bandRest(double h, int i) {
		return h * (0.74 - 0.16 * i);
	}

	/** A four-point star at {@code c}, {@code r} across. */
	private static void star(WorldRenderContext ctx, Vec3 c, Vec3 right, float r, float alpha) {
		if (r <= 0.002f) {
			return;
		}
		MiraculousSequenceClient.ribbon(ctx, new Vec3[] { c.add(0, r, 0), c, c.subtract(0, r, 0) }, 0.022f, GLOW, 0xFFFFFF, alpha);
		MiraculousSequenceClient.ribbon(ctx, new Vec3[] { c.add(right.scale(r)), c, c.subtract(right.scale(r)) }, 0.022f, GLOW, 0xFFFFFF, alpha);
	}

	/** A hexagon of light round {@code c}, facing along the normal of {@code right} and up. */
	private static void hex(WorldRenderContext ctx, Vec3 c, Vec3 right, float r, float alpha) {
		Vec3[] path = new Vec3[7];
		for (int i = 0; i < 7; i++) {
			double a = Math.PI / 6 + i * Math.PI / 3;
			path[i] = c.add(right.scale(Math.cos(a) * r)).add(0, Math.sin(a) * r, 0);
		}
		// solid gold: additive light alone is lost against a bright sky
		solid(ctx, path, 0.02f * alpha, QueenBeeRender.GOLD);
		MiraculousSequenceClient.ribbon(ctx, path, 0.05f, GLOW, CORE, alpha);
	}

	/** A solid (not additive, so it can be black) path through world points. */
	private static void solid(WorldRenderContext ctx, Vec3[] path, float thick, int rgb) {
		VertexConsumer vc = ctx.consumers().getBuffer(RenderType.entityCutoutNoCull(com.projecthero.mod.client.maxsteel.TurboDraw.WHITE));
		Vec3 cam = ctx.camera().getPosition();
		for (int i = 0; i + 1 < path.length; i++) {
			Vec3 a = path[i].subtract(cam), b = path[i + 1].subtract(cam);
			QueenBeeRender.segment(vc, ctx.matrixStack(), (float) a.x, (float) a.y, (float) a.z, (float) b.x, (float) b.y, (float) b.z, thick, rgb, LightTexture.FULL_BRIGHT);
		}
	}

	private static void world(WorldRenderContext ctx, Player p, MiraculousState s, float t) {
		Vec3 feet = MiraculousSequenceClient.origin(s, t);
		double h = p.getBbHeight();
		float yaw = MiraculousSequenceClient.yaw(s, t);
		Vec3 fwd = MiraculousSequenceClient.forward(yaw);
		Vec3 right = MiraculousSequenceClient.right(fwd);
		Vec3 comb = comb(feet, h, fwd);
		if (t < COMB + 1) {
			// Pollen's streak as she spirals round and into the comb
			Vec3[] trail = new Vec3[9];
			for (int i = 0; i < trail.length; i++) {
				float u = Math.max(0f, t - i * 0.6f);
				float k = Math.min(1f, u / COMB);
				double a = u * 1.1;
				double r = 1.1 * (1 - k) + 0.04;
				trail[i] = comb.add(right.scale(Math.cos(a) * r)).add(fwd.scale(Math.sin(a) * r)).add(0, 0.5 * (1 - k), 0);
			}
			MiraculousSequenceClient.ribbon(ctx, trail, 0.05f, GLOW, CORE, 0.8f * (1f - MiraculousSequenceClient.smooth((t - COMB + 1) / 2f)));
		}
		if (t >= COMB && t < COMB + 10) {
			star(ctx, comb.subtract(fwd.scale(0.05)), right, 0.34f * Mth.sin(Mth.PI * (t - COMB) / 10f), 1f);
		}
		if (t >= BANDS && t < BANDS_END + 6) {
			for (int i = 0; i < BAND_COUNT; i++) {
				band(ctx, feet, h, i, t);
			}
		}
		if (t >= MASK + 1 && t < MASK_END) {
			// the light that wipes across her eyes with her hand
			float k = (t - (MASK + 1f)) / (MASK_END - MASK - 1f);
			Vec3 eyes = feet.add(0, h * 0.885, 0).add(fwd.scale(0.3));
			Vec3 from = eyes.add(right.scale(0.34)), to = eyes.subtract(right.scale(0.34));
			Vec3 at = from.lerp(to, MiraculousSequenceClient.smooth(k));
			MiraculousSequenceClient.ribbon(ctx, new Vec3[] { from.lerp(at, 0.5), at }, 0.04f, GLOW, CORE, Mth.sin(Mth.PI * k));
			star(ctx, at, right, 0.14f * Mth.sin(Mth.PI * k), 1f);
		}
		float[] pose = SCENE.pose(p, s, Math.min(t, TOTAL));
		if (t >= TOP && t < HIP) {
			Vec3 handAt = hand(feet, h, fwd, right, pose[1], pose[2]);
			Vec3 top = topAt(feet, h, fwd, right, handAt, t);
			if (t >= THROW) {
				solid(ctx, new Vec3[] { handAt, top }, 0.012f, QueenBeeRender.STRING);
			}
			float grow = MiraculousSequenceClient.smooth((t - TOP) / 5f);
			PoseStack ps = ctx.matrixStack();
			Vec3 rel = top.subtract(ctx.camera().getPosition());
			ps.pushPose();
			ps.translate(rel.x, rel.y, rel.z);
			ps.scale(grow, grow, grow);
			QueenBeeRender.renderTop(ps, ctx.consumers(), LightTexture.FULL_BRIGHT, t * 1.5f, t < THROW ? 1f - grow : 0f);
			ps.popPose();
			if (t < TOP + 8) {
				star(ctx, top, right, 0.22f * Mth.sin(Mth.PI * (t - TOP) / 8f), 1f);
			}
		}
		if (t >= POSE && t < TOTAL + MiraculousSequenceClient.EASE_OUT) {
			// the wall of honeycomb behind her: the middle cell first, then the six round it
			float out = 1f - MiraculousSequenceClient.smooth((t - TOTAL) / MiraculousSequenceClient.EASE_OUT);
			Vec3 c = feet.add(0, h * 0.62, 0).subtract(fwd.scale(0.7));
			float r = 0.34f;
			double step = r * Math.sqrt(3.0);
			hex(ctx, c, right, r * MiraculousSequenceClient.smooth((t - POSE) / 4f), out);
			for (int i = 0; i < 6; i++) {
				float in = MiraculousSequenceClient.smooth((t - POSE - 2f - i) / 4f);
				double a = i * Math.PI / 3;
				hex(ctx, c.add(right.scale(Math.cos(a) * step)).add(0, Math.sin(a) * step, 0), right, r * in, 0.8f * out);
			}
			Vec3 hand = hand(feet, h, fwd, right, pose[1], pose[2]);
			if (t < POSE + 12) {
				star(ctx, hand.add(0, 0.08, 0), right, 0.3f * Mth.sin(Mth.PI * (t - POSE) / 12f), 1f);
			}
		}
	}

	/** Band {@code i}: a coil from the comb down to where it comes to rest, gold on even bands, black on odd, gone once landed. */
	private static void band(WorldRenderContext ctx, Vec3 feet, double h, int i, float t) {
		float start = QueenBeeSequence.bandStart(i);
		if (t < start) {
			return;
		}
		float k = Math.min(1f, (t - start) / BAND_SWEEP);
		float fade = 1f - MiraculousSequenceClient.smooth((t - start - BAND_SWEEP) / 5f);
		if (fade <= 0f) {
			return;
		}
		double top = h * 0.95;
		double down = Mth.lerp(k, top, bandRest(h, i));
		// the coil's tail catches up with its head, so it reads as a band running down her and not a tube round her
		double tail = Mth.lerp(MiraculousSequenceClient.smooth(k), top, down + 0.12);
		double a0 = i * 1.3 + t * 0.35;
		double r = 0.58 - 0.16 * k;
		Vec3[] coil = MiraculousSequenceClient.helix(feet, tail, down, a0, a0 + Math.PI * 3, r, r, 26);
		if (i % 2 == 0) {
			MiraculousSequenceClient.ribbon(ctx, coil, 0.055f, GLOW, CORE, fade);
		} else {
			solid(ctx, coil, 0.03f * fade, QueenBeeRender.BLACK);
		}
	}

	// ---------------------------------------------------------------- the camera

	/** Keys: tick, distance, angle round her from the front (deg), camera height, height looked at. */
	private static final float[][] CAM = {
			{ 0, 1.9f, 150, 1.75f, 1.6f },
			{ 16, 1.15f, 178, 1.85f, 1.7f },
			{ 24, 2.5f, 190, 1.75f, 1.05f },
			{ 50, 2.6f, 280, 1.85f, 1.0f },
			{ 78, 2.3f, 350, 1.5f, 1.05f },
			{ 86, 1.5f, 360, 1.62f, 1.55f },
			{ 100, 1.5f, 360, 1.62f, 1.55f },
			{ 106, 2.2f, 385, 1.4f, 1.25f },
			{ 114, 3.3f, 395, 2.1f, 0.95f },
			{ 136, 3.5f, 420, 2.0f, 0.95f },
			{ 148, 2.5f, 372, 1.35f, 1.25f },
			{ TOTAL, 3.0f, 362, 1.25f, 1.3f },
	};
}
