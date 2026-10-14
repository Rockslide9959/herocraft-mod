package com.projecthero.mod.city;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;

/**
 * v0.16.2 City part B: one City as the systems see it -- an id (the structure start's chunk, or a negative id for a
 * test city), its outer bounds and its placed pieces (the blueprint). Built from a {@code projecthero:city}
 * {@code StructureStart} by {@link CityLocator}, or registered directly by a gametest.
 */
public record CityArea(long id, BoundingBox bounds, List<StructurePiece> pieces) {

	/** True if {@code pos} is inside the City's outer bounds. */
	public boolean contains(BlockPos pos) {
		return bounds.isInside(pos);
	}

	/** Every piece whose box holds {@code pos} (usually one; contained jigsaw children can overlap a parent's box). */
	public List<StructurePiece> piecesAt(BlockPos pos) {
		List<StructurePiece> out = new ArrayList<>(2);
		for (StructurePiece p : pieces) {
			if (p.getBoundingBox().isInside(pos)) {
				out.add(p);
			}
		}
		return out;
	}

	/** True if any piece's box holds {@code pos}. */
	public boolean hasPieceAt(BlockPos pos) {
		for (StructurePiece p : pieces) {
			if (p.getBoundingBox().isInside(pos)) {
				return true;
			}
		}
		return false;
	}
}
