package com.projecthero.mod.alarm;

/**
 * v0.17.7: an alarm zone's state. <b>Calm</b> (nobody has noticed anything) -> <b>Suspicious</b> (a guard's suspicion
 * meter is filling, or a guard is already fighting but the alarm has not been raised) -> <b>Alert</b> (a runner reached a
 * panel or a sensor fired: siren, red lamps, Blast Doors shut, reinforcement waves) -> back to Calm after the waves die
 * and {@link AlarmZone#CALM_DOWN_TICKS} pass with no guard seeing anyone. The ordinal is the network / save value.
 */
public enum AlarmState {
	CALM, SUSPICIOUS, ALERT;

	public static AlarmState byId(int id) {
		AlarmState[] v = values();
		return id >= 0 && id < v.length ? v[id] : CALM;
	}

	/** {@code projecthero.alarm.state.calm} ... */
	public String key() {
		return "projecthero.alarm.state." + name().toLowerCase(java.util.Locale.ROOT);
	}
}
