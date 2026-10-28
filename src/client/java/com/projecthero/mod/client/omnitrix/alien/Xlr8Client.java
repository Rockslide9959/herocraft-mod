package com.projecthero.mod.client.omnitrix.alien;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.AlienParts;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.xlr8.Xlr8Kit;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.1: XLR8's client kit -- the blue-and-black Kineceleran skin with a striped chest, a black visor across the blue helmet
 * (a blue band over the crown, a little crest), a long striped tail that streams out flat as he runs, ball feet, claws, and
 * translucent AFTERIMAGES behind him while he dashes or runs flat out. A pose (third and first person) for every move.
 */
public final class Xlr8Client implements AlienClientKit {
	private static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/omnitrix/xlr8.png");
	private static final ResourceLocation EXTRAS = ProjectHeroMod.id("textures/entity/omnitrix/xlr8_extras.png");

	// the extras sheet's swatches (32x32): u0, v0, u1, v1
	private static final float[] BLACK = sw(0, 0, 4, 4);
	private static final float[] BLUE = sw(4, 0, 4, 4);
	private static final float[] BAND = sw(8, 0, 4, 4);
	private static final float[] WHITE = sw(12, 0, 4, 4);
	private static final float[] WHEEL = sw(16, 0, 8, 8);
	private static final float[] TAIL = sw(0, 8, 8, 8);
	private static final float[] VISOR = sw(0, 16, 16, 8);
	private static final float[] GLINT = sw(24, 0, 4, 4);

	private static float[] sw(int x, int y, int w, int h) {
		return new float[] { x / 32f, y / 32f, (x + w) / 32f, (y + h) / 32f };
	}

