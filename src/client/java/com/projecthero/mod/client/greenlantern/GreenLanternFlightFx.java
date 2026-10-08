package com.projecthero.mod.client.greenlantern;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.FlightPoseHelper;
import com.projecthero.mod.client.maxsteel.TurboDraw;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.15, user request: "When player is flying add a transparent green glow around them while hovering and cruising,
 * when the player starts sprint flying add a model for a green trail that trails behind the player, make it look really
 * good, keep the glow around the player while sprint flying". Client-only, for every Green Lantern in Ring Flight (the
 * synced {@code GREEN_LANTERN_FLYING} / {@code GREEN_LANTERN_BOOSTING} flags), so everyone sees it:
 * <ul>
 *   <li><b>The aura</b> -- an ellipsoid of light round the body, leaning with it ({@link FlightPoseHelper#lean}): an
 *   additive glow, a faint translucent shell and an inner core, breathing slowly; brighter while sprint-flying. Not drawn
 *   for yourself in first person (you would be inside it).</li>
 *   <li><b>The trail</b> (sprint flight) -- a tapered streamer of hard light from the legs back along the path actually
 *   flown: a sampled point every tick (plus the live interpolated one at its head), each fading over {@link #TRAIL_LIFE}
 *   ticks, drawn as camera-facing ribbons -- a soft green sheet, a white-hot core and an additive glow -- with two thin
 *   strands twisting round it. It keeps fading out behind you for a moment after the sprint ends.</li>
 * </ul>
 */
public final class GreenLanternFlightFx {
	/** How long a trail point lives, ticks. */
	private static final int TRAIL_LIFE = 16;

	private record Point(Vec3 pos, long tick) {
	}

	private static final class Trail {
		final ArrayDeque<Point> points = new ArrayDeque<>();
		/** 0..1 eased aura strength (fades in on take-off, out on landing). */
		float aura;
		float auraPrev;
		float boost;
		float boostPrev;
	}

	private static final Map<UUID, Trail> TRAILS = new HashMap<>();

	private GreenLanternFlightFx() {
	}

	public static void initialize() {
		ClientTickEvents.END_CLIENT_TICK.register(GreenLanternFlightFx::tick);
		WorldRenderEvents.AFTER_ENTITIES.register(GreenLanternFlightFx::render);
	}

	private static boolean flying(Player p) {
		return p.getAttachedOrElse(ModAttachments.GREEN_LANTERN_FLYING, false) && !p.isInvisible();
	}

	private static boolean sprintFlying(Player p) {
		return flying(p) && (p.getAttachedOrElse(ModAttachments.GREEN_LANTERN_BOOSTING, false) || p.isSprinting());
	}

	/** Where the trail leaves the body: low on the legs, following the flight lean. */
	private static Vec3 emitter(Player p, float partial) {
		Vec3 feet = p.getPosition(partial);
		float yaw = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
		float lean = FlightPoseHelper.lean(p, partial) * Mth.DEG_TO_RAD;
		Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
		Vec3 up = new Vec3(0, Mth.cos(lean), 0).add(fwd.scale(Mth.sin(lean)));
		return feet.add(up.scale(0.35));
	}

	private static void tick(Minecraft mc) {
		if (mc.level == null || mc.isPaused()) {
			if (mc.level == null) {
				TRAILS.clear();
			}
			return;
		}
		long now = mc.level.getGameTime();
		for (Player p : mc.level.players()) {
			boolean fly = flying(p);
			Trail t = TRAILS.get(p.getUUID());
			if (t == null) {
				if (!fly) {
					continue;
				}
				t = new Trail();
				TRAILS.put(p.getUUID(), t);
			}
			t.auraPrev = t.aura;
			t.boostPrev = t.boost;
			t.aura = Mth.approach(t.aura, fly ? 1f : 0f, 0.12f);
			boolean sprint = sprintFlying(p);
			t.boost = Mth.approach(t.boost, sprint ? 1f : 0f, 0.15f);
			if (sprint) {
				Vec3 at = emitter(p, 1f);
				Point last = t.points.peekLast();
				if (last == null || last.pos.distanceToSqr(at) > 0.0004) {
					t.points.addLast(new Point(at, now));
				}
			}
			while (!t.points.isEmpty() && now - t.points.peekFirst().tick > TRAIL_LIFE) {
				t.points.removeFirst();
			}
		}
		for (Iterator<Map.Entry<UUID, Trail>> it = TRAILS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Trail> e = it.next();
			Player p = mc.level.getPlayerByUUID(e.getKey());
			Trail t = e.getValue();
			if (p == null || (t.aura <= 0f && t.auraPrev <= 0f && t.points.isEmpty())) {
				it.remove();
			}
		}
	}

	private static void render(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || ctx.consumers() == null || ctx.matrixStack() == null || TRAILS.isEmpty()) {
			return;
		}
		PoseStack pose = ctx.matrixStack();
		MultiBufferSource buffers = ctx.consumers();
		Vec3 cam = ctx.camera().getPosition();
		float partial = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
		long now = mc.level.getGameTime();
		for (Map.Entry<UUID, Trail> e : TRAILS.entrySet()) {
			Player p = mc.level.getPlayerByUUID(e.getKey());
			if (p == null) {
				continue;
			}
			Trail t = e.getValue();
			float aura = Mth.lerp(partial, t.auraPrev, t.aura);
			float boost = Mth.lerp(partial, t.boostPrev, t.boost);
			boolean self = p == mc.player && mc.options.getCameraType().isFirstPerson();
			float time = p.tickCount + partial;
			if (aura > 0.01f && !self) {
				aura(pose, buffers, cam, p, partial, time, aura, boost);
			}
			if (!t.points.isEmpty()) {
				trail(pose, buffers, cam, p, t, partial, now, time, sprintFlying(p));
			}
		}
	}

	// ---------------------------------------------------------------- the aura

	private static void aura(PoseStack pose, MultiBufferSource buffers, Vec3 cam, Player p, float partial, float time, float k,
			float boost) {
		Vec3 feet = p.getPosition(partial);
		float yaw = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot);
		float lean = FlightPoseHelper.lean(p, partial);
		float breathe = 0.5f + 0.5f * Mth.sin(time * 0.15f);
		float s = p.getScale();
		pose.pushPose();
		pose.translate(feet.x - cam.x, feet.y - cam.y, feet.z - cam.z);
		pose.mulPose(Axis.YP.rotationDegrees(-yaw));
		pose.mulPose(Axis.XP.rotationDegrees(lean));
		pose.scale(s, s, s);
		pose.translate(0f, 0.95f, 0f);
		float a = k * (0.75f + 0.25f * breathe) * (1f + 0.35f * boost);
		// additive glow: brightens whatever is behind it instead of tinting it dark
		VertexConsumer add = HardLightRibbon.additive(buffers);
		shell(add, pose, 0.7f, 1.3f, 0.58f, 0x35F075, 0.1f * a);
		shell(add, pose, 0.56f, 1.14f, 0.45f, 0x35F075, 0.05f * a);
		// a faint translucent skin of light, just outside the body
		VertexConsumer vc = HardLightDraw.buffer(buffers);
		shell(vc, pose, 0.7f + 0.03f * breathe, 1.3f + 0.04f * breathe, 0.56f + 0.03f * breathe, 0x35F075, 0.06f * a);
		pose.popPose();
	}

	/** An ellipsoid (radii rx / ry / rz) at the origin. */
	private static void shell(VertexConsumer vc, PoseStack pose, float rx, float ry, float rz, int rgb, float alpha) {
		pose.pushPose();
		pose.scale(rx, ry, rz);
		TurboDraw.sphere(vc, pose, 1f, rgb, alpha);
		pose.popPose();
	}

	// ---------------------------------------------------------------- the trail

	private static void trail(PoseStack pose, MultiBufferSource buffers, Vec3 cam, Player p, Trail t, float partial, long now,
			float time, boolean live) {
		int n = t.points.size() + (live ? 1 : 0);
		if (n < 2) {
			return;
		}
		Vec3[] pts = new Vec3[n];
		float[] age = new float[n]; // 0 = fresh .. 1 = gone
		int i = 0;
		// newest first: the live emitter, then the samples back along the path
		if (live) {
			pts[i] = emitter(p, partial);
			age[i++] = 0f;
		}
		Iterator<Point> it = t.points.descendingIterator();
		while (it.hasNext()) {
			Point pt = it.next();
			pts[i] = pt.pos;
			age[i++] = Mth.clamp((now - pt.tick + partial) / TRAIL_LIFE, 0f, 1f);
		}
		pose.pushPose();
		pose.translate(-cam.x, -cam.y, -cam.z);
		PoseStack.Pose last = pose.last();
		VertexConsumer vc = HardLightRibbon.translucent(buffers);
		ribbon(vc, last, cam, pts, age, 0.34f, 0x35F075, 0.42f, 1.0f);
		ribbon(vc, last, cam, pts, age, 0.11f, 0xF0FFF4, 0.9f, 1.6f);
		strands(vc, last, cam, pts, age, time);
		VertexConsumer add = HardLightRibbon.additive(buffers);
		ribbon(add, last, cam, pts, age, 0.6f, 0x35F075, 0.3f, 1.2f);
		pose.popPose();
	}

	/**
	 * A camera-facing ribbon through {@code pts}, {@code width} wide at its head, tapering and fading with age
	 * ({@code fadePow} shapes the fade).
	 */
	private static void ribbon(VertexConsumer vc, PoseStack.Pose pose, Vec3 cam, Vec3[] pts, float[] age, float width, int rgb,
			float alpha, float fadePow) {
		Vec3 prevL = null;
		Vec3 prevR = null;
		float prevA = 0f;
		for (int i = 0; i < pts.length; i++) {
			Vec3 dir = i + 1 < pts.length ? pts[i].subtract(pts[i + 1]) : pts[i - 1].subtract(pts[i]);
			if (dir.lengthSqr() < 1.0e-8) {
				dir = new Vec3(0, 1, 0);
			}
			Vec3 side = dir.cross(cam.subtract(pts[i]));
			side = side.lengthSqr() < 1.0e-8 ? new Vec3(1, 0, 0) : side.normalize();
			float life = 1f - age[i];
			// taper: a fine point at the very head (it leaves the body), widest just behind, thinning to the tail
			float head = Math.min(1f, (i + 0.35f) / 2.2f);
			float w = width * head * (float) Math.pow(life, 0.7);
			float a = alpha * (float) Math.pow(life, fadePow);
			Vec3 l = pts[i].add(side.scale(w));
			Vec3 r = pts[i].subtract(side.scale(w));
			if (prevL != null) {
				HardLightRibbon.quad(vc, pose, (float) prevL.x, (float) prevL.y, (float) prevL.z, (float) l.x, (float) l.y, (float) l.z,
						(float) r.x, (float) r.y, (float) r.z, (float) prevR.x, (float) prevR.y, (float) prevR.z, rgb, prevA, a, a, prevA);
			}
			prevL = l;
			prevR = r;
			prevA = a;
		}
	}

	/** Two thin strands of light twisting round the trail. */
	private static void strands(VertexConsumer vc, PoseStack.Pose pose, Vec3 cam, Vec3[] pts, float[] age, float time) {
		for (int s = 0; s < 2; s++) {
			Vec3[] q = new Vec3[pts.length];
			for (int i = 0; i < pts.length; i++) {
				Vec3 dir = i + 1 < pts.length ? pts[i].subtract(pts[i + 1]) : pts[i - 1].subtract(pts[i]);
				dir = dir.lengthSqr() < 1.0e-8 ? new Vec3(0, 1, 0) : dir.normalize();
				Vec3 a = Math.abs(dir.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
				Vec3 u = dir.cross(a).normalize();
				Vec3 v = dir.cross(u).normalize();
				float phase = i * 0.55f - time * 0.5f + s * Mth.PI;
				float r = 0.26f * Math.min(1f, (i + 0.35f) / 2.2f) * (1f - age[i] * 0.5f);
				q[i] = pts[i].add(u.scale(Mth.cos(phase) * r)).add(v.scale(Mth.sin(phase) * r));
			}
			ribbon(vc, pose, cam, q, age, 0.035f, 0xB8FFCC, 0.85f, 1.3f);
		}
	}
}
