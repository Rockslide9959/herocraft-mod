package com.projecthero.mod.client.spin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.spin.model.SteelBallRender;
import com.projecthero.mod.spin.Spin;
import com.projecthero.mod.spin.SpinBalls;
import com.projecthero.mod.spin.data.SpinState;

import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * v0.17.3 round 2: a held Steel Ball ({@code projecthero:steel_ball}) is the real 3D ball ({@link SteelBallRender}) -- spinning gently
 * in the hand, fast and golden while its owner charges a Golden Spin or Ball Breaker. In the inventory it is the flat single-ball icon
 * ({@code textures/item/steel_ball.png}). The item model is {@code builtin/entity}; {@code models/item/steel_ball.json} poses it.
 */
public final class SteelBallItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
	private static final ResourceLocation ICON = ProjectHeroMod.id("textures/item/steel_ball.png");
	/** The ball's radius in item space (a block is 1): a fist-sized ball. */
	private static final float RADIUS = 0.22f;

	@Override
	public void render(ItemStack stack, ItemDisplayContext mode, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
		if (mode == ItemDisplayContext.GUI) {
			icon(pose, buffers);
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		float time = mc.level == null ? 0f : mc.level.getGameTime() + mc.getTimer().getGameTimeDeltaPartialTick(false);
		boolean golden = false;
		SpinBalls.BallId id = SpinBalls.id(stack);
		if (id != null && mc.level != null) {
			Player owner = mc.level.getPlayerByUUID(id.owner());
			golden = owner != null && Spin.charging(owner) != SpinState.CHARGE_NONE
					&& owner.getMainHandItem() == stack;
		}
		boolean held = mode.firstPerson() || mode == ItemDisplayContext.THIRD_PERSON_LEFT_HAND || mode == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
		float spin = held ? (time * (golden ? 70f : 9f)) % 360f : 0f;
		pose.pushPose();
		pose.translate(0.5f, 0.5f, 0.5f);
		SteelBallRender.render(pose, buffers, golden ? LightTexture.FULL_BRIGHT : light, RADIUS, spin, golden);
		pose.popPose();
	}

	/** The inventory icon: the 2D single-ball texture on a quad facing the screen. */
	private static void icon(PoseStack pose, MultiBufferSource buffers) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(ICON));
		PoseStack.Pose p = pose.last();
		int lit = LightTexture.FULL_BRIGHT;
		vc.addVertex(p, 0f, 0f, 0.5f).setColor(255, 255, 255, 255).setUv(0f, 1f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lit).setNormal(p, 0f, 0f, 1f);
		vc.addVertex(p, 1f, 0f, 0.5f).setColor(255, 255, 255, 255).setUv(1f, 1f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lit).setNormal(p, 0f, 0f, 1f);
		vc.addVertex(p, 1f, 1f, 0.5f).setColor(255, 255, 255, 255).setUv(1f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lit).setNormal(p, 0f, 0f, 1f);
		vc.addVertex(p, 0f, 1f, 0.5f).setColor(255, 255, 255, 255).setUv(0f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lit).setNormal(p, 0f, 0f, 1f);
	}
}
