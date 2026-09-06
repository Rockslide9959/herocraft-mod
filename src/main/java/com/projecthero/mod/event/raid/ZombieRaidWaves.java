package com.projecthero.mod.event.raid;

import java.util.List;

import com.projecthero.mod.event.WaveDefinition;
import com.projecthero.mod.event.entity.RaidEntityTypes;
import com.projecthero.mod.event.entity.RaidZombie;

import net.minecraft.world.entity.Mob;

/**
 * The twelve waves, as data.
 *
 * <h2>Difficulty by composition, not by health bars</h2>
 * Not one entry here scales a mob's health with the wave number. Every step up in difficulty comes
 * from what is in the wave and how those things interact -- which matters far more than usual in this
 * mod, where a player may arrive flying, in powered armour, with area attacks. The progression is:
 *
 * <ol>
 *   <li>plain frontline -- learn the arena;</li>
 *   <li>+ babies -- you cannot stand still;</li>
 *   <li>+ armoured -- you cannot only shoot;</li>
 *   <li><b>boss</b> over a mixed horde -- the first real fight;</li>
 *   <li>+ sword skeletons -- something chases the ranged player specifically;</li>
 *   <li>+ acid -- ground you were holding becomes ground you cannot hold;</li>
 *   <li>juggernauts + armoured -- a wall that has to be dealt with, slowly, under pressure;</li>
 *   <li><b>boss</b> over a large horde;</li>
 *   <li>acid + skeletons + frontline -- ranged denial and melee pressure at once;</li>
 *   <li>juggernauts + babies + mixed -- the fastest and the slowest threats simultaneously;</li>
 *   <li>everything -- the survival wave;</li>
 *   <li><b>final boss</b> over an elite mix.</li>
 * </ol>
 *
 * <p>v0.9.9 doubled every wave's size and re-cut the compositions -- the numbers below are the solo
 * baseline (12, 18, 16, 20+boss, 18, 20, 14, 34+boss, 26, 30, 52, 34+boss). Counts are still
 * per-participant-scaled at spawn time by
 * {@link com.projecthero.mod.event.EventConfig.ZombieRaid#mobCountPerExtraPlayer} (lowered to 0.30 in
 * the same pass so a full group is not buried). The Juggernaut entries opt out of that scaling (a negative per-player value)
 * -- doubling the number of 120-HP chargers for a duo is a much bigger jump than doubling the number
 * of basic zombies, and the wave table is easier to reason about when the heavy units are fixed.
 */
public final class ZombieRaidWaves {
	/** Marker for "this entry does not scale with group size". */
	private static final double NO_SCALING = -1.0;

	private ZombieRaidWaves() {
	}

	private static WaveDefinition.Spawn basic(int count) {
		return WaveDefinition.Spawn.of(() -> RaidEntityTypes.RAID_ZOMBIE, count,
				mob -> variant(mob, RaidZombie.Variant.BASIC));
	}

	private static WaveDefinition.Spawn baby(int count) {
		return WaveDefinition.Spawn.of(() -> RaidEntityTypes.RAID_ZOMBIE, count,
				mob -> variant(mob, RaidZombie.Variant.BABY));
	}

	private static WaveDefinition.Spawn armoured(int count) {
		return WaveDefinition.Spawn.of(() -> RaidEntityTypes.RAID_ZOMBIE, count,
				mob -> variant(mob, RaidZombie.Variant.ARMOURED));
	}

	private static WaveDefinition.Spawn acid(int count) {
		return WaveDefinition.Spawn.of(() -> RaidEntityTypes.ACID_ZOMBIE, count);
	}

	private static WaveDefinition.Spawn skeleton(int count) {
		return WaveDefinition.Spawn.of(() -> RaidEntityTypes.SWORD_SKELETON, count);
	}

	private static WaveDefinition.Spawn juggernaut(int count) {
		return new WaveDefinition.Spawn(() -> RaidEntityTypes.JUGGERNAUT_ZOMBIE, count, NO_SCALING, null);
	}

	private static void variant(Mob mob, RaidZombie.Variant variant) {
		if (mob instanceof RaidZombie zombie) {
			zombie.setVariant(variant);
		}
	}

	private static final List<WaveDefinition> WAVES = List.of(
			new WaveDefinition(1, false, List.of(basic(12))),
			new WaveDefinition(2, false, List.of(basic(10), baby(8))),
			new WaveDefinition(3, false, List.of(basic(10), armoured(6))),
			new WaveDefinition(4, true, List.of(basic(10), baby(6), armoured(4))),
			new WaveDefinition(5, false, List.of(basic(8), skeleton(10))),
			new WaveDefinition(6, false, List.of(basic(12), acid(8))),
			new WaveDefinition(7, false, List.of(juggernaut(4), armoured(10))),
			new WaveDefinition(8, true, List.of(basic(12), baby(8), armoured(8), skeleton(6))),
			new WaveDefinition(9, false, List.of(basic(8), baby(8), skeleton(10))),
			new WaveDefinition(10, false, List.of(juggernaut(4), baby(12), basic(8), acid(6))),
			new WaveDefinition(11, false, List.of(basic(12), baby(10), armoured(8), acid(8), skeleton(10), juggernaut(4))),
			new WaveDefinition(12, true, List.of(armoured(10), acid(8), skeleton(10), juggernaut(6))));

	public static int count() {
		return WAVES.size();
	}

	/** @param number 1-based wave number */
	public static WaveDefinition get(int number) {
		return WAVES.get(Math.max(1, Math.min(WAVES.size(), number)) - 1);
	}

	public static boolean isBossWave(int number) {
		return get(number).bossWave();
	}
}
