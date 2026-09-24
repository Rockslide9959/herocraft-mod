package com.projecthero.mod.hero;

import com.projecthero.mod.greenlantern.GreenLantern;
import com.projecthero.mod.hero.power.HeroFlight;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.maxsteel.MaxSteel;
import com.projecthero.mod.punisher.Punisher;
import com.projecthero.mod.spider.SpiderMan;
import com.projecthero.mod.symbiote.Symbiote;
import com.projecthero.mod.worthiness.Worthiness;

import net.minecraft.server.level.ServerPlayer;

/**
 * Cross-tier power bookkeeping. Since v0.11.14 every power is either {@link PowerClass#PRIMARY} or
 * {@link PowerClass#SECONDARY} (the Symbiote is the only Secondary): gaining a Primary power replaces
 * the old one via {@link #claimPrimary} / {@link #claimExperimental}, rather than being refused.
 *
 * <p>Historically ProjectHero had two families of Primary power and a player held only <em>one</em> of them at a time:
 *
 * <ul>
 *   <li><b>EXPERIMENTAL</b> -- the 27 mutation powers ({@link ExperimentalPowers}).</li>
 *   <li><b>HERO-TIER</b> -- Thor (worthiness of Mjolnir), Tony Stark / Iron Man, Spider-Man,
 *       Max Steel.</li>
 * </ul>
 *
 * <p>That still holds -- but where a natural acquisition (Arc Reactor, Steel bond, Punisher training,
 * the Green Lantern ring, Mjolnir, a mutation serum) used to be refused while the player held the other
 * family, it now <em>replaces</em> it. Every power's own grant routine calls {@link #claimPrimary} (or the
 * mutation manager calls {@link #claimExperimental}) first.
 *
 * <p>Commands also replace: every {@code /<hero>} and {@code /heropower grant} verb calls
 * {@link #wipeAll} first, then the grant routine itself claims Primary status.
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
				|| Punisher.hasPower(player)
				|| GreenLantern.hasPower(player);
	}

	/** True if the player owns any of the 27 experimental mutation powers. */
	public static boolean hasExperimental(ServerPlayer player) {
		return !ExperimentalPowers.state(player).ownedPowers.isEmpty();
	}

	/**
	 * Called by a Primary power's own grant routine just before it hands the power over: everything the
	 * player held that is not {@code heroKey} is stripped ({@link #wipeAll(ServerPlayer, java.util.Set)} --
	 * mutations, every other Hero-Tier power), and the Symbiote (the only Secondary power) is removed too,
	 * unless the new power is Spider-Man, the one thing it can share a host with.
	 *
	 * @param heroKey one of the {@code HeroCommand} keys: {@code thor}/{@code iron_man}/{@code spider_man}/
	 *                {@code max_steel}/{@code punisher}/{@code green_lantern}
	 */
	public static void claimPrimary(ServerPlayer player, String heroKey) {
		wipeAll(player, java.util.Set.of(heroKey));
		dropSymbioteUnless(player, "spider_man".equals(heroKey));
	}

	/**
	 * The mutation-serum flavour of {@link #claimPrimary}: gaining an experimental power strips every
	 * Hero-Tier power and the Symbiote, but keeps any other mutations the player already owns (they stack
	 * up to the mutation capacity).
	 */
	public static void claimExperimental(ServerPlayer player) {
		wipeHeroTier(player, java.util.Set.of());
		dropSymbioteUnless(player, false);
	}

	private static void dropSymbioteUnless(ServerPlayer player, boolean compatible) {
		if (!compatible && Symbiote.hasSymbiote(player)) {
			Symbiote.remove(player);
		}
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

		wipeHeroTier(player, excludeHeroKeys);
	}

	private static void wipeHeroTier(ServerPlayer player, java.util.Set<String> excludeHeroKeys) {
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
		if (!excludeHeroKeys.contains("green_lantern") && GreenLantern.hasPower(player)) {
			GreenLantern.revoke(player);
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
		if (!excludeHeroKeys.contains("green_lantern") && GreenLantern.hasPower(player)) {
			return true;
		}
		return false;
	}
}
