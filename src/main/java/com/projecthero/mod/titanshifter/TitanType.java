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
	/** The Attack Titan (id kept as generic_titan so saved shifters keep their Titan). v0.16.7: 15 blocks (config). */
	GENERIC_TITAN("generic_titan", "titan_form", 15.0f, 5.0f, 500.0f, 25.0, 8.0, 0.45, 1.0f),
	/** v0.16.7 (user): the Armoured Titan -- 15 blocks, slow crystal-plated tank. Stats are first-pass; tune in the revamp. */
	ARMORED_TITAN("armored_titan", "titan_armored", 15.0f, 5.0f, 800.0f, 35.0, 14.0, 0.36, 1.1f),
	/** v0.16.7 (user): the Female Titan -- 12 blocks, fast and agile, hardened fists and shins. */
	FEMALE_TITAN("female_titan", "titan_female", 12.0f, 4.0f, 450.0f, 20.0, 6.0, 0.55, 0.95f),
	/** v0.16.7 (user): the Beast Titan -- 14 blocks, long arms, throws boulders. */
	BEAST_TITAN("beast_titan", "titan_beast", 14.0f, 4.67f, 600.0f, 22.0, 6.0, 0.42, 1.0f);

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

	/** v0.16.7: "Attack Titan" etc. ({@code projecthero.titan_shifter.type.<id>}). */
	public String nameKey() {
		return "projecthero.titan_shifter.type." + id;
	}

	/** v0.16.7: the one-paragraph pitch shown on the Titan Serum picker. */
	public String descKey() {
		return nameKey() + ".desc";
	}

	/** v0.16.7: how far a Titan Leap carries (the Armoured Titan is heavy, the Female Titan agile). */
	public double leapScale() {
		return switch (this) {
			case ARMORED_TITAN -> 0.7;
			case FEMALE_TITAN -> 1.25;
			default -> 1.0;
		};
	}

	/** v0.16.7: the R combo's recharge scale (Female jabs fast, Armoured swings slow). */
	public double comboCooldownScale() {
		return switch (this) {
			case ARMORED_TITAN -> 1.5;
			case FEMALE_TITAN -> 0.6;
			default -> 1.0;
		};
	}

	/** v0.16.7: extra fist reach of the R combo (the Beast Titan's long arms). */
	public double extraReach() {
		return this == BEAST_TITAN ? 3.0 : 0.0;
	}

	/** v0.16.7: the Armoured Titan's crystal plates -- built-in damage reduction (0..1), always on. */
	public double plateReduction() {
		return this == ARMORED_TITAN ? 0.35 : 0.0;
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

	/** v0.12.36: the sliced model and atlas of the dissolving corpse. */
	public ResourceLocation corpseGeo() {
		return ProjectHeroMod.id("geo/" + modelName + "_corpse.geo.json");
	}

	public ResourceLocation corpseTexture() {
		return ProjectHeroMod.id("textures/entity/" + modelName + "_corpse.png");
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
