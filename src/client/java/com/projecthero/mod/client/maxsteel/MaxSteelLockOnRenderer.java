package com.projecthero.mod.client.maxsteel;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.maxsteel.MaxSteelConfig;
import com.projecthero.mod.maxsteel.data.MaxSteelFx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.2: the Turbo Cannon lock-on reticle -- four blue corner brackets that face the camera around whatever the
 * cannon is locked onto, closing in and spinning faster as the charge builds, with a white centre pip once it is
 * full. Drawn only for the local pilot (it is their HUD, in the world), from the synced {@link MaxSteelFx#cannonTarget()}.
 *
 * <p>Also owns the client lifecycle for the Max Steel render caches ({@link MaxSteelNano}, {@link MaxSteelArmCannon}).
 */
public final class MaxSteelLockOnRenderer {
	private MaxSteelLockOnRenderer() {
	}

	public static void initialize() {
		WorldRenderEvents.AFTER_ENTITIES.register(MaxSteelLockOnRenderer::render);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
			MaxSteelNano.clear();
			MaxSteelArmCannon.clear();
		}));

	}

	private static void render(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		Player player = mc.player;
		if (player == null || mc.level == null || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		MaxSteelFx fx = player.getAttachedOrElse(ModAttachments.MAX_STEEL_FX, null);
		if (fx == null || fx.cannonChargeStart() == 0L || fx.cannonTarget() < 0) {
			return;
		}
		Entity target = mc.level.getEntity(fx.cannonTarget());
		if (target == null || !target.isAlive()) {
			return;
		}
		float pt = context.tickCounter().getGameTimeDeltaPartialTick(false);
		float time = mc.level.getGameTime() + pt;
		float charge = Math.min(1f, (mc.level.getGameTime() - fx.cannonChargeStart() + pt) / MaxSteelConfig.CANNON_MAX_CHARGE_TICKS);
		Camera camera = context.camera();
		Vec3 cam = camera.getPosition();
		Vec3 centre = target.getPosition(pt).add(0, target.getBbHeight() * 0.5, 0);
		float size = Math.max(target.getBbWidth(), target.getBbHeight()) * 0.5f + 0.35f;
		size *= 1.35f - 0.35f * charge;

		PoseStack pose = context.matrixStack();
		pose.pushPose();
		pose.translate(centre.x - cam.x, centre.y - cam.y, centre.z - cam.z);
		pose.mulPose(camera.rotation()); // face the camera
		pose.mulPose(Axis.ZP.rotationDegrees(time * (4f + 16f * charge)));
		VertexConsumer vc = TurboDraw.buffer(context.consumers());
		int colour = charge >= 1f ? 0xFFFFFF : TurboDraw.CYAN;
		float t = 0.035f + 0.02f * charge; // stroke
		float arm = size * 0.38f;
		for (int k = 0; k < 4; k++) {
			pose.pushPose();
			pose.mulPose(Axis.ZP.rotationDegrees(k * 90f));
			// an L at the (+size, +size) corner
			pose.pushPose();
			pose.translate(size - arm * 0.5f, size, 0f);
			TurboDraw.box(vc, pose.last(), arm * 0.5f, t, t * 0.5f, colour, 0.9f);
			pose.popPose();
			pose.pushPose();
			pose.translate(size, size - arm * 0.5f, 0f);
			TurboDraw.box(vc, pose.last(), t, arm * 0.5f, t * 0.5f, colour, 0.9f);
			pose.popPose();
			pose.popPose();
		}
		if (charge >= 1f) {
			TurboDraw.box(vc, pose.last(), t * 1.6f, t * 1.6f, t * 0.5f, 0xFFFFFF, 0.9f);
		}
		pose.popPose();
	}
}
