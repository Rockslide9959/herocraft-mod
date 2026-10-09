package com.projecthero.mod.punisher;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.data.ExperimentalState;
import com.projecthero.mod.punisher.ability.PunisherGrenade;
import com.projecthero.mod.punisher.ability.PunisherMark;
import com.projecthero.mod.punisher.ability.PunisherMelee;
import com.projecthero.mod.punisher.ability.PunisherRoll;
import com.projecthero.mod.punisher.ability.PunisherSmoke;
import com.projecthero.mod.punisher.ability.PunisherWarzone;

import net.minecraft.server.level.ServerPlayer;

/**
 * Bridges the six universal ability slots to the Punisher kit and runs the power's per-player server
 * tick. {@link com.projecthero.mod.hero.AbilityRouter} hands the Punisher the slots when
 * {@link #hasContext} is true (has the power, no experimental mutation selected) -- it sits after
 * Thor / Iron Man / Spider-Man / Max Steel in router priority.
 *
 * <p>v0.15.18 kit (Shift = sneaking at the moment the key goes down; every Shift move has its own cooldown):
 * <pre>
 *   R (slot 1)  Target Designation        Shift+R  Threat Assessment
 *   G (slot 2)  Brutal Strike             Shift+G  Breach Kick
 *   Z (slot 4)  Frag Grenade (hold: cook) Shift+Z  Warzone (hold 5 s)
 *   X (slot 3)  Tactical Roll             Shift+X  Tactical Advance
 *   C (slot 6)  Smoke Screen              Shift+C  Flashbang
 *   V (slot 5)  weapon abilities (PunisherWeaponAbilities, a separate change)
 *   N           Tactical Satchel (PunisherActionPayload.OPEN_SATCHEL -- N is not an ability slot, never on the HUD)
 * </pre>
 * With a gun in hand a tap of R reloads, so R's moves need a short hold there (the client's firearm R gesture).
 * Suited as Agent Venom, Sneak+X / Z / V are the Symbiote extras instead -- they shadow Tactical Advance and Warzone.
 */
public final class PunisherAbilityManager {
	/** Z (grenade) press game-time per player, for the cook gesture. */
	private static final Map<UUID, Long> GRENADE_PRESSED = new ConcurrentHashMap<>();

	private PunisherAbilityManager() {
	}

	public static void clearSessionState() {
		GRENADE_PRESSED.clear();
	}

	public static void onCleanup(UUID id) {
		GRENADE_PRESSED.remove(id);
		PunisherWarzone.forget(id);
	}

	public static boolean hasContext(ServerPlayer player) {
		if (!Punisher.hasPower(player)) {
			return false;
		}
		ExperimentalState st = player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st == null || st.activePower.isEmpty();
	}

	public static void handle(ServerPlayer player, AbilitySlot slot, boolean pressed) {
		// v0.13.11: suited as Agent Venom, Sneak + X / Z / V are the Symbiote extras instead
		// (v0.15.18: so while suited they shadow Shift+X Tactical Advance and Shift+Z Warzone)
		if (com.projecthero.mod.symbiote.SymbioteAgentVenomAbilities.handle(player, slot, pressed)) {
			return;
		}
		boolean shift = player.isShiftKeyDown();
		switch (slot) {
			case SLOT_1 -> { // R
				if (pressed) {
					if (shift) {
						PunisherMark.assess(player);
					} else {
						PunisherMark.designate(player);
					}
				}
			}
			case SLOT_2 -> { // G
				if (pressed) {
					if (shift) {
						PunisherMelee.breachKick(player);
					} else {
						PunisherMelee.brutalStrike(player);
					}
				}
			}
			case SLOT_3 -> { // X
				if (pressed) {
					if (shift) {
						PunisherRoll.advance(player);
					} else {
						PunisherRoll.roll(player);
					}
				}
			}
			case SLOT_4 -> handleZ(player, pressed, shift);
			case SLOT_5 -> com.projecthero.mod.punisher.ability.PunisherWeaponAbilities.handle(player, pressed); // v0.15.18: V = Weapon Ability
			case SLOT_6 -> { // C
				if (pressed) {
					if (shift) {
						PunisherSmoke.throwFlashbang(player);
					} else {
						PunisherSmoke.smokeScreen(player);
					}
				}
			}
			default -> {
				// H / N never reach a Hero-Tier power's slots (N opens the satchel through PunisherActionPayload)
			}
		}
	}

