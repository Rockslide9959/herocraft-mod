package com.projecthero.mod.hero.revamp.v0145;

import com.projecthero.mod.hero.power.p12.SuperRegenerationHandlers;
import com.projecthero.mod.hero.visual.MutationVisuals;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;

/**
 * v0.14.5 Super Regeneration rework: server-side registration (visual flags, HUD meters, event hooks) and the
 * per-tick hook for this power. The mechanics themselves live in {@link SuperRegenerationHandlers}.
 */
public final class SuperRegenerationV0145 {
	private SuperRegenerationV0145() {
	}

	public static void init() {
		// Red pixel veins over the whole body while the passive heal is running (seen by everyone).
		MutationVisuals.registerFlag(SuperRegenerationHandlers.VEINS_FLAG, SuperRegenerationHandlers::regenerating);

		// Revive charges: a lethal hit spends a ready charge -- checked before any Totem of Undying.
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) ->
				!(entity instanceof ServerPlayer p && SuperRegenerationHandlers.tryRevive(p, source)));

		// 1 s of damage immunity after a revive (/kill and the void excepted).
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer p && SuperRegenerationHandlers.immune(p)
					&& !source.is(DamageTypes.GENERIC_KILL) && !source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
				return false;
			}
			return true;
		});

		// Transient maps: dropped on logout and when the server stops (see ServerStateReset for why).
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				SuperRegenerationHandlers.forget(handler.getPlayer().getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SuperRegenerationHandlers.clearAll());
	}

	public static void serverTick(MinecraftServer server) {
		SuperRegenerationHandlers.tickDissolves(server);
	}
}
