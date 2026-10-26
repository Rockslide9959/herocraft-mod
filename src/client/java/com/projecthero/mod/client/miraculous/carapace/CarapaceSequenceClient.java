package com.projecthero.mod.client.miraculous.carapace;

import static com.projecthero.mod.miraculous.carapace.CarapaceSequence.BRACELET;
import static com.projecthero.mod.miraculous.carapace.CarapaceSequence.BUBBLE;
import static com.projecthero.mod.miraculous.carapace.CarapaceSequence.PANELS;
import static com.projecthero.mod.miraculous.carapace.CarapaceSequence.PLATES;
import static com.projecthero.mod.miraculous.carapace.CarapaceSequence.PLATES_END;
import static com.projecthero.mod.miraculous.carapace.CarapaceSequence.POSE;
import static com.projecthero.mod.miraculous.carapace.CarapaceSequence.SHELL;
import static com.projecthero.mod.miraculous.carapace.CarapaceSequence.TOTAL;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.client.miraculous.MiraculousSequenceClient;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.carapace.CarapaceSequence;
import com.projecthero.mod.miraculous.data.MiraculousState;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: the client half of Carapace's long transformation ({@link CarapaceSequence}) on the shared frame
 * ({@link MiraculousSequenceClient}): Wayzz entering the Bracelet, the green hexagon shell-bubble closing round him from
 * the feet up (the Shelter's hexagon light, {@code CarapaceWorldFx}), the suit snapping on plate by plate inside it, the
 * bubble breaking as the Shield forms on his back and spins ({@link #renderShield}), and the braced pose with the Shield
 * raised and a hexagon flash.
 */
public final class CarapaceSequenceClient {
	private static final Vector3f GREEN = new Vector3f(0.25f, 0.68f, 0.35f);
	private static final Vector3f MINT = new Vector3f(0.6f, 0.95f, 0.6f);
	private static final Vector3f WHITE = new Vector3f(0.92f, 1.0f, 0.92f);
	private static final int GLOW = 0x3FAE5A;
	private static final int CORE = 0xD6FFD6;
	private static final float BUBBLE_R = 1.25f;

	/** Pose keys: head x, right arm x / z, left arm x / z, right leg x / z, left leg x / z, body x. */
	private static final float[] STAND = { 0f, 0f, 0.1f, 0f, -0.1f, 0f, 0.05f, 0f, -0.05f, 0f };
	/** The left fist up with the Bracelet, the right hand to it. */
	private static final float[] WRIST = { 0.25f, -1.35f, -0.55f, -1.6f, 0.05f, 0f, 0.05f, 0f, -0.05f, 0f };
	/** Curled up, arms crossed, as the bubble closes. */
	private static final float[] HUDDLE = { 0.3f, -1.3f, -0.75f, -1.3f, 0.75f, -0.15f, 0.05f, -0.1f, -0.05f, 0.2f };
	/** Opening out as the plates click on. */
	private static final float[] SPREAD = { -0.15f, -0.15f, 0.9f, -0.15f, -0.9f, 0f, 0.1f, 0f, -0.1f, -0.05f };
	/** Arms wide while the Shield spins on his back. */
	private static final float[] WIDE = { -0.25f, -0.1f, 1.35f, -0.1f, -1.35f, 0f, 0.12f, 0f, -0.12f, -0.05f };
	/** Braced: the Shield up before him on the left arm, the right fist cocked, feet wide. */
	private static final float[] BRACED = { 0.05f, 0.35f, 0.35f, -1.5f, 0.3f, -0.2f, 0.35f, 0.15f, -0.35f, 0.15f };

	private static final int[] AT = { 0, 6, BUBBLE + 6, PLATES + 10, SHELL + 4, POSE };
	private static final int[] BL = { 6, 8, 12, 8, 6, 1 };
	private static final float[][] KEYS = { STAND, WRIST, HUDDLE, SPREAD, WIDE, BRACED };

	public static final MiraculousSequenceClient.Scene SCENE = new MiraculousSequenceClient.Scene() {
		@Override
		public float[] pose(Player p, MiraculousState s, float t) {
			float[] pose = MiraculousSequenceClient.keyed(t, AT, BL, KEYS);
			if (t >= PLATES && t < PLATES_END) {
				// a little jolt each time a plate snaps on
				pose = pose.clone();
				pose[9] += 0.06f * (1f - plateFrac(t));
			}
			return pose;
		}

		@Override
		public void extraPose(Player p, HumanoidModel<?> model, float t, float w) {
			MiraculousSequenceClient.crouch(model, 0.55f * w * MiraculousSequenceClient.smooth((t - (POSE - 2)) / 6f));
		}

		@Override
		public void fx(ClientLevel level, Player p, MiraculousState s, int t, float yaw) {
			CarapaceSequenceClient.fx(level, p, t, yaw);
		}

		@Override
		public float[][] cam() {
			return CAM;
		}

		@Override
		public void world(WorldRenderContext ctx, Player p, MiraculousState s, float t, float partial) {
			CarapaceSequenceClient.world(ctx, p, s, t);
		}
	};

	private CarapaceSequenceClient() {
	}

	/** How far into the current plate's step, 0..1. */
	private static float plateFrac(float t) {
		float step = (PLATES_END - PLATES) / (float) PANELS;
		float k = (t - PLATES) / step;
		return k - (int) k;
	}

	/**
	 * The Shield during the sequence: hidden until it forms, then spinning on his back, then raised before him. True =
	 * handled (the layer draws nothing more); false = not playing.
	 */
	public static boolean renderShield(PoseStack pose, MultiBufferSource buffers, int light, HumanoidModel<?> parent, Player player, float partial,
			boolean slim) {
		float t = MiraculousSequenceClient.age(player, Kwami.WAYZZ, partial);
		if (t < 0f) {
			return false;
		}
		if (t < SHELL) {
			return true;
		}
		if (t < POSE) {
			float grow = MiraculousSequenceClient.smooth((t - SHELL) / 8f);
			// spinning fast as it forms, slowing to a stop as he turns to face front
			float k = Mth.clamp((t - SHELL) / (float) (POSE - SHELL), 0f, 1f);
			float angle = 1440f * (1f - (1f - k) * (1f - k));
			pose.pushPose();
			parent.body.translateAndRotate(pose);
			pose.translate(0f, 6f / 16f, 2.6f / 16f);
			pose.mulPose(Axis.ZP.rotationDegrees(12f + angle));
			pose.scale(grow, grow, grow);
			CarapaceRender.renderShieldModel(pose, buffers, light, 1f);
			pose.popPose();
			return true;
		}
		CarapaceRender.renderShield(pose, buffers, light, parent, CarapaceRender.ShieldAt.GUARD, slim);
		return true;
	}

	// ---------------------------------------------------------------- the light show

	private static void fx(ClientLevel level, Player p, int t, float yaw) {
		RandomSource rnd = level.random;
		double h = p.getBbHeight();
		Vec3 feet = p.position();
		Vec3 fwd = MiraculousSequenceClient.forward(yaw);
		Vec3 right = MiraculousSequenceClient.right(fwd);
		Vec3 wrist = feet.add(0, h * 0.72, 0).add(fwd.scale(0.55)).add(right.scale(-0.3));
		DustParticleOptions green = new DustParticleOptions(GREEN, 1.1f);
		DustParticleOptions mint = new DustParticleOptions(MINT, 0.8f);
		DustParticleOptions white = new DustParticleOptions(WHITE, 0.7f);
		if (t < BRACELET) {
			// Wayzz swims in: a lazy green streak curling into the Bracelet
			float k = t / (float) BRACELET;
			Vec3 from = wrist.add(fwd.scale(0.9 * (1 - k))).add(right.scale(-0.6 * Math.sin(k * 4) * (1 - k))).add(0, 0.5 * (1 - k), 0);
			level.addParticle(green, from.x, from.y, from.z, 0, 0, 0);
			level.addParticle(mint, from.x, from.y, from.z, 0, 0, 0);
		} else if (t < BUBBLE + 4) {
			if (t == BRACELET) {
				level.addParticle(ParticleTypes.FLASH, wrist.x, wrist.y, wrist.z, 0, 0, 0);
			}
			level.addParticle(t % 2 == 0 ? mint : green, wrist.x, wrist.y, wrist.z, (rnd.nextDouble() - 0.5) * 0.1, (rnd.nextDouble() - 0.5) * 0.1,
					(rnd.nextDouble() - 0.5) * 0.1);
		}
		float b = CarapaceSequence.bubble(t);
		if (b > 0f && b < 1f) {
			// sparks along the closing rim of the bubble
			double lat = (-1 + 2 * b) * Math.PI / 2;
			Vec3 c = feet.add(0, h * 0.5, 0);
			double y = Math.sin(lat) * BUBBLE_R, q = Math.cos(lat) * BUBBLE_R;
			for (int i = 0; i < 3; i++) {
				double a = rnd.nextDouble() * Math.PI * 2;
				level.addParticle(i == 0 ? white : mint, c.x + Math.cos(a) * q, c.y + y, c.z + Math.sin(a) * q, 0, 0.01, 0);
			}
		}
		if (t >= PLATES && t < PLATES_END && CarapaceSequence.plateClick(t)) {
			// a plate snaps on: a puff of green round the band of him it covers (out from the chest)
			int k = CarapaceSequence.plate(t);
			double spread = (k + 1) / (double) PANELS;
			for (int side = -1; side <= 1; side += 2) {
				double y = h * 0.62 + side * spread * h * (side > 0 ? 0.4 : 0.62);
				for (int i = 0; i < 8; i++) {
					double a = rnd.nextDouble() * Math.PI * 2;
					level.addParticle(i % 3 == 0 ? white : green, feet.x + Math.cos(a) * 0.36, feet.y + y, feet.z + Math.sin(a) * 0.36, Math.cos(a) * 0.04, 0,
							Math.sin(a) * 0.04);
				}
			}
		}
		if (t == SHELL) {
			// the bubble breaks
			Vec3 c = feet.add(0, h * 0.5, 0);
			for (int i = 0; i < 80; i++) {
				Vec3 dir = new Vec3(rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.5).normalize();
				Vec3 q = c.add(dir.scale(BUBBLE_R));
				level.addParticle(i % 3 == 0 ? white : i % 2 == 0 ? mint : green, q.x, q.y, q.z, dir.x * 0.18, dir.y * 0.18, dir.z * 0.18);
			}
		} else if (t == POSE) {
			Vec3 c = feet.add(0, h * 0.62, 0).add(fwd.scale(0.6));
			level.addParticle(ParticleTypes.FLASH, c.x, c.y, c.z, 0, 0, 0);
			for (int i = 0; i < 16; i++) {
				level.addParticle(ParticleTypes.END_ROD, c.x, c.y, c.z, (rnd.nextDouble() - 0.5) * 0.3, (rnd.nextDouble() - 0.4) * 0.25,
						(rnd.nextDouble() - 0.5) * 0.3);
			}
		}
	}

	private static void world(WorldRenderContext ctx, Player p, MiraculousState s, float t) {
		Vec3 feet = MiraculousSequenceClient.origin(s, t);
		double h = p.getBbHeight();
		Vec3 cam = ctx.camera().getPosition();
		float b = CarapaceSequence.bubble(t);
		boolean breaking = t >= SHELL && t < SHELL + 6;
		if (b > 0f || breaking) {
			// the hexagon shell-bubble: closing from the feet up, pulsing as each plate lands, flying apart at the end
			float r = BUBBLE_R;
			float a = 0.55f + 0.1f * Mth.sin(t * 0.3f);
			if (t >= PLATES && t < PLATES_END) {
				a += 0.3f * (1f - plateFrac(t));
			}
			float to = -1f + 2f * b;
			if (breaking) {
				float k = (t - SHELL) / 6f;
				r *= 1f + 0.5f * k;
				a *= 1f - k;
				to = 1f;
			}
			VertexConsumer vc = ctx.consumers().getBuffer(RenderType.entityTranslucentEmissive(CarapaceRender.HEX));
			PoseStack pose = ctx.matrixStack();
			pose.pushPose();
			pose.translate(-cam.x, -cam.y, -cam.z);
			CarapaceWorldFx.sphere(vc, pose.last(), feet.add(0, h * 0.5, 0), r, -1f, to, a, t * 0.01f);
			pose.popPose();
		}
		if (t >= POSE && t < POSE + 12) {
			// the hexagon flash off the raised Shield
			float k = (t - POSE) / 12f;
			float yaw = MiraculousSequenceClient.yaw(s, t);
			Vec3 fwd = MiraculousSequenceClient.forward(yaw);
			Vec3 right = MiraculousSequenceClient.right(fwd);
			Vec3 c = feet.add(0, h * 0.62, 0).add(fwd.scale(0.6 + 0.3 * k));
			float rad = 0.35f + 1.2f * k;
			Vec3[] hex = new Vec3[7];
			for (int i = 0; i <= 6; i++) {
				double a = Math.PI / 2 + i * Math.PI / 3;
				hex[i] = c.add(right.scale(Math.cos(a) * rad)).add(0, Math.sin(a) * rad, 0);
			}
			MiraculousSequenceClient.ribbon(ctx, hex, 0.09f, GLOW, CORE, 1f - k);
		}
	}

	// ---------------------------------------------------------------- the camera

	private static final float[][] CAM = {
			{ 0, 2.2f, 0, 1.5f, 1.4f },
			{ 14, 1.35f, -35, 1.3f, 1.15f },
			{ 24, 2.1f, -20, 1.2f, 1.0f },
			{ 48, 3.5f, 30, 1.0f, 0.9f },
			{ 80, 3.0f, 120, 1.3f, 1.0f },
			{ 106, 2.8f, 250, 1.3f, 1.05f },
			{ 116, 2.2f, 360, 1.25f, 1.1f },
			{ 140, 2.6f, 360, 1.1f, 1.0f },
			{ 150, 3.2f, 372, 0.6f, 1.0f },
			{ TOTAL, 3.6f, 380, 0.5f, 1.0f },
	};
}
