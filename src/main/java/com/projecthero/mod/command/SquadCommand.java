package com.projecthero.mod.command;

import java.util.List;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import com.projecthero.mod.squad.Squad;
import com.projecthero.mod.squad.SquadManager;
import com.projecthero.mod.squad.Squads;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /squad} -- player-facing squad management.
 *
 * <p>The only top-level command root this mod registers besides {@code /projecthero} (see
 * {@link ProjectHeroCommand}), and deliberately so: everything under {@code /projecthero} is admin
 * tooling, whereas this is something every player uses constantly and mid-fight. It is also mirrored at
 * {@code /projecthero squad} so it is discoverable from the mod's own root.
 *
 * <pre>
 *   /squad                       what squad am I in?
 *   /squad create &lt;name&gt;         start one (you become its leader)
 *   /squad invite &lt;player&gt;       offer a place -- the invite stands for 60 s
 *   /squad accept [name]         take up an offer
 *   /squad decline               turn every standing offer down
 *   /squad leave                 walk away (the leader leaving hands it on)
 *   /squad kick &lt;player&gt;         leader only
 *   /squad rename &lt;name&gt;         leader only
 *   /squad disband               leader only
 * </pre>
 *
 * <p>No permission level is required for any of it: a squad is a social arrangement, not an admin one.
 */
public final class SquadCommand {
	private SquadCommand() {
	}

	public static void initialize(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(build());
	}

