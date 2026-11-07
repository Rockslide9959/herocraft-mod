package com.projecthero.mod.miraculous.ryuko;

import java.util.UUID;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.KwamiKit;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.data.MiraculousState;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3: Ryuko's six keys (Longg, the Dragon Choker, the sword).
 * <pre>
 *   R  Blade Combo        Shift+R  Fencing Lunge
 *   G  Element Strike     Shift+G  Element Burst
 *   X  Element Dash       Shift+X  Storm Rise
 *   Z  Sword Throw        Shift+Z  DRAGON FORM (starts the countdown)
 *   V  Switch Element     Shift+V  Storm Guard
 *   C  Parry              Shift+C  Riposte
 * </pre>
 * G and X change with her element (V). The moves live in {@link RyukoMoves}; every number is in {@link RyukoConfig}.
 */
public final class RyukoKit implements KwamiKit {
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
		if (Ryuko.inForm(player)) {
			// in the form she is the storm: her keys rest, and the same key ends it early
			if (id.equals(RyukoConfig.FORM)) {
				RyukoMoves.endForm(player);
			}
			return;
		}
		if (!Miraculous.ready(player, id)) {
			return;
		}
		switch (id) {
			case RyukoConfig.STRIKE -> RyukoMoves.strike(player);
			case RyukoConfig.LUNGE -> RyukoMoves.lunge(player);
			case RyukoConfig.ELEMENT_STRIKE -> RyukoMoves.elementStrike(player);
			case RyukoConfig.ELEMENT_BURST -> RyukoMoves.elementBurst(player);
			case RyukoConfig.ELEMENT_DASH -> RyukoMoves.elementDash(player);
			case RyukoConfig.RISE -> RyukoMoves.rise(player);
			case RyukoConfig.THROW -> RyukoMoves.swordThrow(player);
			case RyukoConfig.FORM -> RyukoMoves.dragonForm(player);
			case RyukoConfig.SWITCH -> RyukoMoves.switchElement(player);
			case RyukoConfig.GUARD -> RyukoMoves.guard(player);
			case RyukoConfig.PARRY -> RyukoMoves.parry(player);
			case RyukoConfig.RIPOSTE -> RyukoMoves.riposte(player);
			default -> {
			}
		}
	}

	@Override
	public void tick(ServerPlayer player) {
		if (Miraculous.transformedAs(player, Kwami.LONGG)) {
			RyukoMoves.tick(player);
		}
	}

	@Override
	public void onDetransform(ServerPlayer player) {
		RyukoMoves.endAll(player);
	}

	@Override
	public float modifyIncomingDamage(ServerPlayer player, DamageSource source, float amount) {
		return RyukoMoves.incoming(player, source, amount);
	}

	@Override
	public void clearSessionState() {
		RyukoMoves.clearSessionState();
	}

	@Override
	public void forget(UUID id) {
		RyukoMoves.forget(id);
	}

	@Override
	public boolean active(Player player, AbilitySlot slot) {
		MiraculousState s = Miraculous.peek(player);
		if (s == null) {
			return false;
		}
		long now = player.level().getGameTime();
		return switch (slot) {
			case SLOT_1 -> s.val(Ryuko.V_COMBO_UNTIL) > now || s.val(Ryuko.V_LUNGE) > now;
			case SLOT_2 -> s.val(Ryuko.V_WHIRL) > now;
			case SLOT_3 -> s.val(Ryuko.V_SLIDE) > now || s.val(Ryuko.V_GLIDE) > now;
			case SLOT_4 -> s.val(Ryuko.V_THROW) > now || s.val(Ryuko.V_FORM) > now;
			case SLOT_5 -> s.val(Ryuko.V_GUARD) > now;
			case SLOT_6 -> s.val(Ryuko.V_PARRY) > now || s.val(Ryuko.V_RIPOSTE) > now;
			default -> false;
		};
	}

	@Override
	public String[] idsOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> new String[] { RyukoConfig.STRIKE, RyukoConfig.LUNGE };
			case SLOT_2 -> new String[] { RyukoConfig.ELEMENT_STRIKE, RyukoConfig.ELEMENT_BURST };
			case SLOT_3 -> new String[] { RyukoConfig.ELEMENT_DASH, RyukoConfig.RISE };
			case SLOT_4 -> new String[] { RyukoConfig.THROW, RyukoConfig.FORM };
			case SLOT_5 -> new String[] { RyukoConfig.SWITCH, RyukoConfig.GUARD };
			case SLOT_6 -> new String[] { RyukoConfig.PARRY, RyukoConfig.RIPOSTE };
			default -> new String[0];
		};
	}

	@Override
	public int maxCooldown(String abilityId) {
		return switch (abilityId) {
			case RyukoConfig.STRIKE -> RyukoConfig.STRIKE_COOLDOWN;
			case RyukoConfig.LUNGE -> RyukoConfig.LUNGE_COOLDOWN;
			case RyukoConfig.ELEMENT_STRIKE -> RyukoConfig.STRIKE_ELEMENT_COOLDOWN;
			case RyukoConfig.ELEMENT_BURST -> RyukoConfig.BURST_COOLDOWN;
			case RyukoConfig.ELEMENT_DASH -> RyukoConfig.DASH_COOLDOWN;
			case RyukoConfig.RISE -> RyukoConfig.RISE_COOLDOWN;
			case RyukoConfig.THROW -> RyukoConfig.THROW_COOLDOWN;
			case RyukoConfig.FORM -> RyukoConfig.FORM_COOLDOWN;
			case RyukoConfig.SWITCH -> RyukoConfig.SWITCH_COOLDOWN;
			case RyukoConfig.GUARD -> RyukoConfig.GUARD_COOLDOWN;
			case RyukoConfig.PARRY -> RyukoConfig.PARRY_COOLDOWN;
			case RyukoConfig.RIPOSTE -> RyukoConfig.RIPOSTE_COOLDOWN;
			default -> 0;
		};
	}
}
