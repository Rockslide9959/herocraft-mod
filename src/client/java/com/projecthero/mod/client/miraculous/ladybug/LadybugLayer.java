package com.projecthero.mod.client.miraculous.ladybug;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.render.OverlayStrips;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.16.6: Ladybug on every player who holds Tikki (every viewer: the state is synced) -- the suit ({@link LadybugRender})
 * over the wearer's own skin, their jacket / sleeve / trouser layer going texel by texel during a change and their hat
 * layer (hair, hats, glasses) kept, the Yo-yo stored on her back (v0.16.7; it hung at her right hip) from the
 * belt while no move has it out, and the posed right hand's world position for {@link LadybugWorld}'s string.
 */
public class LadybugLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	public LadybugLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
			float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.isInvisible() || !Miraculous.holds(player, Kwami.TIKKI)) {
			return;
		}
		LadybugRender.Anim anim = LadybugRender.anim(player, partialTick);
		if (anim.cover() <= 0f) {
			return;
		}
		PlayerModel<AbstractClientPlayer> parent = getParentModel();
		LadybugRender.render(pose, buffers, light, player, parent, anim);
		if (anim.cover() < 0.999f) {
			// the jacket / sleeves / trousers go texel by texel as the suit covers them
			float cover = anim.cover();
			OverlayStrips.render(pose, buffers, light, player, parent, (u, v) -> LadybugRender.order(u, v) > cover, false);
		}
		// v0.16.6 (user): the wearer's hat layer (hair, hats, glasses) stays -- the core hides every overlay part while a
		// suit shows, so it is drawn again here, from the wearer's own skin
		if (player.isModelPartShown(PlayerModelPart.HAT)) {
			boolean was = parent.hat.visible;
			parent.hat.visible = true;
			parent.hat.render(pose, buffers.getBuffer(RenderType.entityTranslucent(player.getSkin().texture())), light,
					LivingEntityRenderer.getOverlayCoords(player, 0f));
			parent.hat.visible = was;
		}
		if (!Miraculous.transformed(player)) {
			return;
		}
		// v0.16.7 (user): the Yo-yo is stored on her BACK, on the right side of the small of her back, its face outward
		// (model space: +y is down, the back is +z; was on the front of her right hip)
		if (anim.cover() > 0.8f && !LadybugWorld.yoyoOut(player)) {
			pose.pushPose();
			parent.body.translateAndRotate(pose);
			pose.translate(-0.12f, 0.58f, 0.215f); // the right side of her back, where she keeps it in the show (-x is her right; the spool is centred on this point, body half-width 0.25)
			pose.mulPose(com.mojang.math.Axis.YP.rotation((float) (-Math.PI / 2)));
			LadybugGeometry.yoyo(pose, buffers, light, 0f, 0f, 0.8f);
			pose.popPose();
		}
		// where the posed right hand is this frame (camera-relative in the entity pass), for the string
		pose.pushPose();
		parent.rightArm.translateAndRotate(pose);
		Vector3f tip = pose.last().pose().transformPosition(new Vector3f(-1f / 16f, 11f / 16f, 0f));
		pose.popPose();
		Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
		Vec3 world = cam.add(tip.x, tip.y, tip.z);
		if (world.distanceToSqr(player.getPosition(partialTick)) < 9.0) {
			LadybugWorld.recordHand(player, world);
		}
	}
}
