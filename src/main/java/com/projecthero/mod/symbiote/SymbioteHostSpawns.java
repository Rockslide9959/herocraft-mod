package com.projecthero.mod.symbiote;

import com.projecthero.mod.hero.HeroConfig;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * Decides whether a naturally spawning hostile mob becomes a {@link SymbioteHost}.
 *
 * <p>Same discipline as {@code CursedZombieSpawns}: the roll is delivered from a
 * {@code Mob#finalizeSpawn} mixin, the only vanilla call that knows both that a mob is being created
 * <em>and why</em>. Only {@link MobSpawnType#NATURAL} and {@link MobSpawnType#CHUNK_GENERATION} are
 * eligible, so spawners, eggs, reinforcements, conversions and this mod's own event spawns are all
 * excluded automatically.
 *
 * <p>The mob is mutated in place (attachment + base attributes + name), not swapped, so this can run
 * directly in the mixin's {@code RETURN} callback with no deferred queue.
 *
 * <p>Cost on the spawn path: one instance check, a handful of {@code instanceof} exclusions, and one
 * {@code nextDouble}. Nothing touches a chunk or an entity query.
 */
public final class SymbioteHostSpawns {
	private SymbioteHostSpawns() {
	}

	public static void consider(Mob mob, ServerLevelAccessor level, MobSpawnType reason) {
		if (reason != MobSpawnType.NATURAL && reason != MobSpawnType.CHUNK_GENERATION) {
			return;
		}
		if (!eligible(mob)) {
			return;
		}
		double chance = HeroConfig.get().symbioteHostChance;
		if (chance <= 0.0 || level.getRandom().nextDouble() >= chance) {
			return;
		}
		SymbioteHost.mark(mob);
	}

	private static boolean eligible(Mob mob) {
		if (!(mob instanceof Monster)) {
			return false;
		}
		if (mob.isBaby()) {
			return false;
		}
		// This mod's own custom hostiles all extend Monster/Zombie -- keep the Symbiote off raid mobs,
		// bosses, and anything already special.
		if (!mob.getType().getDescriptionId().startsWith("entity.minecraft.")) {
			return false;
		}
		// Bosses, splitters, fliers and mobs whose whole identity is one mechanic: leave them alone.
		return !(mob instanceof Warden || mob instanceof Creeper || mob instanceof Slime
				|| mob instanceof MagmaCube || mob instanceof Ghast || mob instanceof Phantom
				|| mob instanceof Shulker || mob instanceof EnderMan
				|| mob.getType() == EntityType.ENDER_DRAGON || mob.getType() == EntityType.WITHER
				|| mob.getType() == EntityType.ELDER_GUARDIAN);
	}
}
