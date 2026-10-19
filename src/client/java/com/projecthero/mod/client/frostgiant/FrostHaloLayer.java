package com.projecthero.mod.client.frostgiant;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * v0.16.1: the Frost Giants' bloom. A second, ADDITIVE pass of the glowmask ({@link RenderType#eyes}) on top of GeckoLib's
 * own glow layer, so ice, crystals, ridges and red eyes read as light sources at dusk and in the blizzard. The models also
 * carry "halo" shells -- inflated copies of every glowing cube (and a plate over each eye) whose texels are fully
 * transparent in the base texture and in the glowmask's alpha but carry a soft radial colour in the glowmask's RGB: the
 * main pass and GeckoLib's translucent glow pass discard them, this additive pass turns them into a soft halo.
 */
public class FrostHaloLayer<T extends GeoAnimatable> extends GeoRenderLayer<T> {
	/** Strength of the additive pass (vertex colour multiplier). */
	private final int colour;

	public FrostHaloLayer(GeoRenderer<T> renderer, float strength) {
		super(renderer);
		int c = Math.max(0, Math.min(255, Math.round(255 * strength)));
		this.colour = 0xFF000000 | (c << 16) | (c << 8) | c;
	}

	/** v0.16.7 (perf): base texture -> its glowmask's additive render type (was a substring + concat every frame). */
	private static final java.util.Map<ResourceLocation, RenderType> EYES = new java.util.concurrent.ConcurrentHashMap<>();

	static boolean tooFar(net.minecraft.world.entity.Entity e) {
		var cam = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
		double scale = e instanceof net.minecraft.world.entity.LivingEntity l ? Math.max(1.0, l.getScale()) : 1.0;
		double far = 48.0 + e.getBbHeight() * 2.0 * scale;
		return e.distanceToSqr(cam) > far * far;
	}

	@Override
	public void render(PoseStack poseStack, T animatable, BakedGeoModel bakedModel, @Nullable RenderType renderType, MultiBufferSource bufferSource,
			@Nullable VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
		if (animatable instanceof net.minecraft.world.entity.Entity e && e.isInvisible()) {
			return;
		}
		if (animatable instanceof net.minecraft.world.entity.Entity e && tooFar(e)) {
			return; // v0.16.7 (perf): the bloom pass re-renders the whole model; past 48 blocks it is a few pixels
		}
		RenderType additive = EYES.computeIfAbsent(getTextureResource(animatable), base -> {
			String p = base.getPath();
			return RenderType.eyes(ResourceLocation.fromNamespaceAndPath(base.getNamespace(), p.substring(0, p.length() - 4) + "_glowmask.png"));
		});
		getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, additive, bufferSource.getBuffer(additive), partialTick,
				LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, colour);
	}
}
