package com.projecthero.mod.client.quirk;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * v0.16.7: the worn Quirk hero suit pieces (and the Dark Shadow bird head) on every humanoid -- players, armour stands,
 * mannequins, zombies. See {@link QuirkCostume}; the first-person sleeve is {@code QuirkFirstPersonArmMixin}.
 */
public class QuirkCostumeLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
	public QuirkCostumeLayer(RenderLayerParent<T, M> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing, float limbSwingAmount,
			float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (getParentModel() instanceof HumanoidModel<?> humanoid) {
			QuirkCostume.render(pose, buffers, light, entity, humanoid);
		}
	}
}
