package com.herocraft.mod.command;

import com.herocraft.mod.titan.entity.DisguisedTitanEntity;
import com.herocraft.mod.titan.entity.TitanEntity;
import com.herocraft.mod.titan.entity.TitanEntityTypes;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;

/** Admin/testing commands for the Titan boss: {@code /titan disguised|titan|status}. Op 2. */
public final class TitanCommand {
	private TitanCommand() {
	}

	public static void initialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("titan")
				.requires(source -> source.hasPermission(2))
				.then(Commands.literal("disguised").executes(TitanCommand::spawnDisguised))
				.then(Commands.literal("titan").executes(TitanCommand::spawnTitan))
				.then(Commands.literal("status").executes(TitanCommand::status)));
	}

	private static Vec3 ahead(ServerPlayer player, double distance) {
		Vec3 look = player.getLookAngle();
		return player.position().add(look.x * distance, 0, look.z * distance);
	}

	private static int spawnDisguised(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		ServerLevel level = player.serverLevel();
		Vec3 at = ahead(player, 5.0);
		DisguisedTitanEntity e = TitanEntityTypes.DISGUISED_TITAN.create(level);
		if (e == null) {
			c.getSource().sendFailure(Component.literal("Could not create the disguised Titan."));
			return 0;
		}
		e.moveTo(at.x, player.getY(), at.z, player.getYRot() + 180f, 0f);
		e.finalizeSpawn(level, level.getCurrentDifficultyAt(e.blockPosition()), MobSpawnType.COMMAND, null);
		e.setPersistenceRequired();
		level.addFreshEntity(e);
		c.getSource().sendSuccess(() -> Component.literal("Spawned the disguised Titan ahead. Kill it to trigger the transformation."), true);
		return 1;
	}

	private static int spawnTitan(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		ServerLevel level = player.serverLevel();
		Vec3 at = ahead(player, 12.0);
		TitanEntity e = TitanEntityTypes.TITAN.create(level);
		if (e == null) {
			c.getSource().sendFailure(Component.literal("Could not create the Titan."));
			return 0;
		}
		e.moveTo(at.x, player.getY(), at.z, player.getYRot() + 180f, 0f);
		level.addFreshEntity(e);
		e.onTransformed();
		c.getSource().sendSuccess(() -> Component.literal("Spawned the true Titan directly (testing shortcut)."), true);
		return 1;
	}

	private static int status(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		ServerLevel level = player.serverLevel();
		int disguised = level.getEntitiesOfClass(DisguisedTitanEntity.class, player.getBoundingBox().inflate(1024)).size();
		int titans = level.getEntitiesOfClass(TitanEntity.class, player.getBoundingBox().inflate(1024)).size();
		c.getSource().sendSuccess(() -> Component.literal(
				"Nearby (1024 blocks): " + disguised + " disguised, " + titans + " active Titan(s)."), false);
		return 1;
	}
}
