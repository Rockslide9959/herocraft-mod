package com.projecthero.mod.speedforce;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.hero.power.SafeTeleport;
import com.projecthero.mod.hero.power.p04.SuperSpeedTimeSlow;
import com.projecthero.mod.speedforce.data.SpeedForceState;
import com.projecthero.mod.speedforce.data.SpeedForceTrip;
import com.projecthero.mod.speedforce.network.SpeedForceFxPayload;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 (user: "holding z for 5 seconds while running at max momentum open up the speed force, player gets put into the speed
 * force dimension"; "you decide what using the speed force does"): <b>the Speed Force</b>, the dimension
 * {@code projecthero:speed_force} (datapack JSON, built like {@code projecthero:hulk_mind}: a void with no sky light, always
 * night, nothing spawns; its sky -- streams and bolts of lightning in the runner's colour -- is drawn client-side).
 *
 * <ul>
 *   <li><b>In:</b> through the portal Flash Time opens (see {@code SpeedForceAbilities#flashTick}). The runner lands on a long
 *       glowing road of gold and froglight built for them in their own lane, facing up it -- full health, the Speed Force
 *       full, every Speed Force cooldown reset, and the momentum held at 100% the whole time.</li>
 *   <li><b>Speed Force travel:</b> every block run up the road carries them {@link SpeedForceConfig#REALM_TRAVEL_SCALE} blocks
 *       across the real world, in the direction they were running when they went in (the road's "up" is that way, its
 *       sides are its sides); up to {@link SpeedForceConfig#REALM_TRAVEL_MAX}. The action bar counts it.</li>
 *   <li><b>Out:</b> the exit portal at the road's end ({@link SpeedForceConfig#REALM_EXIT_DISTANCE} blocks up -- the full
 *       30,000), sneaking for 1 s (anywhere on the road: out where the run has taken you), or after 60 s by itself. They
 *       come out where the travel took them, in the dimension they came from, on a safe surface, through a lightning
 *       flash -- and drained for 2 minutes ({@code SpeedForceSpeedster#drain}).</li>
 * </ul>
 */
public final class SpeedForceRealm {
	public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, ProjectHeroMod.id("speed_force"));

	/** The road: its top at this height, 7 wide, one lane per runner inside, lanes this far apart. */
	public static final int ROAD_Y = 64;
	public static final int ROAD_HALF = 3;
	public static final int LANE_SPACING = 256;
	/** Where the road starts (x) and the exit portal stands. */
	public static final int ENTRY_X = 0;
	public static final int EXIT_X = ENTRY_X + SpeedForceConfig.REALM_EXIT_DISTANCE;
	/** The exit portal's ring: centred this far above the road, this radius. */
	public static final double EXIT_RING_Y = ROAD_Y + 4.5;
	public static final double EXIT_RING_RADIUS = 4.0;

	/** runner -> their lane, and ticks they have held Sneak. */
	private static final Map<UUID, Integer> LANES = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> SNEAK = new ConcurrentHashMap<>();

	private SpeedForceRealm() {
	}

	public static void clearSessionState() {
		LANES.clear();
		SNEAK.clear();
	}

	public static boolean inside(Player p) {
		return p.level().dimension() == DIMENSION;
	}

	/** The lane's centre line (z) -- client-safe, from any z on it. */
	public static int laneZ(int lane) {
		return lane * LANE_SPACING;
	}

	public static int laneOf(double z) {
		return (int) Math.round(z / LANE_SPACING);
	}

	private static int freeLane() {
		for (int i = 0;; i++) {
			if (!LANES.containsValue(i)) {
				return i;
			}
		}
	}

	// ---------------------------------------------------------------- the travel mapping

	/**
	 * Where a run inside lands in the real world, as an offset from where they went in: the road's +X is the way they were
	 * facing ({@code entryYaw}), its +Z their right; every block x {@link SpeedForceConfig#REALM_TRAVEL_SCALE}, the whole
	 * thing capped at {@link SpeedForceConfig#REALM_TRAVEL_MAX}. Pure (tested directly).
	 */
	public static Vec3 travelOffset(float entryYaw, double alongRoad, double acrossRoad) {
		double yaw = Math.toRadians(entryYaw);
		Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
		Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
		Vec3 off = fwd.scale(alongRoad).add(right.scale(acrossRoad)).scale(SpeedForceConfig.REALM_TRAVEL_SCALE);
		double len = off.length();
		if (len > SpeedForceConfig.REALM_TRAVEL_MAX) {
			off = off.scale(SpeedForceConfig.REALM_TRAVEL_MAX / len);
		}
		return off;
	}

	/** The real-world offset the runner's position inside is worth right now. */
	public static Vec3 travelled(SpeedForceTrip trip, Vec3 at) {
		return travelOffset(trip.yaw(), at.x - trip.entryX(), at.z - trip.entryZ());
	}

	// ---------------------------------------------------------------- in

	/** Into the Speed Force (from the portal). False (nothing done) if the dimension is missing or they are already inside. */
	public static boolean enter(ServerPlayer player) {
		if (inside(player) || player.getAttachedOrElse(SpeedForceAttachments.TRIP, null) != null) {
			return false;
		}
		MinecraftServer server = player.getServer();
		ServerLevel realm = server == null ? null : server.getLevel(DIMENSION);
		if (realm == null) {
			return false;
		}
		SpeedForceAbilities.endFlashTime(player);
		player.stopRiding();
		SpeedForceState cur = SpeedForceSpeedster.state(player);
		if (cur.carryId != 0) {
			var carried = player.level().getEntity(cur.carryId);
			SpeedForceAbilities.release(player, carried instanceof net.minecraft.world.entity.LivingEntity le ? le : null);
		}
		int lane = freeLane();
		LANES.put(player.getUUID(), lane);
		buildRoad(realm, lane);
		double ex = ENTRY_X + 0.5;
		double ey = ROAD_Y + 1;
		double ez = laneZ(lane) + 0.5;
		player.setAttached(SpeedForceAttachments.TRIP, new SpeedForceTrip(player.level().dimension().location().toString(),
				player.getX(), player.getY(), player.getZ(), player.getYRot(), ex, ey, ez, lane, 0));
		player.teleportTo(realm, ex, ey, ez, -90.0f, 0.0f);
		player.resetFallDistance();
		// inside: full health, the Speed Force full, every cooldown reset, running at 100%
		player.setHealth(player.getMaxHealth());
		player.clearFire();
		SpeedForceState n = SpeedForceSpeedster.state(player).copy();
		n.meter = SpeedForceConfig.METER_MAX;
		n.momentum = 1f;
		n.speedMode = true;
		n.abilityReadyAt.clear();
		SpeedForceSpeedster.save(player, n);
		SpeedForceSpeedster.graceMotion(player, 4);
		realm.sendParticles(ParticleTypes.FLASH, ex, ey + 1.0, ez, 2, 0.2, 0.2, 0.2, 0);
		realm.sendParticles(ParticleTypes.ELECTRIC_SPARK, ex, ey + 1.0, ez, 60, 0.8, 1.0, 0.8, 0.5);
		realm.playSound(null, ex, ey, ez, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.0f, 1.6f);
		realm.playSound(null, ex, ey, ez, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.8f);
		player.displayClientMessage(Component.translatable("message.projecthero.speed_force_speedster.realm_enter")
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		player.displayClientMessage(Component.translatable("message.projecthero.speed_force_speedster.realm_hint")
				.withStyle(ChatFormatting.YELLOW), false);
		return true;
	}

	/** The lane's road (gold, edged and striped with glowing froglight) and, at its far end, the exit portal's gold ring. */
	static void buildRoad(ServerLevel level, int lane) {
		int z0 = laneZ(lane);
		BlockState gold = Blocks.GOLD_BLOCK.defaultBlockState();
		BlockState glow = Blocks.OCHRE_FROGLIGHT.defaultBlockState();
		BlockState air = Blocks.AIR.defaultBlockState();
		int flags = Block.UPDATE_CLIENTS;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int x = ENTRY_X - 24; x <= EXIT_X + 24; x++) {
			for (int dz = -ROAD_HALF; dz <= ROAD_HALF; dz++) {
				boolean edge = Math.abs(dz) == ROAD_HALF;
				boolean stripe = dz == 0 && Math.floorMod(x, 4) < 2;
				level.setBlock(p.set(x, ROAD_Y, z0 + dz), edge || stripe ? glow : gold, flags);
			}
		}
		// the exit ring: gold blocks round a circle standing across the road at EXIT_X
		for (int dy = 0; dy <= 9; dy++) {
			for (int dz = -5; dz <= 5; dz++) {
				double dyc = ROAD_Y + 1 + dy + 0.5 - EXIT_RING_Y; // the block's centre against the ring's
				double d = Math.sqrt(dyc * dyc + dz * dz);
				BlockPos at = new BlockPos(EXIT_X, ROAD_Y + 1 + dy, z0 + dz);
				boolean ring = Math.abs(d - EXIT_RING_RADIUS) < 0.55;
				level.setBlock(at, ring ? gold : air, flags);
			}
		}
	}

	// ---------------------------------------------------------------- inside

	/** Every tick of a Speed Force Speedster (from {@code SpeedForceSpeedster#tick}). */
	static void tick(ServerPlayer player) {
		SpeedForceTrip trip = player.getAttachedOrElse(SpeedForceAttachments.TRIP, null);
		if (!inside(player)) {
			if (trip != null || LANES.containsKey(player.getUUID())) {
				// out some other way (a death, a command): the trip is over
				player.removeAttached(SpeedForceAttachments.TRIP);
				LANES.remove(player.getUUID());
				SNEAK.remove(player.getUUID());
			}
			return;
		}
		if (trip == null) {
			toSpawn(player); // no business here
			return;
		}
		LANES.putIfAbsent(player.getUUID(), trip.lane());
		int ticks = trip.ticks() + 1;
		trip = trip.withTicks(ticks);
		player.setAttached(SpeedForceAttachments.TRIP, trip);
		// the Speed Force carries you: 100% momentum, the meter full, never a fall
		SpeedForceState s = SpeedForceSpeedster.state(player);
		if (s.momentum < 1f || s.meter < SpeedForceConfig.METER_MAX || !s.speedMode) {
			SpeedForceState n = s.copy();
			n.momentum = 1f;
			n.meter = SpeedForceConfig.METER_MAX;
			n.speedMode = true;
			SpeedForceSpeedster.save(player, n);
		}
		player.resetFallDistance();
		ServerLevel level = player.serverLevel();
		// off the road into the void: back on it
		if (player.getY() < ROAD_Y - 12) {
			double x = Math.max(ENTRY_X, Math.min(EXIT_X - 2, player.getX()));
			SpeedForceSpeedster.graceMotion(player, 3);
			player.teleportTo(level, x, ROAD_Y + 1, laneZ(trip.lane()) + 0.5, player.getYRot(), player.getXRot());
			player.setDeltaMovement(Vec3.ZERO);
		}
		Vec3 off = travelled(trip, player.position());
		if (ticks % 10 == 0) {
			int left = Math.max(0, (SpeedForceConfig.REALM_MAX_TICKS - ticks + 19) / 20);
			player.displayClientMessage(Component.translatable("message.projecthero.speed_force_speedster.realm_travel",
					String.format(java.util.Locale.ROOT, "%,d", Math.round(off.length())), compass(off), left).withStyle(ChatFormatting.GOLD), true);
		}
		// out: the exit portal, a held sneak, or the time
		int sneak = player.isShiftKeyDown() ? SNEAK.merge(player.getUUID(), 1, Integer::sum) : 0;
		if (!player.isShiftKeyDown()) {
			SNEAK.remove(player.getUUID());
		}
		boolean atExit = player.getX() >= EXIT_X - 0.3 && Math.abs(player.getZ() - (laneZ(trip.lane()) + 0.5)) <= EXIT_RING_RADIUS + 1.0;
		if (atExit || sneak >= SpeedForceConfig.REALM_SNEAK_TICKS || ticks >= SpeedForceConfig.REALM_MAX_TICKS) {
			exit(player);
		}
	}

	/** N / NE / E ... of a real-world offset (the way the run is carrying you). */
	static String compass(Vec3 off) {
		if (off.horizontalDistanceSqr() < 1.0) {
			return "-";
		}
		// Minecraft: -Z is north, +X east
		double deg = Math.toDegrees(Math.atan2(off.x, -off.z));
		String[] names = { "N", "NE", "E", "SE", "S", "SW", "W", "NW" };
		return names[Math.floorMod((int) Math.round(deg / 45.0), 8)];
	}

	// ---------------------------------------------------------------- out

	/**
	 * Out of the Speed Force: where the run inside carried them (in the dimension they came from, on a safe surface), through a
	 * bolt of their lightning -- and drained for 2 minutes. True if they were inside (with a trip) and are out now.
	 */
	public static boolean exit(ServerPlayer player) {
		SpeedForceTrip trip = player.getAttachedOrElse(SpeedForceAttachments.TRIP, null);
		MinecraftServer server = player.getServer();
		if (trip == null || server == null) {
			return false;
		}
		Vec3 off = inside(player) ? travelled(trip, player.position()) : Vec3.ZERO;
		ServerLevel home = homeLevel(server, trip);
		Vec3 from = new Vec3(trip.x(), trip.y(), trip.z());
		boolean sameDim = home.dimension().location().toString().equals(trip.dimension());
		Vec3 target = sameDim ? from.add(off) : spawn(home);
		Vec3 land = landing(home, player, target, sameDim ? from : target);
		player.removeAttached(SpeedForceAttachments.TRIP);
		LANES.remove(player.getUUID());
		SNEAK.remove(player.getUUID());
		SuperSpeedTimeSlow.end(player, false);
		float yaw = trip.yaw();
		player.teleportTo(home, land.x, land.y, land.z, yaw, 0.0f);
		player.resetFallDistance();
		player.setDeltaMovement(Vec3.ZERO);
		SpeedForceSpeedster.drain(player, SpeedForceConfig.DRAINED_TICKS);
		SpeedForceAbilities.cooldown(player, SpeedForceAbilities.PORTAL, SpeedForceConfig.PORTAL_COOLDOWN);
		home.sendParticles(ParticleTypes.FLASH, land.x, land.y + 1.0, land.z, 2, 0.2, 0.2, 0.2, 0);
		home.sendParticles(ParticleTypes.ELECTRIC_SPARK, land.x, land.y + 1.0, land.z, 80, 0.8, 1.2, 0.8, 0.6);
		home.sendParticles(SpeedForceAbilities.dust(player, 2.2f), land.x, land.y + 1.0, land.z, 40, 0.6, 1.0, 0.6, 0.0);
		home.playSound(null, land.x, land.y, land.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.2f, 1.3f);
		home.playSound(null, land.x, land.y, land.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.0f, 1.0f);
		SpeedForceSpeedster.fx(player, SpeedForceFxPayload.REALM_EXIT, land.add(0, 1.0, 0), land.add(0, 28.0, 0));
		player.displayClientMessage(Component.empty(), true); // the travel counter goes with the Speed Force
		player.displayClientMessage(Component.translatable("message.projecthero.speed_force_speedster.realm_exit",
				String.format(java.util.Locale.ROOT, "%,d", Math.round(land.subtract(from).horizontalDistance())))
				.withStyle(ChatFormatting.GOLD), false);
		player.displayClientMessage(Component.translatable("message.projecthero.speed_force_speedster.drained")
				.withStyle(ChatFormatting.RED), false);
		return true;
	}

	private static ServerLevel homeLevel(MinecraftServer server, SpeedForceTrip trip) {
		ResourceLocation id = ResourceLocation.tryParse(trip.dimension());
		ServerLevel level = id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
		return level == null || level.dimension() == DIMENSION ? server.overworld() : level;
	}

	private static Vec3 spawn(ServerLevel level) {
		BlockPos sp = level.getSharedSpawnPos();
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sp.getX(), sp.getZ());
		return new Vec3(sp.getX() + 0.5, Math.max(y, level.getMinBuildHeight() + 1), sp.getZ() + 0.5);
	}

	/**
	 * A safe place to come out at (or near) {@code target}: inside the world border, the top of the ground there (a level with
	 * a ceiling -- the Nether -- is searched for a floor near the height they went in at), out of lava; a spiral out to 24
	 * blocks if the spot itself won't do, and where they went in ({@code fallback}) if nothing near will.
	 */
	static Vec3 landing(ServerLevel level, ServerPlayer player, Vec3 target, Vec3 fallback) {
		WorldBorder border = level.getWorldBorder();
		double x = Math.max(border.getMinX() + 8, Math.min(border.getMaxX() - 8, target.x));
		double z = Math.max(border.getMinZ() + 8, Math.min(border.getMaxZ() - 8, target.z));
		int bx = (int) Math.floor(x);
		int bz = (int) Math.floor(z);
		for (int r = 0; r <= 24; r += 4) {
			for (int dx = -r; dx <= r; dx += 4) {
				for (int dz = -r; dz <= r; dz += 4) {
					if (r > 0 && Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					Vec3 at = standAt(level, player, bx + dx, bz + dz, fallback.y);
					if (at != null) {
						return at;
					}
				}
			}
		}
		Vec3 back = standAt(level, player, (int) Math.floor(fallback.x), (int) Math.floor(fallback.z), fallback.y);
		return back != null ? back : fallback;
	}

	private static Vec3 standAt(ServerLevel level, ServerPlayer player, int x, int z, double nearY) {
		level.getChunk(x >> 4, z >> 4); // generate / load the column
		if (level.dimensionType().hasCeiling()) {
			int top = level.getMinBuildHeight() + level.getLogicalHeight() - 2;
			int start = (int) Math.max(level.getMinBuildHeight() + 1, Math.min(top, Math.floor(nearY)));
			for (int d = 0; d < level.getLogicalHeight(); d++) {
				for (int y : new int[] { start - d, start + d }) {
					if (y > level.getMinBuildHeight() && y < top && standable(level, player, x, y, z)) {
						return new Vec3(x + 0.5, y, z + 0.5);
					}
				}
			}
			return null;
		}
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		if (y <= level.getMinBuildHeight() + 1) {
			return null; // nothing to stand on (the End's void)
		}
		return standable(level, player, x, y, z) ? new Vec3(x + 0.5, y, z + 0.5) : null;
	}

	private static boolean standable(ServerLevel level, ServerPlayer player, int x, int y, int z) {
		BlockPos below = new BlockPos(x, y - 1, z);
		BlockState floor = level.getBlockState(below);
		if (floor.getFluidState().is(net.minecraft.tags.FluidTags.LAVA) || floor.is(Blocks.MAGMA_BLOCK) || floor.is(Blocks.FIRE)) {
			return false;
		}
		boolean ground = floor.isFaceSturdy(level, below, net.minecraft.core.Direction.UP) || floor.getFluidState().is(net.minecraft.tags.FluidTags.WATER);
		return ground && SafeTeleport.isSafe(level, player, new Vec3(x + 0.5, y, z + 0.5));
	}

	/** Logged in: still inside from before (a logout or a crash inside) -- out, where the run had taken them. */
	public static void onJoin(ServerPlayer player) {
		if (!inside(player)) {
			if (player.getAttachedOrElse(SpeedForceAttachments.TRIP, null) != null) {
				player.removeAttached(SpeedForceAttachments.TRIP);
			}
			return;
		}
		if (!exit(player)) {
			toSpawn(player);
		}
	}

	/** Anyone in the Speed Force without a trip (or without the power): back to the world spawn. */
	public static void toSpawn(ServerPlayer player) {
		MinecraftServer server = player.getServer();
		if (server == null) {
			return;
		}
		ServerLevel overworld = server.overworld();
		Vec3 at = spawn(overworld);
		player.teleportTo(overworld, at.x, at.y, at.z, player.getYRot(), player.getXRot());
	}
}
