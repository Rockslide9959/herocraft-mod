package com.projecthero.mod.hero.power.p02;

import java.util.function.BooleanSupplier;

import com.projecthero.mod.hero.power.AbilityHelpers;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * v0.15.11: an animal killed by a laser drops cooked food. Vanilla's animal loot tables already smelt the meat (beef,
 * porkchop, chicken, mutton, rabbit, cod, salmon) when the victim {@code is_on_fire} at death -- but every laser hit
 * set the fire <em>after</em> the damage, so the killing blow always found a cold body and dropped it raw.
 *
 * <p>Every laser damage call runs inside {@link #hurt} / {@link #run}, which marks "a laser is dealing damage right
 * now". An {@link ServerLivingEntityEvents#ALLOW_DEATH} listener (fired inside {@code hurt}, before {@code die} rolls
 * the loot) sets anything about to die in that window alight for a second, so the loot table cooks it. Only the dying
 * entity is touched: no fire block is ever placed, and a hit that does not kill changes nothing. Server thread only;
 * the depth counter always unwinds (try/finally), so there is no state to reset between worlds.
 *
 * <p>Used by Laser Vision (every beam / blast / sweep / ignite) and the Kryptonian's Heat Vision.
 */
public final class LaserCooking {
	private static int depth;
	private static boolean registered;

	private LaserCooking() {
	}

	public static void initialize() {
		if (registered) {
			return;
		}
		registered = true;
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (depth > 0) {
				cook(entity);
			}
			return true;
		});
	}

	/** Is a laser dealing damage right now? */
	public static boolean active() {
		return depth > 0;
	}

	/** Set {@code entity} alight just long enough for its death loot to be smelted. */
	public static void cook(LivingEntity entity) {
		if (!entity.fireImmune() && entity.getRemainingFireTicks() < 20) {
			entity.setRemainingFireTicks(20);
		}
	}

	/** {@link AbilityHelpers#hurt} as a laser: a kill drops cooked food. */
	public static boolean hurt(ServerPlayer p, LivingEntity target, DamageSource source, float amount) {
		return run(() -> AbilityHelpers.hurt(p, target, source, amount));
	}

	/** {@link AbilityHelpers#hurtBurst} as a laser: a kill drops cooked food. */
	public static boolean hurtBurst(ServerPlayer p, LivingEntity target, DamageSource source, float amount) {
		return run(() -> AbilityHelpers.hurtBurst(p, target, source, amount));
	}

	/** Run any laser damage code (e.g. a shared strike helper) so kills inside it drop cooked food. */
	public static boolean run(BooleanSupplier damage) {
		depth++;
		try {
			return damage.getAsBoolean();
		} finally {
			depth--;
		}
	}

	/** {@link #run} for damage code with no result. */
	public static void runVoid(Runnable damage) {
		depth++;
		try {
			damage.run();
		} finally {
			depth--;
		}
	}
}
