package com.projecthero.mod.client.omnitrix;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixConfig;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.1: the Omnitrix on the left wrist -- the classic watch built in voxels by {@link OmnitrixModel} (the faceted cuff, the
 * raised bezel and core, the white prongs, the button), drawn in the left arm's space in both third and first person.
 *
 * <p>v0.17.1 (user: "lift up the wrist and still see the regular dial face first and then after like 0.3 seconds the omnitrix
 * transitions to the alien selection screen ... like how it is in the show"; the show's frames are the reference): V raises the
 * wrist ({@link #RAISE_TICKS}) showing the normal hourglass face, holds it {@link #HOLD_TICKS}, then the core pops up (its dark
 * side with the lit lime slots showing) and the face morphs ({@link #MORPH_TICKS}, 16 drawn frames from
 * {@code scratchpad/gen_omnitrix_face_morph.js}): the X dividers swing to near-parallel and meet as a diamond outline, a small
 * green diamond appears in it and grows and brightens, the rim and the four lights bloom; then the selected alien's dial icon
 * (the user's dial art) fades in on the lit diamond. V again reverses it quickly (the diamond shrinks back into the X, the core
 * sinks, the arm comes down). The scroll wheel spins the dial ring a full turn ({@link #SPIN_TICKS}, a critically damped spring
 * so rapid scrolls just add turns and never jitter) while the icon stays upright and swaps in place halfway through each turn
 * with a quick crossfade and a green flash. All of it runs client-side off the synced state, so every viewer sees the same.
 * The face goes red while the watch recharges, when V is refused, flashing in a transformation's last 15 s, and on each beep of
 * the power-down.
 */
public final class OmnitrixWatch {
	public static final ResourceLocation TEX = ProjectHeroMod.id("textures/entity/omnitrix/watch.png");
	public static final ResourceLocation TEX_RED = ProjectHeroMod.id("textures/entity/omnitrix/watch_red.png");
	public static final ResourceLocation MORPH = ProjectHeroMod.id("textures/entity/omnitrix/face_morph.png");
	public static final ResourceLocation MORPH_RED = ProjectHeroMod.id("textures/entity/omnitrix/face_morph_red.png");
	public static final ResourceLocation CIRCUIT = ProjectHeroMod.id("textures/entity/omnitrix/circuit.png");
	public static final ResourceLocation CIRCUIT_RED = ProjectHeroMod.id("textures/entity/omnitrix/circuit_red.png");
	/** How far the whole dial (bezel, lights, core) rises on its circuit riser, px. */
	public static final float LIFT = 1.3f;
	/** First person: the selection screen's roll so the icon stands upright for the eye on the slanted wrist (degrees). */
	static final float FP_ROLL = 0f; // v0.17.1 (user: "dont make it diagonal to the omnitrix"): the icon stays square with the watch

	/** The activation's beats (ticks from V). */
	public static final float RAISE_TICKS = 6f, HOLD_TICKS = 6f, POP_TICKS = 4f, MORPH_TICKS = 7f, ICON_TICKS = 3f;
	static final float POP_AT = RAISE_TICKS + HOLD_TICKS, MORPH_AT = POP_AT + 3f, ICON_AT = MORPH_AT + MORPH_TICKS - 1f;
	/** One dial turn (ticks to settle). */
	public static final float SPIN_TICKS = 7f;
	private static final int MORPH_FRAMES = 16;

	private OmnitrixWatch() {
	}

	/** The dial icon (64x64, linear-filtered) of {@code alienId}; {@code red} the red-diamond variant. */
	public static ResourceLocation icon(String alienId, boolean red) {
		return ProjectHeroMod.id("textures/gui/omnitrix/dial_" + alienId + (red ? "_red" : "") + ".png");
	}

	// ================================================================ the per-player animation memory (client)

	/** What each watch is showing: the up / down values when V lowered it, and the dial spin. */
	private static final class Dial {
		long selectStart = Long.MIN_VALUE;
		boolean selecting;
		/** The up-animation's values at the moment the watch started down. */
		float r0 = 1f, l0 = 1f, m0 = 1f, i0 = 1f;
		// the spin: a critically damped spring from angle toward target (degrees)
		float angle, target, vel;
		float clock = Float.NaN;
		long lastTurn = Long.MIN_VALUE;
		String shown = "", prev = "";
		float swapAt = -99f;
		final ArrayDeque<float[]> swapAngles = new ArrayDeque<>();
		final ArrayDeque<String> swapIds = new ArrayDeque<>();
	}

	private static final Map<Player, Dial> DIALS = new WeakHashMap<>();

	private static float smooth(float x) {
		x = Mth.clamp(x, 0f, 1f);
		return x * x * (3f - 2f * x);
	}

	/** The pop: 0 -> a little over 1 -> 1. */
	private static float popEase(float x) {
		if (x <= 0f) {
			return 0f;
		}
		x = Mth.clamp(x, 0f, 1f);
		float c1 = 1.6f, c3 = c1 + 1f;
		return 1f + c3 * (float) Math.pow(x - 1, 3) + c1 * (float) Math.pow(x - 1, 2);
	}

	/** The up-sequence at {@code t} ticks after V: {raise, lift, morph, icon}. */
	private static float[] up(float t) {
		return new float[] { smooth(t / RAISE_TICKS), popEase((t - POP_AT) / POP_TICKS), smooth((t - MORPH_AT) / MORPH_TICKS),
				Mth.clamp((t - ICON_AT) / ICON_TICKS, 0f, 1f) };
	}

	private static Dial dial(Player p, OmnitrixState s) {
		Dial d = DIALS.computeIfAbsent(p, k -> new Dial());
		if (d.selectStart != s.selectStart || d.selecting != s.selecting) {
			if (d.selecting && !s.selecting && d.selectStart != Long.MIN_VALUE) {
				// lowered: start from wherever the up-sequence had got to
				float[] u = up(s.selectStart - d.selectStart);
				d.r0 = u[0];
				d.l0 = u[1];
				d.m0 = u[2];
				d.i0 = u[3];
			} else if (!s.selecting) {
				d.r0 = d.l0 = d.m0 = d.i0 = 1f;
			}
			d.selectStart = s.selectStart;
			d.selecting = s.selecting;
		}
		return d;
	}

	/** {raise, lift, morph, icon} now. */
	private static float[] phase(Player p, OmnitrixState s, float partial) {
		if (!s.hasPower || s.transformed()) {
			return new float[4];
		}
		Dial d = dial(p, s);
		float t = p.level().getGameTime() + partial - s.selectStart;
		if (s.selecting) {
			return up(t);
		}
		if (s.selectStart == 0) {
			return new float[4];
		}
		// down, quickly: the icon goes, the diamond shrinks back into the X, the core sinks, then the arm comes down
		return new float[] { d.r0 * (1f - smooth((t - 3f) / 5f)), d.l0 * (1f - smooth((t - 2f) / 3f)), d.m0 * (1f - smooth(t / 3.5f)),
				d.i0 * (1f - Mth.clamp(t / 1.5f, 0f, 1f)) };
	}

	/** 0 (down) .. 1 (up) -- the wrist's raise, eased (the pose and the first-person arm follow it). */
	public static float raise(Player p, float partial) {
		return phase(p, Omnitrix.state(p), partial)[0];
	}

	/** Advances the spin to now and returns {angle, swapFade (0..1 since the last icon swap)}; the shown icon in d.shown. */
	private static float[] spin(Player p, OmnitrixState s, float partial) {
		Dial d = dial(p, s);
		float clock = p.level().getGameTime() + partial;
		if (Float.isNaN(d.clock) || clock < d.clock - 40f) {
			d.clock = clock;
			d.shown = s.selected;
			d.lastTurn = s.animId == Omnitrix.ANIM_TURN ? s.animStart : Long.MIN_VALUE;
		}
		if (s.animId == Omnitrix.ANIM_TURN && s.animStart != d.lastTurn) {
			d.lastTurn = s.animStart;
			// v0.17.1 (user: "only make the dial turn quarter of a full rotation and ... the other direction based on which way the
			// player scrolls"): a quarter turn per step, its sense from where the new alien sits on the dial relative to the last
			// one queued (next = forward, previous = back); the icon swaps halfway through it
			String from = d.swapIds.isEmpty() ? d.shown : d.swapIds.peekLast();
			int dir = dialStep(from, s.selected);
			float start = d.target;
			d.target += 90f * dir;
			d.swapAngles.add(new float[] { (start + d.target) * 0.5f, dir });
			d.swapIds.add(s.selected);
		}
		if (d.swapIds.isEmpty() && !d.shown.equals(s.selected) && s.animId != Omnitrix.ANIM_TURN) {
			// picked another way (the dial screen): swap in place
			d.prev = d.shown;
			d.shown = s.selected;
			d.swapAt = clock;
		}
		float dt = Mth.clamp(clock - d.clock, 0f, 3f);
		d.clock = clock;
		// a critically damped spring: smooth from rest, settles in ~SPIN_TICKS, retargets without a jolt
		float w = 5.2f / SPIN_TICKS;
		for (float left = dt; left > 1e-4f;) {
			float h = Math.min(0.2f, left);
			float acc = w * w * (d.target - d.angle) - 2f * w * d.vel;
			d.vel += acc * h;
			d.angle += d.vel * h;
			left -= h;
		}
		if (Math.abs(d.target - d.angle) < 0.5f && Math.abs(d.vel) < 1f) {
			d.angle = d.target;
			d.vel = 0f;
		}
		while (!d.swapAngles.isEmpty() && (d.swapAngles.peek()[1] >= 0f ? d.angle >= d.swapAngles.peek()[0] : d.angle <= d.swapAngles.peek()[0])) {
			d.swapAngles.poll();
			d.prev = d.shown;
			d.shown = d.swapIds.poll();
			d.swapAt = clock;
		}
		// keep the numbers small (either way round)
		if (Math.abs(d.angle) >= 3600f && Math.abs(d.target) >= 3600f && Math.signum(d.angle) == Math.signum(d.target)) {
			float k = Math.signum(d.angle) * 3600f;
			d.angle -= k;
			d.target -= k;
			for (float[] a : d.swapAngles) {
				a[0] -= k;
			}
		}
		return new float[] { d.angle % 360f, Mth.clamp((clock - d.swapAt) / 2f, 0f, 1f) };
	}

	/** +1 if {@code to} is the next alien after {@code from} on the dial (wrapping), -1 if the previous one. */
	private static int dialStep(String from, String to) {
		java.util.List<com.projecthero.mod.omnitrix.alien.Aliens.Alien> pool = com.projecthero.mod.omnitrix.alien.Aliens.playable();
		int n = pool.size(), a = -1, b = -1;
		for (int k = 0; k < n; k++) {
			if (pool.get(k).id().equals(from)) {
				a = k;
			}
			if (pool.get(k).id().equals(to)) {
				b = k;
			}
		}
		if (a < 0 || b < 0 || n < 2) {
			return 1;
		}
		return Math.floorMod(b - a, n) == n - 1 ? -1 : 1;
	}

	private static float since(Player p, OmnitrixState s, float partial, int anim) {
		return s.animId == anim ? p.level().getGameTime() + partial - s.animStart : 9999f;
	}

	/** Whether the face shows red now. */
	public static boolean red(Player p, float partial) {
		OmnitrixState s = Omnitrix.state(p);
		long now = p.level().getGameTime();
		if (since(p, s, partial, Omnitrix.ANIM_DENIED) < 30f || since(p, s, partial, Omnitrix.ANIM_TIMEOUT) < 30f) {
			return true;
		}
		if (s.transformed()) {
			// the power-down: red exactly on each beep (the warning flashing stops)
			if (Omnitrix.poweringDown(s)) {
				return Omnitrix.beepBlink(s, now, partial) > 0f;
			}
			long left = s.endsAt - now;
			return left <= OmnitrixConfig.WARN_TICKS && (now / 5) % 2 == 0;
		}
		return now < s.rechargeUntil;
	}

	/**
	 * Draws the watch in the LEFT arm's space (call after {@code arm.translateAndRotate}, or inside a first-person
	 * {@code renderHand}). {@code slim}: the 3-pixel arm.
	 */
	public static void render(PoseStack pose, MultiBufferSource buffers, int light, Player p, float partial, boolean slim, boolean firstPerson) {
		OmnitrixState s = Omnitrix.state(p);
		float L = slim ? 2f : 3f;
		float[] ph = phase(p, s, partial);
		float morph = ph[2], iconIn = ph[3];
		boolean red = red(p, partial);
		ResourceLocation tex = red ? TEX_RED : TEX;
		// the core: pops up for the selection screen, slammed down by the slap
		float slap = since(p, s, partial, Omnitrix.ANIM_SLAP);
		float lift = LIFT * ph[1];
		if (slap < Omnitrix.SLAP_TICKS + 2) {
			lift = Mth.lerp(Mth.clamp(slap / 2f, 0f, 1f), lift, -0.12f);
		}
		float[] sp = s.transformed() ? new float[] { 0f, 1f } : spin(p, s, partial);
		float twist = sp[0];
		OmnitrixModel.draw(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(tex)), light, L, lift, twist, false, false);
		// the circuit riser the whole dial stands on while it is up
		if (lift > 0.02f) {
			OmnitrixModel.riser(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(red ? CIRCUIT_RED : CIRCUIT)), L, lift);
		}
		// ---- the face, upright (the ring turns round it): a morph frame, the icon fading in on the last one, the swap crossfade
		float top = OmnitrixModel.coreTop(L, lift);
		float fx = (top + 0.012f) / 16f;
		float fr = OmnitrixModel.FACE_R;
		float cy = OmnitrixModel.CY;
		// the icon stands upright for the WEARER, first and third person alike (+z, the edge away from the chest, is up) --
		// v0.17.1 (user: "the alien icon should be facing the player who is using the omnitrix, dont turn it the other way")
		float up = fr / 16f;
		float y0 = (cy - fr) / 16f, y1 = (cy + fr) / 16f;
		// first person: the raised wrist sits at a slant, so as the face morphs it rolls the screen upright for the eye
		pose.pushPose();
		if (firstPerson) {
			pose.translate(0f, cy / 16f, 0f);
			pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(FP_ROLL * smooth(morph)));
			pose.translate(0f, -cy / 16f, 0f);
		}
		PoseStack.Pose last = pose.last();
		int lit = LightTexture.FULL_BRIGHT;
		Dial d = s.transformed() ? null : DIALS.get(p);
		String shown = d == null || d.shown.isEmpty() ? s.selected : d.shown;
		boolean showIcon = !s.transformed() && iconIn > 0.001f && !shown.isEmpty();
		if (!showIcon || iconIn < 0.999f) {
			int f = Math.round(Mth.clamp(morph, 0f, 1f) * (MORPH_FRAMES - 1));
			float u0 = (f % 4) * 0.25f, v0 = (f / 4) * 0.25f;
			VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(red ? MORPH_RED : MORPH));
			faceQuad(vc, last, 0xFFFFFFFF, fx, y0, y1, up, u0, v0, u0 + 0.25f, v0 + 0.25f, lit);
		}
		if (showIcon) {
			float swap = d == null ? 1f : sp[1];
			if (iconIn >= 0.999f && swap >= 0.999f) {
				VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(icon(shown, red)));
				faceQuad(vc, last, 0xFFFFFFFF, fx, y0, y1, up, 0f, 0f, 1f, 1f, lit);
			} else {
				if (swap < 0.999f && d != null && !d.prev.isEmpty() && iconIn >= 0.999f) {
					VertexConsumer old = buffers.getBuffer(RenderType.entityCutoutNoCull(icon(d.prev, red)));
					faceQuad(old, last, 0xFFFFFFFF, fx, y0, y1, up, 0f, 0f, 1f, 1f, lit);
				}
				int a = Math.round(255 * Math.min(iconIn, swap));
				VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(icon(shown, red)));
				faceQuad(vc, last, (a << 24) | 0xFFFFFF, fx + 0.002f / 16f, y0, y1, up, 0f, 0f, 1f, 1f, lit);
			}
		}
		// ---- the glow: the lit face, the bloom round the bezel, the four lights, the swap's flash, a beep's red blink
		float glowK = smooth((morph - 0.6f) / 0.4f) * (s.transformed() ? 0f : 1f);
		float flash = d == null || s.transformed() ? 0f : (1f - sp[1]) * 0.9f;
		float blink = Omnitrix.beepBlink(s, p.level().getGameTime(), partial);
		int g = red ? 0xFF3020 : 0x7CFF2A;
		VertexConsumer glow = buffers.getBuffer(RenderType.eyes(OmnitrixTransformFx.GLOW));
		float gx = fx + 0.02f / 16f;
		if (glowK > 0.01f || flash > 0.01f) {
			glowQuad(glow, last, gx, cy / 16f, 0f, (fr + 0.2f) / 16f, scale(g, 0.28f * glowK + 0.55f * flash));
			glowQuad(glow, last, (top - 0.1f) / 16f, cy / 16f, 0f, 3.3f / 16f, scale(g, 0.42f * glowK + 0.3f * flash));
			float bezelTop = OmnitrixModel.cuffTop(L) + 0.4f + lift;
			for (int k = 0; k < 4; k++) {
				double ang = k * Math.PI / 2;
				glowQuad(glow, last, (bezelTop + 0.12f) / 16f, (cy + (float) Math.cos(ang) * 1.8f) / 16f, (float) Math.sin(ang) * 1.8f / 16f, 0.55f / 16f,
						scale(g, 0.7f * glowK));
			}
		}
		if (blink > 0.01f) {
			glowQuad(glow, last, gx, cy / 16f, 0f, (fr + 0.9f) / 16f, scale(0xFF2A1A, blink));
		}
		pose.popPose();
	}

	/** {@code rgb} scaled by {@code k} (additive layers fade by darkening), opaque alpha. */
	private static int scale(int rgb, float k) {
		k = Mth.clamp(k, 0f, 1f);
		int r = Math.round(((rgb >> 16) & 255) * k), gg = Math.round(((rgb >> 8) & 255) * k), b = Math.round((rgb & 255) * k);
		return 0xFF000000 | (r << 16) | (gg << 8) | b;
	}

	/** A face-plane quad at x = {@code x}: image u along y (y0 -> y1), image v from +up to -up. */
	private static void faceQuad(VertexConsumer vc, PoseStack.Pose p, int argb, float x, float y0, float y1, float up, float u0, float v0, float u1,
			float v1, int light) {
		int a = (argb >>> 24) & 255, r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
		vc.addVertex(p, x, y0, up).setColor(r, g, b, a).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 1, 0, 0);
		vc.addVertex(p, x, y1, up).setColor(r, g, b, a).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 1, 0, 0);
		vc.addVertex(p, x, y1, -up).setColor(r, g, b, a).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 1, 0, 0);
		vc.addVertex(p, x, y0, -up).setColor(r, g, b, a).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 1, 0, 0);
	}

	private static void glowQuad(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float r, int argb) {
		int a = (argb >>> 24) & 255, rr = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
		int l = LightTexture.FULL_BRIGHT;
		vc.addVertex(p, x, y - r, z + r).setColor(rr, g, b, a).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(l).setNormal(p, 1, 0, 0);
		vc.addVertex(p, x, y + r, z + r).setColor(rr, g, b, a).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(l).setNormal(p, 1, 0, 0);
		vc.addVertex(p, x, y + r, z - r).setColor(rr, g, b, a).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(l).setNormal(p, 1, 0, 0);
		vc.addVertex(p, x, y - r, z - r).setColor(rr, g, b, a).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(l).setNormal(p, 1, 0, 0);
	}
}
