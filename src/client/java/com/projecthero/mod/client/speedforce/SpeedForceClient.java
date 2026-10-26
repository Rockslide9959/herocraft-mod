package com.projecthero.mod.client.speedforce;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.client.mutation.v0145.GhostArmour;
import com.projecthero.mod.client.thor.ThorDraw;
import com.projecthero.mod.speedforce.SpeedForceSpeedster;
import com.projecthero.mod.speedforce.network.SpeedForceFxPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: the Speed Force Speedster's client side.
 * <ul>
 *   <li>the HUD ({@link SpeedForceHud}) and the skate run ({@link SpeedForcePose});</li>
 *   <li>after-images: plain see-through copies of the runner (no tint), left every tick while running -- with extra copies
 *       in between at speed, so a 250 b/s run still reads as a continuous blur -- each in the skate pose it was left in;</li>
 *   <li>gold Speed Force lightning crackling off the body all the time, growing with the momentum (and blinding in a
 *       Surge), bolts strung between the after-images from 5% momentum, and (above 10%) arcs leaping from the body to
 *       the blocks around (v0.17.1, user);</li>
 *   <li>{@link SpeedForceFxPayload}: the Lightning Throw's gold bolts, the sonic boom ring, after-image streaks (Step,
 *       Rewind, the Remnant, the Tornado), the awakening's column of lightning and the Surge's flash.</li>
 * </ul>
 */
public final class SpeedForceClient {
	/** v0.17.1 (user: "make the lightning trail longer"): copies last 1.5 s (was 0.8 s). */
	private static final int TRAIL_LIFE = 30;
	/** v0.17.1 (user): the trail and its lightning show from 5% momentum; arcs to nearby blocks above 10%. */
	private static final float TRAIL_MOMENTUM = 0.05f;
	private static final float GROUND_ARC_MOMENTUM = 0.10f;
	private static final float TRAIL_ALPHA = 0.38f;
	private static final double FILL_SPACING = 1.6;
	private static final int MAX_FILL = 10;
	private static final int PLAIN = 0xFFFFFF;

	private record Snapshot(long time, int life, float alpha, double x, double y, double z, float bodyYaw, float headYaw, float pitch,
			float limbPos, float limbSpeed, float weight, boolean bolts, boolean fixed) {
	}

	private record Bolt(Vec3 from, Vec3 to, long start, int life, double seed, int colour) {
	}

	private record Ring(Vec3 at, long start, int life, float radius) {
	}

	/**
	 * v0.17.1 (user: "make it kinda linger ... stick to the player and the blocks so that it looks more like a trail ... last
	 * twice as long"): one bolt from a point on the runner's body (kept as an offset, so it rides along with them) to the
	 * block surface it struck (fixed in the world), crackling for {@code life} ticks as it stretches out behind.
	 */
	private record GroundArc(int playerId, Vec3 bodyOffset, Vec3 hit, long start, int life, double seed, float strength, int colour) {
	}

	/** Ticks a ground arc lasts (was a re-pick every ~3: now twice as long, and each one stays put). */
	private static final int GROUND_ARC_LIFE = 8;
	private static final List<GroundArc> GROUND_ARCS = new ArrayList<>();

	private static final Map<UUID, Deque<Snapshot>> TRAILS = new HashMap<>();
	private static final Map<UUID, double[]> LAST_RECORDED = new HashMap<>();
	private static final List<Bolt> BOLTS = new ArrayList<>();
	private static final List<Ring> RINGS = new ArrayList<>();

	private SpeedForceClient() {
	}

	public static void initialize() {
		HudRenderCallback.EVENT.register(SpeedForceHud::render);
		ClientTickEvents.END_CLIENT_TICK.register(SpeedForceHud::tick);
		ClientTickEvents.END_CLIENT_TICK.register(SpeedForcePose::tick);
		ClientTickEvents.END_CLIENT_TICK.register(SpeedForceClient::tick);
		WorldRenderEvents.AFTER_ENTITIES.register(SpeedForceClient::render);
		ClientPlayNetworking.registerGlobalReceiver(SpeedForceFxPayload.TYPE,
				(payload, context) -> context.client().execute(() -> accept(payload)));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
	}

	private static void reset() {
		TRAILS.clear();
		LAST_RECORDED.clear();
		BOLTS.clear();
		RINGS.clear();
		GROUND_ARCS.clear();
	}

	// ---------------------------------------------------------------- the trail

