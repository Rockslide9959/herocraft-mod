package com.projecthero.mod.client.miraculous.ryuko;

import static com.projecthero.mod.miraculous.ryuko.RyukoSequence.BOLT;
import static com.projecthero.mod.miraculous.ryuko.RyukoSequence.BOLT_END;
import static com.projecthero.mod.miraculous.ryuko.RyukoSequence.CHOKER;
import static com.projecthero.mod.miraculous.ryuko.RyukoSequence.MASK;
import static com.projecthero.mod.miraculous.ryuko.RyukoSequence.MASK_END;
import static com.projecthero.mod.miraculous.ryuko.RyukoSequence.POSE;
import static com.projecthero.mod.miraculous.ryuko.RyukoSequence.SLASH;
import static com.projecthero.mod.miraculous.ryuko.RyukoSequence.SWORD;
import static com.projecthero.mod.miraculous.ryuko.RyukoSequence.TOTAL;
import static com.projecthero.mod.miraculous.ryuko.RyukoSequence.WATER;
import static com.projecthero.mod.miraculous.ryuko.RyukoSequence.WIND;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.miraculous.MiraculousSequenceClient;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.data.MiraculousState;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3: the client half of Ryuko's long transformation ({@code RyukoSequence}) on the shared frame
 * ({@link MiraculousSequenceClient}): Longg's streak spiralling into the choker and a red flash; a cyclone winding up her as she
 * turns; a ring of water climbing her; a jagged bolt striking her from above; her hand sweeping across her eyes; the sword
 * forming in her hand and cutting the air; and the last pose, sword high, with a dragon of light circling her.
 */
public final class RyukoSequenceClient {
	private static final Vector3f RED = new Vector3f(0.86f, 0.14f, 0.12f);
	private static final Vector3f WIND_C = new Vector3f(0.85f, 0.97f, 0.93f);
	private static final Vector3f WATER_C = new Vector3f(0.25f, 0.66f, 1f);
	private static final Vector3f BOLT_C = new Vector3f(1f, 0.92f, 0.4f);

	/** Pose keys: head x, right arm x / z, left arm x / z, right leg x / z, left leg x / z, body x. */
	private static final float[] STAND = { 0f, 0f, 0.1f, 0f, -0.1f, 0f, 0.05f, 0f, -0.05f, 0f };
	/** Both hands at her throat, on the choker, chin up. */
	private static final float[] CHOKER_P = { -0.35f, -2.1f, -0.5f, -2.1f, 0.5f, 0f, 0.05f, 0f, -0.05f, 0f };
	/** Arms out wide and low, the storm lifting her. */
	private static final float[] WIND_P = { -0.1f, -0.1f, 1.0f, -0.1f, -1.0f, 0f, 0.05f, 0f, -0.05f, 0f };
	/** Arms sweeping up with the water. */
	private static final float[] WATER_P = { -0.2f, -0.5f, 1.9f, -0.5f, -1.9f, 0f, 0.08f, 0f, -0.08f, -0.03f };
	/** Struck: thrown open, head back. */
	private static final float[] BOLT_P = { -0.5f, -0.2f, 2.4f, -0.2f, -2.4f, 0f, 0.3f, 0f, -0.3f, -0.08f };
	/** Her right hand at her eyes. */
	private static final float[] MASK_P = { 0f, -2.2f, 0f, 0.25f, -0.5f, 0f, 0.05f, 0f, -0.05f, 0f };
	/** Her right hand held out, open, for the sword. */
	private static final float[] SWORD_P = { 0.1f, -1.5f, 0.1f, 0.2f, -0.4f, 0f, 0.05f, 0f, -0.05f, 0f };
	/** High and ready to cut. */
	private static final float[] SLASH_UP_P = { 0f, -2.9f, 0.3f, 0.3f, -0.5f, -0.1f, 0.1f, 0.1f, -0.1f, 0f };
	/** Cut through, low and across. */
	private static final float[] SLASH_P = { 0.15f, -0.7f, -0.3f, 0.3f, -0.5f, -0.25f, 0.1f, 0.25f, -0.1f, 0.12f };
	/** Sword high, off hand at the hip, one foot forward, chin up. */
	private static final float[] POSE_P = { -0.18f, -3.0f, 0.2f, 0.2f, -0.55f, -0.3f, 0.06f, 0.2f, -0.08f, -0.03f };

