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
 * v0.17.1 Dark Shadow revamp ({@code docs/QUIRKS_REVAMP_PROPOSAL.md} 3.4): the keys (Fumikage Tokoyami / Tsukuyomi). X is the
 * movement key, V the mode, R / G / Z / C attacks; N stays Hide (the user's).
 *
 * <pre>
 *   R  Shadow Strike           Shift+R  Dark Fang
 *   G  Shadow Grab             Shift+G  Shadow Shield (held)        hold G past the grab: Shadow Toss (release throws)
 *   X  Shadow Lunge            Shift+X  Fetch                       hold X: Black Fallen Angel (the Abyssal glide)
 *   Z  Shadow Scout (marks)    Shift+Z  Sunshine Ankh
 *   C  Abyssal Rampage         Shift+C  Unleash (controlled Berserk) hold C at 100: Abyssal Black Body
 *   V  Black Ankh (mode)       Shift+V  Light Flare (works while berserk / hidden)
 *   N  hide / call it out (DarkShadowHidePayload)
 * </pre>
 * In Abyssal Black Body R / G / Z / C are its claw dive. Black Ankh always turns off whatever its cooldown. While hidden only
 * Shift+V (the flare) and N work. The moves are in {@link DarkShadowMoves}, the numbers in {@link DarkShadowConfig}.
 */
public final class DarkShadowKit implements QuirkKit {
	@Override
	public void handle(ServerPlayer player, AbilitySlot slot, boolean pressed, boolean shift) {
		if (!pressed) {
			switch (slot) {
				case SLOT_2 -> DarkShadowMoves.gUp(player);
				case SLOT_3 -> DarkShadowMoves.xUp(player);
				default -> {
				}
			}
			return;
		}
		String[] ids = idsOf(slot);
		if (ids.length < 2) {
			return;
		}
		String id = ids[shift ? 1 : 0];
		// Abyssal Black Body: every attack key is the claw dive
		if (DarkShadow.blackBody(player)) {
			if (slot != AbilitySlot.SLOT_3 && slot != AbilitySlot.SLOT_5 && Quirks.ready(player, DarkShadowConfig.DIVE)) {
				DarkShadowMoves.clawDive(player);
			}
			return;
		}
		// toggles always turn off
		if (id.equals(DarkShadowConfig.ANKH) && DarkShadow.ankh(player)) {
			DarkShadowMoves.toggleAnkh(player);
			return;
		}
		if (slot == AbilitySlot.SLOT_3 && !shift && DarkShadow.abyssal(player)) {
			DarkShadowMoves.abyssalOff(player);
			Quirks.setCooldown(player, DarkShadowConfig.ABYSSAL, DarkShadowConfig.get().moves.abyssalCooldown);
			return;
		}
		if (DarkShadow.recalled(player) && !id.equals(DarkShadowConfig.FLARE)) {
			Quirks.say(player, "message.projecthero.dark_shadow.recalled", ChatFormatting.DARK_PURPLE);
			return;
		}
		if (slot == AbilitySlot.SLOT_3 && !shift) {
			DarkShadowMoves.xDown(player); // the tap (Shadow Lunge) waits for the release; the hold is the glide
			return;
		}
		if (!Quirks.ready(player, id)) {
			return;
		}
		switch (id) {
			case DarkShadowConfig.STRIKE -> DarkShadowMoves.strike(player);
			case DarkShadowConfig.FANG -> DarkShadowMoves.fang(player);
			case DarkShadowConfig.GRAB -> DarkShadowMoves.gDown(player);
			case DarkShadowConfig.SHIELD -> DarkShadowMoves.shieldOn(player);
			case DarkShadowConfig.FETCH -> DarkShadowMoves.fetch(player);
			case DarkShadowConfig.SCOUT -> DarkShadowMoves.scout(player);
			case DarkShadowConfig.NOVA -> DarkShadowMoves.nova(player);
			case DarkShadowConfig.ANKH -> DarkShadowMoves.toggleAnkh(player);
			case DarkShadowConfig.RAMPAGE -> DarkShadowMoves.rampage(player);
			case DarkShadowConfig.UNLEASH -> DarkShadowMoves.unleash(player);
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
	public void ultimate(ServerPlayer player) {
		DarkShadowMoves.blackBody(player);
	}

	@Override
	public boolean active(Player player, AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> DarkShadow.running(player, DarkShadow.V_FANG) || DarkShadow.running(player, DarkShadow.V_DASH)
					|| DarkShadow.running(player, DarkShadow.V_DIVE);
			case SLOT_2 -> DarkShadow.shielding(player) || DarkShadow.on(player, DarkShadow.V_GRAB);
			case SLOT_3 -> DarkShadow.abyssal(player) || DarkShadow.on(player, DarkShadow.V_FETCH) || DarkShadow.running(player, DarkShadow.V_LUNGE);
			case SLOT_4 -> DarkShadow.on(player, DarkShadow.V_SCOUT);
			case SLOT_5 -> DarkShadow.ankh(player) || DarkShadow.running(player, DarkShadow.V_FLARE);
			case SLOT_6 -> DarkShadow.berserk(player) || DarkShadow.running(player, DarkShadow.V_RAMPAGE) || DarkShadow.blackBody(player);
			default -> false;
		};
	}

	/** The Light Flare (Shift+V) works while berserk -- it is what calms it. */
	@Override
	public boolean usableWhileOverloaded(AbilitySlot slot, boolean shift) {
		return slot == AbilitySlot.SLOT_5 && shift;
	}

	@Override
	public String[] idsOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> new String[] { DarkShadowConfig.STRIKE, DarkShadowConfig.FANG };
			case SLOT_2 -> new String[] { DarkShadowConfig.GRAB, DarkShadowConfig.SHIELD };
			case SLOT_3 -> new String[] { DarkShadowConfig.LUNGE, DarkShadowConfig.FETCH };
			case SLOT_4 -> new String[] { DarkShadowConfig.SCOUT, DarkShadowConfig.NOVA };
			case SLOT_5 -> new String[] { DarkShadowConfig.ANKH, DarkShadowConfig.FLARE };
			case SLOT_6 -> new String[] { DarkShadowConfig.RAMPAGE, DarkShadowConfig.UNLEASH };
			default -> new String[0];
		};
	}

	/** v0.17.1 (menus): keep G held after a grab = Shadow Toss, hold X = Black Fallen Angel (display ids; the kit times the hold). */
	public static final String HOLD_TOSS = "quirk_dark_shadow_toss";

	@Override
	public String holdIdOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_2 -> HOLD_TOSS;
			case SLOT_3 -> DarkShadowConfig.ABYSSAL;
			default -> null;
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
			case DarkShadowConfig.LUNGE -> m.lungeCooldown;
			case DarkShadowConfig.FETCH -> m.fetchCooldown;
			case DarkShadowConfig.SCOUT -> m.scoutCooldown;
			case DarkShadowConfig.NOVA -> m.novaCooldown;
			case DarkShadowConfig.ANKH -> m.ankhCooldown;
			case DarkShadowConfig.RECALL -> m.recallCooldown;
			case DarkShadowConfig.RAMPAGE -> m.rampageCooldown;
			case DarkShadowConfig.UNLEASH -> m.berserkCooldown;
			case DarkShadowConfig.FLARE -> m.flareCooldown;
			case DarkShadowConfig.DIVE -> m.diveCooldown;
			case DarkShadowConfig.CATCH -> m.catchCooldown;
			default -> 0;
		};
	}
}
