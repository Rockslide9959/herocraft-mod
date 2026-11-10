package com.projecthero.mod.saiyan;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Stand-in, replaced by core. v0.17.3: the read side of the Saiyan power that the moves call. Keeps a plain per-player map
 * (form, Ki) so the moves compile and can be tested before the core lands; the core agent's real class keeps these
 * signatures. Take the core's version on merge.
 */
public final class Saiyan {
	public static final float KI_MAX = 100f;

	private static final Map<UUID, SaiyanForm> FORM = new ConcurrentHashMap<>();
	private static final Map<UUID, Float> KI = new ConcurrentHashMap<>();

	private Saiyan() {
	}

	public static SaiyanForm form(Player p) {
		return FORM.getOrDefault(p.getUUID(), SaiyanForm.BASE);
	}

	public static float ki(Player p) {
		return KI.getOrDefault(p.getUUID(), KI_MAX);
	}

	public static float kiMax(Player p) {
		return KI_MAX;
	}

	/** Takes {@code amount} Ki if there is that much; false (and nothing taken) if not. */
	public static boolean spendKi(ServerPlayer p, float amount) {
		if (p.getAbilities().instabuild) {
			return true;
		}
		float k = ki(p);
		if (k < amount) {
			return false;
		}
		KI.put(p.getUUID(), k - amount);
		return true;
	}

	public static boolean charging(Player p) {
		return false;
	}

	public static int transformingTicks(Player p) {
		return Integer.MAX_VALUE;
	}

	/** Stand-in only: lets the moves' harness set a form. */
	public static void standInSet(Player p, SaiyanForm f, float ki) {
		FORM.put(p.getUUID(), f);
		KI.put(p.getUUID(), ki);
	}
}
