package com.projecthero.mod.command;

import com.projecthero.mod.maxsteel.MaxSteel;
import com.projecthero.mod.maxsteel.MaxSteelConfig;
import com.projecthero.mod.maxsteel.MaxSteelTransform;
import com.projecthero.mod.maxsteel.data.MaxSteelState;

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
 * Admin / testing commands for the Max Steel Hero-Tier power:
 * {@code /maxsteel power grant|revoke | energy <0-500> | transform on|off | status}. Op 2.
 *
 * <p>{@code power grant} is a shortcut past the Steel-crash-site / bonding progression, for testing;
 * the survival route is finding Steel and bonding with Level 30 or a T.U.R.B.O. Stabilizer.
 */
public final class MaxSteelCommand {
	private MaxSteelCommand() {
	}

	public static void initialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("maxsteel")
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
				.then(Commands.literal("energy")
						.then(Commands.argument("amount", FloatArgumentType.floatArg(0f, MaxSteelConfig.MAX_TURBO_ENERGY))
								.executes(c -> energy(c, self(c)))
								.then(Commands.argument("player", EntityArgument.player())
										.executes(c -> energy(c, EntityArgument.getPlayer(c, "player"))))))
				.then(Commands.literal("transform")
						.then(Commands.literal("on").executes(c -> transform(c, self(c), true)))
						.then(Commands.literal("off").executes(c -> transform(c, self(c), false))))
				.then(Commands.literal("spawnsteel").executes(c -> spawnSteel(c, self(c))))
				.then(Commands.literal("status").executes(c -> status(c, self(c)))));
	}

	private static ServerPlayer self(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		return c.getSource().getPlayerOrException();
	}

	private static int power(CommandContext<CommandSourceStack> c, ServerPlayer target, boolean grant) {
		if (grant) {
			// Command grant = always replace: wipe every power of every tier first.
			com.projecthero.mod.hero.HeroTiers.wipeAll(target);
			if (!MaxSteel.bond(target)) {
				c.getSource().sendFailure(Component.translatable("commands.projecthero.maxsteel.already"));
				return 0;
			}
		} else {
			MaxSteel.revoke(target);
		}
		c.getSource().sendSuccess(() -> Component.translatable(grant
				? "commands.projecthero.maxsteel.granted" : "commands.projecthero.maxsteel.revoked",
				target.getDisplayName()), true);
		return 1;
	}

	private static int energy(CommandContext<CommandSourceStack> c, ServerPlayer target) {
		float amount = FloatArgumentType.getFloat(c, "amount");
		MaxSteelState s = MaxSteel.state(target).copy();
		s.turboEnergy = Math.max(0f, Math.min(MaxSteelConfig.MAX_TURBO_ENERGY, amount));
		s.lastHighCostTick = 0L;
		MaxSteel.save(target, s);
		c.getSource().sendSuccess(() -> Component.translatable("commands.projecthero.maxsteel.energy_set",
				String.format(java.util.Locale.ROOT, "%.1f", MaxSteel.state(target).turboEnergy)), true);
		return 1;
	}

	private static int transform(CommandContext<CommandSourceStack> c, ServerPlayer target, boolean on) {
		if (!MaxSteel.hasPower(target)) {
			c.getSource().sendFailure(Component.translatable("commands.projecthero.maxsteel.no_power"));
			return 0;
		}
		boolean ok = on ? MaxSteelTransform.beginSuitUp(target, false) : MaxSteelTransform.beginSuitDown(target);
		c.getSource().sendSuccess(() -> Component.literal(ok ? (on ? "Suiting up" : "Suiting down")
				: "Nothing to do"), false);
		return ok ? 1 : 0;
	}

	private static int spawnSteel(CommandContext<CommandSourceStack> c, ServerPlayer target) {
		net.minecraft.world.phys.Vec3 look = target.getLookAngle();
		net.minecraft.world.phys.Vec3 at = target.getEyePosition().add(look.x * 3, 0.2, look.z * 3);
		com.projecthero.mod.maxsteel.entity.SteelEntity.spawn(target.serverLevel(), at.x, at.y, at.z);
		c.getSource().sendSuccess(() -> Component.literal("Spawned Steel."), false);
		return 1;
	}

	private static int status(CommandContext<CommandSourceStack> c, ServerPlayer target) {
		MaxSteelState s = MaxSteel.state(target);
		c.getSource().sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT,
				"Max Steel: %s | energy %.1f/%.0f | transformed %s | mode %s | dir %d | lockout %d",
				s.hasPower, s.turboEnergy, MaxSteelConfig.MAX_TURBO_ENERGY, s.transformed,
				s.modeEnum(), s.transformDir,
				Math.max(0L, s.lockoutUntil - target.level().getGameTime()))), false);
		return 1;
	}
}