	public static LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("squad")
				.executes(ctx -> status(ctx.getSource()))
				.then(Commands.literal("create")
						.then(Commands.argument("name", StringArgumentType.greedyString())
								.executes(ctx -> create(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
				.then(Commands.literal("invite")
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> invite(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
				.then(Commands.literal("accept")
						.executes(ctx -> accept(ctx.getSource(), null))
						.then(Commands.argument("name", StringArgumentType.greedyString())
								.executes(ctx -> accept(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
				.then(Commands.literal("decline").executes(ctx -> decline(ctx.getSource())))
				.then(Commands.literal("leave").executes(ctx -> leave(ctx.getSource())))
				.then(Commands.literal("list").executes(ctx -> status(ctx.getSource())))
				.then(Commands.literal("kick")
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> kick(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
				.then(Commands.literal("rename")
						.then(Commands.argument("name", StringArgumentType.greedyString())
								.executes(ctx -> rename(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
				.then(Commands.literal("disband").executes(ctx -> disband(ctx.getSource())));
	}

	// ---------------- subcommands ----------------

	private static int status(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		SquadManager manager = SquadManager.get(player.server);
		Squad squad = manager.squadOf(player.getUUID());
		if (squad == null) {
			source.sendSuccess(() -> Component.translatable("commands.projecthero.squad.none")
					.withStyle(ChatFormatting.GRAY), false);
			List<Squad> pending = manager.pendingInvites(player.getUUID(), player.level().getGameTime());
			for (Squad offer : pending) {
				source.sendSuccess(() -> Component.translatable("commands.projecthero.squad.pending", offer.name())
						.withStyle(ChatFormatting.YELLOW), false);
			}
			return 0;
		}
		source.sendSuccess(() -> Component.translatable("commands.projecthero.squad.header", squad.name(), squad.size())
				.withStyle(ChatFormatting.AQUA), false);
		for (var id : squad.members()) {
			ServerPlayer member = player.server.getPlayerList().getPlayer(id);
			String name = member != null ? member.getGameProfile().getName() : id.toString().substring(0, 8);
			boolean leader = id.equals(squad.leader());
			source.sendSuccess(() -> Component.literal(" - " + name + (leader ? " (leader)" : ""))
					.withStyle(member != null ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY), false);
		}
		return squad.size();
	}

	private static int create(CommandSourceStack source, String rawName)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		SquadManager manager = SquadManager.get(player.server);
		if (manager.squadOf(player.getUUID()) != null) {
			source.sendFailure(Component.translatable("commands.projecthero.squad.already_in"));
			return 0;
		}
		String name = clean(rawName);
		if (name.isEmpty()) {
			source.sendFailure(Component.translatable("commands.projecthero.squad.bad_name"));
			return 0;
		}
		Squad squad = manager.create(name, player.getUUID());
		source.sendSuccess(() -> Component.translatable("commands.projecthero.squad.created", squad.name())
				.withStyle(ChatFormatting.AQUA), false);
		return 1;
	}

	private static int invite(CommandSourceStack source, ServerPlayer target)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		SquadManager manager = SquadManager.get(player.server);
		Squad squad = manager.squadOf(player.getUUID());
		if (squad == null) {
			source.sendFailure(Component.translatable("commands.projecthero.squad.none"));
			return 0;
		}
		if (target.getUUID().equals(player.getUUID())) {
			source.sendFailure(Component.translatable("commands.projecthero.squad.invite_self"));
			return 0;
		}
		if (squad.has(target.getUUID())) {
			source.sendFailure(Component.translatable("commands.projecthero.squad.already_member", target.getGameProfile().getName()));
			return 0;
		}
		if (squad.size() >= SquadManager.MAX_MEMBERS) {
			source.sendFailure(Component.translatable("commands.projecthero.squad.full", SquadManager.MAX_MEMBERS));
			return 0;
		}
		manager.invite(squad, target.getUUID(), player.level().getGameTime());
		source.sendSuccess(() -> Component.translatable("commands.projecthero.squad.invited",
				target.getGameProfile().getName()).withStyle(ChatFormatting.AQUA), false);
		// A clickable acceptance: mid-fight, nobody wants to type a squad name.
		target.sendSystemMessage(Component.translatable("commands.projecthero.squad.invite_received",
						player.getGameProfile().getName(), squad.name())
				.withStyle(ChatFormatting.YELLOW)
				.append(Component.literal(" ")
						.append(Component.translatable("commands.projecthero.squad.accept_button")
								.withStyle(s -> s.withColor(ChatFormatting.GREEN).withBold(true)
										.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
												"/squad accept " + squad.name()))
										.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
												Component.translatable("commands.projecthero.squad.accept_hover")))))));
		return 1;
	}

	private static int accept(CommandSourceStack source, String name)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		SquadManager manager = SquadManager.get(player.server);
		if (manager.squadOf(player.getUUID()) != null) {
			source.sendFailure(Component.translatable("commands.projecthero.squad.already_in"));
			return 0;
		}
		long now = player.level().getGameTime();
		Squad squad = null;
		if (name == null || name.isBlank()) {
			squad = manager.pendingInvite(player.getUUID(), null, now);
		} else {
			String wanted = clean(name);
			for (Squad offer : manager.pendingInvites(player.getUUID(), now)) {
				if (offer.name().equalsIgnoreCase(wanted)) {
					squad = offer;
					break;
				}
			}
		}
		if (squad == null) {
			source.sendFailure(Component.translatable("commands.projecthero.squad.no_invite"));
			return 0;
		}
		if (squad.size() >= SquadManager.MAX_MEMBERS) {
			source.sendFailure(Component.translatable("commands.projecthero.squad.full", SquadManager.MAX_MEMBERS));
			return 0;
		}
		manager.addMember(squad, player.getUUID());
		Squads.broadcast(player, squad, Component.translatable("commands.projecthero.squad.joined",
				player.getGameProfile().getName(), squad.name()).withStyle(ChatFormatting.AQUA));
		return 1;
	}

	private static int decline(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		SquadManager.get(player.server).clearInvites(player.getUUID());
		source.sendSuccess(() -> Component.translatable("commands.projecthero.squad.declined")
				.withStyle(ChatFormatting.GRAY), false);
		return 1;
	}

	private static int leave(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		SquadManager manager = SquadManager.get(player.server);
		Squad squad = manager.squadOf(player.getUUID());
		if (squad == null) {
			source.sendFailure(Component.translatable("commands.projecthero.squad.none"));
			return 0;
		}
		Squads.broadcast(player, squad, Component.translatable("commands.projecthero.squad.left",
				player.getGameProfile().getName(), squad.name()).withStyle(ChatFormatting.GRAY));
		manager.removeMember(squad, player.getUUID());
		return 1;
	}

	private static int kick(CommandSourceStack source, ServerPlayer target)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		SquadManager manager = SquadManager.get(player.server);
		Squad squad = manager.squadOf(player.getUUID());
		if (squad == null) {
			source.sendFailure(Component.translatable("commands.projecthero.squad.none"));
			return 0;
		}
		if (!squad.leader().equals(player.getUUID())) {
			source.sendFailure(Component.translatable("commands.projecthero.squad.not_leader"));
			return 0;
		}
		if (!squad.has(target.getUUID()) || target.getUUID().equals(player.getUUID())) {
			source.sendFailure(Component.translatable("commands.projecthero.squad.not_member",
					target.getGameProfile().getName()));
			return 0;
		}
		Squads.broadcast(player, squad, Component.translatable("commands.projecthero.squad.kicked",
				target.getGameProfile().getName(), squad.name()).withStyle(ChatFormatting.GRAY));
		manager.removeMember(squad, target.getUUID());
		return 1;
	}

	private static int rename(CommandSourceStack source, String rawName)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		SquadManager manager = SquadManager.get(player.server);
		Squad squad = manager.squadOf(player.getUUID());
		if (squad == null) {
			source.sendFailure(Component.translatable("commands.projecthero.squad.none"));
			return 0;
		}
		if (!squad.leader().equals(player.getUUID())) {
			source.sendFailure(Component.translatable("commands.projecthero.squad.not_leader"));
			return 0;
		}
		String name = clean(rawName);
		if (name.isEmpty()) {
			source.sendFailure(Component.translatable("commands.projecthero.squad.bad_name"));
			return 0;
		}
		manager.rename(squad, name);
		Squads.broadcast(player, squad, Component.translatable("commands.projecthero.squad.renamed", name)
				.withStyle(ChatFormatting.AQUA));
		return 1;
	}

	private static int disband(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		SquadManager manager = SquadManager.get(player.server);
		Squad squad = manager.squadOf(player.getUUID());
		if (squad == null) {
			source.sendFailure(Component.translatable("commands.projecthero.squad.none"));
			return 0;
		}
		if (!squad.leader().equals(player.getUUID())) {
			source.sendFailure(Component.translatable("commands.projecthero.squad.not_leader"));
			return 0;
		}
		Squads.broadcast(player, squad, Component.translatable("commands.projecthero.squad.disbanded", squad.name())
				.withStyle(ChatFormatting.GRAY));
		manager.disband(squad);
		return 1;
	}

	/** Squad names are chat furniture: no formatting codes, no newlines, and short enough to fit a HUD. */
	private static String clean(String raw) {
		String name = raw.replaceAll("[\\u00a7\\r\\n]", "").trim();
		return name.length() > SquadManager.MAX_NAME_LENGTH
				? name.substring(0, SquadManager.MAX_NAME_LENGTH).trim()
				: name;
	}
}
