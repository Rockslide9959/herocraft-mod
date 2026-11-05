package com.projecthero.mod.client.omnitrix.alien;

import java.util.Map;
import java.util.WeakHashMap;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.omnitrix.AlienParts;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * v0.17.2 (user: "Make the ghostfreak tail better please, make it react to the direciton that the player is moving in and never go
 * through blocks or the player unless the player is phasing, make the tail flowing and animated kinda, look at how its done in
 * the show and try to replicate it please, try to colour match the tail to the skin please"): Ghostfreak's wispy ghost tail.
 * <p>
 * A rope of {@link #N} points simulated in the WORLD each client tick (verlet): the first is pinned under his waist, the rest
 * carry their own momentum, so the tail is left behind wherever he moves -- it streams out opposite his motion when he flies,
 * swings out on a turn and drifts back -- while a weak pull back toward its resting shape (hanging down, curving a little back)
 * and a slow travelling wave (growing toward the tip, like the show's wisp rippling as he floats) keep it alive when he is
 * still. Every point is pushed out of block collision shapes and out of his own torso and head after each step, so it lies on
 * floors, bends round walls and never cuts through him -- except while he is phasing (Intangibility / Phantom Rush), when it
 * passes through walls with him.
 * <p>
 * Drawn as tapering boxes, one per link, each turned to its link, textured with the tail his second skin paints on its legs
 * ({@code ghostfreak_tail.png}, gen_ghostfreak_v2.js: front, back and both sides, the rows running root to tip) and fading out
 * toward the tip.
 */
final class GhostfreakTail {
	private static final ResourceLocation SHEET = ProjectHeroMod.id("textures/entity/omnitrix/ghostfreak_tail.png");
	static final int N = 11;
	/** Link lengths (blocks), root to tip. */
	private static final float[] LEN = { 0.11f, 0.12f, 0.12f, 0.12f, 0.115f, 0.11f, 0.105f, 0.1f, 0.095f, 0.09f };
	/** Width (x) and depth (z) at each point, model pixels. */
	private static final float[] W = { 7.8f, 7.2f, 6.4f, 5.6f, 4.8f, 4.0f, 3.3f, 2.6f, 1.9f, 1.3f, 0.6f };
	private static final float[] D = { 3.9f, 3.6f, 3.3f, 3.0f, 2.7f, 2.3f, 2.0f, 1.6f, 1.2f, 0.8f, 0.4f };
	/** The root under his waist (model space, blocks: y down from the top of the model, z backward). */
	private static final float ROOT_Y = 11.2f / 16f;
	/** The resting shape: each link this far from straight down toward his back (degrees), growing to the tip. */
	private static final float REST_BACK = 18f, REST_BACK_TIP = 42f;

	private static final class Tail {
		final Vec3[] pos = new Vec3[N], prev = new Vec3[N], last = new Vec3[N];
		Vector3f rootModel = new Vector3f(0f, ROOT_Y, 0f);
		boolean started;
	}

	private static final Map<AbstractClientPlayer, Tail> TAILS = new WeakHashMap<>();

	private GhostfreakTail() {
	}

	// ---------------------------------------------------------------- model space <-> world

	/** Model space (blocks, as the player renderer lays the model out: y down from 1.501, +z his back) to a world offset. */
	private static Vec3 toWorld(Vector3f m, float bodyYaw) {
		Vector3f v = new Vector3f(-m.x, 1.501f - m.y, m.z);
		new Quaternionf().rotationY((180f - bodyYaw) * Mth.DEG_TO_RAD).transform(v);
		return new Vec3(v.x, v.y, v.z);
	}

	private static Vector3f toModel(Vec3 w, float bodyYaw) {
		Vector3f v = new Vector3f((float) w.x, (float) w.y, (float) w.z);
		new Quaternionf().rotationY(-(180f - bodyYaw) * Mth.DEG_TO_RAD).transform(v);
		return new Vector3f(-v.x, 1.501f - v.y, v.z);
	}

	// ---------------------------------------------------------------- the simulation (client tick)

	static void tick(AbstractClientPlayer p, boolean phasing) {
		Tail t = TAILS.computeIfAbsent(p, k -> new Tail());
		float yaw = p.yBodyRot;
		Vec3 at = p.position();
		Vec3 root = at.add(toWorld(t.rootModel, yaw));
		// the resting shape, in the world: from the root down and curving back
		Vec3[] rest = new Vec3[N];
		{
			Vector3f m = new Vector3f(t.rootModel);
			rest[0] = root;
			for (int i = 1; i < N; i++) {
				float a = Mth.lerp(i / (float) (N - 1), REST_BACK, REST_BACK_TIP) * Mth.DEG_TO_RAD;
				m.add(0f, LEN[i - 1] * Mth.cos(a), LEN[i - 1] * Mth.sin(a));
				rest[i] = at.add(toWorld(m, yaw));
			}
		}
		if (!t.started || t.pos[1].distanceToSqr(root) > 9.0) {
			for (int i = 0; i < N; i++) {
				t.pos[i] = rest[i];
				t.prev[i] = rest[i];
				t.last[i] = rest[i];
			}
			t.started = true;
		}
		for (int i = 0; i < N; i++) {
			t.last[i] = t.pos[i];
		}
		// his sideways axis in the world, for the wave
		Vec3 side = toWorld(new Vector3f(1f, 1.501f, 0f), yaw);
		float age = p.tickCount;
		t.prev[0] = t.pos[0];
		t.pos[0] = root;
		for (int i = 1; i < N; i++) {
			float k = i / (float) (N - 1);
			Vec3 vel = t.pos[i].subtract(t.prev[i]).scale(0.76); // v0.17.2 (user: "moves around too much"): calmer, was 0.84
			// a weak pull to the resting shape (stronger near the root, so it leaves his waist hanging down), a ghostly
			// half-gravity, and the travelling ripple: side to side and a little up and down, larger toward the tip
			Vec3 pull = rest[i].subtract(t.pos[i]).scale(0.08 + 0.12 * (1f - k));
			double wave = Mth.sin(age * 0.17f - i * 0.62f) * 0.0035 * k;
			double bob = Mth.cos(age * 0.13f - i * 0.5f) * 0.0015 * k;
			Vec3 acc = pull.add(side.scale(wave)).add(0, -0.004 + bob, 0);
			t.prev[i] = t.pos[i];
			t.pos[i] = t.pos[i].add(vel).add(acc);
		}
		// the links' lengths, then out of blocks and out of his body -- a few passes so they settle together
		for (int pass = 0; pass < 4; pass++) {
			for (int i = 1; i < N; i++) {
				Vec3 d = t.pos[i].subtract(t.pos[i - 1]);
				double l = d.length();
				if (l > 1e-6) {
					t.pos[i] = t.pos[i - 1].add(d.scale(LEN[i - 1] / l));
				}
			}
			for (int i = 1; i < N; i++) {
				if (!phasing) {
					t.pos[i] = outOfBlocks(p, t.pos[i], Math.max(0.025, D[i] / 32f));
				}
				t.pos[i] = outOfBody(t.pos[i], at, yaw, t.rootModel);
			}
		}
	}

	/** {@code q} pushed out of every block collision box it is inside (with radius {@code r}), along the shortest way out. */
	static Vec3 outOfBlocks(AbstractClientPlayer p, Vec3 q, double r) {
		for (int it = 0; it < 3; it++) {
			AABB box = new AABB(q.x - r, q.y - r, q.z - r, q.x + r, q.y + r, q.z + r);
			boolean moved = false;
			for (VoxelShape shape : p.level().getBlockCollisions(null, box)) {
				for (AABB b : shape.toAabbs()) {
					if (!b.intersects(box)) {
						continue;
					}
					// the penetration on each axis, out of the nearer face
					double px1 = b.maxX - box.minX, px0 = box.maxX - b.minX;
					double py1 = b.maxY - box.minY, py0 = box.maxY - b.minY;
					double pz1 = b.maxZ - box.minZ, pz0 = box.maxZ - b.minZ;
					double px = Math.min(px0, px1), py = Math.min(py0, py1), pz = Math.min(pz0, pz1);
					if (py <= px && py <= pz) {
						q = q.add(0, py1 < py0 ? py1 : -py0, 0);
					} else if (px <= pz) {
						q = q.add(px1 < px0 ? px1 : -px0, 0, 0);
					} else {
						q = q.add(0, 0, pz1 < pz0 ? pz1 : -pz0);
					}
					box = new AABB(q.x - r, q.y - r, q.z - r, q.x + r, q.y + r, q.z + r);
					moved = true;
				}
			}
			if (!moved) {
				break;
			}
		}
		return q;
	}

	/** {@code q} kept out of his torso and head (model-space boxes, a little padded); below the root it is free. */
	private static Vec3 outOfBody(Vec3 q, Vec3 at, float yaw, Vector3f root) {
		Vector3f m = toModel(q.subtract(at), yaw);
		float s = 1f / 16f;
		// torso: x -4.6..4.6, z -2.6..2.6 px, from his neck down to just above the root; head: x/z -4.6..4.6 px above the neck
		float bottom = root.y - 0.6f * s;
		if (m.y > bottom) {
			return q;
		}
		float hx = (m.y < 0f ? 4.6f : 4.6f) * s, hz = (m.y < 0f ? 4.6f : 2.6f) * s;
		if (Math.abs(m.x) >= hx || Math.abs(m.z) >= hz) {
			return q;
		}
		float ox = hx - Math.abs(m.x), oz = hz - Math.abs(m.z), oy = bottom - m.y;
		if (oy < ox && oy < oz) {
			m.y = bottom; // out under the waist
		} else if (oz <= ox) {
			m.z = Math.copySign(hz, m.z == 0f ? 1f : m.z); // out of the back (or the front)
		} else {
			m.x = Math.copySign(hx, m.x == 0f ? 1f : m.x);
		}
		return at.add(toWorld(m, yaw));
	}

	// ---------------------------------------------------------------- drawing

	/** In the model space the player renderer hands {@code renderExtras} (before any bone). */
	static void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model, float partial,
			int alpha) {
		Tail t = TAILS.computeIfAbsent(p, k -> new Tail());
		// where the tail leaves him this frame: under the body bone (it leans and twists with his poses)
		PoseStack bp = new PoseStack();
		model.body.translateAndRotate(bp);
		Vector3f root = bp.last().pose().transformPosition(new Vector3f(0f, ROOT_Y, 0f));
		t.rootModel = root;
		Minecraft mc = Minecraft.getInstance();
		if (!t.started || (p == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson())) {
			return;
		}
		float yaw = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot);
		Vec3 at = p.getPosition(partial);
		Vector3f[] m = new Vector3f[N];
		m[0] = root;
		for (int i = 1; i < N; i++) {
			Vec3 w = t.last[i].lerp(t.pos[i], partial);
			m[i] = toModel(w.subtract(at), yaw);
		}
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(SHEET));
		Vector3f up = new Vector3f(0f, 1f, 0f);
		for (int i = 0; i < N - 1; i++) {
			Vector3f d = new Vector3f(m[i + 1]).sub(m[i]);
			float len = d.length();
			if (len < 1e-4f) {
				continue;
			}
			pose.pushPose();
			pose.translate(m[i].x, m[i].y, m[i].z);
			pose.mulPose(new Quaternionf().rotationTo(up, d.div(len)));
			float k = i / (float) (N - 2);
			int a = (int) (alpha * Mth.clamp(1.15f - 0.75f * k * k, 0.3f, 1f));
			link(pose, vc, light, W[i] / 32f, D[i] / 32f, W[i + 1] / 32f, D[i + 1] / 32f, len, i, a);
			pose.popPose();
		}
	}

	/**
	 * One link along +y (blocks): a frustum from half-sizes (w0, d0) at its root to (w1, d1) at its end, run a little into the
	 * next one so the joints never gap. Its rows of the sheet are this link's share of the skin's 12-row tail.
	 */
	private static void link(PoseStack pose, VertexConsumer vc, int light, float w0, float d0, float w1, float d1, float len, int i, int a) {
		float y0 = -0.02f, y1 = len + 0.02f;
		float v0 = i * 12f / (N - 1) / 64f, v1 = (i + 1) * 12f / (N - 1) / 64f;
		int c = (a << 24) | 0xFFFFFF;
		PoseStack.Pose last = pose.last();
		// front (-z: his front), back (+z), his right side (-x), his left (+x)
		AlienParts.quad(vc, last, c, light, -w0, y0, -d0, w0, y0, -d0, w1, y1, -d1, -w1, y1, -d1, 0, 0, -1, 0f, v0, 8f / 64f, v1);
		AlienParts.quad(vc, last, c, light, w0, y0, d0, -w0, y0, d0, -w1, y1, d1, w1, y1, d1, 0, 0, 1, 8f / 64f, v0, 16f / 64f, v1);
		AlienParts.quad(vc, last, c, light, -w0, y0, d0, -w0, y0, -d0, -w1, y1, -d1, -w1, y1, d1, -1, 0, 0, 16f / 64f, v0, 20f / 64f, v1);
		AlienParts.quad(vc, last, c, light, w0, y0, -d0, w0, y0, d0, w1, y1, d1, w1, y1, -d1, 1, 0, 0, 20f / 64f, v0, 24f / 64f, v1);
		// the end cap (the root cap is inside him / the last link)
		AlienParts.quad(vc, last, c, light, -w1, y1, -d1, w1, y1, -d1, w1, y1, d1, -w1, y1, d1, 0, 1, 0, 0f, v1 - 1f / 64f, 8f / 64f, v1);
	}
}
