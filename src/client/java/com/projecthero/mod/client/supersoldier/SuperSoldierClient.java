package com.projecthero.mod.client.supersoldier;

import com.projecthero.mod.supersoldier.entity.SuperSoldierEntities;
import com.projecthero.mod.supersoldier.item.SuperSoldierItems;

import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;

import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/**
 * v0.14.8: one call from {@code ProjectHeroModClient} wires the Super Soldier client side -- the HUD and the thrown
 * shield. v0.14.9: the Adamantium Shield's round renderer and its {@code blocking} model predicate (the same one vanilla
 * gives {@code minecraft:shield}, so raising it swaps to the blocking display transforms). v0.17.5: the stepped-dish shields,
 * strapped to the forearm and animated ({@link CapShieldPose}; no blocking model swap), slung on the back when carried
 * ({@link CapShieldBackLayer}).
 */
public final class SuperSoldierClient {
	private SuperSoldierClient() {
	}

	public static void initialize() {
		HudRenderCallback.EVENT.register(SuperSoldierHud::render);
		EntityRendererRegistry.register(SuperSoldierEntities.SOLDIER_SHIELD, SoldierShieldRenderer::new);
		BuiltinItemRendererRegistry.INSTANCE.register(SuperSoldierItems.ADAMANTIUM_SHIELD, new AdamantiumShieldRenderer());
		// v0.17.5: the Soldier's Shield is the same stepped dish (its classic painted art); no "blocking" model swap any more --
		// the raise is the arm moving (CapShieldPose), the shield stays strapped where it is
		BuiltinItemRendererRegistry.INSTANCE.register(SuperSoldierItems.SOLDIER_SHIELD, new AdamantiumShieldRenderer(AdamantiumShieldRenderer.SOLDIER_TEXTURE));
		CapShieldPose.initialize();
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof PlayerRenderer playerRenderer) {
				helper.register(new CapShieldBackLayer(playerRenderer));
			}
		});
	}
}
