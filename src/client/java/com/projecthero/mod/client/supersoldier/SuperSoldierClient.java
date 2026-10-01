package com.projecthero.mod.client.supersoldier;

import com.projecthero.mod.supersoldier.entity.SuperSoldierEntities;
import com.projecthero.mod.supersoldier.item.SuperSoldierItems;

import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.object.builder.v1.client.model.FabricModelPredicateProviderRegistry;

import net.minecraft.resources.ResourceLocation;

/**
 * v0.14.8: one call from {@code ProjectHeroModClient} wires the Super Soldier client side -- the HUD and the thrown
 * shield. v0.14.9: the Adamantium Shield's round renderer and its {@code blocking} model predicate (the same one vanilla
 * gives {@code minecraft:shield}, so raising it swaps to the blocking display transforms).
 */
public final class SuperSoldierClient {
	private SuperSoldierClient() {
	}

	public static void initialize() {
		HudRenderCallback.EVENT.register(SuperSoldierHud::render);
		EntityRendererRegistry.register(SuperSoldierEntities.SOLDIER_SHIELD, SoldierShieldRenderer::new);
		BuiltinItemRendererRegistry.INSTANCE.register(SuperSoldierItems.ADAMANTIUM_SHIELD, new AdamantiumShieldRenderer());
		FabricModelPredicateProviderRegistry.register(SuperSoldierItems.ADAMANTIUM_SHIELD, ResourceLocation.withDefaultNamespace("blocking"),
				(stack, level, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0f : 0.0f);
	}
}
