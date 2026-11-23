package com.projecthero.mod.alarm;

import com.projecthero.mod.alarm.block.AlarmBlocks;
import com.projecthero.mod.alarm.network.AlarmStatusPayload;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

/**
 * v0.17.7: the shared <b>alarm & stealth framework</b> -- alarm zones, guard suspicion and roles, Alarm Panels / Lamps,
 * Blast Doors, Security Cameras, Laser Tripwires, Keycards, reinforcement waves and the Ghost bonus. See
 * {@code docs/ALARM_REFERENCE.md} for how a place plugs in.
 *
 * <p>No static per-world state: zones live in {@link AlarmZones} (world storage, with level-owned transient fields);
 * {@link AlarmProfiles} and the lamp registry only hold start-up descriptions.
 */
public final class AlarmMod {
	private AlarmMod() {
	}

	public static void initialize() {
		AlarmBlocks.initialize();
		AlarmKeycards.initialize();
		PayloadTypeRegistry.playS2C().register(AlarmStatusPayload.TYPE, AlarmStatusPayload.CODEC);
		ServerTickEvents.END_WORLD_TICK.register(AlarmDirector::tickLevel);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Mob m && entity.level() instanceof ServerLevel level && AlarmGuards.hasRole(m, GuardRole.CAPTAIN)) {
				AlarmKeycards.captainDied(level, m);
			}
		});
	}
}
