package com.projecthero.mod.client.hulk;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.hulk.WorldBreaker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * v0.16.4 (user): World Breaker -- "he starts getting glowing green veins". While it runs, two emissive passes over the
 * Hulk's own animated model (so they follow every animation and his 10-block scale for free):
 * <ol>
 *   <li>the veins ({@code textures/entity/hulk_world_breaker_veins.png}, made by
 *       {@code scratchpad/gen_hulk_world_breaker_veins_v0164.js} on hulk.png's UV layout, only over his green skin), drawn
 *       additive and full-bright, throbbing with a heartbeat;</li>
 *   <li>a deep green glow: his own skin again, additive, tinted dark green and pulsing slowly.</li>
 * </ol>
 * The gladiator bones are already hidden again by the time this runs (the gladiator layer restores the base pass), so only
 * the body glows.
 */
public class HulkWorldBreakerLayer extends GeoRenderLayer<HulkAnimatable> {
	public static final ResourceLocation VEINS = ProjectHeroMod.id("textures/entity/hulk_world_breaker_veins.png");
	public static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/hulk.png");

	private final HulkRenderer hulk;

	public HulkWorldBreakerLayer(HulkRenderer renderer) {
		super(renderer);
		this.hulk = renderer;
	}

	/** 0..1, the heartbeat: a sharp double thump about once a second, slower glow under it. */
	public static float pulse(AbstractClientPlayer player, float partialTick) {
		float t = (player.level().getGameTime() + partialTick) / 20.0f;
		float beat = t % 1.0f;
		float thump = (float) (Math.exp(-Math.pow((beat - 0.08f) / 0.06f, 2)) + 0.7 * Math.exp(-Math.pow((beat - 0.32f) / 0.07f, 2)));
		return Math.max(0.0f, Math.min(1.0f, 0.35f + 0.65f * thump));
	}

	private static int grey(float f, int r, int g, int b) {
		f = Math.max(0.0f, Math.min(1.0f, f));
		return 0xFF000000 | (Math.round(r * f) << 16) | (Math.round(g * f) << 8) | Math.round(b * f);
	}

	@Override
	public void render(PoseStack poseStack, HulkAnimatable animatable, BakedGeoModel bakedModel, @Nullable RenderType renderType,
			MultiBufferSource bufferSource, @Nullable VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
		AbstractClientPlayer player = hulk.getCurrentEntity();
		if (player == null || hulk.ghost() || !WorldBreaker.active(player)) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (player.isInvisible() && (mc.player == null || player.isInvisibleTo(mc.player))) {
			return;
		}
		float p = pulse(player, partialTick);
		int full = LightTexture.FULL_BRIGHT;
		// the deep green glow over his whole body (slow swell)
		float slow = 0.5f + 0.5f * (float) Math.sin((player.level().getGameTime() + partialTick) * 0.15f);
		RenderType glow = RenderType.eyes(SKIN);
		getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glow, bufferSource.getBuffer(glow), partialTick, full,
				OverlayTexture.NO_OVERLAY, grey(0.05f + 0.07f * slow, 60, 255, 40));
		// the veins, throbbing
		RenderType veins = RenderType.eyes(VEINS);
		getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, veins, bufferSource.getBuffer(veins), partialTick, full,
				OverlayTexture.NO_OVERLAY, grey(p, 255, 255, 255));
	}
}
