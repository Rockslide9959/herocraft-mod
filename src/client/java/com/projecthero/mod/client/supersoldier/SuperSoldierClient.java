package com.projecthero.mod.client.supersoldier;

import com.projecthero.mod.supersoldier.entity.SuperSoldierEntities;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

/** v0.14.8: one call from {@code ProjectHeroModClient} wires the Super Soldier client side -- the HUD and the thrown shield. */
public final class SuperSoldierClient {
	private SuperSoldierClient() {
	}

	public static void initialize() {
		HudRenderCallback.EVENT.register(SuperSoldierHud::render);
		EntityRendererRegistry.register(SuperSoldierEntities.SOLDIER_SHIELD, SoldierShieldRenderer::new);
	}
}
