package com.projecthero.mod.client.omnitrix.alien;

import java.util.Map;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.feedback.FeedbackKit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * v0.17.2 (user: "Give him the tail and long ear things with the plug things at the end please, look at the references and make sure
 * the his tail and ear things look good and that the plugs at the end look really accurate aswell please"): Feedback's two antenna
 * cables and his tail, each ending in a gold plug.
 * <p>
 * <b>The cords.</b> Three ropes of {@link #N} points simulated in the WORLD each client tick (verlet, as {@code GhostfreakTail}): the
 * first point is pinned to the head (the ears) or the small of the back (the tail); the rest keep their own momentum, so the cords
 * lag, swing out on turns and trail behind when he runs or leaps, while a springy pull toward a resting shape -- the big loose arcs of
 * the show (docs/reference/feedback) -- brings them back. The ears' resting shapes ride the HEAD bone (they turn and nod with it), the
 * tail's the body. They are pushed out of blocks (they lie on the floor, bend round walls) and out of his torso. Moves bend them:
 * Absorb flares all three forward with the plugs facing out, Plug Whip sweeps the tail round in front of him, Plug In stretches the
 * cords out to what they plug into.
 * <p>
 * <b>Drawn</b> as smooth round tubes: the points through a Catmull-Rom spline, {@link #SUB} rings a link, {@link #SIDES} sides a
 * ring, carried along by a parallel-transport frame (no twisting), with smooth normals, in the cords' blue-black.
 * <p>
 * (v0.17.2 round 2, user: "remove the plugs on his fingers please ... also remove the plugs from his toes please": no finger or toe caps.)
 * <p>
 * <b>The plugs</b> follow the reference closely: a round gold barrel (12 sides) a little over twice the cord's width, the cord going
 * into a dark socket in its back face, three black ring grooves splitting it into four bands, a bevelled front, and two thin flat
 * gold prongs side by side out of the front. They light up as his Charge fills (and flare while he absorbs). While he drains
 * something, beads of light run up the cords into him.
 */
public final class FeedbackCables {
	private static final ResourceLocation WHITE = ProjectHeroMod.id("textures/entity/mutation/white.png");

	/** Points a rope. */
	static final int N = 14;
	/** Spline rings a link, sides a ring. */
	private static final int SUB = 4, SIDES = 8;

	/** The cords' blue-black (the skin's 1f2329 / 23272b, a touch bluer, as the show's rim light). */
	static final int CORD = 0xFF1F242B;
	/** The barrel's ring grooves: thin dark-brown cut lines, as the reference draws them. */
	static final int GROOVE_LINE = 0xFF5C3F16;
	/** The plugs' golds: the barrel, the darker bevel, the paler prongs, the grooves, the socket. */
	static final int GOLD = 0xFFD6A847, GOLD_DARK = 0xFFA87E2E, GOLD_PALE = 0xFFE6C064, GROOVE = 0xFF2A1F12, SOCKET = 0xFF0B0B0C;

	// ---------------------------------------------------------------- the ropes' resting shapes (bone pixels: y down, +z his back)

	/** Head-bone control points (pixels) of his left ear (+x): up off the back of his head, over in a high arc, falling to his hip. */
	private static final float[][] EAR_L = { { 2.0f, -7.6f, 2.6f }, { 3.0f, -12.6f, 3.8f }, { 6.4f, -17.0f, 5.6f }, { 11.6f, -17.6f, 7.4f }, { 16.0f, -11.6f, 8.4f },
			{ 17.6f, -3.0f, 8.0f }, { 17.2f, 5.0f, 6.0f }, { 15.4f, 10.4f, 3.6f } };
	/** His right ear (-x): out the back of his head in a lower loop, hanging to his hip. */
	private static final float[][] EAR_R = { { -2.0f, -7.0f, 3.4f }, { -4.6f, -9.4f, 7.0f }, { -9.0f, -8.6f, 10.6f }, { -12.8f, -3.0f, 11.6f },
			{ -14.4f, 4.0f, 9.6f }, { -14.0f, 9.4f, 6.4f } };
	/** Body-bone control points: from the small of his back, out and down in a long curve to the ground beside him. */
	private static final float[][] TAIL = { { 0.0f, 9.6f, 2.3f }, { 0.5f, 10.6f, 5.4f }, { 2.6f, 16.6f, 10.0f }, { 5.4f, 21.0f, 12.2f },
			{ 8.6f, 23.4f, 11.0f }, { 11.4f, 23.6f, 7.4f }, { 12.6f, 23.4f, 3.6f } };

	/** Absorb: the cords flared forward round him, plugs facing out ahead. */
	private static final float[][] EAR_L_ABSORB = { { 2.0f, -7.6f, 2.6f }, { 4.6f, -11.4f, 3.0f }, { 9.4f, -12.2f, -0.6f }, { 13.4f, -9.0f, -4.6f },
			{ 15.4f, -4.0f, -8.0f }, { 15.8f, 0.6f, -11.4f } };
	private static final float[][] EAR_R_ABSORB = { { -2.0f, -7.0f, 3.4f }, { -4.6f, -10.8f, 3.4f }, { -9.4f, -11.6f, -0.4f }, { -13.4f, -8.4f, -4.6f },
			{ -15.4f, -3.4f, -8.0f }, { -15.8f, 1.2f, -11.4f } };
	private static final float[][] TAIL_ABSORB = { { 0.0f, 10.4f, 1.8f }, { 1.0f, 12.0f, 6.0f }, { 4.6f, 11.6f, 10.4f }, { 10.0f, 9.6f, 10.6f },
			{ 14.0f, 7.0f, 6.0f }, { 15.4f, 5.4f, 0.0f }, { 15.0f, 4.6f, -5.4f } };

	/** Cord radius (pixels) at the root and tip of each rope: the tail swells into his back. */
	private static final float[][] RADIUS = { { 0.66f, 0.5f }, { 0.66f, 0.5f }, { 1.3f, 0.58f } };
	private static final int EAR_LEFT = 0, EAR_RIGHT = 1, TAILI = 2;
	/**
	 * v0.17.2 round 2 (user: "the tail and ears react way too much to movement, they jiggle a LOT and shake around a lot and go inside his
	 * body sometimes, i like this detail but tone it down a bit please"): the first points of each cord are rigid with the bone (the ears
	 * 2, the tail 3), so the bases never swing into him.
	 * Round 3 (user: "you made feedbacks tails not move a lot iwht movement, thats not what i wanted I just wanted it to move slightly
	 * less bc it was too flappy and wiggly and woggly anf shaky, please give it more movement now"): the rest swing freely again -- soft
	 * springs, so the tips lag and sweep -- and {@link #DAMP} is what takes the shake out: a swing settles instead of ringing on.
	 * {@link #CARRY} of his own movement still comes along with them, so they swing a little less far than round 1's.
	 */
	private static final int[] PINNED = { 2, 2, 3 };
	private static final double DAMP = 0.76, CARRY = 0.3;
	private static final float PULL_ROOT = 0.42f, PULL_TIP = 0.12f, SWAY = 0.75f;

	// ---------------------------------------------------------------- the plug (pixels)
	/** The reference's proportions: the barrel ~2.6x as long as it is wide, the prongs ~0.3 of the barrel. */
	static final float PLUG_R = 1.25f, PLUG_LEN = 6.5f, PRONG_LEN = 2.0f;
	/** Ring grooves along the barrel (fractions of its length from the back) and their half width. */
	private static final float[] GROOVES = { 0.42f, 0.62f, 0.81f };
	private static final float GROOVE_W = 0.016f;

	// ---------------------------------------------------------------- per player

	private static final class Rope {
		final int index;
		final Vec3[] pos = new Vec3[N], prev = new Vec3[N], last = new Vec3[N];
		/** Rest shapes resampled to N points (bone pixels), and the link lengths (model blocks). */
		final Vector3f[] rest, restAbsorb;
		final float[] len = new float[N - 1];
		float stretch = 1f;
		/** Drawn this frame (model blocks), for the pulses and the plug glow position. */
		final Vector3f[] model = new Vector3f[N];

		Rope(int index, float[][] ctrl, float[][] absorb) {
			this.index = index;
			this.rest = resample(ctrl);
			this.restAbsorb = resample(absorb);
			for (int i = 0; i < N - 1; i++) {
				len[i] = rest[i].distance(rest[i + 1]) / 16f;
			}
			for (int i = 0; i < N; i++) {
				model[i] = new Vector3f();
			}
		}
	}

	private static final class Cables {
		final Rope[] ropes = { new Rope(EAR_LEFT, EAR_L, EAR_L_ABSORB), new Rope(EAR_RIGHT, EAR_R, EAR_R_ABSORB), new Rope(TAILI, TAIL, TAIL_ABSORB) };
		final Matrix4f head = new Matrix4f(), body = new Matrix4f();
		boolean posed, started;
		float absorb, whip = -1f;
		int plugTicks, lastAnimStart = -1;
		float yaw;
	}

	private static final Map<AbstractClientPlayer, Cables> CABLES = new WeakHashMap<>();

	private FeedbackCables() {
	}

	// ---------------------------------------------------------------- the rest curves

	/** A Catmull-Rom curve through {@code ctrl} resampled to {@link #N} points evenly spaced along it. */
	private static Vector3f[] resample(float[][] ctrl) {
		int segs = ctrl.length - 1, per = 40;
		Vector3f[] dense = new Vector3f[segs * per + 1];
		for (int s = 0; s < segs; s++) {
			float[] p0 = ctrl[Math.max(0, s - 1)], p1 = ctrl[s], p2 = ctrl[s + 1], p3 = ctrl[Math.min(ctrl.length - 1, s + 2)];
			for (int k = 0; k < per; k++) {
				dense[s * per + k] = cr(p0, p1, p2, p3, k / (float) per);
			}
		}
		float[] l = ctrl[ctrl.length - 1];
		dense[dense.length - 1] = new Vector3f(l[0], l[1], l[2]);
		float[] acc = new float[dense.length];
		for (int i = 1; i < dense.length; i++) {
			acc[i] = acc[i - 1] + dense[i].distance(dense[i - 1]);
		}
		Vector3f[] out = new Vector3f[N];
		int j = 0;
		for (int i = 0; i < N; i++) {
			float want = acc[acc.length - 1] * i / (N - 1);
			while (j < acc.length - 2 && acc[j + 1] < want) {
				j++;
			}
			float span = acc[j + 1] - acc[j];
			float t = span < 1e-6f ? 0f : (want - acc[j]) / span;
			out[i] = new Vector3f(dense[j]).lerp(dense[j + 1], Mth.clamp(t, 0f, 1f));
		}
		return out;
	}

	private static Vector3f cr(float[] p0, float[] p1, float[] p2, float[] p3, float t) {
		float t2 = t * t, t3 = t2 * t;
		Vector3f v = new Vector3f();
		float[] o = new float[3];
		for (int c = 0; c < 3; c++) {
			o[c] = 0.5f * ((2 * p1[c]) + (-p0[c] + p2[c]) * t + (2 * p0[c] - 5 * p1[c] + 4 * p2[c] - p3[c]) * t2 + (-p0[c] + 3 * p1[c] - 3 * p2[c] + p3[c]) * t3);
		}
		return v.set(o[0], o[1], o[2]);
	}

	private static Vec3 crv(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double t) {
		double t2 = t * t, t3 = t2 * t;
		return p1.scale(2).add(p2.subtract(p0).scale(t)).add(p0.scale(2).subtract(p1.scale(5)).add(p2.scale(4)).subtract(p3).scale(t2))
				.add(p0.scale(-1).add(p1.scale(3)).subtract(p2.scale(3)).add(p3).scale(t3)).scale(0.5);
	}

	// ---------------------------------------------------------------- model space <-> world

	/** The player renderer's model-space-to-world factor: the scale attribute times PlayerRenderer's 0.9375. */
	private static float k(AbstractClientPlayer p) {
		return p.getScale() * 0.9375f;
	}

	private static Vec3 toWorld(Vector3f m, float bodyYaw, float k) {
		Vector3f v = new Vector3f(-m.x * k, (1.501f - m.y) * k, m.z * k);
		new Quaternionf().rotationY((180f - bodyYaw) * Mth.DEG_TO_RAD).transform(v);
		return new Vec3(v.x, v.y, v.z);
	}

	private static Vector3f toModel(Vec3 w, float bodyYaw, float k) {
		Vector3f v = new Vector3f((float) w.x, (float) w.y, (float) w.z);
		new Quaternionf().rotationY(-(180f - bodyYaw) * Mth.DEG_TO_RAD).transform(v);
		return new Vector3f(-v.x / k, 1.501f - v.y / k, v.z / k);
	}

	// ---------------------------------------------------------------- what the cords plug into (synced state)

	/** Where the cords of rope {@code r} are plugged right now (world), or null. */
	@Nullable
	private static Vec3 plugTarget(AbstractClientPlayer p, int r, float partial) {
		int flags = Omnitrix.state(p).kitFlags;
		if ((flags & FeedbackKit.F_PLUG) == 0) {
			return null;
		}
		int ent = FeedbackKit.plugEntity(p);
		if (ent > 0) {
			Entity e = p.level().getEntity(ent - 1);
			if (e == null) {
				return null;
			}
			// the three plugs bite in at its chest, a little apart
			double spread = e.getBbWidth() * 0.3;
			Vec3 c = e.getPosition(partial).add(0, e.getBbHeight() * (r == TAILI ? 0.4 : 0.62), 0);
			Vec3 side = new Vec3(c.z - p.getZ(), 0, p.getX() - c.x);
			side = side.lengthSqr() < 1e-6 ? Vec3.ZERO : side.normalize().scale(spread);
			return r == EAR_LEFT ? c.subtract(side) : r == EAR_RIGHT ? c.add(side) : c;
		}
		BlockPos b = FeedbackKit.plugBlock(p);
		if (b == null) {
			return null;
		}
		VoxelShape s = p.level().getBlockState(b).getShape(p.level(), b);
		Vec3 c = s.isEmpty() ? Vec3.atCenterOf(b) : s.bounds().getCenter().add(b.getX(), b.getY(), b.getZ());
		return c.add((r - 1) * 0.12, 0, 0);
	}

	// ---------------------------------------------------------------- the simulation (client tick)

	public static void tick(AbstractClientPlayer p) {
		Cables c = CABLES.computeIfAbsent(p, k -> new Cables());
		if (!c.posed) {
			return; // never drawn yet: no bone matrices
		}
		var s = Omnitrix.state(p);
		int flags = s.kitFlags;
		c.absorb = Mth.approach(c.absorb, (flags & FeedbackKit.F_ABSORB) != 0 ? 1f : 0f, 0.25f);
		// Plug Whip: a fresh whip animation starts the sweep
		if (s.animId == FeedbackKit.ANIM_WHIP && (int) s.animStart != c.lastAnimStart) {
			c.lastAnimStart = (int) s.animStart;
			c.whip = 0f;
		}
		if (c.whip >= 0f) {
			c.whip += 1f / 9f;
			if (c.whip > 1.6f) {
				c.whip = -1f;
			}
		}
		boolean plugging = (flags & FeedbackKit.F_PLUG) != 0;
		c.plugTicks = plugging ? c.plugTicks + 1 : 0;
		float yaw = p.yBodyRot;
		float k = k(p);
		Vec3 at = p.position();
		float age = p.tickCount;
		boolean first = !c.started;
		for (Rope r : c.ropes) {
			Matrix4f bone = r.index == TAILI ? c.body : c.head;
			// the resting shape in the world this tick
			Vec3[] rest = new Vec3[N];
			for (int i = 0; i < N; i++) {
				Vector3f m = localRest(c, r, i, age);
				Vector3f mm = bone.transformPosition(new Vector3f(m.x / 16f, m.y / 16f, m.z / 16f));
				rest[i] = at.add(toWorld(mm, yaw, k));
			}
			Vec3 target = plugTarget(p, r.index, 1f);
			if (target != null) {
				// stretched out to it: an easy arc from the root, reaching over the first few ticks
				float reach = Mth.clamp(c.plugTicks / 4f, 0f, 1f);
				double d = target.distanceTo(rest[0]);
				float base = 0f;
				for (float l : r.len) {
					base += l * k;
				}
				r.stretch = Mth.lerp(0.5f, r.stretch, (float) Math.max(1.0, d * 1.04 / base));
				for (int i = 1; i < N; i++) {
					double t = i / (double) (N - 1);
					Vec3 line = rest[0].lerp(target, t).add(0, Math.sin(t * Math.PI) * Math.min(0.5, d * 0.08), 0);
					rest[i] = rest[i].lerp(line, reach);
				}
			} else {
				r.stretch = Mth.lerp(0.25f, r.stretch, 1f);
			}
			if (first || r.pos[0] == null || r.pos[1].distanceToSqr(rest[0]) > 16.0) {
				for (int i = 0; i < N; i++) {
					r.pos[i] = rest[i];
					r.prev[i] = rest[i];
					r.last[i] = rest[i];
				}
			}
			for (int i = 0; i < N; i++) {
				r.last[i] = r.pos[i];
			}
			// some of his own movement carries the cords along with him, the rest they trail
			Vec3 carry = rest[0].subtract(r.pos[0]).scale(CARRY);
			int pin = target != null ? 1 : PINNED[r.index];
			for (int i = 0; i < N; i++) {
				if (i < pin) {
					r.prev[i] = r.pos[i];
					r.pos[i] = rest[i]; // rigid with the bone
				} else {
					r.pos[i] = r.pos[i].add(carry);
					r.prev[i] = r.prev[i].add(carry);
				}
			}
			boolean stiff = target != null || (r.index == TAILI && c.whip >= 0f);
			for (int i = pin; i < N; i++) {
				float t = i / (float) (N - 1);
				Vec3 vel = r.pos[i].subtract(r.prev[i]).scale(DAMP);
				// sprung toward the rest shape: firm near the root, softer toward the tip
				double pull = stiff ? 0.5 : Mth.lerp(t, PULL_ROOT, PULL_TIP);
				Vec3 acc = rest[i].subtract(r.pos[i]).scale(pull).add(0, -0.004 * t, 0);
				r.prev[i] = r.pos[i];
				r.pos[i] = r.pos[i].add(vel).add(acc);
			}
			if (target != null && c.plugTicks >= 4) {
				r.pos[N - 1] = target;
			}
			for (int pass = 0; pass < 4; pass++) {
				for (int i = pin; i < N; i++) {
					Vec3 d = r.pos[i].subtract(r.pos[i - 1]);
					double l = d.length();
					double want = r.len[i - 1] * k * r.stretch;
					if (l > 1e-6) {
						r.pos[i] = r.pos[i - 1].add(d.scale(want / l));
					}
				}
				for (int i = pin; i < N; i++) {
					if (target == null || i < N - 2) {
						r.pos[i] = outOfBlocks(p, r.pos[i], 0.05);
					}
					r.pos[i] = outOfBody(r.pos[i], at, yaw, k, RADIUS[r.index][i < 3 ? 0 : 1] / 16f);
				}
			}
		}
		c.started = true;
		// a crackle off the plugs when he is brimming with Charge (never in your own eyes)
		Minecraft mc = Minecraft.getInstance();
		int charge = FeedbackKit.charge(p);
		if (charge >= 60 && !(p == mc.player && mc.options.getCameraType().isFirstPerson()) && p.getRandom().nextInt(charge >= 90 ? 3 : 8) == 0) {
			Rope r = c.ropes[p.getRandom().nextInt(3)];
			Vec3 tip = r.pos[N - 1];
			p.level().addParticle(FeedbackKit.SPARK, tip.x, tip.y, tip.z, 0, 0.02, 0);
		}
	}

	/** Rope {@code r}'s resting point {@code i} in its bone's pixels this tick: the rest shape, Absorb's flare, Plug Whip's sweep, a gentle sway. */
	private static Vector3f localRest(Cables c, Rope r, int i, float age) {
		Vector3f m = new Vector3f(r.rest[i]);
		if (c.absorb > 0f) {
			m.lerp(r.restAbsorb[i], c.absorb * c.absorb * (3 - 2 * c.absorb));
		}
		if (r.index == TAILI && c.whip >= 0f) {
			m = whipRest(r, i, c.whip);
		}
		// a slow, gentle idle sway, growing toward the tip, out of step between the ropes
		float t = i / (float) (N - 1);
		float ph = r.index * 2.1f;
		m.add(Mth.sin(age * 0.07f + ph - i * 0.3f) * 0.9f * SWAY * t, Mth.cos(age * 0.06f + ph - i * 0.25f) * 0.5f * SWAY * t,
				Mth.sin(age * 0.05f + ph * 1.7f) * 0.6f * SWAY * t);
		return m;
	}

	/** Plug Whip's tail ({@code w} 0..1.6): swept round from behind him, across his front at waist height, and let go. */
	private static Vector3f whipRest(Rope r, int i, float w) {
		Vector3f root = r.rest[0];
		float t = i / (float) (N - 1);
		float len = 26f * t;
		// the lash: the cord swings from straight back (0) round his left side to his front and on to his right
		float sweep = Mth.clamp(w, 0f, 1f);
		float ang = (float) (Math.PI * (0.15 + 1.25 * (1 - Math.pow(1 - sweep, 2.2)))) - t * 0.5f;
		float lift = 1.0f - Mth.clamp((w - 1f) / 0.6f, 0f, 1f);
		Vector3f out = new Vector3f(root.x + Mth.sin(ang) * len, root.y + (2.0f - 3.0f * t) * lift + (1 - lift) * 10f * t, root.z + Mth.cos(ang) * len);
		return w > 1f ? out.lerp(r.rest[i], 1 - lift) : out;
	}

	/** {@code q} pushed out of every block collision box it is inside (radius {@code r}), the shortest way out. */
	private static Vec3 outOfBlocks(AbstractClientPlayer p, Vec3 q, double r) {
		for (int it = 0; it < 3; it++) {
			AABB box = new AABB(q.x - r, q.y - r, q.z - r, q.x + r, q.y + r, q.z + r);
			boolean moved = false;
			for (VoxelShape shape : p.level().getBlockCollisions(null, box)) {
				for (AABB b : shape.toAabbs()) {
					if (!b.intersects(box)) {
						continue;
					}
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

	/**
	 * {@code q} kept out of his body (model-space boxes padded by the cord's radius {@code rad}): the head, the torso and the legs. v0.17.2
	 * round 2 (user: "go inside his body sometimes ... sometimes you can see the tail poking out of at the bottom of his torso"): out of the
	 * torso or the legs a cord always leaves by the back or a side -- never through his front or out under his torso.
	 */
	private static Vec3 outOfBody(Vec3 q, Vec3 at, float yaw, float k, float rad) {
		Vector3f m = toModel(q.subtract(at), yaw, k);
		float s = 1f / 16f;
		float x0, z0;
		boolean head;
		if (m.y >= -8.6f * s - rad && m.y < 0f) {
			x0 = 4.6f * s;
			z0 = 4.6f * s;
			head = true;
		} else if (m.y >= 0f && m.y <= 24.4f * s) {
			x0 = 4.4f * s; // torso and legs share a width (the legs are 2 x 4 px side by side)
			z0 = 2.4f * s;
			head = false;
		} else {
			return q;
		}
		x0 += rad;
		z0 += rad;
		if (Math.abs(m.x) >= x0 || Math.abs(m.z) >= z0) {
			return q;
		}
		float ox = x0 - Math.abs(m.x), back = z0 - m.z, front = z0 + m.z;
		if (head && front < ox && front < back) {
			m.z = -z0;
		} else if (back <= ox) {
			m.z = z0; // out of his back
		} else {
			m.x = Math.copySign(x0, m.x == 0f ? 1f : m.x);
		}
		return at.add(toWorld(m, yaw, k));
	}

	// ---------------------------------------------------------------- drawing

	/** In the model space the player renderer hands {@code renderExtras} (before any bone). */
	public static void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model, float partial,
			float age) {
		Cables c = CABLES.computeIfAbsent(p, q -> new Cables());
		PoseStack bp = new PoseStack();
		model.head.translateAndRotate(bp);
		c.head.set(bp.last().pose());
		bp = new PoseStack();
		model.body.translateAndRotate(bp);
		c.body.set(bp.last().pose());
		c.posed = true;
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
		Minecraft mc = Minecraft.getInstance();
		if (!c.started || (p == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson())) {
			return;
		}
		float yaw = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot);
		Vec3 at = p.getPosition(partial);
		float k = k(p);
		int charge = FeedbackKit.charge(p);
		float glow = Math.max(c.absorb, charge / (float) FeedbackKit.MAX_CHARGE * 0.8f);
		boolean draining = (Omnitrix.state(p).kitFlags & FeedbackKit.F_PLUG) != 0;
		for (Rope r : c.ropes) {
			Matrix4f bone = r.index == TAILI ? c.body : c.head;
			Vector3f[] m = r.model;
			// the root rides the bone exactly this frame; the rest is the simulation, interpolated
			Vector3f root = bone.transformPosition(new Vector3f(r.rest[0].x / 16f, r.rest[0].y / 16f, r.rest[0].z / 16f));
			m[0].set(root);
			int pin = plugTarget(p, r.index, partial) != null ? 1 : PINNED[r.index];
			for (int i = 1; i < N; i++) {
				if (i < pin) {
					Vector3f l = localRest(c, r, i, age); // rigid with the bone, exactly this frame
					m[i].set(bone.transformPosition(new Vector3f(l.x / 16f, l.y / 16f, l.z / 16f)));
					continue;
				}
				Vec3 w = r.last[i].lerp(r.pos[i], partial);
				m[i].set(toModel(w.subtract(at), yaw, k));
			}
			cord(pose, vc, light, r, m);
		}
		// emissive: the plug tips by Charge, and the beads running up the cords while he drains something
		VertexConsumer em = buffers.getBuffer(RenderType.eyes(WHITE));
		for (Rope r : c.ropes) {
			PlugFrame f = plugFrame(r.model);
			if (glow > 0.03f) {
				plugGlow(pose, em, f, glow, age);
			}
			if (draining || c.absorb > 0.5f) {
				pulses(pose, em, r.model, age, r.index);
			}
		}
	}

	// ---------------------------------------------------------------- the tube

	/** The rope's points through a Catmull-Rom spline: SUB rings a link. */
	private static Vector3f[] spline(Vector3f[] m) {
		int rings = (N - 1) * SUB + 1;
		Vector3f[] out = new Vector3f[rings];
		for (int i = 0; i < N - 1; i++) {
			Vector3f p0 = m[Math.max(0, i - 1)], p1 = m[i], p2 = m[i + 1], p3 = m[Math.min(N - 1, i + 2)];
			for (int s = 0; s < SUB; s++) {
				out[i * SUB + s] = cr(new float[] { p0.x, p0.y, p0.z }, new float[] { p1.x, p1.y, p1.z }, new float[] { p2.x, p2.y, p2.z },
						new float[] { p3.x, p3.y, p3.z }, s / (float) SUB);
			}
		}
		out[rings - 1] = new Vector3f(m[N - 1]);
		return out;
	}

	private static void cord(PoseStack pose, VertexConsumer vc, int light, Rope r, Vector3f[] m) {
		Vector3f[] c = spline(m);
		int rings = c.length;
		Vector3f[] t = new Vector3f[rings];
		for (int i = 0; i < rings; i++) {
			Vector3f a = c[Math.max(0, i - 1)], b = c[Math.min(rings - 1, i + 1)];
			t[i] = new Vector3f(b).sub(a);
			if (t[i].lengthSquared() < 1e-10f) {
				t[i].set(0, 1, 0);
			}
			t[i].normalize();
		}
		// a parallel-transport frame: no twisting along the cord
		Vector3f n = perpendicular(t[0]);
		Vector3f[][] ring = new Vector3f[rings][SIDES];
		Vector3f[][] nor = new Vector3f[rings][SIDES];
		float r0 = RADIUS[r.index][0], r1 = RADIUS[r.index][1];
		for (int i = 0; i < rings; i++) {
			if (i > 0) {
				n = transport(n, t[i - 1], t[i]);
			}
			Vector3f b = new Vector3f(t[i]).cross(n).normalize();
			float u = i / (float) (rings - 1);
			// the tail tapers fast out of his back, then runs even; the ears barely taper
			float rad = (r.index == TAILI ? Mth.lerp(Math.min(1f, u / 0.35f), r0, r1) : Mth.lerp(u, r0, r1)) / 16f;
			for (int s = 0; s < SIDES; s++) {
				double a = s * Math.PI * 2 / SIDES;
				Vector3f d = new Vector3f(n).mul((float) Math.cos(a)).add(new Vector3f(b).mul((float) Math.sin(a)));
				nor[i][s] = d;
				ring[i][s] = new Vector3f(d).mul(rad).add(c[i]);
			}
		}
		PoseStack.Pose last = pose.last();
		for (int i = 0; i < rings - 1; i++) {
			for (int s = 0; s < SIDES; s++) {
				int s2 = (s + 1) % SIDES;
				quadN(vc, last, CORD, light, ring[i][s], ring[i][s2], ring[i + 1][s2], ring[i + 1][s], nor[i][s], nor[i][s2], nor[i + 1][s2], nor[i + 1][s]);
			}
		}
		// the plug on the end, along the cord's last direction, in the transported frame
		plug(pose, vc, light, c[rings - 1], t[rings - 1], n);
	}

	private static Vector3f perpendicular(Vector3f t) {
		Vector3f a = Math.abs(t.y) < 0.9f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
		return a.cross(t, new Vector3f()).normalize();
	}

	/** {@code n} carried from tangent {@code a} to tangent {@code b} by the smallest rotation. */
	private static Vector3f transport(Vector3f n, Vector3f a, Vector3f b) {
		Vector3f out = new Quaternionf().rotationTo(a, b).transform(new Vector3f(n));
		// re-orthogonalise
		out.sub(new Vector3f(b).mul(out.dot(b)));
		return out.lengthSquared() < 1e-10f ? perpendicular(b) : out.normalize();
	}

	// ---------------------------------------------------------------- the plug

	private record PlugFrame(Vector3f at, Vector3f t, Vector3f n, Vector3f b) {
	}

	private static PlugFrame plugFrame(Vector3f[] m) {
		Vector3f t = new Vector3f(m[N - 1]).sub(m[N - 2]);
		if (t.lengthSquared() < 1e-10f) {
			t.set(0, 1, 0);
		}
		t.normalize();
		Vector3f n = perpendicular(t);
		return new PlugFrame(new Vector3f(m[N - 1]), t, n, new Vector3f(t).cross(n).normalize());
	}

	/** A point in the plug's frame: {@code s} along it from its back face, (x, y) across (pixels). */
	private static Vector3f pf(Vector3f at, Vector3f t, Vector3f n, Vector3f b, float s, float x, float y) {
		return new Vector3f(at).add(new Vector3f(t).mul(s / 16f)).add(new Vector3f(n).mul(x / 16f)).add(new Vector3f(b).mul(y / 16f));
	}

	/**
	 * The plug at the cord's end {@code at}, facing {@code t}: the cord runs a little way into a dark socket in the barrel's back face;
	 * the barrel (12 sides, {@link #PLUG_R}) has three black grooves and a bevelled front; two flat prongs stand out of the front.
	 */
	static void plug(PoseStack pose, VertexConsumer vc, int light, Vector3f at, Vector3f tIn, Vector3f nIn) {
		Vector3f t = new Vector3f(tIn).normalize();
		Vector3f n = new Vector3f(nIn).sub(new Vector3f(t).mul(nIn.dot(t)));
		n = n.lengthSquared() < 1e-8f ? perpendicular(t) : n.normalize();
		Vector3f b = new Vector3f(t).cross(n).normalize();
		PoseStack.Pose last = pose.last();
		int sides = 12;
		float R = PLUG_R, L = PLUG_LEN;
		// the barrel's profile, back face to front: a bevelled back edge, four gold bands split by three dark grooves (each a little
		// step in, so it reads as a cut ring), and a bevelled front
		float g = GROOVE_W * L;
		float edge = 0.25f;
		frustum(vc, last, light, at, t, n, b, 0f, R * 0.92f, edge, R, GOLD_DARK, sides);
		float s = edge;
		for (float q : GROOVES) {
			frustum(vc, last, light, at, t, n, b, s, R, q * L - g, R, GOLD, sides);
			ringFace(vc, last, light, at, t, n, b, q * L - g, R * 0.94f, R, 1f, GROOVE_LINE, sides);
			frustum(vc, last, light, at, t, n, b, q * L - g, R * 0.94f, q * L + g, R * 0.94f, GROOVE_LINE, sides);
			ringFace(vc, last, light, at, t, n, b, q * L + g, R * 0.94f, R, -1f, GROOVE_LINE, sides);
			s = q * L + g;
		}
		frustum(vc, last, light, at, t, n, b, s, R, L * 0.9f, R, GOLD, sides);
		frustum(vc, last, light, at, t, n, b, L * 0.9f, R, L, R * 0.84f, GOLD_DARK, sides);
		// the back face: nearly all a black socket round the cord, a thin gold lip at its edge (the reference's dark back end)
		ringFace(vc, last, light, at, t, n, b, 0f, R * 0.8f, R * 0.92f, -1f, GOLD_DARK, sides);
		ringFace(vc, last, light, at, t, n, b, 0.02f, 0f, R * 0.8f, -1f, SOCKET, sides);
		// the front face
		ringFace(vc, last, light, at, t, n, b, L, 0f, R * 0.84f, 1f, GOLD_DARK, sides);
		// the two flat prongs, side by side across b, their flat faces toward each other
		float gap = R * 0.42f, thick = R * 0.15f, wide = R * 0.42f;
		for (int side = -1; side <= 1; side += 2) {
			float y0 = side * gap, y1 = side * (gap + thick);
			box(vc, last, light, at, t, n, b, L - 0.1f, L + PRONG_LEN, -wide / 2, wide / 2, Math.min(y0, y1), Math.max(y0, y1), GOLD_PALE);
		}
	}

	/** A frustum (open) along the plug from s0 (radius r0) to s1 (r1). */
	private static void frustum(VertexConsumer vc, PoseStack.Pose last, int light, Vector3f at, Vector3f t, Vector3f n, Vector3f b, float s0, float r0, float s1, float r1,
			int col, int sides) {
		float slope = (r0 - r1) / Math.max(1e-4f, s1 - s0);
		for (int i = 0; i < sides; i++) {
			double a0 = (i + 0.5) * Math.PI * 2 / sides, a1 = (i + 1.5) * Math.PI * 2 / sides;
			float c0 = (float) Math.cos(a0), z0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), z1 = (float) Math.sin(a1);
			Vector3f n0 = new Vector3f(n).mul(c0).add(new Vector3f(b).mul(z0)).add(new Vector3f(t).mul(slope)).normalize();
			Vector3f n1 = new Vector3f(n).mul(c1).add(new Vector3f(b).mul(z1)).add(new Vector3f(t).mul(slope)).normalize();
			quadN(vc, last, col, light, pf(at, t, n, b, s0, c0 * r0, z0 * r0), pf(at, t, n, b, s0, c1 * r0, z1 * r0), pf(at, t, n, b, s1, c1 * r1, z1 * r1),
					pf(at, t, n, b, s1, c0 * r1, z0 * r1), n0, n1, n1, n0);
		}
	}

	/** A flat annulus across the plug at {@code s} (inner radius {@code ri}, outer {@code ro}), facing {@code dir} along it. */
	private static void ringFace(VertexConsumer vc, PoseStack.Pose last, int light, Vector3f at, Vector3f t, Vector3f n, Vector3f b, float s, float ri, float ro,
			float dir, int col, int sides) {
		Vector3f nn = new Vector3f(t).mul(dir);
		for (int i = 0; i < sides; i++) {
			double a0 = (i + 0.5) * Math.PI * 2 / sides, a1 = (i + 1.5) * Math.PI * 2 / sides;
			float c0 = (float) Math.cos(a0), z0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), z1 = (float) Math.sin(a1);
			quadN(vc, last, col, light, pf(at, t, n, b, s, c0 * ro, z0 * ro), pf(at, t, n, b, s, c1 * ro, z1 * ro), pf(at, t, n, b, s, c1 * ri, z1 * ri),
					pf(at, t, n, b, s, c0 * ri, z0 * ri), nn, nn, nn, nn);
		}
	}

	/** A box in the plug's frame: s0..s1 along, x0..x1 across n, y0..y1 across b. */
	private static void box(VertexConsumer vc, PoseStack.Pose last, int light, Vector3f at, Vector3f t, Vector3f n, Vector3f b, float s0, float s1, float x0, float x1,
			float y0, float y1, int col) {
		Vector3f[] v = new Vector3f[8];
		for (int i = 0; i < 8; i++) {
			v[i] = pf(at, t, n, b, (i & 1) == 0 ? s0 : s1, (i & 2) == 0 ? x0 : x1, (i & 4) == 0 ? y0 : y1);
		}
		Vector3f nt = new Vector3f(t), nn = new Vector3f(n), nb = new Vector3f(b);
		Vector3f mt = new Vector3f(t).negate(), mn = new Vector3f(n).negate(), mb = new Vector3f(b).negate();
		quadN(vc, last, col, light, v[1], v[3], v[7], v[5], nt, nt, nt, nt); // tip
		quadN(vc, last, col, light, v[0], v[4], v[6], v[2], mt, mt, mt, mt);
		quadN(vc, last, col, light, v[2], v[6], v[7], v[3], nn, nn, nn, nn);
		quadN(vc, last, col, light, v[0], v[1], v[5], v[4], mn, mn, mn, mn);
		quadN(vc, last, col, light, v[4], v[5], v[7], v[6], nb, nb, nb, nb);
		quadN(vc, last, col, light, v[0], v[2], v[3], v[1], mb, mb, mb, mb);
	}

	/** The plug lit by his Charge: the prongs and the front face glow ({@code k} 0..1), flickering when he is full. */
	private static void plugGlow(PoseStack pose, VertexConsumer em, PlugFrame f, float k, float age) {
		float flick = k > 0.75f ? 0.85f + 0.15f * Mth.sin(age * 1.7f) : 1f;
		int c = argb(FeedbackKit.COLOUR, Mth.clamp(k * flick, 0f, 1f));
		PoseStack.Pose last = pose.last();
		float R = PLUG_R;
		float gap = R * 0.42f, thick = R * 0.15f, wide = R * 0.42f;
		for (int side = -1; side <= 1; side += 2) {
			float y0 = side * (gap - 0.06f), y1 = side * (gap + thick + 0.06f);
			box(em, last, LightTexture.FULL_BRIGHT, f.at, f.t, f.n, f.b, PLUG_LEN + PRONG_LEN * 0.4f, PLUG_LEN + PRONG_LEN + 0.08f, -wide / 2 - 0.06f, wide / 2 + 0.06f,
					Math.min(y0, y1), Math.max(y0, y1), c);
		}
		ringFace(em, last, LightTexture.FULL_BRIGHT, f.at, f.t, f.n, f.b, PLUG_LEN + 0.03f, 0f, R * 0.7f, 1f, argb(FeedbackKit.COLOUR, k * 0.6f), 12);
	}

	/** Beads of light running up the cord from the plug into him. */
	private static void pulses(PoseStack pose, VertexConsumer em, Vector3f[] m, float age, int idx) {
		Vector3f[] c = spline(m);
		PoseStack.Pose last = pose.last();
		for (int j = 0; j < 3; j++) {
			float u = 1f - ((age * 0.09f + j / 3f + idx * 0.17f) % 1f);
			float fi = u * (c.length - 1);
			int i = Math.min(c.length - 2, (int) fi);
			Vector3f q = new Vector3f(c[i]).lerp(c[i + 1], fi - i);
			float r = 0.95f / 16f;
			int col = argb(FeedbackKit.WHITE, 0.9f);
			// a little diamond (octahedron) of light
			Vector3f[] d = { new Vector3f(r, 0, 0), new Vector3f(-r, 0, 0), new Vector3f(0, r, 0), new Vector3f(0, -r, 0), new Vector3f(0, 0, r), new Vector3f(0, 0, -r) };
			int[][] faces = { { 0, 2, 4 }, { 2, 1, 4 }, { 1, 3, 4 }, { 3, 0, 4 }, { 2, 0, 5 }, { 1, 2, 5 }, { 3, 1, 5 }, { 0, 3, 5 } };
			for (int[] fc : faces) {
				Vector3f a = new Vector3f(q).add(d[fc[0]]), b = new Vector3f(q).add(d[fc[1]]), e = new Vector3f(q).add(d[fc[2]]);
				Vector3f nn = new Vector3f(d[fc[0]]).add(d[fc[1]]).add(d[fc[2]]).normalize();
				quadN(em, last, col, LightTexture.FULL_BRIGHT, a, b, e, e, nn, nn, nn, nn);
			}
		}
	}

	private static int argb(int rgb, float k) {
		int r = (int) (((rgb >> 16) & 255) * k), g = (int) (((rgb >> 8) & 255) * k), b = (int) ((rgb & 255) * k);
		return 0xFF000000 | (Mth.clamp(r, 0, 255) << 16) | (Mth.clamp(g, 0, 255) << 8) | Mth.clamp(b, 0, 255);
	}

	// ---------------------------------------------------------------- vertices

	private static void quadN(VertexConsumer vc, PoseStack.Pose p, int argb, int light, Vector3f a, Vector3f b, Vector3f c, Vector3f d, Vector3f na, Vector3f nb,
			Vector3f nc, Vector3f nd) {
		int al = (argb >>> 24) & 0xFF, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, bl = argb & 0xFF;
		vc.addVertex(p, a.x, a.y, a.z).setColor(r, g, bl, al).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, na.x, na.y, na.z);
		vc.addVertex(p, b.x, b.y, b.z).setColor(r, g, bl, al).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nb.x, nb.y, nb.z);
		vc.addVertex(p, c.x, c.y, c.z).setColor(r, g, bl, al).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nc.x, nc.y, nc.z);
		vc.addVertex(p, d.x, d.y, d.z).setColor(r, g, bl, al).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nd.x, nd.y, nd.z);
	}

	/** The world position of rope {@code r}'s plug tip (0, 1 the ears, 2 the tail), or null if not simulated yet. */
	@Nullable
	public static Vec3 tip(AbstractClientPlayer p, int r, float partial) {
		Cables c = CABLES.get(p);
		if (c == null || !c.started) {
			return null;
		}
		Rope rope = c.ropes[r];
		return rope.last[N - 1].lerp(rope.pos[N - 1], partial);
	}
}
