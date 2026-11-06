package com.projecthero.mod.client.omnitrix.alien.jetray;

import java.util.Map;
import java.util.WeakHashMap;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.omnitrix.AlienClients;
import com.projecthero.mod.client.omnitrix.alien.JetrayClient;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.jetray.JetrayKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * v0.17.2 (user: "give him his tail aswell please give it the same rules as the humungosaur tail ... give jetray better flight
 * animations too please and when he flies he needs his wings so create the wing model and have it be in between his arms and his
 * tail, look at the reference images i sent"): Jetray's body beyond the skin -- the manta's WING MEMBRANES, his WHIP TAIL and his
 * FLIGHT ATTITUDE. Client only, one {@link State} per player (pruned every tick: gone, reverted or another alien).
 * <ul>
 *   <li><b>Wings</b> (only in flight, folding out / in over 0.4 s): one gold membrane each side, a 6x6 grid stretched every frame
 *       between corner points taken from the posed bones -- the shoulder tip and the wrist on the arm, the armpit on the body, the
 *       low shin on the leg -- so it follows the arms; it billows a little, ripples with the beat, and its underside is a darker gold
 *       ({@code jetray_wings.png}, scratchpad/recal_v0172/jetray/gen_jetray_wings.js).</li>
 *   <li><b>Tail</b>: six thin red segments from the small of his back and an arrowhead fin; a follow-the-leader spring chain in
 *       world space per player, ticked 20 times a second: it lags behind his turns, sways with his walk, trails and streams
 *       straight back at speed, is pushed out of every block it would enter and kept behind his back and out of his legs (the
 *       Humungousaur tail rules). Not drawn in first person.</li>
 *   <li><b>Flight</b>: the whole body pitched along his travel (up to 83 degrees cruising, head-first in a dive), banked into
 *       his turns, a slow wing-beat ripple cruising, arms swept back sprinting, an upright hover with gentle flaps
 *       ({@link #bodyTilt} for {@code StinkflyFlightPoseMixin}, the arms and legs in {@link #flightPose}).</li>
 * </ul>
 */
public final class JetrayBody {
	private JetrayBody() {
	}

	private static final ResourceLocation WINGS = ProjectHeroMod.id("textures/entity/omnitrix/jetray_wings.png");

	/** Tail: segments, segment length (blocks at scale 1), radius at the root and the tip (model px). */
	private static final int SEGMENTS = 6;
	private static final double SEG_LEN = 0.23;
	private static final float R_ROOT = 1.25f, R_TIP = 0.4f;
	/** The tail's root on the body bone (px): the small of his back. */
	private static final float ROOT_X = 0f, ROOT_Y = 10.5f, ROOT_Z = 1.6f;
	/** Cruise speed (blocks / tick) the flight attitude counts as full speed; above it the arms sweep back toward sprint. */
	private static final float CRUISE = 1.25f, SPRINT = 2.25f;

	/** Per player: eased flight attitude, wing opening and the tail chain (world space, previous + current tick). */
	static final class State {
		float open, openO;
		float lean, leanO, bank, bankO, speed, speedO, sweep, sweepO;
		/** Round 4: 0..1, drawn into the Supersonic dart. */
		float tuck, tuckO;
		final Vec3[] tail = new Vec3[SEGMENTS + 1];
		final Vec3[] tailO = new Vec3[SEGMENTS + 1];
		boolean tailLive;
	}

	private static final Map<AbstractClientPlayer, State> STATES = new WeakHashMap<>();

	/** Every client tick: forget players that are gone, turned back or are another alien now (and everything off-world). */
	public static void prune(Minecraft mc) {
		if (mc.level == null) {
			STATES.clear();
			return;
		}
		STATES.keySet().removeIf(p -> p.isRemoved() || p.level() != mc.level || !(AlienClients.shownKit(p) instanceof JetrayClient));
	}

	/** Tests / harness: how many players have state. */
	public static int tracked() {
		return STATES.size();
	}

	static boolean flying(AbstractClientPlayer p) {
		OmnitrixState st = Omnitrix.state(p);
		return (st.kitFlags & (JetrayKit.F_FLYING | JetrayKit.F_DIVING | JetrayKit.F_THUNDER)) != 0
				|| st.animId == JetrayKit.ANIM_BEAT && p.level().getGameTime() - st.animStart < 12; // round 4: the wings open as they go up, a take-off too
	}

	/** Round 4: how far through its 3 s the Supersonic charge is (0 if not charging). */
	public static float charge(net.minecraft.world.entity.player.Player p, float partial) {
		OmnitrixState st = Omnitrix.state(p);
		if ((st.kitFlags & JetrayKit.F_SONIC_CHARGE) == 0) {
			return 0f;
		}
		float left = st.readyAt(JetrayKit.ID + "." + JetrayKit.SONIC_AT) - (p.level().getGameTime() + partial);
		return Mth.clamp(1f - left / JetrayKit.SONIC_CHARGE, 0f, 1f);
	}

	private static float lerp(float partial, float o, float n) {
		return Mth.lerp(partial, o, n);
	}

	private static float smooth(float x) {
		x = Mth.clamp(x, 0f, 1f);
		return x * x * (3f - 2f * x);
	}

	// ---------------------------------------------------------------- the tick

	public static void tick(AbstractClientPlayer p) {
		State s = STATES.computeIfAbsent(p, k -> new State());
		boolean fly = flying(p);
		// the wings: 0.4 s out, 0.4 s in
		s.openO = s.open;
		s.open = fly ? Math.min(1f, s.open + 0.125f) : Math.max(0f, s.open - 0.125f);
		// the attitude from how far he actually moved (other players' velocity is not synced)
		double dx = p.getX() - p.xo, dy = p.getY() - p.yo, dz = p.getZ() - p.zo;
		float yaw = p.yBodyRot * Mth.DEG_TO_RAD;
		float fwd = (float) (-Mth.sin(yaw) * dx + Mth.cos(yaw) * dz);
		float side = (float) (-Mth.cos(yaw) * dx - Mth.sin(yaw) * dz);
		float horiz = (float) Math.sqrt(dx * dx + dz * dz);
		float moving = Mth.clamp((float) Math.sqrt(dx * dx + dy * dy + dz * dz) / 0.6f, 0f, 1f);
		// pitched along the travel: level flight lies him out flat (83), climbing stands him up, a dive tips him head first
		float along = (float) Math.toDegrees(Math.atan2(fwd, dy));
		float lean = fly ? Mth.clamp(along * 0.92f, -30f, 125f) * moving : 0f;
		float turn = Mth.wrapDegrees(p.yBodyRot - p.yBodyRotO);
		float sp = Mth.clamp(Math.max(0f, fwd) / CRUISE, 0f, 1f);
		float bank = fly ? Mth.clamp(-turn * 2.6f * (0.35f + sp) - side / CRUISE * 22f, -45f, 45f) : 0f;
		float sweep = fly ? Mth.clamp((horiz - CRUISE * 1.05f) / (SPRINT - CRUISE * 1.05f), 0f, 1f) : 0f;
		// round 4: he levels out along his aim as the Supersonic charge builds, and holds that dart line past the barrier
		boolean sonic = (Omnitrix.state(p).kitFlags & JetrayKit.F_SONIC) != 0;
		float charge = charge(p, 0f);
		if (sonic || charge > 0f) {
			lean = Mth.lerp(sonic ? 1f : smooth(charge / 0.5f), lean, Mth.clamp(90f + p.getXRot(), -30f, 125f));
		}
		float tuck = sonic ? 1f : smooth((charge - 0.35f) / 0.65f);
		s.tuckO = s.tuck;
		s.tuck += (tuck - s.tuck) * 0.25f;
		s.leanO = s.lean;
		s.bankO = s.bank;
		s.speedO = s.speed;
		s.sweepO = s.sweep;
		s.lean += (lean - s.lean) * 0.2f;
		s.bank += (bank - s.bank) * 0.2f;
		s.speed += ((sonic || charge > 0f ? 1f : fly ? Math.max(sp, moving * 0.6f) : 0f) - s.speed) * 0.2f;
		s.sweep += (sweep - s.sweep) * 0.15f;
		tickTail(p, s);
	}

	// ---------------------------------------------------------------- the flight attitude

	/** The whole-body tilt for {@code StinkflyFlightPoseMixin}: {pitch forward, roll} in degrees and a bob (blocks), or null. */
	public static float[] bodyTilt(AbstractClientPlayer p, float partial) {
		if (!(AlienClients.shownKit(p) instanceof JetrayClient)) {
			return null;
		}
		State s = STATES.get(p);
		if (s == null) {
			return null;
		}
		float open = smooth(lerp(partial, s.openO, s.open));
		if (open < 0.02f) {
			return null;
		}
		float age = p.tickCount + partial;
		float speed = lerp(partial, s.speedO, s.speed);
		float bob = Mth.sin(age * 0.28f) * 0.05f * (1f - speed) * open + Mth.sin(age * 3.1f) * 0.016f * charge(p, partial); // round 4: the shudder as it builds
		return new float[] { lerp(partial, s.leanO, s.lean) * open, lerp(partial, s.bankO, s.bank) * open, bob };
	}

	/**
	 * The flying limbs (no move clip running): arms spread out to stretch the wings, legs together and trailing. Hovering, slow
	 * flaps; cruising, a gentle beat ripple; sprinting, the arms swept back along his sides into a dart.
	 */
	public static boolean flightPose(AbstractClientPlayer p, HumanoidModel<?> m, float age) {
		State s = STATES.get(p);
		if (s == null || !flying(p)) {
			return false;
		}
		float partial = age - Mth.floor(age);
		float sp = lerp(partial, s.speedO, s.speed);
		float sw = lerp(partial, s.sweepO, s.sweep);
		float open = smooth(lerp(partial, s.openO, s.open));
		float flap = Mth.sin(age * 0.3f);
		float ripple = Mth.sin(age * 0.2f);
		// right arm out = +zRot, left = -zRot
		float hoverZ = 1.1f + flap * 0.32f, cruiseZ = 1.42f + ripple * 0.07f, sprintZ = 0.42f + ripple * 0.03f;
		float armZ = Mth.lerp(sw, Mth.lerp(sp, hoverZ, cruiseZ), sprintZ);
		float armX = Mth.lerp(sw, Mth.lerp(sp, -0.12f + flap * 0.06f, 0.0f), 0.3f);
		// round 4, Supersonic: the wings thrown wide to catch the charge, then drawn in shuddering, tight to his sides past the barrier
		float charge = charge(p, partial);
		if (charge > 0f) {
			float wide = smooth(charge / 0.2f) * (1f - smooth((charge - 0.2f) / 0.8f));
			armZ = Mth.lerp(wide, armZ, 1.75f) + Mth.sin(age * 2.6f) * 0.035f * charge;
			armX = Mth.lerp(wide, armX, 0.55f) + Mth.sin(age * 3.7f) * 0.025f * charge;
		}
		float tuck = lerp(partial, s.tuckO, s.tuck);
		armZ = Mth.lerp(tuck, armZ, 0.12f);
		armX = Mth.lerp(tuck, armX, 0.2f);
		armZ = Mth.lerp(open, 0.35f, armZ);
		m.rightArm.xRot = armX;
		m.leftArm.xRot = armX;
		m.rightArm.yRot = 0f;
		m.leftArm.yRot = 0f;
		m.rightArm.zRot = armZ;
		m.leftArm.zRot = -armZ;
		float bank = lerp(partial, s.bankO, s.bank) * Mth.DEG_TO_RAD;
		float kick = Mth.sin(age * 0.25f) * 0.1f * (1f - sp);
		m.rightLeg.xRot = Mth.lerp(sp, 0.25f + kick, 0.06f);
		m.leftLeg.xRot = Mth.lerp(sp, 0.18f - kick, 0.06f);
		m.rightLeg.yRot = 0f;
		m.leftLeg.yRot = 0f;
		m.rightLeg.zRot = Mth.lerp(sp, 0.06f, 0.015f) + Math.max(0f, bank) * 0.15f;
		m.leftLeg.zRot = Mth.lerp(sp, -0.06f, -0.015f) + Math.min(0f, bank) * 0.15f;
		headLevel(p, m, age);
		return true;
	}

	/** The head stays level against the body's pitch, so he looks where he flies. */
	public static void headLevel(AbstractClientPlayer p, HumanoidModel<?> m, float age) {
		State s = STATES.get(p);
		if (s == null) {
			return;
		}
		float partial = age - Mth.floor(age);
		float open = smooth(lerp(partial, s.openO, s.open));
		float lean = lerp(partial, s.leanO, s.lean) * open;
		m.head.xRot = Math.max(-1.45f, m.head.xRot - Math.min(lean, 90f) * Mth.DEG_TO_RAD * 0.85f);
	}

	// ---------------------------------------------------------------- the body frame (model -> world), mirroring the renderer

	/**
	 * A model-space point (blocks: what the bones' transforms give) to world space at the end of this tick: the player renderer's
	 * {@code translate(0,-1.501,0)}, {@code scale(0.9375)} and flip, {@code StinkflyFlightPoseMixin}'s tilt, the body yaw and the
	 * entity scale. {@code w} 0 turns a direction.
	 */
	private static Vec3 toWorld(AbstractClientPlayer p, State s, double x, double y, double z, double w) {
		double sc = p.getScale();
		y = y - 1.501 * w;
		x *= 0.9375;
		y *= 0.9375;
		z *= 0.9375;
		x = -x;
		y = -y;
		float open = smooth(s.open);
		double pivot = 0.9 * sc * w;
		double bob = 0; // the hover bob is a few hundredths: left out of the physics
		y -= pivot;
		double a = -s.lean * open * Mth.DEG_TO_RAD;
		double ny = y * Math.cos(a) - z * Math.sin(a);
		double nz = y * Math.sin(a) + z * Math.cos(a);
		y = ny;
		z = nz;
		double b = s.bank * open * Mth.DEG_TO_RAD;
		double nx = x * Math.cos(b) - y * Math.sin(b);
		ny = x * Math.sin(b) + y * Math.cos(b);
		x = nx;
		y = ny + pivot + bob * w;
		double t = (180.0 - p.yBodyRot) * Mth.DEG_TO_RAD;
		nx = x * Math.cos(t) + z * Math.sin(t);
		nz = -x * Math.sin(t) + z * Math.cos(t);
		Vec3 v = new Vec3(nx * sc, y * sc, nz * sc);
		return w == 0 ? v : v.add(p.position());
	}

	// ---------------------------------------------------------------- the tail

	private static void tickTail(AbstractClientPlayer p, State s) {
		Level level = p.level();
		double sc = p.getScale();
		double len = SEG_LEN * sc;
		Vec3 root = toWorld(p, s, ROOT_X / 16.0, ROOT_Y / 16.0, ROOT_Z / 16.0, 1);
		Vec3 back = toWorld(p, s, 0, 0, 1, 0).normalize();
		Vec3 up = toWorld(p, s, 0, -1, 0, 0).normalize();
		Vec3 right = up.cross(back).normalize();
		Vec3 hip = toWorld(p, s, 0, 12 / 16.0, 0, 1);
		float open = smooth(s.open);
		OmnitrixState st = Omnitrix.state(p);
		float animT = p.level().getGameTime() - st.animStart;
		boolean arch = st.animId == JetrayKit.ANIM_TAIL && animT >= 0 && animT < 12;
		float walk = p.walkAnimation.position();
		float walkAmt = Math.min(1f, p.walkAnimation.speed()) * (1f - open);
		float age = p.tickCount;
		if (!s.tailLive || s.tail[0] == null || s.tail[0].distanceToSqr(root) > 16 * sc * sc) {
			// fresh (or teleported): laid out at rest
			Vec3 at = root;
			for (int i = 0; i <= SEGMENTS; i++) {
				if (i > 0) {
					at = at.add(restDir(i - 1, back, up, right, open, false, 0, 0, 0).scale(len));
				}
				s.tail[i] = at;
				// round 4: at 200 b/s this runs every tick, so last tick's point is where the body was then, or the tail would not keep up with him between ticks
				s.tailO[i] = at.subtract(p.getX() - p.xo, p.getY() - p.yo, p.getZ() - p.zo);
			}
			s.tailLive = true;
			return;
		}
		Vec3[] vel = new Vec3[SEGMENTS + 1];
		for (int i = 0; i <= SEGMENTS; i++) {
			vel[i] = s.tail[i].subtract(s.tailO[i]);
			s.tailO[i] = s.tail[i];
		}
		Vec3[] next = new Vec3[SEGMENTS + 1];
		next[0] = root;
		for (int i = 1; i <= SEGMENTS; i++) {
			Vec3 cur = s.tail[i];
			// verlet: carry the motion (air drag eats 20% a tick, so at speed it trails), a little gravity on the ground
			Vec3 pos = cur.add(vel[i].scale(0.8)).add(0, -0.015 * (1f - open), 0);
			// the spring toward his rest shape (stiffer at the root)
			Vec3 rest = next[i - 1].add(restDir(i - 1, back, up, right, open, arch, walk, walkAmt, age).scale(len));
			double k = Mth.lerp((i - 1) / (double) (SEGMENTS - 1), 0.55, 0.22) * (arch ? 1.6 : 1.0);
			pos = pos.add(rest.subtract(pos).scale(Math.min(0.9, k)));
			// follow the leader: exactly one segment from the one before
			pos = constrain(next[i - 1], pos, len);
			// never into his body or legs: anything inside the torso / leg column is pushed out behind his back
			Vec3 rel = pos.subtract(hip);
			double rb = rel.dot(back), ru = rel.dot(up), rr = rel.dot(right);
			double minBack = (0.16 + 0.015 * i * (1f - open)) * sc;
			if (rb < minBack && Math.abs(rr) < 0.38 * sc && ru > -0.85 * sc && ru < 0.85 * sc) {
				pos = pos.add(back.scale(minBack - rb));
				pos = constrain(next[i - 1], pos, len);
			}
			// never through blocks: pushed out of any collision box it entered (then re-measured; a squeeze may stretch it a hair)
			pos = outOfBlocks(level, pos, cur);
			next[i] = pos;
		}
		System.arraycopy(next, 0, s.tail, 0, SEGMENTS + 1);
	}

	/**
	 * Segment {@code i}'s resting direction: standing, back and down with the tip curling up and a sway with the walk; flying,
	 * straight out behind along his legs (a hair above them); the Tail Blast arches it up over his shoulder.
	 */
	private static Vec3 restDir(int i, Vec3 back, Vec3 up, Vec3 right, float open, boolean arch, float walk, float walkAmt, float age) {
		double ground = Math.toRadians(42 - i * 13);
		double air = Math.toRadians(83 + i * 1.0);
		double a = Mth.lerp(open, ground, air);
		if (arch) {
			a = Math.toRadians(-(35 + i * 24));
		}
		Vec3 d = back.scale(Math.cos(a)).subtract(up.scale(Math.sin(a)));
		double sway = Mth.sin(walk * 0.6662f + i * 0.55f) * 0.35 * walkAmt + Mth.sin(age * 0.07f + i * 0.6f) * 0.07;
		return d.add(right.scale(sway)).normalize();
	}

	private static Vec3 constrain(Vec3 from, Vec3 to, double len) {
		Vec3 d = to.subtract(from);
		double l = d.length();
		return l < 1.0e-6 ? from.add(0, -len, 0) : from.add(d.scale(len / l));
	}

	/** Pushes {@code pos} out of whatever collision box holds it, through the nearest face; else back where it was. */
	private static Vec3 outOfBlocks(Level level, Vec3 pos, Vec3 was) {
		final double pad = 0.04;
		BlockPos bp = BlockPos.containing(pos);
		VoxelShape shape = level.getBlockState(bp).getCollisionShape(level, bp);
		if (shape.isEmpty()) {
			return pos;
		}
		for (AABB box : shape.toAabbs()) {
			AABB b = box.move(bp).inflate(pad);
			if (!b.contains(pos)) {
				continue;
			}
			double[] d = { pos.x - b.minX, b.maxX - pos.x, pos.y - b.minY, b.maxY - pos.y, pos.z - b.minZ, b.maxZ - pos.z };
			int best = 0;
			for (int k = 1; k < 6; k++) {
				if (d[k] < d[best]) {
					best = k;
				}
			}
			Vec3 out = switch (best) {
				case 0 -> new Vec3(b.minX, pos.y, pos.z);
				case 1 -> new Vec3(b.maxX, pos.y, pos.z);
				case 2 -> new Vec3(pos.x, b.minY, pos.z);
				case 3 -> new Vec3(pos.x, b.maxY, pos.z);
				case 4 -> new Vec3(pos.x, pos.y, b.minZ);
				default -> new Vec3(pos.x, pos.y, b.maxZ);
			};
			BlockPos ob = BlockPos.containing(out);
			if (!ob.equals(bp) && !level.getBlockState(ob).getCollisionShape(level, ob).isEmpty()) {
				return was; // pushed into the next block: stay where it was last tick
			}
			return out;
		}
		return pos;
	}

	/** The tail's tip in the world (interpolated), or null -- the Tail Blast and the Tail Grab tether start there. */
	public static Vec3 tailTip(AbstractClientPlayer p, float partial) {
		State s = STATES.get(p);
		if (s == null || !s.tailLive || s.tail[SEGMENTS] == null) {
			return null;
		}
		return s.tailO[SEGMENTS].lerp(s.tail[SEGMENTS], partial);
	}

	// ---------------------------------------------------------------- rendering

	/** Called from {@link JetrayClient#renderExtras}: the wings (when open) and the tail (third person only). */
	public static void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model,
			float partial, float age) {
		State s = STATES.get(p);
		if (s == null) {
			return;
		}
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(WINGS));
		float open = smooth(lerp(partial, s.openO, s.open));
		if (open > 0.01f) {
			for (int side = -1; side <= 1; side += 2) {
				wing(pose, vc, light, model, side, open, lerp(partial, s.speedO, s.speed), lerp(partial, s.sweepO, s.sweep), age);
			}
		}
		Minecraft mc = Minecraft.getInstance();
		if (p == mc.player && mc.options.getCameraType().isFirstPerson()) {
			return;
		}
		tail(pose, vc, light, p, model, s, partial);
	}

	/** A point in {@code bone}'s pixels, in model space (blocks). */
	private static Vector3f at(ModelPart bone, float x, float y, float z) {
		PoseStack ps = new PoseStack();
		bone.translateAndRotate(ps);
		Vector4f v = ps.last().pose().transform(new Vector4f(x / 16f, y / 16f, z / 16f, 1f));
		return new Vector3f(v.x, v.y, v.z);
	}

	private static Vector3f lerpV(Vector3f a, Vector3f b, float t) {
		return new Vector3f(a).lerp(b, t);
	}

	/**
	 * One membrane: corners A (the shoulder tip, on the arm), B (the wrist), C (low on the shin) and D (the armpit, on the
	 * body). Grid (u down from the shoulder line to the free trailing edge, v across from his side to the arm); folding shrinks
	 * A, B and C onto his side.
	 */
	private static void wing(PoseStack pose, VertexConsumer vc, int light, PlayerModel<AbstractClientPlayer> m, int side, float open, float speed,
			float sweep, float age) {
		boolean right = side < 0;
		ModelPart arm = right ? m.rightArm : m.leftArm;
		ModelPart leg = right ? m.rightLeg : m.leftLeg;
		Vector3f a = at(arm, -side * -0.4f, -3.2f, 0.9f); // the shoulder tip, over the arm's root
		Vector3f b = at(arm, side * -0.6f, 9.6f, 0.9f); // the wrist, on the arm's inner edge
		Vector3f c = at(leg, side * 1.7f, 10.8f, 0.9f); // low on the shin, on the leg's outer side
		Vector3f d = at(m.body, side * 3.7f, 1.6f, 0.9f); // the armpit
		// folded: everything drawn in onto the line down his side
		a = lerpV(d, a, open);
		b = lerpV(lerpV(d, c, 0.55f), b, open);
		c = lerpV(lerpV(d, c, 0.8f), c, open);
		Vector3f bodyBack = at(m.body, 0, 0, 1).sub(at(m.body, 0, 0, 0)).normalize();
		final int n = 6;
		Vector3f[][] g = new Vector3f[n + 1][n + 1];
		// billow: lifted toward his back in the airflow; a travelling ripple when cruising, bigger slow flaps hovering
		float lift = 0.07f * open * (0.4f + 0.6f * speed) * (1f - 0.6f * sweep);
		for (int i = 0; i <= n; i++) {
			float u = i / (float) n;
			for (int j = 0; j <= n; j++) {
				float v = j / (float) n;
				Vector3f inner = lerpV(d, c, u);
				Vector3f outer = lerpV(a, b, u);
				Vector3f pt = lerpV(inner, outer, v);
				float belly = Mth.sin(Mth.PI * u) * Mth.sin(Mth.PI * v);
				float ripple = Mth.sin(age * 0.35f - u * 3.5f - v * 1.5f) * 0.03f * open * (0.3f + speed) * (1f - sweep);
				pt.add(new Vector3f(bodyBack).mul((lift + ripple) * belly));
				g[i][j] = pt;
			}
		}
		PoseStack.Pose last = pose.last();
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				Vector3f p00 = g[i][j], p10 = g[i + 1][j], p11 = g[i + 1][j + 1], p01 = g[i][j + 1];
				Vector3f nrm = new Vector3f(p10).sub(p00).cross(new Vector3f(p01).sub(p00));
				if (nrm.lengthSquared() < 1.0e-10f) {
					continue;
				}
				nrm.normalize();
				if (nrm.dot(bodyBack) < 0) {
					nrm.negate();
				}
				float u0 = i / (float) n * 0.5f, u1 = (i + 1) / (float) n * 0.5f; // texture y (down the membrane)
				float v0 = j / (float) n * 0.5f, v1 = (j + 1) / (float) n * 0.5f; // texture x (across)
				// the back face (gold), a hair toward his back; the underside (darker gold), a hair below
				face(vc, last, light, p00, p10, p11, p01, nrm, 0.004f, v0, u0, v1, u1, 0f);
				Vector3f neg = new Vector3f(nrm).negate();
				face(vc, last, light, p00, p10, p11, p01, neg, 0.004f, v0, u0, v1, u1, 0.5f);
			}
		}
	}

	/** One membrane cell offset {@code off} along {@code nrm}: texture x {@code x0..x1} (+{@code du}), y {@code y0..y1}. */
	private static void face(VertexConsumer vc, PoseStack.Pose last, int light, Vector3f p00, Vector3f p10, Vector3f p11, Vector3f p01, Vector3f nrm,
			float off, float x0, float y0, float x1, float y1, float du) {
		float ox = nrm.x * off, oy = nrm.y * off, oz = nrm.z * off;
		vert(vc, last, light, p00.x + ox, p00.y + oy, p00.z + oz, x0 + du, y0, nrm);
		vert(vc, last, light, p10.x + ox, p10.y + oy, p10.z + oz, x0 + du, y1, nrm);
		vert(vc, last, light, p11.x + ox, p11.y + oy, p11.z + oz, x1 + du, y1, nrm);
		vert(vc, last, light, p01.x + ox, p01.y + oy, p01.z + oz, x1 + du, y0, nrm);
	}

	private static void vert(VertexConsumer vc, PoseStack.Pose last, int light, float x, float y, float z, float u, float v, Vector3f n) {
		vc.addVertex(last, x, y, z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
				.setLight(light).setNormal(last, n.x, n.y, n.z);
	}

	/**
	 * The tail: the chain's world points brought into model space through the inverse of this frame's model matrix (camera-relative,
	 * like every entity), its root pinned to the posed body; tapered square segments and the arrowhead fin.
	 */
	private static void tail(PoseStack pose, VertexConsumer vc, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> m, State s,
			float partial) {
		if (!s.tailLive) {
			return;
		}
		Matrix4f inv = new Matrix4f(pose.last().pose()).invert();
		Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
		Vector3f rootModel = at(m.body, ROOT_X, ROOT_Y, ROOT_Z);
		Vector3f[] q = new Vector3f[SEGMENTS + 1];
		for (int i = 0; i <= SEGMENTS; i++) {
			Vec3 w = s.tailO[i].lerp(s.tail[i], partial).subtract(cam);
			Vector4f v = inv.transform(new Vector4f((float) w.x, (float) w.y, (float) w.z, 1f));
			q[i] = new Vector3f(v.x, v.y, v.z);
		}
		// drawn somewhere other than the world (the inventory doll): the chain is not his here -- skip it
		if (q[0].distance(rootModel) > 1.5f) {
			return;
		}
		// pin the root to the posed body, easing the correction out along the chain
		Vector3f fix = new Vector3f(rootModel).sub(q[0]);
		for (int i = 0; i <= SEGMENTS; i++) {
			q[i].add(new Vector3f(fix).mul(1f - i / (float) SEGMENTS));
		}
		Vector3f bodyBack = at(m.body, 0, 0, 1).sub(at(m.body, 0, 0, 0)).normalize();
		PoseStack.Pose last = pose.last();
		Vector3f prevS1 = null;
		for (int i = 0; i < SEGMENTS; i++) {
			Vector3f a0 = q[i], a1 = q[i + 1];
			Vector3f axis = new Vector3f(a1).sub(a0);
			if (axis.lengthSquared() < 1.0e-8f) {
				continue;
			}
			axis.normalize();
			// a frame round the segment, kept turning smoothly from the last (the "top" face stays on top)
			Vector3f ref = prevS1 != null ? new Vector3f(prevS1) : new Vector3f(bodyBack);
			Vector3f s1 = ref.sub(new Vector3f(axis).mul(ref.dot(axis)));
			if (s1.lengthSquared() < 1.0e-6f) {
				s1 = Math.abs(axis.y) < 0.9f ? new Vector3f(0, 1, 0).cross(axis) : new Vector3f(1, 0, 0).cross(axis);
			}
			s1.normalize();
			Vector3f s2 = new Vector3f(axis).cross(s1).normalize();
			prevS1 = s1;
			float r0 = Mth.lerp(i / (float) SEGMENTS, R_ROOT, R_TIP) / 16f;
			float r1 = Mth.lerp((i + 1) / (float) SEGMENTS, R_ROOT, R_TIP) / 16f;
			float tv0 = (64f + i * 64f / SEGMENTS) / 128f, tv1 = (64f + (i + 1) * 64f / SEGMENTS) / 128f;
			Vector3f[] dirs = { s1, s2, new Vector3f(s1).negate(), new Vector3f(s2).negate() };
			for (int k = 0; k < 4; k++) {
				Vector3f e0 = dirs[k], e1 = dirs[(k + 1) & 3];
				// the face between two edges of the square section: the corners at +-(e0 + e1) rotated 45 degrees -> use edge
				// midpoints for a diamond section that reads round from any side
				Vector3f c00 = new Vector3f(a0).add(new Vector3f(e0).mul(r0));
				Vector3f c01 = new Vector3f(a0).add(new Vector3f(e1).mul(r0));
				Vector3f c11 = new Vector3f(a1).add(new Vector3f(e1).mul(r1));
				Vector3f c10 = new Vector3f(a1).add(new Vector3f(e0).mul(r1));
				Vector3f nrm = new Vector3f(e0).add(e1).normalize();
				float tu0 = k * 4f / 128f, tu1 = (k * 4f + 4f) / 128f;
				vert(vc, last, light, c00.x, c00.y, c00.z, tu0, tv0, nrm);
				vert(vc, last, light, c10.x, c10.y, c10.z, tu0, tv1, nrm);
				vert(vc, last, light, c11.x, c11.y, c11.z, tu1, tv1, nrm);
				vert(vc, last, light, c01.x, c01.y, c01.z, tu1, tv0, nrm);
			}
		}
		// the arrowhead fin on the tip: two crossed quads along the last segment
		Vector3f tip = q[SEGMENTS];
		Vector3f axis = new Vector3f(q[SEGMENTS]).sub(q[SEGMENTS - 1]);
		if (axis.lengthSquared() < 1.0e-8f || prevS1 == null) {
			return;
		}
		axis.normalize();
		Vector3f s1 = new Vector3f(prevS1);
		Vector3f s2 = new Vector3f(axis).cross(s1).normalize();
		float finLen = 4.6f / 16f, finW = 2.3f / 16f;
		Vector3f base = new Vector3f(tip).sub(new Vector3f(axis).mul(0.6f / 16f));
		Vector3f end = new Vector3f(base).add(new Vector3f(axis).mul(finLen));
		for (Vector3f across : new Vector3f[] { s1, s2 }) {
			Vector3f nrm = new Vector3f(axis).cross(across).normalize();
			Vector3f w = new Vector3f(across).mul(finW);
			Vector3f b0 = new Vector3f(base).sub(w), b1 = new Vector3f(base).add(w), e1 = new Vector3f(end).add(w), e0 = new Vector3f(end).sub(w);
			// texture: x 16..48 along (base -> point), y 64..96 across
			vert(vc, last, light, b0.x, b0.y, b0.z, 16f / 128f, 64f / 128f, nrm);
			vert(vc, last, light, e0.x, e0.y, e0.z, 48f / 128f, 64f / 128f, nrm);
			vert(vc, last, light, e1.x, e1.y, e1.z, 48f / 128f, 96f / 128f, nrm);
			vert(vc, last, light, b1.x, b1.y, b1.z, 16f / 128f, 96f / 128f, nrm);
		}
	}
}
