package com.projecthero.mod.frostgiant;

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
import com.projecthero.mod.event.raid.ZombieRaidNetworking;
import com.projecthero.mod.frostgiant.block.FrostGiantBlocks;
import com.projecthero.mod.frostgiant.block.JotunheimRuneStoneBlock;
import com.projecthero.mod.frostgiant.entity.FrostGiantEntity;
import com.projecthero.mod.frostgiant.entity.FrostGiantWarlordEntity;
import com.projecthero.mod.frostgiant.entity.FrostShamanEntity;
import com.projecthero.mod.frostgiant.entity.IceBruteEntity;
import com.projecthero.mod.frostgiant.entity.IceCasketEntity;
import com.projecthero.mod.frostgiant.entity.JotunRaiderEntity;
import com.projecthero.mod.frostgiant.entity.KingLaufeyEntity;
import com.projecthero.mod.frostgiant.entity.RimeHoundEntity;
import com.projecthero.mod.frostgiant.entity.RuneBrazierEntity;
import com.projecthero.mod.frostgiant.entity.YmirArmEntity;
import com.projecthero.mod.frostgiant.item.FrostGear;
import com.projecthero.mod.hero.power.TempBlocks;

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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: <b>The Frost Giant Assault</b> -- a rift to Jotunheim tears open above the Rift Anchor during a blizzard and
 * King Laufey's Frost Giants march through it. Built on the world-event framework ({@link EventInstance}: persistence,
 * owned-mob tracking and cleanup, pause / abandon), structured like the Ultron Uprising.
 *
 * <pre>
 * COUNTDOWN (the rift opens, the blizzard rolls in, four Rune Braziers are lit)
 *   -> WAVE 1 .. WAVE 5 (breathers between; horns from the rift)
 *   -> BOSS: King Laufey -- phase 2 opens the Casket (blizzard x2, braziers -30%), phase 3 wakes Ymir
 *        break the Casket while he is staggered, then finish him
 *   -> VICTORY (the rift collapses, the snow melts) -> the anchor becomes the reward chest
 * every fighter down at once, at any point -> defeat (the anchor shatters)
 * </pre>
 * <ul>
 *   <li><b>Arena</b>: 56 blocks round the anchor, edged in pale blue / white snow. Fighters are kept inside; anyone who
 *       walks in joins; a fighter who dies is down until they walk back in.</li>
 *   <li><b>Cold</b> ({@link #tickCold}, every tick): outside a brazier's warmth fighters build up vanilla freeze; inside one
 *       they thaw and regenerate. Fire resistance, being on fire, lava, an active fire power, the Casket in the off hand or a
 *       full Rimed set stop it. All four braziers out, or the Casket open: twice as fast.</li>
 *   <li><b>Temporary blocks</b> ({@link FrostTempBlocks}): snow layers, walls, spikes, pillars, frozen water -- tracked
 *       here, saved with the assault, and all taken back on expiry / win / loss / abort (and by {@link FrostCleanup} if
 *       the assault ever vanishes without cleaning up).</li>
 * </ul>
 */
public class FrostGiantAssault extends EventInstance {
	public static final String TYPE_ID = "frost_giants";
	public static final int MAX_WAVES = 8;
	static final int ARRIVALS_PER_TICK = 3;
	static final int STRAGGLER_TICKS = 90 * 20;
	static final int BOSS_ARRIVAL_TICKS = 70;
	static final int VICTORY_TICKS = 100;
	public static final int SKY_COLOR = 0x9FC6E8;
	public static final int SKY_COLOR_CASKET = 0x4E6A8A;

	public enum Phase { COUNTDOWN, WAVE, BREATHER, BOSS, VICTORY }

	/** Unit kinds in a wave. */
	public static final int HOUND = 0, RAIDER = 1, SHAMAN = 2, BRUTE = 3, WARLORD = 4;
	static final int KINDS = 5;

	private Phase phase = Phase.COUNTDOWN;
	private long phaseStart = -1;
	private int wave;
	private int waveTotal;
	private final int[] owed = new int[KINDS];
	private final List<UUID> braziers = new ArrayList<>();
	private UUID bossId;
	private UUID warlordId;
	private UUID ymirId;
	private boolean casketOpened;
	private boolean ymirAwake;
	private boolean casketBroken;
	private final Set<UUID> fighters = new HashSet<>();
	private final Set<UUID> down = new HashSet<>();
	private String activatorName = "";
	private final FrostTempBlocks temp;
	// Ymir
	private int ymirMove;
	private int ymirTick;
	private int ymirCooldown = 100;
	private Vec3 ymirFrom = Vec3.ZERO;
	private Vec3 ymirTo = Vec3.ZERO;
	private final Set<UUID> ymirHit = new HashSet<>();

	private transient EventBossBar bar;
	private transient int lastLit = -1;
	private transient long lastBlizzardSent = -1000;
	private transient int lastBlizzardKey = -1;
	private transient final Map<UUID, Float> coldCarry = new HashMap<>();
	private transient long iceBudgetTick = -1;
	private transient int iceBudgetUsed;
	/** Test hooks: arrivals land this far from the centre when >= 0 (and skip the beam when {@link #instantArrivals}). */
	public transient double arrivalRadiusOverride = -1;
	public transient boolean instantArrivals;
	/** Test hook: braziers on this ring instead of the configured one, when >= 0. */
	public transient double brazierRingOverride = -1;
	/** Test hook: no snow falls (keeps the test's own block counts clean). */
	public transient boolean noSnow;

	public FrostGiantAssault(UUID id) {
		super(id);
		this.temp = new FrostTempBlocks(id);
	}

	// ---------------------------------------------------------------- identity

	@Override
	public String typeId() {
		return TYPE_ID;
	}

	@Override
	public Component displayName() {
		return Component.translatable("event.projecthero.frost_giants");
	}

	public static double arena() {
		return FrostGiantConfig.arena().radius;
	}

	@Override
	public double radius() {
		return arena() + 24.0;
	}

	/** The running assault with this id, or null. */
	public static FrostGiantAssault find(ServerLevel level, UUID id) {
		if (id == null) {
			return null;
		}
		return EventSavedData.get(level).byId(id) instanceof FrostGiantAssault a && a.state().active() ? a : null;
	}

	/** Every running assault on the server. */
	public static List<FrostGiantAssault> all(MinecraftServer server) {
		List<FrostGiantAssault> out = new ArrayList<>();
		for (EventInstance e : EventManager.active(server)) {
			if (e instanceof FrostGiantAssault a) {
				out.add(a);
			}
		}
		return out;
	}

	public static int waveCount() {
		return Mth.clamp(FrostGiantConfig.waves().waveCount, 1, MAX_WAVES);
	}

	/** Wave {@code n}'s solo composition as {hounds, raiders, shamans, brutes, warlords}. */
	public static int[] composition(int n) {
		FrostGiantConfig.Waves cfg = FrostGiantConfig.waves();
		int i = Math.max(1, n) - 1;
		return new int[] { at(cfg.hounds, i), at(cfg.raiders, i), at(cfg.shamans, i), at(cfg.brutes, i), at(cfg.warlords, i) };
	}

	private static int at(int[] arr, int i) {
		if (arr == null || arr.length == 0) {
			return 0;
		}
		return Math.max(0, arr[Math.min(i, arr.length - 1)]);
	}

	/** {@code base} scaled for {@code fighters}: round(base x (1 + 0.5 x (fighters - 1))). The Warlord never multiplies. */
	public static int scaled(int base, int fighters) {
		if (base <= 0) {
			return 0;
		}
		return (int) Math.round(base * (1.0 + FrostGiantConfig.waves().perExtraFighter * Math.max(0, fighters - 1)));
	}

	public Phase phase() {
		return phase;
	}

	public int wave() {
		return wave;
	}

	public Set<UUID> fighters() {
		return fighters;
	}

	public boolean isFighter(UUID player) {
		return fighters.contains(player);
	}

	public int owed() {
		int n = 0;
		for (int o : owed) {
			n += o;
		}
		return n;
	}

	public int fighterCount() {
		return Math.max(1, fighters.size() - down.size());
	}

	public FrostTempBlocks tempBlocks() {
		return temp;
	}

	public boolean casketOpened() {
		return casketOpened;
	}

	public boolean casketBroken() {
		return casketBroken;
	}

	public boolean ymirAwake() {
		return ymirAwake;
	}

	public List<UUID> brazierIds() {
		return braziers;
	}

	public List<RuneBrazierEntity> liveBraziers(ServerLevel level) {
		List<RuneBrazierEntity> out = new ArrayList<>();
		for (UUID u : braziers) {
			if (level.getEntity(u) instanceof RuneBrazierEntity b && b.isAlive()) {
				out.add(b);
			}
		}
		return out;
	}

	public int litBraziers(ServerLevel level) {
		int n = 0;
		for (RuneBrazierEntity b : liveBraziers(level)) {
			if (b.isLit()) {
				n++;
			}
		}
		return n;
	}

	public LivingEntity boss(ServerLevel level) {
		return bossId != null && level.getEntity(bossId) instanceof LivingEntity l ? l : null;
	}

	public boolean hasRoom(ServerLevel level) {
		return ownedAlive(level) < Math.min(FrostGiantConfig.waves().enemyCap, EventConfig.framework().maxLiveMobs);
	}

	/** Live giants (Laufey and the Casket excluded). */
	public int giantsAlive(ServerLevel level) {
		int n = 0;
		for (Mob m : liveOwnedMobs(level)) {
			if (m instanceof FrostGiantEntity && !(m instanceof KingLaufeyEntity) && !m.isDeadOrDying()) {
				n++;
			}
		}
		return n;
	}

	/** Live units by kind {hounds, raiders, shamans, brutes, warlords}. */
	public int[] liveCounts(ServerLevel level) {
		int[] n = new int[KINDS];
		for (Mob m : liveOwnedMobs(level)) {
			if (m instanceof RimeHoundEntity) n[HOUND]++;
			else if (m instanceof JotunRaiderEntity) n[RAIDER]++;
			else if (m instanceof FrostShamanEntity) n[SHAMAN]++;
			else if (m instanceof IceBruteEntity) n[BRUTE]++;
			else if (m instanceof FrostGiantWarlordEntity) n[WARLORD]++;
		}
		return n;
	}

	/** Online fighters (for the bestiary). */
	public List<ServerPlayer> fighterPlayers(ServerLevel level) {
		List<ServerPlayer> out = new ArrayList<>();
		for (UUID u : fighters) {
			if (level.getServer().getPlayerList().getPlayer(u) instanceof ServerPlayer p) {
				out.add(p);
			}
		}
		return out;
	}

	/** Makes {@code mob} one of this assault's (bound + owned + cleaned up with it). */
	public void adopt(Mob mob) {
		if (mob instanceof FrostGiantEntity g) {
			g.bindToAssault(id());
		}
		own(mob);
	}

	// ---------------------------------------------------------------- activation

	/** Called by {@link FrostGiants#start}: lock in everyone nearby, light the anchor, raise the braziers, open the rift. */
	void activate(ServerLevel level, ServerPlayer activator) {
		if (activator != null) {
			activatorName = activator.getGameProfile().getName();
		}
		setState(EventState.RUNNING);
		phase = Phase.COUNTDOWN;
		phaseStart = level.getGameTime();
		BlockPos c = center();
		double r2 = arena() * arena();
		for (ServerPlayer p : level.players()) {
			if (FrostCombat.isFighter(p) && p.distanceToSqr(c.getX() + 0.5, p.getY(), c.getZ() + 0.5) <= r2) {
				fighters.add(p.getUUID());
			}
		}
		if (activator != null && FrostCombat.isFighter(activator)) {
			fighters.add(activator.getUUID());
		}
		participants().refresh(level, c, EventConfig.framework().abandonRadius, true);
		keepTheAnchor(level);
		raiseBraziers(level);
		broadcastTitle(level, Component.translatable("title.projecthero.frost_giants.start").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD),
				Component.translatable("title.projecthero.frost_giants.start.sub").withStyle(ChatFormatting.GRAY));
		Vec3 rift = riftPos();
		level.playSound(null, rift.x, c.getY() + 10, rift.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 3.0f, 0.5f);
		level.playSound(null, c, SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 1.0f, 0.7f);
		FrostFx.forced(level, ParticleTypes.FLASH, rift.x, rift.y, rift.z, 1, 0, 0, 0, 0);
		sendBlizzard(level, true);
		ProjectHeroMod.LOGGER.info("[ProjectHero] Frost Giant Assault {} started at {} with {} fighter(s)", id(), c, fighters.size());
	}

	/** The four Rune Braziers, lit, on their ring. */
	public void raiseBraziers(ServerLevel level) {
		FrostGiantConfig.Braziers cfg = FrostGiantConfig.braziers();
		int n = Math.max(0, cfg.count);
		double ring = brazierRingOverride >= 0 ? brazierRingOverride : cfg.ring;
		double start = Math.PI / 4;
		double hp = FrostGiantConfig.brazierHealthFor(fighterCount());
		for (int i = 0; i < n; i++) {
			double a = start + i * Math.PI * 2 / n;
			BlockPos site = groundAt(level, center().getX() + 0.5 + Math.cos(a) * ring, center().getZ() + 0.5 + Math.sin(a) * ring);
			RuneBrazierEntity b = FrostGiantEntityTypes.RUNE_BRAZIER.create(level);
			if (b == null) {
				continue;
			}
			b.moveTo(site.getX() + 0.5, site.getY(), site.getZ() + 0.5, 0f, 0f);
			b.bindToAssault(id());
			level.addFreshEntity(b);
			b.configure(hp);
			braziers.add(b.getUUID());
			level.sendParticles(ParticleTypes.FLAME, site.getX() + 0.5, site.getY() + 1.5, site.getZ() + 0.5, 20, 0.3, 0.3, 0.3, 0.05);
		}
		lastLit = litBraziers(level);
	}

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

	public Vec3 riftPos() {
		BlockPos c = center();
		return new Vec3(c.getX() + 0.5, c.getY() + FrostGiantConfig.arena().riftHeight, c.getZ() + 0.5);
	}

	// ---------------------------------------------------------------- tick

	@Override
	protected void onTick(ServerLevel level) {
		long now = level.getGameTime();
		if (state() == EventState.PENDING) {
			activate(level, null);
		}
		if (phaseStart < 0) {
			phaseStart = now;
		}
		if (phase != Phase.VICTORY) {
			keepTheAnchor(level);
		}
		enforceArena(level);
		ZombieRaidNetworking.pushSky(level, center(), arena(), casketOpened && !casketBroken ? SKY_COLOR_CASKET : SKY_COLOR);
		sendBlizzard(level, false);
		drawBorder(level, now);
		tetherOwnedMobs(level, arena());
		snowfall(level);
		temp.tickExpiry(level, now, 96);
		trackBraziers(level);
		long inPhase = now - phaseStart;
		switch (phase) {
			case COUNTDOWN -> {
				if (inPhase >= FrostGiantConfig.waves().countdownSeconds * 20L) {
					startWave(level, 1);
				}
			}
			case WAVE -> tickWave(level, now, inPhase);
			case BREATHER -> {
				if (inPhase >= FrostGiantConfig.waves().breatherSeconds * 20L) {
					if (wave >= waveCount()) {
						startBoss(level);
					} else {
						startWave(level, wave + 1);
					}
				}
			}
			case BOSS -> tickBoss(level);
			case VICTORY -> {
				temp.meltSnow(level, Math.max(8, temp.snowCount() / 6 + 1));
				if (inPhase >= VICTORY_TICKS) {
					complete(level);
				}
			}
		}
		if (!state().finished()) {
			updateBar(level, now);
		}
	}

	/** Every server tick (from {@link FrostGiants#serverTick}): the cold, Ymir, the ice budget. */
	public void serverTick(ServerLevel level) {
		if (state() != EventState.RUNNING || center() == null) {
			return;
		}
		tickCold(level);
		if (ymirAwake) {
			tickYmir(level);
		}
	}

	/** The anchor stays: knocked out mid-assault, it comes straight back, lit. */
	private void keepTheAnchor(ServerLevel level) {
		if (!level.isLoaded(center())) {
			return;
		}
		BlockState state = level.getBlockState(center());
		if (!state.is(FrostGiantBlocks.RUNE_STONE)) {
			level.setBlock(center(), FrostGiantBlocks.RUNE_STONE.defaultBlockState().setValue(JotunheimRuneStoneBlock.ACTIVE, true), 3);
		} else if (!state.getValue(JotunheimRuneStoneBlock.ACTIVE)) {
			level.setBlock(center(), state.setValue(JotunheimRuneStoneBlock.ACTIVE, true), 3);
		}
	}

	private double horizontalDistance(Entity e) {
		BlockPos c = center();
		double dx = e.getX() - (c.getX() + 0.5);
		double dz = e.getZ() - (c.getZ() + 0.5);
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** Fighters stay in; anyone who walks in joins; a downed fighter back inside is up again. */
	private void enforceArena(ServerLevel level) {
		double r = arena();
		for (ServerPlayer p : level.players()) {
			if (!FrostCombat.isFighter(p)) {
				continue;
			}
			double d = horizontalDistance(p);
			UUID u = p.getUUID();
			if (!fighters.contains(u)) {
				if (d <= r) {
					fighters.add(u);
					p.displayClientMessage(Component.translatable("message.projecthero.frost_giants.joined").withStyle(ChatFormatting.AQUA), true);
				}
				continue;
			}
			if (down.contains(u)) {
				if (d <= r) {
					down.remove(u);
				}
				continue;
			}
			if (d > r && d < r + 48) {
				pullBack(level, p, r - 4);
				p.displayClientMessage(Component.translatable("message.projecthero.frost_giants.no_escape").withStyle(ChatFormatting.AQUA), true);
				level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.POWDER_SNOW_BREAK, SoundSource.HOSTILE, 1.0f, 0.6f);
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
		double y = Math.max(surface, Math.min(p.getY(), surface + 16.0));
		p.teleportTo(x, y, z);
		p.fallDistance = 0;
	}

	/** The arena's edge: a ring of pale blue and white snow dust, every half second (long-range). */
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
		int points = 128;
		for (int i = 0; i < points; i++) {
			double a = i * Math.PI * 2 / points + (now % 80) * 0.002;
			double x = c.getX() + 0.5 + Math.cos(a) * r;
			double z = c.getZ() + 0.5 + Math.sin(a) * r;
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
			var dust = i % 2 == 0 ? FrostFx.PALE : FrostFx.WHITE;
			for (ServerPlayer viewer : viewers) {
				level.sendParticles(viewer, dust, true, x, y + 0.6, z, 1, 0, 0.4, 0, 0);
				level.sendParticles(viewer, dust, true, x, y + 2.0, z, 1, 0, 0.4, 0, 0);
				if (i % 3 == 0) {
					// full-bright, so the edge still reads at night (dust takes the world's light)
					level.sendParticles(viewer, ParticleTypes.END_ROD, true, x, y + 1.3, z, 1, 0, 0.5, 0, 0);
				}
				if (i % 6 == 0) {
					level.sendParticles(viewer, ParticleTypes.SNOWFLAKE, true, x, y + 1.2, z, 1, 0.1, 0.6, 0.1, 0.01);
				}
			}
		}
	}

	// ---------------------------------------------------------------- the blizzard

	/** 0 = clear, 1 = the blizzard, 2 = doubled (the Casket open). */
	public int blizzardLevel() {
		if (phase == Phase.VICTORY || casketBroken) {
			return 0;
		}
		return casketOpened ? 2 : 1;
	}

	/** 0 none, 1 open, 2 torn wide (Ymir), 3 collapsing. */
	public int riftStage() {
		if (phase == Phase.VICTORY) {
			return 3;
		}
		return ymirAwake && !casketBroken ? 2 : 1;
	}

	private void sendBlizzard(ServerLevel level, boolean force) {
		long now = level.getGameTime();
		int key = blizzardLevel() * 10 + riftStage();
		if (!force && key == lastBlizzardKey && now - lastBlizzardSent < 40) {
			return;
		}
		lastBlizzardKey = key;
		lastBlizzardSent = now;
		BlockPos c = center();
		FrostBlizzardPayload payload = new FrostBlizzardPayload(true, c.getX(), c.getY(), c.getZ(), (float) arena(),
				(float) FrostGiantConfig.arena().riftHeight, (byte) blizzardLevel(), (byte) riftStage());
		double reach = arena() + 96;
		for (ServerPlayer p : level.players()) {
			if (horizontalDistance(p) < reach && ServerPlayNetworking.canSend(p, FrostBlizzardPayload.TYPE)) {
				ServerPlayNetworking.send(p, payload);
			}
		}
	}

	private void clearBlizzard(ServerLevel level) {
		BlockPos c = center();
		FrostBlizzardPayload clear = FrostBlizzardPayload.clear(c.getX(), c.getY(), c.getZ());
		for (ServerPlayer p : level.players()) {
			if (ServerPlayNetworking.canSend(p, FrostBlizzardPayload.TYPE)) {
				ServerPlayNetworking.send(p, clear);
			}
		}
	}

	/** Temporary snow layers build up: a few a tick, on air above solid ground, capped, only where chunks tick. */
	private void snowfall(ServerLevel level) {
		FrostGiantConfig.Arena cfg = FrostGiantConfig.arena();
		if (noSnow || blizzardLevel() == 0 || temp.snowCount() >= cfg.maxSnowLayers) {
			return;
		}
		BlockPos c = center();
		int tries = cfg.snowLayersPerTick * blizzardLevel() * 2;
		int placed = 0;
		double r = arena() - 1;
		for (int i = 0; i < tries && placed < cfg.snowLayersPerTick * blizzardLevel(); i++) {
			double a = level.random.nextDouble() * Math.PI * 2;
			double d = Math.sqrt(level.random.nextDouble()) * r;
			int x = c.getX() + Mth.floor(Math.cos(a) * d);
			int z = c.getZ() + Mth.floor(Math.sin(a) * d);
			BlockPos col = new BlockPos(x, c.getY(), z);
			if (!FrostTempBlocks.buildable(level, col)) {
				continue;
			}
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
			BlockPos at = new BlockPos(x, y, z);
			if (Math.abs(y - c.getY()) > 30) {
				continue;
			}
			BlockState below = level.getBlockState(at.below());
			if (below.is(Blocks.SNOW) && temp.tracks(at.below())) {
				at = at.below(); // our own layer: thicken it
			}
			if (temp.addSnow(level, at, cfg.maxSnowDepth)) {
				placed++;
			}
		}
	}

	// ---------------------------------------------------------------- the cold

	/** Every tick, for every fighter inside: build up freeze outside warmth, thaw and regenerate inside it. */
	public void tickCold(ServerLevel level) {
		int blizzard = blizzardLevel();
		if (blizzard == 0) {
			coldCarry.clear();
			return;
		}
		FrostGiantConfig.Cold cfg = FrostGiantConfig.cold();
		List<RuneBrazierEntity> live = liveBraziers(level);
		boolean allOut = !live.isEmpty() && live.stream().noneMatch(RuneBrazierEntity::isLit);
		double mult = (allOut ? cfg.allOutMultiplier : 1.0) * (blizzard >= 2 ? cfg.casketOpenMultiplier : 1.0);
		long now = level.getGameTime();
		double r = arena();
		for (UUID u : fighters) {
			if (down.contains(u) || !(level.getPlayerByUUID(u) instanceof ServerPlayer p) || !FrostCombat.isFighter(p) || horizontalDistance(p) > r + 2) {
				continue;
			}
			applyCold(level, p, live, mult, now, cfg);
		}
	}

	/** One fighter, one tick. Package-visible for the gametests. */
	public void applyCold(ServerLevel level, LivingEntity p, List<RuneBrazierEntity> live, double mult, long now, FrostGiantConfig.Cold cfg) {
		boolean warm = false;
		for (RuneBrazierEntity b : live) {
			if (b.warms(p)) {
				warm = true;
				break;
			}
		}
		int ticks = p.getTicksFrozen();
		if (warm) {
			p.setTicksFrozen(Math.max(0, ticks - (int) Math.ceil(cfg.thawPerSecond / 20.0)));
			MobEffectInstance regen = p.getEffect(MobEffects.REGENERATION);
			if (regen == null || regen.getDuration() < 20) {
				p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, true, true));
			}
			coldCarry.remove(p.getUUID());
			return;
		}
		if (isProtectedFromCold(p)) {
			coldCarry.remove(p.getUUID());
			return;
		}
		float carry = coldCarry.getOrDefault(p.getUUID(), 0f) + (float) (cfg.buildPerSecond / 20.0 * mult);
		int whole = (int) carry;
		coldCarry.put(p.getUUID(), carry - whole);
		int cap = p.getTicksRequiredToFreeze() + 40;
		// +2 cancels vanilla's own thaw (it takes 2 a tick off anyone not standing in powder snow)
		p.setTicksFrozen(Math.min(cap, ticks + 2 + whole));
		if (p.isFullyFrozen() && now % 40 == 20) {
			p.hurt(level.damageSources().freeze(), cfg.frozenDamage);
		}
	}

	/** Fire resistance, burning, lava, a lit fire power, the Casket in the off hand, a full Rimed set. */
	public static boolean isProtectedFromCold(LivingEntity p) {
		if (p.hasEffect(MobEffects.FIRE_RESISTANCE) || p.isOnFire() || p.isInLava() || !p.canFreeze() || FrostGear.coldImmune(p)) {
			return true;
		}
		return p instanceof net.minecraft.world.entity.player.Player pl && FrostCombat.hasActivePower(pl, FrostCombat.PYROKINESIS);
	}

	// ---------------------------------------------------------------- braziers

	private void trackBraziers(ServerLevel level) {
		int lit = litBraziers(level);
		if (lastLit >= 0 && lit != lastLit && lit == 0) {
			broadcastTitle(level, Component.translatable("title.projecthero.frost_giants.all_out").withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.BOLD),
					Component.translatable("title.projecthero.frost_giants.all_out.sub").withStyle(ChatFormatting.GRAY));
		}
		lastLit = lit;
	}

	/** A brazier went out (called by the brazier). */
	public void onBrazierOut(ServerLevel level, RuneBrazierEntity b) {
		int lit = litBraziers(level);
		broadcast(level, Component.translatable("message.projecthero.frost_giants.brazier_out", lit, Math.max(1, braziers.size()))
				.withStyle(ChatFormatting.DARK_AQUA));
	}

	public void onBrazierRelit(ServerLevel level, RuneBrazierEntity b, net.minecraft.world.entity.player.Player by) {
		broadcast(level, Component.translatable("message.projecthero.frost_giants.brazier_relit",
				by == null ? Component.literal("?") : by.getDisplayName(), litBraziers(level), Math.max(1, braziers.size())).withStyle(ChatFormatting.GOLD));
	}

	// ---------------------------------------------------------------- temporary ice

	/**
	 * A temporary ice block at {@code pos} for {@code ttl} ticks: tracked by assault {@code a} (inside its arena, within its
	 * per-tick budget), or by the mod's global {@link TempBlocks} when a giant has no assault (a spawn-egg giant).
	 */
	public static boolean placeTempIce(ServerLevel level, FrostGiantAssault a, BlockPos pos, BlockState state, int ttl) {
		if (a == null) {
			if (!level.getBlockState(pos).isAir()
					|| !level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(pos)).isEmpty()) {
				return false;
			}
			return TempBlocks.place(level, pos, state, ttl);
		}
		return a.placeIce(level, pos, state, ttl);
	}

	public boolean placeIce(ServerLevel level, BlockPos pos, BlockState state, int ttl) {
		BlockPos c = center();
		double dx = pos.getX() + 0.5 - (c.getX() + 0.5);
		double dz = pos.getZ() + 0.5 - (c.getZ() + 0.5);
		if (dx * dx + dz * dz > (arena() + 4) * (arena() + 4)) {
			return false; // never outside the arena
		}
		long now = level.getGameTime();
		if (iceBudgetTick != now) {
			iceBudgetTick = now;
			iceBudgetUsed = 0;
		}
		if (iceBudgetUsed >= FrostGiantConfig.arena().iceBlocksPerTick) {
			return false;
		}
		if (temp.place(level, pos, state, ttl)) {
			iceBudgetUsed++;
			return true;
		}
		return false;
	}

	// ---------------------------------------------------------------- waves

	public void startWave(ServerLevel level, int n) {
		wave = Mth.clamp(n, 1, MAX_WAVES);
		int[] mix = composition(wave);
		int f = fighterCount();
		for (int i = 0; i < KINDS; i++) {
			owed[i] = i == WARLORD ? mix[i] : scaled(mix[i], f);
		}
		waveTotal = owed();
		phase = Phase.WAVE;
		phaseStart = level.getGameTime();
		broadcastTitle(level, Component.translatable("title.projecthero.frost_giants.wave", wave, waveCount()).withStyle(ChatFormatting.AQUA),
				Component.translatable("title.projecthero.frost_giants.wave.sub", waveTotal).withStyle(ChatFormatting.GRAY));
		horn(level);
		spawnOwed(level, ARRIVALS_PER_TICK);
	}

	private void horn(ServerLevel level) {
		Vec3 rift = riftPos();
		for (ServerPlayer p : level.players()) {
			if (horizontalDistance(p) < arena() + 48) {
				p.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(SoundEvents.RAID_HORN, SoundSource.HOSTILE,
						rift.x, rift.y - 20, rift.z, 64.0f, 0.75f, level.random.nextLong()));
			}
		}
	}

	private void tickWave(ServerLevel level, long now, long inPhase) {
		int alive = ownedAlive(level);
		if (owed() > 0) {
			spawnOwed(level, ARRIVALS_PER_TICK);
			return;
		}
		if (alive == 0) {
			phase = Phase.BREATHER;
			phaseStart = now;
			broadcast(level, Component.translatable(wave >= waveCount() ? "message.projecthero.frost_giants.waves_done"
					: "message.projecthero.frost_giants.wave_cleared", wave).withStyle(ChatFormatting.AQUA));
			return;
		}
		if (alive <= 3 && inPhase > STRAGGLER_TICKS * 2) {
			for (Mob m : liveOwnedMobs(level)) {
				if (!(m instanceof FrostGiantWarlordEntity)) {
					disown(m.getUUID());
					m.discard();
				}
			}
		}
	}

	/** Brings in up to {@code max} owed units while the cap has room: the Warlord and Brutes first, raiders in pairs. */
	private void spawnOwed(ServerLevel level, int max) {
		int made = 0;
		int guard = 0;
		while (made < max && owed() > 0 && hasRoom(level) && guard++ < 64) {
			int kind = owed[WARLORD] > 0 ? WARLORD : owed[BRUTE] > 0 ? BRUTE : owed[SHAMAN] > 0 && level.random.nextBoolean() ? SHAMAN
					: owed[RAIDER] > 0 && (owed[HOUND] == 0 || level.random.nextBoolean()) ? RAIDER : owed[HOUND] > 0 ? HOUND
					: owed[SHAMAN] > 0 ? SHAMAN : RAIDER;
			owed[kind]--;
			Mob first = spawnUnit(level, kind, null);
			if (first != null) {
				made++;
				if (kind == RAIDER && owed[RAIDER] > 0) {
					owed[RAIDER]--;
					if (spawnUnit(level, RAIDER, first.position()) != null) {
						made++; // raiders advance in pairs
					}
				} else if (kind == HOUND) {
					for (int i = 0; i < 2 && owed[HOUND] > 0; i++) {
						owed[HOUND]--;
						if (spawnUnit(level, HOUND, first.position()) != null) {
							made++; // and hounds in packs
						}
					}
				}
			}
		}
	}

	/**
	 * One unit of {@code kind}, brought down the frost beam to a spot round the arena (next to {@code near} when given).
	 * Null if it could not be made.
	 */
	public Mob spawnUnit(ServerLevel level, int kind, Vec3 near) {
		EntityType<? extends Mob> type = switch (kind) {
			case RAIDER -> FrostGiantEntityTypes.RAIDER;
			case SHAMAN -> FrostGiantEntityTypes.SHAMAN;
			case BRUTE -> FrostGiantEntityTypes.BRUTE;
			case WARLORD -> FrostGiantEntityTypes.WARLORD;
			default -> FrostGiantEntityTypes.HOUND;
		};
		Mob mob = type.create(level);
		if (mob == null) {
			return null;
		}
		Vec3 at = near != null ? landingNear(level, near) : landingSpot(level);
		mob.moveTo(at.x, at.y, at.z, level.random.nextFloat() * 360f, 0f);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(at)), MobSpawnType.EVENT, null);
		if (mob instanceof FrostGiantWarlordEntity w) {
			w.configure(fighterCount());
			warlordId = w.getUUID();
		}
		adopt(mob);
		if (mob instanceof FrostGiantEntity g && !instantArrivals) {
			Vec3 rift = riftPos();
			g.beginArrival(new Vec3(rift.x + (at.x - rift.x) * 0.25, rift.y - 2, rift.z + (at.z - rift.z) * 0.25), at,
					FrostGiantConfig.arena().arrivalTicks);
		}
		level.addFreshEntity(mob);
		net.minecraft.world.entity.player.Player target = FrostCombat.nearestPlayer(level, at, arena() * 2);
		if (target != null) {
			mob.setTarget(target);
		}
		return mob;
	}

	/** A spot on the landing ring (the centre if nothing round it is loaded). */
	private Vec3 landingSpot(ServerLevel level) {
		BlockPos c = center();
		double min = arrivalRadiusOverride >= 0 ? arrivalRadiusOverride : 14.0;
		double max = arrivalRadiusOverride >= 0 ? arrivalRadiusOverride : arena() - 10.0;
		for (int attempt = 0; attempt < 8; attempt++) {
			double a = level.random.nextDouble() * Math.PI * 2;
			double r = min + level.random.nextDouble() * Math.max(0.0, max - min);
			Vec3 at = ground(level, c.getX() + 0.5 + Math.cos(a) * r, c.getZ() + 0.5 + Math.sin(a) * r);
			if (at != null) {
				return at;
			}
		}
		return new Vec3(c.getX() + 0.5, c.getY() + 1, c.getZ() + 0.5);
	}

	private Vec3 landingNear(ServerLevel level, Vec3 near) {
		for (int attempt = 0; attempt < 6; attempt++) {
			double a = level.random.nextDouble() * Math.PI * 2;
			Vec3 at = ground(level, near.x + Math.cos(a) * 3.5, near.z + Math.sin(a) * 3.5);
			if (at != null) {
				return at;
			}
		}
		return near;
	}

	private Vec3 ground(ServerLevel level, double x, double z) {
		BlockPos c = center();
		BlockPos column = BlockPos.containing(x, c.getY(), z);
		if (!level.isLoaded(column)) {
			return null;
		}
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
		if (Math.abs(y - c.getY()) > 20) {
			return null;
		}
		return new Vec3(x, Math.max(y, c.getY()), z);
	}

	/** Laufey's Royal Guard: {@code n} Jotun Raiders from the rift, landing near him. */
	public void summonRoyalGuard(ServerLevel level, int n) {
		LivingEntity boss = boss(level);
		for (int i = 0; i < n; i++) {
			if (!hasRoom(level)) {
				return;
			}
			spawnUnit(level, RAIDER, boss != null ? boss.position() : null);
		}
	}

	// ---------------------------------------------------------------- the boss

	/** King Laufey comes down the beam (also the command's / tests' hook). */
	public KingLaufeyEntity startBoss(ServerLevel level) {
		KingLaufeyEntity laufey = FrostGiantEntityTypes.LAUFEY.create(level);
		phase = Phase.BOSS;
		phaseStart = level.getGameTime();
		if (laufey == null) {
			complete(level); // nothing to fight: never leave an assault stuck
			return null;
		}
		BlockPos c = center();
		Vec3 at = arrivalRadiusOverride >= 0 ? landingSpot(level) : ground(level, c.getX() + 0.5 + 10, c.getZ() + 0.5);
		if (at == null) {
			at = new Vec3(c.getX() + 0.5, c.getY() + 1, c.getZ() + 0.5);
		}
		laufey.moveTo(at.x, at.y, at.z, 180f, 0f);
		laufey.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(at)), MobSpawnType.EVENT, null);
		laufey.configure(fighterCount());
		adopt(laufey);
		if (!instantArrivals) {
			Vec3 rift = riftPos();
			laufey.beginArrival(new Vec3(rift.x, rift.y - 4, rift.z), at, BOSS_ARRIVAL_TICKS);
		}
		level.addFreshEntity(laufey);
		bossId = laufey.getUUID();
		broadcastTitle(level, Component.translatable("title.projecthero.frost_giants.laufey").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD),
				Component.translatable("title.projecthero.frost_giants.laufey.sub").withStyle(ChatFormatting.GRAY));
		level.playSound(null, c, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 2.0f, 0.5f);
		horn(level);
		return laufey;
	}

	private void tickBoss(ServerLevel level) {
		LivingEntity b = boss(level);
		if (b != null && b.isAlive() && !b.isDeadOrDying()) {
			return;
		}
		if (bossId != null) {
			disown(bossId); // let the collapse play out instead of vanishing with the cleanup
		}
		phase = Phase.VICTORY;
		phaseStart = level.getGameTime();
		for (Mob m : liveOwnedMobs(level)) {
			if (m instanceof FrostGiantEntity) {
				m.hurt(level.damageSources().genericKill(), 10000f); // the rest of Jotunheim's host breaks with their king
			}
		}
		retractYmir(level);
		Vec3 rift = riftPos();
		FrostFx.forced(level, ParticleTypes.FLASH, rift.x, rift.y, rift.z, 3, 4, 2, 4, 0);
		FrostFx.forced(level, ParticleTypes.END_ROD, rift.x, rift.y, rift.z, 80, 6, 3, 6, 0.3);
		level.playSound(null, rift.x, center().getY() + 12, rift.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 3.0f, 1.4f);
		broadcastTitle(level, Component.translatable("title.projecthero.frost_giants.victory").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
				Component.translatable("title.projecthero.frost_giants.victory.sub").withStyle(ChatFormatting.WHITE));
		sendBlizzard(level, true);
	}

	/** Laufey opened the Casket (phase 2): the blizzard doubles, every brazier loses 30%, the sky darkens. */
	public void onCasketOpened(ServerLevel level) {
		if (casketOpened) {
			return;
		}
		casketOpened = true;
		if (boss(level) instanceof KingLaufeyEntity l) {
			l.markCasketOpened();
		}
		double drain = FrostGiantConfig.braziers().casketOpenDrain;
		for (RuneBrazierEntity b : liveBraziers(level)) {
			b.drain(level, drain);
		}
		broadcastTitle(level, Component.translatable("title.projecthero.frost_giants.casket").withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.BOLD),
				Component.translatable("title.projecthero.frost_giants.casket.sub").withStyle(ChatFormatting.GRAY));
		sendBlizzard(level, true);
	}

	/** Phase 3: the rift tears wide and Ymir's arm reaches through. */
	public void onYmirWakes(ServerLevel level) {
		if (ymirAwake || casketBroken) {
			return;
		}
		ymirAwake = true;
		ymirMove = 0;
		ymirCooldown = 100;
		summonYmir(level);
		Vec3 rift = riftPos();
		FrostFx.forced(level, ParticleTypes.FLASH, rift.x, rift.y, rift.z, 2, 3, 1, 3, 0);
		level.playSound(null, rift.x, center().getY() + 12, rift.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 3.0f, 0.4f);
		broadcastTitle(level, Component.translatable("title.projecthero.frost_giants.ymir").withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.BOLD),
				Component.translatable("title.projecthero.frost_giants.ymir.sub").withStyle(ChatFormatting.GRAY));
		sendBlizzard(level, true);
	}

	private YmirArmEntity ymir(ServerLevel level) {
		return ymirId != null && level.getEntity(ymirId) instanceof YmirArmEntity y ? y : null;
	}

	private void summonYmir(ServerLevel level) {
		YmirArmEntity arm = FrostGiantEntityTypes.YMIR_ARM.create(level);
		if (arm == null) {
			return;
		}
		BlockPos c = center();
		Vec3 at = ground(level, c.getX() + 0.5 - 12, c.getZ() + 0.5);
		if (at == null) {
			at = Vec3.atBottomCenterOf(c);
		}
		arm.moveTo(at.x, at.y, at.z, 0f, 0f);
		level.addFreshEntity(arm);
		arm.play(com.projecthero.mod.frostgiant.FrostGiantAnims.YMIR_REACH_IN);
		ymirId = arm.getUUID();
	}

	/** The Casket broke: Ymir is torn back through the rift and the blizzard ends. */
	public void onCasketBroken(ServerLevel level) {
		if (casketBroken) {
			return;
		}
		casketBroken = true;
		retractYmir(level);
		broadcastTitle(level, Component.translatable("title.projecthero.frost_giants.casket_broken").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
				Component.translatable("title.projecthero.frost_giants.casket_broken.sub").withStyle(ChatFormatting.WHITE));
		sendBlizzard(level, true);
	}

	private void retractYmir(ServerLevel level) {
		YmirArmEntity arm = ymir(level);
		if (arm != null && ymirMove != 3) {
			arm.play(com.projecthero.mod.frostgiant.FrostGiantAnims.YMIR_RETRACT);
			ymirMove = 3;
			ymirTick = 0;
		} else if (arm == null) {
			ymirAwake = false;
		}
	}

	/** A Frozen Grasp prison was broken {@code ticksAlive} after it closed: quick enough, Laufey staggers. */
	public void onGraspBroken(ServerLevel level, int ticksAlive) {
		if (ticksAlive <= FrostGiantConfig.laufey().quickBreakTicks && boss(level) instanceof KingLaufeyEntity l) {
			l.tryStagger(level, "grasp");
		}
	}

	public void broadcastStagger(ServerLevel level, String why) {
		broadcastTitle(level, Component.translatable("title.projecthero.frost_giants.stagger").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD),
				Component.translatable("title.projecthero.frost_giants.stagger.sub." + why).withStyle(ChatFormatting.AQUA));
	}

	// ---------------------------------------------------------------- Ymir

	private void tickYmir(ServerLevel level) {
		FrostGiantConfig.Ymir cfg = FrostGiantConfig.ymir();
		YmirArmEntity arm = ymir(level);
		if (ymirMove == 3) {
			if (++ymirTick >= com.projecthero.mod.frostgiant.FrostGiantAnims.YMIR_RETRACT.length()) {
				if (arm != null) {
					arm.discard();
				}
				ymirId = null;
				ymirAwake = false;
				ymirMove = 0;
			}
			return;
		}
		if (casketBroken || phase != Phase.BOSS) {
			retractYmir(level);
			return;
		}
		if (arm == null) {
			summonYmir(level); // reloaded: he reaches back in
			return;
		}
		if (ymirMove == 0) {
			if (--ymirCooldown > 0) {
				return;
			}
			List<net.minecraft.world.entity.player.Player> targets = FrostCombat.playersNear(level, Vec3.atCenterOf(center()), arena());
			if (targets.isEmpty()) {
				ymirCooldown = 40;
				return;
			}
			var victim = targets.get(level.random.nextInt(targets.size()));
			startYmirMove(level, arm, level.random.nextFloat() < 0.6f, victim.position());
			return;
		}
		ymirTick++;
		if (ymirMove == 1) {
			int hit = com.projecthero.mod.frostgiant.FrostGiantAnims.YMIR_PALM_SLAM.hit();
			if (ymirTick < hit && ymirTick % 2 == 0) {
				// the hand's shadow grows on the ground, frost runes in it
				double r = cfg.palmRadius * Math.min(1.0, 0.25 + ymirTick / (double) hit);
				FrostFx.ring(level, FrostFx.SHADOW, ymirTo.add(0, 0.15, 0), r, 10 + (int) (r * 5), true);
				FrostFx.ring(level, FrostFx.RUNE, ymirTo.add(0, 0.2, 0), r * 0.5, 8, true);
				FrostFx.forced(level, FrostFx.SHADOW, ymirTo.x, ymirTo.y + 0.15, ymirTo.z, (int) (r * 3), r * 0.5, 0.0, r * 0.5, 0);
			}
			if (ymirTick == hit) {
				FrostFx.shake(level, ymirTo, 1.0f, 20, 40);
				FrostFx.landing(level, ymirTo, cfg.palmRadius * 0.6);
				level.playSound(null, ymirTo.x, ymirTo.y, ymirTo.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 4.0f, 0.4f);
				for (LivingEntity e : FrostCombat.targetsNear(level, ymirTo, cfg.palmRadius)) {
					if (FrostCombat.hit(level, null, e, cfg.palmDamage, 80) && !(e instanceof RuneBrazierEntity)) {
						e.push(0, 0.5, 0);
						e.hurtMarked = true;
					}
				}
			}
			if (ymirTick >= com.projecthero.mod.frostgiant.FrostGiantAnims.YMIR_PALM_SLAM.length()) {
				ymirMove = 0;
				ymirCooldown = cfg.attackCooldownTicks;
				arm.play(com.projecthero.mod.frostgiant.FrostGiantAnims.YMIR_IDLE);
			}
		} else if (ymirMove == 2) {
			int hit = com.projecthero.mod.frostgiant.FrostGiantAnims.YMIR_GLACIAL_SWEEP.hit();
			if (ymirTick < hit && ymirTick % 3 == 0) {
				FrostFx.line(level, FrostFx.SHADOW, ymirFrom.add(0, 0.15, 0), ymirTo.add(0, 0.15, 0), 1.0, true);
				FrostFx.line(level, ParticleTypes.SNOWFLAKE, ymirFrom.add(0, 0.4, 0), ymirTo.add(0, 0.4, 0), 2.5, true);
			}
			if (ymirTick >= hit) {
				float t = Math.min(1f, (ymirTick - hit) / (float) Math.max(1, cfg.sweepTicks));
				Vec3 at = ymirFrom.lerp(ymirTo, t);
				arm.setPos(at.x, at.y, at.z);
				if (ymirTick % 2 == 0) {
					FrostFx.landing(level, at, 1.5);
				}
				for (LivingEntity e : FrostCombat.targetsNear(level, at.add(0, 1, 0), cfg.sweepWidth + 1.5)) {
					if (ymirHit.add(e.getUUID()) && FrostCombat.hit(level, null, e, cfg.sweepDamage, 60) && !(e instanceof RuneBrazierEntity)) {
						Vec3 d = ymirTo.subtract(ymirFrom).normalize();
						e.push(d.x * 1.5, 1.1, d.z * 1.5);
						e.hurtMarked = true;
					}
				}
				if (ymirTick == hit) {
					level.playSound(null, at.x, at.y, at.z, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3.0f, 0.3f);
					FrostFx.shake(level, at, 0.7f, 20, 48);
				}
			}
			if (ymirTick >= com.projecthero.mod.frostgiant.FrostGiantAnims.YMIR_GLACIAL_SWEEP.length()) {
				ymirMove = 0;
				ymirCooldown = cfg.attackCooldownTicks;
				arm.play(com.projecthero.mod.frostgiant.FrostGiantAnims.YMIR_IDLE);
			}
		}
	}

	/** Ymir begins a Palm Slam on {@code at}, or a Glacial Sweep through it. */
	private void startYmirMove(ServerLevel level, YmirArmEntity arm, boolean palm, Vec3 at) {
		ymirHit.clear();
		ymirTick = 0;
		if (palm) {
			ymirMove = 1;
			ymirTo = at;
			arm.moveTo(ymirTo.x, ymirTo.y, ymirTo.z, arm.getYRot(), 0f);
			arm.play(com.projecthero.mod.frostgiant.FrostGiantAnims.YMIR_PALM_SLAM);
		} else {
			ymirMove = 2;
			double a = level.random.nextDouble() * Math.PI;
			Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a));
			ymirFrom = clampToArena(at.subtract(dir.scale(22)));
			ymirTo = clampToArena(at.add(dir.scale(22)));
			arm.moveTo(ymirFrom.x, ymirFrom.y, ymirFrom.z, (float) (Math.atan2(-dir.x, dir.z) * Mth.RAD_TO_DEG), 0f);
			arm.play(com.projecthero.mod.frostgiant.FrostGiantAnims.YMIR_GLACIAL_SWEEP);
		}
	}

	/** Command / harness hook: Ymir starts a Palm Slam on (or a Glacial Sweep through) {@code at} right now. */
	public boolean debugYmir(ServerLevel level, boolean palm, Vec3 at) {
		YmirArmEntity arm = ymir(level);
		if (!ymirAwake || arm == null || ymirMove == 3) {
			return false;
		}
		startYmirMove(level, arm, palm, at);
		return true;
	}

	private Vec3 clampToArena(Vec3 p) {
		BlockPos c = center();
		Vec3 cc = new Vec3(c.getX() + 0.5, p.y, c.getZ() + 0.5);
		Vec3 d = p.subtract(cc);
		double max = arena() - 2;
		if (d.horizontalDistance() > max) {
			d = d.scale(max / d.horizontalDistance());
		}
		return cc.add(d.x, 0, d.z);
	}

	/** {@code /projecthero raid advancetimer frost_giants}: on to the next stage now. */
	public void debugAdvance(ServerLevel level) {
		long now = level.getGameTime();
		switch (phase) {
			case COUNTDOWN -> startWave(level, 1);
			case WAVE -> {
				for (Mob m : liveOwnedMobs(level)) {
					disown(m.getUUID());
					m.discard();
				}
				for (int i = 0; i < KINDS; i++) {
					owed[i] = 0;
				}
				phase = Phase.BREATHER;
				phaseStart = now - FrostGiantConfig.waves().breatherSeconds * 20L;
			}
			case BREATHER -> phaseStart = now - FrostGiantConfig.waves().breatherSeconds * 20L;
			case BOSS -> {
				LivingEntity b = boss(level);
				if (b instanceof KingLaufeyEntity l) {
					if (l.phase() < 3) {
						l.forcePhase(level, l.phase() + 1);
					} else if (!casketBroken) {
						if (l.casketId() != null && level.getEntity(l.casketId()) instanceof IceCasketEntity casket) {
							casket.kill();
						} else {
							onCasketBroken(level);
							l.onCasketBroken(level);
						}
					} else {
						l.kill();
					}
				} else if (b != null) {
					b.kill();
				}
			}
			case VICTORY -> phaseStart = now - VICTORY_TICKS;
		}
	}

	// ---------------------------------------------------------------- deaths and the end

	/** A fighter died: down until they walk back in. When every fighter is down at once, Jotunheim wins. */
	public void onFighterDied(ServerLevel level, ServerPlayer player) {
		if (!fighters.contains(player.getUUID()) || state().finished()) {
			return;
		}
		down.add(player.getUUID());
		participants().recordDeath(player.getUUID());
		broadcast(level, Component.translatable("message.projecthero.frost_giants.fighter_down", player.getDisplayName()).withStyle(ChatFormatting.DARK_AQUA));
		if (down.containsAll(fighters)) {
			fail(level, Component.translatable("message.projecthero.frost_giants.defeat").withStyle(ChatFormatting.DARK_AQUA));
		}
	}

	/** Everyone still standing at the end. */
	public List<ServerPlayer> winners(ServerLevel level) {
		List<ServerPlayer> out = new ArrayList<>();
		for (UUID u : fighters) {
			if (!down.contains(u) && level.getPlayerByUUID(u) instanceof ServerPlayer p && p.isAlive()) {
				out.add(p);
			}
		}
		return out;
	}

	@Override
	protected void onFinished(ServerLevel level, boolean success) {
		tearDown(level);
		BlockPos c = center();
		if (success) {
			List<ServerPlayer> winners = winners(level);
			FrostGiantRewards.placeChest(level, c, Math.max(1, winners.size()));
			for (ServerPlayer p : winners) {
				FrostGiantRewards.grantCasket(p);
			}
			level.playSound(null, c, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);
			com.projecthero.mod.galactus.GalactusWorldState.markFrostBeaten(level.getServer()); // v0.16.1: the cosmic signal can find this world now
			ProjectHeroMod.LOGGER.info("[ProjectHero] Frost Giant Assault {} broken; {} fighter(s) rewarded", id(), winners.size());
		} else {
			// Jotunheim won: the anchor shatters
			level.setBlock(c, Blocks.AIR.defaultBlockState(), 3);
			FrostFx.shatter(level, Vec3.atCenterOf(c), 1.5);
			level.playSound(null, c, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 2.0f, 0.5f);
		}
	}

	@Override
	protected void onAborted(ServerLevel level) {
		tearDown(level);
		if (level.isLoaded(center())) {
			BlockState state = level.getBlockState(center());
			if (state.is(FrostGiantBlocks.RUNE_STONE)) {
				level.setBlock(center(), state.setValue(JotunheimRuneStoneBlock.ACTIVE, false), 3);
			}
		}
	}

	@Override
	protected void onPaused(ServerLevel level) {
		super.onPaused(level);
		if (bar != null) {
			bar.clear(level);
		}
		ZombieRaidNetworking.clearSky(level, center());
		clearBlizzard(level);
	}

	private void tearDown(ServerLevel level) {
		if (bar != null) {
			bar.clear(level);
		}
		ZombieRaidNetworking.clearSky(level, center());
		clearBlizzard(level);
		for (UUID u : braziers) {
			if (level.getEntity(u) instanceof RuneBrazierEntity b) {
				b.discard();
			}
		}
		braziers.clear();
		if (ymir(level) instanceof YmirArmEntity arm) {
			arm.discard();
		}
		ymirId = null;
		ymirAwake = false;
		temp.restoreAll(level);
		coldCarry.clear();
	}

	// ---------------------------------------------------------------- the bar

	private void updateBar(ServerLevel level, long now) {
		if (bar == null) {
			bar = new EventBossBar(id(), BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_10, false);
		}
		long inPhase = now - phaseStart;
		int lit = litBraziers(level);
		int total = Math.max(1, braziers.size());
		Component name;
		float progress;
		switch (phase) {
			case COUNTDOWN -> {
				int t = FrostGiantConfig.waves().countdownSeconds * 20;
				name = Component.translatable("event.projecthero.frost_giants.bar.countdown", Math.max(0, (int) Math.ceil((t - inPhase) / 20.0)));
				progress = Mth.clamp(inPhase / (float) Math.max(1, t), 0f, 1f);
			}
			case BREATHER -> {
				int t = FrostGiantConfig.waves().breatherSeconds * 20;
				name = Component.translatable("event.projecthero.frost_giants.bar.breather", wave, waveCount(), lit, total);
				progress = Mth.clamp(inPhase / (float) Math.max(1, t), 0f, 1f);
			}
			case WAVE -> {
				int left = owed() + ownedAlive(level);
				name = Component.translatable("event.projecthero.frost_giants.bar.wave", wave, waveCount(), left, lit, total);
				progress = waveTotal <= 0 ? 0f : Mth.clamp(left / (float) waveTotal, 0f, 1f);
			}
			case BOSS -> {
				name = Component.translatable(casketBroken ? "event.projecthero.frost_giants.bar.boss_drained"
						: ymirAwake ? "event.projecthero.frost_giants.bar.boss_ymir" : "event.projecthero.frost_giants.bar.boss", lit, total);
				progress = lit / (float) total;
			}
			default -> {
				name = Component.translatable("event.projecthero.frost_giants.bar.victory");
				progress = 0f;
			}
		}
		bar.update(level, center(), radius(), name.copy().withStyle(ChatFormatting.AQUA), progress);
	}

	// ---------------------------------------------------------------- save

	@Override
	protected void saveExtra(CompoundTag tag) {
		tag.putString("FrostPhase", phase.name());
		tag.putLong("FrostPhaseStart", phaseStart);
		tag.putInt("FrostWave", wave);
		tag.putInt("FrostWaveTotal", waveTotal);
		tag.putIntArray("FrostOwed", owed.clone());
		tag.put("FrostBraziers", uuids(braziers));
		if (bossId != null) {
			tag.putUUID("FrostBoss", bossId);
		}
		if (warlordId != null) {
			tag.putUUID("FrostWarlord", warlordId);
		}
		tag.putBoolean("FrostCasketOpened", casketOpened);
		tag.putBoolean("FrostYmirAwake", ymirAwake);
		tag.putBoolean("FrostCasketBroken", casketBroken);
		tag.putInt("FrostYmirMove", ymirMove == 3 ? 3 : 0);
		tag.putInt("FrostYmirCooldown", ymirCooldown);
		tag.putString("FrostActivator", activatorName);
		tag.put("FrostFighters", uuids(fighters));
		tag.put("FrostDown", uuids(down));
		tag.put("FrostBlocks", temp.save());
	}

	@Override
	protected void loadExtra(CompoundTag tag) {
		try {
			phase = Phase.valueOf(tag.getString("FrostPhase"));
		} catch (IllegalArgumentException e) {
			phase = Phase.COUNTDOWN;
		}
		phaseStart = tag.getLong("FrostPhaseStart");
		wave = tag.getInt("FrostWave");
		waveTotal = tag.getInt("FrostWaveTotal");
		int[] o = tag.getIntArray("FrostOwed");
		for (int i = 0; i < KINDS; i++) {
			owed[i] = i < o.length ? o[i] : 0;
		}
		braziers.clear();
		ListTag bl = tag.getList("FrostBraziers", Tag.TAG_INT_ARRAY);
		for (int i = 0; i < bl.size(); i++) {
			braziers.add(NbtUtils.loadUUID(bl.get(i)));
		}
		bossId = tag.hasUUID("FrostBoss") ? tag.getUUID("FrostBoss") : null;
		warlordId = tag.hasUUID("FrostWarlord") ? tag.getUUID("FrostWarlord") : null;
		casketOpened = tag.getBoolean("FrostCasketOpened");
		ymirAwake = tag.getBoolean("FrostYmirAwake");
		casketBroken = tag.getBoolean("FrostCasketBroken");
		ymirMove = tag.getInt("FrostYmirMove");
		ymirCooldown = tag.getInt("FrostYmirCooldown");
		activatorName = tag.getString("FrostActivator");
		readUuids(tag.getList("FrostFighters", Tag.TAG_INT_ARRAY), fighters);
		readUuids(tag.getList("FrostDown", Tag.TAG_INT_ARRAY), down);
		temp.load(tag.getList("FrostBlocks", Tag.TAG_COMPOUND), null);
	}

	private static ListTag uuids(Iterable<UUID> set) {
		ListTag list = new ListTag();
		for (UUID u : set) {
			list.add(NbtUtils.createUUID(u));
		}
		return list;
	}

	private static void readUuids(ListTag list, Set<UUID> into) {
		into.clear();
		for (int i = 0; i < list.size(); i++) {
			into.add(NbtUtils.loadUUID(list.get(i)));
		}
	}

	/** Test hook: a fighter added directly. */
	public void addFighter(UUID id) {
		fighters.add(id);
	}

	/** Test hook: does this assault own {@code e}? */
	public boolean ownsEntity(LivingEntity e) {
		return owns(e.getUUID());
	}
}
