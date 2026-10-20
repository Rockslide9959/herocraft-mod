package com.projecthero.mod.ironman.ability;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.ironman.IronManIntegrity;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.suit.IronManSuit;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * v0.16.1 comic kit input: one key edge on a kit slot (R / G / X / Z / V) of the worn suit.
 * <ul>
 *   <li>On the PRESS the move is chosen -- Shift held = the key's Shift move, else its tap move -- and remembered for that
 *       slot, so the release always reaches the move that was pressed even if Shift changed in between.</li>
 *   <li>A move fires only with its own cooldown ready ({@code <suit>/<move id>}) and the suit not disabled (0 integrity;
 *       the Hulkbuster's Replacement Pod is the one exception -- it is how that suit repairs itself in the field).</li>
 *   <li>The cooldown starts when the move reports it went off ({@link IronManComicMoves#fire}); a HOLD move gets the
 *       release too and decides on which edge that is.</li>
 * </ul>
 * There is no energy: nothing is ever paid.
 */
public final class IronManComicRouter {
	/** player -> slot (1..5) -> the move id pressed on that slot. */
	private static final Map<UUID, String[]> PRESSED = new HashMap<>();

	private IronManComicRouter() {
	}

	public static void clearSessionState() {
		PRESSED.clear();
	}

	public static void forget(UUID player) {
		PRESSED.remove(player);
	}

	/** Drop every remembered press (the suit shut down) -- held moves get their release so they can let go. */
	static void releaseAll(ServerPlayer player) {
		String[] pressed = PRESSED.remove(player.getUUID());
		if (pressed == null) {
			return;
		}
		String suitId = com.projecthero.mod.ironman.IronManArmor.wornSuitId(player);
		IronManSuit suit = suitId == null ? null : com.projecthero.mod.ironman.suit.IronManSuits.byId(suitId);
		for (String id : pressed) {
			IronManComicKit.Move m = id == null ? null : IronManComicKit.byId(id);
			if (m != null && m.input() == IronManComicKit.Input.HOLD && suit != null) {
				IronManComicMoves.fire(player, suit, m, false);
			}
		}
	}

	/** Dispatch one key edge from {@code IronManAbilities.trigger}. */
	public static void trigger(ServerPlayer player, IronManSuit suit, String slotAbilityId, boolean pressed) {
		int slot = IronManComicKit.slotOf(slotAbilityId);
		if (slot == 0) {
			return;
		}
		String[] held = PRESSED.computeIfAbsent(player.getUUID(), k -> new String[7]);
		// v0.16.7 Era I: Shift+C is the emergency eject (hold 3 s), never a move
		boolean eraOne = com.projecthero.mod.ironman.ability.era1.IronManEraOne.isEraOne(suit);
		if (eraOne && slot == 6 && (player.isShiftKeyDown() || held[slot] == null && !pressed)) {
			if (held[slot] == null) {
				com.projecthero.mod.ironman.ability.era1.IronManEraOne.ejectKey(player, suit, pressed);
				return;
			}
		}
		if (!pressed) {
			String id = held[slot];
			held[slot] = null;
			IronManComicKit.Move m = id == null ? null : IronManComicKit.byId(id);
			if (m != null && m.input() == IronManComicKit.Input.HOLD) {
				boolean fired = IronManComicMoves.fire(player, suit, m, false);
				if (fired) {
					startCooldown(player, suit, m);
					// v0.16.7 Era I: a hold move that goes off on the release (unibeam, haymaker, charged repulsor) pays then
					if (eraOne) {
						com.projecthero.mod.ironman.ability.era1.IronManEraOne.pay(player, suit, m);
					}
				}
			}
			return;
		}
		IronManComicKit.Move m = IronManComicKit.move(suit.id(), slot, player.isShiftKeyDown());
		if (m == null) {
			return;
		}
		if (IronManIntegrity.disabled(player, suit.id()) && !IronManComicMoves.worksWhileDisabled(m)) {
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.suit_disabled")
					.withStyle(ChatFormatting.RED), true);
			return;
		}
		if (m.cooldownTicks() > 0 && !TonyStark.abilityReady(player, suit.id(), m.cooldownKey())) {
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.on_cooldown",
					String.format(java.util.Locale.ROOT, "%.1f",
							TonyStark.abilityCooldownRemaining(player, suit.id(), m.cooldownKey()) / 20.0f)), true);
			return;
		}
		if (eraOne && !com.projecthero.mod.ironman.ability.era1.IronManEraOne.canPay(player, suit, m)) {
			return;
		}
		if (m.input() == IronManComicKit.Input.HOLD) {
			held[slot] = m.id();
		}
		boolean fired = IronManComicMoves.fire(player, suit, m, true);
		if (fired) {
			startCooldown(player, suit, m);
			if (eraOne) {
				com.projecthero.mod.ironman.ability.era1.IronManEraOne.pay(player, suit, m);
			}
		}
	}

	/**
	 * Fire a move as if its key was pressed (tap) -- for the gametests: same checks, same cooldown. Returns whether it
	 * went off.
	 */
	public static boolean fireForTests(ServerPlayer player, IronManSuit suit, int slot, boolean shift) {
		IronManComicKit.Move m = IronManComicKit.move(suit.id(), slot, shift);
		if (m == null || IronManIntegrity.disabled(player, suit.id()) && !IronManComicMoves.worksWhileDisabled(m)) {
			return false;
		}
		if (m.cooldownTicks() > 0 && !TonyStark.abilityReady(player, suit.id(), m.cooldownKey())) {
			return false;
		}
		boolean fired = IronManComicMoves.fire(player, suit, m, true);
		if (m.input() == IronManComicKit.Input.HOLD) {
			fired |= IronManComicMoves.fire(player, suit, m, false);
		}
		if (fired) {
			startCooldown(player, suit, m);
		}
		return fired;
	}

	private static void startCooldown(ServerPlayer player, IronManSuit suit, IronManComicKit.Move m) {
		if (m.cooldownTicks() > 0) {
			TonyStark.triggerCooldown(player, suit.id(), m.cooldownKey(), m.cooldownTicks());
		}
	}
}
