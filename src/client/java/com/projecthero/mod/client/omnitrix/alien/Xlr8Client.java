package com.projecthero.mod.client.omnitrix.alien;

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
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.1: XLR8's client kit. v0.17.1 revamp (user: the planetminecraft XLR8 skin, a closed-mask variant): the user's skin
 * ({@code xlr8.png}, the visor up, his teal face and green eyes) and the closed-mask copy ({@code xlr8_mask.png}, the blue-black
 * visor slid down over the face) -- the mask is drawn whenever {@link Xlr8Kit#visorDown} (Speed Mode, a dash, the tornado,
 * Phase, any speed move just now) and the face at rest. The green eyes glow (an emissive pass of the skin's own eye pixels,
 * through the visor too). Extras in the skin's palette: the helmet's swept-back crest, a long blue-black tail with a teal stripe
 * that streams out flat as he runs, ball feet that roll (no claws, v0.17.1 user). Speed Mode leaves the plain Speedster's after-image trail
 * ({@link #trails}, drawn by {@code SuperSpeedClientV0145} in this skin); Phase flickers him half see-through. A pose (third and
 * first person) for every move.
 */
public final class Xlr8Client implements AlienClientKit {
	private static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/omnitrix/xlr8.png");
	private static final ResourceLocation MASK = ProjectHeroMod.id("textures/entity/omnitrix/xlr8_mask.png");
	private static final ResourceLocation EYES = ProjectHeroMod.id("textures/entity/omnitrix/xlr8_eyes.png");
	private static final ResourceLocation MASK_EYES = ProjectHeroMod.id("textures/entity/omnitrix/xlr8_mask_eyes.png");
	private static final ResourceLocation EXTRAS = ProjectHeroMod.id("textures/entity/omnitrix/xlr8_extras.png");

	// the extras sheet's swatches (32x32): u0, v0, u1, v1
	private static final float[] BLACK = sw(0, 0, 4, 4);
	private static final float[] TEAL = sw(4, 0, 4, 4);
	private static final float[] PALE = sw(8, 0, 4, 4);
	private static final float[] WHITE = sw(12, 0, 4, 4);
	private static final float[] WHEEL = sw(16, 0, 8, 8);
	private static final float[] TAIL = sw(0, 8, 8, 8);

	private static float[] sw(int x, int y, int w, int h) {
		return new float[] { x / 32f, y / 32f, (x + w) / 32f, (y + h) / 32f };
	}

	// {tick, rArm xyz, lArm xyz, body xy, rLeg xyz, lLeg xyz, headX}
	private static final float[] JAB_R = { -1.65f, 0.25f, 0, 0.6f, 0, 0, 0.3f, -0.35f, 0.2f, 0, 0, -0.3f, 0, 0, 0 };
	private static final float[] JAB_L = { 0.6f, 0, 0, -1.65f, -0.25f, 0, 0.3f, 0.35f, -0.3f, 0, 0, 0.2f, 0, 0, 0 };
	private static final float[][] ASSAULT = alternate(12, 1, JAB_R, JAB_L);
	private static final float[] RUN_A = { 0.9f, 0, 0.25f, 0.9f, 0, -0.25f, 0.55f, 0, -1.1f, 0, 0, 0.8f, 0, 0, -0.5f };
	private static final float[] RUN_B = { 0.9f, 0, 0.25f, 0.9f, 0, -0.25f, 0.55f, 0, 0.8f, 0, 0, -1.1f, 0, 0, -0.5f };
	private static final float[][] TORNADO = alternate(60, 3, RUN_A, RUN_B);
	private static final float[][] SPEED_SWEEP = alternate(40, 2, RUN_A, RUN_B);
	private static final float[][] MINE = alternate(14, 2, new float[] { -1.2f, 0.2f, 0, -0.4f, 0, 0, 0.6f, 0, -0.8f, 0, 0, 0.6f, 0, 0, -0.3f },
			new float[] { -0.4f, 0, 0, -1.2f, -0.2f, 0, 0.6f, 0, 0.6f, 0, 0, -0.8f, 0, 0, -0.3f });
	private static final float[] KICK_R = { -0.4f, 0, 0.9f, -0.4f, 0, -0.9f, -0.25f, 0.3f, -1.55f, 0.1f, 0, 0.2f, 0, 0, 0.1f };
	private static final float[] KICK_L = { -0.4f, 0, 0.9f, -0.4f, 0, -0.9f, -0.25f, -0.3f, 0.2f, 0, 0, -1.55f, -0.1f, 0, 0.1f };
	private static final float[][] KICKS = alternate(16, 2, KICK_R, KICK_L);
	/** Blitz: a blur in, the right fist driven through. */
	private static final float[][] BLITZ = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, 0.9f, 0, 0.2f, -0.6f, 0, 0, 0.45f, 0.5f, -0.7f, 0, 0, 0.6f, 0, 0, -0.3f),
			QuirkKeyframes.f(4, -1.75f, 0.2f, 0, 0.8f, 0, 0, 0.35f, -0.45f, 0.3f, 0, 0, -0.5f, 0, 0, 0), QuirkKeyframes.rest(12) };
	/** Mach Punch: a deep wind-up, then everything into one straight right. */
	private static final float[][] MACH = { QuirkKeyframes.rest(0),
			QuirkKeyframes.f(3, 1.2f, 0.3f, 0.5f, -1.0f, -0.3f, 0, 0.3f, 0.8f, -0.6f, 0, 0, 0.5f, 0, 0, -0.2f),
			QuirkKeyframes.f(5, -1.6f, 0.15f, 0, 0.9f, 0, -0.2f, 0.45f, -0.7f, 0.5f, 0, 0, -0.6f, 0, 0, 0.1f),
			QuirkKeyframes.f(10, -1.6f, 0.15f, 0, 0.9f, 0, -0.2f, 0.45f, -0.7f, 0.5f, 0, 0, -0.6f, 0, 0, 0.1f), QuirkKeyframes.rest(16) };
	private static final float[][] SNATCH = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -1.6f, -0.1f, 0, 0.3f, 0, 0, 0.35f, -0.3f, 0.3f, 0, 0, -0.4f, 0, 0, 0),
			QuirkKeyframes.f(5, -0.6f, -0.6f, 0, 0.2f, 0, 0, -0.1f, 0.2f, 0, 0, 0, 0, 0, 0, 0.2f), QuirkKeyframes.rest(12) };
	private static final float[][] DASH = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, 1.1f, 0, 0.3f, 1.1f, 0, -0.3f, 0.75f, 0, -0.9f, 0, 0, 0.7f, 0, 0, -0.65f),
			QuirkKeyframes.f(8, 1.1f, 0, 0.3f, 1.1f, 0, -0.3f, 0.75f, 0, 0.7f, 0, 0, -0.9f, 0, 0, -0.65f), QuirkKeyframes.rest(12) };
	/** Speed Mode: the right hand slaps the visor down (or flips it up), a crouch, ready to run. */
	private static final float[][] MODE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -2.6f, -0.5f, 0, 0.2f, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -0.15f),
			QuirkKeyframes.f(5, -1.9f, -0.5f, 0, 0.5f, 0, -0.2f, 0.3f, 0, -0.5f, 0, 0, 0.4f, 0, 0, 0.1f),
			QuirkKeyframes.f(9, 0.8f, 0, 0.3f, 0.8f, 0, -0.3f, 0.45f, 0, -0.6f, 0, 0, 0.5f, 0, 0, -0.35f), QuirkKeyframes.rest(14) };
	private static final float[][] WHIRL = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -0.2f, 0, 1.5f, -0.2f, 0, -1.5f, 0.1f, 1.6f, 0, 0, 0.2f, 0, 0, -0.2f, 0),
			QuirkKeyframes.f(6, -0.2f, 0, 1.5f, -0.2f, 0, -1.5f, 0.1f, 4.4f, 0, 0, 0.2f, 0, 0, -0.2f, 0),
			QuirkKeyframes.f(10, -0.2f, 0, 1.5f, -0.2f, 0, -1.5f, 0.1f, 6.28f, 0, 0, 0.2f, 0, 0, -0.2f, 0),
			QuirkKeyframes.f(13, 0, 0, 0, 0, 0, 0, 0, 6.28f, 0, 0, 0, 0, 0, 0, 0) };
	private static final float[][] BURST = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -0.9f, 0.7f, -0.3f, -0.9f, -0.7f, 0.3f, 0.3f, 0, -0.3f, 0, 0.1f, -0.3f, 0, -0.1f, 0.3f),
			QuirkKeyframes.f(6, -0.4f, 0, 1.6f, -0.4f, 0, -1.6f, -0.25f, 0, 0, 0, 0.3f, 0, 0, -0.3f, -0.5f), QuirkKeyframes.rest(16) };
	/** Decoys: a sidestep blur, arms flung wide. */
	private static final float[][] DECOYS = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -0.3f, 0, 1.3f, -0.3f, 0, -1.3f, 0.2f, 0.9f, -0.3f, 0, 0.4f, 0.3f, 0, -0.4f, 0),
			QuirkKeyframes.f(5, -0.3f, 0, 1.3f, -0.3f, 0, -1.3f, 0.2f, -0.9f, 0.3f, 0, 0.4f, -0.3f, 0, -0.4f, 0), QuirkKeyframes.rest(10) };
	private static final float[][] JAB = { QuirkKeyframes.rest(0), QuirkKeyframes.f(1.5f, -1.65f, 0.25f, 0, 0.3f, 0, 0, 0.15f, -0.3f, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.f(3, 0.3f, 0, 0, -1.65f, -0.25f, 0, 0.15f, 0.3f, 0, 0, 0, 0, 0, 0, 0), QuirkKeyframes.rest(6) };
	private static final float[][] SWEEP = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -0.6f, 0, 1.2f, -0.6f, 0, -1.2f, 0.5f, -0.6f, -0.4f, 0, 0.2f, -0.4f, 0, -0.2f, 0.3f),
			QuirkKeyframes.f(7, -0.6f, 0, 1.2f, -0.6f, 0, -1.2f, 0.5f, 0.6f, -0.4f, 0, 0.2f, -0.4f, 0, -0.2f, 0.3f), QuirkKeyframes.rest(12) };

	private static final FirstPersonClip FP_ASSAULT = fpAlternate(12, 1, new float[] { -22, 24, 0, 0.16f, 0.1f, -0.25f }, new float[] { 0, -5, 0, 0, -0.05f, 0.05f })
			.both();
	private static final FirstPersonClip FP_RUN = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 30, -10, 0, -0.1f, -0.25f, 0.1f),
			FirstPersonClip.f(55, 30, -10, 0, -0.1f, -0.25f, 0.1f), FirstPersonClip.f(60, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_SWEEP_RUN = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 30, -10, 0, -0.1f, -0.25f, 0.1f),
			FirstPersonClip.f(36, 30, -10, 0, -0.1f, -0.25f, 0.1f), FirstPersonClip.f(40, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_BLITZ = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, 25, -10, 0, -0.05f, -0.15f, 0.2f),
			FirstPersonClip.f(4, -25, 28, 0, 0.2f, 0.1f, -0.35f), FirstPersonClip.f(12, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_MACH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 35, -20, 10, -0.1f, -0.25f, 0.3f),
			FirstPersonClip.f(5, -30, 30, 0, 0.25f, 0.12f, -0.45f), FirstPersonClip.f(10, -30, 30, 0, 0.25f, 0.12f, -0.45f), FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_SNATCH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -25, 20, 0, 0.15f, 0.1f, -0.35f),
			FirstPersonClip.f(5, 10, -15, -20, -0.1f, -0.05f, 0.1f), FirstPersonClip.f(12, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_DASH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, 35, -15, 0, -0.1f, -0.3f, 0.15f),
			FirstPersonClip.f(8, 35, -15, 0, -0.1f, -0.3f, 0.15f), FirstPersonClip.f(12, 0, 0, 0, 0, 0, 0)).both();
	/** The hand slaps up to the visor and pulls it down. */
	private static final FirstPersonClip FP_MODE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -50, 25, 0, 0.1f, 0.28f, -0.15f),
			FirstPersonClip.f(5, -38, 25, 0, 0.1f, 0.18f, -0.15f), FirstPersonClip.f(9, 25, -10, 0, -0.05f, -0.2f, 0.1f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_WIDE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -20, -20, 0, -0.1f, 0.1f, 0),
			FirstPersonClip.f(6, -30, -45, 30, -0.3f, 0.15f, 0.05f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_KICK = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 20, -30, 30, -0.25f, -0.1f, 0.1f),
			FirstPersonClip.f(14, 20, -30, 30, -0.25f, -0.1f, 0.1f), FirstPersonClip.f(17, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_MINE = fpAlternate(14, 2, new float[] { -10, 20, 0, 0.12f, -0.05f, -0.3f }, new float[] { 10, 0, 0, 0, -0.15f, 0 }).both();
	private static final FirstPersonClip FP_JAB = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(1.5f, -18, 24, 0, 0.15f, 0.08f, -0.25f),
			FirstPersonClip.f(3, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4.5f, -18, 24, 0, 0.15f, 0.08f, -0.25f), FirstPersonClip.f(6, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_SWEEP = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 30, -40, 0, -0.2f, -0.25f, 0),
			FirstPersonClip.f(7, 30, 40, 0, 0.25f, -0.25f, 0), FirstPersonClip.f(12, 0, 0, 0, 0, 0, 0));
	/** Phase: the hands buzz (a fast shiver for the first second; the vibration keeps going while X is held, see {@link #pose}). */
	private static final FirstPersonClip FP_PHASE = fpAlternate(20, 1, new float[] { -6, 4, 3, 0.03f, 0.02f, 0 }, new float[] { 4, -4, -3, -0.03f, -0.02f, 0 }).both();

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

	private static int flags(Player p) {
		return Omnitrix.state(p).kitFlags;
	}

	/** v0.17.1 revamp: Speed Mode leaves the plain Speedster's after-image trail (asked by {@code SuperSpeedClientV0145}). */
	public static boolean trails(Player p) {
		return Xlr8Kit.is(p) && (flags(p) & Xlr8Kit.F_SPEED) != 0;
	}

	@Override
	public ResourceLocation skin() {
		return SKIN;
	}

	/** The visor down (the closed-mask skin) while he runs; his face at rest. */
	@Override
	public ResourceLocation skin(AbstractClientPlayer p) {
		return Xlr8Kit.visorDown(p) ? MASK : SKIN;
	}

	/** Phase: he flickers half see-through. */
	@Override
	public int bodyAlpha(AbstractClientPlayer p) {
		if ((flags(p) & Xlr8Kit.F_PHASE) == 0) {
			return 255;
		}
		return 120 + (int) (60 * Mth.sin((p.tickCount + p.getId()) * 2.1f));
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
		// the helmet's crest: a swept-back fin over the crown, teal-edged (the visor itself is the skin's)
		AlienParts.on(pose, model.head, () -> {
			// a low ridge along the crown that sweeps up and back past the nape
			box(pose, vc, -0.6f, -8.9f, -2.5f, 0.6f, -7.95f, 3.2f, BLACK, light);
			box(pose, vc, -0.3f, -9.15f, -1.5f, 0.3f, -8.9f, 3.0f, TEAL, light);
			pose.pushPose();
			pose.translate(0, -8.4f / 16f, 3.2f / 16f);
			pose.mulPose(Axis.XP.rotation(-0.5f));
			box(pose, vc, -0.55f, -0.5f, 0f, 0.55f, 0.5f, 3.0f, BLACK, light);
			pose.popPose();
			// the small swept fins over the ears
			for (int side = -1; side <= 1; side += 2) {
				float x = side * 4.1f;
				box(pose, vc, Math.min(x, x + side * 0.5f), -6.2f, -1.0f, Math.max(x, x + side * 0.5f), -4.8f, 2.6f, BLACK, light);
			}
		});
		// the tail: four segments from the small of the back, streaming out flatter the faster he runs
		AlienParts.on(pose, model.body, () -> {
			pose.pushPose();
			pose.translate(0, 10.5f / 16f, 1.6f / 16f);
			float lift = Mth.clamp(sp * 1.4f, 0f, 0.6f);
			float[] w = { 3.0f, 2.4f, 1.8f, 1.1f };
			float[] len = { 4.5f, 4.5f, 4.0f, 3.5f };
			for (int i = 0; i < 4; i++) {
				float sway = Mth.sin(age * (0.18f + sp) + i * 0.9f) * (0.12f + i * 0.04f);
				pose.mulPose(Axis.XP.rotation(i == 0 ? -0.75f + lift : 0.22f - lift * 0.15f));
				pose.mulPose(Axis.YP.rotation(sway));
				box(pose, vc, -w[i] / 2, -w[i] / 2, 0, w[i] / 2, w[i] / 2, len[i], TAIL, light);
				pose.translate(0, 0, (len[i] - 0.3f) / 16f);
			}
			box(pose, vc, -0.4f, -0.4f, 0, 0.4f, 0.4f, 1.5f, PALE, light);
			pose.popPose();
		});
		// ball feet that roll with his stride
		float roll = (p.walkDist + partial * sp) * 6f;
		for (ModelPart leg : new ModelPart[] { model.rightLeg, model.leftLeg }) {
			AlienParts.on(pose, leg, () -> {
				pose.pushPose();
				pose.translate(0, 11.4f / 16f, -0.3f / 16f);
				pose.mulPose(Axis.XP.rotation(roll));
				box(pose, vc, -2.4f, -1.9f, -2.4f, 2.4f, 1.9f, 2.4f, WHEEL, light);
				pose.popPose();
			});
		}
		for (ModelPart arm : new ModelPart[] { model.rightArm, model.leftArm }) {
			boolean right = arm == model.rightArm;
		}
		// (every cutout draw is done: fetching another buffer type ends the previous one)
		// the green eyes glow: the skin's own eye pixels drawn again full-bright (the visor's faint eyes when it is down)
		VertexConsumer eyes = buffers.getBuffer(RenderType.eyes(Xlr8Kit.visorDown(p) ? MASK_EYES : EYES));
		model.head.render(pose, eyes, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
		model.hat.render(pose, eyes, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
	}

	@Override
	public void renderFirstPersonArm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, ModelPart arm, boolean right) {
		// v0.17.1 (user: "remove the claws on xlr8"): nothing extra on the first-person arm
	}

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		float[][] clip = switch (animId) {
			case Xlr8Kit.ANIM_ASSAULT -> ASSAULT;
			case Xlr8Kit.ANIM_TORNADO -> TORNADO;
			case Xlr8Kit.ANIM_BLITZ -> BLITZ;
			case Xlr8Kit.ANIM_MACH -> MACH;
			case Xlr8Kit.ANIM_SNATCH -> SNATCH;
			case Xlr8Kit.ANIM_DASH -> DASH;
			case Xlr8Kit.ANIM_SPEED_SWEEP -> SPEED_SWEEP;
			case Xlr8Kit.ANIM_MODE -> MODE;
			case Xlr8Kit.ANIM_WHIRL -> WHIRL;
			case Xlr8Kit.ANIM_KICKS -> KICKS;
			case Xlr8Kit.ANIM_DECOYS -> DECOYS;
			case Xlr8Kit.ANIM_BURST -> BURST;
			case Xlr8Kit.ANIM_MINE -> MINE;
			case Xlr8Kit.ANIM_JAB -> JAB;
			case Xlr8Kit.ANIM_SWEEP -> SWEEP;
			default -> null;
		};
		boolean played = clip != null && QuirkKeyframes.play(m, clip, ticks, 1f);
		int f = flags(p);
		if ((f & Xlr8Kit.F_PHASE) != 0) {
			// Phase: every limb shivers (his molecules vibrating through the wall)
			float j = age * 9.7f;
			m.body.yRot += Mth.sin(j) * 0.06f;
			m.head.xRot += Mth.sin(j * 1.3f) * 0.05f;
			m.rightArm.xRot += Mth.sin(j * 1.1f) * 0.12f;
			m.leftArm.xRot += Mth.cos(j * 0.9f) * 0.12f;
			m.rightLeg.xRot += Mth.cos(j * 1.2f) * 0.08f;
			m.leftLeg.xRot += Mth.sin(j * 1.4f) * 0.08f;
			return true;
		}
		boolean flatOut = speed(p) > ((f & Xlr8Kit.F_SPEED) != 0 ? 0.18f : 0.3f);
		if (!played && flatOut && p.onGround()) {
			// flat out: the raptor lean, arms tucked back, the head low and forward
			com.projecthero.mod.client.HipBend.pitch(m, 1f, (f & Xlr8Kit.F_SPEED) != 0 ? 0.6f : 0.45f);
			m.rightArm.xRot = 0.9f + Mth.sin(age * 1.6f) * 0.15f;
			m.leftArm.xRot = 0.9f - Mth.sin(age * 1.6f) * 0.15f;
			m.rightArm.zRot = 0.25f;
			m.leftArm.zRot = -0.25f;
			m.head.xRot -= 0.45f;
			return true;
		}
		if (!played && (f & Xlr8Kit.F_SPEED) != 0 && p.onClimbable() && !p.onGround()) {
			// running straight up a wall: the body pitched into it, legs pumping
			m.rightLeg.xRot = Mth.sin(age * 1.8f) * 1.1f;
			m.leftLeg.xRot = -Mth.sin(age * 1.8f) * 1.1f;
			m.rightArm.xRot = -0.6f - Mth.sin(age * 1.8f) * 0.4f;
			m.leftArm.xRot = -0.6f + Mth.sin(age * 1.8f) * 0.4f;
			return true;
		}
		return played;
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case Xlr8Kit.ANIM_ASSAULT -> FP_ASSAULT;
			case Xlr8Kit.ANIM_TORNADO -> FP_RUN;
			case Xlr8Kit.ANIM_SPEED_SWEEP -> FP_SWEEP_RUN;
			case Xlr8Kit.ANIM_BLITZ -> FP_BLITZ;
			case Xlr8Kit.ANIM_MACH -> FP_MACH;
			case Xlr8Kit.ANIM_SNATCH -> FP_SNATCH;
			case Xlr8Kit.ANIM_DASH -> FP_DASH;
			case Xlr8Kit.ANIM_MODE -> FP_MODE;
			case Xlr8Kit.ANIM_WHIRL, Xlr8Kit.ANIM_BURST, Xlr8Kit.ANIM_DECOYS -> FP_WIDE;
			case Xlr8Kit.ANIM_KICKS -> FP_KICK;
			case Xlr8Kit.ANIM_MINE -> FP_MINE;
			case Xlr8Kit.ANIM_JAB -> FP_JAB;
			case Xlr8Kit.ANIM_SWEEP -> FP_SWEEP;
			case Xlr8Kit.ANIM_PHASE -> FP_PHASE;
			default -> null;
		};
	}

	@Override
	public void tick(AbstractClientPlayer p) {
		var level = p.level();
		var r = p.getRandom();
		float sp = speed(p);
		int flags = flags(p);
		boolean mode = (flags & Xlr8Kit.F_SPEED) != 0;
		if (sp > (mode ? 0.18f : 0.3f) && p.onGround() && r.nextInt(2) == 0) {
			level.addParticle(ParticleTypes.CLOUD, p.getX() + (r.nextDouble() - 0.5) * 0.4, p.getY() + 0.05, p.getZ() + (r.nextDouble() - 0.5) * 0.4, 0, 0.01, 0);
		}
		if (mode && sp > 0.25f && r.nextInt(3) == 0) {
			// a blue static off his heels
			level.addParticle(ParticleTypes.ELECTRIC_SPARK, p.getX() + (r.nextDouble() - 0.5) * 0.5, p.getY() + 0.3 + r.nextDouble() * 0.8,
					p.getZ() + (r.nextDouble() - 0.5) * 0.5, 0, 0, 0);
		}
		if ((flags & Xlr8Kit.F_TORNADO) != 0) {
			for (int i = 0; i < 2; i++) {
				level.addParticle(ParticleTypes.CLOUD, p.getX(), p.getY() + r.nextDouble() * 1.6, p.getZ(), (r.nextDouble() - 0.5) * 0.3, 0.05,
						(r.nextDouble() - 0.5) * 0.3);
			}
		}
		if (mode && p.horizontalCollision && r.nextInt(2) == 0) {
			level.addParticle(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.4, p.getZ(), 0, -0.05, 0);
		}
		if (mode && !p.isInWater() && level.getFluidState(p.blockPosition().below()).isSource() && sp > 0.1f) {
			level.addParticle(ParticleTypes.SPLASH, p.getX(), p.getY() + 0.05, p.getZ(), 0, 0.1, 0);
		}
		if ((flags & Xlr8Kit.F_PHASE) != 0 && r.nextInt(2) == 0) {
			level.addParticle(ParticleTypes.ELECTRIC_SPARK, p.getX() + (r.nextDouble() - 0.5) * 0.7, p.getY() + r.nextDouble() * 1.8,
					p.getZ() + (r.nextDouble() - 0.5) * 0.7, 0, 0, 0);
		}
	}

	@Override
	public float[] badge() {
		return new float[] { 0f, 3.6f, -2.06f, 3.0f };
	}
}
