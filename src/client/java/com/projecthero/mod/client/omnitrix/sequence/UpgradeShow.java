package com.projecthero.mod.client.omnitrix.sequence;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.omnitrix.AlienParts;
import com.projecthero.mod.client.omnitrix.OmnitrixSequenceClient;
import com.projecthero.mod.omnitrix.alien.Aliens;

import net.minecraft.client.Camera;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import static com.projecthero.mod.client.omnitrix.sequence.ShowMath.env;
import static com.projecthero.mod.client.omnitrix.sequence.ShowMath.hash;
import static com.projecthero.mod.client.omnitrix.sequence.ShowMath.rot;
import static com.projecthero.mod.client.omnitrix.sequence.ShowMath.smooth;

/**
 * v0.17.3 (user: "create transformation sequence for all the aliens that are in this video ... please make the posing and everything
 * look exactly like how it looks in the show, try to be as expressive as you can without bending the limbs"): Upgrade's
 * transformation from the show, shot for shot, cut to its own soundtrack. Compilation video second {@code v} is tick
 * {@code (v - 211.10) * 20}. The beats (ticks after the right-click):
 * <pre>
 *   0-6      the slap (front close shot, the grey room)                           v 211.10-211.40
 *   6-27     FULL GREEN FLASH                                                      v 211.4-212.45
 *   26-33    the dial, huge, coming out of the green                              v 212.4-212.75
 *   33-48    right in on the forearm: glowing circuit lines racing over it        v 212.75-213.5   (dark green, blurred lime bars)
 *   48-62    the hand and wrist going black, the circuits branching               v 213.5-214.2
 *   62-74    up the arm to the shoulder, the last of the skin swallowed            v 214.2-214.8
 *   74-84    a glowing trace streaking across the chest                           v 214.8-215.3
 *   84-92    the round eye opening on the face, filling the frame                 v 215.3-215.7
 *   92-132   from below: the black body, circuits closing over it, up to the eye  v 215.7-217.7   (black field, pale lime dots)
 *   132-138  WHITE FLASH -- the body changes under it (133)                         v 217.7-218.0
 *   138-164  Upgrade from low, the white thinning onto the streaming green         v 218.0-219.3
 *   164-168  a green-white flash out                                               v 219.3-219.5
 *   168-180  back in the world (the show cuts straight to the scene)               v 219.5-220.1
 * </pre>
 * The black shell and its circuits, the eye and the pose are on the human body, so everyone nearby sees them; flat-colour boxes, no
 * particles.
 */
public final class UpgradeShow extends ShowSequenceClient {
	static final float SLAP_END = 6f, DIAL_AT = 26f, FOREARM_AT = 33f, HAND_AT = 48f, SHOULDER_AT = 62f, CHEST_AT = 74f, EYE_AT = 84f, BELOW_AT = 92f,
			WHITE_AT = 132f, REVEAL_AT = 138f, OUT_AT = 164f, WORLD_AT = 168f;
	static final int TRANSFORM_AT = 133;

	private static final ResourceLocation WHITE = ProjectHeroMod.id("textures/entity/mutation/white.png");
	/** The Mechamorph black, the circuit lime, its glow. */
	private static final int SHELL = 0xFF0E120C, LIME = 0xFFC4FF4A, GLOW = 0x3E6A10;
	private static final int DARK_IN = 0x3E6216, DARK_MID = 0x1C300A, DARK_OUT = 0x040702, BAR = 0xD2FF6A;
	private static final int FLOW_IN = 0xB6FF8C, FLOW_MID = 0x46D63A, FLOW_OUT = 0x167A12, FLOW_RAY = 0x2A9A22, FLOW_DOT = 0x0F5A0C;

	private static final KirbyDots.Style PALE_DOTS = KirbyDots.Style.show().rgb(0xB6DA88).clear(0.35f),
			EDGE_DOTS = KirbyDots.Style.show().bands(1).rgb(0x9CC07A).clear(0.6f), FLOW_DOTS = KirbyDots.Style.show().bands(2).rgb(FLOW_DOT).clear(0.55f)
					.flow(2.2f).angle(80f).alpha(170);

