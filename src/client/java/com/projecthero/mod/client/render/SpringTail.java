package com.projecthero.mod.client.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * v0.17.3 (user: "For the Saiyan tail please make it smooth and round like Spider-Monkey's tail and give it the same reaction
 * rules"): the spring-chain tail Spidermonkey has had since v0.17.2, pulled out so the Saiyan tail runs on exactly the same rules.
 *
 * <p>One instance per player: {@code n} links in WORLD space, ticked on the client. Each tick the links coast on their own
 * velocity (verlet, damped), fall a little, then spring toward a rest shape built in the player model's frame on the parent
 * link's current position; the link lengths are restored, a per-tail rule keeps them out of the wearer's body, and every link is
 * pushed out of any block's collision box. The reaction rules ({@link #restDir}): the idle curl sways slowly, walking blends to the
 * trailing walk shape with a sway that runs down the tail with the walk cycle, turning swings it (it lags behind the body yaw),
 * and when {@code loose} (swinging, flying, falling fast) the spring goes soft and the damping light so it streams behind.
 *
 * <p>Model space here is the player model's pixels as LivingEntityRenderer places it (x = his left... y DOWN, z behind him, feet
 * at y 24). Holds only {@link Vec3}s (no world, entity or block state), so a static map of these needs only a disconnect clear.
 */
public final class SpringTail {
	/**
	 * The monkey tail's rest directions (7 links, model space: x = his left... y down, z behind him), idle: low out behind, up,
	 * hooked over at the tip. Spidermonkey's since v0.17.2; the Saiyan's since v0.17.3.
	 */
	public static final float[][] MONKEY_IDLE = { { 0, 0.75f, 1 }, { 0, 0.2f, 1 }, { 0, -0.5f, 1 }, { 0, -1, 0.45f }, { 0, -1, -0.1f },
			{ 0, -0.6f, -0.8f }, { 0, 0.3f, -1 } };
	/** ...and walking: trailing out further, the hook opened up. */
	public static final float[][] MONKEY_WALK = { { 0, 0.6f, 1 }, { 0, 0.15f, 1 }, { 0, -0.15f, 1 }, { 0, -0.45f, 1 }, { 0, -0.85f, 0.6f },
			{ 0, -1, 0 }, { 0, -0.5f, -0.8f } };

	public final int n;
	private final float[] len;
	public final Vec3[] pos;
	public final Vec3[] prev;
	public final Vec3[] last;
	public boolean init;

	/** Rest direction for link {@code i} (1..n) given the parent joint's current model-space position. */
	@FunctionalInterface
	public interface Aim {
		Vec3 dir(int i, Vec3 parentModel);
	}

	/** Keeps a link out of the body: the model-space point moved, or null to leave it. */
	@FunctionalInterface
	public interface Keep {
		Vec3 keep(int i, Vec3 model);
	}

	public SpringTail(float[] len) {
		this.n = len.length;
		this.len = len;
		this.pos = new Vec3[n + 1];
		this.prev = new Vec3[n + 1];
		this.last = new Vec3[n + 1];
	}

	// ---------------------------------------------------------------- model space <-> world space

	/** The render scale of a player's body: the scale attribute times the player renderer's own 0.9375. */
	public static float scale(Player p) {
		return p.getScale() * 0.9375f;
	}

	/** Model pixels -> world, as LivingEntityRenderer places the model (yaw = body yaw, base = his feet). */
	public static Vec3 toWorld(Player p, Vec3 base, float yaw, double mx, double my, double mz) {
		double s = scale(p);
		double x = -mx / 16.0 * s, y = -(my / 16.0 - 1.501) * s, z = mz / 16.0 * s;
		double a = Math.toRadians(180.0 - yaw), c = Math.cos(a), sn = Math.sin(a);
		return base.add(x * c + z * sn, y, -x * sn + z * c);
	}

	/** World -> model pixels (the inverse of {@link #toWorld}). */
	public static Vec3 toModel(Player p, Vec3 base, float yaw, Vec3 w) {
		double s = scale(p);
		Vec3 d = w.subtract(base);
		double a = Math.toRadians(180.0 - yaw), c = Math.cos(a), sn = Math.sin(a);
		double x = d.x * c - d.z * sn, z = d.x * sn + d.z * c;
		return new Vec3(-x / s * 16.0, (-d.y / s + 1.501) * 16.0, z / s * 16.0);
	}

	/** Where the model's feet are (the player renderer drops a crouching player 2 px). */
	public static Vec3 base(Player p, float partial) {
		Vec3 b = partial >= 1f ? p.position() : p.getPosition(partial);
		return p.isCrouching() ? b.add(0, p.getScale() * -2.0 / 16.0, 0) : b;
	}

