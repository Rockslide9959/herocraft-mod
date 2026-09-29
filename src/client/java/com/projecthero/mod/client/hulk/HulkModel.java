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
	/** Per animatable instance: {head X, head Y as written, look X, look Y as added} from the last frame. */
	private static final java.util.Map<Long, float[]> LAST_HEAD = new java.util.concurrent.ConcurrentHashMap<>();

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
			// v0.13.17: a clip that doesn't key the head (throw, pickup) leaves last frame's value in the bone, so adding the
			// look on top piled up every frame and spun his head. If the bone still holds exactly what we wrote last frame,
			// no clip touched it: take our own look offset back off before adding this frame's.
			float curX = head.getRotX();
			float curY = head.getRotY();
			float[] last = LAST_HEAD.get(instanceId);
			float baseX = last != null && curX == last[0] ? curX - last[2] : curX;
			float baseY = last != null && curY == last[1] ? curY - last[3] : curY;
			float addX = data.headPitch() * Mth.DEG_TO_RAD;
			float addY = data.netHeadYaw() * Mth.DEG_TO_RAD;
			head.setRotX(baseX + addX);
			head.setRotY(baseY + addY);
			LAST_HEAD.put(instanceId, new float[] { baseX + addX, baseY + addY, addX, addY });
		});
	}
}
