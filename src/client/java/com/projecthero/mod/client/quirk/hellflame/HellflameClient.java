package com.projecthero.mod.client.quirk.hellflame;

import com.projecthero.mod.client.quirk.QuirkClient;
import com.projecthero.mod.client.quirk.QuirkClientKit;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.Quirks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7: Hellflame's client side -- the poses ({@link HellflamePose}), the flames every viewer sees
 * ({@link HellflameFx}: the Cloak's beard / brow / shoulder flames, the foot jets, the beam, the Prominence charge, the
 * Sun) and the Flame Boost flight of the local player ({@link HellflameFlightClient}). The costume itself is drawn by the
 * core's costume layer from {@code textures/entity/quirk/hellflame_*.png}.
 */
public final class HellflameClient implements QuirkClientKit {
	private HellflameClient() {
	}

	public static void initialize() {
		QuirkClient.register(Quirk.HELLFLAME, new HellflameClient());
		HellflameParticleClient.register();
		ClientTickEvents.START_CLIENT_TICK.register(HellflameFlightClient::tick);
		ClientTickEvents.END_CLIENT_TICK.register(HellflameFx::tick);
	}

	@Override
	public void applyPose(Player player, HumanoidModel<?> model) {
		HellflamePose.apply(player, model);
	}

	@Override
	public boolean hidesSkinOverlay(Player player) {
		return Quirks.costumeOn(player);
	}
}