	/** A point on the body bone in model pixels for his stance (the body bone leans forward 0.5 rad and drops 3.2 px crouching). */
	public static Vec3 rootModel(Player p, float rootY, float rootZ) {
		if (!p.isCrouching()) {
			return new Vec3(0, rootY, rootZ);
		}
		float c = Mth.cos(0.5f), s = Mth.sin(0.5f);
		return new Vec3(0, 3.2f + rootY * c - rootZ * s, rootY * s + rootZ * c);
	}

	// ---------------------------------------------------------------- the reaction rules

	/**
	 * The rest direction of link {@code i} (1..n), model space, normalised: the idle shape blended toward the walk shape by
	 * {@code walk} (0..1), with the walk sway (running down the tail with the walk cycle {@code phase}, bigger toward the tip) and,
	 * standing, a slow idle sway of the outer links.
	 */
	public static Vec3 restDir(float[][] idle, float[][] walkShape, int i, int n, float walk, float phase, float age) {
		float[] a = idle[i - 1], b = walkShape[i - 1];
		float f = (i - 1f) / (n - 1f);
		double sway = Mth.sin(phase - i * 0.55f) * walk * 0.55f * (0.3f + f) + Mth.sin(age * 0.07f + i * 0.6f) * 0.12f * (1f - walk) * f;
		return new Vec3(Mth.lerp(walk, a[0], b[0]) + sway, Mth.lerp(walk, a[1], b[1]), Mth.lerp(walk, a[2], b[2])).normalize();
	}

	/** How far into the walk shape: the walk animation's speed, full at a brisk walk. */
	public static float walk(Player p) {
		return Mth.clamp(p.walkAnimation.speed() * 1.6f, 0f, 1f);
	}

	/** The walk cycle's phase (the same as the legs'). */
	public static float phase(Player p) {
		return p.walkAnimation.position() * 0.6662f;
	}

	/** The standard body rule: no link nearer his front than {@code behind} px across his back and legs (null = untouched). */
	public static Vec3 keepBehind(Vec3 m, float behind) {
		if (m.z < behind && m.y > -4 && m.y < 25 && Math.abs(m.x) < 6.5) {
			return new Vec3(m.x, m.y, behind);
		}
		return null;
	}

	/** True when the chain must start over (never ticked, or the root jumped: a teleport). */
	public boolean stale(Vec3 root) {
		return !init || pos[0].distanceToSqr(root) > 9.0;
	}

	/** (Re)starts the chain in the given rest shape. */
	public void restart(Player p, Vec3 base, float yaw, Vec3 rm, Vec3 root, float[][] rest) {
		Vec3 at = rm;
		pos[0] = root;
		for (int i = 1; i <= n; i++) {
			float[] d = rest[i - 1];
			Vec3 dir = new Vec3(d[0], d[1], d[2]).normalize().scale(len[i - 1]);
			at = at.add(dir);
			pos[i] = toWorld(p, base, yaw, at.x, at.y, at.z);
		}
		for (int i = 0; i <= n; i++) {
			prev[i] = pos[i];
			last[i] = pos[i];
		}
		init = true;
	}

	/**
	 * One client tick.
	 *
	 * @param rm     the root in model pixels this tick (see {@link #rootModel})
	 * @param rest   the shape a (re)start uses
	 * @param ride   carry the whole chain along with the root first (fast travel on a line, where it would otherwise stream away)
	 * @param loose  soft spring, light damping, more gravity: streaming behind
	 * @param spring how hard each link springs toward its rest direction this tick (0..1)
	 */
	public void tick(Player p, float yaw, Vec3 base, Vec3 rm, float[][] rest, boolean ride, boolean loose, float spring, Aim aim, Keep keep) {
		Vec3 root = toWorld(p, base, yaw, rm.x, rm.y, rm.z);
		double s = scale(p) / 16.0;
		if (stale(root)) {
			restart(p, base, yaw, rm, root, rest);
		}
		for (int i = 0; i <= n; i++) {
			last[i] = pos[i];
		}
		if (ride) {
			Vec3 shift = root.subtract(pos[0]);
			for (int i = 1; i <= n; i++) {
				pos[i] = pos[i].add(shift);
				prev[i] = prev[i].add(shift);
			}
		}
		pos[0] = root;
		prev[0] = root;
		double grav = (loose ? 0.012 : 0.004) * scale(p);
		for (int i = 1; i <= n; i++) {
			Vec3 v = pos[i].subtract(prev[i]).scale(loose ? 0.9 : 0.72);
			prev[i] = pos[i];
			pos[i] = pos[i].add(v).add(0, -grav, 0);
		}
		Level level = p.level();
		for (int iter = 0; iter < 3; iter++) {
			Vec3 parentModel = toModel(p, base, yaw, pos[0]);
			for (int i = 1; i <= n; i++) {
				if (iter == 0) {
					// spring toward the rest shape, built on the parent link where it is now
					Vec3 dir = aim.dir(i, parentModel);
					Vec3 targetModel = parentModel.add(dir.scale(len[i - 1]));
					Vec3 target = toWorld(p, base, yaw, targetModel.x, targetModel.y, targetModel.z);
					pos[i] = pos[i].add(target.subtract(pos[i]).scale(spring));
				}
				// keep the link's length
				Vec3 d = pos[i].subtract(pos[i - 1]);
				double l = d.length();
				double want = len[i - 1] * s;
				pos[i] = l < 1e-6 ? pos[i - 1].add(0, -want, 0) : pos[i - 1].add(d.scale(want / l));
				// never inside the body
				Vec3 m = toModel(p, base, yaw, pos[i]);
				Vec3 kept = keep.keep(i, m);
				if (kept != null) {
					pos[i] = toWorld(p, base, yaw, kept.x, kept.y, kept.z);
				}
				// never through a block
				pos[i] = outOfBlocks(level, pos[i], pos[i - 1]);
				parentModel = toModel(p, base, yaw, pos[i]);
			}
		}
	}

