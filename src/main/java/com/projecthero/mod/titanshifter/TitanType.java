package com.projecthero.mod.titanshifter;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.resources.ResourceLocation;

/**
 * A kind of Titan a shifter can become. v0.12.31 ships only {@link #GENERIC_TITAN}; adding another is a
 * matter of adding a constant here (its own geo/texture/animation files, size, stats) -- the entity,
 * renderer, ability manager and state all read the selected type. Per-type ability sets are the natural
 * next step: {@code TitanAbilities} dispatches on the type where a kit differs.
 */
public enum TitanType {
	GENERIC_TITAN("generic_titan", "titan_form", 11.0f, 3.67f, 500.0f, 25.0, 8.0, 0.45, 1.0f);

	public final String id;
	private final String modelName;
	public final float height;
	public final float width;
	public final float maxHealth;
	public final double armor;
	public final double toughness;
	public final double speed;
	/** Multiplier on all outgoing Titan damage -- lets a future type hit harder/softer than the baseline. */
	public final float damageScale;

	TitanType(String id, String modelName, float height, float width, float maxHealth, double armor,
			double toughness, double speed, float damageScale) {
		this.id = id;
		this.modelName = modelName;
		this.height = height;
		this.width = width;
		this.maxHealth = maxHealth;
		this.armor = armor;
		this.toughness = toughness;
		this.speed = speed;
		this.damageScale = damageScale;
	}

	/** The baseline reads the live config; a future type would return its own constants. */
	public float dimensionHeight() {
		return this == GENERIC_TITAN ? (float) TitanShifterConfig.stats().heightBlocks : height;
	}

	public float dimensionWidth() {
		return this == GENERIC_TITAN ? (float) TitanShifterConfig.stats().widthBlocks : width;
	}

	public double health() {
		return this == GENERIC_TITAN ? TitanShifterConfig.stats().maxHealth : maxHealth;
	}

	public double armorValue() {
		return this == GENERIC_TITAN ? TitanShifterConfig.stats().armor : armor;
	}

	public double toughnessValue() {
		return this == GENERIC_TITAN ? TitanShifterConfig.stats().armorToughness : toughness;
	}

	public double speedValue() {
		return this == GENERIC_TITAN ? TitanShifterConfig.stats().movementSpeed : speed;
	}

	public ResourceLocation geo() {
		return ProjectHeroMod.id("geo/" + modelName + ".geo.json");
	}

	public ResourceLocation texture() {
		return ProjectHeroMod.id("textures/entity/" + modelName + ".png");
	}

	public ResourceLocation hardenedTexture() {
		return ProjectHeroMod.id("textures/entity/" + modelName + "_hardened.png");
	}

	public ResourceLocation animation() {
		return ProjectHeroMod.id("animations/" + modelName + ".animation.json");
	}

	public String animPrefix() {
		return "animation." + modelName + ".";
	}

	/**
	 * The model is authored at this height in blocks; the renderer scales by (dimension height / this). v0.12.32:
	 * the body is a regular player model (32 px = 2 blocks), so an 11-block Titan is drawn at 5.5x.
	 */
	public float modelHeight() {
		return 2.0f;
	}

	public static TitanType byId(String id) {
		for (TitanType t : values()) {
			if (t.id.equals(id)) {
				return t;
			}
		}
		return GENERIC_TITAN;
	}

	public static TitanType byOrdinal(int i) {
		TitanType[] all = values();
		return all[Math.floorMod(i, all.length)];
	}
}
