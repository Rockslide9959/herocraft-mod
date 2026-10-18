package com.projecthero.mod.client.miraculous.catnoir;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.LivingEntity;

/**
 * v0.16.6: a creature CATACLYSM touched turns dark and cracks -- its own model drawn again over itself, first a blotchy
 * blackening, then a web of black cracks, for {@link CataclysmFx#DARK_TICKS} (on every living renderer; it draws
 * nothing unless that creature was just touched).
 */
public class CataclysmTouchLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
	public CataclysmTouchLayer(RenderLayerParent<T, M> parent) {
		super(parent);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static RenderLayer<?, ?> of(LivingEntityRenderer<?, ?> renderer) {
		return new CataclysmTouchLayer((RenderLayerParent) renderer);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing, float limbSwingAmount, float partialTick,
			float ageInTicks, float netHeadYaw, float headPitch) {
		if (entity.isInvisible()) {
			return;
		}
		float[] k = CataclysmFx.darkness(entity.getId(), partialTick);
		if (k == null) {
			return;
		}
		M model = getParentModel();
		if (k[0] > 0.01f) {
			model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(CataclysmFx.ENTITY_DARK)), light, OverlayTexture.NO_OVERLAY,
					FastColor.ARGB32.color(Math.round(245 * k[0]), 255, 255, 255));
		}
		if (k[1] > 0.01f) {
			model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(CataclysmFx.ENTITY_CRACKS)), light, OverlayTexture.NO_OVERLAY,
					FastColor.ARGB32.color(Math.round(255 * k[1]), 255, 255, 255));
		}
	}
}
