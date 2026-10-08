package com.projecthero.mod.symbiote;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.carnage.CarnageSpawner;
import com.projecthero.mod.carnage.entity.CarnageEntity;
import com.projecthero.mod.carnage.entity.CrimsonMeteorEntity;
import com.projecthero.mod.symbiote.entity.SymbioteEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.phys.AABB;

/**
 * v0.15.15: <b>Call Carnage</b>. A player who carries a Symbiote (any host) holds right-click on a free Symbiote for
 * {@link #CHANNEL_TICKS} (5 s): crimson motes swirl into it and it slowly reddens; at the end it is consumed -- it turns
 * fully crimson and dissolves -- and a Carnage meteor is called down near the player through the Carnage boss's own
 * spawner ({@link CarnageSpawner#dropNear}). A {@link #COOLDOWN_TICKS} (10 min) per-player cooldown follows.
 *
 * <p>No new packets: holding the use key on an entity makes the client re-send the interaction every 4 ticks
 * ({@code Minecraft#startUseItem} repeats while the key is held), so each interaction just "pings" the channel and the
 * Symbiote's own server tick ({@link #tickChannel}) cancels it once the pings stop (key released or the crosshair moved
 * off it), the caller wanders more than {@link #MAX_DISTANCE} blocks away, or loses the Symbiote.
 *
 * <p>Holding flint and steel or a Symbiote Vial keeps their own behaviour (burn / bottle) -- see
 * {@link SymbioteEntity#interact}. The cooldown map is static server state, cleared by {@link Symbiote#clearSessionState}.
 */
public final class SymbioteCarnageCall {
	public static final int CHANNEL_TICKS = 100;
	public static final int COOLDOWN_TICKS = 12_000;
	/** No interaction for this long and the key is taken as released. */
	public static final int PING_TIMEOUT_TICKS = 10;
	public static final double MAX_DISTANCE = 4.0;
	/** How far from the caller the meteor comes down (the same band as {@code /projecthero carnage spawn}). */
	public static final int DROP_MIN = 12;
	public static final int DROP_MAX = 20;
	/** Ticks the consumed Symbiote takes to dissolve. */
	public static final int CONSUME_TICKS = 30;

	private static final DustParticleOptions CRIMSON = new DustParticleOptions(new org.joml.Vector3f(0.78f, 0.04f, 0.07f), 1.1f);
	private static final DustParticleOptions BLOOD = new DustParticleOptions(new org.joml.Vector3f(0.45f, 0.0f, 0.03f), 1.6f);

	private static final Map<UUID, Long> COOLDOWN_UNTIL = new ConcurrentHashMap<>();

	/** How the meteor is sent down: {@link CarnageSpawner#dropNear}. */
	@FunctionalInterface
	public interface Dropper {
		boolean drop(ServerLevel level, ServerPlayer player, int min, int max);
	}

	/** Gametests swap this out so no real meteor lands in the shared test world; always {@code CarnageSpawner::dropNear} in play. */
	public static Dropper dropper = CarnageSpawner::dropNear;

	private SymbioteCarnageCall() {
	}

	public static void clearSessionState() {
		COOLDOWN_UNTIL.clear();
	}

	/** Game time this player may call Carnage again (0 = now). */
	public static long readyAt(ServerPlayer player) {
		return COOLDOWN_UNTIL.getOrDefault(player.getUUID(), 0L);
	}

	/** Tests / commands: forget a player's cooldown. */
	public static void resetCooldown(ServerPlayer player) {
		COOLDOWN_UNTIL.remove(player.getUUID());
	}

	/** Can this player call Carnage through a Symbiote at all (bonded with one, any host)? */
	public static boolean canChannel(ServerPlayer player) {
		return Symbiote.hasSymbiote(player) && !player.isSpectator();
	}

