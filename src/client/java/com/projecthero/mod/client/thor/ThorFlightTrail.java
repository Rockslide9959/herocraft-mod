package com.projecthero.mod.client.thor;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.projecthero.mod.power.ThorPowers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7 polish (user: "Change the flight vfx please, remove the particles, make it a very slightly trail behind him please"): Thor's
 * flight no longer puffs sparks and clouds behind him (the server's old {@code tickFlightTrail}); instead every viewer draws a faint,
 * short streak through where his body was over the last few ticks -- a soft pale-blue band with a thin white core, tapering and fading
 * to nothing within {@link #LIFE} ticks, and only while he is actually moving. Hovering leaves nothing.
 *
 * <p>Positions are sampled once a client tick per flying player (keyed by entity id; plain coordinates, no entity or world is held) and
 * cleared on a world change or disconnect. Not drawn for your own body in first person.
 */
public final class ThorFlightTrail {
	/** How long a point of the streak lasts, in ticks. */
	private static final int LIFE = 6;
	/** Gap left behind the body before the streak starts, in blocks (so it never glows over him). */
	private static final double GAP = 0.45;
	/** Under this much streak length (blocks) nothing is drawn -- a hover or a slow drift. */
	private static final double MIN_LENGTH = 0.6;
	private static final double MAX_DISTANCE_SQ = 96.0 * 96.0;

	private static final int SOFT = 0x9CCBFF;
	private static final int CORE = 0xEAF5FF;
	private static final float SOFT_ALPHA = 0.16f;
	private static final float CORE_ALPHA = 0.22f;
	private static final float SOFT_HALF_WIDTH = 0.2f;
	private static final float CORE_HALF_WIDTH = 0.045f;

	/** Per player (entity id): {x, y, z, game time} samples, newest first. */
	private static final Map<Integer, ArrayDeque<double[]>> TRAILS = new HashMap<>();
	private static int levelId;

	private ThorFlightTrail() {
	}

	public static void initialize() {
		ClientTickEvents.END_CLIENT_TICK.register(ThorFlightTrail::tick);
		WorldRenderEvents.AFTER_ENTITIES.register(ThorFlightTrail::render);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> TRAILS.clear());
	}

	private static Vec3 centre(Player p, float partial) {
		return new Vec3(Mth.lerp(partial, p.xo, p.getX()), Mth.lerp(partial, p.yo, p.getY()) + p.getBbHeight() * 0.5,
				Mth.lerp(partial, p.zo, p.getZ()));
	}

	private static void tick(Minecraft mc) {
		if (mc.level == null || mc.isPaused()) {
			return;
		}
		int id = System.identityHashCode(mc.level);
		if (id != levelId) {
			levelId = id;
			TRAILS.clear();
		}
		long now = mc.level.getGameTime();
		for (Player p : mc.level.players()) {
			if (ThorPowers.isFlying(p) && !p.isSpectator()) {
				ArrayDeque<double[]> d = TRAILS.computeIfAbsent(p.getId(), k -> new ArrayDeque<>());
				Vec3 c = centre(p, 1.0f);
				d.addFirst(new double[] { c.x, c.y, c.z, now });
			}
		}
		for (Iterator<ArrayDeque<double[]>> it = TRAILS.values().iterator(); it.hasNext();) {
			ArrayDeque<double[]> d = it.next();
			while (!d.isEmpty() && now - d.peekLast()[3] > LIFE) {
				d.removeLast();
			}
			if (d.isEmpty()) {
				it.remove();
			}
		}
	}

	private static void render(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || TRAILS.isEmpty() || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		double now = mc.level.getGameTime() + partial;
		Vec3 cam = context.camera().getPosition();
		PoseStack.Pose pose = context.matrixStack().last();
		VertexConsumer vc = null;
		for (Map.Entry<Integer, ArrayDeque<double[]>> e : TRAILS.entrySet()) {
			if (!(mc.level.getEntity(e.getKey()) instanceof Player p) || p.distanceToSqr(cam) > MAX_DISTANCE_SQ
					|| ThorLightningArcClient.firstPersonSelf(p)) {
				continue;
			}
			ArrayDeque<double[]> d = e.getValue();
			if (d.size() < 2) {
				continue;
			}
			// the streak: from just behind where he is now back through the samples (still flying: his live position leads it)
			double[] newest = d.peekFirst();
			Vec3 head = ThorPowers.isFlying(p) ? centre(p, partial) : new Vec3(newest[0], newest[1], newest[2]);
			int n = 0;
			Vec3[] path = new Vec3[d.size() + 1];
			float[] age = new float[d.size() + 1];
			double length = 0.0;
			for (double[] s : d) {
				Vec3 v = new Vec3(s[0], s[1], s[2]);
				if (n == 0) {
					// skip the points still inside his body; the streak starts GAP behind him, on the line to the first one past it
					Vec3 dir = v.subtract(head);
					if (dir.length() < GAP) {
						continue;
					}
					path[n] = head.add(dir.normalize().scale(GAP)).subtract(cam);
					age[n] = 0.0f;
					n++;
				}
				Vec3 rel = v.subtract(cam);
				length += rel.distanceTo(path[n - 1]);
				path[n] = rel;
				age[n] = (float) Math.max(0.0, now - s[3]);
				n++;
			}
			if (n < 2 || length < MIN_LENGTH) {
				continue;
			}
			if (vc == null) {
				vc = ThorDraw.buffer(context.consumers());
			}
			Vec3[] pts = java.util.Arrays.copyOf(path, n);
			float[] ages = java.util.Arrays.copyOf(age, n);
			// fade in over the first bit of length so a take-off never shows a hard end
			float grow = (float) Mth.clamp((length - MIN_LENGTH) / 1.2, 0.0, 1.0);
			strip(vc, pose, pts, ages, SOFT_HALF_WIDTH, SOFT, SOFT_ALPHA * grow);
			strip(vc, pose, pts, ages, CORE_HALF_WIDTH, CORE, CORE_ALPHA * grow);
		}
	}

	/**
	 * A camera-facing strip through {@code path} (camera-relative), each node {@code halfWidth} wide and {@code alpha} bright scaled by how
	 * young it is: both fall off with the square of its age over {@link #LIFE}, so the streak tapers and fades to nothing at its tail.
	 */
	private static void strip(VertexConsumer vc, PoseStack.Pose pose, Vec3[] path, float[] age, float halfWidth, int rgb, float alpha) {
		if (alpha <= 0.003f) {
			return;
		}
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		int len = path.length;
		Vec3[] side = new Vec3[len];
		int[] a = new int[len];
		for (int i = 0; i < len; i++) {
			float life = Mth.clamp(1.0f - age[i] / LIFE, 0.0f, 1.0f);
			float k = life * life;
			Vec3 tangent = path[Math.min(len - 1, i + 1)].subtract(path[Math.max(0, i - 1)]);
			Vec3 s = tangent.cross(path[i].scale(-1.0));
			double l = s.length();
			double w = halfWidth * (0.25 + 0.75 * k);
			side[i] = l < 1.0e-6 ? new Vec3(w, 0, 0) : s.scale(w / l);
			a[i] = Math.round(Mth.clamp(alpha * k, 0.0f, 1.0f) * 255.0f);
		}
		for (int i = 1; i < len; i++) {
			Vec3 p0 = path[i - 1];
			Vec3 p1 = path[i];
			vertex(vc, pose, p0.subtract(side[i - 1]), r, g, b, a[i - 1]);
			vertex(vc, pose, p0.add(side[i - 1]), r, g, b, a[i - 1]);
			vertex(vc, pose, p1.add(side[i]), r, g, b, a[i]);
			vertex(vc, pose, p1.subtract(side[i]), r, g, b, a[i]);
		}
	}

	private static void vertex(VertexConsumer vc, PoseStack.Pose pose, Vec3 p, int r, int g, int b, int a) {
		vc.addVertex(pose, (float) p.x, (float) p.y, (float) p.z)
				.setColor(r, g, b, a)
				.setUv(0.5f, 0.5f)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(LightTexture.FULL_BRIGHT)
				.setNormal(pose, 0f, 1f, 0f);
	}
}
