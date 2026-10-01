package com.projecthero.mod.client.sorter;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.ironman.sorter.SorterBotEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * v0.14.16: the Stark Sorter Bot -- GeckoLib model with a glowmask (arc-reactor chest light, visor, thruster),
 * plus the stack it is carrying drawn between its hands so players can follow each load to its chest.
 */
public class SorterBotRenderer extends GeoEntityRenderer<SorterBotEntity> {
	public SorterBotRenderer(EntityRendererProvider.Context context) {
		super(context, new Model());
		this.shadowRadius = 0.25f;
		addRenderLayer(new AutoGlowingGeoLayer<>(this));
	}

	@Override
	public void render(SorterBotEntity bot, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
		super.render(bot, yaw, partialTick, pose, buffers, light);
		ItemStack cargo = bot.shownCargo();
		int anim = bot.anim();
		if (cargo.isEmpty() || anim == SorterBotEntity.ANIM_SPAWN || anim == SorterBotEntity.ANIM_DESPAWN) {
			return;
		}
		float bodyYaw = Mth.rotLerp(partialTick, bot.yRotO, bot.getYRot());
		float bob = Mth.sin((bot.tickCount + partialTick) * 0.25f) * 0.02f;
		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
		pose.translate(0.0, 0.34 + bob, 0.30);
		pose.scale(0.42f, 0.42f, 0.42f);
		Minecraft.getInstance().getItemRenderer().renderStatic(cargo, ItemDisplayContext.FIXED, light,
				OverlayTexture.NO_OVERLAY, pose, buffers, bot.level(), bot.getId());
		pose.popPose();
	}

	static final class Model extends GeoModel<SorterBotEntity> {
		private static final ResourceLocation GEO = ProjectHeroMod.id("geo/stark_sorter_bot.geo.json");
		private static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/stark_sorter_bot.png");
		private static final ResourceLocation ANIMATION = ProjectHeroMod.id("animations/stark_sorter_bot.animation.json");

		@Override
		public ResourceLocation getModelResource(SorterBotEntity bot) {
			return GEO;
		}

		@Override
		public ResourceLocation getTextureResource(SorterBotEntity bot) {
			return TEXTURE;
		}

		@Override
		public ResourceLocation getAnimationResource(SorterBotEntity bot) {
			return ANIMATION;
		}
	}
}
