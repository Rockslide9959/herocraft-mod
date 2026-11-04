package com.projecthero.mod.galactus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.event.EventBossBar;
import com.projecthero.mod.event.EventConfig;
import com.projecthero.mod.event.EventInstance;
import com.projecthero.mod.event.EventManager;
import com.projecthero.mod.event.EventSavedData;
import com.projecthero.mod.event.EventState;
import com.projecthero.mod.event.boss.BossThreat;
import com.projecthero.mod.event.raid.ZombieRaidNetworking;
import com.projecthero.mod.galactus.block.GalactusBlocks;
import com.projecthero.mod.galactus.block.HeraldsBeaconBlock;
import com.projecthero.mod.galactus.cinematic.GalactusCinematics;
import com.projecthero.mod.galactus.cinematic.GalactusScenes;
import com.projecthero.mod.galactus.cinematic.GalactusScenes.Scene;
import com.projecthero.mod.galactus.entity.ElementConverterEntity;
import com.projecthero.mod.galactus.entity.GalactusEntity;
import com.projecthero.mod.galactus.entity.GalactusEntityTypes;
import com.projecthero.mod.galactus.entity.GalactusMob;
import com.projecthero.mod.galactus.entity.PowerPylonEntity;
import com.projecthero.mod.galactus.entity.PunisherRobotEntity;
import com.projecthero.mod.galactus.entity.SilverSurferEntity;
import com.projecthero.mod.galactus.entity.WorldshipEntity;
import com.projecthero.mod.galactus.item.GalactusItems;
import com.projecthero.mod.galactus.orbit.DevourerEntity;
import com.projecthero.mod.galactus.orbit.DevourerPartEntity;
import com.projecthero.mod.galactus.orbit.GalactusOrbit;
import com.projecthero.mod.galactus.orbit.OrbitArena;
import com.projecthero.mod.galactus.orbit.OrbitDebris;
import com.projecthero.mod.galactus.orbit.OrbitIsland;
import com.projecthero.mod.galactus.orbit.OrbitRules;
import com.projecthero.mod.galactus.orbit.StageDevourer;
import com.projecthero.mod.galactus.orbit.StageFeast;
import com.projecthero.mod.galactus.orbit.StageIslands;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.joml.Quaternionf;

/**
 * v0.17.2: <b>Galactus raid v2 -- the Devourer Descends</b>, a three-stage super raid with cutscenes (design:
 * {@code docs/GALACTUS_RAID_V2.md}; the friend's notes in {@code docs/reference/galactus_revamp}). Built on the world-event
 * framework ({@link EventInstance}), anchored on the Herald's Beacon in the overworld; the stages are fought in the orbit
 * ({@link GalactusOrbit}, an instance of its own).
 *
 * <pre>
 * ARRIVAL   C1: the Herald streaks down from orbit to the beacon (cutscene)
 * HERALD    the Silver Surfer duel (he cannot be killed: at 10% he...)
 * KNEEL     ...falls to one knee: "He is here."
 * DEVOURER  C2: the sky tears, a 230-block Galactus head rises over the horizon, roars, and lifts the party into space
 * ASCENT    the arrival sweep over the meteor field
 * STAGE1    the Shattered Orbit ({@link StageIslands}): he smashes the islands; hit his stuck fist; three staggers
 * PULL      C3: the Worldship pulls the islands into its hull; he kneels over the Earth; the Surfer turns
 * STAGE2    the Feast ({@link StageFeast}): overload his converters with Cosmic Cells (World Consumption at 100% = loss)
 * RISE      C4: he lets go of the Earth and rises to his full height beside the deck
 * STAGE3    the Devourer ({@link StageDevourer}): the real fight, a hit-box per part; at 5% he kneels for the Nullifier
 * ENDING    C5: the Nullifier fires, he comes apart into stars; the fall home; the beacon becomes the chest
 * CONSUMED  the loss: the Earth drains grey; everyone home
 * </pre>
 * Late joiners right-click the active beacon (the Rift) to go up. Death up there keeps your items (handed back on
 * respawn). A logout up there returns you to your raid, or home if it has ended. A restart rebuilds the current stage.
 */
public class GalactusEvent extends EventInstance {
	public static final String TYPE_ID = "galactus";
	public static final int SKY_COLOR = 0x2B0B4F;
	public static final int SKY_COLOR_CONSUMED = 0xE8D8FF;
	static final int HERALD_RESPAWN_TICKS = 200;
	static final int KNEEL_TICKS = 70;

	public enum Phase { ARRIVAL, HERALD, KNEEL, DEVOURER, ASCENT, STAGE1, PULL, STAGE2, RISE, STAGE3, ENDING, CONSUMED }

	private Phase phase = Phase.ARRIVAL;
	private long phaseStart = -1;
	private final Set<UUID> fighters = new HashSet<>();
	private final Set<UUID> down = new HashSet<>();
	private String activatorName = "";
	private boolean beaconCentre = true;
	private float sceneYaw;
	private UUID surferId;
	private int slot = -1;
	public final StageIslands stage1 = new StageIslands();
	public final StageFeast stage2 = new StageFeast();
	public final StageDevourer stage3 = new StageDevourer();
	private double consumption;
	private int partsEarned;
	private final List<UUID> partDrops = new ArrayList<>();
	private UUID nullifierUser;
	private boolean nullifierGiven;
	private long finaleAt = -1;
	private final Map<UUID, Integer> shardsOwed = new HashMap<>();
	private final Map<UUID, Double> damageBy = new HashMap<>();
	private long pullAt = -1;

	private transient EventBossBar bar;
	private transient EventBossBar bossBar;
	private transient ServerBossEvent orbitBar;
	private transient ServerBossEvent orbitBossBar;
	private transient UUID devourerId;
	private transient UUID orbitSurferId;
	private transient UUID worldshipId;
	private transient final List<UUID> orbitMobs = new ArrayList<>();
	private transient BossThreat threat;
	private transient final Map<String, Long> recentHits = new HashMap<>();
	private transient final Set<UUID> helped = new HashSet<>();
	/** Who C2 is lifting (taken as it starts: the hold has run out by the time it ends). */
	private transient final Set<UUID> liftIds = new HashSet<>();
	private transient List<OrbitArena.Pad> cachedPads = new ArrayList<>();
	private transient boolean needsRebuild;
	private transient long lastSkySent = -1000;
	private transient int lastSkyKey = -1;
	private transient long missingSince = -1;
	private transient Vec3 heraldLast;
	/** Test hooks. */
	public transient boolean instant;
	public transient boolean noGuards;

	public GalactusEvent(UUID id) {
		super(id);
	}

	// ---------------------------------------------------------------- identity

	@Override
	public String typeId() {
		return TYPE_ID;
	}

	@Override
	public Component displayName() {
		return Component.translatable("event.projecthero.galactus");
	}

	public static double arena() {
		return GalactusConfig.arena().radius;
	}

	@Override
	public double radius() {
		return arena() + 32.0;
	}

	public static GalactusEvent find(ServerLevel level, UUID id) {
		if (id == null) {
			return null;
		}
		return EventSavedData.get(level).byId(id) instanceof GalactusEvent g && g.state().active() ? g : null;
	}

	public static List<GalactusEvent> all(MinecraftServer server) {
		List<GalactusEvent> out = new ArrayList<>();
		for (EventInstance e : EventManager.active(server)) {
			if (e instanceof GalactusEvent g) {
				out.add(g);
			}
		}
		return out;
	}

	public Phase phase() {
		return phase;
	}

	public long phaseStart() {
		return phaseStart;
	}

	public boolean isFighter(UUID player) {
		return fighters.contains(player);
	}

	public Set<UUID> fighters() {
		return fighters;
	}

	public void addFighter(UUID id) {
		fighters.add(id);
	}

	public int fighterCount() {
		return Math.max(1, fighters.size() - down.size());
	}

	public double consumption() {
		return consumption;
	}

	public int partsEarned() {
		return partsEarned;
	}

	public int orbitSlot() {
		return slot;
	}

	public float sceneYaw() {
		return sceneYaw;
	}

	/** Is the fight up in the orbit right now? */
	public boolean orbitActive() {
		return slot >= 0 && state() == EventState.RUNNING && phase.ordinal() >= Phase.ASCENT.ordinal();
	}

	private boolean orbitPhase() {
		return phase.ordinal() >= Phase.ASCENT.ordinal();
	}

	/** Fighters (online, alive, not down) in {@code level}. */
	public List<ServerPlayer> fighterPlayers(ServerLevel level) {
		List<ServerPlayer> out = new ArrayList<>();
		for (UUID u : fighters) {
			if (!down.contains(u) && level.getServer().getPlayerList().getPlayer(u) instanceof ServerPlayer p && p.isAlive() && p.level() == level) {
				out.add(p);
			}
		}
		return out;
	}

	/** Fighters up in this raid's orbit instance. */
	public List<ServerPlayer> orbitFighters(ServerLevel orbit) {
		List<ServerPlayer> out = new ArrayList<>();
		if (orbit == null || slot < 0) {
			return out;
		}
		for (UUID u : fighters) {
			if (orbit.getServer().getPlayerList().getPlayer(u) instanceof ServerPlayer p && p.isAlive() && p.level() == orbit
					&& GalactusOrbit.slotOf(p.getX()) == slot && !p.isSpectator()) {
				out.add(p);
			}
		}
		return out;
	}

	public SilverSurferEntity surfer(ServerLevel level) {
		return surferId != null && level.getEntity(surferId) instanceof SilverSurferEntity s && s.isAlive() ? s : null;
	}

	public SilverSurferEntity orbitSurfer(ServerLevel orbit) {
		return orbitSurferId != null && orbit != null && orbit.getEntity(orbitSurferId) instanceof SilverSurferEntity s && s.isAlive() ? s : null;
	}

	public DevourerEntity devourer(ServerLevel level) {
		return devourerId != null && level != null && level.getEntity(devourerId) instanceof DevourerEntity d && d.isAlive() ? d : null;
	}

	public List<PunisherRobotEntity> orbitPunishers(ServerLevel orbit) {
		List<PunisherRobotEntity> out = new ArrayList<>();
		for (UUID u : orbitMobs) {
			if (orbit.getEntity(u) instanceof PunisherRobotEntity p && p.isAlive() && !p.isDeadOrDying()) {
				out.add(p);
			}
		}
		return out;
	}

	public void adopt(Mob mob) {
		if (mob instanceof GalactusMob g) {
			g.bindToEvent(id());
		}
		own(mob);
	}

	/** A mob of the orbit: bound to this raid (its orphan guard), tracked by the raid (the framework owns overworld mobs only). */
	public void adoptOrbit(Mob mob) {
		if (mob instanceof GalactusMob g) {
			g.bindToEvent(id());
		}
		mob.setPersistenceRequired();
		orbitMobs.add(mob.getUUID());
	}

	private ServerLevel orbitLevel(MinecraftServer server) {
		return GalactusOrbit.level(server);
	}

	private Vec3 anchor() {
		return Vec3.atBottomCenterOf(center()).add(0, 1, 0);
	}

	/** {@code EventInstance} hook: fighters up in the orbit count as present (the beacon area is empty while they fight). */
	@Override
	protected boolean offsitePresent(ServerLevel level) {
		ServerLevel orbit = orbitLevel(level.getServer());
		return orbitActive() && !orbitFighters(orbit).isEmpty();
	}

	// ---------------------------------------------------------------- activation and C1

