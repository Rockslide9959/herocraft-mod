package com.projecthero.mod.ghostrider;

import com.projecthero.mod.ghostrider.entity.GhostRiderEntityTypes;
import com.projecthero.mod.ghostrider.item.GhostRiderItems;
import com.projecthero.mod.ghostrider.item.MephistoContractItem;
import com.projecthero.mod.ghostrider.network.GhostRiderActionPayload;
import com.projecthero.mod.ghostrider.network.GhostRiderMarkPayload;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.level.ServerPlayer;

/**
 * v0.16.5: one entry point for the whole Ghost Rider power, called once from {@code ProjectHeroMod.onInitialize}: the
 * contract, the Hell Cycle, the damage rules, the payloads (H / signing in, the Judgement Mark out) and the lifecycle
 * hooks. The per-player tick runs from {@code AbilityRouter.serverTick}; static state is dropped by
 * {@code ServerStateReset} through {@link #clearSessionState}.
 */
public final class GhostRiderMod {
	private GhostRiderMod() {
	}

	public static void initialize() {
		GhostRiderItems.initialize();
		GhostRiderEntityTypes.initialize();
		GhostRiderDamage.initialize();
		GhostRiderFear.initialize();

		PayloadTypeRegistry.playC2S().register(GhostRiderActionPayload.TYPE, GhostRiderActionPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(GhostRiderMarkPayload.TYPE, GhostRiderMarkPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(GhostRiderActionPayload.TYPE, (payload, context) -> {
			ServerPlayer p = context.player();
			// v0.16.7: Erased -- no power actions (signing the contract is not one)
			if (payload.action() != GhostRiderActionPayload.Action.SIGN_CONTRACT && com.projecthero.mod.hero.PowerSuppression.refuse(p, payload.action())) {
				return;
			}
			switch (payload.action()) {
				case TOGGLE_FORM -> {
					if (GhostRider.hasPower(p)) {
						GhostRider.toggleForm(p);
					}
				}
				case SIGN_CONTRACT -> MephistoContractItem.sign(p);
			}
		});

		ServerPlayerEvents.JOIN.register(GhostRider::onPlayerJoin);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> GhostRider.onPlayerRespawn(newPlayer));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p && GhostRider.hasPower(p)) {
				GhostRider.clearTransient(p);
			}
		});
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> GhostRider.clearTransient(player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> GhostRider.clearTransient(handler.getPlayer()));
	}

	/** Every static scratch collection of the power (called by {@code ServerStateReset}). */
	public static void clearSessionState() {
		GhostRider.clearSessionState();
	}
}
