package com.projecthero.mod.quirk.erasure;

import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.QuirkCombos;
import com.projecthero.mod.quirk.meter.QuirkMeterSpec;

/**
 * v0.17.1 Quirk framework + the Erasure revamp: Dry Eyes' bands and pacing ({@code docs/QUIRKS_REVAMP_PROPOSAL.md} 3.5).
 * Fresh 0-49, Strained 50-89 (the stare reaches 32 blocks, a red screen edge), Burning 90-100 (the stare holds two targets,
 * 0.5 HP/s), forced shut at 100.
 *
 * <p>Pacing (the user's rule: nothing builds up too quick): the stare (10/s raw) and Reel Lock (5/s raw while R is held) dry
 * the eyes x{@link #BUILD}. The heavy-use script -- a held stare the whole time plus a three-second Reel Lock every time Capture
 * Bind is ready -- reaches Burning after about 48 s (the framework's stare-only script: 50 s at x0.18; v0.16.7: 9 s).
 * Blinking recovers at the full rate: Burning to Fresh in about 3.3 s.
 */
public final class ErasureMeter {
	public static final float BUILD = 0.15f;
	public static final float RECOVER = 1.0f;
	/**
	 * The doc's starter route (3.5 / 2.7): Capture Bind, then Bind and Spin on the same target within 1.5 s = a 1.5x harder
	 * fling (wired in {@code ErasureMoves.bind / spin}).
	 */
	public static QuirkCombos.Route BIND_SPIN;
	/** v0.17.1: Scarf Whip, then Capture Pull on the same target within 1.2 s = a 1.4x harder yank. */
	public static QuirkCombos.Route CRACK_YANK;

	private ErasureMeter() {
	}

	static void register() {
		BIND_SPIN = QuirkCombos.route(Quirk.ERASURE, "bind_spin", ErasureConfig.BIND, ErasureConfig.SPIN).window(30).bonus(1.5f).sameTarget().register();
		CRACK_YANK = QuirkCombos.route(Quirk.ERASURE, "crack_yank", ErasureConfig.WHIP, ErasureConfig.LASH).window(24).bonus(1.4f).sameTarget()
				.register();
		QuirkMeterSpec.builder(Quirk.ERASURE)
				.bands(50f, 90f)
				.names("fresh", "strained", "burning", "shut")
				.damage(1.0f, 1.0f, 1.0f, 1.0f)
				.pacing(BUILD, RECOVER)
				.heavyUse(h -> h.sustained(ErasureConfig.STARE_DRY * 20f)
						.move(ErasureConfig.REEL_DRY * 20f * ErasureConfig.REEL_TYPICAL_SECONDS, ErasureConfig.BIND_COOLDOWN)
						.recovery(ErasureConfig.RECOVER * 20f, 0))
				.register();
	}
}
