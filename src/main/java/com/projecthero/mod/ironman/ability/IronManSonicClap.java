package com.projecthero.mod.ironman.ability;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.ironman.IronManAbilityFx;
import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.TonyStark;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.27: the sonic clap (Mark 2 G; Mark III calls it too). The wearer claps both repulsors together in front of them
 * ({@link IronManAbilityFx#SONIC_CLAP} pose) and a sonic shockwave rolls out through a {@link #CONE_DEGREES}-degree
 * cone {@link #RANGE} blocks deep: everything in it takes the clap damage and is hurled back.
 */
public final class IronManSonicClap {
	/** Ability id the cooldown is stored under (per suit). */
	public static final String ABILITY_ID = "sonic_clap";
	public static final double RANGE = 12.0;
	/** Full cone angle (so +-30 degrees either side of the look). */
	public static final double CONE_DEGREES = 60.0;
	public static final double KNOCKBACK = 2.2;

	private IronManSonicClap() {
	}

	/**
	 * Fire the clap. Checks the worn chestplate, the {@link #ABILITY_ID} cooldown and the energy itself; on success
	 * spends {@code energyCost} (scaled by the suit's cost multiplier), starts {@code cooldownTicks} and returns true.
	 */
	public static boolean fire(ServerPlayer player, float damage, float energyCost, int cooldownTicks) {
		String suitId = IronManArmor.wornSuitId(player);
		if (suitId == null || !IronManArmor.canOperate(player)) {
			return false;
		}
		if (!IronManArmor.hasChestplate(player, suitId)) {
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.need_chest"), true);
			return false;
		}
		if (!IronManAbilities.cooldownReady(player, suitId, ABILITY_ID)) {
			return false;
		}
		var suit = com.projecthero.mod.ironman.suit.IronManSuits.byId(suitId);
		float cost = energyCost * (suit == null ? 1f : suit.energyCostMultiplier());
		if (!IronManEnergy.spend(player, suitId, cost)) {
			IronManAbilities.noEnergy(player, cost);
			return false;
		}
		TonyStark.triggerCooldown(player, suitId, ABILITY_ID, cooldownTicks);

		ServerLevel level = (ServerLevel) player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 look = com.projecthero.mod.ironman.IronManTargeting.aimLook(player, RANGE + 4.0); // v0.14.30: the cone points at the lock
		IronManAbilityFx.play(player, IronManAbilityFx.SONIC_CLAP, 16);
		AbilityHelpers.sound(player, SoundEvents.WARDEN_SONIC_BOOM, 1.2f, 1.3f);
		AbilityHelpers.sound(player, SoundEvents.ANVIL_LAND, 0.6f, 1.8f);

		// the shockwave: expanding rings of particles down the cone
		for (int i = 1; i <= 6; i++) {
			double d = i * RANGE / 6.0;
			Vec3 c = eye.add(look.scale(d));
			level.sendParticles(ParticleTypes.SONIC_BOOM, c.x, c.y, c.z, 1, 0, 0, 0, 0);
			double r = Math.tan(Math.toRadians(CONE_DEGREES / 2.0)) * d;
			level.sendParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, 6, r * 0.4, r * 0.4, r * 0.4, 0.02);
		}

		for (LivingEntity e : inCone(player)) {
			AbilityHelpers.hurtBurst(player, e, damage);
			AbilityHelpers.knockbackFrom(e, player.position(), KNOCKBACK);
			e.setDeltaMovement(e.getDeltaMovement().add(0, 0.3, 0));
		}
		return true;
	}

	/** Everything the clap can hit: within {@link #RANGE} of the eyes and inside the cone. */
	public static java.util.List<LivingEntity> inCone(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = com.projecthero.mod.ironman.IronManTargeting.aimLook(player, RANGE + 4.0); // v0.14.30: the cone points at the lock
		double cos = Math.cos(Math.toRadians(CONE_DEGREES / 2.0));
		java.util.List<LivingEntity> out = new java.util.ArrayList<>();
		for (LivingEntity e : AbilityHelpers.enemiesAround(player, eye, RANGE + 1.0)) {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
			double dist = to.length();
			if (dist > RANGE + e.getBbWidth() * 0.5) {
				continue;
			}
			if (dist < 1.0 || to.scale(1.0 / dist).dot(look) >= cos) {
				out.add(e);
			}
		}
		return out;
	}
}
