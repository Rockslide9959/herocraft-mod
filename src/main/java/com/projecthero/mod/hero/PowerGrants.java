package com.projecthero.mod.hero;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.maxsteel.MaxSteel;
import com.projecthero.mod.spider.SpiderMan;
import com.projecthero.mod.worthiness.Worthiness;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Granting a power without a command in the way. v0.13.18: lifted out of {@code HeroCommand} so the admin
 * {@code /projecthero power grant|stack} and the new random-power serums share exactly one implementation of "give
 * this player that Hero-Tier power" -- including each hero's prerequisites (Spider-Man's Spider Adhesion,
 * Wolverine's Super Regeneration) and the Primary-slot rule ({@link HeroTiers#claimPrimary}).
 */
public final class PowerGrants {
	/** Every Hero-Tier key a grant accepts: the {@link HeroTiers#HERO_KEYS} Primary heroes plus the Symbiote. */
	public static final List<String> HERO_TIER_KEYS = List.of(
			"thor", "iron_man", "spider_man", "max_steel", "punisher", "green_lantern", "wolverine", "titan_shifter",
			"all_might", "hulk", "moon_knight", "super_soldier", "kryptonian", "nova", "ghost_rider", "cyborg", "blue_beetle",
			"ladybug", "cat_noir", "rena_rouge", "symbiote");

	/**
	 * v0.13.19: Hero-Tier powers still being built in phases. Grantable by an operator for testing, but the random
	 * serums never roll them -- players should not be handed a half-finished hero.
	 */
	public static final java.util.Set<String> IN_DEVELOPMENT = java.util.Set.of();

	/** What a grant did, with the line to show whoever asked for it. */
	public record Result(boolean ok, Component message) {
		static Result ok(String text) {
			return new Result(true, Component.literal(text));
		}

		static Result fail(String text) {
			return new Result(false, Component.literal(text));
		}
	}

	private PowerGrants() {
	}

	/** Whether {@code player} currently holds the Hero-Tier power {@code key}. */
	public static boolean holdsHeroTier(ServerPlayer player, String key) {
		if ("symbiote".equals(key)) {
			return com.projecthero.mod.symbiote.Symbiote.hasSymbiote(player);
		}
		return HeroTiers.holdsHero(player, key);
	}

	/** Hero-Tier keys the player does not hold yet. */
	public static List<String> missingHeroTiers(ServerPlayer player) {
		List<String> out = new ArrayList<>();
		for (String key : HERO_TIER_KEYS) {
			if (!holdsHeroTier(player, key) && !IN_DEVELOPMENT.contains(key)) {
				out.add(key);
			}
		}
		return out;
	}

	/** Experimental powers the player does not own yet. */
	public static List<Power> missingExperimental(ServerPlayer player) {
		List<Power> out = new ArrayList<>();
		for (Power p : Powers.mutations()) {
			if (!ExperimentalPowers.owns(player, p)) {
				out.add(p);
			}
		}
		return out;
	}

	/**
	 * Add one experimental power alongside what the player has (fails when mutation capacity is full). v0.13.22: goes
	 * through the same rules as a mutation from a serum -- the capacity check comes first (nothing is lost when full),
	 * then the mutation group claims its Primary slot ({@link HeroTiers#claimExperimental}), and the research
	 * stages / advancements are recorded. It used to skip the slot rule, so a random serum could leave a player with
	 * two heroes, the Symbiote AND mutations.
	 */
	public static boolean grantExperimental(ServerPlayer target, Power power) {
		// v0.14.12: Super Speed replaces other mutations -- but a player whose slots are all full still keeps everything
		// (v0.14.13-0.14.20 Super Speed was Hero-Tier; v0.14.21 it is a mutation again and goes through here)
		if (!Powers.isMutation(power) || ExperimentalPowers.owns(target, power) || ExperimentalPowers.atCapacity(target)) {
			return false;
		}
		HeroTiers.claimMutation(target, power);
		boolean ok = ExperimentalPowers.grant(target, power);
		if (ok) {
			PowerPassives.reconcileActive(target);
			com.projecthero.mod.hero.mutation.MutationManager.recordConfirmed(target, power);
		}
		return ok;
	}

	/**
	 * Grant the Hero-Tier power {@code hero}. Claims the Primary slot (v0.14.4: replaces whatever was held), exactly as the
	 * admin command always has.
	 */
	public static Result grantHero(ServerPlayer target, String hero) {
		return grantHero(target, hero, true);
	}

