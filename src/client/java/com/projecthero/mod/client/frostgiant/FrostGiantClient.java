package com.projecthero.mod.client.frostgiant;

import com.projecthero.mod.frostgiant.FrostBlizzardPayload;
import com.projecthero.mod.frostgiant.FrostGiantEntityTypes;
import com.projecthero.mod.frostgiant.item.FrostGear;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.renderer.entity.ThrownItemRenderer;

/**
 * v0.16.1: the Frost Giant Assault's client side -- the giants' GeckoLib renderers, the braziers, prisons, the Casket and
 * Ymir, the thrown ice javelins, the blizzard / rift / HUD ({@link FrostBlizzardClient}) and the Rimed / Rime-touched
 * tooltip lines. The Iron Man and Mind Stone highlights need nothing here: the giants are Monsters, so the existing
 * client-side {@code EntityGlowMixin} path already outlines them (never a server glowing flag).
 */
public final class FrostGiantClient {
	private FrostGiantClient() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(FrostGiantEntityTypes.HOUND, ctx -> new FrostGiantRenderer<>(ctx, 0.6f));
		EntityRendererRegistry.register(FrostGiantEntityTypes.RAIDER, ctx -> new FrostGiantRenderer<>(ctx, 1.0f));
		EntityRendererRegistry.register(FrostGiantEntityTypes.SHAMAN, ctx -> new FrostGiantRenderer<>(ctx, 1.0f));
		EntityRendererRegistry.register(FrostGiantEntityTypes.BRUTE, ctx -> new FrostGiantRenderer<>(ctx, 1.8f));
		EntityRendererRegistry.register(FrostGiantEntityTypes.WARLORD, ctx -> new FrostGiantRenderer<>(ctx, 2.4f));
		EntityRendererRegistry.register(FrostGiantEntityTypes.LAUFEY, ctx -> new FrostGiantRenderer<>(ctx, 2.8f));
		EntityRendererRegistry.register(FrostGiantEntityTypes.RUNE_BRAZIER, FrostPropRenderers.Brazier::new);
		EntityRendererRegistry.register(FrostGiantEntityTypes.ICE_PRISON, FrostPropRenderers.Prison::new);
		EntityRendererRegistry.register(FrostGiantEntityTypes.ICE_CASKET, FrostPropRenderers.Casket::new);
		EntityRendererRegistry.register(FrostGiantEntityTypes.YMIR_ARM, FrostPropRenderers.Ymir::new);
		EntityRendererRegistry.register(FrostGiantEntityTypes.ICE_JAVELIN, ctx -> new ThrownItemRenderer<>(ctx, 2.2f, true));

		ClientPlayNetworking.registerGlobalReceiver(FrostBlizzardPayload.TYPE, (payload, ctx) -> ctx.client().execute(() -> FrostBlizzardClient.accept(payload)));
		ClientTickEvents.END_CLIENT_TICK.register(FrostBlizzardClient::tick);
		WorldRenderEvents.AFTER_ENTITIES.register(FrostBlizzardClient::render);
		HudRenderCallback.EVENT.register(FrostBlizzardClient::hud);
		ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> FrostBlizzardClient.reset());
		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> FrostGear.appendTooltip(stack, lines));
	}
}