	/** {@code pt} pushed out of any block collision box it is in, along the shortest way out. */
	public static Vec3 outOfBlocks(Level level, Vec3 pt, Vec3 parent) {
		for (int pass = 0; pass < 2; pass++) {
			BlockPos bp = BlockPos.containing(pt);
			BlockState bs = level.getBlockState(bp);
			if (bs.isAir()) {
				return pt;
			}
			VoxelShape shape = bs.getCollisionShape(level, bp);
			if (shape.isEmpty()) {
				return pt;
			}
			boolean moved = false;
			for (AABB box : shape.toAabbs()) {
				AABB b = box.move(bp).inflate(0.03);
				if (!b.contains(pt)) {
					continue;
				}
				double[] out = { b.minX - pt.x, b.maxX - pt.x, b.minY - pt.y, b.maxY - pt.y, b.minZ - pt.z, b.maxZ - pt.z };
				int best = 0;
				for (int j = 1; j < 6; j++) {
					if (Math.abs(out[j]) < Math.abs(out[best])) {
						best = j;
					}
				}
				pt = switch (best / 2) {
					case 0 -> new Vec3(pt.x + out[best], pt.y, pt.z);
					case 1 -> new Vec3(pt.x, pt.y + out[best], pt.z);
					default -> new Vec3(pt.x, pt.y, pt.z + out[best]);
				};
				moved = true;
				break;
			}
			if (!moved) {
				return pt;
			}
		}
		return pt;
	}

	// ---------------------------------------------------------------- drawing

	/** The rest-shape tip in the world (before the chain has ticked). */
	public Vec3 restTip(Player p, float partial, Vec3 rm, float[][] rest) {
		float yaw = Mth.lerp(partial, p.yBodyRotO, p.yBodyRot);
		Vec3 at = rm;
		for (int i = 1; i <= n; i++) {
			float[] d = rest[i - 1];
			at = at.add(new Vec3(d[0], d[1], d[2]).normalize().scale(len[i - 1]));
		}
		return toWorld(p, base(p, partial), yaw, at.x, at.y, at.z);
	}

	/**
	 * The simulated joints in this render's model space (blocks, the render pose's own units), {@code pts[0]} = {@code root}. The
	 * world points go through the inverse of the pose (camera-relative world, no camera rotation). Returns false (pts untouched
	 * beyond [0]) when the chain has not ticked or its root is further than {@code maxOff} from the drawn root (not in the level --
	 * the inventory screen -- or a pose the simulation does not know). When {@code shift} is set, a smaller offset is taken out of
	 * every joint so the drawn tail stays on the drawn root (a leaning flight pose, a spin).
	 */
	public boolean modelPoints(PoseStack pose, Vector3f root, float partial, Vector3f[] pts, float maxOff, boolean shift) {
		pts[0] = root;
		if (!init) {
			return false;
		}
		Matrix4f inv = new Matrix4f(pose.last().pose()).invert();
		Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
		Vec3 r0 = last[0].lerp(pos[0], partial).subtract(cam);
		Vector3f check = inv.transformPosition(new Vector3f((float) r0.x, (float) r0.y, (float) r0.z));
		if (check.distance(root) >= maxOff) {
			return false;
		}
		Vector3f off = shift ? new Vector3f(root).sub(check) : new Vector3f();
		for (int i = 1; i <= n; i++) {
			Vec3 w = last[i].lerp(pos[i], partial).subtract(cam);
			pts[i] = inv.transformPosition(new Vector3f((float) w.x, (float) w.y, (float) w.z)).add(off);
		}
		return true;
	}

	/** The rest shape's joints from {@code root} in the render's model space (blocks). */
	public void restPoints(Vector3f root, float[][] rest, Vector3f[] pts) {
		pts[0] = root;
		Vector3f at = new Vector3f(root);
		for (int i = 1; i <= n; i++) {
			float[] d = rest[i - 1];
			at = new Vector3f(at).add(new Vector3f(d[0], d[1], d[2]).normalize(len[i - 1] / 16f));
			pts[i] = at;
		}
	}
}
