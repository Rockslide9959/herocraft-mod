package com.projecthero.mod.client.omnitrix.alien;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.AlienParts;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
import com.projecthero.mod.client.omnitrix.OmnitrixHud;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.omnitrix.alien.xlr8.Xlr8Kit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: XLR8's client kit. v0.17.1 revamp (user: the planetminecraft XLR8 skin, a closed-mask variant): the user's skin
 * ({@code xlr8.png}, the visor up, his teal face and green eyes) and the closed-mask copy ({@code xlr8_mask.png}, the blue-black
 * visor slid down over the face) -- the mask is drawn whenever {@link Xlr8Kit#visorDown} (Speed Mode, a dash, the tornado, the
 * skate, any speed move just now) and the face at rest. The green eyes glow (an emissive pass of the skin's own eye pixels,
 * through the visor too). The long blue-black tail streams out flat as he runs. A pose (third and first person) for every move.
 *
 * <p>v0.17.2 revamp (user: "add xlr8s speed trail to his speed mode please, the blue and black lines"): his streak lines
 * ({@link Xlr8StreakTrail}) beside the plain Speedster's after-images ({@link #trails}), the particle spam gone; the tail swings
 * wide on hard turns and goes flat out for Tail Spin and the kick finisher; Blur Punches' arms blur into three ghost copies; the
 * a crouched skate for Friction Zero; the visor's rim glows white at a full
 * Kinetic Charge, whose bar sits over his keys; on ice, slime and honey his own client lets him skid.
 */
public final class Xlr8Client implements AlienClientKit {
	private static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/omnitrix/xlr8.png");
	private static final ResourceLocation MASK = ProjectHeroMod.id("textures/entity/omnitrix/xlr8_mask.png");
	private static final ResourceLocation EYES = ProjectHeroMod.id("textures/entity/omnitrix/xlr8_eyes.png");
	private static final ResourceLocation MASK_EYES = ProjectHeroMod.id("textures/entity/omnitrix/xlr8_mask_eyes.png");
	private static final ResourceLocation EXTRAS = ProjectHeroMod.id("textures/entity/omnitrix/xlr8_extras.png");

