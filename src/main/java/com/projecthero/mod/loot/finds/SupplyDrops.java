package com.projecthero.mod.loot.finds;

import java.util.Collection;
import java.util.List;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * v0.17.5 Supply Drops (a Tier 1 Find that comes to you): every {@code projectheroSupplyDropDays} to that plus one
 * in-game days (default 2-3) each overworld player gets a crate parachuted down 40-80 blocks away, never onto water,
 * with a chat hint giving the rough direction. 85% a Tier 1 Hero Cache, 15% a Tier 2 Site Vault. Off with the
 * gamerule {@code projectheroSupplyDrops false}. Operators: {@code /projecthero supplydrop [players] [tier]}.
 *
 * <p>Static-free: the schedule is world saved data ({@link SupplyDropSchedule}), the crate's timer lives on the
 * {@link SupplyDropEntity}. The two game rule keys are registration-time constants.
 */
public final class SupplyDrops {
	public static final String RULE_NAME = "projectheroSupplyDrops";
	public static final String DAYS_RULE_NAME = "projectheroSupplyDropDays";
	public static GameRules.Key<GameRules.BooleanValue> RULE;
	public static GameRules.Key<GameRules.IntegerValue> DAYS_RULE;

	public static final int MIN_DISTANCE = 40;
	public static final int MAX_DISTANCE = 80;
	public static final int DROP_HEIGHT = 60;
	public static final float TIER2_CHANCE = 0.15f;
	private static final int CHECK_INTERVAL = 100;
	private static final int RETRY_TICKS = 1200;
	private static final String[] COMPASS = { "n", "ne", "e", "se", "s", "sw", "w", "nw" };

	public enum Result { DROPPED, DISABLED, NOT_OVERWORLD, NO_SPOT }

	/** The outcome of a drop attempt; {@code crate} is set when {@link Result#DROPPED}. */
	public record Attempt(Result result, SupplyDropEntity crate) {
	}

	private SupplyDrops() {
	}

	static void initialize() {
		RULE = GameRuleRegistry.register(RULE_NAME, GameRules.Category.SPAWNING, GameRuleFactory.createBooleanRule(true));
		DAYS_RULE = GameRuleRegistry.register(DAYS_RULE_NAME, GameRules.Category.SPAWNING, GameRuleFactory.createIntRule(2, 1, 365));
		ServerTickEvents.END_SERVER_TICK.register(SupplyDrops::tick);
	}

	public static boolean enabled(ServerLevel level) {
		return level.getGameRules().getBoolean(RULE);
	}

