package com.projecthero.mod.quirk.darkshadow;

import java.util.UUID;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.quirk.QuirkKit;
import com.projecthero.mod.quirk.Quirks;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7: Dark Shadow's keys (Fumikage Tokoyami / Tsukuyomi). The moves are in {@link DarkShadowMoves}, every number in
 * {@link DarkShadowConfig}, the synced keys in {@link DarkShadow}. Black Fallen Angel and Black Ankh are toggles (turning
 * one off ignores its cooldown); Shadow Guard is held; Calm (Shift+C) also works while berserk.
 */
public final class DarkShadowKit implements QuirkKit {
	@Override
	public void handle(ServerPlayer player, AbilitySlot slot, boolean pressed, boolean shift) {
		if (!pressed) {
			if (slot == AbilitySlot.SLOT_2) {
				DarkShadowMoves.guardOff(player);
			}
			return;
		}
		String[] ids = idsOf(slot);
		if (ids.length < 2) {
			return;
		}
		String id = ids[shift ? 1 : 0];
		// a toggle always turns off
		if (id.equals(DarkShadowConfig.ANGEL) && DarkShadow.angel(player)) {
			DarkShadowMoves.toggleAngel(player);
			return;
		}
		if (id.equals(DarkShadowConfig.ANKH) && DarkShadow.ankh(player)) {
			DarkShadowMoves.toggleAnkh(player);
			return;
		}
		if (!Quirks.ready(player, id)) {
			return;
		}
		switch (id) {
			case DarkShadowConfig.STRIKE -> DarkShadowMoves.strike(player);
			case DarkShadowConfig.FLURRY -> DarkShadowMoves.flurry(player);
			case DarkShadowConfig.GUARD -> DarkShadowMoves.guardOn(player);
			case DarkShadowConfig.COUNTER -> DarkShadowMoves.counter(player);
			case DarkShadowConfig.ANGEL -> DarkShadowMoves.toggleAngel(player);
			case DarkShadowConfig.DIVE -> DarkShadowMoves.dive(player);
			case DarkShadowConfig.GRAB -> DarkShadowMoves.grab(player);
			case DarkShadowConfig.THROW -> DarkShadowMoves.grabThrow(player);
			case DarkShadowConfig.RAMPAGE -> DarkShadowMoves.rampage(player);
			case DarkShadowConfig.SWALLOW -> DarkShadowMoves.swallow(player);
			case DarkShadowConfig.ANKH -> DarkShadowMoves.toggleAnkh(player);
			case DarkShadowConfig.CALM -> DarkShadowMoves.calm(player);
			default -> {
			}
		}
	}

	@Override
	public void tick(ServerPlayer player) {
		DarkShadowMoves.tick(player);
	}

	@Override
	public void stopAll(ServerPlayer player) {
		DarkShadowMoves.stopAll(player);
	}

	@Override
	public float modifyIncomingDamage(ServerPlayer player, DamageSource source, float amount) {
		return DarkShadowMoves.incoming(player, source, amount);
	}

	@Override
	public void clearSessionState() {
		DarkShadowMoves.clearSessionState();
	}

	@Override
	public void forget(UUID id) {
		DarkShadowMoves.forget(id);
	}

	@Override
	public boolean active(Player player, AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> DarkShadow.running(player, DarkShadow.V_FLURRY);
			case SLOT_2 -> DarkShadow.guarding(player) || DarkShadow.running(player, DarkShadow.V_COUNTER);
			case SLOT_3 -> DarkShadow.angel(player) || DarkShadow.running(player, DarkShadow.V_DIVE);
			case SLOT_4 -> DarkShadow.on(player, DarkShadow.V_GRAB);
			case SLOT_5 -> DarkShadow.rampaging(player) || DarkShadow.on(player, DarkShadow.V_SWALLOW);
			case SLOT_6 -> DarkShadow.ankh(player) || DarkShadow.berserk(player);
			default -> false;
		};
	}

	@Override
	public boolean usableWhileOverloaded(AbilitySlot slot, boolean shift) {
		return slot == AbilitySlot.SLOT_6 && shift;
	}

	@Override
	public String[] idsOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> new String[] { DarkShadowConfig.STRIKE, DarkShadowConfig.FLURRY };
			case SLOT_2 -> new String[] { DarkShadowConfig.GUARD, DarkShadowConfig.COUNTER };
			case SLOT_3 -> new String[] { DarkShadowConfig.ANGEL, DarkShadowConfig.DIVE };
			case SLOT_4 -> new String[] { DarkShadowConfig.GRAB, DarkShadowConfig.THROW };
			case SLOT_5 -> new String[] { DarkShadowConfig.RAMPAGE, DarkShadowConfig.SWALLOW };
			case SLOT_6 -> new String[] { DarkShadowConfig.ANKH, DarkShadowConfig.CALM };
			default -> new String[0];
		};
	}

	@Override
	public int maxCooldown(String abilityId) {
		return switch (abilityId) {
			case DarkShadowConfig.STRIKE -> DarkShadowConfig.STRIKE_COOLDOWN;
			case DarkShadowConfig.FLURRY -> DarkShadowConfig.FLURRY_COOLDOWN;
			case DarkShadowConfig.GUARD -> DarkShadowConfig.GUARD_COOLDOWN;
			case DarkShadowConfig.COUNTER -> DarkShadowConfig.COUNTER_COOLDOWN;
			case DarkShadowConfig.ANGEL -> DarkShadowConfig.ANGEL_COOLDOWN;
			case DarkShadowConfig.DIVE -> DarkShadowConfig.DIVE_COOLDOWN;
			case DarkShadowConfig.GRAB -> DarkShadowConfig.GRAB_COOLDOWN;
			case DarkShadowConfig.THROW -> DarkShadowConfig.THROW_COOLDOWN;
			case DarkShadowConfig.RAMPAGE -> DarkShadowConfig.RAMPAGE_COOLDOWN;
			case DarkShadowConfig.SWALLOW -> DarkShadowConfig.SWALLOW_COOLDOWN;
			case DarkShadowConfig.ANKH -> DarkShadowConfig.ANKH_COOLDOWN;
			case DarkShadowConfig.CALM -> DarkShadowConfig.CALM_COOLDOWN;
			default -> 0;
		};
	}
}