	private static void tick(Minecraft mc) {
		if (mc.level == null) {
			reset();
			return;
		}
		long now = mc.level.getGameTime();
		java.util.Set<UUID> present = new java.util.HashSet<>();
		for (AbstractClientPlayer p : mc.level.players()) {
			present.add(p.getUUID());
			if (!SpeedForceSpeedster.speedActive(p) || p.isSpectator() || p.isInvisible()) {
				LAST_RECORDED.remove(p.getUUID());
				continue;
			}
			double dx = p.getX() - p.xo;
			double dy = p.getY() - p.yo;
			double dz = p.getZ() - p.zo;
			if (dx * dx + dz * dz < 0.12 * 0.12 && Math.abs(dy) < 0.25) {
				LAST_RECORDED.remove(p.getUUID());
				continue;
			}
			float momentum = SpeedForceSpeedster.momentum(p);
			if (momentum < TRAIL_MOMENTUM && !SpeedForceSpeedster.surging(p)) {
				LAST_RECORDED.remove(p.getUUID());
				continue;
			}
			float weight = SpeedForcePose.weight(p, 0f);
			boolean bolts = true;
			Deque<Snapshot> q = TRAILS.computeIfAbsent(p.getUUID(), k -> new ArrayDeque<>());
			// a copy where the player WAS last tick (never ahead of the interpolated body), and at speed copies in between
			double[] last = LAST_RECORDED.get(p.getUUID());
			float limb = p.walkAnimation.position(0f);
			float limbSpeed = p.walkAnimation.speed(0f);
			int n = 1;
			if (last != null) {
				double gap = Math.sqrt(sq(p.xo - last[0]) + sq(p.yo - last[1]) + sq(p.zo - last[2]));
				n = (int) Math.max(1, Math.min(MAX_FILL, Math.ceil(gap / FILL_SPACING)));
			}
			for (int i = n; i >= 1; i--) {
				double t = last == null ? 1.0 : 1.0 - (i - 1) / (double) n;
				double x = last == null ? p.xo : last[0] + (p.xo - last[0]) * t;
				double y = last == null ? p.yo : last[1] + (p.yo - last[1]) * t;
				double z = last == null ? p.zo : last[2] + (p.zo - last[2]) * t;
				float l = last == null ? limb : (float) (last[3] + (limb - last[3]) * t);
				q.addLast(new Snapshot(now, TRAIL_LIFE, TRAIL_ALPHA, x, y, z, p.yBodyRotO, p.yHeadRotO, p.xRotO, l, limbSpeed, weight, bolts, false));
			}
			LAST_RECORDED.put(p.getUUID(), new double[] { p.xo, p.yo, p.zo, limb });
			spawnGroundArcs(p, now);
		}
		for (Iterator<Map.Entry<UUID, Deque<Snapshot>>> it = TRAILS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Deque<Snapshot>> e = it.next();
			Deque<Snapshot> q = e.getValue();
			q.removeIf(s -> now - s.time() > s.life());
			if (q.isEmpty() || !present.contains(e.getKey())) {
				it.remove();
			}
		}
		BOLTS.removeIf(b -> now - b.start() > b.life());
		GROUND_ARCS.removeIf(a -> now - a.start() > a.life() || !(mc.level.getEntity(a.playerId()) instanceof Player));
		RINGS.removeIf(r -> now - r.start() > r.life());
	}

	private static double sq(double d) {
		return d * d;
	}

