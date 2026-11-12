package com.projecthero.mod.client.spin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.spin.data.SpinMark;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * v0.17.3 the Spin on a creature's own body, any creature, every viewer.
 * <ul>
 *   <li><b>Spun</b> (a ball hit, Spin Touch, Golden Spin): golden spiral bands scroll round and up the skin -- it twists.</li>
 *   <li><b>Aged</b> (Ball Breaker): a grey wash with dark cracks over the whole skin.</li>
 * </ul>
 * The rings that wind round the body are drawn in the world ({@link SpinFx}).
 */
public class SpinMarkLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
	public static final ResourceLocation SWIRL = ProjectHeroMod.id("textures/entity/spin/power/spin_swirl.png");
	public static final ResourceLocation CRACKS = ProjectHeroMod.id("textures/entity/spin/power/aged_cracks.png");

	public SpinMarkLayer(RenderLayerParent<T, M> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing, float limbSwingAmount, float partialTick,
			float ageInTicks, float netHeadYaw, float headPitch) {
		SpinMark m = entity.getAttachedOrElse(ModAttachments.SPIN_MARK, null);
		if (m == null || entity.isInvisible()) {
			return;
		}
		long now = entity.level().getGameTime();
		if (m.aged(now)) {
			getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(CRACKS)), light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
		}
		if (m.spun(now) || m.locked(now)) {
			float t = ageInTicks * 0.045f;
			getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.energySwirl(SWIRL, (t * 1.6f) % 1f, (t * -0.9f) % 1f)),
					LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0xFF6E5622); // (dim: energySwirl is additive)
		}
	}
}
