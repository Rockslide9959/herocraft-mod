package com.projecthero.mod.miraculous.hawkmoth;

import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.MiraculousKits;
import com.projecthero.mod.miraculous.hawkmoth.entity.HawkMothEntityTypes;
import com.projecthero.mod.miraculous.hawkmoth.network.HawkMothLinkPayload;
import com.projecthero.mod.miraculous.hawkmoth.network.HawkMothMarksPayload;
import com.projecthero.mod.miraculous.hawkmoth.network.HawkMothSensePayload;
import com.projecthero.mod.miraculous.hawkmoth.network.HawkMothSwarmPayload;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.server.level.ServerPlayer;

/**
 * v0.16.6: Hawk Moth's server entry point (called from {@code MiraculousMod.initialize}): the kit, the akuma entity, the
 * four payloads, the {@code /hawkmoth} command (the offer's [Accept] / [Refuse] and the Akuma Link message), the
 * akumas' tick and the damage rules (the akumas' veto, the shield, the dash, the window closing on a hit).
 */
public final class HawkMothMod {
	private static final HawkMothKit KIT = new HawkMothKit();

	private HawkMothMod() {
	}

	public static void initialize() {
		MiraculousKits.register(Kwami.NOOROO, KIT);
		HawkMothEntityTypes.initialize();
		PayloadTypeRegistry.playS2C().register(HawkMothMarksPayload.TYPE, HawkMothMarksPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(HawkMothSensePayload.TYPE, HawkMothSensePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(HawkMothSwarmPayload.TYPE, HawkMothSwarmPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(HawkMothLinkPayload.TYPE, HawkMothLinkPayload.CODEC);
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> HawkMothCommand.register(dispatcher));
		ServerTickEvents.END_SERVER_TICK.register(HawkMothAkumas::serverTick);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer p && Miraculous.transformedAs(p, Kwami.NOOROO) && HawkMothKit.blocks(p, source)) {
				return false;
			}
			return HawkMothAkumas.allowDamage(entity, source, amount);
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			// the window closes the moment his body is hurt
			if (entity instanceof ServerPlayer p && taken > 0f && HawkMothKit.windowOpen(p)) {
				HawkMothKit.closeWindow(p);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p) {
				HawkMothAkumas.forgetPlayer(p);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> HawkMothAkumas.forgetPlayer(handler.getPlayer()));
	}

	public static HawkMothKit kit() {
		return KIT;
	}
}
