package com.projecthero.mod.client.miraculous.multimouse;

import com.projecthero.mod.client.miraculous.KwamiClientKit;
import com.projecthero.mod.client.miraculous.MiraculousClient;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.multimouse.entity.MultimouseEntityTypes;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.player.Player;

import com.mojang.blaze3d.vertex.PoseStack;

/**
 * v0.17.1: Multimouse's client side -- the suit layer (with the ears, rope belt and tail), the poses, the mini copies'
 * renderer, the world visuals (the rope, the snares, Mouse Hearing: {@link MultimouseWorldFx}) and the HUD card
 * ({@link MultimouseHud}). First person: the suit sleeve ({@code MultimouseFirstPersonArmMixin}); worn armour is hidden
 * by {@code MultimouseArmorMixin}. The worn Mouse Pendant is drawn by {@code MiraculousJewelLayer}.
 */
public final class MultimouseClient implements KwamiClientKit {
	private MultimouseClient() {
	}

	public static void initialize() {
		MiraculousClient.register(Kwami.MULLO, new MultimouseClient());
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof PlayerRenderer playerRenderer) {
				helper.register(new MultimouseLayer(playerRenderer));
			}
		});
		EntityRendererRegistry.register(MultimouseEntityTypes.MINI, MiniMouseRenderer::new);
		WorldRenderEvents.AFTER_ENTITIES.register(MultimouseWorldFx::render);
		MultimouseHud.initialize();
	}

	@Override
	public void applyPose(Player player, HumanoidModel<?> model) {
		// the suit is drawn over her own skin: while any of it shows, her whole second layer goes -- the hood replaces
		// her hair layer too (the reveal redraws them texel by texel)
		if (model instanceof PlayerModel<?> pm && MultimouseRender.cover(player, 0f) > 0f) {
			pm.hat.visible = false;
			pm.jacket.visible = false;
			pm.leftSleeve.visible = false;
			pm.rightSleeve.visible = false;
			pm.leftPants.visible = false;
			pm.rightPants.visible = false;
		}
		MultimousePose.apply(player, model);
	}

	/** False on purpose -- worn armour is hidden by {@code MultimouseArmorMixin}; the second layer by {@link #applyPose}. */
	@Override
	public boolean hidesSkinOverlay(Player player) {
		return false;
	}

	/** First person (the mixin): the suit sleeve over the arm. */
	public static void renderFirstPersonArm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, ModelPart arm, boolean right,
			float partial) {
		if (!Miraculous.holds(player, Kwami.MULLO)) {
			return;
		}
		float cover = MultimouseRender.cover(player, partial);
		if (cover > 0f) {
			MultimouseRender.renderArm(pose, buffers, light, arm, right, MultimouseRender.slim(player), cover);
		}
	}
}
