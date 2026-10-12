package com.projecthero.mod.ultron.contagion;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.ironman.JarvisDialogue;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ultron.UltronFx;
import com.projecthero.mod.ultron.UltronRewards;
import com.projecthero.mod.ultron.UltronWorldState;
import com.projecthero.mod.ultron.block.UltronBlocks;
import com.projecthero.mod.ultron.contagion.block.ContagionBlocks;
import com.projecthero.mod.ultron.contagion.block.FirewallBeaconBlockEntity;
import com.projecthero.mod.ultron.contagion.entity.ContagionEntityTypes;
import com.projecthero.mod.ultron.contagion.entity.UltronNexusEntity;
import com.projecthero.mod.ultron.contagion.item.ContagionItems;
import com.projecthero.mod.ultron.item.UltronItems;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: <b>The Ultron Contagion</b> -- "you can't kill an idea". One entry point ({@link #initialize}, from
 * {@code Ultron.initialize}) and the world-wide driver ({@link #tick}, once a server tick, overworld only).
 *
 * <h2>The life of a Contagion</h2>
 * <ol>
 *   <li>{@code IDLE} -> {@code SCHEDULED}: once Ultron has been beaten in this world ({@link UltronWorldState}), it begins
 *       a random {@link ContagionConfig#delayDaysMin}..{@link ContagionConfig#delayDaysMax} in-game days later.</li>
 *   <li>{@code PATIENT_ZERO}: a placed Ultron Core, else a Stark machine, else a spot 80-140 blocks from a player who fought
 *       him, flickers red for 20 s ({@link #choosePatientZero}); JARVIS glitches, everyone gets the title.</li>
 *   <li>{@code ACTIVE}: Nexus spires grow ({@link UltronNexusEntity}), the <b>Saturation</b> climbs (+1% per standing Nexus
 *       per 2 min; thresholds at 25 / 50 / 75 / 100%), Firewall Beacons push it back, and each fallen Nexus hands JARVIS a
 *       <b>Trace</b>. Three Traces -- or 100% Saturation -- start the Sokovia Protocol.</li>
 *   <li>{@code FINALE}: {@link SokoviaFinale}. Win: everything reverts and he goes {@code DORMANT}. Loss / escape: back to
 *       {@code ACTIVE} at 50%.</li>
 *   <li>{@code DORMANT}: Resurgence -- a {@link ContagionConfig.Resurgence#chance} roll every
 *       {@link ContagionConfig.Resurgence#intervalDays} days (never while a Firewall Beacon is fuelled) brings back
 *       "Ultron v2", v3... up to v5.</li>
 * </ol>
 * Never on Peaceful. Never force-loads a chunk: a Nexus in an unloaded chunk neither spreads nor counts toward the
 * Saturation, and every spawn checks the spot is loaded.
 */
public final class Contagion {
	/** Every robot / spire the Contagion makes carries this tag (stop and orphan cleanup). */
	public static final String UNIT_TAG = "projecthero_contagion";
	public static final int PATIENT_ZERO_TICKS = 400;

	private Contagion() {
	}

	public static void initialize() {
		ContagionEntityTypes.initialize();
		ContagionBlocks.initialize();
		ContagionItems.initialize();
		com.projecthero.mod.ultron.contagion.item.FirewallChip.initialize();
		com.projecthero.mod.ultron.contagion.item.VibraniumPlatingII.initialize();
		ContagionNet.initialize();
		Assimilation.initialize();
		SuitHijack.initialize();
		MindStoneBeam.initialize();
		ContagionRewards.initialize();
		ServerTickEvents.END_SERVER_TICK.register(Contagion::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			// (Ultron's defeats are recorded by the Uprising itself: UltronWorldState.recordDefeat)
			if (entity instanceof ServerPlayer p) {
				SokoviaFinale.onPlayerDied(p);
			}
		});
		ServerEntityEvents.ENTITY_LOAD.register(Contagion::onEntityLoad);
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> ContagionCommand.register(dispatcher));
	}

	// ---------------------------------------------------------------- the driver

	public static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		if (level == null) {
			return;
		}
		ContagionState st = ContagionState.get(level);
		long now = level.getGameTime();
		// cleanup always runs, even when the Contagion is off or on Peaceful
		NaniteCorruption.tickRevert(level, st, ContagionConfig.nexus().revertPerTick);
		if (st.craterRevertAt >= 0 && now >= st.craterRevertAt) {
			SkyCitadel.revertCrater(level, st);
		}
		// the finale runs (or finishes taking its citadel away) whatever the stage; only its teardown runs when switched off
		if (st.finale != null && (st.finale.phase == SokoviaFinale.Phase.ENDING || ContagionConfig.get().enabled
				&& level.getDifficulty() != Difficulty.PEACEFUL)) {
			SokoviaFinale.tick(level, st);
		}
		if (!ContagionConfig.get().enabled || level.getDifficulty() == Difficulty.PEACEFUL) {
			if (now % 20 == 0) {
				ContagionNet.syncAll(level, st);
			}
			return;
		}
		switch (st.stage) {
			case IDLE -> tickIdle(level, st, now);
			case SCHEDULED -> {
				if (now >= st.startAt && now % 20 == 0) {
					beginPatientZero(level, st);
				}
			}
			case PATIENT_ZERO -> tickPatientZero(level, st, now);
			case ACTIVE -> tickActive(level, st, now);
			case FINALE -> tickInfection(level, st, now);
			case DORMANT -> tickDormant(level, st, now);
		}
		if (now % 20 == 0 && st.stage != ContagionState.Stage.IDLE) {
			st.setDirty();
		}
		if (now % 10 == 0) {
			ContagionNet.syncAll(level, st);
		}
	}

	private static void tickIdle(ServerLevel level, ContagionState st, long now) {
		if (now % 100 != 0) {
			return;
		}
		long first = UltronWorldState.firstDefeatTime(level);
		if (first < 0 || first == st.ignoredDefeatTime) {
			return;
		}
		schedule(level, st, first);
	}

	/** Picks the start time {@code delayDaysMin..delayDaysMax} days after {@code from}. */
	public static void schedule(ServerLevel level, ContagionState st, long from) {
		ContagionConfig cfg = ContagionConfig.get();
		int min = Math.max(0, Math.min(cfg.delayDaysMin, cfg.delayDaysMax));
		int max = Math.max(min, cfg.delayDaysMax);
		long days = min + (max > min ? level.random.nextInt(max - min + 1) : 0);
		st.startAt = from + days * 24000L + level.random.nextInt(24000);
		st.stage = ContagionState.Stage.SCHEDULED;
		st.setDirty();
		ProjectHeroMod.LOGGER.info("[ProjectHero] The Ultron Contagion is scheduled for game time {}", st.startAt);
	}

	private static void tickInfection(ServerLevel level, ContagionState st, long now) {
		if (now % ContagionConfig.assimilation().sweepTicks == 0) {
			Assimilation.sweep(level, st);
		}
		if (now % 20 == 5) {
			Assimilation.tickMachines(level, st);
		}
		SuitHijack.tick(level, st);
	}

	private static void tickActive(ServerLevel level, ContagionState st, long now) {
		tickInfection(level, st, now);
		ContagionConfig.Saturation sat = ContagionConfig.saturation();
		// the rise: +1% per standing, grown, ticking Nexus every 2 minutes
		if (st.lastRise < 0 || now < st.lastRise) {
			st.lastRise = now;
		}
		if (now - st.lastRise >= sat.riseIntervalTicks) {
			st.lastRise = now;
			int ticking = 0;
			for (ContagionState.NexusRecord r : st.nexus.values()) {
				if (level.isPositionEntityTicking(r.pos) && level.getEntity(r.id) instanceof UltronNexusEntity n && n.grown()) {
					ticking++;
				}
			}
			addSaturation(level, st, ticking * sat.risePerNexus);
		}
		// Firewall Beacons push it back
		if (st.lastBeaconDrop < 0 || now < st.lastBeaconDrop) {
			st.lastBeaconDrop = now;
		}
		if (now - st.lastBeaconDrop >= sat.beaconIntervalTicks) {
			st.lastBeaconDrop = now;
			if (st.saturation < sat.beaconCeiling) {
				addSaturation(level, st, -sat.beaconDrop * st.fuelledBeacons(now));
			}
		}
		// new Nexus spires
		if (st.nextNexusAt < 0) {
			st.nextNexusAt = now + nexusInterval(st);
		}
		if (now >= st.nextNexusAt) {
			st.nextNexusAt = now + nexusInterval(st);
			if (st.nexus.size() < ContagionConfig.maxNexusFor(st.version)) {
				trySpawnNexusNearPlayer(level, st);
			}
		}
		// Hunter-Killer squads from 75%
		if (st.saturation >= sat.huntersAt) {
			if (st.nextHuntersAt < 0) {
				st.nextHuntersAt = now + ContagionConfig.hunters().intervalTicks;
			}
			if (now >= st.nextHuntersAt) {
				st.nextHuntersAt = now + ContagionConfig.hunters().intervalTicks;
				List<ServerPlayer> ps = eligiblePlayers(level);
				if (!ps.isEmpty()) {
					ServerPlayer p = ps.get(level.random.nextInt(ps.size()));
					if (ContagionUnits.hunterSquad(level, p, st.version) > 0) {
						p.sendSystemMessage(Component.translatable("message.projecthero.contagion.hunters").withStyle(ChatFormatting.RED));
					}
				}
			}
		} else {
			st.nextHuntersAt = -1;
		}
		// the end of the hunt
		if (st.traces >= 3 || st.saturation >= 100.0) {
			SokoviaFinale.begin(level, st, st.traces >= 3);
		}
	}

	private static long nexusInterval(ContagionState st) {
		ContagionConfig.Nexus n = ContagionConfig.nexus();
		return st.saturation >= ContagionConfig.saturation().fastSpawnAt ? n.fastSpawnIntervalTicks : n.spawnIntervalTicks;
	}

	/** Adds {@code delta} percent (clamped to 0..100). */
	public static void addSaturation(ServerLevel level, ContagionState st, double delta) {
		double before = st.saturation;
		st.saturation = Math.max(0.0, Math.min(100.0, st.saturation + delta));
		if (st.saturation != before) {
			st.setDirty();
			ContagionConfig.Saturation sat = ContagionConfig.saturation();
			for (double threshold : new double[] { sat.fastSpawnAt, sat.golemsAt, sat.huntersAt }) {
				if (before < threshold && st.saturation >= threshold) {
					broadcast(level, Component.translatable("message.projecthero.contagion.saturation_" + (int) threshold)
							.withStyle(ChatFormatting.DARK_RED));
				}
			}
		}
	}

	private static void tickDormant(ServerLevel level, ContagionState st, long now) {
		ContagionConfig.Resurgence cfg = ContagionConfig.resurgence();
		if (!cfg.enabled) {
			return;
		}
		if (st.nextResurgenceRoll < 0) {
			st.nextResurgenceRoll = now + cfg.intervalDays * 24000L;
			st.setDirty();
			return;
		}
		if (now < st.nextResurgenceRoll) {
			return;
		}
		st.nextResurgenceRoll = now + cfg.intervalDays * 24000L;
		if (resurgenceBlocked(level, st, now)) {
			return;
		}
		if (level.random.nextDouble() < cfg.chance) {
			resurge(level, st);
		}
	}

	/** Resurgence never happens while any Firewall Beacon in the world is fuelled. */
	public static boolean resurgenceBlocked(ServerLevel level, ContagionState st, long now) {
		return st.fuelledBeacons(now) > 0;
	}

	/** Ultron comes back as the next version (capped) and the Contagion starts over in 30 s. */
	public static void resurge(ServerLevel level, ContagionState st) {
		st.version = Math.min(ContagionConfig.resurgence().maxVersion, st.version + 1);
		st.saturation = 0;
		st.traces = 0;
		st.stage = ContagionState.Stage.SCHEDULED;
		st.startAt = level.getGameTime() + 600;
		st.nextNexusAt = -1;
		st.setDirty();
		sayAll(level, "resurgence", Component.literal(String.valueOf(st.version)));
	}

	// ---------------------------------------------------------------- patient zero

	/** Patient zero: an Ultron Core, else a Stark machine, else a spot near a player who fought him. */
	public static BlockPos choosePatientZero(ServerLevel level) {
		List<ServerPlayer> players = eligiblePlayers(level);
		if (players.isEmpty()) {
			return null;
		}
		BlockPos core = null;
		BlockPos machine = null;
		for (ServerPlayer p : players) {
			for (BlockEntity be : blockEntitiesNear(level, p.blockPosition(), 6)) {
				if (be.getType() == UltronBlocks.ULTRON_CORE_BE && core == null) {
					core = be.getBlockPos();
				} else if (machine == null && isStarkMachine(be)) {
					machine = be.getBlockPos();
				}
			}
		}
		if (core != null) {
			return core;
		}
		if (machine != null) {
			return machine;
		}
		List<ServerPlayer> fought = new ArrayList<>();
		for (ServerPlayer p : players) {
			if (UltronRewards.clears(p) > 0) {
				fought.add(p);
			}
		}
		List<ServerPlayer> pool = fought.isEmpty() ? players : fought;
		ServerPlayer p = pool.get(level.random.nextInt(pool.size()));
		for (int i = 0; i < 12; i++) {
			BlockPos spot = groundSpot(level, p.position(), 80, 140);
			if (spot != null) {
				return spot;
			}
		}
		return null;
	}

	/** Is {@code be} a Stark machine (Fabricator, suit platform, Gantry floor, Sorting Station, Stark Furnace / Smelter / Smoker)? */
	public static boolean isStarkMachine(BlockEntity be) {
		BlockEntityType<?> t = be.getType();
		return t == com.projecthero.mod.ironman.IronManBlocks.STARK_FABRICATOR_BE || t == com.projecthero.mod.ironman.IronManBlocks.SUIT_PLATFORM_BE
				|| t == com.projecthero.mod.ironman.IronManBlocks.GANTRY_FLOOR_BE || t == com.projecthero.mod.ironman.sorter.StarkSorter.STATION_BE
				|| t == com.projecthero.mod.ironman.furnace.StarkFurnaces.BLOCK_ENTITY;
	}

	/** Every block entity in the loaded chunks within {@code chunks} chunks of {@code pos}. */
	public static List<BlockEntity> blockEntitiesNear(ServerLevel level, BlockPos pos, int chunks) {
		List<BlockEntity> out = new ArrayList<>();
		int cx = SectionPos.blockToSectionCoord(pos.getX());
		int cz = SectionPos.blockToSectionCoord(pos.getZ());
		for (int x = cx - chunks; x <= cx + chunks; x++) {
			for (int z = cz - chunks; z <= cz + chunks; z++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
				if (chunk != null) {
					out.addAll(chunk.getBlockEntities().values());
				}
			}
		}
		return out;
	}

	private static void beginPatientZero(ServerLevel level, ContagionState st) {
		BlockPos pz = choosePatientZero(level);
		if (pz == null) {
			return; // nobody online (or nowhere loaded): try again in a second
		}
		st.patientZero = pz;
		st.patientZeroAt = level.getGameTime();
		st.stage = ContagionState.Stage.PATIENT_ZERO;
		st.setDirty();
		ProjectHeroMod.LOGGER.info("[ProjectHero] Ultron Contagion: patient zero at {}", pz);
	}

	private static void tickPatientZero(ServerLevel level, ContagionState st, long now) {
		BlockPos pz = st.patientZero;
		if (pz == null) {
			st.stage = ContagionState.Stage.SCHEDULED;
			return;
		}
		long t = now - st.patientZeroAt;
		if (level.isLoaded(pz) && t % 4 == 0 && (t / 4) % 3 != 2) {
			UltronFx.forced(level, UltronFx.RED_BIG, pz.getX() + 0.5, pz.getY() + 0.6, pz.getZ() + 0.5, 6, 0.4, 0.4, 0.4, 0.0);
			if (t % 40 == 0) {
				level.playSound(null, pz, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0f, 0.5f + level.random.nextFloat() * 0.5f);
			}
		}
		if (t < PATIENT_ZERO_TICKS) {
			return;
		}
		BlockPos at = level.isLoaded(pz) ? nexusSpotNear(level, pz) : null;
		if (at == null) {
			at = pz;
		}
		if (!level.isLoaded(at)) {
			return; // wait for it to load: never force it
		}
		announceStart(level, st);
		spawnNexus(level, st, at);
		compromiseAround(level, st, pz, 6);
		st.stage = ContagionState.Stage.ACTIVE;
		st.patientZero = null;
		st.nextNexusAt = level.getGameTime() + nexusInterval(st);
		st.lastRise = level.getGameTime();
		st.lastBeaconDrop = level.getGameTime();
		st.setDirty();
	}

	private static void announceStart(ServerLevel level, ContagionState st) {
		for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
			if (TonyStark.hasPower(p)) {
				JarvisDialogue.speak(p, "contagion_start");
			} else {
				title(p, Component.translatable("title.projecthero.contagion").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
						Component.translatable(st.version > 1 ? "title.projecthero.contagion.sub_version" : "title.projecthero.contagion.sub",
								st.version).withStyle(ChatFormatting.GRAY));
			}
			p.playNotifySound(SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.6f, 0.4f);
		}
		sayAll(level, st.version > 1 ? "return_version" : "return", Component.literal(String.valueOf(st.version)));
	}

	// ---------------------------------------------------------------- Nexus spires

	/** A dry, natural ground spot near {@code near} (searching a few blocks round it), or null. */
	public static BlockPos nexusSpotNear(ServerLevel level, BlockPos near) {
		for (int r = 2; r <= 6; r++) {
			for (int i = 0; i < 8; i++) {
				double a = i * Math.PI / 4 + r;
				BlockPos col = near.offset((int) Math.round(Math.cos(a) * r), 0, (int) Math.round(Math.sin(a) * r));
				BlockPos g = groundAt(level, col);
				if (g != null) {
					return g;
				}
			}
		}
		return null;
	}

	/** The air block on top of the ground at {@code column} if it is solid, dry, loaded and natural-looking; else null. */
	public static BlockPos groundAt(ServerLevel level, BlockPos column) {
		if (!level.isLoaded(column)) {
			return null;
		}
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
		BlockPos top = new BlockPos(column.getX(), y, column.getZ());
		BlockState below = level.getBlockState(top.below());
		if (!below.isSolid() || !below.getFluidState().isEmpty() || !level.getBlockState(top).getFluidState().isEmpty()) {
			return null;
		}
		return top;
	}

	/** A ground spot {@code min..max} blocks from {@code from} (horizontally), or null. */
	public static BlockPos groundSpot(ServerLevel level, Vec3 from, double min, double max) {
		double a = level.random.nextDouble() * Math.PI * 2;
		double r = min + level.random.nextDouble() * Math.max(0, max - min);
		return groundAt(level, BlockPos.containing(from.x + Math.cos(a) * r, from.y, from.z + Math.sin(a) * r));
	}

	/** Online, alive, non-spectator overworld players. */
	public static List<ServerPlayer> eligiblePlayers(ServerLevel level) {
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer p : level.players()) {
			if (p.isAlive() && !p.isSpectator()) {
				out.add(p);
			}
		}
		return out;
	}

	/** Tries to plant a Nexus 64-160 blocks from a random online player (loaded, unprotected ground only). */
	public static UltronNexusEntity trySpawnNexusNearPlayer(ServerLevel level, ContagionState st) {
		List<ServerPlayer> ps = eligiblePlayers(level);
		if (ps.isEmpty()) {
			return null;
		}
		ContagionConfig.Nexus cfg = ContagionConfig.nexus();
		long now = level.getGameTime();
		for (int attempt = 0; attempt < 10; attempt++) {
			ServerPlayer p = ps.get(level.random.nextInt(ps.size()));
			BlockPos spot = groundSpot(level, p.position(), cfg.minDistance, cfg.maxDistance);
			if (spot == null || !level.isPositionEntityTicking(spot)) {
				continue;
			}
			if (st.protectedByBeacon(spot, now)) {
				probeBeacons(level, spot);
				continue;
			}
			if (inSpawnProtection(level, spot) || tooCloseToNexus(st, spot, 48)) {
				continue;
			}
			return spawnNexus(level, st, spot);
		}
		return null;
	}

	private static boolean inSpawnProtection(ServerLevel level, BlockPos pos) {
		int r = level.getServer().getSpawnProtectionRadius();
		if (r <= 0) {
			return false;
		}
		BlockPos spawn = level.getSharedSpawnPos();
		return Math.max(Math.abs(pos.getX() - spawn.getX()), Math.abs(pos.getZ() - spawn.getZ())) <= r + ContagionConfig.nexus().maxRadius;
	}

	private static boolean tooCloseToNexus(ContagionState st, BlockPos pos, double d) {
		for (ContagionState.NexusRecord r : st.nexus.values()) {
			if (r.pos.distSqr(pos) < d * d) {
				return true;
			}
		}
		return false;
	}

	/** Plants a Nexus at {@code ground} now (the spot must be loaded). */
	public static UltronNexusEntity spawnNexus(ServerLevel level, ContagionState st, BlockPos ground) {
		UltronNexusEntity n = ContagionEntityTypes.ULTRON_NEXUS.create(level);
		if (n == null) {
			return null;
		}
		n.moveTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
		n.configure(st.version);
		st.addNexus(n.getUUID(), ground, level.getGameTime());
		n.addTag(Contagion.UNIT_TAG);
		level.addFreshEntity(n);
		level.playSound(null, ground, SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 3.0f, 0.6f);
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(n) < 200 * 200) {
				p.displayClientMessage(Component.translatable("message.projecthero.contagion.nexus_rising").withStyle(ChatFormatting.RED), true);
			}
		}
		return n;
	}

	/** Every Stark machine within {@code r} of {@code pos} becomes Compromised by the Nexus nearest to it. */
	public static void compromiseAround(ServerLevel level, ContagionState st, BlockPos pos, int r) {
		for (BlockEntity be : blockEntitiesNear(level, pos, 1)) {
			if (isStarkMachine(be) && be.getBlockPos().distSqr(pos) <= r * r) {
				Assimilation.compromise(level, st, be.getBlockPos());
			}
		}
	}

	/** The Nexus whose corruption covers {@code pos} (horizontal radius, 24 up / down), or null. */
	public static ContagionState.NexusRecord corruptionAt(ServerLevel level, BlockPos pos) {
		if (level.dimension() != Level.OVERWORLD) {
			return null;
		}
		ContagionState st = ContagionState.get(level);
		for (ContagionState.NexusRecord r : st.nexus.values()) {
			if (r.radius <= 0) {
				continue;
			}
			double dx = pos.getX() - r.pos.getX(), dz = pos.getZ() - r.pos.getZ();
			if (dx * dx + dz * dz <= (double) r.radius * r.radius && Math.abs(pos.getY() - r.pos.getY()) <= 24) {
				return r;
			}
		}
		return null;
	}

	/** A fallen Nexus: the corruption reverts, what it held is freed, -5% Saturation, and JARVIS gets a Trace. */
	public static void onNexusDestroyed(ServerLevel level, UltronNexusEntity nexus) {
		ContagionState st = ContagionState.get(level);
		ContagionState.NexusRecord rec = st.nexus.get(nexus.getUUID());
		int radius = rec != null ? Math.max(rec.radius, 4) : ContagionConfig.nexus().maxRadius;
		Assimilation.releaseAround(level, st, nexus.position(), radius);
		NaniteCorruption.queueRevertWave(st, nexus.getUUID());
		addSaturation(level, st, -ContagionConfig.saturation().purgeDrop);
		blueWave(level, nexus.position(), radius);
		boolean traceCounts = st.stage == ContagionState.Stage.ACTIVE;
		if (traceCounts) {
			st.traces = Math.min(3, st.traces + 1);
		}
		st.setDirty();
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(nexus) < 160 * 160) {
				if (traceCounts) {
					JarvisDialogue.speak(p, st.traces >= 3 ? "contagion_trace_3" : "contagion_trace");
					p.displayClientMessage(Component.translatable("message.projecthero.contagion.trace", st.traces).withStyle(ChatFormatting.AQUA), false);
				} else {
					JarvisDialogue.speak(p, "contagion_nexus_down");
				}
			}
		}
		sayNear(level, nexus.position(), "nexus_down", 96);
	}

	/** The cyan wave rolling out from a fallen Nexus. */
	public static void blueWave(ServerLevel level, Vec3 c, double radius) {
		for (double r = 2; r <= radius; r += 3) {
			UltronFx.ring(level, UltronFx.CYAN, c.add(0, 0.6, 0), r, (int) Math.min(160, r * 5));
		}
		UltronFx.forced(level, ParticleTypes.FLASH, c.x, c.y + 4, c.z, 1, 0, 0, 0, 0);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 3.0f, 0.6f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.0f, 0.8f);
	}

	/** Every fuelled Firewall Beacon whose dome {@code pos} is inside flickers (the client draws the blue hexes). */
	public static void probeBeacons(ServerLevel level, BlockPos pos) {
		ContagionState st = ContagionState.get(level);
		double r = ContagionConfig.beacon().radius;
		long now = level.getGameTime();
		for (var e : st.beacons.entrySet()) {
			BlockPos b = e.getKey();
			if (e.getValue() > now && b.distSqr(pos) <= r * r && level.isLoaded(b)
					&& level.getBlockEntity(b) instanceof FirewallBeaconBlockEntity be && now >= be.probeUntil() - FirewallBeaconBlockEntity.PROBE_TICKS / 2) {
				float yaw = (float) Math.toDegrees(Math.atan2(pos.getZ() - b.getZ(), pos.getX() - b.getX()));
				be.probe(yaw);
			}
		}
	}

	// ---------------------------------------------------------------- start / stop (commands, tests)

	/** Starts the Contagion now (skipping the delay): patient zero next tick. */
	public static void startNow(ServerLevel level) {
		ContagionState st = ContagionState.get(level);
		if (st.stage == ContagionState.Stage.ACTIVE || st.stage == ContagionState.Stage.FINALE || st.stage == ContagionState.Stage.PATIENT_ZERO) {
			return;
		}
		st.stage = ContagionState.Stage.SCHEDULED;
		st.startAt = level.getGameTime();
		st.setDirty();
	}

	/**
	 * Stops everything: the finale is torn down (every citadel block removed), every Nexus and Contagion robot in a loaded
	 * chunk is removed (the rest remove themselves when they load), every recorded block is queued for revert, golems and
	 * machines are freed, and the stage goes back to {@code DORMANT} (beaten before) or {@code IDLE}.
	 */
	public static void stop(ServerLevel level) {
		ContagionState st = ContagionState.get(level);
		SokoviaFinale.abort(level, st);
		for (UUID id : new ArrayList<>(st.nexus.keySet())) {
			ContagionState.NexusRecord r = st.nexus.get(id);
			Assimilation.releaseAround(level, st, Vec3.atCenterOf(r.pos), Math.max(4, r.radius));
			NaniteCorruption.queueRevertWave(st, id);
		}
		removeUnits(level);
		Assimilation.releaseAllMachines(level, st);
		st.stage = st.omegaBeaten ? ContagionState.Stage.DORMANT : ContagionState.Stage.IDLE;
		if (st.stage == ContagionState.Stage.IDLE) {
			// do not restart on our own: a stop is a stop until a command (or Ultron's world state is reset)
			st.ignoredDefeatTime = UltronWorldState.firstDefeatTime(level);
		}
		st.saturation = 0;
		st.traces = 0;
		st.patientZero = null;
		st.nextNexusAt = -1;
		st.nextHuntersAt = -1;
		st.setDirty();
		ContagionNet.syncAll(level, st);
	}

	/** Removes every loaded Contagion robot / spire. */
	public static int removeUnits(ServerLevel level) {
		int n = 0;
		for (Entity e : level.getAllEntities()) {
			if (e.getTags().contains(UNIT_TAG)) {
				e.discard();
				n++;
			}
		}
		return n;
	}

	/** Orphan guard: a Contagion unit loading while there is no Contagion (stopped, or beaten) goes away. */
	private static void onEntityLoad(Entity entity, ServerLevel level) {
		Assimilation.onEntityLoad(entity, level);
		if (!entity.getTags().contains(UNIT_TAG) || level.dimension() != Level.OVERWORLD) {
			return;
		}
		ContagionState st = ContagionState.get(level);
		boolean running = st.stage == ContagionState.Stage.ACTIVE || st.stage == ContagionState.Stage.FINALE
				|| st.stage == ContagionState.Stage.PATIENT_ZERO;
		if (!running) {
			level.getServer().execute(entity::discard);
		}
	}

	// ---------------------------------------------------------------- talk

	/** Ultron's own voice in chat ({@code boss.projecthero.ultron.contagion.say.<key>}) to everyone in the overworld. */
	public static void sayAll(ServerLevel level, String key, Object... args) {
		Component line = line(key, args);
		for (ServerPlayer p : level.players()) {
			p.sendSystemMessage(line);
		}
	}

	/** Ultron's voice to everyone within {@code radius} of {@code at}. */
	public static void sayNear(ServerLevel level, Vec3 at, String key, double radius) {
		Component line = line(key);
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(at) < radius * radius) {
				p.sendSystemMessage(line);
			}
		}
	}

	private static Component line(String key, Object... args) {
		return Component.translatable("boss.projecthero.ultron.chat",
				Component.translatable("boss.projecthero.ultron.name").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
				Component.translatable("boss.projecthero.ultron.contagion.say." + key, args).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
	}

	public static void broadcast(ServerLevel level, Component message) {
		for (ServerPlayer p : level.players()) {
			p.sendSystemMessage(message);
		}
	}

	public static void title(ServerPlayer p, Component title, Component sub) {
		p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
		p.connection.send(new ClientboundSetSubtitleTextPacket(sub));
		p.connection.send(new ClientboundSetTitleTextPacket(title));
	}

	// ---------------------------------------------------------------- Mind Stone

	/** A Mind Stone (plain or empowered) in the off hand. */
	public static boolean holdsMindStone(Player p) {
		return UltronItems.MIND_STONE != null && p.getOffhandItem().is(UltronItems.MIND_STONE);
	}

	/** A Mind Stone in either hand. */
	public static boolean holdsMindStoneAny(Player p) {
		return UltronItems.MIND_STONE != null && (p.getOffhandItem().is(UltronItems.MIND_STONE) || p.getMainHandItem().is(UltronItems.MIND_STONE));
	}
}
