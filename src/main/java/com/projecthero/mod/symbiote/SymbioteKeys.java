package com.projecthero.mod.symbiote;

/**
 * v0.17.5 (user: "since symbiote is a secondary power for now can we make it able to be used with any other power, you only get
 * symbiote moveset if player transforms into symbiote armour while having a primary power, if H does something for the primary power
 * already while having symbiote then make it shift+H to transform into symbiote"): what an H press does for a Normal host. Pure, so the
 * table is testable on the server ({@code SymbioteSecondaryGameTests}); the client ({@code ProjectHeroModClient#handlePowerSelect})
 * asks it with what it knows about the Primary.
 *
 * <table>
 *   <tr><th>Situation</th><th>H</th><th>Shift+H</th></tr>
 *   <tr><td>No Primary (the Symbiote alone)</td><td>suit on / off</td><td>suit on / off (as before)</td></tr>
 *   <tr><td>Primary that does nothing on plain H</td><td>suit on / off</td><td>the power wheel if there is one, else suit on / off</td></tr>
 *   <tr><td>Primary that uses plain H</td><td>the Primary's H (tap)</td><td>suit on / off</td></tr>
 *   <tr><td>... and a mutation wheel (it lived on Shift+H)</td><td>tap = the Primary's H, hold 10 ticks = the power wheel</td><td>suit on / off</td></tr>
 *   <tr><td>Suit on, with a Primary</td><td>suit off</td><td>suit off</td></tr>
 * </table>
 * Players without the Symbiote are untouched (the wheel stays on Shift+H for them).
 */
public final class SymbioteKeys {
	/** Ticks H has to be held (with the Symbiote stacked on a Primary that uses H and a power wheel) to open the wheel. */
	public static final int WHEEL_HOLD_TICKS = 10;

	public enum HAction {
		/** Toggle the Symbiote suit. */
		SYMBIOTE,
		/** Not ours: run the ordinary H chain (the Primary's H, or nothing). */
		PRIMARY,
		/** Open the power wheel. */
		WHEEL,
		/** Wait: a tap (released before {@link #WHEEL_HOLD_TICKS}) is the Primary's H, a hold opens the wheel. */
		TAP_OR_HOLD
	}

	private SymbioteKeys() {
	}

	/**
	 * @param stacked a Normal host with a Primary power ({@link Symbiote#stacked})
	 * @param suited the Symbiote suit is on (or coming on / off)
	 * @param primaryOwnsH the Primary does something on plain H
	 * @param shift Shift held
	 * @param wheel the power wheel has something to switch to
	 * @param wheelWasShiftH the wheel would have been on Shift+H (a mutation that uses plain H for its Utility 1)
	 */
	public static HAction route(boolean stacked, boolean suited, boolean primaryOwnsH, boolean shift, boolean wheel, boolean wheelWasShiftH) {
		if (!stacked) {
			return HAction.PRIMARY; // the ordinary chain (it toggles the suit for a lone Normal host, as it always did)
		}
		if (suited) {
			return HAction.SYMBIOTE;
		}
		if (primaryOwnsH) {
			if (shift) {
				return HAction.SYMBIOTE;
			}
			return wheel && wheelWasShiftH ? HAction.TAP_OR_HOLD : HAction.PRIMARY;
		}
		if (shift && wheel) {
			return HAction.WHEEL;
		}
		return HAction.SYMBIOTE;
	}
}
