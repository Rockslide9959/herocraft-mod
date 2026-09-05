package com.herocraft.mod.command;

import com.herocraft.mod.hero.ExperimentalPowers;
import com.herocraft.mod.hero.Power;
import com.herocraft.mod.hero.Powers;
import com.herocraft.mod.spider.SpiderMan;
import com.herocraft.mod.spider.SpiderWebReserve;
import com.herocraft.mod.spider.data.SpiderManState;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Admin / testing commands for the Spider-Man Hero Class:
 * {@code /spiderman power grant|revoke | web <amount> | status}. Op 2.
 *
 * <p>The real route is the survival one -- mutate into Spider Climbing / Adhesion, craft an Arachnid
 * Mutagen, use it -- and {@code grant} deliberately goes through that same evolution, granting the
 * prerequisite power first if it is missing so the tested path is the shipped path rather than a
 * shortcut around it.
 */
public final class SpiderManCommand {
	private SpiderManCommand() {
	}

	public static void initialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("spiderman")
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
				.then(Commands.literal("web")
						.then(Commands.argument("amount", FloatArgumentType.floatArg(0.0f, SpiderWebReserve.MAX))
								.executes(c -> web(c, self(c)))
								.then(Commands.argument("player", EntityArgument.player())
										.executes(c -> web(c, EntityArgument.getPlayer(c, "player"))))))
				.then(Commands.literal("symbiote")
						.then(Commands.literal("give")
								.executes(c -> symbiote(c, self(c), true))
								.then(Commands.argument("player", EntityArgument.player())
										.executes(c -> symbiote(c, EntityArgument.getPlayer(c, "player"), true))))
						.then(Commands.literal("remove")
								.executes(c -> symbiote(c, self(c), false))
								.then(Commands.argument("player", EntityArgument.player())
										.executes(c -> symbiote(c, EntityArgument.getPlayer(c, "player"), false))))
						.then(Commands.literal("spawn").executes(c -> spawnSymbiote(c, self(c))))
						.then(Commands.literal("host").executes(c -> spawnHost(c, self(c)))))
				.then(Commands.literal("status").executes(c -> status(c, self(c)))));
	}

	/** Drop a free-floating Symbiote 3 blocks ahead (testing the bond flow / meteor / lab spawn). */
	private static int spawnSymbiote(CommandContext<CommandSourceStack> c, ServerPlayer player) {
		net.minecraft.world.phys.Vec3 look = player.getLookAngle();
		net.minecraft.world.phys.Vec3 at = player.position().add(look.x * 3.0, 0.5, look.z * 3.0);
		com.herocraft.mod.symbiote.entity.SymbioteEntity.spawn(player.serverLevel(), at.x, at.y, at.z);
		c.getSource().sendSuccess(() -> Component.literal("Spawned a free Symbiote ahead."), false);
		return 1;
	}

	/** Turn the mob the player is looking at (or the nearest hostile) into a Symbiote Host. */
	private static int spawnHost(CommandContext<CommandSourceStack> c, ServerPlayer player) {
		net.minecraft.world.entity.Mob target = player.serverLevel().getEntitiesOfClass(
				net.minecraft.world.entity.Mob.class, player.getBoundingBox().inflate(12.0),
				m -> m instanceof net.minecraft.world.entity.monster.Monster && m.isAlive()
						&& !com.herocraft.mod.symbiote.SymbioteHost.is(m))
				.stream().min(java.util.Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
		if (target == null) {
			c.getSource().sendFailure(Component.literal("No eligible hostile mob within 12 blocks."));
			return 0;
		}
		com.herocraft.mod.symbiote.SymbioteHost.mark(target);
		c.getSource().sendSuccess(() -> Component.literal("Marked " + target.getName().getString()
				+ " as a Symbiote Host."), false);
		return 1;
	}

	private static int symbiote(CommandContext<CommandSourceStack> c, ServerPlayer target, boolean give) {
		// v0.9.14: the Symbiote is a standalone power now -- no longer gated on Spider-Man here. See
		// the dedicated /symbiote command for the full standalone verb set (bond/purge/activate/etc).
		boolean changed = give
				? com.herocraft.mod.symbiote.Symbiote.grant(target)
				: com.herocraft.mod.symbiote.Symbiote.remove(target);
		if (!changed) {
			c.getSource().sendFailure(Component.translatable(give
					? "commands.herocraft.spiderman.symbiote_already"
					: "commands.herocraft.spiderman.symbiote_none"));
			return 0;
		}
		c.getSource().sendSuccess(() -> Component.translatable(give
				? "commands.herocraft.spiderman.symbiote_given"
				: "commands.herocraft.spiderman.symbiote_removed", target.getDisplayName()), true);
		return 1;
	}

	private static ServerPlayer self(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		return c.getSource().getPlayerOrException();
	}

	private static int power(CommandContext<CommandSourceStack> c, ServerPlayer target, boolean grant) {
		if (grant) {
			if (SpiderMan.hasPower(target)) {
				c.getSource().sendFailure(Component.translatable("commands.herocraft.spiderman.already"));
				return 0;
			}
			// Command grant = always replace: wipe every power of every tier, then walk the real
			// evolution path (grant the Spider Adhesion prerequisite, evolve it).
			com.herocraft.mod.hero.HeroTiers.wipeAll(target);
			if (!SpiderMan.hasSpiderAdhesion(target)) {
				Power adhesion = Powers.byKey(SpiderMan.SPIDER_ADHESION_KEY);
				if (adhesion == null || !ExperimentalPowers.grant(target, adhesion)) {
					c.getSource().sendFailure(Component.translatable("commands.herocraft.spiderman.no_adhesion"));
					return 0;
				}
			}
			if (!SpiderMan.evolveFromAdhesion(target)) {
				c.getSource().sendFailure(Component.translatable("commands.herocraft.spiderman.no_adhesion"));
				return 0;
			}
		} else {
			SpiderMan.revoke(target);
		}
		c.getSource().sendSuccess(() -> Component.translatable(grant
				? "commands.herocraft.spiderman.granted" : "commands.herocraft.spiderman.revoked",
				target.getDisplayName()), true);
		return 1;
	}

	private static int web(CommandContext<CommandSourceStack> c, ServerPlayer target) {
		float amount = FloatArgumentType.getFloat(c, "amount");
		SpiderMan.setWebReserve(target, amount);
		c.getSource().sendSuccess(() -> Component.translatable("commands.herocraft.spiderman.web_set",
				String.format(java.util.Locale.ROOT, "%.1f", SpiderMan.state(target).webReserve)), true);
		return 1;
	}

	private static int status(CommandContext<CommandSourceStack> c, ServerPlayer target) {
		SpiderManState s = SpiderMan.state(target);
		com.herocraft.mod.symbiote.SymbioteState sym = com.herocraft.mod.symbiote.Symbiote.state(target);
		c.getSource().sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT,
				"Spider-Man: %s | web %.1f/%.0f | swinging %s | climb mode %d | adhesion owned %s | symbiote bonded %s active %s",
				s.hasPower, s.webReserve, SpiderWebReserve.maxFor(target), s.swinging, s.climbMode(),
				SpiderMan.hasSpiderAdhesion(target), sym.hasSymbiote, sym.active)), false);
		return 1;
	}
}
