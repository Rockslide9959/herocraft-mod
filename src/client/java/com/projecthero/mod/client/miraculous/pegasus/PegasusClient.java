package com.projecthero.mod.client.miraculous.pegasus;

import com.projecthero.mod.client.miraculous.KwamiClientKit;
import com.projecthero.mod.client.miraculous.MiraculousClient;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.pegasus.Pegasus;
import com.projecthero.mod.miraculous.pegasus.entity.PegasusEntityTypes;
import com.projecthero.mod.miraculous.pegasus.network.PegasusVoyageMenuPayload;
import com.projecthero.mod.miraculous.pegasus.network.PegasusWaypointsPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.6: Pegasus's client side -- the suit layer (with the Horseshoe), the poses, the portal and horseshoe renderers,
 * the hoof prints / sky platforms / waypoint markers ({@link PegasusWorldFx}), the Sky Gallop movement
 * ({@link PegasusSkyClient}) and the Voyage menu ({@link PegasusVoyageScreen}). First person: the sleeve is
 * {@code PegasusFirstPersonArmMixin}, the Horseshoe in hand {@code PegasusHorseshoeHandMixin}; worn armour is hidden by
 * {@code PegasusArmorMixin}.
 */
public final class PegasusClient implements KwamiClientKit {
	private PegasusClient() {
	}

	public static void initialize() {
		MiraculousClient.register(Kwami.KAALKI, new PegasusClient());
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof PlayerRenderer playerRenderer) {
				helper.register(new PegasusLayer(playerRenderer));
			}
		});
		EntityRendererRegistry.register(PegasusEntityTypes.PORTAL, PegasusPortalRenderer::new);
		EntityRendererRegistry.register(PegasusEntityTypes.HORSESHOE, HorseshoeRenderer::new);
		WorldRenderEvents.AFTER_ENTITIES.register(PegasusWorldFx::render);
		ClientTickEvents.START_CLIENT_TICK.register(PegasusSkyClient::tick);
		ClientTickEvents.END_CLIENT_TICK.register(PegasusWorldFx::tick);
		ClientPlayNetworking.registerGlobalReceiver(PegasusVoyageMenuPayload.TYPE, (payload, context) -> PegasusVoyageScreen.receive(payload));
		ClientPlayNetworking.registerGlobalReceiver(PegasusWaypointsPayload.TYPE, (payload, context) -> PegasusWorldFx.receive(payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> PegasusWorldFx.reset());
	}

	@Override
	public void applyPose(Player player, HumanoidModel<?> model) {
		// the suit is drawn over his own skin: while any of it shows, his own second layer goes -- the hat (hair) too,
		// the mane hood replaces it (the reveal redraws it texel by texel while it changes)
		if (model instanceof PlayerModel<?> pm && PegasusRender.cover(player, 0f) > 0f) {
			pm.hat.visible = false;
			pm.jacket.visible = false;
			pm.leftSleeve.visible = false;
			pm.rightSleeve.visible = false;
			pm.leftPants.visible = false;
			pm.rightPants.visible = false;
		}
		PegasusPose.apply(player, model);
	}

	/** False on purpose -- worn armour is hidden by {@code PegasusArmorMixin}; the second layer by {@link #applyPose}. */
	@Override
	public boolean hidesSkinOverlay(Player player) {
		return false;
	}

	/** Where the Horseshoe is on {@code player} now: thrown (none), in his hand for a move, else at his hip. */
	public static PegasusRender.ShoeAt shoeAt(Player player, float partial) {
		if (Pegasus.shoeOut(player)) {
			return PegasusRender.ShoeAt.NONE;
		}
		return PegasusPose.inHand(player, partial) ? PegasusRender.ShoeAt.HAND : PegasusRender.ShoeAt.BELT;
	}
}