	// the extras sheet's swatches (32x32): u0, v0, u1, v1
	private static final float[] BLACK = sw(0, 0, 4, 4);
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
	private static final float[][] PUNCHES = alternate(12, 1, JAB_R, JAB_L);
	private static final float[] RUN_A = { 0.9f, 0, 0.25f, 0.9f, 0, -0.25f, 0.55f, 0, -1.1f, 0, 0, 0.8f, 0, 0, -0.5f };
	private static final float[] RUN_B = { 0.9f, 0, 0.25f, 0.9f, 0, -0.25f, 0.55f, 0, 0.8f, 0, 0, -1.1f, 0, 0, -0.5f };
	private static final float[][] TORNADO = alternate(60, 3, RUN_A, RUN_B);
	private static final float[][] RUSH = alternate(40, 2, RUN_A, RUN_B);
	private static final float[][] MINE = alternate(14, 2, new float[] { -1.2f, 0.2f, 0, -0.4f, 0, 0, 0.6f, 0, -0.8f, 0, 0, 0.6f, 0, 0, -0.3f },
			new float[] { -0.4f, 0, 0, -1.2f, -0.2f, 0, 0.6f, 0, 0.6f, 0, 0, -0.8f, 0, 0, -0.3f });
	/** v0.17.2 revamp: skating kicks -- the legs sweep out sideways off the ball feet, arms flung for balance. */
	private static final float[] KICK_R = { -0.4f, 0, 0.9f, -0.4f, 0, -0.9f, -0.25f, 0.3f, -1.55f, 0.1f, 0.35f, 0.2f, 0, 0, 0.1f };
	private static final float[] KICK_L = { -0.4f, 0, 0.9f, -0.4f, 0, -0.9f, -0.25f, -0.3f, 0.2f, 0, 0, -1.55f, -0.1f, -0.35f, 0.1f };
	private static final float[][] KICKS = alternate(16, 2, KICK_R, KICK_L);
	/** Raptor Strike: a blur in and a low slide-tackle, the right leg sweeping through, a fist driven down after. */
	private static final float[][] STRIKE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, 0.9f, 0, 0.2f, -0.6f, 0, 0, 0.45f, 0.5f, -0.7f, 0, 0, 0.6f, 0, 0, -0.3f),
			QuirkKeyframes.f(4, -0.5f, 0, 0.6f, 0.4f, 0, -0.6f, -0.45f, -0.3f, -1.45f, 0, 0.2f, 0.4f, 0, 0, 0.2f),
			QuirkKeyframes.f(8, -1.75f, 0.2f, 0, 0.8f, 0, 0, 0.35f, -0.45f, 0.3f, 0, 0, -0.5f, 0, 0, 0), QuirkKeyframes.rest(12) };
	/** Sonic Punch: a deep wind-up, then everything into one straight right. */
	private static final float[][] SONIC = { QuirkKeyframes.rest(0),
			QuirkKeyframes.f(3, 1.2f, 0.3f, 0.5f, -1.0f, -0.3f, 0, 0.3f, 0.8f, -0.6f, 0, 0, 0.5f, 0, 0, -0.2f),
			QuirkKeyframes.f(5, -1.6f, 0.15f, 0, 0.9f, 0, -0.2f, 0.45f, -0.7f, 0.5f, 0, 0, -0.6f, 0, 0, 0.1f),
			QuirkKeyframes.f(10, -1.6f, 0.15f, 0, 0.9f, 0, -0.2f, 0.45f, -0.7f, 0.5f, 0, 0, -0.6f, 0, 0, 0.1f), QuirkKeyframes.rest(16) };
	private static final float[][] SNATCH = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -1.6f, -0.1f, 0, 0.3f, 0, 0, 0.35f, -0.3f, 0.3f, 0, 0, -0.4f, 0, 0, 0),
			QuirkKeyframes.f(5, -0.6f, -0.6f, 0, 0.2f, 0, 0, -0.1f, 0.2f, 0, 0, 0, 0, 0, 0, 0.2f), QuirkKeyframes.rest(12) };
	/** Rescue Carry: a scoop -- both arms swing low, then up and back over the shoulders. */
	private static final float[][] CARRY = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -0.9f, 0, 0.3f, -0.9f, 0, -0.3f, 0.5f, 0, -0.3f, 0, 0, 0.3f, 0, 0, 0.2f),
			QuirkKeyframes.f(7, -2.6f, 0, 0.2f, -2.6f, 0, -0.2f, 0, 0, 0, 0, 0, 0, 0, 0, -0.1f), QuirkKeyframes.rest(12) };
	private static final float[][] DASH = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, 1.1f, 0, 0.3f, 1.1f, 0, -0.3f, 0.75f, 0, -0.9f, 0, 0, 0.7f, 0, 0, -0.65f),
			QuirkKeyframes.f(8, 1.1f, 0, 0.3f, 1.1f, 0, -0.3f, 0.75f, 0, 0.7f, 0, 0, -0.9f, 0, 0, -0.65f), QuirkKeyframes.rest(12) };
	/** Tail Spin: a spin on one ball foot, arms tucked, the other leg out (the tail goes flat out, {@link #renderExtras}). */
	private static final float[][] TAIL_SPIN = { QuirkKeyframes.rest(0),
			QuirkKeyframes.f(2, -0.2f, 0, 0.6f, -0.2f, 0, -0.6f, 0.1f, 1.6f, 0, 0, 0.6f, 0, 0, -0.2f, 0),
			QuirkKeyframes.f(6, -0.2f, 0, 0.6f, -0.2f, 0, -0.6f, 0.1f, 4.4f, 0, 0, 0.6f, 0, 0, -0.2f, 0),
			QuirkKeyframes.f(10, -0.2f, 0, 0.6f, -0.2f, 0, -0.6f, 0.1f, 6.28f, 0, 0, 0.6f, 0, 0, -0.2f, 0),
			QuirkKeyframes.f(13, 0, 0, 0, 0, 0, 0, 0, 6.28f, 0, 0, 0, 0, 0, 0, 0) };
	/** The kick finisher: one fast full turn, the tail whipping round flat. */
	private static final float[][] TAIL_WHIP = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -0.3f, 0, 0.9f, -0.3f, 0, -0.9f, 0.15f, 3.1f, 0, 0, 0.3f, 0, 0, -0.3f, 0),
			QuirkKeyframes.f(5, -0.3f, 0, 0.9f, -0.3f, 0, -0.9f, 0.15f, 6.28f, 0, 0, 0.3f, 0, 0, -0.3f, 0), QuirkKeyframes.f(8, 0, 0, 0, 0, 0, 0, 0, 6.28f, 0, 0, 0, 0, 0, 0, 0) };
	/** Shift+G again: both hands shove the twister away. */
	private static final float[][] LOOSE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -0.6f, 0, 0.3f, -0.6f, 0, -0.3f, -0.2f, 0, 0.3f, 0, 0, -0.3f, 0, 0, 0),
			QuirkKeyframes.f(6, -1.6f, 0.2f, 0, -1.6f, -0.2f, 0, 0.3f, 0, -0.4f, 0, 0, 0.4f, 0, 0, -0.1f), QuirkKeyframes.rest(14) };
	private static final float[][] BURST = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -0.9f, 0.7f, -0.3f, -0.9f, -0.7f, 0.3f, 0.3f, 0, -0.3f, 0, 0.1f, -0.3f, 0, -0.1f, 0.3f),
			QuirkKeyframes.f(6, -0.4f, 0, 1.6f, -0.4f, 0, -1.6f, -0.25f, 0, 0, 0, 0.3f, 0, 0, -0.3f, -0.5f), QuirkKeyframes.rest(16) };
	/** Decoys: a sidestep blur, arms flung wide. */
	private static final float[][] DECOYS = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -0.3f, 0, 1.3f, -0.3f, 0, -1.3f, 0.2f, 0.9f, -0.3f, 0, 0.4f, 0.3f, 0, -0.4f, 0),
			QuirkKeyframes.f(5, -0.3f, 0, 1.3f, -0.3f, 0, -1.3f, 0.2f, -0.9f, 0.3f, 0, 0.4f, -0.3f, 0, -0.4f, 0), QuirkKeyframes.rest(10) };
	private static final float[][] JAB = { QuirkKeyframes.rest(0), QuirkKeyframes.f(1.5f, -1.65f, 0.25f, 0, 0.3f, 0, 0, 0.15f, -0.3f, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.f(3, 0.3f, 0, 0, -1.65f, -0.25f, 0, 0.15f, 0.3f, 0, 0, 0, 0, 0, 0, 0), QuirkKeyframes.rest(6) };
	private static final float[][] SWEEP = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -0.6f, 0, 1.2f, -0.6f, 0, -1.2f, 0.5f, -0.6f, -0.4f, 0, 0.2f, -0.4f, 0, -0.2f, 0.3f),
			QuirkKeyframes.f(7, -0.6f, 0, 1.2f, -0.6f, 0, -1.2f, 0.5f, 0.6f, -0.4f, 0, 0.2f, -0.4f, 0, -0.2f, 0.3f), QuirkKeyframes.rest(12) };

	private static final FirstPersonClip FP_PUNCHES = fpAlternate(12, 1, new float[] { -22, 24, 0, 0.16f, 0.1f, -0.25f }, new float[] { 0, -5, 0, 0, -0.05f, 0.05f })
			.both();
	private static final FirstPersonClip FP_RUN = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 30, -10, 0, -0.1f, -0.25f, 0.1f),
			FirstPersonClip.f(55, 30, -10, 0, -0.1f, -0.25f, 0.1f), FirstPersonClip.f(60, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_RUSH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 30, -10, 0, -0.1f, -0.25f, 0.1f),
			FirstPersonClip.f(36, 30, -10, 0, -0.1f, -0.25f, 0.1f), FirstPersonClip.f(40, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_STRIKE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, 25, -10, 0, -0.05f, -0.15f, 0.2f),
			FirstPersonClip.f(4, -25, 28, 0, 0.2f, 0.1f, -0.35f), FirstPersonClip.f(12, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_SONIC = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 35, -20, 10, -0.1f, -0.25f, 0.3f),
			FirstPersonClip.f(5, -30, 30, 0, 0.25f, 0.12f, -0.45f), FirstPersonClip.f(10, -30, 30, 0, 0.25f, 0.12f, -0.45f), FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_SNATCH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -25, 20, 0, 0.15f, 0.1f, -0.35f),
			FirstPersonClip.f(5, 10, -15, -20, -0.1f, -0.05f, 0.1f), FirstPersonClip.f(12, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_DASH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, 35, -15, 0, -0.1f, -0.3f, 0.15f),
			FirstPersonClip.f(8, 35, -15, 0, -0.1f, -0.3f, 0.15f), FirstPersonClip.f(12, 0, 0, 0, 0, 0, 0)).both();
	/** Friction Zero: the arms swept back low for the whole skate. */
	private static final FirstPersonClip FP_SKATE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 40, -15, 0, -0.1f, -0.35f, 0.2f),
			FirstPersonClip.f(76, 40, -15, 0, -0.1f, -0.35f, 0.2f), FirstPersonClip.f(80, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_WIDE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -20, -20, 0, -0.1f, 0.1f, 0),
			FirstPersonClip.f(6, -30, -45, 30, -0.3f, 0.15f, 0.05f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_KICK = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 20, -30, 30, -0.25f, -0.1f, 0.1f),
			FirstPersonClip.f(14, 20, -30, 30, -0.25f, -0.1f, 0.1f), FirstPersonClip.f(17, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_MINE = fpAlternate(14, 2, new float[] { -10, 20, 0, 0.12f, -0.05f, -0.3f }, new float[] { 10, 0, 0, 0, -0.15f, 0 }).both();
	private static final FirstPersonClip FP_JAB = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(1.5f, -18, 24, 0, 0.15f, 0.08f, -0.25f),
			FirstPersonClip.f(3, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4.5f, -18, 24, 0, 0.15f, 0.08f, -0.25f), FirstPersonClip.f(6, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_SWEEP = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 30, -40, 0, -0.2f, -0.25f, 0),
			FirstPersonClip.f(7, 30, 40, 0, 0.25f, -0.25f, 0), FirstPersonClip.f(12, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_LOOSE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 15, 0, 0, 0, -0.1f, 0.15f),
			FirstPersonClip.f(6, -30, 0, 0, 0, 0.1f, -0.35f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0)).both();

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

	public Xlr8Client() {
		Xlr8StreakTrail.init(); // v0.17.2 revamp: the streak lines, the zip lines, Time Burst's screen tint
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
		return visor(p, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false)) >= 1f ? MASK : SKIN;
	}

	/** How long the visor takes to slide down over the face or up off it (ticks). */
	private static final float VISOR_SLIDE = 6f;
	/** Per player: whether the visor was last seen down and when that changed (client-only; dropped with the client). */
	private static final java.util.Map<java.util.UUID, float[]> VISOR = new java.util.HashMap<>();
	/** v0.17.2 revamp: per player, the tail's swing on hard turns {previous, current} (radians; + = out to his left). */
	private static final java.util.Map<java.util.UUID, float[]> SWING = new java.util.HashMap<>();
	/** v0.17.2 revamp (Friction): the local player's skid -- {x, z, ticks left}. */
	private static final float[] SKID = new float[3];

	/**
	 * v0.17.1 (user: "just make it slide down onto his face, when it goes up make it slide up off his face"): 0 = up (his face),
	 * 1 = down (the helmet's visor), sliding between over {@link #VISOR_SLIDE} ticks from the moment Xlr8Kit#visorDown flips.
	 */
	static float visor(AbstractClientPlayer p, float partial) {
		boolean down = Xlr8Kit.visorDown(p);
		float now = p.tickCount + partial;
		float[] v = VISOR.computeIfAbsent(p.getUUID(), k -> new float[] { down ? 1f : 0f, -100f });
		if ((v[0] > 0.5f) != down) {
			v[0] = down ? 1f : 0f;
			v[1] = now;
		}
		float k = Mth.clamp((now - v[1]) / VISOR_SLIDE, 0f, 1f);
		k = k * k * (3f - 2f * k);
		return down ? k : 1f - k;
	}

	/** v0.17.2 revamp (Tail Balance): how far the tail swings out sideways on a hard turn, radians (eased; any player in view). */
	static float tailSwing(Player p, float partial) {
		float[] s = SWING.get(p.getUUID());
		return s == null ? 0f : Mth.lerp(partial, s[0], s[1]);
	}

	private static void box(PoseStack pose, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, float[] uv, int light) {
		AlienParts.box(pose, vc, x0, y0, z0, x1, y1, z1, uv[0], uv[1], uv[2], uv[3], 0xFFFFFFFF, light);
	}

	/** Horizontal speed in blocks per tick (works for every player in view: from the last two positions). */
	private static float speed(AbstractClientPlayer p) {
		double dx = p.getX() - p.xo, dz = p.getZ() - p.zo;
		return (float) Math.sqrt(dx * dx + dz * dz);
	}

	/** The move playing now (its anim id) and how far in (ticks), or -1. */
	private static float animTicks(Player p, int animId, float partial) {
		OmnitrixState s = Omnitrix.state(p);
		return s.animId == animId ? p.level().getGameTime() - s.animStart + partial : -1f;
	}

	@Override
	public void renderExtras(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model, float partial,
			float age) {
		// v0.17.1 (user: "remove whatever extra is there and just leave the alien skin itself"): no crest or ear fins (the helmet is
		// the skin's now)
		// v0.17.2 (user: "give xlr8 his tail back ... i just wanted you to edit his skin to remove the tail"): the 3D tail is back,
		// the painted one stays out of the skin
		// the tail: four segments from the small of the back, streaming out flatter the faster he runs
		int f = flags(p);
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(EXTRAS));
		// v0.17.2 revamp: Tail Spin and the kick finisher whip it round flat out
		float spin = Math.max(animTicks(p, Xlr8Kit.ANIM_TAIL_SPIN, partial) >= 0 && animTicks(p, Xlr8Kit.ANIM_TAIL_SPIN, partial) < 13 ? 1f : 0f,
				animTicks(p, Xlr8Kit.ANIM_TAIL_WHIP, partial) >= 0 && animTicks(p, Xlr8Kit.ANIM_TAIL_WHIP, partial) < 8 ? 1f : 0f);
		float swing = tailSwing(p, partial);
		AlienParts.on(pose, model.body, () -> {
			pose.pushPose();
			pose.translate(0, 10.5f / 16f, 1.6f / 16f);
			// v0.17.1 (user: "xlr8s tail still shakes viciously when he moves"): the lift follows vanilla's smoothed walk speed and the
			// sway keeps one slow beat; it calms as the tail streams out behind him
			float ws = Mth.clamp(p.walkAnimation.speed(partial), 0f, 1f);
			float lift = Math.max(ws * 0.6f, spin * 0.75f);
			float[] w = { 3.0f, 2.4f, 1.8f, 1.1f };
			float[] len = { 4.5f, 4.5f, 4.0f, 3.5f };
			for (int i = 0; i < 4; i++) {
				float sway = Mth.sin(age * 0.15f + i * 0.9f) * (0.08f + i * 0.03f) * (1f - 0.6f * ws) * (1f - spin);
				pose.mulPose(Axis.XP.rotation(i == 0 ? -0.75f + lift : (0.22f - lift * 0.15f) * (1f - spin)));
				// v0.17.2 revamp (Tail Balance): on a hard turn the tail swings out to the side
				pose.mulPose(Axis.YP.rotation(sway + swing * (i == 0 ? 0.55f : 0.2f)));
				box(pose, vc, -w[i] / 2, -w[i] / 2, 0, w[i] / 2, w[i] / 2, len[i], TAIL, light);
				pose.translate(0, 0, (len[i] - 0.3f) / 16f);
			}
			box(pose, vc, -0.4f, -0.4f, 0, 0.4f, 0.4f, 1.5f, PALE, light);
			pose.popPose();
		});
		// (no ball-foot discs: the user removed his ball feet in v0.17.1)
		float vis = visor(p, partial);
		if (vis > 0.001f && vis < 1f) {
			// the visor half-way: the helmet's front slides down from the brow (its lower edge leading) -- base and hat layers
			VertexConsumer plate = buffers.getBuffer(RenderType.entityTranslucent(MASK));
			AlienParts.on(pose, model.head, () -> {
				PoseStack.Pose m = pose.last();
				float h = 8f * vis; // how far down it has come (px)
				float top = -8f / 16f, bot = (-8f + h) / 16f;
				float vTop = (16f - h) / 64f, vBot = 16f / 64f; // the visor's lower edge leads
				for (float[] L : new float[][] { { -4.02f, 8f / 64f, 16f / 64f, 0f }, { -4.52f, 40f / 64f, 48f / 64f, 8f } }) {
					float z = L[0] / 16f, x0 = -(4f + (L[3] > 0 ? 0.5f : 0f)) / 16f, x1 = -x0;
					AlienParts.quad(plate, m, 0xFFFFFFFF, light, x1, top, z, x0, top, z, x0, bot, z, x1, bot, z, 0, 0, -1, L[1], vTop, L[2], vBot);
				}
			});
		}
		// v0.17.2 revamp (Blur Punches): his arms blur -- two see-through ghost copies a beat behind each fist
		float pt = animTicks(p, Xlr8Kit.ANIM_PUNCHES, partial);
		if (pt >= 0f && pt < 12f) {
			VertexConsumer ghost = buffers.getBuffer(RenderType.entityTranslucent(skin(p)));
			for (int k = 1; k <= 2; k++) {
				int a = k == 1 ? 0x80 : 0x48;
				int col = (a << 24) | 0xC8E4FF;
				for (ModelPart arm : new ModelPart[] { model.rightArm, model.leftArm }) {
					float x = arm.xRot, y = arm.yRot;
					arm.xRot = x + (k * 0.38f) * (arm == model.rightArm ? 1f : -1f);
					arm.yRot = y + k * 0.12f * (arm == model.rightArm ? -1f : 1f);
					arm.render(pose, ghost, light, OverlayTexture.NO_OVERLAY, col);
					arm.xRot = x;
					arm.yRot = y;
				}
			}
		}
		// (every cutout draw is done: fetching another buffer type ends the previous one)
		// the green eyes glow: the skin's own eye pixels drawn again full-bright (the visor's faint eyes when it is down)
		boolean down = Xlr8Kit.visorDown(p);
		VertexConsumer eyes = buffers.getBuffer(RenderType.eyes(down ? MASK_EYES : EYES));
		model.head.render(pose, eyes, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
		model.hat.render(pose, eyes, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
		// v0.17.2 revamp (Kinetic Charge): at a full bar the visor's rim glows white
		if (down && vis >= 1f && Xlr8Kit.charge(p) >= 1f) {
			VertexConsumer rim = buffers.getBuffer(RenderType.eyes(EXTRAS));
			int a = 150 + (int) (90 * (0.5f + 0.5f * Mth.sin(age * 0.5f)));
			int col = (a << 24) | 0xFFFFFF;
			AlienParts.on(pose, model.head, () -> {
				PoseStack.Pose m = pose.last();
				float z = -4.6f / 16f, o = 4.55f / 16f, t = 0.45f / 16f;
				float top = -8.55f / 16f, bot = 0.05f / 16f;
				float u0 = WHITE[0], v0 = WHITE[1], u1 = WHITE[2], v1 = WHITE[3];
				AlienParts.quad(rim, m, col, LightTexture.FULL_BRIGHT, o, top, z, -o, top, z, -o, top + t, z, o, top + t, z, 0, 0, -1, u0, v0, u1, v1); // brow
				AlienParts.quad(rim, m, col, LightTexture.FULL_BRIGHT, o, top, z, o - t, top, z, o - t, bot, z, o, bot, z, 0, 0, -1, u0, v0, u1, v1);
				AlienParts.quad(rim, m, col, LightTexture.FULL_BRIGHT, -o + t, top, z, -o, top, z, -o, bot, z, -o + t, bot, z, 0, 0, -1, u0, v0, u1, v1);
			});
		}
	}

	@Override
	public void renderFirstPersonArm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, ModelPart arm, boolean right) {
		// v0.17.1 (user: "remove the claws on xlr8"): nothing extra on the first-person arm
	}

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		float[][] clip = switch (animId) {
			case Xlr8Kit.ANIM_PUNCHES -> PUNCHES;
			case Xlr8Kit.ANIM_TORNADO -> TORNADO;
			case Xlr8Kit.ANIM_STRIKE -> STRIKE;
			case Xlr8Kit.ANIM_SONIC -> SONIC;
			case Xlr8Kit.ANIM_SNATCH -> SNATCH;
			case Xlr8Kit.ANIM_DASH -> DASH;
			case Xlr8Kit.ANIM_RUSH -> RUSH;
			// v0.17.1 (user: "remove the hand and body animation for when xlr8s visor goes down"): none -- the visor slides itself
			case Xlr8Kit.ANIM_TAIL_SPIN -> TAIL_SPIN;
			case Xlr8Kit.ANIM_TAIL_WHIP -> TAIL_WHIP;
			case Xlr8Kit.ANIM_KICKS -> KICKS;
			case Xlr8Kit.ANIM_DECOYS -> DECOYS;
			case Xlr8Kit.ANIM_BURST -> BURST;
			case Xlr8Kit.ANIM_MINE -> MINE;
			case Xlr8Kit.ANIM_JAB -> JAB;
			case Xlr8Kit.ANIM_SWEEP -> SWEEP;
			case Xlr8Kit.ANIM_LOOSE -> LOOSE;
			case Xlr8Kit.ANIM_CARRY -> CARRY;
			default -> null;
		};
		boolean played = clip != null && QuirkKeyframes.play(m, clip, ticks, 1f);
		int f = flags(p);
		if ((f & Xlr8Kit.F_SKATE) != 0) {
			// v0.17.2 revamp: Friction Zero -- a deep speed-skater's crouch, arms swept back, one leg pushing out behind
			float stride = Mth.sin(age * 0.5f);
			m.body.xRot = 0.65f;
			m.body.y = 4.2f;
			m.head.y = 5.0f;
			m.head.xRot = -0.55f;
			m.rightArm.y = 6.0f;
			m.leftArm.y = 6.0f;
			m.rightArm.xRot = 1.2f;
			m.leftArm.xRot = 1.2f;
			m.rightArm.zRot = 0.25f;
			m.leftArm.zRot = -0.25f;
			m.rightLeg.z = 4.5f;
			m.leftLeg.z = 4.5f;
			m.rightLeg.y = 12.6f;
			m.leftLeg.y = 12.6f;
			m.rightLeg.xRot = -0.45f + 0.35f * stride;
			m.leftLeg.xRot = -0.45f - 0.35f * stride;
			m.rightLeg.zRot = 0.18f;
			m.leftLeg.zRot = -0.18f;
			return true;
		}
		// v0.17.1 (user: "give xlr8 the regular minecraft walk and sprint animation"): vanilla's own walk and sprint swing play
		if (!played && (f & Xlr8Kit.F_SPEED) != 0 && p.onClimbable() && !p.onGround()) {
			// running straight up a wall: the body pitched into it, legs pumping
			m.rightLeg.xRot = Mth.sin(age * 1.8f) * 1.1f;
			m.leftLeg.xRot = -Mth.sin(age * 1.8f) * 1.1f;
			m.rightArm.xRot = -0.6f - Mth.sin(age * 1.8f) * 0.4f;
			m.leftArm.xRot = -0.6f + Mth.sin(age * 1.8f) * 0.4f;
			return true;
		}
		if (!played && (f & Xlr8Kit.F_SPEED) != 0 && p.onGround()) {
			// v0.17.2 revamp: on his ball feet the stride pushes out sideways a touch, like a skate (on top of vanilla's swing)
			float ws = Mth.clamp(p.walkAnimation.speed(), 0f, 1f);
			float push = Mth.cos(p.walkAnimation.position() * 0.6662f) * 0.22f * ws;
			m.rightLeg.zRot += Math.max(0f, push);
			m.leftLeg.zRot -= Math.max(0f, -push);
		}
		return played;
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case Xlr8Kit.ANIM_PUNCHES -> FP_PUNCHES;
			case Xlr8Kit.ANIM_TORNADO -> FP_RUN;
			case Xlr8Kit.ANIM_RUSH -> FP_RUSH;
			case Xlr8Kit.ANIM_STRIKE -> FP_STRIKE;
			case Xlr8Kit.ANIM_SONIC -> FP_SONIC;
			case Xlr8Kit.ANIM_SNATCH, Xlr8Kit.ANIM_CARRY -> FP_SNATCH;
			case Xlr8Kit.ANIM_DASH -> FP_DASH;
			case Xlr8Kit.ANIM_SKATE -> FP_SKATE;
			case Xlr8Kit.ANIM_TAIL_SPIN, Xlr8Kit.ANIM_TAIL_WHIP, Xlr8Kit.ANIM_BURST, Xlr8Kit.ANIM_DECOYS -> FP_WIDE;
			case Xlr8Kit.ANIM_LOOSE -> FP_LOOSE;
			case Xlr8Kit.ANIM_KICKS -> FP_KICK;
			case Xlr8Kit.ANIM_MINE -> FP_MINE;
			case Xlr8Kit.ANIM_JAB -> FP_JAB;
			case Xlr8Kit.ANIM_SWEEP -> FP_SWEEP;
			default -> null;
		};
	}

	@Override
	public void tick(AbstractClientPlayer p) {
		int flags = flags(p);
		boolean mode = (flags & Xlr8Kit.F_SPEED) != 0;
		float sp = speed(p);
		// v0.17.2 revamp (Tail Balance): the tail swings out on hard turns while he runs
		float turn = Mth.wrapDegrees(p.yBodyRot - p.yBodyRotO);
		float[] s = SWING.computeIfAbsent(p.getUUID(), k -> new float[2]);
		s[0] = s[1];
		float want = mode && sp > 0.2f ? Mth.clamp(-turn * 0.035f, -0.75f, 0.75f) : 0f;
		s[1] = Mth.lerp(0.3f, s[1], want);
		// v0.17.2 revamp: no more cloud puffs and static -- the blue-and-black streak lines (Xlr8StreakTrail) replace them; only
		// the splash of running on water stays
		if (mode && !p.isInWater() && p.level().getFluidState(p.blockPosition().below()).isSource() && sp > 0.1f && p.getRandom().nextInt(2) == 0) {
			p.level().addParticle(ParticleTypes.SPLASH, p.getX(), p.getY() + 0.05, p.getZ(), 0, 0.1, 0);
		}
		if (p instanceof LocalPlayer lp && p == Minecraft.getInstance().player) {
			skid(lp, flags);
		}
	}

	/**
	 * v0.17.2 revamp (Friction): on ice, slime or honey in Speed Mode he skids -- let go of the keys and his momentum carries on
	 * for a second, easing off. The owner's client moves him, so the skid is simulated here.
	 */
	private static void skid(LocalPlayer p, int flags) {
		boolean slick = (flags & Xlr8Kit.F_SLICK) != 0 && (flags & Xlr8Kit.F_SPEED) != 0;
		Vec3 d = p.getDeltaMovement();
		boolean input = p.input != null && (Math.abs(p.input.forwardImpulse) > 0.01f || Math.abs(p.input.leftImpulse) > 0.01f);
		if (!slick) {
			SKID[2] = 0;
			return;
		}
		if (input) {
			SKID[0] = (float) d.x;
			SKID[1] = (float) d.z;
			SKID[2] = 20;
			return;
		}
		if (SKID[2] > 0) {
			float k = SKID[2] / 20f;
			p.setDeltaMovement(SKID[0] * k, d.y, SKID[1] * k);
			SKID[2]--;
		}
	}

	/** v0.17.2 revamp: the Kinetic Charge bar over his keys (blue, white-hot at full). */
	@Override
	public void hud(net.minecraft.client.gui.GuiGraphics g, LocalPlayer p, int x, int y, int w, float partial) {
		float c = Xlr8Kit.charge(p);
		boolean full = c >= 1f;
		OmnitrixHud.meter(g, Minecraft.getInstance(), x, y - 6, Component.translatable("hud.projecthero.omnitrix.xlr8.charge"), c,
				full ? 0xFFEAF6FF : 0xFF3A7BD5, Math.round(c * 100f) + "%", full ? 0xFFFFFFFF : 0xFF9FD8FF);
	}

	@Override
	public int hudHeight() {
		return 10;
	}

	@Override
	public float[] badge() {
		return new float[] { 0f, 3.6f, -2.06f, 3.0f };
	}
}
