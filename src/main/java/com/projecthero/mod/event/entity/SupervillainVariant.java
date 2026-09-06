package com.projecthero.mod.event.entity;

import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;

import com.projecthero.mod.event.EventConfig;

/**
 * The three Supervillain appearances. Purely cosmetic: the appearance is rolled independently of the
 * power (spec section 18), drives only the model / texture / hitbox and the boss-bar name, and is
 * stored as a synced string on the boss entity.
 *
 * <ul>
 *   <li>{@link #CHIMERA} -- an enormous bio-engineered monster, the largest hitbox.</li>
 *   <li>{@link #ARSENAL} -- a heavily armoured techno-supervillain, slightly smaller.</li>
 *   <li>{@link #OMEGA_MAGE} -- a robed sorcerer, roughly humanoid with extra height.</li>
 * </ul>
 */
public enum SupervillainVariant {
	CHIMERA("chimera", 2.15f, "entity.projecthero.supervillain.chimera", "item.projecthero.chimera_core"),
	ARSENAL("arsenal", 1.95f, "entity.projecthero.supervillain.arsenal", "item.projecthero.arsenal_reactor"),
	OMEGA_MAGE("omega_mage", 1.75f, "entity.projecthero.supervillain.omega_mage", "item.projecthero.omega_crystal");

	private final String id;
	private final float scale;
	private final String nameKey;
	private final String trophyKey;

	SupervillainVariant(String id, float scale, String nameKey, String trophyKey) {
		this.id = id;
		this.scale = scale;
		this.nameKey = nameKey;
		this.trophyKey = trophyKey;
	}

	public String id() {
		return id;
	}

	/** Visual model scale. Hitboxes are deliberately smaller than the model (spec section 51). */
	public float scale() {
		return scale;
	}

	public Component displayName() {
		return Component.translatable(nameKey);
	}

	public String trophyKey() {
		return trophyKey;
	}

	public static SupervillainVariant byId(String id) {
		for (SupervillainVariant v : values()) {
			if (v.id.equals(id)) {
				return v;
			}
		}
		return null;
	}

	/** Weighted random pick from the config weights (defaults to ~33% each). */
	public static SupervillainVariant random(RandomSource random) {
		EventConfig.SupervillainRaid cfg = EventConfig.supervillain();
		double c = Math.max(0.0, cfg.weightChimera);
		double a = Math.max(0.0, cfg.weightArsenal);
		double o = Math.max(0.0, cfg.weightOmegaMage);
		double total = c + a + o;
		if (total <= 0.0) {
			return values()[random.nextInt(values().length)];
		}
		double roll = random.nextDouble() * total;
		if (roll < c) {
			return CHIMERA;
		}
		return roll < c + a ? ARSENAL : OMEGA_MAGE;
	}
}
