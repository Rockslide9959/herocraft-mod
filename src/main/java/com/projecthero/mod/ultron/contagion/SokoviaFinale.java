package com.projecthero.mod.ultron.contagion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.ironman.JarvisDialogue;
import com.projecthero.mod.ultron.UltronCombat;
import com.projecthero.mod.ultron.UltronEntityTypes;
import com.projecthero.mod.ultron.UltronFx;
import com.projecthero.mod.ultron.contagion.entity.CitadelMarkerEntity;
import com.projecthero.mod.ultron.contagion.entity.ContagionEntityTypes;
import com.projecthero.mod.ultron.contagion.entity.LiftEngineEntity;
import com.projecthero.mod.ultron.contagion.entity.UltronOmegaEntity;
import com.projecthero.mod.ultron.contagion.entity.UplinkAntennaEntity;
import com.projecthero.mod.ultron.contagion.entity.VibraniumCoreEntity;

import it.unimi.dsi.fastutil.longs.LongArrayList;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: <b>The Sokovia Protocol</b> -- the Contagion's finale. One instance at most, kept in {@link ContagionState}.
 *
 * <ol>
 *   <li>{@code MARKED}: 3 Traces (or 100% Saturation) and JARVIS finds him. A red beam marks a site 300-600 blocks from the
 *       players' average position (a red diamond on everyone's HUD). It waits up to one in-game day, or until a player is
 *       within 64 blocks. If the day runs out with nobody there, the Core charges in {@code AWAY} -- nothing is built in an
 *       unloaded chunk, ever.</li>
 *   <li>{@code BUILDING}: the sky-citadel ({@link SkyCitadel}) goes up {@link ContagionConfig.Finale#height} blocks above the
 *       ground a few hundred blocks a tick, the rising-rock path spirals up beside it, then the Vibranium Core, four guarded
 *       Lift Engines and Ultron Omega arrive.</li>
 *   <li>{@code FIGHT}: the Core charges 0 -> 100% over 12 minutes (Core Siphon +5%, each Lift Engine destroyed -15%). 100%
 *       = the citadel drops (loss). Every fighter down = loss. The upload finishing = he escapes (partial loss). Omega
 *       dying (the Vision moment) = victory.</li>
 *   <li>{@code ENDING}: everything placed is taken away (only our own blocks), fighters get slow falling; a loss leaves a
 *       cosmetic crater (reverted after 5 minutes). Win: rewards, every Nexus collapses, he goes dormant.</li>
 * </ol>
 * Fighters follow the Uprising's rules: anyone who comes within 64 blocks joins; a fighter who dies is down until they come
 * back. Falling off: fliers fly back; anyone else is caught by the safety net after 2 s (put back on the edge, 6 damage).
 */
public final class SokoviaFinale {
	public enum Phase { MARKED, AWAY, BUILDING, FIGHT, ENDING }

	public enum Outcome { NONE, WIN, LOSS, ESCAPE, ABORT }

	public Phase phase = Phase.MARKED;
	public Outcome outcome = Outcome.NONE;
	/** The site's column (x, z); y = the ground once it is known (-1 before). */
	public BlockPos site;
	public int groundY = Integer.MIN_VALUE;
	public long markedAt;
	public long phaseStart;
	public double charge;
	public int buildCursor;
	public int pathCursor;
	public boolean byTraces;
	public final LongArrayList placed = new LongArrayList();
	public int removeCursor;
	public UUID omega;
	public UUID core;
	public UUID marker;
	public final List<UUID> engines = new ArrayList<>();
	public final List<UUID> antennae = new ArrayList<>();
	public final Set<UUID> fighters = new HashSet<>();
	public final Set<UUID> down = new HashSet<>();
	public final Set<UUID> liftUsed = new HashSet<>();
	public long uploadStart = -1;
	public boolean omegaSpawned;

	// transient
	private List<SkyCitadel.Placement> discPlan;
	private List<SkyCitadel.Placement> pathPlan;
	private ServerBossEvent chargeBar;
	private ServerBossEvent uploadBar;
	private final Map<UUID, Integer> falling = new HashMap<>();
	private final Map<UUID, Integer> lifting = new HashMap<>();

	public SokoviaFinale() {
	}

	// ---------------------------------------------------------------- geometry

	/** The middle of the citadel floor (top face of the floor block), or null before the ground is known. */
	public Vec3 centre() {
		if (site == null || groundY == Integer.MIN_VALUE) {
			return null;
		}
		return new Vec3(site.getX() + 0.5, floorY() + 1.0, site.getZ() + 0.5);
	}

	public int floorY() {
		return groundY + ContagionConfig.finale().height;
	}

	public BlockPos floorCentre() {
		return new BlockPos(site.getX(), floorY(), site.getZ());
	}

	/** The top of the Vibranium Core (Omega's siphon spot), or null. */
	public Vec3 coreTop() {
		Vec3 c = centre();
		return c == null ? null : c.add(0, 7.0, 0);
	}

	public float uploadProgress(long now) {
		return uploadStart < 0 ? -1f : Math.min(1f, (now - uploadStart) / (float) Math.max(1, ContagionConfig.omega().uploadTicks));
	}

	// ---------------------------------------------------------------- begin

	/** JARVIS found him: mark the site. */
	public static void begin(ServerLevel level, ContagionState st, boolean byTraces) {
		if (st.finale != null) {
			return;
		}
		SokoviaFinale f = new SokoviaFinale();
		f.byTraces = byTraces;
		f.site = pickSite(level);
		f.markedAt = level.getGameTime();
		f.phaseStart = f.markedAt;
		st.finale = f;
		st.stage = ContagionState.Stage.FINALE;
		st.setDirty();
		for (ServerPlayer p : level.players()) {
			JarvisDialogue.speak(p, "contagion_found");
			Contagion.title(p, Component.translatable("title.projecthero.sokovia").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
					Component.translatable(byTraces ? "title.projecthero.sokovia.sub" : "title.projecthero.sokovia.sub_saturation")
							.withStyle(ChatFormatting.GRAY));
			p.sendSystemMessage(Component.translatable("message.projecthero.contagion.site", f.site.getX(), f.site.getZ())
					.withStyle(ChatFormatting.RED));
		}
		Contagion.sayAll(level, "sokovia");
		ProjectHeroMod.LOGGER.info("[ProjectHero] Sokovia Protocol site at {}", f.site);
	}

	/** 300-600 blocks from the players' average position (never loads anything to look). */
	static BlockPos pickSite(ServerLevel level) {
		List<ServerPlayer> ps = Contagion.eligiblePlayers(level);
		double x = 0, z = 0;
		for (ServerPlayer p : ps) {
			x += p.getX();
			z += p.getZ();
		}
		if (!ps.isEmpty()) {
			x /= ps.size();
			z /= ps.size();
		} else {
			x = level.getSharedSpawnPos().getX();
			z = level.getSharedSpawnPos().getZ();
		}
		ContagionConfig.Finale cfg = ContagionConfig.finale();
		double a = level.random.nextDouble() * Math.PI * 2;
		double r = cfg.minDistance + level.random.nextDouble() * Math.max(0, cfg.maxDistance - cfg.minDistance);
		return BlockPos.containing(x + Math.cos(a) * r, 0, z + Math.sin(a) * r);
	}

	// ---------------------------------------------------------------- tick

	public static void tick(ServerLevel level, ContagionState st) {
		SokoviaFinale f = st.finale;
		if (f == null) {
			return;
		}
		long now = level.getGameTime();
		switch (f.phase) {
			case MARKED -> f.tickMarked(level, st, now);
			case AWAY -> f.tickAway(level, st, now);
			case BUILDING -> f.tickBuilding(level, st, now);
			case FIGHT -> f.tickFight(level, st, now);
			case ENDING -> f.tickEnding(level, st, now);
		}
		if (st.finale != null) {
			f.tickPlayers(level, now);
			f.tickBars(level);
		}
	}

	private boolean siteLoaded(ServerLevel level) {
		return level.isLoaded(new BlockPos(site.getX(), 64, site.getZ())) && level.isPositionEntityTicking(new BlockPos(site.getX(), 64, site.getZ()));
	}

	private boolean playerNear(ServerLevel level, double r) {
		for (ServerPlayer p : Contagion.eligiblePlayers(level)) {
			double dx = p.getX() - site.getX(), dz = p.getZ() - site.getZ();
			if (dx * dx + dz * dz <= r * r) {
				return true;
			}
		}
		return false;
	}

	private void tickMarked(ServerLevel level, ContagionState st, long now) {
		ContagionConfig.Finale cfg = ContagionConfig.finale();
		boolean arrived = playerNear(level, cfg.arriveRadius);
		if ((arrived || now - markedAt >= cfg.waitTicks) && siteLoaded(level)) {
			startBuilding(level, st, now);
			return;
		}
		if (now - markedAt >= cfg.waitTicks) {
			phase = Phase.AWAY;
			phaseStart = now;
			Contagion.sayAll(level, "sokovia_unopposed");
		}
	}

	private void tickAway(ServerLevel level, ContagionState st, long now) {
		charge += 1.0 / Math.max(1, ContagionConfig.finale().chargeTicks);
		if (charge >= 1.0) {
			finish(level, st, Outcome.LOSS);
			return;
		}
		if (playerNear(level, ContagionConfig.finale().arriveRadius) && siteLoaded(level)) {
			startBuilding(level, st, now);
		}
	}

	private void startBuilding(ServerLevel level, ContagionState st, long now) {
		groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, site.getX(), site.getZ());
		int top = level.getMaxBuildHeight() - 12;
		if (groundY + ContagionConfig.finale().height > top) {
			groundY = top - ContagionConfig.finale().height;
		}
		site = new BlockPos(site.getX(), groundY, site.getZ());
		phase = Phase.BUILDING;
		phaseStart = now;
		ensureMarker(level);
		level.playSound(null, site, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 4.0f, 0.4f);
		Contagion.sayNear(level, Vec3.atCenterOf(site), "citadel_rise", 256);
		st.setDirty();
	}

	private void ensureMarker(ServerLevel level) {
		if (marker != null && level.getEntity(marker) != null) {
			return;
		}
		CitadelMarkerEntity m = ContagionEntityTypes.CITADEL_MARKER.create(level);
		if (m != null) {
			m.moveTo(site.getX() + 0.5, groundY, site.getZ() + 0.5, 0f, 0f);
			m.addTag(Contagion.UNIT_TAG);
			level.addFreshEntity(m);
			marker = m.getUUID();
		}
	}

	private List<SkyCitadel.Placement> disc() {
		if (discPlan == null) {
			discPlan = SkyCitadel.planDisc(floorCentre(), site.asLong());
		}
		return discPlan;
	}

	private List<SkyCitadel.Placement> path() {
		if (pathPlan == null) {
			pathPlan = SkyCitadel.planPath(floorCentre(), groundY);
		}
		return pathPlan;
	}

	private void tickBuilding(ServerLevel level, ContagionState st, long now) {
		List<SkyCitadel.Placement> d = disc();
		int budget = 500;
		while (budget-- > 0 && buildCursor < d.size()) {
			SkyCitadel.Placement p = d.get(buildCursor);
			if (!level.isLoaded(BlockPos.of(p.pos()))) {
				break; // wait for the chunk; never force it
			}
			if (SkyCitadel.place(level, p)) {
				placed.add(p.pos());
			}
			buildCursor++;
		}
		List<SkyCitadel.Placement> pp = path();
		for (int i = 0; i < 2 && pathCursor < pp.size(); i++) {
			SkyCitadel.Placement p = pp.get(pathCursor);
			if (!level.isLoaded(BlockPos.of(p.pos()))) {
				break;
			}
			if (SkyCitadel.place(level, p)) {
				placed.add(p.pos());
				BlockPos b = BlockPos.of(p.pos());
				level.sendParticles(UltronFx.RED_SMALL, b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.0);
			}
			pathCursor++;
		}
		if (now % 10 == 0) {
			Vec3 c = centre();
			UltronFx.forced(level, UltronFx.RED_BIG, c.x, c.y - 3, c.z, 30, 10, 3, 10, 0.05);
		}
		if (buildCursor >= d.size() && pathCursor >= pp.size()) {
			startFight(level, st, now);
		}
		st.setDirty();
	}

	private void startFight(ServerLevel level, ContagionState st, long now) {
		phase = Phase.FIGHT;
		phaseStart = now;
		Vec3 c = centre();
		if (core == null || level.getEntity(core) == null) {
			VibraniumCoreEntity v = ContagionEntityTypes.VIBRANIUM_CORE.create(level);
			if (v != null) {
				v.moveTo(c.x, c.y + 1, c.z, 0f, 0f);
				v.addTag(Contagion.UNIT_TAG);
				level.addFreshEntity(v);
				core = v.getUUID();
			}
		}
		if (engines.isEmpty()) {
			double r = ContagionConfig.finale().radius - 5;
			for (int k = 0; k < 4; k++) {
				double a = Math.PI / 4 + k * Math.PI / 2;
				Vec3 at = c.add(Math.cos(a) * r, 0, Math.sin(a) * r);
				LiftEngineEntity e = ContagionEntityTypes.LIFT_ENGINE.create(level);
				if (e == null) {
					continue;
				}
				e.moveTo(at.x, at.y, at.z, (float) Math.toDegrees(a), 0f);
				Mob guard = ContagionUnits.spawn(level, UltronEntityTypes.HEAVY, at.add(Math.cos(a) * -3, 0.2, Math.sin(a) * -3), null, st.version);
				e.setGuard(guard != null ? guard.getUUID() : null);
				e.addTag(Contagion.UNIT_TAG);
				level.addFreshEntity(e);
				engines.add(e.getUUID());
			}
		}
		for (ServerPlayer p : level.players()) {
			double dx = p.getX() - c.x, dz = p.getZ() - c.z;
			if (dx * dx + dz * dz < 64 * 64) {
				fighters.add(p.getUUID());
			}
		}
		st.setDirty();
	}

	private void spawnOmega(ServerLevel level, ContagionState st) {
		omegaSpawned = true;
		Vec3 c = centre().add(0, 4, 0);
		UltronOmegaEntity o = ContagionEntityTypes.ULTRON_OMEGA.create(level);
		if (o == null) {
			return;
		}
		o.moveTo(c.x, c.y, c.z + 6, 180f, 0f);
		o.configure(Math.max(1, fighters.size()), st.version);
		o.addTag(Contagion.UNIT_TAG);
		level.addFreshEntity(o);
		omega = o.getUUID();
		UltronFx.forced(level, ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z + 6, 2, 1, 1, 1, 0);
		UltronFx.beam(level, c.add(0, 60, 6), c.add(0, 0, 6), UltronFx.CANNON, 20);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 4.0f, 0.6f);
	}

	private void tickFight(ServerLevel level, ContagionState st, long now) {
		if (!omegaSpawned && now - phaseStart >= 60) {
			spawnOmega(level, st);
		}
		UltronOmegaEntity o = omega == null ? null : level.getEntity(omega) instanceof UltronOmegaEntity oe ? oe : null;
		boolean vision = o != null && o.inVision();
		if (!vision && siteLoaded(level)) {
			addCharge(level, 1.0 / Math.max(1, ContagionConfig.finale().chargeTicks));
		} else if (!siteLoaded(level)) {
			charge += 1.0 / Math.max(1, ContagionConfig.finale().chargeTicks); // nobody there: it keeps charging
		}
		if (core != null && level.getEntity(core) instanceof VibraniumCoreEntity v) {
			v.setCharge((float) charge);
			if (now % 20 == 0) {
				Vec3 c = v.position().add(0, 3.5, 0);
				level.sendParticles(UltronFx.RED, c.x, c.y, c.z, (int) (2 + charge * 10), 0.8, 1.5, 0.8, 0.02);
			}
		}
		if (charge >= 1.0) {
			finish(level, st, Outcome.LOSS);
			return;
		}
		// fighters: anyone close joins; a down fighter who comes back is back in
		Vec3 c = centre();
		for (ServerPlayer p : level.players()) {
			double dx = p.getX() - c.x, dz = p.getZ() - c.z;
			if (dx * dx + dz * dz < 64 * 64 && UltronCombat.isFighter(p)) {
				fighters.add(p.getUUID());
				down.remove(p.getUUID());
			}
		}
		if (!fighters.isEmpty() && down.containsAll(fighters)) {
			finish(level, st, Outcome.LOSS);
			return;
		}
		// the upload
		if (uploadStart >= 0) {
			antennae.removeIf(id -> !(level.getEntity(id) instanceof LivingEntity le) || !le.isAlive());
			if (antennae.isEmpty()) {
				uploadStart = -1;
				if (o != null) {
					o.strand(level);
				}
				for (ServerPlayer p : fighterPlayers(level)) {
					JarvisDialogue.speak(p, "contagion_stranded");
				}
			} else if (now - uploadStart >= ContagionConfig.omega().uploadTicks) {
				uploadStart = -1;
				if (o != null) {
					o.escape(level);
				}
				finish(level, st, Outcome.ESCAPE);
				return;
			}
		}
		if (omegaSpawned && omega != null && o == null && level.isPositionEntityTicking(BlockPos.containing(c)) && now - phaseStart > 200
				&& now % 100 == 0) {
			// Omega is gone without a death (removed by hand?): the protocol can't finish -- treat it as an escape
			finish(level, st, Outcome.ESCAPE);
		}
	}

	/** Adds {@code d} (fraction) to the Core's charge, clamped 0..1. */
	public void addCharge(ServerLevel level, double d) {
		charge = Math.max(0.0, Math.min(1.0, charge + d));
	}

	/** Omega begins the upload: three Uplink Antennae rise and the 35 s bar starts. */
	public void startUpload(ServerLevel level, UltronOmegaEntity o) {
		uploadStart = level.getGameTime();
		Vec3 c = centre();
		int n = Math.max(1, fighters.size());
		double r = ContagionConfig.finale().radius - 6;
		for (int k = 0; k < 3; k++) {
			double a = k * Math.PI * 2 / 3 + Math.PI / 6;
			UplinkAntennaEntity ant = ContagionEntityTypes.UPLINK_ANTENNA.create(level);
			if (ant == null) {
				continue;
			}
			ant.moveTo(c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r, 0f, 0f);
			ant.configure(n);
			ant.addTag(Contagion.UNIT_TAG);
			level.addFreshEntity(ant);
			antennae.add(ant.getUUID());
		}
		for (ServerPlayer p : fighterPlayers(level)) {
			JarvisDialogue.speak(p, "contagion_upload");
		}
	}

	// ---------------------------------------------------------------- the end

	/** The Vibranium Core's charge for an Ultron that is winning; or ends now (tests / commands). */
	public static void finish(ServerLevel level, ContagionState st, Outcome outcome) {
		SokoviaFinale f = st.finale;
		if (f == null || f.phase == Phase.ENDING) {
			return;
		}
		f.outcome = outcome;
		f.phase = Phase.ENDING;
		f.phaseStart = level.getGameTime();
		f.removeCursor = 0;
		Vec3 c = f.centre();
		// every Contagion robot on the citadel goes
		if (c != null) {
			for (Entity e : level.getEntities((Entity) null, new net.minecraft.world.phys.AABB(c, c).inflate(ContagionConfig.finale().radius + 12, 40, ContagionConfig.finale().radius + 12),
					e -> e.getTags().contains(Contagion.UNIT_TAG) && !(e instanceof UltronOmegaEntity o && o.isAlive() && !o.isDeadOrDying() && outcome == Outcome.WIN))) {
				if (e instanceof CitadelMarkerEntity) {
					continue;
				}
				if (!(e instanceof UltronOmegaEntity)) {
					UltronFx.wreck(level, e.position().add(0, 1, 0), 0.8);
				}
				e.discard();
			}
		}
		for (ServerPlayer p : f.fighterPlayers(level)) {
			p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 600, 0, false, false, true));
		}
		switch (outcome) {
			case WIN -> f.win(level, st);
			case LOSS -> f.lose(level, st);
			case ESCAPE -> f.escaped(level, st);
			default -> {
			}
		}
		if (f.chargeBar != null) {
			f.chargeBar.removeAllPlayers();
		}
		if (f.uploadBar != null) {
			f.uploadBar.removeAllPlayers();
		}
		st.setDirty();
	}

	private void win(ServerLevel level, ContagionState st) {
		Vec3 c = centre();
		List<ServerPlayer> winners = new ArrayList<>();
		for (ServerPlayer p : fighterPlayers(level)) {
			if (!down.contains(p.getUUID())) {
				winners.add(p);
			}
		}
		if (c != null) {
			BlockPos chestAt = Contagion.groundAt(level, BlockPos.containing(c.x, 0, c.z));
			if (chestAt == null) {
				chestAt = new BlockPos(site.getX(), groundY, site.getZ());
			}
			ContagionRewards.placeChest(level, chestAt, Math.max(1, winners.size()));
		}
		for (ServerPlayer p : winners) {
			ContagionRewards.grantFirstWin(p);
			JarvisDialogue.speak(p, "contagion_won");
			Contagion.title(p, Component.translatable("title.projecthero.sokovia.won").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
					Component.translatable("title.projecthero.sokovia.won.sub").withStyle(ChatFormatting.WHITE));
		}
		// the infection is wiped: every Nexus collapses, every recorded block reverts, golems and machines come back
		for (UUID id : new ArrayList<>(st.nexus.keySet())) {
			ContagionState.NexusRecord r = st.nexus.get(id);
			Assimilation.releaseAround(level, st, Vec3.atCenterOf(r.pos), Math.max(4, r.radius));
			if (level.getEntity(id) instanceof Entity n) {
				UltronFx.wreck(level, n.position().add(0, 4, 0), 3.0);
				n.discard();
			}
			NaniteCorruption.queueRevertWave(st, id);
		}
		Assimilation.releaseAllMachines(level, st);
		st.omegaBeaten = true;
		st.saturation = 0;
		st.traces = 0;
		st.stage = ContagionState.Stage.DORMANT;
		st.nextResurgenceRoll = -1;
		level.playSound(null, BlockPos.containing(c != null ? c : Vec3.atCenterOf(site)), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS,
				2.0f, 1.0f);
	}

	private void lose(ServerLevel level, ContagionState st) {
		Vec3 c = centre();
		if (c != null && siteLoaded(level)) {
			// the drop: a column of debris down to the ground, then the impact
			for (double y = c.y; y > groundY; y -= 3) {
				UltronFx.forced(level, ParticleTypes.LARGE_SMOKE, c.x, y, c.z, 12, 8, 1.5, 8, 0.05);
				UltronFx.forced(level, UltronFx.RED_BIG, c.x, y, c.z, 6, 8, 1.5, 8, 0.05);
			}
			level.playSound(null, BlockPos.containing(c), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 6.0f, 0.3f);
			SkyCitadel.crater(level, st, new BlockPos(site.getX(), groundY, site.getZ()), 12);
		}
		st.saturation = ContagionConfig.saturation().resetTo;
		st.traces = 0;
		st.stage = ContagionState.Stage.ACTIVE;
		st.nextNexusAt = -1;
		Contagion.sayAll(level, "sokovia_won");
		for (ServerPlayer p : level.players()) {
			Contagion.title(p, Component.translatable("title.projecthero.sokovia.lost").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
					Component.translatable("title.projecthero.sokovia.lost.sub").withStyle(ChatFormatting.GRAY));
		}
	}

	private void escaped(ServerLevel level, ContagionState st) {
		st.saturation = ContagionConfig.saturation().resetTo;
		st.traces = 2; // one Trace lost: one more Nexus brings JARVIS back to him
		st.stage = ContagionState.Stage.ACTIVE;
		st.nextNexusAt = -1;
		for (ServerPlayer p : level.players()) {
			JarvisDialogue.speak(p, "contagion_escaped");
			Contagion.title(p, Component.translatable("title.projecthero.sokovia.escaped").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
					Component.translatable("title.projecthero.sokovia.escaped.sub").withStyle(ChatFormatting.GRAY));
		}
	}

	/** Takes the citadel away (our own blocks only, loaded chunks only); the finale ends when nothing is left. */
	private void tickEnding(ServerLevel level, ContagionState st, long now) {
		if (marker != null && level.getEntity(marker) instanceof Entity m) {
			m.discard();
		}
		int budget = 400;
		boolean particles = outcome == Outcome.LOSS;
		int examined = 0;
		while (removeCursor < placed.size() && budget > 0 && examined < 2000) {
			examined++;
			long key = placed.getLong(removeCursor);
			if (SkyCitadel.remove(level, key, particles && budget % 7 == 0)) {
				placed.removeLong(removeCursor);
				budget--;
			} else {
				removeCursor++; // not loaded: come back to it
			}
		}
		if (removeCursor >= placed.size()) {
			removeCursor = 0;
		}
		if (outcome == Outcome.WIN && now % 4 == 0 && centre() != null) {
			Vec3 c = centre();
			UltronFx.forced(level, UltronFx.CYAN, c.x, c.y, c.z, 20, 10, 2, 10, 0.05);
		}
		if (placed.isEmpty()) {
			for (UUID id : new UUID[] { omega, core, marker }) {
				if (id != null && level.getEntity(id) instanceof Entity e && !(e instanceof UltronOmegaEntity o && o.isDeadOrDying())) {
					e.discard();
				}
			}
			st.finale = null;
			if (st.stage == ContagionState.Stage.FINALE) {
				st.stage = ContagionState.Stage.ACTIVE;
			}
		}
		st.setDirty();
	}

	/** {@code /projecthero contagion stop}: end the finale and take everything away. */
	public static void abort(ServerLevel level, ContagionState st) {
		if (st.finale == null) {
			return;
		}
		finish(level, st, Outcome.ABORT);
		SokoviaFinale f = st.finale;
		if (f != null) {
			for (int i = 0; i < 200 && st.finale != null; i++) {
				f.tickEnding(level, st, level.getGameTime());
			}
		}
	}

	// ---------------------------------------------------------------- hooks

	public static void onPlayerDied(ServerPlayer p) {
		ContagionState st = ContagionState.get(p.serverLevel());
		SokoviaFinale f = st.finale;
		if (f != null && f.phase == Phase.FIGHT && f.fighters.contains(p.getUUID())) {
			f.down.add(p.getUUID());
			st.setDirty();
		}
	}

	public static void onEngineDestroyed(ServerLevel level, LiftEngineEntity engine) {
		ContagionState st = ContagionState.get(level);
		SokoviaFinale f = st.finale;
		if (f == null) {
			return;
		}
		f.engines.remove(engine.getUUID());
		f.addCharge(level, -ContagionConfig.finale().engineChargeRemoval);
		for (ServerPlayer p : f.fighterPlayers(level)) {
			JarvisDialogue.speak(p, "contagion_engine_down");
		}
		st.setDirty();
	}

	public static void onAntennaDestroyed(ServerLevel level, UplinkAntennaEntity antenna) {
		ContagionState st = ContagionState.get(level);
		if (st.finale != null) {
			st.finale.antennae.remove(antenna.getUUID());
			st.setDirty();
		}
	}

	public static void onOmegaDefeated(ServerLevel level, UltronOmegaEntity o, boolean byMindStone) {
		ContagionState st = ContagionState.get(level);
		if (st.finale != null && o.getUUID().equals(st.finale.omega)) {
			finish(level, st, Outcome.WIN);
		}
	}

	/** The one-time Stark Lift: right-click the marker beam's base. */
	public static void starkLift(ServerPlayer p) {
		ContagionState st = ContagionState.get(p.serverLevel());
		SokoviaFinale f = st.finale;
		if (f == null || (f.phase != Phase.FIGHT && f.phase != Phase.BUILDING)) {
			p.displayClientMessage(Component.translatable("message.projecthero.contagion.lift_offline").withStyle(ChatFormatting.GRAY), true);
			return;
		}
		if (!f.liftUsed.add(p.getUUID())) {
			p.displayClientMessage(Component.translatable("message.projecthero.contagion.lift_used").withStyle(ChatFormatting.GRAY), true);
			return;
		}
		f.lifting.put(p.getUUID(), 0);
		p.displayClientMessage(Component.translatable("message.projecthero.contagion.lift").withStyle(ChatFormatting.AQUA), true);
		JarvisDialogue.speak(p, "contagion_lift");
		st.setDirty();
	}

	// ---------------------------------------------------------------- players: the safety net, the lift

	private static boolean canFly(Player p) {
		return p.getAbilities().flying || p.getAbilities().mayfly || p.isFallFlying();
	}

	private void tickPlayers(ServerLevel level, long now) {
		Vec3 c = centre();
		if (c == null || (phase != Phase.FIGHT && phase != Phase.BUILDING)) {
			lifting.clear();
			falling.clear();
			return;
		}
		double reach = ContagionConfig.finale().radius + 30;
		for (ServerPlayer p : level.players()) {
			double dx = p.getX() - c.x, dz = p.getZ() - c.z;
			if (dx * dx + dz * dz > reach * reach) {
				falling.remove(p.getUUID());
				lifting.remove(p.getUUID());
				continue;
			}
			Integer lift = lifting.get(p.getUUID());
			if (lift != null) {
				tickLift(level, p, c, lift);
				continue;
			}
			if (p.isSpectator() || canFly(p) || p.onGround() || p.getY() >= c.y - 2 || p.getY() <= groundY + 2 || p.getDeltaMovement().y > -0.2) {
				falling.remove(p.getUUID());
				continue;
			}
			int t = falling.merge(p.getUUID(), 1, Integer::sum);
			if (t >= ContagionConfig.finale().fallTicks) {
				falling.remove(p.getUUID());
				double a = Math.atan2(dz, dx);
				double r = ContagionConfig.finale().radius - 4;
				p.teleportTo(c.x + Math.cos(a) * r, c.y + 0.2, c.z + Math.sin(a) * r);
				p.setDeltaMovement(Vec3.ZERO);
				p.hurtMarked = true;
				p.resetFallDistance();
				p.hurt(level.damageSources().fall(), ContagionConfig.finale().fallDamage);
				p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, false, false, true));
				UltronFx.forced(level, UltronFx.CYAN, p.getX(), p.getY() + 1, p.getZ(), 20, 0.4, 0.8, 0.4, 0.05);
				p.displayClientMessage(Component.translatable("message.projecthero.contagion.safety_net").withStyle(ChatFormatting.AQUA), true);
			}
		}
	}

	private void tickLift(ServerLevel level, ServerPlayer p, Vec3 c, int t) {
		lifting.put(p.getUUID(), t + 1);
		p.resetFallDistance();
		p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0, false, false, false));
		if (t % 2 == 0) {
			ContagionNet.beam(level, new Vec3(p.getX(), groundY, p.getZ()), p.position().add(0, 2, 0), 0x40B8FF, 0.45f, 4);
		}
		if (p.getY() < c.y + 2.5) {
			p.setDeltaMovement(p.getDeltaMovement().multiply(0.3, 0, 0.3).add(0, 0.9, 0));
		} else {
			Vec3 to = c.subtract(p.position()).multiply(1, 0, 1);
			if (to.length() < ContagionConfig.finale().radius - 3 || t > 400) {
				lifting.remove(p.getUUID());
				return;
			}
			Vec3 v = to.normalize().scale(0.7);
			p.setDeltaMovement(v.x, 0.05, v.z);
		}
		p.hurtMarked = true;
		if (t > 600) {
			lifting.remove(p.getUUID());
		}
	}

	// ---------------------------------------------------------------- bars and HUD

	private void tickBars(ServerLevel level) {
		Vec3 c = centre();
		if (c == null || (phase != Phase.FIGHT && phase != Phase.BUILDING)) {
			return;
		}
		if (chargeBar == null) {
			chargeBar = new ServerBossEvent(Component.translatable("event.projecthero.sokovia.charge", 0), BossEvent.BossBarColor.RED,
					BossEvent.BossBarOverlay.NOTCHED_10);
		}
		chargeBar.setProgress((float) Math.max(0, Math.min(1, charge)));
		if (level.getGameTime() % 20 == 0) {
			chargeBar.setName(Component.translatable("event.projecthero.sokovia.charge", (int) Math.floor(charge * 100)));
		}
		float up = uploadProgress(level.getGameTime());
		if (up >= 0) {
			if (uploadBar == null) {
				uploadBar = new ServerBossEvent(Component.translatable("event.projecthero.sokovia.upload"), BossEvent.BossBarColor.PURPLE,
						BossEvent.BossBarOverlay.PROGRESS);
			}
			uploadBar.setProgress(up);
		} else if (uploadBar != null) {
			uploadBar.removeAllPlayers();
			uploadBar = null;
		}
		if (level.getGameTime() % 10 == 0) {
			for (ServerPlayer p : level.players()) {
				double dx = p.getX() - c.x, dz = p.getZ() - c.z;
				boolean near = dx * dx + dz * dz < 160 * 160;
				if (near) {
					chargeBar.addPlayer(p);
					if (uploadBar != null) {
						uploadBar.addPlayer(p);
					}
				} else {
					chargeBar.removePlayer(p);
					if (uploadBar != null) {
						uploadBar.removePlayer(p);
					}
				}
			}
		}
	}

	/** The fighters who are online in {@code level}. */
	public List<ServerPlayer> fighterPlayers(ServerLevel level) {
		List<ServerPlayer> out = new ArrayList<>();
		for (UUID id : fighters) {
			if (level.getPlayerByUUID(id) instanceof ServerPlayer p) {
				out.add(p);
			}
		}
		return out;
	}

	/** The Omega entity, if loaded. */
	public UltronOmegaEntity omega(ServerLevel level) {
		return omega != null && level.getEntity(omega) instanceof UltronOmegaEntity o ? o : null;
	}

	/** Test hook: the ground is at {@code y} and the site is here. */
	public void setSiteForTest(BlockPos ground) {
		site = ground;
		groundY = ground.getY();
		discPlan = null;
		pathPlan = null;
	}

	// ---------------------------------------------------------------- save / load

	public CompoundTag save() {
		CompoundTag t = new CompoundTag();
		t.putString("Phase", phase.name());
		t.putString("Outcome", outcome.name());
		t.putLong("Site", site.asLong());
		t.putInt("GroundY", groundY);
		t.putLong("MarkedAt", markedAt);
		t.putLong("PhaseStart", phaseStart);
		t.putDouble("Charge", charge);
		t.putInt("BuildCursor", buildCursor);
		t.putInt("PathCursor", pathCursor);
		t.putBoolean("ByTraces", byTraces);
		t.putLongArray("Placed", placed.toLongArray());
		if (omega != null) {
			t.putUUID("Omega", omega);
		}
		if (core != null) {
			t.putUUID("Core", core);
		}
		if (marker != null) {
			t.putUUID("Marker", marker);
		}
		t.put("Engines", uuids(engines));
		t.put("Antennae", uuids(antennae));
		t.put("Fighters", uuids(fighters));
		t.put("Down", uuids(down));
		t.put("LiftUsed", uuids(liftUsed));
		t.putLong("UploadStart", uploadStart);
		t.putBoolean("OmegaSpawned", omegaSpawned);
		return t;
	}

	public static SokoviaFinale load(CompoundTag t) {
		SokoviaFinale f = new SokoviaFinale();
		try {
			f.phase = Phase.valueOf(t.getString("Phase"));
			f.outcome = Outcome.valueOf(t.getString("Outcome"));
		} catch (IllegalArgumentException e) {
			f.phase = Phase.ENDING;
			f.outcome = Outcome.ABORT;
		}
		f.site = BlockPos.of(t.getLong("Site"));
		f.groundY = t.getInt("GroundY");
		f.markedAt = t.getLong("MarkedAt");
		f.phaseStart = t.getLong("PhaseStart");
		f.charge = t.getDouble("Charge");
		f.buildCursor = t.getInt("BuildCursor");
		f.pathCursor = t.getInt("PathCursor");
		f.byTraces = t.getBoolean("ByTraces");
		for (long l : t.getLongArray("Placed")) {
			f.placed.add(l);
		}
		f.omega = t.hasUUID("Omega") ? t.getUUID("Omega") : null;
		f.core = t.hasUUID("Core") ? t.getUUID("Core") : null;
		f.marker = t.hasUUID("Marker") ? t.getUUID("Marker") : null;
		readUuids(t.getList("Engines", Tag.TAG_INT_ARRAY), f.engines);
		readUuids(t.getList("Antennae", Tag.TAG_INT_ARRAY), f.antennae);
		readUuids(t.getList("Fighters", Tag.TAG_INT_ARRAY), f.fighters);
		readUuids(t.getList("Down", Tag.TAG_INT_ARRAY), f.down);
		readUuids(t.getList("LiftUsed", Tag.TAG_INT_ARRAY), f.liftUsed);
		f.uploadStart = t.getLong("UploadStart");
		f.omegaSpawned = t.getBoolean("OmegaSpawned");
		return f;
	}

	private static ListTag uuids(Iterable<UUID> ids) {
		ListTag l = new ListTag();
		for (UUID u : ids) {
			l.add(NbtUtils.createUUID(u));
		}
		return l;
	}

	private static void readUuids(ListTag l, java.util.Collection<UUID> into) {
		for (Tag t : l) {
			into.add(NbtUtils.loadUUID(t));
		}
	}
}