	private static final float W = CameraTrack.W;
	private static final CameraTrack TRACK = new CameraTrack(CameraTrack.keys(new float[][] {
			// the slap: in close on his face and the raised wrist
			{ 0f, 1.15f, 14f, 1.45f, 1.38f, 0f, 0.15f },
			{ 6f, 1.0f, 12f, 1.45f, 1.36f, 0f, 0.15f } }, ShowShots.dialCloseUp(6.01f, DIAL_AT),
			new float[][] {
					// the dial, huge
					{ 26.01f, 0.34f, 10f, W, W, W, W },
					{ 33f, 0.27f, 0f, W, W, W, W },
					// right in on the forearm, from the left
					{ 33.01f, 0.24f, -70f, W, W, W, W },
					{ 48f, 0.2f, -55f, W, W, W, W },
					// the hand and the wrist, from in front and below
					{ 48.01f, 0.32f, -25f, W, W, W, W },
					{ 62f, 0.28f, -40f, W, W, W, W },
					// up the arm to the shoulder
					{ 62.01f, 0.3f, -80f, 1.3f, 1.3f, -0.28f, 0.04f },
					{ 74f, 0.26f, -90f, 1.32f, 1.32f, -0.3f, 0.02f },
					// a trace streaking across the chest
					{ 74.01f, 0.3f, 14f, 1.24f, 1.26f, 0f, 0f },
					{ 84f, 0.26f, 26f, 1.26f, 1.28f, 0f, 0f },
					// the eye on the face, pulling back off it
					{ 84.01f, 0.55f, 0f, 1.64f, 1.64f, 0f, 0f },
					{ 92f, 0.7f, 0f, 1.64f, 1.64f, 0f, 0f },
					// from below: the black body, circuits closing up over it
					{ 92.01f, 0.72f, 12f, 0.98f, 1.5f, 0f, 0f },
					{ 104f, 0.66f, 20f, 1.0f, 1.54f, 0f, 0f },
					{ 104.01f, 0.62f, -18f, 1.06f, 1.6f, 0f, 0f },
					{ 118f, 0.56f, -10f, 1.08f, 1.6f, 0f, 0f },
					{ 118.01f, 0.46f, 24f, 1.28f, 1.66f, 0f, 0f },
					{ 132f, 0.4f, 30f, 1.3f, 1.66f, 0f, 0f },
					// Upgrade, from low and in front
					{ 132.01f, 1.2f, 0f, 0.35f, 1.3f, 0f, 0f },
					{ 168f, 1.25f, 0f, 0.3f, 1.15f, 0f, 0f } }));

	public UpgradeShow() {
		super(Aliens.UPGRADE.id());
	}

	@Override
	public float worldAt() {
		return WORLD_AT;
	}

	@Override
	public int burstAt() {
		return 164;
	}

	@Override
	public CameraTrack track() {
		return TRACK;
	}

	@Override
	public double fov(float t) {
		double fov = 62;
		if (t >= FOREARM_AT && t < CHEST_AT + 10) {
			// the close-ups race, a little wide
			fov += 6;
		} else if (t >= REVEAL_AT && t < WORLD_AT) {
			fov -= 5 * smooth((t - REVEAL_AT) / 26f);
		}
		return fov;
	}

	// ================================================================ the poses