	void activate(ServerLevel level, ServerPlayer activator, boolean beacon) {
		if (activator != null) {
			activatorName = activator.getGameProfile().getName();
			sceneYaw = activator.getYRot();
		}
		beaconCentre = beacon;
		setState(EventState.RUNNING);
		phase = Phase.ARRIVAL;
		phaseStart = level.getGameTime();
		BlockPos c = center();
		double r2 = arena() * arena();
		for (ServerPlayer p : level.players()) {
			if (GalactusCombat.isFighter(p) && p.distanceToSqr(c.getX() + 0.5, p.getY(), c.getZ() + 0.5) <= r2) {
				fighters.add(p.getUUID());
			}
		}
		if (activator != null && GalactusCombat.isFighter(activator)) {
			fighters.add(activator.getUUID());
		}
		participants().refresh(level, c, EventConfig.framework().abandonRadius, true);
		keepTheBeacon(level);
		level.playSound(null, c, SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 3.0f, 0.4f);
		level.playSound(null, c, SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 3.0f, 0.5f);
		GalactusFx.forced(level, ParticleTypes.END_ROD, c.getX() + 0.5, c.getY() + 2, c.getZ() + 0.5, 60, 0.3, 12, 0.3, 0.05);
		sendSky(level, true);
		SilverSurferEntity s = spawnSurfer(level);
		if (s != null && !instant) {
			s.setNoAi(true);
			Vec3 at = GalactusScenes.heraldPos(anchor(), sceneYaw, 0);
			s.moveTo(at.x, at.y, at.z, sceneYaw + 180f, 0f);
		}
		if (instant) {
			startHerald(level);
		} else {
			GalactusCinematics.play(fighterPlayers(level), Scene.HERALD, anchor(), sceneYaw, 0, -1, 0f);
		}
		ProjectHeroMod.LOGGER.info("[ProjectHero] Galactus raid {} started at {} with {} fighter(s)", id(), c, fighters.size());
	}

	private void keepTheBeacon(ServerLevel level) {
		if (!beaconCentre || !level.isLoaded(center())) {
			return;
		}
		BlockState state = level.getBlockState(center());
		if (!state.is(GalactusBlocks.HERALDS_BEACON)) {
			if (state.isAir() || state.canBeReplaced()) {
				level.setBlock(center(), GalactusBlocks.HERALDS_BEACON.defaultBlockState().setValue(HeraldsBeaconBlock.ACTIVE, true), 3);
			}
		} else if (!state.getValue(HeraldsBeaconBlock.ACTIVE)) {
			level.setBlock(center(), state.setValue(HeraldsBeaconBlock.ACTIVE, true), 3);
		}
	}

	private void setPhase(Phase p, long now) {
		phase = p;
		phaseStart = now;
	}

