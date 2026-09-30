package com.projecthero.mod.hero.revamp.v0145;

import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.power.p02.LaserVisionHandlers;
import com.projecthero.mod.hero.visual.MutationMeters;
import com.projecthero.mod.hero.visual.MutationMeters.Kind;
import com.projecthero.mod.hero.visual.MutationMeters.Spec;
import com.projecthero.mod.hero.visual.MutationMeters.Style;
import com.projecthero.mod.hero.visual.MutationVisuals;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * v0.14.5 Laser Vision rework: server-side registration (visual flags, HUD meters, event hooks) and the
 * per-tick hook for this power.
 */
public final class LaserVisionV0145 {
	/** Red for the heat bar, its label and the Maximum Output bars. */
	public static final int RED = 0xFFE01E1E;
	public static final int RED_TEXT = 0xFFFF3A3A;

	private LaserVisionV0145() {
	}

	public static void init() {
		String k = LaserVisionHandlers.KEY;
		// Heat: a red Hairline under the keys, always shown, "Heat  NN%" in red.
		MutationMeters.register(new Spec(k, "heat", Kind.BUILD, Style.HAIRLINE, "Heat", LaserVisionHandlers.MAX_HEAT, RED,
				true, true, false, RED_TEXT));
		MutationMeters.register(new Spec(k, "overheat", Kind.TIMER, Style.HAIRLINE, "OVERHEATED",
				LaserVisionHandlers.OVERHEAT_TICKS, 0xFF8A8A8A, false, false, false, RED_TEXT));
		// Maximum Output: the charge-up, then the 10 s it runs -- Hairlines above the keys.
		MutationMeters.register(new Spec(k, "mo_charge", Kind.BUILD, Style.HAIRLINE, "Maximum Output — charging", 100f, RED,
				false, true, true, RED_TEXT));
		MutationMeters.register(new Spec(k, "max_ticks", Kind.TIMER, Style.HAIRLINE, "Maximum Output",
				LaserVisionHandlers.MAX_OUTPUT_TICKS, RED, false, false, true, RED_TEXT));

		// Maximum Output's beam, as a synced flag too: the client draws it while the flag OR the p02.max animation
		// is up, so a Sweeping Arc / Recoil Blast pose fired mid-beam never makes the big beam blink out.
		MutationVisuals.registerFlag("p02.max", p -> owns(p) && LaserVisionHandlers.maxOutputRunning(p));
	}

	private static boolean owns(ServerPlayer p) {
		return ExperimentalPowers.state(p).ownedPowers.contains(LaserVisionHandlers.KEY);
	}

	public static void serverTick(MinecraftServer server) {
		if (server.getTickCount() % 100 != 37) {
			return;
		}
		// the pre-v0.14.5 permanent Night Vision, also for anyone who has since lost the power
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			LaserVisionHandlers.dropLegacyNightVision(p);
		}
	}
}
