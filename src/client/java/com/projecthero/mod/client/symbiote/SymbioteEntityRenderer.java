package com.projecthero.mod.client.symbiote;

import com.projecthero.mod.symbiote.entity.SymbioteEntity;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * The free-floating Symbiote has no mesh -- it is a writhing black particle cloud spawned by the
 * entity itself ({@link SymbioteEntity#tick}). This renderer exists only because Fabric requires one
 * per entity type. Same pattern as {@code TurboBoltRenderer}.
 */
public class SymbioteEntityRenderer extends EntityRenderer<SymbioteEntity> {
	private static final ResourceLocation TEX = ResourceLocation.withDefaultNamespace("textures/particle/generic_0.png");

	public SymbioteEntityRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public void render(SymbioteEntity entity, float yaw, float partialTicks, PoseStack pose,
			MultiBufferSource buffers, int light) {
		// intentionally empty -- particles are the visual
	}

	@Override
	public ResourceLocation getTextureLocation(SymbioteEntity entity) {
		return TEX;
	}
}
