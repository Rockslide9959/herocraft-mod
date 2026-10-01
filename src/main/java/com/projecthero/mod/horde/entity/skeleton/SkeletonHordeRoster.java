package com.projecthero.mod.horde.entity.skeleton;

import com.projecthero.mod.event.entity.RaidEntityTypes;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

/**
 * v0.14.16: what the Skeleton Horde block sends, wave by wave. The six horde skeletons answer the six zombies of the
 * Zombie Horde one for one:
 * <table>
 *   <tr><th>Zombie Horde</th><th>Skeleton Horde</th><th>from wave</th></tr>
 *   <tr><td>zombie</td><td>skeleton / stray (vanilla)</td><td>1</td></tr>
 *   <tr><td>baby zombie</td><td>{@link BoneRunner} -- small, fast, pounces</td><td>1</td></tr>
 *   <tr><td>husk</td><td>{@link BlightArcher} -- Wither + Hunger arrows</td><td>2</td></tr>
 *   <tr><td>armoured zombie</td><td>{@link BoneKnight} -- full iron, projectile-proof shield</td><td>3</td></tr>
 *   <tr><td>acid spitter</td><td>{@link BoneBomber} -- exploding arrows, bursts on death</td><td>3</td></tr>
 *   <tr><td>Juggernaut</td><td>{@link BoneBrute} -- 1.6x, Ground Slam</td><td>5</td></tr>
 * </table>
 * plus the {@link Necromancer} (wave 4+), who raises Bone Runners and heals the horde. The older mix (sword skeletons,
 * bogged, wither skeletons) stays in. Every special gets more common with each wave, and each horde skeleton gets 6%
 * more health per wave ({@link HordeSkeleton#setHordeWave}).
 */
public final class SkeletonHordeRoster {
	private SkeletonHordeRoster() {
	}

	/** One unplaced Skeleton Horde mob for wave {@code w} (1..8). */
	public static Mob create(ServerLevel level, int w) {
		Mob mob = pick(level.random, w).create(level);
		if (mob instanceof HordeSkeleton s) {
			s.setHordeWave(w);
		}
		return mob;
	}

	/** The type rolled for wave {@code w} -- split out so the odds can be tested without spawning anything. */
	public static EntityType<? extends Mob> pick(RandomSource r, int w) {
		int roll = r.nextInt(100);
		int t = 0;
		if (w >= 5 && roll < (t += 2 + (w - 5))) {
			return SkeletonHordeEntityTypes.BONE_BRUTE;
		}
		if (w >= 4 && roll < (t += 3 + (w - 4) / 2)) {
			return SkeletonHordeEntityTypes.NECROMANCER;
		}
		if (w >= 4 && roll < (t += 8)) {
			return EntityType.WITHER_SKELETON;
		}
		if (w >= 3 && roll < (t += 5 + (w - 3))) {
			return SkeletonHordeEntityTypes.BONE_BOMBER;
		}
		if (w >= 3 && roll < (t += 6 + (w - 3))) {
			return SkeletonHordeEntityTypes.BONE_KNIGHT;
		}
		if (w >= 2 && roll < (t += 7 + (w - 2))) {
			return SkeletonHordeEntityTypes.BLIGHT_ARCHER;
		}
		if (w >= 2 && roll < (t += 8)) {
			return RaidEntityTypes.SWORD_SKELETON;
		}
		if (roll < (t += 10 + w)) {
			return SkeletonHordeEntityTypes.BONE_RUNNER;
		}
		if (w >= 2 && roll < (t += 8)) {
			return EntityType.BOGGED;
		}
		if (roll < t + Math.max(10, 24 - 2 * w)) {
			return EntityType.STRAY;
		}
		return EntityType.SKELETON;
	}
}
