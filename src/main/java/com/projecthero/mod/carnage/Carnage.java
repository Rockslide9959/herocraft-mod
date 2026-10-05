package com.projecthero.mod.carnage;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.projecthero.mod.carnage.entity.CarnageEntity;
import com.projecthero.mod.carnage.entity.CrimsonSpawnEntity;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * v0.14.25: Carnage -- one entry point, called from {@code ProjectHeroMod.onInitialize}. See {@link CarnageEntity} and
 * {@code docs/CARNAGE_REFERENCE.md}.
 */
public final class Carnage {
	private Carnage() {
	}

	public static void initialize() {
		CarnageEntityTypes.initialize();
		CarnageItems.initialize();
	}

	/**
	 * {@code /projecthero raid <verb> carnage}: {@code start} drops a meteor 12-20 blocks from you; {@code end} removes
	 * every Carnage and his brood; the timer verbs have nothing to do.
	 */
	public static int command(CommandContext<CommandSourceStack> c, String verb) throws CommandSyntaxException {
		switch (verb) {
			case "start" -> {
				ServerPlayer player = c.getSource().getPlayerOrException();
				if (!CarnageSpawner.dropNear(player.serverLevel(), player, 12, 20)) {
					c.getSource().sendFailure(Component.literal("No open ground nearby for the meteor."));
					return 0;
				}
				c.getSource().sendSuccess(() -> Component.literal("A crimson meteor is falling."), true);
				return 1;
			}
			case "end" -> {
				int n = 0;
				for (ServerLevel level : c.getSource().getServer().getAllLevels()) {
					for (Entity e : level.getAllEntities()) {
						if (e instanceof CarnageEntity || e instanceof CrimsonSpawnEntity) {
							e.discard();
							n++;
						}
					}
				}
				int removed = n;
				c.getSource().sendSuccess(() -> Component.literal("Removed " + removed + " Carnage entities."), true);
				return n;
			}
			default -> {
				c.getSource().sendSuccess(() -> Component.literal("Carnage has no timer."), false);
				return 0;
			}
		}
	}
}
