package com.projecthero.mod.client.omnitrix.alien;

import java.util.Map;
import java.util.WeakHashMap;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2: a chain tail, first XLR8's, now any alien's (user: "give uphuck a short little tail, make it the updated tail please"). v0.17.2 (user: "make xlr8s tail better, make it move with player movement, not go into blocks or the player, similar to jetrays
 * tail"): XLR8's tail, one smooth tapering tube instead of four stiff boxes, on the Jetray tail's rules.
 * <p>
 * A follow-the-leader chain simulated in the WORLD each client tick: every point keeps its own momentum, is sprung toward his
 * resting shape (down and back from the small of his back, the tip curling up; flatter the faster he goes, dead flat at a run) and
 * stays exactly one link from the point before it. So it lags behind his turns, trails straight back at speed and swings past him
 * when he stops. Each point is then kept behind his back and legs and pushed out of every block collision box.
 * <p>
 * It follows the moves through the body bone: Tail Spin and the kick finisher turn the bone, and the tail comes round after it,
 * held flat. Drawn as a round spline tube from {@code xlr8_tail.png} (gen_xlr8_tail.js: the navy and teal chevrons the source skin
 * paints on the tail). Not drawn in first person; the inventory doll, which is not where he is in the world, gets the resting shape.
 */
final class TubeTail {
	private final ResourceLocation sheet;
	/** The chain's points, the length of a link (blocks at scale 1). */
	private final int N;
	private final double LEN;
	/** The player renderer's model scale. */
	private static final float MODEL_SCALE = 0.9375f;
	/** The tail's root on the body bone (px): the small of his back, just inside it. */
	private final float ROOT_Y, ROOT_Z;
	/** The tube's radius (px) at the root and tip. */
	private final float R_ROOT, R_TIP;
	private static final int SUB = 3, SIDES = 8;
	/** The fastest a point may carry its own motion (blocks / tick): a dead stop swings the tail past him, never flings it. */
	private static final double MAX_CARRY = 0.45;

	private final class Tail {
		final Vec3[] pos = new Vec3[N], last = new Vec3[N];
		/** Where the body bone holds the tail this frame (model space), so the moves carry it. */
		Vector3f rootModel = new Vector3f(0f, ROOT_Y / 16f, ROOT_Z / 16f);
		Vector3f backModel = new Vector3f(0f, 0f, 1f);
		boolean started;
	}

	private final Map<AbstractClientPlayer, Tail> TAILS = new WeakHashMap<>();

	/** @param texture the sheet under textures/entity/omnitrix/ @param rootY rootZ the root on the body bone (px) */
	TubeTail(String texture, int points, double linkLength, float rootRadius, float tipRadius, float rootY, float rootZ) {
		sheet = ProjectHeroMod.id("textures/entity/omnitrix/" + texture + ".png");
		N = points;
		LEN = linkLength;
		R_ROOT = rootRadius;
		R_TIP = tipRadius;
		ROOT_Y = rootY;
		ROOT_Z = rootZ;
	}

	/** Model space (blocks, y down from the top of the model) to a world offset from his feet. */
	private static Vec3 toWorld(Vector3f m, float yaw, float scale, boolean point) {
		float k = MODEL_SCALE * scale;
		double x = -m.x * k, y = ((point ? 1.501f : 0f) - m.y) * k, z = m.z * k;
		double a = (180f - yaw) * Mth.DEG_TO_RAD, c = Math.cos(a), s = Math.sin(a);
		return new Vec3(x * c + z * s, y, -x * s + z * c);
	}

	/** Link {@code i}'s resting angle below level (degrees, negative curls up): {@code lift} 0 standing .. 1 flat out. */
	private float restAngle(int i, float lift) {
		float k = i * 8f / (N - 2); // the whole curl, however many links
		return Mth.lerp(lift, 60f - k * 10f, 7f - k * 1.6f);
	}

	/**
	 * Every client tick.
	 *
	 * @param flat  0..1 forced flat out (Tail Spin, the kick finisher, Friction Zero's skate)
	 * @param swing the Tail Balance swing, radians (+ = out to his left)
	 */
	void tick(AbstractClientPlayer p, float flat, float swing) {
		Tail t = TAILS.computeIfAbsent(p, k -> new Tail());
		float yaw = p.yBodyRot, sc = p.getScale();
		double len = LEN * sc;
		Vec3 at = p.position();
		Vec3 root = at.add(toWorld(t.rootModel, yaw, sc, true));
		// his back along the ground, turned with the body bone; a forward lean does not point the tail at the sky
		Vec3 b = toWorld(t.backModel, yaw, 1f, false);
		b = new Vec3(b.x, 0, b.z);
		Vec3 back = b.lengthSqr() > 1.0e-4 ? b.normalize() : toWorld(new Vector3f(0, 0, 1), yaw, 1f, false).normalize();
		Vec3 up = new Vec3(0, 1, 0);
		Vec3 left = toWorld(new Vector3f(1, 0, 0), yaw, 1f, false).normalize();
		float sp = (float) new Vec3(p.getX() - p.xo, 0, p.getZ() - p.zo).length();
		float ws = Mth.clamp(p.walkAnimation.speed(), 0f, 1f);
		float run = Mth.clamp(sp / 0.32f, 0f, 1f);
		float lift = Mth.clamp(Math.max(Math.max(ws * 0.6f, run), flat), 0f, 1f);
		float age = p.tickCount;
		if (!t.started || t.pos[0].distanceToSqr(root) > 144.0) {
			// fresh (or teleported): laid out at rest
			Vec3 q = root;
			for (int i = 0; i < N; i++) {
				if (i > 0) {
					q = q.add(rest(i - 1, back, up, left, lift, 0f, 0f, 0f).scale(len));
				}
				t.pos[i] = q;
				t.last[i] = q;
			}
			t.started = true;
			return;
		}
		Vec3 hip = at.add(0, 0.7 * sc, 0);
		Vec3[] next = new Vec3[N];
		next[0] = root;
		for (int i = 1; i < N; i++) {
			float f = (i - 1) / (float) (N - 2);
			Vec3 vel = t.pos[i].subtract(t.last[i]).scale(0.8);
			if (vel.lengthSqr() > MAX_CARRY * MAX_CARRY) {
				vel = vel.normalize().scale(MAX_CARRY);
			}
			Vec3 q = t.pos[i].add(vel).add(0, -0.012, 0);
			// the spring toward the resting shape, measured from the point before: firm at the root, loose at the tip
			Vec3 want = next[i - 1].add(rest(i - 1, back, up, left, lift, age, ws * (1f - 0.6f * run), swing).scale(len));
			q = q.add(want.subtract(q).scale(Mth.lerp(f, 0.5f, 0.17f)));
			// settled together with the links: a few passes, blocks last
			for (int pass = 0; pass < 3; pass++) {
				q = link(next[i - 1], q, len);
				q = behindHim(q, hip, back, left, i, ws, sc);
				q = link(next[i - 1], q, len);
				q = GhostfreakTail.outOfBlocks(p, q, Math.max(0.03, radius(i / (float) (N - 1)) / 16f * 0.9f));
			}
			next[i] = q;
		}
		System.arraycopy(t.pos, 0, t.last, 0, N);
		System.arraycopy(next, 0, t.pos, 0, N);
	}

	/** Link {@code i}'s resting direction: {@code move} sets the slow sway of his walk, {@code age} the idle one, {@code swing} Tail Balance. */
	private Vec3 rest(int i, Vec3 back, Vec3 up, Vec3 left, float lift, float age, float move, float swing) {
		double a = Math.toRadians(restAngle(i, lift));
		float tip = i / (float) (N - 2);
		double sway = Mth.sin(age * 0.13f - i * 0.6f) * 0.07 * (0.4 + 0.6 * tip) + Mth.sin(age * 0.3f - i * 0.55f) * 0.13 * move + swing * (0.7 - 0.25 * tip);
		return back.scale(Math.cos(a)).subtract(up.scale(Math.sin(a))).add(left.scale(sway)).normalize();
	}

	private static Vec3 link(Vec3 from, Vec3 to, double len) {
		Vec3 d = to.subtract(from);
		double l = d.length();
		return l < 1.0e-6 ? from.add(0, -len, 0) : from.add(d.scale(len / l));
	}

	/** Anything inside the column of his torso and legs goes out behind his back, further down where his legs swing back. */
	private static Vec3 behindHim(Vec3 q, Vec3 hip, Vec3 back, Vec3 left, int i, float ws, float sc) {
		Vec3 rel = q.subtract(hip);
		double rb = rel.dot(back), rl = rel.dot(left), ru = rel.y;
		double minBack = (0.17 + 0.012 * i + (ru < 0 ? ws * 0.2 : 0)) * sc;
		if (rb < minBack && Math.abs(rl) < 0.36 * sc && ru > -0.8 * sc && ru < 1.2 * sc) {
			return q.add(back.scale(minBack - rb));
		}
		return q;
	}

	private float radius(float f) {
		return Mth.lerp((float) Math.pow(f, 1.25), R_ROOT, R_TIP);
	}

	// ---------------------------------------------------------------- drawing

	/** In the model space the player renderer hands {@code renderExtras} (before any bone). */
	void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model, float partial) {
		Tail t = TAILS.computeIfAbsent(p, k -> new Tail());
		PoseStack bp = new PoseStack();
		model.body.translateAndRotate(bp);
		Vector3f root = bp.last().pose().transformPosition(new Vector3f(0f, ROOT_Y / 16f, ROOT_Z / 16f));
		Vector3f bodyBack = bp.last().pose().transformDirection(new Vector3f(0f, 0f, 1f));
		Vector3f[] q = new Vector3f[N];
		boolean world = t.started;
		if (world) {
			Matrix4f inv = new Matrix4f(pose.last().pose()).invert();
			Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
			for (int i = 0; i < N; i++) {
				Vec3 w = t.last[i].lerp(t.pos[i], partial).subtract(cam);
				Vector4f v = inv.transform(new Vector4f((float) w.x, (float) w.y, (float) w.z, 1f));
				q[i] = new Vector3f(v.x, v.y, v.z);
			}
			world = q[0].distance(root) <= 1.5f;
		}
		if (world) {
			t.rootModel = root;
			t.backModel = bodyBack;
			Minecraft mc = Minecraft.getInstance();
			if (p == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()) {
				return;
			}
			// pin the root to the posed body, easing the correction out along the chain
			Vector3f fix = new Vector3f(root).sub(q[0]);
			for (int i = 0; i < N; i++) {
				q[i].add(new Vector3f(fix).mul(1f - i / (float) (N - 1)));
			}
		} else {
			// the inventory doll: the resting shape, in the model's own space
			q[0] = new Vector3f(root);
			for (int i = 1; i < N; i++) {
				float a = restAngle(i - 1, 0f) * Mth.DEG_TO_RAD;
				q[i] = new Vector3f(q[i - 1]).add(0f, (float) LEN / MODEL_SCALE * Mth.sin(a), (float) LEN / MODEL_SCALE * Mth.cos(a));
			}
		}
		tube(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(sheet)), light, q);
	}

	/**
	 * v0.17.3: a scripted shape instead of the world chain, for an Omnitrix show transformation (the holder's camera there counts
	 * as first person, which hid the world tail, and the shots need the show's tail: hanging, curled up, streaming flat out).
	 * Laid out from the root along the body bone's own back / down / side axes, so it stays on him however the pose leans.
	 * Link {@code i}'s angle below his back runs from {@code downRoot} at the root to {@code downTip} at the tip (degrees,
	 * negative curls up); {@code sway} swings it out to his left (radians, growing toward the tip) and {@code wave} adds a
	 * travelling ripple of that size at {@code phase}.
	 */
	void renderScripted(PoseStack pose, MultiBufferSource buffers, int light, PlayerModel<AbstractClientPlayer> model, float downRoot, float downTip,
			float sway, float wave, float phase) {
		PoseStack bp = new PoseStack();
		model.body.translateAndRotate(bp);
		Matrix4f m = bp.last().pose();
		Vector3f root = m.transformPosition(new Vector3f(0f, ROOT_Y / 16f, ROOT_Z / 16f));
		Vector3f back = m.transformDirection(new Vector3f(0f, 0f, 1f)).normalize();
		Vector3f down = m.transformDirection(new Vector3f(0f, 1f, 0f)).normalize();
		Vector3f side = m.transformDirection(new Vector3f(1f, 0f, 0f)).normalize();
		Vector3f[] q = new Vector3f[N];
		q[0] = root;
		float len = (float) LEN / MODEL_SCALE;
		for (int i = 1; i < N; i++) {
			float f = (i - 1) / (float) (N - 2);
			float a = Mth.lerp(f, downRoot, downTip) * Mth.DEG_TO_RAD;
			float s = sway * (0.3f + 0.7f * f) + wave * f * Mth.sin(phase - i * 0.7f);
			Vector3f d = new Vector3f(back).mul(Mth.cos(a) * Mth.cos(s)).add(new Vector3f(down).mul(Mth.sin(a) * Mth.cos(s)))
					.add(new Vector3f(side).mul(Mth.sin(s)));
			q[i] = new Vector3f(q[i - 1]).add(d.normalize().mul(len));
		}
		tube(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(sheet)), light, q);
	}

	/** The chain as one round tube: a spline through its points, a ring every step turned smoothly from the last (the top stays on top). */
	private void tube(PoseStack pose, VertexConsumer vc, int light, Vector3f[] q) {
		int steps = (N - 1) * SUB;
		Vector3f[] c = new Vector3f[steps + 1];
		for (int s = 0; s <= steps; s++) {
			int i = Math.min(N - 2, s / SUB);
			c[s] = catmull(q[Math.max(0, i - 1)], q[i], q[i + 1], q[Math.min(N - 1, i + 2)], (s - i * SUB) / (float) SUB);
		}
		PoseStack.Pose last = pose.last();
		Vector3f n = new Vector3f(0f, -1f, 0f), t = new Vector3f();
		Vector3f[] ring0 = null, nrm0 = null;
		for (int s = 0; s <= steps; s++) {
			t.set(c[Math.min(steps, s + 1)]).sub(c[Math.max(0, s - 1)]);
			if (t.lengthSquared() < 1.0e-10f) {
				t.set(0f, 0f, 1f);
			}
			t.normalize();
			n.sub(new Vector3f(t).mul(n.dot(t)));
			if (n.lengthSquared() < 1.0e-6f) {
				n.set(Math.abs(t.y) < 0.9f ? new Vector3f(0, 1, 0).cross(t) : new Vector3f(1, 0, 0).cross(t));
			}
			n.normalize();
			Vector3f b = new Vector3f(t).cross(n).normalize();
			float f = s / (float) steps, r = radius(f) / 16f;
			Vector3f[] ring = new Vector3f[SIDES + 1], nrm = new Vector3f[SIDES + 1];
			for (int k = 0; k <= SIDES; k++) {
				// k = SIDES / 2 is the top: the sheet's chevrons run down the middle of its width
				float a = (k / (float) SIDES - 0.5f) * Mth.TWO_PI;
				nrm[k] = new Vector3f(n).mul(Mth.cos(a)).add(new Vector3f(b).mul(Mth.sin(a)));
				ring[k] = new Vector3f(c[s]).add(new Vector3f(nrm[k]).mul(r));
			}
			if (ring0 != null) {
				float v0 = (s - 1) / (float) steps;
				for (int k = 0; k < SIDES; k++) {
					float u0 = k / (float) SIDES, u1 = (k + 1) / (float) SIDES;
					vert(vc, last, light, ring0[k], u0, v0, nrm0[k]);
					vert(vc, last, light, ring[k], u0, f, nrm[k]);
					vert(vc, last, light, ring[k + 1], u1, f, nrm[k + 1]);
					vert(vc, last, light, ring0[k + 1], u1, v0, nrm0[k + 1]);
				}
			}
			ring0 = ring;
			nrm0 = nrm;
		}
	}

	private static Vector3f catmull(Vector3f a, Vector3f b, Vector3f c, Vector3f d, float t) {
		float t2 = t * t, t3 = t2 * t;
		return new Vector3f(cr(a.x, b.x, c.x, d.x, t, t2, t3), cr(a.y, b.y, c.y, d.y, t, t2, t3), cr(a.z, b.z, c.z, d.z, t, t2, t3));
	}

	private static float cr(float a, float b, float c, float d, float t, float t2, float t3) {
		return 0.5f * (2f * b + (c - a) * t + (2f * a - 5f * b + 4f * c - d) * t2 + (3f * b - a - 3f * c + d) * t3);
	}

	private static void vert(VertexConsumer vc, PoseStack.Pose last, int light, Vector3f p, float u, float v, Vector3f n) {
		vc.addVertex(last, p.x, p.y, p.z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(last, n.x, n.y, n.z);
	}
}
