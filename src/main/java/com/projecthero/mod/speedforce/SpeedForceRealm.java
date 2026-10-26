package com.projecthero.mod.speedforce;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.hero.power.SafeTeleport;
import com.projecthero.mod.hero.power.p04.SuperSpeedTimeSlow;
import com.projecthero.mod.speedforce.SpeedForceMemory.Door;
import com.projecthero.mod.speedforce.SpeedForceMemory.Sample;
import com.projecthero.mod.speedforce.data.SpeedForceMemories.Place;
import com.projecthero.mod.speedforce.data.SpeedForceState;
import com.projecthero.mod.speedforce.data.SpeedForceTrip;
import com.projecthero.mod.speedforce.entity.BlackFlashEntity;
import com.projecthero.mod.speedforce.entity.SpeedForceDoorwayEntity;
import com.projecthero.mod.speedforce.entity.SpeedForceEchoEntity;
import com.projecthero.mod.speedforce.entity.SpeedForceEntityTypes;
import com.projecthero.mod.speedforce.network.SpeedForceFxPayload;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 (user: "holding z for 5 seconds while running at max momentum open up the speed force"): <b>the Speed Force</b>,
 * the dimension {@code projecthero:speed_force} (datapack JSON, built like {@code projecthero:hulk_mind}: a void with no sky
 * light, always night, nothing spawns; its sky -- streams and bolts of lightning in the runner's colour -- is drawn
 * client-side). v0.17.1 round 2 (user, "Speed Force Memories"):
 *
 * <ul>
 *   <li><b>In:</b> through the portal a held Z opens at 100% momentum ({@code SpeedForceAbilities#portalTick}). Full health,
 *       the Speed Force full, every cooldown reset, the momentum held at 100% while inside.</li>
 *   <li><b>The memory road:</b> a glowing road of gold and froglight in the runner's own lane, branching into doorways
 *       ({@link SpeedForceDoorwayEntity}) to places they have been ({@link SpeedForceMemory#doors}): their bed (or the
 *       world spawn), their last death, the last 5 places they came out of the Speed Force at and up to 5 places they
 *       marked from the Shift+N menu, each with a floating label. Doorways whose dimension is gone are skipped.</li>
 *   <li><b>Fast travel:</b> running through a doorway brings them out at that place, in any dimension, on a safe spot
 *       ({@link #landingNear}, the old safe-landing search {@link #landing} as its fallback).</li>
 *   <li><b>Echoes:</b> see-through after-images of the runner ({@link SpeedForceEchoEntity}) replay their recent Overworld
 *       runs up the road; running through one gives back {@link SpeedForceConfig#ECHO_REFUND} Speed Force, once each.</li>
 *   <li><b>Out:</b> a doorway, or Sneak held 1 s (back where they went in). The drain after: the full 2 minutes to another
 *       dimension or a marked place (and for Sneak), 30 s for the short hop back to their last exit point
 *       ({@link SpeedForceMemory#drainTicksFor}).</li>
 *   <li><b>The Black Flash:</b> stay longer than 30 s and he appears down the road, charges and punches them OUT -- back to
 *       where they went in, with him following them into the real world for the fight ({@link BlackFlash}). No drain
 *       while he fights; beat him and there is none at all.</li>
 * </ul>
 */
public final class SpeedForceRealm {
	public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, ProjectHeroMod.id("speed_force"));

	/** The road: its top at this height, 7 wide, one lane per runner inside, lanes this far apart. */
	public static final int ROAD_Y = 64;
	public static final int ROAD_HALF = 3;
	public static final int LANE_SPACING = 256;
	/** Where the road starts (x) and ends. */
	public static final int ENTRY_X = 0;
	public static final int ROAD_END_X = ENTRY_X + SpeedForceConfig.REALM_ROAD_LENGTH;
	/** Doorway slots (two a pair, one each side of the road). */
	public static final int DOOR_SLOTS = 12;
	/** A doorway's opening: 5 wide (dx -2..2), 5 tall; its frame one block round it; its swirl centred this high. */
	public static final int DOOR_HALF = 2;
	public static final double DOOR_CENTRE_Y = ROAD_Y + 3.5;
	public static final double DOOR_RADIUS = 2.3;
	/** The old (pre-memories) road ran out to here with an exit ring at 300: cleared away if a lane still has it. */
	private static final int OLD_ROAD_END = 330;

	/** One echo on the road: its entity, its track (a world point every {@link SpeedForceConfig#ECHO_SAMPLE_TICKS}), its phase. */
	static final class Echo {
		final int id;
		final List<Vec3> track;
		final int offset;

		Echo(int id, List<Vec3> track, int offset) {
			this.id = id;
			this.track = track;
			this.offset = offset;
		}
	}

	/** What a runner's trip has built: the doorways (and their entities), the echoes, the Black Flash's charge. */
	static final class Live {
		final List<Door> doors = new ArrayList<>();
		final List<Integer> doorIds = new ArrayList<>();
		final List<Echo> echoes = new ArrayList<>();
		int bfId = -1;
		int bfStart = -1;
		Vec3 bfFrom;
	}

	/** runner -> their lane, ticks they have held Sneak, what the trip built. */
	private static final Map<UUID, Integer> LANES = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> SNEAK = new ConcurrentHashMap<>();
	private static final Map<UUID, Live> LIVE = new ConcurrentHashMap<>();

	private SpeedForceRealm() {
	}

	public static void clearSessionState() {
		LANES.clear();
		SNEAK.clear();
		LIVE.clear();
		SPENT.clear();
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

	/** Doorway slot {@code slot}'s x (the middle of its opening) and side of the road (-1 / +1). */
	public static int doorX(int slot) {
		return ENTRY_X + SpeedForceConfig.REALM_DOOR_FIRST_X + (slot / 2) * SpeedForceConfig.REALM_DOOR_SPACING;
	}

	public static int doorSide(int slot) {
		return slot % 2 == 0 ? -1 : 1;
	}

	/** The block z the doorway's frame stands at. */
	public static int doorZ(int lane, int slot) {
		return laneZ(lane) + doorSide(slot) * (ROAD_HALF + SpeedForceConfig.REALM_SPUR_LENGTH);
	}

	private static int freeLane() {
		for (int i = 0;; i++) {
			if (!LANES.containsValue(i)) {
				return i;
			}
		}
	}

	/** The doorways this runner's road has right now (tests, the harness). */
	public static List<Door> doorsOf(ServerPlayer p) {
		Live live = LIVE.get(p.getUUID());
		return live == null ? List.of() : List.copyOf(live.doors);
	}

	/** Echo entity ids on this runner's road (tests, the harness). */
	public static List<Integer> echoesOf(ServerPlayer p) {
		Live live = LIVE.get(p.getUUID());
		List<Integer> out = new ArrayList<>();
		if (live != null) {
			for (Echo e : live.echoes) {
				out.add(e.id);
			}
		}
		return out;
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
		double ex = ENTRY_X + 0.5;
		double ey = ROAD_Y + 1;
		double ez = laneZ(lane) + 0.5;
		// the doorways come from where they have been -- read before they leave
		List<Door> doors = SpeedForceMemory.doors(player);
		List<List<Sample>> runs = SpeedForceMemory.runs(player);
		player.setAttached(SpeedForceAttachments.TRIP, new SpeedForceTrip(player.level().dimension().location().toString(),
				player.getX(), player.getY(), player.getZ(), player.getYRot(), ex, ey, ez, lane, 0));
		buildRoad(realm, lane, doors.size());
		player.teleportTo(realm, ex, ey, ez, -90.0f, 0.0f);
		player.resetFallDistance();
		// inside: full health, the Speed Force full, every cooldown reset, running at 100%
		player.setHealth(player.getMaxHealth());
		player.clearFire();
		SpeedForceState n = SpeedForceSpeedster.state(player).copy();
		n.meter = SpeedForceSpeedster.meterMax(player);
		n.momentum = 1f;
		n.speedMode = true;
		n.abilityReadyAt.clear();
		SpeedForceSpeedster.save(player, n);
		SpeedForceSpeedster.graceMotion(player, 4);
		Live live = new Live();
		LIVE.put(player.getUUID(), live);
		spawnDoors(realm, lane, doors, live);
		spawnEchoes(realm, player, lane, runs, live);
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

	/**
	 * The lane's memory road: gold edged and striped with glowing froglight, {@link SpeedForceConfig#REALM_ROAD_LENGTH} long,
	 * and a branch out to a doorway frame for each of the first {@code doors} slots (the rest cleared away).
	 */
	static void buildRoad(ServerLevel level, int lane, int doors) {
		int z0 = laneZ(lane);
		BlockState gold = Blocks.GOLD_BLOCK.defaultBlockState();
		BlockState glow = Blocks.OCHRE_FROGLIGHT.defaultBlockState();
		BlockState air = Blocks.AIR.defaultBlockState();
		int flags = Block.UPDATE_CLIENTS;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int x = ENTRY_X - 24; x <= ROAD_END_X + 4; x++) {
			for (int dz = -ROAD_HALF; dz <= ROAD_HALF; dz++) {
				boolean edge = Math.abs(dz) == ROAD_HALF;
				boolean stripe = dz == 0 && Math.floorMod(x, 4) < 2;
				level.setBlock(p.set(x, ROAD_Y, z0 + dz), edge || stripe ? glow : gold, flags);
			}
		}
		// an older trip's long road and exit ring (before the memories): gone
		if (!level.getBlockState(p.set(ROAD_END_X + 10, ROAD_Y, z0)).isAir()) {
			for (int x = ROAD_END_X + 5; x <= OLD_ROAD_END; x++) {
				for (int dz = -5; dz <= 5; dz++) {
					for (int dy = 0; dy <= 10; dy++) {
						level.setBlock(p.set(x, ROAD_Y + dy, z0 + dz), air, flags);
					}
				}
			}
		}
		int spur = SpeedForceConfig.REALM_SPUR_LENGTH;
		for (int slot = 0; slot < DOOR_SLOTS; slot++) {
			int dx0 = doorX(slot);
			int side = doorSide(slot);
			boolean used = slot < doors;
			for (int k = ROAD_HALF + 1; k <= ROAD_HALF + spur + 1; k++) {
				int z = z0 + side * k;
				for (int dx = -DOOR_HALF - 1; dx <= DOOR_HALF + 1; dx++) {
					for (int dy = 0; dy <= DOOR_HALF + 5; dy++) {
						BlockState s = air;
						if (used) {
							boolean frameRow = k == ROAD_HALF + spur;
							boolean floor = dy == 0 && Math.abs(dx) <= DOOR_HALF;
							boolean pillar = frameRow && Math.abs(dx) == DOOR_HALF + 1 && dy >= 0 && dy <= 6;
							boolean lintel = frameRow && dy == 6 && Math.abs(dx) <= DOOR_HALF + 1;
							if (floor) {
								s = Math.abs(dx) == DOOR_HALF || (dx == 0 && k % 2 == 0) ? glow : gold;
							} else if (pillar || lintel) {
								s = (dy == 6 && (Math.abs(dx) == DOOR_HALF + 1 || dx == 0)) || dy == 0 ? glow : gold;
							}
						}
						level.setBlock(p.set(dx0 + dx, ROAD_Y + dy, z), s, flags);
					}
				}
			}
		}
	}

	private static void spawnDoors(ServerLevel realm, int lane, List<Door> doors, Live live) {
		for (int i = 0; i < doors.size() && i < DOOR_SLOTS; i++) {
			Door d = doors.get(i);
			SpeedForceDoorwayEntity e = SpeedForceEntityTypes.DOORWAY.create(realm);
			if (e == null) {
				continue;
			}
			e.moveTo(doorX(i) + 0.5, ROAD_Y + 1, doorZ(lane, i) + 0.5, 0f, 0f);
			e.setup(d.kind(), doorSide(i));
			e.setCustomName(d.label());
			realm.addFreshEntity(e);
			live.doors.add(d);
			live.doorIds.add(e.getId());
		}
	}

	/**
	 * The echoes: each of the runner's recent runs ({@link SpeedForceMemory#runs}) laid along the road -- its own heading
	 * turned to run up the road, scaled to fit it, its weave kept (squeezed onto the road's width) -- and replayed at the pace
	 * it was run, over and over. With no recorded run, two plain sprints up the road stand in.
	 */
	private static void spawnEchoes(ServerLevel realm, ServerPlayer player, int lane, List<List<Sample>> runs, Live live) {
		double zc = laneZ(lane) + 0.5;
		double[] lanes = { -1.5, 1.5, -0.5, 0.5 };
		List<List<Vec3>> tracks = new ArrayList<>();
		for (List<Sample> run : runs) {
			tracks.add(roadTrack(run, zc, lanes[tracks.size() % lanes.length]));
		}
		if (tracks.isEmpty()) {
			for (int k = 0; k < 2; k++) {
				List<Vec3> t = new ArrayList<>();
				for (int i = 0; i <= 24; i++) {
					t.add(new Vec3(ENTRY_X + 8 + i * 5.0, ROAD_Y + 1, zc + lanes[k] + Math.sin(i * 0.5 + k) * 0.6));
				}
				tracks.add(t);
			}
		}
		for (int i = 0; i < tracks.size(); i++) {
			SpeedForceEchoEntity e = SpeedForceEntityTypes.ECHO.create(realm);
			if (e == null) {
				continue;
			}
			List<Vec3> t = tracks.get(i);
			e.setOwner(player.getUUID());
			e.moveTo(t.get(0).x, t.get(0).y, t.get(0).z, -90f, 0f);
			realm.addFreshEntity(e);
			live.echoes.add(new Echo(e.getId(), t, i * 23));
		}
	}

	/** One run turned onto the road (pure: tested through the trip). */
	static List<Vec3> roadTrack(List<Sample> run, double laneCentreZ, double offset) {
		Vec3 start = run.get(0).pos();
		Vec3 end = run.get(run.size() - 1).pos();
		Vec3 dir = new Vec3(end.x - start.x, 0, end.z - start.z);
		dir = dir.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : dir.normalize();
		Vec3 right = new Vec3(-dir.z, 0, dir.x);
		double along = Math.max(1.0, end.subtract(start).dot(dir));
		double scale = Math.max(0.05, Math.min(2.0, (SpeedForceConfig.REALM_ROAD_LENGTH - 24) / along));
		List<Vec3> out = new ArrayList<>();
		for (Sample s : run) {
			Vec3 d = s.pos().subtract(start);
			double x = ENTRY_X + 8 + d.dot(dir) * scale;
			double z = laneCentreZ + Math.max(-2.4, Math.min(2.4, offset + d.dot(right) * scale));
			out.add(new Vec3(x, ROAD_Y + 1, z));
		}
		return out;
	}

	// ---------------------------------------------------------------- inside

	/** Every tick of a Speed Force Speedster (from {@code SpeedForceSpeedster#tick}). */
	static void tick(ServerPlayer player) {
		SpeedForceTrip trip = player.getAttachedOrElse(SpeedForceAttachments.TRIP, null);
		if (!inside(player)) {
			if (trip != null || LANES.containsKey(player.getUUID()) || LIVE.containsKey(player.getUUID())) {
				// out some other way (a death, a command): the trip is over
				player.removeAttached(SpeedForceAttachments.TRIP);
				LANES.remove(player.getUUID());
				SNEAK.remove(player.getUUID());
				clearLive(player);
			}
			return;
		}
		if (trip == null) {
			toSpawn(player); // no business here
			return;
		}
		ServerLevel level = player.serverLevel();
		LANES.putIfAbsent(player.getUUID(), trip.lane());
		Live live = LIVE.get(player.getUUID());
		if (live == null) {
			// the trip outlived its road (a reload): build it again
			live = new Live();
			LIVE.put(player.getUUID(), live);
			List<Door> doors = SpeedForceMemory.doors(player);
			buildRoad(level, trip.lane(), doors.size());
			spawnDoors(level, trip.lane(), doors, live);
			spawnEchoes(level, player, trip.lane(), SpeedForceMemory.runs(player), live);
		}
		int ticks = trip.ticks() + 1;
		trip = trip.withTicks(ticks);
		player.setAttached(SpeedForceAttachments.TRIP, trip);
		// the Speed Force carries you: 100% momentum, never a fall (the meter is filled on the way in, not held)
		SpeedForceState s = SpeedForceSpeedster.state(player);
		if (s.momentum < 1f || !s.speedMode) {
			SpeedForceState n = s.copy();
			n.momentum = 1f;
			n.speedMode = true;
			SpeedForceSpeedster.save(player, n);
		}
		player.resetFallDistance();
		// off the road into the void: back on it
		if (player.getY() < ROAD_Y - 12) {
			double x = Math.max(ENTRY_X, Math.min(ROAD_END_X - 2, player.getX()));
			SpeedForceSpeedster.graceMotion(player, 3);
			player.teleportTo(level, x, ROAD_Y + 1, laneZ(trip.lane()) + 0.5, player.getYRot(), player.getXRot());
			player.setDeltaMovement(Vec3.ZERO);
		}
		tickEchoes(level, player, live, ticks);
		// the Black Flash: he has come -- nothing gets you out now but his fist
		if (live.bfStart >= 0) {
			tickBlackFlash(level, player, live, trip);
			return;
		}
		if (ticks % 10 == 0) {
			int left = Math.max(0, (SpeedForceConfig.BLACK_FLASH_AFTER_TICKS - ticks + 19) / 20);
			player.displayClientMessage(Component.translatable("message.projecthero.speed_force_speedster.realm_status", left)
					.withStyle(left <= 10 ? ChatFormatting.RED : ChatFormatting.GOLD), true);
		}
		// out: a doorway, or a held sneak (back where they went in)
		for (int i = 0; i < live.doors.size(); i++) {
			if (throughDoor(player, trip.lane(), i)) {
				takeDoor(player, live.doors.get(i));
				return;
			}
		}
		int sneak = player.isShiftKeyDown() ? SNEAK.merge(player.getUUID(), 1, Integer::sum) : 0;
		if (!player.isShiftKeyDown()) {
			SNEAK.remove(player.getUUID());
		}
		if (sneak >= SpeedForceConfig.REALM_SNEAK_TICKS) {
			exit(player);
			return;
		}
		if (ticks > SpeedForceConfig.BLACK_FLASH_AFTER_TICKS) {
			startBlackFlash(level, player, live, ticks);
		}
	}

	/** Whether the runner has run into doorway slot {@code slot}'s opening. */
	static boolean throughDoor(ServerPlayer p, int lane, int slot) {
		int side = doorSide(slot);
		double planeZ = doorZ(lane, slot) + 0.5;
		return Math.abs(p.getX() - (doorX(slot) + 0.5)) <= DOOR_HALF + 0.6 && side * (p.getZ() - planeZ) >= -0.2
				&& p.getY() >= ROAD_Y - 1 && p.getY() < ROAD_Y + 7;
	}

	private static void tickEchoes(ServerLevel level, ServerPlayer player, Live live, int ticks) {
		for (Echo echo : live.echoes) {
			if (!(level.getEntity(echo.id) instanceof SpeedForceEchoEntity e)) {
				continue;
			}
			int per = SpeedForceConfig.ECHO_SAMPLE_TICKS;
			int dur = (echo.track.size() - 1) * per;
			int loop = dur + 20;
			int t = (ticks + echo.offset) % Math.max(1, loop);
			Vec3 at;
			Vec3 next;
			if (t >= dur) {
				at = echo.track.get(echo.track.size() - 1);
				next = at;
			} else {
				int i = t / per;
				double f = (t % per) / (double) per;
				Vec3 a = echo.track.get(i);
				Vec3 b = echo.track.get(Math.min(echo.track.size() - 1, i + 1));
				at = a.lerp(b, f);
				next = b;
			}
			Vec3 d = next.subtract(at);
			float yaw = d.horizontalDistanceSqr() > 1.0e-4 ? (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0) : e.getYRot();
			e.moveTo(at.x, at.y, at.z, yaw, 0f);
			e.setYHeadRot(yaw);
			// run through it: a little of the Speed Force back, once per echo
			if (!echoSpent(player, echo) && player.getBoundingBox().inflate(0.35).intersects(e.getBoundingBox())) {
				SPENT.computeIfAbsent(player.getUUID(), k -> ConcurrentHashMap.newKeySet()).add(echo.id);
				e.setSpent(true);
				SpeedForceSpeedster.addMeter(player, SpeedForceConfig.ECHO_REFUND);
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 1.0, at.z, 16, 0.3, 0.6, 0.3, 0.15);
				level.sendParticles(SpeedForceAbilities.dust(player, 1.4f), at.x, at.y + 1.0, at.z, 12, 0.3, 0.6, 0.3, 0.0);
				SpeedForceSpeedster.sound(player, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.6f);
				player.displayClientMessage(Component.translatable("message.projecthero.speed_force_speedster.echo",
						String.format(java.util.Locale.ROOT, "%.0f", SpeedForceConfig.ECHO_REFUND)).withStyle(ChatFormatting.YELLOW), true);
			}
		}
	}

	/** Echoes a runner has caught this trip. */
	private static final Map<UUID, java.util.Set<Integer>> SPENT = new ConcurrentHashMap<>();

	private static boolean echoSpent(ServerPlayer p, Echo echo) {
		return echoCaught(p, echo.id);
	}

	/** Test / harness hook: whether this runner has caught echo {@code id}. */
	public static boolean echoCaught(ServerPlayer p, int id) {
		java.util.Set<Integer> s = SPENT.get(p.getUUID());
		return s != null && s.contains(id);
	}

	private static void clearLive(ServerPlayer player) {
		Live live = LIVE.remove(player.getUUID());
		SPENT.remove(player.getUUID());
		MinecraftServer server = player.getServer();
		ServerLevel realm = server == null ? null : server.getLevel(DIMENSION);
		if (live == null || realm == null) {
			return;
		}
		for (int id : live.doorIds) {
			discard(realm.getEntity(id));
		}
		for (Echo e : live.echoes) {
			discard(realm.getEntity(e.id));
		}
		discard(realm.getEntity(live.bfId));
	}

	private static void discard(Entity e) {
		if (e != null) {
			e.discard();
		}
	}

	// ---------------------------------------------------------------- the Black Flash, inside

	/** 30 s in: he appears down the road -- the warning title and sound -- and stands a moment before he runs. */
	static void startBlackFlash(ServerLevel level, ServerPlayer player, Live live, int tripTicks) {
		BlackFlashEntity bf = SpeedForceEntityTypes.BLACK_FLASH.create(level);
		double ahead = player.getX() + SpeedForceConfig.BLACK_FLASH_CHARGE_DISTANCE;
		double x = ahead <= ROAD_END_X - 2 ? ahead : player.getX() - SpeedForceConfig.BLACK_FLASH_CHARGE_DISTANCE;
		Vec3 from = new Vec3(x, ROAD_Y + 1, player.getZ());
		live.bfStart = tripTicks;
		live.bfFrom = from;
		if (bf != null) {
			bf.setCinematic(true);
			bf.moveTo(from.x, from.y, from.z, x > player.getX() ? 90f : -90f, 0f);
			bf.setYHeadRot(bf.getYRot());
			level.addFreshEntity(bf);
			live.bfId = bf.getId();
		}
		level.sendParticles(ParticleTypes.FLASH, from.x, from.y + 1, from.z, 2, 0.2, 0.2, 0.2, 0);
		level.sendParticles(BlackFlashEntity.BLACK, from.x, from.y + 1, from.z, 60, 0.6, 1.2, 0.6, 0.0);
		player.connection.send(new ClientboundSetTitlesAnimationPacket(4, 40, 10));
		player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("message.projecthero.black_flash.warning_sub")
				.withStyle(ChatFormatting.RED, ChatFormatting.ITALIC)));
		player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("message.projecthero.black_flash.warning")
				.withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.9f, 0.6f);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 1.4f, 1.4f);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 1.2f, 0.5f);
	}

	/** He stands 15 ticks, then runs the road at you; on reaching you, the punch. */
	private static void tickBlackFlash(ServerLevel level, ServerPlayer player, Live live, SpeedForceTrip trip) {
		int age = trip.ticks() - live.bfStart;
		Entity e = level.getEntity(live.bfId);
		if (age % 10 == 0) {
			player.displayClientMessage(Component.translatable("message.projecthero.black_flash.coming").withStyle(ChatFormatting.DARK_RED), true);
		}
		int stand = 15;
		Vec3 target = player.position();
		if (e instanceof BlackFlashEntity bf) {
			if (age <= stand) {
				if (age % 3 == 0) {
					BlackFlash.fx(bf, SpeedForceFxPayload.BF_BOLT, bf.position().add(0, 1, 0), bf.position().add(level.random.nextGaussian() * 2, 1 + level.random.nextDouble() * 2, level.random.nextGaussian() * 2));
				}
				bf.setAction(BlackFlashEntity.ACTION_CROUCH);
			} else {
				bf.setAction(BlackFlashEntity.ACTION_CHARGE);
				float k = Math.min(1f, (age - stand) / (float) (SpeedForceConfig.BLACK_FLASH_CHARGE_TICKS - stand));
				Vec3 at = live.bfFrom.lerp(target, k * k);
				Vec3 d = target.subtract(at);
				float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0);
				bf.moveTo(at.x, at.y, at.z, yaw, 0f);
				bf.setYHeadRot(yaw);
				bf.yBodyRot = yaw;
				level.sendParticles(BlackFlashEntity.RED, at.x, at.y + 1, at.z, 4, 0.3, 0.6, 0.3, 0.0);
				if (age % 4 == 0) {
					level.playSound(null, at.x, at.y, at.z, SoundEvents.ELYTRA_FLYING, SoundSource.HOSTILE, 0.5f, 0.6f);
				}
			}
			if (age > stand && (bf.position().distanceTo(target) < 1.8 || age >= SpeedForceConfig.BLACK_FLASH_CHARGE_TICKS)) {
				bf.setAction(BlackFlashEntity.ACTION_PUNCH);
				punchOut(player);
			}
		} else if (age >= SpeedForceConfig.BLACK_FLASH_CHARGE_TICKS) {
			punchOut(player);
		}
	}

	/**
	 * v0.17.1 (user): the Black Flash punches the runner OUT of the Speed Force -- a cinematic hit (a flash, a shockwave of
	 * black lightning, thunder) that throws them back to where they went in, in the real world, and he follows them out:
	 * the fight is on ({@link BlackFlash#spawnFor}). No drain yet: beat him and there is none. True if they were inside.
	 */
	public static boolean punchOut(ServerPlayer player) {
		SpeedForceTrip trip = player.getAttachedOrElse(SpeedForceAttachments.TRIP, null);
		MinecraftServer server = player.getServer();
		if (trip == null || server == null) {
			return false;
		}
		if (inside(player)) {
			Vec3 c = player.position().add(0, 1, 0);
			ServerLevel realm = player.serverLevel();
			realm.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 3, 0.2, 0.2, 0.2, 0);
			realm.sendParticles(ParticleTypes.SONIC_BOOM, c.x, c.y, c.z, 1, 0, 0, 0, 0);
			realm.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.4f, 0.6f);
		}
		ServerLevel home = homeLevel(server, trip);
		Vec3 from = new Vec3(trip.x(), trip.y(), trip.z());
		boolean sameDim = home.dimension().location().toString().equals(trip.dimension());
		Vec3 target = sameDim ? from : spawn(home);
		Vec3 land = leave(player, home, target, trip.yaw(), 0, sameDim);
		// thrown: the hit's knockback, back the way they faced
		double yr = Math.toRadians(trip.yaw());
		Vec3 back = new Vec3(Math.sin(yr), 0, -Math.cos(yr));
		player.setDeltaMovement(back.x * 1.4, 0.55, back.z * 1.4);
		player.hurtMarked = true;
		// he follows: a few blocks in front of where they landed (the way they face)
		Vec3 ahead = landing(home, player, land.add(back.scale(-5.0)), land);
		BlackFlashEntity bf = BlackFlash.spawnFor(player, home, ahead);
		Vec3 c = land.add(0, 1.0, 0);
		home.sendParticles(ParticleTypes.SONIC_BOOM, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		home.sendParticles(BlackFlashEntity.BLACK, c.x, c.y, c.z, 50, 1.0, 1.0, 1.0, 0.0);
		home.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1.4f, 0.8f);
		home.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.HOSTILE, 1.4f, 0.6f);
		if (bf != null) {
			BlackFlash.fx(bf, SpeedForceFxPayload.BF_PUNCH, c, ahead.add(0, 1, 0));
			player.hurt(bf.damageSources().mobAttack(bf), 4.0f);
		}
		player.displayClientMessage(Component.translatable("message.projecthero.black_flash.punched_out").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), false);
		return true;
	}

	// ---------------------------------------------------------------- out

	/** Out through doorway {@code door}: at that place, on a safe spot, drained as the trip's kind says. */
	public static boolean takeDoor(ServerPlayer player, Door door) {
		SpeedForceTrip trip = player.getAttachedOrElse(SpeedForceAttachments.TRIP, null);
		MinecraftServer server = player.getServer();
		if (trip == null || server == null) {
			return false;
		}
		ServerLevel to = SpeedForceMemory.levelOf(server, door.place().dimension());
		if (to == null || to.dimension() == DIMENSION) {
			return false;
		}
		int drain = SpeedForceMemory.drainTicksFor(door.kind(), door.index(), trip.dimension(), door.place().dimension());
		Vec3 land = leave(player, to, new Vec3(door.place().x(), door.place().y(), door.place().z()), door.place().yaw(), drain, true);
		player.displayClientMessage(Component.translatable("message.projecthero.speed_force_speedster.realm_door", door.label(), drain / 20)
				.withStyle(ChatFormatting.GOLD), false);
		return land != null;
	}

	/**
	 * Out by holding Sneak (or a logout inside): back where they went in, in the dimension they came from, through a bolt of
	 * their lightning -- drained the full 2 minutes. True if they were inside (with a trip) and are out now.
	 */
	public static boolean exit(ServerPlayer player) {
		SpeedForceTrip trip = player.getAttachedOrElse(SpeedForceAttachments.TRIP, null);
		MinecraftServer server = player.getServer();
		if (trip == null || server == null) {
			return false;
		}
		ServerLevel home = homeLevel(server, trip);
		Vec3 from = new Vec3(trip.x(), trip.y(), trip.z());
		boolean sameDim = home.dimension().location().toString().equals(trip.dimension());
		leave(player, home, sameDim ? from : spawn(home), trip.yaw(), SpeedForceConfig.DRAINED_TICKS, sameDim);
		player.displayClientMessage(Component.translatable("message.projecthero.speed_force_speedster.realm_exit").withStyle(ChatFormatting.GOLD), false);
		return true;
	}

	/**
	 * The way out every exit shares: the trip over (the road's entities gone), out at a safe spot at / near {@code target}
	 * ({@code exact}: the remembered spot itself first), the drain ({@code drainTicks}; 0 = none), the portal on its cooldown,
	 * the landing remembered as an exit point. Returns where they landed.
	 */
	private static Vec3 leave(ServerPlayer player, ServerLevel to, Vec3 target, float yaw, int drainTicks, boolean exact) {
		Vec3 land = exact ? landingNear(to, player, target) : landing(to, player, target, target);
		player.removeAttached(SpeedForceAttachments.TRIP);
		LANES.remove(player.getUUID());
		SNEAK.remove(player.getUUID());
		clearLive(player);
		SuperSpeedTimeSlow.end(player, false);
		SpeedForceAbilities.endFlashTime(player);
		player.teleportTo(to, land.x, land.y, land.z, yaw, 0.0f);
		player.resetFallDistance();
		player.setDeltaMovement(Vec3.ZERO);
		SpeedForceSpeedster.graceMotion(player, 4);
		if (drainTicks > 0) {
			SpeedForceSpeedster.drain(player, drainTicks);
		}
		SpeedForceAbilities.cooldown(player, SpeedForceAbilities.PORTAL, SpeedForceConfig.PORTAL_COOLDOWN);
		SpeedForceMemory.addExit(player, to, land, yaw);
		to.sendParticles(ParticleTypes.FLASH, land.x, land.y + 1.0, land.z, 2, 0.2, 0.2, 0.2, 0);
		to.sendParticles(ParticleTypes.ELECTRIC_SPARK, land.x, land.y + 1.0, land.z, 80, 0.8, 1.2, 0.8, 0.6);
		to.sendParticles(SpeedForceAbilities.dust(player, 2.2f), land.x, land.y + 1.0, land.z, 40, 0.6, 1.0, 0.6, 0.0);
		to.playSound(null, land.x, land.y, land.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.2f, 1.3f);
		to.playSound(null, land.x, land.y, land.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.0f, 1.0f);
		SpeedForceSpeedster.fx(player, SpeedForceFxPayload.REALM_EXIT, land.add(0, 1.0, 0), land.add(0, 28.0, 0));
		player.displayClientMessage(Component.empty(), true); // the status line goes with the Speed Force
		if (drainTicks > 0) {
			player.displayClientMessage(Component.translatable("message.projecthero.speed_force_speedster.drained", drainTicks / 20)
					.withStyle(ChatFormatting.RED), false);
		}
		return land;
	}

	private static ServerLevel homeLevel(MinecraftServer server, SpeedForceTrip trip) {
		ServerLevel level = SpeedForceMemory.levelOf(server, trip.dimension());
		return level == null || level.dimension() == DIMENSION ? server.overworld() : level;
	}

	private static Vec3 spawn(ServerLevel level) {
		BlockPos sp = level.getSharedSpawnPos();
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sp.getX(), sp.getZ());
		return new Vec3(sp.getX() + 0.5, Math.max(y, level.getMinBuildHeight() + 1), sp.getZ() + 0.5);
	}

	/**
	 * A remembered spot to come out at: the spot itself when it is still safe to stand on, else the nearest safe spot within
	 * 3 blocks at about that height (a bed, a room, a cave), else the open-sky search {@link #landing}.
	 */
	static Vec3 landingNear(ServerLevel level, ServerPlayer player, Vec3 target) {
		int bx = (int) Math.floor(target.x);
		int by = (int) Math.floor(target.y + 0.01);
		int bz = (int) Math.floor(target.z);
		level.getChunk(bx >> 4, bz >> 4);
		for (int r = 0; r <= 3; r++) {
			for (int dy : new int[] { 0, 1, -1, 2, -2 }) {
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
							continue;
						}
						int y = by + dy;
						if (y > level.getMinBuildHeight() && y < level.getMaxBuildHeight() - 2 && standable(level, player, bx + dx, y, bz + dz)) {
							return new Vec3(bx + dx + 0.5, y, bz + dz + 0.5);
						}
					}
				}
			}
		}
		return landing(level, player, target, target);
	}

	/**
	 * A safe place to come out at (or near) {@code target}: inside the world border, the top of the ground there (a level with
	 * a ceiling -- the Nether -- is searched for a floor near the height asked for), out of lava; a spiral out to 24 blocks if
	 * the spot itself won't do, and {@code fallback} if nothing near will.
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
					Vec3 at = standAt(level, player, bx + dx, bz + dz, target.y);
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

	/** Logged in: still inside from before (a logout or a crash inside) -- out, back where they went in. */
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

	/** A place as a world position (tests). */
	public static Vec3 pos(Place p) {
		return new Vec3(p.x(), p.y(), p.z());
	}
}
