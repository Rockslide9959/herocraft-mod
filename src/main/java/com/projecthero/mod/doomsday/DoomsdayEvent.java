package com.projecthero.mod.doomsday;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.doomsday.DoomsdayAdaptation.Bucket;
import com.projecthero.mod.doomsday.entity.CasketEntity;
import com.projecthero.mod.doomsday.entity.DoomsdayEntity;
import com.projecthero.mod.doomsday.entity.DoomsdayEntityTypes;
import com.projecthero.mod.event.EventBossBar;
import com.projecthero.mod.event.EventConfig;
import com.projecthero.mod.event.EventInstance;
import com.projecthero.mod.event.EventManager;
import com.projecthero.mod.event.EventSavedData;
import com.projecthero.mod.event.EventState;
import com.projecthero.mod.event.raid.ZombieRaidNetworking;
import com.projecthero.mod.network.ParticleBatch;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2: <b>Doomsday</b> -- the world event. See {@code docs/DOOMSDAY_DESIGN.md}.
 *
 * <ol>
 *   <li>{@link Phase#IMPACT}: a Calatonian casket crashes down (or the Casket block cracks open); something inside pounds
 *       on the lid, harder and faster, for {@link DoomsdayConfig.Arena#impactSeconds}.</li>
 *   <li>{@link Phase#FIGHT}: he tears out, still bound, and the arena (radius {@link DoomsdayConfig.Arena#radius}) closes.
 *       Three lives, each one adapting ({@link DoomsdayAdaptation}); every death drops a Phantom Zone shard.</li>
 *   <li>After the third death the shards become the <b>Phantom Zone Projector</b>, handed to the fighter who carried the
 *       most. Use it on his corpse before he rises again: he is banished, the event is won and the chest appears. Miss it
 *       and he rises enraged.</li>
 * </ol>
 * Every fighter down at once is a loss: he leaps away into the sky.
 */
public class DoomsdayEvent extends EventInstance {
	public static final String TYPE_ID = "doomsday";
	public static final int SKY_COLOR = 0x4A1C14;

	public enum Phase { IMPACT, FIGHT, GONE }

	private Phase phase = Phase.IMPACT;
	private long phaseStart = -1;
	private final Set<UUID> fighters = new HashSet<>();
	private final Set<UUID> down = new HashSet<>();
	private UUID doomsdayId;
	private UUID casketId;
	private final List<UUID> parts = new ArrayList<>();
	private boolean fromCasketBlock;
	private float casketYaw;
	private int deaths;
	private boolean projectorGiven;
	private long missingSince = -1;

	private transient EventBossBar bar;
	/** Test hook: the casket opens at once, the arena is small. */
	public transient boolean instant;

	public DoomsdayEvent(UUID id) {
		super(id);
	}

	@Override
	public String typeId() {
		return TYPE_ID;
	}

	@Override
	public Component displayName() {
		return Component.translatable("event.projecthero.doomsday");
	}

	public static double arena() {
		return DoomsdayConfig.arena().radius;
	}

	@Override
	public double radius() {
		return arena() + 32.0;
	}

	public static DoomsdayEvent find(ServerLevel level, UUID id) {
		if (id == null) {
			return null;
		}
		return EventSavedData.get(level).byId(id) instanceof DoomsdayEvent d && d.state().active() ? d : null;
	}

	public static List<DoomsdayEvent> all(MinecraftServer server) {
		List<DoomsdayEvent> out = new ArrayList<>();
		for (EventInstance e : EventManager.active(server)) {
			if (e instanceof DoomsdayEvent d) {
				out.add(d);
			}
		}
		return out;
	}

	public Phase phase() {
		return phase;
	}

	public int deaths() {
		return deaths;
	}

	public boolean isFighter(UUID player) {
		return fighters.contains(player);
	}

	public Set<UUID> fighters() {
		return fighters;
	}

	public int fighterCount() {
		return Math.max(1, fighters.size() - down.size());
	}

	public boolean projectorGiven() {
		return projectorGiven;
	}

	public DoomsdayEntity doomsday(ServerLevel level) {
		return doomsdayId != null && level.getEntity(doomsdayId) instanceof DoomsdayEntity d && d.isAlive() ? d : null;
	}

	public CasketEntity casket(ServerLevel level) {
		return casketId != null && level.getEntity(casketId) instanceof CasketEntity c && c.isAlive() ? c : null;
	}

	public static boolean isFighterPlayer(Player p) {
		return p.isAlive() && !p.isSpectator() && !p.getAbilities().invulnerable;
	}

	// ---------------------------------------------------------------- activation

	void activate(ServerLevel level, ServerPlayer activator, boolean casketBlock, float yaw) {
		fromCasketBlock = casketBlock;
		casketYaw = yaw;
		setState(EventState.RUNNING);
		phase = Phase.IMPACT;
		phaseStart = level.getGameTime();
		BlockPos c = center();
		double r2 = arena() * arena();
		for (ServerPlayer p : level.players()) {
			if (isFighterPlayer(p) && p.distanceToSqr(c.getX() + 0.5, p.getY(), c.getZ() + 0.5) <= r2) {
				fighters.add(p.getUUID());
			}
		}
		if (activator != null && isFighterPlayer(activator)) {
			fighters.add(activator.getUUID());
		}
		participants().refresh(level, c, EventConfig.framework().abandonRadius, true);
		int openAfter = instant ? 2 : Math.max(2, DoomsdayConfig.arena().impactSeconds) * 20;
		Vec3 at = Vec3.atBottomCenterOf(c);
		CasketEntity casket = casketBlock ? CasketEntity.landed(level, id(), at, yaw, openAfter)
				: CasketEntity.falling(level, id(), at, openAfter + CasketEntity.FALL_TICKS);
		if (casketBlock) {
			level.removeBlock(c, false);
		}
		casketId = casket.getUUID();
		broadcastTitle(level, Component.translatable("title.projecthero.doomsday.start").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
				Component.translatable("title.projecthero.doomsday.start.sub").withStyle(ChatFormatting.GRAY));
		level.playSound(null, c, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 2.0f, 0.4f);
		ProjectHeroMod.LOGGER.info("[ProjectHero] Doomsday {} started at {} with {} fighter(s)", id(), c, fighters.size());
	}

	/** The casket burst: Doomsday is out. */
	public void onCasketBurst(ServerLevel level, CasketEntity casket) {
		if (phase != Phase.IMPACT) {
			return;
		}
		spawnDoomsday(level, casket.position());
		phase = Phase.FIGHT;
		phaseStart = level.getGameTime();
		broadcastTitle(level, Component.translatable("title.projecthero.doomsday.out").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
				Component.translatable("title.projecthero.doomsday.out.sub").withStyle(ChatFormatting.GRAY));
	}

	private DoomsdayEntity spawnDoomsday(ServerLevel level, Vec3 at) {
		DoomsdayEntity d = DoomsdayEntityTypes.DOOMSDAY.create(level);
		if (d == null) {
			return null;
		}
		d.moveTo(at.x, at.y + 0.2, at.z, casketYaw, 0f);
		d.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(at)), MobSpawnType.EVENT, null);
		d.bindToEvent(id());
		d.configure(fighterCount());
		d.emerge();
		level.addFreshEntity(d);
		own(d);
		doomsdayId = d.getUUID();
		return d;
	}

	// ---------------------------------------------------------------- the framework tick (every 0.5 s)

	@Override
	protected void onTick(ServerLevel level) {
		long now = level.getGameTime();
		if (phaseStart < 0) {
			phaseStart = now;
		}
		enforceArena(level);
		ZombieRaidNetworking.pushSky(level, center(), arena(), SKY_COLOR);
		drawBorder(level, now);
		switch (phase) {
			case IMPACT -> {
				if (casket(level) == null && level.isLoaded(center()) && now - phaseStart > 200) {
					// the casket went missing (a reload mid-fall): he is out anyway
					onCasketBurst(level, CasketEntity.landed(level, id(), Vec3.atBottomCenterOf(center()), casketYaw, 1));
				}
			}
			case FIGHT -> {
				DoomsdayEntity d = doomsday(level);
				if (d == null && level.isLoaded(center())) {
					if (missingSince < 0) {
						missingSince = now;
					} else if (now - missingSince > 200) {
						fail(level, Component.translatable("message.projecthero.doomsday.vanished").withStyle(ChatFormatting.GRAY));
						return;
					}
				} else {
					missingSince = -1;
				}
			}
			default -> {
			}
		}
		if (!state().finished()) {
			updateBar(level, now);
		}
	}

	private double horizontalDistance(net.minecraft.world.entity.Entity e) {
		BlockPos c = center();
		double dx = e.getX() - (c.getX() + 0.5);
		double dz = e.getZ() - (c.getZ() + 0.5);
		return Math.sqrt(dx * dx + dz * dz);
	}

	private void enforceArena(ServerLevel level) {
		double r = arena();
		for (ServerPlayer p : level.players()) {
			if (!isFighterPlayer(p)) {
				continue;
			}
			double d = horizontalDistance(p);
			UUID u = p.getUUID();
			if (!fighters.contains(u)) {
				if (d <= r) {
					fighters.add(u);
					p.displayClientMessage(Component.translatable("message.projecthero.doomsday.joined").withStyle(ChatFormatting.DARK_RED), true);
				}
				continue;
			}
			if (down.contains(u)) {
				if (d <= r) {
					down.remove(u);
				}
				continue;
			}
			if (phase == Phase.FIGHT && d > r && d < r + 48 && !p.isPassenger()) {
				pullBack(level, p, r - 4);
				p.displayClientMessage(Component.translatable("message.projecthero.doomsday.no_escape").withStyle(ChatFormatting.DARK_RED), true);
			}
		}
		// he never leaves the arena either: past the edge he leaps back to the middle
		DoomsdayEntity d = doomsday(level);
		if (d != null && !d.helpless() && horizontalDistance(d) > r + 6 && d.onGround()) {
			Vec3 c = Vec3.atBottomCenterOf(center());
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(c.x), Mth.floor(c.z));
			d.teleportTo(c.x, y, c.z);
			DoomsdayFx.impact(level, new Vec3(c.x, y, c.z), 2.0);
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
		if (phase != Phase.FIGHT) {
			return;
		}
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
		int points = Math.max(16, Math.min(192, DoomsdayConfig.arena().borderPoints));
		ParticleBatch batch = new ParticleBatch();
		double reach = ParticleBatch.FORCE_RANGE;
		for (int i = 0; i < points; i++) {
			double a = i * Math.PI * 2 / points + (now % 120) * 0.0015;
			double x = c.getX() + 0.5 + Math.cos(a) * r;
			double z = c.getZ() + 0.5 + Math.sin(a) * r;
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
			batch.addForced(i % 2 == 0 ? DoomsdayFx.ASH : DoomsdayFx.RED, reach, x, y + 0.8, z, 1, 0, 0.5, 0, 0);
		}
		for (ServerPlayer viewer : viewers) {
			batch.send(level, viewer);
		}
	}

	// ---------------------------------------------------------------- Doomsday's callbacks

	public void notePart(UUID item) {
		parts.add(item);
	}

	public void onBrokeFree(ServerLevel level) {
		broadcastTitle(level, Component.translatable("title.projecthero.doomsday.unbound").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
				Component.translatable("title.projecthero.doomsday.unbound.sub").withStyle(ChatFormatting.GRAY));
	}

	public void onDoomsdayDown(ServerLevel level, DoomsdayEntity d, int life, boolean finalDeath, Bucket killer) {
		deaths++;
		Component sub = killer == null ? Component.translatable("title.projecthero.doomsday.down.sub_none").withStyle(ChatFormatting.GRAY)
				: Component.translatable("title.projecthero.doomsday.down.sub", DoomsdayEntity.bucketName(killer)).withStyle(ChatFormatting.GRAY);
		if (finalDeath) {
			broadcastTitle(level, Component.translatable("title.projecthero.doomsday.final").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD),
					Component.translatable("title.projecthero.doomsday.final.sub").withStyle(ChatFormatting.GRAY));
			if (killer != null) {
				broadcast(level, Component.translatable("message.projecthero.doomsday.killed_by", DoomsdayEntity.bucketName(killer)).withStyle(ChatFormatting.GRAY));
			}
			giveProjector(level, d);
		} else {
			broadcastTitle(level, Component.translatable("title.projecthero.doomsday.down", life).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), sub);
		}
	}

	public void onDoomsdayRevived(ServerLevel level, DoomsdayEntity d, int life) {
		String key = life > 3 ? "title.projecthero.doomsday.enraged" : "title.projecthero.doomsday.rises";
		broadcastTitle(level, Component.translatable(key, life).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
				Component.translatable(key + ".sub").withStyle(ChatFormatting.GRAY));
	}

	/**
	 * The third death: every Phantom Zone shard the fighters carry (and any still lying around) is taken, and the
	 * Projector goes to whoever carried the most (else the nearest fighter). A later enraged death gives a new one only if
	 * nobody still has one.
	 */
	private void giveProjector(ServerLevel level, DoomsdayEntity d) {
		Item projector = DoomsdayItems.PROJECTOR;
		ServerPlayer best = null;
		int bestCount = -1;
		boolean someoneHasOne = false;
		for (ServerPlayer p : fighterPlayers(level)) {
			int n = removeItems(p, DoomsdayItems.PHANTOM_SHARD, Integer.MAX_VALUE);
			if (p.getInventory().countItem(projector) > 0) {
				someoneHasOne = true;
			}
			if (n > bestCount || n == bestCount && best != null && p.distanceToSqr(d) < best.distanceToSqr(d)) {
				best = p;
				bestCount = n;
			}
		}
		for (UUID u : parts) {
			if (level.getEntity(u) instanceof ItemEntity ie) {
				ie.discard();
			}
		}
		parts.clear();
		if (best == null || someoneHasOne && projectorGiven) {
			return;
		}
		ItemStack stack = new ItemStack(projector);
		if (!best.getInventory().add(stack)) {
			best.drop(stack, false);
		}
		projectorGiven = true;
		broadcast(level, Component.translatable("message.projecthero.doomsday.projector_given", best.getDisplayName()).withStyle(ChatFormatting.AQUA));
		level.playSound(null, best.getX(), best.getY(), best.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2.0f, 1.4f);
	}

	public void onBanished(ServerLevel level, ServerPlayer by) {
		if (state().finished()) {
			return;
		}
		phase = Phase.GONE;
		broadcastTitle(level, Component.translatable("title.projecthero.doomsday.banished").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD),
				Component.translatable("title.projecthero.doomsday.banished.sub").withStyle(ChatFormatting.GRAY));
		if (by != null) {
			down.remove(by.getUUID());
		}
		complete(level);
		EventSavedData.get(level).remove(id());
	}

	// ---------------------------------------------------------------- deaths and the end

	public List<ServerPlayer> fighterPlayers(ServerLevel level) {
		List<ServerPlayer> out = new ArrayList<>();
		for (UUID u : fighters) {
			if (!down.contains(u) && level.getPlayerByUUID(u) instanceof ServerPlayer p && p.isAlive()) {
				out.add(p);
			}
		}
		return out;
	}

	public void onFighterDied(ServerLevel level, ServerPlayer player) {
		if (!fighters.contains(player.getUUID()) || state().finished()) {
			return;
		}
		down.add(player.getUUID());
		participants().recordDeath(player.getUUID());
		broadcast(level, Component.translatable("message.projecthero.doomsday.fighter_down", player.getDisplayName()).withStyle(ChatFormatting.DARK_RED));
		if (down.containsAll(fighters) && phase == Phase.FIGHT) {
			DoomsdayEntity d = doomsday(level);
			if (d != null) {
				// he leaps off into the sky, looking for the next fight
				DoomsdayFx.impact(level, d.position(), 3.0);
				level.playSound(null, d.getX(), d.getY(), d.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 4.0f, 0.4f);
			}
			fail(level, Component.translatable("message.projecthero.doomsday.all_down").withStyle(ChatFormatting.DARK_RED));
		}
	}

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
		DoomsdayWorldState ws = DoomsdayWorldState.get(level.getServer());
		if (success) {
			List<ServerPlayer> winners = winners(level);
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ());
			BlockPos chest = new BlockPos(c.getX(), Math.max(y, c.getY()), c.getZ());
			if (level.getBlockState(chest).canBeReplaced()) {
				DoomsdayRewards.placeChest(level, chest, Math.max(1, winners.size()));
			}
			for (ServerPlayer p : winners) {
				DoomsdayRewards.grantClear(p);
			}
			ws.banished++;
			level.playSound(null, c, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);
			EventSavedData.get(level).noteCompleted();
			ProjectHeroMod.LOGGER.info("[ProjectHero] Doomsday {} banished; {} fighter(s) rewarded", id(), winners.size());
		} else {
			ws.lost++;
		}
		ws.setDirty();
	}

	@Override
	protected void onAborted(ServerLevel level) {
		tearDown(level);
	}

	@Override
	protected void onPaused(ServerLevel level) {
		super.onPaused(level);
		if (bar != null) {
			bar.clear(level);
		}
		ZombieRaidNetworking.clearSky(level, center());
	}

	private void tearDown(ServerLevel level) {
		if (bar != null) {
			bar.clear(level);
		}
		ZombieRaidNetworking.clearSky(level, center());
		CasketEntity casket = casket(level);
		if (casket != null) {
			casket.discard();
		}
		for (UUID u : parts) {
			if (level.getEntity(u) instanceof ItemEntity ie) {
				ie.discard();
			}
		}
		parts.clear();
		// the shards and the Projector never outlive the event
		for (UUID u : fighters) {
			if (level.getPlayerByUUID(u) instanceof ServerPlayer p) {
				removeItems(p, DoomsdayItems.PHANTOM_SHARD, Integer.MAX_VALUE);
				removeItems(p, DoomsdayItems.PROJECTOR, Integer.MAX_VALUE);
			}
		}
	}

	static int removeItems(Player p, Item item, int max) {
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

	// ---------------------------------------------------------------- the bar

	private void updateBar(ServerLevel level, long now) {
		if (bar == null) {
			bar = new EventBossBar(id(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_6, true);
		}
		Component name;
		float progress;
		DoomsdayEntity d = doomsday(level);
		if (phase == Phase.IMPACT) {
			name = Component.translatable("event.projecthero.doomsday.bar.impact");
			progress = 1f;
		} else if (d != null && d.isCorpse()) {
			name = Component.translatable(d.finalDeath() ? "event.projecthero.doomsday.bar.banish" : "event.projecthero.doomsday.bar.corpse",
					(int) (d.reviveFraction() * 100));
			progress = 1f - d.reviveFraction();
		} else {
			name = Component.translatable("event.projecthero.doomsday.bar.fight", Math.min(3, deaths), fighterCount());
			progress = Mth.clamp(1f - deaths / 3f, 0.02f, 1f);
		}
		bar.update(level, center(), radius(), name.copy().withStyle(ChatFormatting.DARK_RED), progress);
	}

	// ---------------------------------------------------------------- debug

	/** {@code /projecthero raid advance doomsday}: impact -> out -> kill this life -> revive -> ... -> banish. */
	public void debugAdvance(ServerLevel level, ServerPlayer by) {
		switch (phase) {
			case IMPACT -> {
				CasketEntity c = casket(level);
				if (c != null) {
					c.debugBurst(level);
				} else {
					onCasketBurst(level, CasketEntity.landed(level, id(), Vec3.atBottomCenterOf(center()), casketYaw, 1));
				}
			}
			case FIGHT -> {
				DoomsdayEntity d = doomsday(level);
				if (d == null) {
					return;
				}
				if (d.isCorpse()) {
					if (d.finalDeath() && by != null && d.banish(level, by)) {
						return;
					}
					d.debugFinishRevival();
				} else if (!d.helpless()) {
					d.hurt(level.damageSources().generic(), d.getHealth() + 10000f);
				}
			}
			default -> {
			}
		}
	}

	// ---------------------------------------------------------------- persistence

	@Override
	protected void saveExtra(CompoundTag tag) {
		tag.putString("Phase", phase.name());
		tag.putLong("PhaseStart", phaseStart);
		ListTag f = new ListTag();
		for (UUID u : fighters) {
			f.add(NbtUtils.createUUID(u));
		}
		tag.put("Fighters", f);
		ListTag dn = new ListTag();
		for (UUID u : down) {
			dn.add(NbtUtils.createUUID(u));
		}
		tag.put("Down", dn);
		ListTag pt = new ListTag();
		for (UUID u : parts) {
			pt.add(NbtUtils.createUUID(u));
		}
		tag.put("Parts", pt);
		if (doomsdayId != null) {
			tag.putUUID("Doomsday", doomsdayId);
		}
		if (casketId != null) {
			tag.putUUID("Casket", casketId);
		}
		tag.putBoolean("FromBlock", fromCasketBlock);
		tag.putFloat("Yaw", casketYaw);
		tag.putInt("Deaths", deaths);
		tag.putBoolean("ProjectorGiven", projectorGiven);
	}

	@Override
	protected void loadExtra(CompoundTag tag) {
		try {
			phase = Phase.valueOf(tag.getString("Phase"));
		} catch (IllegalArgumentException e) {
			phase = Phase.IMPACT;
		}
		phaseStart = tag.getLong("PhaseStart");
		fighters.clear();
		down.clear();
		parts.clear();
		ListTag f = tag.getList("Fighters", Tag.TAG_INT_ARRAY);
		for (int i = 0; i < f.size(); i++) {
			fighters.add(NbtUtils.loadUUID(f.get(i)));
		}
		ListTag dn = tag.getList("Down", Tag.TAG_INT_ARRAY);
		for (int i = 0; i < dn.size(); i++) {
			down.add(NbtUtils.loadUUID(dn.get(i)));
		}
		ListTag pt = tag.getList("Parts", Tag.TAG_INT_ARRAY);
		for (int i = 0; i < pt.size(); i++) {
			parts.add(NbtUtils.loadUUID(pt.get(i)));
		}
		doomsdayId = tag.hasUUID("Doomsday") ? tag.getUUID("Doomsday") : null;
		casketId = tag.hasUUID("Casket") ? tag.getUUID("Casket") : null;
		fromCasketBlock = tag.getBoolean("FromBlock");
		casketYaw = tag.getFloat("Yaw");
		deaths = tag.getInt("Deaths");
		projectorGiven = tag.getBoolean("ProjectorGiven");
	}
}
