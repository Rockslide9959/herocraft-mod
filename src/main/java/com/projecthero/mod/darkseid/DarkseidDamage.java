package com.projecthero.mod.darkseid;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.darkseid.entity.DarkseidEntity;
import com.projecthero.mod.darkseid.entity.ParademonEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Damage helpers shared by every Darkseid Raid attack: the {@code projecthero:omega_beam} damage type (its own
 * death message -- "was erased by Darkseid's Omega Beams"), who a raid attack may hit, and a knockback that
 * behaves the same way for walking and flying players.
 */
public final class DarkseidDamage {
	public static final ResourceKey<DamageType> OMEGA = ResourceKey.create(Registries.DAMAGE_TYPE,
			ProjectHeroMod.id("omega_beam"));

	private DarkseidDamage() {
	}

	public static DamageSource omega(Level level, Entity direct, Entity attacker) {
		return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(OMEGA),
				direct, attacker);
	}

	/** A raid attack may land on any living, non-spectator, non-creative player -- and nothing from Apokolips. */
	public static boolean isValidVictim(Entity e) {
		if (!(e instanceof LivingEntity living) || !living.isAlive() || e.isSpectator()) {
			return false;
		}
		if (e instanceof DarkseidEntity || e instanceof ParademonEntity) {
			return false;
		}
		return !(e instanceof Player p) || !p.isCreative();
	}

	/**
	 * Push {@code target} away from {@code origin}. Uses vanilla's own {@code knockback} for the horizontal part (so
	 * knockback resistance still counts) and adds the lift directly; {@code hurtMarked} makes a player's client take
	 * the new velocity immediately, flying or not.
	 */
	public static void knockAway(LivingEntity target, Vec3 origin, double strength, double lift) {
		double dx = target.getX() - origin.x;
		double dz = target.getZ() - origin.z;
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 1.0e-3) {
			dx = target.getRandom().nextDouble() - 0.5;
			dz = target.getRandom().nextDouble() - 0.5;
			len = Math.sqrt(dx * dx + dz * dz);
		}
		if (strength > 0.0) {
			target.knockback(strength, -dx / len, -dz / len);
		}
		if (lift > 0.0) {
			target.setDeltaMovement(target.getDeltaMovement().add(0.0, lift, 0.0));
		}
		target.hurtMarked = true;
	}
}
