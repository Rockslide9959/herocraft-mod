package com.projecthero.mod.client.quirk.engine;

import com.projecthero.mod.client.quirk.QuirkClient;
import com.projecthero.mod.client.quirk.QuirkClientKit;
import com.projecthero.mod.quirk.Quirk;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7: the Engine Quirk's client side -- the poses ({@link EnginePose}) and the exhaust flames from the calves
 * ({@link EngineFx}). The costume is drawn by the Quirk core's costume layer from
 * {@code textures/entity/quirk/engine_<wide|slim>.png}; the FOV cap at high gear is {@code QuirkEngineFovMixin}.
 */
public final class EngineClient implements QuirkClientKit {
	private EngineClient() {
	}

	public static void initialize() {
		QuirkClient.register(Quirk.ENGINE, new EngineClient());
		ClientTickEvents.END_CLIENT_TICK.register(EngineFx::tick);
	}

	@Override
	public void applyPose(Player player, HumanoidModel<?> model) {
		EnginePose.apply(player, model);
	}
}
