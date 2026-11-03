package com.projecthero.mod.client.omnitrix;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixRecal;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * v0.17.2 (user: "an animation starts playing, the dial of the omnitrix pops up and a little slot reveals open up too, the player then
 * slots the recalibration chip into the slots and then the slot closes, the player then looks at the omnitrix watch face as it turns
 * blue, the player then looks at the watch from a few angles turning their wrist a bit to get a better look ... After the 3 minutes
 * the player lifts the omnitrix to look at it, the omnitrix then flashes green and black before slowly reforming into the recalibrated
 * omnitrix ... make the animations look the same as how they are in the video please"): the client half of {@link OmnitrixRecal} --
 * everything runs off the synced start ticks, so the wearer (first person) and everyone watching (third person) see the same beats.
 * <ul>
 *   <li>{@link #rig}: the insert's and the finish's wrist (raise, roll / yaw / pitch, brought toward the eye), the dial's pop, the
 *       slot's door, the face's blue, and the right hand's path with the chip between its fingers (in the watch's space, like
 *       {@link OmnitrixHands});</li>
 *   <li>{@link #renderChip}: the chip at the fingertips (first person; in third person the held item already shows);</li>
 *   <li>{@link #renderReform}: the green glow and light burst round the wrist, the watch flashing as a black / green silhouette, the
 *       prototype shrinking away while the recalibrated watch grows in under it, the white-green peak;</li>
 *   <li>{@link #drawScreen}: the wearer's green screen flashes.</li>
 * </ul>
 */
public final class OmnitrixRecalClient {
	public static final ResourceLocation MORPH_BLUE = ProjectHeroMod.id("textures/entity/omnitrix/face_morph_blue.png");
	public static final ResourceLocation SIL_BLACK = ProjectHeroMod.id("textures/entity/omnitrix/watch_silhouette_black.png");
	public static final ResourceLocation SIL_GREEN = ProjectHeroMod.id("textures/entity/omnitrix/watch_silhouette_green.png");
	/** The reform's blended light (scratchpad/recalchip/gen_halo.js): a soft round glow and a soft streak. */
	public static final ResourceLocation HALO = ProjectHeroMod.id("textures/entity/omnitrix/recal_halo.png");
	public static final ResourceLocation RAY = ProjectHeroMod.id("textures/entity/omnitrix/recal_ray.png");

	private OmnitrixRecalClient() {
	}

	private static float smooth(float x) {
		return OmnitrixHands.smooth(x);
	}

	/** A smooth keyframe track: {t, value} pairs in order; held before the first and after the last. */
	static float track(float t, float[][] k) {
		if (t <= k[0][0]) {
			return k[0][1];
		}
		for (int i = 0; i < k.length - 1; i++) {
			if (t < k[i + 1][0]) {
				float u = (t - k[i][0]) / (k[i + 1][0] - k[i][0]);
				return Mth.lerp(smooth(u), k[i][1], k[i + 1][1]);
			}
		}
		return k[k.length - 1][1];
	}

	private static float[] lerp(float[] a, float[] b, float k) {
		float[] o = new float[9];
		for (int i = 0; i < 9; i++) {
			o[i] = a[i] + (b[i] - a[i]) * k;
		}
		return o;
	}

	// ---------------------------------------------------------------- the insert's keyframes (ticks after Yes)
	// v0.17.2 (user: "the player then looks at the watch from a few angles turning their wrist a bit to get a better look"): the wrist
	// swings its side (the slot) toward the eye for the chip, back face-on for the press, then turns -- rolled one way, the other, tipped
	// up -- as he studies the blue face (the show: sheet_02's last row), and comes down
	private static final float[][] IN_YAW = { { 78, 0 }, { 92, -14 }, { 104, -14 }, { 116, 16 }, { 128, 16 }, { 140, 0 } };
	private static final float[][] IN_ROLL = { { 6, 0 }, { 17, -40 }, { 47, -40 }, { 58, 0 }, { 78, 0 }, { 92, 26 }, { 104, 26 }, { 116, -24 }, { 128, -24 },
			{ 140, 0 } };
	private static final float[][] IN_PITCH = { { 78, 0 }, { 92, 6 }, { 104, 6 }, { 116, -12 }, { 128, -16 }, { 140, 0 } };
	private static final float[][] IN_NEAR = { { 6, 0 }, { 18, 0.05f }, { 48, 0.05f }, { 60, 0.3f }, { 140, 0.3f }, { 152, 0 } };
	// ---------------------------------------------------------------- the finish's (ticks after the time ran out)
	// v0.17.2 (user: "After the 3 minutes the player lifts the omnitrix to look at it"): up close, a small turn, then held still for the reform
	private static final float[][] FIN_NEAR = { { 0, 0 }, { 16, 0.7f }, { 100, 0.7f }, { 116, 0 } };
	private static final float[][] FIN_ROLL = { { 10, 0 }, { 22, 12 }, { 34, -6 }, { 46, 0 } };
	private static final float[][] FIN_YAW = { { 10, 0 }, { 22, -8 }, { 34, 6 }, { 46, 0 } };

	/** The rig while a chip sequence plays on {@code p}, or null (the normal raised-watch rig then). */
	static OmnitrixHands.Rig rig(Player p, OmnitrixState s, float partial, float L) {
		if (!s.hasPower || s.transformed()) {
			return null;
		}
		long now = p.level().getGameTime();
		float t = OmnitrixRecal.insertAge(s, now, partial);
		if (t >= 0f) {
			return insertRig(t, L);
		}
		t = OmnitrixRecal.finishAge(s, now, partial);
		if (t >= 0f) {
			return finishRig(t);
		}
		return null;
	}

	private static OmnitrixHands.Rig insertRig(float t, float L) {
		OmnitrixHands.Rig r = new OmnitrixHands.Rig();
		r.recal = true;
		r.raise = smooth(t / OmnitrixRecal.RAISE_T) * (1f - smooth((t - OmnitrixRecal.LOWER_AT) / (OmnitrixRecal.INSERT_TOTAL - OmnitrixRecal.LOWER_AT - 2f)));
		float pop = OmnitrixHands.popEase((t - OmnitrixRecal.POP_AT) / OmnitrixRecal.POP_T);
		r.lift = t < OmnitrixRecal.DIAL_DOWN_AT ? pop : 1f - smooth((t - OmnitrixRecal.DIAL_DOWN_AT) / OmnitrixRecal.DIAL_DOWN_T);
		float open = smooth((t - OmnitrixRecal.SLOT_OPEN_AT) / OmnitrixRecal.SLOT_T) - smooth((t - OmnitrixRecal.SLOT_CLOSE_AT) / OmnitrixRecal.SLOT_T);
		r.slot = t >= OmnitrixRecal.POP_AT && t < OmnitrixRecal.DIAL_DOWN_AT + OmnitrixRecal.DIAL_DOWN_T ? Mth.clamp(open, 0f, 1f) : -1f;
		r.yaw = track(t, IN_YAW);
		r.roll = track(t, IN_ROLL);
		r.pitch = track(t, IN_PITCH);
		r.near = track(t, IN_NEAR);
		r.blue = insertBlue(t);
		// the right hand: in from the lower right with the chip, slots it, backs off, presses the face, goes
		float lp = OmnitrixWatch.LIFT * r.lift;
		float[] off = OmnitrixHands.pose(OmnitrixHands.OFF, L, lp, 0f);
		float[] hover = chipPose(L, lp, 2.2f), home = chipPose(L, lp, -1.7f), away = chipPose(L, lp, 3.4f);
		away[0] += 0.8f;
		float[] faceHover = facePose(L, lp, 1.8f), facePress = facePose(L, lp, 0.05f);
		float[] h;
		if (t < OmnitrixRecal.HAND_IN_AT) {
			h = off;
		} else if (t < OmnitrixRecal.HAND_AT_SLOT) {
			h = lerp(off, hover, smooth((t - OmnitrixRecal.HAND_IN_AT) / (OmnitrixRecal.HAND_AT_SLOT - OmnitrixRecal.HAND_IN_AT)));
		} else if (t < OmnitrixRecal.HAND_AT_SLOT + 2) {
			h = hover;
		} else if (t < OmnitrixRecal.CHIP_IN) {
			h = lerp(hover, home, smooth((t - OmnitrixRecal.HAND_AT_SLOT - 2) / (OmnitrixRecal.CHIP_IN - OmnitrixRecal.HAND_AT_SLOT - 2)));
		} else if (t < OmnitrixRecal.CHIP_IN + 7) {
			h = lerp(home, away, smooth((t - OmnitrixRecal.CHIP_IN) / 7f));
		} else if (t < 58) {
			h = lerp(away, faceHover, smooth((t - OmnitrixRecal.CHIP_IN - 7) / (58f - OmnitrixRecal.CHIP_IN - 7)));
		} else if (t < OmnitrixRecal.PRESS_AT) {
			h = lerp(faceHover, facePress, smooth((t - 58f) / (OmnitrixRecal.PRESS_AT - 58f)));
		} else if (t < OmnitrixRecal.PRESS_AT + 4) {
			h = facePress;
		} else {
			h = lerp(facePress, off, smooth((t - OmnitrixRecal.PRESS_AT - 4) / 12f));
		}
		r.hand = h;
		r.chip = t >= OmnitrixRecal.HAND_IN_AT && t < OmnitrixRecal.CHIP_IN ? 1f : 0f;
		r.mainHide = smooth(t / 4f) * (1f - smooth((t - OmnitrixRecal.PRESS_AT - 14) / 8f));
		// third person: the hand's weight at the watch (the slot, then the face)
		r.press3p = smooth((t - OmnitrixRecal.HAND_IN_AT) / 14f) * (1f - smooth((t - OmnitrixRecal.PRESS_AT - 6) / 10f));
		return r;
	}

	/** The face's blue during the insert: green until the press, a few dark / blue flickers (the show), then blue. */
	private static float insertBlue(float t) {
		float k = t - OmnitrixRecal.BLUE_AT;
		if (k < 0f) {
			return 0f;
		}
		if (k >= OmnitrixRecal.BLUE_FLICKER_T) {
			return 1f;
		}
		int step = (int) (k / 2.5f);
		return step % 2 == 0 ? 1f : 0.28f;
	}

	private static OmnitrixHands.Rig finishRig(float t) {
		OmnitrixHands.Rig r = new OmnitrixHands.Rig();
		r.recal = true;
		r.raise = smooth(t / OmnitrixRecal.F_RAISE_T) * (1f - smooth((t - OmnitrixRecal.F_LOWER_AT) / (OmnitrixRecal.FINISH_TOTAL - OmnitrixRecal.F_LOWER_AT - 2f)));
		r.near = track(t, FIN_NEAR);
		r.roll = track(t, FIN_ROLL);
		r.yaw = track(t, FIN_YAW);
		r.blue = t < OmnitrixRecal.F_GREEN_AT ? 1f : 0f;
		return r;
	}

	/**
	 * The fingertips holding the chip's back edge {@code back} px out from the slot (in the riser's button side, -z), the hand coming up
	 * from below it like the button press; the chip itself always points straight into the slot ({@link #renderChip}).
	 */
	private static float[] chipPose(float L, float lift, float back) {
		float xo = OmnitrixModel.cuffTop(L);
		float xm = xo + lift * 0.5f;
		// the chip's middle, its front edge {@code back} px off the slot; the fingertips pinch its lower-right corner so the hand stays
		// clear of the slot (it comes in from the lower right) and the chip shows
		float cz = -OmnitrixModel.RISER_R - back - CHIP_LEN * 0.5f;
		return new float[] { xm, OmnitrixModel.CY + PINCH_Y, cz - PINCH_Z, -0.1f, -0.85f, 0.5f, -1f, 0f, 0f };
	}

	/** The fingertip {@code up} px over the face's lower-right rim, the finger coming in from the lower right onto it. */
	private static float[] facePose(float L, float lift, float up) {
		float top = OmnitrixModel.coreTop(L, lift);
		return new float[] { top + up, OmnitrixModel.CY + 0.9f, -0.8f, -0.35f, -0.65f, 0.65f, -1f, 0f, 0f };
	}

	/** The chip's size (px: across, and along the way it goes in) and where the fingertips pinch it (from its middle). */
	private static final float CHIP_W = 1.4f, CHIP_LEN = 2.4f, PINCH_Y = CHIP_W * 0.5f + 0.55f, PINCH_Z = CHIP_LEN * 0.5f;

	/** 0 = the green face, otherwise the blue face at this brightness (the insert's flickers, recalibration mode, the finish's start). */
	public static float blue(Player p, OmnitrixState s, float partial) {
		if (!OmnitrixRecal.busy(s)) {
			return 0f;
		}
		long now = p.level().getGameTime();
		float t = OmnitrixRecal.insertAge(s, now, partial);
		if (t >= 0f) {
			return insertBlue(t);
		}
		t = OmnitrixRecal.finishAge(s, now, partial);
		if (t >= 0f) {
			return t < OmnitrixRecal.F_GREEN_AT ? 1f : 0f;
		}
		return OmnitrixRecal.chipIn(s) ? 1f : 0f;
	}

	/** Whether the wrist shows the recalibrated watch already (the end of the reform, before the server's flag lands). */
	public static boolean reformed(Player p, OmnitrixState s, float partial) {
		float t = OmnitrixRecal.finishAge(s, p.level().getGameTime(), partial);
		return t >= OmnitrixRecal.F_FADE_AT;
	}

	// ---------------------------------------------------------------- first person

	/** Applies the sequence's wrist turn to the first-person left arm's frame (before the arm-space flip), about the watch. */
	static void wrist(PoseStack rel, OmnitrixHands.Rig rig, float L) {
		float cx = OmnitrixModel.CY / 16f, cz = OmnitrixModel.cuffTop(L) / 16f;
		rel.translate(cx, 0f, cz);
		rel.mulPose(com.mojang.math.Axis.YP.rotationDegrees(rig.yaw));
		rel.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(rig.pitch));
		rel.mulPose(com.mojang.math.Axis.XP.rotationDegrees(rig.roll));
		rel.translate(-cx, 0f, -cz);
	}

	private static ItemStack chipStack;

	/** The chip between the right fingertips ({@code h}: the hand's tip / fingers / palm in the watch's space {@code watch}). */
	static void renderChip(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, Matrix4f watch, float[] h) {
		if (chipStack == null) {
			chipStack = new ItemStack(com.projecthero.mod.omnitrix.item.OmnitrixItems.RECALIBRATION_CHIP);
		}
		// the chip points straight into the slot (+z), flat like a card (its face toward the watch's face), held by its back edge
		Vector3f in = new Vector3f(0f, 0f, 1f);
		Vector3f side = new Vector3f(0f, 1f, 0f);
		Vector3f up = new Vector3f(in).negate();
		Vector3f z = new Vector3f(side).cross(up).normalize();
		Vector3f c = new Vector3f(h[0], h[1] - PINCH_Y, h[2] + PINCH_Z);
		pose.pushPose();
		pose.mulPose(watch);
		pose.translate(c.x / 16f, c.y / 16f, c.z / 16f);
		pose.mulPose(new Matrix4f(side.x, side.y, side.z, 0f, up.x, up.y, up.z, 0f, z.x, z.y, z.z, 0f, 0f, 0f, 0f, 1f));
		pose.scale(CHIP_W / 16f, CHIP_LEN / 16f, 2.5f / 16f);
		Minecraft.getInstance().getItemRenderer().renderStatic(chipStack, ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, pose, buffers,
				p.level(), 0);
		pose.popPose();
	}

	// ---------------------------------------------------------------- the reform

	/** The reform's flashing: true = the green-lit silhouette, false = the black one -- faster and faster (8 ticks a flash down to 2). */
	static boolean flashGreen(float t) {
		float u = Mth.clamp((t - OmnitrixRecal.F_FLASH_AT) / (OmnitrixRecal.F_FADE_AT - OmnitrixRecal.F_FLASH_AT), 0f, 1f);
		float span = OmnitrixRecal.F_FADE_AT - OmnitrixRecal.F_FLASH_AT;
		// the number of flashes so far: the rate climbs from 1/8 to 1/2 a tick
		float flashes = span * (u / 8f + (0.5f - 0.125f) * u * u * 0.5f);
		return (flashes % 1f) < 0.5f;
	}

	/** The glow round the wrist now (0..1+, the peak over 1). */
	static float glow(float t) {
		if (t < OmnitrixRecal.F_GREEN_AT) {
			return 0f;
		}
		float g = 0.55f * smooth((t - OmnitrixRecal.F_GREEN_AT) / (OmnitrixRecal.F_FLASH_AT - OmnitrixRecal.F_GREEN_AT));
		if (t >= OmnitrixRecal.F_FLASH_AT) {
			g = 0.75f + 0.25f * smooth((t - OmnitrixRecal.F_FLASH_AT) / 20f) + (flashGreen(t) ? 0.15f : 0f);
		}
		float peak = t - OmnitrixRecal.F_PEAK_AT;
		if (peak > -4f && peak < 10f) {
			g += 0.9f * (peak < 0 ? smooth((peak + 4f) / 4f) : 1f - smooth(peak / 10f));
		}
		if (t >= OmnitrixRecal.F_FADE_AT) {
			g *= 1f - smooth((t - OmnitrixRecal.F_FADE_AT) / 14f);
		}
		return Math.max(0f, g);
	}

	/**
	 * v0.17.2 (user: "the omnitrix then flashes green and black before slowly reforming into the recalibrated omnitrix"; the show:
	 * reform_76_92.png): the reform on the wrist (in the left arm's space). Draws the glow and light burst over the face whenever the
	 * finish is past the green beat, the green wash over the watch before the flashing, and -- while it flashes -- the whole watch
	 * itself as silhouettes: the prototype shrinking into the arm while the recalibrated watch grows out of it. Returns true when it drew
	 * the watch (the caller then draws nothing more).
	 */
	static boolean renderReform(PoseStack pose, MultiBufferSource buffers, int light, Player p, OmnitrixState s, float partial, float L) {
		float t = OmnitrixRecal.finishAge(s, p.level().getGameTime(), partial);
		if (t < OmnitrixRecal.F_GREEN_AT) {
			return false;
		}
		float g = glow(t);
		float cy = OmnitrixModel.CY;
		boolean flashing = t >= OmnitrixRecal.F_FLASH_AT && t < OmnitrixRecal.F_FADE_AT;
		if (flashing) {
			boolean green = flashGreen(t);
			float k = smooth((t - OmnitrixRecal.F_MORPH_AT) / OmnitrixRecal.F_MORPH_T);
			ResourceLocation tex = green ? SIL_GREEN : SIL_BLACK;
			int l = green ? LightTexture.FULL_BRIGHT : light;
			if (k < 0.999f) {
				scaled(pose, L, 1f - 0.5f * k, () -> OmnitrixModel.draw(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(tex)), l, L, 0f, 0f, false, false,
						0f, false));
			}
			if (k > 0.001f) {
				scaled(pose, L, 0.55f + 0.45f * k, () -> OmnitrixModel.draw(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(tex)), l, L, 0f, 0f, false,
						false, 0f, true));
			}
		} else {
			// before the flashing the prototype goes translucent green (the show's second beat); after it the new watch keeps a wash
			float a = t < OmnitrixRecal.F_FLASH_AT ? 0.8f * smooth((t - OmnitrixRecal.F_GREEN_AT) / (OmnitrixRecal.F_FLASH_AT - OmnitrixRecal.F_GREEN_AT))
					: 0.85f * (1f - smooth((t - OmnitrixRecal.F_FADE_AT) / 12f));
			if (a > 0.01f) {
				boolean recal = t >= OmnitrixRecal.F_FADE_AT;
				OmnitrixModel.tint(((int) (a * 255) << 24) | 0xFFFFFF);
				scaled(pose, L, 1.025f, () -> OmnitrixModel.draw(pose, buffers.getBuffer(RenderType.entityTranslucent(SIL_GREEN)), LightTexture.FULL_BRIGHT, L,
						0f, 0f, false, false, 0f, recal));
				OmnitrixModel.untint();
			}
		}
		burst(pose, buffers, t, g, L, cy);
		return flashing;
	}

	private static void scaled(PoseStack pose, float L, float k, Runnable draw) {
		float cx = (L - 1f) * 0.5f / 16f, cy = OmnitrixModel.CY / 16f;
		pose.pushPose();
		pose.translate(cx, cy, 0f);
		pose.scale(k, k, k);
		pose.translate(-cx, -cy, 0f);
		draw.run();
		pose.popPose();
	}

	/** The glow over the face and the rays of the light burst, in the face's plane. */
	private static void burst(PoseStack pose, MultiBufferSource buffers, float t, float g, float L, float cy) {
		if (g < 0.01f) {
			return;
		}
		PoseStack.Pose last = pose.last();
		// the halo and rays stand BEHIND the watch (at the band), so the silhouette stays sharp in front of the light, as in the show
		float x = (OmnitrixModel.cuffTop(L) - 0.05f) / 16f;
		int green = 0x48E83E, hot = 0xE2FFD2;
		float peak = Mth.clamp(1f - Math.abs(t - OmnitrixRecal.F_PEAK_AT) / 8f, 0f, 1f);
		float gc = Math.min(1.3f, g);
		// a blended green halo first (so it reads green even in daylight, where an additive glow washes out), then the light on top
		VertexConsumer halo = buffers.getBuffer(RenderType.entityTranslucentEmissive(HALO));
		int ha = (int) (255 * Math.min(0.95f, 0.75f * g));
		glowQuad(halo, last, x, cy / 16f, 0f, (4.5f + 3.5f * gc) / 16f, (ha << 24) | 0x3CE03A);
		int rays = 12;
		float len = (3.5f + 4.5f * gc) / 16f, w = 0.3f / 16f;
		VertexConsumer streak = buffers.getBuffer(RenderType.entityTranslucentEmissive(RAY));
		for (int i = 0; i < rays; i++) {
			double a = i * Math.PI * 2 / rays + t * 0.02 + (i % 2) * 0.13;
			float rl = len * (i % 3 == 0 ? 1.15f : i % 3 == 1 ? 0.8f : 0.95f);
			ray(streak, last, x + 0.0005f, cy / 16f, 0f, (float) Math.cos(a), (float) Math.sin(a), rl, w * 2.2f, (ha << 24) | 0x7CFF5A, 0f);
		}
		VertexConsumer vc = buffers.getBuffer(RenderType.eyes(OmnitrixTransformFx.GLOW));
		glowQuad(vc, last, x + 0.001f, cy / 16f, 0f, (2.6f + 2f * gc) / 16f, scale(green, Math.min(1f, 0.6f * g)));
		if (peak > 0f) {
			// the white-green peak: in front of everything for a moment
			float xf = (OmnitrixModel.coreTop(L, 0f) + 0.4f) / 16f;
			glowQuad(vc, last, xf, cy / 16f, 0f, (2.5f + 4.5f * peak) / 16f, scale(hot, 0.85f * peak));
		}
		// the rays: long soft streaks out from the face, slowly turning, longer as it builds
		for (int i = 0; i < rays; i++) {
			double a = i * Math.PI * 2 / rays + t * 0.02 + (i % 2) * 0.13;
			float rl = len * (i % 3 == 0 ? 1f : i % 3 == 1 ? 0.7f : 0.85f);
			ray(vc, last, x + 0.003f, cy / 16f, 0f, (float) Math.cos(a), (float) Math.sin(a), rl, w, scale(green, Math.min(1f, 0.5f * g)), 0.5f);
		}
	}

	private static int scale(int rgb, float k) {
		k = Mth.clamp(k, 0f, 1f);
		int r = Math.round(((rgb >> 16) & 255) * k), gg = Math.round(((rgb >> 8) & 255) * k), b = Math.round((rgb & 255) * k);
		return 0xFF000000 | (r << 16) | (gg << 8) | b;
	}

	private static void glowQuad(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float r, int argb) {
		int a = (argb >>> 24) & 255, rr = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
		int l = LightTexture.FULL_BRIGHT;
		vc.addVertex(p, x, y - r, z + r).setColor(rr, g, b, a).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(l).setNormal(p, 1, 0, 0);
		vc.addVertex(p, x, y + r, z + r).setColor(rr, g, b, a).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(l).setNormal(p, 1, 0, 0);
		vc.addVertex(p, x, y + r, z - r).setColor(rr, g, b, a).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(l).setNormal(p, 1, 0, 0);
		vc.addVertex(p, x, y - r, z - r).setColor(rr, g, b, a).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(l).setNormal(p, 1, 0, 0);
	}

	/** A soft streak from (y, z) out along (dy, dz), {@code len} long and {@code w} wide, in the plane x. */
	private static void ray(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float dy, float dz, float len, float w, int argb, float u0) {
		int a = (argb >>> 24) & 255, rr = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
		int l = LightTexture.FULL_BRIGHT;
		float py = -dz * w, pz = dy * w;
		float y1 = y + dy * len, z1 = z + dz * len;
		// u runs along the streak from u0 (its root) to 1, v across it (soft at both sides)
		vc.addVertex(p, x, y + py, z + pz).setColor(rr, g, b, a).setUv(u0, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(l).setNormal(p, 1, 0, 0);
		vc.addVertex(p, x, y1 + py, z1 + pz).setColor(rr, g, b, a).setUv(1f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(l).setNormal(p, 1, 0, 0);
		vc.addVertex(p, x, y1 - py, z1 - pz).setColor(rr, g, b, a).setUv(1f, 1f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(l).setNormal(p, 1, 0, 0);
		vc.addVertex(p, x, y - py, z - pz).setColor(rr, g, b, a).setUv(u0, 1f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(l).setNormal(p, 1, 0, 0);
	}

	// ---------------------------------------------------------------- the screen

	/** The wearer's screen during the reform: a green tint on the green flashes, and the white-green flash at the peak. */
	public static void drawScreen(GuiGraphics g, Player p, OmnitrixState s, float partial) {
		float t = OmnitrixRecal.finishAge(s, p.level().getGameTime(), partial);
		if (t < OmnitrixRecal.F_FLASH_AT) {
			return;
		}
		float a = 0f;
		int rgb = 0x3CD23C;
		if (t < OmnitrixRecal.F_FADE_AT) {
			// the show: the whole frame goes green round the wrist while it flashes
			a = flashGreen(t) ? 0.26f : 0.1f;
		}
		float peak = t - OmnitrixRecal.F_PEAK_AT;
		if (peak > -3f && peak < 12f) {
			float k = peak < 0 ? smooth((peak + 3f) / 3f) : 1f - smooth(peak / 12f);
			if (0.75f * k > a) {
				a = 0.75f * k;
				rgb = 0xD8FFC8;
			}
		}
		if (a > 0.004f) {
			g.fill(0, 0, g.guiWidth(), g.guiHeight(), ((int) (a * 255) << 24) | rgb);
		}
	}
}
