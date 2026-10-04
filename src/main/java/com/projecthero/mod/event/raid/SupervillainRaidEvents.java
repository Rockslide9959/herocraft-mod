package com.projecthero.mod.event.raid;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Wires the Supervillain Village Raid into the game's event bus:
 * <ul>
 *   <li>{@code AFTER_DAMAGE} -- a Pillager Spy (or its bolt) that hits a player gives them the Supervillain's Mark
 *       ({@link SupervillainRaidStarter#onPlayerDamaged}). v0.14.21: anywhere, not only in a village, and a hit a
 *       power soaks down to 0 damage still counts; a shield block or a miss does not. Killing a Spy marks the killer
 *       too ({@code PillagerSpy#die}).</li>
 *   <li>{@code END_SERVER_TICK} -- v0.14.21: a marked player in a village turns the mark into the omen, and an omen
 *       that runs out starts the raid ({@link SupervillainMark#tick}).</li>
 * </ul>
 * The death hook that hands out rewards and detects the boss dying is shared with the Zombie Raid in
 * {@code GraveboundEvents} (it already fires {@code AFTER_DEATH} for every entity).
 */
public final class SupervillainRaidEvents {
	private SupervillainRaidEvents() {
	}

	public static void initialize() {
		SupervillainMark.initialize();
		ServerLivingEntityEvents.AFTER_DAMAGE.register(SupervillainRaidEvents::onAfterDamage);
		ServerTickEvents.END_SERVER_TICK.register(SupervillainMark::tick);
	}

	private static void onAfterDamage(LivingEntity entity, net.minecraft.world.damagesource.DamageSource source,
			float baseDamage, float damageTaken, boolean blocked) {
		if (blocked) {
			return;
		}
		if (!(entity instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel)) {
			return;
		}
		SupervillainRaidStarter.onPlayerDamaged(player, source.getDirectEntity(), source.getEntity());
	}
}
