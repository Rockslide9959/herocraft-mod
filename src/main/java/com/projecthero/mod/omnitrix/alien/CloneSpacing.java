package com.projecthero.mod.omnitrix.alien;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 (user: "make the limit to dittos clones 10 please and give them their own position instead of just standing inside each
 * other please" / "make the limit on his clones 10 and make them each have their own position, they shouldnt stand inside each other
 * like how they currently do please"): where a duplicating alien's clones stand, shared by Ditto and Echo Echo. Every clone has a slot,
 * every slot a spot of its own: a two-row fan behind him when they follow, rings round whatever they surround, and a push that keeps
 * any two apart on the way there.
 */
public final class CloneSpacing {
	/** The most clones either alien keeps. */
	public static final int LIMIT = 10;
	/** The closest two clones stand: their arms just clear each other at Ditto's and Echo Echo's size. */
	public static final double GAP = 0.72;

	/** The fan: {radius, degrees either side of straight behind him}. Any two spots are at least 0.95 blocks apart. */
	private static final double[][] FAN = { { 1.3, 70 }, { 1.3, -70 }, { 1.3, 22 }, { 1.3, -22 }, { 2.35, 100 }, { 2.35, -100 }, { 2.35, 60 }, { 2.35, -60 },
			{ 2.35, 20 }, { 2.35, -20 } };

	private CloneSpacing() {
	}

	/** Clone {@code slot}'s spot in the fan behind {@code owner}, turning with him. */
	public static Vec3 fan(Player owner, int slot) {
		double[] s = FAN[slot % FAN.length];
		double r = s[0] + slot / FAN.length; // past the fan (nothing does this today) a wider copy of it, never the same spot
		double a = (owner.getYRot() + 180.0 + s[1]) * Mth.DEG_TO_RAD;
		return owner.position().add(-Math.sin(a) * r, 0, Math.cos(a) * r);
	}

	/** A spot in front of {@code owner}, off to one side: where a clone pulled out of him ends up, so he sees it happen. */
	public static Vec3 peel(Player owner, int slot, double r) {
		double side = (slot % 2 == 0 ? 1 : -1) * (40.0 + (slot / 2) * 24.0);
		double a = (owner.getYRot() + side) * Mth.DEG_TO_RAD;
		return owner.position().add(-Math.sin(a) * r, 0, Math.cos(a) * r);
	}

	/** Clone {@code index}'s spot in rings round {@code centre}: the first ring {@code r0} out, as many as fit, then the next ring out. */
	public static Vec3 ring(Vec3 centre, int index, double r0) {
		double r = r0;
		int i = index;
		for (int band = 0;; band++) {
			int fits = Math.max(3, (int) (Mth.TWO_PI * r / GAP));
			if (i < fits) {
				double a = Mth.TWO_PI * i / fits + band * 0.5;
				return centre.add(Math.cos(a) * r, 0, Math.sin(a) * r);
			}
			i -= fits;
			r += GAP;
		}
	}

	/** {@code want} if {@code type} fits there, else halfway back to {@code home}, else {@code home}: never inside a wall. */
	public static Vec3 clear(Level level, EntityType<?> type, Vec3 want, Vec3 home) {
		if (level.noCollision(type.getDimensions().makeBoundingBox(want))) {
			return want;
		}
		Vec3 half = want.lerp(home, 0.5);
		return level.noCollision(type.getDimensions().makeBoundingBox(half)) ? half : home;
	}

	/**
	 * A brother is within arm's length of {@code me}. A clone this close to its spot stops there instead of shouldering in: where a wall has
	 * squeezed two spots together they settle side by side, not bumping back and forth. Any two fan spots are farther apart than this.
	 */
	public static boolean crowded(Entity me, Iterable<? extends Entity> brothers) {
		for (Entity e : brothers) {
			if (e != me && Math.abs(me.getY() - e.getY()) <= 0.9) {
				double dx = me.getX() - e.getX(), dz = me.getZ() - e.getZ();
				if (dx * dx + dz * dz < 0.9 * 0.9) {
					return true;
				}
			}
		}
		return false;
	}

	/** The way {@code me} must step this tick to get clear of any of {@code brothers} closer than {@link #GAP}. */
	public static Vec3 apart(Entity me, Iterable<? extends Entity> brothers) {
		double x = 0, z = 0;
		for (Entity e : brothers) {
			if (e == me) {
				continue;
			}
			double dx = me.getX() - e.getX(), dz = me.getZ() - e.getZ();
			double d2 = dx * dx + dz * dz;
			if (d2 >= GAP * GAP || Math.abs(me.getY() - e.getY()) > 0.9) {
				continue;
			}
			if (d2 < 1.0e-4) {
				// exactly on top of each other: the ids pick the way, opposite ways for the two of them
				double a = (me.getId() - e.getId()) * 2.4;
				dx = Math.cos(a) * 0.01;
				dz = Math.sin(a) * 0.01;
				d2 = 1.0e-4;
			}
			double d = Math.sqrt(d2);
			double push = (GAP - d) * 0.45 / d;
			x += dx * push;
			z += dz * push;
		}
		return new Vec3(x, 0, z);
	}
}
