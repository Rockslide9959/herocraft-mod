package com.projecthero.mod.client.miraculous;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.miraculous.entity.KwamiEntity;
import com.projecthero.mod.miraculous.item.MiraculousBoxItem;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/**
 * v0.16.6: the Kwami through GeckoLib -- the user-approved concept rigs ({@code scratchpad/gen_kwami_models.js},
 * exported by {@code scratchpad/gen_kwami_game.js}): {@code geo/miraculous/<id>.geo.json} + {@code
 * textures/entity/miraculous/<id>.png}, every clip in {@code animations/miraculous/kwami.animation.json}. The rigs are
 * about 18 model pixels tall; drawn at {@link #SCALE} they float about 0.6 blocks tall. A plain Entity has no body yaw
 * in GeckoLib, so the renderer turns it by its own yaw, and the head follows its pitch (it looks where its owner looks).
 */
public class KwamiRenderer extends GeoEntityRenderer<KwamiEntity> {
	public static final float SCALE = 0.56f;

	public KwamiRenderer(EntityRendererProvider.Context context) {
		super(context, new Model());
		this.shadowRadius = 0f;
		withScale(SCALE);
	}

	@Override
	protected void applyRotations(KwamiEntity animatable, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick,
			float nativeScale) {
		super.applyRotations(animatable, poseStack, ageInTicks, Mth.rotLerp(partialTick, animatable.yRotO, animatable.getYRot()), partialTick,
				nativeScale);
	}

	static final class Model extends GeoModel<KwamiEntity> {
		private static final ResourceLocation ANIM = ProjectHeroMod.id("animations/miraculous/kwami.animation.json");

		@Override
		public ResourceLocation getModelResource(KwamiEntity k) {
			return ProjectHeroMod.id("geo/miraculous/" + k.kwami().id + ".geo.json");
		}

		@Override
		public ResourceLocation getTextureResource(KwamiEntity k) {
			return ProjectHeroMod.id("textures/entity/miraculous/" + k.kwami().id + ".png");
		}

		@Override
		public ResourceLocation getAnimationResource(KwamiEntity k) {
			return ANIM;
		}

		/** Every clip keys the head, so this offset never accumulates. */
		@Override
		public void setCustomAnimations(KwamiEntity k, long instanceId, AnimationState<KwamiEntity> state) {
			GeoBone head = getAnimationProcessor().getBone("head");
			if (head != null) {
				float pitch = Mth.lerp(state.getPartialTick(), k.xRotO, k.getXRot());
				head.setRotX(head.getRotX() + pitch * Mth.DEG_TO_RAD * 0.7f);
			}
		}
	}

	/** v0.16.6: the Miraculous Box in hand / in the inventory (its own GeckoLib model, no animation). */
	public static final class BoxRenderer extends GeoItemRenderer<MiraculousBoxItem> {
		public BoxRenderer() {
			super(new GeoModel<>() {
				@Override
				public ResourceLocation getModelResource(MiraculousBoxItem item) {
					return ProjectHeroMod.id("geo/miraculous/miraculous_box.geo.json");
				}

				@Override
				public ResourceLocation getTextureResource(MiraculousBoxItem item) {
					return ProjectHeroMod.id("textures/item/miraculous_box_model.png");
				}

				@Override
				public ResourceLocation getAnimationResource(MiraculousBoxItem item) {
					return ProjectHeroMod.id("animations/miraculous/miraculous_box.animation.json");
				}
			});
		}
	}
}
