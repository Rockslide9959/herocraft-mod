package com.projecthero.mod.power;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.projecthero.mod.squad.Squads;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.4: the one "may this Thor power touch that?" rule every Thor ability goes through -- Lightning Strike and its
 * auto-chain, Chain Lightning, Lightning Beam, God of Thunder's Wrath, Thunderclap, Storm Call, the thrown hammer and
 * Hammer Volley. Damage, knockback, slowness and fire all check it, not just the hit itself: the squad friendly-fire
 * veto ({@link Squads#isFriendlyFire}) only ever cancels damage, and vanilla lightning never carried an attacker for it
 * to see in the first place.
 *
 * <p>Spared: the caster, their squadmates, pets owned by either, other players while PvP is off, and armour stands.
 * Everything else -- hostile mobs, animals, players outside the squad on a PvP server -- is fair game.
 */
public final class ThorTargets {
	private ThorTargets() {
	}

	/** Can {@code caster}'s power hurt, shove, slow or ignite {@code target}? */
	public static boolean canAffect(Player caster, Entity target) {
		if (target == null || target == caster || !target.isAlive() || target instanceof ArmorStand) {
			return false;
		}
		if (caster == null) {
			return true;
		}
		if (Squads.areAllies(caster, target)) {
			return false;
		}
		if (target instanceof Player) {
			MinecraftServer server = caster.getServer();
			return server == null || server.isPvpAllowed();
		}
		if (target instanceof OwnableEntity pet) {
			UUID owner = pet.getOwnerUUID();
			if (owner != null) {
				if (owner.equals(caster.getUUID())) {
					return false;
				}
				Player ownerPlayer = caster.level().getPlayerByUUID(owner);
				if (ownerPlayer != null && Squads.areAllies(caster, ownerPlayer)) {
					return false;
				}
			}
		}
		return true;
	}

	/** Every living thing within {@code radius} of {@code center} (box-distance) that {@code caster} may affect. */
	public static List<LivingEntity> inRadius(ServerLevel level, Player caster, Vec3 center, double radius) {
		List<LivingEntity> out = new ArrayList<>();
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
				e -> canAffect(caster, e))) {
			if (com.projecthero.mod.hero.power.AbilityHelpers.distanceSqToBox(e, center) <= radius * radius + 1.0E-6) {
				out.add(e);
			}
		}
		return out;
	}

	/**
	 * Lightning damage credited to {@code caster}: still the lightning damage type (so Thor's own lightning immunity and
	 * every other lightning rule are unchanged), but with the caster as the attacker, which is what lets the squad
	 * friendly-fire veto, PvP rules, kill credit and mob retaliation see who threw it.
	 */
	public static DamageSource lightning(ServerLevel level, Player caster) {
		if (caster == null) {
			return level.damageSources().lightningBolt();
		}
		return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
				.getHolderOrThrow(DamageTypes.LIGHTNING_BOLT), caster);
	}
}
