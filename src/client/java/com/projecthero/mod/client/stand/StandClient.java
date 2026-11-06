package com.projecthero.mod.client.stand;

import com.projecthero.mod.stand.StandConfig;
import com.projecthero.mod.stand.Stands;
import com.projecthero.mod.stand.data.StandState;
import com.projecthero.mod.stand.network.StandActionPayload;
import com.projecthero.mod.stand.network.TimeStopPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * v0.17.2: the Stand's client side -- the Stand drawn in the world ({@link StandRender}, Stand users only), the HUD, the
 * Stand Menu (Shift+N), the time stop ({@link TimeStopClient}), Precision Sight's zoom and outlines, and the H request
 * (sent from {@code ProjectHeroModClient}).
 */
public final class StandClient {
	private StandClient() {
	}

	public static void initialize() {
		HudRenderCallback.EVENT.register(StandHud::render);
		WorldRenderEvents.AFTER_ENTITIES.register(StandRender::render);
		WorldRenderEvents.AFTER_ENTITIES.register(StandCoat::render); // the N pose's coat: the user's, so every viewer sees it
		WorldRenderEvents.AFTER_TRANSLUCENT.register(StandFx::render);
		ClientPlayNetworking.registerGlobalReceiver(com.projecthero.mod.stand.network.StandFxPayload.TYPE,
				(payload, context) -> StandFx.receive(payload));
		WorldRenderEvents.END.register(TimeStopClient::render);
		ClientPlayNetworking.registerGlobalReceiver(TimeStopPayload.TYPE, (payload, context) -> TimeStopClient.receive(payload));
		// the N pose points down the look: a player standing still lets their body drift up to 50 degrees from it, so while posing it squares up
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.level == null) {
				return;
			}
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
		});
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

	/** Precision Sight is on for the local player. */
	public static boolean sightActive() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			return false;
		}
		StandState s = Stands.peek(mc.player);
		return s != null && s.isUser() && s.sightUntil > mc.level.getGameTime();
	}

	/** Precision Sight outlines every creature within range, for this Stand user's eyes only. */
	public static boolean sightOutlined(Entity e) {
		Minecraft mc = Minecraft.getInstance();
		return e instanceof LivingEntity && e != mc.player && sightActive() && e.distanceToSqr(mc.player) < StandConfig.SIGHT_GLOW_RANGE * StandConfig.SIGHT_GLOW_RANGE;
	}
}