	private static void accept(SpeedForceFxPayload msg) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		long now = mc.level.getGameTime();
		Vec3 from = new Vec3(msg.fx(), msg.fy(), msg.fz());
		Vec3 to = new Vec3(msg.tx(), msg.ty(), msg.tz());
		// v0.17.1 (user): the runner's chosen lightning colour
		int col = mc.level.getEntity(msg.playerId()) instanceof Player owner ? com.projecthero.mod.speedforce.SpeedForceColours.of(owner) : 0;
		switch (msg.kind()) {
			case SpeedForceFxPayload.BOLT -> {
				for (int k = 0; k < 2; k++) {
					BOLTS.add(new Bolt(from, to, now, 8 + k * 2, now * 1.7 + k * 13.1 + from.x, col));
				}
			}
			case SpeedForceFxPayload.BOOM -> RINGS.add(new Ring(from, now, 12, 7f));
			case SpeedForceFxPayload.SURGE -> {
				RINGS.add(new Ring(from.subtract(0, 0.9, 0), now, 14, 5f));
				for (int k = 0; k < 8; k++) {
					double a = k * Math.PI / 4;
					BOLTS.add(new Bolt(from, from.add(Math.cos(a) * 2.5, (k % 3 - 1) * 0.8, Math.sin(a) * 2.5), now, 10, k * 7.3 + now, col));
				}
			}
			case SpeedForceFxPayload.AWAKEN -> {
				for (int k = 0; k < 4; k++) {
					Vec3 top = to.add((k - 1.5) * 1.5, 0, (k % 2 - 0.5) * 2.0);
					BOLTS.add(new Bolt(top, from, now + k * 3, 12, k * 31.7 + now, col));
				}
				RINGS.add(new Ring(from.subtract(0, 0.9, 0), now, 20, 6f));
			}
			case SpeedForceFxPayload.STREAK -> {
				if (!(mc.level.getEntity(msg.playerId()) instanceof AbstractClientPlayer p)) {
					return;
				}
				Deque<Snapshot> q = TRAILS.computeIfAbsent(p.getUUID(), k -> new ArrayDeque<>());
				double len = from.distanceTo(to);
				int n = (int) Math.max(2, Math.min(40, Math.round(len / 0.8)));
				Vec3 d = to.subtract(from);
				float yaw = d.horizontalDistanceSqr() > 1.0e-4 ? (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0) : p.getYRot();
				float limb = p.walkAnimation.position(0f);
				for (int i = 0; i < n; i++) {
					double t = i / (double) (n - 1);
					Vec3 at = from.lerp(to, t);
					int life = Math.max(3, Math.round(14 * (0.45f + 0.55f * (float) t)));
					q.addLast(new Snapshot(now, life, 0.42f, at.x, at.y, at.z, yaw, yaw, 0f, limb + i * 1.1f, 1.0f, 1.0f, false, true));
				}
			}
			default -> {
			}
		}
	}

	// ---------------------------------------------------------------- rendering

	private static void render(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		PoseStack pose = context.matrixStack();
		MultiBufferSource buffers = context.consumers();
		if (mc.level == null || pose == null || buffers == null) {
			return;
		}
		Vec3 cam = context.camera().getPosition();
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		long now = mc.level.getGameTime();
		boolean firstPerson = mc.options.getCameraType().isFirstPerson();
		List<Vec3[]> arcs = new ArrayList<>();
		List<Float> arcAlpha = new ArrayList<>();
		List<Float> arcWidth = new ArrayList<>();
		List<Integer> arcColour = new ArrayList<>();

		for (AbstractClientPlayer acp : mc.level.players()) {
			if (!SpeedForceSpeedster.speedActive(acp) || acp.isSpectator() || acp.isInvisible()) {
				continue;
			}
			boolean own = acp == mc.player;
			if (own && firstPerson) {
				continue;
			}
			bodyLightning(acp, cam, partial, now, arcs, arcAlpha, arcWidth);
			int bodyCol = com.projecthero.mod.speedforce.SpeedForceColours.of(acp);
			while (arcColour.size() < arcs.size()) {
				arcColour.add(bodyCol);
			}
		}

		for (Map.Entry<UUID, Deque<Snapshot>> e : TRAILS.entrySet()) {
			Player player = mc.level.getPlayerByUUID(e.getKey());
			if (!(player instanceof AbstractClientPlayer acp)) {
				continue;
			}
			EntityRenderer<? super AbstractClientPlayer> r = mc.getEntityRenderDispatcher().getRenderer(acp);
			if (!(r instanceof PlayerRenderer pr)) {
				continue;
			}
			PlayerModel<AbstractClientPlayer> model = pr.getModel();
			ResourceLocation skin = acp.getSkin().texture();
			boolean own = player == mc.player;
			boolean wearsArmour = GhostArmour.wearsAny(acp);
			Vec3 at = acp.getPosition(partial);
			Vec3 heading = at.subtract(acp.xo, acp.yo, acp.zo);
			Snapshot prev = null;
			for (Snapshot s : e.getValue()) {
				if (!s.fixed()) {
					Vec3 off = new Vec3(s.x(), s.y(), s.z()).subtract(at);
					if (off.lengthSqr() < 0.36 || (heading.lengthSqr() > 1.0e-4 && off.dot(heading) > 0.0)) {
						continue;
					}
				}
				float age = (now - s.time()) + partial;
				float fade = 1.0f - age / s.life();
				if (fade <= 0.0f) {
					continue;
				}
				// never a copy right over the camera (third-person-back runs straight through its own trail)
				if (cam.distanceToSqr(s.x(), s.y() + 1.0, s.z()) < (own && firstPerson ? 2.25 : 4.0)) {
					continue;
				}
				// gold bolts strung between consecutive copies (only ever behind the runner)
				if (s.bolts() && !s.fixed()) {
					if (prev != null && Math.abs(s.time() - prev.time()) <= 2
							&& new Vec3(s.x() - prev.x(), s.y() - prev.y(), s.z() - prev.z()).lengthSqr() < 25.0) {
						double frame = Math.floor((now + partial) * 3.0);
						double seed = frame * 3.7 + s.time() * 0.91 + s.x() * 0.37;
						if (ThorDraw.hash(seed) > 0.35) {
							Vec3 a = new Vec3(prev.x(), prev.y() + 0.25 + ThorDraw.hash(seed + 1) * 1.45, prev.z()).subtract(cam);
							Vec3 b = new Vec3(s.x(), s.y() + 0.25 + ThorDraw.hash(seed + 2) * 1.45, s.z()).subtract(cam);
							arcs.add(ThorDraw.jagged(a, b, 6, 0.24, seed));
							arcAlpha.add(Math.min(1.0f, fade * 1.2f));
							arcWidth.add(0.9f);
							arcColour.add(com.projecthero.mod.speedforce.SpeedForceColours.of(acp));
						}
					}
					prev = s;
				}
				int alpha = Math.round(Math.min(1.0f, fade) * s.alpha() * 255.0f);
				int color = (alpha << 24) | PLAIN;
				pose.pushPose();
				pose.translate(s.x() - cam.x, s.y() - cam.y, s.z() - cam.z);
				pose.mulPose(Axis.YP.rotationDegrees(180.0f - s.bodyYaw()));
				pose.scale(-1.0f, -1.0f, 1.0f);
				pose.scale(0.9375f, 0.9375f, 0.9375f);
				pose.translate(0.0f, -1.501f, 0.0f);
				model.attackTime = 0.0f;
				model.riding = false;
				model.young = false;
				model.crouching = false;
				SpeedForcePose.overrideWeight = s.weight();
				try {
					model.setupAnim(acp, s.limbPos(), s.limbSpeed(), acp.tickCount + partial, s.headYaw() - s.bodyYaw(), s.pitch());
				} finally {
					SpeedForcePose.overrideWeight = Float.NaN;
				}
				model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(skin)), LightTexture.FULL_BRIGHT,
						OverlayTexture.NO_OVERLAY, color);
				if (wearsArmour) {
					SpeedForcePose.overrideWeight = s.weight();
					try {
						GhostArmour.render(pr, acp, pose, buffers, color, s.limbPos(), s.limbSpeed(), acp.tickCount + partial,
								s.headYaw() - s.bodyYaw(), s.pitch(), partial);
					} finally {
						SpeedForcePose.overrideWeight = Float.NaN;
					}
				}
				pose.popPose();
			}
		}

		// the Lightning Throw / awakening bolts
		// the lingering arcs to the blocks round the runners
		for (GroundArc g : GROUND_ARCS) {
			if (!(mc.level.getEntity(g.playerId()) instanceof Player owner)) {
				continue;
			}
			float age = (now - g.start()) + partial;
			float fade = 1.0f - age / g.life();
			if (fade <= 0f || age < 0f) {
				continue;
			}
			Vec3 from = owner.getPosition(partial).add(g.bodyOffset());
			if (owner == mc.player && firstPerson && from.distanceToSqr(cam) < 1.0) {
				from = from.add(owner.getLookAngle().scale(0.6)); // never a bolt straight through your own eyes
			}
			double frame = Math.floor((now + partial) * 10.0);
			int nodes = (int) Math.max(4, Math.min(16, from.distanceTo(g.hit()) * 2.0));
			arcs.add(ThorDraw.jagged(from.subtract(cam), g.hit().subtract(cam), nodes, 0.2 + 0.1 * g.strength(), g.seed() + frame * 0.37));
			arcAlpha.add(Math.min(1f, fade * 1.5f) * (0.8f + 0.2f * g.strength()));
			arcWidth.add(0.6f + 0.4f * g.strength());
			arcColour.add(g.colour());
			// a little fork where it bit
			if (ThorDraw.hash(g.seed() + 8) > 0.45) {
				Vec3 tip = g.hit().add((ThorDraw.hash(g.seed() + frame + 9) - 0.5) * 0.9, 0.05 + ThorDraw.hash(g.seed() + frame + 10) * 0.3,
						(ThorDraw.hash(g.seed() + frame + 11) - 0.5) * 0.9);
				arcs.add(ThorDraw.jagged(g.hit().subtract(cam), tip.subtract(cam), 3, 0.12, g.seed() + frame + 12));
				arcAlpha.add(fade * 0.7f);
				arcWidth.add(0.4f);
				arcColour.add(g.colour());
			}
		}

		for (Bolt b : BOLTS) {
			float age = (now - b.start()) + partial;
			if (age < 0f) {
				continue;
			}
			float fade = 1.0f - age / b.life();
			if (fade <= 0f) {
				continue;
			}
			double frame = Math.floor((now + partial) * 4.0);
			int nodes = (int) Math.max(4, Math.min(24, b.from().distanceTo(b.to()) * 1.2));
			Vec3[] path = ThorDraw.jagged(b.from().subtract(cam), b.to().subtract(cam), nodes, 0.35, b.seed() + frame * 1.3);
			arcs.add(path);
			arcAlpha.add(Math.min(1f, fade * 1.4f));
			arcWidth.add(1.6f);
			arcColour.add(b.colour());
			if (ThorDraw.hash(b.seed() + frame) > 0.3) {
				Vec3 root = path[path.length / 2];
				Vec3 tip = root.add((ThorDraw.hash(b.seed() + frame + 1) - 0.5) * 2.0, (ThorDraw.hash(b.seed() + frame + 2) - 0.5) * 1.5,
						(ThorDraw.hash(b.seed() + frame + 3) - 0.5) * 2.0);
				arcs.add(ThorDraw.jagged(root, tip, 4, 0.2, b.seed() + frame + 4));
				arcAlpha.add(fade);
				arcWidth.add(0.8f);
				arcColour.add(b.colour());
			}
		}

		VertexConsumer vc = ThorDraw.buffer(buffers);
		PoseStack.Pose last = pose.last();
		for (Ring ring : RINGS) {
			float age = (now - ring.start()) + partial;
			float t = age / ring.life();
			if (t >= 1f || t < 0f) {
				continue;
			}
			float r0 = 0.5f + ring.radius() * t;
			Vec3 c = ring.at().subtract(cam);
			ThorDraw.ring(vc, last, c, r0 * 0.75f, r0 + 0.6f, 0xFFE8A0, 0.8f * (1f - t), 48);
			ThorDraw.ring(vc, last, c.add(0, 0.6, 0), r0 * 0.85f, r0 + 0.3f, 0xFFFFFF, 0.5f * (1f - t), 48);
		}
		for (int i = 0; i < arcs.size(); i++) {
			Vec3[] path = arcs.get(i);
			float a = arcAlpha.get(i);
			float w = arcWidth.get(i);
			// gold Speed Force lightning: a soft orange glow, a gold body and a white-hot core
			int c = i < arcColour.size() ? arcColour.get(i) : 0;
			ThorDraw.ribbon(vc, last, path, 0.24f * w, com.projecthero.mod.speedforce.SpeedForceColours.tone(c, 0), 0.16f * a);
			ThorDraw.ribbon(vc, last, path, 0.11f * w, com.projecthero.mod.speedforce.SpeedForceColours.tone(c, 1), 0.4f * a);
			ThorDraw.ribbon(vc, last, path, 0.045f * w, com.projecthero.mod.speedforce.SpeedForceColours.tone(c, 2), 0.8f * a);
			ThorDraw.ribbon(vc, last, path, 0.016f * w, com.projecthero.mod.speedforce.SpeedForceColours.tone(c, 3), 1.0f * a);
		}
	}

	/**
	 * v0.17.1 (user: "make the lightning arc off the player and onto nearby blocks when running at above 10% momentum"):
	 * bolts leap from the runner's body to the ground and walls around them -- each aims at a random spot within a few
	 * blocks and strikes the first solid block surface it meets, re-picked several times a second. More, longer and
	 * further-reaching as the momentum climbs; a few stray ones standing still.
	 */
	private static void spawnGroundArcs(AbstractClientPlayer p, long now) {
		float m = SpeedForceSpeedster.momentum(p);
		if (m <= GROUND_ARC_MOMENTUM && !SpeedForceSpeedster.surging(p)) {
			return;
		}
		Vec3 at = p.position();
		boolean moving = at.subtract(p.xo, p.yo, p.zo).lengthSqr() > 0.01;
		float strength = Math.min(1f, (m - GROUND_ARC_MOMENTUM) / 0.6f);
		// new arcs a tick: about one in three ticks at 10%, one or two a tick at full speed
		double want = (moving ? 0.35 : 0.1) + strength * 1.4;
		int count = (int) want + (ThorDraw.hash(now * 1.7 + p.getId()) < want - (int) want ? 1 : 0);
		double reach = 2.0 + strength * 3.0;
		int colour = com.projecthero.mod.speedforce.SpeedForceColours.of(p);
		for (int k = 0; k < count; k++) {
			double seed = now * 7.1 + k * 19.3 + p.getId() * 0.71 + 500.0;
			Vec3 off = new Vec3((ThorDraw.hash(seed + 1) - 0.5) * 0.5, 0.3 + ThorDraw.hash(seed + 2) * 1.2, (ThorDraw.hash(seed + 3) - 0.5) * 0.5);
			Vec3 from = at.add(off);
			// mostly down and out (the ground round the feet), now and then sideways into a wall
			double a = ThorDraw.hash(seed + 4) * Math.PI * 2.0;
			double out = reach * (0.5 + ThorDraw.hash(seed + 5) * 0.5);
			double down = ThorDraw.hash(seed + 6) < 0.8 ? -(1.5 + ThorDraw.hash(seed + 7) * 2.0) : (ThorDraw.hash(seed + 7) - 0.3) * 1.5;
			Vec3 aim = from.add(Math.cos(a) * out, down, Math.sin(a) * out);
			net.minecraft.world.phys.BlockHitResult hit = p.level().clip(new net.minecraft.world.level.ClipContext(from, aim,
					net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, p));
			if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
				continue;
			}
			Vec3 to = hit.getLocation();
			GROUND_ARCS.add(new GroundArc(p.getId(), off, to, now, GROUND_ARC_LIFE + (int) (ThorDraw.hash(seed + 8) * 3), seed, strength, colour));
			if (ThorDraw.hash(seed + 13) > 0.6) {
				p.level().addParticle(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, to.x, to.y + 0.05, to.z,
						(ThorDraw.hash(seed + 14) - 0.5) * 0.2, 0.1, (ThorDraw.hash(seed + 15) - 0.5) * 0.2);
			}
		}
	}

	/**
	 * Gold lightning off the body, re-shaped several times a tick: a stray crackle standing still, more and longer bolts as
	 * the momentum grows (trailing back off a runner), and a storm in a Surge.
	 */
	private static void bodyLightning(AbstractClientPlayer p, Vec3 cam, float partial, long now, List<Vec3[]> arcs, List<Float> alpha,
			List<Float> width) {
		float m = SpeedForceSpeedster.momentum(p);
		boolean surge = SpeedForceSpeedster.surging(p);
		Vec3 at = p.getPosition(partial);
		Vec3 heading = at.subtract(p.xo, p.yo, p.zo);
		boolean moving = heading.lengthSqr() > 0.01;
		int count = surge ? 10 : 1 + Math.round(m * 6f);
		double length = surge ? 1.8 : 0.35 + m * 1.4;
		double frame = Math.floor((now + partial) * 4.0);
		for (int k = 0; k < count; k++) {
			double seed = frame * 5.3 + k * 31.7 + p.getId() * 0.37;
			// standing still with little momentum, only an occasional flicker
			double keep = surge ? 0.1 : moving ? 0.25 : 0.75 - m * 0.4;
			if (ThorDraw.hash(seed) < keep) {
				continue;
			}
			Vec3 from = at.add((ThorDraw.hash(seed + 1) - 0.5) * 0.55, 0.2 + ThorDraw.hash(seed + 2) * 1.5, (ThorDraw.hash(seed + 3) - 0.5) * 0.55);
			Vec3 dir;
			if (moving && !surge) {
				dir = heading.normalize().scale(-(0.4 + ThorDraw.hash(seed + 4)) * length)
						.add((ThorDraw.hash(seed + 5) - 0.5) * length, (ThorDraw.hash(seed + 6) - 0.5) * length * 0.8, (ThorDraw.hash(seed + 7) - 0.5) * length);
			} else {
				dir = new Vec3(ThorDraw.hash(seed + 5) - 0.5, ThorDraw.hash(seed + 6) - 0.45, ThorDraw.hash(seed + 7) - 0.5).normalize().scale(length);
			}
			Vec3[] path = ThorDraw.jagged(from.subtract(cam), from.add(dir).subtract(cam), 5, 0.12 + 0.08 * m, seed);
			arcs.add(path);
			alpha.add(surge ? 1f : 0.75f + 0.25f * m);
			width.add(surge ? 1.1f : 0.55f + 0.5f * m);
		}
	}
}
