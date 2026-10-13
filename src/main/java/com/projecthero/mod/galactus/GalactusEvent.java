package com.projecthero.mod.galactus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.event.EventBossBar;
import com.projecthero.mod.event.EventConfig;
import com.projecthero.mod.event.EventInstance;
import com.projecthero.mod.event.EventManager;
import com.projecthero.mod.event.EventSavedData;
import com.projecthero.mod.event.EventState;
import com.projecthero.mod.event.raid.ZombieRaidNetworking;
import com.projecthero.mod.galactus.block.GalactusBlocks;
import com.projecthero.mod.galactus.block.HeraldsBeaconBlock;
import com.projecthero.mod.galactus.entity.ElementConverterEntity;
import com.projecthero.mod.galactus.entity.GalactusEntity;
import com.projecthero.mod.galactus.entity.GalactusEntityTypes;
import com.projecthero.mod.galactus.entity.GalactusWeakPointEntity;
import com.projecthero.mod.galactus.entity.PowerPylonEntity;
import com.projecthero.mod.galactus.entity.PunisherRobotEntity;
import com.projecthero.mod.galactus.entity.SilverSurferEntity;
import com.projecthero.mod.galactus.entity.WorldshipEntity;
import com.projecthero.mod.galactus.item.GalactusItems;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
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

/**
 * v0.16.1: <b>Galactus: The Devourer</b> -- a cosmic signal reaches the Devourer of Worlds and his Herald comes first. Built
 * on the world-event framework ({@link EventInstance}: persistence, owned-mob tracking and cleanup, pause / abandon),
 * structured like the Frost Giant Assault.
 *
 * <pre>
 * ARRIVAL (the sky turns deep violet, stars in daylight; the Herald descends)
 *   -> HERALD: the Silver Surfer duel; at 10% he stops ...
 *   -> TURN: ... and turns: an ally (protected) for the rest of the fight
 *   -> WORLDSHIP: the Worldship looms; 3-5 Element Converters crash down and eat the land (World Consumption rises),
 *        each guarded by Punishers (shielded while they live); each destroyed drops an Ultimate Nullifier part
 *   -> GALACTUS: the ~60-block Devourer at the arena's edge; four Power Pylons feed him (regeneration, sealed weak
 *        points); break them, break his weak points (or wear him down) -> the Surfer assembles the Nullifier from the
 *        parts -> hold it on him for 3 s
 *   -> RETREAT (he raises a hand, speaks, is gone in a burst of Power Cosmic; the Worldship leaves; the land comes back)
 *        -> the beacon becomes the reward chest + first-clear items
 * World Consumption at 100% -> CONSUMED (the cinematic) -> everything reverts, a loss.  Every fighter down at once: a loss.
 * </pre>
 * He cannot be killed: at 1 health he holds until the Nullifier. Every changed block is in {@link GalactusTerrain}.
 */
public class GalactusEvent extends EventInstance {
	public static final String TYPE_ID = "galactus";
	public static final int SKY_COLOR = 0x2B0B4F;
	public static final int SKY_COLOR_CONSUMED = 0xE8D8FF;
	static final int HERALD_RESPAWN_TICKS = 200;

	public enum Phase { ARRIVAL, HERALD, TURN, WORLDSHIP, GALACTUS, RETREAT, CONSUMED }

	private Phase phase = Phase.ARRIVAL;
	private long phaseStart = -1;
	private final Set<UUID> fighters = new HashSet<>();
	private final Set<UUID> down = new HashSet<>();
	private String activatorName = "";
	private boolean beaconCentre = true;
	private UUID surferId;
	private UUID galactusId;
	private final List<UUID> converters = new ArrayList<>();
	private int converterTotal;
	private final List<UUID> pylons = new ArrayList<>();
	private final float[] weakHp = new float[GalactusWeakPointEntity.KINDS];
	private final boolean[] weakBroken = new boolean[GalactusWeakPointEntity.KINDS];
	private double consumption;
	private int partsEarned;
	private final List<UUID> partDrops = new ArrayList<>();
	private boolean nullifierMoment;
	private boolean nullifierAssembled;
	private long partsShortSince = -1;
	private final List<Zone> zones = new ArrayList<>();
	private int lastLineKey = -1;
	private UUID nullifierUser;

	/** A Matter Transmutation patch: it hurts anyone standing in it until it expires. */
	public record Zone(Vec3 centre, double radius, long expiresAt) {
	}

	private transient EventBossBar bar;
	private transient EventBossBar bossBar;
	private transient UUID worldshipId;
	private transient long lastSkySent = -1000;
	private transient int lastSkyKey = -1;
	private transient long missingSince = -1;
	/** Test hooks. */
	public transient boolean instant;
	public transient double converterRingOverride = -1;
	public transient double pylonRingOverride = -1;
	public transient double galactusDistanceOverride = -1;
	/** Test hook: converters are spawned without guards. */
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

	public boolean nullifierMoment() {
		return nullifierMoment;
	}

	public boolean nullifierAssembled() {
		return nullifierAssembled;
	}

	public boolean weakPointBroken(int kind) {
		return weakBroken[kind];
	}

	public float weakPointHealth(int kind) {
		return weakHp[kind];
	}

	public void setWeakPointHealth(int kind, float hp) {
		weakHp[kind] = hp;
	}

	public List<UUID> converterIds() {
		return converters;
	}

	public List<ServerPlayer> fighterPlayers(ServerLevel level) {
		List<ServerPlayer> out = new ArrayList<>();
		for (UUID u : fighters) {
			if (!down.contains(u) && level.getServer().getPlayerList().getPlayer(u) instanceof ServerPlayer p && p.isAlive()) {
				out.add(p);
			}
		}
		return out;
	}

	public SilverSurferEntity surfer(ServerLevel level) {
		return surferId != null && level.getEntity(surferId) instanceof SilverSurferEntity s && s.isAlive() ? s : null;
	}

	public GalactusEntity galactus(ServerLevel level) {
		return galactusId != null && level.getEntity(galactusId) instanceof GalactusEntity g && g.isAlive() ? g : null;
	}

	public WorldshipEntity worldship(ServerLevel level) {
		return worldshipId != null && level.getEntity(worldshipId) instanceof WorldshipEntity w ? w : null;
	}

	public List<ElementConverterEntity> liveConverters(ServerLevel level) {
		List<ElementConverterEntity> out = new ArrayList<>();
		for (UUID u : converters) {
			if (level.getEntity(u) instanceof ElementConverterEntity c && c.isAlive() && !c.isDeadOrDying()) {
				out.add(c);
			}
		}
		return out;
	}

	public List<PowerPylonEntity> livePylons(ServerLevel level) {
		List<PowerPylonEntity> out = new ArrayList<>();
		for (UUID u : pylons) {
			if (level.getEntity(u) instanceof PowerPylonEntity p && p.isAlive() && !p.isDeadOrDying()) {
				out.add(p);
			}
		}
		return out;
	}

	/** Standing pylons (a pylon in an unloaded chunk still counts: it hasn't been broken). */
	public int pylonsStanding(ServerLevel level) {
		return pylons.size();
	}

