package com.projecthero.mod.darkseid.raid;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.darkseid.DarkseidConfig;
import com.projecthero.mod.darkseid.DarkseidDamage;
import com.projecthero.mod.darkseid.DarkseidFx;
import com.projecthero.mod.darkseid.DarkseidSounds;
import com.projecthero.mod.darkseid.entity.BoomTubeEntity;
import com.projecthero.mod.darkseid.entity.DarkseidAnims;
import com.projecthero.mod.darkseid.entity.DarkseidEntity;
import com.projecthero.mod.darkseid.entity.DarkseidEntityTypes;
import com.projecthero.mod.darkseid.entity.MotherBoxEntity;
import com.projecthero.mod.darkseid.entity.ParademonEntity;
import com.projecthero.mod.darkseid.item.DarkseidItems;
import com.projecthero.mod.event.EventBossBar;
import com.projecthero.mod.event.EventConfig;
import com.projecthero.mod.event.EventInstance;
import com.projecthero.mod.event.EventManager;
import com.projecthero.mod.event.EventSavedData;
import com.projecthero.mod.event.EventSpawns;
import com.projecthero.mod.event.EventState;
import com.projecthero.mod.event.raid.ZombieRaidNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * <b>APOKOLIPS INVASION</b> -- the Darkseid Raid's manager. Built on the mod's world-event framework
 * ({@link EventInstance}): the framework gives it persistence, owned-mob tracking and cleanup, and the
 * pause/abandon rules; this class is the raid's state machine.
 *
 * <pre>
 * INACTIVE -> PREPARATION -> INVASION_WAVE_1 -> INVASION_WAVE_2 -> INVASION_WAVE_3 -> DARKSEID_ENTRANCE
 *   -> MOTHER_BOX_PHASE -> DARKSEID_PHASE_1 -(60%)-> DARKSEID_PHASE_2 -(25%)-> DARKSEID_PHASE_3 -> VICTORY
 *                                         (all participants down at once, at any point) -> DEFEAT
 * </pre>
 *
 * <h2>What lives where</h2>
 * The raid decides the <em>encounter</em>: who is taking part ({@link DarkseidRoster}), the arena boundary, the
 * waves and their Boom Tubes, the Mother Boxes and their overloads, Darkseid's shield and phase, the soft enrage,
 * reinforcements, victory, defeat, rewards and cleanup. {@link DarkseidEntity} and its {@code DarkseidCombat}
 * decide only how he fights. The raid tells him which phase he is in; he tells the raid when he has died.
 *
 * <h2>Cost</h2>
 * Runs on the framework's 10-tick cadence. Per tick it walks the level's player list once, its (at most eight)
 * roster members, its four boxes, its handful of open Boom Tubes and its owned mobs (capped at
 * {@link DarkseidConfig.Raid#enemyCap}); every entity it owns is found by UUID, never by an area scan.
 *
 * <h2>Cleanup</h2>
 * Victory, defeat, {@code /projecthero raid end darkseid}, a server stop and a server start (a crash) all end the
 * raid through {@link #cleanup}: Darkseid, Parademons, Mother Boxes, Boom Tubes, boss bars, the sky tint, the music
 * and any Omega marks go. Every raid entity also checks for its raid itself (their "orphan guards"), so one sitting
 * in a chunk that was unloaded at the time removes itself the next time it ticks.
 */
public class DarkseidRaid extends EventInstance {
	public static final String TYPE_ID = "darkseid_raid";

	public enum Stage {
		INACTIVE, PREPARATION, INVASION_WAVE_1, INVASION_WAVE_2, INVASION_WAVE_3, DARKSEID_ENTRANCE, MOTHER_BOX_PHASE,
		DARKSEID_PHASE_1, DARKSEID_PHASE_2, DARKSEID_PHASE_3, VICTORY, DEFEAT;

		public boolean isWave() {
			return this == INVASION_WAVE_1 || this == INVASION_WAVE_2 || this == INVASION_WAVE_3;
		}

		public boolean isFight() {
			return this == DARKSEID_PHASE_1 || this == DARKSEID_PHASE_2 || this == DARKSEID_PHASE_3;
		}

		public int waveNumber() {
			return this == INVASION_WAVE_1 ? 1 : this == INVASION_WAVE_2 ? 2 : this == INVASION_WAVE_3 ? 3 : 0;
		}
	}

	private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(1.0f, 0.1f, 0.05f), 1.5f);

	/** Sky colours per stretch of the raid (fog/horizon; the dome is drawn a little brighter). */
	private static final int SKY_INVASION = 0x2A0714;
	private static final int SKY_DARKSEID = 0x1A0205;
	private static final int SKY_PHASE_2 = 0x360604;
	private static final int SKY_RAGE = 0x560A04;

	/** A Boom Tube the raid is sending Parademons through. */
	private static final class Tube {
		Vec3 pos;
		BoomTubeEntity.Kind kind;
		final List<ParademonEntity.Variant> queue = new ArrayList<>();
		long openAt;
		UUID entityId;
	}

	/** A Mother Box and its neglect timer (the overload clock is the raid's rule, not the box's). */
	private static final class Box {
		UUID id;
		Vec3 pos;
		int neglectTicks;
		boolean disabled;
	}

	/** A lingering overload danger zone. Transient -- it is gone within seconds anyway. */
	private record Hazard(Vec3 pos, double radius, long until) {
	}

	private Stage stage = Stage.INACTIVE;
	private int stageTicks;
	private boolean waveCleared;
	private int breakTicks;
	private final DarkseidRoster roster = new DarkseidRoster();
	private UUID activatorId;
	private UUID darkseidId;
	private float lastDarkseidHealth = 1.0f;
	private int darkseidMissingTicks;
	private final List<Box> boxes = new ArrayList<>();
	private final List<Tube> tubes = new ArrayList<>();
	private final List<Hazard> hazards = new ArrayList<>();
	private boolean overloadHappened;
	private boolean rosterSealed;
	private int enrageLevel;
	private long arrivedAtAge = -1;
	private int reactivateTicks;
	private int enrageTubeTicks;
	private boolean rewardsGranted;
	private int waveTotal = 1;
	private int lastAliveCount = -1;
	private int unchangedTicks;
	private boolean musicPlaying;
	/** Set by the server-stop hook: an aborted raid gives the activator their beacon back. */
	public boolean refundOnAbort;

	private final EventBossBar raidBar;
	/** Rebuilt every raid tick: roster members Darkseid may target right now. */
	private final List<ServerPlayer> combatTargets = new ArrayList<>();

	public DarkseidRaid(UUID id) {
		super(id);
		this.raidBar = new EventBossBar(id, BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10, false);
	}

	// ================================================================ identity / lookup

	@Override
	public String typeId() {
		return TYPE_ID;
	}

	@Override
	public Component displayName() {
		return Component.translatable("event.projecthero.darkseid_raid");
	}

	@Override
	public double radius() {
		return DarkseidConfig.raid().raidRadius;
	}

	/** The running raid with this id, or null (finished, removed, or never existed). */
	public static DarkseidRaid find(ServerLevel level, UUID id) {
		if (id == null) {
			return null;
		}
		return EventSavedData.get(level).byId(id) instanceof DarkseidRaid raid && raid.state().active() ? raid : null;
	}

	/** Every running Darkseid Raid on the server. */
	public static List<DarkseidRaid> all(net.minecraft.server.MinecraftServer server) {
		List<DarkseidRaid> out = new ArrayList<>();
		for (EventInstance e : EventManager.active(server)) {
			if (e instanceof DarkseidRaid raid) {
				out.add(raid);
			}
		}
		return out;
	}

	public Stage stage() {
		return stage;
	}

	public DarkseidRoster roster() {
		return roster;
	}

	public int enrageLevel() {
		return enrageLevel;
	}

	public boolean overloadHappened() {
		return overloadHappened;
	}

	public boolean rosterSealed() {
		return rosterSealed;
	}

	public UUID darkseidId() {
		return darkseidId;
	}

	public boolean isParticipant(UUID player) {
		return roster.contains(player);
	}

	public boolean isInArena(Player p) {
		BlockPos c = center();
		double r = radius();
		return c != null && p.distanceToSqr(c.getX() + 0.5, p.getY(), c.getZ() + 0.5) <= r * r;
	}

	public List<ServerPlayer> combatTargets() {
		return combatTargets;
	}

	public int enemyCap() {
		return DarkseidConfig.raid().enemyCap;
	}

	public DarkseidEntity darkseid(ServerLevel level) {
		return darkseidId != null && level.getEntity(darkseidId) instanceof DarkseidEntity d && !d.isRemoved() ? d : null;
	}

	public int enemiesAlive(ServerLevel level) {
		int n = 0;
		for (Mob mob : liveOwnedMobs(level)) {
			if (mob instanceof ParademonEntity) {
				n++;
			}
		}
		return n;
	}

	private int enemiesQueued() {
		int n = 0;
		for (Tube t : tubes) {
			n += t.queue.size();
		}
		return n;
	}

	/** Players channelling a Mother Box right now (Darkseid's Grip goes for them first). */
	public List<LivingEntity> channelers(ServerLevel level) {
		List<LivingEntity> out = new ArrayList<>();
		for (Box b : boxes) {
			if (!b.disabled && level.getEntity(b.id) instanceof MotherBoxEntity box && box.isBeingChanneled()) {
				ServerPlayer p = box.channeler(level);
				if (p != null) {
					out.add(p);
				}
			}
		}
		return out;
	}

	// ================================================================ activation

	/**
	 * Begin an invasion at {@code at}, triggered by {@code activator} (the Boom Tube Beacon, or the admin command).
	 * Registers everyone eligible within {@code registrationRadius}, nearest first, up to the cap.
	 *
	 * @return the raid, or null if one is already running too close
	 */
	public static DarkseidRaid startAt(ServerLevel level, BlockPos at, ServerPlayer activator) {
		DarkseidRaid raid = new DarkseidRaid(UUID.randomUUID());
		if (!EventManager.start(level, raid, at)) {
			return null;
		}
		raid.activate(level, activator);
		return raid;
	}

	private void activate(ServerLevel level, ServerPlayer activator) {
		activatorId = activator == null ? null : activator.getUUID();
		setState(EventState.RUNNING);
		DarkseidConfig.Raid cfg = DarkseidConfig.raid();
		BlockPos c = center();
		List<ServerPlayer> nearby = new ArrayList<>();
		for (ServerPlayer p : level.players()) {
			if (canJoin(p) && p.distanceToSqr(c.getX() + 0.5, p.getY(), c.getZ() + 0.5) <= cfg.registrationRadius * cfg.registrationRadius) {
				nearby.add(p);
			}
		}
		nearby.sort((a, b) -> Double.compare(a.distanceToSqr(Vec3.atCenterOf(c)), b.distanceToSqr(Vec3.atCenterOf(c))));
		if (activator != null && canJoin(activator)) {
			nearby.remove(activator);
			nearby.add(0, activator);
		}
		for (ServerPlayer p : nearby) {
			if (roster.size() >= cfg.maxParticipants) {
				break;
			}
			roster.add(p.getUUID(), p.getGameProfile().getName(), true);
		}
		participants().refresh(level, c, EventConfig.framework().abandonRadius, true);
		setStage(Stage.PREPARATION);

		announce(level, Component.translatable("title.projecthero.darkseid_raid.invasion").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
				Component.translatable("title.projecthero.darkseid_raid.approaches").withStyle(ChatFormatting.RED));
		tellAll(level, Component.translatable("message.projecthero.darkseid_raid.roster", roster.size(), cfg.maxParticipants)
				.withStyle(ChatFormatting.GOLD));
		if (activator != null && !canJoin(activator)) {
			activator.sendSystemMessage(Component.translatable("message.projecthero.darkseid_raid.creative").withStyle(ChatFormatting.GRAY));
		}
		Vec3 cv = Vec3.atBottomCenterOf(c);
		level.playSound(null, c, DarkseidSounds.BOOM_TUBE, SoundSource.HOSTILE, 5.0f, 0.6f);
		level.playSound(null, c, DarkseidSounds.ENTRANCE, SoundSource.HOSTILE, 4.0f, 0.7f);
		BoomTubeEntity.open(level, cv.add(0, 3, 0), BoomTubeEntity.Kind.ENTRANCE, 3.0f, 50, id());
		DarkseidFx.shake(level, cv, radius() + 32, 0.8f, 20);
		pushSky(level);
		ProjectHeroMod.LOGGER.info("[ProjectHero] Darkseid Raid {} started at {} with {} participant(s)", id(), c, roster.size());
	}

	private static boolean canJoin(ServerPlayer p) {
		return p.isAlive() && !p.isSpectator() && !p.isCreative();
	}

	private void setStage(Stage next) {
		stage = next;
		stageTicks = 0;
	}

	// ================================================================ tick

	@Override
	protected void onTick(ServerLevel level) {
		int dt = Math.max(1, EventConfig.framework().tickIntervalTicks);
		if (stage == Stage.INACTIVE) {
			// a raid record with no activation (should not happen) -- start it cleanly
			activate(level, null);
		}
		stageTicks += dt;
		refreshRoster(level, dt);
		if (stage == Stage.VICTORY) {
			tickVictory(level);
			return;
		}
		if (checkDefeat(level)) {
			return;
		}
		switch (stage) {
			case PREPARATION -> tickPreparation(level);
			case INVASION_WAVE_1, INVASION_WAVE_2, INVASION_WAVE_3 -> tickWave(level, dt);
			case DARKSEID_ENTRANCE -> tickEntrance(level);
			case MOTHER_BOX_PHASE, DARKSEID_PHASE_1, DARKSEID_PHASE_2, DARKSEID_PHASE_3 -> tickDarkseid(level, dt);
			default -> {
			}
		}
		tickBoxes(level, dt);
		tickTubes(level);
		tickHazards(level, dt);
		tetherOwnedMobs(level, radius() - 4.0);
		updateBars(level);
		pushSky(level);
	}

	// ================================================================ roster / boundary / defeat

	private void refreshRoster(ServerLevel level, int dt) {
		DarkseidConfig.Raid cfg = DarkseidConfig.raid();
		BlockPos c = center();
		double cx = c.getX() + 0.5;
		double cz = c.getZ() + 0.5;
		double r = radius();
		long now = level.getGameTime();
		boolean openStage = stage.ordinal() < Stage.VICTORY.ordinal();

		// new arrivals
		for (ServerPlayer p : level.players()) {
			if (!canJoin(p) || roster.contains(p.getUUID())) {
				continue;
			}
			if (p.distanceToSqr(cx, p.getY(), cz) > r * r || !openStage) {
				continue;
			}
			if (!rosterSealed && roster.size() < cfg.maxParticipants) {
				roster.add(p.getUUID(), p.getGameProfile().getName(), false);
				tellAll(level, Component.translatable("message.projecthero.darkseid_raid.joined", p.getDisplayName())
						.withStyle(ChatFormatting.GOLD));
			} else if (roster.markToldOutsider(p.getUUID())) {
				p.sendSystemMessage(Component.translatable(rosterSealed ? "message.projecthero.darkseid_raid.sealed_outsider"
						: "message.projecthero.darkseid_raid.full_outsider").withStyle(ChatFormatting.GRAY));
			}
		}

		combatTargets.clear();
		for (DarkseidRoster.Member m : roster.all()) {
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(m.id);
			if (p == null || p.level() != level) {
				m.inside = false;
				continue;
			}
			m.alive = p.isAlive();
			if (!m.alive) {
				m.inside = false;
				continue;
			}
			double d = Math.sqrt(p.distanceToSqr(cx, p.getY(), cz));
			if (m.lockedOut(now)) {
				m.inside = false;
				if (d <= r && openStage) {
					// back too soon after dying: set down just outside the boundary
					Vec3 out = edgePoint(level, p, r + 4.0);
					p.teleportTo(level, out.x, out.y, out.z, p.getYRot(), p.getXRot());
					p.displayClientMessage(Component.translatable("message.projecthero.darkseid_raid.reentry",
							(m.lockoutUntil - now + 19) / 20).withStyle(ChatFormatting.RED), true);
				}
				continue;
			}
			if (d <= r) {
				m.inside = true;
				m.outsideTicks = 0;
			} else if (d <= cfg.leaveRadius && openStage) {
				m.inside = true;
				m.outsideTicks += dt;
				int left = cfg.boundaryGraceSeconds - m.outsideTicks / 20;
				if (left > 0) {
					p.displayClientMessage(Component.translatable("message.projecthero.darkseid_raid.boundary", left)
							.withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
				} else {
					// pulled back in -- flight is never disabled, only the arena is enforced
					Vec3 back = edgePoint(level, p, r - 6.0);
					p.teleportTo(level, back.x, back.y, back.z, p.getYRot(), p.getXRot());
					p.fallDistance = 0.0f;
					m.outsideTicks = 0;
					level.sendParticles(ParticleTypes.PORTAL, back.x, back.y + 1, back.z, 30, 0.4, 0.8, 0.4, 0.3);
					p.displayClientMessage(Component.translatable("message.projecthero.darkseid_raid.pulled_back")
							.withStyle(ChatFormatting.LIGHT_PURPLE), true);
				}
			} else {
				m.inside = false; // left on purpose (teleported home, ...) -- no longer chased or pulled
			}
			if (m.inside && d <= r + 16.0 && !p.isCreative() && !p.isSpectator()) {
				combatTargets.add(p);
			}
		}
	}

	/** A safe point {@code distance} from the centre, toward {@code p}, at a sane height for them. */
	private Vec3 edgePoint(ServerLevel level, ServerPlayer p, double distance) {
		BlockPos c = center();
		double dx = p.getX() - (c.getX() + 0.5);
		double dz = p.getZ() - (c.getZ() + 0.5);
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 1.0e-3) {
			dx = 1;
			len = 1;
		}
		double x = c.getX() + 0.5 + dx / len * distance;
		double z = c.getZ() + 0.5 + dz / len * distance;
		int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
		// flyers keep (a capped amount of) their altitude; walkers land on the surface
		double y = Math.max(surface, Math.min(p.getY(), surface + 24.0));
		return new Vec3(x, y, z);
	}

	/** Defeat: every participant who is online is dead or waiting out their re-entry delay, all at once. */
	private boolean checkDefeat(ServerLevel level) {
		if (stage.ordinal() < Stage.INVASION_WAVE_1.ordinal() || stage.ordinal() >= Stage.VICTORY.ordinal() || roster.isEmpty()) {
			return false;
		}
		long now = level.getGameTime();
		boolean anyOnline = false;
		for (DarkseidRoster.Member m : roster.all()) {
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(m.id);
			if (p == null) {
				continue;
			}
			anyOnline = true;
			if (p.isAlive() && !m.lockedOut(now)) {
				return false;
			}
		}
		if (!anyOnline) {
			return false; // nobody online at all -- the framework's pause/abandon rules handle that
		}
		setStage(Stage.DEFEAT);
		fail(level, Component.translatable("message.projecthero.darkseid_raid.defeat").withStyle(ChatFormatting.DARK_RED));
		return true;
	}

	/** Death hook (see {@link DarkseidRaidEvents}): a participant died -- start their re-entry delay. */
	public void onParticipantDied(ServerLevel level, ServerPlayer player) {
		DarkseidRoster.Member m = roster.get(player.getUUID());
		if (m == null || stage.ordinal() >= Stage.VICTORY.ordinal()) {
			return;
		}
		m.deaths++;
		m.alive = false;
		m.inside = false;
		m.lockoutUntil = level.getGameTime() + DarkseidConfig.raid().reentryDelaySeconds * 20L;
		participants().recordDeath(player.getUUID());
		tellAll(level, Component.translatable("message.projecthero.darkseid_raid.fallen", player.getDisplayName(),
				DarkseidConfig.raid().reentryDelaySeconds).withStyle(ChatFormatting.RED));
		checkDefeat(level);
	}

	// ================================================================ preparation / waves

	private void tickPreparation(ServerLevel level) {
		int length = DarkseidConfig.raid().preparationSeconds * 20;
		Vec3 c = Vec3.atBottomCenterOf(center());
		// the sky splitting open: sparks and distant Boom Tube flashes around the arena
		for (int i = 0; i < 3; i++) {
			Vec3 p = randomArenaPoint(level, 0.8).add(0, 3 + level.random.nextDouble() * 6, 0);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 4, 0.5, 0.5, 0.5, 0.3);
		}
		if (stageTicks % 60 == 0) {
			level.playSound(null, center(), DarkseidSounds.BOOM_TUBE, SoundSource.HOSTILE, 3.0f, 1.3f);
		}
		if (stageTicks >= length) {
			startWave(level, 1);
		} else if (stageTicks == length / 2) {
			BoomTubeEntity.open(level, c.add(0, 8, 0), BoomTubeEntity.Kind.FLASH, 4.0f, 20, id());
		}
	}

	private void startWave(ServerLevel level, int n) {
		DarkseidConfig.Raid cfg = DarkseidConfig.raid();
		setStage(n == 1 ? Stage.INVASION_WAVE_1 : n == 2 ? Stage.INVASION_WAVE_2 : Stage.INVASION_WAVE_3);
		waveCleared = false;
		lastAliveCount = -1;
		unchangedTicks = 0;
		double scale = 1.0 + cfg.waveScalingPerExtraPlayer * Math.max(0, roster.size() - 1);
		List<ParademonEntity.Variant> ground = new ArrayList<>();
		List<ParademonEntity.Variant> air = new ArrayList<>();
		switch (n) {
			case 1 -> addN(ground, ParademonEntity.Variant.STANDARD, cfg.wave1Standard, scale);
			case 2 -> {
				addN(ground, ParademonEntity.Variant.STANDARD, cfg.wave2Standard, scale);
				addN(air, ParademonEntity.Variant.RANGED, cfg.wave2Ranged, scale);
			}
			default -> {
				addN(ground, ParademonEntity.Variant.ELITE, cfg.wave3Elite, scale);
				addN(ground, ParademonEntity.Variant.BRUTE, cfg.wave3Brute, scale);
				addN(ground, ParademonEntity.Variant.STANDARD, cfg.wave3Standard, scale);
				addN(air, ParademonEntity.Variant.RANGED, cfg.wave3Ranged, scale);
			}
		}
		waveTotal = Math.max(1, ground.size() + air.size());
		int groundTubes = n + 1;
		int airTubes = air.isEmpty() ? 0 : 1 + (n == 3 ? 1 : 0);
		distribute(level, ground, groundTubes, n == 3 ? BoomTubeEntity.Kind.ELITE : BoomTubeEntity.Kind.MELEE, false);
		distribute(level, air, airTubes, BoomTubeEntity.Kind.RANGED, true);

		announce(level, Component.translatable("title.projecthero.darkseid_raid.wave", n).withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
				Component.translatable("title.projecthero.darkseid_raid.wave" + n + ".sub").withStyle(ChatFormatting.GOLD));
		level.playSound(null, center(), net.minecraft.sounds.SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 4.0f, 0.7f);
	}

	private static void addN(List<ParademonEntity.Variant> list, ParademonEntity.Variant v, int base, double scale) {
		int n = (int) Math.round(base * scale);
		for (int i = 0; i < n; i++) {
			list.add(v);
		}
	}

	/** Split {@code mobs} across {@code count} new Boom Tubes placed around the arena. */
	private void distribute(ServerLevel level, List<ParademonEntity.Variant> mobs, int count, BoomTubeEntity.Kind kind, boolean inAir) {
		if (mobs.isEmpty() || count <= 0) {
			return;
		}
		java.util.Collections.shuffle(mobs, new java.util.Random(level.random.nextLong()));
		List<Tube> made = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			Vec3 pos = tubeSite(level, inAir);
			if (pos == null) {
				continue;
			}
			Tube t = new Tube();
			t.pos = pos;
			t.kind = kind;
			t.openAt = level.getGameTime() + 30; // the tube crackles open before anything comes through
			made.add(t);
		}
		if (made.isEmpty()) {
			// nowhere legal around the arena (tiny island, unloaded ring) -- use the centre rather than lose the wave
			Tube t = new Tube();
			t.pos = Vec3.atBottomCenterOf(center()).add(0, inAir ? 6 : 1.5, 0);
			t.kind = kind;
			t.openAt = level.getGameTime() + 30;
			made.add(t);
		}
		for (int i = 0; i < mobs.size(); i++) {
			made.get(i % made.size()).queue.add(mobs.get(i));
		}
		for (Tube t : made) {
			BoomTubeEntity tube = BoomTubeEntity.open(level, t.pos, t.kind, 3.2f, 40 + t.queue.size() * 12, id());
			t.entityId = tube.getUUID();
			tubes.add(t);
		}
	}

	private Vec3 tubeSite(ServerLevel level, boolean inAir) {
		BlockPos p = EventSpawns.findSpawn(level, center(), combatTargets, level.random);
		if (p == null) {
			return null;
		}
		return Vec3.atBottomCenterOf(p).add(0, inAir ? 6.0 : 1.6, 0);
	}

	private void tickWave(ServerLevel level, int dt) {
		int n = stage.waveNumber();
		if (waveCleared) {
			breakTicks -= dt;
			if (breakTicks <= 0) {
				if (n >= 3) {
					beginEntrance(level);
				} else {
					startWave(level, n + 1);
				}
			}
			return;
		}
		int alive = enemiesAlive(level);
		if (alive == 0 && enemiesQueued() == 0 && stageTicks > 40) {
			waveCleared = true;
			breakTicks = n >= 3 ? 60 : DarkseidConfig.raid().betweenWaveSeconds * 20;
			tellAll(level, Component.translatable("message.projecthero.darkseid_raid.wave_cleared", n).withStyle(ChatFormatting.GREEN));
			level.playSound(null, center(), net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 0.8f);
			return;
		}
		trackStall(level, alive, dt);
	}

	/** Softlock guard: a wave whose count stops moving has its stragglers pulled back in, or released. */
	private void trackStall(ServerLevel level, int alive, int dt) {
		if (alive != lastAliveCount || enemiesQueued() > 0) {
			lastAliveCount = alive;
			unchangedTicks = 0;
			return;
		}
		unchangedTicks += dt;
		if (unchangedTicks < DarkseidConfig.raid().waveStallSeconds * 20) {
			return;
		}
		unchangedTicks = 0;
		int moved = 0;
		List<Mob> stuck = new ArrayList<>();
		for (Mob mob : liveOwnedMobs(level)) {
			if (mob instanceof ParademonEntity) {
				stuck.add(mob);
			}
		}
		for (Mob mob : stuck) {
			BlockPos pos = EventSpawns.findSpawn(level, center(), combatTargets, level.random);
			if (pos != null) {
				mob.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
				mob.getNavigation().stop();
				moved++;
			}
		}
		if (moved == 0) {
			for (Mob mob : stuck) {
				disown(mob.getUUID());
				mob.discard();
			}
		}
	}

	// ================================================================ Boom Tubes

	private void tickTubes(ServerLevel level) {
		if (tubes.isEmpty()) {
			return;
		}
		long now = level.getGameTime();
		int alive = enemiesAlive(level);
		int cap = enemyCap();
		Iterator<Tube> it = tubes.iterator();
		while (it.hasNext()) {
			Tube t = it.next();
			if (t.queue.isEmpty()) {
				it.remove();
				continue;
			}
			if (now < t.openAt || alive >= cap || !level.isLoaded(BlockPos.containing(t.pos))) {
				continue;
			}
			// the visual may have closed while the cap held the queue back -- reopen it
			if (t.entityId == null || level.getEntity(t.entityId) == null) {
				BoomTubeEntity tube = BoomTubeEntity.open(level, t.pos, t.kind, 3.2f, 30 + t.queue.size() * 12, id());
				t.entityId = tube.getUUID();
			}
			ParademonEntity.Variant v = t.queue.remove(0);
			if (spawnParademon(level, t.pos, v) != null) {
				alive++;
			}
		}
	}

	private ParademonEntity spawnParademon(ServerLevel level, Vec3 at, ParademonEntity.Variant variant) {
		ParademonEntity mob = DarkseidEntityTypes.PARADEMON.create(level);
		if (mob == null) {
			return null;
		}
		mob.setup(variant, id());
		double y = variant == ParademonEntity.Variant.RANGED ? at.y - 0.8 : at.y - 1.5;
		mob.moveTo(at.x + (level.random.nextDouble() - 0.5), y, at.z + (level.random.nextDouble() - 0.5),
				level.random.nextFloat() * 360.0f, 0.0f);
		mob.restrictTo(center(), (int) radius());
		own(mob);
		level.addFreshEntity(mob);
		level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 10, 0.4, 0.4, 0.4, 0.1);
		return mob;
	}

	/** Darkseid's Boom Tube Reinforcements: {@code count} tubes, their kind weighted by phase, within the cap. */
	public void requestReinforcements(ServerLevel level, int count) {
		int room = enemyCap() - enemiesAlive(level) - enemiesQueued();
		if (room <= 0) {
			return;
		}
		int phase = stage == Stage.DARKSEID_PHASE_3 ? 3 : stage == Stage.DARKSEID_PHASE_2 ? 2 : 1;
		for (int i = 0; i < count && room > 0; i++) {
			double roll = level.random.nextDouble();
			double melee = phase == 1 ? 0.6 : phase == 2 ? 0.4 : 0.3;
			double ranged = phase == 1 ? 0.3 : 0.35;
			BoomTubeEntity.Kind kind;
			List<ParademonEntity.Variant> mobs = new ArrayList<>();
			int n = Math.min(room, 3 + (enrageLevel > 0 ? 1 : 0));
			if (roll < melee) {
				kind = BoomTubeEntity.Kind.MELEE;
				addN(mobs, ParademonEntity.Variant.STANDARD, n, 1.0);
			} else if (roll < melee + ranged) {
				kind = BoomTubeEntity.Kind.RANGED;
				addN(mobs, ParademonEntity.Variant.RANGED, n, 1.0);
			} else {
				kind = BoomTubeEntity.Kind.ELITE;
				addN(mobs, ParademonEntity.Variant.ELITE, Math.max(1, n - 1), 1.0);
				if (phase >= 3) {
					mobs.add(ParademonEntity.Variant.BRUTE);
				}
			}
			room -= mobs.size();
			distribute(level, mobs, 1, kind, kind == BoomTubeEntity.Kind.RANGED);
		}
		tellAll(level, Component.translatable("message.projecthero.darkseid_raid.reinforcements").withStyle(ChatFormatting.RED));
	}

	// ================================================================ entrance

	private static final int ENTRANCE_TUBE_AT = 40;
	private static final int ENTRANCE_SPAWN_AT = 60;
	private static final int ENTRANCE_BOXES_AT = 150;

	private void beginEntrance(ServerLevel level) {
		setStage(Stage.DARKSEID_ENTRANCE);
		Vec3 c = Vec3.atBottomCenterOf(center());
		level.playSound(null, center(), DarkseidSounds.ENTRANCE, SoundSource.HOSTILE, 5.0f, 0.45f);
		for (int i = 0; i < 4; i++) {
			strikeVisualLightning(level, randomArenaPoint(level, 0.9));
		}
		DarkseidFx.shake(level, c, radius() + 32, 0.6f, 40);
		tellAll(level, Component.translatable("message.projecthero.darkseid_raid.darkness").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
	}

	private void tickEntrance(ServerLevel level) {
		Vec3 spawn = darkseidSpawnPoint(level);
		// red embers everywhere as the arena darkens
		for (int i = 0; i < 4; i++) {
			Vec3 p = randomArenaPoint(level, 0.9).add(0, level.random.nextDouble() * 4, 0);
			level.sendParticles(RED, p.x, p.y, p.z, 2, 0.3, 0.3, 0.3, 0.0);
		}
		if (crossed(ENTRANCE_TUBE_AT)) {
			BoomTubeEntity.open(level, spawn.add(0, 2.6, 0), BoomTubeEntity.Kind.ENTRANCE, 6.5f, 180, id());
			strikeVisualLightning(level, spawn);
			DarkseidFx.shake(level, spawn, radius() + 32, 1.0f, 30);
		}
		if (crossed(ENTRANCE_SPAWN_AT) && darkseid(level) == null) {
			spawnDarkseid(level, spawn, 1.0f, true);
		}
		if (crossed(ENTRANCE_BOXES_AT)) {
			spawnMotherBoxes(level);
		}
		if (stageTicks >= DarkseidConfig.raid().entranceSeconds * 20) {
			if (darkseid(level) == null) {
				spawnDarkseid(level, spawn, 1.0f, false);
			}
			if (boxes.isEmpty()) {
				spawnMotherBoxes(level);
			}
			arrivedAtAge = ageTicks();
			setStage(Stage.MOTHER_BOX_PHASE);
		}
	}

	/** True on the one raid tick at which {@code stageTicks} passes {@code mark}. */
	private boolean crossed(int mark) {
		int dt = Math.max(1, EventConfig.framework().tickIntervalTicks);
		return stageTicks >= mark && stageTicks - dt < mark;
	}

	private Vec3 darkseidSpawnPoint(ServerLevel level) {
		BlockPos c = center();
		int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ());
		int y = Math.abs(surface - c.getY()) <= 20 ? surface : c.getY();
		return new Vec3(c.getX() + 0.5, y, c.getZ() + 0.5);
	}

	private DarkseidEntity spawnDarkseid(ServerLevel level, Vec3 at, float healthFraction, boolean cinematic) {
		DarkseidEntity d = DarkseidEntityTypes.DARKSEID.create(level);
		if (d == null) {
			return null;
		}
		d.bindToRaid(id());
		d.moveTo(at.x, at.y, at.z, level.random.nextFloat() * 360.0f, 0.0f);
		if (!level.noCollision(d, d.getBoundingBox())) {
			BlockPos alt = EventSpawns.findSpawn(level, center(), List.of(), level.random);
			if (alt != null) {
				d.moveTo(alt.getX() + 0.5, alt.getY(), alt.getZ() + 0.5, 0.0f, 0.0f);
			}
		}
		d.scaleHealthTo(DarkseidConfig.healthFor(Math.max(1, roster.size())));
		d.setHealth(d.getMaxHealth() * Math.max(0.05f, healthFraction));
		boolean shielded = stage == Stage.DARKSEID_ENTRANCE || stage == Stage.MOTHER_BOX_PHASE;
		d.setPhaseImmediate(shielded ? 0 : stage == Stage.DARKSEID_PHASE_3 ? 3 : stage == Stage.DARKSEID_PHASE_2 ? 2 : 1);
		d.setShield(shielded ? 1.0f : currentShield(level));
		own(d);
		level.addFreshEntity(d);
		darkseidId = d.getUUID();
		darkseidMissingTicks = 0;
		if (cinematic) {
			d.beginEntrance();
			level.playSound(null, d.blockPosition(), DarkseidSounds.ENTRANCE, SoundSource.HOSTILE, 5.0f, 0.55f);
			level.playSound(null, d.blockPosition(), DarkseidSounds.PHASE, SoundSource.HOSTILE, 5.0f, 0.5f);
			DarkseidFx.shake(level, at, radius() + 32, 1.6f, 40);
			DarkseidFx.zoom(level, at, radius() + 32, 0.2f, 40);
			level.sendParticles(ParticleTypes.FLASH, at.x, at.y + 2, at.z, 2, 0.5, 0.5, 0.5, 0);
			announce(level, Component.translatable("title.projecthero.darkseid_raid.darkseid").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
					Component.translatable("title.projecthero.darkseid_raid.darkseid.sub").withStyle(ChatFormatting.GRAY));
		}
		return d;
	}

	// ================================================================ Mother Boxes

	private void spawnMotherBoxes(ServerLevel level) {
		if (!boxes.isEmpty()) {
			return;
		}
		BlockPos c = center();
		double dist = DarkseidConfig.motherBoxes().distanceFromCenter;
		int[][] dirs = { { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 } };
		for (int i = 0; i < 4; i++) {
			int x = c.getX() + (int) Math.round(dirs[i][0] * dist);
			int z = c.getZ() + (int) Math.round(dirs[i][1] * dist);
			int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			int y = Math.abs(surface - c.getY()) <= 20 ? surface : c.getY();
			Vec3 pos = new Vec3(x + 0.5, y + 1.2, z + 0.5);
			MotherBoxEntity box = MotherBoxEntity.spawn(level, pos, id(), i);
			Box b = new Box();
			b.id = box.getUUID();
			b.pos = pos;
			boxes.add(b);
			strikeVisualLightning(level, pos);
		}
		announce(level, Component.translatable("title.projecthero.darkseid_raid.mother_boxes").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
				Component.translatable("title.projecthero.darkseid_raid.mother_boxes.sub").withStyle(ChatFormatting.AQUA));
		tellAll(level, Component.translatable("message.projecthero.darkseid_raid.mother_box_help").withStyle(ChatFormatting.AQUA));
	}

	private int activeBoxes() {
		int n = 0;
		for (Box b : boxes) {
			if (!b.disabled) {
				n++;
			}
		}
		return n;
	}

	private float currentShield(ServerLevel level) {
		return boxes.isEmpty() ? 0.0f : activeBoxes() / 4.0f;
	}

	/** Called by the box itself the moment a channel completes. */
	public void onMotherBoxDisabled(ServerLevel level, MotherBoxEntity entity, ServerPlayer by) {
		for (Box b : boxes) {
			if (b.id.equals(entity.getUUID())) {
				b.disabled = true;
				b.neglectTicks = 0;
			}
		}
		float shield = currentShield(level);
		DarkseidEntity d = darkseid(level);
		if (d != null) {
			d.setShield(shield);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, d.getX(), d.getY() + d.getBbHeight() * 0.5, d.getZ(), 30,
					d.getBbWidth() * 0.6, d.getBbHeight() * 0.4, d.getBbWidth() * 0.6, 0.3);
			level.playSound(null, d.blockPosition(), net.minecraft.sounds.SoundEvents.SHIELD_BREAK, SoundSource.HOSTILE, 3.0f, 0.5f);
		}
		DarkseidFx.shake(level, entity.position(), 48.0, 0.8f, 12);
		announce(level, Component.translatable("title.projecthero.darkseid_raid.box_disabled").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD),
				Component.translatable("title.projecthero.darkseid_raid.box_disabled.sub", Math.round(shield * 100)).withStyle(ChatFormatting.LIGHT_PURPLE));
		if (by != null) {
			tellAll(level, Component.translatable("message.projecthero.darkseid_raid.box_disabled_by", by.getDisplayName(), 4 - activeBoxes())
					.withStyle(ChatFormatting.AQUA));
		}
		if (stage == Stage.MOTHER_BOX_PHASE && activeBoxes() == 0) {
			shieldFallen(level);
		}
	}

	private void shieldFallen(ServerLevel level) {
		setStage(Stage.DARKSEID_PHASE_1);
		DarkseidEntity d = darkseid(level);
		if (d != null) {
			d.beginPhaseTransition(level, 1);
			level.playSound(null, d.blockPosition(), DarkseidSounds.PHASE, SoundSource.HOSTILE, 5.0f, 0.6f);
		}
		announce(level, Component.translatable("title.projecthero.darkseid_raid.shield_fallen").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
				Component.translatable("title.projecthero.darkseid_raid.shield_fallen.sub").withStyle(ChatFormatting.YELLOW));
	}

	/** Neglect timers and overloads for every active box, in every stage that has boxes. */
	private void tickBoxes(ServerLevel level, int dt) {
		if (boxes.isEmpty() || stage.ordinal() >= Stage.VICTORY.ordinal()) {
			return;
		}
		DarkseidConfig.MotherBoxes cfg = DarkseidConfig.motherBoxes();
		int overloadTicks = Math.max(200, cfg.motherBoxOverloadTime * 20);
		for (Box b : boxes) {
			if (b.disabled) {
				continue;
			}
			if (!(level.getEntity(b.id) instanceof MotherBoxEntity box)) {
				continue; // its chunk is not loaded -- nobody is near it, so its clock waits too
			}
			if (!box.isActive()) {
				b.disabled = true;
				continue;
			}
			if (box.isBeingChanneled() || box.progress() > 0.0f) {
				b.neglectTicks = 0;
			} else {
				b.neglectTicks += dt;
			}
			box.setOverloadWarning(b.neglectTicks / (float) overloadTicks);
			if (b.neglectTicks >= overloadTicks) {
				b.neglectTicks = 0;
				overload(level, box);
			}
		}
		DarkseidEntity d = darkseid(level);
		if (d != null && stage != Stage.DARKSEID_ENTRANCE) {
			d.setShield(currentShield(level));
		}
	}

	/**
	 * MOTHER BOX OVERLOAD: the punishment for ignoring a box -- never a wipe. Darkseid heals, the box detonates
	 * (a knock-back blast), a Boom Tube disgorges Parademons beside it, and the ground there burns for a while.
	 */
	private void overload(ServerLevel level, MotherBoxEntity box) {
		DarkseidConfig.MotherBoxes cfg = DarkseidConfig.motherBoxes();
		overloadHappened = true;
		Vec3 p = box.position().add(0, 0.5, 0);
		DarkseidEntity d = darkseid(level);
		if (d != null) {
			d.heal((float) (d.getMaxHealth() * cfg.motherBoxHealAmount));
			level.sendParticles(ParticleTypes.HEART, d.getX(), d.getY() + d.getBbHeight(), d.getZ(), 6, 0.6, 0.3, 0.6, 0.0);
			DarkseidFx.line(level, RED, p, d.position().add(0, d.getBbHeight() * 0.6, 0), 1.0, 40);
		}
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, p.x, p.y, p.z, 1, 0, 0, 0, 0);
		level.sendParticles(RED, p.x, p.y, p.z, 60, cfg.overloadExplosionRadius * 0.4, 1.0, cfg.overloadExplosionRadius * 0.4, 0.0);
		level.playSound(null, p.x, p.y, p.z, DarkseidSounds.MOTHER_BOX_OVERLOAD, SoundSource.HOSTILE, 4.0f, 0.7f);
		level.playSound(null, p.x, p.y, p.z, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3.0f, 0.6f);
		DarkseidFx.shake(level, p, 40.0, 1.2f, 16);
		double r = cfg.overloadExplosionRadius;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(p, p).inflate(r), DarkseidDamage::isValidVictim)) {
			double dist = e.position().distanceTo(p);
			if (dist > r) {
				continue;
			}
			float dmg = (float) (cfg.overloadExplosionDamage * (1.0 - 0.5 * dist / r));
			if (e.hurt(DarkseidDamage.omega(level, d, d), dmg)) {
				DarkseidDamage.knockAway(e, p, 1.6, 0.6);
			}
		}
		List<ParademonEntity.Variant> mobs = new ArrayList<>();
		int room = Math.max(0, enemyCap() - enemiesAlive(level) - enemiesQueued());
		for (int i = 0; i < Math.min(room, cfg.overloadParademons); i++) {
			mobs.add(i == 0 && stage.isFight() ? ParademonEntity.Variant.ELITE : ParademonEntity.Variant.STANDARD);
		}
		if (!mobs.isEmpty()) {
			Tube t = new Tube();
			t.pos = p.add(0, 2.5, 0);
			t.kind = BoomTubeEntity.Kind.OVERLOAD;
			t.openAt = level.getGameTime() + 20;
			t.queue.addAll(mobs);
			t.entityId = BoomTubeEntity.open(level, t.pos, t.kind, 3.5f, 40 + mobs.size() * 12, id()).getUUID();
			tubes.add(t);
		}
		hazards.add(new Hazard(new Vec3(p.x, p.y - 1.2, p.z), cfg.dangerZoneRadius, level.getGameTime() + cfg.dangerZoneSeconds * 20L));
		announce(level, Component.translatable("title.projecthero.darkseid_raid.overload").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
				Component.translatable("title.projecthero.darkseid_raid.overload.sub").withStyle(ChatFormatting.GOLD));
	}

	private void tickHazards(ServerLevel level, int dt) {
		if (hazards.isEmpty()) {
			return;
		}
		long now = level.getGameTime();
		DarkseidEntity d = darkseid(level);
		float dmg = DarkseidConfig.motherBoxes().dangerZoneDamagePerSecond * dt / 20.0f;
		Iterator<Hazard> it = hazards.iterator();
		while (it.hasNext()) {
			Hazard h = it.next();
			if (now >= h.until()) {
				it.remove();
				continue;
			}
			DarkseidFx.ring(level, RED, h.pos().add(0, 0.2, 0), h.radius(), 20);
			level.sendParticles(ParticleTypes.LAVA, h.pos().x, h.pos().y + 0.3, h.pos().z, 3, h.radius() * 0.5, 0.1, h.radius() * 0.5, 0.0);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(h.pos(), h.pos()).inflate(h.radius(), 3.0, h.radius()),
					DarkseidDamage::isValidVictim)) {
				double dx = e.getX() - h.pos().x;
				double dz = e.getZ() - h.pos().z;
				if (dx * dx + dz * dz <= h.radius() * h.radius() && e.getY() - h.pos().y < 3.0) {
					e.hurt(DarkseidDamage.omega(level, d, d), dmg);
				}
			}
		}
	}

	// ================================================================ the fight

	private void tickDarkseid(ServerLevel level, int dt) {
		DarkseidEntity d = darkseid(level);
		if (d == null) {
			// unloaded, or removed by something outside the raid: if players are here and he is not, bring him back
			if (!combatTargets.isEmpty()) {
				darkseidMissingTicks += dt;
				if (darkseidMissingTicks >= 200) {
					ProjectHeroMod.LOGGER.info("[ProjectHero] Darkseid Raid {}: Darkseid missing -- returning him", id());
					d = spawnDarkseid(level, darkseidSpawnPoint(level), lastDarkseidHealth, false);
				}
			}
			if (d == null) {
				return;
			}
		}
		darkseidMissingTicks = 0;
		if (!d.isAlive()) {
			return;
		}
		float f = d.getHealth() / d.getMaxHealth();
		lastDarkseidHealth = f;

		// roster seals at half health: late arrivals can still fight, but are not official participants
		if (!rosterSealed && f <= DarkseidConfig.raid().rosterSealHealthFraction) {
			rosterSealed = true;
			tellAll(level, Component.translatable("message.projecthero.darkseid_raid.sealed").withStyle(ChatFormatting.GOLD));
		}
		// roster growth before the seal raises his health (keeping the fraction)
		double want = DarkseidConfig.healthFor(Math.max(1, roster.size()));
		if (!rosterSealed && Math.abs(d.getMaxHealth() - want) > 1.0) {
			d.scaleHealthTo(want);
		}

		if (stage == Stage.DARKSEID_PHASE_1 && f <= 0.60f && !d.isTransitioning()) {
			setStage(Stage.DARKSEID_PHASE_2);
			d.beginPhaseTransition(level, 2);
			announce(level, Component.translatable("title.projecthero.darkseid_raid.phase2").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
					Component.translatable("title.projecthero.darkseid_raid.phase2.sub").withStyle(ChatFormatting.GOLD));
			DarkseidFx.shake(level, d.position(), radius() + 32, 1.2f, 30);
		} else if (stage == Stage.DARKSEID_PHASE_2 && f <= 0.25f && !d.isTransitioning()) {
			setStage(Stage.DARKSEID_PHASE_3);
			d.beginPhaseTransition(level, 3);
			announce(level, Component.translatable("title.projecthero.darkseid_raid.phase3").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
					Component.translatable("title.projecthero.darkseid_raid.phase3.sub").withStyle(ChatFormatting.RED));
			DarkseidFx.shake(level, d.position(), radius() + 32, 2.0f, 40);
			startMusic(level);
		}
		if (stage == Stage.DARKSEID_PHASE_3) {
			rageAmbience(level);
		}
		tickEnrage(level, d, dt);
	}

	/** Omega Rage: the arena itself cracks and burns. */
	private void rageAmbience(ServerLevel level) {
		for (int i = 0; i < 3; i++) {
			Vec3 p = randomArenaPoint(level, 0.95);
			level.sendParticles(ParticleTypes.LAVA, p.x, p.y + 0.2, p.z, 2, 0.4, 0.0, 0.4, 0.0);
			level.sendParticles(RED, p.x, p.y + 0.4, p.z, 3, 0.6, 0.3, 0.6, 0.0);
		}
		if (stageTicks % 160 == 0) {
			strikeVisualLightning(level, randomArenaPoint(level, 0.9));
			level.playSound(null, center(), net.minecraft.sounds.SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 4.0f, 0.5f);
		}
	}

	/**
	 * The soft enrage: the raid's timer never wipes anyone, it just makes him stronger step by step -- more damage,
	 * shorter cooldowns, faster, more reinforcements, and Mother Boxes switching themselves back on (each one that
	 * does takes 25% off the damage he receives until it is disabled again).
	 */
	private void tickEnrage(ServerLevel level, DarkseidEntity d, int dt) {
		if (arrivedAtAge < 0) {
			arrivedAtAge = ageTicks();
		}
		DarkseidConfig.Raid cfg = DarkseidConfig.raid();
		long since = ageTicks() - arrivedAtAge;
		long enrageAt = cfg.raidSoftEnrageTime * 20L;
		if (since < enrageAt) {
			return;
		}
		int wantLevel = 1 + (int) ((since - enrageAt) / Math.max(200L, cfg.softEnrageStepSeconds * 20L));
		if (wantLevel > enrageLevel) {
			enrageLevel = wantLevel;
			d.refreshEnrage();
			announce(level, Component.translatable("title.projecthero.darkseid_raid.enrage", enrageLevel).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
					Component.translatable("title.projecthero.darkseid_raid.enrage.sub").withStyle(ChatFormatting.RED));
			level.playSound(null, d.blockPosition(), DarkseidSounds.PHASE, SoundSource.HOSTILE, 5.0f, 0.4f);
		}
		reactivateTicks += dt;
		if (reactivateTicks >= cfg.enrageMotherBoxReactivateSeconds * 20) {
			reactivateTicks = 0;
			for (Box b : boxes) {
				if (b.disabled && level.getEntity(b.id) instanceof MotherBoxEntity box) {
					b.disabled = false;
					b.neglectTicks = 0;
					box.reactivate(level);
					tellAll(level, Component.translatable("message.projecthero.darkseid_raid.box_reactivated").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
					break;
				}
			}
		}
		enrageTubeTicks += dt;
		if (enrageTubeTicks >= 30 * 20) {
			enrageTubeTicks = 0;
			requestReinforcements(level, 1);
		}
	}

	// ================================================================ victory / defeat

	/** Darkseid's own {@code die()}: the arena calms at once, rewards follow when his death sequence ends. */
	public void onDarkseidDefeated(ServerLevel level, DarkseidEntity d, DamageSource source) {
		if (stage == Stage.VICTORY || stage == Stage.DEFEAT) {
			return;
		}
		setStage(Stage.VICTORY);
		lastDarkseidHealth = 0.0f;
		for (Mob mob : liveOwnedMobs(level)) {
			if (mob instanceof ParademonEntity) {
				// recalled through the Boom Tubes with their master
				level.sendParticles(ParticleTypes.END_ROD, mob.getX(), mob.getY() + 1, mob.getZ(), 8, 0.3, 0.6, 0.3, 0.05);
				disown(mob.getUUID());
				mob.discard();
			}
		}
		tubes.clear();
		hazards.clear();
		removeBoxes(level);
		stopMusic(level);
		tellAll(level, Component.translatable("message.projecthero.darkseid_raid.falling").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
	}

	private void tickVictory(ServerLevel level) {
		updateBars(level);
		pushSky(level);
		if (stageTicks >= DarkseidAnims.DEATH_TICKS + 30) {
			complete(level);
		}
	}

	@Override
	protected void onFinished(ServerLevel level, boolean success) {
		if (success) {
			announce(level, Component.translatable("title.projecthero.darkseid_raid.victory").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
					Component.translatable("title.projecthero.darkseid_raid.victory.sub").withStyle(ChatFormatting.YELLOW));
			level.playSound(null, center(), DarkseidSounds.VICTORY, SoundSource.PLAYERS, 3.0f, 1.0f);
			level.playSound(null, center(), net.minecraft.sounds.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 2.0f, 1.0f);
			if (!rewardsGranted) {
				rewardsGranted = true;
				DarkseidRaidRewards.grant(level, this);
			}
			EventSavedData.get(level).noteCompleted();
		} else {
			DarkseidEntity d = darkseid(level);
			if (d != null && d.isAlive()) {
				// he leaves the way he came
				BoomTubeEntity.open(level, d.position().add(0, d.getBbHeight() * 0.5, 0), BoomTubeEntity.Kind.EXIT, 5.0f, 60, null);
				level.sendParticles(ParticleTypes.END_ROD, d.getX(), d.getY() + 2, d.getZ(), 60, 1.0, 1.5, 1.0, 0.1);
			}
			announce(level, Component.translatable("title.projecthero.darkseid_raid.defeat").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
					Component.translatable("title.projecthero.darkseid_raid.defeat.sub").withStyle(ChatFormatting.GRAY));
			level.playSound(null, center(), DarkseidSounds.DEFEAT, SoundSource.HOSTILE, 4.0f, 0.5f);
		}
		cleanup(level);
		ProjectHeroMod.LOGGER.info("[ProjectHero] Darkseid Raid {} ended ({})", id(), success ? "victory" : "defeat");
	}

	@Override
	protected void onAborted(ServerLevel level) {
		cleanup(level);
		if (refundOnAbort && activatorId != null && stage.ordinal() < Stage.VICTORY.ordinal()) {
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(activatorId);
			if (p != null) {
				ItemStack beacon = new ItemStack(DarkseidItems.BOOM_TUBE_BEACON);
				if (!p.getInventory().add(beacon)) {
					p.drop(beacon, false);
				}
				p.sendSystemMessage(Component.translatable("message.projecthero.darkseid_raid.refunded").withStyle(ChatFormatting.GRAY));
			}
		}
	}

	@Override
	protected void onPaused(ServerLevel level) {
		super.onPaused(level);
		raidBar.clear(level);
		ZombieRaidNetworking.clearSky(level, center());
		stopMusic(level);
	}

	/**
	 * Tear down everything that is not an owned mob (the framework discards those itself right after this): boxes,
	 * tubes, bars, sky, music, Omega marks. Idempotent.
	 */
	private void cleanup(ServerLevel level) {
		removeBoxes(level);
		for (Tube t : tubes) {
			if (t.entityId != null && level.getEntity(t.entityId) instanceof BoomTubeEntity tube) {
				tube.discard();
			}
		}
		tubes.clear();
		hazards.clear();
		raidBar.clear(level);
		DarkseidEntity d = darkseid(level);
		if (d != null) {
			d.clearBossBar(level);
		}
		ZombieRaidNetworking.clearSky(level, center());
		stopMusic(level);
		for (DarkseidRoster.Member m : roster.all()) {
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(m.id);
			if (p != null) {
				MobEffectInstance glow = p.getEffect(MobEffects.GLOWING);
				if (glow != null && glow.isAmbient() && !glow.isVisible()) {
					p.removeEffect(MobEffects.GLOWING);
				}
			}
		}
	}

	private void removeBoxes(ServerLevel level) {
		for (Box b : boxes) {
			if (level.getEntity(b.id) instanceof MotherBoxEntity box) {
				level.sendParticles(ParticleTypes.SMOKE, box.getX(), box.getY() + 0.5, box.getZ(), 10, 0.3, 0.3, 0.3, 0.02);
				box.discard();
			}
		}
		boxes.clear();
	}

	// ================================================================ music

	private void startMusic(ServerLevel level) {
		if (musicPlaying) {
			return;
		}
		musicPlaying = true;
		for (ServerPlayer p : audience(level)) {
			p.connection.send(new ClientboundSoundEntityPacket(DarkseidSounds.holder(DarkseidSounds.RAID_MUSIC), SoundSource.RECORDS,
					p, 1.0f, 1.0f, level.random.nextLong()));
		}
	}

	private void stopMusic(ServerLevel level) {
		if (!musicPlaying) {
			return;
		}
		musicPlaying = false;
		ClientboundStopSoundPacket stop = new ClientboundStopSoundPacket(DarkseidSounds.RAID_MUSIC.getLocation(), SoundSource.RECORDS);
		for (ServerPlayer p : level.players()) {
			p.connection.send(stop);
		}
	}

	// ================================================================ HUD: bars, sky, messages

	private void updateBars(ServerLevel level) {
		Component name;
		float progress;
		BossEvent.BossBarColor color = BossEvent.BossBarColor.RED;
		DarkseidConfig.Raid cfg = DarkseidConfig.raid();
		switch (stage) {
			case PREPARATION -> {
				int length = cfg.preparationSeconds * 20;
				name = Component.translatable("bar.projecthero.darkseid_raid.preparation", Math.max(0, (length - stageTicks) / 20));
				progress = Math.min(1.0f, stageTicks / (float) Math.max(1, length));
			}
			case INVASION_WAVE_1, INVASION_WAVE_2, INVASION_WAVE_3 -> {
				int n = stage.waveNumber();
				if (waveCleared) {
					name = Component.translatable("bar.projecthero.darkseid_raid.wave_cleared", n, Math.max(0, breakTicks / 20));
					progress = 1.0f;
					color = BossEvent.BossBarColor.GREEN;
				} else {
					int remaining = enemiesAlive(level) + enemiesQueued();
					name = Component.translatable("bar.projecthero.darkseid_raid.wave", n, remaining);
					progress = Math.min(1.0f, remaining / (float) waveTotal);
				}
			}
			case DARKSEID_ENTRANCE -> {
				name = Component.translatable("bar.projecthero.darkseid_raid.entrance");
				progress = Math.min(1.0f, stageTicks / (float) Math.max(1, cfg.entranceSeconds * 20));
				color = BossEvent.BossBarColor.PURPLE;
			}
			case MOTHER_BOX_PHASE -> {
				float shield = currentShield(level);
				name = Component.translatable("bar.projecthero.darkseid_raid.mother_boxes", 4 - activeBoxes(), Math.round(shield * 100));
				progress = shield;
				color = BossEvent.BossBarColor.PURPLE;
			}
			case DARKSEID_PHASE_1, DARKSEID_PHASE_2, DARKSEID_PHASE_3 -> {
				int phase = stage == Stage.DARKSEID_PHASE_1 ? 1 : stage == Stage.DARKSEID_PHASE_2 ? 2 : 3;
				int enemies = enemiesAlive(level) + enemiesQueued();
				long since = arrivedAtAge < 0 ? 0 : ageTicks() - arrivedAtAge;
				long left = cfg.raidSoftEnrageTime * 20L - since;
				if (enrageLevel > 0) {
					name = Component.translatable("bar.projecthero.darkseid_raid.enraged", phase, enemies, enrageLevel);
					progress = 1.0f;
					color = BossEvent.BossBarColor.YELLOW;
				} else {
					long secs = Math.max(0, left / 20);
					name = Component.translatable("bar.projecthero.darkseid_raid.fight", phase, enemies,
							String.format("%d:%02d", secs / 60, secs % 60));
					progress = Math.max(0.0f, Math.min(1.0f, left / (float) Math.max(1, cfg.raidSoftEnrageTime * 20)));
					color = activeBoxes() > 0 ? BossEvent.BossBarColor.PINK : BossEvent.BossBarColor.RED;
				}
			}
			case VICTORY -> {
				name = Component.translatable("bar.projecthero.darkseid_raid.victory");
				progress = 1.0f;
				color = BossEvent.BossBarColor.GREEN;
			}
			default -> {
				return;
			}
		}
		raidBar.setColor(color);
		raidBar.update(level, center(), radius() + 32.0, name.copy().withStyle(ChatFormatting.GOLD), progress);
	}

	private void pushSky(ServerLevel level) {
		int color = switch (stage) {
			case DARKSEID_ENTRANCE, MOTHER_BOX_PHASE, DARKSEID_PHASE_1, VICTORY -> SKY_DARKSEID;
			case DARKSEID_PHASE_2 -> SKY_PHASE_2;
			case DARKSEID_PHASE_3 -> SKY_RAGE;
			default -> SKY_INVASION;
		};
		ZombieRaidNetworking.pushSky(level, center(), radius() + 16.0, color);
	}

	/** Online roster members, plus anyone else standing in the arena. */
	private List<ServerPlayer> audience(ServerLevel level) {
		List<ServerPlayer> out = new ArrayList<>();
		for (DarkseidRoster.Member m : roster.all()) {
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(m.id);
			if (p != null && p.level() == level) {
				out.add(p);
			}
		}
		BlockPos c = center();
		double r = radius() + 16.0;
		for (ServerPlayer p : level.players()) {
			if (!out.contains(p) && p.distanceToSqr(c.getX() + 0.5, p.getY(), c.getZ() + 0.5) <= r * r) {
				out.add(p);
			}
		}
		return out;
	}

	/** A big title + subtitle to everyone in the raid. */
	public void announce(ServerLevel level, Component title, Component subtitle) {
		for (ServerPlayer p : audience(level)) {
			p.connection.send(new ClientboundSetTitlesAnimationPacket(8, 50, 16));
			if (subtitle != null) {
				p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
			}
			p.connection.send(new ClientboundSetTitleTextPacket(title));
		}
	}

	/** A chat line to everyone in the raid. */
	public void tellAll(ServerLevel level, Component message) {
		for (ServerPlayer p : audience(level)) {
			p.sendSystemMessage(message);
		}
	}

	// ================================================================ helpers

	private Vec3 randomArenaPoint(ServerLevel level, double radiusFraction) {
		BlockPos c = center();
		double a = level.random.nextDouble() * Math.PI * 2.0;
		double r = Math.sqrt(level.random.nextDouble()) * radius() * radiusFraction;
		int x = Mth.floor(c.getX() + 0.5 + Math.cos(a) * r);
		int z = Mth.floor(c.getZ() + 0.5 + Math.sin(a) * r);
		int y = level.isLoaded(new BlockPos(x, c.getY(), z)) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) : c.getY();
		return new Vec3(x + 0.5, y, z + 0.5);
	}

	/** Thunder and a bolt of light with no fire and no damage -- vanilla's own visual-only lightning. */
	private static void strikeVisualLightning(ServerLevel level, Vec3 at) {
		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
		if (bolt != null) {
			bolt.moveTo(at.x, at.y, at.z);
			bolt.setVisualOnly(true);
			level.addFreshEntity(bolt);
		}
	}

	// ================================================================ debug (op commands)

	/** {@code /projecthero raid advancetimer darkseid}: skip to the next beat of the raid. */
	public String debugAdvance(ServerLevel level) {
		switch (stage) {
			case PREPARATION -> {
				startWave(level, 1);
				return "wave 1";
			}
			case INVASION_WAVE_1, INVASION_WAVE_2, INVASION_WAVE_3 -> {
				for (Mob mob : liveOwnedMobs(level)) {
					if (mob instanceof ParademonEntity) {
						disown(mob.getUUID());
						mob.discard();
					}
				}
				tubes.clear();
				waveCleared = true;
				breakTicks = 0;
				return "wave " + stage.waveNumber() + " cleared";
			}
			case DARKSEID_ENTRANCE -> {
				stageTicks = DarkseidConfig.raid().entranceSeconds * 20;
				tickEntrance(level);
				return "entrance skipped";
			}
			case MOTHER_BOX_PHASE -> {
				// through the normal path, so every message, the shield and the phase change all fire
				for (Box b : new ArrayList<>(boxes)) {
					if (!b.disabled && level.getEntity(b.id) instanceof MotherBoxEntity box) {
						box.forceDisable(level);
					}
				}
				if (stage == Stage.MOTHER_BOX_PHASE) {
					boxes.forEach(b -> b.disabled = true);
					shieldFallen(level);
				}
				return "shield broken";
			}
			case DARKSEID_PHASE_1, DARKSEID_PHASE_2, DARKSEID_PHASE_3 -> {
				DarkseidEntity d = darkseid(level);
				if (d == null) {
					return "Darkseid not loaded";
				}
				float target = stage == Stage.DARKSEID_PHASE_1 ? 0.59f : stage == Stage.DARKSEID_PHASE_2 ? 0.24f : 0.0f;
				if (target <= 0.0f) {
					d.hurt(level.damageSources().genericKill(), Float.MAX_VALUE);
					return "Darkseid defeated";
				}
				d.setHealth(d.getMaxHealth() * target);
				return "Darkseid set to " + Math.round(target * 100) + "%";
			}
			default -> {
				return "nothing to advance";
			}
		}
	}

	/** Force the soft enrage now (tests / command). */
	public void debugEnrage() {
		arrivedAtAge = ageTicks() - DarkseidConfig.raid().raidSoftEnrageTime * 20L - 1;
	}

	/** A one-line state summary for {@code /projecthero raid status darkseid}. */
	public String describe(ServerLevel level) {
		DarkseidEntity d = darkseid(level);
		return stage.name() + " | roster " + roster.size() + (rosterSealed ? " (sealed)" : "") + " | boxes " + activeBoxes() + "/"
				+ boxes.size() + " | enemies " + enemiesAlive(level) + "+" + enemiesQueued() + " | Darkseid "
				+ (d == null ? "-" : Math.round(d.getHealth()) + "/" + Math.round(d.getMaxHealth())) + " | enrage " + enrageLevel
				+ (overloadHappened ? " | overloaded" : "");
	}

	// ================================================================ persistence

	@Override
	protected void saveExtra(CompoundTag tag) {
		tag.putString("Stage", stage.name());
		tag.putInt("StageTicks", stageTicks);
		tag.putBoolean("WaveCleared", waveCleared);
		tag.putInt("BreakTicks", breakTicks);
		tag.putInt("WaveTotal", waveTotal);
		if (activatorId != null) {
			tag.putUUID("Activator", activatorId);
		}
		if (darkseidId != null) {
			tag.putUUID("Darkseid", darkseidId);
		}
		tag.putFloat("LastHealth", lastDarkseidHealth);
		tag.putBoolean("Overloaded", overloadHappened);
		tag.putBoolean("Sealed", rosterSealed);
		tag.putInt("Enrage", enrageLevel);
		tag.putLong("ArrivedAt", arrivedAtAge);
		tag.putInt("Reactivate", reactivateTicks);
		tag.putBoolean("Rewarded", rewardsGranted);
		ListTag boxList = new ListTag();
		for (Box b : boxes) {
			CompoundTag t = new CompoundTag();
			t.putUUID("Id", b.id);
			t.putDouble("X", b.pos.x);
			t.putDouble("Y", b.pos.y);
			t.putDouble("Z", b.pos.z);
			t.putInt("Neglect", b.neglectTicks);
			t.putBoolean("Disabled", b.disabled);
			boxList.add(t);
		}
		tag.put("Boxes", boxList);
		ListTag tubeList = new ListTag();
		for (Tube tube : tubes) {
			CompoundTag t = new CompoundTag();
			t.putDouble("X", tube.pos.x);
			t.putDouble("Y", tube.pos.y);
			t.putDouble("Z", tube.pos.z);
			t.putString("Kind", tube.kind.name());
			t.putLong("OpenAt", tube.openAt);
			byte[] q = new byte[tube.queue.size()];
			for (int i = 0; i < q.length; i++) {
				q[i] = (byte) tube.queue.get(i).ordinal();
			}
			t.putByteArray("Queue", q);
			tubeList.add(t);
		}
		tag.put("Tubes", tubeList);
		roster.save(tag);
	}

	@Override
	protected void loadExtra(CompoundTag tag) {
		try {
			stage = Stage.valueOf(tag.getString("Stage"));
		} catch (IllegalArgumentException e) {
			stage = Stage.INACTIVE;
		}
		stageTicks = tag.getInt("StageTicks");
		waveCleared = tag.getBoolean("WaveCleared");
		breakTicks = tag.getInt("BreakTicks");
		waveTotal = Math.max(1, tag.getInt("WaveTotal"));
		activatorId = tag.hasUUID("Activator") ? tag.getUUID("Activator") : null;
		darkseidId = tag.hasUUID("Darkseid") ? tag.getUUID("Darkseid") : null;
		lastDarkseidHealth = tag.contains("LastHealth") ? tag.getFloat("LastHealth") : 1.0f;
		overloadHappened = tag.getBoolean("Overloaded");
		rosterSealed = tag.getBoolean("Sealed");
		enrageLevel = tag.getInt("Enrage");
		arrivedAtAge = tag.contains("ArrivedAt") ? tag.getLong("ArrivedAt") : -1;
		reactivateTicks = tag.getInt("Reactivate");
		rewardsGranted = tag.getBoolean("Rewarded");
		boxes.clear();
		ListTag boxList = tag.getList("Boxes", Tag.TAG_COMPOUND);
		for (int i = 0; i < boxList.size(); i++) {
			CompoundTag t = boxList.getCompound(i);
			Box b = new Box();
			b.id = t.getUUID("Id");
			b.pos = new Vec3(t.getDouble("X"), t.getDouble("Y"), t.getDouble("Z"));
			b.neglectTicks = t.getInt("Neglect");
			b.disabled = t.getBoolean("Disabled");
			boxes.add(b);
		}
		tubes.clear();
		ListTag tubeList = tag.getList("Tubes", Tag.TAG_COMPOUND);
		for (int i = 0; i < tubeList.size(); i++) {
			CompoundTag t = tubeList.getCompound(i);
			Tube tube = new Tube();
			tube.pos = new Vec3(t.getDouble("X"), t.getDouble("Y"), t.getDouble("Z"));
			try {
				tube.kind = BoomTubeEntity.Kind.valueOf(t.getString("Kind"));
			} catch (IllegalArgumentException e) {
				tube.kind = BoomTubeEntity.Kind.MELEE;
			}
			tube.openAt = t.getLong("OpenAt");
			for (byte b : t.getByteArray("Queue")) {
				tube.queue.add(ParademonEntity.Variant.byId(b));
			}
			tubes.add(tube);
		}
		roster.load(tag);
	}
}
