package com.projecthero.mod.city;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import com.projecthero.mod.mixin.CitySinglePoolElementAccessor;
import com.projecthero.mod.mixin.CityStructureTemplateAccessor;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * v0.16.2 City part B: "what should be at this position?", read straight from the City's placed jigsaw pieces -- no
 * second copy of the City is stored.
 *
 * <p>A {@link PoolElementStructurePiece} with a {@link SinglePoolElement} remembers its template, its corner
 * ({@code getPosition}) and its rotation (pool pieces are never mirrored and rotate about their corner). A world
 * position maps back into the template by subtracting the corner and applying the inverse rotation; the template's
 * block at that local position, rotated forward, is the expected state.
 *
 * <p>Each template's palette is indexed once by local position (non-air blocks only, without jigsaw and structure
 * blocks, which the placement replaces) and cached (LRU, {@value #CACHE_SIZE} templates). Pieces of any other kind,
 * multi-palette templates and inline templates without an index simply have no expectation (null).
 */
public final class CityBlueprint {
	private static final int CACHE_SIZE = 96;

	private static final Map<StructureTemplate, Long2ObjectOpenHashMap<BlockState>> INDEX = Collections.synchronizedMap(
			new LinkedHashMap<>(64, 0.75f, true) {
				@Override
				protected boolean removeEldestEntry(Map.Entry<StructureTemplate, Long2ObjectOpenHashMap<BlockState>> eldest) {
					return size() > CACHE_SIZE;
				}
			});

	private CityBlueprint() {
	}

	/** The expected block at {@code pos} in {@code city}, or null if the blueprint says nothing about it (air included). */
	public static BlockState expectedAt(ServerLevel level, CityArea city, BlockPos pos) {
		for (StructurePiece piece : city.piecesAt(pos)) {
			BlockState s = expectedIn(level, piece, pos);
			if (s != null) {
				return s;
			}
		}
		return null;
	}

	/** The expected block at {@code pos} according to one placed piece, or null. */
	public static BlockState expectedIn(ServerLevel level, StructurePiece piece, BlockPos pos) {
		if (!(piece instanceof PoolElementStructurePiece pe) || !pe.getBoundingBox().isInside(pos)) {
			return null;
		}
		Long2ObjectOpenHashMap<BlockState> index = index(level, pe);
		if (index == null) {
			return null;
		}
		Rotation rot = pe.getRotation();
		BlockPos local = StructureTemplate.transform(pos.subtract(pe.getPosition()), Mirror.NONE, inverse(rot), BlockPos.ZERO);
		BlockState s = index.get(local.asLong());
		return s == null ? null : s.rotate(rot);
	}

	/** Calls {@code out} for every expected (non-air) block of {@code piece} inside {@code clip}, in world positions. */
	public static void forEachExpected(ServerLevel level, StructurePiece piece, BoundingBox clip, BiConsumer<BlockPos, BlockState> out) {
		if (!(piece instanceof PoolElementStructurePiece pe) || !pe.getBoundingBox().intersects(clip)) {
			return;
		}
		Long2ObjectOpenHashMap<BlockState> index = index(level, pe);
		if (index == null) {
			return;
		}
		Rotation rot = pe.getRotation();
		BlockPos origin = pe.getPosition();
		for (Long2ObjectMap.Entry<BlockState> e : index.long2ObjectEntrySet()) {
			BlockPos world = StructureTemplate.transform(BlockPos.of(e.getLongKey()), Mirror.NONE, rot, BlockPos.ZERO).offset(origin);
			if (clip.isInside(world)) {
				out.accept(world, e.getValue().rotate(rot));
			}
		}
	}

	/** The template id a piece was built from (for "which building is this?"), or null. */
	public static ResourceLocation templateId(StructurePiece piece) {
		if (piece instanceof PoolElementStructurePiece pe && pe.getElement() instanceof SinglePoolElement single) {
			return ((CitySinglePoolElementAccessor) single).projecthero$template().left().orElse(null);
		}
		return null;
	}

	/**
	 * True if the piece's pool element runs structure processors (random block swaps, decay...). The audit sweep skips
	 * those pieces: a processor may have left a template position empty on purpose. Event-tracked damage still works,
	 * because that only fires when the block that was there matched the blueprint.
	 */
	public static boolean hasProcessors(StructurePiece piece) {
		if (piece instanceof PoolElementStructurePiece pe && pe.getElement() instanceof SinglePoolElement single) {
			var holder = ((CitySinglePoolElementAccessor) single).projecthero$processors();
			return holder != null && holder.isBound() && !holder.value().list().isEmpty();
		}
		return true;
	}

	static Rotation inverse(Rotation rot) {
		return switch (rot) {
			case CLOCKWISE_90 -> Rotation.COUNTERCLOCKWISE_90;
			case COUNTERCLOCKWISE_90 -> Rotation.CLOCKWISE_90;
			default -> rot;
		};
	}

	private static Long2ObjectOpenHashMap<BlockState> index(ServerLevel level, PoolElementStructurePiece pe) {
		if (!(pe.getElement() instanceof SinglePoolElement single)) {
			return null;
		}
		// terrain-matching pieces (village streets) are bent onto the ground when placed: their template is no blueprint
		if (single.getProjection() == StructureTemplatePool.Projection.TERRAIN_MATCHING) {
			return null;
		}
		StructureTemplate template = ((CitySinglePoolElementAccessor) single).projecthero$template()
				.map(id -> level.getStructureManager().get(id).orElse(null), t -> t);
		if (template == null) {
			return null;
		}
		Long2ObjectOpenHashMap<BlockState> cached = INDEX.get(template);
		if (cached != null) {
			return cached.isEmpty() && multiPalette(template) ? null : cached;
		}
		Long2ObjectOpenHashMap<BlockState> built = new Long2ObjectOpenHashMap<>();
		List<StructureTemplate.Palette> palettes = ((CityStructureTemplateAccessor) template).projecthero$palettes();
		if (palettes.size() == 1) {
			for (StructureTemplate.StructureBlockInfo info : palettes.get(0).blocks()) {
				BlockState s = info.state();
				if (s.isAir() || s.is(Blocks.JIGSAW) || s.is(Blocks.STRUCTURE_BLOCK) || s.is(Blocks.STRUCTURE_VOID)) {
					continue;
				}
				built.put(info.pos().asLong(), s);
			}
		}
		INDEX.put(template, built);
		return palettes.size() == 1 ? built : null;
	}

	private static boolean multiPalette(StructureTemplate template) {
		return ((CityStructureTemplateAccessor) template).projecthero$palettes().size() != 1;
	}

	/** Tests: forget the cached template indexes (a test template was rebuilt under the same object). */
	public static void clearSessionState() {
		INDEX.clear();
	}
}
