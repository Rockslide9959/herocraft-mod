package com.projecthero.mod.nova;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.projecthero.mod.nova.worldgen.NovaPodSitePiece;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.13 admin / test commands (op only), hung off {@code /projecthero}:
 * <ul>
 *   <li>{@code /projecthero nova force <0-100>} -- sets your Nova Force;</li>
 *   <li>{@code /projecthero nova site} -- builds a Crashed Nova Corps Pod (with its Centurion) 14 blocks in front of you.</li>
 * </ul>
 * The power itself is granted like every other hero's: {@code /projecthero power grant <player> nova}.
 */
public final class NovaCommand {
	private NovaCommand() {
	}

	public static LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("nova")
				.requires(source -> source.hasPermission(2))
				.then(Commands.literal("force")
						.then(Commands.argument("amount", FloatArgumentType.floatArg(0f, NovaConfig.FORCE_MAX))
								.executes(NovaCommand::setForce)))
				.then(Commands.literal("site").executes(NovaCommand::site));
	}

	private static int setForce(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		if (!Nova.hasPower(player)) {
			c.getSource().sendFailure(Component.literal("You do not carry the Nova Force"));
			return 0;
		}
		float v = FloatArgumentType.getFloat(c, "amount");
		Nova.setForce(player, v);
		c.getSource().sendSuccess(() -> Component.literal("Nova Force set to " + Math.round(v)), false);
		return 1;
	}

	private static int site(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		Vec3 f = Vec3.directionFromRotation(0, player.getYRot()).scale(14.0);
		int x = (int) Math.floor(player.getX() + f.x);
		int z = (int) Math.floor(player.getZ() + f.z);
		NovaPodSitePiece piece = buildSite(player.serverLevel(), x, z);
		var at = piece.center();
		c.getSource().sendSuccess(() -> Component.literal("Crashed Nova Corps Pod built at " + at.getX() + " " + at.getY() + " " + at.getZ()), true);
		return 1;
	}

	/** Builds a whole pod site centred on {@code x, z} right now (the command, the gametests). */
	public static NovaPodSitePiece buildSite(ServerLevel level, int x, int z) {
		level.getChunk(x >> 4, z >> 4);
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
		NovaPodSitePiece piece = new NovaPodSitePiece(x, y, z);
		piece.build(level, piece.getBoundingBox(), true, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES);
		return piece;
	}
}
