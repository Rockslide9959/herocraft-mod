package com.projecthero.mod.speedforce;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.power.p04.SuperSpeedTimeSlow;
import com.projecthero.mod.speedforce.data.SpeedForceRace;
import com.projecthero.mod.speedforce.entity.SpeedForceEchoEntity;
import com.projecthero.mod.speedforce.entity.SpeedForceEntityTypes;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 (user, race ghosts): a Speed Force Shard used on a block sets a race START, used again elsewhere the FINISH (sneak
 * + use clears the course). Running start to finish in Speed Mode is timed; the best times are kept
 * ({@link SpeedForceAttachments#RACE}, listed in the Shift+N menu) and the best run's track is saved
 * ({@link SpeedForceAttachments#RACE_GHOST}) and replayed next time as a see-through ghost in the runner's lightning colour
 * -- a {@link SpeedForceEchoEntity}, the memory road's echo, tinted. Flash Time ends a race (the world's clock stops).
 * The running races are session-only (cleared through {@code SpeedForceSpeedster#clearSessionState}).
 */
public final class SpeedForceRaces {
	/** One race being run: its start (game time), the samples so far, the ghost racing it. */
	private static final class Run {
		final long start;
		final List<Vec3> samples = new ArrayList<>();
		int ghostId = -1;
		List<Vec3> ghost = List.of();

		Run(long start) {
			this.start = start;
		}
	}

	private static final Map<UUID, Run> RUNS = new ConcurrentHashMap<>();
	private static final Map<UUID, Vec3> LAST = new ConcurrentHashMap<>();

	private SpeedForceRaces() {
	}

	static void initialize() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> abort(handler.getPlayer(), null));
	}

	static void clearSessionState() {
		RUNS.clear();
		LAST.clear();
	}

	public static SpeedForceRace of(ServerPlayer p) {
		return p.getAttachedOrElse(SpeedForceAttachments.RACE, SpeedForceRace.EMPTY);
	}

	/** Whether {@code p} is running their course right now. */
	public static boolean racing(ServerPlayer p) {
		return RUNS.containsKey(p.getUUID());
	}

	/** The ghost's entity id in the race being run (-1: none). */
	public static int ghostOf(ServerPlayer p) {
		Run r = RUNS.get(p.getUUID());
		return r == null ? -1 : r.ghostId;
	}

	/** Ticks as m:ss.cc. */
	public static String format(int ticks) {
		int cs = ticks * 5;
		return String.format(Locale.ROOT, "%d:%02d.%02d", cs / 6000, (cs / 100) % 60, cs % 100);
	}

	// ---------------------------------------------------------------- the shard

	/** The Speed Force Shard used on {@code clicked}: the start, then the finish (sneaking: the course cleared). */
	public static boolean useShard(ServerPlayer p, BlockPos clicked) {
		if (!SpeedForceSpeedster.hasPower(p)) {
			return false;
		}
		String dim = p.level().dimension().location().toString();
		SpeedForceRace race = of(p);
		BlockPos gate = clicked.above();
		abort(p, null);
		if (p.isShiftKeyDown()) {
			p.setAttached(SpeedForceAttachments.RACE, SpeedForceRace.EMPTY);
			p.removeAttached(SpeedForceAttachments.RACE_GHOST);
			SpeedForceSpeedster.say(p, "message.projecthero.speed_force_speedster.race_cleared", ChatFormatting.GRAY);
			return true;
		}
		if (race.start().isEmpty() || race.ready() || !race.dimension().equals(dim)) {
			p.setAttached(SpeedForceAttachments.RACE, SpeedForceRace.startAt(dim, gate));
			p.removeAttached(SpeedForceAttachments.RACE_GHOST);
			SpeedForceSpeedster.say(p, "message.projecthero.speed_force_speedster.race_start_set", ChatFormatting.GOLD);
		} else if (Vec3.atCenterOf(race.start().get()).distanceTo(Vec3.atCenterOf(gate)) < SpeedForceConfig.RACE_MIN_LENGTH) {
			SpeedForceSpeedster.say(p, "message.projecthero.speed_force_speedster.race_too_short", ChatFormatting.RED,
					Math.round(SpeedForceConfig.RACE_MIN_LENGTH));
			return true;
		} else {
			p.setAttached(SpeedForceAttachments.RACE, race.withFinish(gate));
			p.removeAttached(SpeedForceAttachments.RACE_GHOST);
			SpeedForceSpeedster.say(p, "message.projecthero.speed_force_speedster.race_finish_set", ChatFormatting.GOLD);
		}
		SpeedForceSpeedster.sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.6f);
		return true;
	}

	// ---------------------------------------------------------------- the run

	static void tick(ServerPlayer p) {
		SpeedForceRace race = of(p);
		String dim = p.level().dimension().location().toString();
		Vec3 now = p.position();
		Vec3 prev = LAST.put(p.getUUID(), now);
		if (!race.ready() || !race.dimension().equals(dim)) {
			abort(p, null);
			return;
		}
		ServerLevel level = p.serverLevel();
		long time = level.getGameTime();
		Vec3 start = Vec3.atBottomCenterOf(race.start().get());
		Vec3 finish = Vec3.atBottomCenterOf(race.finish().get());
		// the gates, a column of sparks the runner alone sees
		if (time % 10 == 0 && now.distanceToSqr(start) < 96 * 96) {
			gateFx(p, level, start);
		}
		if (time % 10 == 5 && now.distanceToSqr(finish) < 96 * 96) {
			gateFx(p, level, finish);
		}
		if (prev == null || prev.distanceToSqr(now) > 60 * 60) {
			abort(p, null); // a teleport
			return;
		}
		Run run = RUNS.get(p.getUUID());
		if (run == null) {
			if (SpeedForceSpeedster.speedActive(p) && crosses(prev, now, start)) {
				begin(p, level, race, time);
			}
			return;
		}
		long t = time - run.start;
		while (run.samples.size() * (long) SpeedForceConfig.RACE_SAMPLE_TICKS <= t) {
			run.samples.add(now);
		}
		moveGhost(level, run, t);
		if (crosses(prev, now, finish) && t > 0) {
			finish(p, level, race, run, (int) t, now);
		} else if (!SpeedForceSpeedster.speedActive(p) || t > SpeedForceConfig.RACE_MAX_TICKS || SuperSpeedTimeSlow.isCasting(p)) {
			abort(p, "message.projecthero.speed_force_speedster.race_dropped");
		}
	}

	/** Gametests: one step of the race as if the runner had just moved from {@code from} to where they stand. */
	public static void stepForTest(ServerPlayer p, Vec3 from) {
		LAST.put(p.getUUID(), from);
		tick(p);
	}

	private static boolean crosses(Vec3 a, Vec3 b, Vec3 gate) {
		if (Math.min(a.y, b.y) > gate.y + SpeedForceConfig.RACE_GATE_HEIGHT || Math.max(a.y, b.y) < gate.y - SpeedForceConfig.RACE_GATE_HEIGHT) {
			return false;
		}
		double dx = b.x - a.x;
		double dz = b.z - a.z;
		double len2 = dx * dx + dz * dz;
		double t = len2 < 1.0e-9 ? 0.0 : Math.max(0.0, Math.min(1.0, ((gate.x - a.x) * dx + (gate.z - a.z) * dz) / len2));
		double px = a.x + dx * t - gate.x;
		double pz = a.z + dz * t - gate.z;
		return px * px + pz * pz <= SpeedForceConfig.RACE_GATE_RADIUS * SpeedForceConfig.RACE_GATE_RADIUS;
	}

	private static void begin(ServerPlayer p, ServerLevel level, SpeedForceRace race, long time) {
		Run run = new Run(time);
		SpeedForceRace.Ghost ghost = p.getAttachedOrElse(SpeedForceAttachments.RACE_GHOST, SpeedForceRace.Ghost.EMPTY);
		if (ghost.track().size() >= 2) {
			SpeedForceEchoEntity e = SpeedForceEntityTypes.ECHO.create(level);
			if (e != null) {
				Vec3 at = ghost.track().get(0);
				e.setOwner(p.getUUID());
				e.setTint(SpeedForceColours.tone(SpeedForceColours.of(p), 2));
				e.moveTo(at.x, at.y, at.z, p.getYRot(), 0f);
				level.addFreshEntity(e);
				run.ghostId = e.getId();
				run.ghost = ghost.track();
			}
		}
		RUNS.put(p.getUUID(), run);
		int best = race.bestTime();
		p.displayClientMessage(best < 0 ? Component.translatable("message.projecthero.speed_force_speedster.race_go").withStyle(ChatFormatting.GOLD)
				: Component.translatable("message.projecthero.speed_force_speedster.race_go_best", format(best)).withStyle(ChatFormatting.GOLD), true);
		SpeedForceSpeedster.sound(p, SoundEvents.NOTE_BLOCK_BELL.value(), 1.0f, 2.0f);
	}

	private static void moveGhost(ServerLevel level, Run run, long t) {
		if (run.ghostId < 0 || !(level.getEntity(run.ghostId) instanceof SpeedForceEchoEntity e)) {
			return;
		}
		List<Vec3> track = run.ghost;
		double f = t / (double) SpeedForceConfig.RACE_SAMPLE_TICKS;
		int i = (int) Math.floor(f);
		if (i >= track.size() - 1) {
			Vec3 end = track.get(track.size() - 1);
			e.moveTo(end.x, end.y, end.z, e.getYRot(), 0f);
			e.setSpent(true); // the ghost finished: it fades
			return;
		}
		Vec3 a = track.get(i);
		Vec3 b = track.get(i + 1);
		Vec3 at = a.lerp(b, f - i);
		float yaw = b.distanceToSqr(a) > 1.0e-4 ? (float) Math.toDegrees(Math.atan2(-(b.x - a.x), b.z - a.z)) : e.getYRot();
		e.moveTo(at.x, at.y, at.z, yaw, 0f);
		e.setYHeadRot(yaw);
	}

	private static void finish(ServerPlayer p, ServerLevel level, SpeedForceRace race, Run run, int ticks, Vec3 now) {
		RUNS.remove(p.getUUID());
		discardGhost(level, run);
		int best = race.bestTime();
		boolean record = best < 0 || ticks < best;
		p.setAttached(SpeedForceAttachments.RACE, race.withTime(ticks));
		if (record) {
			List<Vec3> track = new ArrayList<>(run.samples);
			track.add(now);
			p.setAttached(SpeedForceAttachments.RACE_GHOST, new SpeedForceRace.Ghost(track));
		}
		p.displayClientMessage(Component.translatable(record ? "message.projecthero.speed_force_speedster.race_record"
				: "message.projecthero.speed_force_speedster.race_time", format(ticks), best < 0 ? "-" : format(best))
				.withStyle(record ? ChatFormatting.GOLD : ChatFormatting.YELLOW, ChatFormatting.BOLD), false);
		SpeedForceSpeedster.sound(p, record ? SoundEvents.PLAYER_LEVELUP : SoundEvents.NOTE_BLOCK_CHIME.value(), 1.0f, 1.4f);
		level.sendParticles(ParticleTypes.FIREWORK, now.x, now.y + 1.0, now.z, 30, 0.5, 0.8, 0.5, 0.2);
	}

	/** The race stops (no time): {@code message} said if not null. */
	static void abort(ServerPlayer p, String message) {
		Run run = RUNS.remove(p.getUUID());
		if (run == null) {
			return;
		}
		discardGhost(p.serverLevel(), run);
		if (message != null) {
			SpeedForceSpeedster.say(p, message, ChatFormatting.GRAY);
		}
	}

	private static void discardGhost(ServerLevel level, Run run) {
		if (run.ghostId >= 0 && level.getEntity(run.ghostId) instanceof SpeedForceEchoEntity e) {
			e.discard();
		}
		run.ghostId = -1;
	}

	private static void gateFx(ServerPlayer p, ServerLevel level, Vec3 gate) {
		for (int i = 0; i < 6; i++) {
			level.sendParticles(p, ParticleTypes.ELECTRIC_SPARK, true, gate.x, gate.y + 0.3 + i * 0.5, gate.z, 2, 0.15, 0.1, 0.15, 0.02);
		}
		level.sendParticles(p, ParticleTypes.END_ROD, true, gate.x, gate.y + 3.2, gate.z, 1, 0.05, 0.05, 0.05, 0.0);
	}
}
