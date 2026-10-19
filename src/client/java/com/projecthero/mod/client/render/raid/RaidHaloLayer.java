package com.projecthero.mod.client.render.raid;

import java.util.function.ToIntFunction;

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
 * v0.16.1: the raid undead's bloom (the Frost Giants' {@code FrostHaloLayer} with a per-entity colour): a second, additive
 * pass of the emissive texture ({@link RenderType#eyes}), which also lights the "halo" shells baked into the acid, cursed
 * and boss models (cubes invisible in the main and translucent glow passes, a soft radial glow here). {@code colour}
 * returns 0xRRGGBB -- the strength / tint of the pass (the Powered Zombie Boss tints it with its power's colour).
 */
public class RaidHaloLayer<T extends GeoAnimatable> extends GeoRenderLayer<T> {
	private final ToIntFunction<T> colour;

	public RaidHaloLayer(GeoRenderer<T> renderer, ToIntFunction<T> colour) {
		super(renderer);
		this.colour = colour;
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
				LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0xFF000000 | (colour.applyAsInt(animatable) & 0xFFFFFF));
	}
}
