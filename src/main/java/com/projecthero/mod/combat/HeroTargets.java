package com.projecthero.mod.combat;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import com.projecthero.mod.hero.HeroConfig;
import com.projecthero.mod.hero.revamp.d.MirrorImageEntity;
import com.projecthero.mod.hero.revamp.d.ShadowServantEntity;
import com.projecthero.mod.squad.Squads;
import com.projecthero.mod.titanshifter.entity.TitanFormEntity;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.20: the ONE "who may this power hit?" rule set every player power routes its target selection through
 * (see {@code docs/TARGETING.md} for which ability uses which rule).
 *
 * <ol>
 * <li>{@link #canHarm} -- every living thing the player could reasonably mean to hit: hostile mobs, animals,
 * villagers, golems, neutral mobs and (PvP on) other players. Used by deliberate, aimed attacks -- melee abilities,
 * beams, rays, thrown things, grabs, and the player's own slams/blasts centred on their attack.</li>
 * <li>{@link #isHostile} -- {@code canHarm} <em>and</em> a threat: an {@link Enemy}, any mob currently targeting the
 * player or a squadmate, a neutral mob angry at them, something that just attacked them or that they are fighting, or
 * (PvP on) a player who
 * recently hurt them or a squadmate. Used by anything that picks its targets automatically or sweeps a huge area
 * without aiming -- turrets, sentries, summons, homing/lock-on, chain jumps, auras, storms, scans -- so it never
 * wrecks a farm or a village.</li>
 * </ol>
 *
 * <p>Never harmed by either: the owner, the dead, armour stands, spectators, creative players, the owner's own tamed
 * pets and summons (or a squadmate's), and other players while PvP is off (server PvP or the mod's
 * {@link HeroConfig#abilityPvpDamage}). Squadmates: {@code canHarm} follows the squad damage veto
 * ({@link Squads#shields}) -- with the leader's friendly fire on, a deliberate hit lands -- while {@code isHostile}
 * never auto-targets a squadmate ({@link Squads#areAllies}). A rampaging Hulk is nobody's ally either way.
 *
 * <p>The owner may be null (an orphaned projectile or construct): then only the "never" rules about the target itself
 * apply, and nothing counts as hostile but an {@link Enemy}.
 */
public final class HeroTargets {
	/** How long (ticks) "just hit me / just hit my squadmate" keeps a player or mob counted as hostile. */
	public static final int RECENT_ATTACK_TICKS = 200;

	private HeroTargets() {
	}

	// ---------------------------------------------------------------- rule 1

	/** Rule 1: may {@code owner}'s deliberate, aimed attack harm {@code target}? */
	public static boolean canHarm(Entity owner, Entity target) {
		if (!(target instanceof LivingEntity living) || target == owner || !living.isAlive() || living.isDeadOrDying()
				|| target instanceof ArmorStand || target.isSpectator() || target.isRemoved()) {
			return false;
		}
		// creative (and any other invulnerable) players -- the same flag vanilla's Player#hurt checks
		if (target instanceof Player p && p.getAbilities().invulnerable) {
			return false;
		}
		if (owner == null) {
			return true;
		}
		// never the thing you are riding, nor anything riding you
		if (target.hasIndirectPassenger(owner) || owner.hasIndirectPassenger(target)) {
			return false;
		}
		Player ownerPlayer = owner instanceof Player op ? op : null;
		if (target instanceof Player p) {
			if (!pvpOn(owner)) {
				return false;
			}
			return ownerPlayer == null || !Squads.shields(ownerPlayer, p);
		}
		UUID petOwner = ownerOf(target);
		if (petOwner != null) {
			if (petOwner.equals(owner.getUUID())) {
				return false; // your own pet / summon / construct
			}
			// a squadmate's pet -- v0.15.11: checked by id, so it stays safe while its owner is offline or far away
			if (ownerPlayer != null && Squads.protectsPet(ownerPlayer, petOwner)) {
				return false;
			}
		}
		return true;
	}

	// ---------------------------------------------------------------- rule 2

	/** Rule 2: {@link #canHarm} and a threat -- for automatic, unaimed or huge-area targeting. */
	public static boolean isHostile(Entity owner, Entity target) {
		if (!canHarm(owner, target)) {
			return false;
		}
		if (owner instanceof Player op && target instanceof Player tp && Squads.areAllies(op, tp)) {
			return false; // auto-targeting never picks a squadmate, friendly fire or not
		}
		if (target instanceof Enemy) {
			return true;
		}
		if (owner == null) {
			return false;
		}
		LivingEntity living = (LivingEntity) target;
		if (target instanceof Mob mob && isFriend(owner, mob.getTarget())) {
			return true; // hunting the owner or a squadmate
		}
		if (target instanceof NeutralMob neutral && neutral.isAngry()) {
			UUID grudge = neutral.getPersistentAngerTarget();
			if (grudge != null) {
				if (grudge.equals(owner.getUUID())) {
					return true;
				}
				Player grudged = owner.level().getPlayerByUUID(grudge);
				if (grudged != null && isFriend(owner, grudged)) {
					return true;
				}
			}
		}
		if (owner instanceof LivingEntity lo) {
			if (lo.getLastHurtByMob() == target) {
				return true; // it just attacked the owner (vanilla forgets after 100 ticks)
			}
			if (lo.getLastHurtMob() == target && lo.tickCount - lo.getLastHurtMobTimestamp() <= RECENT_ATTACK_TICKS) {
				return true; // the owner's current fight -- like a tamed wolf joining in
			}
		}
		LivingEntity victim = living.getLastHurtMob();
		return victim != null && living.tickCount - living.getLastHurtMobTimestamp() <= RECENT_ATTACK_TICKS
				&& isFriend(owner, victim);
	}

	// ---------------------------------------------------------------- shared helpers

	/** Is server PvP on for abilities (vanilla server setting AND the mod's ability-PvP switch)? */
	public static boolean pvpOn(Entity owner) {
		if (owner == null) {
			return true;
		}
		MinecraftServer server = owner.getServer();
		return server != null && server.isPvpAllowed() && HeroConfig.get().abilityPvpDamage;
	}

	/** The owner, or one of their squadmates. */
	private static boolean isFriend(Entity owner, Entity who) {
		if (who == null) {
			return false;
		}
		if (who == owner) {
			return true;
		}
		return owner instanceof Player op && Squads.areAllies(op, who);
	}

	/**
	 * Who owns {@code e}, if it is somebody's pet, summon or construct: vanilla tamed / ownable animals (incl. bonded
	 * Symbiote pets), Mirror Images, Shadow Servants and a Titan Shifter's body; null for everything else.
	 */
	public static UUID ownerOf(Entity e) {
		if (e instanceof OwnableEntity ownable) {
			return ownable.getOwnerUUID();
		}
		if (e instanceof MirrorImageEntity image) {
			return image.ownerId().orElse(null);
		}
		if (e instanceof ShadowServantEntity servant) {
			return servant.ownerId().orElse(null);
		}
		if (e instanceof TitanFormEntity form) {
			return form.ownerId();
		}
		if (e instanceof TraceableEntity traceable && traceable.getOwner() instanceof Player p) {
			return p.getUUID();
		}
		return null;
	}

	/**
	 * v0.15.11: the owner of {@code e} if it is a tamed animal -- anything {@link OwnableEntity} with an owner: wolves,
	 * cats, parrots, horses, llamas, Symbiote pet hosts and other mods' pets. Null for wild animals and for summons /
	 * constructs (those are {@link #ownerOf}'s business). Used by the squad damage veto.
	 */
	public static UUID petOwnerOf(Entity e) {
		return e instanceof OwnableEntity ownable ? ownable.getOwnerUUID() : null;
	}

	/**
	 * v0.15.11: is {@code target} a pet of one of {@code player}'s squadmates (or the player's own)? For abilities that
	 * keep their own "friend" lists (weapon combos, grapples, heals) so they agree with {@link #canHarm}.
	 */
	public static boolean isFriendlyPet(Player player, Entity target) {
		UUID keeper = petOwnerOf(target);
		return keeper != null && player != null && (keeper.equals(player.getUUID()) || Squads.protectsPet(player, keeper));
	}

	/** Is {@code e} owned by {@code owner} (pet / summon / construct)? */
	public static boolean isOwnedBy(Entity e, Entity owner) {
		UUID id = owner == null ? null : ownerOf(e);
		return id != null && id.equals(owner.getUUID());
	}

	// ---------------------------------------------------------------- queries

	/** Living things in {@code box} that {@code owner} may harm (rule 1). */
	public static List<LivingEntity> harmable(Level level, Entity owner, AABB box) {
		return level.getEntitiesOfClass(LivingEntity.class, box, e -> canHarm(owner, e));
	}

	/** Living things in {@code box} that {@code owner} may harm and that are hostile (rule 2). */
	public static List<LivingEntity> hostiles(Level level, Entity owner, AABB box) {
		return level.getEntitiesOfClass(LivingEntity.class, box, e -> isHostile(owner, e));
	}

	/** Rule-1 predicate for a filter, combined with {@code extra}. */
	public static Predicate<LivingEntity> harmable(Entity owner, Predicate<LivingEntity> extra) {
		return e -> canHarm(owner, e) && extra.test(e);
	}

	/** Rule-2 predicate for a filter, combined with {@code extra}. */
	public static Predicate<LivingEntity> hostile(Entity owner, Predicate<LivingEntity> extra) {
		return e -> isHostile(owner, e) && extra.test(e);
	}

	/** Centre-of-box helper used by radius queries. */
	public static AABB around(Vec3 center, double radius) {
		return new AABB(center, center).inflate(radius);
	}
}
