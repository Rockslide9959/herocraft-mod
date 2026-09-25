package com.projecthero.mod.command;

import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.titanshifter.TitanPhase;
import com.projecthero.mod.titanshifter.TitanShifter;
import com.projecthero.mod.titanshifter.TitanType;
import com.projecthero.mod.titanshifter.data.TitanShifterState;
import com.projecthero.mod.titanshifter.entity.TitanFormEntity;
import com.projecthero.mod.titanshifter.entity.TitanShifterEntities;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Admin / debug commands for the Titan Shifter: {@code /projecthero titanshifter grant|remove [player]},
 * {@code transform|revert [player]} (forced, skipping the cooldown), {@code spawn} (an ownerless test Titan
 * you can punch) and {@code status}. Op level 2, like every other power command.
 */
public final class TitanShifterCommand {
	private TitanShifterCommand() {
	}

	public static LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("titanshifter")
				.requires(s -> s.hasPermission(2))
				.then(Commands.literal("grant")
						.executes(c -> grant(c, self(c)))
						.then(Commands.argument("player", EntityArgument.player())
								.executes(c -> grant(c, EntityArgument.getPlayer(c, "player")))))
				.then(Commands.literal("remove")
						.executes(c -> remove(c, self(c)))
						.then(Commands.argument("player", EntityArgument.player())
								.executes(c -> remove(c, EntityArgument.getPlayer(c, "player")))))
				.then(Commands.literal("transform")
						.executes(c -> transform(c, self(c)))
						.then(Commands.argument("player", EntityArgument.player())
								.executes(c -> transform(c, EntityArgument.getPlayer(c, "player")))))
				.then(Commands.literal("revert")
						.executes(c -> revert(c, self(c)))
						.then(Commands.argument("player", EntityArgument.player())
								.executes(c -> revert(c, EntityArgument.getPlayer(c, "player")))))
				.then(Commands.literal("spawn").executes(TitanShifterCommand::spawn))
				.then(Commands.literal("status").executes(c -> status(c, self(c))));
	}

	/** Also used by {@code /projecthero power grant titan_shifter}. */
	public static int grant(CommandContext<CommandSourceStack> c, ServerPlayer target) {
		String name = target.getGameProfile().getName();
		if (!TitanShifter.grant(target)) {
			c.getSource().sendFailure(Component.literal(name + " already has Titan Shifting"));
			return 0;
		}
		c.getSource().sendSuccess(() -> Component.literal("Granted Titan Shifting to " + name), true);
		return 1;
	}

	private static int remove(CommandContext<CommandSourceStack> c, ServerPlayer target) {
		TitanShifter.revoke(target);
		c.getSource().sendSuccess(() -> Component.literal("Removed Titan Shifting from " + target.getGameProfile().getName()), true);
		return 1;
	}

	private static int transform(CommandContext<CommandSourceStack> c, ServerPlayer target) {
		if (!TitanShifter.state(target).unlocked && !TitanShifter.grant(target)) {
			return 0;
		}
		// forced: clear the cooldown first
		TitanShifterState s = TitanShifter.state(target).copy();
		s.cooldownUntil = 0L;
		target.setAttached(com.projecthero.mod.attachment.ModAttachments.TITAN_SHIFTER_STATE, s);
		boolean ok = TitanShifter.transform(target);
		if (!ok) {
			c.getSource().sendFailure(Component.literal("Could not transform " + target.getGameProfile().getName()
					+ " (not human, no room, or already riding)"));
		}
		return ok ? 1 : 0;
	}

	private static int revert(CommandContext<CommandSourceStack> c, ServerPlayer target) {
		TitanShifterState s = TitanShifter.state(target);
		if (s.phase() == TitanPhase.TITAN) {
			TitanShifter.revert(target);
			return 1;
		}
		if (s.phase() != TitanPhase.HUMAN) {
			TitanShifter.forceEnd(target, false);
			return 1;
		}
		c.getSource().sendFailure(Component.literal(target.getGameProfile().getName() + " is not a Titan"));
		return 0;
	}

	private static int spawn(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = self(c);
		ServerLevel level = (ServerLevel) p.level();
		TitanFormEntity dummy = TitanShifterEntities.TITAN_FORM.create(level);
		if (dummy == null) {
			return 0;
		}
		dummy.bindDummy(TitanType.GENERIC_TITAN);
		net.minecraft.world.phys.Vec3 look = p.getLookAngle();
		dummy.moveTo(p.getX() + look.x * 8, p.getY(), p.getZ() + look.z * 8, p.getYRot() + 180.0f, 0.0f);
		level.addFreshEntity(dummy);
		c.getSource().sendSuccess(() -> Component.literal("Spawned a test Titan (ownerless; it does nothing)"), true);
		return 1;
	}

	private static int status(CommandContext<CommandSourceStack> c, ServerPlayer p) {
		TitanShifterState s = TitanShifter.state(p);
		long now = p.level().getGameTime();
		c.getSource().sendSuccess(() -> Component.literal("TitanShifter unlocked=" + s.unlocked + " phase=" + s.phase()
				+ " type=" + s.typeId + " hp=" + s.titanHealth + "/" + s.titanMaxHealth
				+ " transformCooldown=" + Math.max(0, (s.cooldownUntil - now) / 20) + "s primary="
				+ HeroTiers.holdsHero(p, TitanShifter.KEY)), false);
		return 1;
	}

	private static ServerPlayer self(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		return c.getSource().getPlayerOrException();
	}
}
