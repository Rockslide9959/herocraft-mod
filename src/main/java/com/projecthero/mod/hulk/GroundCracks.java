package com.projecthero.mod.hulk;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.4 (user): the ground cracking under the Hulk's moves -- the vanilla block-breaking crack overlay ("destroy
 * progress") drawn on the surface blocks, plus dust pillars. Nothing is broken: each crack is a decal that fades out and
 * is cleared after its life. A crack has its own breaker id from a reserved negative range (never an entity id), one per
 * block, so up to {@link #MAX_ACTIVE} can show at once. Ticks itself (registered once, on first use) and is cleared
 * with the rest of the Hulk's session state.
 */
public final class GroundCracks {
	private static final class Crack {
		final ResourceKey<Level> dim;
		final BlockPos pos;
		final int id;
		long start;
		int life;
		int stage;
		int sent = -1;

		Crack(ResourceKey<Level> dim, BlockPos pos, int id) {
			this.dim = dim;
			this.pos = pos;
			this.id = id;
		}
	}

	private static final int MAX_ACTIVE = 1500;
	private static final int ID_BASE = -1_900_000_000;
	private static final int ID_SPAN = 1_000_000;
	private static final Map<String, Crack> ACTIVE = new HashMap<>();
	private static int nextId;
	private static long serverTick;

	static {
		ServerTickEvents.END_SERVER_TICK.register(GroundCracks::tick);
	}

	private GroundCracks() {
	}

	public static void clearSessionState() {
		ACTIVE.clear();
		nextId = 0;
	}

	/** How many crack decals are live (tests). */
	public static int activeCount() {
		return ACTIVE.size();
	}

	/** The solid surface block at {@code (x, z)} near {@code y} (two above to four below), or null. */
	public static BlockPos surface(ServerLevel level, double x, double y, double z) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		int bx = (int) Math.floor(x);
		int bz = (int) Math.floor(z);
		int top = (int) Math.floor(y) + 1;
		for (int by = top; by >= top - 6; by--) {
			p.set(bx, by, bz);
			if (!level.isLoaded(p)) {
				return null;
			}
			BlockState st = level.getBlockState(p);
			if (!st.isAir() && !st.canBeReplaced() && st.getFluidState().isEmpty() && !st.getCollisionShape(level, p).isEmpty()) {
				BlockState above = level.getBlockState(p.above());
				if (above.isAir() || above.canBeReplaced()) {
					return p.immutable();
				}
			}
		}
		return null;
	}

	/** Crack one block ({@code stage} 0..9) for {@code life} ticks. */
	public static void crack(ServerLevel level, BlockPos pos, int stage, int life) {
		if (ACTIVE.size() >= MAX_ACTIVE) {
			return;
		}
		String key = level.dimension().location() + "|" + pos.asLong();
		Crack c = ACTIVE.get(key);
		if (c == null) {
			c = new Crack(level.dimension(), pos.immutable(), ID_BASE - (nextId++ % ID_SPAN));
			ACTIVE.put(key, c);
		}
		c.start = serverTick;
		c.life = Math.max(c.life, life);
		c.stage = Math.max(c.stage, Math.min(9, Math.max(0, stage)));
		if (c.sent != c.stage) {
			level.destroyBlockProgress(c.id, c.pos, c.stage);
			c.sent = c.stage;
		}
	}

	/** Cracks the surface under {@code at}. Returns the block cracked, or null. */
	public static BlockPos crackAt(ServerLevel level, Vec3 at, int stage, int life) {
		BlockPos s = surface(level, at.x, at.y, at.z);
		if (s != null) {
			crack(level, s, stage, life);
		}
		return s;
	}

	/**
	 * A star of jagged cracks round {@code center}: a cracked-through disc in the middle and {@code rays} cracks running out
	 * to {@code radius}, fading towards their tips, with dust bursting out of a few of them.
	 */
	public static void star(ServerLevel level, Vec3 center, double radius, int rays, int life) {
		if (radius <= 0.0) {
			return;
		}
		double inner = Math.min(1.8, radius * 0.4);
		for (double dx = -inner; dx <= inner; dx += 1.0) {
			for (double dz = -inner; dz <= inner; dz += 1.0) {
				if (dx * dx + dz * dz <= inner * inner) {
					crackAt(level, center.add(dx, 0, dz), 9, life);
				}
			}
		}
		long seed = level.getGameTime() * 31L + (long) (center.x * 7) + (long) (center.z * 13);
		java.util.Random rnd = new java.util.Random(seed);
		for (int r = 0; r < rays; r++) {
			double a = (Math.PI * 2 * r) / rays + rnd.nextDouble() * 0.5;
			double len = radius * (0.65 + rnd.nextDouble() * 0.35);
			double x = center.x;
			double z = center.z;
			for (double d = inner; d <= len; d += 0.8) {
				a += (rnd.nextDouble() - 0.5) * 0.45; // jagged
				x = center.x + Math.cos(a) * d;
				z = center.z + Math.sin(a) * d;
				int stage = 9 - (int) (6.0 * d / len);
				BlockPos s = crackAt(level, new Vec3(x, center.y, z), stage, life);
				if (s != null && rnd.nextInt(4) == 0) {
					dust(level, s, 3);
				}
			}
		}
	}

	/** A crack line {@code length} long and {@code width} wide from {@code from} along {@code dir} (horizontal). */
	public static void line(ServerLevel level, Vec3 from, Vec3 dir, double length, double width, int stage, int life) {
		Vec3 f = new Vec3(dir.x, 0, dir.z);
		if (f.lengthSqr() < 1.0e-6) {
			return;
		}
		f = f.normalize();
		Vec3 side = new Vec3(-f.z, 0, f.x);
		for (double d = 0.0; d <= length; d += 0.8) {
			for (double w = -width * 0.5; w <= width * 0.5 + 1.0e-3; w += 0.9) {
				crackAt(level, from.add(f.scale(d)).add(side.scale(w)), stage, life);
			}
		}
	}

	/** Dust pillars of the block itself shooting up off a cracked block. */
	public static void dust(ServerLevel level, BlockPos s, int count) {
		BlockState st = level.getBlockState(s);
		if (st.isAir()) {
			return;
		}
		level.sendParticles(new BlockParticleOption(ParticleTypes.DUST_PILLAR, st), s.getX() + 0.5, s.getY() + 1.05, s.getZ() + 0.5,
				count, 0.3, 0.1, 0.3, 0.15);
	}

	private static void tick(MinecraftServer server) {
		serverTick++;
		if (ACTIVE.isEmpty() || serverTick % 5 != 0) {
			return;
		}
		Iterator<Crack> it = ACTIVE.values().iterator();
		while (it.hasNext()) {
			Crack c = it.next();
			ServerLevel level = server.getLevel(c.dim);
			long age = serverTick - c.start;
			if (level == null) {
				it.remove();
				continue;
			}
			if (age >= c.life) {
				level.destroyBlockProgress(c.id, c.pos, -1);
				it.remove();
				continue;
			}
			// the last 40% of its life it heals over
			double left = (c.life - age) / (0.4 * c.life);
			int now = left >= 1.0 ? c.stage : (int) Math.floor(c.stage * Math.max(0.0, left));
			if (now != c.sent) {
				level.destroyBlockProgress(c.id, c.pos, now);
				c.sent = now;
			}
		}
	}
}
