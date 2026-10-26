package com.projecthero.mod.client.miraculous.pegasus;

import static com.projecthero.mod.miraculous.pegasus.PegasusSequence.GLASSES;
import static com.projecthero.mod.miraculous.pegasus.PegasusSequence.MANE;
import static com.projecthero.mod.miraculous.pegasus.PegasusSequence.MANE_END;
import static com.projecthero.mod.miraculous.pegasus.PegasusSequence.PORTAL;
import static com.projecthero.mod.miraculous.pegasus.PegasusSequence.PORTAL_DOWN;
import static com.projecthero.mod.miraculous.pegasus.PegasusSequence.PORTAL_END;
import static com.projecthero.mod.miraculous.pegasus.PegasusSequence.POSE;
import static com.projecthero.mod.miraculous.pegasus.PegasusSequence.SHOE;
import static com.projecthero.mod.miraculous.pegasus.PegasusSequence.TOTAL;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.client.miraculous.MiraculousSequenceClient;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.data.MiraculousState;
import com.projecthero.mod.miraculous.pegasus.PegasusSequence;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: the client half of Pegasus's long transformation ({@link PegasusSequence}) on the shared frame
 * ({@link MiraculousSequenceClient}): Kaalki entering the Horseshoe Glasses, a white-gold horseshoe ring of light with a
 * portal's swirl in it ({@link PegasusPortalRenderer#draw}) opening over his head and sinking down him as he passes
 * through it (the suit forming where it has passed), a mane-like flourish of light sweeping back over his head, the
 * Horseshoe spinning in his hand ({@link #renderShoe}), and the final pose beside a portal that opens for a moment.
 */
public final class PegasusSequenceClient {
	private static final Vector3f GOLD = new Vector3f(0.9f, 0.76f, 0.46f);
	private static final Vector3f WHITE = new Vector3f(1.0f, 0.97f, 0.88f);
	private static final Vector3f BLACK = new Vector3f(0.06f, 0.05f, 0.06f);
	private static final int GLOW = 0xE6C276;
	private static final int CORE = 0xFFFBEA;

	/** Pose keys: head x, right arm x / z, left arm x / z, right leg x / z, left leg x / z, body x. */
	private static final float[] STAND = { 0f, 0f, 0.1f, 0f, -0.1f, 0f, 0.05f, 0f, -0.05f, 0f };
	/** Pushing the glasses up. */
	private static final float[] GLASSES_P = { 0f, -2.45f, -0.35f, 0f, -0.1f, 0f, 0.05f, 0f, -0.05f, 0f };
	/** Reaching up into the portal over him. */
	private static final float[] REACH = { -0.45f, -2.95f, 0.3f, -2.95f, -0.3f, 0f, 0.05f, 0f, -0.05f, 0f };
	/** A galloping stride through it, arms swept back. */
	private static final float[] GALLOP = { -0.1f, 0.35f, 1.1f, 0.35f, -1.1f, -0.6f, 0.05f, 0.5f, -0.05f, 0.1f };
	/** The head tossed back like a mane. */
	private static final float[] MANE_P = { -0.6f, 0.2f, 0.8f, 0.2f, -0.8f, 0f, 0.08f, 0f, -0.08f, -0.1f };
	/** The Horseshoe out before him. */
	private static final float[] SHOE_P = { 0f, -1.5f, 0.15f, -0.4f, -0.8f, -0.15f, 0.08f, 0.1f, -0.08f, 0f };
	/** The Horseshoe raised, the other arm out to the portal at his left. */
	private static final float[] POSE_P = { -0.1f, -2.6f, 0.4f, -1.4f, -1.05f, -0.2f, 0.1f, 0.15f, -0.08f, 0f };

	private static final int[] AT = { 0, 6, PORTAL + 2, PORTAL_DOWN + 10, MANE, SHOE, POSE };
	private static final int[] BL = { 6, 8, 10, 8, 6, 6, 1 };
	private static final float[][] KEYS = { STAND, GLASSES_P, REACH, GALLOP, MANE_P, SHOE_P, POSE_P };

	public static final MiraculousSequenceClient.Scene SCENE = new MiraculousSequenceClient.Scene() {
		@Override
		public float[] pose(Player p, MiraculousState s, float t) {
			float[] pose = MiraculousSequenceClient.keyed(t, AT, BL, KEYS);
			if (t >= PORTAL_DOWN + 4 && t < MANE - 2) {
				pose = pose.clone();
				// galloping through the ring
				float g = 0.55f * Mth.sin(t * 0.55f);
				pose[5] += g;
				pose[7] -= g;
				pose[1] -= 0.3f * g;
				pose[3] += 0.3f * g;
			} else if (t >= MANE && t < MANE_END) {
				pose = pose.clone();
				// the toss: back, and forward again
				pose[0] = Mth.lerp(MiraculousSequenceClient.smooth((t - MANE) / (MANE_END - MANE)), -0.6f, 0.05f);
			} else if (t >= SHOE + 4 && t < POSE - 2) {
				pose = pose.clone();
				pose[1] += 0.2f * Mth.sin(t * 1.3f);
				pose[2] += 0.15f * Mth.cos(t * 1.3f);
			}
			return pose;
		}

		@Override
		public void fx(ClientLevel level, Player p, MiraculousState s, int t, float yaw) {
			PegasusSequenceClient.fx(level, p, t, yaw);
		}

		@Override
		public float[][] cam() {
			return CAM;
		}

		@Override
		public void world(WorldRenderContext ctx, Player p, MiraculousState s, float t, float partial) {
			PegasusSequenceClient.world(ctx, p, s, t);
		}
	};

	private PegasusSequenceClient() {
	}

	/**
	 * The Horseshoe during the sequence: hidden until the suit is on, at the belt, spinning in his hand, raised for the
	 * pose. True = handled; false = not playing.
	 */
	public static boolean renderShoe(PoseStack pose, MultiBufferSource buffers, int light, HumanoidModel<?> parent, Player player, float partial,
			boolean slim) {
		float t = MiraculousSequenceClient.age(player, Kwami.KAALKI, partial);
		if (t < 0f) {
			return false;
		}
		if (t < MANE_END) {
			return true;
		}
		if (t < SHOE) {
			PegasusRender.renderShoe(pose, buffers, light, parent, PegasusRender.ShoeAt.BELT, slim);
			return true;
		}
		if (t >= POSE) {
			PegasusRender.renderShoe(pose, buffers, light, parent, PegasusRender.ShoeAt.HAND, slim);
			return true;
		}
		// whirled round on the finger: the HAND hold, turning about the grip
		pose.pushPose();
		parent.rightArm.translateAndRotate(pose);
		pose.translate((slim ? -0.5f : -1f) / 16f, 10.6f / 16f, -0.6f / 16f);
		pose.mulPose(Axis.XP.rotationDegrees(-90f));
		pose.mulPose(Axis.ZP.rotationDegrees((t - SHOE) * 38f));
		pose.mulPose(Axis.YP.rotationDegrees(90f));
		pose.translate(0f, 3.0f / 16f, 0f);
		pose.scale(0.85f, 0.85f, 0.85f);
		PegasusRender.renderShoeModel(pose, buffers, light);
		pose.popPose();
		return true;
	}

	// ---------------------------------------------------------------- the light show

	private static void fx(ClientLevel level, Player p, int t, float yaw) {
		RandomSource rnd = level.random;
		double h = p.getBbHeight();
		Vec3 feet = p.position();
		Vec3 fwd = MiraculousSequenceClient.forward(yaw);
		Vec3 right = MiraculousSequenceClient.right(fwd);
		Vec3 eyes = feet.add(0, h * 0.88, 0).add(fwd.scale(0.3));
		DustParticleOptions gold = new DustParticleOptions(GOLD, 1.1f);
		DustParticleOptions white = new DustParticleOptions(WHITE, 0.8f);
		DustParticleOptions black = new DustParticleOptions(BLACK, 0.9f);
		if (t < GLASSES) {
			// Kaalki trots in: a bobbing white-gold streak into the glasses
			float k = t / (float) GLASSES;
			Vec3 from = eyes.add(fwd.scale(1.1 * (1 - k))).add(right.scale(0.5 * (1 - k))).add(0, 0.25 * Math.abs(Math.sin(k * 9)) * (1 - k), 0);
			level.addParticle(gold, from.x, from.y, from.z, 0, 0, 0);
			level.addParticle(white, from.x, from.y, from.z, 0, 0, 0);
		} else if (t < PORTAL + 4) {
			for (int side = -1; side <= 1; side += 2) {
				Vec3 lens = eyes.add(right.scale(0.12 * side));
				if (t == GLASSES) {
					level.addParticle(ParticleTypes.FLASH, lens.x, lens.y, lens.z, 0, 0, 0);
				}
				level.addParticle(t % 2 == 0 ? white : gold, lens.x, lens.y, lens.z, (rnd.nextDouble() - 0.5) * 0.08, 0.02, (rnd.nextDouble() - 0.5) * 0.08);
			}
		}
		float[] pass = PegasusSequence.passPortal(t);
		if (pass != null && pass[0] > 0.2f) {
			// sparks shed off the ring as it passes down him
			double y = pass[1] * h;
			for (int i = 0; i < 3; i++) {
				double a = rnd.nextDouble() * Math.PI * 2;
				level.addParticle(i == 0 ? white : gold, feet.x + Math.cos(a) * 0.95, feet.y + y, feet.z + Math.sin(a) * 0.95, 0, -0.02, 0);
			}
			if (t % 4 == 0) {
				level.addParticle(ParticleTypes.END_ROD, feet.x, feet.y + y, feet.z, (rnd.nextDouble() - 0.5) * 0.2, 0, (rnd.nextDouble() - 0.5) * 0.2);
			}
		}
		if (t >= MANE && t < MANE_END) {
			// the mane flourish streams off the back of his head
			Vec3 nape = feet.add(0, h * 0.86, 0).subtract(fwd.scale(0.3));
			level.addParticle(t % 3 == 0 ? black : t % 2 == 0 ? white : gold, nape.x, nape.y, nape.z, -fwd.x * 0.12, 0.02, -fwd.z * 0.12);
		} else if (t >= SHOE && t < POSE) {
			Vec3 hand = feet.add(0, h * 0.62, 0).add(fwd.scale(0.6)).add(right.scale(0.35));
			double a = t * 0.66;
			level.addParticle(t % 2 == 0 ? gold : white, hand.x + right.x * Math.cos(a) * 0.3, hand.y + Math.sin(a) * 0.3,
					hand.z + right.z * Math.cos(a) * 0.3, 0, 0, 0);
		} else if (t == POSE) {
			Vec3 c = feet.add(0, h * 0.55, 0).subtract(right.scale(1.5));
			level.addParticle(ParticleTypes.FLASH, c.x, c.y, c.z, 0, 0, 0);
			for (int i = 0; i < 40; i++) {
				double a = rnd.nextDouble() * Math.PI * 2;
				Vec3 dir = right.scale(Math.cos(a) * 0.3).add(fwd.scale(Math.sin(a) * 0.3)).add(0, (rnd.nextDouble() - 0.3) * 0.2, 0);
				level.addParticle(i % 3 == 0 ? white : gold, c.x, c.y, c.z, dir.x, dir.y, dir.z);
			}
		}
	}

	/** A horseshoe of light: an arc of 300 degrees, its open end at {@code gap} (unit, in the arc's plane). */
	private static Vec3[] horseshoe(Vec3 c, Vec3 u, Vec3 v, double r) {
		int n = 30;
		Vec3[] out = new Vec3[n + 1];
		for (int i = 0; i <= n; i++) {
			double a = Math.toRadians(-60 + 300 * i / (double) n) - Math.PI / 2;
			out[i] = c.add(u.scale(Math.cos(a) * r)).add(v.scale(Math.sin(a) * r));
		}
		return out;
	}

	private static void world(WorldRenderContext ctx, Player p, MiraculousState s, float t) {
		Vec3 feet = MiraculousSequenceClient.origin(s, t);
		double h = p.getBbHeight();
		float yaw = MiraculousSequenceClient.yaw(s, t);
		Vec3 fwd = MiraculousSequenceClient.forward(yaw);
		Vec3 right = MiraculousSequenceClient.right(fwd);
		Vec3 cam = ctx.camera().getPosition();
		PoseStack pose = ctx.matrixStack();
		float[] pass = PegasusSequence.passPortal(t);
		if (pass != null && pass[0] > 0.01f) {
			// the ring he passes through: a portal's swirl lying flat in a horseshoe of white-gold light, sinking down him
			Vec3 c = feet.add(0, pass[1] * h, 0);
			float r = 0.95f * (0.2f + 0.8f * pass[0]);
			pose.pushPose();
			pose.translate(c.x - cam.x, c.y - cam.y, c.z - cam.z);
			pose.mulPose(Axis.YP.rotationDegrees(-MiraculousSequenceClient.yaw(s, 0f)));
			pose.mulPose(Axis.XP.rotationDegrees(90f));
			PegasusPortalRenderer.draw(pose, ctx.consumers(), r, t, 0.55f * pass[0]);
			pose.popPose();
			Vec3 f0 = MiraculousSequenceClient.forward(MiraculousSequenceClient.yaw(s, 0f));
			Vec3 r0 = MiraculousSequenceClient.right(f0);
			MiraculousSequenceClient.ribbon(ctx, horseshoe(c, r0, f0, r + 0.08), 0.1f, GLOW, CORE, pass[0]);
		}
		if (t >= MANE && t < MANE_END + 6) {
			// the mane: three streamers of light sweeping from the brow back over the head and down the neck
			float k = MiraculousSequenceClient.smooth((t - MANE) / (MANE_END - MANE - 4f));
			float fade = 1f - MiraculousSequenceClient.smooth((t - MANE_END) / 6f);
			Vec3 head = feet.add(0, h * 0.86, 0);
			for (int strand = -1; strand <= 1; strand++) {
				int n = 14;
				Vec3[] path = new Vec3[n + 1];
				for (int i = 0; i <= n; i++) {
					double f = i / (double) n * k;
					double a = f * Math.PI * 1.15;
					double r = 0.4 + 0.12 * f;
					double side = strand * 0.11 + 0.07 * Math.sin(f * Math.PI * 3 + t * 0.4);
					path[i] = head.add(fwd.scale(Math.cos(a) * r)).add(0, Math.sin(a) * r, 0).add(right.scale(side));
				}
				MiraculousSequenceClient.ribbon(ctx, path, 0.055f, strand == 0 ? GLOW : 0xF4F0E0, CORE, 0.9f * fade);
			}
		}
		float open = PegasusSequence.posePortal(t);
		if (open > 0.01f) {
			// the portal at his side, open for a moment
			Vec3 c = feet.add(0, 1.05, 0).subtract(right.scale(1.55)).add(fwd.scale(0.15));
			float r = 1.05f * (0.15f + 0.85f * open);
			pose.pushPose();
			pose.translate(c.x - cam.x, c.y - cam.y, c.z - cam.z);
			pose.mulPose(Axis.YP.rotationDegrees(-yaw - 25f));
			PegasusPortalRenderer.draw(pose, ctx.consumers(), r, t, open);
			pose.popPose();
		}
	}

	// ---------------------------------------------------------------- the camera

	private static final float[][] CAM = {
			{ 0, 2.2f, 0, 1.6f, 1.55f },
			{ 14, 1.3f, 15, 1.72f, 1.68f },
			{ 22, 2.0f, 20, 1.6f, 1.6f },
			{ 32, 3.4f, 40, 1.0f, 2.3f },
			{ 42, 3.4f, 60, 2.5f, 1.4f },
			{ 70, 3.4f, 220, 2.4f, 0.9f },
			{ 90, 3.2f, 340, 1.8f, 0.8f },
			{ 98, 1.8f, 300, 1.8f, 1.7f },
			{ 116, 1.9f, 290, 1.7f, 1.6f },
			{ 122, 2.6f, 380, 1.2f, 1.1f },
			{ 146, 2.8f, 400, 1.2f, 1.1f },
			{ 154, 4.6f, 335, 0.8f, 1.2f },
			{ TOTAL, 4.9f, 330, 0.8f, 1.2f },
	};
}
