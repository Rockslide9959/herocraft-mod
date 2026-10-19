package com.projecthero.mod.miraculous.viperion;

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
 * v0.16.7: Viperion's six keys (Sass, the Snake Bracelet, the Lyre).
 * <pre>
 *   R  Lyre Strike          Shift+R  Lyre Sweep
 *   G  Sonic Chord          Shift+G  Resonance
 *   X  Serpent Dash         Shift+X  Coil Leap
 *   Z  Venom Strike         Shift+Z  SECOND CHANCE (starts the countdown)
 *   V  Constrict            Shift+V  String Shield
 *   C  Heat Sense (toggle)  Shift+C  Hiss
 * </pre>
 * The moves live in {@link ViperionMoves}, Second Chance in {@link SecondChance}; every number is in {@link ViperionConfig}.
 */
public final class ViperionKit implements KwamiKit {
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
		if (id.equals(ViperionConfig.SECOND_CHANCE)) {
			// a live checkpoint: Shift+Z rewinds (never on the cooldown); else it sets one when ready
			if (SecondChance.live(player)) {
				SecondChance.rewind(player, false);
			} else if (Miraculous.ready(player, id)) {
				SecondChance.checkpoint(player);
			}
			return;
		}
		if (id.equals(ViperionConfig.HEAT) && Miraculous.state(player).val(Viperion.V_HEAT) != 0L) {
			ViperionMoves.heatSense(player); // off again is never on a cooldown
			return;
		}
		if (!Miraculous.ready(player, id)) {
			return;
		}
		switch (id) {
			case ViperionConfig.STRIKE -> ViperionMoves.strike(player);
			case ViperionConfig.SWEEP -> ViperionMoves.sweep(player);
			case ViperionConfig.CHORD -> ViperionMoves.chord(player);
			case ViperionConfig.RESONANCE -> ViperionMoves.resonance(player);
			case ViperionConfig.DASH -> ViperionMoves.dash(player);
			case ViperionConfig.COIL -> ViperionMoves.coil(player);
			case ViperionConfig.VENOM -> ViperionMoves.venom(player);
			case ViperionConfig.CONSTRICT -> ViperionMoves.constrict(player);
			case ViperionConfig.SHIELD -> ViperionMoves.shield(player);
			case ViperionConfig.HEAT -> ViperionMoves.heatSense(player);
			case ViperionConfig.HISS -> ViperionMoves.hiss(player);
			default -> {
			}
		}
	}

	@Override
	public void tick(ServerPlayer player) {
		if (!Miraculous.transformedAs(player, Kwami.SASS)) {
			return;
		}
		ViperionMoves.tick(player);
		SecondChance.tick(player);
	}

	@Override
	public void onDetransform(ServerPlayer player) {
		ViperionMoves.endAll(player);
		SecondChance.drop(player);
	}

	@Override
	public float modifyIncomingDamage(ServerPlayer player, DamageSource source, float amount) {
		return ViperionMoves.incoming(player, source, amount);
	}

	@Override
	public void clearSessionState() {
		ViperionMoves.clearSessionState();
		SecondChance.clearSessionState();
	}

	@Override
	public void forget(UUID id) {
		ViperionMoves.forget(id);
		SecondChance.forget(id);
	}

	@Override
	public boolean active(Player player, AbilitySlot slot) {
		MiraculousState s = Miraculous.peek(player);
		if (s == null) {
			return false;
		}
		long now = player.level().getGameTime();
		return switch (slot) {
			case SLOT_1 -> s.val(Viperion.V_COMBO) > now;
			case SLOT_3 -> s.val(Viperion.V_DASH) > now || s.val(Viperion.V_LEAP) > now;
			case SLOT_4 -> s.val(Viperion.V_CP_UNTIL) > now;
			case SLOT_5 -> s.val(Viperion.V_CONSTRICT) > now || s.val(Viperion.V_SHIELD) > now;
			case SLOT_6 -> s.val(Viperion.V_HEAT) != 0L || s.val(Viperion.V_HISS) > now;
			default -> false;
		};
	}

	@Override
	public String[] idsOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> new String[] { ViperionConfig.STRIKE, ViperionConfig.SWEEP };
			case SLOT_2 -> new String[] { ViperionConfig.CHORD, ViperionConfig.RESONANCE };
			case SLOT_3 -> new String[] { ViperionConfig.DASH, ViperionConfig.COIL };
			case SLOT_4 -> new String[] { ViperionConfig.VENOM, ViperionConfig.SECOND_CHANCE };
			case SLOT_5 -> new String[] { ViperionConfig.CONSTRICT, ViperionConfig.SHIELD };
			case SLOT_6 -> new String[] { ViperionConfig.HEAT, ViperionConfig.HISS };
			default -> new String[0];
		};
	}

	@Override
	public int maxCooldown(String abilityId) {
		return switch (abilityId) {
			case ViperionConfig.STRIKE -> ViperionConfig.STRIKE_COOLDOWN;
			case ViperionConfig.SWEEP -> ViperionConfig.SWEEP_COOLDOWN;
			case ViperionConfig.CHORD -> ViperionConfig.CHORD_COOLDOWN;
			case ViperionConfig.RESONANCE -> ViperionConfig.RESONANCE_COOLDOWN;
			case ViperionConfig.DASH -> ViperionConfig.DASH_COOLDOWN;
			case ViperionConfig.COIL -> ViperionConfig.COIL_COOLDOWN;
			case ViperionConfig.VENOM -> ViperionConfig.VENOM_COOLDOWN;
			case ViperionConfig.SECOND_CHANCE -> ViperionConfig.SECOND_CHANCE_COOLDOWN;
			case ViperionConfig.CONSTRICT -> ViperionConfig.CONSTRICT_COOLDOWN;
			case ViperionConfig.SHIELD -> ViperionConfig.SHIELD_COOLDOWN;
			case ViperionConfig.HEAT -> ViperionConfig.HEAT_COOLDOWN;
			case ViperionConfig.HISS -> ViperionConfig.HISS_COOLDOWN;
			default -> 0;
		};
	}
}
