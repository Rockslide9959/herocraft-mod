package com.projecthero.mod.client.quirk.erasure;

import com.projecthero.mod.client.quirk.QuirkClient;
import com.projecthero.mod.client.quirk.QuirkClientKit;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.erasure.ErasureGogglesPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7: the Erasure Quirk's client side -- the poses ({@link ErasurePose}), the risen hair / red eyes / goggles
 * ({@link ErasureLayer}), the capture scarf in the world ({@link ErasureWorld}), the swing / zip movement
 * ({@link ErasureSwingClient}) and the screen effects: the red vignette while YOU are erased, the blink, the goggles
 * ({@link ErasureHud}; the outlines are {@code ErasureGogglesGlowMixin}).
 */
public final class ErasureClient implements QuirkClientKit {
	private ErasureClient() {
	}

	public static void initialize() {
		QuirkClient.register(Quirk.ERASURE, new ErasureClient());
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof PlayerRenderer playerRenderer) {
				helper.register(new ErasureLayer(playerRenderer));
				helper.register(new SleepingBagLayer(playerRenderer)); // v0.17.1: Aizawa's Sleeping Bag, on anyone
			}
		});
		ErasureFirstPerson.register(); // v0.17.1 revamp: first-person arms
		WorldRenderEvents.AFTER_ENTITIES.register(ErasureWorld::render);
		HudRenderCallback.EVENT.register(ErasureHud::render);
		ClientTickEvents.START_CLIENT_TICK.register(ErasureSwingClient::tick);
		ClientPlayNetworking.registerGlobalReceiver(ErasureGogglesPayload.TYPE, (payload, context) -> ErasureHud.receive(payload.ids()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ErasureHud.reset();
			ErasureSwingClient.reset();
			ErasureWorld.reset();
		});
	}

	@Override
	public void applyPose(Player player, HumanoidModel<?> model) {
		ErasurePose.apply(player, model);
	}

	/**
	 * v0.17.1 revamp: the Dry Eyes widget -- an eye at the bar's left that closes as it dries (red while he stares, shut at
	 * 100), then the shared banded bar.
	 */
	@Override
	public boolean drawMeterWidget(net.minecraft.client.gui.GuiGraphics g, Player player, com.projecthero.mod.client.quirk.QuirkHud.MeterView v) {
		int ex = v.x(), ew = 11;
		int cy = v.y() + v.h() / 2;
		float dry = net.minecraft.util.Mth.clamp(v.value() / 100f, 0f, 1f);
		boolean staring = com.projecthero.mod.quirk.Quirks.peek(player) != null
				&& com.projecthero.mod.quirk.Quirks.peek(player).val(com.projecthero.mod.quirk.erasure.ErasureConfig.V_ERASE) != 0L;
		boolean shut = com.projecthero.mod.quirk.erasure.ErasureMoves.eyesShut(player);
		int open = shut ? 0 : Math.max(1, Math.round(3f * (1f - dry * 0.85f)));
		// the almond: white, then the iris (red while staring), the lids closing from top and bottom
		for (int dx = 0; dx < ew; dx++) {
			float u = (dx - (ew - 1) / 2f) / ((ew - 1) / 2f);
			int half = Math.max(0, Math.round(open * (float) Math.sqrt(Math.max(0f, 1f - u * u))));
			if (half > 0) {
				g.fill(ex + dx, cy - half, ex + dx + 1, cy + half, 0xFFE8E4DC);
			}
			g.fill(ex + dx, cy - half - 1, ex + dx + 1, cy - half, 0xFF2A2A2E);
			g.fill(ex + dx, cy + half, ex + dx + 1, cy + half + 1, 0xFF2A2A2E);
		}
		if (open > 1) {
			int iris = staring ? 0xFFE02020 : 0xFF3A3A3A;
			g.fill(ex + 4, cy - Math.min(2, open), ex + 7, cy + Math.min(2, open), iris);
			if (staring) {
				g.fill(ex + 5, cy - 1, ex + 6, cy, 0xFFFFB0B0);
			}
		}
		com.projecthero.mod.client.quirk.QuirkHud.MeterView bar = new com.projecthero.mod.client.quirk.QuirkHud.MeterView(v.x() + ew + 3, v.y(),
				Math.max(10, v.w() - ew - 3), v.h(), v.value(), v.band(), v.spec(), v.colour(), v.now(), v.overloaded(), v.erased());
		g.fill(bar.x() - 1, bar.y() - 1, bar.x() + bar.w() + 1, bar.y() + bar.h() + 1, 0xFF4A4A4A);
		g.fill(bar.x(), bar.y(), bar.x() + bar.w(), bar.y() + bar.h(), 0xC0141414);
		com.projecthero.mod.client.quirk.QuirkHud.drawBands(g, bar);
		return true;
	}
}
