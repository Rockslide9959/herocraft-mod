package com.projecthero.mod.client.thoracquire;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.projecthero.mod.hero.guide.HeroPackGuide;
import com.projecthero.mod.thoracquire.CraterCloudPayload;
import com.projecthero.mod.thoracquire.ThorAcquire;
import com.projecthero.mod.thoracquire.ThorAcquireAttachments;
import com.projecthero.mod.thoracquire.Valor;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * v0.17.7 Thor acquisition, client side: the crater storm clouds ({@link CraterCloudRenderer}), the lift cutscene's light and
 * flash ({@link MjolnirLiftClient}), the Valor bar when you look at a free hammer ({@link ValorHud}), the Star Forge renderer,
 * the Destroyer renderer, and the Guidebook's live Valor line.
 */
public final class ThorAcquireClient {
	/** Crater hammer spot -> client game time it was last listed by the server. */
	static final Map<BlockPos, Long> CRATERS = new HashMap<>();
	/** Trial centre -> client game time last listed. */
	static final Map<BlockPos, Long> TRIALS = new HashMap<>();
	private static int lastValor = -1;

	private ThorAcquireClient() {
	}

	public static void initialize() {
		ClientPlayNetworking.registerGlobalReceiver(CraterCloudPayload.TYPE, (payload, ctx) -> ctx.client().execute(() -> {
			Minecraft mc = Minecraft.getInstance();
			long now = mc.level != null ? mc.level.getGameTime() : 0L;
			for (BlockPos p : payload.craters()) {
				CRATERS.put(p, now);
			}
			for (BlockPos p : payload.trials()) {
				TRIALS.put(p, now);
			}
		}));
		ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> {
			CRATERS.clear();
			TRIALS.clear();
			lastValor = -1;
		});
		EntityRendererRegistry.register(ThorAcquire.DESTROYER, DestroyerRenderer::new);
		BlockEntityRenderers.register(ThorAcquire.STAR_FORGE_BE, StarForgeRenderer::new);
		WorldRenderEvents.AFTER_ENTITIES.register(CraterCloudRenderer::render);
		WorldRenderEvents.AFTER_ENTITIES.register(MjolnirLiftClient::render);
		HudRenderCallback.EVENT.register(ValorHud::render);
		HudRenderCallback.EVENT.register(MjolnirLiftClient::hud);
		ClientTickEvents.END_CLIENT_TICK.register(ThorAcquireClient::tick);
		HeroPackGuide.thorValorLive = ThorAcquireClient::guideLines;
	}

	private static void tick(Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			return;
		}
		long now = mc.level.getGameTime();
		// forget craters / trials the server stopped listing (claimed, unloaded, over)
		CRATERS.values().removeIf(t -> now - t > 120 || now < t);
		TRIALS.values().removeIf(t -> now - t > 40 || now < t);
		int v = Valor.get(mc.player);
		if (v != lastValor) {
			lastValor = v;
			HeroPackGuide.refreshThor();
		}
	}

	/** The Guidebook's Thor chapter: the reader's own Valor. */
	private static List<Component> guideLines() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return List.of();
		}
		if (com.projecthero.mod.worthiness.Worthiness.isWorthy(mc.player)) {
			return List.of(Component.translatable("projecthero.guide.thor.valor.worthy").withStyle(ChatFormatting.GOLD));
		}
		int v = mc.player.getAttachedOrElse(ThorAcquireAttachments.VALOR, 0);
		int pct = Valor.percentOf(v);
		StringBuilder bar = new StringBuilder();
		for (int i = 0; i < 20; i++) {
			bar.append(i < pct / 5 ? '|' : '.');
		}
		return List.of(Component.translatable("projecthero.guide.thor.valor.now", v, Valor.MAX, pct).withStyle(ChatFormatting.WHITE),
				Component.literal(" [").withStyle(ChatFormatting.DARK_GRAY)
						.append(Component.literal(bar.substring(0, Math.min(20, pct / 5))).withStyle(ChatFormatting.GOLD))
						.append(Component.literal(bar.substring(Math.min(20, pct / 5))).withStyle(ChatFormatting.DARK_GRAY))
						.append(Component.literal("]").withStyle(ChatFormatting.DARK_GRAY)));
	}
}
