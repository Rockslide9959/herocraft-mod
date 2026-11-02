package com.projecthero.mod.miraculous.queenbee;

import java.util.UUID;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.KwamiKit;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.data.MiraculousState;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.2: Queen Bee's six keys (Pollen, the Bee Comb, the spinning top).
 * <pre>
 *   R  Top Strike            Shift+R  Top Throw
 *   G  Spinning Shield       Shift+G  Top Tangle
 *   X  Top Swing             Shift+X  Top Copter (toggle)
 *   Z  Stinger Dive          Shift+Z  VENOM (starts the countdown)
 *   V  Hive Sense (toggle)   Shift+V  Queen's Command
 *   C  Sting                 Shift+C  Stinger Volley
 * </pre>
 * The moves live in {@link QueenBeeMoves}; every number is in {@link QueenBeeConfig}.
 */
public final class QueenBeeKit implements KwamiKit {
	@Override
	public void handle(ServerPlayer player, AbilitySlot slot, boolean pressed, boolean shift) {
		if (!pressed) {
			return;
		}
		String[] ids = idsOf(slot);
		if (ids.length < 2) {
			return;
		}
		String id = ids[shift ? 1 : 0];
		MiraculousState st = Miraculous.state(player);
		if (id.equals(QueenBeeConfig.VENOM)) {
			// armed: the second press is a touch (never on the cooldown)
			QueenBeeMoves.venom(player);
			return;
		}
		// switching a toggle off is never on a cooldown
		if (id.equals(QueenBeeConfig.HIVE) && st.val(QueenBee.V_HIVE) != 0L) {
			QueenBeeMoves.hive(player);
			return;
		}
		if (id.equals(QueenBeeConfig.COPTER) && st.val(QueenBee.V_COPTER) != 0L) {
			QueenBeeMoves.copter(player);
			return;
		}
		if (!Miraculous.ready(player, id)) {
			return;
		}
		switch (id) {
			case QueenBeeConfig.STRIKE -> QueenBeeMoves.strike(player);
			case QueenBeeConfig.THROW -> {
				if (st.val(QueenBee.V_THROW) == 0L) {
					QueenBeeMoves.topThrow(player);
				}
			}
			case QueenBeeConfig.SHIELD -> QueenBeeMoves.shield(player);
			case QueenBeeConfig.TANGLE -> QueenBeeMoves.tangle(player);
			case QueenBeeConfig.SWING -> QueenBeeMoves.topSwing(player);
			case QueenBeeConfig.COPTER -> QueenBeeMoves.copter(player);
			case QueenBeeConfig.DIVE -> QueenBeeMoves.dive(player);
			case QueenBeeConfig.HIVE -> QueenBeeMoves.hive(player);
			case QueenBeeConfig.COMMAND -> QueenBeeMoves.command(player);
			case QueenBeeConfig.STING -> QueenBeeMoves.sting(player);
			case QueenBeeConfig.VOLLEY -> QueenBeeMoves.volley(player);
			default -> {
			}
		}
	}

	@Override
	public void tick(ServerPlayer player) {
		if (!Miraculous.transformedAs(player, Kwami.POLLEN)) {
			return;
		}
		QueenBeeMoves.tick(player);
	}

	@Override
	public void onDetransform(ServerPlayer player) {
		QueenBeeMoves.endAll(player);
	}

	@Override
	public void clearSessionState() {
		QueenBeeMoves.clearSessionState();
	}

	@Override
	public void forget(UUID id) {
		QueenBeeMoves.forget(id);
	}

	@Override
	public boolean active(Player player, AbilitySlot slot) {
		MiraculousState s = Miraculous.peek(player);
		if (s == null) {
			return false;
		}
		long now = player.level().getGameTime();
		return switch (slot) {
			case SLOT_1 -> s.val(QueenBee.V_COMBO) > now || s.val(QueenBee.V_THROW) > now;
			case SLOT_2 -> s.val(QueenBee.V_SHIELD) > now || s.val(QueenBee.V_TANGLE) > now;
			case SLOT_3 -> s.val(QueenBee.V_SWING) > now || s.val(QueenBee.V_COPTER) != 0L;
			case SLOT_4 -> s.val(QueenBee.V_DIVE) != 0L || s.val(QueenBee.V_VENOM) > now;
			case SLOT_5 -> s.val(QueenBee.V_HIVE) != 0L;
			case SLOT_6 -> s.val(QueenBee.V_VOLLEY) > now;
			default -> false;
		};
	}

	@Override
	public String[] idsOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> new String[] { QueenBeeConfig.STRIKE, QueenBeeConfig.THROW };
			case SLOT_2 -> new String[] { QueenBeeConfig.SHIELD, QueenBeeConfig.TANGLE };
			case SLOT_3 -> new String[] { QueenBeeConfig.SWING, QueenBeeConfig.COPTER };
			case SLOT_4 -> new String[] { QueenBeeConfig.DIVE, QueenBeeConfig.VENOM };
			case SLOT_5 -> new String[] { QueenBeeConfig.HIVE, QueenBeeConfig.COMMAND };
			case SLOT_6 -> new String[] { QueenBeeConfig.STING, QueenBeeConfig.VOLLEY };
			default -> new String[0];
		};
	}

	@Override
	public int maxCooldown(String abilityId) {
		return switch (abilityId) {
			case QueenBeeConfig.STRIKE -> QueenBeeConfig.STRIKE_COOLDOWN;
			case QueenBeeConfig.THROW -> QueenBeeConfig.THROW_COOLDOWN;
			case QueenBeeConfig.SHIELD -> QueenBeeConfig.SHIELD_COOLDOWN;
			case QueenBeeConfig.TANGLE -> QueenBeeConfig.TANGLE_COOLDOWN;
			case QueenBeeConfig.SWING -> QueenBeeConfig.SWING_COOLDOWN;
			case QueenBeeConfig.COPTER -> QueenBeeConfig.COPTER_COOLDOWN;
			case QueenBeeConfig.DIVE -> QueenBeeConfig.DIVE_COOLDOWN;
			case QueenBeeConfig.VENOM -> QueenBeeConfig.VENOM_COOLDOWN;
			case QueenBeeConfig.HIVE -> QueenBeeConfig.HIVE_COOLDOWN;
			case QueenBeeConfig.COMMAND -> QueenBeeConfig.COMMAND_COOLDOWN;
			case QueenBeeConfig.STING -> QueenBeeConfig.STING_COOLDOWN;
			case QueenBeeConfig.VOLLEY -> QueenBeeConfig.VOLLEY_COOLDOWN;
			default -> 0;
		};
	}
}
