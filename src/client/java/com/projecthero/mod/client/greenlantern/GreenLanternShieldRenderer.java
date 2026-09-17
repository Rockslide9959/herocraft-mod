package com.projecthero.mod.client.greenlantern;

import com.projecthero.mod.attachment.ModAttachments;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the Directional Shield (Z, held) as a green-tinted vanilla {@code minecraft:shield}, floating
 * a block in front of the wielder's eyes ({@link #DISTANCE}, v0.11.5's "spawn 1 block in front of the
 * player") and tracking their live look direction every frame. Rendered for the wielder as well as
 * everyone else (v0.11.5 -- "make them able to see the shield aswell"), in both first and third person.
 *
 * <p>Purely a render, exactly like {@code SpiderWebLineRenderer}: no entity is spawned, nothing to
 * clean up -- {@link ModAttachments#GREEN_LANTERN_BARRIER_HP} (synced to everyone) going back to 0
 * simply stops this from drawing on the next frame. The Protective Dome (Shift+Z) uses the same HP
 * attachment but gets its own all-around particle-outline visual in {@code GreenLanternShield}
 * instead of a held shield, so this skips dome barriers (see {@link ModAttachments#GREEN_LANTERN_BARRIER_IS_DOME}).
 *
 * <p>The green tint uses vanilla's own shield colouring ({@link DataComponents#BASE_COLOR}) rather
 * than a bespoke texture -- shields already support 16 colours natively.
 */
public final class GreenLanternShieldRenderer {
	private static final ItemStack SHIELD_STACK = buildShieldStack();
	/** How far in front of the eyes the shield floats. */
	private static final double DISTANCE = 1.0;

	private GreenLanternShieldRenderer() {
	}

	public static void initialize() {
		WorldRenderEvents.AFTER_ENTITIES.register(GreenLanternShieldRenderer::render);
	}

	private static ItemStack buildShieldStack() {
		ItemStack stack = new ItemStack(Items.SHIELD);
		stack.set(DataComponents.BASE_COLOR, DyeColor.GREEN);
		return stack;
	}

	private static void render(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || context.consumers() == null) {
			return;
		}
		PoseStack poseStack = context.matrixStack();
		if (poseStack == null) {
			return;
		}
		Camera camera = context.camera();
		MultiBufferSource buffers = context.consumers();
		float partialTick = context.tickCounter().getGameTimeDeltaPartialTick(false);

		// v0.11.5: the wielder can now see their own shield too -- it floats a full block out in front
		// of the eyes, well clear of the first-person camera, so the old "blinding" concern that used to
		// skip rendering it for the wielder in first person no longer applies.
		for (Player player : mc.level.players()) {
			float hp = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_BARRIER_HP, 0f);
			boolean dome = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_BARRIER_IS_DOME, false);
			if (hp <= 0f || dome) {
				continue;
			}
			drawShield(poseStack, buffers, camera, mc, player, partialTick);
		}
	}

	private static void drawShield(PoseStack poseStack, MultiBufferSource buffers, Camera camera,
			Minecraft mc, Player player, float partialTick) {
		Vec3 eye = player.getEyePosition(partialTick);
		Vec3 look = player.getViewVector(partialTick);
		Vec3 pos = eye.add(look.scale(DISTANCE));
		Vec3 camPos = camera.getPosition();

		float yaw = player.getViewYRot(partialTick);
		float pitch = player.getViewXRot(partialTick);

		poseStack.pushPose();
		poseStack.translate(pos.x - camPos.x, pos.y - camPos.y, pos.z - camPos.z);
		// Face the shield's front along the same direction the player is looking -- vanilla's yaw is
		// offset 180 degrees from render-space forward (+Z), the same convention thrown-item/arrow
		// rendering uses.
		poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - yaw));
		poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
		poseStack.scale(1.15f, 1.15f, 1.15f);

		int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(pos));
		mc.getItemRenderer().renderStatic(SHIELD_STACK, ItemDisplayContext.FIXED, light,
				OverlayTexture.NO_OVERLAY, poseStack, buffers, mc.level, player.getId());

		poseStack.popPose();
	}
}
