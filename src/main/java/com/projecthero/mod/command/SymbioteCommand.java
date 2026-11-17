package com.projecthero.mod.command;

import com.projecthero.mod.symbiote.Symbiote;
import com.projecthero.mod.symbiote.SymbioteCompatibility;
import com.projecthero.mod.symbiote.SymbioteHostType;
import com.projecthero.mod.symbiote.SymbioteState;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;


import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Admin/testing commands for the standalone Symbiote power (v0.9.14):
 * {@code /symbiote give|remove|bond|purge|activate|deactivate|status [player]}. Op 2.
 *
 * <p>{@code give}/{@code bond} are the same verb (aliased, since "bond" is the spec's own wording);
 * both go through {@link Symbiote#grant} directly -- unlike the real world encounter
 * ({@link com.projecthero.mod.symbiote.SymbioteBonding}), a command grant never purges anything on its
 * own, since an operator testing "does the Symbiote suit work" on a player who happens to have Thor
 * should not lose Thor as a side effect. Use {@code /symbiote purge} explicitly to test the purge
 * behaviour, or bond via the real in-world encounter.
 */
public final class SymbioteCommand {
	private SymbioteCommand() {
	}


	public static LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("symbiote")
				.requires(source -> source.hasPermission(2))
				.then(Commands.literal("give").executes(c -> bond(c, self(c)))
						.then(Commands.argument("player", EntityArgument.player())
								.executes(c -> bond(c, EntityArgument.getPlayer(c, "player")))))
				.then(Commands.literal("bond").executes(c -> bond(c, self(c)))
						.then(Commands.argument("player", EntityArgument.player())
								.executes(c -> bond(c, EntityArgument.getPlayer(c, "player")))))
				.then(Commands.literal("remove").executes(c -> remove(c, self(c)))
						.then(Commands.argument("player", EntityArgument.player())
								.executes(c -> remove(c, EntityArgument.getPlayer(c, "player")))))
				.then(Commands.literal("purge").executes(c -> purge(c, self(c)))
						.then(Commands.argument("player", EntityArgument.player())
								.executes(c -> purge(c, EntityArgument.getPlayer(c, "player")))))
				.then(Commands.literal("activate").executes(c -> setActive(c, self(c), true))
						.then(Commands.argument("player", EntityArgument.player())
								.executes(c -> setActive(c, EntityArgument.getPlayer(c, "player"), true))))
				.then(Commands.literal("deactivate").executes(c -> setActive(c, self(c), false))
						.then(Commands.argument("player", EntityArgument.player())
								.executes(c -> setActive(c, EntityArgument.getPlayer(c, "player"), false))))
				.then(Commands.literal("status").executes(c -> status(c, self(c)))
						.then(Commands.argument("player", EntityArgument.player())
								.executes(c -> status(c, EntityArgument.getPlayer(c, "player")))))
				// v0.17.5: the suit colours -- /projecthero power tune symbiote colour <preset> [player] | colour hex <body> <emblem> <eyes> [player]
				.then(colour())
				// v0.17.5: the suit model -- /projecthero power tune symbiote style <classic|lethal_protector> [player] (colours kept)
				.then(style());
	}

	private static LiteralArgumentBuilder<CommandSourceStack> style() {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("style");
		for (com.projecthero.mod.symbiote.SymbioteSuitStyle style : com.projecthero.mod.symbiote.SymbioteSuitStyle.values()) {
			root.then(Commands.literal(style.id()).executes(c -> setStyle(c, self(c), style))
					.then(Commands.argument("player", EntityArgument.player())
							.executes(c -> setStyle(c, EntityArgument.getPlayer(c, "player"), style))));
		}
		return root;
	}

	private static int setStyle(CommandContext<CommandSourceStack> c, ServerPlayer target,
			com.projecthero.mod.symbiote.SymbioteSuitStyle style) {
		com.projecthero.mod.symbiote.SymbioteSuitColours.setStyle(target, style);
		c.getSource().sendSuccess(() -> Component.literal(target.getGameProfile().getName() + "'s Symbiote suit style: " + style.id()), true);
		return 1;
	}

	private static LiteralArgumentBuilder<CommandSourceStack> colour() {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("colour");
		for (com.projecthero.mod.symbiote.SymbioteColourPreset preset : com.projecthero.mod.symbiote.SymbioteColourPreset.values()) {
			root.then(Commands.literal(preset.id()).executes(c -> colourPreset(c, self(c), preset))
					.then(Commands.argument("player", EntityArgument.player())
							.executes(c -> colourPreset(c, EntityArgument.getPlayer(c, "player"), preset))));
		}
		root.then(Commands.literal("hex")
				.then(Commands.argument("body", com.mojang.brigadier.arguments.StringArgumentType.word())
						.then(Commands.argument("emblem", com.mojang.brigadier.arguments.StringArgumentType.word())
								.then(Commands.argument("eyes", com.mojang.brigadier.arguments.StringArgumentType.word())
										.executes(c -> colourHex(c, self(c)))
										.then(Commands.argument("player", EntityArgument.player())
												.executes(c -> colourHex(c, EntityArgument.getPlayer(c, "player"))))))));
		return root;
	}

	private static int colourPreset(CommandContext<CommandSourceStack> c, ServerPlayer target,
			com.projecthero.mod.symbiote.SymbioteColourPreset preset) {
		com.projecthero.mod.symbiote.SymbioteSuitColours.setPreset(target, preset);
		c.getSource().sendSuccess(() -> Component.literal(target.getGameProfile().getName() + "'s Symbiote suit: " + preset.id()), true);
		return 1;
	}

	private static int colourHex(CommandContext<CommandSourceStack> c, ServerPlayer target) {
		var body = com.projecthero.mod.symbiote.SymbioteColours.parseHex(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "body"));
		var emblem = com.projecthero.mod.symbiote.SymbioteColours.parseHex(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "emblem"));
		var eyes = com.projecthero.mod.symbiote.SymbioteColours.parseHex(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "eyes"));
		if (body.isEmpty() || emblem.isEmpty() || eyes.isEmpty()) {
			c.getSource().sendFailure(Component.literal("Colours are hex RGB, like #14161F or 1e4fb8."));
			return 0;
		}
		var colours = new com.projecthero.mod.symbiote.SymbioteColours(body.get(), emblem.get(), eyes.get());
		com.projecthero.mod.symbiote.SymbioteSuitColours.setColours(target, colours);
		c.getSource().sendSuccess(() -> Component.literal(target.getGameProfile().getName() + "'s Symbiote suit: "
				+ com.projecthero.mod.symbiote.SymbioteColours.hex(colours.primary()) + " / "
				+ com.projecthero.mod.symbiote.SymbioteColours.hex(colours.secondary()) + " / "
				+ com.projecthero.mod.symbiote.SymbioteColours.hex(colours.eyes())), true);
		return 1;
	}

	private static int bond(CommandContext<CommandSourceStack> c, ServerPlayer target) {
		boolean ok = Symbiote.grant(target);
		c.getSource().sendSuccess(() -> Component.literal(ok
				? target.getGameProfile().getName() + " has bonded with the Symbiote."
				: target.getGameProfile().getName() + " has already bonded with a Symbiote."), true);
		return ok ? 1 : 0;
	}

	private static int remove(CommandContext<CommandSourceStack> c, ServerPlayer target) {
		boolean ok = Symbiote.remove(target);
		c.getSource().sendSuccess(() -> Component.literal(ok
				? "Separated the Symbiote from " + target.getGameProfile().getName() + "."
				: target.getGameProfile().getName() + " has no Symbiote bond."), true);
		return ok ? 1 : 0;
	}

	/** Purge every power the Symbiote is incompatible with -- Spider-Man is left untouched. */
	private static int purge(CommandContext<CommandSourceStack> c, ServerPlayer target) {
		boolean had = SymbioteCompatibility.hasIncompatiblePower(target);
		SymbioteCompatibility.purge(target);
		c.getSource().sendSuccess(() -> Component.literal(had
				? "Purged every Symbiote-incompatible power from " + target.getGameProfile().getName() + "."
				: target.getGameProfile().getName() + " had nothing to purge."), true);
		return 1;
	}

	private static int setActive(CommandContext<CommandSourceStack> c, ServerPlayer target, boolean wantActive) {
		if (!Symbiote.hasSymbiote(target)) {
			c.getSource().sendFailure(Component.literal(target.getGameProfile().getName() + " has no Symbiote bond."));
			return 0;
		}
		if (Symbiote.isActive(target) == wantActive) {
			c.getSource().sendFailure(Component.literal(target.getGameProfile().getName()
					+ " is already " + (wantActive ? "active" : "inactive") + "."));
			return 0;
		}
		Symbiote.state(target).toggleReadyAt = 0L; // command bypasses the anti-spam gate
		Symbiote.toggle(target);
		c.getSource().sendSuccess(() -> Component.literal((wantActive ? "Activating" : "Deactivating")
				+ " the Symbiote for " + target.getGameProfile().getName() + "."), true);
		return 1;
	}

	private static int status(CommandContext<CommandSourceStack> c, ServerPlayer target) {
		SymbioteState s = Symbiote.state(target);
		String hostType = Symbiote.hasSymbiote(target) ? SymbioteHostType.of(target).name() : "-";
		c.getSource().sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT,
				"%s: bonded=%s active=%s hostType=%s",
				target.getGameProfile().getName(), s.hasSymbiote, s.active, hostType)), false);
		return 1;
	}

	private static ServerPlayer self(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		return c.getSource().getPlayerOrException();
	}
}
