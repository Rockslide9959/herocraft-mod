package com.projecthero.mod.quirk.erasure;

import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.meter.QuirkMeterSpec;

/**
 * v0.17.1 Quirk framework: Dry Eyes' bands and pacing ({@code docs/QUIRKS_REVAMP_PROPOSAL.md} 3.5). Fresh 0-49, Strained
 * 50-89 (the kit's: a longer stare, a red screen edge), Burning 90-100 (two targets, 0.5 HP/s), forced shut at 100.
 *
 * <p>Pacing: the stare dries the eyes x{@link #BUILD} -- a held stare now reaches Burning after about 50 s (it was 9 s).
 * Blinking recovers at the full rate (Burning to Fresh in about 3.3 s).
 */
public final class ErasureMeter {
	public static final float BUILD = 0.18f;
	public static final float RECOVER = 1.0f;
	/**
	 * The doc's starter route (3.5 / 2.7): Capture Bind, then Bind and Spin on the same target within 1.5 s = extra fling.
	 * Registered here; the Erasure kit build reports the two moves ({@code QuirkCombos.hit}) and reads the bonus.
	 */
	public static com.projecthero.mod.quirk.QuirkCombos.Route BIND_SPIN;

	private ErasureMeter() {
	}

	static void register() {
		BIND_SPIN = com.projecthero.mod.quirk.QuirkCombos.route(Quirk.ERASURE, "bind_spin", ErasureConfig.BIND, ErasureConfig.SPIN)
				.window(30).bonus(1.5f).sameTarget().register();
		QuirkMeterSpec.builder(Quirk.ERASURE)
				.bands(50f, 90f)
				.names("fresh", "strained", "burning", "shut")
				.damage(1.0f, 1.0f, 1.0f, 1.0f)
				.pacing(BUILD, RECOVER)
				.heavyUse(h -> h.sustained(ErasureConfig.STARE_DRY * 20f).recovery(ErasureConfig.RECOVER * 20f, 0))
				.register();
	}
}
