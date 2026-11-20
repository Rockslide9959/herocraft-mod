package com.projecthero.mod.client.symbiote;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.network.SymbioteKitPayload;
import com.projecthero.mod.symbiote.Symbiote;
import com.projecthero.mod.symbiote.SymbioteAbilityManager;
import com.projecthero.mod.symbiote.SymbioteState;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;

/**
 * v0.17.5 Symbiote moveset v3, local player:
 * <ul>
 *   <li><b>The Form wheel</b> ({@link #tickWheelHold}, from {@code ProjectHeroModClient}'s key handling, before the ability-key loop):
 *       V held {@link SymbioteAbilityManager#FORM_WHEEL_TICKS} without Sneak opens {@link SymbioteFormWheelScreen}. The generic loop
 *       already sent V's press; once the wheel is open V's release is never sent (the server lets the press lapse, and is told so by
 *       {@link SymbioteKitPayload#WHEEL_OPENED}), and V is ignored until it is physically let go. A shorter tap is sent as usual and
 *       cycles the Form on the server.</li>
 *   <li><b>The crush</b> ({@link #startTick}, before vanilla handles the attack key): holding a Tendril Grab, a left click is swallowed
 *       (no punch on anyone, no block breaking) and sent as {@link SymbioteKitPayload#CRUSH}; the arm swings.</li>
 * </ul>
 */
public final class SymbioteKitClient {
	private static int vHeldTicks;
	private static boolean wheelOpenedThisHold;
	private static boolean awaitVRelease;

	private SymbioteKitClient() {
	}

	public static void initialize() {
		ClientTickEvents.START_CLIENT_TICK.register(SymbioteKitClient::startTick);
	}

	/** May V open the Form wheel for this player? A Normal host whose kit owns the keys (not dormant, not inside Power Armour). */
	static boolean kitOwnsV(LocalPlayer p) {
		return p != null && p.isAlive() && Symbiote.isNormalHost(p) && !Symbiote.dormant(p)
				&& !com.projecthero.mod.powerarmour.PowerArmour.ownsKeys(p);
	}

	/** The V that opened the wheel is still physically down: the ability loop must neither release nor re-press it. */
	public static boolean swallowV() {
		return awaitVRelease;
	}

	/** The wheel opened during this V hold -- the "screen opened" cleanup must not send V's release (it would read as a tap). */
	public static boolean wheelOpenedThisHold() {
		return wheelOpenedThisHold;
	}

	public static void tickWheelHold(Minecraft client) {
		boolean vDown = ModKeyBindings.ABILITY_SLOTS[AbilitySlot.SLOT_5.ordinal()].isDown();
		if (awaitVRelease && !vDown && client.screen == null) {
			awaitVRelease = false;
		}
		boolean eligible = client.screen == null && !awaitVRelease && vDown && !Screen.hasShiftDown()
				&& client.player != null && !client.player.isShiftKeyDown() && kitOwnsV(client.player);
		if (!eligible) {
			vHeldTicks = 0;
			if (client.screen == null && !vDown) {
				wheelOpenedThisHold = false;
			}
			return;
		}
		vHeldTicks++;
		if (!wheelOpenedThisHold && vHeldTicks >= SymbioteAbilityManager.FORM_WHEEL_TICKS) {
			wheelOpenedThisHold = true;
			awaitVRelease = true;
			ClientPlayNetworking.send(new SymbioteKitPayload(SymbioteKitPayload.WHEEL_OPENED, 0));
			client.setScreen(new SymbioteFormWheelScreen());
		}
	}

	private static void startTick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.screen != null || !Symbiote.isNormalHost(p)) {
			return;
		}
		SymbioteState s = p.getAttachedOrElse(ModAttachments.SYMBIOTE_STATE, null);
		if (s == null || !s.tendrilGrabHeld) {
			return;
		}
		boolean clicked = false;
		while (mc.options.keyAttack.consumeClick()) {
			clicked = true;
		}
		mc.options.keyAttack.setDown(false); // no punching the held target (or anyone else) and no block breaking while it is held
		if (clicked) {
			ClientPlayNetworking.send(new SymbioteKitPayload(SymbioteKitPayload.CRUSH, 0));
			p.swing(InteractionHand.MAIN_HAND);
		}
	}
}
