package com.projecthero.mod.client.omnitrix;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.joml.Vector3f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 polish (user: "make green vfx flash and kirby dots appear when the player transforms and red vfx and kirby dots
 * appear when player detransforms"): the Ben 10 transformation look.
 * <ul>
 *   <li><b>On the wearer's screen</b> (first person): a flat vivid light-green field ({@code #3CD23C}; red {@code #D82424}
 *       turning back) washes over the screen from a pale lime flash, and clumps of solid black Kirby dots -- a big dot with two
 *       or three smaller ones bubbling off it -- pop in with a little overshoot, drift outward from the centre, then break up and
 *       shrink away as the field fades. In third person the wearer's screen keeps only a light tint (28 % of the field).</li>
 *   <li><b>In the world</b> (everyone in view, and the wearer's own third-person camera): v0.17.1 (user: "dont make it that small
 *       dome ... make it a bigger flash ... similar to like an explosion vfx") the cartoon transformation explosion
 *       ({@link #drawBurst}) -- a pale-green core that pops over the body in two ticks, a flat flash disc that blasts out to
 *       about six blocks across (seven and a half for the timeout) and hollows from the middle (revealing the new body) while
 *       its edges feather away, radial light streaks, a shockwave ring racing over the ground, an additive glow, and clumps of
 *       black Kirby dots riding the flash outward, then shrinking and scattering.</li>
 * </ul>
 * Fully client-side: the synced {@code animId} / {@code animStart} of {@link Omnitrix#ANIM_TRANSFORM}, {@code ANIM_REVERT} and
 * {@code ANIM_TIMEOUT} start it, remembered per player in {@link #LIVE} so a kit move right after cannot cut it short; the
 * dots are seeded from the start so every viewer sees the same burst. {@link #LIVE} is cleared on disconnect and when the
 * level goes.
 */
public final class OmnitrixTransformFx {
	public static final ResourceLocation DOT = ProjectHeroMod.id("textures/gui/omnitrix/fx_dot.png");
	public static final ResourceLocation GLOW = ProjectHeroMod.id("textures/gui/omnitrix/fx_glow.png");

	/** The kinds: green in, red out (Shift+V / revoke), red out stronger (timeout). */
	public static final int TRANSFORM = 0, REVERT = 1, TIMEOUT = 2;

	/** The burst colours: the flat field, the lighter inner flash, the hot core, the additive glow. */
	static final int GREEN = 0x3CD23C, GREEN_LIGHT = 0xA8F590, GREEN_HOT = 0xE2FFD2, GREEN_GLOW = 0x48E83E;
	static final int RED = 0xD82424, RED_LIGHT = 0xFF8C74, RED_HOT = 0xFFE2D8, RED_GLOW = 0xFF3A26;

	private record Fx(int kind, long start) {
	}

	/** entity id -> the effect playing on that player. */
	private static final Map<Integer, Fx> LIVE = new HashMap<>();
	private static net.minecraft.client.multiplayer.ClientLevel lastLevel;

	/** Set only by the screenshot harness: freezes the effects at this many ticks in (-1 = off). */
	public static volatile float freezeAt = -1f;

	private OmnitrixTransformFx() {
	}

	public static void initialize() {
		ClientTickEvents.END_CLIENT_TICK.register(OmnitrixTransformFx::tick);
		WorldRenderEvents.AFTER_ENTITIES.register(OmnitrixTransformFx::renderWorld);
		ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> LIVE.clear());
	}

	/** Clears every player's effect (the client reset). */
	public static void clear() {
		LIVE.clear();
	}

	static float duration(int kind) {
		return kind == TIMEOUT ? 20f : kind == REVERT ? 16f : 18f;
	}

	/** The burst's flash radius at its peak, in blocks (a 1.8-block body). */
	static float radius(int kind) {
		return kind == TIMEOUT ? 3.7f : kind == REVERT ? 3.1f : 3.0f;
	}

	private static int kindOf(int animId) {
		return animId == Omnitrix.ANIM_TRANSFORM ? TRANSFORM : animId == Omnitrix.ANIM_TIMEOUT ? TIMEOUT : animId == Omnitrix.ANIM_REVERT ? REVERT : -1;
	}

	private static void tick(Minecraft mc) {
		// a new level (a dimension change, a respawn into another world) re-numbers the entities: drop everything
		if (mc.level != lastLevel) {
			lastLevel = mc.level;
			LIVE.clear();
		}
		if (mc.level == null) {
			return;
		}
		long now = mc.level.getGameTime();
		for (Player p : mc.level.players()) {
			if (!Omnitrix.hasPower(p)) {
				continue;
			}
			OmnitrixState s = Omnitrix.state(p);
			int kind = kindOf(s.animId);
			if (kind >= 0 && now - s.animStart < duration(kind)) {
				Fx old = LIVE.get(p.getId());
				if (old == null || old.start != s.animStart) {
					LIVE.put(p.getId(), new Fx(kind, s.animStart));
				}
			}
		}
		Iterator<Map.Entry<Integer, Fx>> it = LIVE.entrySet().iterator();
		while (it.hasNext()) {
			Fx fx = it.next().getValue();
			if (now - fx.start > duration(fx.kind) + 4 || now < fx.start - 40) {
				it.remove();
			}
		}
	}

	private static float age(Fx fx, long now, float partial) {
		return freezeAt >= 0f ? freezeAt : now + partial - fx.start;
	}

	// ================================================================ hashing / easing

	private static float hash(long seed, int i) {
		long h = seed * 0x9E3779B97F4A7C15L + i * 0xC2B2AE3D27D4EB4FL;
		h ^= h >>> 31;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 29;
		return (h >>> 40) / (float) (1L << 24);
	}

	private static float smooth(float x) {
		x = Mth.clamp(x, 0f, 1f);
		return x * x * (3f - 2f * x);
	}

	private static float easeOut(float x) {
		x = Mth.clamp(x, 0f, 1f);
		float k = 1f - x;
		return 1f - k * k * k;
	}

	/** The pop-in: 0 -> 1.12 -> 1 over {@code len} ticks. */
	private static float pop(float t, float len) {
		if (t <= 0f) {
			return 0f;
		}
		float x = Mth.clamp(t / len, 0f, 1f);
		float c1 = 2.2f, c3 = c1 + 1f;
		return 1f + c3 * (float) Math.pow(x - 1, 3) + c1 * (float) Math.pow(x - 1, 2);
	}

	// ================================================================ the screen

	/** The wearer's full-screen flash (drawn first by the HUD). */
	public static void drawScreen(GuiGraphics g, LocalPlayer p, OmnitrixState s, long now, float partial) {
		Fx fx = LIVE.get(p.getId());
		if (fx == null) {
			return;
		}
		float d = duration(fx.kind);
		float t = age(fx, now, partial);
		if (t < 0f || t > d) {
			return;
		}
		float u = t / d;
		boolean green = fx.kind == TRANSFORM;
		float peak = fx.kind == TIMEOUT ? 0.9f : fx.kind == REVERT ? 0.72f : 0.82f;
		// in third person the flash is the explosion in the world (drawBurst); the screen only takes a light tint of it
		boolean thirdPerson = !Minecraft.getInstance().options.getCameraType().isFirstPerson();
		if (thirdPerson) {
			peak *= 0.28f;
		}
		// the field: up in a tick and a half, held, then fading out by 80% of the run
		float field = peak * Mth.clamp(t / 1.5f, 0f, 1f) * (1f - smooth((u - 0.2f) / 0.65f));
		// the opening flash: the first ticks run from a pale lime / pale coral into the flat field colour
		float hot = 1f - Mth.clamp((t - 0.5f) / 2.5f, 0f, 1f);
		int base = green ? GREEN : RED;
		int flash = green ? 0xD2FFB4 : 0xFF9A80;
		int rgb = lerpRgb(hot * 0.7f, base, flash);
		int w = g.guiWidth(), h = g.guiHeight();
		if (field > 0.004f) {
			g.fill(0, 0, w, h, ((int) (field * 255) << 24) | rgb);
		}
		if (thirdPerson) {
			return;
		}
		// the Kirby dots
		long seed = fx.start * 31L + p.getId();
		int clusters = fx.kind == TIMEOUT ? 9 : fx.kind == REVERT ? 6 : 8;
		float unit = Math.min(w, h);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		// a jittered 4x3 grid of slots, a few left empty, so the clumps spread over the whole screen like the show's
		int[] order = { 5, 0, 10, 7, 2, 9, 4, 11, 1, 6, 8, 3 };
		for (int c = 0; c < clusters; c++) {
			int slot = order[(int) (hash(seed, 900) * 12 + c) % 12];
			float gx = (slot % 4 + 0.15f + hash(seed, c * 13 + 1) * 0.7f) / 4f;
			float gy = (slot / 4 + 0.15f + hash(seed, c * 13 + 2) * 0.7f) / 3f;
			float cx = gx * w, cy = gy * h;
			float big = unit * (0.075f + hash(seed, c * 13 + 3) * 0.085f) * (fx.kind == TIMEOUT ? 1.1f : 1f);
			float delay = 0.4f + hash(seed, c * 13 + 4) * 3.2f;
			float tc = t - delay;
			if (tc <= 0f) {
				continue;
			}
			// outward from the centre of the screen, faster for the clumps further out
			float dx = cx - w * 0.5f, dy = cy - h * 0.5f;
			float dl = Math.max(1f, Mth.sqrt(dx * dx + dy * dy));
			float drift = unit * 0.011f * tc * (0.6f + dl / unit);
			cx += dx / dl * drift;
			cy += dy / dl * drift;
			// the clump breaks up and shrinks away over the back half
			float shrinkFrom = 0.3f + hash(seed, c * 13 + 5) * 0.12f;
			int blobs = 2 + (int) (hash(seed, c * 13 + 6) * 2.99f);
			for (int b = 0; b <= blobs; b++) {
				float r, ox = 0f, oy = 0f;
				if (b == 0) {
					r = big;
				} else {
					r = big * (0.34f + hash(seed, c * 40 + b * 3) * 0.34f);
					float ang = hash(seed, c * 40 + b * 3 + 1) * Mth.TWO_PI;
					float dist = big + r * (0.05f + hash(seed, c * 40 + b * 3 + 2) * 0.45f);
					// satellites bubble out a little as the clump scatters
					dist += unit * 0.004f * tc;
					ox = Mth.cos(ang) * dist;
					oy = Mth.sin(ang) * dist;
				}
				float sh = 1f - smooth((u - shrinkFrom - b * 0.04f) / 0.38f);
				float sc = pop(tc - b * 0.6f, 3f) * sh;
				if (sc <= 0.01f) {
					continue;
				}
				disc(g, cx + ox, cy + oy, r * sc, 0f, 0f, 0f, 0.94f);
			}
		}
		g.setColor(1f, 1f, 1f, 1f);
		RenderSystem.disableBlend();
	}

	private static void disc(GuiGraphics g, float x, float y, float r, float red, float gr, float bl, float a) {
		var pose = g.pose();
		pose.pushPose();
		pose.translate(x, y, 0f);
		pose.scale(r / 64f, r / 64f, 1f);
		g.setColor(red, gr, bl, a);
		g.blit(DOT, -64, -64, 0f, 0f, 128, 128, 128, 128);
		pose.popPose();
	}

	private static int lerpRgb(float t, int a, int b) {
		t = Mth.clamp(t, 0f, 1f);
		int r = (int) Mth.lerp(t, (a >> 16) & 255, (b >> 16) & 255);
		int gg = (int) Mth.lerp(t, (a >> 8) & 255, (b >> 8) & 255);
		int bb = (int) Mth.lerp(t, a & 255, b & 255);
		return (r << 16) | (gg << 8) | bb;
	}

	// ================================================================ the world

	private static void renderWorld(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (LIVE.isEmpty() || mc.level == null || ctx.consumers() == null || ctx.matrixStack() == null) {
			return;
		}
		float partial = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
		long now = mc.level.getGameTime();
		Camera cam = ctx.camera();
		Vec3 cp = cam.getPosition();
		Vector3f left = new Vector3f(cam.getLeftVector()), up = new Vector3f(cam.getUpVector());
		Vector3f back = new Vector3f(cam.getLookVector()).negate();
		PoseStack pose = ctx.matrixStack();
		MultiBufferSource buffers = ctx.consumers();
		for (Map.Entry<Integer, Fx> e : LIVE.entrySet()) {
			if (!(mc.level.getEntity(e.getKey()) instanceof Player p) || p.isInvisible() || p.distanceToSqr(cp) > 96 * 96) {
				continue;
			}
			// the wearer's own first-person view has the screen flash instead (the burst would sit on the camera)
			if (p == mc.player && cam.getEntity() == p && !cam.isDetached()) {
				continue;
			}
			Fx fx = e.getValue();
			float d = duration(fx.kind);
			float t = age(fx, now, partial);
			if (t < 0f || t > d) {
				continue;
			}
			float h = p.getBbHeight();
			float scale = Mth.clamp(h / 1.8f, 0.8f, 1.8f);
			float R = radius(fx.kind) * Math.max(1f, scale * 0.85f);
			// the burst's centre: mid-body, lifted a little so less of the flash sinks into the ground
			float cy = Math.max(h * 0.5f, R * 0.4f);
			Vec3 at = p.getPosition(partial).add(0, cy, 0).subtract(cp);
			drawBurst(pose, buffers, fx, t, d, scale, R, -cy, at, p.getId(), left, up, back);
		}
	}

	/** The ground the flat layers fade into (local y), and the puffy edge's phase, for {@link #vert} / {@link #annulus}. */
	private static float groundY = -1e9f;
	private static float puffPhase;

	/**
	 * v0.17.1 (user: "make it a bigger flash ... similar to like an explosion vfx but make it look like the transformation
	 * flash"): the cartoon transformation explosion. In draw order:
	 * <ol>
	 *   <li>the shockwave ring racing over the ground to 1.35x the radius (the only layer in true world space);</li>
	 *   <li>an additive glow (a wide soft one swelling with the flash, a hot one on the core's pop);</li>
	 *   <li>the flash disc, its edge puffy like a cartoon blast: out to {@link #radius} in ~7 ticks (ease-out), creeping on,
	 *       hollowing from the middle from tick 2 (revealing the new body) while its edge feathers wider and the whole fades from
	 *       tick 4.5 (gone by about 80 % of the run);</li>
	 *   <li>radial light streaks shooting out in 3.5 ticks, then pulling off the centre and thinning;</li>
	 *   <li>the core: a pale-green blob popping over the body in 2 ticks (engulfing it), gone by tick 6;</li>
	 *   <li>the black Kirby-dot clumps (field-colour rims) riding the flash disc outward, popping in, shrinking and scattering.</li>
	 * </ol>
	 * Layers 2-6 are camera-facing and drawn scaled toward the camera (same on-screen size and place, but at the sphere's front
	 * face), so the ground at the feet cannot slice them off; they also fade into the ground. The flat layers use
	 * {@link OmnitrixFxRenderType#FLAT} (unlit, no depth write), so they paint in order. Around 320 quads at most (the timeout).
	 */
	private static void drawBurst(PoseStack pose, MultiBufferSource buffers, Fx fx, float t, float d, float s, float R, float footY, Vec3 at, int id,
			Vector3f left, Vector3f up, Vector3f back) {
		boolean green = fx.kind == TRANSFORM;
		int base = green ? GREEN : RED, light = green ? GREEN_LIGHT : RED_LIGHT, hotRgb = green ? GREEN_HOT : RED_HOT;
		int glowRgb = green ? GREEN_GLOW : RED_GLOW;
		float u = t / d;
		float camDist = (float) at.length();
		// the camera inside / right at the burst: soften it rather than slap a wall of colour on the lens
		float near = Mth.clamp((camDist - 0.8f) / (R * 0.9f), 0.3f, 1f);
		long seed = fx.start * 131L + id * 7L;
		puffPhase = hash(seed, 555) * Mth.TWO_PI;

		// ---- the flash disc's timeline
		float grow = easeOut(t / 7f);
		float late = Mth.clamp((t - 7f) / Math.max(1f, d - 7f), 0f, 1f);
		float rOut = R * (0.1f + 0.9f * grow + 0.12f * late);
		float open = smooth((t - 2.2f) / 8f);
		float rIn = rOut * 0.93f * open;
		float fadeK = smooth((t - 4.5f) / (d * 0.6f));
		float discA = 0.94f * Mth.clamp(t / 0.8f, 0f, 1f) * (1f - fadeK) * near;

		// ---- 1. the shockwave ring over the ground (true world space)
		VertexConsumer flat = buffers.getBuffer(OmnitrixFxRenderType.FLAT);
		float ringT = (t - 1f) / 11f;
		float ringA = 0.9f * (1f - smooth((t - 3f) / (d - 4f))) * near * Mth.clamp((t - 1f) / 1.5f, 0f, 1f);
		if (ringT > 0f && ringA > 0.01f) {
			pose.pushPose();
			pose.translate(at.x, at.y, at.z);
			PoseStack.Pose gp = pose.last();
			groundY = -1e9f;
			float rr = R * 1.35f * easeOut(ringT);
			float w = 0.22f * rr + 0.25f * s;
			Vector3f gx = new Vector3f(1, 0, 0), gz = new Vector3f(0, 0, 1);
			float y = footY + 0.07f;
			annulus(flat, gp, 0f, y, 0f, gx, gz, Math.max(0f, rr - w), rr - w * 0.3f, 0f, ringA, light, light, 28, 0f, 0f);
			annulus(flat, gp, 0f, y, 0f, gx, gz, rr - w * 0.3f, rr, ringA, 0f, light, base, 28, 0f, 0f);
			pose.popPose();
		}

		// ---- the camera-facing layers, drawn at the sphere's front face (scaled about the camera: same projection)
		float dFront = Math.max(camDist * 0.5f, camDist - R * 1.15f);
		float k = camDist > 0.01f ? Mth.clamp(dFront / camDist, 0.05f, 1f) : 1f;
		pose.pushPose();
		pose.scale(k, k, k);
		pose.translate(at.x, at.y, at.z);
		PoseStack.Pose last = pose.last();
		groundY = footY;

		// ---- 2. the glow (additive: brightness in the colour)
		VertexConsumer glow = buffers.getBuffer(RenderType.eyes(GLOW));
		float glowK = (1f - smooth((t - 1f) / (d * 0.55f))) * 0.7f * near;
		billboard(glow, last, 0, 0, 0, R * (0.5f + 0.85f * grow), scale(glowRgb, glowK), left, up, back, LightTexture.FULL_BRIGHT);
		float hotK = (1f - smooth((t - 0.5f) / 4.5f)) * near;
		billboard(glow, last, 0, 0, 0, s * (0.4f + 1.9f * pop(t, 2.2f)), scale(hotRgb, hotK), left, up, back, LightTexture.FULL_BRIGHT);

		flat = buffers.getBuffer(OmnitrixFxRenderType.FLAT);
		// ---- 3. the flash disc: [hole -> inner feather] [body, light -> mid] [mid -> base] [outer feather -> nothing]
		if (discA > 0.01f) {
			float f = (rOut - rIn) * (0.12f + 0.35f * fadeK);
			float innerA = discA * (1f - Mth.clamp(open * 4f, 0f, 1f));
			int innerRgb = lerpRgb(Mth.clamp(1f - t / 5f, 0f, 1f), light, hotRgb);
			int midRgb = lerpRgb(0.5f, innerRgb, base);
			float a = rIn + f, b = rOut - f, m = (a + b) * 0.5f;
			int segs = 32;
			annulus(flat, last, 0, 0, 0, left, up, rIn, a, innerA, discA, innerRgb, innerRgb, segs, 0f, 0f);
			annulus(flat, last, 0, 0, 0, left, up, a, m, discA, discA, innerRgb, midRgb, segs, 0f, 0.4f);
			annulus(flat, last, 0, 0, 0, left, up, m, b, discA, discA, midRgb, base, segs, 0.4f, 0.8f);
			annulus(flat, last, 0, 0, 0, left, up, b, rOut, discA, 0f, base, base, segs, 0.8f, 1f);
		}

		// ---- 4. the light streaks
		int rays = fx.kind == TIMEOUT ? 12 : fx.kind == REVERT ? 9 : 10;
		float rayA = (1f - smooth((t - 4f) / 5.5f)) * near;
		float rayGrow = easeOut((t - 0.3f) / 3.5f);
		if (rayA > 0.01f && rayGrow > 0f) {
			float spin = hash(seed, 77) * Mth.TWO_PI;
			float pull = smooth((t - 3f) / 6f);
			for (int i = 0; i < rays; i++) {
				float ang = (i + hash(seed, i * 5 + 1) * 0.55f) / rays * Mth.TWO_PI + spin;
				float len = R * (1.0f + 0.55f * hash(seed, i * 5 + 2)) * rayGrow;
				float r0 = 0.35f * s + len * pull, rT = 0.35f * s + len;
				float rM = r0 + (rT - r0) * 0.3f;
				float w = s * (0.26f + 0.14f * hash(seed, i * 5 + 3)) * (1f - 0.55f * pull);
				ray(flat, last, left, up, ang, r0, rM, rT, w, lerpRgb(0.45f, hotRgb, light), base, rayA);
			}
		}

		// ---- 5. the core engulfing the body
		float coreA = (1f - smooth((t - 2.5f) / 3.5f)) * near;
		if (coreA > 0.01f) {
			float coreR = s * (0.25f + 1.4f * pop(t, 2.2f) + 0.4f * Mth.clamp((t - 2f) / 4f, 0f, 1f));
			int coreRgb = lerpRgb(Mth.clamp(t / 4f, 0f, 1f), hotRgb, light);
			annulus(flat, last, 0, 0, 0, left, up, 0f, coreR * 0.78f, coreA, coreA, hotRgb, coreRgb, 20, 0f, 0.5f);
			annulus(flat, last, 0, 0, 0, left, up, coreR * 0.78f, coreR, coreA, 0f, coreRgb, coreRgb, 20, 0.5f, 1f);
		}
		groundY = -1e9f;

		// ---- 6. the Kirby dots riding the flash
		int clumps = fx.kind == TIMEOUT ? 15 : fx.kind == REVERT ? 10 : 12;
		VertexConsumer dots = buffers.getBuffer(RenderType.text(DOT));
		float thrown = rOut * (1f + 0.18f * late);
		for (int c = 0; c < clumps; c++) {
			float ang = (c + hash(seed, c * 11 + 1) * 0.7f) / clumps * Mth.TWO_PI + hash(seed, 999) * Mth.TWO_PI;
			float frac = c == 0 ? 0.12f : 0.3f + 0.62f * hash(seed, c * 11 + 2);
			float delay = 0.8f + hash(seed, c * 11 + 3) * 2.6f;
			float tc = t - delay;
			if (tc <= 0f) {
				continue;
			}
			float cr = frac * thrown;
			float cx = Mth.cos(ang) * cr, cy = Mth.sin(ang) * cr;
			// the clumps sinking into the ground stay above it
			cy = Math.max(cy, footY + 0.25f * s);
			float big = s * (0.17f + hash(seed, c * 11 + 4) * 0.17f) * (fx.kind == TIMEOUT ? 1.12f : 1f);
			float shrinkFrom = 0.42f + hash(seed, c * 11 + 5) * 0.15f;
			int blobs = 2 + (int) (hash(seed, c * 11 + 6) * 1.99f);
			for (int b = 0; b <= blobs; b++) {
				float r, ox = 0f, oy = 0f;
				if (b == 0) {
					r = big;
				} else {
					r = big * (0.34f + hash(seed, c * 40 + b * 3) * 0.32f);
					float a2 = hash(seed, c * 40 + b * 3 + 1) * Mth.TWO_PI;
					// satellites bubble off and scatter as the clump breaks up
					float dist = big + r * (0.05f + hash(seed, c * 40 + b * 3 + 2) * 0.4f) + 0.025f * tc * s * (0.5f + u);
					ox = Mth.cos(a2) * dist;
					oy = Mth.sin(a2) * dist;
				}
				float sh = 1f - smooth((u - shrinkFrom - b * 0.04f) / 0.38f);
				float sc = pop(tc - b * 0.5f, 2.5f) * sh;
				if (sc <= 0.02f) {
					continue;
				}
				float qx = cx + ox, qy = cy + oy;
				float wx = left.x * qx + up.x * qy + back.x * 0.03f, wy = left.y * qx + up.y * qy + back.y * 0.03f,
						wz = left.z * qx + up.z * qy + back.z * 0.03f;
				float rr = r * sc;
				// a rim of the field colour behind the black dot: reads where the disc has opened or faded
				billboard(dots, last, wx, wy, wz, rr * 1.14f, 0xFF000000 | base, left, up, back, LightTexture.FULL_BRIGHT);
				billboard(dots, last, wx + back.x * 0.012f, wy + back.y * 0.012f, wz + back.z * 0.012f, rr, 0xF5030503, left, up, back,
						LightTexture.FULL_BRIGHT);
			}
		}
		pose.popPose();
	}

	/** The cartoon-blast bump on a puffy edge at angle {@code a} (about +-11 %). */
	private static float puff(float a) {
		return 1f + 0.065f * Mth.sin(5f * a + puffPhase) + 0.035f * Mth.sin(9f * a + puffPhase * 2.3f);
	}

	/**
	 * A flat ring band in the plane of {@code ax} / {@code ay} between {@code r0} (colour / alpha 0) and {@code r1}; {@code p0} /
	 * {@code p1} are how much of the puffy edge each radius takes (0 = round).
	 */
	private static void annulus(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, Vector3f ax, Vector3f ay, float r0, float r1,
			float a0, float a1, int rgb0, int rgb1, int segs, float p0, float p1) {
		if (r1 <= r0 || (a0 <= 0.004f && a1 <= 0.004f)) {
			return;
		}
		for (int i = 0; i < segs; i++) {
			float t0 = i * Mth.TWO_PI / segs, t1 = (i + 1) * Mth.TWO_PI / segs;
			float c0 = Mth.cos(t0), s0 = Mth.sin(t0), c1 = Mth.cos(t1), s1 = Mth.sin(t1);
			float q00 = r0 * Mth.lerp(p0, 1f, puff(t0)), q10 = r1 * Mth.lerp(p1, 1f, puff(t0));
			float q01 = r0 * Mth.lerp(p0, 1f, puff(t1)), q11 = r1 * Mth.lerp(p1, 1f, puff(t1));
			vert(vc, p, x + (ax.x * c0 + ay.x * s0) * q00, y + (ax.y * c0 + ay.y * s0) * q00, z + (ax.z * c0 + ay.z * s0) * q00, rgb0, a0);
			vert(vc, p, x + (ax.x * c0 + ay.x * s0) * q10, y + (ax.y * c0 + ay.y * s0) * q10, z + (ax.z * c0 + ay.z * s0) * q10, rgb1, a1);
			vert(vc, p, x + (ax.x * c1 + ay.x * s1) * q11, y + (ax.y * c1 + ay.y * s1) * q11, z + (ax.z * c1 + ay.z * s1) * q11, rgb1, a1);
			vert(vc, p, x + (ax.x * c1 + ay.x * s1) * q01, y + (ax.y * c1 + ay.y * s1) * q01, z + (ax.z * c1 + ay.z * s1) * q01, rgb0, a0);
		}
	}

	/** A light streak: a kite from {@code r0} (hot, a point) widening to {@code w} at {@code rM}, tapering to nothing at {@code rT}. */
	private static void ray(VertexConsumer vc, PoseStack.Pose p, Vector3f ax, Vector3f ay, float ang, float r0, float rM, float rT, float w, int rgbIn,
			int rgbOut, float a) {
		float c = Mth.cos(ang), s = Mth.sin(ang);
		float dx = ax.x * c + ay.x * s, dy = ax.y * c + ay.y * s, dz = ax.z * c + ay.z * s;
		float qx = -ax.x * s + ay.x * c, qy = -ax.y * s + ay.y * c, qz = -ax.z * s + ay.z * c;
		vert(vc, p, dx * r0, dy * r0, dz * r0, rgbIn, a);
		vert(vc, p, dx * rM + qx * w, dy * rM + qy * w, dz * rM + qz * w, rgbIn, a);
		vert(vc, p, dx * rT, dy * rT, dz * rT, rgbOut, 0f);
		vert(vc, p, dx * rM - qx * w, dy * rM - qy * w, dz * rM - qz * w, rgbIn, a);
	}

	/** One {@link OmnitrixFxRenderType#FLAT} vertex, its alpha fading into the ground ({@link #groundY}) below the burst. */
	private static void vert(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, int rgb, float a) {
		a *= smooth((y - groundY + 0.25f) / 1.1f);
		vc.addVertex(p, x, y, z).setColor((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, Math.round(Mth.clamp(a, 0f, 1f) * 255));
	}

	/** {@code rgb} scaled by {@code k} (additive layers fade by darkening), opaque alpha. */
	private static int scale(int rgb, float k) {
		k = Mth.clamp(k, 0f, 1f);
		int r = Math.round(((rgb >> 16) & 255) * k), gg = Math.round(((rgb >> 8) & 255) * k), b = Math.round((rgb & 255) * k);
		return 0xFF000000 | (r << 16) | (gg << 8) | b;
	}

	private static void billboard(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float r, int argb, Vector3f left, Vector3f up,
			Vector3f n, int light) {
		int a = (argb >>> 24) & 255, rr = (argb >> 16) & 255, gg = (argb >> 8) & 255, bb = argb & 255;
		float lx = left.x * r, ly = left.y * r, lz = left.z * r, ux = up.x * r, uy = up.y * r, uz = up.z * r;
		vc.addVertex(p, x + lx + ux, y + ly + uy, z + lz + uz).setColor(rr, gg, bb, a).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
				.setNormal(p, n.x, n.y, n.z);
		vc.addVertex(p, x + lx - ux, y + ly - uy, z + lz - uz).setColor(rr, gg, bb, a).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
				.setNormal(p, n.x, n.y, n.z);
		vc.addVertex(p, x - lx - ux, y - ly - uy, z - lz - uz).setColor(rr, gg, bb, a).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
				.setNormal(p, n.x, n.y, n.z);
		vc.addVertex(p, x - lx + ux, y - ly + uy, z - lz + uz).setColor(rr, gg, bb, a).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
				.setNormal(p, n.x, n.y, n.z);
	}
}