	// {tick, rArm xyz, lArm xyz, body xy, rLeg xyz, lLeg xyz, headX}
	private static final float[] JAB_R = { -1.65f, 0.25f, 0, 0.6f, 0, 0, 0.3f, -0.35f, 0.2f, 0, 0, -0.3f, 0, 0, 0 };
	private static final float[] JAB_L = { 0.6f, 0, 0, -1.65f, -0.25f, 0, 0.3f, 0.35f, -0.3f, 0, 0, 0.2f, 0, 0, 0 };
	private static final float[][] FLURRY = alternate(12, 1, JAB_R, JAB_L);
	private static final float[] RUN_A = { 0.9f, 0, 0.25f, 0.9f, 0, -0.25f, 0.55f, 0, -1.1f, 0, 0, 0.8f, 0, 0, -0.5f };
	private static final float[] RUN_B = { 0.9f, 0, 0.25f, 0.9f, 0, -0.25f, 0.55f, 0, 0.8f, 0, 0, -1.1f, 0, 0, -0.5f };
	private static final float[][] TORNADO = alternate(60, 3, RUN_A, RUN_B);
	private static final float[][] MINE = alternate(14, 2, new float[] { -1.2f, 0.2f, 0, -0.4f, 0, 0, 0.6f, 0, -0.8f, 0, 0, 0.6f, 0, 0, -0.3f },
			new float[] { -0.4f, 0, 0, -1.2f, -0.2f, 0, 0.6f, 0, 0.6f, 0, 0, -0.8f, 0, 0, -0.3f });
	private static final float[] KICK_R = { -0.4f, 0, 0.9f, -0.4f, 0, -0.9f, -0.25f, 0.3f, -1.55f, 0.1f, 0, 0.2f, 0, 0, 0.1f };
	private static final float[] KICK_L = { -0.4f, 0, 0.9f, -0.4f, 0, -0.9f, -0.25f, -0.3f, 0.2f, 0, 0, -1.55f, -0.1f, 0, 0.1f };
	private static final float[][] KICKS = alternate(16, 2, KICK_R, KICK_L);
	private static final float[][] PUNCH = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, 0.9f, 0, 0.2f, -0.6f, 0, 0, 0.45f, 0.5f, -0.7f, 0, 0, 0.6f, 0, 0, -0.3f),
			QuirkKeyframes.f(4, -1.75f, 0.2f, 0, 0.8f, 0, 0, 0.35f, -0.45f, 0.3f, 0, 0, -0.5f, 0, 0, 0), QuirkKeyframes.rest(12) };
	private static final float[][] SNATCH = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -1.6f, -0.1f, 0, 0.3f, 0, 0, 0.35f, -0.3f, 0.3f, 0, 0, -0.4f, 0, 0, 0),
			QuirkKeyframes.f(5, -0.6f, -0.6f, 0, 0.2f, 0, 0, -0.1f, 0.2f, 0, 0, 0, 0, 0, 0, 0.2f), QuirkKeyframes.rest(12) };
	private static final float[][] DASH = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, 1.1f, 0, 0.3f, 1.1f, 0, -0.3f, 0.75f, 0, -0.9f, 0, 0, 0.7f, 0, 0, -0.65f),
			QuirkKeyframes.f(8, 1.1f, 0, 0.3f, 1.1f, 0, -0.3f, 0.75f, 0, 0.7f, 0, 0, -0.9f, 0, 0, -0.65f), QuirkKeyframes.rest(12) };
	private static final float[][] VISOR_TAP = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -2.5f, -0.6f, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -0.1f),
			QuirkKeyframes.f(7, -2.4f, -0.6f, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -0.1f), QuirkKeyframes.rest(11) };
	private static final float[][] WHIRL = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -0.2f, 0, 1.5f, -0.2f, 0, -1.5f, 0.1f, 1.6f, 0, 0, 0.2f, 0, 0, -0.2f, 0),
			QuirkKeyframes.f(6, -0.2f, 0, 1.5f, -0.2f, 0, -1.5f, 0.1f, 4.4f, 0, 0, 0.2f, 0, 0, -0.2f, 0),
			QuirkKeyframes.f(10, -0.2f, 0, 1.5f, -0.2f, 0, -1.5f, 0.1f, 6.28f, 0, 0, 0.2f, 0, 0, -0.2f, 0),
			QuirkKeyframes.f(13, 0, 0, 0, 0, 0, 0, 0, 6.28f, 0, 0, 0, 0, 0, 0, 0) };
	private static final float[][] BURST = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -0.9f, 0.7f, -0.3f, -0.9f, -0.7f, 0.3f, 0.3f, 0, -0.3f, 0, 0.1f, -0.3f, 0, -0.1f, 0.3f),
			QuirkKeyframes.f(6, -0.4f, 0, 1.6f, -0.4f, 0, -1.6f, -0.25f, 0, 0, 0, 0.3f, 0, 0, -0.3f, -0.5f), QuirkKeyframes.rest(16) };
	private static final float[][] JAB = { QuirkKeyframes.rest(0), QuirkKeyframes.f(1.5f, -1.65f, 0.25f, 0, 0.3f, 0, 0, 0.15f, -0.3f, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.f(3, 0.3f, 0, 0, -1.65f, -0.25f, 0, 0.15f, 0.3f, 0, 0, 0, 0, 0, 0, 0), QuirkKeyframes.rest(6) };
	private static final float[][] SWEEP = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -0.6f, 0, 1.2f, -0.6f, 0, -1.2f, 0.5f, -0.6f, -0.4f, 0, 0.2f, -0.4f, 0, -0.2f, 0.3f),
			QuirkKeyframes.f(7, -0.6f, 0, 1.2f, -0.6f, 0, -1.2f, 0.5f, 0.6f, -0.4f, 0, 0.2f, -0.4f, 0, -0.2f, 0.3f), QuirkKeyframes.rest(12) };

	private static final FirstPersonClip FP_FLURRY = fpAlternate(12, 1, new float[] { -22, 24, 0, 0.16f, 0.1f, -0.25f }, new float[] { 0, -5, 0, 0, -0.05f, 0.05f })
			.both();
	private static final FirstPersonClip FP_RUN = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 30, -10, 0, -0.1f, -0.25f, 0.1f),
			FirstPersonClip.f(55, 30, -10, 0, -0.1f, -0.25f, 0.1f), FirstPersonClip.f(60, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_PUNCH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, 25, -10, 0, -0.05f, -0.15f, 0.2f),
			FirstPersonClip.f(4, -25, 28, 0, 0.2f, 0.1f, -0.35f), FirstPersonClip.f(12, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_SNATCH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -25, 20, 0, 0.15f, 0.1f, -0.35f),
			FirstPersonClip.f(5, 10, -15, -20, -0.1f, -0.05f, 0.1f), FirstPersonClip.f(12, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_DASH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, 35, -15, 0, -0.1f, -0.3f, 0.15f),
			FirstPersonClip.f(8, 35, -15, 0, -0.1f, -0.3f, 0.15f), FirstPersonClip.f(12, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_VISOR = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -75, 30, 0, 0.15f, 0.45f, 0.1f),
			FirstPersonClip.f(7, -72, 30, 0, 0.15f, 0.45f, 0.1f), FirstPersonClip.f(11, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_WIDE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -20, -20, 0, -0.1f, 0.1f, 0),
			FirstPersonClip.f(6, -30, -45, 30, -0.3f, 0.15f, 0.05f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_KICK = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 20, -30, 30, -0.25f, -0.1f, 0.1f),
			FirstPersonClip.f(14, 20, -30, 30, -0.25f, -0.1f, 0.1f), FirstPersonClip.f(17, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_MINE = fpAlternate(14, 2, new float[] { -10, 20, 0, 0.12f, -0.05f, -0.3f }, new float[] { 10, 0, 0, 0, -0.15f, 0 }).both();
	private static final FirstPersonClip FP_JAB = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(1.5f, -18, 24, 0, 0.15f, 0.08f, -0.25f),
			FirstPersonClip.f(3, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4.5f, -18, 24, 0, 0.15f, 0.08f, -0.25f), FirstPersonClip.f(6, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_SWEEP = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 30, -40, 0, -0.2f, -0.25f, 0),
			FirstPersonClip.f(7, 30, 40, 0, 0.25f, -0.25f, 0), FirstPersonClip.f(12, 0, 0, 0, 0, 0, 0));

	/** A pose that flips between {@code a} and {@code b} every {@code period} ticks for {@code len}, then lets go. */
	static float[][] alternate(int len, int period, float[] a, float[] b) {
		int n = len / period + 2;
		float[][] out = new float[n + 1][];
		out[0] = QuirkKeyframes.rest(0);
		for (int i = 1; i < n; i++) {
			float[] src = i % 2 == 1 ? a : b;
			float[] f = new float[QuirkKeyframes.COLS];
			f[0] = Math.min(len, i * period);
			System.arraycopy(src, 0, f, 1, QuirkKeyframes.COLS - 1);
			out[i] = f;
		}
		out[n] = QuirkKeyframes.rest(len + 3);
		return out;
	}

	/** A first-person clip that flips between two arm offsets {pitch, yaw, roll, x, y, z}. */
	static FirstPersonClip fpAlternate(int len, int period, float[] a, float[] b) {
		int n = len / period + 1;
		float[][] fr = new float[n + 2][];
		fr[0] = FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0);
		for (int i = 1; i <= n; i++) {
			float[] s = i % 2 == 1 ? a : b;
			fr[i] = FirstPersonClip.f(Math.min(len, i * period), s[0], s[1], s[2], s[3], s[4], s[5]);
		}
		fr[n + 1] = FirstPersonClip.f(len + 3, 0, 0, 0, 0, 0, 0);
		return FirstPersonClip.of(fr);
	}

	@Override
	public ResourceLocation skin() {
		return SKIN;
	}

	private static void box(PoseStack pose, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, float[] uv, int light) {
		AlienParts.box(pose, vc, x0, y0, z0, x1, y1, z1, uv[0], uv[1], uv[2], uv[3], 0xFFFFFFFF, light);
	}

	/** Horizontal speed in blocks per tick (works for every player in view: from the last two positions). */
	private static float speed(AbstractClientPlayer p) {
		double dx = p.getX() - p.xo, dz = p.getZ() - p.zo;
		return (float) Math.sqrt(dx * dx + dz * dz);
	}

	@Override
	public void renderExtras(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model, float partial,
			float age) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(EXTRAS));
		float sp = speed(p);
		// the helmet: the visor across the face, the band over the crown, a little crest at the back
		AlienParts.on(pose, model.head, () -> {
			box(pose, vc, -4.35f, -5.6f, -4.5f, 4.35f, -2.6f, -3.7f, VISOR, light);
			box(pose, vc, -4.35f, -5.6f, -3.7f, -3.9f, -2.9f, 1.0f, BLACK, light);
			box(pose, vc, 3.9f, -5.6f, -3.7f, 4.35f, -2.9f, 1.0f, BLACK, light);
			box(pose, vc, -1.0f, -8.45f, -4.35f, 1.0f, -7.6f, 4.35f, BAND, light);
			box(pose, vc, -1.0f, -7.6f, -4.45f, 1.0f, -5.6f, -3.9f, BAND, light);
			pose.pushPose();
			pose.translate(0, -8.0f / 16f, 2.5f / 16f);
			pose.mulPose(Axis.XP.rotation(0.55f));
			box(pose, vc, -0.5f, -2.6f, -0.5f, 0.5f, 0f, 3.5f, BLUE, light);
			pose.popPose();
		});
		// the tail: four striped segments from the small of the back, streaming out flatter the faster he runs
		AlienParts.on(pose, model.body, () -> {
			pose.pushPose();
			pose.translate(0, 10.5f / 16f, 1.6f / 16f);
			float lift = Mth.clamp(sp * 1.4f, 0f, 0.6f);
			float[] w = { 3.2f, 2.6f, 2.0f, 1.3f };
			float[] len = { 4.5f, 4.5f, 4.0f, 3.5f };
			for (int i = 0; i < 4; i++) {
				float sway = Mth.sin(age * (0.18f + sp) + i * 0.9f) * (0.12f + i * 0.04f);
				pose.mulPose(Axis.XP.rotation(i == 0 ? -0.75f + lift : 0.22f - lift * 0.15f));
				pose.mulPose(Axis.YP.rotation(sway));
				box(pose, vc, -w[i] / 2, -w[i] / 2, 0, w[i] / 2, w[i] / 2, len[i], TAIL, light);
				pose.translate(0, 0, (len[i] - 0.3f) / 16f);
			}
			pose.popPose();
		});
		// ball feet and claws
		float roll = (p.walkDist + partial * sp) * 6f;
		for (ModelPart leg : new ModelPart[] { model.rightLeg, model.leftLeg }) {
			AlienParts.on(pose, leg, () -> {
				pose.pushPose();
				pose.translate(0, 12f / 16f, 0);
				pose.mulPose(Axis.XP.rotation(roll));
				box(pose, vc, -2.3f, -1.6f, -2.3f, 2.3f, 1.6f, 2.3f, WHEEL, light);
				pose.popPose();
			});
		}
		for (ModelPart arm : new ModelPart[] { model.rightArm, model.leftArm }) {
			boolean right = arm == model.rightArm;
			AlienParts.on(pose, arm, () -> claws(pose, vc, right, light));
		}
		// (every cutout draw is done: fetching another buffer type ends the previous one)
		VertexConsumer glint = buffers.getBuffer(RenderType.eyes(EXTRAS));
		AlienParts.on(pose, model.head, () -> box(pose, glint, -3.6f, -4.6f, -4.56f, 3.6f, -4.1f, -4.5f, GLINT, LightTexture.FULL_BRIGHT));
		// afterimages: translucent copies of the body trailing behind, while he dashes or runs flat out
		boolean dashing = (Omnitrix.state(p).kitFlags & Xlr8Kit.F_DASH) != 0;
		if (dashing || sp > 0.45f) {
			VertexConsumer ghost = buffers.getBuffer(RenderType.entityTranslucent(SKIN));
			double dx = p.getX() - p.xo, dy = p.getY() - p.yo, dz = p.getZ() - p.zo;
			float yaw = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot);
			float scale = 0.9375f * p.getScale();
			int copies = dashing ? 4 : 2;
			for (int i = 1; i <= copies; i++) {
				Vector3f back = new Vector3f((float) -dx, (float) -dy, (float) -dz).mul(i * 1.1f);
				back.rotateY((float) Math.toRadians(-(180f - yaw)));
				back.set(-back.x, -back.y, back.z).div(scale);
				pose.pushPose();
				pose.translate(back.x, back.y, back.z);
				int a = Math.max(20, (dashing ? 130 : 90) - i * 28);
				model.renderToBuffer(pose, ghost, light, OverlayTexture.NO_OVERLAY, (a << 24) | 0x9FD8FF);
				pose.popPose();
			}
		}
	}

	private static void claws(PoseStack pose, VertexConsumer vc, boolean right, int light) {
		float x0 = right ? -3.0f : -1.0f;
		for (int k = 0; k < 3; k++) {
			float x = x0 + k * 1.5f;
			box(pose, vc, x, 11.8f, -2.4f, x + 0.6f, 13.2f, -1.6f, WHITE, light);
		}
	}

	@Override
	public void renderFirstPersonArm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, ModelPart arm, boolean right) {
		claws(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(EXTRAS)), right, light);
	}

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		float[][] clip = switch (animId) {
			case Xlr8Kit.ANIM_FLURRY -> FLURRY;
			case Xlr8Kit.ANIM_TORNADO -> TORNADO;
			case Xlr8Kit.ANIM_PUNCH -> PUNCH;
			case Xlr8Kit.ANIM_SNATCH -> SNATCH;
			case Xlr8Kit.ANIM_DASH -> DASH;
			case Xlr8Kit.ANIM_WALLRUN -> VISOR_TAP;
			case Xlr8Kit.ANIM_WHIRL -> WHIRL;
			case Xlr8Kit.ANIM_KICKS -> KICKS;
			case Xlr8Kit.ANIM_BURST -> BURST;
			case Xlr8Kit.ANIM_MINE -> MINE;
			case Xlr8Kit.ANIM_JAB -> JAB;
			case Xlr8Kit.ANIM_SWEEP -> SWEEP;
			default -> null;
		};
		boolean played = clip != null && QuirkKeyframes.play(m, clip, ticks, 1f);
		if (!played && speed(p) > 0.3f && p.onGround()) {
			// flat out: the raptor lean, arms tucked back
			com.projecthero.mod.client.HipBend.pitch(m, 1f, 0.45f);
			m.rightArm.xRot = 0.8f + Mth.sin(age * 1.6f) * 0.15f;
			m.leftArm.xRot = 0.8f - Mth.sin(age * 1.6f) * 0.15f;
			m.rightArm.zRot = 0.25f;
			m.leftArm.zRot = -0.25f;
			m.head.xRot -= 0.4f;
			return true;
		}
		return played;
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case Xlr8Kit.ANIM_FLURRY -> FP_FLURRY;
			case Xlr8Kit.ANIM_TORNADO -> FP_RUN;
			case Xlr8Kit.ANIM_PUNCH -> FP_PUNCH;
			case Xlr8Kit.ANIM_SNATCH -> FP_SNATCH;
			case Xlr8Kit.ANIM_DASH -> FP_DASH;
			case Xlr8Kit.ANIM_WALLRUN -> FP_VISOR;
			case Xlr8Kit.ANIM_WHIRL, Xlr8Kit.ANIM_BURST -> FP_WIDE;
			case Xlr8Kit.ANIM_KICKS -> FP_KICK;
			case Xlr8Kit.ANIM_MINE -> FP_MINE;
			case Xlr8Kit.ANIM_JAB -> FP_JAB;
			case Xlr8Kit.ANIM_SWEEP -> FP_SWEEP;
			default -> null;
		};
	}

	@Override
	public void tick(AbstractClientPlayer p) {
		var level = p.level();
		var r = p.getRandom();
		float sp = speed(p);
		if (sp > 0.3f && p.onGround() && r.nextInt(2) == 0) {
			level.addParticle(ParticleTypes.CLOUD, p.getX() + (r.nextDouble() - 0.5) * 0.4, p.getY() + 0.05, p.getZ() + (r.nextDouble() - 0.5) * 0.4, 0, 0.01, 0);
		}
		int flags = Omnitrix.state(p).kitFlags;
		if ((flags & Xlr8Kit.F_TORNADO) != 0) {
			for (int i = 0; i < 2; i++) {
				level.addParticle(ParticleTypes.CLOUD, p.getX(), p.getY() + r.nextDouble() * 1.6, p.getZ(), (r.nextDouble() - 0.5) * 0.3, 0.05,
						(r.nextDouble() - 0.5) * 0.3);
			}
		}
		if ((flags & Xlr8Kit.F_WALLRUN) != 0 && p.horizontalCollision && r.nextInt(2) == 0) {
			level.addParticle(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.4, p.getZ(), 0, -0.05, 0);
		}
		if ((flags & Xlr8Kit.F_WALLRUN) != 0 && p.isInWater() == false && level.getFluidState(p.blockPosition().below()).isSource() && sp > 0.1f) {
			level.addParticle(ParticleTypes.SPLASH, p.getX(), p.getY() + 0.05, p.getZ(), 0, 0.1, 0);
		}
	}

	@Override
	public float[] badge() {
		return new float[] { 0f, 3.6f, -2.06f, 3.0f };
	}
}
