package com.projecthero.mod.hero;

import com.projecthero.mod.greenlantern.GreenLantern;
import com.projecthero.mod.hero.power.HeroFlight;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.maxsteel.MaxSteel;
import com.projecthero.mod.punisher.Punisher;
import com.projecthero.mod.spider.SpiderMan;
import com.projecthero.mod.symbiote.Symbiote;
import com.projecthero.mod.wolverine.Wolverine;
import com.projecthero.mod.worthiness.Worthiness;

import net.minecraft.server.level.ServerPlayer;

/**
 * Cross-tier power bookkeeping. Every power is either {@link PowerClass#PRIMARY} or
 * {@link PowerClass#SECONDARY} (the Symbiote is the only Secondary). Since v0.11.15 a player holds up to
 * {@link #PRIMARY_SLOTS} Primary powers: the experimental mutations count as one group, each Hero-Tier power
 * as one. Gaining a power that does not fit replaces the oldest ({@link #claimPrimary} / {@link #claimExperimental}).
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
				|| GreenLantern.hasPower(player)
				|| Wolverine.hasPower(player);
	}

	/** How many Hero-Tier (non-experimental) Primary powers the player holds. */
	public static int heroCount(ServerPlayer player) {
		int n = 0;
		for (String key : HERO_KEYS) {
			if (holdsHero(player, key)) {
				n++;
			}
		}
		return n;
	}

	/** True if the player owns any of the 27 experimental mutation powers. */
	public static boolean hasExperimental(ServerPlayer player) {
		return !ExperimentalPowers.state(player).ownedPowers.isEmpty();
	}

	/** How many non-experimental Primary powers a player can hold at once (the experimental group counts as one). */
	public static final int PRIMARY_SLOTS = 2;

	/** Every non-experimental Primary power key, as used by {@code HeroCommand}. */
	public static final java.util.List<String> HERO_KEYS = java.util.List.of(
			"thor", "iron_man", "spider_man", "max_steel", "punisher", "green_lantern", "wolverine");

	/** Whether the player currently holds the Hero-Tier power named by {@code key}. */
	public static boolean holdsHero(ServerPlayer player, String key) {
		return switch (key) {
			case "thor" -> Worthiness.isWorthy(player);
			case "iron_man" -> TonyStark.hasPower(player);
			case "spider_man" -> SpiderMan.hasPower(player);
			case "max_steel" -> MaxSteel.hasPower(player);
			case "punisher" -> Punisher.hasPower(player);
			case "green_lantern" -> GreenLantern.hasPower(player);
			case "wolverine" -> Wolverine.hasPower(player);
			default -> false;
		};
	}

	private static void revokeHero(ServerPlayer player, String key) {
		switch (key) {
			case "thor" -> Worthiness.setScore(player, 0);
			case "iron_man" -> {
				if (TonyStark.hasPower(player)) {
					TonyStark.revoke(player);
				}
			}
			case "spider_man" -> {
				if (SpiderMan.hasPower(player)) {
					SpiderMan.revoke(player);
				}
			}
			case "max_steel" -> {
				if (MaxSteel.hasPower(player)) {
					MaxSteel.revoke(player);
				}
			}
			case "punisher" -> {
				if (Punisher.hasPower(player)) {
					Punisher.revoke(player);
				}
			}
			case "green_lantern" -> {
				if (GreenLantern.hasPower(player)) {
					GreenLantern.revoke(player);
				}
			}
			case "wolverine" -> {
				if (Wolverine.hasPower(player)) {
					Wolverine.revoke(player);
				}
			}
			default -> {
			}
		}
	}

	/** Held Hero-Tier powers, oldest first. Powers held from before the order was tracked count as oldest. */
	private static java.util.List<String> heroOrder(ServerPlayer player) {
		java.util.List<String> out = new java.util.ArrayList<>();
		String raw = player.getAttachedOrCreate(com.projecthero.mod.attachment.ModAttachments.PRIMARY_ORDER);
		for (String k : raw.split(",")) {
			if (HERO_KEYS.contains(k) && !out.contains(k) && holdsHero(player, k)) {
				out.add(k);
			}
		}
		int legacy = 0;
		for (String k : HERO_KEYS) {
			if (!out.contains(k) && holdsHero(player, k)) {
				out.add(legacy++, k);
			}
		}
		return out;
	}

	private static void saveOrder(ServerPlayer player, java.util.List<String> order) {
		player.setAttached(com.projecthero.mod.attachment.ModAttachments.PRIMARY_ORDER, String.join(",", order));
	}

	/** Revokes the oldest Hero-Tier powers (never {@code keep}) until at most {@code max} remain. True if any were. */
	private static boolean trimHeroes(ServerPlayer player, java.util.List<String> order, String keep, int max) {
		boolean removed = false;
		java.util.Iterator<String> it = order.iterator();
		while (order.size() > max && it.hasNext()) {
			String oldest = it.next();
			if (oldest.equals(keep)) {
				continue;
			}
			it.remove();
			revokeHero(player, oldest);
			removed = true;
		}
		return removed;
	}

	/** Strip every experimental mutation (toggles/passives off, flight stopped) and leave Hero-Tier powers alone. */
	public static void wipeExperimental(ServerPlayer player) {
		ExperimentalPowers.setActive(player, null);
		if (HeroFlight.isFlying(player)) {
			HeroFlight.setFlying(player, false);
		}
		ExperimentalPowers.clearAll(player);
	}

	/**
	 * Called by a Primary power's own grant routine just before it hands the power over. A player holds up
	 * to {@link #PRIMARY_SLOTS} Primary powers; the experimental mutations (which stack with each other) are
	 * one group. Gaining a non-experimental Primary power always strips every mutation, and if the player
	 * already holds {@link #PRIMARY_SLOTS} Hero-Tier powers the oldest is replaced. The Symbiote (the only
	 * Secondary power) is removed too, unless the new power is Spider-Man, the one thing it can share a host with.
	 *
	 * @param heroKey one of {@link #HERO_KEYS}
	 */
	public static void claimPrimary(ServerPlayer player, String heroKey) {
		if (hasExperimental(player)) {
			wipeExperimental(player);
		}
		java.util.List<String> order = heroOrder(player);
		order.remove(heroKey);
		trimHeroes(player, order, heroKey, PRIMARY_SLOTS - 1);
		order.add(heroKey);
		saveOrder(player, order);
		PowerPassives.reconcileActive(player);
		dropSymbioteUnless(player, "spider_man".equals(heroKey));
	}

	/**
	 * The mutation-serum flavour of {@link #claimPrimary}: the mutation group takes one Primary slot, so at
	 * most one Hero-Tier power survives (the newest); the Symbiote is removed. Other mutations the player
	 * already owns are kept (they stack up to the mutation capacity).
	 *
	 * @return true if anything was replaced
	 */
	public static boolean claimExperimental(ServerPlayer player) {
		java.util.List<String> order = heroOrder(player);
		boolean changed = trimHeroes(player, order, "", PRIMARY_SLOTS - 1);
		saveOrder(player, order);
		if (changed) {
			PowerPassives.reconcileActive(player);
		}
		if (Symbiote.hasSymbiote(player)) {
			Symbiote.remove(player);
			changed = true;
		}
		return changed;
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
		wipeExperimental(player);

		wipeHeroTier(player, excludeHeroKeys);
	}

	private static void wipeHeroTier(ServerPlayer player, java.util.Set<String> excludeHeroKeys) {
		for (String key : HERO_KEYS) {
			if (!excludeHeroKeys.contains(key)) {
				revokeHero(player, key);
			}
		}
		saveOrder(player, heroOrder(player));
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
		if (!excludeHeroKeys.contains("wolverine") && Wolverine.hasPower(player)) {
			return true;
		}
		return false;
	}
}
