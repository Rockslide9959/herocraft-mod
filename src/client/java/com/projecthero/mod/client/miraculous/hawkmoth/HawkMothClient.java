package com.projecthero.mod.client.miraculous.hawkmoth;

import com.projecthero.mod.client.miraculous.KwamiClientKit;
import com.projecthero.mod.client.miraculous.MiraculousClient;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.data.MiraculousState;
import com.projecthero.mod.miraculous.hawkmoth.HawkMothKit;
import com.projecthero.mod.miraculous.hawkmoth.entity.HawkMothEntityTypes;
import com.projecthero.mod.miraculous.hawkmoth.network.HawkMothLinkPayload;
import com.projecthero.mod.miraculous.hawkmoth.network.HawkMothMarksPayload;
import com.projecthero.mod.miraculous.hawkmoth.network.HawkMothSensePayload;
import com.projecthero.mod.miraculous.hawkmoth.network.HawkMothSwarmPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.6: Hawk Moth's client side -- the suit / cane layer, the poses, the akuma renderer, the white butterflies and the
 * akuma masks ({@link ButterflyFx}), the outlines' data ({@link HawkMothFxClient}), the Window's camera and the Akuma Link
 * chat box.
 */
public final class HawkMothClient implements KwamiClientKit {
	/** The camera is on an akuma's mob because of the Window (so only that is put back). */
	private static boolean windowCamera;

	private HawkMothClient() {
	}

	public static void initialize() {
		MiraculousClient.register(Kwami.NOOROO, new HawkMothClient());
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof PlayerRenderer playerRenderer) {
				helper.register(new HawkMothLayer(playerRenderer));
			}
		});
		EntityRendererRegistry.register(HawkMothEntityTypes.AKUMA, AkumaRenderer::new);
		WorldRenderEvents.AFTER_ENTITIES.register(ButterflyFx::render);
		ClientPlayNetworking.registerGlobalReceiver(HawkMothMarksPayload.TYPE, (payload, context) -> HawkMothFxClient.receiveMarks(payload));
		ClientPlayNetworking.registerGlobalReceiver(HawkMothSensePayload.TYPE, (payload, context) -> HawkMothFxClient.receiveSense(payload));
		ClientPlayNetworking.registerGlobalReceiver(HawkMothSwarmPayload.TYPE, (payload, context) -> HawkMothFxClient.receiveSwarm(payload));
		ClientPlayNetworking.registerGlobalReceiver(HawkMothLinkPayload.TYPE,
				(payload, context) -> context.client().setScreen(new ChatScreen("/hawkmoth link ")));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			HawkMothFxClient.reset();
			windowCamera = false;
		});
		ClientTickEvents.END_CLIENT_TICK.register(HawkMothClient::tickWindow);
	}

	/** Hawk Moth's Window: the camera on the akumatized mob while the synced window value names it. */
	private static void tickWindow(Minecraft mc) {
		if (mc.player == null || mc.level == null) {
			windowCamera = false;
			return;
		}
		MiraculousState s = Miraculous.peek(mc.player);
		long id = s == null || !Miraculous.transformedAs(mc.player, Kwami.NOOROO) ? 0L : s.val(HawkMothKit.V_WINDOW_MOB);
		Entity target = id == 0L ? null : mc.level.getEntity((int) id);
		if (target != null && target.isAlive()) {
			if (mc.getCameraEntity() != target) {
				mc.setCameraEntity(target);
			}
			windowCamera = true;
		} else if (windowCamera) {
			mc.setCameraEntity(mc.player);
			windowCamera = false;
		}
	}

	@Override
	public void applyPose(Player player, HumanoidModel<?> model) {
		HawkMothPose.apply(player, model);
	}

	@Override
	public boolean hidesSkinOverlay(Player player) {
		return HawkMothSuitRender.hidesSkinOverlay(player);
	}
}
