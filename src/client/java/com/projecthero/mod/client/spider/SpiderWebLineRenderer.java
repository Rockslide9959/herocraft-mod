package com.projecthero.mod.client.spider;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.spider.data.SpiderManState;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the web line, for every swinging player the client can see -- and (v0.12.20) every fading strand from
 * {@link SpiderStrands}: Web Zip, the Combat Mode moves, and the remnant of a released swing, all in the same
 * line look so a zip reads as the same web the swing throws.
 *
 * <p>Purely a render: a handful of line segments straight into the world-render buffer. No entity of any kind
 * is created, so there is nothing to accumulate, nothing to leak and nothing left behind when the line ends --
 * the state flips and the line simply stops being drawn (or fades, for strands).
 *
 * <p>The line sags slightly toward its middle rather than being a taut straight segment, which reads far more
 * like webbing and costs one interpolation per segment. Both ends are pinned to real positions every frame: the
 * near end to the tracked fist ({@link SpiderStrands#handPosition}), the far end to the anchor / target.
 */
public final class SpiderWebLineRenderer {
	private static final int SEGMENTS = 10;
	private static final float R = 0.94f;
	private static final float G = 0.96f;
	private static final float B = 1.0f;
	private static final float BASE_ALPHA = 0.9f;
	/** v0.16.2: the strand's look -- half its width, how long one texture repeat is, and the spiral fibre round it. */
	private static final net.minecraft.resources.ResourceLocation LINE_TEXTURE =
			com.projecthero.mod.ProjectHeroMod.id("textures/effect/web_line.png");
	private static final double HALF_WIDTH = 0.028;
	private static final double TEXTURE_TILE = 0.45;
	private static final double HELIX_RADIUS = 0.024;
	private static final double HELIX_TURNS_PER_BLOCK = 1.6;
	/** v0.16.2: a released strand's swing -- how fast it decays (ticks) and its angular frequency (rad/tick). */
	private static final double SWAY_DECAY = 14.0;
	private static final double SWAY_FREQ = 0.34;

	/** playerId -> anchor + hand for players seen swinging last frame, so a release can be noticed. */
	private static final Map<Integer, SwingMemo> LAST_SWING = new HashMap<>();

	private record SwingMemo(Vec3 anchor, boolean rightHand, Vec3 hand) {
	}

	private SpiderWebLineRenderer() {
	}

	public static void initialize() {
		WorldRenderEvents.AFTER_ENTITIES.register(SpiderWebLineRenderer::render);
	}

	private static void render(WorldRenderContext context) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || context.consumers() == null) {
			return;
		}
		Camera camera = context.camera();
		MultiBufferSource consumers = context.consumers();
		PoseStack poseStack = context.matrixStack();
		if (poseStack == null) {
			return;
		}
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);

		// Swings: draw live lines; notice releases and hand them to the strand list to fade out.
		Set<Integer> swingingNow = new HashSet<>();
		for (Player player : client.level.players()) {
			SpiderManState state = player.getAttachedOrElse(ModAttachments.SPIDER_MAN_STATE, null);
			if (state == null || !state.swinging) {
				continue;
			}
			Vec3 anchor = new Vec3(state.anchorX, state.anchorY, state.anchorZ);
			swingingNow.add(player.getId());
			Vec3 hand = com.projecthero.mod.spider.SpiderMoves.hangingUpsideDown(player)
					// v0.16.1 the upside-down hang: the line is tied on at his feet (the top of his box, the body being flipped)
					// v0.16.2: down through where his feet meet to his hands' grip just below them (SpiderHang)
					? SpiderHang.lineEnd(player, partial)
					: SpiderStrands.handPosition(player, state.swingHandRight, partial, camera);
			LAST_SWING.put(player.getId(), new SwingMemo(anchor, state.swingHandRight, hand));
			drawLine(poseStack, consumers, camera, hand, anchor, BASE_ALPHA);
		}
		LAST_SWING.entrySet().removeIf(e -> {
			if (swingingNow.contains(e.getKey())) {
				return false;
			}
			SpiderStrands.swingReleased(e.getKey(), e.getValue().anchor(), e.getValue().rightHand(), e.getValue().hand());
			return true;
		});

		// Strands.
		SpiderStrands.prune(partial);
		double now = client.level.getGameTime() + partial;
		for (SpiderStrands.Strand s : SpiderStrands.strands()) {
			Player owner = client.level.getEntity(s.playerId) instanceof Player p ? p : null;
			if (owner == null) {
				continue;
			}
			float alpha = s.alpha(now) * BASE_ALPHA;
			if (alpha <= 0.0f) {
				continue;
			}
			// Attached to the hand only while the web is being held; the moment it starts to fade the near end
			// is frozen where it was, so a fading web never trails the player's hand around.
			Vec3 hand = s.frozenHand;
			if (hand == null) {
				hand = SpiderStrands.handPosition(owner, s.rightHand, partial, camera);
				if (now - s.startTick >= s.hold) {
					s.frozenHand = hand;
				}
			}
			Vec3 end = SpiderStrands.endPoint(s, partial);
			if (s.frozenHand != null) {
				// v0.16.2 (user): a let-go web no longer hangs frozen in the air -- its loose end falls and swings
				// down under the end still stuck, and sways in the breeze while it fades
				hand = looseEnd(s.frozenHand, end, now - (s.startTick + s.hold), s.playerId * 31 + s.slot);
			}
			drawLine(poseStack, consumers, camera, hand, end, alpha);
		}

		// v0.16.1 Weave Mode: the centre marker -- the centroid of the anchors placed so far (synced), a small pulsing star
		for (Player player : client.level.players()) {
			com.projecthero.mod.spider.data.SpiderMoveState ws = player.getAttachedOrElse(ModAttachments.SPIDER_MOVE_STATE, null);
			if (ws == null || !ws.has(com.projecthero.mod.spider.data.SpiderMoveState.FLAG_WEAVING) || ws.strands < 1) {
				continue;
			}
			Vec3 c = new Vec3(ws.cx, ws.cy, ws.cz);
			double r = 0.35 + 0.08 * Math.sin(now * 0.4);
			float a = 0.85f;
			drawLine(poseStack, consumers, camera, c.add(-r, 0, 0), c.add(r, 0, 0), a);
			drawLine(poseStack, consumers, camera, c.add(0, -r, 0), c.add(0, r, 0), a);
			drawLine(poseStack, consumers, camera, c.add(0, 0, -r), c.add(0, 0, r), a);
			double d = r * 0.6;
			drawLine(poseStack, consumers, camera, c.add(-d, -d, -d), c.add(d, d, d), a * 0.7f);
			drawLine(poseStack, consumers, camera, c.add(-d, d, -d), c.add(d, -d, d), a * 0.7f);
		}

		// v0.16.1 Web Whip: the strand whirled once round him, a ring that sweeps out with the spin and fades
		for (Player player : client.level.players()) {
			com.projecthero.mod.spider.data.SpiderMoveState ms = player.getAttachedOrElse(ModAttachments.SPIDER_MOVE_STATE, null);
			if (ms == null || ms.animId != com.projecthero.mod.spider.SpiderAnim.WHIP) {
				continue;
			}
			double age = now - ms.animStart;
			if (age < 0 || age > 14) {
				continue;
			}
			double sweep = Math.min(1.0, age / 9.0);
			float a = (float) (BASE_ALPHA * (age < 9 ? 1.0 : 1.0 - (age - 9) / 5.0));
			Vec3 c = player.getPosition(partial).add(0, 1.05, 0);
			double r = com.projecthero.mod.spider.SpiderManConfig.moves().webWhipRadius;
			double start = Math.toRadians(-player.getViewYRot(partial) - 90.0);
			int n = 32;
			Vec3 prev = null;
			for (int i = 0; i <= (int) (n * sweep); i++) {
				double ang = start - i / (double) n * Math.PI * 2.0;
				Vec3 p = c.add(Math.cos(ang) * r, Math.sin(i * 0.7) * 0.15, -Math.sin(ang) * r);
				if (prev == null) {
					drawLine(poseStack, consumers, camera, SpiderStrands.handPosition(player, true, partial, camera), p, a);
				} else {
					drawLine(poseStack, consumers, camera, prev, p, a);
				}
				prev = p;
			}
		}
	}

	/**
	 * v0.16.2: where the loose end of a released strand is, {@code t} ticks after it was let go. A damped pendulum
	 * about the still-stuck end: it starts where the hand let go, swings down and past straight-down a few times,
	 * settling, and picks up a gentle sideways breeze as it fades. Pure function of time, so every frame and viewer agree.
	 */
	private static Vec3 looseEnd(Vec3 released, Vec3 pinned, double t, int seed) {
		Vec3 offset = released.subtract(pinned);
		double len = offset.length();
		if (len < 1.0e-3 || t <= 0.0) {
			return released;
		}
		Vec3 d0 = offset.scale(1.0 / len);
		Vec3 down = new Vec3(0, -1, 0);
		Vec3 axis = down.cross(d0);
		double sin0 = axis.length();
		double theta0 = Math.atan2(sin0, down.dot(d0)); // angle from hanging straight down to where it was let go
		axis = sin0 < 1.0e-4 ? new Vec3(1, 0, 0) : axis.scale(1.0 / sin0);
		// damped swing: from theta0, through down, past it, settling (period ~18 ticks, half-life ~10 ticks)
		double theta = theta0 * Math.exp(-t / SWAY_DECAY) * Math.cos(t * SWAY_FREQ);
		Vec3 dir = rotate(down, axis, theta);
		// the breeze: a slow side-to-side drift that builds up as it hangs
		double phase = (seed & 0xFF) * 0.1;
		double breeze = Math.min(1.0, t / 25.0) * len * 0.06;
		Vec3 wind = new Vec3(Math.cos(phase), 0, Math.sin(phase)).scale(Math.sin(t * 0.11 + phase) * breeze);
		// and it slumps a little as the tension goes out of it
		double slump = Math.min(1.0, t / 30.0) * 0.08;
		return pinned.add(dir.scale(len * (1.0 - slump))).add(wind);
	}

	/** {@code v} rotated by {@code angle} about the unit {@code axis} (Rodrigues). */
	private static Vec3 rotate(Vec3 v, Vec3 axis, double angle) {
		double c = Math.cos(angle), s = Math.sin(angle);
		return v.scale(c).add(axis.cross(v).scale(s)).add(axis.scale(axis.dot(v) * (1 - c)));
	}

	/**
	 * v0.16.2: the web is a real strand now, not a 1-pixel line -- a camera-facing ribbon wearing a braided-silk texture
	 * (two fibres twisting round each other, soft fuzzy edges; {@code textures/effect/web_line.png}), lit by the world,
	 * tapered where it leaves the hand, with one thin extra fibre spiralling round it so it reads as twisted, in 3D.
	 */
	private static void drawLine(PoseStack poseStack, MultiBufferSource consumers, Camera camera,
			Vec3 hand, Vec3 anchor, float alpha) {
		Vec3 cam = camera.getPosition();
		double length = hand.distanceTo(anchor);
		if (length < 1.0e-3) {
			return;
		}
		int segments = (int) Math.max(SEGMENTS, Math.min(64, length * 3.0));
		double slack = Math.min(0.9, length * 0.035);
		Vec3[] pts = new Vec3[segments + 1];
		for (int i = 0; i <= segments; i++) {
			double t = (double) i / segments;
			// a parabola that is zero at both ends and deepest in the middle
			pts[i] = hand.lerp(anchor, t).subtract(0, slack * (4.0 * t * (1.0 - t)), 0);
		}

		poseStack.pushPose();
		poseStack.translate(-cam.x, -cam.y, -cam.z);
		var pose = poseStack.last();
		var level = Minecraft.getInstance().level;

		// the ribbon (quads, each segment its own -- several strands share one buffer)
		VertexConsumer ribbon = consumers.getBuffer(RenderType.entityTranslucent(LINE_TEXTURE));
		double v = 0.0;
		Vec3[] left = new Vec3[segments + 1];
		Vec3[] right = new Vec3[segments + 1];
		for (int i = 0; i <= segments; i++) {
			Vec3 tangent = pts[Math.min(segments, i + 1)].subtract(pts[Math.max(0, i - 1)]).normalize();
			Vec3 toCam = cam.subtract(pts[i]);
			Vec3 side = tangent.cross(toCam);
			side = side.lengthSqr() < 1.0e-8 ? new Vec3(0, 1, 0) : side.normalize();
			double t = (double) i / segments;
			double width = HALF_WIDTH * (t < 0.08 ? 0.55 + 0.45 * t / 0.08 : 1.0);
			left[i] = pts[i].add(side.scale(width));
			right[i] = pts[i].subtract(side.scale(width));
		}
		for (int i = 0; i < segments; i++) {
			double v0 = v;
			v += pts[i].distanceTo(pts[i + 1]) / TEXTURE_TILE;
			int light = level == null ? 0xF000F0
					: net.minecraft.client.renderer.LevelRenderer.getLightColor(level, net.minecraft.core.BlockPos.containing(pts[i]));
			Vec3 n = cam.subtract(pts[i]).normalize();
			ribbonVertex(ribbon, pose, left[i], 0f, (float) v0, alpha, light, n);
			ribbonVertex(ribbon, pose, right[i], 1f, (float) v0, alpha, light, n);
			ribbonVertex(ribbon, pose, right[i + 1], 1f, (float) v, alpha, light, n);
			ribbonVertex(ribbon, pose, left[i + 1], 0f, (float) v, alpha, light, n);
		}

		// the spiral fibre (segment pairs, not a strip)
		VertexConsumer fibre = consumers.getBuffer(RenderType.lines());
		Vec3 prev = null;
		int fibreSteps = segments * 3;
		for (int k = 0; k <= fibreSteps; k++) {
			double t = (double) k / fibreSteps;
			double f = t * segments;
			int i = Math.min(segments - 1, (int) f);
			Vec3 c = pts[i].lerp(pts[i + 1], f - i);
			Vec3 tangent = pts[i + 1].subtract(pts[i]).normalize();
			Vec3 a = tangent.cross(new Vec3(0, 1, 0));
			a = a.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : a.normalize();
			Vec3 b = tangent.cross(a).normalize();
			double ang = t * length * HELIX_TURNS_PER_BLOCK * Math.PI * 2.0;
			Vec3 p = c.add(a.scale(Math.cos(ang) * HELIX_RADIUS)).add(b.scale(Math.sin(ang) * HELIX_RADIUS));
			if (prev != null) {
				Vec3 d = p.subtract(prev);
				double len = Math.max(1.0e-6, d.length());
				fibre.addVertex(pose.pose(), (float) prev.x, (float) prev.y, (float) prev.z)
						.setColor(0.86f, 0.88f, 0.92f, alpha * 0.75f).setNormal(pose, (float) (d.x / len), (float) (d.y / len), (float) (d.z / len));
				fibre.addVertex(pose.pose(), (float) p.x, (float) p.y, (float) p.z)
						.setColor(0.86f, 0.88f, 0.92f, alpha * 0.75f).setNormal(pose, (float) (d.x / len), (float) (d.y / len), (float) (d.z / len));
			}
			prev = p;
		}
		poseStack.popPose();
	}

	private static void ribbonVertex(VertexConsumer buffer, PoseStack.Pose pose, Vec3 p, float u, float v, float alpha,
			int light, Vec3 n) {
		buffer.addVertex(pose.pose(), (float) p.x, (float) p.y, (float) p.z)
				.setColor(R, G, B, alpha).setUv(u, v)
				.setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(light)
				.setNormal(pose, (float) n.x, (float) n.y, (float) n.z);
	}
}