	private static final int[] AT = { 0, 8, WIND, WATER, BOLT, MASK, SWORD, 128, SLASH, POSE };
	private static final int[] BL = { 6, 6, 8, 8, 3, 6, 6, 5, 3, 1 };
	private static final float[][] KEYS = { STAND, CHOKER_P, WIND_P, WATER_P, BOLT_P, MASK_P, SWORD_P, SLASH_UP_P, SLASH_P, POSE_P };

	/** Keys: tick, distance, angle round her from the front (deg), camera height, height looked at. */
	private static final float[][] CAM = {
			{ 0, 1.9f, 150, 1.75f, 1.6f },
			{ 16, 1.1f, 178, 1.65f, 1.55f },
			{ 24, 3.0f, 190, 1.1f, 0.9f },
			{ 56, 3.2f, 280, 1.2f, 1.0f },
			{ 84, 3.6f, 350, 0.7f, 1.3f },
			{ 94, 3.0f, 356, 0.9f, 1.3f },
			{ 98, 1.5f, 360, 1.62f, 1.55f },
			{ 112, 1.5f, 360, 1.62f, 1.55f },
			{ 118, 2.2f, 385, 1.5f, 1.3f },
			{ 134, 3.0f, 405, 1.5f, 1.1f },
			{ 146, 2.6f, 372, 1.0f, 1.4f },
			{ TOTAL, 3.2f, 362, 0.9f, 1.5f },
	};

	public static final MiraculousSequenceClient.Scene SCENE = new MiraculousSequenceClient.Scene() {
		@Override
		public float[] pose(Player p, MiraculousState s, float t) {
			return MiraculousSequenceClient.keyed(t, AT, BL, KEYS);
		}

		@Override
		public void extraPose(Player p, HumanoidModel<?> model, float t, float w) {
			float e = w * (MiraculousSequenceClient.smooth((t - (MASK - 6f)) / 6f) - MiraculousSequenceClient.smooth((t - MASK_END) / 6f));
			if (e > 0f) {
				// the hand wipes across her eyes
				float sweep = Mth.lerp(Mth.clamp((t - MASK) / (MASK_END - MASK), 0f, 1f), 0.75f, -0.5f);
				model.rightArm.yRot = Mth.lerp(e, model.rightArm.yRot, sweep);
			}
		}

		@Override
		public void fx(ClientLevel level, Player p, MiraculousState s, int t, float yaw) {
			RyukoSequenceClient.fx(level, p, t);
		}

		@Override
		public float[][] cam() {
			return CAM;
		}

		@Override
		public void world(WorldRenderContext ctx, Player p, MiraculousState s, float t, float partial) {
			RyukoSequenceClient.world(ctx, p, t, partial);
		}
	};

	private RyukoSequenceClient() {
	}

	/** Is the sword in her hand yet? True outside the long transformation. */
	public static boolean swordOut(Player player, float partial) {
		float t = MiraculousSequenceClient.age(player, Kwami.LONGG, partial);
		return t < 0f || t >= SWORD + 6;
	}

