package com.projecthero.mod.punisher;

import com.projecthero.mod.firearm.AmmoKind;
import com.projecthero.mod.firearm.FirearmData;
import com.projecthero.mod.firearm.Firearms;
import com.projecthero.mod.punisher.data.PunisherState;

import net.minecraft.server.level.ServerPlayer;

/**
 * The Punisher's personal reserve ammunition (v0.9.22, replacing the old infinite reserve).
 *
 * <p>Each of the four guns has its own pool, sized at <b>three full magazines</b> of that gun's
 * ammunition. Reloading draws from the pool; it refills on its own at roughly <b>1% of capacity every
 * 0.7 seconds</b> ({@link #REGEN_INTERVAL_TICKS}). A pool with no stored value yet is treated as
 * full, so a freshly-trained Punisher starts with all three magazines in reserve for every weapon.
 *
 * <p>State lives on {@link PunisherState#ammoReserve} (persistent, {@code copyOnDeath}, synced to all
 * clients so {@code FirearmHud} can draw the real number). Writes happen only on a reload and at most
 * once per {@link #REGEN_INTERVAL_TICKS} of regen, never every tick.
 */
public final class PunisherAmmoReserve {
	/** 1% of capacity is added every 0.7 s. */
	public static final int REGEN_INTERVAL_TICKS = 14;
	private static final double REGEN_FRACTION = 0.01;

	private PunisherAmmoReserve() {
	}

	private static String gunId(AmmoKind kind) {
		return switch (kind) {
			case PISTOL -> Firearms.PISTOL;
			case RIFLE -> Firearms.RIFLE;
			case SHOTGUN -> Firearms.SHOTGUN;
			case SNIPER -> Firearms.SNIPER;
		};
	}

	/** Reserve capacity for a gun's ammunition: three full magazines. */
	public static int capacity(AmmoKind kind) {
		FirearmData data = Firearms.get(gunId(kind));
		return data == null ? 0 : data.magazineSize * 3;
	}

	private static double stored(PunisherState s, AmmoKind kind) {
		Double v = s.ammoReserve.get(kind.lower());
		return v == null ? capacity(kind) : Math.min(v, capacity(kind));
	}

	/** Whole rounds available in the reserve right now (for the HUD and reload checks). */
	public static int count(PunisherState s, AmmoKind kind) {
		return (int) Math.floor(stored(s, kind));
	}

	public static int count(ServerPlayer player, AmmoKind kind) {
		return count(Punisher.state(player), kind);
	}

	/** Take up to {@code want} rounds from the pool. Returns how many were actually removed. */
	public static int take(ServerPlayer player, AmmoKind kind, int want) {
		if (want <= 0) {
			return 0;
		}
		PunisherState s = Punisher.state(player).copy();
		double have = stored(s, kind);
		int taken = (int) Math.min(want, Math.floor(have));
		if (taken > 0) {
			s.ammoReserve.put(kind.lower(), have - taken);
			Punisher.save(player, s);
		}
		return taken;
	}

	/**
	 * Called every tick for a Punisher; adds one regen step once per {@link #REGEN_INTERVAL_TICKS} to
	 * every pool that is below capacity. Only writes state on a tick where something actually changed.
	 */
	public static void tickRegen(ServerPlayer player) {
		if (player.tickCount % REGEN_INTERVAL_TICKS != 0) {
			return;
		}
		PunisherState s = Punisher.state(player).copy();
		boolean changed = false;
		for (AmmoKind kind : AmmoKind.values()) {
			int cap = capacity(kind);
			if (cap <= 0) {
				continue;
			}
			double have = stored(s, kind);
			if (have >= cap) {
				continue;
			}
			s.ammoReserve.put(kind.lower(), Math.min(cap, have + cap * REGEN_FRACTION));
			changed = true;
		}
		if (changed) {
			Punisher.save(player, s);
		}
	}
}
