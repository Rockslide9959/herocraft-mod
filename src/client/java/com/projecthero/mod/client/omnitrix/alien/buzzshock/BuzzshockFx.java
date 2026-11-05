package com.projecthero.mod.client.omnitrix.alien.buzzshock;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.mutation.pyro.PyroDraw;
import com.projecthero.mod.client.omnitrix.OmnitrixRenderers;
import com.projecthero.mod.client.omnitrix.alien.brainstorm.BrainstormFx;
import com.projecthero.mod.omnitrix.alien.buzzshock.BuzzshockKit;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 Buzzshock's rendered electricity, on Brainstorm's lightning ({@link BrainstormFx#bolt}: jagged additive ribbons with a
 * white-hot core and forks, re-rolled every other tick) in his own lime green: his bolts (from his hand, or from a copy / the Spark
 * Bomb when the carrier names one), Megavolt's thick one, and the Spark Bomb -- a churning green-white orb licking arcs. v0.17.2 round 3
 * (user: "upgrade his spark flight please ... with his crackling lightning vfx please"): Spark Flight -- bolts crawl over him, a jagged
 * tail of lightning streams back along the path he just flew, and in his own view arcs streak past the edges of the screen.
 */
public final class BuzzshockFx {
	private BuzzshockFx() {
	}

	private static boolean registered;

	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		OmnitrixRenderers.projectileLook("buzzshock_arc", (e, partial, pose, buffers) -> arc(e, partial, pose, buffers, false));
		OmnitrixRenderers.projectileLook("buzzshock_big", (e, partial, pose, buffers) -> arc(e, partial, pose, buffers, true));
		OmnitrixRenderers.projectileLook("buzzshock_ball", BuzzshockFx::ball);
		WorldRenderEvents.AFTER_ENTITIES.register(BuzzshockFx::flights);
	}

	// ---------------------------------------------------------------- Spark Flight

	/** How many ticks of his path the lightning tail keeps. */
	private static final int TAIL = 14;
	/** Each flier's recent path, newest first (the tail is drawn from it, so it follows the curve he actually flew). */
	private static final Map<Player, Deque<Vec3>> PATHS = new WeakHashMap<>();

	/** One client tick: his path, while he flies; it runs out behind him once he stops. */
	public static void tickFlight(Player p) {
		Deque<Vec3> path = PATHS.get(p);
		if (BuzzshockKit.holdFlying(p)) {
			if (path == null) {
				path = new ArrayDeque<>();
				PATHS.put(p, path);
			}
			path.addFirst(p.position().add(0, p.getBbHeight() * 0.5, 0));
			while (path.size() > TAIL) {
				path.removeLast();
			}
		} else if (path != null) {
			path.pollLast();
			if (path.isEmpty()) {
				PATHS.remove(p);
			}
		}
	}

	private static void flights(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || context.consumers() == null || context.matrixStack() == null || PATHS.isEmpty()) {
			return;
		}
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		PoseStack pose = context.matrixStack();
		MultiBufferSource buffers = context.consumers();
		for (Player p : mc.level.players()) {
			Deque<Vec3> path = PATHS.get(p);
			if (path == null || path.isEmpty() || p.isInvisible()) {
				continue;
			}
			boolean own = p == mc.player && mc.options.getCameraType().isFirstPerson();
			if (own) {
				ownView(pose, buffers, p, partial);
			} else {
				tail(pose, buffers, p, path, partial);
			}
		}
	}

	/** The crackle over his body and the jagged tail down his path. */
	private static void tail(PoseStack pose, MultiBufferSource buffers, Player p, Deque<Vec3> path, float partial) {
		boolean flying = BuzzshockKit.holdFlying(p);
		Vec3 c = flying ? p.getPosition(partial).add(0, p.getBbHeight() * 0.5, 0) : path.peekFirst();
		long tick = p.level().getGameTime();
		Random rnd = new Random(p.getId() * 31L + tick / 2);
		// the tail: three strands, each a run of bolts along the path, thinning away from him
		Vec3[] pts = path.toArray(new Vec3[0]);
		float n = Math.max(1f, pts.length - 1f);
		for (int strand = 0; strand < 3; strand++) {
			Vec3 from = c;
			for (int i = 2 + strand; i < pts.length; i += 3) {
				float s = i / n;
				Vec3 to = pts[i].add((rnd.nextDouble() - 0.5) * 0.5 * s, (rnd.nextDouble() - 0.5) * 0.5 * s, (rnd.nextDouble() - 0.5) * 0.5 * s);
				if (strand == 0) {
					BrainstormFx.bolt(pose, buffers, from, to, rnd.nextLong(), 0.05f * (1f - s * 0.7f), 1f - s * 0.6f, BuzzshockKit.COLOUR);
				} else {
					BrainstormFx.bolt(pose, buffers, from, to, rnd.nextLong(), 0.024f * (1f - s * 0.6f), 0.8f - s * 0.5f, 0xC8FF80);
				}
				from = to;
			}
		}
		if (!flying) {
			return;
		}
		// the crawl: short arcs jumping off him all round, and a soft halo
		float sz = p.getBbHeight();
		for (int i = 0; i < 6; i++) {
			Vec3 a = c.add((rnd.nextDouble() - 0.5) * sz * 0.7, (rnd.nextDouble() - 0.5) * sz * 0.7, (rnd.nextDouble() - 0.5) * sz * 0.7);
			Vec3 b = a.add((rnd.nextDouble() - 0.5) * 1.1, (rnd.nextDouble() - 0.5) * 1.1, (rnd.nextDouble() - 0.5) * 1.1);
			BrainstormFx.bolt(pose, buffers, a, b, rnd.nextLong(), 0.03f, 0.9f, BuzzshockKit.COLOUR);
		}
		Vec3 cam = BrainstormFx.cam();
		pose.pushPose();
		VertexConsumer vc = PyroDraw.glowBuffer(buffers);
		PyroDraw.glow(pose, vc, c.subtract(cam), 0.75f + rnd.nextFloat() * 0.2f, PyroDraw.dim(BuzzshockKit.COLOUR, 0.4f));
		pose.popPose();
	}

	/** In his own view: thin arcs running back past the edges of the screen, and one lick off the leading fist. */
	private static void ownView(PoseStack pose, MultiBufferSource buffers, Player p, float partial) {
		Vec3 eye = p.getEyePosition(partial);
		Vec3 look = p.getViewVector(partial);
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		right = right.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : right.normalize();
		Vec3 up = right.cross(look).normalize();
		Random rnd = new Random(p.getId() * 13L + p.level().getGameTime() / 2);
		for (int i = 0; i < 4; i++) {
			float a = rnd.nextFloat() * Mth.TWO_PI;
			double r = 1.5 + rnd.nextDouble();
			Vec3 side = right.scale(Mth.cos(a) * r).add(up.scale(Mth.sin(a) * r * 0.7));
			Vec3 from = eye.add(look.scale(3.0 + rnd.nextDouble() * 2.0)).add(side);
			Vec3 to = eye.subtract(look.scale(0.5)).add(side.scale(0.9));
			BrainstormFx.bolt(pose, buffers, from, to, rnd.nextLong(), 0.022f, 0.55f, BuzzshockKit.COLOUR);
		}
		Vec3 fist = BrainstormFx.hand(p, partial).add(look.scale(0.5));
		BrainstormFx.bolt(pose, buffers, fist, fist.add(look.scale(1.2)).add(right.scale(rnd.nextDouble() - 0.5)), rnd.nextLong(), 0.02f, 0.7f, BuzzshockKit.COLOUR);
	}

	private static float fade(AlienProjectileEntity e, float partial, float life) {
		return Mth.clamp(1f - (e.tickCount + partial) / life, 0f, 1f);
	}

	/** A bolt ending here; colour 0 = from the owner's hand, n = from entity n - 1. */
	private static void arc(AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers, boolean big) {
		Minecraft mc = Minecraft.getInstance();
		Vec3 end = e.getPosition(partial);
		Vec3 start = null;
		int c = e.colour();
		if (c > 0) {
			Entity from = mc.level.getEntity(c - 1);
			if (from != null) {
				start = from.getPosition(partial).add(0, from.getBbHeight() * 0.55, 0);
			}
		} else if (e.getOwner() instanceof Player owner) {
			start = BrainstormFx.hand(owner, partial);
		}
		if (start == null) {
			return;
		}
		float k = 0.55f + 0.45f * fade(e, partial, big ? 12f : 7f);
		pose.pushPose();
		BrainstormFx.toCamera(pose, e, partial);
		long seed = e.getId() * 7919L + (e.tickCount / 2);
		BrainstormFx.bolt(pose, buffers, start, end, seed, big ? 0.26f : 0.11f, k, BuzzshockKit.COLOUR);
		BrainstormFx.bolt(pose, buffers, start, end, seed + 5, big ? 0.12f : 0.04f, k * 0.7f, 0xC8FF80);
		pose.popPose();
	}

	/** The Spark Bomb: a churning green-white orb with little arcs licking off it. */
	private static void ball(AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers) {
		float t = e.tickCount + partial;
		float r = e.size() * 0.6f;
		pose.pushPose();
		pose.translate(0, e.getBbHeight() * 0.5, 0);
		VertexConsumer vc = PyroDraw.additive(buffers);
		PyroDraw.sphere(pose, vc, r, 0xFFF0FFD0, 0xFF3C8010, 0.12f, t, PyroDraw.FULL);
		PyroDraw.sphere(pose, vc, r * 1.7f, 0xFF204010, 0xFF081004, 0.05f, t * 0.5f, PyroDraw.FULL);
		pose.popPose();
		pose.pushPose();
		BrainstormFx.toCamera(pose, e, partial);
		Vec3 c = e.getPosition(partial).add(0, e.getBbHeight() * 0.5, 0);
		Random rnd = new Random(e.getId() * 17L + e.tickCount / 2);
		for (int i = 0; i < 4; i++) {
			Vec3 d = new Vec3(rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.5).normalize().scale(r * 2.4);
			BrainstormFx.bolt(pose, buffers, c, c.add(d), rnd.nextLong(), 0.035f, 0.85f, BuzzshockKit.COLOUR);
		}
		pose.popPose();
	}
}