	/**
	 * @param allowWipe whether Spider-Man may clear the player's mutations to make room for its Spider Adhesion
	 *                  prerequisite (the admin command does; a serum must not quietly delete powers, so with
	 *                  {@code false} the grant simply fails when there is no room)
	 */
	public static Result grantHero(ServerPlayer target, String hero, boolean allowWipe) {
		String name = target.getGameProfile().getName();
		if (!HERO_TIER_KEYS.contains(hero)) {
			return Result.fail("Unknown Hero-Tier power (" + String.join(", ", HERO_TIER_KEYS) + ")");
		}
		// v0.16.1: the projectheroUniquePowers gamerule -- refused before anything (prerequisites, wipes) is touched
		String holder = UniquePowers.blockingHolder(target, hero);
		if (holder != null) {
			return new Result(false, UniquePowers.takenMessage(hero, holder));
		}
		// Spider-Man needs the mutations cleared for its Spider Adhesion prerequisite.
		if ("spider_man".equals(hero) && allowWipe) {
			HeroTiers.wipeExperimental(target);
		}
		switch (hero) {
			case "thor" -> {
				HeroTiers.claimPrimary(target, "thor");
				Worthiness.setScore(target, Worthiness.TEST_WORTHY_SCORE);
				return Result.ok("Made " + name + " worthy of Mjolnir (grab the hammer to wield Thor's power)");
			}
			case "iron_man" -> {
				boolean ok = TonyStark.grant(target);
				return new Result(ok, Component.literal((ok ? "Granted Tony Stark to " : "Already had Tony Stark: ") + name));
			}
			case "spider_man" -> {
				if (SpiderMan.hasPower(target)) {
					return Result.fail(name + " is already Spider-Man");
				}
				if (!SpiderMan.hasSpiderAdhesion(target)) {
					Power adhesion = Powers.byKey(SpiderMan.SPIDER_ADHESION_KEY);
					if (adhesion == null || !ExperimentalPowers.grant(target, adhesion)) {
						return Result.fail("Could not give " + name + " the Spider Adhesion prerequisite (mutation slots full?)");
					}
				}
				return SpiderMan.evolveFromAdhesion(target) ? Result.ok("Evolved " + name + " into Spider-Man")
						: Result.fail("Could not evolve " + name + " into Spider-Man");
			}
			case "max_steel" -> {
				boolean ok = MaxSteel.bond(target);
				return new Result(ok, Component.literal(ok ? "Bonded " + name + " with Steel (Max Steel)" : name + " is already Max Steel"));
			}
			case "punisher" -> {
				boolean ok = com.projecthero.mod.punisher.Punisher.grant(target);
				return new Result(ok, Component.literal(ok ? "Granted the Punisher to " + name : name + " is already the Punisher"));
			}
			case "green_lantern" -> {
				boolean ok = com.projecthero.mod.greenlantern.GreenLantern.bond(target);
				return new Result(ok, Component.literal(ok ? "Bonded " + name + " with a Power Ring (Green Lantern)"
						: name + " is already Green Lantern"));
			}
			case "wolverine" -> {
				if (com.projecthero.mod.wolverine.Wolverine.hasPower(target)) {
					return Result.fail(name + " is already Wolverine");
				}
				if (!com.projecthero.mod.wolverine.Wolverine.hasSuperRegeneration(target)) {
					Power sr = Powers.byKey(com.projecthero.mod.wolverine.Wolverine.SUPER_REGENERATION_KEY);
					if (sr == null || !ExperimentalPowers.grant(target, sr)) {
						return Result.fail("Could not give " + name + " the Super Regeneration prerequisite (mutation slots full?)");
					}
				}
				return com.projecthero.mod.wolverine.Wolverine.ascendFromSuperRegeneration(target)
						? Result.ok("Ascended " + name + " into Wolverine") : Result.fail("Could not ascend " + name + " into Wolverine");
			}
			case "titan_shifter" -> {
				boolean ok = com.projecthero.mod.titanshifter.TitanShifter.grant(target);
				return new Result(ok, Component.literal(ok ? "Granted Titan Shifting to " + name : name + " already has Titan Shifting"));
			}
			case "all_might" -> {
				boolean ok = com.projecthero.mod.allmight.AllMight.grant(target);
				return new Result(ok, Component.literal(ok ? "Granted All Might to " + name : name + " already has One For All"));
			}
			case "hulk" -> {
				boolean ok = com.projecthero.mod.hulk.Hulk.grant(target);
				return new Result(ok, Component.literal(ok ? "Gave " + name + " the Gamma power (Hulk)" : name + " already has the Gamma power"));
			}
			case "moon_knight" -> {
				boolean ok = com.projecthero.mod.moonknight.MoonKnight.grant(target);
				return new Result(ok, Component.literal(ok ? "Sealed " + name + "'s pact with Khonshu (Moon Knight)" : name + " already serves Khonshu"));
			}
			case "super_soldier" -> {
				boolean ok = com.projecthero.mod.supersoldier.SuperSoldier.grant(target);
				return new Result(ok, Component.literal(ok ? "Gave " + name + " the Super Soldier Serum" : name + " is already a Super Soldier"));
			}
			case "kryptonian" -> {
				boolean ok = com.projecthero.mod.kryptonian.Kryptonian.grant(target);
				return new Result(ok, Component.literal(ok ? "Made " + name + " a Kryptonian" : name + " is already a Kryptonian"));
			}
			case "blue_beetle" -> {
				boolean ok = com.projecthero.mod.bluebeetle.BlueBeetle.grant(target);
				return new Result(ok, Component.literal(ok ? "Bonded " + name + " with the Scarab (Blue Beetle)" : name + " already carries the Scarab"));
			}
			case "cyborg" -> {
				boolean ok = com.projecthero.mod.cyborg.Cyborg.grant(target);
				return new Result(ok, Component.literal(ok ? "Fused " + name + " with a Mother Box (Cyborg)" : name + " is already Cyborg"));
			}
			case "ghost_rider" -> {
				boolean ok = com.projecthero.mod.ghostrider.GhostRider.grant(target);
				return new Result(ok, Component.literal(ok ? "Bound " + name + " to the Spirit of Vengeance (Ghost Rider)" : name + " already carries the Spirit of Vengeance"));
			}
			case "ladybug", "cat_noir", "rena_rouge" -> {
				// v0.16.6: the Miraculous -- the jewel's Kwami pops out and introduces itself
				com.projecthero.mod.miraculous.Kwami k = com.projecthero.mod.miraculous.Kwami.byHeroKey(hero);
				boolean ok = com.projecthero.mod.miraculous.Miraculous.grant(target, k);
				String heroName = com.projecthero.mod.miraculous.MiraculousPhrases.heroName(k);
				return new Result(ok, Component.literal(ok ? "Gave " + name + " the Miraculous of " + heroName
						: name + " already holds that Miraculous"));
			}
			case "nova" -> {
				boolean ok = com.projecthero.mod.nova.Nova.grant(target);
				return new Result(ok, Component.literal(ok ? "Gave " + name + " the Nova Force (Nova)" : name + " already carries the Nova Force"));
			}
			default -> {
				boolean ok = com.projecthero.mod.symbiote.Symbiote.grant(target);
				return new Result(ok, Component.literal(ok ? "Bonded " + name + " with the Symbiote" : name + " already has the Symbiote"));
			}
		}
	}

