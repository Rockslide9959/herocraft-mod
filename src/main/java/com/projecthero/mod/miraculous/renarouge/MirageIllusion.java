package com.projecthero.mod.miraculous.renarouge;

import org.jetbrains.annotations.Nullable;

/**
 * v0.16.7 (user): the eight illusions of the Mirage wheel (Shift+Z), in wheel order (clockwise from the top). Client-safe:
 * the wheel reads the ids for its lang keys ({@code screen.projecthero.rena_mirage.<id>} / {@code .hint}). Every one runs
 * {@link RenaRougeConfig#MIRAGE_TICKS}; see {@link RenaMirage}.
 */
public enum MirageIllusion {
	FOXES("foxes", false),
	MONSTER("monster", false),
	VANISH("vanish", false),
	ZONE("zone", true),
	LURE("lure", true),
	PITFALL("pitfall", true),
	ECLIPSE("eclipse", false),
	MIRRORS("mirrors", false);

	public final String id;
	/** Lands where she looks (else round her). */
	public final boolean aimed;

	MirageIllusion(String id, boolean aimed) {
		this.id = id;
		this.aimed = aimed;
	}

	public static @Nullable MirageIllusion byIndex(int i) {
		MirageIllusion[] all = values();
		return i >= 0 && i < all.length ? all[i] : null;
	}
}
