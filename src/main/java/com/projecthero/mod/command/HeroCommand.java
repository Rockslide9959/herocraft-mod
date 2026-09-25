package com.projecthero.mod.command;

import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.maxsteel.MaxSteel;
import com.projecthero.mod.spider.SpiderMan;
import com.projecthero.mod.worthiness.Worthiness;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;


import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Admin/testing commands for experimental powers:
 * {@code /heropower grant|stack|revoke|active|list|clear|serum|research|status}. Op-only. The real
 * acquisition route is the mutation/serum system (batch 2); this is for testing and for server
 * operators.
 *
 * <p>{@code grant <power>} replaces whatever the player has with just that power (see {@link #grant});
 * {@code stack <power>} is the additive form. {@code revoke <power>} removes one power;
 * {@code revoke all} removes every power (and all cooldown/resource state).
 *
 * <p>v0.6.19: {@code grant} / {@code revoke} also take an explicit category. {@code grant experimental
 * <key>} is the bare form above; {@code grant hero <thor|iron_man|spider_man|max_steel>} grants a
 * Hero-Tier power the same way that hero's own command does, and {@code revoke hero <…>} tears it down.
 *
 * <p>v0.6.20: the {@code hero} sub-literal is now optional -- the bare {@code /heropower grant <key>}
 * (and {@code revoke}) recognises the four Hero-Tier keys directly and routes them to the same path,
 * and they show up in tab-completion alongside the experimental keys.
 */
public final class HeroCommand {
	/** The four Hero-Tier powers, addressable by {@code /heropower grant|revoke hero <key>}. */
	private static final java.util.List<String> HERO_TIER_KEYS =
			java.util.List.of("thor", "iron_man", "spider_man", "max_steel", "punisher", "green_lantern", "wolverine", "symbiote");

	/**
	 * The bare {@code /heropower grant <key>} form (v0.6.20): experimental keys <em>and</em> the four
	 * Hero-Tier keys, so {@code /heropower grant spider_man} just works without having to remember the
	 * {@code hero} sub-literal.
	 */
	private static final SuggestionProvider<CommandSourceStack> POWER_KEYS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(java.util.stream.Stream.concat(
					HERO_TIER_KEYS.stream(), Powers.all().stream().map(Power::key)), builder);

	private static final SuggestionProvider<CommandSourceStack> POWER_KEYS_OR_ALL = (ctx, builder) ->
			SharedSuggestionProvider.suggest(java.util.stream.Stream.concat(
					java.util.stream.Stream.of("all"),
					java.util.stream.Stream.concat(HERO_TIER_KEYS.stream(), Powers.all().stream().map(Power::key))),
					builder);

	private static final SuggestionProvider<CommandSourceStack> HERO_TIER =
			(ctx, builder) -> SharedSuggestionProvider.suggest(HERO_TIER_KEYS, builder);

	private HeroCommand() {
	}


	/**
	 * v0.12.16: the only power admin tree -- {@code /projecthero power grant|remove|stack <power> [player]},
	 * over every power in the mod (the 27 mutations and every Hero-Tier power). {@code grant} replaces
	 * whatever the player has, {@code stack} adds alongside it, {@code remove} takes one power (or {@code all}).
	 */
	public static LiteralArgumentBuilder<CommandSourceStack> buildAdmin() {
		return Commands.literal("power")
				.requires(source -> source.hasPermission(2))
				.then(Commands.literal("grant")
						.then(Commands.argument("power", StringArgumentType.word()).suggests(POWER_KEYS)
								.executes(c -> grant(c, self(c)))
								.then(Commands.argument("player", EntityArgument.player())
										.executes(c -> grant(c, EntityArgument.getPlayer(c, "player"))))))
				.then(Commands.literal("remove")
						.then(Commands.argument("power", StringArgumentType.word()).suggests(POWER_KEYS_OR_ALL)
								.executes(c -> revoke(c, self(c)))
								.then(Commands.argument("player", EntityArgument.player())
										.executes(c -> revoke(c, EntityArgument.getPlayer(c, "player"))))))
				.then(Commands.literal("stack")
						.then(Commands.argument("power", StringArgumentType.word()).suggests(POWER_KEYS)
								.executes(c -> stack(c, self(c)))
								.then(Commands.argument("player", EntityArgument.player())
										.executes(c -> stack(c, EntityArgument.getPlayer(c, "player"))))));
	}

	public static LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("power")
				.requires(source -> source.hasPermission(2))
				.then(Commands.literal("grant")
						// New (v0.6.19): an explicit category. "/heropower grant experimental <key>" is the
						// old bare form; "/heropower grant hero <thor|iron_man|spider_man|max_steel>" grants a
						// Hero-Tier power the same way its own /<hero> command would.
						.then(Commands.literal("experimental")
								.then(Commands.argument("power", StringArgumentType.word()).suggests(POWER_KEYS)
										.executes(c -> grant(c, self(c)))
										.then(Commands.argument("player", EntityArgument.player())
												.executes(c -> grant(c, EntityArgument.getPlayer(c, "player"))))))
						.then(Commands.literal("hero")
								.then(Commands.argument("hero", StringArgumentType.word()).suggests(HERO_TIER)
										.executes(c -> grantHero(c, self(c)))
										.then(Commands.argument("player", EntityArgument.player())
												.executes(c -> grantHero(c, EntityArgument.getPlayer(c, "player"))))))
						// Backwards-compatible bare form: "/heropower grant <experimental key>".
						.then(Commands.argument("power", StringArgumentType.word()).suggests(POWER_KEYS)
								.executes(c -> grant(c, self(c)))
								.then(Commands.argument("player", EntityArgument.player())
										.executes(c -> grant(c, EntityArgument.getPlayer(c, "player"))))))
				.then(Commands.literal("stack")
						.then(Commands.argument("power", StringArgumentType.word()).suggests(POWER_KEYS)
								.executes(c -> stack(c, self(c)))
								.then(Commands.argument("player", EntityArgument.player())
										.executes(c -> stack(c, EntityArgument.getPlayer(c, "player"))))))
				.then(Commands.literal("revoke")
						.then(Commands.literal("experimental")
								.then(Commands.argument("power", StringArgumentType.word()).suggests(POWER_KEYS_OR_ALL)
										.executes(c -> revoke(c, self(c)))
										.then(Commands.argument("player", EntityArgument.player())
												.executes(c -> revoke(c, EntityArgument.getPlayer(c, "player"))))))
						.then(Commands.literal("hero")
								.then(Commands.argument("hero", StringArgumentType.word()).suggests(HERO_TIER)
										.executes(c -> revokeHero(c, self(c)))
										.then(Commands.argument("player", EntityArgument.player())
												.executes(c -> revokeHero(c, EntityArgument.getPlayer(c, "player"))))))
						.then(Commands.argument("power", StringArgumentType.word()).suggests(POWER_KEYS_OR_ALL)
								.executes(c -> revoke(c, self(c)))
								.then(Commands.argument("player", EntityArgument.player())
										.executes(c -> revoke(c, EntityArgument.getPlayer(c, "player"))))))
				.then(Commands.literal("active")
						.then(Commands.argument("power", StringArgumentType.word()).suggests(POWER_KEYS)
								.executes(c -> active(c, self(c)))
								.then(Commands.argument("player", EntityArgument.player())
										.executes(c -> active(c, EntityArgument.getPlayer(c, "player"))))))
				.then(Commands.literal("list")
						.executes(c -> list(c, self(c)))
						.then(Commands.argument("player", EntityArgument.player())
								.executes(c -> list(c, EntityArgument.getPlayer(c, "player")))))
				.then(Commands.literal("clear")
						.executes(c -> clear(c, self(c)))
						.then(Commands.argument("player", EntityArgument.player())
								.executes(c -> clear(c, EntityArgument.getPlayer(c, "player")))))
				.then(Commands.literal("serum")
						.then(Commands.argument("power", StringArgumentType.word()).suggests(POWER_KEYS)
								.executes(c -> serum(c, self(c)))
								.then(Commands.argument("player", EntityArgument.player())
										.executes(c -> serum(c, EntityArgument.getPlayer(c, "player"))))))
				.then(Commands.literal("research")
						.then(Commands.argument("power", StringArgumentType.word()).suggests(POWER_KEYS)
								.executes(c -> research(c, self(c)))
								.then(Commands.argument("player", EntityArgument.player())
										.executes(c -> research(c, EntityArgument.getPlayer(c, "player"))))))
				.then(Commands.literal("status")
						.executes(c -> status(c, self(c)))
						.then(Commands.argument("player", EntityArgument.player())
								.executes(c -> status(c, EntityArgument.getPlayer(c, "player")))));
	}

	private static ServerPlayer self(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		return c.getSource().getPlayerOrException();
	}

	private static Power resolve(CommandContext<CommandSourceStack> c) {
		String key = StringArgumentType.getString(c, "power");
		Power power = Powers.byKey(key);
		return power != null ? power : Powers.get(com.projecthero.mod.ProjectHeroMod.id(key));
	}

	/**
	 * Grants a power as the player's <em>sole</em> experimental power: any powers they already own are
	 * cleanly removed first (toggles off, passives/flight torn down, cooldowns and resources wiped),
	 * then this one is granted and made active. This is the "give me that power now" testing verb --
	 * repeated grants just swap, they never pile up or leave the previous power half-active. Use
	 * {@code /heropower stack} to add a power without removing the others.
	 */
	private static int grant(CommandContext<CommandSourceStack> c, ServerPlayer target) {
		// v0.6.20: the bare form also accepts a Hero-Tier key, so "/heropower grant spider_man" routes
		// to the same path as "/heropower grant hero spider_man".
		String rawKey = StringArgumentType.getString(c, "power");
		boolean heroKey = HERO_TIER_KEYS.contains(rawKey);
		Power power = heroKey ? null : resolve(c);
		if (power == null && !heroKey) {
			c.getSource().sendFailure(Component.literal("Unknown power (experimental key or "
					+ String.join("/", HERO_TIER_KEYS) + ")"));
			return 0;
		}
		boolean hadAny = com.projecthero.mod.hero.HeroTiers.hasExperimental(target)
				|| com.projecthero.mod.hero.HeroTiers.hasHeroTier(target)
				|| com.projecthero.mod.symbiote.Symbiote.hasSymbiote(target);

		// A command grant always REPLACES (v0.12.20: for Hero-Tier keys and the Symbiote too): tear down every
		// power of every tier first (experimental toggles/passives/flight, all Hero-Tier powers, the Symbiote),
		// then grant this one as the sole power. Use /projecthero power stack to add one alongside instead.
		com.projecthero.mod.hero.HeroTiers.wipeAll(target);
		if (com.projecthero.mod.symbiote.Symbiote.hasSymbiote(target)) {
			com.projecthero.mod.symbiote.Symbiote.remove(target);
		}
		if (heroKey) {
			return grantHeroByKey(c, target, rawKey);
		}
		ExperimentalPowers.grant(target, power);
		com.projecthero.mod.hero.PowerPassives.reconcileActive(target);

		final boolean replaced = hadAny;
		c.getSource().sendSuccess(() -> Component.literal((replaced ? "Replaced previous power with " : "Granted ")
				+ power.key() + " -> " + target.getGameProfile().getName()), true);
		return 1;
	}

	/** Additive grant: unlock a power without disturbing the others, up to the mutation capacity. */
	private static int stack(CommandContext<CommandSourceStack> c, ServerPlayer target) {
		String rawKey = StringArgumentType.getString(c, "power");
		if (HERO_TIER_KEYS.contains(rawKey)) {
			return grantHeroByKey(c, target, rawKey); // claims a Primary slot, alongside what they already hold
		}
		Power power = resolve(c);
		if (power == null) {
			c.getSource().sendFailure(Component.literal("Unknown power"));
			return 0;
		}
		boolean ok = ExperimentalPowers.grant(target, power);
		c.getSource().sendSuccess(() -> Component.literal((ok ? "Granted " : "Could not grant (owned or at capacity): ")
				+ power.key() + " -> " + target.getGameProfile().getName()), true);
		return ok ? 1 : 0;
	}

	private static int revoke(CommandContext<CommandSourceStack> c, ServerPlayer target) {
		String rawKey = StringArgumentType.getString(c, "power");
		if (HERO_TIER_KEYS.contains(rawKey)) {
			return revokeHeroByKey(c, target, rawKey);
		}
		if (rawKey.equalsIgnoreCase("all")) {
			int owned = ExperimentalPowers.state(target).ownedPowers.size();
			ExperimentalPowers.setActive(target, null);
			if (com.projecthero.mod.hero.power.HeroFlight.isFlying(target)) {
				com.projecthero.mod.hero.power.HeroFlight.setFlying(target, false);
			}
			ExperimentalPowers.clearAll(target);
			com.projecthero.mod.hero.PowerPassives.reconcileActive(target);
			final int removed = owned;
			c.getSource().sendSuccess(() -> Component.literal("Revoked all " + removed + " power(s) from "
					+ target.getGameProfile().getName()), true);
			return removed;
		}
		Power power = resolve(c);
		if (power == null) {
			c.getSource().sendFailure(Component.literal("Unknown power (or use 'all')"));
			return 0;
		}
		// forget() runs the full teardown (its toggled modes off, its power-wide passives off, its
		// resources / cooldowns cleared) and re-points the selected power if this was it.
		boolean removed = ExperimentalPowers.forget(target, power);
		if (removed) {
			var state = ExperimentalPowers.state(target).copy();
			state.researchStage.remove(power.key());
			target.setAttached(com.projecthero.mod.attachment.ModAttachments.EXPERIMENTAL_STATE, state);
			com.projecthero.mod.hero.PowerPassives.reconcileActive(target);
		}
		c.getSource().sendSuccess(() -> Component.literal((removed ? "Revoked " : "Not owned: ") + power.key()), true);
		return removed ? 1 : 0;
	}

	// ---------------- Hero-Tier powers (v0.6.19) ----------------

	/**
	 * Grant one of the four Hero-Tier powers, taking the same route its own {@code /<hero>} command
	 * does: Thor becomes worthy, Tony Stark is granted directly, Spider-Man evolves from Spider
	 * Adhesion (granting that first if it is missing), Max Steel bonds.
	 */
	private static int grantHero(CommandContext<CommandSourceStack> c, ServerPlayer target) {
		return grantHeroByKey(c, target, StringArgumentType.getString(c, "hero"));
	}

	private static int grantHeroByKey(CommandContext<CommandSourceStack> c, ServerPlayer target, String hero) {
		String name = target.getGameProfile().getName();
		if (!HERO_TIER_KEYS.contains(hero)) {
			c.getSource().sendFailure(Component.literal("Unknown Hero-Tier power (thor, iron_man, spider_man, max_steel)"));
			return 0;
		}
		// Claim a Primary slot (the oldest power is replaced if the player already holds two). The other heroes'
		// grant routines claim for themselves; Thor has no routine of its own, and Spider-Man needs the
		// mutations cleared for its Adhesion prerequisite.
		if ("spider_man".equals(hero)) {
			com.projecthero.mod.hero.HeroTiers.wipeExperimental(target);
		}
		switch (hero) {
			case "thor" -> {
				com.projecthero.mod.hero.HeroTiers.claimPrimary(target, "thor");
				Worthiness.setScore(target, Worthiness.TEST_WORTHY_SCORE);
				c.getSource().sendSuccess(() -> Component.literal(
						"Made " + name + " worthy of Mjolnir (grab the hammer to wield Thor's power)"), true);
				return 1;
			}
			case "iron_man" -> {
				boolean ok = TonyStark.grant(target);
				c.getSource().sendSuccess(() -> Component.literal((ok ? "Granted Tony Stark to " : "Already had Tony Stark: ")
						+ name), true);
				return ok ? 1 : 0;
			}
			case "spider_man" -> {
				if (SpiderMan.hasPower(target)) {
					c.getSource().sendFailure(Component.literal(name + " is already Spider-Man"));
					return 0;
				}
				if (!SpiderMan.hasSpiderAdhesion(target)) {
					Power adhesion = Powers.byKey(SpiderMan.SPIDER_ADHESION_KEY);
					if (adhesion == null || !ExperimentalPowers.grant(target, adhesion)) {
						c.getSource().sendFailure(Component.literal(
								"Could not give " + name + " the Spider Adhesion prerequisite (mutation slots full?)"));
						return 0;
					}
				}
				if (!SpiderMan.evolveFromAdhesion(target)) {
					c.getSource().sendFailure(Component.literal("Could not evolve " + name + " into Spider-Man"));
					return 0;
				}
				c.getSource().sendSuccess(() -> Component.literal("Evolved " + name + " into Spider-Man"), true);
				return 1;
			}
			case "max_steel" -> {
				boolean ok = MaxSteel.bond(target);
				c.getSource().sendSuccess(() -> Component.literal(ok ? "Bonded " + name + " with Steel (Max Steel)"
						: name + " is already Max Steel"), true);
				return ok ? 1 : 0;
			}
			case "punisher" -> {
				boolean ok = com.projecthero.mod.punisher.Punisher.grant(target);
				c.getSource().sendSuccess(() -> Component.literal(ok ? "Granted the Punisher to " + name
						: name + " is already the Punisher"), true);
				return ok ? 1 : 0;
			}
			case "green_lantern" -> {
				boolean ok = com.projecthero.mod.greenlantern.GreenLantern.bond(target);
				c.getSource().sendSuccess(() -> Component.literal(ok ? "Bonded " + name + " with a Power Ring (Green Lantern)"
						: name + " is already Green Lantern"), true);
				return ok ? 1 : 0;
			}
			case "wolverine" -> {
				return com.projecthero.mod.command.WolverineCommand.grant(c, target);
			}
			case "symbiote" -> {
				boolean ok = com.projecthero.mod.symbiote.Symbiote.grant(target);
				c.getSource().sendSuccess(() -> Component.literal(ok ? "Bonded " + name + " with the Symbiote"
						: name + " already has the Symbiote"), true);
				return ok ? 1 : 0;
			}
			default -> {
				c.getSource().sendFailure(Component.literal(
						"Unknown Hero-Tier power (thor, iron_man, spider_man, max_steel, punisher, green_lantern, wolverine)"));
				return 0;
			}
		}
	}

	/** Remove a Hero-Tier power: Thor loses worthiness, the other three tear their power down cleanly. */
	private static int revokeHero(CommandContext<CommandSourceStack> c, ServerPlayer target) {
		return revokeHeroByKey(c, target, StringArgumentType.getString(c, "hero"));
	}

	private static int revokeHeroByKey(CommandContext<CommandSourceStack> c, ServerPlayer target, String hero) {
		String name = target.getGameProfile().getName();
		switch (hero) {
			case "thor" -> Worthiness.setScore(target, 0);
			case "iron_man" -> TonyStark.revoke(target);
			case "spider_man" -> SpiderMan.revoke(target);
			case "max_steel" -> MaxSteel.revoke(target);
			case "punisher" -> com.projecthero.mod.punisher.Punisher.revoke(target);
			case "green_lantern" -> com.projecthero.mod.greenlantern.GreenLantern.revoke(target);
			case "wolverine" -> com.projecthero.mod.wolverine.Wolverine.revoke(target);
			case "symbiote" -> com.projecthero.mod.symbiote.Symbiote.remove(target);
			default -> {
				c.getSource().sendFailure(Component.literal(
						"Unknown Hero-Tier power (thor, iron_man, spider_man, max_steel, punisher, green_lantern, wolverine)"));
				return 0;
			}
		}
		c.getSource().sendSuccess(() -> Component.literal("Revoked " + hero + " from " + name), true);
		return 1;
	}

	private static int active(CommandContext<CommandSourceStack> c, ServerPlayer target) {
		String key = StringArgumentType.getString(c, "power");
		if (key.equalsIgnoreCase("none")) {
			ExperimentalPowers.setActive(target, null);
			c.getSource().sendSuccess(() -> Component.literal("Active experimental power cleared"), true);
			return 1;
		}
		Power power = resolve(c);
		if (power == null || !ExperimentalPowers.owns(target, power)) {
			c.getSource().sendFailure(Component.literal("Player does not own that power"));
			return 0;
		}
		ExperimentalPowers.setActive(target, power);
		c.getSource().sendSuccess(() -> Component.literal("Active: " + power.key()), true);
		return 1;
	}

	private static int list(CommandContext<CommandSourceStack> c, ServerPlayer target) {
		var state = ExperimentalPowers.state(target);
		c.getSource().sendSuccess(() -> Component.literal("Owned (" + state.ownedPowers.size() + "/"
				+ ExperimentalPowers.capacity() + "): " + String.join(", ", state.ownedPowers)
				+ " | active: " + (state.activePower.isEmpty() ? "<none>" : state.activePower)), false);
		return state.ownedPowers.size();
	}

	private static int clear(CommandContext<CommandSourceStack> c, ServerPlayer target) {
		ExperimentalPowers.clearAll(target);
		com.projecthero.mod.hero.PowerPassives.reconcileActive(target);
		c.getSource().sendSuccess(() -> Component.literal("Cleared all experimental state for "
				+ target.getGameProfile().getName()), true);
		return 1;
	}

	private static int serum(CommandContext<CommandSourceStack> c, ServerPlayer target) {
		Power power = resolve(c);
		if (power == null) {
			c.getSource().sendFailure(Component.literal("Unknown power"));
			return 0;
		}
		net.minecraft.world.item.ItemStack stack = net.minecraft.world.item.alchemy.PotionContents.createItemStack(
				net.minecraft.world.item.Items.POTION, com.projecthero.mod.hero.mutation.ModSerums.serum(power));
		target.getInventory().add(stack);
		c.getSource().sendSuccess(() -> Component.literal("Gave " + power.key() + " serum"), true);
		return 1;
	}

	private static int research(CommandContext<CommandSourceStack> c, ServerPlayer target) {
		Power power = resolve(c);
		if (power == null) {
			c.getSource().sendFailure(Component.literal("Unknown power"));
			return 0;
		}
		com.projecthero.mod.hero.mutation.MutationManager.studyResearchNote(target, power);
		return 1;
	}

	private static int status(CommandContext<CommandSourceStack> c, ServerPlayer target) {
		c.getSource().sendSuccess(() -> Component.literal(com.projecthero.mod.hero.mutation.MutationManager.describe(target)), false);
		return 1;
	}
}
