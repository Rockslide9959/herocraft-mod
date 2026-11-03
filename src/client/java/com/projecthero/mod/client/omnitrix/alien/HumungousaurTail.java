package com.projecthero.mod.client.omnitrix.alien;

import java.util.HashMap;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.omnitrix.AlienParts;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.humungousaur.HumungousaurKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * v0.17.2 (user: "give humungeosaur a tail, make it move in relation to how the player moves, dont let it go through blocks or
 * inside humungosuars body, make sure the tail gets the spiked ball and spikes through his different size transformations
 * please"): Humungousaur's tail -- five tapering segments out of the base of his back, drawn on the body bone (so it grows with
 * him at x1.5 / x2.2 / x3.0).
 * <p>
 * Motion is a small client-side spring chain per player (entity id): each segment's angle (relative to his body) chases a target
 * -- drooping when he stands, trailing out behind as he walks / runs, lifted when he sprints or jumps, swaying with the walk cycle
 * -- and every body turn (his body yaw, or the body bone spinning in a pose clip) pushes the tail the other way, so it lags and
 * whips back. The Tail Club lifts it to hip height and swings it round hard behind the spin.
 * <p>
 * Collision runs once per client tick (not per frame): each segment's end, belly and sides are tested against block collision
 * shapes in the world and against his own body / leg boxes (from the last rendered pose plus the walk cycle ahead); a segment
 * that would hit bends up, sideways or down in 8-degree steps until it is clear, and relaxes back once there is room.
 * <p>
 * Spikes by size: base size has small bone nubs; x2.2 bone spikes along its top; x3.0 bigger spikes and the spiked club on the
 * tip. The Spike Burst stands extra spikes out of every segment's sides at any size (scaled with its in / out timing).
 */
final class HumungousaurTail {
	private static final net.minecraft.resources.ResourceLocation EXTRAS = ProjectHeroMod.id("textures/entity/omnitrix/humungousaur_extras.png");
	private static final float S = 1f / 64f;
	// the extras sheet: tail hide (u 0..8, v 0..8), bone (u 16..20, v 0..4)
	private static final float HIDE_U0 = 0f, HIDE_V0 = 0f, HIDE_U1 = 8 * S, HIDE_V1 = 8 * S;
	private static final float BONE_U0 = 16 * S, BONE_V0 = 0f, BONE_U1 = 20 * S, BONE_V1 = 4 * S;

	static final int N = 5;
	/** Segment lengths, half-widths (x) in body pixels; the half-height is {@link #HH} of the half-width. */
	private static final float[] LEN = { 5.0f, 5.0f, 4.6f, 4.2f, 3.8f };
	private static final float[] HALF = { 3.0f, 2.6f, 2.15f, 1.7f, 1.3f };
	private static final float HH = 0.9f;
	/** Where the tail leaves his back, in the body bone (y down from the neck, z = backward). */
	private static final float ROOT_Y = 10.2f, ROOT_Z = 1.6f;
	/** Absolute pitches (degrees, + = up): standing droop, trailing at speed, the Tail Club's hip-height sweep. */
	private static final float[] REST = { -45f, -35f, -24f, -14f, -6f };
	private static final float[] TRAIL = { -26f, -15f, -8f, -3f, 0f };
	private static final float[] CLUB = { -14f, -7f, -2f, 1f, 3f };
	/** How much of a body turn each segment keeps (the tip lags most). */
	private static final float[] LAG = { 0.35f, 0.5f, 0.64f, 0.77f, 0.88f };
	private static final float[] K = { 0.30f, 0.26f, 0.22f, 0.19f, 0.16f };
	private static final float[] DAMP = { 0.62f, 0.66f, 0.70f, 0.73f, 0.76f };
	private static final float P_MIN = -82f, P_MAX = 55f, Y_MAX = 80f;
	/** The club on the tip at x3.0: its half-sizes and length. */
	private static final float CLUB_HW = 2.6f, CLUB_HH = 2.4f, CLUB_LEN = 4.4f;
	private static final float STEP = 8f;
	private static final int STEPS = 10;

	private static final Map<Integer, Tail> TAILS = new HashMap<>();
	private static boolean registered;

