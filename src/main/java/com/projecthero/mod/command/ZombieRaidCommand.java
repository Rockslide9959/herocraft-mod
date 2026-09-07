package com.projecthero.mod.command;

import com.projecthero.mod.event.EventInstance;
import com.projecthero.mod.event.EventManager;
import com.projecthero.mod.event.EventSavedData;
import com.projecthero.mod.event.boss.BossPowers;
import com.projecthero.mod.event.entity.CursedZombie;
import com.projecthero.mod.event.entity.EmpoweredZombie;
import com.projecthero.mod.event.entity.RaidEntityTypes;
import com.projecthero.mod.event.raid.ZombieRaid;
import com.projecthero.mod.event.raid.ZombieRaidRewards;
import com.projecthero.mod.event.raid.ZombieRaidStarter;
import com.projecthero.mod.grave.CurseSource;
import com.projecthero.mod.grave.GraveboundCurse;
import com.projecthero.mod.worldgen.GraveyardTracker;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;

/**
 * {@code /projecthero zombieraid ...} -- the Zombie / Gravebound Raid development and testing commands
 * (nested under {@code /projecthero} by {@link ProjectHeroCommand}; v0.10.1).
 *
 * <p>Op-only ({@code hasPermission(2)}). The survival-facing verbs stay where they belong: a player
 * gets cursed by a Graveyard, a Cursed Zombie or a Grave Ritual Totem, never by a command.
 */
public final class ZombieRaidCommand {
	private static final SuggestionProvider<CommandSourceStack> BOSS_POWERS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(BossPowers.eligibleKeys(), builder);

	private ZombieRaidCommand() {
	}

	public static LiteralArgumentBuilder<CommandSourceStack> build() {
		return raidNode("zombieraid").requires(source -> source.hasPermission(2));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> raidNode(String literal) {
		return Commands.literal(literal)
				.then(Commands.literal("start").executes(ZombieRaidCommand::start))
				.then(Commands.literal("stop").executes(ZombieRaidCommand::stop))
				.then(Commands.literal("status").executes(ZombieRaidCommand::status))
				.then(Commands.literal("wave")
						.then(Commands.argument("number", IntegerArgumentType.integer(1, 12))
								.executes(ZombieRaidCommand::wave)))
				.then(Commands.literal("completeWave").executes(ZombieRaidCommand::completeWave))
				.then(Commands.literal("completewave").executes(ZombieRaidCommand::completeWave))
				.then(Commands.literal("curse").executes(ZombieRaidCommand::curse))
				.then(Commands.literal("clearCurse").executes(ZombieRaidCommand::clearCurse))
				.then(Commands.literal("clearcurse").executes(ZombieRaidCommand::clearCurse))
				.then(Commands.literal("boss")
						.then(Commands.argument("power", StringArgumentType.word()).suggests(BOSS_POWERS)
								.executes(ZombieRaidCommand::boss)))
				.then(Commands.literal("spawnCursedZombie").executes(ZombieRaidCommand::spawnCursedZombie))
				.then(Commands.literal("spawncursedzombie").executes(ZombieRaidCommand::spawnCursedZombie))
				.then(Commands.literal("giveEssence")
						.executes(c -> giveEssence(c, 32))
						.then(Commands.argument("count", IntegerArgumentType.integer(1, 640))
								.executes(c -> giveEssence(c, IntegerArgumentType.getInteger(c, "count")))))
				.then(Commands.literal("giveessence")
						.executes(c -> giveEssence(c, 32)))
				.then(Commands.literal("markGraveyard").executes(ZombieRaidCommand::markGraveyard));
	}

	// ---------------- raid control ----------------

	private static int start(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		ServerLevel level = player.serverLevel();
		if (!ZombieRaidStarter.start(level, player.blockPosition())) {
			c.getSource().sendFailure(Component.literal("A Zombie Raid is already running nearby."));
			return 0;
		}
		c.getSource().sendSuccess(() -> Component.literal("Zombie Raid started at " + player.blockPosition()), true);
		return 1;
	}

	private static int stop(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		ZombieRaid raid = raidAt(player);
		if (raid == null) {
			c.getSource().sendFailure(Component.literal("No Zombie Raid here."));
			return 0;
		}
		raid.abort(player.serverLevel());
		EventSavedData.get(player.serverLevel()).remove(raid.id());
		c.getSource().sendSuccess(() -> Component.literal("Zombie Raid stopped and cleaned up."), true);
		return 1;
	}

	private static int status(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		ServerLevel level = player.serverLevel();
		ZombieRaid raid = raidAt(player);
		int curse = GraveboundCurse.remainingTicks(player);
		String curseText = curse > 0
				? String.format(java.util.Locale.ROOT, "%d:%02d (%s)", curse / 1200, (curse / 20) % 60,
						GraveboundCurse.state(player).source())
				: "none";
		String raidText = raid == null ? "none"
				: String.format(java.util.Locale.ROOT, "wave %d/%d, %d enemies, state %s, bosses down %d",
						raid.wave(), raid.totalWaves(), raid.enemiesRemaining(level), raid.state(), raid.bossesDefeated());
		int active = EventManager.active(level.getServer()).size();
		c.getSource().sendSuccess(() -> Component.literal(
				"Curse: " + curseText + " | Raid here: " + raidText + " | active world events: " + active), false);
		return 1;
	}

	/** Jump straight to a wave: cleans up the current one, then starts the requested number. */
	private static int wave(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		int number = IntegerArgumentType.getInteger(c, "number");
		ZombieRaid raid = raidAt(player);
		if (raid == null) {
			// Nothing running -- start one and immediately jump it forward.
			if (!ZombieRaidStarter.start(player.serverLevel(), player.blockPosition())) {
				c.getSource().sendFailure(Component.literal("Could not start a raid here."));
				return 0;
			}
			raid = raidAt(player);
		}
		if (raid == null) {
			return 0;
		}
		raid.debugJumpToWave(player.serverLevel(), number);
		c.getSource().sendSuccess(() -> Component.literal("Jumped to wave " + number), true);
		return 1;
	}

	/** Kill everything the current wave still owns, which makes the raid advance normally. */
	private static int completeWave(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		ZombieRaid raid = raidAt(player);
		if (raid == null) {
			c.getSource().sendFailure(Component.literal("No Zombie Raid here."));
			return 0;
		}
		int cleared = raid.debugClearWave(player.serverLevel());
		c.getSource().sendSuccess(() -> Component.literal("Cleared " + cleared + " wave mobs."), true);
		return 1;
	}

	// ---------------- curse ----------------

	private static int curse(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		boolean applied = GraveboundCurse.apply(player, CurseSource.COMMAND);
		c.getSource().sendSuccess(() -> Component.literal(applied
				? "Gravebound Curse applied."
				: "Already cursed -- the existing timer was left untouched."), true);
		return applied ? 1 : 0;
	}

	private static int clearCurse(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		boolean cleared = GraveboundCurse.clear(player, false);
		c.getSource().sendSuccess(() -> Component.literal(cleared ? "Curse cleared." : "Not cursed."), true);
		return cleared ? 1 : 0;
	}

	// ---------------- spawning ----------------

	private static int boss(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		ServerLevel level = player.serverLevel();
		String requested = StringArgumentType.getString(c, "power");
		String powerKey = resolvePower(requested);
		if (powerKey == null) {
			c.getSource().sendFailure(Component.literal("Unknown or boss-ineligible power: " + requested));
			return 0;
		}
		EmpoweredZombie boss = RaidEntityTypes.EMPOWERED_ZOMBIE.create(level);
		if (boss == null) {
			return 0;
		}
		BlockPos pos = player.blockPosition();
		boss.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYRot(), 0.0f);
		boss.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.COMMAND, null);
		boss.configure(powerKey, null, false, 1);
		level.addFreshEntity(boss);

