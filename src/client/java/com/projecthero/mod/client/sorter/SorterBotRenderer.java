package com.projecthero.mod.client.sorter;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.ironman.sorter.SorterBotEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;
import software.bernie.geckolib.util.RenderUtil;

/**
 * v0.14.16: the Stark Sorter Bot -- GeckoLib model with a glowmask (arc-reactor chest light, visor, thruster),
 * plus the stack it is carrying drawn between its hands so players can follow each load to its chest.
 *
 * <p>v0.14.17: GeckoLib only turns {@code LivingEntity}s to face their body yaw -- for this plain {@code Entity} it
 * passed 0, so the bot always faced south while the cargo (placed by its real yaw) floated off to one side. It now
 * turns to its own yaw (the way it is flying), and the cargo rides the model's {@code cargo} bone (a child of the
 * arm, between the hands), so it follows every arm swing of the fly / pickup / deposit clips.
 */
public class SorterBotRenderer extends GeoEntityRenderer<SorterBotEntity> {
	/** Item size in blocks: about the gap between the hands (8 px). */
	private static final float CARGO_SCALE = 0.42f;

	public SorterBotRenderer(EntityRendererProvider.Context context) {
		super(context, new Model());
		this.shadowRadius = 0.25f;
		addRenderLayer(new AutoGlowingGeoLayer<>(this));
		addRenderLayer(new BlockAndItemGeoLayer<>(this) {
			@Override
			protected ItemStack getStackForBone(GeoBone bone, SorterBotEntity bot) {
				if (!"cargo".equals(bone.getName())) {
					return null;
				}
				int anim = bot.anim();
				ItemStack cargo = bot.shownCargo();
				return cargo.isEmpty() || anim == SorterBotEntity.ANIM_SPAWN || anim == SorterBotEntity.ANIM_DESPAWN ? null : cargo;
			}

			/** The pose stack is already transformed for this bone (see OathbreakerRenderer): just move to the pivot. */
			@Override
			public void renderForBone(PoseStack pose, SorterBotEntity bot, GeoBone bone, RenderType renderType,
					MultiBufferSource buffers, VertexConsumer buffer, float partialTick, int light, int overlay) {
				ItemStack cargo = getStackForBone(bone, bot);
				if (cargo == null) {
					return;
				}
				pose.pushPose();
				RenderUtil.translateToPivotPoint(pose, bone);
				pose.scale(CARGO_SCALE, CARGO_SCALE, CARGO_SCALE);
				Minecraft.getInstance().getItemRenderer().renderStatic(cargo, ItemDisplayContext.FIXED, light,
						OverlayTexture.NO_OVERLAY, pose, buffers, bot.level(), bot.getId());
				pose.popPose();
			}
		});
	}

	/** Face the way it flies: its own (smoothed) yaw, not GeckoLib's non-living default of 0. */
	@Override
	protected void applyRotations(SorterBotEntity bot, PoseStack pose, float ageInTicks, float rotationYaw, float partialTick,
			float nativeScale) {
		super.applyRotations(bot, pose, ageInTicks, Mth.rotLerp(partialTick, bot.yRotO, bot.getYRot()), partialTick, nativeScale);
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
