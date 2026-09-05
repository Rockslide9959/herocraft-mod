package com.herocraft.mod.hero;

/**
 * How a slot's input is interpreted by {@link AbilityRouter}.
 *
 * <ul>
 *   <li>{@link #INSTANT} -- key-down fires the ability once; cooldown-gated.</li>
 *   <li>{@link #HOLD} -- key-down starts a channel, key-up ends it; drains a resource each tick
 *       while held rather than using a fixed cooldown.</li>
 *   <li>{@link #TOGGLE} -- key-down flips a persistent on/off state (stance, mode, aura).</li>
 *   <li>{@link #CYCLE} -- key-down advances through a small set of modes (e.g. density Light/Normal/
 *       Heavy).</li>
 *   <li>{@link #CHARGE} -- key-down begins building charge, key-up (or a later slot) releases it.</li>
 * </ul>
 */
public enum AbilityActivation {
	INSTANT,
	HOLD,
	TOGGLE,
	CYCLE,
	CHARGE
}
