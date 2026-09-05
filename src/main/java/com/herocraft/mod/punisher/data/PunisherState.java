package com.herocraft.mod.punisher.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.world.item.component.ItemContainerContents;

/**
 * The whole Punisher Hero-Tier power for one player, in one isolated namespaced attachment
 * ({@code herocraft:punisher_state}) -- exactly like {@code MaxSteelState} / {@code TonyStarkState}.
 *
 * <p>Persistent + {@code copyOnDeath()} (the power, the unlocked arsenal, cooldowns and training
 * progress must survive death and relog) and synced to <b>everyone</b> (other clients need
 * {@link #hasPower} to render {@link com.herocraft.mod.client.gui.FirearmHud}'s ∞ reserve and the
 * ability HUD; the owning client needs the arsenal list and the active-buff timers). The server
 * stays authoritative -- every check runs against the server-side copy.
 *
 * <p>Codec field order is load-bearing (14 of 16 {@code RecordCodecBuilder} slots) -- do not reorder.
 */
public final class PunisherState {
	/** Permanent Hero-Tier power flag -- set once Vigilante Training completes (or a command). */
	public boolean hasPower;
	/** Firearm ids the Punisher has unlocked for the Arsenal (the pistol is always available). */
	public final Set<String> unlockedWeapons;
	/** {@code abilityId} -> absolute game-time it is ready again. */
	public final Map<String, Long> abilityReadyAt;

	/**
	 * The Tactical Satchel: a persistent 9-slot personal container (opened with R while wearing the
	 * full Punisher tactical armour). Ender-chest-like -- it rides on the player, so it survives death
	 * ({@code copyOnDeath}), power swaps and dimension changes. Stored as an {@link ItemContainerContents}
	 * so it serialises exactly like a vanilla shulker box / bundle.
	 */
	public ItemContainerContents satchel = ItemContainerContents.EMPTY;

	/** Absolute game-time Adrenaline / Suppressive Fire end (0 = inactive). */
	public long adrenalineUntil;
	public long suppressiveUntil;
	/** Absolute game-time the current Tactical Roll ends (drives the i-frame window + animation). */
	public long rollUntil;
	/**
	 * Absolute game-time the Adrenaline crash lands (v0.9.4). Equal to {@link #adrenalineUntil} while
	 * the buff runs; the tick it passes, the player gets Nausea I once and this is zeroed. Persisted so
	 * a relog during Adrenaline still owes you the crash.
	 */
	public long adrenalineCrashAt;

	// ---- Vigilante Training progress ----
	public boolean trainingActive;
	public int killCount;
	public int rangedKillCount;
	public int headshotCount;
	public boolean craftedFirearm;
	public boolean defeatedCaptain;

	public PunisherState() {
		this(false, new LinkedHashSet<>(), new HashMap<>(), 0L, 0L, 0L,
				false, 0, 0, 0, false, false, ItemContainerContents.EMPTY, 0L);
	}

	public PunisherState(boolean hasPower, Set<String> unlockedWeapons, Map<String, Long> abilityReadyAt,
			long adrenalineUntil, long suppressiveUntil, long rollUntil,
			boolean trainingActive, int killCount, int rangedKillCount, int headshotCount,
			boolean craftedFirearm, boolean defeatedCaptain, ItemContainerContents satchel, long adrenalineCrashAt) {
		this.hasPower = hasPower;
		this.unlockedWeapons = new LinkedHashSet<>(unlockedWeapons);
		this.abilityReadyAt = new HashMap<>(abilityReadyAt);
		this.adrenalineUntil = adrenalineUntil;
		this.suppressiveUntil = suppressiveUntil;
		this.rollUntil = rollUntil;
		this.trainingActive = trainingActive;
		this.killCount = killCount;
		this.rangedKillCount = rangedKillCount;
		this.headshotCount = headshotCount;
		this.craftedFirearm = craftedFirearm;
		this.defeatedCaptain = defeatedCaptain;
		this.satchel = satchel == null ? ItemContainerContents.EMPTY : satchel;
		this.adrenalineCrashAt = adrenalineCrashAt;
	}

	public PunisherState copy() {
		return new PunisherState(hasPower, unlockedWeapons, abilityReadyAt, adrenalineUntil, suppressiveUntil,
				rollUntil, trainingActive, killCount, rangedKillCount, headshotCount, craftedFirearm, defeatedCaptain,
				satchel, adrenalineCrashAt);
	}

	public boolean trainingDone() {
		return trainingActive
				&& killCount >= com.herocraft.mod.punisher.PunisherConfig.TRAIN_KILLS
				&& rangedKillCount >= com.herocraft.mod.punisher.PunisherConfig.TRAIN_RANGED_KILLS
				&& headshotCount >= com.herocraft.mod.punisher.PunisherConfig.TRAIN_HEADSHOTS
				&& craftedFirearm && defeatedCaptain;
	}

	public static final Codec<PunisherState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.BOOL.optionalFieldOf("has_power", false).forGetter(s -> s.hasPower),
			Codec.STRING.listOf().optionalFieldOf("unlocked_weapons", List.of())
					.forGetter(s -> new ArrayList<>(s.unlockedWeapons)),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("ability_ready_at", Map.of())
					.forGetter(s -> new HashMap<>(s.abilityReadyAt)),
			Codec.LONG.optionalFieldOf("adrenaline_until", 0L).forGetter(s -> s.adrenalineUntil),
			Codec.LONG.optionalFieldOf("suppressive_until", 0L).forGetter(s -> s.suppressiveUntil),
			Codec.LONG.optionalFieldOf("roll_until", 0L).forGetter(s -> s.rollUntil),
			Codec.BOOL.optionalFieldOf("training_active", false).forGetter(s -> s.trainingActive),
			Codec.INT.optionalFieldOf("kill_count", 0).forGetter(s -> s.killCount),
			Codec.INT.optionalFieldOf("ranged_kill_count", 0).forGetter(s -> s.rangedKillCount),
			Codec.INT.optionalFieldOf("headshot_count", 0).forGetter(s -> s.headshotCount),
			Codec.BOOL.optionalFieldOf("crafted_firearm", false).forGetter(s -> s.craftedFirearm),
			Codec.BOOL.optionalFieldOf("defeated_captain", false).forGetter(s -> s.defeatedCaptain),
			ItemContainerContents.CODEC.optionalFieldOf("satchel", ItemContainerContents.EMPTY).forGetter(s -> s.satchel),
			Codec.LONG.optionalFieldOf("adrenaline_crash_at", 0L).forGetter(s -> s.adrenalineCrashAt)
	).apply(instance, (hp, uw, ara, au, su, ru, ta, kc, rkc, hc, cf, dc, sat, aca) ->
			new PunisherState(hp, new LinkedHashSet<>(uw), ara, au, su, ru, ta, kc, rkc, hc, cf, dc, sat, aca)));
}
