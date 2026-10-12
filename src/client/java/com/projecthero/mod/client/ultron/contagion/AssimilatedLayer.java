package com.projecthero.mod.client.ultron.contagion;

import java.util.function.Predicate;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * v0.16.1: the Ultron Contagion's look on a vanilla mob -- the mob's own model drawn again with an overlay texture of
 * half-metal plating (same UV layout, transparent elsewhere) and then its red eyes, emissive. Shown while
 * {@code show} says so (the synced Assimilated / Ultron Golem attachment).
 */
public class AssimilatedLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
	private final ResourceLocation plating;
	private final ResourceLocation eyes;
	private final Predicate<T> show;

	public AssimilatedLayer(RenderLayerParent<T, M> parent, String texture, Predicate<T> show) {
		super(parent);
		this.plating = ProjectHeroMod.id("textures/entity/ultron/" + texture + ".png");
		this.eyes = ProjectHeroMod.id("textures/entity/ultron/" + texture + "_eyes.png");
		this.show = show;
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing, float limbSwingAmount, float partialTick,
			float ageInTicks, float netHeadYaw, float headPitch) {
		if (entity.isInvisible() || !show.test(entity)) {
			return;
		}
		int overlay = LivingEntityRenderer.getOverlayCoords(entity, 0f);
		getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(plating)), light, overlay, 0xFFFFFFFF);
		getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(eyes)), 0xF000F0, overlay, 0xFFFFFFFF);
	}
}
