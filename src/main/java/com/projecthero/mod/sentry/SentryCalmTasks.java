package com.projecthero.mod.sentry;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.17.5 round 3 (user: "doing tasks like fishing and stuff should also make it drain faster"): calm everyday things drain the Sentry's
 * Void. While doing it: a bobber out in the water, -0.15 a second more ({@link #fishingInWater}, read by {@code Sentry.tickMeters}). Each
 * finished task takes a chunk (never scaled by the suit or merging, never in the takeover):
 * <ul>
 *   <li>a fish (or anything) reeled in: -2 ({@code Stats.FISH_CAUGHT} going up);</li>
 *   <li>a ripe crop harvested (wheat, carrots, potatoes, beetroot, nether wart, cocoa, sweet berries at full age): -0.4 ({@link #broke});</li>
 *   <li>a trade with a villager: -1.5 ({@code Stats.TRADED_WITH_VILLAGER});</li>
 *   <li>animals bred: -1.5 ({@code Stats.ANIMALS_BRED});</li>
 *   <li>a music disc played: -1 ({@code Stats.PLAY_RECORD}).</li>
 * </ul>
 * The stats are read once a second ({@link #check}) against the last values seen (a session map by player id only, cleared on server stop
 * via {@code Sentry.clearSessionState}). The first look at a player only records the values.
 */
public final class SentryCalmTasks {
	private static final ResourceLocation[] STATS = { Stats.FISH_CAUGHT, Stats.TRADED_WITH_VILLAGER, Stats.ANIMALS_BRED, Stats.PLAY_RECORD };
	private static final float[] CHUNKS = { SentryConfig.VOID_TASK_FISH, SentryConfig.VOID_TASK_TRADE, SentryConfig.VOID_TASK_BREED,
			SentryConfig.VOID_TASK_MUSIC };
	private static final Map<UUID, int[]> SEEN = new ConcurrentHashMap<>();

	private SentryCalmTasks() {
	}

	static void clearSessionState() {
		SEEN.clear();
	}

	static void forget(UUID id) {
		SEEN.remove(id);
	}

	/** A bobber of his is out in the water right now. */
	public static boolean fishingInWater(ServerPlayer p) {
		return p.fishing != null && p.fishing.isAlive() && p.fishing.isInWater();
	}

	/** Once a second (from {@code Sentry.tick}): the task stats that went up since the last look each take their chunk. */
	static void check(ServerPlayer p) {
		int[] now = new int[STATS.length];
		for (int i = 0; i < STATS.length; i++) {
			now[i] = p.getStats().getValue(Stats.CUSTOM.get(STATS[i]));
		}
		int[] last = SEEN.put(p.getUUID(), now);
		if (last == null) {
			return;
		}
		for (int i = 0; i < STATS.length; i++) {
			int done = Math.min(8, now[i] - last[i]);
			for (int k = 0; k < done; k++) {
				task(p, CHUNKS[i]);
			}
		}
	}

	/** A block he broke (from {@code PlayerBlockBreakEvents.AFTER}): a ripe crop is a harvest. */
	public static void broke(ServerPlayer p, BlockState st) {
		if (ripe(st)) {
			task(p, SentryConfig.VOID_TASK_HARVEST);
		}
	}

	/** Whether this is a crop at full age (what a harvest is). */
	public static boolean ripe(BlockState st) {
		if (st.getBlock() instanceof CropBlock crop) {
			return crop.isMaxAge(st);
		}
		if (st.getBlock() instanceof NetherWartBlock) {
			return st.getValue(NetherWartBlock.AGE) >= 3;
		}
		if (st.getBlock() instanceof CocoaBlock) {
			return st.getValue(CocoaBlock.AGE) >= 2;
		}
		if (st.getBlock() instanceof SweetBerryBushBlock) {
			return st.getValue(SweetBerryBushBlock.AGE) >= 2;
		}
		return false;
	}

	/** One finished calm task: the Void drops by {@code chunk} (negative). Public for the tests. */
	public static void task(ServerPlayer p, float chunk) {
		if (!Sentry.hasPower(p) || Sentry.inTakeover(p) || chunk >= 0f || Sentry.state(p).voidLevel <= 0f) {
			return;
		}
		Sentry.addVoid(p, chunk);
		p.serverLevel().sendParticles(Sentry.dust(Sentry.GOLD, 0.9f), p.getX(), p.getY() + 1.2, p.getZ(), 4, 0.3, 0.3, 0.3, 0.0);
		p.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1.6, p.getZ(), 1, 0.2, 0.1, 0.2, 0.0);
	}

	/** A fish (or anything) reeled in. */
	public static void fished(ServerPlayer p) {
		task(p, SentryConfig.VOID_TASK_FISH);
	}
}