	public int punishersAlive(ServerLevel level) {
		int n = 0;
		for (Mob m : liveOwnedMobs(level)) {
			if (m instanceof PunisherRobotEntity && !m.isDeadOrDying()) {
				n++;
			}
		}
		return n;
	}

	/** Where the Surfer guides the fighters to (while the Nullifier is being assembled), or null. */
	public Vec3 nullifierSpot() {
		if (!nullifierMoment || nullifierAssembled || center() == null) {
			return null;
		}
		return Vec3.atBottomCenterOf(center()).add(0, 1, 0);
	}

	public void adopt(Mob mob) {
		if (mob instanceof com.projecthero.mod.galactus.entity.GalactusMob g) {
			g.bindToEvent(id());
		}
		own(mob);
	}

	// ---------------------------------------------------------------- activation

	void activate(ServerLevel level, ServerPlayer activator, boolean beacon) {
		if (activator != null) {
			activatorName = activator.getGameProfile().getName();
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
		broadcastTitle(level, Component.translatable("title.projecthero.galactus.start").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
				Component.translatable("title.projecthero.galactus.start.sub").withStyle(ChatFormatting.GRAY));
		level.playSound(null, c, SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 3.0f, 0.4f);
		level.playSound(null, c, SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 3.0f, 0.5f);
		GalactusFx.forced(level, ParticleTypes.END_ROD, c.getX() + 0.5, c.getY() + 2, c.getZ() + 0.5, 60, 0.3, 12, 0.3, 0.05);
		sendSky(level, true);
		ProjectHeroMod.LOGGER.info("[ProjectHero] Galactus: The Devourer {} started at {} with {} fighter(s)", id(), c, fighters.size());
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
		if (phase != Phase.RETREAT && phase != Phase.CONSUMED) {
			keepTheBeacon(level);
		}
		enforceArena(level);
		ZombieRaidNetworking.pushSky(level, center(), arena(), phase == Phase.CONSUMED ? SKY_COLOR_CONSUMED : SKY_COLOR);
		sendSky(level, false);
		drawBorder(level, now);
		tetherOwnedMobs(level, arena());
		long inPhase = now - phaseStart;
		switch (phase) {
			case ARRIVAL -> {
				if (instant || inPhase >= GalactusConfig.arena().arrivalSeconds * 20L) {
					startHerald(level);
				}
			}
			case HERALD -> tickHerald(level, now);
			case TURN -> tickTurn(level, inPhase);
			case WORLDSHIP -> {
				keepWorldship(level);
				if (converters.isEmpty() && converterTotal > 0) {
					startGalactus(level);
				}
			}
			case GALACTUS -> {
				keepWorldship(level);
				if (galactus(level) == null && level.isLoaded(BlockPos.containing(galactusSite()))) {
					if (missingSince < 0) {
						missingSince = now;
					} else if (now - missingSince > 100) {
						missingSince = -1;
						spawnGalactus(level, false); // gone without the Nullifier (a reload): he comes back
					}
				} else {
					missingSince = -1;
				}
			}
			case RETREAT -> tickRetreat(level, inPhase);
			case CONSUMED -> tickConsumed(level, inPhase);
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
		for (int i = 0; i < points; i++) {
			double a = i * Math.PI * 2 / points + (now % 120) * 0.0015;
			double x = c.getX() + 0.5 + Math.cos(a) * r;
			double z = c.getZ() + 0.5 + Math.sin(a) * r;
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
			for (ServerPlayer viewer : viewers) {
				level.sendParticles(viewer, i % 2 == 0 ? GalactusFx.COSMIC : GalactusFx.SILVER, true, x, y + 0.8, z, 1, 0, 0.5, 0, 0);
				if (i % 3 == 0) {
					level.sendParticles(viewer, ParticleTypes.END_ROD, true, x, y + 1.6, z, 1, 0, 0.6, 0, 0);
				}
			}
		}
	}

	// ---------------------------------------------------------------- the sky

	/** 0 clear, 1 violet with stars, 2 consumed (white-hot), 3 clearing. */
	public int skyStage() {
		return switch (phase) {
			case CONSUMED -> 2;
			case RETREAT -> 3;
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

	private void clearSky(ServerLevel level) {
		BlockPos c = center();
		GalactusSkyPayload clear = GalactusSkyPayload.clear(c.getX(), c.getY(), c.getZ());
		for (ServerPlayer p : level.players()) {
			if (ServerPlayNetworking.canSend(p, GalactusSkyPayload.TYPE)) {
				ServerPlayNetworking.send(p, clear);
			}
		}
		ZombieRaidNetworking.clearSky(level, c);
	}

	// ---------------------------------------------------------------- Act 1: the Herald

	public SilverSurferEntity startHerald(ServerLevel level) {
		phase = Phase.HERALD;
		phaseStart = level.getGameTime();
		SilverSurferEntity s = spawnSurfer(level);
		broadcastTitle(level, Component.translatable("title.projecthero.galactus.herald").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD),
				Component.translatable("title.projecthero.galactus.herald.sub").withStyle(ChatFormatting.GRAY));
		GalactusDialogue.surfer(level, center(), "arrival");
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
		GalactusFx.burst(level, s.position(), 2.0);
		level.playSound(null, s.getX(), s.getY(), s.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 3.0f, 1.4f);
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
		if (s.isAlly()) {
			startWorldship(level);
		} else if (s.isTurning()) {
			onSurferTurning(level, s);
		}
	}

	/** The Surfer stopped at 10% (called by him): the turn begins. */
	public void onSurferTurning(ServerLevel level, SilverSurferEntity s) {
		if (phase != Phase.HERALD) {
			return;
		}
		phase = Phase.TURN;
		phaseStart = level.getGameTime();
		lastLineKey = -1;
		broadcastTitle(level, Component.translatable("title.projecthero.galactus.turn").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD),
				Component.translatable("title.projecthero.galactus.turn.sub").withStyle(ChatFormatting.GRAY));
	}

	private void tickTurn(ServerLevel level, long inPhase) {
		int key = inPhase < 10 ? 1 : inPhase < 50 ? 2 : inPhase < 90 ? 3 : 4;
		if (key != lastLineKey && key <= 3) {
			lastLineKey = key;
			GalactusDialogue.surfer(level, center(), "turn" + key);
		}
		SilverSurferEntity s = surfer(level);
		if (s != null && s.isAlly() || s == null && inPhase > GalactusConfig.herald().turnTicks + 40) {
			startWorldship(level);
		}
	}

	/** The turn is over (called by him): he is an ally from here on. */
	public void onSurferTurned(ServerLevel level, SilverSurferEntity s) {
		if (phase == Phase.HERALD || phase == Phase.TURN) {
			startWorldship(level);
		}
	}

	public void surferLine(ServerLevel level, String key) {
		GalactusDialogue.surfer(level, center(), key);
	}

	// ---------------------------------------------------------------- Act 2: the Worldship and the Element Converters

	public void startWorldship(ServerLevel level) {
		if (phase == Phase.WORLDSHIP || phase.ordinal() > Phase.WORLDSHIP.ordinal()) {
			return;
		}
		phase = Phase.WORLDSHIP;
		phaseStart = level.getGameTime();
		keepWorldship(level);
		int n = GalactusConfig.converterCountFor(fighterCount());
		converterTotal = n;
		double ring = converterRingOverride >= 0 ? converterRingOverride : GalactusConfig.converters().ring;
		double start = level.random.nextDouble() * Math.PI * 2;
		for (int i = 0; i < n; i++) {
			double a = start + i * Math.PI * 2 / n;
			spawnConverter(level, i, center().getX() + 0.5 + Math.cos(a) * ring, center().getZ() + 0.5 + Math.sin(a) * ring);
		}
		broadcastTitle(level, Component.translatable("title.projecthero.galactus.worldship").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
				Component.translatable("title.projecthero.galactus.worldship.sub", n).withStyle(ChatFormatting.GRAY));
		GalactusDialogue.galactus(level, center(), "hunger");
		level.playSound(null, center(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 3.0f, 0.4f);
	}

	private void keepWorldship(ServerLevel level) {
		if (worldship(level) != null || !level.isLoaded(center())) {
			return;
		}
		WorldshipEntity w = GalactusEntityTypes.WORLDSHIP.create(level);
		if (w == null) {
			return;
		}
		BlockPos c = center();
		w.moveTo(c.getX() + 0.5, c.getY() + GalactusConfig.arena().worldshipHeight, c.getZ() + 0.5, 0f, 0f);
		level.addFreshEntity(w);
		worldshipId = w.getUUID();
	}

	public ElementConverterEntity spawnConverter(ServerLevel level, int index, double x, double z) {
		ElementConverterEntity conv = GalactusEntityTypes.ELEMENT_CONVERTER.create(level);
		if (conv == null) {
			return null;
		}
		BlockPos site = groundAt(level, x, z);
		conv.moveTo(site.getX() + 0.5, site.getY(), site.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
		conv.configure(index, GalactusConfig.converterHealthFor(fighterCount()));
		adopt(conv);
		level.addFreshEntity(conv);
		if (!instant) {
			conv.beginFall(site.getY() + 70, GalactusConfig.converters().fallTicks);
		} else {
			conv.landNow();
		}
		converters.add(conv.getUUID());
		if (!noGuards) {
			for (int g = 0; g < GalactusConfig.converters().guards; g++) {
				summonPunishers(level, 1, conv);
			}
		}
		conv.setShielded(!noGuards && GalactusConfig.converters().guards > 0);
		return conv;
	}

	/** {@code n} Punishers drop from the Worldship: guards of {@code guardOf}, or (null) free hunters round the arena. */
	public void summonPunishers(ServerLevel level, int n, ElementConverterEntity guardOf) {
		for (int i = 0; i < n; i++) {
			if (guardOf == null && punishersAlive(level) >= GalactusConfig.summons().cap) {
				return;
			}
			PunisherRobotEntity p = GalactusEntityTypes.PUNISHER.create(level);
			if (p == null) {
				return;
			}
			Vec3 near;
			if (guardOf != null) {
				double a = level.random.nextDouble() * Math.PI * 2;
				near = guardOf.position().add(Math.cos(a) * 5, 0, Math.sin(a) * 5);
			} else {
				double a = level.random.nextDouble() * Math.PI * 2;
				double r = 10 + level.random.nextDouble() * (arena() * 0.5);
				near = Vec3.atBottomCenterOf(center()).add(Math.cos(a) * r, 0, Math.sin(a) * r);
			}
			BlockPos g = groundAt(level, near.x, near.z);
			p.moveTo(g.getX() + 0.5, g.getY(), g.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
			p.finalizeSpawn(level, level.getCurrentDifficultyAt(g), MobSpawnType.EVENT, null);
			p.configure(GalactusConfig.punisherHealthFor(fighterCount()));
			if (guardOf != null) {
				p.guard(guardOf);
			}
			adopt(p);
			level.addFreshEntity(p);
			GalactusFx.forced(level, ParticleTypes.END_ROD, g.getX() + 0.5, g.getY() + 12, g.getZ() + 0.5, 30, 0.3, 12, 0.3, 0.02);
			GalactusFx.burst(level, Vec3.atBottomCenterOf(g), 1.5);
		}
	}

	public void onConverterDestroyed(ServerLevel level, ElementConverterEntity conv) {
		if (!converters.remove(conv.getUUID())) {
			return;
		}
		GalactusTerrain.get(level).markGroup(id(), conv.index());
		partsEarned++;
		dropPart(level, conv.position().add(0, 1.0, 0));
		int left = converters.size();
		broadcast(level, Component.translatable("message.projecthero.galactus.converter_down", converterTotal - left, converterTotal)
				.withStyle(ChatFormatting.LIGHT_PURPLE));
		if (left > 0) {
			surferLine(level, "part");
		}
	}

	public void onPunisherDied(ServerLevel level, PunisherRobotEntity p) {
		// the guard count is re-read every half second (updateShields)
	}

	private void dropPart(ServerLevel level, Vec3 at) {
		ItemEntity item = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(GalactusItems.NULLIFIER_PART));
		item.setUnlimitedLifetime();
		item.setInvulnerable(true);
		item.setDeltaMovement(0, 0.25, 0);
		level.addFreshEntity(item);
		partDrops.add(item.getUUID());
		GalactusFx.forced(level, ParticleTypes.END_ROD, at.x, at.y, at.z, 40, 0.2, 4.0, 0.2, 0.02);
	}

	private void updateShields(ServerLevel level) {
		List<ElementConverterEntity> live = liveConverters(level);
		if (live.isEmpty()) {
			return;
		}
		Set<UUID> guarded = new HashSet<>();
		for (Mob m : liveOwnedMobs(level)) {
			if (m instanceof PunisherRobotEntity p && !p.isDeadOrDying() && p.guardOf() != null) {
				guarded.add(p.guardOf());
			}
		}
		for (ElementConverterEntity c : live) {
			c.setShielded(guarded.contains(c.getUUID()));
		}
	}

	/** One tick of every converter's appetite (budgeted; loaded chunks only). */
	private void tickConverters(ServerLevel level, long now) {
		GalactusConfig.Converters cfg = GalactusConfig.converters();
		int budget = Math.max(1, cfg.blocksPerTick);
		GalactusTerrain terrain = GalactusTerrain.get(level);
		boolean terrainDamage = GalactusConfig.arena().terrainDamage;
		BlockState into = GalactusBlocks.CONSUMED_GROUND.defaultBlockState();
		for (ElementConverterEntity c : liveConverters(level)) {
			if (!c.landed()) {
				continue;
			}
			if (c.nextGrowAt < 0) {
				c.nextGrowAt = now + cfg.growTicks;
			}
			if (now >= c.nextGrowAt && c.radius < cfg.maxRadius) {
				c.radius++;
				c.nextGrowAt = now + cfg.growTicks;
			}
			if (!terrainDamage) {
				if (now % 4 == 0 && c.radius > 0) {
					double a = level.random.nextDouble() * Math.PI * 2;
					double r = Math.sqrt(level.random.nextDouble()) * c.radius;
					level.sendParticles(GalactusFx.VOID, c.getX() + Math.cos(a) * r, c.getY() + 0.2, c.getZ() + Math.sin(a) * r, 3, 0.4, 0.1, 0.4, 0);
				}
				continue;
			}
			int end = Spiral.RING_END[Math.max(0, Math.min(c.radius, Spiral.MAX))];
			int base = c.getBlockX();
			int baseZ = c.getBlockZ();
			for (int col = 0; col < cfg.columnsPerTick && c.cursor < end && budget > 0; col++) {
				int i = c.cursor;
				int x = base + Spiral.OFFSETS[i * 2];
				int z = baseZ + Spiral.OFFSETS[i * 2 + 1];
				if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) {
					break; // wait for it; never force a load
				}
				c.cursor++;
				BlockPos column = new BlockPos(x, c.getBlockY(), z);
				if (GalactusTerrain.nearBlockEntity(level, column, cfg.blockEntityClearance)) {
					continue;
				}
				budget -= terrain.consumeColumn(level, id(), c.index(), x, z, Math.max(1, cfg.depth), into, budget);
			}
		}
	}

	/** Gametest hook: run {@code ticks} of converter appetite now (with the radius already grown to {@code radius}). */
	public int debugConsume(ServerLevel level, ElementConverterEntity c, int radius, int steps) {
		c.radius = Math.max(c.radius, radius);
		c.nextGrowAt = level.getGameTime() + 100000;
		int before = GalactusTerrain.get(level).count(id());
		for (int i = 0; i < steps; i++) {
			tickConverters(level, level.getGameTime());
		}
		return GalactusTerrain.get(level).count(id()) - before;
	}

	/** Column offsets (dx, dz) within {@link #MAX} blocks, nearest first, for the converters' outward walk. */
	static final class Spiral {
		static final int MAX = 24;
		static final int[] OFFSETS;
		static final int[] RING_END;

		static {
			List<int[]> list = new ArrayList<>();
			for (int dx = -MAX; dx <= MAX; dx++) {
				for (int dz = -MAX; dz <= MAX; dz++) {
					if (dx * dx + dz * dz <= MAX * MAX) {
						list.add(new int[] { dx, dz });
					}
				}
			}
			list.sort((a, b) -> Integer.compare(a[0] * a[0] + a[1] * a[1], b[0] * b[0] + b[1] * b[1]));
			OFFSETS = new int[list.size() * 2];
			for (int i = 0; i < list.size(); i++) {
				OFFSETS[i * 2] = list.get(i)[0];
				OFFSETS[i * 2 + 1] = list.get(i)[1];
			}
			RING_END = new int[MAX + 1];
			for (int r = 0; r <= MAX; r++) {
				int count = 0;
				for (int i = 0; i < list.size(); i++) {
					int[] o = list.get(i);
					if (o[0] * o[0] + o[1] * o[1] <= r * r) {
						count = i + 1;
					}
				}
				RING_END[r] = count;
			}
		}

		private Spiral() {
		}
	}

	// ---------------------------------------------------------------- World Consumption

	/** Percent per second right now: each landed converter, then each standing pylon. */
	public double consumptionRate(ServerLevel level) {
		GalactusConfig.Consumption cfg = GalactusConfig.consumption();
		if (phase == Phase.WORLDSHIP) {
			int landed = 0;
			for (ElementConverterEntity c : liveConverters(level)) {
				if (c.landed()) {
					landed++;
				}
			}
			return landed * cfg.perConverterPerMinute / 60.0;
		}
		if (phase == Phase.GALACTUS) {
			return pylons.size() * cfg.perPylonPerMinute / 60.0;
		}
		return 0.0;
	}

	public void addConsumption(ServerLevel level, double percent) {
		if (phase == Phase.CONSUMED || phase == Phase.RETREAT) {
			return;
		}
		double before = consumption;
		consumption = Mth.clamp(consumption + percent, 0.0, 100.0);
		for (int mark : new int[] { 50, 75, 90 }) {
			if (before < mark && consumption >= mark) {
				broadcastTitle(level, Component.translatable("title.projecthero.galactus.consumption", mark).withStyle(ChatFormatting.LIGHT_PURPLE),
						Component.translatable("title.projecthero.galactus.consumption.sub").withStyle(ChatFormatting.GRAY));
			}
		}
		if (consumption >= 100.0) {
			beginConsumed(level);
		}
	}

	private void beginConsumed(ServerLevel level) {
		if (phase == Phase.CONSUMED) {
			return;
		}
		phase = Phase.CONSUMED;
		phaseStart = level.getGameTime();
		broadcastTitle(level, Component.translatable("title.projecthero.galactus.consumed").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD),
				Component.translatable("title.projecthero.galactus.consumed.sub").withStyle(ChatFormatting.GRAY));
		GalactusDialogue.galactus(level, center(), "consumed");
		level.playSound(null, center(), SoundEvents.ENDER_DRAGON_DEATH, SoundSource.HOSTILE, 4.0f, 0.4f);
		sendSky(level, true);
	}

	private void tickConsumed(ServerLevel level, long inPhase) {
		BlockPos c = center();
		if (inPhase % 20 == 0) {
			GalactusFx.shake(level, Vec3.atCenterOf(c), 1.5f, 24, arena() + 32);
			GalactusFx.forced(level, ParticleTypes.FLASH, c.getX() + 0.5, c.getY() + 20, c.getZ() + 0.5, 1, 0, 0, 0, 0);
			GalactusFx.forced(level, GalactusFx.COSMIC, c.getX() + 0.5, c.getY() + 4, c.getZ() + 0.5, 120, arena() * 0.4, 6, arena() * 0.4, 0.2);
		}
		if (inPhase >= GalactusConfig.consumption().lossCinematicTicks) {
			fail(level, Component.translatable("message.projecthero.galactus.defeat").withStyle(ChatFormatting.DARK_PURPLE));
		}
	}

	// ---------------------------------------------------------------- Act 3: Galactus

	private Vec3 galactusSite() {
		BlockPos c = center();
		double d = galactusDistanceOverride >= 0 ? galactusDistanceOverride : GalactusConfig.galactus().standDistance;
		return new Vec3(c.getX() + 0.5, c.getY(), c.getZ() + 0.5 - d);
	}

	public GalactusEntity startGalactus(ServerLevel level) {
		phase = Phase.GALACTUS;
		phaseStart = level.getGameTime();
		for (int k = 0; k < GalactusWeakPointEntity.KINDS; k++) {
			weakHp[k] = (float) GalactusConfig.weakPointHealthFor(k, fighterCount());
			weakBroken[k] = false;
		}
		GalactusEntity g = spawnGalactus(level, true);
		int n = Math.max(0, GalactusConfig.pylons().count);
		double ring = pylonRingOverride >= 0 ? pylonRingOverride : GalactusConfig.pylons().ring;
		for (int i = 0; i < n; i++) {
			double a = Math.PI / 4 + i * Math.PI * 2 / n;
			BlockPos site = groundAt(level, center().getX() + 0.5 + Math.cos(a) * ring, center().getZ() + 0.5 + Math.sin(a) * ring);
			PowerPylonEntity p = GalactusEntityTypes.POWER_PYLON.create(level);
			if (p == null) {
				continue;
			}
			p.moveTo(site.getX() + 0.5, site.getY(), site.getZ() + 0.5, 0f, 0f);
			p.configure(GalactusConfig.pylonHealthFor(fighterCount()));
			adopt(p);
			level.addFreshEntity(p);
			pylons.add(p.getUUID());
			GalactusFx.burst(level, p.position().add(0, 3, 0), 1.5);
		}
		broadcastTitle(level, Component.translatable("title.projecthero.galactus.galactus").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
				Component.translatable("title.projecthero.galactus.galactus.sub").withStyle(ChatFormatting.GRAY));
		GalactusDialogue.galactus(level, center(), "arrival");
		level.playSound(null, center(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 4.0f, 0.3f);
		return g;
	}

	private GalactusEntity spawnGalactus(ServerLevel level, boolean fresh) {
		GalactusEntity g = GalactusEntityTypes.GALACTUS.create(level);
		if (g == null) {
			return null;
		}
		Vec3 site = galactusSite();
		BlockPos ground = groundAt(level, site.x, site.z);
		BlockPos c = center();
		float yaw = (float) (Mth.atan2(c.getZ() + 0.5 - site.z, c.getX() + 0.5 - site.x) * Mth.RAD_TO_DEG) - 90f;
		g.moveTo(site.x, ground.getY(), site.z, yaw, 0f);
		g.setYBodyRot(yaw);
		g.setYHeadRot(yaw);
		g.configure(GalactusConfig.galactusHealthFor(fighterCount()));
		adopt(g);
		level.addFreshEntity(g);
		if (!instant) {
			g.beginArrival(fresh ? 80 : 20);
		}
		galactusId = g.getUUID();
		GalactusFx.burst(level, g.position().add(0, 20, 0), 6.0);
		GalactusFx.shake(level, g.position(), 1.5f, 40, 200);
		return g;
	}

	public void onPylonDestroyed(ServerLevel level, PowerPylonEntity p) {
		if (!pylons.remove(p.getUUID())) {
			return;
		}
		broadcast(level, Component.translatable("message.projecthero.galactus.pylon_down", pylons.size()).withStyle(ChatFormatting.LIGHT_PURPLE));
		if (pylons.isEmpty()) {
			broadcastTitle(level, Component.translatable("title.projecthero.galactus.unsealed").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
					Component.translatable("title.projecthero.galactus.unsealed.sub").withStyle(ChatFormatting.GRAY));
			surferLine(level, "unsealed");
		}
	}

	public void onWeakPointBroken(ServerLevel level, int kind) {
		weakBroken[kind] = true;
		weakHp[kind] = 0f;
		int broken = 0;
		for (boolean b : weakBroken) {
			if (b) {
				broken++;
			}
		}
		broadcast(level, Component.translatable("message.projecthero.galactus.weak_point_broken",
				Component.translatable("entity.projecthero.galactus_weak_point." + kind), broken, GalactusWeakPointEntity.KINDS)
				.withStyle(ChatFormatting.GOLD));
		checkNullifierMoment(level);
	}

	public void onGrabbed(ServerLevel level, LivingEntity victim) {
		if (victim instanceof ServerPlayer p) {
			p.displayClientMessage(Component.translatable("message.projecthero.galactus.grabbed").withStyle(ChatFormatting.LIGHT_PURPLE), true);
		}
		broadcast(level, Component.translatable("message.projecthero.galactus.grabbed_all", victim.getDisplayName()).withStyle(ChatFormatting.LIGHT_PURPLE));
	}

	public void onGripBroken(ServerLevel level, LivingEntity victim) {
		broadcast(level, Component.translatable("message.projecthero.galactus.grip_broken", victim.getDisplayName()).withStyle(ChatFormatting.GOLD));
	}

	/** Every weak point broken, or his health worn down: the Surfer starts on the Ultimate Nullifier. */
	public void checkNullifierMoment(ServerLevel level) {
		if (nullifierMoment || phase != Phase.GALACTUS) {
			return;
		}
		GalactusEntity g = galactus(level);
		boolean allBroken = true;
		for (boolean b : weakBroken) {
			allBroken &= b;
		}
		boolean low = g != null && g.getHealth() <= Math.max(1.0f, g.getMaxHealth() * GalactusConfig.galactus().nullifierAtFraction);
		if (!allBroken && !low) {
			return;
		}
		nullifierMoment = true;
		broadcastTitle(level, Component.translatable("title.projecthero.galactus.nullifier").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
				Component.translatable("title.projecthero.galactus.nullifier.sub", partsEarned).withStyle(ChatFormatting.GRAY));
		surferLine(level, "nullifier");
		tryAssemble(level);
	}

	/** The Surfer assembles the Nullifier once fighters hold every part (or straight away if there were none). */
	private void tryAssemble(ServerLevel level) {
		if (!nullifierMoment || nullifierAssembled || phase != Phase.GALACTUS) {
			return;
		}
		List<ServerPlayer> holders = fighterPlayers(level);
		int held = 0;
		ServerPlayer best = null;
		int bestCount = -1;
		for (ServerPlayer p : holders) {
			int n = p.getInventory().countItem(GalactusItems.NULLIFIER_PART);
			held += n;
			if (n > bestCount) {
				bestCount = n;
				best = p;
			}
		}
		if (best == null || held < partsEarned) {
			return;
		}
		int toTake = partsEarned;
		for (ServerPlayer p : holders) {
			toTake -= removeItems(p, GalactusItems.NULLIFIER_PART, toTake);
		}
		nullifierAssembled = true;
		ItemStack nullifier = new ItemStack(GalactusItems.ULTIMATE_NULLIFIER);
		if (!best.getInventory().add(nullifier)) {
			best.drop(nullifier, false);
		}
		SilverSurferEntity s = surfer(level);
		Vec3 from = s != null ? s.position().add(0, 1, 0) : Vec3.atCenterOf(center());
		GalactusFx.line(level, GalactusFx.SILVER, from, best.position().add(0, 1, 0), 0.6);
		GalactusFx.burst(level, best.position().add(0, 1, 0), 1.5);
		level.playSound(null, best.getX(), best.getY(), best.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.5f, 0.6f);
		broadcastTitle(level, Component.translatable("title.projecthero.galactus.assembled").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
				Component.translatable("title.projecthero.galactus.assembled.sub", best.getDisplayName()).withStyle(ChatFormatting.GRAY));
		surferLine(level, "assembled");
	}

	private static int removeItems(ServerPlayer p, net.minecraft.world.item.Item item, int max) {
		int removed = 0;
		var inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize() && removed < max; i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(item)) {
				int take = Math.min(max - removed, s.getCount());
				s.shrink(take);
				removed += take;
			}
		}
		return removed;
	}

	/** Parts lost to lava or the void come back near the Surfer (after {@link GalactusConfig.Nullifier#lostPartTicks}). */
	private void watchParts(ServerLevel level, long now) {
		if (partsEarned <= 0 || nullifierAssembled) {
			return;
		}
		int held = 0;
		for (ServerPlayer p : fighterPlayers(level)) {
			held += p.getInventory().countItem(GalactusItems.NULLIFIER_PART);
		}
		int lying = 0;
		for (Iterator<UUID> it = partDrops.iterator(); it.hasNext();) {
			UUID u = it.next();
			Entity e = level.getEntity(u);
			if (e instanceof ItemEntity ie && ie.isAlive()) {
				lying += ie.getItem().getCount();
			} else if (e == null && level.isLoaded(center())) {
				it.remove();
			}
		}
		if (held + lying >= partsEarned) {
			partsShortSince = -1;
			return;
		}
		if (partsShortSince < 0) {
			partsShortSince = now;
		} else if (now - partsShortSince >= GalactusConfig.nullifier().lostPartTicks) {
			partsShortSince = -1;
			SilverSurferEntity s = surfer(level);
			Vec3 at = s != null ? s.position() : Vec3.atCenterOf(center()).add(0, 2, 0);
			for (int i = held + lying; i < partsEarned; i++) {
				dropPart(level, at);
			}
			surferLine(level, "recovered");
		}
	}

	/** Matter Transmutation: the patch round {@code at} becomes cosmic hazard (blocks only with terrain damage on). */
	public void transmute(ServerLevel level, Vec3 at, int radius, int life) {
		zones.add(new Zone(at, radius + 0.5, level.getGameTime() + life));
		if (!GalactusConfig.arena().terrainDamage) {
			return;
		}
		GalactusTerrain terrain = GalactusTerrain.get(level);
		BlockState hazard = GalactusBlocks.COSMIC_HAZARD.defaultBlockState();
		BlockPos c = BlockPos.containing(at);
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if (dx * dx + dz * dz > radius * radius + radius) {
					continue;
				}
				int x = c.getX() + dx;
				int z = c.getZ() + dz;
				if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) {
					continue;
				}
				int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
				if (Math.abs(top - c.getY()) > 4) {
					continue;
				}
				BlockPos p = new BlockPos(x, top, z);
				BlockState s = level.getBlockState(p);
				if ((GalactusTerrain.consumable(s) || s.is(GalactusBlocks.CONSUMED_GROUND))
						&& !GalactusTerrain.nearBlockEntity(level, p, GalactusConfig.converters().blockEntityClearance)) {
					terrain.place(level, id(), GalactusTerrain.GROUP_HAZARD, p, hazard, life);
				}
			}
		}
	}

	public List<Zone> zones() {
		return zones;
	}

	private void tickZones(ServerLevel level, long now) {
		zones.removeIf(z -> now >= z.expiresAt());
		if (zones.isEmpty() || now % 10 != 0) {
			return;
		}
		float dmg = GalactusConfig.transmute().damagePerSecond * 0.5f;
		for (Zone z : zones) {
			if (now % 20 == 0) {
				GalactusFx.forced(level, GalactusFx.COSMIC_SMALL, z.centre().x, z.centre().y + 0.3, z.centre().z, 8, z.radius() * 0.5, 0.1,
						z.radius() * 0.5, 0.0);
			}
			for (LivingEntity e : GalactusCombat.targetsNear(level, z.centre(), z.radius() + 0.5)) {
				double dy = e.getY() - z.centre().y;
				if (dy > -1.5 && dy < 1.6 && e.onGround()) {
					GalactusCombat.hit(level, level.damageSources().magic(), e, dmg);
				}
			}
		}
	}

	// ---------------------------------------------------------------- the end: the Nullifier

	/** {@code by} has held the Nullifier on him for 3 s: he retreats. True if it worked. */
	public boolean useNullifier(ServerLevel level, ServerPlayer by) {
		if (phase != Phase.GALACTUS) {
			return false;
		}
		GalactusEntity g = galactus(level);
		if (g == null) {
			return false;
		}
		nullifierUser = by.getUUID();
		phase = Phase.RETREAT;
		phaseStart = level.getGameTime();
		lastLineKey = -1;
		g.beginRetreat(level);
		for (Mob m : liveOwnedMobs(level)) {
			if (m instanceof PunisherRobotEntity) {
				m.kill();
			}
		}
		GalactusFx.line(level, GalactusFx.SILVER, by.getEyePosition(), g.facePos(), 1.5);
		GalactusFx.burst(level, g.facePos(), 5.0);
		level.playSound(null, by.getX(), by.getY(), by.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 3.0f, 0.5f);
		broadcastTitle(level, Component.translatable("title.projecthero.galactus.nullified").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
				Component.translatable("title.projecthero.galactus.nullified.sub", by.getDisplayName()).withStyle(ChatFormatting.WHITE));
		sendSky(level, true);
		return true;
	}

	private void tickRetreat(ServerLevel level, long inPhase) {
		int hit = com.projecthero.mod.galactus.GalactusAnims.GALACTUS_RETREAT.hit();
		int len = com.projecthero.mod.galactus.GalactusAnims.GALACTUS_RETREAT.length();
		if (lastLineKey < 1 && inPhase >= 10) {
			lastLineKey = 1;
			GalactusDialogue.galactus(level, center(), "retreat1");
		}
		if (lastLineKey < 2 && inPhase >= hit) {
			lastLineKey = 2;
			GalactusDialogue.galactus(level, center(), "retreat2");
		}
		if (lastLineKey < 3 && inPhase >= len) {
			lastLineKey = 3;
			GalactusEntity g = galactus(level);
			if (g != null) {
				GalactusFx.burst(level, g.position().add(0, 30, 0), 8.0);
				GalactusFx.shake(level, g.position(), 1.5f, 30, 220);
				level.playSound(null, g.getX(), g.getY() + 30, g.getZ(), SoundEvents.ENDER_DRAGON_DEATH, SoundSource.HOSTILE, 5.0f, 1.2f);
				disown(g.getUUID());
				g.discard();
			}
			WorldshipEntity w = worldship(level);
			if (w != null) {
				w.play(com.projecthero.mod.galactus.GalactusAnims.WORLDSHIP_LEAVE);
			}
			GalactusTerrain.get(level).markAll(id());
		}
		if (lastLineKey < 4 && inPhase >= len + 40) {
			lastLineKey = 4;
			surferLine(level, "farewell");
		}
		if (inPhase >= len + com.projecthero.mod.galactus.GalactusAnims.WORLDSHIP_LEAVE.length()) {
			complete(level);
		}
	}

	// ---------------------------------------------------------------- the server tick (every tick)

	public void serverTick(ServerLevel level) {
		if (state() != EventState.RUNNING || center() == null) {
			return;
		}
		long now = level.getGameTime();
		if (phase == Phase.WORLDSHIP) {
			tickConverters(level, now);
			if (now % 10 == 0) {
				updateShields(level);
			}
		}
		if (now % 20 == 0) {
			addConsumption(level, consumptionRate(level));
			if (phase == Phase.GALACTUS) {
				GalactusEntity g = galactus(level);
				if (g != null && !pylons.isEmpty()) {
					g.heal((float) (GalactusConfig.galactus().regenPerPylonPerSecond * pylons.size()));
					for (PowerPylonEntity p : livePylons(level)) {
						GalactusFx.line(level, GalactusFx.COSMIC_SMALL, p.position().add(0, p.getBbHeight(), 0),
								g.weakPointPos(GalactusWeakPointEntity.KIND_EMBLEM), 4.0);
					}
				}
				watchParts(level, now);
				tryAssemble(level);
			} else if (phase == Phase.WORLDSHIP) {
				watchParts(level, now);
			}
		}
		tickZones(level, now);
	}

	// ---------------------------------------------------------------- deaths and the end

	public void onFighterDied(ServerLevel level, ServerPlayer player) {
		if (!fighters.contains(player.getUUID()) || state().finished()) {
			return;
		}
		down.add(player.getUUID());
		participants().recordDeath(player.getUUID());
		broadcast(level, Component.translatable("message.projecthero.galactus.fighter_down", player.getDisplayName()).withStyle(ChatFormatting.DARK_PURPLE));
		if (down.containsAll(fighters) && phase != Phase.RETREAT) {
			fail(level, Component.translatable("message.projecthero.galactus.all_down").withStyle(ChatFormatting.DARK_PURPLE));
		}
	}

	public List<ServerPlayer> winners(ServerLevel level) {
		List<ServerPlayer> out = new ArrayList<>();
		for (UUID u : fighters) {
			if ((!down.contains(u) || u.equals(nullifierUser)) && level.getPlayerByUUID(u) instanceof ServerPlayer p && p.isAlive()) {
				out.add(p);
			}
		}
		return out;
	}

	@Override
	protected void onFinished(ServerLevel level, boolean success) {
		tearDown(level);
		BlockPos c = center();
		GalactusWorldState ws = GalactusWorldState.get(level.getServer());
		if (success) {
			List<ServerPlayer> winners = winners(level);
			BlockState there = level.getBlockState(c);
			if (there.isAir() || there.canBeReplaced() || there.is(GalactusBlocks.HERALDS_BEACON)) {
				GalactusRewards.placeChest(level, c, Math.max(1, winners.size()));
			}
			for (ServerPlayer p : winners) {
				GalactusRewards.grantClear(p);
			}
			ws.retreats++;
			level.playSound(null, c, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);
			ProjectHeroMod.LOGGER.info("[ProjectHero] Galactus {} driven off; {} fighter(s) rewarded", id(), winners.size());
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
	}

	private void tearDown(ServerLevel level) {
		clearBars(level);
		clearSky(level);
		WorldshipEntity w = worldship(level);
		if (w != null) {
			w.discard();
		}
		worldshipId = null;
		for (UUID u : partDrops) {
			if (level.getEntity(u) instanceof ItemEntity ie) {
				ie.discard();
			}
		}
		partDrops.clear();
		// the event's items never outlive it
		for (UUID u : fighters) {
			if (level.getServer().getPlayerList().getPlayer(u) instanceof ServerPlayer p) {
				removeItems(p, GalactusItems.NULLIFIER_PART, Integer.MAX_VALUE);
				removeItems(p, GalactusItems.ULTIMATE_NULLIFIER, Integer.MAX_VALUE);
			}
		}
		zones.clear();
		GalactusTerrain.get(level).markAll(id());
	}

	// ---------------------------------------------------------------- bars

	private void updateBars(ServerLevel level, long now) {
		if (bar == null) {
			bar = new EventBossBar(id(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10, false);
		}
		if (bossBar == null) {
			bossBar = new EventBossBar(new UUID(id().getMostSignificantBits() ^ 0x6A6A6A6AL, id().getLeastSignificantBits() ^ 0x5A5AL),
					BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS, false);
		}
		long inPhase = now - phaseStart;
		int pct = (int) Math.floor(consumption);
		Component name;
		float progress;
		switch (phase) {
			case ARRIVAL -> {
				int t = GalactusConfig.arena().arrivalSeconds * 20;
				name = Component.translatable("event.projecthero.galactus.bar.arrival", Math.max(0, (int) Math.ceil((t - inPhase) / 20.0)));
				progress = Mth.clamp(inPhase / (float) Math.max(1, t), 0f, 1f);
			}
			case HERALD, TURN -> {
				name = Component.translatable(phase == Phase.TURN ? "event.projecthero.galactus.bar.turn" : "event.projecthero.galactus.bar.herald");
				progress = 1f;
			}
			case WORLDSHIP -> {
				name = Component.translatable("event.projecthero.galactus.bar.worldship", pct, converterTotal - converters.size(), converterTotal);
				progress = (float) (consumption / 100.0);
			}
			case GALACTUS -> {
				int broken = 0;
				for (boolean b : weakBroken) {
					if (b) {
						broken++;
					}
				}
				name = Component.translatable(nullifierMoment ? "event.projecthero.galactus.bar.nullifier" : "event.projecthero.galactus.bar.galactus",
						pct, pylons.size(), broken, GalactusWeakPointEntity.KINDS);
				progress = (float) (consumption / 100.0);
			}
			case RETREAT -> {
				name = Component.translatable("event.projecthero.galactus.bar.retreat");
				progress = 0f;
			}
			default -> {
				name = Component.translatable("event.projecthero.galactus.bar.consumed");
				progress = 1f;
			}
		}
		bar.update(level, center(), radius(), name.copy().withStyle(ChatFormatting.LIGHT_PURPLE), progress);
		// the boss's own bar: the Herald in the duel, Galactus in Act 3
		LivingEntity boss = null;
		Component bossName = null;
		if (phase == Phase.HERALD) {
			SilverSurferEntity s = surfer(level);
			if (s != null) {
				boss = s;
				bossName = Component.translatable("entity.projecthero.silver_surfer");
			}
		} else if (phase == Phase.GALACTUS) {
			GalactusEntity g = galactus(level);
			if (g != null) {
				boss = g;
				bossName = Component.translatable("entity.projecthero.galactus");
			}
		}
		if (boss != null) {
			bossBar.update(level, center(), radius(), bossName.copy().withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD),
					boss.getHealth() / Math.max(1f, boss.getMaxHealth()));
		} else if (bossBar.isShowing()) {
			bossBar.clear(level);
		}
	}

	// ---------------------------------------------------------------- ground helper

	private BlockPos groundAt(ServerLevel level, double x, double z) {
		BlockPos column = BlockPos.containing(x, center().getY(), z);
		if (!level.isLoaded(column)) {
			return column;
		}
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
		if (Math.abs(y - center().getY()) > 24) {
			y = center().getY() + 1;
		}
		return new BlockPos(column.getX(), Math.max(y, level.getMinBuildHeight() + 1), column.getZ());
	}

	// ---------------------------------------------------------------- commands

	/** {@code /projecthero raid advancetimer galactus}: on to the next stage now. */
	public void debugAdvance(ServerLevel level, ServerPlayer by) {
		switch (phase) {
			case ARRIVAL -> startHerald(level);
			case HERALD -> {
				SilverSurferEntity s = surfer(level);
				if (s != null) {
					s.beginTurn(level);
				} else {
					startWorldship(level);
				}
			}
			case TURN -> {
				SilverSurferEntity s = surfer(level);
				if (s != null) {
					s.becomeAlly(level);
				}
				startWorldship(level);
			}
			case WORLDSHIP -> {
				for (ElementConverterEntity c : liveConverters(level)) {
					for (Mob m : liveOwnedMobs(level)) {
						if (m instanceof PunisherRobotEntity p && c.getUUID().equals(p.guardOf())) {
							p.kill();
						}
					}
					c.setShielded(false);
					c.kill();
					break;
				}
				if (liveConverters(level).isEmpty() && !converters.isEmpty()) {
					converters.clear();
				}
			}
			case GALACTUS -> {
				if (!pylons.isEmpty()) {
					for (PowerPylonEntity p : livePylons(level)) {
						p.kill();
					}
					pylons.clear();
				} else if (!nullifierMoment) {
					GalactusEntity g = galactus(level);
					for (int k = 0; k < GalactusWeakPointEntity.KINDS; k++) {
						GalactusWeakPointEntity w = g != null ? g.weakPoint(k) : null;
						if (w != null) {
							w.kill();
						} else {
							onWeakPointBroken(level, k);
						}
					}
					checkNullifierMoment(level);
				} else if (!nullifierAssembled) {
					for (int i = 0; i < partsEarned && by != null; i++) {
						by.getInventory().add(new ItemStack(GalactusItems.NULLIFIER_PART));
					}
					tryAssemble(level);
					if (!nullifierAssembled && by != null) {
						nullifierAssembled = true;
						by.getInventory().add(new ItemStack(GalactusItems.ULTIMATE_NULLIFIER));
					}
				} else if (by != null) {
					useNullifier(level, by);
				}
			}
			case RETREAT -> phaseStart = level.getGameTime() - 100000;
			case CONSUMED -> phaseStart = level.getGameTime() - 100000;
		}
	}

	// ---------------------------------------------------------------- save

	@Override
	protected void saveExtra(CompoundTag tag) {
		tag.putString("GalPhase", phase.name());
		tag.putLong("GalPhaseStart", phaseStart);
		tag.putString("GalActivator", activatorName);
		tag.putBoolean("GalBeacon", beaconCentre);
		tag.put("GalFighters", uuids(fighters));
		tag.put("GalDown", uuids(down));
		if (surferId != null) {
			tag.putUUID("GalSurfer", surferId);
		}
		if (galactusId != null) {
			tag.putUUID("GalGalactus", galactusId);
		}
		tag.put("GalConverters", uuids(converters));
		tag.putInt("GalConverterTotal", converterTotal);
		tag.put("GalPylons", uuids(pylons));
		ListTag weak = new ListTag();
		for (int k = 0; k < GalactusWeakPointEntity.KINDS; k++) {
			CompoundTag w = new CompoundTag();
			w.putFloat("Hp", weakHp[k]);
			w.putBoolean("Broken", weakBroken[k]);
			weak.add(w);
		}
		tag.put("GalWeak", weak);
		tag.putDouble("GalConsumption", consumption);
		tag.putInt("GalParts", partsEarned);
		tag.put("GalPartDrops", uuids(partDrops));
		tag.putBoolean("GalNullMoment", nullifierMoment);
		tag.putBoolean("GalNullAssembled", nullifierAssembled);
		if (nullifierUser != null) {
			tag.putUUID("GalNullUser", nullifierUser);
		}
		ListTag zl = new ListTag();
		for (Zone z : zones) {
			CompoundTag t = new CompoundTag();
			t.putDouble("X", z.centre().x);
			t.putDouble("Y", z.centre().y);
			t.putDouble("Z", z.centre().z);
			t.putDouble("R", z.radius());
			t.putLong("E", z.expiresAt());
			zl.add(t);
		}
		tag.put("GalZones", zl);
		tag.putInt("GalLine", lastLineKey);
	}

	@Override
	protected void loadExtra(CompoundTag tag) {
		try {
			phase = Phase.valueOf(tag.getString("GalPhase"));
		} catch (IllegalArgumentException e) {
			phase = Phase.ARRIVAL;
		}
		phaseStart = tag.getLong("GalPhaseStart");
		activatorName = tag.getString("GalActivator");
		beaconCentre = !tag.contains("GalBeacon") || tag.getBoolean("GalBeacon");
		readUuids(tag.getList("GalFighters", Tag.TAG_INT_ARRAY), fighters);
		readUuids(tag.getList("GalDown", Tag.TAG_INT_ARRAY), down);
		surferId = tag.hasUUID("GalSurfer") ? tag.getUUID("GalSurfer") : null;
		galactusId = tag.hasUUID("GalGalactus") ? tag.getUUID("GalGalactus") : null;
		readUuids(tag.getList("GalConverters", Tag.TAG_INT_ARRAY), converters);
		converterTotal = tag.getInt("GalConverterTotal");
		readUuids(tag.getList("GalPylons", Tag.TAG_INT_ARRAY), pylons);
		ListTag weak = tag.getList("GalWeak", Tag.TAG_COMPOUND);
		for (int k = 0; k < GalactusWeakPointEntity.KINDS; k++) {
			CompoundTag w = k < weak.size() ? weak.getCompound(k) : new CompoundTag();
			weakHp[k] = w.getFloat("Hp");
			weakBroken[k] = w.getBoolean("Broken");
		}
		consumption = tag.getDouble("GalConsumption");
		partsEarned = tag.getInt("GalParts");
		readUuids(tag.getList("GalPartDrops", Tag.TAG_INT_ARRAY), partDrops);
		nullifierMoment = tag.getBoolean("GalNullMoment");
		nullifierAssembled = tag.getBoolean("GalNullAssembled");
		nullifierUser = tag.hasUUID("GalNullUser") ? tag.getUUID("GalNullUser") : null;
		zones.clear();
		ListTag zl = tag.getList("GalZones", Tag.TAG_COMPOUND);
		for (int i = 0; i < zl.size(); i++) {
			CompoundTag t = zl.getCompound(i);
			zones.add(new Zone(new Vec3(t.getDouble("X"), t.getDouble("Y"), t.getDouble("Z")), t.getDouble("R"), t.getLong("E")));
		}
		lastLineKey = tag.getInt("GalLine");
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

	/** Test hook: does this event own {@code e}? */
	public boolean ownsEntity(Entity e) {
		return owns(e.getUUID());
	}

	/** Test hook: the consumption, set directly. */
	public void debugSetConsumption(ServerLevel level, double percent) {
		consumption = 0;
		addConsumption(level, percent);
	}

	/** Test hook: the cinematic skipped (a loss ends now). */
	public void debugFinishConsumed(ServerLevel level) {
		if (phase == Phase.CONSUMED) {
			phaseStart = level.getGameTime() - 100000;
			tickConsumed(level, 100000);
		}
	}

	/** Test hook: everything inside {@code box} that this event owns. */
	public List<Mob> ownedIn(ServerLevel level, AABB box) {
		List<Mob> out = new ArrayList<>();
		for (Mob m : liveOwnedMobs(level)) {
			if (box.contains(m.position())) {
				out.add(m);
			}
		}
		return out;
	}
}
