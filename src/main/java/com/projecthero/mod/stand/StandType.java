package com.projecthero.mod.stand;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.resources.ResourceLocation;

/**
 * v0.17.2: one Stand (the framework's registry). A Stand has a name, the six canon stat grades (Power, Speed, Range,
 * Stamina, Precision, Potential), a player-shaped skin and a move list. Star Platinum is the first; the Stand Arrow
 * awakens a random one from {@link #pool()}.
 */
public record StandType(String id, String power, String speed, String range, String stamina, String precision, String potential,
		ResourceLocation texture, int colour, List<StandMove> moves) {
	private static final Map<String, StandType> REGISTRY = new LinkedHashMap<>();

	public static final StandType STAR_PLATINUM = register(new StandType("star_platinum", "A", "A", "C", "A", "A", "A",
			ProjectHeroMod.id("textures/entity/stand/star_platinum.png"), 0x9A5CFF, StandMove.STAR_PLATINUM));

	public static StandType register(StandType type) {
		REGISTRY.put(type.id(), type);
		return type;
	}

	/** The Stand with that id, or null. */
	public static StandType byId(String id) {
		return id == null ? null : REGISTRY.get(id);
	}

	/** Every Stand the Arrow can awaken. */
	public static List<StandType> pool() {
		return List.copyOf(REGISTRY.values());
	}

	public String nameKey() {
		return "projecthero.stand." + id;
	}

	/** The six grades in card order. */
	public String[] stats() {
		return new String[] { power, speed, range, stamina, precision, potential };
	}

	/** The move bound to a slot (1..6, or 8 for N) and Shift state, or null. */
	public StandMove move(int slot, boolean shift) {
		for (StandMove m : moves) {
			if (m.slot() == slot && m.shift() == shift) {
				return m;
			}
		}
		return null;
	}

	public StandMove move(String moveId) {
		for (StandMove m : moves) {
			if (m.id().equals(moveId)) {
				return m;
			}
		}
		return null;
	}
}
