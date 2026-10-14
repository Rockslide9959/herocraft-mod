package com.projecthero.mod.city;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import com.projecthero.mod.ProjectHeroMod;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * v0.16.2 City part B: "is this position in a City, and which one?". The one place that knows about the
 * {@code projecthero:city} structure (built by City part A): membership is
 * {@code level.structureManager().getStructureWithPieceAt(pos, city)}, and the pieces of that start are the blueprint.
 *
 * <p>Two extras keep it cheap and testable:
 * <ul>
 * <li><b>Chunk filter.</b> Every loaded chunk that holds a reference to a City start is in a per-level set, so the
 * block-change hook costs one hash lookup everywhere else ({@link #isCityChunk}).</li>
 * <li><b>Registered cities.</b> {@link #register} adds a City made of hand-built pieces (gametests, and anything that
 * wants City services without A's worldgen). They are checked before the real structure and never saved.</li>
 * </ul>
 */
public final class CityLocator {
	public static final ResourceKey<Structure> CITY_KEY = ResourceKey.create(Registries.STRUCTURE, ProjectHeroMod.id("city"));

	private static final Map<ResourceKey<Level>, LongOpenHashSet> CITY_CHUNKS = new ConcurrentHashMap<>();
	private static final Map<ResourceKey<Level>, List<CityArea>> REGISTERED = new ConcurrentHashMap<>();
	private static final Map<ResourceKey<Level>, Long2ObjectOpenHashMap<CityArea>> AREA_CACHE = new ConcurrentHashMap<>();
	private static final AtomicLong NEXT_REGISTERED_ID = new AtomicLong(-1);

	private CityLocator() {
	}

	static void initialize() {
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk) -> {
			Structure city = structure(level);
			if (city != null && !chunk.getReferencesForStructure(city).isEmpty()) {
				chunks(level).add(chunk.getPos().toLong());
			}
		});
		ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
			LongOpenHashSet set = CITY_CHUNKS.get(level.dimension());
			if (set != null) {
				set.remove(chunk.getPos().toLong());
			}
		});
	}

	/** The {@code projecthero:city} structure, or null if this world has none (City part A not present). */
	public static Structure structure(ServerLevel level) {
		return level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(CITY_KEY);
	}

	private static LongOpenHashSet chunks(ServerLevel level) {
		return CITY_CHUNKS.computeIfAbsent(level.dimension(), k -> new LongOpenHashSet());
	}

	/** The fast filter: true if the chunk {@code chunkPos} (a {@link ChunkPos#toLong}) is part of any City. */
	public static boolean isCityChunk(Level level, long chunkPos) {
		LongOpenHashSet set = CITY_CHUNKS.get(level.dimension());
		return set != null && set.contains(chunkPos);
	}

	/** Loaded City chunks of {@code level} (a copy). */
	public static LongSet loadedCityChunks(ServerLevel level) {
		LongOpenHashSet set = CITY_CHUNKS.get(level.dimension());
		return set == null ? new LongOpenHashSet() : new LongOpenHashSet(set);
	}

	/** The City with a piece at {@code pos} (the blueprint membership rule), or null. */
	public static CityArea cityAt(ServerLevel level, BlockPos pos) {
		for (CityArea a : registered(level)) {
			if (a.hasPieceAt(pos)) {
				return a;
			}
		}
		if (!isCityChunk(level, ChunkPos.asLong(pos))) {
			return null;
		}
		Structure city = structure(level);
		if (city == null) {
			return null;
		}
		StructureStart start = level.structureManager().getStructureWithPieceAt(pos, city);
		return start.isValid() ? area(level, start) : null;
	}

	/** The City whose outer bounds hold {@code pos} (streets between pieces count), or null. */
	public static CityArea cityAround(ServerLevel level, BlockPos pos) {
		for (CityArea a : registered(level)) {
			if (a.contains(pos)) {
				return a;
			}
		}
		if (!isCityChunk(level, ChunkPos.asLong(pos))) {
			return null;
		}
		Structure city = structure(level);
		if (city == null) {
			return null;
		}
		StructureStart start = level.structureManager().getStructureAt(pos, city);
		return start.isValid() ? area(level, start) : null;
	}

	/** Every City overlapping the chunk {@code chunk} (for the audit sweep). */
	public static List<CityArea> citiesInChunk(ServerLevel level, ChunkPos chunk) {
		List<CityArea> out = new ArrayList<>();
		for (CityArea a : registered(level)) {
			BoundingBox b = a.bounds();
			if (chunk.x >= b.minX() >> 4 && chunk.x <= b.maxX() >> 4 && chunk.z >= b.minZ() >> 4 && chunk.z <= b.maxZ() >> 4) {
				out.add(a);
			}
		}
		Structure city = structure(level);
		if (city != null && level.hasChunk(chunk.x, chunk.z)) {
			for (StructureStart start : level.structureManager().startsForStructure(chunk, s -> s == city)) {
				if (start.isValid()) {
					out.add(area(level, start));
				}
			}
		}
		return out;
	}

	/** A City by id, if one is registered or cached (cached = met since the server started). */
	public static CityArea byId(ServerLevel level, long id) {
		for (CityArea a : registered(level)) {
			if (a.id() == id) {
				return a;
			}
		}
		Long2ObjectOpenHashMap<CityArea> cache = AREA_CACHE.get(level.dimension());
		return cache == null ? null : cache.get(id);
	}

	private static CityArea area(ServerLevel level, StructureStart start) {
		long id = start.getChunkPos().toLong();
		Long2ObjectOpenHashMap<CityArea> cache = AREA_CACHE.computeIfAbsent(level.dimension(), k -> new Long2ObjectOpenHashMap<>());
		CityArea a = cache.get(id);
		if (a == null) {
			a = new CityArea(id, start.getBoundingBox(), List.copyOf(start.getPieces()));
			cache.put(id, a);
		}
		return a;
	}

	private static List<CityArea> registered(ServerLevel level) {
		List<CityArea> list = REGISTERED.get(level.dimension());
		return list == null ? List.of() : list;
	}

	/**
	 * Registers a City made of {@code pieces} (gametests; anything that wants City services without the worldgen). Its
	 * chunks join the fast filter. Remove it with {@link #unregister}. Never saved.
	 */
	public static CityArea register(ServerLevel level, List<StructurePiece> pieces) {
		BoundingBox bounds = null;
		for (StructurePiece p : pieces) {
			bounds = bounds == null ? p.getBoundingBox() : BoundingBox.encapsulatingBoxes(List.of(bounds, p.getBoundingBox())).orElse(bounds);
		}
		if (bounds == null) {
			throw new IllegalArgumentException("a City needs at least one piece");
		}
		CityArea area = new CityArea(NEXT_REGISTERED_ID.getAndDecrement(), bounds, new ArrayList<>(pieces));
		REGISTERED.computeIfAbsent(level.dimension(), k -> new CopyOnWriteArrayList<>()).add(area);
		LongOpenHashSet set = chunks(level);
		for (int cx = bounds.minX() >> 4; cx <= bounds.maxX() >> 4; cx++) {
			for (int cz = bounds.minZ() >> 4; cz <= bounds.maxZ() >> 4; cz++) {
				set.add(ChunkPos.asLong(cx, cz));
			}
		}
		return area;
	}

	public static void unregister(ServerLevel level, CityArea area) {
		List<CityArea> list = REGISTERED.get(level.dimension());
		if (list != null) {
			list.remove(area);
		}
		LongOpenHashSet set = CITY_CHUNKS.get(level.dimension());
		if (set != null) {
			BoundingBox b = area.bounds();
			for (int cx = b.minX() >> 4; cx <= b.maxX() >> 4; cx++) {
				for (int cz = b.minZ() >> 4; cz <= b.maxZ() >> 4; cz++) {
					long c = ChunkPos.asLong(cx, cz);
					boolean other = false;
					for (CityArea o : registered(level)) {
						BoundingBox ob = o.bounds();
						if (cx >= ob.minX() >> 4 && cx <= ob.maxX() >> 4 && cz >= ob.minZ() >> 4 && cz <= ob.maxZ() >> 4) {
							other = true;
						}
					}
					Structure city = structure(level);
					boolean real = city != null && level.hasChunk(cx, cz)
							&& !level.getChunk(cx, cz).getReferencesForStructure(city).isEmpty();
					if (!other && !real) {
						set.remove(c);
					}
				}
			}
		}
	}

	public static void clearSessionState() {
		CITY_CHUNKS.clear();
		REGISTERED.clear();
		AREA_CACHE.clear();
	}
}
