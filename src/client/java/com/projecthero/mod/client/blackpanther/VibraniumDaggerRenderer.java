package com.projecthero.mod.client.blackpanther;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.blackpanther.entity.VibraniumDaggerEntity;
import com.projecthero.mod.blackpanther.item.BlackPantherItems;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** v0.17.3: the thrown Vibranium Dagger: the dagger sprite spinning end over end along its flight. */
public class VibraniumDaggerRenderer extends EntityRenderer<VibraniumDaggerEntity> {
	public VibraniumDaggerRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0f;
	}

	@Override
	public void render(VibraniumDaggerEntity entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		pose.pushPose();
		pose.translate(0f, 0.15f, 0f);
		// v0.17.7 (user: "throwing dagger: make the dagger rotate the correct way please"): it used to face the camera and spin in
		// the screen's plane. Now the sprite's plane holds the flight line: turned to the heading (the sprite's +x along it), tipped
		// to the climb / drop, then tumbling end over end FORWARD about the sideways axis (the point leads over the top), with the
		// sprite's diagonal blade (point up-right) turned -45 so at spin 0 the point is along the flight. Knife and suited dagger
		// are the same entity, so both get it; on the way back it follows its own velocity, so it tumbles towards him.
		net.minecraft.world.phys.Vec3 v = entity.getDeltaMovement();
		float heading, climb;
		if (v.lengthSqr() > 1.0e-6) {
			heading = (float) Math.toDegrees(Math.atan2(v.x, v.z));
			climb = (float) Math.toDegrees(Math.atan2(v.y, Math.sqrt(v.x * v.x + v.z * v.z)));
		} else {
			heading = -entity.getYRot();
			climb = -entity.getXRot();
		}
		pose.mulPose(Axis.YP.rotationDegrees(heading - 90f));
		pose.mulPose(Axis.ZP.rotationDegrees(climb));
		pose.mulPose(Axis.ZP.rotationDegrees(-(entity.tickCount + partial) * 50f - 45f));
		pose.scale(1.1f, 1.1f, 1.1f);
		Minecraft.getInstance().getItemRenderer().renderStatic(new ItemStack(BlackPantherItems.DAGGER), ItemDisplayContext.GROUND, light,
				OverlayTexture.NO_OVERLAY, pose, buffers, entity.level(), entity.getId());
		pose.popPose();
		super.render(entity, yaw, partial, pose, buffers, light);
	}

	@Override
	@SuppressWarnings("deprecation")
	public ResourceLocation getTextureLocation(VibraniumDaggerEntity entity) {
		return TextureAtlas.LOCATION_BLOCKS;
	}
}