	/** v0.16.1: tear one Hero-Tier power (or the Symbiote) down through its own revoke; false for an unknown key. */
	public static boolean revokeHeroTier(ServerPlayer target, String hero) {
		switch (hero) {
			case "thor" -> com.projecthero.mod.power.ThorPowers.revokePower(target);
			case "iron_man" -> TonyStark.revoke(target);
			case "spider_man" -> SpiderMan.revoke(target);
			case "max_steel" -> MaxSteel.revoke(target);
			case "punisher" -> com.projecthero.mod.punisher.Punisher.revoke(target);
			case "green_lantern" -> com.projecthero.mod.greenlantern.GreenLantern.revoke(target);
			case "wolverine" -> com.projecthero.mod.wolverine.Wolverine.revoke(target);
			case "titan_shifter" -> com.projecthero.mod.titanshifter.TitanShifter.revoke(target);
			case "all_might" -> com.projecthero.mod.allmight.AllMight.revoke(target);
			case "hulk" -> com.projecthero.mod.hulk.Hulk.revoke(target);
			case "moon_knight" -> com.projecthero.mod.moonknight.MoonKnight.revoke(target);
			case "super_soldier" -> com.projecthero.mod.supersoldier.SuperSoldier.revoke(target);
			case "kryptonian" -> com.projecthero.mod.kryptonian.Kryptonian.revoke(target);
			case "nova" -> com.projecthero.mod.nova.Nova.revoke(target);
			case "ghost_rider" -> com.projecthero.mod.ghostrider.GhostRider.revoke(target);
			case "cyborg" -> com.projecthero.mod.cyborg.Cyborg.revoke(target);
			case "blue_beetle" -> com.projecthero.mod.bluebeetle.BlueBeetle.revoke(target);
			case "ladybug", "cat_noir", "rena_rouge" -> {
				if (HeroTiers.holdsHero(target, hero)) {
					com.projecthero.mod.miraculous.Miraculous.revoke(target);
				}
			}
			case "symbiote" -> com.projecthero.mod.symbiote.Symbiote.remove(target);
			default -> {
				return false;
			}
		}
		PowerPassives.reconcileActive(target);
		return true;
	}
}
