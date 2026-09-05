package com.herocraft.mod.event;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * A win/lose condition an event runs against. The Zombie Raid ships with exactly one
 * ({@link com.herocraft.mod.event.raid.SurviveWavesObjective}); the interface exists so the objective
 * types the design calls out for later events -- protect villagers, defend an objective, destroy
 * anchors, stop a ritual, rescue NPCs, timed survival -- can be added as new implementations without
 * touching {@link EventInstance} again.
 *
 * <p>Deliberately minimal. An objective is ticked on the event's own slow cadence, reports whether it
 * is done or lost, and supplies one line of HUD text. Anything more elaborate belongs in the event.
 */
public interface EventObjective {

	/** Stable id, used for save/load dispatch. */
	String typeId();

	/** Called on the event's tick cadence while the event is {@link EventState#RUNNING}. */
	default void tick(ServerLevel level, EventInstance event) {
	}

	boolean isComplete();

	default boolean isFailed() {
		return false;
	}

	/** One short line for the event HUD, e.g. {@code Wave 7 / 12}. */
	Component describe();

	default void save(CompoundTag tag) {
	}

	default void load(CompoundTag tag) {
	}
}
