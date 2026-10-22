package com.projecthero.mod.quirk.hellflame;

import java.util.UUID;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.quirk.QuirkKit;
import com.projecthero.mod.quirk.Quirks;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7 revamp: Hellflame's (Endeavor's) six keys, built round Body Heat. User rule (every Quirk): X is the MOVEMENT key,
 * V the UTILITY / mode key, and R, G, Z, C (and their Shift moves) are mainly attacks.
 * <pre>
 *   R  Hell Spider                     Shift+R  Hell Spider: Mesh
 *   G  Prominence Burn (hold)          Shift+G  Vanishing Fist
 *   X  Fire Jet Flight (hold)          Shift+X  Flashfire Fist: Jet Burn
 *   Z  Hellflame Barrage               Shift+Z  Fire Pillar
 *   V  Cool Down (works overheated)    Shift+V  Plus Ultra: Full Power
 *   C  Fire Wall                       Shift+C  Flame Cloak (toggle)
 * </pre>
 * The moves live in {@link HellflameMoves}; every number is in {@link HellflameConfig}. The held moves (the beam on G, the
 * flight on X) end on the key's release whatever Shift is doing by then; the Cloak can always be switched off.
 */
public final class HellflameKit implements QuirkKit {
	@Override
	public void handle(ServerPlayer player, AbilitySlot slot, boolean pressed, boolean shift) {
		if (!pressed) {
			if (slot == AbilitySlot.SLOT_2) {
				HellflameMoves.releaseProminence(player);
			} else if (slot == AbilitySlot.SLOT_3) {
				HellflameMoves.endFlight(player);
			}
			return;
		}
		String[] ids = idsOf(slot);
		if (ids.length < 2) {
			return;
		}
		String id = ids[shift ? 1 : 0];
		// the Cloak can always be switched off
		if (id.equals(HellflameConfig.CLOAK) && HellflameMoves.cloaked(player)) {
			HellflameMoves.toggleCloak(player);
			return;
		}
		if (id.equals(HellflameConfig.FLIGHT) && HellflameMoves.flying(player)) {
			return; // already flying (held)
		}
		if (!Quirks.ready(player, id)) {
			return;
		}
		switch (id) {
			case HellflameConfig.JET -> HellflameMoves.jetBurn(player);
			case HellflameConfig.FLIGHT -> HellflameMoves.toggleFlight(player);
			case HellflameConfig.SPIDER -> HellflameMoves.hellSpider(player);
			case HellflameConfig.MESH -> HellflameMoves.mesh(player);
			case HellflameConfig.WALL -> HellflameMoves.fireWall(player);
			case HellflameConfig.CLOAK -> HellflameMoves.toggleCloak(player);
			case HellflameConfig.BARRAGE -> HellflameMoves.barrage(player);
			case HellflameConfig.PILLAR -> HellflameMoves.firePillar(player);
			case HellflameConfig.PROMINENCE -> HellflameMoves.startProminence(player);
			case HellflameConfig.VANISH -> HellflameMoves.vanishingFist(player);
			case HellflameConfig.COOL -> HellflameMoves.coolDown(player);
			case HellflameConfig.PLUS -> HellflameMoves.plusUltraStart(player);
			default -> {
			}
		}
	}

	@Override
	public void tick(ServerPlayer player) {
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

	@Override
	public boolean active(Player player, AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> Hellflame.meshUp(player);
			case SLOT_2 -> Hellflame.charging(player) || Hellflame.beaming(player) || Hellflame.decoyUp(player);
			case SLOT_3 -> Hellflame.flying(player) || Hellflame.dashing(player);
			case SLOT_4 -> Hellflame.barraging(player) || Hellflame.pillarActive(player);
			case SLOT_5 -> Hellflame.plusUltra(player);
			case SLOT_6 -> Hellflame.wallUp(player) || Hellflame.cloaked(player);
			default -> false;
		};
	}

	/** V (Cool Down) works while overheated -- but not while burnt out after Plus Ultra ({@link #keyLocked}). */
	@Override
	public boolean usableWhileOverloaded(AbilitySlot slot, boolean shift) {
		return slot == AbilitySlot.SLOT_5 && !shift;
	}

	@Override
	public boolean keyLocked(Player player, AbilitySlot slot, boolean shift) {
		return Hellflame.burntOut(player);
	}

	@Override
	public String[] idsOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> new String[] { HellflameConfig.SPIDER, HellflameConfig.MESH };
			case SLOT_2 -> new String[] { HellflameConfig.PROMINENCE, HellflameConfig.VANISH };
			case SLOT_3 -> new String[] { HellflameConfig.FLIGHT, HellflameConfig.JET };
			case SLOT_4 -> new String[] { HellflameConfig.BARRAGE, HellflameConfig.PILLAR };
			case SLOT_5 -> new String[] { HellflameConfig.COOL, HellflameConfig.PLUS };
			case SLOT_6 -> new String[] { HellflameConfig.WALL, HellflameConfig.CLOAK };
			default -> new String[0];
		};
	}

	@Override
	public int maxCooldown(String abilityId) {
		return switch (abilityId) {
			case HellflameConfig.JET -> HellflameConfig.JET_COOLDOWN;
			case HellflameConfig.FLIGHT -> HellflameConfig.FLIGHT_COOLDOWN;
			case HellflameConfig.SPIDER -> HellflameConfig.SPIDER_COOLDOWN;
			case HellflameConfig.MESH -> HellflameConfig.MESH_COOLDOWN;
			case HellflameConfig.WALL -> HellflameConfig.WALL_COOLDOWN;
			case HellflameConfig.CLOAK -> HellflameConfig.CLOAK_COOLDOWN;
			case HellflameConfig.BARRAGE -> HellflameConfig.BARRAGE_COOLDOWN;
			case HellflameConfig.PILLAR -> HellflameConfig.PILLAR_COOLDOWN;
			case HellflameConfig.PROMINENCE -> HellflameConfig.PROMINENCE_COOLDOWN;
			case HellflameConfig.VANISH -> HellflameConfig.VANISH_COOLDOWN;
			case HellflameConfig.COOL -> HellflameConfig.COOL_COOLDOWN;
			case HellflameConfig.PLUS -> HellflameConfig.PLUS_COOLDOWN;
			default -> 0;
		};
	}
}
