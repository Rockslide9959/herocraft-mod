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
 *   <li><b>On the wearer's screen</b> (first and third person): a flat vivid green field ({@code #00A000}; red {@code #C00000}
 *       turning back) washes over the screen from a lime flash, and clumps of solid black Kirby dots -- a big dot with two or
 *       three smaller ones bubbling off it -- pop in with a little overshoot, drift outward from the centre, then break up and
 *       shrink away as the field fades (16 ticks; 14 for Shift+V's early revert, 20 and denser for the timeout).</li>
 *   <li><b>In the world</b> (everyone in view): a bright additive flash bursts on the body, and camera-facing Kirby dots --
 *       black discs on slightly larger green ones -- burst outward from it, drift, and shrink away.</li>
 *   <li>v0.17.1 (user: "make the green flash show up in third person aswell"): the <b>third-person bloom</b> ({@link #drawBloom}),
 *       the screen effect's world twin -- a flat vivid green field the shape of the body with black Kirby-dot clumps on it -- for
 *       the wearer's own third-person camera and anyone watching. In third person the wearer's screen keeps only a light tint
 *       (28 % of the field, no screen dots); first person is unchanged.</li>
 * </ul>
 * Fully client-side: the synced {@code animId} / {@code animStart} of {@link Omnitrix#ANIM_TRANSFORM}, {@code ANIM_REVERT} and
 * {@code ANIM_TIMEOUT} start it, remembered per player in {@link #LIVE} so a kit move right after cannot cut it short; the
 * dots are seeded from the start so every viewer sees the same burst. {@link #LIVE} is cleared on disconnect and when the
 * level goes.
 */
public final class OmnitrixTransformFx {
	public static final ResourceLocation DOT = ProjectHeroMod.id("textures/gui/omnitrix/fx_dot.png");
	public static final ResourceLocation GLOW = ProjectHeroMod.id("textures/gui/omnitrix/fx_glow.png");
	public static final ResourceLocation BLOOM = ProjectHeroMod.id("textures/gui/omnitrix/fx_bloom.png");

	/** The kinds: green in, red out (Shift+V / revoke), red out stronger (timeout). */
	public static final int TRANSFORM = 0, REVERT = 1, TIMEOUT = 2;

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
		return kind == TIMEOUT ? 20f : kind == REVERT ? 14f : 16f;
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

	// ================================================================ hashing

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
		// v0.17.1: in third person the flash is on the body in the world (drawBloom); the screen only takes a light tint of it
		boolean thirdPerson = !Minecraft.getInstance().options.getCameraType().isFirstPerson();
		if (thirdPerson) {
			peak *= 0.28f;
		}
		// the field: up in a tick and a half, held, then fading out by 80% of the run
		float field = peak * Mth.clamp(t / 1.5f, 0f, 1f) * (1f - smooth((u - 0.2f) / 0.65f));
		// the opening flash: the first ticks run from a hot lime / orange-red into the flat field colour
		float hot = 1f - Mth.clamp((t - 0.5f) / 2.5f, 0f, 1f);
		int base = green ? 0x00A000 : 0xC00000;
		int flash = green ? 0x9CFF6A : 0xFF7A5A;
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
			Vec3 at = p.getPosition(partial).add(0, p.getBbHeight() * 0.52, 0).subtract(cp);
			float scale = Math.max(0.6f, p.getBbHeight() / 1.8f);
			pose.pushPose();
			pose.translate(at.x, at.y, at.z);
			drawBurst(pose, buffers, fx, t, d, scale, p.getId(), left, up, back);
			// v0.17.1 (user: "make the green flash show up in third person aswell"): the flat green field enveloping the body
			// with the Kirby dots on it, pushed toward the camera by the body's depth so it sits over the model
			float depth = Math.max(p.getBbWidth() * 0.75f, 0.45f) * scale;
			pose.translate(back.x * depth, back.y * depth, back.z * depth);
			drawBloom(pose, buffers, fx, t, d, scale, p.getId(), left, up, back);
			pose.popPose();
		}
	}

	private static void drawBurst(PoseStack pose, MultiBufferSource buffers, Fx fx, float t, float d, float scale, int id, Vector3f left, Vector3f up,
			Vector3f back) {
		boolean green = fx.kind == TRANSFORM;
		float u = t / d;
		// ---- the flash: a wide soft glow and a hot core, both additive, swelling fast then fading
		int glowRgb = green ? 0x3CFF2A : 0xFF3020;
		int coreRgb = green ? 0xD8FFB8 : 0xFFD0C0;
		float strength = fx.kind == TIMEOUT ? 1.15f : fx.kind == REVERT ? 0.85f : 1f;
		float swell = 1f - (float) Math.pow(1f - Mth.clamp(t / 5f, 0f, 1f), 3);
		float fade = 1f - smooth((t - 1f) / (d * 0.55f));
		VertexConsumer glow = buffers.getBuffer(RenderType.eyes(GLOW));
		PoseStack.Pose last = pose.last();
		billboard(glow, last, 0, 0, 0, (0.8f + 1.5f * swell) * scale * strength, scale(glowRgb, fade * 0.9f), left, up, back, LightTexture.FULL_BRIGHT);
		billboard(glow, last, 0, 0, 0, (0.4f + 0.6f * swell) * scale, scale(coreRgb, fade * (1f - Mth.clamp(t / 6f, 0f, 1f))), left, up, back,
				LightTexture.FULL_BRIGHT);
		// ---- the Kirby dots: clumps flung out of the body, drifting and shrinking away
		long seed = fx.start * 31L + id;
		int clumps = fx.kind == TIMEOUT ? 14 : fx.kind == REVERT ? 9 : 12;
		int ring = green ? 0xFF22C21A : 0xFFD8241A;
		VertexConsumer dots = buffers.getBuffer(RenderType.entityTranslucentEmissive(DOT));
		for (int c = 0; c < clumps; c++) {
			float yaw = hash(seed, c * 7 + 1) * Mth.TWO_PI;
			float rise = (hash(seed, c * 7 + 2) - 0.4f) * 1.4f;
			Vector3f dir = new Vector3f(Mth.cos(yaw), rise, Mth.sin(yaw)).normalize();
			float delay = hash(seed, c * 7 + 3) * 3f;
			float tc = t - delay;
			if (tc <= 0f) {
				continue;
			}
			// out fast, then easing off (drag)
			float speed = (0.13f + hash(seed, c * 7 + 4) * 0.1f) * scale;
			float dist = 0.45f * scale + speed * (1f - (float) Math.exp(-tc * 0.2f)) / 0.2f;
			float big = (0.08f + hash(seed, c * 7 + 5) * 0.11f) * scale;
			float shrink = 1f - smooth((u - 0.45f - hash(seed, c * 7 + 6) * 0.15f) / 0.4f);
			int blobs = 1 + (int) (hash(seed, c * 7 + 7) * 2.5f);
			for (int b = 0; b <= blobs; b++) {
				float r = b == 0 ? big : big * (0.38f + hash(seed, c * 31 + b) * 0.3f);
				float sc = pop(tc - b * 0.5f, 2.5f) * shrink;
				if (sc <= 0.02f) {
					continue;
				}
				// satellites sit round the main dot in the camera's plane
				float ang = hash(seed, c * 31 + b + 11) * Mth.TWO_PI;
				float off = b == 0 ? 0f : big + r * 0.25f;
				float ox = dir.x * dist + (left.x * Mth.cos(ang) + up.x * Mth.sin(ang)) * off;
				float oy = dir.y * dist + (left.y * Mth.cos(ang) + up.y * Mth.sin(ang)) * off;
				float oz = dir.z * dist + (left.z * Mth.cos(ang) + up.z * Mth.sin(ang)) * off;
				float rr = r * sc;
				// a green (red) rim behind the black dot: reads on any background and ties the dots to the flash
				billboard(dots, last, ox, oy, oz, rr * 1.16f, ring, left, up, back, LightTexture.FULL_BRIGHT);
				billboard(dots, last, ox + back.x * 0.012f, oy + back.y * 0.012f, oz + back.z * 0.012f, rr, 0xF0050805, left, up, back,
						LightTexture.FULL_BRIGHT);
			}
		}
	}

	/**
	 * The third-person flash (the screen effect's world twin): a flat vivid green ({@code #00A000}; red {@code #C00000} turning
	 * back) bloom the shape of the body -- an upright ellipse round it, swelling in over two ticks with a hot lime flash, holding,
	 * then shrinking and fading -- and clumps of solid black Kirby dots stuck to it, popping in with an overshoot, drifting
	 * outward past its edge and shrinking away as it goes. Camera-facing, so it reads the same from the wearer's third-person
	 * camera and from anyone watching.
	 */
	private static void drawBloom(PoseStack pose, MultiBufferSource buffers, Fx fx, float t, float d, float scale, int id, Vector3f left,
			Vector3f up, Vector3f back) {
		boolean green = fx.kind == TRANSFORM;
		float u = t / d;
		float peak = fx.kind == TIMEOUT ? 0.9f : fx.kind == REVERT ? 0.8f : 0.86f;
		float in = 1f - (float) Math.pow(1f - Mth.clamp(t / 2f, 0f, 1f), 3);
		float out = smooth((u - 0.3f) / 0.55f);
		float alpha = peak * Mth.clamp(t / 1.2f, 0f, 1f) * (1f - out);
		if (alpha <= 0.01f) {
			return;
		}
		// the ellipse: a little overshoot as it swells, shrinking to 70% as it fades
		float size = (0.55f + 0.6f * in + 0.12f * Mth.sin(Mth.clamp(t / 4f, 0f, 1f) * Mth.PI)) * (1f - 0.3f * out);
		float rx = 0.82f * scale * size, ry = 1.22f * scale * size;
		PoseStack.Pose last = pose.last();
		int base = green ? 0x00A000 : 0xC00000;
		int hotRgb = green ? 0x9CFF6A : 0xFF7A5A;
		float hot = 1f - Mth.clamp((t - 0.5f) / 2.5f, 0f, 1f);
		int rgb = lerpRgb(hot * 0.75f, base, hotRgb);
		VertexConsumer bloom = buffers.getBuffer(RenderType.entityTranslucentEmissive(BLOOM));
		ellipse(bloom, last, 0f, 0f, 0f, rx, ry, ((int) (alpha * 255) << 24) | rgb, left, up, back);
		// a lighter inner field for depth (the show's flash is brightest in the middle)
		ellipse(bloom, last, back.x * 0.01f, back.y * 0.01f, back.z * 0.01f, rx * 0.62f, ry * 0.7f,
				((int) (alpha * 0.45f * 255) << 24) | lerpRgb(0.35f + hot * 0.4f, base, hotRgb), left, up, back);
		// ---- the Kirby dots on the field: clumps (one big dot with two or three smaller ones bubbling off it)
		long seed = fx.start * 131L + id * 7L;
		int clumps = fx.kind == TIMEOUT ? 9 : fx.kind == REVERT ? 7 : 8;
		VertexConsumer dots = buffers.getBuffer(RenderType.entityTranslucentEmissive(DOT));
		float lift = 0.02f;
		for (int c = 0; c < clumps; c++) {
			// spread over the ellipse: a jittered ring of slots plus one near the middle
			float ang = (c + hash(seed, c * 11 + 1) * 0.6f) / clumps * Mth.TWO_PI + hash(seed, 999) * Mth.TWO_PI;
			float rad = c == 0 ? 0.15f : 0.42f + hash(seed, c * 11 + 2) * 0.4f;
			float ex = Mth.cos(ang) * rad, ey = Mth.sin(ang) * rad;
			float delay = 0.3f + hash(seed, c * 11 + 3) * 2.4f;
			float tc = t - delay;
			if (tc <= 0f) {
				continue;
			}
			// drifting outward, past the field's edge as it shrinks
			float drift = 1f + 0.035f * tc * (0.6f + rad);
			float cx = ex * 0.82f * scale * drift * (0.55f + 0.6f * in), cyy = ey * 1.22f * scale * drift * (0.55f + 0.6f * in);
			float big = (0.13f + hash(seed, c * 11 + 4) * 0.12f) * scale * (fx.kind == TIMEOUT ? 1.1f : 1f);
			float shrinkFrom = 0.32f + hash(seed, c * 11 + 5) * 0.12f;
			int blobs = 2 + (int) (hash(seed, c * 11 + 6) * 1.99f);
			for (int b = 0; b <= blobs; b++) {
				float r, ox = 0f, oy = 0f;
				if (b == 0) {
					r = big;
				} else {
					r = big * (0.34f + hash(seed, c * 40 + b * 3) * 0.32f);
					float a2 = hash(seed, c * 40 + b * 3 + 1) * Mth.TWO_PI;
					float dist = big + r * (0.05f + hash(seed, c * 40 + b * 3 + 2) * 0.4f) + 0.006f * tc * scale;
					ox = Mth.cos(a2) * dist;
					oy = Mth.sin(a2) * dist;
				}
				float sh = 1f - smooth((u - shrinkFrom - b * 0.04f) / 0.36f);
				float sc = pop(tc - b * 0.5f, 2.5f) * sh;
				if (sc <= 0.02f) {
					continue;
				}
				float px = cx + ox, py = cyy + oy;
				float wx = left.x * px + up.x * py + back.x * lift, wy = left.y * px + up.y * py + back.y * lift,
						wz = left.z * px + up.z * py + back.z * lift;
				billboard(dots, last, wx, wy, wz, r * sc, 0xF5030503, left, up, back, LightTexture.FULL_BRIGHT);
			}
		}
	}

	private static void ellipse(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float rx, float ry, int argb, Vector3f left, Vector3f up,
			Vector3f n) {
		int a = (argb >>> 24) & 255, rr = (argb >> 16) & 255, gg = (argb >> 8) & 255, bb = argb & 255;
		int light = LightTexture.FULL_BRIGHT;
		float lx = left.x * rx, ly = left.y * rx, lz = left.z * rx, ux = up.x * ry, uy = up.y * ry, uz = up.z * ry;
		vc.addVertex(p, x + lx + ux, y + ly + uy, z + lz + uz).setColor(rr, gg, bb, a).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
				.setNormal(p, n.x, n.y, n.z);
		vc.addVertex(p, x + lx - ux, y + ly - uy, z + lz - uz).setColor(rr, gg, bb, a).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
				.setNormal(p, n.x, n.y, n.z);
		vc.addVertex(p, x - lx - ux, y - ly - uy, z - lz - uz).setColor(rr, gg, bb, a).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
				.setNormal(p, n.x, n.y, n.z);
		vc.addVertex(p, x - lx + ux, y - ly + uy, z - lz + uz).setColor(rr, gg, bb, a).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
				.setNormal(p, n.x, n.y, n.z);
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
