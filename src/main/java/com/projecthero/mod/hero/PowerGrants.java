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
			"all_might", "hulk", "symbiote");

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
			if (!holdsHeroTier(player, key)) {
				out.add(key);
			}
		}
		return out;
	}

	/** Experimental powers the player does not own yet. */
	public static List<Power> missingExperimental(ServerPlayer player) {
		List<Power> out = new ArrayList<>();
		for (Power p : Powers.all()) {
			if (!ExperimentalPowers.owns(player, p)) {
				out.add(p);
			}
		}
		return out;
	}

	/** Add one experimental power alongside what the player has (fails when mutation capacity is full). */
	public static boolean grantExperimental(ServerPlayer target, Power power) {
		boolean ok = ExperimentalPowers.grant(target, power);
		if (ok) {
			PowerPassives.reconcileActive(target);
		}
		return ok;
	}

	/**
	 * Grant the Hero-Tier power {@code hero}. Claims a Primary slot (the oldest of two is replaced), exactly as the
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
			default -> {
				boolean ok = com.projecthero.mod.symbiote.Symbiote.grant(target);
				return new Result(ok, Component.literal(ok ? "Bonded " + name + " with the Symbiote" : name + " already has the Symbiote"));
			}
		}
	}
}
