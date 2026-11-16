package com.projecthero.mod.powerarmour.kit;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.powerarmour.ModuleType;
import com.projecthero.mod.powerarmour.PowerArmour;
import com.projecthero.mod.powerarmour.PowerArmourState;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * v0.17.5 Power Armour: the bridge from the universal ability keys to the armour kit. {@code AbilityRouter} hands every key to
 * {@link #handle} while {@link PowerArmour#ownsKeys} -- ahead of every Primary power, so the Primary's keys are suppressed while
 * sealed (its passives keep running). Presses during the enter / exit sequence are swallowed. Shift is read server-side.
 *
 * <pre>
 *   R  Servo Punch (hold: charge, breaks weak blocks)   Shift+R  arm weapon (Gatling / Flamer / Tesla / Rail Cannon / Power Fist)
 *   G  Ground Stomp                                      Shift+G  Servo Grip (Magnetic Clamp: from range); again: throw
 *   Z  Shoulder Charge (Breacher: through blocks)        Shift+Z  Core Overload
 *   X  Jump Jets (Thruster Pack: hold to fly)
 *   C  back module (Missile Pod / Shield / Repair Drone / Sentry Turret / Thruster afterburner)
 *   V  Visor (Ore Scanner, Target Lock)                  Shift+V  hold: open up and step out
 * </pre>
 * An unpowered suit (empty core and bank) only answers Shift+V.
 */
public final class PowerArmourAbilityManager {
	private PowerArmourAbilityManager() {
	}

	public static boolean hasContext(ServerPlayer player) {
		return PowerArmour.ownsKeys(player);
	}

	public static void handle(ServerPlayer player, AbilitySlot slot, boolean pressed) {
		PowerArmourState s = PowerArmour.state(player);
		if (s == null || !s.sealed()) {
			return;
		}
		long now = player.level().getGameTime();
		boolean shift = player.isShiftKeyDown();
		// Shift+V (hold): open up -- works unpowered too
		if (slot == AbilitySlot.SLOT_5 && (shift || s.charge == 3)) {
			if (pressed && shift) {
				s.charge = 3;
				s.chargeStart = now;
			} else if (!pressed && s.charge == 3) {
				s.charge = 0;
			}
			PowerArmour.sync(player);
			return;
		}
		if (!s.suit.powered()) {
			if (pressed) {
				player.displayClientMessage(Component.translatable("message.projecthero.power_armour.no_power").withStyle(ChatFormatting.GRAY), true);
			}
			// a release still ends whatever was held
			if (!pressed) {
				PowerArmourAbilities.release(player, s, slot);
			}
			return;
		}
		switch (slot) {
			case SLOT_1 -> {
				if (pressed) {
					if (shift) {
						PowerArmourWeapons.weaponPress(player, s, now);
					} else {
						PowerArmourAbilities.punchStart(player, s, now);
					}
				} else {
					PowerArmourAbilities.release(player, s, slot);
				}
			}
			case SLOT_2 -> {
				if (pressed) {
					if (shift || s.grabbed >= 0) {
						PowerArmourAbilities.grip(player, s, now);
					} else {
						PowerArmourAbilities.stomp(player, s, now);
					}
				}
			}
			case SLOT_3 -> {
				if (s.suit.has(ModuleType.THRUSTER_PACK)) {
					PowerArmourAbilities.thrust(player, s, pressed);
				} else if (pressed) {
					PowerArmourAbilities.jets(player, s, now);
				}
			}
			case SLOT_4 -> {
				if (pressed) {
					if (shift) {
						PowerArmourAbilities.overload(player, s, now);
					} else {
						PowerArmourAbilities.shoulderCharge(player, s, now);
					}
				}
			}
			case SLOT_5 -> {
				if (pressed) {
					PowerArmourAbilities.visor(player, s);
				}
			}
			case SLOT_6 -> {
				if (pressed) {
					PowerArmourWeapons.backModule(player, s, now);
				}
			}
			default -> {
			}
		}
		PowerArmour.sync(player);
	}
}
