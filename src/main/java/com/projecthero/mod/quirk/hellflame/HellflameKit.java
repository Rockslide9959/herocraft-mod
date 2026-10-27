package com.projecthero.mod.quirk.hellflame;

import java.util.UUID;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.quirk.QuirkKit;
import com.projecthero.mod.quirk.Quirks;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7 revamp, v0.17.1 (Quirks revamp): Hellflame's (Endeavor's) six keys, built round Body Heat. User rule (every
 * Quirk): X is the MOVEMENT key, V the UTILITY key, and R, G, Z, C (and their Shift moves) are attacks; H is only the power
 * selector and there is no N.
 * <pre>
 *   key  tap                  Shift                     hold
 *   R    Hell Spider          Hell Spider: Mesh
 *   G    Flashfire Fist       Vanishing Fist            Flashfire Fist: Hellblast (charge, release fires the beam)
 *   X    Jet Burst            Flashfire Fist: Jet Burn  Fire Jet Flight (flies while held)
 *   Z    Hellflame Barrage    Hellflame Pillar
 *   C    Flame Wave           Flame Cloak (12 s)        Plus Ultra (full gauge): Prominence Burn
 *   V    Heat Vent (works overheated)  Fire Wall
 * </pre>
 * On G and X a plain press waits: released within {@link HellflameConfig#HOLD_TICKS} it is the tap move (fired on the
 * release), held past it the hold move starts. Shift moves fire on the press. The moves live in {@link HellflameMoves};
 * every number is in {@link HellflameConfig}.
 */
public final class HellflameKit implements QuirkKit {
	private static boolean hasHold(AbilitySlot slot) {
		return slot == AbilitySlot.SLOT_2 || slot == AbilitySlot.SLOT_3;
	}

	@Override
	public void handle(ServerPlayer player, AbilitySlot slot, boolean pressed, boolean shift) {
		String[] ids = idsOf(slot);
		if (ids.length < 2) {
			return;
		}
		int i = slot.ordinal();
		if (!pressed) {
			if (!hasHold(slot)) {
				return;
			}
			boolean tap = HellflameMoves.keyUp(player, i);
			if (slot == AbilitySlot.SLOT_2) {
				HellflameMoves.releaseHellblast(player);
			} else {
				HellflameMoves.endFlight(player);
			}
			if (tap) {
				fire(player, ids[0]);
			}
			return;
		}
		String id = ids[shift ? 1 : 0];
		// the Cloak can always be put out early
		if (id.equals(HellflameConfig.CLOAK) && HellflameMoves.cloaked(player)) {
			HellflameMoves.toggleCloak(player);
			return;
		}
		if (shift || !hasHold(slot)) {
			fire(player, id);
			return;
		}
		HellflameMoves.keyDown(player, i);
	}

	/** Runs one move by id when it is ready and nothing big is running. */
	static void fire(ServerPlayer player, String id) {
		if (HellflameMoves.busy(player) || !Quirks.ready(player, id) || Hellflame.burntOut(player)
				|| Quirks.overloaded(player) && !id.equals(HellflameConfig.COOL)) {
			return;
		}
		switch (id) {
			case HellflameConfig.BURST -> HellflameMoves.jetBurst(player);
			case HellflameConfig.JET -> HellflameMoves.jetBurn(player);
			case HellflameConfig.FLIGHT -> {
				if (!HellflameMoves.flying(player)) {
					HellflameMoves.toggleFlight(player);
				}
			}
			case HellflameConfig.SPIDER -> HellflameMoves.hellSpider(player);
			case HellflameConfig.MESH -> HellflameMoves.mesh(player);
			case HellflameConfig.FIST -> HellflameMoves.flashfireFist(player);
			case HellflameConfig.VANISH -> HellflameMoves.vanishingFist(player);
			case HellflameConfig.HELLBLAST -> HellflameMoves.startHellblast(player);
			case HellflameConfig.BARRAGE -> HellflameMoves.barrage(player);
			case HellflameConfig.PILLAR -> HellflameMoves.firePillar(player);
			case HellflameConfig.WAVE -> HellflameMoves.flameWave(player);
			case HellflameConfig.CLOAK -> HellflameMoves.toggleCloak(player);
			case HellflameConfig.COOL -> HellflameMoves.coolDown(player);
			case HellflameConfig.WALL -> HellflameMoves.fireWall(player);
			default -> {
			}
		}
	}

