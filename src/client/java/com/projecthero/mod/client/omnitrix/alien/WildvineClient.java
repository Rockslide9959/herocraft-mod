package com.projecthero.mod.client.omnitrix.alien;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.AlienClients;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
import com.projecthero.mod.client.omnitrix.alien.wildvine.WildvineFx;
import com.projecthero.mod.client.omnitrix.alien.wildvine.WildvineLineClient;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.wildvine.WildvineKit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 Wildvine's client kit (user: "Use this skin: https://www.planetminecraft.com/skin/wildvine-ben-10-4704618/ remove the pixel
 * omnitrix from his skin model and replace it with our omnitrix badge please"). The user's skin on the player body at 1.1 -- a lanky
 * plant with his one eye in the bulb of his head -- with loose, swaying vine arms and a pose per move (user: "edit and update animations
 * on all moves please"), third person here and first person in the {@code FP_} clips. His vines, seed pods, coils, tendrils and the
 * burrow's mound are drawn by {@link WildvineFx}; the swing moves him on his own client ({@link WildvineLineClient}).
 */
public final class WildvineClient implements AlienClientKit {
	private static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/omnitrix/wildvine.png");

	public static void register() {
		AlienClients.register(Aliens.WILDVINE.id(), new WildvineClient());
		WildvineFx.register();
		WildvineLineClient.register();
	}

	// ---------------------------------------------------------------- poses (QuirkKeyframes: tick, rArm xyz, lArm xyz, body x y, rLeg xyz, lLeg xyz, head x)
	/** Arms and the body's lean only: the legs keep walking. */
	private static float[] f(float t, float rax, float ray, float raz, float lax, float lay, float laz, float bx, float by) {
		return QuirkKeyframes.f(t, rax, ray, raz, lax, lay, laz, bx, by, 0, 0, 0, 0, 0, 0, 0);
	}

	/** With the legs: a braced stance ({@code rl} / {@code ll} = each leg's forward swing, negative forward). */
	private static float[] l(float t, float rax, float ray, float raz, float lax, float lay, float laz, float bx, float by, float rl, float ll, float head) {
		return QuirkKeyframes.f(t, rax, ray, raz, lax, lay, laz, bx, by, rl, 0, 0.06f, ll, 0, -0.06f, head);
	}

	private static final float AIM = QuirkKeyframes.AIM;

	/** Vine Lash: up over the shoulder, then the whole arm cracks out along the aim, the body turning into it; the vine runs back in. */
	private static final float[][] LASH = { QuirkKeyframes.rest(0), f(1.5f, -2.8f, 0.5f, 0.3f, -0.2f, 0, -0.35f, -0.12f, 0.35f),
			f(3.5f, AIM, AIM, 0, 0.35f, 0, -0.3f, 0.22f, -0.3f), f(6, AIM, AIM, 0, 0.3f, 0, -0.25f, 0.18f, -0.25f), f(9, -1.0f, 0.1f, 0.1f, 0.1f, 0, -0.15f, 0.05f, -0.1f),
			QuirkKeyframes.rest(12) };
	/** Vine Slash: both arms thrown across to his left, then swept out and over to the right as the fan tears out. */
	private static final float[][] SLASH = { QuirkKeyframes.rest(0), l(3, -1.55f, 0.95f, 0, -1.55f, 0.55f, 0, 0.1f, 0.55f, 0.2f, -0.25f, 0),
			l(6, -1.3f, -0.8f, 0.4f, -1.25f, -1.0f, 0, 0.42f, -0.6f, 0.35f, -0.4f, -0.1f), l(13, -1.3f, -0.95f, 0.45f, -1.25f, -1.1f, 0, 0.4f, -0.65f, 0.35f, -0.4f, -0.1f),
			l(18, -0.7f, -0.3f, 0.2f, -0.6f, -0.3f, 0, 0.12f, -0.2f, 0.1f, -0.12f, 0), QuirkKeyframes.rest(22) };
	/** Seed Bomb: the right hand reaches back over his shoulder for a pod, then a full overarm throw, the left arm leading. */
	private static final float[][] THROW = { QuirkKeyframes.rest(0), f(2, -3.0f, 0.35f, -0.25f, -1.3f, -0.1f, -0.2f, -0.2f, 0.45f),
			f(4, -0.85f, -0.25f, 0.1f, 0.35f, 0, -0.2f, 0.34f, -0.4f), f(7, -0.7f, -0.2f, 0.1f, 0.3f, 0, -0.15f, 0.25f, -0.3f), QuirkKeyframes.rest(12) };
	/** Seed Mines: both hands behind his back for the pods, then a deep bend pressing them into the ground ahead. */
	private static final float[][] PLANT = { QuirkKeyframes.rest(0), l(2.5f, -3.0f, 0, 0.3f, -3.0f, 0, -0.3f, -0.15f, 0, 0, 0, 0),
			l(5.5f, -1.15f, 0.25f, 0.1f, -1.15f, -0.25f, -0.1f, 0.85f, 0, 0.3f, -0.45f, -0.3f), l(11, -1.15f, 0.25f, 0.1f, -1.15f, -0.25f, -0.1f, 0.85f, 0, 0.3f, -0.45f, -0.3f),
			QuirkKeyframes.rest(15) };
	/** The mines' manual burst: a sharp fist pulled in, like jerking the vine they hang from. */
	private static final float[][] DETONATE = { QuirkKeyframes.rest(0), f(2, -1.6f, -0.3f, 0, -0.3f, 0, -0.2f, -0.05f, 0.2f), f(4.5f, -0.5f, 0.5f, -0.15f, -0.2f, 0, -0.2f, -0.15f, 0.3f),
			f(8, -0.5f, 0.5f, -0.15f, -0.2f, 0, -0.2f, -0.1f, 0.2f), QuirkKeyframes.rest(12) };
	/** Constrict: arms cast out along the aim, then held, the hands working as the coils bite (the sway is in {@link #pose}). */
	private static final float[][] WRAP = { QuirkKeyframes.rest(0), l(2, -2.5f, 0.2f, 0.2f, -2.5f, -0.2f, -0.2f, -0.15f, 0, 0, 0, 0),
			l(5, AIM, AIM, 0.06f, AIM, AIM, -0.06f, 0.18f, 0, 0.25f, -0.3f, 0), l(56, AIM, AIM, 0.06f, AIM, AIM, -0.06f, 0.14f, 0, 0.25f, -0.3f, 0), QuirkKeyframes.rest(62) };
	/** Vine Snare: arms out wide and up, then both palms slammed down, knees bending. */
	private static final float[][] SNARE = { QuirkKeyframes.rest(0), l(3, -2.2f, 0, 1.0f, -2.2f, 0, -1.0f, -0.2f, 0, 0, 0, 0),
			l(5, -0.45f, 0, 0.5f, -0.45f, 0, -0.5f, 0.55f, 0, 0.35f, -0.45f, -0.25f), l(13, -0.45f, 0, 0.5f, -0.45f, 0, -0.5f, 0.5f, 0, 0.35f, -0.45f, -0.25f),
			QuirkKeyframes.rest(18) };
	/** Regrowth: curled in on himself, then unfolding with both arms up, like a shoot opening. */
	private static final float[][] REGROW = { QuirkKeyframes.rest(0), l(4, -1.0f, 0.5f, -0.2f, -1.0f, -0.5f, 0.2f, 0.6f, 0, 0.2f, -0.25f, 0.3f),
			l(11, -2.9f, 0, 0.55f, -2.9f, 0, -0.55f, -0.25f, 0, 0, 0, -0.4f), l(17, -2.8f, 0, 0.7f, -2.8f, 0, -0.7f, -0.2f, 0, 0, 0, -0.3f), QuirkKeyframes.rest(23) };
	/** Overgrowth: both arms hauled high, a beat, then he folds down and drives them into the ground, held as the thicket rises. */
	private static final float[][] OVERGROWTH = { QuirkKeyframes.rest(0), l(5, -3.0f, 0, 0.25f, -3.0f, 0, -0.25f, -0.3f, 0, 0.1f, -0.1f, -0.4f),
			l(7.5f, -1.0f, 0.15f, 0.05f, -1.0f, -0.15f, -0.05f, 1.0f, 0, 0.4f, -0.55f, 0.1f), l(24, -1.0f, 0.15f, 0.05f, -1.0f, -0.15f, -0.05f, 0.95f, 0, 0.4f, -0.55f, 0.1f),
			QuirkKeyframes.rest(31) };
	/** Vine Slam: arm out at the prey; both hands on the vine and leaning back to heave it up; over the top and hurled down. */
	private static final float[][] SLAM = { QuirkKeyframes.rest(0), l(2.5f, AIM, AIM, 0, -0.5f, 0, -0.3f, 0.15f, -0.15f, 0.2f, -0.25f, 0),
			l(9, -3.0f, 0.1f, 0.2f, -3.0f, -0.1f, -0.2f, -0.38f, 0, -0.25f, 0.2f, -0.2f), l(13, -0.7f, 0.1f, 0.1f, -0.7f, -0.1f, -0.1f, 0.75f, 0, 0.45f, -0.5f, 0.2f),
			l(19, -0.7f, 0.1f, 0.1f, -0.7f, -0.1f, -0.1f, 0.7f, 0, 0.45f, -0.5f, 0.2f), QuirkKeyframes.rest(24) };
	/** Camouflage: he folds up small, arms crossed over his chest, before the green closes over him. */
	private static final float[][] CAMO = { QuirkKeyframes.rest(0), l(5, -1.2f, 0.7f, 0, -1.2f, -0.7f, 0, 0.5f, 0, 0.2f, -0.25f, 0.3f), l(9, -1.2f, 0.7f, 0, -1.2f, -0.7f, 0, 0.5f, 0, 0.2f, -0.25f, 0.3f),
			QuirkKeyframes.rest(12) };
	/** The stretched-arm punch. */
	private static final float[][] PUNCH = { QuirkKeyframes.rest(0), f(1.5f, -1.0f, 0.5f, 0.1f, -0.1f, 0, -0.2f, 0, 0.3f), f(3, AIM, AIM, 0, 0.25f, 0, -0.2f, 0.18f, -0.3f),
			f(5, AIM, AIM, 0, 0.2f, 0, -0.15f, 0.14f, -0.2f), QuirkKeyframes.rest(9) };
	/** Burrow, going under: arms up together like a diver, the whole body sinking (the sink is in {@link #pose}). */
	private static final float[][] DIVE = { QuirkKeyframes.rest(0), l(3, -3.05f, 0, 0.12f, -3.05f, 0, -0.12f, 0, 0, 0, 0, 0.25f),
			l(WildvineKit.DIVE_TICKS + 2, -3.1f, 0, 0.05f, -3.1f, 0, -0.05f, 0, 0, 0, 0, 0.25f) };
	/** Burrow, bursting up: arms punched high, spreading as he clears the ground. */
	private static final float[][] EMERGE = { l(0, -3.0f, 0, 0.1f, -3.0f, 0, -0.1f, 0, 0, 0, 0, -0.3f), l(5, -2.7f, 0, 0.55f, -2.7f, 0, -0.55f, -0.15f, 0, 0.2f, -0.2f, -0.2f),
			l(10, -2.2f, 0, 0.9f, -2.2f, 0, -0.9f, -0.05f, 0, 0.1f, -0.1f, 0), QuirkKeyframes.rest(15) };

	/** The first-person clips. */
	private static float[] p(float t, float pitch, float yaw, float roll, float x, float y, float z) {
		return FirstPersonClip.f(t, pitch, yaw, roll, x, y, z);
	}

	private static final FirstPersonClip FP_LASH = FirstPersonClip.of(p(0, 0, 0, 0, 0, 0, 0), p(1.5f, -55, 10, 0, 0.05f, 0.35f, 0.15f), p(3.5f, 8, 18, 0, 0.1f, -0.02f, -0.42f),
			p(7, 5, 15, 0, 0.08f, -0.03f, -0.36f), p(12, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_SLASH = FirstPersonClip.of(p(0, 0, 0, 0, 0, 0, 0), p(3, -10, 40, 0, 0.3f, 0.05f, -0.1f), p(6, -4, -35, 10, -0.2f, -0.05f, -0.35f),
			p(14, -4, -38, 10, -0.2f, -0.05f, -0.32f), p(22, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_THROW = FirstPersonClip.of(p(0, 0, 0, 0, 0, 0, 0), p(2, -70, 5, 10, 0, 0.55f, 0.35f), p(4, 12, 22, 0, 0.12f, 0, -0.45f), p(7, 10, 18, 0, 0.1f, -0.05f, -0.35f),
			p(12, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_PLANT = FirstPersonClip.of(p(0, 0, 0, 0, 0, 0, 0), p(2.5f, 25, 0, 0, 0, -0.4f, 0.25f), p(5.5f, 45, 12, 0, 0.1f, -0.2f, -0.3f), p(11, 45, 12, 0, 0.1f, -0.2f, -0.3f),
			p(15, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_DETONATE = FirstPersonClip.of(p(0, 0, 0, 0, 0, 0, 0), p(2, -12, 15, 0, 0.05f, 0.05f, -0.3f), p(4.5f, 30, 25, 0, 0.1f, -0.12f, 0.1f), p(8, 30, 25, 0, 0.1f, -0.12f, 0.1f),
			p(12, 0, 0, 0, 0, 0, 0));
	/** Hanging on the vine: the arm up the line for as long as he hangs ({@link #FP_RELEASE} lowers it). */
	private static final FirstPersonClip FP_SWING = FirstPersonClip.of(p(0, 0, 0, 0, 0, 0, 0), p(3, -45, 12, 0, 0.08f, 0.28f, -0.2f), p(80000, -45, 12, 0, 0.08f, 0.28f, -0.2f));
	private static final FirstPersonClip FP_RELEASE = FirstPersonClip.of(p(0, -45, 12, 0, 0.08f, 0.28f, -0.2f), p(5, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_WRAP = FirstPersonClip.of(p(0, 0, 0, 0, 0, 0, 0), p(2, -40, 0, 0, 0, 0.3f, 0.1f), p(5, 0, 14, 0, 0.1f, 0.04f, -0.42f), p(56, 0, 14, 0, 0.1f, 0.04f, -0.36f),
			p(62, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_SNARE = FirstPersonClip.of(p(0, 0, 0, 0, 0, 0, 0), p(3, -45, -15, 0, -0.1f, 0.3f, 0), p(5, 40, 8, 0, 0.05f, -0.25f, -0.25f), p(13, 40, 8, 0, 0.05f, -0.25f, -0.25f),
			p(18, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_REGROW = FirstPersonClip.of(p(0, 0, 0, 0, 0, 0, 0), p(4, 15, 25, 0, 0.2f, -0.1f, 0.1f), p(11, -60, -8, 0, -0.1f, 0.4f, -0.1f), p(17, -60, -8, 0, -0.1f, 0.4f, -0.1f),
			p(23, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_OVERGROWTH = FirstPersonClip.of(p(0, 0, 0, 0, 0, 0, 0), p(5, -65, 0, 0, 0, 0.55f, 0.1f), p(7.5f, 50, 10, 0, 0.1f, -0.3f, -0.3f), p(24, 50, 10, 0, 0.1f, -0.3f, -0.3f),
			p(31, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_SLAM = FirstPersonClip.of(p(0, 0, 0, 0, 0, 0, 0), p(2.5f, 0, 14, 0, 0.1f, 0.04f, -0.4f), p(9, -65, 5, 0, 0.03f, 0.6f, 0.2f), p(13, 35, 10, 0, 0.08f, -0.25f, -0.35f),
			p(19, 35, 10, 0, 0.08f, -0.25f, -0.35f), p(24, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_CAMO = FirstPersonClip.of(p(0, 0, 0, 0, 0, 0, 0), p(5, 20, 32, 0, 0.32f, -0.05f, 0.05f), p(9, 20, 32, 0, 0.32f, -0.05f, 0.05f), p(12, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_PUNCH = FirstPersonClip.of(p(0, 0, 0, 0, 0, 0, 0), p(2, -20, 25, 0, 0.15f, 0.08f, -0.35f), p(7, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_DIVE = FirstPersonClip.of(p(0, 0, 0, 0, 0, 0, 0), p(3, -60, 0, 0, 0, 0.5f, 0), p(WildvineKit.DIVE_TICKS, -60, 0, 0, 0, 0.9f, 0)).both();
	private static final FirstPersonClip FP_EMERGE = FirstPersonClip.of(p(0, -65, 0, 0, 0, 0.3f, 0), p(5, -60, -14, 0, -0.1f, 0.5f, 0), p(10, -30, -25, 0, -0.2f, 0.3f, 0), p(15, 0, 0, 0, 0, 0, 0)).both();

	@Override
	public ResourceLocation skin() {
		return SKIN;
	}

	@Override
	public float[] badge() {
		// where the skin painted its little Omnitrix: the middle of the top of his chest (texels 23..24 x 21..22, painted out),
		// just proud of the jacket layer that covers it
		return new float[] { 0f, 2.0f, -2.3f, 2.6f };
	}

	private static void set(ModelPart part, float x, float y, float z) {
		part.xRot = x;
		part.yRot = y;
		part.zRot = z;
	}

	/** The right arm straight at a world point from the shoulder (Ladybug's pose on her string). */
	private static void pointAt(AbstractClientPlayer p, ModelPart arm, Vec3 target, float partial) {
		Vec3 shoulder = p.getPosition(partial).add(0, p.getBbHeight() * 0.78, 0);
		Vec3 d = target.subtract(shoulder);
		float pitch = (float) Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z));
		float dirYaw = (float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG);
		float body = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot);
		set(arm, -Mth.HALF_PI - pitch, Mth.wrapDegrees(dirYaw - body) * Mth.DEG_TO_RAD, 0f);
	}

	/** The whole model {@code px} model pixels down (it sinks into the ground it stands on). */
	private static void sink(HumanoidModel<?> m, float px) {
		for (ModelPart part : new ModelPart[] { m.head, m.hat, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg }) {
			part.y += px;
		}
		if (m instanceof net.minecraft.client.model.PlayerModel<?> pm) {
			for (ModelPart part : new ModelPart[] { pm.jacket, pm.rightSleeve, pm.leftSleeve, pm.rightPants, pm.leftPants }) {
				part.y += px;
			}
		}
	}

	private static float ease(float k) {
		k = Mth.clamp(k, 0f, 1f);
		return k * k * (3f - 2f * k);
	}

	private static boolean playAimed(HumanoidModel<?> m, float[][] clip, float ticks) {
		float[] p = QuirkKeyframes.sample(clip, ticks, m.head.xRot - Mth.HALF_PI, m.head.yRot);
		if (p == null) {
			return false;
		}
		QuirkKeyframes.apply(m, p, QuirkKeyframes.weight(ticks, QuirkKeyframes.length(clip)));
		return true;
	}

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		int flags = Omnitrix.state(p).kitFlags;
		if ((flags & WildvineKit.F_SWING) != 0) {
			// Ladybug's hang: the right arm straight up the line, the other out for balance, the legs trailing and kicking with the arc
			float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
			Vec3 hook = WildvineKit.hook(p);
			if (hook != null) {
				pointAt(p, m.rightArm, hook, partial);
			}
			set(m.leftArm, -0.4f + 0.2f * Mth.sin(age * 0.2f), 0f, -0.6f);
			m.rightLeg.xRot = 0.35f + 0.2f * Mth.sin(age * 0.2f);
			m.leftLeg.xRot = -0.45f - 0.2f * Mth.sin(age * 0.2f);
			return true;
		}
		float[][] clip = switch (animId) {
			case WildvineKit.ANIM_LASH -> LASH;
			case WildvineKit.ANIM_SLASH -> SLASH;
			case WildvineKit.ANIM_THROW -> THROW;
			case WildvineKit.ANIM_PLANT -> PLANT;
			case WildvineKit.ANIM_DETONATE -> DETONATE;
			case WildvineKit.ANIM_WRAP -> WRAP;
			case WildvineKit.ANIM_SNARE -> SNARE;
			case WildvineKit.ANIM_REGROW -> REGROW;
			case WildvineKit.ANIM_OVERGROWTH -> OVERGROWTH;
			case WildvineKit.ANIM_SLAM -> SLAM;
			case WildvineKit.ANIM_CAMO -> CAMO;
			case WildvineKit.ANIM_DIVE -> DIVE;
			case WildvineKit.ANIM_EMERGE -> EMERGE;
			case WildvineKit.ANIM_PUNCH -> PUNCH;
			default -> null;
		};
		boolean played = clip != null && playAimed(m, clip, ticks);
		if (played) {
			if (animId == WildvineKit.ANIM_DIVE) {
				sink(m, 34f * ease(ticks / WildvineKit.DIVE_TICKS));
			} else if (animId == WildvineKit.ANIM_EMERGE) {
				sink(m, 34f * (1f - ease(ticks / 6f)));
			} else if (animId == WildvineKit.ANIM_WRAP && ticks > 5f && ticks < 56f) {
				// the hands work the coils
				m.rightArm.xRot += Mth.sin(ticks * 0.9f) * 0.06f;
				m.leftArm.xRot -= Mth.sin(ticks * 0.9f) * 0.06f;
			}
		} else {
			// loose vine arms: they hang a little out from his thin body and sway like stems
			m.rightArm.zRot += 0.12f + Mth.sin(age * 0.09f) * 0.05f;
			m.leftArm.zRot -= 0.12f + Mth.sin(age * 0.09f + 1.3f) * 0.05f;
			m.rightArm.xRot += Mth.sin(age * 0.07f) * 0.04f;
			m.leftArm.xRot += Mth.sin(age * 0.07f + 2f) * 0.04f;
		}
		return played;
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case WildvineKit.ANIM_LASH -> FP_LASH;
			case WildvineKit.ANIM_SLASH -> FP_SLASH;
			case WildvineKit.ANIM_THROW -> FP_THROW;
			case WildvineKit.ANIM_PLANT -> FP_PLANT;
			case WildvineKit.ANIM_DETONATE -> FP_DETONATE;
			case WildvineKit.ANIM_SWING -> FP_SWING;
			case WildvineKit.ANIM_RELEASE -> FP_RELEASE;
			case WildvineKit.ANIM_WRAP -> FP_WRAP;
			case WildvineKit.ANIM_SNARE -> FP_SNARE;
			case WildvineKit.ANIM_REGROW -> FP_REGROW;
			case WildvineKit.ANIM_OVERGROWTH -> FP_OVERGROWTH;
			case WildvineKit.ANIM_SLAM -> FP_SLAM;
			case WildvineKit.ANIM_CAMO -> FP_CAMO;
			case WildvineKit.ANIM_DIVE -> FP_DIVE;
			case WildvineKit.ANIM_EMERGE -> FP_EMERGE;
			case WildvineKit.ANIM_PUNCH -> FP_PUNCH;
			default -> null;
		};
	}

	/** v0.17.2 (user: "can you add little seed bomb models to wildvines back please"): the seed pods on his back, and in his hands. */
	@Override
	public void renderExtras(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p,
			net.minecraft.client.model.PlayerModel<AbstractClientPlayer> model, float partial, float age) {
		WildvineFx.backPods(pose, buffers, light, p, model, partial, age);
		var s = Omnitrix.state(p);
		float t = p.level().getGameTime() + partial - s.animStart;
		if (s.animId == WildvineKit.ANIM_THROW && t < WildvineKit.THROW_AT) {
			WildvineFx.handPod(pose, buffers, light, model.rightArm, age);
		} else if (s.animId == WildvineKit.ANIM_PLANT && t < WildvineKit.PLANT_AT) {
			WildvineFx.handPod(pose, buffers, light, model.rightArm, age);
			WildvineFx.handPod(pose, buffers, light, model.leftArm, age);
		}
	}

	// ---------------------------------------------------------------- the HUD: his seed pods (in first person he cannot see his back)

	@Override
	public int hudHeight() {
		return 10;
	}

	@Override
	public void hud(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.player.LocalPlayer p, int x, int y, int w, float partial) {
		int n = WildvineKit.pods(p);
		float next = n >= WildvineKit.PODS ? 0f : WildvineKit.podRegrowth(p, partial);
		com.projecthero.mod.client.omnitrix.OmnitrixHud.meter(g, Minecraft.getInstance(), x, y - 6, Component.translatable("hud.projecthero.omnitrix.wildvine.pods"),
				(n + next) / WildvineKit.PODS, 0xFF000000 | WildvineKit.COLOUR, n + "/" + WildvineKit.PODS, 0xFFE2E2E2);
	}

	// ---------------------------------------------------------------- the burrow: a mound moving under the ground

	/** How long he has been under, ticks; negative above ground. */
	private static float under(AbstractClientPlayer p, float partial) {
		if ((Omnitrix.state(p).kitFlags & WildvineKit.F_BURROW) == 0) {
			return -1f;
		}
		return p.level().getGameTime() + partial - WildvineKit.burrowAt(p);
	}

	/** He sinks on his own body for the dive, the mound takes over once he is down. */
	private static boolean moundShown(AbstractClientPlayer p) {
		return under(p, 0f) >= WildvineKit.DIVE_TICKS;
	}

	@Override
	public boolean customBody(AbstractClientPlayer p) {
		return moundShown(p);
	}

	@Override
	public void renderBody(AbstractClientPlayer p, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		WildvineFx.mound(p, partial, ease((under(p, partial) - WildvineKit.DIVE_TICKS + 3f) / 5f), pose, buffers, light);
	}

	@Override
	public boolean renderFirstPersonHand(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, boolean right) {
		return moundShown(p); // under the ground: no arms to show
	}
}
