package com.projecthero.mod.client.miraculous.renarouge;

import com.projecthero.mod.client.miraculous.KwamiClientKit;
import com.projecthero.mod.client.miraculous.MiraculousClient;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.renarouge.RenaRouge;
import com.projecthero.mod.miraculous.renarouge.entity.RenaRougeEntityTypes;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.6: Rena Rouge's client side -- the suit layer on players, the poses, and the renderers of her illusions, the
 * thrown Flute, the illusion wall and the Mirage bubble. The first-person sleeve / Flute and the camouflage are mixins
 * ({@code RenaRougeFirstPersonArmMixin}, {@code RenaRougeFluteHandMixin}, {@code RenaRougeCamouflageMixin},
 * {@code RenaRougeHeldItemMixin}).
 */
public final class RenaRougeClient implements KwamiClientKit {
	private RenaRougeClient() {
	}

	public static void initialize() {
		MiraculousClient.register(Kwami.TRIXX, new RenaRougeClient());
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof PlayerRenderer playerRenderer) {
				helper.register(new RenaRougeLayer(playerRenderer));
			}
		});
		EntityRendererRegistry.register(RenaRougeEntityTypes.ILLUSION, RenaIllusionRenderer::new);
		EntityRendererRegistry.register(RenaRougeEntityTypes.FLUTE, FluteBoomerangRenderer::new);
		EntityRendererRegistry.register(RenaRougeEntityTypes.FAKE_WALL, FakeWallRenderer::new);
		EntityRendererRegistry.register(RenaRougeEntityTypes.BUBBLE, MirageBubbleRenderer::new);
		// v0.16.7: the Mirage wheel, its foxes and its big visuals
		EntityRendererRegistry.register(RenaRougeEntityTypes.FOX, RenaFoxRenderer::new);
		EntityRendererRegistry.register(RenaRougeEntityTypes.MIRAGE_FX, MirageFxRenderer::new);
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
				com.projecthero.mod.miraculous.renarouge.network.RenaMirageMenuPayload.TYPE, (payload, context) -> RenaMirageWheelScreen.receive(payload));
	}

	@Override
	public void applyPose(Player player, HumanoidModel<?> model) {
		// the suit is a cut-out over her own skin: while any of it shows, her jacket / sleeve / trouser layers go (the
		// reveal redraws them texel by texel), but her hat layer -- her own hair -- stays
		if (model instanceof PlayerModel<?> pm && RenaRougeRender.cover(player, 0f) > 0f) {
			pm.jacket.visible = false;
			pm.leftSleeve.visible = false;
			pm.rightSleeve.visible = false;
			pm.leftPants.visible = false;
			pm.rightPants.visible = false;
		}
		RenaRougePose.apply(player, model);
	}

	/** v0.16.6: false on purpose -- the shared hook also hides the hat (her hair); see {@link #applyPose} and {@code RenaRougeArmorMixin}. */
	@Override
	public boolean hidesSkinOverlay(Player player) {
		return false;
	}

	/** Where the Flute is on {@code player} now: thrown (none), at her lips, in her hand for a move, else on her back. */
	public static RenaRougeRender.FluteAt fluteAt(Player player, float partial) {
		RenaRougeRender.FluteAt seq = RenaRougeSequenceClient.fluteAt(player, partial); // v0.17.1: spun and caught in the long transformation
		if (seq != null) {
			return seq;
		}
		if (RenaRouge.fluteOut(player)) {
			return RenaRougeRender.FluteAt.NONE;
		}
		if (RenaRouge.running(player, RenaRouge.V_PLAY)) {
			return RenaRougeRender.FluteAt.LIPS;
		}
		float[] p = RenaRougePose.playing(player, partial);
		if (p != null) {
			int id = (int) p[0];
			switch (id) {
				case RenaRouge.ANIM_STRIKE_1, RenaRouge.ANIM_STRIKE_2, RenaRouge.ANIM_STRIKE_3, RenaRouge.ANIM_SWEEP, RenaRouge.ANIM_CATCH,
						RenaRouge.ANIM_DOUBLE, RenaRouge.ANIM_WALL, RenaRouge.ANIM_THROW -> {
					return RenaRougeRender.FluteAt.HAND;
				}
				case RenaRouge.ANIM_MIRAGE -> {
					return p[1] > 24f ? RenaRougeRender.FluteAt.HAND : RenaRougeRender.FluteAt.LIPS;
				}
				default -> {
				}
			}
		}
		return RenaRougeRender.FluteAt.BACK;
	}

	/** First person: the Flute is in her hand (a move) or at her lips; null = vanilla draws the hand as usual. */
	public static RenaRougeRender.FluteAt firstPersonFlute(Player player, float partial) {
		if (!RenaRouge.is(player) || RenaRougeRender.cover(player, partial) < 0.999f) {
			return null;
		}
		RenaRougeRender.FluteAt at = fluteAt(player, partial);
		return at == RenaRougeRender.FluteAt.HAND || at == RenaRougeRender.FluteAt.LIPS ? at : null;
	}
}
