package com.projecthero.mod.alarm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.alarm.block.AlarmBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * v0.17.7: every alarm zone in one dimension ({@code data/projecthero_alarm_zones.dat}).
 *
 * <p>Zones appear two ways:
 * <ul>
 * <li><b>From a structure</b>: a structure registered with {@link AlarmProfiles#registerStructure} becomes a zone (its
 * start's bounding box, id = the box's min corner) the first time a player stands in it ({@link #find}).</li>
 * <li><b>Explicitly</b>: {@link #register(BoundingBox, String)} (gametests, harnesses, anything built on a live level,
 * e.g. {@code HideoutPiece.buildAll}).</li>
 * </ul>
 * A zone scans its loaded chunk columns once each for Alarm Panels, Alarm Lamps, Blast Doors and spawn points
 * ({@link #scan}); blocks placed later in a live level add themselves ({@link #noteBlock}).
 *
 * <p>World storage, not a static map: nothing for {@code ServerStateReset}.
 */
public final class AlarmZones extends SavedData {
	private static final String FILE_ID = ProjectHeroMod.MOD_ID + "_alarm_zones";
	private static final SavedData.Factory<AlarmZones> FACTORY = new SavedData.Factory<>(AlarmZones::new, AlarmZones::load,
			DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);

	private final Map<Long, AlarmZone> zones = new LinkedHashMap<>();
	/** Not saved: the zone each player was last told about (HUD sync), so leaving sends one "none". */
	final Map<UUID, Long> lastSynced = new HashMap<>();

	public static AlarmZones get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
	}

	public Collection<AlarmZone> all() {
		return Collections.unmodifiableCollection(zones.values());
	}

	public AlarmZone byId(long id) {
		return zones.get(id);
	}

	/** The stored zone containing {@code pos} (no structure lookup), or null. Smallest box wins. */
	public AlarmZone zoneAt(BlockPos pos) {
		AlarmZone best = null;
		long bestVol = Long.MAX_VALUE;
		for (AlarmZone z : zones.values()) {
			if (z.contains(pos)) {
				long vol = (long) z.box.getXSpan() * z.box.getYSpan() * z.box.getZSpan();
				if (vol < bestVol) {
					bestVol = vol;
					best = z;
				}
			}
		}
		return best;
	}

	/** Registers (or returns) the zone with this box; its id is the box's min corner. */
	public AlarmZone register(BoundingBox box, String profileId) {
		long id = new BlockPos(box.minX(), box.minY(), box.minZ()).asLong();
		AlarmZone z = zones.get(id);
		if (z == null) {
			z = new AlarmZone(id, box, profileId);
			zones.put(id, z);
		} else {
			z.profileId = profileId;
		}
		setDirty();
		return z;
	}

	/** Forgets one zone (tests). */
	public void remove(long id) {
		if (zones.remove(id) != null) {
			setDirty();
		}
	}

	/** Test seam: forget every zone whose box intersects {@code box}. */
	public void removeWithin(BoundingBox box) {
		if (zones.values().removeIf(z -> z.box.intersects(box))) {
			setDirty();
		}
	}

	/**
	 * The zone at {@code pos}: a stored one, else (when {@code pos} is inside a registered alarm structure's start box)
	 * a new one made from that structure. Null when {@code pos} is not guarded.
	 */
	public static AlarmZone find(ServerLevel level, BlockPos pos) {
		AlarmZones zs = get(level);
		AlarmZone z = zs.zoneAt(pos);
		if (z != null || AlarmProfiles.structures().isEmpty() || !level.isLoaded(pos)) {
			return z;
		}
		var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
		for (Map.Entry<ResourceLocation, String> e : AlarmProfiles.structures().entrySet()) {
			Structure s = registry.get(e.getKey());
			if (s == null) {
				continue;
			}
			StructureStart start = level.structureManager().getStructureAt(pos, s);
			if (start != null && start.isValid()) {
				return zs.register(start.getBoundingBox(), e.getValue());
			}
		}
		return null;
	}

	/** A panel / lamp / door / spawn point was placed in a live level: file it under its zone. */
	public static void noteBlock(ServerLevel level, BlockPos pos, BlockState state) {
		AlarmZone z = get(level).zoneAt(pos);
		if (z != null && file(z, pos, state)) {
			get(level).setDirty();
		}
	}

	private static boolean file(AlarmZone z, BlockPos pos, BlockState s) {
		long l = pos.asLong();
		if (s.is(AlarmBlocks.PANEL)) {
			return z.panels.add(l);
		}
		if (AlarmBlocks.isLamp(s)) {
			return z.lamps.add(l);
		}
		if (s.is(AlarmBlocks.BLAST_DOOR)) {
			return z.doors.add(l);
		}
		if (s.is(AlarmBlocks.SPAWN_POINT)) {
			return z.spawns.add(l);
		}
		return false;
	}

	/** Scans every loaded, not yet scanned chunk column of the zone. Returns how many columns were scanned. */
	public static int scan(ServerLevel level, AlarmZone z) {
		int n = 0;
		BoundingBox b = z.box;
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int cx = b.minX() >> 4; cx <= b.maxX() >> 4; cx++) {
			for (int cz = b.minZ() >> 4; cz <= b.maxZ() >> 4; cz++) {
				long key = ChunkPos.asLong(cx, cz);
				if (z.scanned.contains(key)) {
					continue;
				}
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) {
					continue;
				}
				z.scanned.add(key);
				n++;
				int x0 = Math.max(b.minX(), cx << 4);
				int x1 = Math.min(b.maxX(), (cx << 4) + 15);
				int z0 = Math.max(b.minZ(), cz << 4);
				int z1 = Math.min(b.maxZ(), (cz << 4) + 15);
				for (int sy = Math.max(b.minY(), level.getMinBuildHeight()) >> 4; sy <= Math.min(b.maxY(), level.getMaxBuildHeight() - 1) >> 4; sy++) {
					int idx = level.getSectionIndexFromSectionY(sy);
					if (idx < 0 || idx >= chunk.getSections().length) {
						continue;
					}
					LevelChunkSection section = chunk.getSection(idx);
					if (section.hasOnlyAir() || !section.maybeHas(AlarmBlocks::isZoneBlock)) {
						continue;
					}
					int y0 = Math.max(b.minY(), sy << 4);
					int y1 = Math.min(b.maxY(), (sy << 4) + 15);
					for (int x = x0; x <= x1; x++) {
						for (int y = y0; y <= y1; y++) {
							for (int zz = z0; zz <= z1; zz++) {
								BlockState s = section.getBlockState(x & 15, y & 15, zz & 15);
								if (AlarmBlocks.isZoneBlock(s)) {
									file(z, m.set(x, y, zz).immutable(), s);
								}
							}
						}
					}
				}
			}
		}
		if (n > 0) {
			get(level).setDirty();
		}
		return n;
	}

	/** Players (any game mode) standing in the zone. */
	public static List<ServerPlayer> playersIn(ServerLevel level, AlarmZone z) {
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer p : level.players()) {
			if (z.contains(p.blockPosition())) {
				out.add(p);
			}
		}
		return out;
	}

	/** Mark the save dirty (the director calls this when a zone changed). */
	public void changed() {
		setDirty();
	}

	// ---------------------------------------------------------------- save

	private static AlarmZones load(CompoundTag tag, HolderLookup.Provider registries) {
		AlarmZones s = new AlarmZones();
		ListTag list = tag.getList("Zones", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			AlarmZone z = AlarmZone.load(list.getCompound(i));
			if (z != null) {
				s.zones.put(z.id, z);
			}
		}
		return s;
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag list = new ListTag();
		for (AlarmZone z : zones.values()) {
			list.add(z.save());
		}
		tag.put("Zones", list);
		return tag;
	}
}
