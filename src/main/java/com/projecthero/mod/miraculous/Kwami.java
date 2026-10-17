package com.projecthero.mod.miraculous;

import org.jetbrains.annotations.Nullable;

/**
 * v0.16.6: the three Miraculous Kwamis. Each is its own Hero-Tier power (its {@link #heroKey}), but all three share one
 * state attachment ({@code MiraculousState}), the box / jewel / Kwami entity / transformation / Shift+Z countdown core
 * ({@link Miraculous}), and differ only in their {@link KwamiKit} (the six keys).
 */
public enum Kwami {
	/** Ladybug: the Earrings, the Yo-yo, cookies. */
	TIKKI("tikki", "ladybug", "earrings", 0xFFE0242C),
	/** Cat Noir: the Ring, the Baton, Camembert. */
	PLAGG("plagg", "cat_noir", "ring", 0xFF7EE23A),
	/** Rena Rouge: the Necklace, the Flute, macarons. */
	TRIXX("trixx", "rena_rouge", "necklace", 0xFFEE7C24),
	/** Hawk Moth (v0.16.6): the Brooch, the Cane, honey. */
	NOOROO("nooroo", "hawk_moth", "brooch", 0xFF9A5CD6);

	/** The Kwami's own id ({@code tikki}): the entity variant, the state's {@code kwami} field, lang keys. */
	public final String id;
	/** The Hero-Tier power key ({@code ladybug}): HeroTiers, the commands, the guide, UniquePowers. */
	public final String heroKey;
	/** Which jewel: earrings / ring / necklace. */
	public final String jewel;
	/** The hero's signature colour (ARGB): HUD accent, transformation light. */
	public final int color;

	Kwami(String id, String heroKey, String jewel, int color) {
		this.id = id;
		this.heroKey = heroKey;
		this.jewel = jewel;
		this.color = color;
	}

	public static @Nullable Kwami byId(String id) {
		for (Kwami k : values()) {
			if (k.id.equals(id)) {
				return k;
			}
		}
		return null;
	}

	public static @Nullable Kwami byHeroKey(String key) {
		for (Kwami k : values()) {
			if (k.heroKey.equals(key)) {
				return k;
			}
		}
		return null;
	}
}
