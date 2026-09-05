package com.herocraft.mod.client.render;

import com.herocraft.mod.maxsteel.entity.TurboBoltEntity;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * The Turbo Blast bolt has no mesh -- it is a thick cyan streak carried by the entity's own particle
 * spawns (see {@link TurboBoltEntity#tick}). This renderer exists only because Fabric requires one
 * per entity type. Same pattern as {@code IronManMissileRenderer}.
 */
public class TurboBoltRenderer extends EntityRenderer<TurboBoltEntity> {
	private static final ResourceLocation TEX = ResourceLocation.withDefaultNamespace("textures/particle/generic_0.png");

	public TurboBoltRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public void render(TurboBoltEntity entity, float yaw, float partialTicks, PoseStack pose,
			MultiBufferSource buffers, int light) {
		// intentionally empty -- particles are the visual
	}

	@Override
	public ResourceLocation getTextureLocation(TurboBoltEntity entity) {
		return TEX;
	}
}
