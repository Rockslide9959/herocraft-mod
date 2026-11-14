package com.projecthero.mod.client.stand;

import com.projecthero.mod.stand.Stands;
import com.projecthero.mod.stand.network.StandActionPayload;
import com.projecthero.mod.stand.network.TimeStopPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * v0.17.2: the Stand's client side -- the Stand drawn in the world ({@link StandRender}, Stand users only), the HUD, the
 * Stand Menu (Shift+N), the time stop ({@link TimeStopClient}), the flicked ball bearing, the swing-driven Stand jabs
 * ({@link StandSwing}) and the H request (sent from {@code ProjectHeroModClient}).
 */
public final class StandClient {
	private StandClient() {
	}

	public static void initialize() {
		HudRenderCallback.EVENT.register(StandHud::render);
		// (after every entity is on screen: thinned out in third person, it must not hide the user it stands behind)
		WorldRenderEvents.AFTER_TRANSLUCENT.register(StandRender::render);
		WorldRenderEvents.AFTER_TRANSLUCENT.register(StandFx::render);
		ClientPlayNetworking.registerGlobalReceiver(com.projecthero.mod.stand.network.StandFxPayload.TYPE,
				(payload, context) -> StandFx.receive(payload));
		WorldRenderEvents.END.register(TimeStopClient::render);
		com.projecthero.mod.stand.TimeStop.clientCap = TimeStopClient::speedCap; // v0.17.4: a speedster's pace in a time stop
		ClientPlayNetworking.registerGlobalReceiver(TimeStopPayload.TYPE, (payload, context) -> TimeStopClient.receive(payload));
		// the N pose points down the look: a player standing still lets their body drift up to 50 degrees from it, so while posing it squares up
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.level == null) {
				return;
			}
			StandSwing.tick(client.level);
			for (net.minecraft.world.entity.player.Player p : client.level.players()) {
				var pose = p.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.STAND_POSE, null);
				if (pose != null && pose.pose() != 0) {
					p.yBodyRot += net.minecraft.util.Mth.wrapDegrees(p.getYHeadRot() - p.yBodyRot) * 0.5f;
				}
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			TimeStopClient.reset();
			StandRender.reset();
			StandFx.reset();
			StandSwing.reset();
		});
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(com.projecthero.mod.stand.entity.StandEntities.BALL_BEARING,
				BallBearingRenderer::new);
	}

	/** Whether plain H belongs to the Stand for this player. */
	public static boolean ownsH(LocalPlayer player) {
		return Stands.hasPower(player);
	}

	public static void pressH() {
		ClientPlayNetworking.send(new StandActionPayload(StandActionPayload.Action.TOGGLE_SUMMON, 0));
	}

	/** Shift+N. */
	public static void openMenu() {
		Minecraft.getInstance().setScreen(new StandMenuScreen());
	}

}
