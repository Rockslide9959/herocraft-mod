package com.herocraft.mod.client.render;

import com.herocraft.mod.HeroCraftMod;
import com.herocraft.mod.ironman.entity.IronManMissileEntity;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * The missile has no model -- it is a fast smoke/flame streak carried by the entity's own particle
 * spawns (see {@link IronManMissileEntity#tick}). This renderer exists only because Fabric requires a
 * renderer per entity type.
 */
public class IronManMissileRenderer extends EntityRenderer<IronManMissileEntity> {
	private static final ResourceLocation TEX = HeroCraftMod.id("textures/item/missile_module.png");

	public IronManMissileRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public void render(IronManMissileEntity entity, float yaw, float partialTicks, PoseStack pose,
			MultiBufferSource buffers, int light) {
		// intentionally empty -- particles are the visual
	}

	@Override
	public ResourceLocation getTextureLocation(IronManMissileEntity entity) {
		return TEX;
	}
}