	/** A fresh interval: N to N+1 in-game days. */
	public static long interval(ServerLevel level, RandomSource random) {
		int days = Math.max(1, level.getGameRules().getInt(DAYS_RULE));
		return days * 24000L + random.nextInt(24000);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_INTERVAL != 0) {
			return;
		}
		ServerLevel overworld = server.overworld();
		if (overworld == null || !enabled(overworld)) {
			return;
		}
		SupplyDropSchedule schedule = SupplyDropSchedule.get(overworld);
		long now = overworld.getGameTime();
		for (ServerPlayer player : List.copyOf(overworld.players())) {
			if (player instanceof FakePlayer || player.isSpectator() || !player.isAlive()) {
				continue;
			}
			long due = schedule.nextAt(player.getUUID());
			if (due < 0) {
				schedule.schedule(player.getUUID(), now + interval(overworld, player.getRandom()));
				continue;
			}
			if (now < due) {
				continue;
			}
			Attempt a = scheduledDrop(player);
			schedule.schedule(player.getUUID(), now + (a.result() == Result.DROPPED ? interval(overworld, player.getRandom()) : RETRY_TICKS));
		}
	}

	/** What the timer does when a player's drop is due: respects the game rule and rolls the tier. */
	public static Attempt scheduledDrop(ServerPlayer player) {
		if (!enabled(player.serverLevel())) {
			return new Attempt(Result.DISABLED, null);
		}
		return drop(player, player.getRandom().nextFloat() < TIER2_CHANCE ? 2 : 1);
	}

	/** Sends one crate of the given tier at this player now (ignores the game rule; the command and tests use this). */
	public static Attempt drop(ServerPlayer player, int tier) {
		ServerLevel level = player.serverLevel();
		if (level.dimension() != Level.OVERWORLD) {
			return new Attempt(Result.NOT_OVERWORLD, null);
		}
		RandomSource random = player.getRandom();
		for (int attempt = 0; attempt < 16; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double dist = MIN_DISTANCE + random.nextDouble() * (MAX_DISTANCE - MIN_DISTANCE);
			int x = Mth.floor(player.getX() + Math.sin(angle) * dist);
			int z = Mth.floor(player.getZ() - Math.cos(angle) * dist);
			BlockPos column = new BlockPos(x, 0, z);
			if (!level.hasChunkAt(column)) {
				continue;
			}
			int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos top = new BlockPos(x, ground - 1, z);
			if (ground <= level.getMinBuildHeight() + 1 || !level.getFluidState(top).isEmpty() || !level.getFluidState(top.above()).isEmpty()) {
				continue;
			}
			return new Attempt(Result.DROPPED, launch(level, player, x, ground, z, tier));
		}
		return new Attempt(Result.NO_SPOT, null);
	}

	/** Spawns the crate high above (x, ground, z) and tells the player roughly where to look. */
	public static SupplyDropEntity launch(ServerLevel level, ServerPlayer player, int x, int ground, int z, int tier) {
		double dx = x + 0.5 - player.getX();
		double dz = z + 0.5 - player.getZ();
		int y = Math.min(level.getMaxBuildHeight() - 4, Math.max(ground, (int) player.getY()) + DROP_HEIGHT);
		// the vault's door faces back towards the player
		Direction facing = Direction.getNearest((float) -dx, 0f, (float) -dz);
		if (facing.getAxis() == Direction.Axis.Y) {
			facing = Direction.SOUTH;
		}
		SupplyDropEntity crate = SupplyDropEntity.create(level, x + 0.5, y, z + 0.5, tier, facing);
		level.addFreshEntity(crate);
		int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz) / 10.0) * 10;
		player.sendSystemMessage(Component.translatable("projecthero.supply_drop.incoming",
				Component.translatable("projecthero.supply_drop.dir." + compass(dx, dz)), dist).withStyle(ChatFormatting.GOLD));
		player.playNotifySound(SoundEvents.FIREWORK_ROCKET_LARGE_BLAST_FAR, SoundSource.AMBIENT, 1.0f, 0.7f);
		return crate;
	}

	/** One of the eight compass points ("n", "ne", ... ) for a horizontal offset (north = -z). */
	public static String compass(double dx, double dz) {
		double deg = Math.toDegrees(Math.atan2(dx, -dz));
		return COMPASS[Math.floorMod((int) Math.round(deg / 45.0), 8)];
	}

	static void registerCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("projecthero").requires(s -> s.hasPermission(2))
				.then(Commands.literal("supplydrop")
						.executes(c -> run(c.getSource(), List.of(c.getSource().getPlayerOrException()), 1))
						.then(Commands.argument("players", EntityArgument.players())
								.executes(c -> run(c.getSource(), EntityArgument.getPlayers(c, "players"), 1))
								.then(Commands.argument("tier", IntegerArgumentType.integer(1, 4))
										.executes(c -> run(c.getSource(), EntityArgument.getPlayers(c, "players"),
												IntegerArgumentType.getInteger(c, "tier")))))));
	}

	private static int run(CommandSourceStack source, Collection<ServerPlayer> players, int tier) throws CommandSyntaxException {
		int n = 0;
		for (ServerPlayer p : players) {
			Attempt a = drop(p, tier);
			if (a.result() == Result.DROPPED) {
				n++;
			} else {
				source.sendFailure(Component.translatable("projecthero.supply_drop.failed." + a.result().name().toLowerCase(java.util.Locale.ROOT),
						p.getDisplayName()));
			}
		}
		final int sent = n;
		source.sendSuccess(() -> Component.translatable("projecthero.supply_drop.sent", sent), true);
		return n;
	}
}
