package com.projecthero.mod.client.hulk;

import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.hulk.Hulk;
import com.projecthero.mod.hulk.gladiator.GladiatorGear;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;

/**
 * v0.15.3: client side of the Gladiator Gear -- the screen, and the TAP of N as Banner that opens it.
 *
 * <p>N is shared with the calm-down (hold N for 2 s, {@link HulkClient}), which Banner can use too. So the N chain in
 * {@code ProjectHeroModClient.handleMaxSteelTransform} only <em>claims</em> the press for a Banner ({@link #pressed});
 * the screen is asked for when N comes back up within {@link #TAP_TICKS} -- a long hold stays a calm-down. As the Hulk
 * the gear is locked and N is only the calm-down.
 */
public final class GladiatorGearClient {
	/** A press shorter than this (ticks) is a tap. */
	public static final int TAP_TICKS = 8;

	private static int heldTicks = -1;

	private GladiatorGearClient() {
	}

	public static void initialize() {
		MenuScreens.register(GladiatorGear.MENU, GladiatorGearScreen::new);
		ClientTickEvents.END_CLIENT_TICK.register(GladiatorGearClient::tick);
	}

	/** Whether a fresh N press belongs to the Gladiator Gear: a Gamma player as Banner, no screen open. */
	public static boolean ownsN(Minecraft client) {
		return client.player != null && client.screen == null && Hulk.hasPower(client.player) && !Hulk.isHulk(client.player);
	}

	/** N went down as Banner (from the N chain): start timing the tap. */
	public static void pressed() {
		heldTicks = 0;
	}

	private static void tick(Minecraft client) {
		if (heldTicks < 0) {
			return;
		}
		if (client.player == null || client.screen != null || Hulk.isHulk(client.player)) {
			heldTicks = -1;
			return;
		}
		if (ModKeyBindings.MAX_STEEL_TRANSFORM.isDown()) {
			heldTicks++;
			if (heldTicks >= TAP_TICKS) {
				heldTicks = -1; // a hold: the calm-down's
			}
			return;
		}
		heldTicks = -1;
		ClientPlayNetworking.send(new GladiatorGear.OpenPayload());
	}
}
