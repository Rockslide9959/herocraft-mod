package com.projecthero.mod.speedforce;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.speedforce.data.SpeedForceMemories;
import com.projecthero.mod.speedforce.data.SpeedForceMemories.Place;
import com.projecthero.mod.speedforce.network.SpeedForceMemoryPayload;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 (user, "Speed Force Memories"): what the memory road is built from.
 * <ul>
 *   <li>the places a runner MARKED (the Shift+N menu's "Remember this place", up to 5, a sixth replacing the oldest; the
 *       menu forgets one) and the last 5 places they CAME OUT of the Speed Force at -- both in the persistent
 *       {@link SpeedForceAttachments#MEMORIES};</li>
 *   <li>{@link #doors}: every doorway the road gets -- the bed (or the world spawn), the last death, the exits, the marked
 *       places -- skipping any whose dimension no longer exists;</li>
 *   <li>the echoes' source: the runner's recent runs in the Overworld, a position every
 *       {@link SpeedForceConfig#ECHO_SAMPLE_TICKS} for the last minute ({@link #runs}; session-only, cleared through
 *       {@code SpeedForceSpeedster#clearSessionState}).</li>
 * </ul>
 */
public final class SpeedForceMemory {
	public static final int HOME = 0;
	public static final int DEATH = 1;
	public static final int EXIT = 2;
	public static final int MARKED = 3;

	/** One doorway: its kind, its index within the kind (exits: 0 = the latest), where it leads, its floating label. */
	public record Door(int kind, int index, Place place, Component label) {
	}

	/** A sample of a run: where, which way, when (the runner's tick count). */
	public record Sample(Vec3 pos, float yaw, int tick) {
	}

	private static final Map<UUID, Deque<Sample>> SAMPLES = new ConcurrentHashMap<>();

	private SpeedForceMemory() {
	}

	public static void initialize() {
		PayloadTypeRegistry.playC2S().register(SpeedForceMemoryPayload.TYPE, SpeedForceMemoryPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SpeedForceMemoryPayload.TYPE, (payload, context) -> context.server().execute(() -> {
			ServerPlayer p = context.player();
			if (payload.action() == SpeedForceMemoryPayload.REMEMBER) {
				remember(p);
			} else if (payload.action() == SpeedForceMemoryPayload.FORGET) {
				forget(p, payload.index());
			}
		}));
	}

	static void clearSessionState() {
		SAMPLES.clear();
	}

	static void forgetPlayer(UUID id) {
		SAMPLES.remove(id);
	}

	public static SpeedForceMemories of(ServerPlayer p) {
		return p.getAttachedOrElse(SpeedForceAttachments.MEMORIES, SpeedForceMemories.EMPTY);
	}

	static void save(ServerPlayer p, SpeedForceMemories m) {
		p.setAttached(SpeedForceAttachments.MEMORIES, m);
	}

	public static Place here(ServerPlayer p) {
		return new Place(p.level().dimension().location().toString(), p.getX(), p.getY(), p.getZ(), p.getYRot());
	}

	/** "Remember this place": marks where the runner stands (never inside the Speed Force itself). */
	public static boolean remember(ServerPlayer p) {
		if (!SpeedForceSpeedster.hasPower(p)) {
			return false;
		}
		if (SpeedForceRealm.inside(p)) {
			p.displayClientMessage(Component.translatable("message.projecthero.speed_force_speedster.memory_inside").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		SpeedForceMemories before = of(p);
		SpeedForceMemories after = before.remember(here(p));
		save(p, after);
		boolean replaced = before.marked().size() >= SpeedForceMemories.MARKED_MAX;
		p.displayClientMessage(Component.translatable(replaced ? "message.projecthero.speed_force_speedster.memory_saved_replaced"
				: "message.projecthero.speed_force_speedster.memory_saved", describe(here(p))).withStyle(ChatFormatting.GOLD), true);
		SpeedForceSpeedster.sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.4f);
		return true;
	}

	/** Forgets marked place {@code index}. */
	public static boolean forget(ServerPlayer p, int index) {
		SpeedForceMemories before = of(p);
		if (index < 0 || index >= before.marked().size()) {
			return false;
		}
		save(p, before.forget(index));
		p.displayClientMessage(Component.translatable("message.projecthero.speed_force_speedster.memory_forgotten").withStyle(ChatFormatting.GRAY), true);
		return true;
	}

	/** They came out of the Speed Force at {@code at}: it joins the exit history (newest first, 5 kept). */
	static void addExit(ServerPlayer p, ServerLevel level, Vec3 at, float yaw) {
		save(p, of(p).withExit(new Place(level.dimension().location().toString(), at.x, at.y, at.z, yaw), SpeedForceConfig.MEMORY_EXIT_MERGE));
	}

	// ---------------------------------------------------------------- the doorways

	public static ServerLevel levelOf(MinecraftServer server, String dimension) {
		ResourceLocation id = ResourceLocation.tryParse(dimension);
		return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
	}

	/** Every doorway the memory road gets, in order: home, death, the exits (latest first), the marked places (oldest first). */
	public static List<Door> doors(ServerPlayer p) {
		MinecraftServer server = p.getServer();
		List<Door> out = new ArrayList<>();
		if (server == null) {
			return out;
		}
		// the bed (or respawn anchor), else the world spawn
		BlockPos bed = p.getRespawnPosition();
		ResourceKey<Level> bedDim = p.getRespawnDimension();
		if (bed != null && server.getLevel(bedDim) != null) {
			add(server, out, HOME, 0, new Place(bedDim.location().toString(), bed.getX() + 0.5, bed.getY(), bed.getZ() + 0.5, 0f), "home_bed");
		} else {
			BlockPos sp = server.overworld().getSharedSpawnPos();
			add(server, out, HOME, 0, new Place(Level.OVERWORLD.location().toString(), sp.getX() + 0.5, sp.getY(), sp.getZ() + 0.5, 0f), "home_spawn");
		}
		Optional<GlobalPos> death = p.getLastDeathLocation();
		if (death.isPresent()) {
			BlockPos d = death.get().pos();
			add(server, out, DEATH, 0, new Place(death.get().dimension().location().toString(), d.getX() + 0.5, d.getY(), d.getZ() + 0.5, 0f), "death");
		}
		SpeedForceMemories m = of(p);
		for (int i = 0; i < m.exits().size(); i++) {
			add(server, out, EXIT, i, m.exits().get(i), i == 0 ? "exit_last" : "exit");
		}
		for (int i = 0; i < m.marked().size(); i++) {
			add(server, out, MARKED, i, m.marked().get(i), "marked");
		}
		return out;
	}

	private static void add(MinecraftServer server, List<Door> out, int kind, int index, Place place, String key) {
		ServerLevel level = levelOf(server, place.dimension());
		if (level == null || level.dimension() == SpeedForceRealm.DIMENSION) {
			return; // its dimension is gone (a removed datapack / mod): no doorway
		}
		Component name = Component.translatable("projecthero.speed_force.doorway." + key, index + 1);
		out.add(new Door(kind, index, place, Component.translatable("projecthero.speed_force.doorway.label", name, describe(place))
				.withStyle(kindColour(kind))));
	}

	public static ChatFormatting kindColour(int kind) {
		return switch (kind) {
			case HOME -> ChatFormatting.GREEN;
			case DEATH -> ChatFormatting.RED;
			case EXIT -> ChatFormatting.GOLD;
			default -> ChatFormatting.AQUA;
		};
	}

	/** "Overworld 120, 64, -30" (client-safe). */
	public static Component describe(Place place) {
		return Component.translatable("projecthero.speed_force.doorway.where", dimensionName(place.dimension()),
				String.valueOf((int) Math.floor(place.x())), String.valueOf((int) Math.floor(place.y())), String.valueOf((int) Math.floor(place.z())));
	}

	public static Component dimensionName(String dimension) {
		ResourceLocation id = ResourceLocation.tryParse(dimension);
		if (id == null) {
			return Component.literal(dimension);
		}
		String path = id.getPath();
		String pretty = path.isEmpty() ? path : Character.toUpperCase(path.charAt(0)) + path.substring(1).replace('_', ' ');
		return Component.translatableWithFallback("projecthero.speed_force.dim." + id.getNamespace() + "." + path.replace('/', '.'), pretty);
	}

	/**
	 * v0.17.1 (user): how long the drain lasts for a doorway trip -- the full 2 minutes to a marked place or to another
	 * dimension than they went in from; 30 s for the short hop back to their LAST exit point; the full 2 minutes for
	 * anything else. Pure (tested directly).
	 */
	public static int drainTicksFor(int kind, int index, String fromDimension, String toDimension) {
		if (kind == MARKED || !fromDimension.equals(toDimension)) {
			return SpeedForceConfig.DRAINED_TICKS;
		}
		if (kind == EXIT && index == 0) {
			return SpeedForceConfig.DRAINED_SHORT_TICKS;
		}
		return SpeedForceConfig.DRAINED_TICKS;
	}

	// ---------------------------------------------------------------- the echoes' source

	/** Every tick of a Speed Force Speedster: a sample of the run every few ticks, in the Overworld, for the last minute. */
	static void record(ServerPlayer p) {
		if (p.tickCount % SpeedForceConfig.ECHO_SAMPLE_TICKS != 0) {
			return;
		}
		Deque<Sample> q = SAMPLES.computeIfAbsent(p.getUUID(), k -> new ArrayDeque<>());
		if (!q.isEmpty() && q.peekLast().tick() > p.tickCount) {
			q.clear(); // the tick count started over (a respawn)
		}
		if (p.level().dimension() != Level.OVERWORLD) {
			return;
		}
		q.addLast(new Sample(p.position(), p.getYRot(), p.tickCount));
		while (!q.isEmpty() && q.peekFirst().tick() < p.tickCount - SpeedForceConfig.ECHO_KEEP_TICKS) {
			q.removeFirst();
		}
	}

	/** Gametests / the harness: a recorded sample as if the runner had been at {@code pos} at {@code tick}. */
	public static void recordForTest(ServerPlayer p, Vec3 pos, int tick) {
		SAMPLES.computeIfAbsent(p.getUUID(), k -> new ArrayDeque<>()).addLast(new Sample(pos, 0f, tick));
	}

	/**
	 * The runner's recent runs: unbroken stretches of the samples moving at least 6 blocks a second, at least 4 samples and
	 * 25 blocks long; the latest {@link SpeedForceConfig#ECHO_MAX}, newest last.
	 */
	public static List<List<Sample>> runs(ServerPlayer p) {
		Deque<Sample> q = SAMPLES.get(p.getUUID());
		List<List<Sample>> out = new ArrayList<>();
		if (q == null) {
			return out;
		}
		List<Sample> cur = new ArrayList<>();
		Sample last = null;
		double minStep = 6.0 * SpeedForceConfig.ECHO_SAMPLE_TICKS / 20.0;
		for (Sample s : q) {
			boolean continues = last != null && s.tick() - last.tick() <= SpeedForceConfig.ECHO_SAMPLE_TICKS * 2
					&& s.pos().distanceTo(last.pos()) >= minStep && s.pos().distanceTo(last.pos()) < 400.0;
			if (!continues) {
				close(cur, out);
				cur = new ArrayList<>();
			}
			cur.add(s);
			last = s;
		}
		close(cur, out);
		while (out.size() > SpeedForceConfig.ECHO_MAX) {
			out.remove(0);
		}
		return out;
	}

	private static void close(List<Sample> run, List<List<Sample>> out) {
		if (run.size() >= 4 && run.get(0).pos().distanceTo(run.get(run.size() - 1).pos()) >= 25.0) {
			out.add(run);
		}
	}

	static String fmt(double d) {
		return String.format(Locale.ROOT, "%.0f", d);
	}
}