	private static void fx(ClientLevel level, Player p, int t) {
		RandomSource rnd = level.random;
		double h = p.getBbHeight();
		Vec3 feet = p.position();
		Vec3 throat = feet.add(0, h * 0.8, 0);
		if (t < CHOKER) {
			float k = t / (float) CHOKER;
			double a = t * 1.1;
			double r = 1.1 * (1 - k) + 0.04;
			level.addParticle(new DustParticleOptions(RED, 1.0f), throat.x + Math.cos(a) * r, throat.y + 0.5 * (1 - k), throat.z + Math.sin(a) * r, 0, 0, 0);
		} else if (t == CHOKER) {
			level.addParticle(ParticleTypes.FLASH, throat.x, throat.y, throat.z, 0, 0, 0);
		} else if (t >= WIND && t < WATER) {
			for (int i = 0; i < 4; i++) {
				double a = t * 0.8 + i * Math.PI / 2;
				double y = h * rnd.nextDouble();
				level.addParticle(ParticleTypes.CLOUD, feet.x + Math.cos(a) * 1.1, feet.y + y, feet.z + Math.sin(a) * 1.1, 0, 0.02, 0);
			}
		} else if (t >= WATER && t < BOLT) {
			double y = h * (t - WATER) / (float) (BOLT - WATER);
			for (int i = 0; i < 8; i++) {
				double a = rnd.nextDouble() * Math.PI * 2;
				level.addParticle(ParticleTypes.SPLASH, feet.x + Math.cos(a) * 0.9, feet.y + y, feet.z + Math.sin(a) * 0.9, 0, 0.05, 0);
			}
		} else if (t == BOLT) {
			level.addParticle(ParticleTypes.FLASH, feet.x, feet.y + 1.0, feet.z, 0, 0, 0);
			for (int i = 0; i < 24; i++) {
				level.addParticle(ParticleTypes.ELECTRIC_SPARK, feet.x, feet.y + rnd.nextDouble() * h, feet.z, (rnd.nextDouble() - 0.5) * 0.7,
						(rnd.nextDouble() - 0.3) * 0.5, (rnd.nextDouble() - 0.5) * 0.7);
			}
		} else if (t >= BOLT && t < MASK && t % 2 == 0) {
			level.addParticle(ParticleTypes.ELECTRIC_SPARK, feet.x + (rnd.nextDouble() - 0.5), feet.y + rnd.nextDouble() * h, feet.z + (rnd.nextDouble() - 0.5), 0, 0, 0);
		} else if (t == SWORD + 6) {
			for (int i = 0; i < 14; i++) {
				level.addParticle(new DustParticleOptions(BOLT_C, 0.8f), feet.x, feet.y + 1.3, feet.z, (rnd.nextDouble() - 0.5) * 0.25, (rnd.nextDouble() - 0.3) * 0.25,
						(rnd.nextDouble() - 0.5) * 0.25);
			}
		} else if (t >= POSE && t % 3 == 0) {
			level.addParticle(new DustParticleOptions(WIND_C, 0.7f), feet.x + (rnd.nextDouble() - 0.5) * 3, feet.y + rnd.nextDouble() * 2.5, feet.z + (rnd.nextDouble() - 0.5) * 3, 0, 0.03, 0);
			level.addParticle(new DustParticleOptions(WATER_C, 0.7f), feet.x + (rnd.nextDouble() - 0.5) * 3, feet.y + rnd.nextDouble() * 2.5, feet.z + (rnd.nextDouble() - 0.5) * 3, 0, 0.03, 0);
		}
	}

