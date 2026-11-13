package com.projecthero.mod.client.starfire;

import java.util.UUID;

import com.projecthero.mod.client.omnitrix.OmnitrixRenderers;
import com.projecthero.mod.starfire.Starfire;
import com.projecthero.mod.starfire.StarfireMoves;
import com.projecthero.mod.starfire.network.StarfireActionPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3 Starfire: the client side, called once from {@code ProjectHeroModClient}: the HUD (the Joy / Fury Gauge and the Starbolt
 * bar), the glow overlays on her body, the eye beams, the bolts' look and the moves' glows, and the double-tap-Space flight request.
 * Every move goes through the ability slots; H stays the power selector and N does nothing for her. The Language Kiss bond's
 * outlines through walls are {@code StarfireBondGlowMixin} ({@link #bondGlows}).
 */
public final class StarfireClient {
	private StarfireClient() {
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static void initialize() {
		HudRenderCallback.EVENT.register(StarfireHud::render);
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof PlayerRenderer pr) {
				helper.register(new StarfireGlowLayer(pr));
			}
		});
		OmnitrixRenderers.projectileLook(StarfireMoves.BOLT_KIND, StarfireFx::bolt);
		OmnitrixRenderers.projectileLook(StarfireMoves.BURST_KIND, StarfireFx::bolt);
		WorldRenderEvents.AFTER_ENTITIES.register(StarfireBeamRenderer::render);
		WorldRenderEvents.AFTER_ENTITIES.register(StarfireFx::render);
		// v0.17.3 animation round: the move poses' per-player trackers (StarfirePose), dropped on leaving a world
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(mc -> StarfirePose.clientTick(mc.level));
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> StarfirePose.clear());
	}

	/** Double-tap Space in the air as Starfire: take off / land (the server re-checks). */
	public static boolean wantsFlightToggle(LocalPlayer player) {
		return player != null && Starfire.hasPower(player) && (!player.onGround() || Starfire.isFlying(player));
	}

	public static void toggleFlight() {
		ClientPlayNetworking.send(new StarfireActionPayload(StarfireActionPayload.TOGGLE_FLIGHT));
	}

	/**
	 * Language Kiss on a player: for 60 s each sees the other outlined through walls. True for {@code entity} when this client's
	 * player kissed it, or it is a Starfire who kissed this client's player.
	 */
	public static boolean bondGlows(Entity entity) {
		LocalPlayer me = Minecraft.getInstance().player;
		if (me == null || entity == me || !(entity instanceof Player other)) {
			return false;
		}
		UUID mine = Starfire.bondedWith(me);
		if (mine != null && mine.equals(other.getUUID())) {
			return true;
		}
		UUID theirs = Starfire.bondedWith(other);
		return theirs != null && theirs.equals(me.getUUID());
	}
}
