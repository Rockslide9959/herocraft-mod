package com.projecthero.mod.alarm;

import java.util.Locale;

/**
 * v0.17.7: the special jobs a guard can have, stored as an entity tag ({@code projecthero.role.<name>}) on any existing
 * guard mob, so the mob keeps its own model, skin and AI. Behaviour lives in {@link AlarmRoles}.
 * <ul>
 * <li>{@link #CAPTAIN} -- carries the place's Keycard (dropped on death), +20% damage to guards within 8 blocks.</li>
 * <li>{@link #RUNNER} -- light and fast; once any guard is alerted he sprints to the nearest live Alarm Panel and never
 * fights while one is reachable. Reaching it raises the alarm.</li>
 * <li>{@link #MEDIC} -- heals hurt guards within 8 blocks every 2 s.</li>
 * <li>{@link #SHIELD} -- blocks 70% of damage from the front; an axe or a big hit (10+) from the front knocks the shield
 * away for good.</li>
 * <li>{@link #SNIPER} -- long range; a red laser sight line to its target, a slow heavy shot every 2.5 s.</li>
 * </ul>
 */
public enum GuardRole {
	CAPTAIN, RUNNER, MEDIC, SHIELD, SNIPER;

	public static final String TAG_PREFIX = "projecthero.role.";

	public String tag() {
		return TAG_PREFIX + name().toLowerCase(Locale.ROOT);
	}

	/** {@code projecthero.alarm.role.captain} ... (the guard's name tag). */
	public String key() {
		return "projecthero.alarm.role." + name().toLowerCase(Locale.ROOT);
	}
}
