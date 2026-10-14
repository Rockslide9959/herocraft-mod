package com.projecthero.mod.city;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;

/**
 * v0.16.2 City part B: which City building a position is in, read from the template id of the piece that covers it
 * (a substring of the template path, so {@code projecthero:city/core/hospital_base} and {@code .../hospital_tower} are
 * both the hospital). Doctors, firefighters and the newspaper librarians are found this way: a vanilla profession whose
 * job site sits in the right building.
 */
public enum CityBuilding {
	HOSPITAL("hospital"),
	FIRE_STATION("fire_station", "firestation", "fire_hall"),
	BULLETIN("bulletin", "newspaper"),
	BANK("bank"),
	POLICE("police", "precinct"),
	DEPOT("depot", "maintenance");

	private final String[] keys;

	CityBuilding(String... keys) {
		this.keys = keys;
	}

	public boolean matches(ResourceLocation template) {
		if (template == null) {
			return false;
		}
		String path = template.getPath();
		for (String k : keys) {
			if (path.contains(k)) {
				return true;
			}
		}
		return false;
	}

	/** True if {@code pos} is inside a piece of this building in some City. */
	public boolean contains(ServerLevel level, BlockPos pos) {
		CityArea city = CityLocator.cityAt(level, pos);
		if (city == null) {
			return false;
		}
		for (StructurePiece p : city.piecesAt(pos)) {
			if (matches(CityBlueprint.templateId(p))) {
				return true;
			}
		}
		return false;
	}

	/** The boxes of this building's pieces in {@code city} within {@code range} blocks of {@code near}. */
	public List<BoundingBox> boxesNear(CityArea city, BlockPos near, int range) {
		List<BoundingBox> out = new ArrayList<>();
		for (StructurePiece p : city.pieces()) {
			BoundingBox b = p.getBoundingBox();
			if (matches(CityBlueprint.templateId(p)) && b.inflatedBy(range).isInside(near)) {
				out.add(b);
			}
		}
		return out;
	}
}
