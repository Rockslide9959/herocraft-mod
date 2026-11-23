package com.projecthero.mod.hideout;

/** v0.17.5: a hideout mini-boss. Every one carries a {@link HideoutBossLink} (core, boss bar, threat table, leash). */
public interface HideoutBoss {
	HideoutBossLink hideoutLink();

	/** v0.17.7: the boss's second phase (the five original bosses declare their own {@code phase()}). */
	default com.projecthero.mod.hideout.boss.HideoutPhase phase() {
		return null;
	}
}
