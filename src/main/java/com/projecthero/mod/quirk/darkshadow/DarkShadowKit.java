package com.projecthero.mod.quirk.darkshadow;

import java.util.UUID;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.quirk.QuirkKit;
import com.projecthero.mod.quirk.Quirks;

import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7 (user) Quirk revamp: Dark Shadow's keys (Fumikage Tokoyami / Tsukuyomi). The user's rule for every Quirk: X is
 * the movement move, V the mode, R / G / Z / C (and their Shifts) mainly attacks.
 *
 * <pre>
 *   R  Shadow Strike / Shift+R Dark Fang        G  Shadow Grab / Shift+G Shadow Shield (hold)
 *   X  Abyssal Black Ankh / Shift+X Fetch       Z  Shadow Scout / Shift+Z Sunshine Ankh
 *   V  Black Ankh / Shift+V Recall              C  Berserk / Shift+C Light Flare (works while berserk)
 * </pre>
 * Toggles (Black Ankh, Abyssal, Recall) always turn off whatever their cooldown. While recalled only Shift+V (call it
 * back) and Shift+C (the flare) work. The moves are in {@link DarkShadowMoves}, the numbers in {@link DarkShadowConfig}.
 */
public final class DarkShadowKit implements QuirkKit {
	@Override
	public void handle(ServerPlayer player, AbilitySlot slot, boolean pressed, boolean shift) {
		if (!pressed) {
			if (slot == AbilitySlot.SLOT_2) {
				DarkShadowMoves.shieldOff(player);
			}
			return;
		}
		String[] ids = idsOf(slot);
		if (ids.length < 2) {
			return;
		}
		String id = ids[shift ? 1 : 0];
		// toggles always turn off
		if (id.equals(DarkShadowConfig.ANKH) && DarkShadow.ankh(player)) {
			DarkShadowMoves.toggleAnkh(player);
			return;
		}
		if (id.equals(DarkShadowConfig.ABYSSAL) && DarkShadow.abyssal(player)) {
			DarkShadowMoves.toggleAbyssal(player);
			return;
		}
		if (id.equals(DarkShadowConfig.RECALL) && DarkShadow.recalled(player)) {
			DarkShadowMoves.toggleRecall(player);
			return;
		}
		if (DarkShadow.recalled(player) && !id.equals(DarkShadowConfig.FLARE) && !id.equals(DarkShadowConfig.RECALL)) {
			Quirks.say(player, "message.projecthero.dark_shadow.recalled", ChatFormatting.DARK_PURPLE);
			return;
		}
		if (!Quirks.ready(player, id)) {
			return;
		}
		switch (id) {
			case DarkShadowConfig.STRIKE -> DarkShadowMoves.strike(player);
			case DarkShadowConfig.FANG -> DarkShadowMoves.fang(player);
			case DarkShadowConfig.GRAB -> DarkShadowMoves.grab(player);
			case DarkShadowConfig.SHIELD -> DarkShadowMoves.shieldOn(player);
			case DarkShadowConfig.ABYSSAL -> DarkShadowMoves.toggleAbyssal(player);
			case DarkShadowConfig.FETCH -> DarkShadowMoves.fetch(player);
			case DarkShadowConfig.SCOUT -> DarkShadowMoves.scout(player);
			case DarkShadowConfig.NOVA -> DarkShadowMoves.nova(player);
			case DarkShadowConfig.ANKH -> DarkShadowMoves.toggleAnkh(player);
			case DarkShadowConfig.RECALL -> DarkShadowMoves.toggleRecall(player);
			case DarkShadowConfig.BERSERK -> DarkShadowMoves.unleash(player);
			case DarkShadowConfig.FLARE -> DarkShadowMoves.flare(player);
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
			case SLOT_1 -> DarkShadow.running(player, DarkShadow.V_FANG) || DarkShadow.running(player, DarkShadow.V_DASH);
			case SLOT_2 -> DarkShadow.shielding(player) || DarkShadow.on(player, DarkShadow.V_GRAB);
			case SLOT_3 -> DarkShadow.abyssal(player) || DarkShadow.on(player, DarkShadow.V_FETCH);
			case SLOT_4 -> DarkShadow.on(player, DarkShadow.V_SCOUT);
			case SLOT_5 -> DarkShadow.ankh(player) || DarkShadow.recalled(player);
			case SLOT_6 -> DarkShadow.berserk(player) || DarkShadow.running(player, DarkShadow.V_FLARE);
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
			case SLOT_1 -> new String[] { DarkShadowConfig.STRIKE, DarkShadowConfig.FANG };
			case SLOT_2 -> new String[] { DarkShadowConfig.GRAB, DarkShadowConfig.SHIELD };
			case SLOT_3 -> new String[] { DarkShadowConfig.ABYSSAL, DarkShadowConfig.FETCH };
			case SLOT_4 -> new String[] { DarkShadowConfig.SCOUT, DarkShadowConfig.NOVA };
			case SLOT_5 -> new String[] { DarkShadowConfig.ANKH, DarkShadowConfig.RECALL };
			case SLOT_6 -> new String[] { DarkShadowConfig.BERSERK, DarkShadowConfig.FLARE };
			default -> new String[0];
		};
	}

	@Override
	public int maxCooldown(String abilityId) {
		DarkShadowConfig.Moves m = DarkShadowConfig.get().moves;
		return switch (abilityId) {
			case DarkShadowConfig.STRIKE -> m.strikeCooldown;
			case DarkShadowConfig.FANG -> m.fangCooldown;
			case DarkShadowConfig.GRAB -> m.grabCooldown;
			case DarkShadowConfig.SHIELD -> m.shieldCooldown;
			case DarkShadowConfig.ABYSSAL -> m.abyssalCooldown;
			case DarkShadowConfig.FETCH -> m.fetchCooldown;
			case DarkShadowConfig.SCOUT -> m.scoutCooldown;
			case DarkShadowConfig.NOVA -> m.novaCooldown;
			case DarkShadowConfig.ANKH -> m.ankhCooldown;
			case DarkShadowConfig.RECALL -> m.recallCooldown;
			case DarkShadowConfig.BERSERK -> m.berserkCooldown;
			case DarkShadowConfig.FLARE -> m.flareCooldown;
			default -> 0;
		};
	}
}
