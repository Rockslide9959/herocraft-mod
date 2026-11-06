package com.projecthero.mod.client.omnitrix.alien.jetray;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.mutation.pyro.PyroDraw;
import com.projecthero.mod.client.flight.LightTrail;
import com.projecthero.mod.omnitrix.alien.jetray.JetrayKit;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 round 4 (user: "while hes holding shift+X while flying he gets a green trail"): the green streamer Jetray lays behind him past
 * the barrier, in his neuroshock green. {@link LightTrail.History} keeps the points; the drawing is two additive ribbons here, not
 * {@code LightTrail.draw}, whose wide sheet and glows bloom into a screen-filling fan when the line runs past the third-person camera (the
 * ribbons narrow and fade as they near the lens instead). It lives 2 s, so at full speed it is 400 blocks long. Every viewer records every
 * Jetray in view from the synced flag and his position (nothing is sent for it). In first person the point at his own position is left
 * out, so the streamer starts behind the lens and is there when he looks back.
 */
public final class JetraySonicTrail {
	/** Ticks a point lives. */
	public static final int LIFE = 40;
	/** Moving slower than this (blocks a tick), no new points. */
	private static final double MIN_STEP = 0.5;
	private static final int SHEET = 0xFF5CE028, CORE = 0xFF7CFF3A, DEEP = 0xFF2FB814;
	/** Points between two history samples: they are up to 10 blocks apart. */
	private static final int SUBDIVIDE = 5;

	/** Per player; emptied as the points die, and with the world. */
	private static final Map<UUID, LightTrail.History> TRAILS = new HashMap<>();
	private static boolean registered;

	private JetraySonicTrail() {
	}

	/** Once, from {@code JetrayClient}. */
	public static void init() {
		if (registered) {
			return;
		}
		registered = true;
		ClientTickEvents.END_CLIENT_TICK.register(JetraySonicTrail::tick);
		WorldRenderEvents.AFTER_ENTITIES.register(JetraySonicTrail::render);
	}

	private static Vec3 emitter(Player p, float partial) {
		return p.getPosition(partial).add(0, p.getBbHeight() * 0.5, 0);
	}

	private static void tick(Minecraft mc) {
		if (mc.level == null) {
			TRAILS.clear();
			return;
		}
		if (mc.isPaused()) {
			return;
		}
		long now = mc.level.getGameTime();
		for (Player p : mc.level.players()) {
			if (JetrayKit.supersonic(p) && p.position().distanceToSqr(p.xo, p.yo, p.zo) > MIN_STEP * MIN_STEP) {
				TRAILS.computeIfAbsent(p.getUUID(), k -> new LightTrail.History(LIFE)).sample(emitter(p, 1f), now);
			}
		}
		TRAILS.values().removeIf(h -> {
			h.prune(now);
			return h.isEmpty();
		});
	}

	private static void render(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || TRAILS.isEmpty() || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		Vec3 cam = context.camera().getPosition();
		PoseStack pose = context.matrixStack();
		MultiBufferSource buffers = context.consumers();
		long now = mc.level.getGameTime();
		TRAILS.forEach((id, h) -> {
			Player p = mc.level.getPlayerByUUID(id);
			boolean own = p == mc.player && mc.options.getCameraType().isFirstPerson();
			Vec3 head = p != null && !own && JetrayKit.supersonic(p) ? emitter(p, partial) : null;
			draw(pose, buffers, cam, h, head, partial, now);
		});
	}

	private static void draw(PoseStack pose, MultiBufferSource buffers, Vec3 cam, LightTrail.History h, Vec3 head, float partial, long now) {
		Vec3[] pts = h.pointsNewestFirst(head);
		float[] ages = h.agesNewestFirst(head, partial, now);
		if (pts == null || ages == null || pts.length < 2) {
			return;
		}
		List<Vec3> line = new ArrayList<>();
		List<Float> fade = new ArrayList<>();
		List<Float> scale = new ArrayList<>();
		for (int i = 0; i + 1 < pts.length; i++) {
			for (int k = 0; k < SUBDIVIDE; k++) {
				float u = k / (float) SUBDIVIDE;
				add(line, fade, scale, pts[i].lerp(pts[i + 1], u).subtract(cam), ages[i] + (ages[i + 1] - ages[i]) * u);
			}
		}
		add(line, fade, scale, pts[pts.length - 1].subtract(cam), ages[ages.length - 1]);
		int n = line.size() - 1;
		// a solid core: additive green over a blue sky adds up to white, and the user asked for a green trail
		PyroDraw.ribbon(pose, PyroDraw.solidBuffer(buffers), line, s -> 0.3 * scale.get(idx(s, n)), s -> PyroDraw.lerpRgb(CORE, DEEP, (float) s));
		PyroDraw.ribbon(pose, PyroDraw.additive(buffers), line, s -> 0.8 * scale.get(idx(s, n)), s -> PyroDraw.dim(SHEET, 0.3f * fade.get(idx(s, n))));
	}

	private static int idx(double s, int n) {
		return (int) Math.round(s * n);
	}

	/** A point of the line, camera-relative: it thins and dims out with age, and within a few blocks of the lens. */
	private static void add(List<Vec3> line, List<Float> fade, List<Float> scale, Vec3 at, float age) {
		double d = at.length();
		float life = Math.max(0f, 1f - age / LIFE);
		line.add(at);
		fade.add(life * (float) Math.min(1.0, Math.max(0.0, (d - 1.5) / 4.0)));
		scale.add((float) Math.min(1.0, d / 8.0) * (float) Math.sqrt(life)); // tapers to nothing: a solid ribbon cannot fade
	}
}