	/** Z: tap/hold = Frag Grenade (cook while held, throw on release); Shift held at the press = Warzone (hold 5 s). */
	private static void handleZ(ServerPlayer player, boolean pressed, boolean shift) {
		long now = player.level().getGameTime();
		if (pressed) {
			if (shift) {
				PunisherWarzone.beginCharge(player);
				return;
			}
			if (PunisherGrenade.beginCook(player)) {
				GRENADE_PRESSED.put(player.getUUID(), now);
			}
			return;
		}
		if (PunisherWarzone.charging(player)) {
			PunisherWarzone.cancelCharge(player); // let go before the 5 s were up
			return;
		}
		Long since = GRENADE_PRESSED.remove(player.getUUID());
		if (since == null) {
			return;
		}
		PunisherGrenade.throwGrenade(player, now - since);
	}

	public static void serverTick(ServerPlayer player) {
		if (!Punisher.hasPower(player)) {
			GRENADE_PRESSED.remove(player.getUUID());
			PunisherWarzone.forget(player.getUUID());
			return;
		}
		PunisherPassives.tick(player);
		PunisherRoll.tick(player);
		PunisherWarzone.tickCharge(player);

		Long grenadePress = GRENADE_PRESSED.get(player.getUUID());
		if (grenadePress != null
				&& player.level().getGameTime() - grenadePress >= PunisherConfig.GRENADE_MAX_COOK_TICKS) {
			GRENADE_PRESSED.remove(player.getUUID());
			PunisherGrenade.cookOverflow(player);
		}
	}

	/** The cooldown id of each key's plain move (V: none here -- the weapon abilities are a separate change). */
	public static String abilityIdOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> PunisherMark.ABILITY;
			case SLOT_2 -> PunisherMelee.STRIKE;
			case SLOT_3 -> PunisherRoll.ABILITY;
			case SLOT_4 -> PunisherGrenade.ABILITY;
			case SLOT_6 -> PunisherSmoke.SMOKE;
			default -> "";
		};
	}

	/** v0.15.18: the cooldown id of each key's Shift move, or "" (V, H, N). */
	public static String shiftAbilityIdOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> PunisherMark.THREAT;
			case SLOT_2 -> PunisherMelee.KICK;
			case SLOT_3 -> PunisherRoll.ADVANCE;
			case SLOT_4 -> PunisherWarzone.ABILITY;
			case SLOT_6 -> PunisherSmoke.FLASH;
			default -> "";
		};
	}

	/** Full cooldown length for a cooldown id (the HUD's bar scale), or 0. */
	public static int cooldownTicksOf(String abilityId) {
		return switch (abilityId) {
			case PunisherMark.ABILITY -> PunisherConfig.MARK_COOLDOWN_TICKS;
			case PunisherMark.THREAT -> PunisherConfig.THREAT_COOLDOWN_TICKS;
			case PunisherMelee.STRIKE -> PunisherConfig.BRUTAL_STRIKE_COOLDOWN_TICKS;
			case PunisherMelee.KICK -> PunisherConfig.BREACH_KICK_COOLDOWN_TICKS;
			case PunisherGrenade.ABILITY -> PunisherConfig.GRENADE_COOLDOWN_TICKS;
			case PunisherWarzone.ABILITY -> PunisherConfig.WARZONE_COOLDOWN_TICKS;
			case PunisherRoll.ABILITY -> PunisherConfig.ROLL_COOLDOWN_TICKS;
			case PunisherRoll.ADVANCE -> PunisherConfig.ADVANCE_COOLDOWN_TICKS;
			case PunisherSmoke.SMOKE -> PunisherConfig.SMOKE_COOLDOWN_TICKS;
			case PunisherSmoke.FLASH -> PunisherConfig.FLASHBANG_COOLDOWN_TICKS;
			default -> 0;
		};
	}
}