	@Override
	public void tick(ServerPlayer player) {
		for (AbilitySlot slot : new AbilitySlot[] { AbilitySlot.SLOT_2, AbilitySlot.SLOT_3 }) {
			if (HellflameMoves.holdDue(player, slot.ordinal())) {
				fire(player, idsOf(slot)[2]);
			}
		}
		HellflameMoves.tick(player);
	}

	@Override
	public void stopAll(ServerPlayer player) {
		HellflameMoves.stopAll(player);
	}

	@Override
	public float modifyIncomingDamage(ServerPlayer player, DamageSource source, float amount) {
		return HellflameMoves.incoming(player, source, amount);
	}

	@Override
	public void clearSessionState() {
		HellflameMoves.clearSessionState();
	}

	@Override
	public void forget(UUID id) {
		HellflameMoves.forget(id);
	}

	// ---------------------------------------------------------------- v0.17.1 framework

	@Override
	public void ultimate(ServerPlayer player) {
		HellflameMoves.prominenceBurn(player);
	}

	@Override
	public boolean active(Player player, AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> Hellflame.meshUp(player);
			case SLOT_2 -> Hellflame.charging(player) || Hellflame.beaming(player) || Hellflame.decoyUp(player);
			case SLOT_3 -> Hellflame.flying(player) || Hellflame.dashing(player) || Hellflame.bursting(player);
			case SLOT_4 -> Hellflame.barraging(player) || Hellflame.pillarActive(player);
			case SLOT_5 -> Hellflame.wallUp(player);
			case SLOT_6 -> Hellflame.cloaked(player) || Hellflame.prominence(player);
			default -> false;
		};
	}

	/** V (Heat Vent) works while overheated -- but not while burnt out after Prominence Burn ({@link #keyLocked}). */
	@Override
	public boolean usableWhileOverloaded(AbilitySlot slot, boolean shift) {
		return slot == AbilitySlot.SLOT_5 && !shift;
	}

	@Override
	public boolean keyLocked(Player player, AbilitySlot slot, boolean shift) {
		return Hellflame.burntOut(player);
	}

	/** [tap, Shift, hold] -- the third (hold) entry only on G and X (the guide and the info screen list it). */
	@Override
	public String[] idsOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> new String[] { HellflameConfig.SPIDER, HellflameConfig.MESH };
			case SLOT_2 -> new String[] { HellflameConfig.FIST, HellflameConfig.VANISH, HellflameConfig.HELLBLAST };
			case SLOT_3 -> new String[] { HellflameConfig.BURST, HellflameConfig.JET, HellflameConfig.FLIGHT };
			case SLOT_4 -> new String[] { HellflameConfig.BARRAGE, HellflameConfig.PILLAR };
			case SLOT_5 -> new String[] { HellflameConfig.COOL, HellflameConfig.WALL };
			case SLOT_6 -> new String[] { HellflameConfig.WAVE, HellflameConfig.CLOAK };
			default -> new String[0];
		};
	}

	@Override
	public int maxCooldown(String abilityId) {
		return switch (abilityId) {
			case HellflameConfig.BURST -> HellflameConfig.BURST_COOLDOWN;
			case HellflameConfig.JET -> HellflameConfig.JET_COOLDOWN;
			case HellflameConfig.FLIGHT -> HellflameConfig.FLIGHT_COOLDOWN;
			case HellflameConfig.SPIDER -> HellflameConfig.SPIDER_COOLDOWN;
			case HellflameConfig.MESH -> HellflameConfig.MESH_COOLDOWN;
			case HellflameConfig.FIST -> HellflameConfig.FIST_COOLDOWN;
			case HellflameConfig.VANISH -> HellflameConfig.VANISH_COOLDOWN;
			case HellflameConfig.HELLBLAST -> HellflameConfig.HELLBLAST_COOLDOWN;
			case HellflameConfig.BARRAGE -> HellflameConfig.BARRAGE_COOLDOWN;
			case HellflameConfig.PILLAR -> HellflameConfig.PILLAR_COOLDOWN;
			case HellflameConfig.WAVE -> HellflameConfig.WAVE_COOLDOWN;
			case HellflameConfig.CLOAK -> HellflameConfig.CLOAK_COOLDOWN;
			case HellflameConfig.COOL -> HellflameConfig.COOL_COOLDOWN;
			case HellflameConfig.WALL -> HellflameConfig.WALL_COOLDOWN;
			default -> 0;
		};
	}
}
