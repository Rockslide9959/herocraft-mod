package com.projecthero.mod.client.hulk;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

/**
 * The Hulk's GeckoLib model: {@code geo/hulk.geo.json}, {@code textures/entity/hulk.png} and
 * {@code animations/hulk.animation.json} (all generated from the user's {@code hulk.bbmodel} by
 * {@code scratchpad/gen_hulk.js}). The head follows where the player looks, on top of whatever the animation does.
 */
public class HulkModel extends GeoModel<HulkAnimatable> {
	private static final ResourceLocation GEO = ProjectHeroMod.id("geo/hulk.geo.json");
	private static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/hulk.png");
	private static final ResourceLocation ANIMATION = ProjectHeroMod.id("animations/hulk.animation.json");

	@Override
	public ResourceLocation getModelResource(HulkAnimatable animatable) {
		return GEO;
	}

	@Override
	public ResourceLocation getTextureResource(HulkAnimatable animatable) {
		return TEXTURE;
	}

	@Override
	public ResourceLocation getAnimationResource(HulkAnimatable animatable) {
		return ANIMATION;
	}

	@Override
	public void setCustomAnimations(HulkAnimatable animatable, long instanceId, AnimationState<HulkAnimatable> state) {
		EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
		if (data == null) {
			return;
		}
		getBone("head").ifPresent(head -> {
			head.setRotX(head.getRotX() + data.headPitch() * Mth.DEG_TO_RAD);
			head.setRotY(head.getRotY() + data.netHeadYaw() * Mth.DEG_TO_RAD);
		});
	}
}
