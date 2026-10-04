package com.projecthero.mod.event.boss;

import java.util.List;

import com.projecthero.mod.combat.HeroTargets;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * v0.14.21: who a Gravebound power boss (an {@link com.projecthero.mod.event.entity.EmpoweredZombie}) may hit with its
 * power -- the mob-side mirror of {@link HeroTargets}.
 *
 * <p>{@link HeroTargets} is written from a <em>player's</em> point of view (rule 1 "may this player's aimed attack hit
 * it", rule 2 "is it a threat to this player"). A boss is the other side of that fight, so it uses the same building
 * blocks the other way round:
 * <ul>
 * <li>The same "never" rules on the victim: the dead, armour stands, spectators and invulnerable (creative) players --
 * checked with {@code getAbilities().invulnerable}, exactly as {@link HeroTargets#canHarm} does -- and never the thing
 * the boss rides or is ridden by.</li>
 * <li><b>Players</b> are always fair game (PvP settings are about players hurting players, not a raid boss).</li>
 * <li><b>Players' allies</b>: anything {@link HeroTargets#ownerOf} says a player owns -- tamed pets, bonded Symbiote
 * pets, Mirror Images, Shadow Servants, a Titan Shifter's body -- plus village golems, which fight on the players'
 * side, and any non-hostile mob that is currently attacking the boss.</li>
 * <li><b>Never</b> another raid mob or anything else hostile ({@link Enemy}) -- the boss's area attacks used to be
 * player-only for exactly this reason; now they reach the players' pets too without ever chewing through its own
 * horde -- and never neutral bystanders (villagers, livestock), so a raid on a village does not level the
 * village.</li>
 * </ul>
 */
public final class BossTargets {
	private BossTargets() {
	}

	/** May {@code boss}'s power hit {@code target}? */
	public static boolean isVictim(Entity boss, Entity target) {
		if (!(target instanceof LivingEntity living) || target == boss || !living.isAlive() || living.isDeadOrDying()
				|| target instanceof ArmorStand || target.isSpectator() || target.isRemoved()) {
			return false;
		}
		if (boss != null && (target.hasIndirectPassenger(boss) || boss.hasIndirectPassenger(target))) {
			return false;
		}
		if (target instanceof Player p) {
			return !p.getAbilities().invulnerable;
		}
		if (target instanceof Enemy) {
			// the raid's own horde, other bosses, and wild monsters -- a player's Shadow Servant is not an Enemy
			return false;
		}
		if (HeroTargets.ownerOf(target) != null) {
			return true; // a player's pet / summon / construct
		}
		if (target instanceof AbstractGolem) {
			return true; // iron / snow golems defend the players
		}
		if (boss instanceof LivingEntity lb && lb.getLastHurtByMob() == target) {
			return true; // it is fighting the boss right now
		}
		return target instanceof Mob mob && mob.getTarget() == boss;
	}

	/** Every victim of {@code boss} inside {@code box}. */
	public static List<LivingEntity> victims(Level level, Entity boss, AABB box) {
		return level.getEntitiesOfClass(LivingEntity.class, box, e -> isVictim(boss, e));
	}

	/** True for a player the boss may hit (used for "who counts as a player in this fight" checks). */
	public static boolean isPlayerVictim(Entity boss, Entity target) {
		return target instanceof Player && isVictim(boss, target);
	}
}
