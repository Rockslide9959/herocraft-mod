package com.projecthero.mod.miraculous.pegasus;

import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.MiraculousKits;
import com.projecthero.mod.miraculous.pegasus.entity.PegasusEntityTypes;
import com.projecthero.mod.miraculous.pegasus.network.PegasusVoyageChoicePayload;
import com.projecthero.mod.miraculous.pegasus.network.PegasusVoyageMenuPayload;
import com.projecthero.mod.miraculous.pegasus.network.PegasusWaypointsPayload;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * v0.16.6: Pegasus server entry point (called from {@code MiraculousMod.initialize}): the kit, the portal / horseshoe
 * entities, the Voyage menu and waypoint payloads, and the waypoint sync on join (the markers only their owner sees).
 */
public final class PegasusMod {
	private PegasusMod() {
	}

	public static void initialize() {
		MiraculousKits.register(Kwami.KAALKI, new PegasusKit());
		PegasusEntityTypes.initialize();
		PayloadTypeRegistry.playS2C().register(PegasusVoyageMenuPayload.TYPE, PegasusVoyageMenuPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(PegasusWaypointsPayload.TYPE, PegasusWaypointsPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(PegasusVoyageChoicePayload.TYPE, PegasusVoyageChoicePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(PegasusVoyageChoicePayload.TYPE, (payload, context) -> {
			if (Miraculous.transformedAs(context.player(), Kwami.KAALKI) && !com.projecthero.mod.hero.PowerSuppression.refuse(context.player())) { // v0.16.7
				PegasusMoves.chooseVoyage(context.player(), payload.index());
			}
		});
		ServerPlayerEvents.JOIN.register(player -> {
			if (Miraculous.holds(player, Kwami.KAALKI)) {
				PegasusWaypoints.sync(player);
			}
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (Miraculous.holds(newPlayer, Kwami.KAALKI)) {
				PegasusWaypoints.sync(newPlayer);
			}
		});
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
			if (Miraculous.holds(player, Kwami.KAALKI)) {
				PegasusWaypoints.sync(player);
			}
		});
	}
}
