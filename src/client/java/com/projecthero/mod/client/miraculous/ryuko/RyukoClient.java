package com.projecthero.mod.client.miraculous.ryuko;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.client.miraculous.KwamiClientKit;
import com.projecthero.mod.client.miraculous.MiraculousClient;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.ryuko.Ryuko;
import com.projecthero.mod.miraculous.ryuko.RyukoConfig;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3: Ryuko's client side -- the suit layer with the sword, the poses, the world visuals ({@link RyukoWorldFx}) and Storm
 * Rise's slow glide (her own client caps her fall). First person: the suit sleeve and the sword
 * ({@code RyukoFirstPersonArmMixin}); worn armour is hidden by {@code RyukoArmorMixin}. The worn Dragon Choker is drawn by
 * {@code MiraculousJewelLayer}.
 */
public final class RyukoClient implements KwamiClientKit {
	private RyukoClient() {
	}

	public static void initialize() {
		MiraculousClient.register(Kwami.LONGG, new RyukoClient());
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof PlayerRenderer playerRenderer) {
				helper.register(new RyukoLayer(playerRenderer));
			}
		});
		WorldRenderEvents.AFTER_ENTITIES.register(RyukoWorldFx::render);
		ClientTickEvents.END_CLIENT_TICK.register(RyukoClient::glide);
		// v0.17.3: the quick change's element swirl and sound
		ClientTickEvents.END_CLIENT_TICK.register(RyukoSuitUpFx::tick);
	}

	private static void glide(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.isPaused() || !Ryuko.is(p) || !Ryuko.gliding(p) || p.onGround() || p.getAbilities().flying) {
			return;
		}
		Vec3 v = p.getDeltaMovement();
		if (v.y < -RyukoConfig.GLIDE_FALL_SPEED) {
			p.setDeltaMovement(v.x, -RyukoConfig.GLIDE_FALL_SPEED, v.z);
		}
	}

	@Override
	public void applyPose(Player player, HumanoidModel<?> model) {
		// the suit is drawn over her own skin: while any of it shows, her jacket / sleeve / trouser layers go (the reveal
		// redraws them texel by texel); her hat layer -- her hair -- stays
		if (model instanceof PlayerModel<?> pm && RyukoRender.cover(player, 0f) > 0f) {
			pm.jacket.visible = false;
			pm.leftSleeve.visible = false;
			pm.rightSleeve.visible = false;
			pm.leftPants.visible = false;
			pm.rightPants.visible = false;
		}
		RyukoPose.apply(player, model);
	}

	/** False on purpose -- worn armour is hidden by {@code RyukoArmorMixin}; the second layer by {@link #applyPose}. */
	@Override
	public boolean hidesSkinOverlay(Player player) {
		return false;
	}

	/** First person (the mixin): the suit sleeve over the arm, and the sword in the right hand. */
	public static void renderFirstPersonArm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, ModelPart arm, boolean right,
			float partial) {
		if (!Miraculous.holds(player, Kwami.LONGG)) {
			return;
		}
		float cover = RyukoRender.cover(player, partial);
		if (cover <= 0f) {
			return;
		}
		RyukoRender.renderArm(pose, buffers, light, arm, right, RyukoRender.slim(player), cover, RyukoRender.edgeColor(player, partial));
		if (right && cover > 0.99f && !Ryuko.swordAway(player)) {
			pose.pushPose();
			arm.translateAndRotate(pose);
			RyukoLayer.placeInHand(pose, arm.xRot, RyukoRender.slim(player));
			pose.mulPose(Axis.XP.rotationDegrees(24f));
			RyukoRender.renderSword(pose, buffers, light, Ryuko.color(Ryuko.element(player)));
			pose.popPose();
		}
	}
}
