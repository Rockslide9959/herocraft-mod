package com.projecthero.mod.spider;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiPredicate;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.network.SpiderCobwebSyncPayload;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: the REAL cobweb blocks Web Bomb, Impact Web and Web Blossom spin. Only air is ever filled (never a player's
 * block, a block entity or a fluid), only with the mod's terrain-damage setting and the mobGriefing rule on, at most
 * {@code cobwebsPerCast} per cast and {@code cobwebsMax} at once (the oldest go first). Every one is recorded in this
 * server-wide {@link SavedData} ledger with its owner and its expiry; {@link #tick} takes each back to air once it is
 * due -- or as soon as its chunk is loaded again, so a chunk unload or a server restart can never leave one behind. A
 * player breaking one gets nothing (no string, no cobweb item). Spider-Man and his squad are never slowed by his own
 * cobwebs ({@code WebBlockMixin}); the owner and his online squadmates are told which positions are his
 * ({@link SpiderCobwebSyncPayload}) so their clients agree.
 */
public final class SpiderCobwebs extends SavedData {
	private static final String FILE_ID = ProjectHeroMod.MOD_ID + "_spider_cobwebs";

	private record Entry(ResourceKey<Level> dim, UUID owner, long expiresAt) {
	}

	/** key: dimension + "|" + packed position -> entry, oldest first. */
	private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();

	/** Client hook: is the local player exempt from the cobweb at this position (his own / his squad's)? */
	public static volatile BiPredicate<Entity, BlockPos> clientExempt = (e, pos) -> false;

	private static final SavedData.Factory<SpiderCobwebs> FACTORY = new SavedData.Factory<>(SpiderCobwebs::new, SpiderCobwebs::load,
			DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);

	public static SpiderCobwebs get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
	}

	public static void initialize() {
		// breaking one of his cobwebs drops nothing: it is simply taken away
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (!(level instanceof ServerLevel sl) || !state.is(Blocks.COBWEB)) {
				return true;
			}
			SpiderCobwebs data = get(sl.getServer());
			Entry e = data.entries.get(key(sl.dimension(), pos));
			if (e == null) {
				return true;
			}
			sl.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			data.forget(sl, pos, e);
			return false;
		});
	}

	private static String key(ResourceKey<Level> dim, BlockPos pos) {
		return dim.location() + "|" + pos.asLong();
	}

	// ---------------- placing ----------------

	/** May his abilities place real cobwebs here and now? */
	public static boolean allowed(ServerLevel level) {
		return SpiderManConfig.webs().placeCobwebs && AbilityHelpers.canGrief()
				&& level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
	}

	/** Fill the air nearest {@code centre} within {@code radius} with up to {@code max} cobwebs. Returns how many. */
	public static int fillAround(ServerPlayer owner, Vec3 centre, double radius, int max) {
		ServerLevel level = (ServerLevel) owner.level();
		if (!allowed(level) || max <= 0) {
			return 0;
		}
		BlockPos c = BlockPos.containing(centre);
		int r = (int) Math.ceil(radius);
		List<BlockPos> spots = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -r, -r), c.offset(r, r, r))) {
			if (Vec3.atCenterOf(p).distanceToSqr(centre) <= radius * radius) {
				spots.add(p.immutable());
			}
		}
		spots.sort((a, b) -> Double.compare(Vec3.atCenterOf(a).distanceToSqr(centre), Vec3.atCenterOf(b).distanceToSqr(centre)));
		int placed = 0;
		List<Long> added = new ArrayList<>();
		for (BlockPos p : spots) {
			if (placed >= max) {
				break;
			}
			if (placeOne(owner, level, p)) {
				placed++;
				added.add(p.asLong());
			}
		}
		sync(owner, level, added, true);
		return placed;
	}

	/**
	 * v0.16.2 (user): a carpet of cobwebs over the FLOOR of the whole circle of {@code radius} round {@code centre} -- one
	 * per column, on the first solid ground within a few blocks above / below the centre (so it follows steps and slopes).
	 * Not limited by {@code cobwebsPerCast}; the global {@code cobwebsMax} still retires the oldest. Returns how many.
	 */
	public static int floorCarpet(ServerPlayer owner, Vec3 centre, double radius) {
		ServerLevel level = (ServerLevel) owner.level();
		if (!allowed(level)) {
			return 0;
		}
		BlockPos c = BlockPos.containing(centre);
		int r = (int) Math.ceil(radius);
		List<Long> added = new ArrayList<>();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				if (dx * dx + dz * dz > radius * radius) {
					continue;
				}
				for (int dy = FLOOR_SCAN_UP; dy >= -FLOOR_SCAN_DOWN; dy--) {
					pos.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
					BlockPos below = pos.below();
					if (level.getBlockState(pos).isAir()
							&& !level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
						if (placeOne(owner, level, pos.immutable())) {
							added.add(pos.asLong());
						}
						break;
					}
				}
			}
		}
		sync(owner, level, added, true);
		return added.size();
	}

	private static final int FLOOR_SCAN_UP = 2;
	private static final int FLOOR_SCAN_DOWN = 5;

	/** Cobwebs at exactly these positions (a ring, a pinned target). */
	public static int placeAt(ServerPlayer owner, List<BlockPos> positions) {
		ServerLevel level = (ServerLevel) owner.level();
		if (!allowed(level)) {
			return 0;
		}
		List<Long> added = new ArrayList<>();
		int cap = SpiderManConfig.webs().cobwebsPerCast;
		for (BlockPos p : positions) {
			if (added.size() >= cap) {
				break;
			}
			if (placeOne(owner, level, p)) {
				added.add(p.asLong());
			}
		}
		sync(owner, level, added, true);
		return added.size();
	}

	private static boolean placeOne(ServerPlayer owner, ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos) || !level.getBlockState(pos).isAir() || !level.mayInteract(owner, pos)) {
			return false;
		}
		SpiderCobwebs data = get(level.getServer());
		while (data.entries.size() >= Math.max(1, SpiderManConfig.webs().cobwebsMax)) {
			data.removeOldest(level.getServer());
		}
		level.setBlock(pos, Blocks.COBWEB.defaultBlockState(), Block.UPDATE_ALL);
		long expires = level.getServer().overworld().getGameTime() + Math.max(20, SpiderManConfig.webs().cobwebTicks);
		data.entries.put(key(level.dimension(), pos), new Entry(level.dimension(), owner.getUUID(), expires));
		data.setDirty();
		return true;
	}

	private void removeOldest(MinecraftServer server) {
		Iterator<Map.Entry<String, Entry>> it = entries.entrySet().iterator();
		if (!it.hasNext()) {
			return;
		}
		Map.Entry<String, Entry> first = it.next();
		it.remove();
		take(server, first.getKey(), first.getValue());
		setDirty();
	}

	/** Take a cobweb back to air (if its chunk is loaded and it is still a cobweb). */
	private static boolean take(MinecraftServer server, String key, Entry e) {
		ServerLevel level = server.getLevel(e.dim());
		BlockPos pos = BlockPos.of(Long.parseLong(key.substring(key.lastIndexOf('|') + 1)));
		if (level == null) {
			return true;
		}
		if (!level.isLoaded(pos)) {
			return false;
		}
		if (level.getBlockState(pos).is(Blocks.COBWEB)) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		}
		notifyRemoved(server, level, e.owner(), pos);
		return true;
	}

	private void forget(ServerLevel level, BlockPos pos, Entry e) {
		entries.remove(key(level.dimension(), pos));
		setDirty();
		notifyRemoved(level.getServer(), level, e.owner(), pos);
	}

	// ---------------- upkeep ----------------

	/** Twice a second: every cobweb that is due (or whose chunk just came back after it was due) goes. */
	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % 10 != 3) {
			return;
		}
		SpiderCobwebs data = get(server);
		if (data.entries.isEmpty()) {
			return;
		}
		long now = server.overworld().getGameTime();
		boolean changed = false;
		for (Iterator<Map.Entry<String, Entry>> it = data.entries.entrySet().iterator(); it.hasNext();) {
			Map.Entry<String, Entry> en = it.next();
			if (en.getValue().expiresAt() > now) {
				continue;
			}
			if (take(server, en.getKey(), en.getValue())) {
				it.remove();
				changed = true;
			}
		}
		if (changed) {
			data.setDirty();
		}
	}

	/**
	 * v0.16.1 (the stuck-pull rule): tear out every one of his tracked cobwebs inside {@code box} -- something stuck in
	 * them is being pulled loose. Returns how many went.
	 */
	public static int tearIn(ServerLevel level, net.minecraft.world.phys.AABB box) {
		SpiderCobwebs data = get(level.getServer());
		if (data.entries.isEmpty()) {
			return 0;
		}
		int n = 0;
		for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
				BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
			String k = key(level.dimension(), pos);
			Entry e = data.entries.get(k);
			if (e == null) {
				continue;
			}
			BlockPos p = pos.immutable();
			if (level.getBlockState(p).is(Blocks.COBWEB)) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
			data.forget(level, p, e);
			n++;
		}
		return n;
	}

	/** Is this one of Spider-Man's tracked cobwebs (tests, the block mixin)? */
	public static boolean tracked(ServerLevel level, BlockPos pos) {
		return get(level.getServer()).entries.containsKey(key(level.dimension(), pos));
	}

	/** How many of his cobwebs are tracked in all (tests). */
	public static int trackedCount(MinecraftServer server) {
		return get(server).entries.size();
	}

	/** Spider-Man and his squad pass through his own cobwebs (decided per side; see {@code WebBlockMixin}). */
	public static boolean exempt(Level level, BlockPos pos, Entity entity) {
		if (!(entity instanceof Player)) {
			return false;
		}
		if (level.isClientSide) {
			return clientExempt.test(entity, pos);
		}
		Entry e = get(((ServerLevel) level).getServer()).entries.get(key(level.dimension(), pos));
		return e != null && com.projecthero.mod.spider.web.WebStructures.mayUse(entity, e.owner());
	}

	// ---------------- client sync ----------------

	private static List<ServerPlayer> audience(MinecraftServer server, UUID owner) {
		List<ServerPlayer> out = new ArrayList<>();
		ServerPlayer o = server.getPlayerList().getPlayer(owner);
		if (o != null) {
			out.add(o);
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p != o && com.projecthero.mod.squad.SquadManager.get(server).sameSquad(owner, p.getUUID())) {
				out.add(p);
			}
		}
		return out;
	}

	private static void sync(ServerPlayer owner, ServerLevel level, List<Long> positions, boolean add) {
		if (positions.isEmpty()) {
			return;
		}
		SpiderCobwebSyncPayload payload = new SpiderCobwebSyncPayload(add, positions.stream().mapToLong(Long::longValue).toArray());
		for (ServerPlayer p : audience(level.getServer(), owner.getUUID())) {
			if (p.level() == level && ServerPlayNetworking.canSend(p, SpiderCobwebSyncPayload.TYPE)) {
				ServerPlayNetworking.send(p, payload);
			}
		}
	}

	private static void notifyRemoved(MinecraftServer server, ServerLevel level, UUID owner, BlockPos pos) {
		SpiderCobwebSyncPayload payload = new SpiderCobwebSyncPayload(false, new long[] {pos.asLong()});
		for (ServerPlayer p : audience(server, owner)) {
			if (p.level() == level && ServerPlayNetworking.canSend(p, SpiderCobwebSyncPayload.TYPE)) {
				ServerPlayNetworking.send(p, payload);
			}
		}
	}

	// ---------------- save ----------------

	private static SpiderCobwebs load(CompoundTag tag, HolderLookup.Provider registries) {
		SpiderCobwebs data = new SpiderCobwebs();
		ListTag list = tag.getList("Webs", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag t = list.getCompound(i);
			ResourceLocation dim = ResourceLocation.tryParse(t.getString("D"));
			ResourceKey<Level> key = dim == null ? Level.OVERWORLD : ResourceKey.create(Registries.DIMENSION, dim);
			UUID owner = t.hasUUID("O") ? t.getUUID("O") : new UUID(0, 0);
			data.entries.put(key(key, BlockPos.of(t.getLong("P"))), new Entry(key, owner, t.getLong("E")));
		}
		return data;
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag list = new ListTag();
		for (Map.Entry<String, Entry> en : entries.entrySet()) {
			CompoundTag t = new CompoundTag();
			t.putString("D", en.getValue().dim().location().toString());
			t.putLong("P", Long.parseLong(en.getKey().substring(en.getKey().lastIndexOf('|') + 1)));
			t.putUUID("O", en.getValue().owner());
			t.putLong("E", en.getValue().expiresAt());
			list.add(t);
		}
		tag.put("Webs", list);
		return tag;
	}
}