		ZombieRaid raid = raidAt(player);
		if (raid != null) {
			raid.debugAdoptBoss(boss);
		}
		c.getSource().sendSuccess(() -> Component.literal("Spawned Empowered Zombie with " + powerKey), true);
		return 1;
	}

	/** Accepts a full power key, or a shorthand like {@code geokinesis}. */
	private static String resolvePower(String requested) {
		if (BossPowers.isEligible(requested)) {
			return requested;
		}
		String needle = requested.toLowerCase(java.util.Locale.ROOT);
		for (String key : BossPowers.eligibleKeys()) {
			if (key.endsWith(needle) || key.contains(needle)) {
				return key;
			}
		}
		return null;
	}

	private static int spawnCursedZombie(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		ServerLevel level = player.serverLevel();
		CursedZombie zombie = RaidEntityTypes.CURSED_ZOMBIE.create(level);
		if (zombie == null) {
			return 0;
		}
		BlockPos pos = player.blockPosition().relative(player.getDirection(), 3);
		zombie.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYRot(), 0.0f);
		zombie.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.COMMAND, null);
		level.addFreshEntity(zombie);
		c.getSource().sendSuccess(() -> Component.literal("Spawned a Cursed Zombie."), true);
		return 1;
	}

	private static int giveEssence(CommandContext<CommandSourceStack> c, int count) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		ZombieRaidRewards.giveEssence(player, count);
		c.getSource().sendSuccess(() -> Component.literal("Gave " + count + " Grave Essence."), true);
		return count;
	}

	/**
	 * Register the player's position as a Graveyard for the proximity cache, so the raised Cursed
	 * Zombie spawn rate can be tested without finding a real one.
	 */
	private static int markGraveyard(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		GraveyardTracker.remember(player.serverLevel(), player.blockPosition());
		c.getSource().sendSuccess(() -> Component.literal("Marked this position as a Graveyard for spawn testing."), true);
		return 1;
	}

	// ---------------- helpers ----------------

	private static ZombieRaid raidAt(ServerPlayer player) {
		EventInstance event = EventManager.at(player.serverLevel(), player.blockPosition());
		if (event instanceof ZombieRaid raid) {
			return raid;
		}
		event = EventManager.forPlayer(player);
		return event instanceof ZombieRaid raid ? raid : null;
	}

}
