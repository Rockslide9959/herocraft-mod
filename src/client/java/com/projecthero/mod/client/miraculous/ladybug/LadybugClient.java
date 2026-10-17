package com.projecthero.mod.client.miraculous.ladybug;

import com.projecthero.mod.client.miraculous.KwamiClientKit;
import com.projecthero.mod.client.miraculous.MiraculousClient;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.ladybug.entity.LadybugEntityTypes;
import com.projecthero.mod.miraculous.ladybug.item.LadybugItems;
import com.projecthero.mod.miraculous.ladybug.network.LadybugGlowPayload;
import com.projecthero.mod.miraculous.ladybug.network.LadybugPingPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.object.builder.v1.client.model.FabricModelPredicateProviderRegistry;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.6: Ladybug's client side -- the suit layer (with the Yo-yo at her hip), the poses, everything in the world
 * (the Yo-yo and its string, the shield, the compact, the canopy, the bursts, the ladybug swarm), the swing / zip
 * movement, Ladybug Vision, the outlines and the squad ping marker, the thrown TNT's renderer and the Lucky Bow's
 * pull models. The first-person sleeve is {@code LadybugFirstPersonArmMixin}; the outlines are
 * {@code LadybugGlowMixin} / {@code LadybugGlowColorMixin}.
 */
public final class LadybugClient implements KwamiClientKit {
	private LadybugClient() {
	}

	public static void initialize() {
		MiraculousClient.register(Kwami.TIKKI, new LadybugClient());
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof PlayerRenderer playerRenderer) {
				helper.register(new LadybugLayer(playerRenderer));
			}
		});
		WorldRenderEvents.AFTER_ENTITIES.register(LadybugWorld::render);
		WorldRenderEvents.END.register(LadybugVision::render);
		HudRenderCallback.EVENT.register(LadybugGlowClient::hud);
		ClientTickEvents.START_CLIENT_TICK.register(LadybugSwingClient::tick);
		ClientTickEvents.END_CLIENT_TICK.register(LadybugSwarm::tick);
		ClientPlayNetworking.registerGlobalReceiver(LadybugGlowPayload.TYPE, (payload, context) -> LadybugGlowClient.receive(payload));
		ClientPlayNetworking.registerGlobalReceiver(LadybugPingPayload.TYPE, (payload, context) -> LadybugGlowClient.receive(payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			LadybugGlowClient.reset();
			LadybugSwarm.reset();
			LadybugSwingClient.reset();
			LadybugWorld.reset();
			LadybugVision.reset();
		});
		EntityRendererRegistry.register(LadybugEntityTypes.LUCKY_TNT, ThrownItemRenderer::new);
		FabricModelPredicateProviderRegistry.register(LadybugItems.LUCKY_BOW, ResourceLocation.withDefaultNamespace("pull"),
				(stack, level, entity, seed) -> entity == null || entity.getUseItem() != stack ? 0f
						: (stack.getUseDuration(entity) - entity.getUseItemRemainingTicks()) / 20f);
		FabricModelPredicateProviderRegistry.register(LadybugItems.LUCKY_BOW, ResourceLocation.withDefaultNamespace("pulling"),
				(stack, level, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1f : 0f);
	}

	@Override
	public void applyPose(Player player, HumanoidModel<?> model) {
		LadybugPose.apply(player, model);
	}

	@Override
	public boolean hidesSkinOverlay(Player player) {
		return LadybugRender.hidesSkinOverlay(player);
	}
}
