package com.herocraft.mod.event.raid;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Wires the Supervillain Village Raid into the game's event bus:
 * <ul>
 *   <li>{@code AFTER_DAMAGE} -- the raid trigger. A Pillager Spy that actually damages a player who
 *       is standing in a village marks that village ({@link SupervillainRaidStarter}). A miss, a
 *       normal Pillager, or a hit outside a village all do nothing.</li>
 * </ul>
 * The death hook that hands out rewards and detects the boss dying is shared with the Zombie Raid in
 * {@code GraveboundEvents} (it already fires {@code AFTER_DEATH} for every entity).
 */
public final class SupervillainRaidEvents {
	private SupervillainRaidEvents() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(SupervillainRaidEvents::onAfterDamage);
	}

	private static void onAfterDamage(LivingEntity entity, net.minecraft.world.damagesource.DamageSource source,
			float baseDamage, float damageTaken, boolean blocked) {
		if (damageTaken <= 0.0f || blocked) {
			return;
		}
		if (!(entity instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel)) {
			return;
		}
		SupervillainRaidStarter.onPlayerDamaged(player, source.getDirectEntity(), source.getEntity());
	}
}
