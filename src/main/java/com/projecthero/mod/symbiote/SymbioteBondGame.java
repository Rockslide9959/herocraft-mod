package com.projecthero.mod.symbiote;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.network.SymbioteBondGamePayload;
import com.projecthero.mod.symbiote.entity.SymbioteEntity;
import com.projecthero.mod.symbiote.item.SymbioteVialItem;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;

/**
 * The bonding minigame (v0.12.25): a wild Symbiote (or a filled vial) no longer bonds on touch -- the host
 * has to win a timing test first. Three rounds; in each a marker sweeps back and forth across a bar and the
 * player must stop it inside a shrinking zone. The whole thing is deterministic from a server-chosen seed,
 * so the server replays the client's press ticks ({@link #handleResult}) and never trusts a claimed win.
 *
 * <p>Passing calls {@link SymbioteBonding#bondNow} and the usual 35-second bonding debuff
 * ({@link SymbioteVitalsManager#BONDING_TICKS}) follows. Failing consumes nothing: the Symbiote (or vial) is
 * kept, the host is nauseous for a few seconds and cannot retry for {@link #RETRY_TICKS}.
 */
public final class SymbioteBondGame {
	public static final int ROUNDS = 3;
	/** Ticks between a press and the start of the next round. */
	public static final int ROUND_GAP = 12;
	/** A round with no press after this long counts as a miss (the client gives up first). */
	public static final int ROUND_TIMEOUT = 200;
	/** Marker sweep period per round (ticks for there-and-back) -- faster each round. */
	public static final int[] PERIOD = {40, 32, 26};
	/** Half-width of the target zone per round, as a fraction of the bar. */
	public static final double[] HALF_ZONE = {0.16, 0.14, 0.12};
	private static final int SESSION_TICKS = 20 * 60;
	private static final int RETRY_TICKS = 20 * 5;
	/** Real-time slack (ticks) between the client's claimed duration and the server's clock. */
	private static final int SLACK_TICKS = 40;

	private record Session(int seed, long startedAt, UUID entity) {
	}

	private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> RETRY_AT = new ConcurrentHashMap<>();

	private SymbioteBondGame() {
	}

	public static void clearSessionState() {
		SESSIONS.clear();
		RETRY_AT.clear();
	}

	// ---------------- the deterministic game (shared with the client screen) ----------------

	/** Marker position 0..1, {@code t} ticks into round {@code round}. */
	public static double marker(int seed, int round, int t) {
		double phase = ((seed >>> (round * 7)) & 0x7F) / 127.0;
		double x = ((double) t / PERIOD[round] + phase) % 1.0;
		return x < 0.5 ? x * 2.0 : (1.0 - x) * 2.0;
	}

	/** Centre of the target zone, 0.25..0.75. */
	public static double zoneCenter(int seed, int round) {
		return 0.25 + ((seed >>> (round * 5 + 3)) & 0xFF) / 255.0 * 0.5;
	}

	public static boolean hit(int seed, int round, int t) {
		return Math.abs(marker(seed, round, t) - zoneCenter(seed, round)) <= HALF_ZONE[round];
	}

	// ---------------- server flow ----------------

	/** Start the minigame for {@code player}; {@code entity} is the free Symbiote being touched, or null for a vial. */
	public static void begin(ServerPlayer player, SymbioteEntity entity) {
		long now = player.level().getGameTime();
		Long retry = RETRY_AT.get(player.getUUID());
		if (retry != null && now < retry) {
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.bond_game.retry",
					String.format(java.util.Locale.ROOT, "%.0f", Math.ceil((retry - now) / 20.0)))
					.withStyle(ChatFormatting.DARK_GRAY), true);
			return;
		}
		if (entity != null && !entity.claimBond(player, SESSION_TICKS + 40)) {
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.entity_busy"), true);
			return;
		}
		int seed = player.getRandom().nextInt();
		SESSIONS.put(player.getUUID(), new Session(seed, now, entity == null ? null : entity.getUUID()));
		player.displayClientMessage(Component.translatable("message.projecthero.symbiote.bond_game.begin")
				.withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC), true);
		ServerPlayNetworking.send(player, new SymbioteBondGamePayload(seed));
	}

	/** The client's press ticks (empty = the player backed out). Every claim is replayed and re-checked here. */
	public static void handleResult(ServerPlayer player, List<Integer> presses) {
		Session s = SESSIONS.remove(player.getUUID());
		if (s == null) {
			return; // unprompted: a modified client gains nothing
		}
		long now = player.level().getGameTime();
		if (presses.isEmpty()) {
			release(player, s);
			return;
		}
		boolean ok = presses.size() == ROUNDS && now - s.startedAt <= SESSION_TICKS + SLACK_TICKS;
		int roundStart = 0;
		for (int r = 0; ok && r < ROUNDS; r++) {
			int press = presses.get(r);
			int t = press - roundStart;
			ok = t >= 3 && t <= ROUND_TIMEOUT && hit(s.seed, r, t);
			roundStart = press + ROUND_GAP;
		}
		// the claimed run must also have had time to happen
		ok = ok && hadTime(presses, now - s.startedAt);
		if (!ok) {
			fail(player, s, now);
			return;
		}
		Entity target = s.entity == null ? null : ((ServerLevel) player.level()).getEntity(s.entity);
		if (s.entity != null) {
			if (!(target instanceof SymbioteEntity blob) || blob.isRemoved() || blob.distanceTo(player) > 12.0f
					|| Symbiote.hasSymbiote(player)) {
				player.displayClientMessage(Component.translatable("message.projecthero.symbiote.bond_game.lost")
						.withStyle(ChatFormatting.GRAY), true);
				return;
			}
			SymbioteBonding.bondNow(player, blob);
			return;
		}
		if (!SymbioteVialItem.consumeFilledVial(player)) {
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.bond_game.lost")
					.withStyle(ChatFormatting.GRAY), true);
			return;
		}
		SymbioteBonding.bondFromVialNow(player);
	}

	private static boolean hadTime(List<Integer> presses, long serverElapsed) {
		return serverElapsed >= presses.get(presses.size() - 1) - SLACK_TICKS;
	}

	private static void release(ServerPlayer player, Session s) {
		if (s.entity != null && ((ServerLevel) player.level()).getEntity(s.entity) instanceof SymbioteEntity blob) {
			blob.releaseBond();
		}
	}

	private static void fail(ServerPlayer player, Session s, long now) {
		release(player, s);
		RETRY_AT.put(player.getUUID(), now + RETRY_TICKS);
		player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0, false, false, true));
		player.displayClientMessage(Component.translatable("message.projecthero.symbiote.bond_game.failed")
				.withStyle(ChatFormatting.DARK_PURPLE), false);
		if (player.level() instanceof ServerLevel level) {
			SymbioteSounds.organic(level, player.getX(), player.getY(), player.getZ(), 0.8f, 0.5f);
		}
	}
}
