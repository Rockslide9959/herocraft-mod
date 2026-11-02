package com.projecthero.mod.client.miraculous.queenbee;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.miraculous.KwamiClientKit;
import com.projecthero.mod.client.miraculous.MiraculousClient;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.queenbee.QueenBee;
import com.projecthero.mod.miraculous.queenbee.QueenBeeConfig;

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
 * v0.17.2: Queen Bee's client side -- the suit layer (with the spinning top), the poses, the world visuals
 * ({@link QueenBeeWorldFx}), Hive Sense's gold outlines ({@link QueenBeeHiveSense}, through the Heat Sense glow mixins)
 * and the passive buzzing glide (her own client caps her fall while she holds jump in the air). First person: the suit
 * sleeve ({@code QueenBeeFirstPersonArmMixin}); worn armour is hidden by {@code QueenBeeArmorMixin}. The worn Bee Comb is
 * drawn by {@code MiraculousJewelLayer}.
 */
public final class QueenBeeClient implements KwamiClientKit {
	private QueenBeeClient() {
	}

	public static void initialize() {
		MiraculousClient.register(Kwami.POLLEN, new QueenBeeClient());
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof PlayerRenderer playerRenderer) {
				helper.register(new QueenBeeLayer(playerRenderer));
			}
		});
		WorldRenderEvents.AFTER_ENTITIES.register(QueenBeeWorldFx::render);
		ClientTickEvents.END_CLIENT_TICK.register(QueenBeeClient::glide);
	}

	/** The buzzing glide: holding jump in the air (not flying, not on an elytra), she falls no faster than a slow drift. */
	private static void glide(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.isPaused() || !QueenBee.is(p) || p.onGround() || p.getAbilities().flying || p.isFallFlying() || p.isInWater()
				|| !mc.options.keyJump.isDown()) {
			return;
		}
		Vec3 v = p.getDeltaMovement();
		if (v.y < -QueenBeeConfig.GLIDE_FALL_SPEED) {
			p.setDeltaMovement(v.x, -QueenBeeConfig.GLIDE_FALL_SPEED, v.z);
		}
	}

	@Override
	public void applyPose(Player player, HumanoidModel<?> model) {
		// the suit is drawn over her own skin: while any of it shows, her jacket / sleeve / trouser layers go (the reveal
		// redraws them texel by texel); her hat layer -- her hair -- stays, the suit has no hood
		if (model instanceof PlayerModel<?> pm && QueenBeeRender.cover(player, 0f) > 0f) {
			pm.jacket.visible = false;
			pm.leftSleeve.visible = false;
			pm.rightSleeve.visible = false;
			pm.leftPants.visible = false;
			pm.rightPants.visible = false;
		}
		QueenBeePose.apply(player, model);
	}

	/** False on purpose -- worn armour is hidden by {@code QueenBeeArmorMixin}; the second layer by {@link #applyPose}. */
	@Override
	public boolean hidesSkinOverlay(Player player) {
		return false;
	}

	/** First person (the mixin): the suit sleeve over the arm. */
	public static void renderFirstPersonArm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, ModelPart arm, boolean right,
			float partial) {
		if (!Miraculous.holds(player, Kwami.POLLEN)) {
			return;
		}
		float cover = QueenBeeRender.cover(player, partial);
		if (cover > 0f) {
			QueenBeeRender.renderArm(pose, buffers, light, arm, right, QueenBeeRender.slim(player), cover);
		}
	}
}
