package com.projecthero.mod.kryptonian;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.projecthero.mod.kryptonian.meteor.MeteorManager;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.8 admin / test commands (op only), hung off {@code /projecthero}:
 * <ul>
 *   <li>{@code /projecthero meteor} -- a Kryptonite Meteor falls 80-200 blocks from you, exactly like the nightly event;</li>
 *   <li>{@code /projecthero meteor here} -- one falls ~15 blocks in front of you (quick testing);</li>
 *   <li>{@code /projecthero kryptonian solar <0-100>} -- sets your Solar Energy.</li>
 * </ul>
 */
public final class KryptonianCommand {
	private KryptonianCommand() {
	}

	public static LiteralArgumentBuilder<CommandSourceStack> buildMeteor() {
		return Commands.literal("meteor")
				.requires(source -> source.hasPermission(2))
				.executes(KryptonianCommand::meteorNear)
				.then(Commands.literal("here").executes(KryptonianCommand::meteorHere));
	}

	public static LiteralArgumentBuilder<CommandSourceStack> buildKryptonian() {
		return Commands.literal("kryptonian")
				.requires(source -> source.hasPermission(2))
				.then(Commands.literal("solar")
						.then(Commands.argument("amount", FloatArgumentType.floatArg(0f, KryptonianConfig.SOLAR_MAX))
								.executes(KryptonianCommand::setSolar)));
	}

	private static int meteorNear(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		MeteorManager.Pending p = MeteorManager.summonNear(player);
		if (p == null) {
			c.getSource().sendFailure(Component.literal("No spot for a meteor near you"));
			return 0;
		}
		BlockPos t = p.target();
		c.getSource().sendSuccess(() -> Component.literal("A Kryptonite Meteor is falling at " + t.getX() + " " + t.getY() + " " + t.getZ()), true);
		return 1;
	}

	private static int meteorHere(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		Vec3 f = Vec3.directionFromRotation(0, player.getYRot()).scale(15.0);
		int x = (int) Math.floor(player.getX() + f.x);
		int z = (int) Math.floor(player.getZ() + f.z);
		player.serverLevel().getChunk(x >> 4, z >> 4);
		int y = player.serverLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
		BlockPos target = new BlockPos(x, y, z);
		MeteorManager.launch(player.serverLevel(), target, KryptonianConfig.METEOR_FALL_TICKS);
		c.getSource().sendSuccess(() -> Component.literal("A Kryptonite Meteor is falling at " + x + " " + y + " " + z), true);
		return 1;
	}

	private static int setSolar(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		if (!Kryptonian.hasPower(player)) {
			c.getSource().sendFailure(Component.literal("You are not a Kryptonian"));
			return 0;
		}
		float v = FloatArgumentType.getFloat(c, "amount");
		Kryptonian.setSolar(player, v);
		c.getSource().sendSuccess(() -> Component.literal("Solar Energy set to " + Math.round(v)), false);
		return 1;
	}
}
