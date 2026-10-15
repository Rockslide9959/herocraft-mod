package com.projecthero.mod.client.bluebeetle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.bluebeetle.BlueBeetle;
import com.projecthero.mod.bluebeetle.item.BlueBeetleItems;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * v0.16.5: Blue Beetle's armour on every player who has it out (every viewer), see {@link BlueBeetleRender} -- and the
 * Scarab itself, a 3D scarab fixed between the shoulder blades of anyone bonded to Khaji-Da, armour on or off (the user
 * asked for it to stay on the player). Full-bright, with a slow breathing tilt.
 */
public class BlueBeetleLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	private static ItemStack scarab;

	public BlueBeetleLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
			float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.isInvisible() || !BlueBeetle.hasPower(player)) {
			return;
		}
		BlueBeetleRender.Anim anim = BlueBeetleRender.anim(player, partialTick);
		if (anim.anything()) {
			BlueBeetleRender.render(pose, buffers, light, getParentModel(), player, anim, partialTick, ageInTicks);
		}
		renderBackScarab(pose, buffers, player, ageInTicks);
	}

	/** The Scarab on the spine: in body space, on the back plate between the shoulder blades, facing out backwards. */
	private void renderBackScarab(PoseStack pose, MultiBufferSource buffers, AbstractClientPlayer player, float age) {
		if (scarab == null) {
			scarab = new ItemStack(BlueBeetleItems.SCARAB);
		}
		pose.pushPose();
		getParentModel().body.translateAndRotate(pose);
		// the back of the body cube is at z = +2 px; the armour sits a hair outside it
		pose.translate(0.0f, 0.30f, 0.17f);
		pose.mulPose(Axis.YP.rotationDegrees(180f));
		pose.mulPose(Axis.ZP.rotationDegrees(180f)); // model space is upside down: head of the beetle toward the neck
		pose.mulPose(Axis.XP.rotationDegrees(2.5f * Mth.sin(age * 0.08f)));
		pose.scale(0.42f, 0.42f, 0.6f);
		Minecraft.getInstance().getItemRenderer().renderStatic(scarab, ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
				pose, buffers, player.level(), player.getId());
		pose.popPose();
	}
}