	/** One player's tail. */
	private static final class Tail {
		final float[] pitch = new float[N], yaw = new float[N], vp = new float[N], vy = new float[N];
		final float[] colP = new float[N], colY = new float[N];
		final float[] outP = new float[N], outY = new float[N], prevP = new float[N], prevY = new float[N];
		boolean started;
		long lastTick;
		float lastBodyYaw, lastBoneYaw;
		// the last rendered pose of the body and legs (the tick's collision uses it)
		float bx, by, bz, bxr, byr, bzr;
		float rlx = -1.9f, rly = 12f, rlz, rlxr, llx = 1.9f, lly = 12f, llz, llxr;
	}

	private HumungousaurTail() {
	}

	static void init() {
		if (registered) {
			return;
		}
		registered = true;
		ClientTickEvents.END_CLIENT_TICK.register(HumungousaurTail::prune);
		ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> TAILS.clear());
	}

	/** Forget tails nobody is wearing any more (reverted, out of view, gone). */
	private static void prune(Minecraft mc) {
		if (mc.level == null) {
			TAILS.clear();
			return;
		}
		if (mc.isPaused() || TAILS.isEmpty()) {
			return;
		}
		long now = mc.level.getGameTime();
		TAILS.entrySet().removeIf(e -> now - e.getValue().lastTick > 10 || now < e.getValue().lastTick || mc.level.getEntity(e.getKey()) == null);
	}

	// ---------------------------------------------------------------- the simulation (client tick)

	static void tick(AbstractClientPlayer p) {
		Tail t = TAILS.computeIfAbsent(p.getId(), k -> new Tail());
		Level level = p.level();
		long now = level.getGameTime();
		t.lastTick = now;
		OmnitrixState st = Omnitrix.state(p);

		float walk = Mth.clamp(p.walkAnimation.speed(), 0f, 1f);
		float pos = p.walkAnimation.position();
		float scale = Math.max(0.5f, p.getScale());
		double dx = p.getX() - p.xo, dz = p.getZ() - p.zo;
		float fast = Mth.clamp((float) Math.sqrt(dx * dx + dz * dz) / (0.14f * scale / 1.5f), 0f, 1f);
		float trail = Math.max(walk * 0.7f, fast);
		float lift = (p.isSprinting() ? 8f : 0f) + (!p.onGround() ? 12f : 0f) - (p.isCrouching() ? 8f : 0f);

		boolean club = st.animId == HumungousaurKit.ANIM_TAIL && now - st.animStart < 14;
		float ct = now - st.animStart;
		float clubBlend = club ? (ct < 2 ? ct / 2f : ct > 11 ? (14f - ct) / 3f : 1f) : 0f;
		float clubYaw = !club ? 0f : ct < 2 ? 0f : ct < 9 ? -38f : ct < 13 ? 28f : 0f;

		// how far he turned since last tick (his body, and the body bone in a pose clip)
		float dYaw = Mth.clamp(Mth.wrapDegrees(p.yBodyRot - t.lastBodyYaw), -45f, 45f);
		float dBone = Mth.clamp(Mth.wrapDegrees((t.byr - t.lastBoneYaw) * Mth.RAD_TO_DEG), -60f, 60f);
		t.lastBodyYaw = p.yBodyRot;
		t.lastBoneYaw = t.byr;
		float age = p.tickCount;

		float[] bp = new float[N], byw = new float[N];
		for (int i = 0; i < N; i++) {
			float f = i / (float) (N - 1);
			float pitch = Mth.lerp(trail, REST[i], TRAIL[i]) + lift * (0.5f + 0.5f * f) + Mth.sin(pos * 1.3324f + i * 0.6f) * 3f * walk
					+ Mth.sin(age * 0.06f + i * 0.7f) * 2f * (1f - walk);
			pitch = Mth.lerp(clubBlend, pitch, CLUB[i]);
			float yaw = Mth.sin(pos * 0.6662f - i * 0.55f) * (5f + 4f * i) * walk + Mth.sin(age * 0.04f - i * 0.5f) * (1.5f + i) * (1f - walk)
					+ clubYaw * (0.4f + 0.6f * f);
			bp[i] = pitch;
			byw[i] = yaw;
		}
		boolean fresh = !t.started;
		if (fresh) {
			t.started = true;
			dYaw = 0f;
			dBone = 0f;
			for (int i = 0; i < N; i++) {
				t.pitch[i] = bp[i];
				t.yaw[i] = byw[i];
			}
		}
		for (int i = 0; i < N; i++) {
			t.prevP[i] = t.outP[i];
			t.prevY[i] = t.outY[i];
			// follow the leader: a segment's target carries some of the one before it's swing, so waves run down the tail
			float tp = bp[i] + (i > 0 ? (t.pitch[i - 1] - bp[i - 1]) * 0.5f : 0f);
			float ty = byw[i] + (i > 0 ? (t.yaw[i - 1] - byw[i - 1]) * 0.6f : 0f);
			// a turn swings it the other way (it keeps some of its old heading)
			t.yaw[i] -= (dYaw + dBone) * LAG[i];
			t.vp[i] = (t.vp[i] + (tp - t.pitch[i]) * K[i]) * DAMP[i];
			t.vy[i] = (t.vy[i] + (ty - t.yaw[i]) * K[i]) * DAMP[i];
			t.pitch[i] = Mth.clamp(t.pitch[i] + t.vp[i], P_MIN, P_MAX);
			t.yaw[i] = Mth.clamp(t.yaw[i] + t.vy[i], -Y_MAX, Y_MAX);
		}
		collide(t, p, HumungousaurKit.stage(p) >= 2, club);
		if (fresh) {
			// the first tick: no swing in from nothing
			System.arraycopy(t.outP, 0, t.prevP, 0, N);
			System.arraycopy(t.outY, 0, t.prevY, 0, N);
		}
	}

	// ---------------------------------------------------------------- collision

	/** Settles each segment (root first) out of blocks and his own body / legs; writes outP / outY. */
	private static void collide(Tail t, AbstractClientPlayer p, boolean hasClub, boolean club) {
		Matrix4f toWorld = new Matrix4f().scale(p.getScale()).rotateY((180f - p.yBodyRot) * Mth.DEG_TO_RAD).scale(-0.9375f, -0.9375f, 0.9375f)
				.translate(0f, -1.501f, 0f).translate(t.bx / 16f, t.by / 16f, t.bz / 16f).rotateZYX(t.bzr, t.byr, t.bxr);
		Matrix4f toModel = new Matrix4f().translate(t.bx, t.by, t.bz).rotateZYX(t.bzr, t.byr, t.bxr);
		// legs: the last rendered swing, and where the walk cycle puts them next tick
		float ahead = Mth.cos((p.walkAnimation.position() + p.walkAnimation.speed()) * 0.6662f) * 1.4f * p.walkAnimation.speed();
		float[] rLeg = { t.rlxr, ahead }, lLeg = { t.llxr, -ahead };
		Ctx c = new Ctx(p, toWorld, toModel, t, rLeg, lLeg, !club);
		Vector3f start = new Vector3f(0f, ROOT_Y, ROOT_Z);
		for (int i = 0; i < N; i++) {
			boolean tip = i == N - 1 && hasClub;
			float bpch = t.pitch[i], byaw = t.yaw[i];
			float cp = t.colP[i] * 0.8f, cy = t.colY[i] * 0.8f;
			if (Math.abs(cp) < 0.5f) {
				cp = 0f;
			}
			if (Math.abs(cy) < 0.5f) {
				cy = 0f;
			}
			if (c.hit(i, start, bpch + cp, byaw + cy, tip)) {
				cp = t.colP[i];
				cy = t.colY[i];
				if (c.hit(i, start, bpch + cp, byaw + cy, tip)) {
					float side = cy != 0f ? Math.signum(cy) : byaw != 0f ? -Math.signum(byaw) : 1f;
					boolean found = false;
					float op = cp, oy = cy;
					for (int k = 1; k <= STEPS && !found; k++) {
						float d = STEP * k;
						float[][] tries = { { op + d, oy }, { op, oy + side * d }, { op, oy - side * d }, { op - d, oy }, { op + d, oy + side * d } };
						for (float[] tr : tries) {
							float pp = Mth.clamp(bpch + tr[0], P_MIN, P_MAX), yy = Mth.clamp(byaw + tr[1], -Y_MAX, Y_MAX);
							if (!c.hit(i, start, pp, yy, tip)) {
								cp = pp - bpch;
								cy = yy - byaw;
								found = true;
								break;
							}
						}
					}
					if (!found) {
						cp = Mth.clamp(bpch + op + STEP * STEPS, P_MIN, P_MAX) - bpch;
						cy = oy;
					}
				}
			}
			t.colP[i] = cp;
			t.colY[i] = cy;
			t.outP[i] = Mth.clamp(bpch + cp, P_MIN, P_MAX);
			t.outY[i] = Mth.clamp(byaw + cy, -Y_MAX, Y_MAX);
			start = end(start, t.outP[i], t.outY[i], LEN[i]);
		}
	}

	/** The collision test for one segment pose. */
	private static final class Ctx {
		final AbstractClientPlayer p;
		final Matrix4f toWorld, toModel;
		final Tail t;
		final float[] rLeg, lLeg;
		final boolean self;
		final BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos();

		Ctx(AbstractClientPlayer p, Matrix4f toWorld, Matrix4f toModel, Tail t, float[] rLeg, float[] lLeg, boolean self) {
			this.p = p;
			this.toWorld = toWorld;
			this.toModel = toModel;
			this.t = t;
			this.rLeg = rLeg;
			this.lLeg = lLeg;
			this.self = self;
		}

		boolean hit(int i, Vector3f start, float pitch, float yaw, boolean tip) {
			Vector3f d = dir(pitch, yaw), down = down(pitch, yaw), side = side(yaw);
			float hw = HALF[i], hh = HALF[i] * HH;
			float len = LEN[i];
			Vector3f mid = new Vector3f(d).mul(len * 0.5f).add(start);
			Vector3f end = new Vector3f(d).mul(len).add(start);
			Vector3f[] pts = { end, new Vector3f(down).mul(hh).add(end), new Vector3f(down).mul(hh).add(mid), new Vector3f(side).mul(hw).add(end),
					new Vector3f(side).mul(-hw).add(end), new Vector3f(down).mul(-hh).add(end) };
			for (Vector3f q : pts) {
				if (blocked(q) || (self && inSelf(q, i))) {
					return true;
				}
			}
			if (tip) {
				// the club past the tip
				Vector3f ce = new Vector3f(d).mul(CLUB_LEN).add(end);
				Vector3f[] cl = { ce, new Vector3f(down).mul(CLUB_HH).add(ce), new Vector3f(side).mul(CLUB_HW).add(ce), new Vector3f(side).mul(-CLUB_HW).add(ce),
						new Vector3f(down).mul(CLUB_HH).add(end) };
				for (Vector3f q : cl) {
					if (blocked(q) || (self && inSelf(q, i))) {
						return true;
					}
				}
			}
			return false;
		}

		/** A body-bone pixel point inside a block's collision shape? */
		boolean blocked(Vector3f q) {
			Vector3f w = toWorld.transformPosition(q.x / 16f, q.y / 16f, q.z / 16f, new Vector3f());
			double x = p.getX() + w.x, y = p.getY() + w.y, z = p.getZ() + w.z;
			bp.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
			Level level = p.level();
			if (!level.isLoaded(bp)) {
				return false;
			}
			BlockState s = level.getBlockState(bp);
			if (s.isAir()) {
				return false;
			}
			VoxelShape shape = s.getCollisionShape(level, bp);
			if (shape.isEmpty()) {
				return false;
			}
			double lx = x - bp.getX(), ly = y - bp.getY(), lz = z - bp.getZ();
			for (AABB box : shape.toAabbs()) {
				if (lx >= box.minX && lx <= box.maxX && ly >= box.minY && ly <= box.maxY && lz >= box.minZ && lz <= box.maxZ) {
					return true;
				}
			}
			return false;
		}

		/** Inside his own torso (past the root) or either leg (any of the leg swings)? */
		boolean inSelf(Vector3f q, int i) {
			final float m = 0.25f;
			if (i > 0 && Math.abs(q.x) < 4.25f + m && q.y > -m && q.y < 12.25f + m && Math.abs(q.z) < 2.25f + m) {
				return true;
			}
			Vector3f mq = toModel.transformPosition(q, new Vector3f());
			return inLeg(mq, t.rlx, t.rly, t.rlz, rLeg) || inLeg(mq, t.llx, t.lly, t.llz, lLeg);
		}

		private static boolean inLeg(Vector3f mq, float px, float py, float pz, float[] swings) {
			final float m = 0.35f;
			float x = mq.x - px, y = mq.y - py, z = mq.z - pz;
			if (Math.abs(x) > 2.25f + m) {
				return false;
			}
			for (float a : swings) {
				float c = Mth.cos(a), s = Mth.sin(a);
				float ly = y * c + z * s, lz = -y * s + z * c;
				if (ly > -0.25f - m && ly < 12.25f + m && Math.abs(lz) < 2.25f + m) {
					return true;
				}
			}
			return false;
		}
	}

	// ---------------------------------------------------------------- geometry (shared by tick and render)

	/** A segment's direction for absolute pitch / yaw (degrees), body-bone pixels (y down, z back): Ry(yaw) * Rx(pitch) * +z. */
	private static Vector3f dir(float pitch, float yaw) {
		float p = pitch * Mth.DEG_TO_RAD, y = yaw * Mth.DEG_TO_RAD;
		return new Vector3f(Mth.cos(p) * Mth.sin(y), -Mth.sin(p), Mth.cos(p) * Mth.cos(y));
	}

	/** The segment's belly direction (its local +y). */
	private static Vector3f down(float pitch, float yaw) {
		float p = pitch * Mth.DEG_TO_RAD, y = yaw * Mth.DEG_TO_RAD;
		return new Vector3f(Mth.sin(p) * Mth.sin(y), Mth.cos(p), Mth.sin(p) * Mth.cos(y));
	}

	/** The segment's local +x. */
	private static Vector3f side(float yaw) {
		float y = yaw * Mth.DEG_TO_RAD;
		return new Vector3f(Mth.cos(y), 0f, -Mth.sin(y));
	}

	private static Vector3f end(Vector3f start, float pitch, float yaw, float len) {
		return dir(pitch, yaw).mul(len).add(start);
	}

	// ---------------------------------------------------------------- rendering

	static void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model, float partial,
			int stage, float burst) {
		Tail t = TAILS.get(p.getId());
		if (t != null) {
			// the pose the next tick's collision tests against
			t.bx = model.body.x;
			t.by = model.body.y;
			t.bz = model.body.z;
			t.bxr = model.body.xRot;
			t.byr = model.body.yRot;
			t.bzr = model.body.zRot;
			t.rlx = model.rightLeg.x;
			t.rly = model.rightLeg.y;
			t.rlz = model.rightLeg.z;
			t.rlxr = model.rightLeg.xRot;
			t.llx = model.leftLeg.x;
			t.lly = model.leftLeg.y;
			t.llz = model.leftLeg.z;
			t.llxr = model.leftLeg.xRot;
		}
		// first person: the camera sits inside him -- no tail in its face
		Minecraft mc = Minecraft.getInstance();
		if (p == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()) {
			return;
		}
		float[] pitch = new float[N], yaw = new float[N];
		for (int i = 0; i < N; i++) {
			if (t != null && t.started) {
				pitch[i] = Mth.lerp(partial, t.prevP[i], t.outP[i]);
				yaw[i] = Mth.lerp(partial, t.prevY[i], t.outY[i]);
			} else {
				pitch[i] = REST[i];
			}
		}
		AlienParts.on(pose, model.body, () -> {
			VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(EXTRAS));
			Vector3f start = new Vector3f(0f, ROOT_Y, ROOT_Z);
			for (int i = 0; i < N; i++) {
				float hw = HALF[i], hh = HALF[i] * HH, len = LEN[i];
				pose.pushPose();
				pose.translate(start.x / 16f, start.y / 16f, start.z / 16f);
				pose.mulPose(Axis.YP.rotationDegrees(yaw[i]));
				pose.mulPose(Axis.XP.rotationDegrees(pitch[i]));
				// the hide: overlapping its neighbours at the joints (the root sinks into his back)
				float z0 = i == 0 ? -1.4f : -0.7f;
				AlienParts.box(pose, vc, -hw, -hh, z0, hw, hh, len + 0.5f, HIDE_U0, HIDE_V0, HIDE_U1, HIDE_V1, 0xFFFFFFFF, light);
				segmentSpikes(pose, vc, light, i, hw, hh, len, stage, burst);
				if (i == N - 1 && stage >= 2) {
					pose.translate(0f, 0f, len / 16f);
					clubBall(pose, vc, light, burst);
				}
				pose.popPose();
				start = end(start, pitch[i], yaw[i], len);
			}
		});
	}

	/** The spikes along one segment for his size (in the segment's frame: -y = its top, +z = toward the tip). */
	private static void segmentSpikes(PoseStack pose, VertexConsumer vc, int light, int i, float hw, float hh, float len, int stage, float burst) {
		float taper = 1f - 0.12f * i;
		if (stage <= 0) {
			// base size: small bone nubs
			if (i >= 1 && i <= 3) {
				spike(pose, vc, light, 0f, -hh, len * 0.5f, 0f, -20f, 0.9f + 2.0f * burst, 0.9f);
			} else if (burst > 0f) {
				spike(pose, vc, light, 0f, -hh, len * 0.5f, 0f, -20f, 2.0f * burst, 0.9f);
			}
		} else if (stage == 1) {
			// x2.2: a bone spike along the top of every segment
			spike(pose, vc, light, 0f, -hh, len * 0.45f, 0f, -25f, (2.0f + 1.8f * burst) * taper, 1.2f);
		} else {
			// x3.0: bigger spikes, two to a segment near the root
			float h = (3.2f + 1.8f * burst) * taper;
			if (i <= 2) {
				spike(pose, vc, light, 0f, -hh, len * 0.25f, 0f, -25f, h, 1.5f);
				spike(pose, vc, light, 0f, -hh, len * 0.75f, 0f, -25f, h * 0.85f, 1.3f);
			} else {
				spike(pose, vc, light, 0f, -hh, len * 0.5f, 0f, -25f, h, 1.3f);
			}
		}
		if (burst > 0f) {
			// the Spike Burst: spikes standing out of both sides of every segment
			float h = 2.4f * burst * taper * (1f + 0.25f * stage);
			spike(pose, vc, light, hw, -hh * 0.3f, len * 0.5f, 70f, -25f, h, 1.0f);
			spike(pose, vc, light, -hw, -hh * 0.3f, len * 0.5f, -70f, -25f, h, 1.0f);
		}
	}

	/** x3.0: the spiked ball on the tip, studded with bone (in the tip segment's frame, at its end). */
	private static void clubBall(PoseStack pose, VertexConsumer vc, int light, float burst) {
		AlienParts.box(pose, vc, -CLUB_HW, -CLUB_HH, -0.8f, CLUB_HW, CLUB_HH, CLUB_LEN, HIDE_U0, HIDE_V0, HIDE_U1, HIDE_V1, 0xFFFFFFFF, light);
		float h = 1.9f + 1.3f * burst;
		float zA = 0.6f, zB = CLUB_LEN - 1.2f;
		for (float z : new float[] { zA, zB }) {
			spike(pose, vc, light, 0f, -CLUB_HH, z, 0f, 0f, h, 1.1f); // top
			spike(pose, vc, light, CLUB_HW, 0f, z, 90f, 0f, h, 1.1f); // sides
			spike(pose, vc, light, -CLUB_HW, 0f, z, -90f, 0f, h, 1.1f);
			spike(pose, vc, light, 0f, CLUB_HH, z, 180f, 0f, h * 0.8f, 1.0f); // bottom
		}
		// the corners and the end
		spike(pose, vc, light, CLUB_HW, -CLUB_HH, CLUB_LEN * 0.4f, 45f, 0f, h * 0.9f, 1.0f);
		spike(pose, vc, light, -CLUB_HW, -CLUB_HH, CLUB_LEN * 0.4f, -45f, 0f, h * 0.9f, 1.0f);
		spike(pose, vc, light, CLUB_HW, CLUB_HH, CLUB_LEN * 0.4f, 135f, 0f, h * 0.8f, 1.0f);
		spike(pose, vc, light, -CLUB_HW, CLUB_HH, CLUB_LEN * 0.4f, -135f, 0f, h * 0.8f, 1.0f);
		spike(pose, vc, light, 0f, 0f, CLUB_LEN, 0f, -90f, h * 1.1f, 1.2f);
	}

	/**
	 * A bone spike rooted at (x, y, z) pixels, pointing "up" (-y) turned {@code rz} about z (90 = out of +x) then tilted
	 * {@code rx} about x (negative leans it back toward the tip; -90 points it straight down the tail): a wide base and a
	 * narrow point.
	 */
	private static void spike(PoseStack pose, VertexConsumer vc, int light, float x, float y, float z, float rz, float rx, float h, float w) {
		if (h <= 0.05f) {
			return;
		}
		pose.pushPose();
		pose.translate(x / 16f, y / 16f, z / 16f);
		if (rz != 0f) {
			pose.mulPose(Axis.ZP.rotationDegrees(rz));
		}
		if (rx != 0f) {
			pose.mulPose(Axis.XP.rotationDegrees(rx));
		}
		float b = w * 0.5f, tp = w * 0.25f;
		AlienParts.box(pose, vc, -b, -h * 0.55f, -b, b, 0.4f, b, BONE_U0, BONE_V0, BONE_U1, BONE_V1, 0xFFFFFFFF, light);
		AlienParts.box(pose, vc, -tp, -h, -tp, tp, -h * 0.5f, tp, BONE_U0, BONE_V0, BONE_U1, BONE_V1, 0xFFFFFFFF, light);
		pose.popPose();
	}
}
