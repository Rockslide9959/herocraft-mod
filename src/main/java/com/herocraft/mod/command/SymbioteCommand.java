package com.herocraft.mod.command;

import com.herocraft.mod.symbiote.Symbiote;
import com.herocraft.mod.symbiote.SymbioteCompatibility;
import com.herocraft.mod.symbiote.SymbioteHostType;
import com.herocraft.mod.symbiote.SymbioteState;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

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
 * ({@link com.herocraft.mod.symbiote.SymbioteBonding}), a command grant never purges anything on its
 * own, since an operator testing "does the Symbiote suit work" on a player who happens to have Thor
 * should not lose Thor as a side effect. Use {@code /symbiote purge} explicitly to test the purge
 * behaviour, or bond via the real in-world encounter.
 */
public final class SymbioteCommand {
	private SymbioteCommand() {
	}

	public static void initialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("symbiote")
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
								.executes(c -> status(c, EntityArgument.getPlayer(c, "player"))))));
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
