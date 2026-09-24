package com.projecthero.mod.command;

import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuitSummonManager;
import com.projecthero.mod.ironman.suit.IronManSuits;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
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
 * Admin / testing commands for the Iron Man system (spec sections 35, 40):
 * {@code /ironman power grant|revoke | tech <0-5> | energy <suit> <amount> | suit <id> | status}. Op 2.
 * The real acquisition route is crafting + right-clicking an Arc Reactor; this is for testing.
 */
public final class IronManCommand {
	private static final SuggestionProvider<CommandSourceStack> SUIT_IDS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(IronManSuits.all().stream().map(IronManSuit::id), builder);

	private IronManCommand() {
	}

	public static LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("ironman")
				.requires(source -> source.hasPermission(2))
				.then(Commands.literal("power")
						.then(Commands.literal("grant")
								.executes(c -> power(c, self(c), true))
								.then(Commands.argument("player", EntityArgument.player())
										.executes(c -> power(c, EntityArgument.getPlayer(c, "player"), true))))
						.then(Commands.literal("revoke")
								.executes(c -> power(c, self(c), false))
								.then(Commands.argument("player", EntityArgument.player())
										.executes(c -> power(c, EntityArgument.getPlayer(c, "player"), false)))))
				.then(Commands.literal("tech")
						.then(Commands.argument("level", IntegerArgumentType.integer(0, 5))
								.executes(c -> tech(c, self(c)))
								.then(Commands.argument("player", EntityArgument.player())
										.executes(c -> tech(c, EntityArgument.getPlayer(c, "player"))))))
				.then(Commands.literal("energy")
						.then(Commands.argument("suit", StringArgumentType.word()).suggests(SUIT_IDS)
								.then(Commands.argument("amount", IntegerArgumentType.integer(0))
										.executes(c -> energy(c, self(c)))
										.then(Commands.argument("player", EntityArgument.player())
												.executes(c -> energy(c, EntityArgument.getPlayer(c, "player")))))))
				.then(Commands.literal("suit")
						.then(Commands.argument("suit", StringArgumentType.word()).suggests(SUIT_IDS)
								.executes(c -> summon(c, self(c)))
								.then(Commands.argument("player", EntityArgument.player())
										.executes(c -> summon(c, EntityArgument.getPlayer(c, "player"))))))
				.then(Commands.literal("part")
						.then(Commands.argument("suit", StringArgumentType.word()).suggests(SUIT_IDS)
								.then(Commands.argument("part", StringArgumentType.word())
										.suggests((ctx, b) -> SharedSuggestionProvider.suggest(
												new String[] {"helmet", "chestplate", "leggings", "boots"}, b))
										.executes(c -> part(c, self(c))))))
				.then(Commands.literal("status")
						.executes(c -> status(c, self(c)))
						.then(Commands.argument("player", EntityArgument.player())
								.executes(c -> status(c, EntityArgument.getPlayer(c, "player")))));
	}

	private static ServerPlayer self(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		return c.getSource().getPlayerOrException();
	}

	private static int power(CommandContext<CommandSourceStack> c, ServerPlayer target, boolean grant) {
		if (grant) {
			boolean ok = TonyStark.grant(target);
			c.getSource().sendSuccess(() -> Component.literal((ok ? "Granted Tony Stark to " : "Already had Tony Stark: ")
					+ target.getGameProfile().getName()), true);
			return ok ? 1 : 0;
		}
		TonyStark.revoke(target);
		c.getSource().sendSuccess(() -> Component.literal("Revoked Tony Stark from " + target.getGameProfile().getName()), true);
		return 1;
	}

	private static int tech(CommandContext<CommandSourceStack> c, ServerPlayer target) {
		int level = IntegerArgumentType.getInteger(c, "level");
		if (!TonyStark.hasPower(target)) {
			TonyStark.grant(target);
		}
		TonyStark.unlockTech(target, level);
		c.getSource().sendSuccess(() -> Component.literal("Tech level -> " + level + " for " + target.getGameProfile().getName()), true);
		return 1;
	}

	private static int energy(CommandContext<CommandSourceStack> c, ServerPlayer target) {
		String suitId = StringArgumentType.getString(c, "suit");
		int amount = IntegerArgumentType.getInteger(c, "amount");
		if (IronManSuits.byId(suitId) == null) {
			c.getSource().sendFailure(Component.literal("Unknown suit " + suitId));
			return 0;
		}
		IronManEnergy.setEnergy(target, suitId, amount);
		c.getSource().sendSuccess(() -> Component.literal(suitId + " energy -> " + amount), true);
		return 1;
	}

	private static int summon(CommandContext<CommandSourceStack> c, ServerPlayer target) {
		String suitId = StringArgumentType.getString(c, "suit");
		if (IronManSuits.byId(suitId) == null) {
			c.getSource().sendFailure(Component.literal("Unknown suit " + suitId));
			return 0;
		}
		boolean ok = IronManSuitSummonManager.summon(target, suitId);
		c.getSource().sendSuccess(() -> Component.literal((ok ? "Summoning " : "Could not summon ") + suitId), true);
		return ok ? 1 : 0;
	}

	private static int part(CommandContext<CommandSourceStack> c, ServerPlayer target) {
		String suitId = StringArgumentType.getString(c, "suit");
		String partName = StringArgumentType.getString(c, "part");
		net.minecraft.world.item.ArmorItem.Type type = switch (partName) {
			case "helmet" -> net.minecraft.world.item.ArmorItem.Type.HELMET;
			case "chestplate" -> net.minecraft.world.item.ArmorItem.Type.CHESTPLATE;
			case "leggings" -> net.minecraft.world.item.ArmorItem.Type.LEGGINGS;
			case "boots" -> net.minecraft.world.item.ArmorItem.Type.BOOTS;
			default -> null;
		};
		if (type == null || IronManSuits.byId(suitId) == null) {
			c.getSource().sendFailure(Component.literal("usage: /ironman part <suit> <helmet|chestplate|leggings|boots>"));
			return 0;
		}
		boolean ok = com.projecthero.mod.ironman.suit.IronManSuitSummonManager.summonPart(target, suitId, type);
		c.getSource().sendSuccess(() -> Component.literal((ok ? "Summoning " : "Could not summon ") + suitId + " " + partName), true);
		return ok ? 1 : 0;
	}

	private static int status(CommandContext<CommandSourceStack> c, ServerPlayer target) {
		var s = TonyStark.state(target);
		String worn = IronManArmor.wornSuitId(target);
		c.getSource().sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT,
				"Tony Stark: %s | tech %d | built %s | active suit: %s | wearing: %s",
				s.hasPower, s.techLevel, TonyStark.builtSuitIds(target), s.activeSuit.isEmpty() ? "<none>" : s.activeSuit,
				worn == null ? "<none>" : worn)), false);
		return 1;
	}
}
