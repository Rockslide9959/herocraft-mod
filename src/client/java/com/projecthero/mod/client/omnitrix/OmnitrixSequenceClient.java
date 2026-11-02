package com.projecthero.mod.client.omnitrix;

import java.util.HashMap;
import java.util.Map;

import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixLongTransform;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 (user: "this is the fourarms transformation from the ben 10 show, can you try and recreate this in minecraft please, do your
 * absolute best ... use vfx and try not to use particles too much"): the client half of {@link OmnitrixLongTransform} -- the
 * episode's Four Arms transformation shot for shot, cut to its own soundtrack. Video second {@code v} is tick
 * {@code (v - 2.55) * 20}; the beats (ticks after the right-click):
 * <pre>
 *   0-5     the slap (front medium shot)                      v 2.55-2.8
 *   4-18    FULL GREEN FLASH                                   v 2.8-3.4
 *   14-29   the dial close-up, green light bursting off it     v 3.3-4.0
 *   29-49   the hand swells -- peach flesh, red veins, red rim v 4.0-5.0   (the cartoon stage: olive field, black Kirby dots)
 *   49-74   up the forearm, the shoulder, the chest            v 5.0-6.25
 *   74-89   the neck and the face                              v 6.25-7.0
 *   87-103  GOLDEN FLASH -- the body changes under it (95)     v 7.0-7.7
 *   101-128 from behind: the second pair of arms splits out    v 7.6-9.0
 *   128-136 whip pan round to the front                        v 9.0-9.35
 *   136-162 the red head, four eyes, the roar (from below)     v 9.35-10.65
 *   162-175 low-angle torso                                    v 10.65-11.3
 *   175-209 the full-body side-on power stance                 v 11.3-13.0
 *   207-216 WHITE FLASH                                        v 13.0-13.35
 *   211-249 the same stance on the green ray burst             v 13.1-15.0
 *   236-249 FULL GREEN FLASH                                   v 14.35-15.0
 *   249-261 back in the world, the green fading off           v 15.0-15.6
 * </pre>
 * The holder's view is a scripted camera ({@link #camera}, {@code OmnitrixSequenceCameraMixin}), the HUD is hidden, and from tick 29 the
 * world behind them is replaced by the show's flat cartoon stage ({@link #renderLast}: the stage is drawn over everything, the depth
 * cleared, and their body drawn again on top). The swelling ({@link #renderSwell}) and the poses are on the body, so everyone nearby
 * sees those, plus the dial's light, a golden glow and the usual green burst at the end; the music plays for them too.
 * Everything is flat-colour quads (no particles).
 */
public final class OmnitrixSequenceClient {
	public static final ResourceLocation SWELL = ProjectHeroMod.id("textures/entity/omnitrix/swell.png");
	public static final ResourceLocation SWELL_GLOW = ProjectHeroMod.id("textures/entity/omnitrix/swell_glow.png");

	/** The beats (ticks). */
	static final float SLAP_END = 5f, WATCH_AT = 14f, HAND_AT = 29f, ARM_AT = 49f, FACE_AT = 74f, GOLD_AT = 87f, BACK_AT = 101f, WHIP_AT = 128f,
			HEAD_AT = 136f, TORSO_AT = 162f, POSE_AT = 175f, WHITE_AT = 207f, BURST_STAGE_AT = 211f, GREEN2_AT = 236f, WORLD_AT = 249f;
	/** The green burst everyone else sees in the world (OmnitrixTransformFx), under the second green flash. */
	public static final int BURST_AT = 238;

	/** Colours: the show's flat transformation green, the olive stage, the golden flash. */
	private static final int FLASH_GREEN = 0x40DC14, OLIVE_IN = 0xA9CB63, OLIVE_MID = 0x86AE48, OLIVE_OUT = 0x4C7524;
	private static final int BURST_IN = 0x9CF77A, BURST_MID = 0x3FD33A, BURST_OUT = 0x1D8A18, BURST_RAY = 0x15641A, BURST_DOT = 0x0D3F0B;
	private static final int GOLD_IN = 0xFFF8DA, GOLD_OUT = 0xF4BC2E;

	/** The holder's HUD was hidden for it (and whether it was hidden before). */
	private static boolean hidHud, hudWasHidden;
	/** Where each player's watch was last drawn (world), for the close-up and the dial's light. */
	private static final Map<Integer, Vec3> WRIST = new HashMap<>();

	/** Set only by the screenshot harness: freezes the sequence at this many ticks in (-1 = off). */
	public static volatile float freezeAt = -1f;

	private OmnitrixSequenceClient() {
	}

	public static void initialize() {
		ClientTickEvents.END_CLIENT_TICK.register(OmnitrixSequenceClient::tick);
		WorldRenderEvents.AFTER_ENTITIES.register(OmnitrixSequenceClient::renderWorld);
		WorldRenderEvents.LAST.register(OmnitrixSequenceClient::renderLast);
	}

	public static void clear() {
		WRIST.clear();
	}

	// ================================================================ time

	/** Ticks into {@code p}'s long transformation, or -1. */
	public static float age(Player p, float partial) {
		float t = OmnitrixLongTransform.age(p, partial);
		if (t >= 0f && freezeAt >= 0f) {
			return freezeAt;
		}
		return t;
	}

	private static float smooth(float x) {
		x = Mth.clamp(x, 0f, 1f);
		return x * x * (3f - 2f * x);
	}

	/** 0 -> 1 with a springy overshoot over {@code len} ticks (a bulge popping up). */
	private static float pop(float t, float len) {
		if (t <= 0f) {
			return 0f;
		}
		float x = Mth.clamp(t / len, 0f, 1f);
		float c1 = 2.4f, c3 = c1 + 1f;
		return 1f + c3 * (float) Math.pow(x - 1, 3) + c1 * (float) Math.pow(x - 1, 2);
	}

	/** Up over {@code in}, held, down over {@code out}: a flash's envelope. */
	private static float env(float t, float a, float in, float b, float out) {
		if (t < a || t > b + out) {
			return 0f;
		}
		return t < a + in ? smooth((t - a) / in) : t <= b ? 1f : 1f - smooth((t - b) / out);
	}

	private static float hash(long seed, int i) {
		long h = seed * 0x9E3779B97F4A7C15L + i * 0xC2B2AE3D27D4EB4FL;
		h ^= h >>> 31;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 29;
		return (h >>> 40) / (float) (1L << 24);
	}

	private static long seed(Player p) {
		Long start = Omnitrix.state(p).timers.get(OmnitrixLongTransform.TIMER);
		return (start == null ? 0L : start) * 31L + p.getId();
	}

	// ================================================================ tick: the body held to its mark, the HUD

	private static void tick(Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			WRIST.clear();
			return;
		}
		boolean mine = camera(mc.player, 0f) != null;
		if (mine && !hidHud) {
			hudWasHidden = mc.options.hideGui;
			mc.options.hideGui = true;
			hidHud = true;
		} else if (!mine && hidHud) {
			mc.options.hideGui = hudWasHidden;
			hidHud = false;
		}
		for (Player p : mc.level.players()) {
			if (age(p, 0f) < 0f) {
				WRIST.remove(p.getId());
				continue;
			}
			float yaw = OmnitrixLongTransform.yaw(Omnitrix.state(p));
			p.yBodyRotO = p.yBodyRot = yaw;
			p.yHeadRotO = p.yHeadRot = yaw;
		}
	}

	/** From {@code OmnitrixLayer}, inside the left arm's bone, just before the watch is drawn: remembers where the dial is. */
	public static void noteWrist(AbstractClientPlayer p, PoseStack pose) {
		if (age(p, 0f) < 0f) {
			return;
		}
		Vector3f v = pose.last().pose().transformPosition(new Vector3f(1f / 16f, 8.6f / 16f, -0.6f / 16f));
		Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
		WRIST.put(p.getId(), new Vec3(cam.x + v.x, cam.y + v.y, cam.z + v.z));
	}

	// ================================================================ the camera

	/*
	 * Camera keys: tick, distance from the point looked at, the angle round them (deg; 0 = in front, + = toward their right), camera
	 * height, then the point looked at (height, right, forward) -- all in a 1.8-block human's blocks, scaled with the body once he is
	 * Four Arms. A key a hair after the one before it is a cut. -1 for the look point = the watch.
	 */
	private static final float W = -1f;
	private static final float[][] CAM = {
			{ 0f, 1.9f, 12f, 1.35f, 1.15f, 0f, 0.15f },
			{ 5f, 1.55f, 10f, 1.32f, 1.15f, 0f, 0.15f },
			// the dial close-up (from above and in front, pushing in)
			{ 5.01f, 0.46f, 18f, W, W, W, W },
			{ 29f, 0.32f, 8f, W, W, W, W },
			// the hand swelling, from their left front, sliding up the arm
			{ 29.01f, 0.62f, -58f, 1.14f, 1.06f, -0.08f, 0.3f },
			{ 49f, 0.7f, -70f, 1.24f, 1.16f, -0.2f, 0.18f },
			{ 74f, 0.78f, -84f, 1.5f, 1.38f, -0.32f, 0.02f },
			// the face, from low at the side
			{ 74.01f, 0.72f, -18f, 1.4f, 1.6f, 0f, 0f },
			{ 101f, 0.6f, -14f, 1.44f, 1.62f, 0f, 0f },
			// from behind, low: the arms splitting
			{ 101.01f, 2.3f, 180f, 0.62f, 1.25f, 0f, 0f },
			{ 128f, 1.95f, 176f, 0.7f, 1.3f, 0f, 0f },
			// the whip pan round the right side to the face (the angle runs straight, so it sweeps)
			{ 132f, 1.35f, 95f, 1.2f, 1.5f, 0f, 0f },
			{ 136f, 0.92f, 4f, 1.32f, 1.6f, 0f, 0f },
			{ 162f, 0.76f, 0f, 1.3f, 1.63f, 0f, 0f },
			// low-angle torso
			{ 162.01f, 1.65f, 16f, 0.5f, 1.22f, 0f, 0f },
			{ 175f, 1.5f, 14f, 0.48f, 1.2f, 0f, 0f },
			// the full-body side-on stance (and the green burst behind it)
			{ 175.01f, 2.35f, 60f, 0.8f, 0.95f, 0f, 0f },
			{ 249f, 2.1f, 55f, 0.8f, 0.98f, 0f, 0f } };

	/** Where the holder's camera is: {x, y, z, yaw, pitch}, or null when it is not theirs to show (the world view again). */
	public static double[] camera(Player p, float partial) {
		if (p != Minecraft.getInstance().player) {
			return null;
		}
		float t = age(p, partial);
		if (t < 0f || t >= WORLD_AT) {
			return null;
		}
		int i = 0;
		while (i < CAM.length - 2 && t > CAM[i + 1][0]) {
			i++;
		}
		float[] a = CAM[i], b = CAM[i + 1];
		float f = (t - a[0]) / Math.max(0.001f, b[0] - a[0]);
		// the whip pan runs at speed through its middle key; everything else eases
		f = a[0] >= WHIP_AT && b[0] <= HEAD_AT ? Mth.clamp(f, 0f, 1f) : smooth(f);
		float s = t >= OmnitrixLongTransform.TRANSFORM_AT ? Math.max(1f, p.getBbHeight() / 1.8f) : 1f;
		float yaw = OmnitrixLongTransform.yaw(Omnitrix.state(p));
		Vec3 base = p.getPosition(partial);
		Vec3 fwd = forward(yaw), right = new Vec3(-fwd.z, 0, fwd.x);
		Vec3 look;
		if (a[4] == W) {
			Vec3 w = WRIST.get(p.getId());
			look = w != null ? w : base.add(0, 1.08, 0).add(fwd.scale(0.36)).add(right.scale(-0.02));
		} else {
			float lh = Mth.lerp(f, a[4], b[4]), lr = Mth.lerp(f, a[5], b[5]), lf = Mth.lerp(f, a[6], b[6]);
			look = base.add(0, lh * s, 0).add(right.scale(lr * s)).add(fwd.scale(lf * s));
		}
		float dist = Mth.lerp(f, a[1], b[1]) * s;
		float ang = Mth.lerp(f, a[2], b[2]) * Mth.DEG_TO_RAD;
		Vec3 dir = fwd.scale(Mth.cos(ang)).add(right.scale(Mth.sin(ang)));
		double camY;
		if (a[3] == W) {
			// over the dial: a little above it, looking down onto the face
			camY = look.y + dist * 0.75;
			dist *= 0.66f;
		} else {
			camY = base.y + Mth.lerp(f, a[3], b[3]) * s;
		}
		Vec3 pos = new Vec3(look.x + dir.x * dist, camY, look.z + dir.z * dist);
		// the roar shakes the shot a little
		if (t >= HEAD_AT + 8 && t < TORSO_AT) {
			float k = 0.012f * s * (1f - smooth((t - HEAD_AT - 8) / 18f));
			pos = pos.add(Mth.sin(t * 2.7f) * k, Mth.cos(t * 3.1f) * k, 0);
		}
		double dx = look.x - pos.x, dy = look.y - pos.y, dz = look.z - pos.z;
		double horiz = Math.sqrt(dx * dx + dz * dz);
		float camYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float camPitch = (float) -Math.toDegrees(Math.atan2(dy, horiz));
		return new double[] { pos.x, pos.y, pos.z, camYaw, camPitch };
	}

	/** The field of view while the holder watches it (fixed: the root's slowness would otherwise zoom it), or -1. */
	public static double fov(float partial) {
		LocalPlayer p = Minecraft.getInstance().player;
		if (p == null || camera(p, partial) == null) {
			return -1;
		}
		float t = age(p, partial);
		// a slow push on the dial, the roar punching in
		double fov = 62;
		if (t >= WATCH_AT && t < HAND_AT) {
			fov -= 8 * smooth((t - WATCH_AT) / 15f);
		} else if (t >= HEAD_AT + 6 && t < TORSO_AT) {
			fov -= 7 * smooth((t - HEAD_AT - 6) / 4f);
		}
		return fov;
	}

	private static Vec3 forward(float yawDeg) {
		float rad = yawDeg * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(rad), 0, Mth.cos(rad));
	}

	// ================================================================ the poses

	private static void rot(ModelPart part, float w, float x, float y, float z) {
		part.xRot = Mth.lerp(w, part.xRot, x);
		part.yRot = Mth.lerp(w, part.yRot, y);
		part.zRot = Mth.lerp(w, part.zRot, z);
	}

	/**
	 * Called last by {@link OmnitrixPose#apply}. Before the change the raised-watch pose (still up: the dial was never let go) gets a
	 * tremble that builds, and the head thrown back as the face swells; after it, Four Arms' beats: arms flung out as the second pair
	 * splits, the roar, the flex, and the side-on power stance.
	 */
	public static void pose(AbstractClientPlayer p, HumanoidModel<?> m, float partial) {
		float t = age(p, partial);
		if (t < 0f) {
			return;
		}
		m.head.yRot = 0f;
		if (t < OmnitrixLongTransform.TRANSFORM_AT) {
			float shake = smooth((t - HAND_AT) / 30f) * (t < GOLD_AT ? 1f : 0.5f);
			if (shake > 0f) {
				m.leftArm.xRot += 0.05f * shake * Mth.sin(t * 2.9f);
				m.leftArm.zRot += 0.04f * shake * Mth.cos(t * 3.7f);
				m.rightArm.xRot += 0.04f * shake * Mth.sin(t * 3.3f + 1f);
				m.body.xRot += 0.03f * shake * Mth.sin(t * 2.1f);
			}
			// the face swelling: the head jerks back, the chest arches
			float back = smooth((t - FACE_AT) / 8f);
			if (back > 0f) {
				m.head.xRot = Mth.lerp(back, m.head.xRot, -0.35f + 0.06f * Mth.sin(t * 2.4f));
				m.body.xRot = Mth.lerp(back, m.body.xRot, -0.12f);
				// the swollen arms drop away from the dial, hanging heavy and shaking (and out of the face's shot)
				m.leftArm.y = Mth.lerp(back, m.leftArm.y, 2f);
				m.rightArm.y = Mth.lerp(back, m.rightArm.y, 2f);
				rot(m.leftArm, back, 0.15f + 0.05f * Mth.sin(t * 3.1f), 0f, -0.45f);
				rot(m.rightArm, back, 0.15f + 0.05f * Mth.sin(t * 2.7f), 0f, 0.45f);
			}
			m.hat.copyFrom(m.head);
			return;
		}
		float breathe = Mth.sin(t * 0.25f) * 0.04f;
		if (t < WHIP_AT) {
			// from behind: the arms forced out and up as the second pair splits from them
			float w = smooth((t - OmnitrixLongTransform.TRANSFORM_AT) / 8f);
			rot(m.rightArm, w, 0.15f, 0f, 2.3f + breathe);
			rot(m.leftArm, w, 0.15f, 0f, -2.3f - breathe);
			rot(m.head, w, 0.25f, 0f, 0f);
			m.body.xRot = Mth.lerp(w, m.body.xRot, 0.12f);
		} else if (t < TORSO_AT) {
			// the roar: head thrown back, all four arms wide
			float w = smooth((t - WHIP_AT) / 6f);
			float roar = smooth((t - HEAD_AT - 6) / 4f);
			rot(m.rightArm, w, -0.55f, 0f, 0.95f + breathe);
			rot(m.leftArm, w, -0.55f, 0f, -0.95f - breathe);
			rot(m.head, w, Mth.lerp(roar, -0.15f, -0.6f), 0f, 0f);
			m.body.xRot = Mth.lerp(w, m.body.xRot, -0.08f);
		} else if (t < POSE_AT) {
			// the flex
			float w = smooth((t - TORSO_AT) / 5f);
			rot(m.rightArm, w, -1.15f, 0.25f, 0.55f + breathe);
			rot(m.leftArm, w, -1.15f, -0.25f, -0.55f - breathe);
			rot(m.head, w, -0.25f, 0f, 0f);
			m.body.xRot = Mth.lerp(w, m.body.xRot, 0f);
		} else {
			// the power stance: legs wide, leaning in, the top fists up and ready
			float w = smooth((t - POSE_AT) / 5f);
			rot(m.rightArm, w, -1.45f + breathe, 0.15f, 0.32f);
			rot(m.leftArm, w, -0.95f - breathe, -0.15f, -0.3f);
			rot(m.rightLeg, w, -0.15f, 0f, 0.38f);
			rot(m.leftLeg, w, 0.15f, 0f, -0.38f);
			rot(m.head, w, -0.1f, 0f, 0f);
			m.body.xRot = Mth.lerp(w, m.body.xRot, 0.18f);
			if (m instanceof PlayerModel<?> pm) {
				pm.rightPants.copyFrom(m.rightLeg);
				pm.leftPants.copyFrom(m.leftLeg);
			}
		}
		if (m instanceof PlayerModel<?> pm) {
			pm.rightSleeve.copyFrom(m.rightArm);
			pm.leftSleeve.copyFrom(m.leftArm);
			pm.jacket.copyFrom(m.body);
		}
		m.hat.copyFrom(m.head);
	}

	/**
	 * Four Arms' lower pair while it plays: {x, y, z (pivot px), xRot, yRot, zRot} for the right arm (the left mirrors), and how far
	 * they have split out of the upper pair (0 = still inside it). Null when not playing.
	 */
	public static float[] lowerArms(Player p, HumanoidModel<?> m, float partial) {
		float t = age(p, partial);
		if (t < 0f) {
			return null;
		}
		float split = smooth((t - BACK_AT - 2f) / 18f);
		float breathe = Mth.sin(t * 0.25f + 1f) * 0.04f;
		float xr, zr;
		if (t < WHIP_AT) {
			xr = 0.1f;
			zr = 1.15f;
		} else if (t < TORSO_AT) {
			xr = -0.2f;
			zr = 0.55f;
		} else if (t < POSE_AT) {
			xr = -0.45f;
			zr = 0.42f;
		} else {
			xr = -0.55f;
			zr = 0.5f;
		}
		zr += breathe;
		// still inside the upper arm: its pivot and its swing; split out: under it at the ribs, flared
		float x = Mth.lerp(split, m.rightArm.x, -5.4f), y = Mth.lerp(split, m.rightArm.y, 5.4f), z = Mth.lerp(split, m.rightArm.z, 1.0f);
		return new float[] { x, y, z, Mth.lerp(split, m.rightArm.xRot, xr), Mth.lerp(split, m.rightArm.yRot, 0f), Mth.lerp(split, m.rightArm.zRot, zr),
				split };
	}

	/**
	 * The swell through the model's own parts (from {@code HumanoidModelMixin}, after the scales are reset): the left arm thickens as
	 * the flesh takes it, then the chest, the right arm and the head; after the change the upper arms throb as the second pair forces
	 * its way out.
	 */
	public static void applyBulge(Player p, HumanoidModel<?> m, boolean firstPersonHand) {
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float t = age(p, partial);
		if (t < 0f || firstPersonHand) {
			return;
		}
		if (t < OmnitrixLongTransform.TRANSFORM_AT) {
			float throb = 1f + 0.05f * Mth.sin(t * 1.7f);
			float arm = 1f + 0.38f * smooth((t - HAND_AT - 2f) / 34f) * throb;
			float chest = 1f + 0.16f * smooth((t - 58f) / 24f) * throb;
			float rArm = 1f + 0.26f * smooth((t - 70f) / 16f) * throb;
			float head = 1f + 0.1f * smooth((t - FACE_AT - 2f) / 12f) * throb;
			m.leftArm.xScale = m.leftArm.zScale = arm;
			m.rightArm.xScale = m.rightArm.zScale = rArm;
			m.body.xScale = chest;
			m.body.zScale = 1f + (chest - 1f) * 1.4f;
			m.head.xScale = m.head.yScale = m.head.zScale = head;
			m.hat.copyFrom(m.head);
			m.leftArm.x += (arm - 1f) * 1.5f + (chest - 1f) * 3f;
			m.rightArm.x -= (rArm - 1f) * 1.5f + (chest - 1f) * 3f;
		} else if (t < WHIP_AT) {
			float k = 0.12f * (1f - smooth((t - BACK_AT) / 27f)) * (0.6f + 0.4f * Mth.sin(t * 1.9f));
			m.leftArm.xScale = m.leftArm.zScale = 1f + k;
			m.rightArm.xScale = m.rightArm.zScale = 1f + k;
		}
	}

	// ================================================================ the swelling flesh

	/** Bulges: bone (0 left arm, 1 body, 2 head, 3 right arm), centre x y z (px), half-size (px), the tick it erupts. */
	private static final float[][] BULGES = {
			// the hand and wrist, bubbling out of the watch
			{ 0, 1.2f, 9.4f, -2.1f, 1.5f, 29 }, { 0, 2.9f, 9.6f, 0.2f, 1.4f, 30.5f }, { 0, -0.9f, 8.8f, 0.9f, 1.3f, 32 }, { 0, 1.0f, 10.4f, 0.4f, 1.7f, 33 },
			{ 0, 1.6f, 7.2f, 2.1f, 1.5f, 35 }, { 0, 3.0f, 6.6f, -0.9f, 1.6f, 37 }, { 0, -0.2f, 5.8f, -2.2f, 1.5f, 39 },
			// up the forearm and the upper arm to the shoulder
			{ 0, 1.2f, 4.2f, 2.2f, 1.7f, 42 }, { 0, 3.1f, 3.1f, 0.6f, 1.9f, 45 }, { 0, -1.0f, 3.3f, -0.4f, 1.5f, 47 }, { 0, 1.0f, 1.2f, -2.2f, 1.9f, 50 },
			{ 0, 3.2f, 0.1f, 0.1f, 2.0f, 53 }, { 0, 1.1f, -1.4f, 2.0f, 1.9f, 56 }, { 0, 1.0f, -2.2f, 0.0f, 2.2f, 58 },
			// the chest and the back
			{ 1, 3.4f, 1.2f, -2.1f, 2.0f, 60 }, { 1, 3.5f, 1.6f, 2.1f, 2.0f, 62 }, { 1, 2.8f, 4.4f, -2.2f, 2.2f, 63 }, { 1, 1.0f, 1.4f, -2.3f, 1.8f, 66 },
			{ 1, 0.0f, -0.1f, 0.0f, 1.9f, 69 }, { 1, -1.6f, 3.2f, -2.3f, 2.0f, 70 }, { 1, -2.1f, 0.6f, 2.2f, 1.8f, 72 }, { 1, -3.5f, 1.5f, -2.0f, 2.0f, 74 },
			{ 1, 0.2f, 6.4f, -2.3f, 2.0f, 76 }, { 1, 2.6f, 8.6f, 2.1f, 1.7f, 79 },
			// the right arm
			{ 3, -1.0f, -1.6f, 0.0f, 2.0f, 74 }, { 3, -3.1f, 2.2f, 0.2f, 1.8f, 77 }, { 3, -1.0f, 5.2f, -2.2f, 1.7f, 80 }, { 3, -1.2f, 8.2f, 2.1f, 1.6f, 83 },
			// the neck and the face
			{ 2, 3.6f, -2.0f, -2.0f, 1.8f, 76 }, { 2, 2.4f, -0.6f, -4.0f, 1.5f, 79 }, { 2, 3.9f, -5.0f, 1.0f, 1.7f, 80 }, { 2, -3.5f, -2.4f, -1.6f, 1.8f, 82 },
			{ 2, 0.0f, -7.6f, -2.0f, 2.0f, 84 }, { 2, -2.0f, -1.0f, -4.1f, 1.4f, 86 }, { 2, -3.8f, -5.6f, 1.6f, 1.6f, 87 } };

	/**
	 * The swelling: lumpy peach flesh with red veins and the show's red rim, erupting out of the watch and spreading over the arm, the
	 * chest, the other arm and the face; each lump pops up springily and keeps throbbing. From {@code OmnitrixLayer} (the human body,
	 * before the change). Gone under the golden flash.
	 */
	public static void renderSwell(PoseStack pose, MultiBufferSource buffers, AbstractClientPlayer p, PlayerModel<?> model, float partial) {
		float t = age(p, partial);
		if (t < HAND_AT - 1f || t >= OmnitrixLongTransform.TRANSFORM_AT) {
			return;
		}
		VertexConsumer flesh = buffers.getBuffer(RenderType.entityCutoutNoCull(SWELL));
		int light = LightTexture.FULL_BRIGHT;
		ModelPart[] bones = { model.leftArm, model.body, model.head, model.rightArm };
		for (int i = 0; i < BULGES.length; i++) {
			float[] b = BULGES[i];
			float g = pop(t - b[5], 7f);
			if (g <= 0.02f) {
				continue;
			}
			float r = b[4] * g * (1f + 0.09f * Mth.sin(t * (0.8f + 0.05f * i) + i * 1.7f));
			float cx = b[1], cy = b[2], cz = b[3];
			AlienParts.on(pose, bones[(int) b[0]], () -> {
				lump(pose, flesh, cx, cy, cz, r, 0xFFFFFFFF, light);
			});
		}
		// the red glow round the lumps (additive)
		VertexConsumer glow = buffers.getBuffer(RenderType.eyes(SWELL_GLOW));
		for (int i = 0; i < BULGES.length; i++) {
			float[] b = BULGES[i];
			float g = pop(t - b[5], 7f);
			if (g <= 0.02f) {
				continue;
			}
			float r = b[4] * g * 1.2f * (1f + 0.09f * Mth.sin(t * (0.8f + 0.05f * i) + i * 1.7f));
			float k = 0.55f + 0.25f * Mth.sin(t * 1.3f + i);
			int c = 0xFF000000 | (Math.round(255 * k) << 16) | (Math.round(255 * k) << 8) | Math.round(255 * k);
			float cx = b[1], cy = b[2], cz = b[3];
			AlienParts.on(pose, bones[(int) b[0]], () -> lump(pose, glow, cx, cy, cz, r, c, LightTexture.FULL_BRIGHT));
		}
	}

	/**
	 * After the change: the new arms' red glow as they split out (from {@code FourArmsClient}, on the lower arms' own bones), fading
	 * by the whip pan.
	 */
	public static void renderSplitGlow(PoseStack pose, MultiBufferSource buffers, Player p, ModelPart[] arms, float partial) {
		float t = age(p, partial);
		if (t < OmnitrixLongTransform.TRANSFORM_AT || t >= WHIP_AT + 6) {
			return;
		}
		float k = (1f - smooth((t - BACK_AT - 10f) / 25f)) * (0.7f + 0.3f * Mth.sin(t * 1.6f));
		int c = 0xFF000000 | (Math.round(255 * k) << 16) | (Math.round(255 * k) << 8) | Math.round(255 * k);
		VertexConsumer glow = buffers.getBuffer(RenderType.eyes(SWELL_GLOW));
		for (int i = 0; i < arms.length; i++) {
			// the right arm's box runs -3..1 px, the left's -1..3
			float x0 = i == 0 ? -3.6f : -1.6f, x1 = i == 0 ? 1.6f : 3.6f;
			AlienParts.on(pose, arms[i], () -> AlienParts.box(pose, glow, x0, -2.6f, -2.6f, x1, 9.6f, 2.6f, 0f, 0f, 1f, 1f, c, LightTexture.FULL_BRIGHT));
		}
	}

	// ================================================================ in the world (everyone): the dial's light, the golden glow

	private static void renderWorld(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || ctx.consumers() == null) {
			return;
		}
		float partial = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
		Camera cam = ctx.camera();
		for (Player p : mc.level.players()) {
			float t = age(p, partial);
			if (t < 0f || p.isInvisible() || p.distanceToSqr(cam.getPosition()) > 64 * 64) {
				continue;
			}
			// the holder's own are drawn over the stage instead (renderLast)
			if (p == mc.player && camera(p, partial) != null) {
				continue;
			}
			drawLights(new PoseStack(), ctx.consumers(), cam, p, t, partial);
		}
	}

	/** The dial bursting with light (the slap to the swell) and the golden glow of the change, as additive camera-facing glows. */
	private static void drawLights(PoseStack pose, MultiBufferSource buffers, Camera cam, Player p, float t, float partial) {
		Vec3 cp = cam.getPosition();
		Vector3f left = new Vector3f(cam.getLeftVector()), up = new Vector3f(cam.getUpVector());
		VertexConsumer glow = buffers.getBuffer(RenderType.eyes(OmnitrixTransformFx.GLOW));
		PoseStack.Pose last = pose.last();
		float dial = env(t, 3f, 2f, 24f, 8f);
		Vec3 w = WRIST.get(p.getId());
		if (dial > 0f && w != null) {
			Vec3 at = w.subtract(cp);
			float pulse = 1f + 0.12f * Mth.sin(t * 1.9f);
			glowQuad(glow, last, at, 0.75f * pulse, scaleRgb(0x48E83E, dial * 0.85f), left, up);
			glowQuad(glow, last, at, 0.28f * pulse, scaleRgb(0xE2FFD2, dial), left, up);
			// the light streaks off the dial: thin additive kites turning slowly
			VertexConsumer flat = buffers.getBuffer(OmnitrixFxRenderType.FLAT);
			long seed = seed(p);
			for (int i = 0; i < 9; i++) {
				float ang = (i + hash(seed, i) * 0.6f) / 9f * Mth.TWO_PI + t * 0.03f;
				float len = (0.55f + 0.5f * hash(seed, i + 20)) * (0.6f + 0.4f * dial) * pulse;
				kite(flat, last, at, left, up, ang, 0.06f, len, 0.05f, 0xF0FFD8, 0x48E83E, 0.55f * dial);
			}
		}
		float gold = env(t, GOLD_AT - 2f, 4f, 97f, 7f);
		if (gold > 0f) {
			Vec3 at = p.getPosition(partial).add(0, p.getBbHeight() * 0.55, 0).subtract(cp);
			float s = p.getBbHeight() / 1.8f;
			glowQuad(glow, last, at, 2.2f * s, scaleRgb(0xFFC23A, gold * 0.8f), left, up);
			glowQuad(glow, last, at, 1.0f * s, scaleRgb(0xFFF4C8, gold), left, up);
		}
	}

	// ================================================================ the holder's screen: the stage, the body again, the flashes

	private static void renderLast(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer me = mc.player;
		if (me == null || mc.level == null) {
			return;
		}
		float partial = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
		float t = age(me, partial);
		if (t < 0f) {
			return;
		}
		Camera cam = ctx.camera();
		MultiBufferSource.BufferSource bs = mc.renderBuffers().bufferSource();
		PoseStack pose = new PoseStack();
		Screen sc = new Screen(cam, mc);
		long seed = seed(me);
		// v0.17.2 (user: "recreate the tranformation sequence frame for frame with the background and everything"): every shot has the
		// episode's own background -- the dim room behind the slap, the green bloom round the dial, the olive field framed by boiling
		// black dots through the swelling and the reveal (a warm glow behind the stance), the dark-green ray burst
		boolean staged = camera(me, partial) != null;
		if (staged) {
			if (t < SLAP_END) {
				drawGradient(bs, pose, sc, 0x56682E, 0x2E3C17, 0x0B1005);
			} else if (t < HAND_AT) {
				drawGradient(bs, pose, sc, 0xF2FFE0, 0x7CEB4C, 0x2DB51C);
			} else if (t < BURST_STAGE_AT) {
				drawGradient(bs, pose, sc, OLIVE_IN, OLIVE_MID, OLIVE_OUT);
				if (t >= POSE_AT - 4f) {
					// the stance: a red-orange glow behind his body
					VertexConsumer vc = bs.getBuffer(OmnitrixFxRenderType.STAGE);
					float w = smooth((t - POSE_AT + 4f) / 6f);
					radial(vc, pose.last(), sc, 0xE0743A, 0xB8823E, OLIVE_MID, 0.85f * w, 0f);
				}
				drawDots(bs, pose, sc, seed, t - HAND_AT, 0x050505, 64);
			} else {
				drawGradient(bs, pose, sc, BURST_IN, BURST_MID, BURST_OUT);
				drawRays(bs, pose, sc, seed, t - BURST_STAGE_AT);
				drawDots(bs, pose, sc, seed + 5, t - BURST_STAGE_AT + 6f, BURST_DOT, 52);
			}
			bs.endBatch();
			RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
			// the body again, over the stage, lit flat like a cel
			Vec3 at = me.getPosition(partial).subtract(cam.getPosition());
			mc.getEntityRenderDispatcher().render(me, at.x, at.y, at.z, Mth.lerp(partial, me.yRotO, me.getYRot()), partial, pose, bs, LightTexture.FULL_BRIGHT);
			bs.endBatch();
		}
		if (camera(me, partial) != null) {
			RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
			drawLights(pose, bs, cam, me, t, partial);
			bs.endBatch();
		}
		drawFlashes(bs, pose, sc, seed, t);
		bs.endBatch();
	}

	/** The holder's view plane: a quad 4 blocks in front of the camera covering the whole screen. */
	private static final class Screen {
		final Vector3f c, r, u;
		final float hw, hh;

		Screen(Camera cam, Minecraft mc) {
			float d = 4f;
			Vector3f look = new Vector3f(cam.getLookVector());
			c = look.mul(d);
			r = new Vector3f(cam.getLeftVector()).negate();
			u = new Vector3f(cam.getUpVector());
			double fov = fov(mc.getTimer().getGameTimeDeltaPartialTick(false));
			if (fov < 0) {
				fov = mc.options.fov().get();
			}
			hh = d * (float) Math.tan(Math.toRadians(fov) / 2.0) * 1.35f;
			float aspect = mc.getWindow().getWidth() / (float) Math.max(1, mc.getWindow().getHeight());
			hw = hh * aspect;
		}

		/** A point on the plane: {@code x} / {@code y} in -1..1 of the screen (y up), {@code z} toward the camera (blocks). */
		void put(VertexConsumer vc, PoseStack.Pose p, float x, float y, int rgb, float a) {
			float px = c.x + r.x * x * hw + u.x * y * hh, py = c.y + r.y * x * hw + u.y * y * hh, pz = c.z + r.z * x * hw + u.z * y * hh;
			vc.addVertex(p, px, py, pz).setColor((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, Math.round(Mth.clamp(a, 0f, 1f) * 255));
		}

		/** The radius (in screen-height units) that reaches the corners. */
		float corner() {
			return (float) Math.sqrt(1 + (hw / hh) * (hw / hh)) * 1.05f;
		}
	}

	/** A radial gradient filling the screen: {@code in} at the centre through {@code mid} to {@code out}. */
	private static void drawGradient(MultiBufferSource bs, PoseStack pose, Screen sc, int in, int mid, int out) {
		VertexConsumer vc = bs.getBuffer(OmnitrixFxRenderType.STAGE);
		PoseStack.Pose p = pose.last();
		float k = sc.hh / sc.hw;
		float[] radii = { 0f, 0.35f, 0.75f, 1.1f, sc.corner() * 1.2f };
		int[] cols = { in, lerpRgb(0.5f, in, mid), mid, out, out };
		int segs = 40;
		for (int ring = 0; ring < radii.length - 1; ring++) {
			for (int i = 0; i < segs; i++) {
				float a0 = i * Mth.TWO_PI / segs, a1 = (i + 1) * Mth.TWO_PI / segs;
				float r0 = radii[ring], r1 = radii[ring + 1];
				sc.put(vc, p, Mth.cos(a0) * r0 * k, Mth.sin(a0) * r0, cols[ring], 1f);
				sc.put(vc, p, Mth.cos(a0) * r1 * k, Mth.sin(a0) * r1, cols[ring + 1], 1f);
				sc.put(vc, p, Mth.cos(a1) * r1 * k, Mth.sin(a1) * r1, cols[ring + 1], 1f);
				sc.put(vc, p, Mth.cos(a1) * r0 * k, Mth.sin(a1) * r0, cols[ring], 1f);
			}
		}
	}

	/** The burst's dark rays wheeling out of the centre. */
	private static void drawRays(MultiBufferSource bs, PoseStack pose, Screen sc, long seed, float t) {
		VertexConsumer vc = bs.getBuffer(OmnitrixFxRenderType.STAGE);
		PoseStack.Pose p = pose.last();
		float k = sc.hh / sc.hw;
		int rays = 16;
		float spin = t * 0.006f + hash(seed, 3) * Mth.TWO_PI;
		float grow = smooth(t / 6f);
		float far = sc.corner() * 1.25f;
		for (int i = 0; i < rays; i++) {
			float mid = (i + 0.25f * hash(seed, i + 40)) / rays * Mth.TWO_PI + spin;
			float half = Mth.PI / rays * (0.32f + 0.3f * hash(seed, i + 60));
			float r0 = 0.08f;
			float r1 = Mth.lerp(grow, 0.2f, far);
			sc.put(vc, p, Mth.cos(mid) * r0 * k, Mth.sin(mid) * r0, BURST_RAY, 0f);
			sc.put(vc, p, Mth.cos(mid - half) * r1 * k, Mth.sin(mid - half) * r1, BURST_RAY, 0.85f);
			sc.put(vc, p, Mth.cos(mid + half) * r1 * k, Mth.sin(mid + half) * r1, BURST_RAY, 0.85f);
			sc.put(vc, p, Mth.cos(mid) * r0 * k, Mth.sin(mid) * r0, BURST_RAY, 0f);
		}
	}

	/**
	 * The show's Kirby-dot frame: clumps of solid dots (a big one, a few smaller bubbling off it) crowding the edges of the screen,
	 * popping in, drifting outward and "boiling" -- re-jittered every two ticks like hand-drawn frames.
	 */
	private static void drawDots(MultiBufferSource bs, PoseStack pose, Screen sc, long seed, float t, int rgb, int clumps) {
		VertexConsumer vc = bs.getBuffer(RenderType.textSeeThrough(OmnitrixTransformFx.DOT));
		PoseStack.Pose p = pose.last();
		float k = sc.hh / sc.hw;
		int frame = (int) (t / 2f);
		for (int c = 0; c < clumps; c++) {
			float ang = (c + hash(seed, c * 7 + 1) * 0.8f) / clumps * Mth.TWO_PI;
			// round the screen's edges, corners included (a square ring), a few further in
			// (like the show's frames, the dots crowd the sides more than the top and bottom)
			float rr = 0.62f + 0.42f * hash(seed, c * 7 + 2) + (1f - Math.abs(Mth.cos(ang))) * 0.16f;
			float m = Math.max(Math.abs(Mth.cos(ang)), Math.abs(Mth.sin(ang)));
			float cx = Mth.cos(ang) / m * rr * 0.96f, cy = Mth.sin(ang) / m * rr * 0.96f;
			float delay = hash(seed, c * 7 + 3) * 5f;
			float tc = t - delay;
			if (tc <= 0f) {
				continue;
			}
			float drift = 1f + 0.004f * tc;
			cx *= drift;
			cy *= drift;
			float big = 0.1f + 0.12f * hash(seed, c * 7 + 4);
			int blobs = 3 + (int) (hash(seed, c * 7 + 5) * 3.99f);
			for (int b = 0; b <= blobs; b++) {
				float r = b == 0 ? big : big * (0.3f + 0.4f * hash(seed, c * 31 + b * 3));
				float ox = 0f, oy = 0f;
				if (b > 0) {
					float a2 = hash(seed, c * 31 + b * 3 + 1) * Mth.TWO_PI;
					float dist = big + r * (0.1f + 0.5f * hash(seed, c * 31 + b * 3 + 2));
					ox = Mth.cos(a2) * dist;
					oy = Mth.sin(a2) * dist;
				}
				// the boil: a tiny re-jitter every other tick
				float jx = (hash(seed + frame, c * 13 + b) - 0.5f) * 0.012f, jy = (hash(seed + frame, c * 17 + b) - 0.5f) * 0.012f;
				float sz = r * pop(tc - b * 0.7f, 3f) * (1f + 0.06f * (hash(seed + frame, c * 19 + b) - 0.5f));
				if (sz <= 0.004f) {
					continue;
				}
				dot(vc, p, sc, cx + (ox + jx) * k, cy + oy + jy, sz, rgb);
			}
		}
	}

	private static void dot(VertexConsumer vc, PoseStack.Pose p, Screen sc, float x, float y, float r, int rgb) {
		float rx = r * sc.hh / sc.hw;
		int cr = (rgb >> 16) & 255, cg = (rgb >> 8) & 255, cb = rgb & 255;
		float[][] q = { { x - rx, y + r, 0, 0 }, { x - rx, y - r, 0, 1 }, { x + rx, y - r, 1, 1 }, { x + rx, y + r, 1, 0 } };
		for (float[] v : q) {
			float px = sc.c.x + sc.r.x * v[0] * sc.hw + sc.u.x * v[1] * sc.hh, py = sc.c.y + sc.r.y * v[0] * sc.hw + sc.u.y * v[1] * sc.hh,
					pz = sc.c.z + sc.r.z * v[0] * sc.hw + sc.u.z * v[1] * sc.hh;
			vc.addVertex(p, px, py, pz).setColor(cr, cg, cb, 245).setUv(v[2], v[3]).setLight(LightTexture.FULL_BRIGHT);
		}
	}

	/** The full-screen flashes (green in, gold, white, green out and its fade), the dial's green bloom, and the whip pan's streaks. */
	private static void drawFlashes(MultiBufferSource bs, PoseStack pose, Screen sc, long seed, float t) {
		VertexConsumer vc = bs.getBuffer(OmnitrixFxRenderType.STAGE);
		PoseStack.Pose p = pose.last();
		// the slap's flash: flat green, then it thins into the close-up's bloom
		float g1 = env(t, 3.5f, 1.5f, 11f, 7f);
		if (g1 > 0f) {
			fill(vc, p, sc, lerpRgb(smooth((t - 3.5f) / 3f), 0xD2FFB4, FLASH_GREEN), g1);
		}
		// the close-up's green bloom round the dial
		float bloom = env(t, WATCH_AT, 3f, HAND_AT - 4f, 4f);
		if (bloom > 0f) {
			radial(vc, p, sc, 0xEFFFD8, 0x6CE840, 0x2FB81C, 0.6f * bloom, 0.75f * bloom);
		}
		// the golden flash: a hot white-gold centre going out to amber
		float gold = env(t, GOLD_AT, 4f, 97f, 6f);
		if (gold > 0f) {
			radial(vc, p, sc, GOLD_IN, 0xFFE27A, GOLD_OUT, gold, gold);
		}
		// the whip pan: thick black streaks and white blur racing across
		float whip = env(t, WHIP_AT, 1.5f, HEAD_AT - 2.5f, 2.5f);
		if (whip > 0f) {
			fill(vc, p, sc, 0xF2F4F0, 0.25f * whip);
			for (int i = 0; i < 14; i++) {
				float y = (hash(seed, i + 300) * 2f - 1f) * 0.95f;
				float h = 0.012f + 0.03f * hash(seed, i + 320);
				float len = 0.5f + 0.9f * hash(seed, i + 340);
				float x0 = 1.4f - ((t - WHIP_AT) * (0.45f + 0.3f * hash(seed, i + 360)) + hash(seed, i + 380) * 2f) % 3.6f;
				int col = i % 3 == 0 ? 0xFFFFFF : 0x060606;
				rect(vc, p, sc, x0, y - h, x0 + len, y + h, col, whip * 0.9f, 0f);
			}
		}
		// the white flash into the burst
		float white = env(t, WHITE_AT, 2.5f, 210f, 6f);
		if (white > 0f) {
			fill(vc, p, sc, 0xF3F8EC, 0.92f * white);
		}
		// the green flash out of it, and the green fading off the world
		float g2 = env(t, GREEN2_AT, 3.5f, WORLD_AT, 0.01f);
		if (g2 > 0f) {
			fill(vc, p, sc, FLASH_GREEN, g2);
		}
		if (t >= WORLD_AT) {
			float fade = 1f - smooth((t - WORLD_AT) / (OmnitrixLongTransform.TOTAL - WORLD_AT));
			fill(vc, p, sc, lerpRgb(1f - fade, FLASH_GREEN, 0x9CF060), 0.75f * fade);
		}
	}

	private static void fill(VertexConsumer vc, PoseStack.Pose p, Screen sc, int rgb, float a) {
		rect(vc, p, sc, -1.5f, -1.5f, 1.5f, 1.5f, rgb, a, a);
	}

	/** A rectangle in screen units; {@code aRight} is the alpha at its right end (streaks fade along their length). */
	private static void rect(VertexConsumer vc, PoseStack.Pose p, Screen sc, float x0, float y0, float x1, float y1, int rgb, float aLeft, float aRight) {
		sc.put(vc, p, x0, y0, rgb, aLeft);
		sc.put(vc, p, x1, y0, rgb, aRight);
		sc.put(vc, p, x1, y1, rgb, aRight);
		sc.put(vc, p, x0, y1, rgb, aLeft);
	}

	/** A full-screen radial flash: alpha {@code aIn} at the centre to {@code aOut} at the edges. */
	private static void radial(VertexConsumer vc, PoseStack.Pose p, Screen sc, int in, int mid, int out, float aIn, float aOut) {
		float k = sc.hh / sc.hw;
		float[] radii = { 0f, 0.4f, 0.9f, sc.corner() * 1.2f };
		int[] cols = { in, mid, out, out };
		float[] as = { aIn, (aIn + aOut) * 0.5f, aOut, aOut };
		int segs = 36;
		for (int ring = 0; ring < radii.length - 1; ring++) {
			for (int i = 0; i < segs; i++) {
				float a0 = i * Mth.TWO_PI / segs, a1 = (i + 1) * Mth.TWO_PI / segs;
				float r0 = radii[ring], r1 = radii[ring + 1];
				sc.put(vc, p, Mth.cos(a0) * r0 * k, Mth.sin(a0) * r0, cols[ring], as[ring]);
				sc.put(vc, p, Mth.cos(a0) * r1 * k, Mth.sin(a0) * r1, cols[ring + 1], as[ring + 1]);
				sc.put(vc, p, Mth.cos(a1) * r1 * k, Mth.sin(a1) * r1, cols[ring + 1], as[ring + 1]);
				sc.put(vc, p, Mth.cos(a1) * r0 * k, Mth.sin(a1) * r0, cols[ring], as[ring]);
			}
		}
	}

	// ================================================================ helpers

	/** A rounded lump of radius {@code r} px: three crossed boxes (a little longer along the limb), so it reads as a blob, not a cube. */
	private static void lump(PoseStack pose, VertexConsumer vc, float cx, float cy, float cz, float r, int argb, int light) {
		float s = r * 0.68f, ly = r * 1.15f;
		AlienParts.box(pose, vc, cx - r, cy - s, cz - s, cx + r, cy + s, cz + s, 0f, 0f, 1f, 1f, argb, light);
		AlienParts.box(pose, vc, cx - s, cy - ly, cz - s, cx + s, cy + ly, cz + s, 0f, 0f, 1f, 1f, argb, light);
		AlienParts.box(pose, vc, cx - s, cy - s, cz - r, cx + s, cy + s, cz + r, 0f, 0f, 1f, 1f, argb, light);
	}

	private static void glowQuad(VertexConsumer vc, PoseStack.Pose p, Vec3 at, float r, int argb, Vector3f left, Vector3f up) {
		int rr = (argb >> 16) & 255, gg = (argb >> 8) & 255, bb = argb & 255;
		float lx = left.x * r, ly = left.y * r, lz = left.z * r, ux = up.x * r, uy = up.y * r, uz = up.z * r;
		float x = (float) at.x, y = (float) at.y, z = (float) at.z;
		float[][] q = { { lx + ux, ly + uy, lz + uz, 0, 0 }, { lx - ux, ly - uy, lz - uz, 0, 1 }, { -lx - ux, -ly - uy, -lz - uz, 1, 1 },
				{ -lx + ux, -ly + uy, -lz + uz, 1, 0 } };
		for (float[] v : q) {
			vc.addVertex(p, x + v[0], y + v[1], z + v[2]).setColor(rr, gg, bb, 255).setUv(v[3], v[4])
					.setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(p, 0, 0, 1);
		}
	}

	/** A light streak in the camera plane round {@code at}: from {@code r0} out to {@code len}, {@code w} wide at a third of the way. */
	private static void kite(VertexConsumer vc, PoseStack.Pose p, Vec3 at, Vector3f ax, Vector3f ay, float ang, float r0, float len, float w, int rgbIn,
			int rgbOut, float a) {
		float c = Mth.cos(ang), s = Mth.sin(ang);
		float dx = ax.x * c + ay.x * s, dy = ax.y * c + ay.y * s, dz = ax.z * c + ay.z * s;
		float qx = -ax.x * s + ay.x * c, qy = -ax.y * s + ay.y * c, qz = -ax.z * s + ay.z * c;
		float x = (float) at.x, y = (float) at.y, z = (float) at.z;
		float rm = r0 + (len - r0) * 0.3f;
		put(vc, p, x + dx * r0, y + dy * r0, z + dz * r0, rgbIn, a);
		put(vc, p, x + dx * rm + qx * w, y + dy * rm + qy * w, z + dz * rm + qz * w, rgbIn, a);
		put(vc, p, x + dx * len, y + dy * len, z + dz * len, rgbOut, 0f);
		put(vc, p, x + dx * rm - qx * w, y + dy * rm - qy * w, z + dz * rm - qz * w, rgbIn, a);
	}

	private static void put(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, int rgb, float a) {
		vc.addVertex(p, x, y, z).setColor((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, Math.round(Mth.clamp(a, 0f, 1f) * 255));
	}

	private static int scaleRgb(int rgb, float k) {
		k = Mth.clamp(k, 0f, 1f);
		return 0xFF000000 | (Math.round(((rgb >> 16) & 255) * k) << 16) | (Math.round(((rgb >> 8) & 255) * k) << 8) | Math.round((rgb & 255) * k);
	}

	private static int lerpRgb(float t, int a, int b) {
		t = Mth.clamp(t, 0f, 1f);
		int r = (int) Mth.lerp(t, (a >> 16) & 255, (b >> 16) & 255);
		int g = (int) Mth.lerp(t, (a >> 8) & 255, (b >> 8) & 255);
		int bb = (int) Mth.lerp(t, a & 255, b & 255);
		return (r << 16) | (g << 8) | bb;
	}

	/** Whether {@code p}'s Omnitrix state has the long transformation's mark (for the burst timing). */
	public static Long start(OmnitrixState s) {
		return s.timers.get(OmnitrixLongTransform.TIMER);
	}
}
