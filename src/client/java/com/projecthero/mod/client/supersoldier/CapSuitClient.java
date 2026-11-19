package com.projecthero.mod.client.supersoldier;

import com.projecthero.mod.supersoldier.CapShieldSlot;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;

/**
 * v0.17.5 Battle Suit, client input: <b>holding right-click</b> with the slotted shield usable ({@link CapShieldSlot#canGuard}: a
 * throwable shield in the slot, the main hand empty) raises it -- the press / release edges go to the server
 * ({@link CapShieldSlot.GuardPayload}), which re-validates and syncs the flag every viewer poses from. While it is up the attack key does
 * nothing ({@code WolverineAttackMixin}, as for a raised shield). N is in {@code ProjectHeroModClient#handleMaxSteelTransform}.
 */
public final class CapSuitClient {
	/** True while this client has told the server the slotted shield is raised. */
	public static boolean guardSent;

	private CapSuitClient() {
	}

	public static void initialize() {
		ClientTickEvents.END_CLIENT_TICK.register(CapSuitClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> guardSent = false);
	}

	private static void tick(Minecraft mc) {
		boolean want = mc.player != null && mc.screen == null && mc.options.keyUse.isDown() && CapShieldSlot.canGuard(mc.player)
				&& CapShieldSlot.hasSlot(mc.player);
		if (want != guardSent) {
			guardSent = want;
			if (mc.getConnection() != null) {
				ClientPlayNetworking.send(new CapShieldSlot.GuardPayload(want));
			}
		}
	}
}
