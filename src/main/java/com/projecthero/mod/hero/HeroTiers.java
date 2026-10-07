package com.projecthero.mod.hero;

import com.projecthero.mod.greenlantern.GreenLantern;
import com.projecthero.mod.hero.power.HeroFlight;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.maxsteel.MaxSteel;
import com.projecthero.mod.power.ThorPassives;
import com.projecthero.mod.power.ThorPowers;
import com.projecthero.mod.punisher.Punisher;
import com.projecthero.mod.spider.SpiderMan;
import com.projecthero.mod.symbiote.Symbiote;
import com.projecthero.mod.titanshifter.TitanShifter;
import com.projecthero.mod.wolverine.Wolverine;
import com.projecthero.mod.worthiness.Worthiness;

import net.minecraft.server.level.ServerPlayer;

/**
 * Cross-tier power bookkeeping. Every power is either {@link PowerClass#PRIMARY} or
 * {@link PowerClass#SECONDARY} (the Symbiote is the only Secondary). A player holds {@link #PRIMARY_SLOTS}
 * Primary power (v0.14.4: back to ONE, was two since v0.11.15): the experimental mutations count as one group,
 * each Hero-Tier power as one. Gaining a new one replaces what was held ({@link #claimPrimary} /
 * {@link #claimExperimental}); {@link #enforceLimit} trims saves made under the old two-slot rule on join.
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
				|| Wolverine.hasPower(player)
				|| TitanShifter.isShifter(player)
				|| com.projecthero.mod.allmight.AllMight.hasPower(player)
				|| com.projecthero.mod.hulk.Hulk.hasPower(player)
				|| com.projecthero.mod.moonknight.MoonKnight.hasPower(player)
				|| com.projecthero.mod.supersoldier.SuperSoldier.hasPower(player)
				|| com.projecthero.mod.kryptonian.Kryptonian.hasPower(player)
				|| com.projecthero.mod.nova.Nova.hasPower(player);
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

	/**
	 * True if the player owns any of the 27 experimental mutation powers. v0.14.13: Hero-Tier Super Speed lives in the same
	 * owned list but is not a mutation, so it does not count.
	 */
	public static boolean hasExperimental(ServerPlayer player) {
		for (String key : ExperimentalPowers.state(player).ownedPowers) {
			if (!Powers.HERO_TIER.contains(key)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * How many Primary powers a player can hold at once (the experimental group counts as one). v0.14.4: 1 --
	 * every hero power (Thor via Mjolnir included) replaces whatever the player had.
	 */
	public static final int PRIMARY_SLOTS = 1;

	/** Every non-experimental Primary power key, as used by {@code HeroCommand}. */
	public static final java.util.List<String> HERO_KEYS = java.util.List.of(
			"thor", "iron_man", "spider_man", "max_steel", "punisher", "green_lantern", "wolverine", "titan_shifter", "all_might", "hulk", "moon_knight", "super_soldier", "kryptonian", "nova");

	/** Whether the player currently holds the Hero-Tier power named by {@code key}. */
	public static boolean holdsHero(ServerPlayer player, String key) {
		return switch (key) {
			case "thor" -> ThorPassives.hasPowerOfThor(player);
			case "iron_man" -> TonyStark.hasPower(player);
			case "spider_man" -> SpiderMan.hasPower(player);
			case "max_steel" -> MaxSteel.hasPower(player);
			case "punisher" -> Punisher.hasPower(player);
			case "green_lantern" -> GreenLantern.hasPower(player);
			case "wolverine" -> Wolverine.hasPower(player);
			case "titan_shifter" -> TitanShifter.isShifter(player);
			case "all_might" -> com.projecthero.mod.allmight.AllMight.hasPower(player);
			case "hulk" -> com.projecthero.mod.hulk.Hulk.hasPower(player);
			case "moon_knight" -> com.projecthero.mod.moonknight.MoonKnight.hasPower(player);
			case "super_soldier" -> com.projecthero.mod.supersoldier.SuperSoldier.hasPower(player);
			case "kryptonian" -> com.projecthero.mod.kryptonian.Kryptonian.hasPower(player);
			case "nova" -> com.projecthero.mod.nova.Nova.hasPower(player);
			default -> false;
		};
	}

	private static void revokeHero(ServerPlayer player, String key) {
		switch (key) {
			case "thor" -> ThorPowers.revokePower(player);
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
			case "titan_shifter" -> {
				if (TitanShifter.isShifter(player)) {
					TitanShifter.revoke(player);
				}
			}
			case "all_might" -> {
				if (com.projecthero.mod.allmight.AllMight.hasPower(player)) {
					com.projecthero.mod.allmight.AllMight.revoke(player);
				}
			}
			case "hulk" -> {
				if (com.projecthero.mod.hulk.Hulk.hasPower(player)) {
					com.projecthero.mod.hulk.Hulk.revoke(player);
				}
			}
			case "moon_knight" -> {
				if (com.projecthero.mod.moonknight.MoonKnight.hasPower(player)) {
					com.projecthero.mod.moonknight.MoonKnight.revoke(player);
				}
			}
			case "super_soldier" -> {
				if (com.projecthero.mod.supersoldier.SuperSoldier.hasPower(player)) {
					com.projecthero.mod.supersoldier.SuperSoldier.revoke(player);
				}
			}
			case "kryptonian" -> {
				if (com.projecthero.mod.kryptonian.Kryptonian.hasPower(player)) {
					com.projecthero.mod.kryptonian.Kryptonian.revoke(player);
				}
			}
			case "nova" -> {
				if (com.projecthero.mod.nova.Nova.hasPower(player)) {
					com.projecthero.mod.nova.Nova.revoke(player);
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
	/**
	 * v0.12.16: a Thor who has let go of the hammer (no bound hammer) and then takes on ANY other power
	 * stops being worthy -- they can no longer lift Mjolnir back up and stack Thor on top of it.
	 */
	public static void unworthyIfHammerReleased(ServerPlayer player) {
		if (Boolean.TRUE.equals(player.getAttachedOrElse(
				com.projecthero.mod.attachment.ModAttachments.HAMMER_RELEASED, false))) {
			player.setAttached(com.projecthero.mod.attachment.ModAttachments.HAMMER_RELEASED, false);
			if (Worthiness.isWorthy(player)) {
				Worthiness.setScore(player, 0);
			}
		}
	}

	public static void claimPrimary(ServerPlayer player, String heroKey) {
		if (!"thor".equals(heroKey)) {
			unworthyIfHammerReleased(player);
		}
		// v0.13.12: nobody is both Thor and the Hulk -- becoming one takes the other away
		if ("hulk".equals(heroKey) && (Worthiness.isWorthy(player) || holdsHero(player, "thor"))) {
			revokeHero(player, "thor");
		} else if ("thor".equals(heroKey) && holdsHero(player, "hulk")) {
			revokeHero(player, "hulk");
		}
		if (hasExperimental(player)) {
			wipeExperimental(player);
		}
		java.util.List<String> order = heroOrder(player);
		order.remove(heroKey);
		trimHeroes(player, order, heroKey, PRIMARY_SLOTS - 1);
		order.add(heroKey);
		saveOrder(player, order);
		PowerPassives.reconcileActive(player);
		// v0.13.11: the Punisher shares a host with the Symbiote too (Agent Venom)
		dropSymbioteUnless(player, com.projecthero.mod.symbiote.SymbioteCompatibility.COMPATIBLE_HERO_KEYS.contains(heroKey));
	}

	/**
	 * The mutation-serum flavour of {@link #claimPrimary}: the mutation group takes one Primary slot, so at
	 * most one Hero-Tier power survives (the newest); the Symbiote is removed. Other mutations the player
	 * already owns are kept (they stack up to the mutation capacity).
	 *
	 * @return true if anything was replaced
	 */
	public static boolean claimExperimental(ServerPlayer player) {
		unworthyIfHammerReleased(player);
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

	/**
	 * v0.14.12: Super Speed stands alone, like a Hero-Tier power -- it never stacks with anything. A mutation of this key
	 * replaces every other power the player has; any other mutation replaces it.
	 */
	public static final String SOLO_MUTATION = com.projecthero.mod.hero.power.p04.SuperSpeedHandlers.KEY;

	public static boolean isSoloMutation(com.projecthero.mod.hero.Power power) {
		return power != null && SOLO_MUTATION.equals(power.key());
	}

	/**
	 * v0.14.12: {@link #claimExperimental} for one specific mutation about to be granted: the Hero-Tier / Symbiote rule,
	 * then the Super Speed rule -- gaining Super Speed forgets every other mutation, gaining any other mutation forgets
	 * Super Speed (with a message either way). True if anything was replaced.
	 */
	public static boolean claimMutation(ServerPlayer player, com.projecthero.mod.hero.Power power) {
		boolean changed = claimExperimental(player);
		boolean solo = isSoloMutation(power);
		boolean dropped = false;
		for (String key : java.util.List.copyOf(ExperimentalPowers.state(player).ownedPowers)) {
			if (key.equals(power.key()) || !(solo || SOLO_MUTATION.equals(key))) {
				continue;
			}
			com.projecthero.mod.hero.Power other = Powers.byKey(key);
			if (other != null) {
				ExperimentalPowers.forget(player, other);
				dropped = true;
			}
		}
		if (dropped) {
			PowerPassives.reconcileActive(player);
			player.displayClientMessage(net.minecraft.network.chat.Component.translatable(solo
					? "message.projecthero.mutation.super_speed_alone" : "message.projecthero.mutation.super_speed_replaced")
					.withStyle(net.minecraft.ChatFormatting.YELLOW), false);
		}
		return changed || dropped;
	}

	/**
	 * v0.14.4: brings a player saved under the old two-slot rule down to one Primary power. The newest hero
	 * power is kept (Hero-Tier order is tracked, mutations are not, and a hero is the bigger investment), any
	 * older heroes are revoked, and mutations go if a hero is kept. Run on join. True if anything was removed.
	 */
	public static boolean enforceLimit(ServerPlayer player) {
		boolean changed = false;
		// v0.14.12: a save holding Super Speed alongside other mutations keeps whichever is selected (v0.14.13: run first,
		// so Super Speed is settled before the hero trim below). v0.14.21: Super Speed is a mutation again; a save from
		// its Hero-Tier days still owns it through the mutation state, and its stale "super_speed" Primary-order entry is
		// simply dropped by heroOrder (no longer a HERO_KEY) and re-saved below -- the player keeps the power.
		com.projecthero.mod.hero.data.ExperimentalState es = ExperimentalPowers.state(player);
		if (es.ownedPowers.contains(SOLO_MUTATION) && es.ownedPowers.size() > 1) {
			boolean keepSpeed = SOLO_MUTATION.equals(es.activePower);
			for (String key : java.util.List.copyOf(es.ownedPowers)) {
				com.projecthero.mod.hero.Power p = Powers.byKey(key);
				if (p != null && (keepSpeed != SOLO_MUTATION.equals(key))) {
					ExperimentalPowers.forget(player, p);
				}
			}
			changed = true;
		}
		java.util.List<String> order = heroOrder(player);
		changed |= trimHeroes(player, order, "", PRIMARY_SLOTS);
		saveOrder(player, order);
		if (!order.isEmpty() && hasExperimental(player)) {
			wipeExperimental(player);
			changed = true;
		}
		if (changed) {
			PowerPassives.reconcileActive(player);
			player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
					"message.projecthero.one_power_limit").withStyle(net.minecraft.ChatFormatting.GOLD));
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
		if (!excludeHeroKeys.contains("titan_shifter") && TitanShifter.isShifter(player)) {
			return true;
		}
		if (!excludeHeroKeys.contains("all_might") && com.projecthero.mod.allmight.AllMight.hasPower(player)) {
			return true;
		}
		if (!excludeHeroKeys.contains("hulk") && com.projecthero.mod.hulk.Hulk.hasPower(player)) {
			return true;
		}
		if (!excludeHeroKeys.contains("moon_knight") && com.projecthero.mod.moonknight.MoonKnight.hasPower(player)) {
			return true;
		}
		if (!excludeHeroKeys.contains("super_soldier") && com.projecthero.mod.supersoldier.SuperSoldier.hasPower(player)) {
			return true;
		}
		if (!excludeHeroKeys.contains("kryptonian") && com.projecthero.mod.kryptonian.Kryptonian.hasPower(player)) {
			return true;
		}
		if (!excludeHeroKeys.contains("nova") && com.projecthero.mod.nova.Nova.hasPower(player)) {
			return true;
		}
		return false;
	}
}
