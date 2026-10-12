package com.projecthero.mod.client.greenlantern;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.firearm.item.FirearmItem;
import com.projecthero.mod.greenlantern.GreenLanternConfig;
import com.projecthero.mod.greenlantern.construct.GreenLanternConstructs;
import com.projecthero.mod.network.GreenLanternSniperPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.1: the local side of the Green Lantern Sniper Rifle construct. While the rifle is out
 * ({@link GreenLanternConstructs#HAND_SNIPER} in the synced hand-construct mask) attack and use belong to it -- vanilla's
 * swing / mine / place / use are suppressed by {@code GreenLanternSniperInputMixin} -- and this sends edge-triggered
 * requests: attack down = start charging, attack up = fire, right-click held = scoped. Everything that matters (charge
 * timing, damage, cost, cooldown) is decided on the server; the local charge clock here only drives the HUD and reticle.
 */
public final class GreenLanternSniperClient {
	private static boolean attackWasDown;
	private static boolean scoped;
	/** Local game time the current charge started, or -1. */
	private static long chargeSince = -1L;

	private GreenLanternSniperClient() {
	}

	/** Whether {@code player}'s Sniper Rifle is out (any viewer: the mask is synced to everyone). */
	public static boolean isOut(Player player) {
		return player != null && (player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_HAND_CONSTRUCTS, 0)
				& GreenLanternConstructs.HAND_SNIPER) != 0;
	}

	/** Whether the local player's rifle should take over attack / use right now. */
	public static boolean active() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		// a real gun in the hand keeps its own controls
		return p != null && isOut(p) && !p.isSpectator() && !(p.getMainHandItem().getItem() instanceof FirearmItem);
	}

	public static void clientTick(Minecraft mc) {
		LocalPlayer player = mc.player;
		boolean live = active() && mc.screen == null;
		if (!live) {
			if (attackWasDown || chargeSince >= 0L) {
				// let go mid-charge (a screen opened, the rifle went away): the shot still goes if the rifle is still out
				if (player != null && isOut(player)) {
					ClientPlayNetworking.send(new GreenLanternSniperPayload(GreenLanternSniperPayload.Action.FIRE));
				}
			}
			if (scoped && player != null) {
				ClientPlayNetworking.send(new GreenLanternSniperPayload(GreenLanternSniperPayload.Action.SCOPE_OFF));
			}
			attackWasDown = false;
			scoped = false;
			chargeSince = -1L;
			return;
		}
		boolean attackDown = mc.options.keyAttack.isDown();
		if (attackDown && !attackWasDown) {
			chargeSince = player.level().getGameTime();
			ClientPlayNetworking.send(new GreenLanternSniperPayload(GreenLanternSniperPayload.Action.CHARGE_START));
		} else if (!attackDown && attackWasDown) {
			chargeSince = -1L;
			ClientPlayNetworking.send(new GreenLanternSniperPayload(GreenLanternSniperPayload.Action.FIRE));
		}
		attackWasDown = attackDown;

		boolean useDown = mc.options.keyUse.isDown();
		if (useDown != scoped) {
			scoped = useDown;
			ClientPlayNetworking.send(new GreenLanternSniperPayload(
					useDown ? GreenLanternSniperPayload.Action.SCOPE_ON : GreenLanternSniperPayload.Action.SCOPE_OFF));
		}
	}

	/** Scoped in, first person (the overlay and zoom are first-person only). */
	public static boolean scopedFirstPerson() {
		Minecraft mc = Minecraft.getInstance();
		return scoped && active() && mc.options.getCameraType().isFirstPerson();
	}

	public static boolean scoped() {
		return scoped && active();
	}

	/** 0..1 local charge progress (-1 when not charging). */
	public static float chargeProgress(float partial) {
		Minecraft mc = Minecraft.getInstance();
		if (chargeSince < 0L || mc.level == null) {
			return -1f;
		}
		float held = mc.level.getGameTime() - chargeSince + partial;
		return Math.min(1f, held / GreenLanternConfig.SNIPER_FULL_CHARGE_TICKS);
	}

	/** FOV multiplier the scope wants right now (1 = none). */
	public static float zoomFactor() {
		return scopedFirstPerson() ? GreenLanternConfig.SNIPER_SCOPE_FOV : 1f;
	}

	/** Mouse-look sensitivity multiplier while scoped. */
	public static float sensitivityFactor() {
		return scopedFirstPerson() ? GreenLanternConfig.SNIPER_SCOPE_SENSITIVITY : 1f;
	}
}
