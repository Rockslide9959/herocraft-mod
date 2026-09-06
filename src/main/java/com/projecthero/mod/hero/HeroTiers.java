package com.projecthero.mod.hero;

import com.projecthero.mod.hero.power.HeroFlight;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.maxsteel.MaxSteel;
import com.projecthero.mod.punisher.Punisher;
import com.projecthero.mod.spider.SpiderMan;
import com.projecthero.mod.worthiness.Worthiness;

import net.minecraft.server.level.ServerPlayer;

/**
 * Cross-tier power bookkeeping. ProjectHero has two families of superpower and, by design, a player
 * may hold powers from only <em>one</em> of them at a time:
 *
 * <ul>
 *   <li><b>EXPERIMENTAL</b> -- the 27 mutation powers ({@link ExperimentalPowers}).</li>
 *   <li><b>HERO-TIER</b> -- Thor (worthiness of Mjolnir), Tony Stark / Iron Man, Spider-Man,
 *       Max Steel.</li>
 * </ul>
 *
 * <p>Natural acquisition of either family is refused while the player already holds the other:
 * the mutation manager checks {@link #hasHeroTier}, and the Arc Reactor / Arachnid Mutagen / Steel
 * bonding all check {@link #hasExperimental} (the Arachnid Mutagen additionally consumes every
 * experimental power on evolution, because Spider Adhesion is its prerequisite).
 *
 * <p>Commands never refuse. Every {@code /<hero>} and {@code /heropower grant} verb calls
 * {@link #wipeAll} first, so a command-granted power always simply <em>replaces</em> whatever the
 * player had, of any tier -- exactly as the user asked.
 */
public final class HeroTiers {
	private HeroTiers() {
	}

	/** True if the player holds any Hero-Tier power. */
	public static boolean hasHeroTier(ServerPlayer player) {
		return Worthiness.isWorthy(player)
				|| TonyStark.hasPower(player)
				|| SpiderMan.hasPower(player)
				|| MaxSteel.hasPower(player)
				|| Punisher.hasPower(player);
	}

	/** True if the player owns any of the 27 experimental mutation powers. */
	public static boolean hasExperimental(ServerPlayer player) {
		return !ExperimentalPowers.state(player).ownedPowers.isEmpty();
	}

	/**
	 * Strip every superpower of every tier, cleanly: experimental powers (active power stood down,
	 * hero flight stopped, all state wiped) and all four Hero-Tier powers. This is the basis of the
	 * command "always replace" rule -- callers run this, then grant the one power that was asked for.
	 */
	public static void wipeAll(ServerPlayer player) {
		wipeAll(player, java.util.Set.of());
	}

	/**
	 * Same as {@link #wipeAll(ServerPlayer)}, but skips whichever Hero-Tier powers are named in
	 * {@code excludeHeroKeys} (using the same key strings as {@code HeroCommand}'s Hero-Tier
	 * literals: {@code thor}/{@code iron_man}/{@code spider_man}/{@code max_steel}/{@code punisher}).
	 * The 27 experimental powers are never excludable -- there is no whitelist concept for them, only
	 * for the small, named set of Hero-Tier powers. This is what lets the Symbiote purge every
	 * incompatible power a player holds while keeping Spider-Man
	 * ({@code wipeAll(player, Set.of("spider_man"))}), without the Symbiote needing to know about any
	 * hero tier individually -- a hero added to {@code HeroCommand.HERO_TIER_KEYS} later is
	 * automatically purged unless it is also added to the caller's exclusion set.
	 */
	public static void wipeAll(ServerPlayer player, java.util.Set<String> excludeHeroKeys) {
		// experimental -- never excludable
		ExperimentalPowers.setActive(player, null);
		if (HeroFlight.isFlying(player)) {
			HeroFlight.setFlying(player, false);
		}
		ExperimentalPowers.clearAll(player);

		// hero-tier
		if (!excludeHeroKeys.contains("thor")) {
			Worthiness.setScore(player, 0);
		}
		if (!excludeHeroKeys.contains("iron_man") && TonyStark.hasPower(player)) {
			TonyStark.revoke(player);
		}
		if (!excludeHeroKeys.contains("spider_man") && SpiderMan.hasPower(player)) {
			SpiderMan.revoke(player);
		}
		if (!excludeHeroKeys.contains("max_steel") && MaxSteel.hasPower(player)) {
			MaxSteel.revoke(player);
		}
		if (!excludeHeroKeys.contains("punisher") && Punisher.hasPower(player)) {
			Punisher.revoke(player);
		}

		PowerPassives.reconcileActive(player);
	}

	/**
	 * True if the player holds any Hero-Tier power OTHER than the ones named in
	 * {@code excludeHeroKeys}, or any experimental power. Used by the Symbiote to decide whether
	 * bonding needs to purge anything before it can proceed.
	 */
	public static boolean hasIncompatibleWith(ServerPlayer player, java.util.Set<String> excludeHeroKeys) {
		if (hasExperimental(player)) {
			return true;
		}
		if (!excludeHeroKeys.contains("thor") && Worthiness.isWorthy(player)) {
			return true;
		}
		if (!excludeHeroKeys.contains("iron_man") && TonyStark.hasPower(player)) {
			return true;
		}
		if (!excludeHeroKeys.contains("spider_man") && SpiderMan.hasPower(player)) {
			return true;
		}
		if (!excludeHeroKeys.contains("max_steel") && MaxSteel.hasPower(player)) {
			return true;
		}
		if (!excludeHeroKeys.contains("punisher") && Punisher.hasPower(player)) {
			return true;
		}
		return false;
	}
}
