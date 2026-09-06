package com.projecthero.mod.punisher;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.data.ExperimentalState;
import com.projecthero.mod.punisher.ability.PunisherAdrenaline;
import com.projecthero.mod.punisher.ability.PunisherC4;
import com.projecthero.mod.punisher.ability.PunisherGrenade;
import com.projecthero.mod.punisher.ability.PunisherRoll;
import com.projecthero.mod.punisher.ability.PunisherSuppressive;
import com.projecthero.mod.punisher.satchel.PunisherSatchel;

import net.minecraft.server.level.ServerPlayer;

/**
 * Bridges the six universal ability slots to the Punisher kit and runs the power's per-player server
 * tick. {@link com.projecthero.mod.hero.AbilityRouter} hands the Punisher the slots when
 * {@link #hasContext} is true (has the power, no experimental mutation selected) -- it sits after
 * Thor / Iron Man / Spider-Man / Max Steel in router priority.
 *
 * <pre>
 *   R (1)  Tactical Satchel     G (2)  Frag Grenade (hold to cook)
 *   X (3)  Tactical Roll        Z (4)  Suppressive Fire
 *   V (5)  Adrenaline           C (6)  Explosive Charge (sneak = detonate)
 * </pre>
 *
 * <p>v0.9.3: R opens the Tactical Satchel (was the Arsenal weapon wheel). {@code PunisherArsenal} is
 * kept but no longer bound to a key.
 */
public final class PunisherAbilityManager {
	/** Ability-2 (grenade) press game-time per player, for the cook gesture. */
	private static final Map<UUID, Long> GRENADE_PRESSED = new ConcurrentHashMap<>();

	private PunisherAbilityManager() {
	}

	public static void clearSessionState() {
		GRENADE_PRESSED.clear();
	}

	public static void onCleanup(UUID id) {
		GRENADE_PRESSED.remove(id);
	}

	public static boolean hasContext(ServerPlayer player) {
		if (!Punisher.hasPower(player)) {
			return false;
		}
		ExperimentalState st = player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st == null || st.activePower.isEmpty();
	}

	public static void handle(ServerPlayer player, AbilitySlot slot, boolean pressed) {
		switch (slot) {
			case SLOT_1 -> {
				if (pressed) {
					PunisherSatchel.open(player);
				}
			}
			case SLOT_2 -> handleGrenade(player, pressed);
			case SLOT_3 -> {
				if (pressed) {
					PunisherRoll.roll(player);
				}
			}
			case SLOT_4 -> {
				if (pressed) {
					PunisherSuppressive.activate(player);
				}
			}
			case SLOT_5 -> {
				if (pressed) {
					PunisherAdrenaline.activate(player);
				}
			}
			case SLOT_6 -> {
				if (pressed) {
					if (player.isShiftKeyDown()) {
						PunisherC4.detonateAll(player);
					} else {
						PunisherC4.place(player);
					}
				}
			}
		}
	}

	private static void handleGrenade(ServerPlayer player, boolean pressed) {
		long now = player.level().getGameTime();
		if (pressed) {
			if (PunisherGrenade.beginCook(player)) {
				GRENADE_PRESSED.put(player.getUUID(), now);
			}
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
			return;
		}
		PunisherPassives.tick(player);
		PunisherRoll.tick(player);

		Long grenadePress = GRENADE_PRESSED.get(player.getUUID());
		if (grenadePress != null
				&& player.level().getGameTime() - grenadePress >= PunisherConfig.GRENADE_MAX_COOK_TICKS) {
			GRENADE_PRESSED.remove(player.getUUID());
			PunisherGrenade.cookOverflow(player);
		}
	}

	public static String abilityIdOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> "satchel";
			case SLOT_2 -> PunisherGrenade.ABILITY;
			case SLOT_3 -> PunisherRoll.ABILITY;
			case SLOT_4 -> PunisherSuppressive.ABILITY;
			case SLOT_5 -> PunisherAdrenaline.ABILITY;
			case SLOT_6 -> PunisherC4.ABILITY;
		};
	}
}
