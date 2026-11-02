package com.projecthero.mod.client.omnitrix.alien;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.flight.LightTrail;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.xlr8.Xlr8Kit;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 (XLR8 revamp, user: "add xlr8s speed trail to his speed mode please, the blue and black lines"): XLR8's streak lines.
 * While he runs in Speed Mode (or dashes, spins the Tornado Run or skates Friction Zero), three lines stream back from him -- one
 * from each foot at ankle height and one from the tail tip -- each a blue band ({@code 0x3A7BD5}, a {@code 0x9FD8FF} core) with
 * thin black ({@code 0x0A0E14}) edges ({@link LightTrail#drawBanded}); 0.8 s long, 1.4 s with a white-hot core in Overdrive.
 * They run alongside the after-image copies ({@code SuperSpeedClientV0145}). His zips (Raptor Strike, Blur Dash, Raptor Rush,
 * the Tornado Run's rings) lay the same lines along the zip, fading over 0.5 s ({@link #zip}, from the streak packet). In first
 * person only the two foot lines draw, so nothing crosses the camera. Also: Time Burst's blue visor-glass tint on his own
 * screen. Client-only; every viewer records every XLR8 in view.
 */
public final class Xlr8StreakTrail {
	/** Line life (ticks): 0.8 s, 1.4 s in Overdrive; a zip's line fades over 0.5 s. */
	public static final int LIFE = 16, LIFE_OVERDRIVE = 28, ZIP_LIFE = 10;
	/** Half-width of a line at its head (blocks). */
	private static final float WIDTH = 0.11f;
	/** Faster than this (blocks a tick) and the lines draw. */
	private static final double MIN_SPEED = 0.18;

	private static final class Trail {
		final LightTrail.History[] lines = { new LightTrail.History(LIFE), new LightTrail.History(LIFE), new LightTrail.History(LIFE) };
		boolean overdrive;
		boolean live;
	}

	private record Zip(UUID who, Vec3 from, Vec3 to, Vec3 side, long tick) {
	}

	private static final Map<UUID, Trail> TRAILS = new HashMap<>();
	private static final List<Zip> ZIPS = new ArrayList<>();
	private static boolean registered;

	private Xlr8StreakTrail() {
	}

	/** From {@code Xlr8Client}'s constructor (once). */
	static void init() {
		if (registered) {
			return;
		}
		registered = true;
		ClientTickEvents.END_CLIENT_TICK.register(Xlr8StreakTrail::tick);
		WorldRenderEvents.AFTER_ENTITIES.register(Xlr8StreakTrail::render);
		HudRenderCallback.EVENT.register(Xlr8StreakTrail::burstTint);
	}

	/** Moving with the lines on: Speed Mode, a dash, the tornado or the skate. */
	static boolean streaking(Player p) {
		if (!Xlr8Kit.is(p) || p.isSpectator() || p.isInvisible()) {
			return false;
		}
		int f = Omnitrix.state(p).kitFlags;
		return (f & (Xlr8Kit.F_SPEED | Xlr8Kit.F_DASH | Xlr8Kit.F_TORNADO | Xlr8Kit.F_SKATE)) != 0;
	}

	/** The three emitters at {@code partial}: [0] right foot, [1] left foot (ankle height), [2] the tail tip. */
	static Vec3[] emitters(Player p, float partial) {
		Vec3 feet = p.getPosition(partial);
		float s = p.getScale();
		float yaw = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
		Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
		Vec3 left = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));
		float pos = p.walkAnimation.position(partial);
		float spd = Math.min(1f, p.walkAnimation.speed(partial));
		float swing = Mth.cos(pos * 0.6662f) * 1.4f * spd; // vanilla's leg swing (right leg; the left is opposite)
		boolean skate = (Omnitrix.state(p).kitFlags & Xlr8Kit.F_SKATE) != 0;
		double ankle = skate ? 0.06 : 0.12;
		Vec3 r = feet.add(left.scale(-0.12 * s)).add(fwd.scale(-Mth.sin(swing) * 0.62 * s)).add(0, ankle * s, 0);
		Vec3 l = feet.add(left.scale(0.12 * s)).add(fwd.scale(Mth.sin(swing) * 0.62 * s)).add(0, ankle * s, 0);
		// the tail streams out flat behind at speed; on a hard turn it swings wide (Xlr8Client.tailSwing)
		float swingTail = Xlr8Client.tailSwing(p, partial);
		Vec3 back = fwd.scale(-1).yRot(-swingTail);
		double tailY = skate ? 0.35 : 0.72;
		Vec3 tail = feet.add(back.scale(1.15 * s)).add(fwd.scale(-0.1 * s)).add(0, tailY * s, 0);
		return new Vec3[] { r, l, tail };
	}

	private static void tick(Minecraft mc) {
		if (mc.level == null) {
			TRAILS.clear();
			ZIPS.clear();
			return;
		}
		if (mc.isPaused()) {
			return;
		}
		long now = mc.level.getGameTime();
		for (Player p : mc.level.players()) {
			boolean on = streaking(p) && Math.hypot(p.getX() - p.xo, p.getZ() - p.zo) + Math.abs(p.getY() - p.yo) * 0.5 > MIN_SPEED;
			Trail t = TRAILS.get(p.getUUID());
			if (t == null) {
				if (!on) {
					continue;
				}
				t = new Trail();
				TRAILS.put(p.getUUID(), t);
			}
			boolean od = (Omnitrix.state(p).kitFlags & Xlr8Kit.F_OVERDRIVE) != 0;
			if (od != t.overdrive) {
				t.overdrive = od;
				for (LightTrail.History h : t.lines) {
					h.life(od ? LIFE_OVERDRIVE : LIFE);
				}
			}
			t.live = on;
			Vec3[] at = emitters(p, 1f);
			for (int i = 0; i < 3; i++) {
				if (on) {
					t.lines[i].sample(at[i], now);
				} else {
					t.lines[i].prune(now);
				}
			}
		}
		for (Iterator<Map.Entry<UUID, Trail>> it = TRAILS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Trail> e = it.next();
			Trail t = e.getValue();
			if (mc.level.getPlayerByUUID(e.getKey()) == null || !t.live && t.lines[0].isEmpty() && t.lines[1].isEmpty() && t.lines[2].isEmpty()) {
				it.remove();
			}
		}
		ZIPS.removeIf(z -> now - z.tick() > ZIP_LIFE);
	}

	/** A streak packet for XLR8 ({@code SuperSpeedClientV0145.acceptStreak}): lay two lines along the zip at foot height. */
	public static void zip(Player p, Vec3 from, Vec3 to) {
		if (p.level() == null || from.distanceToSqr(to) < 0.25) {
			return; // a side-step blur, not a zip
		}
		Vec3 d = to.subtract(from);
		Vec3 h = new Vec3(d.x, 0, d.z);
		Vec3 side = h.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : new Vec3(-h.z, 0, h.x).normalize();
		ZIPS.add(new Zip(p.getUUID(), from, to, side, p.level().getGameTime()));
		while (ZIPS.size() > 64) {
			ZIPS.remove(0);
		}
	}

	private static void render(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || ctx.consumers() == null || ctx.matrixStack() == null || TRAILS.isEmpty() && ZIPS.isEmpty()) {
			return;
		}
		PoseStack pose = ctx.matrixStack();
		MultiBufferSource buffers = ctx.consumers();
		Vec3 cam = ctx.camera().getPosition();
		float partial = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
		long now = mc.level.getGameTime();
		for (Map.Entry<UUID, Trail> e : TRAILS.entrySet()) {
			Player p = mc.level.getPlayerByUUID(e.getKey());
			if (p == null || p.distanceToSqr(cam) > 128 * 128) {
				continue;
			}
			Trail t = e.getValue();
			boolean firstPerson = p == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
			Vec3[] head = t.live ? emitters(p, partial) : null;
			LightTrail.Banded style = t.overdrive ? LightTrail.XLR8_OVERDRIVE : LightTrail.XLR8;
			for (int i = 0; i < 3; i++) {
				if (i == 2 && firstPerson) {
					continue; // the tail line would cross the camera
				}
				t.lines[i].drawBanded(pose, buffers, cam, head == null ? null : head[i], partial, now, style, i == 2 ? WIDTH * 0.85f : WIDTH, 1f);
			}
		}
		for (Zip z : ZIPS) {
			float k = Mth.clamp((now - z.tick() + partial) / ZIP_LIFE, 0f, 1f);
			if (k >= 1f) {
				continue;
			}
			Player p = mc.level.getPlayerByUUID(z.who());
			boolean od = p != null && (Omnitrix.state(p).kitFlags & Xlr8Kit.F_OVERDRIVE) != 0;
			double len = z.from().distanceTo(z.to());
			int n = (int) Mth.clamp(len / 0.6, 2, 48);
			for (int line = -1; line <= 1; line += 2) {
				Vec3 off = z.side().scale(0.13 * line).add(0, 0.12, 0);
				Vec3[] pts = new Vec3[n + 1];
				float[] age = new float[n + 1];
				for (int i = 0; i <= n; i++) {
					double f = 1.0 - i / (double) n; // newest (the end of the zip) first
					pts[i] = z.from().lerp(z.to(), f).add(off);
					// the start of the zip fades first, so it reads as a blur racing to its end
					age[i] = Mth.clamp(k + (1f - (float) f) * 0.35f * (1f - k), 0f, 1f);
				}
				LightTrail.drawBanded(pose, buffers, cam, pts, age, od ? LightTrail.XLR8_OVERDRIVE : LightTrail.XLR8, WIDTH, 1f);
			}
		}
	}

	/**
	 * Time Burst on his own screen: a thin blue visor-glass tint and the edges stretched into blue streaks while the world
	 * crawls (fades out over the last second).
	 */
	private static void burstTint(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.options.hideGui || !Xlr8Kit.is(mc.player)) {
			return;
		}
		float partial = delta.getGameTimeDeltaPartialTick(false);
		float v = Xlr8Kit.burstView(mc.player, partial);
		if (v <= 0f) {
			return;
		}
		float a = Math.min(1f, v * 5f); // 0.2 s in at the end, out over the last second
		int w = g.guiWidth(), h = g.guiHeight();
		g.fill(0, 0, w, h, argb(0.10f * a, 0x3A7BD5));
		// the stretched edges: horizontal streaks fading in from both sides
		int band = Math.max(12, w / 7);
		float t = mc.player.tickCount + partial;
		for (int y = 0; y < h; y += 3) {
			float n = 0.5f + 0.5f * Mth.sin(y * 0.37f + t * 0.6f);
			int len = (int) (band * (0.45f + 0.55f * n));
			for (int j = 0; j < 4; j++) {
				int x0 = len * j / 4, x1 = len * (j + 1) / 4;
				int col = argb(0.22f * a * (0.5f + 0.5f * n) * (1f - j / 4f), 0x9FD8FF);
				g.fill(x0, y, x1, y + 1, col);
				g.fill(w - x1, y, w - x0, y + 1, col);
			}
		}
		// the visor's glass rim
		int rim = argb(0.35f * a, 0x0A0E14);
		g.fill(0, 0, w, 2, rim);
		g.fill(0, h - 2, w, h, rim);
	}

	private static int argb(float alpha, int rgb) {
		return (Mth.clamp(Math.round(alpha * 255f), 0, 255) << 24) | (rgb & 0xFFFFFF);
	}
}