	private void tickArrival(ServerLevel level, long in) {
		SilverSurferEntity s = surfer(level);
		Vec3 a = anchor();
		Vec3 at = GalactusScenes.heraldPos(a, sceneYaw, in);
		if (s != null) {
			Vec3 next = GalactusScenes.heraldPos(a, sceneYaw, in + 1);
			Vec3 dir = next.subtract(at);
			float yaw = dir.horizontalDistanceSqr() > 1.0e-4 ? (float) (Mth.atan2(-dir.x, dir.z) * Mth.RAD_TO_DEG) : sceneYaw + 180f;
			if (in >= GalactusScenes.HERALD_LAND) {
				yaw = sceneYaw + 180f;
			}
			s.moveTo(at.x, at.y, at.z, yaw, 0f);
			s.setYHeadRot(yaw);
			s.yBodyRot = yaw;
			if (in >= 20 && in <= GalactusScenes.HERALD_LAND && heraldLast != null) {
				GalactusFx.line(level, GalactusFx.SILVER, heraldLast, at.add(0, 0.6, 0), 0.8);
				GalactusFx.line(level, ParticleTypes.END_ROD, heraldLast, at.add(0, 0.6, 0), 2.5);
			} else if (in < 20 && in % 2 == 0) {
				GalactusFx.forced(level, ParticleTypes.END_ROD, at.x, at.y, at.z, 6, 0.4, 0.4, 0.4, 0.01);
			}
		}
		heraldLast = at.add(0, 0.6, 0);
		if (in == GalactusScenes.HERALD_BOOM) {
			GalactusFx.ring(level, ParticleTypes.CLOUD, at, 6, 48);
			GalactusFx.ring(level, ParticleTypes.END_ROD, at, 9, 64);
			GalactusFx.forced(level, ParticleTypes.FLASH, at.x, at.y, at.z, 3, 0.5, 0.5, 0.5, 0);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 8.0f, 1.3f);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.HOSTILE, 8.0f, 0.6f);
		}
		if (in == GalactusScenes.HERALD_LAND) {
			BlockPos g = groundAt(level, at.x, at.z);
			GalactusFx.ring(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, Vec3.atBottomCenterOf(g).add(0, 0.3, 0), 5, 40);
			GalactusFx.ring(level, GalactusFx.SILVER, Vec3.atBottomCenterOf(g).add(0, 0.3, 0), 3.5, 32);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 4.0f, 1.4f);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.ELYTRA_FLYING, SoundSource.HOSTILE, 3.0f, 1.6f);
		}
		if (in == GalactusScenes.HERALD_TITLE) {
			broadcastTitle(level, Component.translatable("title.projecthero.galactus.herald").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD),
					Component.translatable("title.projecthero.galactus.herald.sub").withStyle(ChatFormatting.GRAY));
			GalactusDialogue.surfer(level, center(), "arrival");
		}
		if (in >= Scene.HERALD.length) {
			startHerald(level);
		}
	}

	// ---------------------------------------------------------------- the Herald

	public SilverSurferEntity startHerald(ServerLevel level) {
		setPhase(Phase.HERALD, level.getGameTime());
		SilverSurferEntity s = surfer(level);
		if (s == null) {
			s = spawnSurfer(level);
		}
		if (s != null) {
			s.setNoAi(false);
		}
		return s;
	}

	private SilverSurferEntity spawnSurfer(ServerLevel level) {
		SilverSurferEntity s = GalactusEntityTypes.SILVER_SURFER.create(level);
		if (s == null) {
			return null;
		}
		BlockPos c = center();
		double y = instant ? c.getY() + 4 : c.getY() + 30;
		s.moveTo(c.getX() + 0.5, y, c.getZ() + 6.5, 180f, 0f);
		s.finalizeSpawn(level, level.getCurrentDifficultyAt(c), MobSpawnType.EVENT, null);
		s.configure(GalactusConfig.heraldHealthFor(fighterCount()));
		adopt(s);
		level.addFreshEntity(s);
		surferId = s.getUUID();
		return s;
	}

	private void tickHerald(ServerLevel level, long now) {
		SilverSurferEntity s = surfer(level);
		if (s == null) {
			if (level.isLoaded(center())) {
				if (missingSince < 0) {
					missingSince = now;
				} else if (now - missingSince > HERALD_RESPAWN_TICKS) {
					missingSince = -1;
					spawnSurfer(level);
				}
			}
			return;
		}
		missingSince = -1;
		if (s.isTurning() || s.isAlly()) {
			onSurferTurning(level, s);
		}
	}

	/** The Surfer stopped at 10% (called by him): he kneels; the Devourer is coming. */
	public void onSurferTurning(ServerLevel level, SilverSurferEntity s) {
		if (phase != Phase.HERALD) {
			return;
		}
		s.holdTurn = true;
		setPhase(Phase.KNEEL, level.getGameTime());
		GalactusDialogue.surfer(level, center(), "kneel");
		broadcastTitle(level, Component.translatable("title.projecthero.galactus.kneel").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD),
				Component.translatable("title.projecthero.galactus.kneel.sub").withStyle(ChatFormatting.GRAY));
	}

	/** Old hook (v0.16: he turned ally here); the v2 raid turns him in C3. */
	public void onSurferTurned(ServerLevel level, SilverSurferEntity s) {
	}

	public void surferLine(ServerLevel level, String key) {
		if (level.dimension() == GalactusOrbit.DIMENSION && slot >= 0) {
			surferLineOrbit(level, key);
		} else {
			GalactusDialogue.surfer(level, center(), key);
		}
	}

	public void surferLineOrbit(ServerLevel orbit, String key) {
		GalactusDialogue.surfer(orbit, GalactusOrbit.origin(slot), key);
	}

	// ---------------------------------------------------------------- C2: the Devourer wakes

	private void startDevourer(ServerLevel level) {
		long now = level.getGameTime();
		setPhase(Phase.DEVOURER, now);
		ServerLevel orbit = orbitLevel(level.getServer());
		prepareOrbit(orbit);
		DevourerEntity d = GalactusEntityTypes.DEVOURER.create(level);
		if (d != null) {
			Vec3 feet = GalactusScenes.devourerFeet(anchor(), sceneYaw, 0);
			d.setHeight(GalactusScenes.DEVOURER_HEIGHT);
			d.moveToFeet(feet.x, feet.y, feet.z, sceneYaw + 180f);
			d.setFacing(sceneYaw + 180f);
			d.setEyes(0f);
			d.setLean(0.12f);
			d.bindToEvent(id());
			level.addFreshEntity(d);
			devourerId = d.getUUID();
		}
		List<ServerPlayer> lift = new ArrayList<>();
		double r2 = (arena() + 16) * (arena() + 16);
		for (ServerPlayer p : fighterPlayers(level)) {
			if (p.distanceToSqr(anchor().x, p.getY(), anchor().z) <= r2) {
				lift.add(p);
			}
		}
		liftIds.clear();
		for (ServerPlayer p : lift) {
			liftIds.add(p.getUUID());
		}
		GalactusCinematics.play(lift, Scene.DEVOURER, anchor(), sceneYaw, 0, d != null ? d.getId() : -1, 0f);
		SilverSurferEntity s = surfer(level);
		if (s != null) {
			s.setNoAi(true);
		}
	}

	private void tickDevourer(ServerLevel level, long in) {
		DevourerEntity d = devourer(level);
		Vec3 a = anchor();
		if (d != null) {
			Vec3 feet = GalactusScenes.devourerFeet(a, sceneYaw, in);
			d.setFeet(feet.x, feet.y, feet.z);
			d.setLook(a.add(0, 2, 0));
			float eyes = in >= GalactusScenes.DEVOURER_EYE_L ? 1f : in >= GalactusScenes.DEVOURER_EYE_R ? 0.5f : 0f;
			d.setEyes(Mth.lerp(0.3f, d.eyes(), eyes));
			if (in >= GalactusScenes.DEVOURER_ROAR && in < GalactusScenes.DEVOURER_ROAR + 30) {
				d.setLean(0.12f - 0.25f * Mth.sin((in - GalactusScenes.DEVOURER_ROAR) / 30f * Mth.PI));
			}
		}
		if (in >= GalactusScenes.DEVOURER_RISE_START && in < GalactusScenes.DEVOURER_RISE_END && in % 20 == 0) {
			level.playSound(null, a.x, a.y, a.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 6.0f, 0.4f);
			level.playSound(null, a.x, a.y, a.z, SoundEvents.AMBIENT_BASALT_DELTAS_MOOD.value(), SoundSource.HOSTILE, 6.0f, 0.5f);
		}
		if ((in == GalactusScenes.DEVOURER_EYE_R || in == GalactusScenes.DEVOURER_EYE_L) && d != null) {
			Vec3 face = d.pose().face;
			level.playSound(null, a.x, a.y + 10, a.z, SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 8.0f, 0.4f);
			GalactusFx.forced(level, ParticleTypes.END_ROD, face.x, face.y, face.z, 40, 6, 3, 6, 0.1);
		}
		if (in == GalactusScenes.DEVOURER_ROAR) {
			level.playSound(null, a.x, a.y, a.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 10.0f, 0.3f);
			level.playSound(null, a.x, a.y, a.z, SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 10.0f, 0.4f);
			level.playSound(null, a.x, a.y, a.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 10.0f, 0.5f);
			GalactusDialogue.galactus(level, center(), "hunger");
		}
		if (in > GalactusScenes.DEVOURER_ROAR && in < GalactusScenes.DEVOURER_ROAR + 24 && in % 2 == 0) {
			double r = (in - GalactusScenes.DEVOURER_ROAR) * 3.5;
			GalactusFx.ring(level, ParticleTypes.CLOUD, a.add(0, 0.3, 0), r, (int) Math.min(160, 24 + r * 2));
		}
		if (in == GalactusScenes.DEVOURER_LIFT) {
			for (ServerPlayer p : fighterPlayers(level)) {
				if (liftIds.contains(p.getUUID())) {
					p.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 70, 3, false, false));
				}
			}
			level.playSound(null, a.x, a.y, a.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 8.0f, 0.5f);
		}
		if (in > GalactusScenes.DEVOURER_LIFT && in % 2 == 0 && d != null) {
			Vec3 face = d.pose().face;
			for (ServerPlayer p : fighterPlayers(level)) {
				if (liftIds.contains(p.getUUID())) {
					GalactusFx.line(level, GalactusFx.COSMIC, face, p.position().add(0, 1, 0), 4.0);
					GalactusFx.forced(level, GalactusFx.COSMIC_SMALL, p.getX(), p.getY() + 1, p.getZ(), 6, 0.6, 1.0, 0.6, 0.05);
				}
			}
		}
		if (in >= Scene.DEVOURER.length) {
			ascend(level);
		}
	}

	/** Everyone lifted goes up; the arrival sweep plays. */
	private void ascend(ServerLevel level) {
		long now = level.getGameTime();
		ServerLevel orbit = orbitLevel(level.getServer());
		DevourerEntity old = devourer(level);
		if (old != null) {
			old.discard();
		}
		devourerId = null;
		SilverSurferEntity s = surfer(level);
		if (s != null) {
			disown(s.getUUID());
			s.discard();
		}
		surferId = null;
		if (orbit == null) {
			fail(level, Component.literal("The orbit is missing (datapack dimension projecthero:galactus_orbit)."));
			return;
		}
		prepareOrbit(orbit);
		List<ServerPlayer> lifted = new ArrayList<>();
		for (ServerPlayer p : fighterPlayers(level)) {
			if (liftIds.contains(p.getUUID()) || instant) {
				lifted.add(p);
			}
		}
		int i = 0;
		for (ServerPlayer p : lifted) {
			p.removeEffect(MobEffects.LEVITATION);
			GalactusOrbit.sendIn(p, orbit, arrivalSpot(i++, lifted.size()), 0f);
			GalactusCinematics.moved(p);
		}
		setPhase(Phase.ASCENT, now);
		spawnOrbitCast(orbit);
		DevourerEntity d = devourer(orbit);
		GalactusCinematics.play(lifted, Scene.ARRIVAL, Vec3.atBottomCenterOf(GalactusOrbit.origin(slot)).add(0, 1, 0), 0f, 0, d != null ? d.getId() : -1, 0f);
	}

	private Vec3 arrivalSpot(int i, int n) {
		BlockPos o = GalactusOrbit.origin(slot);
		double a = n <= 1 ? -Math.PI / 2 : i * Math.PI * 2 / n;
		return new Vec3(o.getX() + 0.5 + Math.cos(a) * 3, o.getY() + 1, o.getZ() + 0.5 + Math.sin(a) * 3);
	}

	/** Takes an instance slot and builds the Stage 1 field (once). */
	private void prepareOrbit(ServerLevel orbit) {
		if (orbit == null) {
			return;
		}
		if (slot < 0) {
			slot = GalactusOrbit.freeSlot(orbit.getServer());
		}
		GalactusOrbit.hold(orbit, slot);
		if (stage1.islands.isEmpty()) {
			GalactusOrbit.wipe(orbit, slot);
			stage1.build(this, orbit, GalactusOrbit.origin(slot), fighters.size());
		}
	}

	/** The Devourer (Stage 1 pose) and the Surfer (an ally from here: he catches fallers) up in the orbit. */
	private void spawnOrbitCast(ServerLevel orbit) {
		BlockPos o = GalactusOrbit.origin(slot);
		DevourerEntity d = devourer(orbit);
		if (d == null) {
			d = GalactusEntityTypes.DEVOURER.create(orbit);
			if (d != null) {
				d.setHeight(phase.ordinal() >= Phase.RISE.ordinal() ? StageDevourer.HEIGHT : 180f);
				d.bindToEvent(id());
				Vec3 at = StageIslands.bodyAt(o, stage1.bodyAngle);
				d.moveToFeet(at.x, at.y, at.z, StageIslands.facing(stage1.bodyAngle));
				d.setFacing(StageIslands.facing(stage1.bodyAngle));
				d.setLean(0.22f);
				d.setEyes(0.6f);
				orbit.addFreshEntity(d);
				devourerId = d.getUUID();
				threat = newThreat(d);
			}
		}
		if (orbitSurfer(orbit) == null) {
			SilverSurferEntity s = GalactusEntityTypes.SILVER_SURFER.create(orbit);
			if (s != null) {
				s.moveTo(o.getX() + 4.5, o.getY() + 4, o.getZ() + 0.5, 0f, 0f);
				s.finalizeSpawn(orbit, orbit.getCurrentDifficultyAt(o), MobSpawnType.EVENT, null);
				s.configure(GalactusConfig.heraldHealthFor(fighterCount()));
				s.bindToEvent(id());
				orbit.addFreshEntity(s);
				s.becomeAlly(orbit);
				orbitMobs.add(s.getUUID());
				orbitSurferId = s.getUUID();
			}
		}
	}

	private BossThreat newThreat(DevourerEntity d) {
		return new BossThreat(d).range(260.0).filter(e -> e instanceof Player p && isFighter(p.getUUID()));
	}

	// ---------------------------------------------------------------- the stages

	public LivingEntity devourerTarget(ServerLevel orbit, DevourerEntity d) {
		if (threat == null) {
			threat = newThreat(d);
		}
		LivingEntity t = threat.tick();
		if (t == null || !t.isAlive() || t.level() != orbit) {
			List<ServerPlayer> f = orbitFighters(orbit);
			if (f.isEmpty()) {
				return null;
			}
			ServerPlayer best = null;
			double bd = Double.MAX_VALUE;
			Vec3 c = Vec3.atCenterOf(GalactusOrbit.origin(slot));
			for (ServerPlayer p : f) {
				double dd = p.distanceToSqr(c);
				if (dd < bd) {
					bd = dd;
					best = p;
				}
			}
			return best;
		}
		return t;
	}

	public void devourerThreat(ServerLevel orbit, DevourerEntity d, DamageSource source, float amount) {
		if (threat == null) {
			threat = newThreat(d);
		}
		threat.record(source, amount);
		if (source.getEntity() instanceof ServerPlayer p) {
			damageBy.merge(p.getUUID(), (double) amount, Double::sum);
		}
	}

	/** A hit on one of the Devourer's parts: the stage decides. */
	public boolean onDevourerHit(ServerLevel level, DevourerEntity d, DevourerPartEntity.Kind kind, DamageSource source, float amount) {
		switch (phase) {
			case STAGE1 -> {
				boolean took = stage1.onHit(this, level, d, kind, amount);
				if (took && source.getEntity() instanceof ServerPlayer p) {
					helped.add(p.getUUID());
					devourerThreat(level, d, source, amount);
				}
				return took;
			}
			case STAGE3 -> {
				return stage3.onHit(this, level, d, kind, source, amount);
			}
			default -> {
				return false;
			}
		}
	}

	/** Stage 1: a stagger landed (everyone who hit the fist earns a Cosmic Shard). */
	public void onStaggered(ServerLevel orbit, int staggers) {
		for (UUID u : helped) {
			shardsOwed.merge(u, 1, Integer::sum);
		}
		helped.clear();
		titleOrbit(orbit, Component.translatable("title.projecthero.galactus.stagger", staggers, GalactusConfig.stage1().staggers)
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), null);
		GalactusDialogue.galactus(orbit, GalactusOrbit.origin(slot), "stagger" + Math.min(3, staggers));
		if (stage1.done()) {
			pullAt = orbit.getGameTime() + 70;
		}
	}

	public boolean recentlyHit(ServerPlayer p, String tag) {
		Long t = recentHits.get(p.getUUID() + tag);
		return t != null && p.serverLevel().getGameTime() < t;
	}

	public void markHit(ServerPlayer p, String tag, int ticks) {
		recentHits.put(p.getUUID() + tag, p.serverLevel().getGameTime() + ticks);
	}

	private void tickStage1(ServerLevel orbit, long now) {
		DevourerEntity d = devourer(orbit);
		stage1.tick(this, orbit, d, now);
		for (ServerPlayer p : orbitFighters(orbit)) {
			OrbitRules.tickPlayer(this, orbit, p, at -> stage1.safest(at).top().add((orbit.random.nextDouble() - 0.5) * 2, 0, (orbit.random.nextDouble() - 0.5) * 2),
					stage1.pads, true, d);
		}
		if (pullAt > 0 && now >= pullAt) {
			pullAt = -1;
			startPull(orbit);
		}
	}

	private Vec3 hullSafe(Vec3 at) {
		BlockPos o = GalactusOrbit.origin(slot);
		double dx = at.x - (o.getX() + 0.5), dz = at.z - (o.getZ() + 0.5);
		double r = Math.sqrt(dx * dx + dz * dz);
		double a = Math.atan2(dz, dx);
		return OrbitArena.hullSpot(o, Math.min(r, OrbitArena.HULL_R - 4), a);
	}

	// ---------------------------------------------------------------- C3: the Worldship

	private void startPull(ServerLevel orbit) {
		long now = orbit.getGameTime();
		setPhase(Phase.PULL, now);
		BlockPos o = GalactusOrbit.origin(slot);
		DevourerEntity d = devourer(orbit);
		if (d != null) {
			d.setArm(true, null, 0f);
			d.setArm(false, null, 0f);
		}
		WorldshipEntity w = GalactusEntityTypes.WORLDSHIP.create(orbit);
		if (w != null) {
			w.moveTo(o.getX() + 0.5, o.getY() + 70, o.getZ() - 150.5, 0f, 0f);
			orbit.addFreshEntity(w);
			worldshipId = w.getUUID();
		}
		List<ServerPlayer> f = orbitFighters(orbit);
		GalactusCinematics.play(f, Scene.WORLDSHIP, Vec3.atBottomCenterOf(o).add(0, 1, 0), 0f, 0, d != null ? d.getId() : -1, 0f);
		for (ServerPlayer p : f) {
			p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, Scene.WORLDSHIP.length + 40, 0, false, false));
		}
	}

	private void tickPull(ServerLevel orbit, long in) {
		BlockPos o = GalactusOrbit.origin(slot);
		WorldshipEntity w = worldshipId != null && orbit.getEntity(worldshipId) instanceof WorldshipEntity ws ? ws : null;
		if (w != null) {
			double k = GalactusScenes.ease(in / 110.0);
			w.setPos(o.getX() + 0.5, o.getY() + Mth.lerp(k, 70, 48), Mth.lerp(k, o.getZ() - 150.5, o.getZ() - 20.5));
		}
		DevourerEntity d = devourer(orbit);
		if (d != null) {
			double k = GalactusScenes.ease((in - 20) / 110.0);
			Vec3 from = StageIslands.bodyAt(o, stage1.bodyAngle), to = StageFeast.kneelFeet(o);
			Vec3 at = from.lerp(to, k);
			float yaw = Mth.rotLerp((float) k, StageIslands.facing(stage1.bodyAngle), 180f);
			StageFeast.kneelPose(d, o, (float) k);
			d.setFeet(at.x, at.y, at.z);
			d.setFacing(yaw);
			d.setEyes(0.6f);
		}
		if (in == GalactusScenes.WORLDSHIP_PULL) {
			// the islands come apart and fly onto the hull
			List<Vec3> tops = OrbitArena.hullTops(o, 2);
			int ti = 0;
			for (OrbitIsland isl : stage1.islands) {
				List<BlockPos> blocks = new ArrayList<>();
				List<BlockState> states = new ArrayList<>();
				isl.blocks((p, s) -> {
					blocks.add(p);
					states.add(s);
				});
				for (int i = 0; i < blocks.size(); i++) {
					orbit.setBlock(blocks.get(i), Blocks.AIR.defaultBlockState(), 2 | 16);
				}
				int step = Math.max(1, blocks.size() / 45);
				for (int i = 0; i < blocks.size(); i += step) {
					Vec3 dest = tops.get((ti++ * 7) % tops.size());
					OrbitDebris.fly(orbit, states.get(i), Vec3.atLowerCornerOf(blocks.get(i)), dest.add(-0.5, -0.5, -0.5), 1f,
							new Quaternionf(), new Quaternionf().rotateXYZ(orbit.random.nextFloat() * 3, orbit.random.nextFloat() * 3, 0), 1,
							GalactusScenes.WORLDSHIP_LOCK - GalactusScenes.WORLDSHIP_PULL - 4 + orbit.random.nextInt(8), 4);
				}
			}
			orbit.playSound(null, o.getX(), o.getY(), o.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 8.0f, 0.4f);
		}
		if (in > GalactusScenes.WORLDSHIP_PULL && in < GalactusScenes.WORLDSHIP_LOCK && in % 3 == 0 && w != null) {
			for (int i = 0; i < 4; i++) {
				double a = orbit.random.nextDouble() * Math.PI * 2;
				Vec3 p = OrbitArena.hullSpot(o, orbit.random.nextDouble() * 30, a);
				GalactusFx.line(orbit, GalactusFx.COSMIC_SMALL, w.position(), p, 3.0);
			}
		}
		if (in == GalactusScenes.WORLDSHIP_LOCK) {
			OrbitArena.buildHull(orbit, o);
			orbit.playSound(null, o.getX(), o.getY(), o.getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 8.0f, 0.3f);
			orbit.playSound(null, o.getX(), o.getY(), o.getZ(), SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.HOSTILE, 8.0f, 0.5f);
			for (ServerPlayer p : orbitFighters(orbit)) {
				Vec3 at = hullSafe(p.position());
				p.teleportTo(at.x, at.y + 0.2, at.z);
				p.setDeltaMovement(Vec3.ZERO);
				GalactusCinematics.moved(p);
			}
		}
		if (in == GalactusScenes.WORLDSHIP_SURFER) {
			surferLineOrbit(orbit, "turn_orbit");
		}
		// anyone who slipped while the islands went: back up
		for (ServerPlayer p : orbitFighters(orbit)) {
			if (p.getY() < GalactusOrbit.PLANE_Y - 10) {
				Vec3 at = in >= GalactusScenes.WORLDSHIP_LOCK ? hullSafe(p.position()) : new Vec3(p.getX(), GalactusOrbit.PLANE_Y + 6, p.getZ());
				p.teleportTo(at.x, at.y + 0.2, at.z);
				p.setDeltaMovement(Vec3.ZERO);
				p.fallDistance = 0;
				GalactusCinematics.moved(p);
			}
		}
		if (in >= Scene.WORLDSHIP.length) {
			startStage2(orbit);
		}
	}

	private void startStage2(ServerLevel orbit) {
		setPhase(Phase.STAGE2, orbit.getGameTime());
		BlockPos o = GalactusOrbit.origin(slot);
		int left = stage2.total > 0 ? stage2.total - stage2.reversed : StageFeast.converterCount(fighters.size());
		if (stage2.total <= 0) {
			stage2.total = left;
		}
		stage2.spawnConverters(this, orbit, o, Math.max(1, left), !noGuards);
		titleOrbit(orbit, Component.translatable("title.projecthero.galactus.stage2").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
				Component.translatable("title.projecthero.galactus.stage2.sub").withStyle(ChatFormatting.GRAY));
	}

	private void tickStage2(ServerLevel orbit, long now) {
		DevourerEntity d = devourer(orbit);
		stage2.tick(this, orbit, d, now);
		for (ServerPlayer p : orbitFighters(orbit)) {
			OrbitRules.tickPlayer(this, orbit, p, this::hullSafe, null, false, d);
		}
		int live = stage2.live(orbit).size();
		addConsumption(orbit, GalactusConfig.stage2().consumptionPerConverterPerMinute * live / 1200.0);
		if (stage2.done()) {
			startRise(orbit);
		}
	}

	public void addConsumption(ServerLevel level, double percent) {
		if (percent <= 0 || phase == Phase.CONSUMED) {
			return;
		}
		int before = (int) consumption;
		consumption = Math.min(100.0, consumption + percent);
		int after = (int) consumption;
		ServerLevel orbit = orbitLevel(level.getServer());
		for (int mark : new int[] { 50, 75, 90 }) {
			if (before < mark && after >= mark) {
				titleOrbit(orbit, Component.translatable("title.projecthero.galactus.consumption", mark).withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
						Component.translatable("title.projecthero.galactus.consumption.sub").withStyle(ChatFormatting.GRAY));
			}
		}
		if (consumption >= 100.0) {
			startConsumed(orbit);
		}
	}

	// ---------------------------------------------------------------- C4: he rises

	private void startRise(ServerLevel orbit) {
		long now = orbit.getGameTime();
		setPhase(Phase.RISE, now);
		BlockPos o = GalactusOrbit.origin(slot);
		stage2.clearCells(orbit, orbitFighters(orbit));
		for (PunisherRobotEntity p : orbitPunishers(orbit)) {
			GalactusFx.burst(orbit, p.position().add(0, 2, 0), 1.5);
			p.discard();
		}
		StageDevourer.buildDeck(orbit, o);
		DevourerEntity d = devourer(orbit);
		stage3.bodyAngle = Math.PI / 2;
		GalactusCinematics.play(orbitFighters(orbit), Scene.RISE, Vec3.atBottomCenterOf(o).add(0, 1, 0), 0f, 0, d != null ? d.getId() : -1, 0f);
	}

	private void tickRise(ServerLevel orbit, long in) {
		BlockPos o = GalactusOrbit.origin(slot);
		DevourerEntity d = devourer(orbit);
		if (d != null) {
			double k = GalactusScenes.ease((in - GalactusScenes.RISE_STAND) / 100.0);
			Vec3 from = StageFeast.kneelFeet(o), to = StageDevourer.bodyAt(o, stage3.bodyAngle, 0);
			Vec3 at = from.lerp(to, k);
			d.setHeight((float) Mth.lerp(k, 180f, StageDevourer.HEIGHT));
			d.setFeet(at.x, at.y, at.z);
			d.setFacing(StageIslands.facing(stage3.bodyAngle));
			d.setLean((float) Mth.lerp(k, 0.75, 0.15));
			if (in < GalactusScenes.RISE_PLANT - 20) {
				float w = (float) (1 - Mth.clamp(in / 40.0, 0, 1));
				Vec3 earth = new Vec3(o.getX() + 0.5, o.getY() - 320, o.getZ() + 0.5 + StageFeast.KNEEL_R + 30);
				d.setArm(true, earth.add(-30, 0, 0), w);
				d.setArm(false, earth.add(30, 0, 0), w);
			} else {
				Vec3 rim = OrbitArena.hullSpot(o, 32, stage3.bodyAngle - 0.25).add(0, 0.5, 0);
				float w = (float) Mth.clamp((in - (GalactusScenes.RISE_PLANT - 20)) / 20.0, 0, 1);
				d.setArm(true, rim.add(0, 10 * (1 - w), 0), w);
				d.setArm(false, null, 0f);
			}
			d.setEyes(Mth.lerp(0.05f, d.eyes(), 0.9f));
			d.setLook(Vec3.atCenterOf(o));
			if (in == GalactusScenes.RISE_STAND) {
				orbit.playSound(null, d.getX(), o.getY(), d.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 10.0f, 0.35f);
			}
			if (in == GalactusScenes.RISE_PLANT) {
				Vec3 rim = OrbitArena.hullSpot(o, 32, stage3.bodyAngle - 0.25);
				orbit.playSound(null, rim.x, rim.y, rim.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 8.0f, 0.3f);
				orbit.playSound(null, rim.x, rim.y, rim.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 6.0f, 0.5f);
				GalactusFx.forced(orbit, ParticleTypes.EXPLOSION, rim.x, rim.y, rim.z, 8, 3, 1, 3, 0);
				GalactusDialogue.galactus(orbit, o, "rise");
			}
		}
		if (in >= Scene.RISE.length) {
			startStage3(orbit);
		}
	}

	private void startStage3(ServerLevel orbit) {
		setPhase(Phase.STAGE3, orbit.getGameTime());
		if (stage3.maxHealth <= 0) {
			stage3.begin(this, fighters.size());
		}
		DevourerEntity d = devourer(orbit);
		if (d != null) {
			d.setHeight(StageDevourer.HEIGHT);
			d.setPartsOn(true);
		}
		titleOrbit(orbit, Component.translatable("title.projecthero.galactus.stage3").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD),
				Component.translatable("title.projecthero.galactus.stage3.sub").withStyle(ChatFormatting.GRAY));
	}

	private List<OrbitArena.Pad> shoulderPads(BlockPos o, DevourerEntity d) {
		List<OrbitArena.Pad> out = new ArrayList<>();
		if (d == null) {
			return out;
		}
		var pose = d.pose();
		for (int i = 0; i < 4; i++) {
			double a = i * Math.PI / 2 + Math.PI / 4;
			Vec3 spot = OrbitArena.hullSpot(o, 27, a);
			BlockPos at = BlockPos.containing(spot.x, GalactusOrbit.PLANE_Y, spot.z);
			Vec3 sh = pose.shoulderR.distanceToSqr(spot) < pose.shoulderL.distanceToSqr(spot) ? pose.shoulderR : pose.shoulderL;
			if (Math.sqrt(sh.distanceToSqr(spot.x, sh.y, spot.z)) < 46) {
				out.add(new OrbitArena.Pad(at, sh.add(0, 6 * pose.k + 1.5, 0)));
			}
		}
		return out;
	}

	/** The four shoulder pads' blocks (built with the deck). */
	public static void buildShoulderPads(ServerLevel orbit, BlockPos o) {
		for (int i = 0; i < 4; i++) {
			Vec3 spot = OrbitArena.hullSpot(o, 27, i * Math.PI / 2 + Math.PI / 4);
			OrbitArena.buildPad(orbit, BlockPos.containing(spot.x, GalactusOrbit.PLANE_Y, spot.z));
		}
	}

	private void tickStage3(ServerLevel orbit, long now) {
		BlockPos o = GalactusOrbit.origin(slot);
		DevourerEntity d = devourer(orbit);
		stage3.tick(this, orbit, d, now);
		if (now % 2 == 0) {
			cachedPads = shoulderPads(o, d);
		}
		for (ServerPlayer p : orbitFighters(orbit)) {
			OrbitRules.tickPlayer(this, orbit, p, this::hullSafe, cachedPads, false, d);
		}
		if (stage3.finale) {
			if (!nullifierGiven && finaleAt > 0 && now - finaleAt >= 50) {
				giveNullifier(orbit);
			}
			// a lost Nullifier comes back to the deck
			if (now % 20 == 0) {
				for (ItemEntity ie : orbit.getEntitiesOfClass(ItemEntity.class, GalactusOrbit.box(slot), x -> x.getItem().is(GalactusItems.ULTIMATE_NULLIFIER))) {
					ie.setUnlimitedLifetime();
					ie.setInvulnerable(true);
					ie.setGlowingTag(true);
					if (ie.getY() < GalactusOrbit.PLANE_Y - 20) {
						ie.teleportTo(o.getX() + 0.5, o.getY() + 2, o.getZ() + 0.5);
						ie.setDeltaMovement(Vec3.ZERO);
					}
				}
				if (nullifierGiven && nullifierHolder(orbit) == null && orbit.getEntitiesOfClass(ItemEntity.class, GalactusOrbit.box(slot),
						x -> x.getItem().is(GalactusItems.ULTIMATE_NULLIFIER)).isEmpty()) {
					nullifierGiven = false; // lost entirely: the Surfer brings another
					finaleAt = now - 40;
				}
			}
		}
	}

	/** Stage 3 down to 5%: he kneels; the Surfer brings the Nullifier. */
	public void onFinale(ServerLevel orbit) {
		finaleAt = orbit.getGameTime();
		DevourerEntity d = devourer(orbit);
		titleOrbit(orbit, Component.translatable("title.projecthero.galactus.nullifier").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
				Component.translatable("title.projecthero.galactus.nullifier.sub").withStyle(ChatFormatting.WHITE));
		if (d != null) {
			Vec3 face = d.pose().face;
			orbit.playSound(null, face.x, face.y, face.z, SoundEvents.ENDER_DRAGON_HURT, SoundSource.HOSTILE, 10.0f, 0.3f);
		}
		GalactusDialogue.galactus(orbit, GalactusOrbit.origin(slot), "finale");
		surferLineOrbit(orbit, "nullifier");
	}

	/** The Surfer hands the Nullifier (built from the Stage 2 parts) to whoever dealt the most damage. */
	private void giveNullifier(ServerLevel orbit) {
		List<ServerPlayer> f = orbitFighters(orbit);
		if (f.isEmpty()) {
			return;
		}
		ServerPlayer best = f.get(0);
		double bd = -1;
		for (ServerPlayer p : f) {
			double dmg = damageBy.getOrDefault(p.getUUID(), 0.0);
			if (dmg > bd) {
				bd = dmg;
				best = p;
			}
		}
		for (ServerPlayer p : f) {
			removeItems(p, GalactusItems.NULLIFIER_PART, Integer.MAX_VALUE);
		}
		ItemStack n = new ItemStack(GalactusItems.ULTIMATE_NULLIFIER);
		if (!best.getInventory().add(n)) {
			best.drop(n, false);
		}
		nullifierGiven = true;
		SilverSurferEntity s = orbitSurfer(orbit);
		if (s != null) {
			GalactusFx.line(orbit, GalactusFx.SILVER, s.position().add(0, 1, 0), best.position().add(0, 1, 0), 0.6);
			s.teleportTo(best.getX() + 1.5, best.getY() + 1.5, best.getZ() + 1.5);
		}
		GalactusFx.forced(orbit, ParticleTypes.END_ROD, best.getX(), best.getY() + 1, best.getZ(), 60, 0.5, 1.5, 0.5, 0.1);
		orbit.playSound(null, best.getX(), best.getY(), best.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 3.0f, 1.2f);
		best.displayClientMessage(Component.translatable("message.projecthero.galactus.nullifier_given").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
		broadcastOrbit(orbit, Component.translatable("message.projecthero.galactus.nullifier_to", best.getDisplayName()).withStyle(ChatFormatting.GOLD));
	}

	/** The fighter holding the Ultimate Nullifier up there, or null. */
	public ServerPlayer nullifierHolder(ServerLevel orbit) {
		for (ServerPlayer p : orbitFighters(orbit)) {
			if (p.getInventory().countItem(GalactusItems.ULTIMATE_NULLIFIER) > 0) {
				return p;
			}
		}
		return null;
	}

	/** {@code by} held the Nullifier on him for 4 s: the ending. True if it worked. */
	public boolean useNullifier(ServerLevel level, ServerPlayer by) {
		if (phase != Phase.STAGE3 || !stage3.finale) {
			return false;
		}
		ServerLevel orbit = orbitLevel(level.getServer());
		nullifierUser = by.getUUID();
		long now = orbit.getGameTime();
		setPhase(Phase.ENDING, now);
		DevourerEntity d = devourer(orbit);
		if (d != null) {
			d.setPartsOn(false);
		}
		for (PunisherRobotEntity p : orbitPunishers(orbit)) {
			p.discard();
		}
		titleOrbit(orbit, Component.translatable("title.projecthero.galactus.nullified").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
				Component.translatable("title.projecthero.galactus.nullified.sub", by.getDisplayName()).withStyle(ChatFormatting.WHITE));
		GalactusCinematics.play(orbitFighters(orbit), Scene.ENDING, Vec3.atBottomCenterOf(GalactusOrbit.origin(slot)).add(0, 1, 0), 0f, 0,
				d != null ? d.getId() : -1, 0f);
		return true;
	}

	// ---------------------------------------------------------------- C5: the ending

	private void tickEnding(ServerLevel level, ServerLevel orbit, long in) {
		BlockPos o = GalactusOrbit.origin(slot);
		DevourerEntity d = devourer(orbit);
		ServerPlayer user = nullifierUser != null && orbit.getPlayerByUUID(nullifierUser) instanceof ServerPlayer sp ? sp : null;
		if (d != null) {
			Vec3 face = d.pose().face;
			if (in < GalactusScenes.ENDING_BURST && user != null && in % 2 == 0) {
				GalactusFx.line(orbit, GalactusFx.SILVER, user.getEyePosition(), face, 1.0);
				GalactusFx.line(orbit, ParticleTypes.END_ROD, user.getEyePosition(), face, 2.0);
			}
			if (in == 2) {
				orbit.playSound(null, face.x, face.y, face.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 10.0f, 0.4f);
				GalactusDialogue.galactus(orbit, o, "retreat1");
			}
			if (in == GalactusScenes.ENDING_CRACK) {
				d.setFlag(DevourerEntity.FLAG_CRACKED, true);
				orbit.playSound(null, face.x, face.y, face.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 10.0f, 0.3f);
			}
			if (in > GalactusScenes.ENDING_CRACK && in < GalactusScenes.ENDING_BURST) {
				d.setEyes(in % 6 < 3 ? 1f : 0.4f);
				d.setLean(Mth.lerp(0.05f, d.lean(), -0.15f));
				d.setLook(face.add(0, 40, 0));
				if (in % 2 == 0) {
					for (int i = 0; i < 6; i++) {
						Vec3 p = face.add((orbit.random.nextDouble() - 0.5) * 20, (orbit.random.nextDouble() - 0.3) * 20, (orbit.random.nextDouble() - 0.5) * 20);
						GalactusFx.forced(orbit, ParticleTypes.END_ROD, p.x, p.y, p.z, 2, 0.5, 0.5, 0.5, 0.2);
					}
				}
			}
			if (in == GalactusScenes.ENDING_BURST) {
				var pose = d.pose();
				Vec3[] pts = { pose.head, pose.face, pose.chest, pose.shoulderR, pose.shoulderL, pose.elbowR, pose.elbowL, pose.fistR, pose.fistL,
						d.feet().add(0, 30, 0), d.feet().add(0, 10, 0) };
				for (Vec3 p : pts) {
					GalactusFx.forced(orbit, ParticleTypes.END_ROD, p.x, p.y, p.z, 80, 6, 6, 6, 0.6);
					GalactusFx.forced(orbit, GalactusFx.COSMIC, p.x, p.y, p.z, 80, 6, 6, 6, 0.4);
					GalactusFx.forced(orbit, ParticleTypes.FIREWORK, p.x, p.y, p.z, 40, 4, 4, 4, 0.5);
				}
				orbit.playSound(null, face.x, face.y, face.z, SoundEvents.ENDER_DRAGON_DEATH, SoundSource.HOSTILE, 10.0f, 0.8f);
				orbit.playSound(null, o.getX(), o.getY(), o.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 10.0f, 0.4f);
				d.discard();
				devourerId = null;
			}
		}
		if (in > GalactusScenes.ENDING_LEAVE) {
			WorldshipEntity w = worldshipId != null && orbit.getEntity(worldshipId) instanceof WorldshipEntity ws ? ws : null;
			if (w != null) {
				w.setPos(w.getX(), w.getY() + 1.2, w.getZ() - 2.5);
			}
		}
		if (in == GalactusScenes.ENDING_LEAVE + 10) {
			surferLineOrbit(orbit, "farewell");
		}
		if (in >= Scene.ENDING.length) {
			goHome(level, orbit);
			complete(level);
		}
	}

	/** Everyone up there falls home (a white fade at the beacon). */
	private void goHome(ServerLevel level, ServerLevel orbit) {
		List<ServerPlayer> up = orbitFighters(orbit);
		if (orbit != null) {
			for (ServerPlayer p : new ArrayList<>(orbit.players())) {
				if (GalactusOrbit.slotOf(p.getX()) == slot && !up.contains(p)) {
					up.add(p);
				}
			}
		}
		for (ServerPlayer p : up) {
			GalactusCinematics.release(p);
			p.removeEffect(MobEffects.JUMP);
			GalactusOrbit.sendHome(p);
		}
		GalactusCinematics.play(up, Scene.HOME, anchor(), sceneYaw, 0, -1, 0f);
	}

	// ---------------------------------------------------------------- the loss

	private void startConsumed(ServerLevel orbit) {
		if (phase == Phase.CONSUMED || orbit == null) {
			return;
		}
		setPhase(Phase.CONSUMED, orbit.getGameTime());
		consumption = 100.0;
		GalactusCinematics.play(orbitFighters(orbit), Scene.CONSUMED, Vec3.atBottomCenterOf(GalactusOrbit.origin(slot)).add(0, 1, 0), 0f, 0, -1, 0f);
		GalactusDialogue.galactus(orbit, GalactusOrbit.origin(slot), "consumed");
		sendOrbitSky(orbit, true);
	}

	private void tickConsumed(ServerLevel level, ServerLevel orbit, long in) {
		if (in % 10 == 0 && orbit != null) {
			BlockPos o = GalactusOrbit.origin(slot);
			orbit.playSound(null, o.getX(), o.getY(), o.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 6.0f, 0.4f);
		}
		if (in >= Scene.CONSUMED.length) {
			goHome(level, orbit);
			fail(level, Component.translatable("message.projecthero.galactus.consumed").withStyle(ChatFormatting.DARK_PURPLE));
		}
	}

	// ---------------------------------------------------------------- joining, rejoining

	/** Right-clicking the active beacon (the Rift) while the fight is up there: up you go. */
	public boolean joinViaRift(ServerLevel level, ServerPlayer p) {
		if (!orbitActive() || phase == Phase.ENDING || phase == Phase.CONSUMED || !GalactusCombat.isFighter(p)) {
			return false;
		}
		ServerLevel orbit = orbitLevel(level.getServer());
		fighters.add(p.getUUID());
		down.remove(p.getUUID());
		Vec3 b = Vec3.atCenterOf(center());
		GalactusFx.forced(level, ParticleTypes.END_ROD, b.x, b.y + 2, b.z, 60, 0.4, 6, 0.4, 0.1);
		level.playSound(null, b.x, b.y, b.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 3.0f, 0.6f);
		GalactusOrbit.sendIn(p, orbit, safeSpot(), 0f);
		p.displayClientMessage(Component.translatable("message.projecthero.galactus.rift").withStyle(ChatFormatting.LIGHT_PURPLE), true);
		catchUpScene(p);
		return true;
	}

	/** A fighter back in the orbit after a logout. */
	public void rejoinOrbit(ServerPlayer p) {
		ServerLevel orbit = orbitLevel(p.server);
		fighters.add(p.getUUID());
		down.remove(p.getUUID());
		Vec3 at = safeSpot();
		p.teleportTo(orbit, at.x, at.y, at.z, 0f, 0f);
		catchUpScene(p);
	}

	private void catchUpScene(ServerPlayer p) {
		Scene s = switch (phase) {
			case ASCENT -> Scene.ARRIVAL;
			case PULL -> Scene.WORLDSHIP;
			case RISE -> Scene.RISE;
			default -> null;
		};
		if (s != null) {
			ServerLevel orbit = orbitLevel(p.server);
			int elapsed = (int) Math.min(s.length - 1, orbit.getGameTime() - phaseStart);
			DevourerEntity d = devourer(orbit);
			GalactusCinematics.play(List.of(p), s, Vec3.atBottomCenterOf(GalactusOrbit.origin(slot)).add(0, 1, 0), 0f, elapsed, d != null ? d.getId() : -1, 0f);
		}
	}

	/** Where a newcomer lands: the Anchor in Stage 1, the deck after. */
	private Vec3 safeSpot() {
		BlockPos o = GalactusOrbit.origin(slot);
		if (phase.ordinal() <= Phase.STAGE1.ordinal()) {
			return Vec3.atBottomCenterOf(o).add(0, 1, -2);
		}
		return OrbitArena.hullSpot(o, 4, -Math.PI / 2);
	}

	// ---------------------------------------------------------------- the framework tick (every 0.5 s)

	@Override
	protected void onTick(ServerLevel level) {
		long now = level.getGameTime();
		if (state() == EventState.PENDING) {
			activate(level, null, beaconCentre);
		}
		if (phaseStart < 0) {
			phaseStart = now;
		}
		keepTheBeacon(level);
		if (!orbitPhase()) {
			if (phase != Phase.DEVOURER) {
				enforceArena(level);
			}
			ZombieRaidNetworking.pushSky(level, center(), arena(), SKY_COLOR);
			sendSky(level, false);
			drawBorder(level, now);
			tetherOwnedMobs(level, arena());
		} else {
			sendOrbitSky(orbitLevel(level.getServer()), false);
			// the beacon is a Rift while the fight is up there
			if (now % 20 == 0) {
				Vec3 b = Vec3.atCenterOf(center());
				GalactusFx.forced(level, GalactusFx.COSMIC, b.x, b.y + 3, b.z, 12, 0.5, 4, 0.5, 0.02);
			}
		}
		if (!state().finished()) {
			updateBars(level, now);
		}
	}

	private double horizontalDistance(Entity e) {
		BlockPos c = center();
		double dx = e.getX() - (c.getX() + 0.5);
		double dz = e.getZ() - (c.getZ() + 0.5);
		return Math.sqrt(dx * dx + dz * dz);
	}

	private void enforceArena(ServerLevel level) {
		double r = arena();
		for (ServerPlayer p : level.players()) {
			if (!GalactusCombat.isFighter(p)) {
				continue;
			}
			double d = horizontalDistance(p);
			UUID u = p.getUUID();
			if (!fighters.contains(u)) {
				if (d <= r) {
					fighters.add(u);
					p.displayClientMessage(Component.translatable("message.projecthero.galactus.joined").withStyle(ChatFormatting.LIGHT_PURPLE), true);
				}
				continue;
			}
			if (down.contains(u)) {
				if (d <= r) {
					down.remove(u);
				}
				continue;
			}
			if (d > r && d < r + 48 && !p.isPassenger()) {
				pullBack(level, p, r - 4);
				p.displayClientMessage(Component.translatable("message.projecthero.galactus.no_escape").withStyle(ChatFormatting.LIGHT_PURPLE), true);
				level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 1.0f, 1.2f);
			}
		}
	}

	private void pullBack(ServerLevel level, ServerPlayer p, double distance) {
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
		double y = Math.max(surface, Math.min(p.getY(), surface + 24.0));
		p.teleportTo(x, y, z);
		p.fallDistance = 0;
	}

	private void drawBorder(ServerLevel level, long now) {
		BlockPos c = center();
		double r = arena();
		List<ServerPlayer> viewers = new ArrayList<>();
		for (ServerPlayer p : level.players()) {
			if (horizontalDistance(p) < r + 48) {
				viewers.add(p);
			}
		}
		if (viewers.isEmpty()) {
			return;
		}
		int points = Math.max(16, Math.min(192, GalactusConfig.arena().borderPoints));
		com.projecthero.mod.network.ParticleBatch batch = new com.projecthero.mod.network.ParticleBatch();
		double reach = com.projecthero.mod.network.ParticleBatch.FORCE_RANGE;
		for (int i = 0; i < points; i++) {
			double a = i * Math.PI * 2 / points + (now % 120) * 0.0015;
			double x = c.getX() + 0.5 + Math.cos(a) * r;
			double z = c.getZ() + 0.5 + Math.sin(a) * r;
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
			batch.addForced(i % 2 == 0 ? GalactusFx.COSMIC : GalactusFx.SILVER, reach, x, y + 0.8, z, 1, 0, 0.5, 0, 0);
			if (i % 3 == 0) {
				batch.addForced(ParticleTypes.END_ROD, reach, x, y + 1.6, z, 1, 0, 0.6, 0, 0);
			}
		}
		for (ServerPlayer viewer : viewers) {
			batch.send(level, viewer);
		}
	}

	// ---------------------------------------------------------------- the sky

	/** 0 clear, 1 violet with stars, 2 consumed (white-hot), 3 clearing, 4 the orbit while he feeds (Stage 2). */
	public int skyStage() {
		return switch (phase) {
			case CONSUMED -> 2;
			case ENDING -> 3;
			case STAGE2 -> 4;
			default -> 1;
		};
	}

	private void sendSky(ServerLevel level, boolean force) {
		long now = level.getGameTime();
		int key = skyStage() * 1000 + (int) consumption;
		if (!force && key == lastSkyKey && now - lastSkySent < 40) {
			return;
		}
		lastSkyKey = key;
		lastSkySent = now;
		BlockPos c = center();
		GalactusSkyPayload payload = new GalactusSkyPayload(true, c.getX(), c.getY(), c.getZ(), (float) arena(), (byte) skyStage(),
				(float) (consumption / 100.0));
		double reach = arena() + 96;
		for (ServerPlayer p : level.players()) {
			if (horizontalDistance(p) < reach && ServerPlayNetworking.canSend(p, GalactusSkyPayload.TYPE)) {
				ServerPlayNetworking.send(p, payload);
			}
		}
	}

	/** The orbit sky: how grey the Earth is (World Consumption), whether it is being fed on. */
	private void sendOrbitSky(ServerLevel orbit, boolean force) {
		if (orbit == null || slot < 0) {
			return;
		}
		long now = orbit.getGameTime();
		int key = 5000 + skyStage() * 1000 + (int) consumption;
		if (!force && key == lastSkyKey && now - lastSkySent < 20) {
			return;
		}
		lastSkyKey = key;
		lastSkySent = now;
		BlockPos o = GalactusOrbit.origin(slot);
		float shown = phase == Phase.ENDING ? 0f : (float) (consumption / 100.0);
		GalactusSkyPayload payload = new GalactusSkyPayload(true, o.getX(), o.getY(), o.getZ(), 200f, (byte) skyStage(), shown);
		for (ServerPlayer p : orbitFighters(orbit)) {
			if (ServerPlayNetworking.canSend(p, GalactusSkyPayload.TYPE)) {
				ServerPlayNetworking.send(p, payload);
			}
		}
	}

	private void clearSky(ServerLevel level) {
		BlockPos c = center();
		GalactusSkyPayload clear = GalactusSkyPayload.clear(c.getX(), c.getY(), c.getZ());
		for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(p, GalactusSkyPayload.TYPE)
					&& (p.level() == level && horizontalDistance(p) < arena() + 200 || fighters.contains(p.getUUID()))) {
				ServerPlayNetworking.send(p, clear);
			}
		}
		ZombieRaidNetworking.clearSky(level, c);
	}

	// ---------------------------------------------------------------- the server tick (every tick)

	public void serverTick(ServerLevel level) {
		if (state() != EventState.RUNNING || center() == null) {
			return;
		}
		long now = level.getGameTime();
		if (phaseStart < 0) {
			phaseStart = now;
		}
		ServerLevel orbit = orbitLevel(level.getServer());
		if (needsRebuild) {
			needsRebuild = false;
			rebuild(level, orbit);
		}
		long in = now - phaseStart;
		long on = orbit != null ? orbit.getGameTime() : now;
		switch (phase) {
			case ARRIVAL -> tickArrival(level, in);
			case HERALD -> tickHerald(level, now);
			case KNEEL -> {
				if (in >= KNEEL_TICKS) {
					startDevourer(level);
				}
			}
			case DEVOURER -> tickDevourer(level, in);
			case ASCENT -> {
				DevourerEntity d = devourer(orbit);
				if (d != null) {
					stage1.place(GalactusOrbit.origin(slot), d);
					d.setEyes(Mth.lerp(0.05f, d.eyes(), 0.6f));
				}
				if (in >= Scene.ARRIVAL.length) {
					setPhase(Phase.STAGE1, now);
					titleOrbit(orbit, Component.translatable("title.projecthero.galactus.stage1").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
							Component.translatable("title.projecthero.galactus.stage1.sub").withStyle(ChatFormatting.GRAY));
					GalactusDialogue.galactus(orbit, GalactusOrbit.origin(slot), "stage1");
				}
			}
			case STAGE1 -> tickStage1(orbit, on);
			case PULL -> tickPull(orbit, in);
			case STAGE2 -> tickStage2(orbit, on);
			case RISE -> tickRise(orbit, in);
			case STAGE3 -> tickStage3(orbit, on);
			case ENDING -> tickEnding(level, orbit, in);
			case CONSUMED -> tickConsumed(level, orbit, in);
		}
		if (orbitPhase() && orbit != null && now % 100 == 0) {
			orbitMobs.removeIf(u -> orbit.getEntity(u) == null);
		}
	}

	/** After a load: the current stage is built again from scratch. */
	private void rebuild(ServerLevel level, ServerLevel orbit) {
		long now = level.getGameTime();
		switch (phase) {
			case ARRIVAL -> {
				if (surfer(level) == null) {
					spawnSurfer(level);
				}
				startHerald(level);
			}
			case KNEEL, DEVOURER -> {
				SilverSurferEntity s = surfer(level);
				if (s != null) {
					s.holdTurn = true;
				}
				startDevourer(level);
			}
			case ASCENT, STAGE1, PULL, STAGE2, RISE, STAGE3 -> {
				if (orbit == null) {
					fail(level, Component.literal("The orbit is missing."));
					return;
				}
				if (slot < 0) {
					slot = GalactusOrbit.freeSlot(level.getServer());
				}
				GalactusOrbit.hold(orbit, slot);
				GalactusOrbit.wipe(orbit, slot);
				OrbitDebris.clear(orbit);
				BlockPos o = GalactusOrbit.origin(slot);
				if (phase == Phase.ASCENT || phase == Phase.STAGE1) {
					int staggers = stage1.staggers;
					stage1.build(this, orbit, o, fighters.size());
					stage1.staggers = staggers;
					spawnOrbitCast(orbit);
					setPhase(Phase.STAGE1, now);
				} else if (phase == Phase.PULL || phase == Phase.STAGE2) {
					OrbitArena.buildHull(orbit, o);
					spawnOrbitCast(orbit);
					DevourerEntity d = devourer(orbit);
					if (d != null) {
						StageFeast.kneelPose(d, o, 1f);
					}
					startStage2(orbit);
				} else {
					OrbitArena.buildHull(orbit, o);
					StageDevourer.buildDeck(orbit, o);
					spawnOrbitCast(orbit);
					DevourerEntity d = devourer(orbit);
					if (d != null) {
						d.setHeight(StageDevourer.HEIGHT);
						stage3.place(o, d);
					}
					startStage3(orbit);
				}
			}
			case ENDING -> complete(level);
			case CONSUMED -> fail(level, Component.translatable("message.projecthero.galactus.consumed").withStyle(ChatFormatting.DARK_PURPLE));
			default -> {
			}
		}
	}

	// ---------------------------------------------------------------- deaths and the end

	public void onFighterDied(ServerLevel level, ServerPlayer player) {
		if (!fighters.contains(player.getUUID()) || state().finished()) {
			return;
		}
		down.add(player.getUUID());
		participants().recordDeath(player.getUUID());
		Component msg = Component.translatable("message.projecthero.galactus.fighter_down", player.getDisplayName()).withStyle(ChatFormatting.DARK_PURPLE);
		ServerLevel overworld = level.getServer().getLevel(dimension());
		if (overworld != null) {
			broadcast(overworld, msg);
		}
		broadcastOrbit(orbitLevel(level.getServer()), msg);
		if (down.containsAll(fighters) && phase != Phase.ENDING && overworld != null) {
			fail(overworld, Component.translatable("message.projecthero.galactus.all_down").withStyle(ChatFormatting.DARK_PURPLE));
		}
	}

	public List<ServerPlayer> winners(ServerLevel level) {
		List<ServerPlayer> out = new ArrayList<>();
		for (UUID u : fighters) {
			if ((!down.contains(u) || u.equals(nullifierUser)) && level.getServer().getPlayerList().getPlayer(u) instanceof ServerPlayer p && p.isAlive()) {
				out.add(p);
			}
		}
		return out;
	}

	@Override
	protected void onFinished(ServerLevel level, boolean success) {
		BlockPos c = center();
		GalactusWorldState ws = GalactusWorldState.get(level.getServer());
		List<ServerPlayer> winners = success ? winners(level) : List.of();
		tearDown(level);
		if (success) {
			BlockState there = level.getBlockState(c);
			if (there.isAir() || there.canBeReplaced() || there.is(GalactusBlocks.HERALDS_BEACON)) {
				GalactusRewards.placeChest(level, c, Math.max(1, winners.size()));
			}
			for (ServerPlayer p : winners) {
				GalactusRewards.grantClear(p);
				int shards = shardsOwed.getOrDefault(p.getUUID(), 0);
				if (shards > 0) {
					ItemStack s = new ItemStack(GalactusItems.COSMIC_SHARD, shards);
					if (!p.getInventory().add(s)) {
						p.drop(s, false);
					}
				}
			}
			ServerPlayer user = nullifierUser != null ? level.getServer().getPlayerList().getPlayer(nullifierUser) : null;
			if (user != null) {
				for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
					if (fighters.contains(p.getUUID())) {
						p.sendSystemMessage(Component.translatable("message.projecthero.galactus.worldbreaker", user.getDisplayName())
								.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
					}
				}
			}
			ws.retreats++;
			level.playSound(null, c, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);
			ProjectHeroMod.LOGGER.info("[ProjectHero] Galactus raid {} won; {} fighter(s) rewarded", id(), winners.size());
		} else {
			if (level.getBlockState(c).is(GalactusBlocks.HERALDS_BEACON)) {
				level.setBlock(c, Blocks.AIR.defaultBlockState(), 3);
			}
			ws.consumed++;
			GalactusFx.burst(level, Vec3.atCenterOf(c), 2.0);
		}
		ws.setDirty();
	}

	@Override
	protected void onAborted(ServerLevel level) {
		tearDown(level);
		if (level.isLoaded(center())) {
			BlockState state = level.getBlockState(center());
			if (state.is(GalactusBlocks.HERALDS_BEACON)) {
				level.setBlock(center(), state.setValue(HeraldsBeaconBlock.ACTIVE, false), 3);
			}
		}
	}

	@Override
	protected void onPaused(ServerLevel level) {
		super.onPaused(level);
		clearBars(level);
		clearSky(level);
	}

	private void clearBars(ServerLevel level) {
		if (bar != null) {
			bar.clear(level);
		}
		if (bossBar != null) {
			bossBar.clear(level);
		}
		if (orbitBar != null) {
			orbitBar.removeAllPlayers();
		}
		if (orbitBossBar != null) {
			orbitBossBar.removeAllPlayers();
		}
	}

	/** Everything the raid made goes: the orbit instance is wiped, everyone up there comes home, the items go. */
	private void tearDown(ServerLevel level) {
		clearBars(level);
		clearSky(level);
		ServerLevel orbit = orbitLevel(level.getServer());
		List<ServerPlayer> everyone = new ArrayList<>();
		for (UUID u : fighters) {
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(u);
			if (p != null) {
				everyone.add(p);
			}
		}
		GalactusCinematics.stop(everyone);
		if (orbit != null && slot >= 0) {
			for (ServerPlayer p : new ArrayList<>(orbit.players())) {
				if (GalactusOrbit.slotOf(p.getX()) == slot) {
					GalactusOrbit.sendHome(p);
				}
			}
			stage2.clearCells(orbit, everyone);
			OrbitDebris.clear(orbit);
			GalactusOrbit.wipe(orbit, slot);
			GalactusOrbit.release(orbit, slot);
		}
		DevourerEntity d = devourer(level);
		if (d != null) {
			d.discard();
		}
		devourerId = null;
		SilverSurferEntity s = surfer(level);
		if (s != null) {
			s.discard();
		}
		for (UUID u : partDrops) {
			if (level.getEntity(u) instanceof ItemEntity ie) {
				ie.discard();
			}
		}
		partDrops.clear();
		for (ServerPlayer p : everyone) {
			removeItems(p, GalactusItems.NULLIFIER_PART, Integer.MAX_VALUE);
			removeItems(p, GalactusItems.ULTIMATE_NULLIFIER, Integer.MAX_VALUE);
			removeItems(p, GalactusItems.COSMIC_CELL, Integer.MAX_VALUE);
		}
		// any leftover v0.16 ground ledger goes back
		GalactusTerrain.get(level).markAll(id());
	}

	static int removeItems(ServerPlayer p, net.minecraft.world.item.Item item, int max) {
		int removed = 0;
		for (int i = 0; i < p.getInventory().getContainerSize() && removed < max; i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (s.is(item)) {
				int n = Math.min(s.getCount(), max - removed);
				s.shrink(n);
				removed += n;
			}
		}
		return removed;
	}

	public void dropPart(ServerLevel level, Vec3 at) {
		partsEarned++;
		ItemEntity item = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(GalactusItems.NULLIFIER_PART));
		item.setUnlimitedLifetime();
		item.setInvulnerable(true);
		item.setDeltaMovement(0, 0.25, 0);
		level.addFreshEntity(item);
		GalactusFx.forced(level, ParticleTypes.END_ROD, at.x, at.y, at.z, 40, 0.2, 4.0, 0.2, 0.02);
	}

	// ---------------------------------------------------------------- messages

	public void broadcastOrbit(ServerLevel orbit, Component msg) {
		for (ServerPlayer p : orbitFighters(orbit)) {
			p.sendSystemMessage(msg);
		}
	}

	public void titleOrbit(ServerLevel orbit, Component title, Component subtitle) {
		for (ServerPlayer p : orbitFighters(orbit)) {
			p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 20));
			p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle != null ? subtitle : Component.empty()));
			p.connection.send(new ClientboundSetTitleTextPacket(title));
		}
	}

	// ---------------------------------------------------------------- bars

	private void updateBars(ServerLevel level, long now) {
		if (!orbitPhase()) {
			if (bar == null) {
				bar = new EventBossBar(id(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10, false);
			}
			if (bossBar == null) {
				bossBar = new EventBossBar(new UUID(id().getMostSignificantBits() ^ 0x6A6A6A6AL, id().getLeastSignificantBits() ^ 0x5A5AL),
						BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS, false);
			}
			String key = switch (phase) {
				case ARRIVAL -> "event.projecthero.galactus.bar.arrival_v2";
				case HERALD -> "event.projecthero.galactus.bar.herald";
				default -> "event.projecthero.galactus.bar.devourer";
			};
			bar.update(level, center(), radius(), Component.translatable(key).withStyle(ChatFormatting.LIGHT_PURPLE), 1f);
			SilverSurferEntity s = phase == Phase.HERALD ? surfer(level) : null;
			if (s != null) {
				bossBar.update(level, center(), radius(), Component.translatable("entity.projecthero.silver_surfer").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD),
						s.getHealth() / Math.max(1f, s.getMaxHealth()));
			} else if (bossBar.isShowing()) {
				bossBar.clear(level);
			}
			return;
		}
		if (bar != null && bar.isShowing()) {
			bar.clear(level);
		}
		if (bossBar != null && bossBar.isShowing()) {
			bossBar.clear(level);
		}
		ServerLevel orbit = orbitLevel(level.getServer());
		if (orbitBar == null) {
			orbitBar = new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
		}
		if (orbitBossBar == null) {
			orbitBossBar = new ServerBossEvent(Component.translatable("entity.projecthero.galactus"), BossEvent.BossBarColor.WHITE,
					BossEvent.BossBarOverlay.NOTCHED_20);
		}
		List<ServerPlayer> up = orbitFighters(orbit);
		int n = Math.max(1, up.size());
		Component name;
		float progress;
		boolean boss = false;
		switch (phase) {
			case STAGE1 -> {
				name = Component.translatable("event.projecthero.galactus.bar.stage1", stage1.staggers, GalactusConfig.stage1().staggers);
				progress = (float) Mth.clamp(stage1.stagger / stage1.staggerNeeded(n), 0, 1);
			}
			case STAGE2 -> {
				name = Component.translatable("event.projecthero.galactus.bar.stage2", (int) consumption, stage2.reversed, stage2.total);
				progress = (float) (consumption / 100.0);
			}
			case STAGE3 -> {
				float[] hands = stage3.crackerHands(n);
				if (stage3.finale) {
					name = Component.translatable("event.projecthero.galactus.bar.finale");
				} else if (hands != null) {
					name = Component.translatable("event.projecthero.galactus.bar.cracker", Math.round(hands[0] * 100), Math.round(hands[1] * 100));
				} else if (stage3.move == StageDevourer.Move.DEVOUR && !stage3.gagging()) {
					name = Component.translatable("event.projecthero.galactus.bar.devour");
				} else {
					name = Component.translatable("event.projecthero.galactus.bar.stage3");
				}
				progress = hands != null ? 1f - stage3.crackerProgress(orbit.getGameTime()) : 1f;
				boss = true;
			}
			default -> {
				name = Component.translatable("event.projecthero.galactus.bar.orbit");
				progress = 1f;
			}
		}
		orbitBar.setName(name.copy().withStyle(ChatFormatting.LIGHT_PURPLE));
		orbitBar.setProgress(Mth.clamp(progress, 0f, 1f));
		orbitBossBar.setProgress((float) Mth.clamp(stage3.fraction(), 0, 1));
		orbitBossBar.setName(Component.translatable("entity.projecthero.galactus").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
		syncBar(orbitBar, up, true);
		syncBar(orbitBossBar, up, boss);
	}

	private static void syncBar(ServerBossEvent bar, List<ServerPlayer> up, boolean show) {
		for (ServerPlayer p : new ArrayList<>(bar.getPlayers())) {
			if (!show || !up.contains(p)) {
				bar.removePlayer(p);
			}
		}
		if (show) {
			for (ServerPlayer p : up) {
				if (!bar.getPlayers().contains(p)) {
					bar.addPlayer(p);
				}
			}
		}
	}

	// ---------------------------------------------------------------- ground helper

	public BlockPos groundAt(ServerLevel level, double x, double z) {
		BlockPos column = BlockPos.containing(x, center().getY(), z);
		if (!level.isLoaded(column)) {
			return column;
		}
		int y = GalactusTerrain.topBlockY(level, column.getX(), column.getZ(), Heightmap.Types.MOTION_BLOCKING_NO_LEAVES) + 1;
		if (Math.abs(y - center().getY()) > 24) {
			y = center().getY() + 1;
		}
		return new BlockPos(column.getX(), Math.max(y, level.getMinBuildHeight() + 1), column.getZ());
	}

	// ---------------------------------------------------------------- commands

	/** {@code /projecthero raid advance galactus}: on to the next beat now. */
	public void debugAdvance(ServerLevel level, ServerPlayer by) {
		ServerLevel orbit = orbitLevel(level.getServer());
		long now = level.getGameTime();
		switch (phase) {
			case ARRIVAL -> startHerald(level);
			case HERALD -> {
				SilverSurferEntity s = surfer(level);
				if (s != null) {
					s.beginTurn(level);
				}
				if (phase == Phase.HERALD) {
					setPhase(Phase.KNEEL, now);
				}
			}
			case KNEEL -> startDevourer(level);
			case DEVOURER -> phaseStart = now - Scene.DEVOURER.length;
			case ASCENT -> phaseStart = now - Scene.ARRIVAL.length;
			case STAGE1 -> {
				stage1.staggers = GalactusConfig.stage1().staggers;
				startPull(orbit);
			}
			case PULL -> phaseStart = now - Scene.WORLDSHIP.length;
			case STAGE2 -> {
				List<ElementConverterEntity> live = stage2.live(orbit);
				if (!live.isEmpty()) {
					live.get(0).setShielded(false);
					stage2.overload.put(live.get(0).getUUID(), GalactusConfig.stage2().overloadTicks);
				}
			}
			case RISE -> phaseStart = now - Scene.RISE.length;
			case STAGE3 -> {
				if (!stage3.finale) {
					stage3.health = stage3.maxHealth * GalactusConfig.stage3().finaleFraction * 1.01;
					DevourerEntity d = devourer(orbit);
					if (d != null) {
						stage3.onHit(this, orbit, d, DevourerPartEntity.Kind.UPPER_R, orbit.damageSources().magic(), 100f);
					}
				} else if (!nullifierGiven) {
					finaleAt = orbit.getGameTime() - 100;
				} else if (by != null) {
					ServerPlayer holder = nullifierHolder(orbit);
					useNullifier(level, holder != null ? holder : by);
				}
			}
			case ENDING -> phaseStart = now - Scene.ENDING.length;
			case CONSUMED -> phaseStart = now - Scene.CONSUMED.length;
		}
	}

	// ---------------------------------------------------------------- save

	@Override
	protected void saveExtra(CompoundTag tag) {
		tag.putString("GalPhase", phase.name());
		tag.putLong("GalPhaseStart", phaseStart);
		tag.putString("GalActivator", activatorName);
		tag.putBoolean("GalBeacon", beaconCentre);
		tag.putFloat("GalYaw", sceneYaw);
		tag.put("GalFighters", uuids(fighters));
		tag.put("GalDown", uuids(down));
		if (surferId != null) {
			tag.putUUID("GalSurfer", surferId);
		}
		tag.putInt("GalSlot", slot);
		tag.put("GalStage1", stage1.save());
		tag.put("GalStage2", stage2.save());
		tag.put("GalStage3", stage3.save());
		tag.putDouble("GalConsumption", consumption);
		tag.putInt("GalParts", partsEarned);
		tag.putBoolean("GalNullGiven", nullifierGiven);
		if (nullifierUser != null) {
			tag.putUUID("GalNullUser", nullifierUser);
		}
		CompoundTag shards = new CompoundTag();
		shardsOwed.forEach((u, n) -> shards.putInt(u.toString(), n));
		tag.put("GalShards", shards);
		CompoundTag dmg = new CompoundTag();
		damageBy.forEach((u, d) -> dmg.putDouble(u.toString(), d));
		tag.put("GalDamage", dmg);
	}

	@Override
	protected void loadExtra(CompoundTag tag) {
		String p = tag.getString("GalPhase");
		phase = switch (p) {
			// v0.16 saves: restart at the Devourer's reveal
			case "TURN", "WORLDSHIP", "GALACTUS", "RETREAT" -> Phase.KNEEL;
			default -> {
				try {
					yield Phase.valueOf(p);
				} catch (IllegalArgumentException e) {
					yield Phase.ARRIVAL;
				}
			}
		};
		phaseStart = tag.getLong("GalPhaseStart");
		activatorName = tag.getString("GalActivator");
		beaconCentre = !tag.contains("GalBeacon") || tag.getBoolean("GalBeacon");
		sceneYaw = tag.getFloat("GalYaw");
		readUuids(tag.getList("GalFighters", Tag.TAG_INT_ARRAY), fighters);
		readUuids(tag.getList("GalDown", Tag.TAG_INT_ARRAY), down);
		surferId = tag.hasUUID("GalSurfer") ? tag.getUUID("GalSurfer") : null;
		slot = tag.contains("GalSlot") ? tag.getInt("GalSlot") : -1;
		stage1.load(tag.getCompound("GalStage1"));
		stage2.load(tag.getCompound("GalStage2"));
		stage3.load(tag.getCompound("GalStage3"));
		consumption = tag.getDouble("GalConsumption");
		partsEarned = tag.getInt("GalParts");
		nullifierGiven = tag.getBoolean("GalNullGiven");
		nullifierUser = tag.hasUUID("GalNullUser") ? tag.getUUID("GalNullUser") : null;
		shardsOwed.clear();
		CompoundTag shards = tag.getCompound("GalShards");
		for (String k : shards.getAllKeys()) {
			try {
				shardsOwed.put(UUID.fromString(k), shards.getInt(k));
			} catch (IllegalArgumentException ignored) {
			}
		}
		damageBy.clear();
		CompoundTag dmg = tag.getCompound("GalDamage");
		for (String k : dmg.getAllKeys()) {
			try {
				damageBy.put(UUID.fromString(k), dmg.getDouble(k));
			} catch (IllegalArgumentException ignored) {
			}
		}
		needsRebuild = true;
	}

	private static ListTag uuids(Iterable<UUID> set) {
		ListTag list = new ListTag();
		for (UUID u : set) {
			list.add(NbtUtils.createUUID(u));
		}
		return list;
	}

	private static void readUuids(ListTag list, java.util.Collection<UUID> into) {
		into.clear();
		for (int i = 0; i < list.size(); i++) {
			into.add(NbtUtils.loadUUID(list.get(i)));
		}
	}

	// ---------------------------------------------------------------- test hooks

	public boolean ownsEntity(Entity e) {
		return owns(e.getUUID()) || orbitMobs.contains(e.getUUID());
	}

	public void debugSetConsumption(ServerLevel level, double percent) {
		consumption = 0;
		addConsumption(level, percent);
	}

	public void debugSetPhase(Phase p, long now) {
		setPhase(p, now);
	}

	public void debugSkipPhaseTime(long ticks) {
		phaseStart -= ticks;
	}

	public List<Mob> ownedIn(ServerLevel level, AABB box) {
		List<Mob> out = new ArrayList<>();
		for (Mob m : liveOwnedMobs(level)) {
			if (box.contains(m.position())) {
				out.add(m);
			}
		}
		return out;
	}

	// ---------------------------------------------------------------- v0.16 compatibility (the old Galactus entities still compile; v2 never spawns them)

	/** @deprecated v0.16 Act 3; the v2 raid has no pylons. */
	@Deprecated
	public int pylonsStanding(ServerLevel level) {
		return 0;
	}

	@Deprecated
	public void onPylonDestroyed(ServerLevel level, PowerPylonEntity p) {
	}

	@Deprecated
	public boolean weakPointBroken(int kind) {
		return false;
	}

	@Deprecated
	public float weakPointHealth(int kind) {
		return (float) GalactusConfig.weakPointHealthFor(kind, fighterCount());
	}

	@Deprecated
	public void setWeakPointHealth(int kind, float hp) {
	}

	@Deprecated
	public void onWeakPointBroken(ServerLevel level, int kind) {
	}

	@Deprecated
	public void checkNullifierMoment(ServerLevel level) {
	}

	@Deprecated
	public double[] galactusWalkBand() {
		return new double[] { 52.0, 74.0 };
	}

	@Deprecated
	public Vec3 nullifierSpot() {
		return null;
	}

	@Deprecated
	public void onGrabbed(ServerLevel level, LivingEntity victim) {
	}

	@Deprecated
	public void onGripBroken(ServerLevel level, LivingEntity victim) {
	}

	@Deprecated
	public void transmute(ServerLevel level, Vec3 at, int radius, int life) {
	}

	@Deprecated
	public int punishersAlive(ServerLevel level) {
		return level.dimension() == GalactusOrbit.DIMENSION ? orbitPunishers(level).size() : 0;
	}

	@Deprecated
	public void summonPunishers(ServerLevel level, int n, ElementConverterEntity guardOf) {
	}

	public void onConverterDestroyed(ServerLevel level, ElementConverterEntity conv) {
		// counted by Stage 2 when it reverses one (the converter's own death calls here too)
	}

	public void onPunisherDied(ServerLevel level, PunisherRobotEntity p) {
	}

	/** v0.16 Galactus (never spawned by the v2 raid). */
	@Deprecated
	public GalactusEntity galactus(ServerLevel level) {
		return null;
	}
}
