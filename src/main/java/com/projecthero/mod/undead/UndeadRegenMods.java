package com.projecthero.mod.undead;

import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3: what slows Undead's regeneration down. Stub contract: the regeneration code multiplies a lost part's start delay by
 * {@link #delayMult} and its regrowth time by {@link #durationMult}. The shoulder sword (user: "if player shift+right clicks with
 * a sword they stab it into their shoulder ... makes regeneration 10% slower and increase regen delay by 10%") makes both 1.1.
 */
public final class UndeadRegenMods {
	private UndeadRegenMods() {
	}

	public static float delayMult(Player p) {
		return 1f;
	}

	public static float durationMult(Player p) {
		return 1f;
	}
}
