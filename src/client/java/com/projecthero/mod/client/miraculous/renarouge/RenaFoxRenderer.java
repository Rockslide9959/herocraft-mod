package com.projecthero.mod.client.miraculous.renarouge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.miraculous.renarouge.entity.RenaFoxEntity;

import net.minecraft.client.model.FoxModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.Fox;

/**
 * v0.16.7: an illusory fox of the Army of Foxes -- the vanilla fox, drawn see-through and tinted to Rena's orange light,
 * shimmering (its alpha breathes), fading out as it pops into mist.
 */
public class RenaFoxRenderer extends MobRenderer<RenaFoxEntity, RenaFoxRenderer.TintedFoxModel<RenaFoxEntity>> {
	static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/fox/fox.png");

	/** The fox model, every vertex multiplied by {@link #tint} (ARGB) instead of the colour it is given. */
	public static final class TintedFoxModel<T extends Fox> extends FoxModel<T> {
		public int tint = 0xFFFFFFFF;

		public TintedFoxModel(ModelPart root) {
			super(root);
		}

		@Override
		public void renderToBuffer(PoseStack pose, VertexConsumer vc, int light, int overlay, int color) {
			super.renderToBuffer(pose, vc, light, overlay, tint);
		}
	}

	public RenaFoxRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new TintedFoxModel<>(ctx.bakeLayer(ModelLayers.FOX)), 0.3f);
	}

	@Override
	public void render(RenaFoxEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		float pop = e.popProgress(partial);
		float shimmer = 0.72f + 0.12f * Mth.sin((e.tickCount + partial) * 0.3f + e.getId());
		float born = Mth.clamp((e.tickCount + partial) / 6f, 0f, 1f);
		int a = Math.round(255 * Mth.clamp(shimmer * born * (1f - pop), 0f, 1f));
		int g = (int) Mth.lerp(pop, 190, 140);
		int b = (int) Mth.lerp(pop, 120, 50);
		this.model.tint = FastColor.ARGB32.color(a, 255, g, b);
		this.shadowRadius = 0.3f * (1f - pop);
		if (pop > 0f) {
			pose.pushPose();
			float s = 1f + 0.4f * pop;
			pose.scale(s, s, s);
			super.render(e, yaw, partial, pose, buffers, light);
			pose.popPose();
			return;
		}
		super.render(e, yaw, partial, pose, buffers, light);
	}

	@Override
	protected RenderType getRenderType(RenaFoxEntity e, boolean bodyVisible, boolean translucent, boolean glowing) {
		return RenderType.entityTranslucentEmissive(TEXTURE);
	}

	@Override
	public ResourceLocation getTextureLocation(RenaFoxEntity e) {
		return TEXTURE;
	}
}