	private static void world(WorldRenderContext ctx, Player p, float t, float partial) {
		if (ctx.consumers() == null || ctx.matrixStack() == null) {
			return;
		}
		PoseStack pose = ctx.matrixStack();
		Vec3 cam = ctx.camera().getPosition();
		Vec3 feet = p.getPosition(partial);
		pose.pushPose();
		pose.translate(-cam.x, -cam.y, -cam.z);
		Matrix4f m = pose.last().pose();
		VertexConsumer vc = ctx.consumers().getBuffer(RenderType.debugQuads());
		double h = p.getBbHeight();
		if (t >= WIND && t < WATER + 6) {
			// the cyclone: three spiral ribbons, winding up her
			float fade = Mth.clamp((t - WIND) / 6f, 0f, 1f) * Mth.clamp((WATER + 6 - t) / 6f, 0f, 1f);
			for (int arm = 0; arm < 3; arm++) {
				Vec3 last = null;
				for (int i = 0; i <= 24; i++) {
					float k = i / 24f;
					double a = t * 0.55 + arm * Mth.TWO_PI / 3 + k * 7.0;
					double r = 0.75 + 0.45 * (1 - k);
					Vec3 q = feet.add(Math.cos(a) * r, k * (h + 0.5), Math.sin(a) * r);
					if (last != null) {
						RyukoWorldFx.ribbon(vc, m, cam, last, q, 0.26f, 0xE6FAF4, 0.62f * fade);
					}
					last = q;
				}
			}
		}
		if (t >= WATER && t < BOLT + 2) {
			// the wave: a thick ring rising up her
			float k = (t - WATER) / (BOLT - WATER);
			double y = Math.min(1f, k) * h;
			float fade = Mth.clamp((BOLT + 2 - t) / 6f, 0f, 1f);
			for (int i = 0; i < 32; i++) {
				double a0 = i * Mth.TWO_PI / 32, a1 = (i + 1) * Mth.TWO_PI / 32;
				Vec3 lo0 = feet.add(Math.cos(a0) * 0.8, y - 0.35, Math.sin(a0) * 0.8), lo1 = feet.add(Math.cos(a1) * 0.8, y - 0.35, Math.sin(a1) * 0.8);
				Vec3 hi0 = feet.add(Math.cos(a0) * 0.62, y + 0.35, Math.sin(a0) * 0.62), hi1 = feet.add(Math.cos(a1) * 0.62, y + 0.35, Math.sin(a1) * 0.62);
				RyukoWorldFx.quad(vc, m, lo0, lo1, hi1, hi0, 0x3FA8FF, 0.7f * fade);
			}
		}
		if (t >= POSE) {
			// the dragon of light: a long ribbon round her, its colour running red to gold
			float grow = Mth.clamp((t - POSE) / 10f, 0f, 1f) * Mth.clamp((TOTAL - t) / 6f, 0f, 1f);
			Vec3 last = null;
			for (int i = 0; i <= 44; i++) {
				float k = i / 44f;
				double a = t * 0.2 + k * 10.0;
				double r = 1.5 + 0.3 * Mth.sin(k * 9f);
				Vec3 q = feet.add(Math.cos(a) * r, 0.2 + k * 2.6 + 0.2 * Mth.sin(k * 14f + t * 0.3f), Math.sin(a) * r);
				if (last != null) {
					RyukoWorldFx.ribbon(vc, m, cam, last, q, 0.42f * (1f - k * 0.75f), k < 0.5f ? 0xD22A28 : 0xF4BE3C, 0.85f * grow);
				}
				last = q;
			}
		}
		VertexConsumer glow = ctx.consumers().getBuffer(RenderType.lightning());
		if (t >= BOLT && t < BOLT_END) {
			// the bolt, jagged, from high above down into her
			float fade = 1f - (t - BOLT) / (BOLT_END - BOLT);
			int seed = (int) t / 2;
			Vec3 last = feet.add(0, 14, 0);
			for (int i = 1; i <= 9; i++) {
				double y = 14.0 - i * (13.0 / 9.0);
				double jx = i == 9 ? 0 : Math.sin(seed * 12.9 + i * 78.2) * 0.6;
				double jz = i == 9 ? 0 : Math.sin(seed * 39.3 + i * 11.1) * 0.6;
				Vec3 q = feet.add(jx, y, jz);
				RyukoWorldFx.ribbon(glow, m, cam, last, q, 0.7f, 0xFFE45A, 0.95f * fade);
				RyukoWorldFx.ribbon(glow, m, cam, last, q, 0.25f, 0xFFFFFF, fade);
				last = q;
			}
		}
		pose.popPose();
	}
}
