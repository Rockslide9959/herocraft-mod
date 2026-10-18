package com.projecthero.mod.quirk;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.server.level.ServerPlayer;

/**
 * v0.16.7: one entry point for the Quirks, called once from {@code ProjectHeroMod.onInitialize}: the core lifecycle and
 * the seven kits. The per-player tick runs from {@code AbilityRouter.serverTick} ({@link Quirks#tick}); static state is
 * dropped by {@code ServerStateReset} through {@link #clearSessionState}.
 */
public final class QuirkMod {
	private QuirkMod() {
	}

	public static void initialize() {
		// the core: Erasure's client sync + status effect, the Quirk Serums
		com.projecthero.mod.hero.PowerSuppression.initialize();
		com.projecthero.mod.quirk.item.QuirkItems.initialize();
		// the kits' damage hook (Shadow Guard, cushioned landings...)
		QuirkDamage.initialize();
		com.projecthero.mod.quirk.hellflame.HellflameMod.initialize();
		com.projecthero.mod.quirk.overhaul.OverhaulMod.initialize();
		com.projecthero.mod.quirk.engine.EngineMod.initialize();
		com.projecthero.mod.quirk.darkshadow.DarkShadowMod.initialize();
		com.projecthero.mod.quirk.erasure.ErasureMod.initialize();
		com.projecthero.mod.quirk.explosion.ExplosionMod.initialize();
		com.projecthero.mod.quirk.zerogravity.ZeroGravityMod.initialize();

		ServerPlayerEvents.JOIN.register(Quirks::onPlayerJoin);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> Quirks.onPlayerRespawn(newPlayer));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p && Quirks.hasPower(p)) {
				Quirks.clearTransient(p);
			}
		});
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> Quirks.clearTransient(player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			Quirks.clearTransient(handler.getPlayer());
			com.projecthero.mod.hero.PowerSuppression.forget(handler.getPlayer());
		});
	}

	public static void clearSessionState() {
		Quirks.clearSessionState();
		com.projecthero.mod.hero.PowerSuppression.clearSessionState();
	}
}