	/**
	 * One interaction from {@code player} on {@code symbiote} while holding the use key. Starts the channel, or keeps it
	 * alive. Returns false if it was refused (cooldown, Peaceful, Carnage already here, someone else channelling).
	 */
	public static boolean ping(ServerPlayer player, SymbioteEntity symbiote) {
		ServerLevel level = player.serverLevel();
		long now = level.getGameTime();
		UUID caller = symbiote.carnageCaller();
		if (caller != null && caller.equals(player.getUUID())) {
			symbiote.pingCarnageCall(now);
			return true;
		}
		if (caller != null || symbiote.isBurning() || symbiote.isConsumed()) {
			return false;
		}
		long ready = readyAt(player);
		if (now < ready) {
			long secs = (ready - now + 19) / 20;
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.carnage_call.cooldown",
					String.format(java.util.Locale.ROOT, "%d:%02d", secs / 60, secs % 60)).withStyle(ChatFormatting.DARK_RED), true);
			return false;
		}
		if (level.getDifficulty() == Difficulty.PEACEFUL) {
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.carnage_call.peaceful")
					.withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (carnageAlreadyHere(level, player)) {
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.carnage_call.already")
					.withStyle(ChatFormatting.DARK_RED), true);
			return false;
		}
		symbiote.startCarnageCall(player.getUUID(), now);
		SymbioteSounds.organic(level, symbiote.getX(), symbiote.getY(), symbiote.getZ(), 1.0f, 0.5f);
		return true;
	}

	/** A Carnage (or a crimson meteor on its way) within 256 blocks: one at a time. */
	private static boolean carnageAlreadyHere(ServerLevel level, ServerPlayer player) {
		AABB box = player.getBoundingBox().inflate(256.0, 384.0, 256.0);
		return !level.getEntitiesOfClass(CarnageEntity.class, box).isEmpty()
				|| !level.getEntitiesOfClass(CrimsonMeteorEntity.class, box).isEmpty();
	}

	/**
	 * Called every server tick by a Symbiote that is being channelled. Returns the 0..1 progress, or -1 if the channel
	 * just ended (cancelled, or completed -- the Symbiote has then begun to dissolve).
	 */
	public static float tickChannel(ServerLevel level, SymbioteEntity symbiote, UUID callerId, long start, long lastPing) {
		long now = level.getGameTime();
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(callerId);
		boolean released = now - lastPing > PING_TIMEOUT_TICKS;
		if (player == null || player.isRemoved() || player.level() != level || !canChannel(player)
				|| player.distanceTo(symbiote) > MAX_DISTANCE || released) {
			if (player != null && !player.isRemoved()) {
				player.displayClientMessage(Component.translatable("message.projecthero.symbiote.carnage_call.cancelled")
						.withStyle(ChatFormatting.GRAY), true);
			}
			symbiote.endCarnageCall();
			return -1.0f;
		}
		float progress = Math.min(1.0f, (now - start) / (float) CHANNEL_TICKS);
		swirl(level, symbiote, now, progress);
		if ((now - start) % 5 == 0) {
			player.displayClientMessage(progressBar(progress), true);
		}
		if ((now - start) % 20 == 10) {
			SymbioteSounds.organic(level, symbiote.getX(), symbiote.getY(), symbiote.getZ(), 0.7f + progress * 0.6f,
					0.5f + progress * 0.4f);
		}
		if (now - start < CHANNEL_TICKS) {
			return progress;
		}
		complete(level, player, symbiote, now);
		return -1.0f;
	}

	private static void complete(ServerLevel level, ServerPlayer player, SymbioteEntity symbiote, long now) {
		if (carnageAlreadyHere(level, player)) {
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.carnage_call.already")
					.withStyle(ChatFormatting.DARK_RED), true);
			symbiote.endCarnageCall();
			return;
		}
		boolean dropped = dropper.drop(level, player, DROP_MIN, DROP_MAX) || dropper.drop(level, player, 6, DROP_MIN);
		if (!dropped) {
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.carnage_call.no_ground")
					.withStyle(ChatFormatting.GRAY), true);
			symbiote.endCarnageCall();
			return;
		}
		COOLDOWN_UNTIL.put(player.getUUID(), now + COOLDOWN_TICKS);
		symbiote.beginConsume();
		player.sendSystemMessage(Component.translatable("message.projecthero.symbiote.carnage_call.called")
				.withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
		level.playSound(null, symbiote.getX(), symbiote.getY(), symbiote.getZ(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE,
				0.8f, 1.4f);
		com.projecthero.mod.ProjectHeroMod.LOGGER.info("[Carnage] {} called a crimson meteor through a Symbiote",
				player.getName().getString());
	}

	/** Crimson motes spiralling in towards the Symbiote, tighter and denser as the call nears completion. */
	private static void swirl(ServerLevel level, SymbioteEntity symbiote, long now, float progress) {
		double cx = symbiote.getX();
		double cy = symbiote.getY();
		double cz = symbiote.getZ();
		int arms = 3 + Math.round(progress * 3.0f);
		for (int k = 0; k < arms; k++) {
			float phase = ((now + k * 4L) % 14L) / 14.0f; // 0 = far out, 1 = arrived
			double r = 2.2 * (1.0 - phase) + 0.15;
			double a = now * 0.32 + k * (Math.PI * 2.0 / arms) + phase * 2.6;
			double y = cy + 0.15 + 1.4 * (1.0 - phase) * (1.0 - phase);
			level.sendParticles(CRIMSON, cx + Math.cos(a) * r, y, cz + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0);
		}
		if (now % 3 == 0) {
			level.sendParticles(BLOOD, cx, cy + 0.3, cz, 1 + Math.round(progress * 3.0f), 0.25, 0.12, 0.25, 0.0);
		}
	}

	/** Action-bar progress: "Calling Carnage [||||||||....]". */
	private static MutableComponent progressBar(float progress) {
		int filled = Math.round(progress * 20.0f);
		MutableComponent bar = Component.literal("[").withStyle(ChatFormatting.DARK_GRAY)
				.append(Component.literal("|".repeat(filled)).withStyle(ChatFormatting.RED))
				.append(Component.literal("|".repeat(20 - filled)).withStyle(ChatFormatting.DARK_GRAY))
				.append(Component.literal("]").withStyle(ChatFormatting.DARK_GRAY));
		return Component.translatable("message.projecthero.symbiote.carnage_call.progress", bar)
				.withStyle(ChatFormatting.DARK_RED);
	}

	/** The consumed Symbiote's last moments: called each tick while it dissolves, {@code left} counting down to 0. */
	public static void consumeFx(ServerLevel level, SymbioteEntity symbiote, int left) {
		double x = symbiote.getX();
		double y = symbiote.getY() + 0.25;
		double z = symbiote.getZ();
		level.sendParticles(CRIMSON, x, y, z, 4, 0.3, 0.2, 0.3, 0.0);
		if (left % 6 == 0) {
			SymbioteSounds.organic(level, x, y, z, 1.0f, 1.4f + level.random.nextFloat() * 0.4f);
		}
		if (left <= 0) {
			level.sendParticles(BLOOD, x, y, z, 30, 0.45, 0.3, 0.45, 0.0);
			level.sendParticles(CRIMSON, x, y + 0.3, z, 40, 0.6, 0.8, 0.6, 0.0);
			SymbioteSounds.organic(level, x, y, z, 1.3f, 0.5f);
		}
	}
}
