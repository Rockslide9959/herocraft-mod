package com.projecthero.mod.beastboy;

import java.util.UUID;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.data.ExperimentalState;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3 Beast Boy: bridges the universal ability slots to the power. {@link com.projecthero.mod.hero.AbilityRouter} hands him
 * the slots when {@link #hasContext} is true. Shift is read server-side.
 *
 * <pre>
 *   human form (quick morphs, back to human after each)
 *   R  Ram               Shift+R  Pounce
 *   G  Gorilla Smash     Shift+G  Snake Strike
 *   Z  T-Rex Roar        Shift+Z  The Beast
 *   X  Context Morph     Shift+X  Fly Morph
 *   C  Elephant Stomp    Shift+C  Octopus Grab
 *   V  (hold) Animal Wheel -- client side, picks through BeastBoyPickPayload
 *                        Shift+V  Animal Senses
 *
 *   in an animal form: R / G / C are the form's own moves (Shift does the same), X / Shift+X switch to the context
 *   morph / the fly (again = human), V the wheel, Shift+V the senses, Shift+Z The Beast.
 * </pre>
 * H stays the power selector and N does nothing for him.
 */
public final class BeastBoyAbilityManager {
	public static final String RAM = "ram";
	public static final String POUNCE = "pounce";
	public static final String GORILLA_SMASH = "gorilla_smash";
	public static final String SNAKE_STRIKE = "snake_strike";
	public static final String TREX_ROAR = "trex_roar_quick";
	public static final String THE_BEAST = "the_beast";
	public static final String CONTEXT_MORPH = "context_morph";
	public static final String FLY_MORPH = "fly_morph";
	public static final String ELEPHANT_STOMP = "elephant_stomp_quick";
	public static final String OCTOPUS_GRAB = "octopus_grab";
	public static final String ANIMAL_WHEEL = "animal_wheel";
	public static final String ANIMAL_SENSES = "animal_senses";

	private BeastBoyAbilityManager() {
	}

	static void clearSessionState() {
	}

	static void forget(UUID id) {
	}

	public static boolean hasContext(ServerPlayer player) {
		if (!BeastBoy.hasPower(player)) {
			return false;
		}
		ExperimentalState st = player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st == null || st.activePower.isEmpty();
	}

	static void tick(ServerPlayer player) {
	}

	public static void handle(ServerPlayer player, AbilitySlot slot, boolean pressed) {
		if (slot.ordinal() >= AbilitySlot.CORE_COUNT || !pressed) {
			return; // H and N are never moves; nothing acts on release
		}
		if (!player.isAlive() || player.isSpectator()) {
			return;
		}
		String id = moveFor(player, slot, player.isShiftKeyDown());
		if (id == null || ANIMAL_WHEEL.equals(id)) {
			return; // the wheel opens on his own screen
		}
		BeastBoyMoves.use(player, id);
	}

	/** The wheel's pick (or Human). */
	public static boolean pick(ServerPlayer player, BeastForm form) {
		if (!hasContext(player) || !player.isAlive()) {
			return false;
		}
		if (form != BeastForm.HUMAN && !BeastBoyForms.WHEEL.contains(form)) {
			return false;
		}
		if (form == BeastForm.HUMAN) {
			if (BeastBoy.isBeast(player)) {
				return BeastBoy.morph(player, form, 0f); // refused with the message
			}
			if (!BeastBoy.morphed(player)) {
				return false;
			}
			BeastBoy.toHuman(player);
			return true;
		}
		return BeastBoy.morph(player, form, BeastBoyConfig.MORPH_COST);
	}

	/** The move a key sends in {@code form}. Client-safe. */
	public static String moveFor(BeastForm form, AbilitySlot slot, boolean shift) {
		if (form == BeastForm.HUMAN) {
			return switch (slot) {
				case SLOT_1 -> shift ? POUNCE : RAM;
				case SLOT_2 -> shift ? SNAKE_STRIKE : GORILLA_SMASH;
				case SLOT_3 -> shift ? FLY_MORPH : CONTEXT_MORPH;
				case SLOT_4 -> shift ? THE_BEAST : TREX_ROAR;
				case SLOT_5 -> shift ? ANIMAL_SENSES : ANIMAL_WHEEL;
				case SLOT_6 -> shift ? OCTOPUS_GRAB : ELEPHANT_STOMP;
				default -> null;
			};
		}
		String[] m = BeastBoyForms.moves(form);
		return switch (slot) {
			case SLOT_1 -> m[0];
			case SLOT_2 -> m[1];
			case SLOT_6 -> m[2];
			case SLOT_3 -> form == BeastForm.BEAST ? null : shift ? FLY_MORPH : CONTEXT_MORPH;
			case SLOT_4 -> shift && form != BeastForm.BEAST ? THE_BEAST : null;
			case SLOT_5 -> shift ? ANIMAL_SENSES : form == BeastForm.BEAST ? null : ANIMAL_WHEEL;
			default -> null;
		};
	}

	public static String moveFor(Player player, AbilitySlot slot, boolean shift) {
		return moveFor(BeastBoy.form(player), slot, shift);
	}

	/** The HUD's ids per slot: plain, then Shift (either may be null). Client-safe. */
	public static String[] idsOf(Player player, AbilitySlot slot) {
		BeastForm f = BeastBoy.form(player);
		return new String[] { moveFor(f, slot, false), moveFor(f, slot, true) };
	}

	/** Morph Stamina a move costs (the HUD greys one he can't pay for). */
	public static float cost(String id) {
		if (id == null) {
			return 0f;
		}
		return switch (id) {
			case RAM -> BeastBoyConfig.RAM_COST;
			case POUNCE -> BeastBoyConfig.POUNCE_COST;
			case GORILLA_SMASH -> BeastBoyConfig.SMASH_COST;
			case SNAKE_STRIKE -> BeastBoyConfig.SNAKE_COST;
			case TREX_ROAR -> BeastBoyConfig.ROAR_COST;
			case THE_BEAST -> BeastBoyConfig.BEAST_COST;
			case ELEPHANT_STOMP -> BeastBoyConfig.STOMP_COST;
			case OCTOPUS_GRAB -> BeastBoyConfig.OCTO_COST;
			case ANIMAL_SENSES -> BeastBoyConfig.SENSES_COST;
			case CONTEXT_MORPH, FLY_MORPH -> BeastBoyConfig.CONTEXT_MORPH_COST;
			default -> 0f; // the forms' own moves are paid for by the form's drain
		};
	}

	/** Cooldown length of {@code id} in ticks (for the HUD's sweep). */
	public static int maxCooldown(String id) {
		if (id == null) {
			return 1;
		}
		return switch (id) {
			case RAM -> BeastBoyConfig.RAM_COOLDOWN;
			case POUNCE -> BeastBoyConfig.POUNCE_COOLDOWN;
			case GORILLA_SMASH -> BeastBoyConfig.SMASH_COOLDOWN;
			case SNAKE_STRIKE -> BeastBoyConfig.SNAKE_COOLDOWN;
			case TREX_ROAR -> BeastBoyConfig.ROAR_COOLDOWN;
			case THE_BEAST -> BeastBoyConfig.BEAST_COOLDOWN;
			case ELEPHANT_STOMP -> BeastBoyConfig.STOMP_COOLDOWN;
			case OCTOPUS_GRAB -> BeastBoyConfig.OCTO_COOLDOWN;
			case ANIMAL_SENSES -> BeastBoyConfig.SENSES_COOLDOWN;
			case CONTEXT_MORPH, FLY_MORPH -> 10;
			case "trex_bite" -> BeastBoyConfig.TREX_BITE_CD;
			case "trex_tail" -> BeastBoyConfig.TREX_TAIL_CD;
			case "trex_roar" -> BeastBoyConfig.TREX_ROAR_CD;
			case "gorilla_punch" -> BeastBoyConfig.GORILLA_PUNCH_CD;
			case "gorilla_throw" -> BeastBoyConfig.GORILLA_THROW_CD;
			case "gorilla_beat" -> BeastBoyConfig.GORILLA_BEAT_CD;
			case "rhino_charge" -> BeastBoyConfig.RHINO_CHARGE_CD;
			case "elephant_trunk" -> BeastBoyConfig.ELEPHANT_TRUNK_CD;
			case "elephant_stomp" -> BeastBoyConfig.ELEPHANT_STOMP_CD;
			case "elephant_spray" -> BeastBoyConfig.ELEPHANT_SPRAY_CD;
			case "wolf_bite" -> BeastBoyConfig.WOLF_BITE_CD;
			case "wolf_howl" -> BeastBoyConfig.WOLF_HOWL_CD;
			case "bear_maul" -> BeastBoyConfig.BEAR_MAUL_CD;
			case "bear_roar" -> BeastBoyConfig.BEAR_ROAR_CD;
			case "ptero_dive" -> BeastBoyConfig.PTERO_DIVE_CD;
			case "hawk_talon" -> BeastBoyConfig.HAWK_TALON_CD;
			case "hawk_screech" -> BeastBoyConfig.HAWK_SCREECH_CD;
			case "bat_pulse" -> BeastBoyConfig.BAT_PULSE_CD;
			case "shark_bite" -> BeastBoyConfig.SHARK_BITE_CD;
			case "octopus_ink" -> BeastBoyConfig.OCTOPUS_INK_CD;
			case "octopus_grab_form" -> BeastBoyConfig.OCTO_COOLDOWN;
			case "whale_slam" -> BeastBoyConfig.WHALE_SLAM_CD;
			case "beast_swipe" -> BeastBoyConfig.BEAST_SWIPE_COOLDOWN;
			case "beast_leap" -> BeastBoyConfig.BEAST_LEAP_COOLDOWN;
			case "beast_roar" -> BeastBoyConfig.BEAST_ROAR_COOLDOWN;
			default -> 1;
		};
	}

	/** The animal a human-form quick morph flashes into (null for the rest). */
	public static BeastForm quickAnimal(String id) {
		if (id == null) {
			return null;
		}
		return switch (id) {
			case RAM -> BeastForm.RHINO;
			case POUNCE -> BeastForm.TIGER;
			case GORILLA_SMASH -> BeastForm.GORILLA;
			case SNAKE_STRIKE -> BeastForm.SNAKE;
			case TREX_ROAR -> BeastForm.TREX;
			case ELEPHANT_STOMP -> BeastForm.ELEPHANT;
			case OCTOPUS_GRAB -> BeastForm.OCTOPUS;
			default -> null;
		};
	}
}
