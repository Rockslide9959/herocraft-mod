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
import net.minecraft.util.Mth;
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
	/** v0.17.1 (user): the trail's lightning shows from 5% momentum (the copies themselves at any); arcs to nearby blocks above 10%. */
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

	private record Ring(Vec3 at, long start, int life, float radius, boolean dark) {
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
		ClientTickEvents.END_CLIENT_TICK.register(SpeedForceSprintKey::tick); // v0.17.1 (user): Ctrl held, Toggle Sprint or not
		WorldRenderEvents.AFTER_ENTITIES.register(SpeedForceClient::render);
		ClientPlayNetworking.registerGlobalReceiver(SpeedForceFxPayload.TYPE,
				(payload, context) -> context.client().execute(() -> accept(payload)));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
		// v0.17.1: the memory road's doorways and echoes, and the Black Flash
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
				com.projecthero.mod.speedforce.entity.SpeedForceEntityTypes.DOORWAY, SpeedForceDoorwayRenderer::new);
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
				com.projecthero.mod.speedforce.entity.SpeedForceEntityTypes.ECHO, SpeedForceEchoRenderer::new);
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
				com.projecthero.mod.speedforce.entity.SpeedForceEntityTypes.BLACK_FLASH, BlackFlashRenderer::new);
	}

	private static void reset() {
		TRAILS.clear();
		LAST_RECORDED.clear();
		BOLTS.clear();
		RINGS.clear();
		GROUND_ARCS.clear();
		PORTALS.clear();
		BlackFlashRenderer.reset();
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
			// v0.17.1 (user: "the afterimage trail should still show up if player is using their speed mode, i still want some
			// visual indicator"): the see-through copies whenever Speed Mode is on and they are moving, at ANY momentum; the
			// lightning strung between them from 5% (and the arcs to the ground from 10%)
			float momentum = SpeedForceSpeedster.momentum(p);
			float weight = SpeedForcePose.weight(p, 0f);
			boolean bolts = momentum >= TRAIL_MOMENTUM || SpeedForceSpeedster.surging(p);
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
		int black = BLACK_FLASH_COLOUR;
		switch (msg.kind()) {
			case SpeedForceFxPayload.BF_BOLT -> {
				for (int k = 0; k < 2; k++) {
					BOLTS.add(new Bolt(from, to, now, 8 + k * 3, now * 1.3 + k * 11.7 + from.z, black));
				}
			}
			case SpeedForceFxPayload.BF_RING -> RINGS.add(new Ring(from.subtract(0, 0.9, 0), now, 14, (float) Math.max(2.0, msg.tx()), true));
			case SpeedForceFxPayload.BF_PUNCH -> {
				RINGS.add(new Ring(from.subtract(0, 0.9, 0), now, 20, 9f, true));
				RINGS.add(new Ring(from.subtract(0, 0.5, 0), now + 3, 16, 6f, true));
				for (int k = 0; k < 12; k++) {
					double a = k * Math.PI / 6;
					BOLTS.add(new Bolt(from, from.add(Math.cos(a) * 4.5, Math.sin(a * 1.3) * 2.5, Math.sin(a) * 4.5), now + (k % 3), 14, k * 7.7 + now, black));
				}
				BOLTS.add(new Bolt(to.add(0, 20, 0), to, now, 12, now * 0.7, black));
			}
			case SpeedForceFxPayload.BF_DISSOLVE -> {
				for (int k = 0; k < 6; k++) {
					double a = k * Math.PI / 3 + now * 0.3;
					BOLTS.add(new Bolt(from.add(Math.cos(a) * 0.4, (k % 3) * 0.4 - 0.4, Math.sin(a) * 0.4),
							to.add(Math.cos(a) * 1.5, k * 0.5, Math.sin(a) * 1.5), now + k, 14, k * 13.1 + now, black));
				}
			}
			case SpeedForceFxPayload.BF_STREAK -> {
				BOLTS.add(new Bolt(from.add(0, 1.0, 0), to.add(0, 1.0, 0), now, 8, now * 1.9 + from.x, black));
				BOLTS.add(new Bolt(from.add(0, 0.4, 0), to.add(0, 1.5, 0), now, 6, now * 2.3 + from.z, black));
			}
			case SpeedForceFxPayload.BOLT -> {
				for (int k = 0; k < 2; k++) {
					BOLTS.add(new Bolt(from, to, now, 8 + k * 2, now * 1.7 + k * 13.1 + from.x, col));
				}
			}
			case SpeedForceFxPayload.BOOM -> RINGS.add(new Ring(from, now, 12, 7f, false));
			case SpeedForceFxPayload.SURGE -> {
				RINGS.add(new Ring(from.subtract(0, 0.9, 0), now, 14, 5f, false));
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
				RINGS.add(new Ring(from.subtract(0, 0.9, 0), now, 20, 6f, false));
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
			case SpeedForceFxPayload.PORTAL, SpeedForceFxPayload.PORTAL_COLLAPSE -> {
				// drawn from the runner's synced charge instead (see render): the slowed world flushes packets in bursts
			}
			case SpeedForceFxPayload.PORTAL_ENTER -> {
				PORTALS.remove(msg.playerId());
				RINGS.add(new Ring(from.subtract(0, 1.1, 0), now, 14, 6f, false));
				for (int k = 0; k < 10; k++) {
					double a = k * Math.PI / 5;
					BOLTS.add(new Bolt(from, from.add(Math.cos(a) * 3.5, Math.sin(a) * 3.0, Math.sin(a * 1.7) * 2.0), now, 10, k * 5.1 + now, col));
				}
			}
			case SpeedForceFxPayload.REALM_EXIT -> {
				for (int k = 0; k < 3; k++) {
					BOLTS.add(new Bolt(to.add((k - 1) * 1.2, 0, (k % 2) * 0.8), from, now + k * 2, 14, k * 17.3 + now, col));
				}
				RINGS.add(new Ring(from.subtract(0, 0.9, 0), now, 18, 7f, false));
			}
			default -> {
			}
		}
	}

	// ---------------------------------------------------------------- the Speed Force portal, Tornado Arms, the realm's sky

	/**
	 * A portal forming in front of a runner: drawn from their synced charge ({@code SpeedForceAbilities#portalCharge}) at their
	 * own interpolated position (Flash Time's slowed world sends its packets in bursts, so nothing sent per tick would keep up);
	 * once the charge is gone it collapses over {@link #PORTAL_COLLAPSE_MS} from where it last was.
	 */
	private record Portal(float progress, long updatedMs) {
	}

	private static final Map<Integer, Portal> PORTALS = new HashMap<>();
	private static final long PORTAL_COLLAPSE_MS = 350L;

	/**
	 * v0.17.1 (user: "as the player charges up this power the portal should start forming in front of them"): a swirling
	 * lightning ring in the plane facing the run -- it widens with {@code progress}, its colour's lightning racing round it,
	 * spiral arms winding in to the middle and arcs crackling off the rim. {@code shrink} 1 = whole, 0 = gone.
	 */
	private static void drawPortal(Vec3 centre, float progress, float yaw, int colour, double time, float shrink, Vec3 cam,
			List<Vec3[]> arcs, List<Float> alpha, List<Float> width, List<Integer> colours) {
		float t = Mth.clamp(progress, 0f, 1f);
		double r = (0.25 + com.projecthero.mod.speedforce.SpeedForceConfig.PORTAL_RADIUS * t) * shrink;
		if (r < 0.05) {
			return;
		}
		double yr = Math.toRadians(yaw);
		Vec3 side = new Vec3(Math.cos(yr), 0, Math.sin(yr)); // across the run
		Vec3 up = new Vec3(0, 1, 0);
		int segs = 20;
		double spin = time * (0.25 + 0.35 * t);
		double frame = Math.floor(time * 3.0);
		Vec3 prev = null;
		Vec3 first = null;
		for (int i = 0; i <= segs; i++) {
			double a = spin + i * Math.PI * 2 / segs;
			Vec3 pt = centre.add(side.scale(Math.cos(a) * r)).add(up.scale(Math.sin(a) * r));
			if (prev != null) {
				arcs.add(ThorDraw.jagged(prev.subtract(cam), pt.subtract(cam), 3, 0.08 + 0.1 * t, frame * 1.3 + i * 7.7));
				alpha.add((0.55f + 0.45f * t) * shrink);
				width.add(0.9f + 1.1f * t);
				colours.add(colour);
			} else {
				first = pt;
			}
			prev = pt;
		}
		// the swirl: arms winding in to the middle
		int armsN = 3 + Math.round(t * 2);
		for (int k = 0; k < armsN; k++) {
			Vec3[] path = new Vec3[9];
			for (int j = 0; j < path.length; j++) {
				double f = j / (double) (path.length - 1);
				double a = -spin * 1.6 + k * Math.PI * 2 / armsN + f * 2.4;
				double rr = r * (1.0 - f * 0.92);
				path[j] = centre.add(side.scale(Math.cos(a) * rr)).add(up.scale(Math.sin(a) * rr)).subtract(cam);
			}
			arcs.add(path);
			alpha.add((0.3f + 0.5f * t) * shrink);
			width.add(0.5f + 0.6f * t);
			colours.add(colour);
		}
		// crackling arcs off the rim
		int cracks = 1 + Math.round(t * 5);
		for (int k = 0; k < cracks; k++) {
			double seed = frame * 4.1 + k * 13.7 + yaw;
			if (ThorDraw.hash(seed) < 0.35) {
				continue;
			}
			double a = ThorDraw.hash(seed + 1) * Math.PI * 2;
			Vec3 rim = centre.add(side.scale(Math.cos(a) * r)).add(up.scale(Math.sin(a) * r));
			Vec3 out = rim.add(side.scale(Math.cos(a) * (0.4 + t))).add(up.scale(Math.sin(a) * (0.4 + t)))
					.add((ThorDraw.hash(seed + 2) - 0.5) * 0.8, (ThorDraw.hash(seed + 3) - 0.5) * 0.8, (ThorDraw.hash(seed + 4) - 0.5) * 0.8);
			arcs.add(ThorDraw.jagged(rim.subtract(cam), out.subtract(cam), 4, 0.15, seed));
			alpha.add(0.8f * shrink);
			width.add(0.6f);
			colours.add(colour);
		}
	}

	/** The pale wind of Tornado Arms: spiralling strands down the cone in front, a few streaks of lightning in them. */
	private static void drawArmsWind(Player p, float partial, double time, Vec3 cam, List<Vec3[]> wind, List<Float> windAlpha,
			List<Vec3[]> arcs, List<Float> alpha, List<Float> width, List<Integer> colours) {
		Vec3 look = p.getViewVector(partial);
		Vec3 apex = p.getEyePosition(partial).subtract(0, 0.5, 0).add(look.scale(0.9));
		Vec3 side = new Vec3(-look.z, 0, look.x);
		side = side.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : side.normalize();
		Vec3 up = side.cross(look).normalize();
		double len = com.projecthero.mod.speedforce.SpeedForceConfig.ARMS_RANGE;
		for (int k = 0; k < 5; k++) {
			Vec3[] path = new Vec3[16];
			for (int j = 0; j < path.length; j++) {
				double f = j / (double) (path.length - 1);
				double s = 0.4 + f * (len - 0.4);
				double rad = 0.25 + s * 0.3;
				double a = time * 1.9 - f * 6.0 + k * Math.PI * 2 / 5;
				path[j] = apex.add(look.scale(s)).add(side.scale(Math.cos(a) * rad)).add(up.scale(Math.sin(a) * rad)).subtract(cam);
			}
			wind.add(path);
			windAlpha.add(0.22f);
		}
		int colour = com.projecthero.mod.speedforce.SpeedForceColours.of(p);
		double frame = Math.floor(time * 4.0);
		for (int k = 0; k < 2; k++) {
			double seed = frame * 3.3 + k * 9.1 + p.getId();
			double s0 = 1.0 + ThorDraw.hash(seed) * (len - 3.0);
			double a = ThorDraw.hash(seed + 1) * Math.PI * 2;
			double rad = 0.25 + s0 * 0.3;
			Vec3 a0 = apex.add(look.scale(s0)).add(side.scale(Math.cos(a) * rad)).add(up.scale(Math.sin(a) * rad));
			Vec3 a1 = apex.add(look.scale(s0 + 2.0)).add(side.scale(Math.cos(a + 1.2) * (rad + 0.6))).add(up.scale(Math.sin(a + 1.2) * (rad + 0.6)));
			arcs.add(ThorDraw.jagged(a0.subtract(cam), a1.subtract(cam), 5, 0.2, seed));
			alpha.add(0.7f);
			width.add(0.6f);
			colours.add(colour);
		}
	}

	/**
	 * v0.17.1: the Speed Force's sky -- a dark void with streams of lightning in the runner's colour racing past far off
	 * (along the road, the way you run), and great bolts flashing across it now and then. Anchored to the camera, so it
	 * reads as sky.
	 */
	private static void drawRealmSky(Vec3 cam, double time, int colour, List<Vec3[]> arcs, List<Float> alpha, List<Float> width,
			List<Integer> colours) {
		for (int k = 0; k < 18; k++) {
			double seed = k * 37.17 + 3.0;
			double ang = ThorDraw.hash(seed) * Math.PI * 2;
			double dist = 70 + ThorDraw.hash(seed + 1) * 60;
			double h = -25 + ThorDraw.hash(seed + 2) * 85;
			double speed = 18 + ThorDraw.hash(seed + 3) * 30;
			double length = 40 + ThorDraw.hash(seed + 4) * 60;
			double span = 320;
			// flowing back along the road (-X) and wrapping round
			double x = (((-time * speed / 20.0 + ThorDraw.hash(seed + 5) * span) % span) + span) % span - span / 2;
			Vec3 a = new Vec3(x, h, Math.sin(ang) * dist);
			Vec3 b = a.add(length, (ThorDraw.hash(seed + 6) - 0.5) * 12, (ThorDraw.hash(seed + 7) - 0.5) * 12);
			double frame = Math.floor(time * 2.0);
			arcs.add(ThorDraw.jagged(a, b, 14, 2.2, seed + frame * 0.71));
			alpha.add(0.45f + 0.35f * (float) ThorDraw.hash(seed + 8));
			width.add(7f + 6f * (float) ThorDraw.hash(seed + 9));
			colours.add(colour);
		}
		// a big bolt across the sky now and then (a few frames each)
		long slot = (long) Math.floor(time / 9.0);
		double seed = slot * 11.3;
		double phase = time - slot * 9.0;
		if (phase < 5.0 && ThorDraw.hash(seed) > 0.25) {
			double ang = ThorDraw.hash(seed + 1) * Math.PI * 2;
			Vec3 a = new Vec3(Math.cos(ang) * 90, 50 + ThorDraw.hash(seed + 2) * 30, Math.sin(ang) * 90);
			Vec3 b = new Vec3(Math.cos(ang + 0.9) * 90, -10 + ThorDraw.hash(seed + 3) * 20, Math.sin(ang + 0.9) * 90);
			arcs.add(ThorDraw.jagged(a, b, 18, 4.0, seed + Math.floor(time * 3.0)));
			alpha.add((float) (1.0 - phase / 5.0));
			width.add(14f);
			colours.add(colour);
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

		// v0.17.1: Tornado Arms' wind, the Speed Force portals, and (inside) the Speed Force's sky and its exit portal
		double time = System.currentTimeMillis() / 50.0;
		List<Vec3[]> wind = new ArrayList<>();
		List<Float> windAlpha = new ArrayList<>();
		for (AbstractClientPlayer acp : mc.level.players()) {
			if (!acp.isSpectator() && com.projecthero.mod.speedforce.SpeedForceAbilities.tornadoArms(acp)) {
				drawArmsWind(acp, partial, time, cam, wind, windAlpha, arcs, arcAlpha, arcWidth, arcColour);
			}
		}
		long ms = System.currentTimeMillis();
		for (AbstractClientPlayer acp : mc.level.players()) {
			int charge = com.projecthero.mod.speedforce.SpeedForceAbilities.portalCharge(acp);
			Portal pt = PORTALS.get(acp.getId());
			float shrink = 1f;
			if (charge > 0) {
				pt = new Portal(Math.min(1f, charge / (float) com.projecthero.mod.speedforce.SpeedForceConfig.PORTAL_TICKS), ms);
				PORTALS.put(acp.getId(), pt);
			} else if (pt != null) {
				shrink = 1f - (ms - pt.updatedMs()) / (float) PORTAL_COLLAPSE_MS;
				if (shrink <= 0f) {
					PORTALS.remove(acp.getId());
					continue;
				}
			} else {
				continue;
			}
			float yaw = acp.getViewYRot(partial);
			double yr = Math.toRadians(yaw);
			Vec3 at = acp.getPosition(partial).add(-Math.sin(yr) * com.projecthero.mod.speedforce.SpeedForceConfig.PORTAL_DISTANCE, 1.2,
					Math.cos(yr) * com.projecthero.mod.speedforce.SpeedForceConfig.PORTAL_DISTANCE);
			drawPortal(at, pt.progress(), yaw, com.projecthero.mod.speedforce.SpeedForceColours.of(acp), time, shrink, cam, arcs, arcAlpha, arcWidth, arcColour);
		}
		PORTALS.keySet().removeIf(id -> !(mc.level.getEntity(id) instanceof Player));
		if (mc.level.dimension() == com.projecthero.mod.speedforce.SpeedForceRealm.DIMENSION && mc.player != null) {
			int col = com.projecthero.mod.speedforce.SpeedForceColours.of(mc.player);
			drawRealmSky(cam, time, col, arcs, arcAlpha, arcWidth, arcColour);
				// v0.17.1 (user, Speed Force Memories): a swirl of lightning in every doorway's opening, tinted by what it is
				float doorScale = (float) (com.projecthero.mod.speedforce.SpeedForceRealm.DOOR_RADIUS
						/ (0.25 + com.projecthero.mod.speedforce.SpeedForceConfig.PORTAL_RADIUS));
				for (com.projecthero.mod.speedforce.entity.SpeedForceDoorwayEntity door : mc.level.getEntitiesOfClass(
						com.projecthero.mod.speedforce.entity.SpeedForceDoorwayEntity.class, new net.minecraft.world.phys.AABB(cam, cam).inflate(160))) {
					Vec3 c = new Vec3(door.getX(), com.projecthero.mod.speedforce.SpeedForceRealm.DOOR_CENTRE_Y, door.getZ());
					drawPortal(c, 1f, 0f, doorColour(door.kind(), col), time + door.getId() * 7.0, doorScale, cam, arcs, arcAlpha, arcWidth, arcColour);
				}
				// the echoes: a faint crackle of the runner's lightning about each (gone once caught)
				double frame = Math.floor(time / 3.0);
				for (com.projecthero.mod.speedforce.entity.SpeedForceEchoEntity echo : mc.level.getEntitiesOfClass(
						com.projecthero.mod.speedforce.entity.SpeedForceEchoEntity.class, new net.minecraft.world.phys.AABB(cam, cam).inflate(96))) {
					if (echo.spent()) {
						continue;
					}
					Vec3 at = echo.getPosition(partial);
					for (int k = 0; k < 3; k++) {
						double seed = frame * 3.1 + k * 17.3 + echo.getId();
						Vec3 a = at.add((ThorDraw.hash(seed) - 0.5) * 0.6, 0.3 + ThorDraw.hash(seed + 1) * 1.4, (ThorDraw.hash(seed + 2) - 0.5) * 0.6);
						Vec3 b = a.add((ThorDraw.hash(seed + 3) - 0.5) * 1.6, (ThorDraw.hash(seed + 4) - 0.5) * 1.0, (ThorDraw.hash(seed + 5) - 0.5) * 1.6);
						arcs.add(ThorDraw.jagged(a.subtract(cam), b.subtract(cam), 4, 0.12, seed));
						arcAlpha.add(0.55f);
						arcWidth.add(0.5f);
						arcColour.add(col);
					}
				}
			}
			for (com.projecthero.mod.speedforce.entity.BlackFlashEntity bf : mc.level.getEntitiesOfClass(
					com.projecthero.mod.speedforce.entity.BlackFlashEntity.class, new net.minecraft.world.phys.AABB(cam, cam).inflate(96))) {
				blackFlashLightning(bf, cam, partial, now, arcs, arcAlpha, arcWidth, arcColour);
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
			ThorDraw.ring(vc, last, c, r0 * 0.75f, r0 + 0.6f, ring.dark() ? 0x0A0A10 : 0xFFE8A0, 0.8f * (1f - t), 48);
			ThorDraw.ring(vc, last, c.add(0, 0.6, 0), r0 * 0.85f, r0 + 0.3f, ring.dark() ? 0xD01818 : 0xFFFFFF, 0.5f * (1f - t), 48);
		}
		for (int i = 0; i < wind.size(); i++) {
			ThorDraw.ribbon(vc, last, wind.get(i), 0.09f, 0xDCE8F4, windAlpha.get(i));
			ThorDraw.ribbon(vc, last, wind.get(i), 0.03f, 0xFFFFFF, windAlpha.get(i) * 1.6f);
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

	/** The Black Flash's lightning: the "black" colour (dark bolts, a red core). */
	private static final int BLACK_FLASH_COLOUR = 9;

	/** A doorway's swirl: the bed / spawn green, the last death red, the exits the runner's own colour, marked places light blue. */
	private static int doorColour(int kind, int own) {
		return switch (kind) {
			case com.projecthero.mod.speedforce.SpeedForceMemory.HOME -> 3;
			case com.projecthero.mod.speedforce.SpeedForceMemory.DEATH -> 1;
			case com.projecthero.mod.speedforce.SpeedForceMemory.MARKED -> 4;
			default -> own;
		};
	}

	/**
	 * v0.17.1: black-and-red lightning crackling off the Black Flash all the time (more of it mid-move, a storm while he
	 * dissolves), trailing back off him as he runs.
	 */
	private static void blackFlashLightning(com.projecthero.mod.speedforce.entity.BlackFlashEntity bf, Vec3 cam, float partial, long now,
			List<Vec3[]> arcs, List<Float> alpha, List<Float> width, List<Integer> colours) {
		boolean dying = bf.deathTime > 0;
		boolean busy = bf.action() != com.projecthero.mod.speedforce.entity.BlackFlashEntity.ACTION_NONE;
		Vec3 at = bf.getPosition(partial);
		Vec3 heading = at.subtract(bf.xo, bf.yo, bf.zo);
		boolean moving = heading.lengthSqr() > 0.01;
		int count = dying ? 12 : busy ? 7 : 4;
		double length = dying ? 2.2 : busy ? 1.4 : 1.0;
		double frame = Math.floor((now + partial) * 4.0);
		float fade = dying ? Math.max(0f, 1f - bf.deathTime / 50f) : bf.isRemnant() ? 0.6f : 1f;
		for (int k = 0; k < count; k++) {
			double seed = frame * 5.3 + k * 31.7 + bf.getId() * 0.37;
			if (ThorDraw.hash(seed) < 0.25) {
				continue;
			}
			Vec3 from = at.add((ThorDraw.hash(seed + 1) - 0.5) * 0.6, 0.2 + ThorDraw.hash(seed + 2) * 1.6, (ThorDraw.hash(seed + 3) - 0.5) * 0.6);
			Vec3 dir;
			if (dying) {
				dir = new Vec3((ThorDraw.hash(seed + 5) - 0.5) * 1.2, 0.6 + ThorDraw.hash(seed + 6) * 1.6, (ThorDraw.hash(seed + 7) - 0.5) * 1.2).scale(length);
			} else if (moving) {
				dir = heading.normalize().scale(-(0.5 + ThorDraw.hash(seed + 4)) * length * 1.6)
						.add((ThorDraw.hash(seed + 5) - 0.5) * length, (ThorDraw.hash(seed + 6) - 0.5) * length * 0.8, (ThorDraw.hash(seed + 7) - 0.5) * length);
			} else {
				dir = new Vec3(ThorDraw.hash(seed + 5) - 0.5, ThorDraw.hash(seed + 6) - 0.45, ThorDraw.hash(seed + 7) - 0.5).normalize().scale(length);
			}
			arcs.add(ThorDraw.jagged(from.subtract(cam), from.add(dir).subtract(cam), 5, 0.18, seed));
			alpha.add(fade);
			width.add(dying ? 1.2f : 0.9f);
			colours.add(BLACK_FLASH_COLOUR);
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
