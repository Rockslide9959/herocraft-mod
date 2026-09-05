package com.herocraft.mod.symbiote;

import java.util.Set;

import com.herocraft.mod.hero.HeroTiers;
import com.herocraft.mod.spider.SpiderMan;

import net.minecraft.server.level.ServerPlayer;

/**
 * The Symbiote's power-compatibility whitelist. Spider-Man is the only power that can coexist with
 * a bonded Symbiote -- everything else (every Hero-Tier power and all 27 experimental mutation
 * powers) is purged the instant the Symbiote successfully bonds.
 *
 * <p>This is deliberately a one-line whitelist rather than a list of every incompatible power: the
 * actual removal is delegated to {@link HeroTiers#wipeAll(ServerPlayer, Set)}, which already
 * enumerates every Hero-Tier power and clears the experimental system dynamically. A future hero
 * class only needs to be added to {@code HeroCommand.HERO_TIER_KEYS} / {@link HeroTiers} to be
 * automatically treated as incompatible here too -- nothing in this class needs to change.
 */
public final class SymbioteCompatibility {
	/** The only Hero-Tier key the Symbiote does not purge on bonding. */
	private static final Set<String> COMPATIBLE_HERO_KEYS = Set.of("spider_man");

	private SymbioteCompatibility() {
	}

	/** True if this player currently holds Spider-Man -- the one power the Symbiote can share a host with. */
	public static boolean isSpiderMan(ServerPlayer player) {
		return SpiderMan.hasPower(player);
	}

	/**
	 * True if the player holds a power the Symbiote must purge before it can bond (anything that is
	 * not Spider-Man). A clean player with no powers at all, or a Spider-Man, both return false.
	 */
	public static boolean hasIncompatiblePower(ServerPlayer player) {
		return HeroTiers.hasIncompatibleWith(player, COMPATIBLE_HERO_KEYS);
	}

	/**
	 * Remove every power the Symbiote is incompatible with (everything except Spider-Man). Safe to
	 * call even if the player has nothing to purge.
	 */
	public static void purge(ServerPlayer player) {
		HeroTiers.wipeAll(player, COMPATIBLE_HERO_KEYS);
	}
}