	@Override
	public void pose(AbstractClientPlayer p, HumanoidModel<?> m, float t) {
		m.head.yRot = 0f;
		if (t < TRANSFORM_AT) {
			float shake = smooth((t - FOREARM_AT) / 30f);
			if (t >= CHEST_AT) {
				// the body seizing as the metal closes over it: arms forced down and out, the chest arched, the head thrown back
				float w = smooth((t - CHEST_AT) / 6f);
				rot(m.leftArm, w, 0.12f + 0.04f * Mth.sin(t * 3.3f), 0f, -0.55f);
				rot(m.rightArm, w, 0.12f + 0.04f * Mth.sin(t * 2.9f), 0f, 0.55f);
				float back = t < BELOW_AT ? 0f : smooth((t - BELOW_AT) / 10f);
				m.head.xRot = Mth.lerp(w, m.head.xRot, -0.1f - 0.3f * back + 0.04f * Mth.sin(t * 2.4f));
				m.body.xRot = Mth.lerp(w, m.body.xRot, -0.06f - 0.08f * back);
				rot(m.rightLeg, w, 0f, 0f, 0.12f);
				rot(m.leftLeg, w, 0f, 0f, -0.12f);
				if (m instanceof PlayerModel<?> pm) {
					pm.rightPants.copyFrom(m.rightLeg);
					pm.leftPants.copyFrom(m.leftLeg);
				}
			}
			if (shake > 0f) {
				m.leftArm.zRot += 0.035f * shake * Mth.cos(t * 3.7f);
				m.rightArm.zRot += 0.035f * shake * Mth.cos(t * 3.1f + 1f);
				m.body.zRot = 0.02f * shake * Mth.sin(t * 2.3f);
			}
		} else {
			// Upgrade's stance: legs wide, the arms hanging out from his sides, a slow liquid sway
			float breathe = Mth.sin(t * 0.3f) * 0.05f;
			rot(m.rightArm, 1f, -0.18f, 0f, 0.62f + breathe);
			rot(m.leftArm, 1f, -0.18f, 0f, -0.62f - breathe);
			rot(m.rightLeg, 1f, 0f, 0f, 0.26f);
			rot(m.leftLeg, 1f, 0f, 0f, -0.26f);
			rot(m.head, 1f, Mth.lerp(smooth((t - REVEAL_AT) / 14f), -0.25f, 0.05f), 0f, 0f);
			m.body.xRot = 0.06f;
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

	// ================================================================ the black shell and its circuits

	/** Box (px, a little over the skin and its outer layer) of each bone: head, body, left arm, right arm, left leg, right leg. */
	private static final float[][] BOXES = { { -4.6f, -8.6f, -4.6f, 4.6f, 0.6f, 4.6f }, { -4.35f, -0.35f, -2.35f, 4.35f, 12.35f, 2.35f },
			{ -1.35f, -2.35f, -2.35f, 3.35f, 12.35f, 2.35f }, { -3.35f, -2.35f, -2.35f, 1.35f, 12.35f, 2.35f }, { -2.35f, -0.35f, -2.35f, 2.35f, 12.35f, 2.35f },
			{ -2.35f, -0.35f, -2.35f, 2.35f, 12.35f, 2.35f } };
	/** When each bone's shell starts closing over it and how long it takes; whether it runs up (from the hand / foot) or down. */
	private static final float[][] SPREAD = { { 79f, 8f, 0 }, { 66f, 24f, 0 }, { 33f, 30f, 1 }, { 76f, 22f, 0 }, { 92f, 18f, 0 }, { 94f, 18f, 0 } };

	@Override
	public void renderBody(PoseStack pose, MultiBufferSource buffers, AbstractClientPlayer p, PlayerModel<?> model, float t) {
		if (t < FOREARM_AT - 1f || t >= TRANSFORM_AT) {
			return;
		}
		int light = LightTexture.FULL_BRIGHT;
		VertexConsumer solid = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
		ModelPart[] bones = { model.head, model.body, model.leftArm, model.rightArm, model.leftLeg, model.rightLeg };
		for (int i = 0; i < bones.length; i++) {
			float g = smooth((t - SPREAD[i][0]) / SPREAD[i][1]);
			if (g <= 0f) {
				continue;
			}
			float[] b = BOXES[i];
			boolean up = SPREAD[i][2] > 0;
			float ya = up ? Mth.lerp(g, b[4], b[1]) : b[1], yb = up ? b[4] : Mth.lerp(g, b[1], b[4]);
			int bone = i;
			AlienParts.on(pose, bones[i], () -> {
				AlienParts.box(pose, solid, b[0], ya, b[2], b[3], yb, b[5], 0f, 0f, 1f, 1f, SHELL, light);
				traces(pose, solid, b, ya, yb, 9100L + bone * 31L, t, SPREAD[bone][0] + 2f, LIME, light, 0.42f);
			});
		}
		// the round eye opening on the face
		float eye = smooth((t - EYE_AT + 1f) / 5f);
		if (eye > 0f) {
			AlienParts.on(pose, model.head, () -> ring(pose, solid, 0f, -4.4f, -4.7f, 2.7f * eye, 0.75f, LIME, light));
		}
		// the circuits' glow
		VertexConsumer glow = buffers.getBuffer(RenderType.eyes(WHITE));
		int c = 0xFF000000 | GLOW;
		for (int i = 0; i < bones.length; i++) {
			float g = smooth((t - SPREAD[i][0]) / SPREAD[i][1]);
			if (g <= 0f) {
				continue;
			}
			float[] b = BOXES[i];
			boolean up = SPREAD[i][2] > 0;
			float ya = up ? Mth.lerp(g, b[4], b[1]) : b[1], yb = up ? b[4] : Mth.lerp(g, b[1], b[4]);
			int bone = i;
			AlienParts.on(pose, bones[i], () -> traces(pose, glow, b, ya, yb, 9100L + bone * 31L, t, SPREAD[bone][0] + 2f, c, LightTexture.FULL_BRIGHT,
					1.1f));
		}
		if (eye > 0f) {
			AlienParts.on(pose, model.head, () -> ring(pose, glow, 0f, -4.4f, -4.75f, 2.7f * eye, 1.6f, 0xFF5A9A20, LightTexture.FULL_BRIGHT));
		}
	}

	/**
	 * Circuit traces over the four sides of a box: per side, three right-angled runs starting at the box's edge, each ending in a little
	 * node; drawn as far as they have grown ({@code from} = when they start), and only where the shell already is ({@code ya..yb}).
	 */
	private static void traces(PoseStack pose, VertexConsumer vc, float[] b, float ya, float yb, long seed, float t, float from, int argb, int light,
			float w) {
		float lift = 0.06f;
		for (int face = 0; face < 4; face++) {
			// the side's plane: u across it, v down it (y)
			float u0 = face < 2 ? b[0] : b[2], u1 = face < 2 ? b[3] : b[5];
			for (int k = 0; k < 3; k++) {
				long s = seed + face * 7L + k * 101L;
				float grow = smooth((t - from - 6f * hash(s, 1)) / 10f);
				if (grow <= 0f) {
					continue;
				}
				float u = Mth.lerp(0.15f + 0.7f * hash(s, 2), u0, u1);
				float v = Mth.lerp(hash(s, 3) < 0.5f ? 0.05f : 0.95f, b[1], b[4]);
				float dir = v < (b[1] + b[4]) / 2 ? 1f : -1f;
				float budget = grow * 4f;
				for (int seg = 0; seg < 4 && budget > 0f; seg++) {
					float f = Math.min(1f, budget);
					budget -= 1f;
					float nu = u, nv = v;
					if (seg % 2 == 0) {
						nv = Mth.clamp(v + dir * (1.5f + 3f * hash(s, 10 + seg)) * f, b[1], b[4]);
					} else {
						float to = Mth.lerp(hash(s, 20 + seg), u0 + 0.6f, u1 - 0.6f);
						nu = Mth.lerp(f, u, to);
					}
					seg(pose, vc, b, face, lift, u, v, nu, nv, w, ya, yb, argb, light);
					u = nu;
					v = nv;
				}
				if (budget >= 0f) {
					seg(pose, vc, b, face, lift, u - 0.55f * w * 2f, v - 0.55f * w * 2f, u + 0.55f * w * 2f, v + 0.55f * w * 2f, w, ya, yb, argb, light);
				}
			}
		}
	}

	/** One straight run from (u, v) to (nu, nv) on side {@code face}, {@code w} wide, clipped to the shell's span. */
	private static void seg(PoseStack pose, VertexConsumer vc, float[] b, int face, float lift, float u, float v, float nu, float nv, float w, float ya,
			float yb, int argb, int light) {
		float ua = Math.min(u, nu) - w / 2, ub = Math.max(u, nu) + w / 2;
		float va = Math.max(Math.min(v, nv) - w / 2, ya), vb = Math.min(Math.max(v, nv) + w / 2, yb);
		if (vb <= va) {
			return;
		}
		switch (face) {
			case 0 -> AlienParts.box(pose, vc, ua, va, b[2] - lift - 0.02f, ub, vb, b[2] - lift, 0f, 0f, 1f, 1f, argb, light);
			case 1 -> AlienParts.box(pose, vc, ua, va, b[5] + lift, ub, vb, b[5] + lift + 0.02f, 0f, 0f, 1f, 1f, argb, light);
			case 2 -> AlienParts.box(pose, vc, b[0] - lift - 0.02f, va, ua, b[0] - lift, vb, ub, 0f, 0f, 1f, 1f, argb, light);
			default -> AlienParts.box(pose, vc, b[3] + lift, va, ua, b[3] + lift + 0.02f, vb, ub, 0f, 0f, 1f, 1f, argb, light);
		}
	}

	/** A flat ring facing forward (-z) at {@code cx cy}, plane {@code z}: radius {@code r}, {@code w} thick. */
	private static void ring(PoseStack pose, VertexConsumer vc, float cx, float cy, float z, float r, float w, int argb, int light) {
		int n = 20;
		for (int i = 0; i < n; i++) {
			float a = i * Mth.TWO_PI / n;
			float x = cx + Mth.cos(a) * r, y = cy + Mth.sin(a) * r, h = w / 2f;
			float e = r * Mth.PI / n + 0.05f;
			AlienParts.box(pose, vc, x - Math.max(h, Math.abs(Mth.sin(a)) * e), y - Math.max(h, Math.abs(Mth.cos(a)) * e), z - 0.02f,
					x + Math.max(h, Math.abs(Mth.sin(a)) * e), y + Math.max(h, Math.abs(Mth.cos(a)) * e), z, 0f, 0f, 1f, 1f, argb, light);
		}
	}

	@Override
	public void renderWorldFx(PoseStack pose, MultiBufferSource buffers, Camera cam, Player p, float t, float partial) {
		ShowShots.dialLight(pose, buffers, cam, p, t, OmnitrixSequenceClient.seed(p), 3f, 26f);
	}

	// ================================================================ the holder's screen

	@Override
	public void drawStage(Stage st, float t) {
		int shot = TRACK.shot(t);
		float inShot = t - TRACK.shotStart(t);
		if (t < SLAP_END) {
			st.gradient(0x7C7E7C, 0x585A5C, 0x2C2E30);
		} else if (t < FOREARM_AT) {
			st.gradient(0xF2FFE0, 0x8CEB5C, 0x3DB52C);
		} else if (t < BELOW_AT) {
			st.gradient(DARK_IN, DARK_MID, DARK_OUT);
			if (t >= HAND_AT && t < SHOULDER_AT) {
				KirbyDots.draw(st, st.shotSeed(shot), inShot, EDGE_DOTS);
			}
			// blurred lime bars racing past (the circuits seen right up close)
			bars(st, t, shot, t >= EYE_AT ? 2 : 5, 1f);
		} else if (t < WHITE_AT) {
			st.gradient(0x101A08, 0x060A04, 0x000000);
			KirbyDots.draw(st, st.shotSeed(shot), inShot, PALE_DOTS);
		} else {
			// the streaming green behind Upgrade
			st.gradient(FLOW_IN, FLOW_MID, FLOW_OUT);
			st.rays(st.seed, t - WHITE_AT, FLOW_RAY, 12);
			KirbyDots.draw(st, st.shotSeed(shot) + 3, t - WHITE_AT, FLOW_DOTS);
		}
	}

	/** Soft glowing lime bars sweeping diagonally across the frame. */
	private static void bars(Stage st, float t, int shot, int count, float alpha) {
		VertexConsumer vc = st.flat();
		PoseStack.Pose p = st.last();
		long seed = st.shotSeed(shot) + 77;
		float ang = (25f + 50f * hash(seed, 0)) * Mth.DEG_TO_RAD;
		float cx = Mth.cos(ang), cy = Mth.sin(ang), k = st.k();
		for (int i = 0; i < count; i++) {
			float off = (hash(seed, i + 10) * 2f - 1f) * 1.1f + (t * (0.03f + 0.04f * hash(seed, i + 20))) % 2.2f - 1.1f;
			float half = 0.03f + 0.05f * hash(seed, i + 30), len = 0.5f + 0.8f * hash(seed, i + 40);
			float along = (hash(seed, i + 50) * 2f - 1f) * 0.6f;
			for (int layer = 0; layer < 2; layer++) {
				float hw = half * (layer == 0 ? 2.6f : 1f), a = (layer == 0 ? 0.22f : 0.75f) * alpha;
				float x0 = (-cy * off + cx * (along - len)), y0 = (cx * off + cy * (along - len));
				float x1 = (-cy * off + cx * (along + len)), y1 = (cx * off + cy * (along + len));
				float nx = -cy * hw, ny = cx * hw;
				st.put(vc, p, (x0 + nx) * k, y0 + ny, BAR, a);
				st.put(vc, p, (x1 + nx) * k, y1 + ny, BAR, a);
				st.put(vc, p, (x1 - nx) * k, y1 - ny, BAR, a);
				st.put(vc, p, (x0 - nx) * k, y0 - ny, BAR, a);
			}
		}
	}

	/** The chest shot's one big glowing trace, sweeping diagonally across the frame. */
	private static void streak(Stage st, float t, float alpha) {
		VertexConsumer vc = st.flat();
		PoseStack.Pose p = st.last();
		float ang = 32f * Mth.DEG_TO_RAD, cx = Mth.cos(ang), cy = Mth.sin(ang), k = st.k();
		float off = Mth.lerp(smooth((t - CHEST_AT) / (EYE_AT - CHEST_AT)), 0.55f, -0.45f);
		for (int layer = 0; layer < 3; layer++) {
			float hw = layer == 0 ? 0.22f : layer == 1 ? 0.1f : 0.045f;
			float a = (layer == 0 ? 0.25f : layer == 1 ? 0.55f : 0.95f) * alpha;
			int rgb = layer == 2 ? 0xF4FFB0 : BAR;
			float x0 = -cy * off - cx * 2.5f, y0 = cx * off - cy * 2.5f, x1 = -cy * off + cx * 2.5f, y1 = cx * off + cy * 2.5f;
			float nx = -cy * hw, ny = cx * hw;
			st.put(vc, p, (x0 + nx) * k, y0 + ny, rgb, a);
			st.put(vc, p, (x1 + nx) * k, y1 + ny, rgb, a);
			st.put(vc, p, (x1 - nx) * k, y1 - ny, rgb, a);
			st.put(vc, p, (x0 - nx) * k, y0 - ny, rgb, a);
		}
	}

	@Override
	public void drawOverlay(Stage st, float t) {
		ShowShots.slapFlash(st, t, 4.5f);
		ShowShots.flash(st, t, ShowShots.FLASH_GREEN, 10f, 1f, DIAL_AT - 1f, 6f, 1f);
		// the glowing trace streaking across the chest, in front of everything
		float streak = env(t, CHEST_AT, 1.5f, EYE_AT - 1.5f, 1.5f);
		if (streak > 0f) {
			streak(st, t, streak);
		}
		// a lime haze over the racing close-ups
		ShowShots.flash(st, t, 0x9AE040, FOREARM_AT, 1f, CHEST_AT, 4f, 0.12f);
		// the white flash the body changes under, thinning off Upgrade
		ShowShots.flash(st, t, 0xFAFFF6, WHITE_AT, 1f, REVEAL_AT, 18f, 1f);
		ShowShots.greenOut(st, t, OUT_AT, WORLD_AT, total());
	}
}
