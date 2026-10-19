package com.projecthero.mod.miraculous.viperion;

import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.MiraculousKits;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.server.level.ServerPlayer;

/**
 * v0.16.7: Viperion server entry point (called from {@code MiraculousMod.initialize}): the kit, Second Chance's
 * automatic rewind on a killing blow ({@code ALLOW_DEATH}) and its server tick (forgetting stale checkpoints).
 */
public final class ViperionMod {
	private ViperionMod() {
	}

	public static void initialize() {
		MiraculousKits.register(Kwami.SASS, new ViperionKit());
		// v0.16.7 (user): a killing blow while a checkpoint is live rewinds instead (one rewind is used)
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> !(entity instanceof ServerPlayer p) || !SecondChance.cheatDeath(p, source));
		ServerTickEvents.END_SERVER_TICK.register(SecondChance::serverTick);
	}
}
