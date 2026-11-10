package com.projecthero.mod.client.saiyan;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.saiyan.Saiyan;
import com.projecthero.mod.saiyan.SaiyanForm;

import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3: what the Saiyan visuals read for a player -- the core's {@link Saiyan} state, or a client-only debug override
 * (set only by the filming harness, scratchpad/saiyan/SaiyanAuraHarness.v0173.java.txt). The override never reaches the server.
 */
public final class SaiyanFx {
	private record Override(SaiyanForm form, boolean charging, long transformAt) {
	}

	private static final Map<UUID, Override> DEBUG = new ConcurrentHashMap<>();

	private SaiyanFx() {
	}

	public static SaiyanForm form(Player p) {
		Override o = DEBUG.get(p.getUUID());
		return o != null ? o.form : Saiyan.form(p);
	}

	public static boolean charging(Player p) {
		Override o = DEBUG.get(p.getUUID());
		return o != null ? o.charging : Saiyan.charging(p);
	}

	/** Ticks since the current form began; MAX_VALUE when long settled. */
	public static int transformingTicks(Player p) {
		Override o = DEBUG.get(p.getUUID());
		if (o == null) {
			return Saiyan.transformingTicks(p);
		}
		long t = p.level().getGameTime() - o.transformAt;
		return t < 0 || t > 100000 ? Integer.MAX_VALUE : (int) t;
	}

	/** Anything to draw for {@code p} (a form, or charging in base). */
	public static boolean active(Player p) {
		return form(p) != SaiyanForm.BASE || charging(p);
	}

	// ---------------------------------------------------------------- debug override (harness only)

	public static void debugForm(Player p, SaiyanForm f, boolean burst) {
		Override o = DEBUG.get(p.getUUID());
		DEBUG.put(p.getUUID(), new Override(f, o != null && o.charging, burst ? p.level().getGameTime() : (o != null ? o.transformAt : Long.MIN_VALUE / 2)));
	}

	public static void debugCharging(Player p, boolean on) {
		Override o = DEBUG.get(p.getUUID());
		DEBUG.put(p.getUUID(), new Override(o != null ? o.form : SaiyanForm.BASE, on, o != null ? o.transformAt : Long.MIN_VALUE / 2));
	}

	public static void debugClear(Player p) {
		DEBUG.remove(p.getUUID());
	}

	public static void clearAll() {
		DEBUG.clear();
	}
}
