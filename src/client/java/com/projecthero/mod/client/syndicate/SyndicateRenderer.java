package com.projecthero.mod.client.syndicate;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.syndicate.SyndicateEntityTypes;
import com.projecthero.mod.syndicate.entity.KingpinEntity;
import com.projecthero.mod.syndicate.entity.SyndicateCriminal;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.14.25: draws every Syndicate member with the player model in his Skindex skin ({@code SyndicateSkin}), swapping to
 * the slim-armed model for slim skins, with held items (the guns, the clubs, the Kingpin's cane) and armour. The Kingpin
 * is drawn a little broader than he is tall on top of his SCALE.
 */
public class SyndicateRenderer extends HumanoidMobRenderer<SyndicateCriminal, SyndicateModel> {
	private final SyndicateModel wide;
	private final SyndicateModel slim;

	public SyndicateRenderer(EntityRendererProvider.Context ctx) {
		this(ctx, new SyndicateModel(ctx.bakeLayer(ModelLayers.PLAYER), false), new SyndicateModel(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true));
	}

	private SyndicateRenderer(EntityRendererProvider.Context ctx, SyndicateModel wide, SyndicateModel slim) {
		super(ctx, wide, 0.5f);
		this.wide = wide;
		this.slim = slim;
		addLayer(new HumanoidArmorLayer<>(this, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
				new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), ctx.getModelManager()));
	}

	public static void initialize() {
		EntityRendererRegistry.register(SyndicateEntityTypes.THUG, SyndicateRenderer::new);
		EntityRendererRegistry.register(SyndicateEntityTypes.GUNMAN, SyndicateRenderer::new);
		EntityRendererRegistry.register(SyndicateEntityTypes.ENFORCER, SyndicateRenderer::new);
		EntityRendererRegistry.register(SyndicateEntityTypes.KINGPIN, SyndicateRenderer::new);
	}

	@Override
	public void render(SyndicateCriminal entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
		this.model = entity.skin().slim() ? slim : wide;
		super.render(entity, yaw, partialTick, pose, buffers, light);
	}

	@Override
	protected void scale(SyndicateCriminal entity, PoseStack pose, float partialTick) {
		super.scale(entity, pose, partialTick);
		// the player model is 15/16 of a block wide at the shoulders like vanilla's player renderer draws it
		pose.scale(0.9375f, 0.9375f, 0.9375f);
		if (entity instanceof KingpinEntity) {
			pose.scale(1.15f, 1.0f, 1.12f);
		}
	}

	@Override
	public ResourceLocation getTextureLocation(SyndicateCriminal entity) {
		return entity.skin().texture();
	}
}
