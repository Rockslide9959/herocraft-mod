package com.projecthero.mod.client.miraculous.renarouge;

import static com.projecthero.mod.miraculous.renarouge.RenaRougeSequence.CATCH;
import static com.projecthero.mod.miraculous.renarouge.RenaRougeSequence.FLUTE;
import static com.projecthero.mod.miraculous.renarouge.RenaRougeSequence.NECKLACE;
import static com.projecthero.mod.miraculous.renarouge.RenaRougeSequence.POSE;
import static com.projecthero.mod.miraculous.renarouge.RenaRougeSequence.SWIRL;
import static com.projecthero.mod.miraculous.renarouge.RenaRougeSequence.SWIRL_END;
import static com.projecthero.mod.miraculous.renarouge.RenaRougeSequence.TAIL;
import static com.projecthero.mod.miraculous.renarouge.RenaRougeSequence.TAIL_END;
import static com.projecthero.mod.miraculous.renarouge.RenaRougeSequence.TOTAL;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.client.miraculous.MiraculousSequenceClient;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.data.MiraculousState;
import com.projecthero.mod.miraculous.renarouge.RenaRougeSequence;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: the client half of Rena Rouge's long transformation ({@link RenaRougeSequence}) on the shared frame
 * ({@link MiraculousSequenceClient}): Trixx diving into the Necklace, the orange fox-fire spiralling round her from the
 * neck down as she turns and the suit forms, the tail flicking out and the ears forming, the Flute spun up into the
 * air and caught ({@link #fluteAt}, {@link #world}), and the sly pose with the Flute behind her back as an orange
 * shimmer fades off her like an illusion.
 */
public final class RenaRougeSequenceClient {
	private static final Vector3f ORANGE = new Vector3f(0.96f, 0.5f, 0.12f);
	private static final Vector3f AMBER = new Vector3f(1.0f, 0.72f, 0.3f);
	private static final Vector3f WHITE = new Vector3f(1.0f, 0.95f, 0.88f);
	private static final int GLOW = 0xF07A1E;
	private static final int CORE = 0xFFE8C8;

	/** Pose keys: head x, right arm x / z, left arm x / z, right leg x / z, left leg x / z, body x. */
	private static final float[] STAND = { 0f, 0f, 0.1f, 0f, -0.1f, 0f, 0.05f, 0f, -0.05f, 0f };
	private static final float[] NECK = { 0.3f, -1.75f, -0.6f, -1.75f, 0.6f, 0f, 0.05f, 0f, -0.05f, 0f };
	private static final float[] ARMS_UP = { -0.35f, -2.95f, 0.3f, -2.95f, -0.3f, 0.05f, -0.06f, -0.05f, 0.06f, 0f };
	private static final float[] TAIL_P = { 0f, 0.35f, 0.65f, 0.35f, -0.65f, -0.1f, 0.08f, 0.15f, -0.1f, 0.08f };
	private static final float[] TOSS = { -0.35f, -2.8f, 0.25f, -0.3f, -0.6f, 0f, 0.06f, 0f, -0.06f, 0f };
	private static final float[] WATCH = { -0.6f, -1.6f, 0.45f, -0.5f, -0.7f, 0f, 0.06f, 0f, -0.06f, -0.05f };
	private static final float[] CATCH_P = { -0.1f, -1.9f, 0.3f, -0.3f, -0.6f, -0.15f, 0.08f, 0.1f, -0.06f, 0f };
	/** Sly: the Flute held behind her back, the other hand on the hip, a hip and a head tilt. */
	private static final float[] SLY = { 0.12f, 0.75f, 0.2f, -0.3f, -0.6f, -0.3f, 0.06f, 0.22f, -0.12f, 0f };

	private static final int[] AT = { 0, 6, SWIRL + 4, TAIL, FLUTE, FLUTE + 8, CATCH, POSE + 2 };
	private static final int[] BL = { 6, 8, 6, 6, 4, 6, 6, 1 };
	private static final float[][] KEYS = { STAND, NECK, ARMS_UP, TAIL_P, TOSS, WATCH, CATCH_P, SLY };

	public static final MiraculousSequenceClient.Scene SCENE = new MiraculousSequenceClient.Scene() {
		@Override
		public float[] pose(Player p, MiraculousState s, float t) {
			float[] pose = MiraculousSequenceClient.keyed(t, AT, BL, KEYS);
			if (t >= SWIRL + 4 && t < SWIRL_END) {
				// her arms sway with the turn
				pose = pose.clone();
				pose[2] += 0.15f * Mth.sin(t * 0.3f);
				pose[4] -= 0.15f * Mth.sin(t * 0.3f);
			}
			return pose;
		}

		@Override
		public void extraPose(Player p, HumanoidModel<?> model, float t, float w) {
			if (t >= TAIL && t < FLUTE) {
				// a look back over the shoulder at the new tail
				float k = Mth.sin(Mth.PI * Mth.clamp((t - TAIL) / (float) (FLUTE - TAIL), 0f, 1f));
				model.head.yRot = Mth.lerp(w, model.head.yRot, -0.9f * k);
			}
			float sly = w * MiraculousSequenceClient.smooth((t - POSE) / 6f);
			if (sly > 0f) {
				model.head.zRot = Mth.lerp(sly, model.head.zRot, 0.22f);
				model.head.yRot = Mth.lerp(sly, model.head.yRot, 0.3f);
				model.rightArm.yRot = Mth.lerp(sly, model.rightArm.yRot, -0.55f);
			}
		}

		@Override
		public void fx(ClientLevel level, Player p, MiraculousState s, int t, float yaw) {
			RenaRougeSequenceClient.fx(level, p, s, t, yaw);
		}

		@Override
		public float[][] cam() {
			return CAM;
		}

		@Override
		public void world(WorldRenderContext ctx, Player p, MiraculousState s, float t, float partial) {
			RenaRougeSequenceClient.world(ctx, p, s, t, partial);
		}
	};

	private RenaRougeSequenceClient() {
	}

	/** The Flute during the sequence (null = not playing: her own rule). */
	public static RenaRougeRender.FluteAt fluteAt(Player player, float partial) {
		float t = MiraculousSequenceClient.age(player, Kwami.TRIXX, partial);
		if (t < 0f) {
			return null;
		}
		if (t < TAIL_END || RenaRougeSequence.fluteFlight(t) >= 0f) {
			return RenaRougeRender.FluteAt.NONE;
		}
		return RenaRougeRender.FluteAt.HAND;
	}

	/** Where the spinning Flute is in its flight (world), for {@code k} 0..1 of it. */
	private static Vec3 flutePos(Vec3 feet, double h, Vec3 fwd, Vec3 right, float k) {
		Vec3 hand = feet.add(0, h * 0.98, 0).add(fwd.scale(0.15)).add(right.scale(0.38));
		return hand.add(0, 1.9 * 4 * k * (1 - k), 0).add(fwd.scale(0.35 * Mth.sin(Mth.PI * k)));
	}

	// ---------------------------------------------------------------- the light show

	private static void fx(ClientLevel level, Player p, MiraculousState s, int t, float yaw) {
		RandomSource rnd = level.random;
		double h = p.getBbHeight();
		Vec3 feet = p.position();
		Vec3 fwd = MiraculousSequenceClient.forward(yaw);
		Vec3 right = MiraculousSequenceClient.right(fwd);
		Vec3 neck = feet.add(0, h * 0.77, 0).add(fwd.scale(0.2));
		DustParticleOptions orange = new DustParticleOptions(ORANGE, 1.1f);
		DustParticleOptions amber = new DustParticleOptions(AMBER, 0.8f);
		DustParticleOptions white = new DustParticleOptions(WHITE, 0.7f);
		if (t < NECKLACE) {
			// Trixx's dive: a streak of orange light curling into the Necklace
			float k = t / (float) NECKLACE;
			Vec3 from = neck.add(fwd.scale(1.1 * (1 - k))).add(right.scale(0.6 * Math.sin(k * 7))).add(0, 0.6 * (1 - k), 0);
			level.addParticle(orange, from.x, from.y, from.z, 0, 0, 0);
			level.addParticle(white, from.x, from.y, from.z, 0, 0, 0);
		} else if (t < SWIRL) {
			if (t == NECKLACE) {
				level.addParticle(ParticleTypes.FLASH, neck.x, neck.y, neck.z, 0, 0, 0);
			}
			for (int i = 0; i < 3; i++) {
				level.addParticle(i == 0 ? white : orange, neck.x, neck.y, neck.z, (rnd.nextDouble() - 0.5) * 0.14, (rnd.nextDouble() - 0.5) * 0.14,
						(rnd.nextDouble() - 0.5) * 0.14);
			}
		} else if (t < SWIRL_END + 2) {
			// fox fire: little flames and embers round the front of the swirl
			float f = MiraculousSequenceClient.smooth((t - (SWIRL + 6)) / (float) (SWIRL_END - SWIRL - 6));
			double y = h * 0.77 * (1 - f);
			double a = t * 0.45;
			for (int strand = 0; strand < 2; strand++) {
				double aa = a + strand * Math.PI;
				double x = feet.x + Math.cos(aa) * 0.6, z = feet.z + Math.sin(aa) * 0.6;
				level.addParticle(t % 3 == 0 ? ParticleTypes.SMALL_FLAME : orange, x, feet.y + y, z, 0, 0.01, 0);
				if (t % 2 == 0) {
					level.addParticle(amber, x, feet.y + y + 0.1, z, 0, 0.03, 0);
				}
			}
		} else if (t >= TAIL && t < TAIL + 14) {
			// the tail flicks out behind her (a puff of orange and white at the hip), then the ears pop up
			Vec3 hip = feet.add(0, h * 0.42, 0).subtract(fwd.scale(0.45));
			double sw = Math.sin((t - TAIL) * 0.8) * 0.4;
			level.addParticle(t % 2 == 0 ? orange : white, hip.x + right.x * sw, hip.y, hip.z + right.z * sw, 0, 0.01, 0);
			if (t == TAIL + 8) {
				for (int side = -1; side <= 1; side += 2) {
					Vec3 ear = feet.add(0, h + 0.1, 0).add(right.scale(0.2 * side));
					for (int i = 0; i < 8; i++) {
						level.addParticle(i % 2 == 0 ? white : orange, ear.x, ear.y, ear.z, (rnd.nextDouble() - 0.5) * 0.1, 0.05 + rnd.nextDouble() * 0.05,
								(rnd.nextDouble() - 0.5) * 0.1);
					}
				}
			}
		} else if (t == TAIL_END) {
			// the Flute appears in her hand
			Vec3 hand = feet.add(0, h * 0.6, 0).add(right.scale(0.38)).add(fwd.scale(0.2));
			for (int i = 0; i < 10; i++) {
				level.addParticle(i % 2 == 0 ? amber : white, hand.x, hand.y, hand.z, (rnd.nextDouble() - 0.5) * 0.12, (rnd.nextDouble() - 0.5) * 0.12,
						(rnd.nextDouble() - 0.5) * 0.12);
			}
		}
		float flight = RenaRougeSequence.fluteFlight(t);
		if (flight >= 0f) {
			Vec3 q = flutePos(feet, h, fwd, right, flight);
			level.addParticle(t % 2 == 0 ? amber : orange, q.x, q.y, q.z, 0, 0, 0);
		}
		if (t >= POSE && t < POSE + 20) {
			// the illusion fades off her: an orange shimmer in her shape, thinning out and rising away
			int n = (int) (14 * (1f - (t - POSE) / 20f)) + 1;
			for (int i = 0; i < n; i++) {
				double y = rnd.nextDouble() * h;
				double r = y > h * 0.75 ? 0.25 : y > h * 0.4 ? 0.32 : 0.2;
				double a = rnd.nextDouble() * Math.PI * 2;
				level.addParticle(i % 3 == 0 ? white : i % 2 == 0 ? amber : orange, feet.x + Math.cos(a) * r, feet.y + y, feet.z + Math.sin(a) * r,
						Math.cos(a) * 0.015, 0.03, Math.sin(a) * 0.015);
			}
			if (t == POSE) {
				Vec3 c = feet.add(0, h * 0.55, 0);
				level.addParticle(ParticleTypes.FLASH, c.x, c.y, c.z, 0, 0, 0);
				for (int i = 0; i < 14; i++) {
					level.addParticle(ParticleTypes.END_ROD, c.x, c.y, c.z, (rnd.nextDouble() - 0.5) * 0.3, (rnd.nextDouble() - 0.3) * 0.25,
							(rnd.nextDouble() - 0.5) * 0.3);
				}
			}
		}
	}

	private static void world(WorldRenderContext ctx, Player p, MiraculousState s, float t, float partial) {
		Vec3 feet = MiraculousSequenceClient.origin(s, t);
		double h = p.getBbHeight();
		float yaw = MiraculousSequenceClient.yaw(s, t);
		Vec3 fwd = MiraculousSequenceClient.forward(yaw);
		Vec3 right = MiraculousSequenceClient.right(fwd);
		if (t >= SWIRL && t < SWIRL_END + 8) {
			// the fox-fire spiral: two ribbons of orange flame winding round her from the Necklace down (and a little up)
			float f = MiraculousSequenceClient.smooth((t - (SWIRL + 6)) / (float) (SWIRL_END - SWIRL - 6));
			float fade = MiraculousSequenceClient.smooth((t - SWIRL) / 6f) * (1f - MiraculousSequenceClient.smooth((t - SWIRL_END) / 8f));
			double neck = h * 0.77;
			double low = neck * (1 - f) - 0.1;
			double high = neck + (h + 0.15 - neck) * Math.min(1f, f * 1.5f);
			for (int strand = 0; strand < 2; strand++) {
				double a0 = t * 0.32 + strand * Math.PI;
				double turns = 1.2 + 1.8 * f;
				Vec3[] down = MiraculousSequenceClient.helix(feet, neck, Math.min(neck - 0.05, low + 0.35), a0, a0 + turns * Math.PI * 2, 0.52, 0.62, 28);
				MiraculousSequenceClient.ribbon(ctx, down, 0.09f, GLOW, CORE, 0.85f * fade);
				Vec3[] up = MiraculousSequenceClient.helix(feet, neck, high, a0 + Math.PI / 2, a0 + Math.PI / 2 - Math.PI * 1.4, 0.5, 0.36, 12);
				MiraculousSequenceClient.ribbon(ctx, up, 0.06f, GLOW, CORE, 0.6f * fade);
			}
		}
		float flight = RenaRougeSequence.fluteFlight(t);
		if (flight >= 0f) {
			// the Flute spinning up into the air and back down into her hand
			Vec3 q = flutePos(feet, h, fwd, right, flight);
			Vec3 cam = ctx.camera().getPosition();
			PoseStack pose = ctx.matrixStack();
			pose.pushPose();
			pose.translate(q.x - cam.x, q.y - cam.y, q.z - cam.z);
			pose.mulPose(Axis.YP.rotationDegrees(-yaw));
			pose.mulPose(Axis.XP.rotationDegrees((t - FLUTE) * 40f));
			pose.translate(0f, -0.4f, 0f);
			RenaRougeRender.renderFluteModel(pose, ctx.consumers(), LightTexture.FULL_BRIGHT, 1f);
			pose.popPose();
		}
		if (t >= POSE && t < POSE + 14) {
			// the shimmer of the illusion breaking off her: an orange ripple spreading away
			float k = (t - POSE) / 14f;
			Vec3 c = feet.add(0, h * 0.5, 0);
			MiraculousSequenceClient.ring(ctx, c, 0.3f + 1.6f * k, 0.5f + 1.8f * k, GLOW, 0.7f * (1f - k));
			MiraculousSequenceClient.band(ctx, feet, 0.4f + 1.2f * k, (float) h * (1f - k), GLOW, 0.3f * (1f - k));
		}
	}

	// ---------------------------------------------------------------- the camera

	private static final float[][] CAM = {
			{ 0, 2.3f, 0, 1.55f, 1.5f },
			{ 14, 1.35f, 20, 1.5f, 1.42f },
			{ 26, 1.7f, 30, 1.5f, 1.4f },
			{ 36, 3.6f, 70, 1.0f, 1.0f },
			{ 66, 3.5f, 250, 1.4f, 1.1f },
			{ 92, 3.2f, 440, 1.4f, 1.2f },
			{ 98, 2.4f, 680, 1.0f, 0.9f },
			{ 116, 2.6f, 560, 1.4f, 1.3f },
			{ 124, 3.2f, 560, 1.0f, 1.9f },
			{ 138, 2.8f, 545, 1.3f, 1.3f },
			{ 148, 3.0f, 530, 1.1f, 1.1f },
			{ TOTAL, 3.6f, 512, 0.9f, 1.15f },
	};
}
