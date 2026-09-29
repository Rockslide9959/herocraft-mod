package com.projecthero.mod.client.moonknight;

import com.projecthero.mod.moonknight.temple.KhonshuTemple;
import com.projecthero.mod.moonknight.temple.MoonKnightRitualFadePayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Client half of the Temple of Khonshu (Moon Knight Phase 7): the altar's scarab renderer and the ritual's white
 * "death" fade ({@link MoonKnightRitualFadePayload}) -- in over ~8 ticks, held while the server keeps the player
 * suspended on the altar, then out over ~24 ticks as they rise.
 */
public final class KhonshuTempleClient {
	private static final int FADE_IN = 8;
	private static final int FADE_OUT = 24;

	private static int total;
	private static int remaining;

	private KhonshuTempleClient() {
	}

	/** Called once from {@code ProjectHeroModClient}. */
	public static void register() {
		BlockEntityRendererRegistry.register(KhonshuTemple.KHONSHU_ALTAR_BE, KhonshuAltarRenderer::new);
		ClientPlayNetworking.registerGlobalReceiver(MoonKnightRitualFadePayload.TYPE,
				(payload, context) -> context.client().execute(() -> start(payload.ticks())));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (remaining > 0) {
				remaining--;
			}
			if (client.level == null) {
				remaining = 0;
			}
		});
		HudRenderCallback.EVENT.register(KhonshuTempleClient::render);
	}

	static void start(int ticks) {
		total = Math.max(1, ticks);
		remaining = total;
	}

	private static void render(GuiGraphics g, DeltaTracker delta) {
		if (remaining <= 0) {
			return;
		}
		float partial = delta.getGameTimeDeltaPartialTick(false);
		float elapsed = total - remaining + partial;
		float left = remaining - partial;
		float alpha = elapsed < FADE_IN ? elapsed / FADE_IN : left < FADE_OUT ? Math.max(0.0f, left / FADE_OUT) : 1.0f;
		int a = Math.round(Math.max(0.0f, Math.min(1.0f, alpha)) * 255.0f);
		if (a <= 0) {
			return;
		}
		g.fill(0, 0, g.guiWidth(), g.guiHeight(), (a << 24) | 0xF4F6FF);
	}
}
