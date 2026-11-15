package com.projecthero.mod.client.targetdummy;

import com.projecthero.mod.targetdummy.DummyHitPayload;
import com.projecthero.mod.targetdummy.TargetDummyEntity;
import com.projecthero.mod.targetdummy.TargetDummyMod;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/** v0.17.5: the Target Dummy's model layers, renderer and hit payload (numbers, wobble, readout). */
public final class TargetDummyClient {
	private TargetDummyClient() {
	}

	public static void initialize() {
		EntityModelLayerRegistry.registerModelLayer(TargetDummyRenderer.LAYER, TargetDummyModel::createLayer);
		EntityModelLayerRegistry.registerModelLayer(TargetDummyRenderer.BASE_LAYER, TargetDummyModel::createBaseLayer);
		EntityRendererRegistry.register(TargetDummyMod.TARGET_DUMMY, TargetDummyRenderer::new);
		ClientPlayNetworking.registerGlobalReceiver(DummyHitPayload.TYPE, (payload, context) -> {
			Minecraft mc = context.client();
			if (mc.level == null) {
				return;
			}
			Entity e = mc.level.getEntity(payload.entityId());
			if (e instanceof TargetDummyEntity dummy) {
				DummyClientStats.of(dummy).accept(dummy, payload);
			}
		});
	}
}
