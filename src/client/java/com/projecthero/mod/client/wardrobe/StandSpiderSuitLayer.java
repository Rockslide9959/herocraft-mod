package com.projecthero.mod.client.wardrobe;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.spider.SpiderSuitRender;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * v0.16.5: the skin-tight Spider-Man suits on a Mannequin, the Wardrobe's figures and vanilla armour stands. Their
 * GeckoLib shell is skipped for every wearer since v0.16.1 and {@code SpiderSuitLayer} only sits on players, so until
 * now a Spider-Man suit on an armour stand was simply invisible.
 */
public class StandSpiderSuitLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
	public StandSpiderSuitLayer(RenderLayerParent<T, M> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing, float limbSwingAmount,
			float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		SpiderSuitRender.render(pose, buffers, light, getParentModel(), entity);
	}
}
