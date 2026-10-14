package com.projecthero.mod.client.hulk;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.hulk.entity.MirrorHulkEntity;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

/**
 * v0.16.4 (user): the Hulk in the mirror of a player's mind ({@link MirrorHulkEntity}) -- the very model, texture and
 * animations the Gamma power draws a player with ({@link HulkModel}), so the reflection is the Hulk he becomes. The head
 * follows the entity's look (the player's mirrored look), on top of whatever the clip does.
 */
public class MirrorHulkModel extends GeoModel<MirrorHulkEntity> {
	private static final ResourceLocation GEO = ProjectHeroMod.id("geo/hulk.geo.json");
	private static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/hulk.png");
	private static final ResourceLocation ANIMATION = ProjectHeroMod.id("animations/hulk.animation.json");
	/** Per instance: {head X, head Y as written, look X, look Y as added} from the last frame (see HulkModel). */
	private static final java.util.Map<Long, float[]> LAST_HEAD = new java.util.concurrent.ConcurrentHashMap<>();

	@Override
	public ResourceLocation getModelResource(MirrorHulkEntity animatable) {
		return GEO;
	}

	@Override
	public ResourceLocation getTextureResource(MirrorHulkEntity animatable) {
		return TEXTURE;
	}

	@Override
	public ResourceLocation getAnimationResource(MirrorHulkEntity animatable) {
		return ANIMATION;
	}

	@Override
	public void setCustomAnimations(MirrorHulkEntity animatable, long instanceId, AnimationState<MirrorHulkEntity> state) {
		EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
		if (data == null) {
			return;
		}
		getBone("head").ifPresent(head -> {
			// a clip that doesn't key the head leaves last frame's value in the bone: take our own offset back off first
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
