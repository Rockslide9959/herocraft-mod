package com.projecthero.mod.wolverine.data;

import com.mojang.serialization.Codec;

/**
 * How far a Wolverine's claw progression has come (v0.12.25):
 * {@code NONE} (no claws yet) -> {@code BONE} (Bone Claw Serum) -> {@code ADAMANTIUM} (Adamantium Serum).
 */
public enum ClawTier {
	NONE("none"),
	BONE("bone"),
	ADAMANTIUM("adamantium");

	public static final Codec<ClawTier> CODEC = Codec.STRING.xmap(ClawTier::byId, t -> t.id);

	public final String id;

	ClawTier(String id) {
		this.id = id;
	}

	public boolean hasClaws() {
		return this != NONE;
	}

	public static ClawTier byId(String id) {
		for (ClawTier t : values()) {
			if (t.id.equals(id)) {
				return t;
			}
		}
		return NONE;
	}
}
