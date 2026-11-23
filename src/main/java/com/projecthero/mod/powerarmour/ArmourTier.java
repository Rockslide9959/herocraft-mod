package com.projecthero.mod.powerarmour;

import java.util.Locale;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * v0.17.5 Power Armour: the seven frame materials, weakest to strongest (Gold sits beside Iron: weaker plating, thriftier
 * with the core). Every number here is documented in {@code docs/POWER_ARMOUR_REFERENCE.md}.
 *
 * <p>{@code slots} is how many upgrade slots the frame has; which body part each slot belongs to is {@link #SLOT_ORDER} (the
 * first {@code slots} entries), so a smithing upgrade only ever adds slots at the end and every module keeps its place.
 */
public enum ArmourTier {
	//            slots armour tough  kb    speed  integ  dmg   drain cdMul  primary   secondary glow      frame tint
	// v0.17.7 (Street tier): armour 12 -> 20 and toughness 0 -> 8 (Celestial = full diamond), damage x1.00 -> x1.25; kit hits are
	// capped at the Street ranges (PowerArmourAbilities.melee / ability / ultimate). Higher tiers still win on integrity, slots and speed.
	IRON(1,        3,  12f,   0f,   0.1f, -0.10f, 300f, 1.00f, 1.00f, 1.15f, 0xB8BCC2, 0x5A5F66, 0xFFB347, 0xFFFFFF),
	GOLD(1,        3,  10f,   0f,   0.0f, -0.05f, 220f, 0.95f, 0.75f, 1.10f, 0xF2C641, 0x9C6A1A, 0xFFF2A0, 0xFFF0D8),
	HERO_ALLOY(2,  4,  14f,   1f,   0.1f,  0.00f, 450f, 1.05f, 1.00f, 1.00f, 0x46505F, 0x3AA0FF, 0x96D6FF, 0xE6EEFF),
	DIAMOND(3,     5,  16f,   2f,   0.15f, 0.00f, 650f, 1.10f, 1.00f, 0.95f, 0x6FE6DC, 0xE8FFFF, 0x9FFFFF, 0xEAFBFF),
	NETHERITE(4,   6,  18f,   4f,   1.0f, -0.05f, 900f, 1.15f, 1.00f, 0.95f, 0x4A4246, 0x8A5A3A, 0xFF5A1F, 0xB8AEB2),
	VIBRANIUM(5,   7,  19f,   6f,   0.3f,  0.05f, 1200f, 1.20f, 1.00f, 0.90f, 0x3A2A50, 0xC8CDDC, 0xB070FF, 0xD8CCEC),
	CELESTIAL(6,   8,  20f,   8f,   0.4f,  0.10f, 1600f, 1.25f, 1.00f, 0.80f, 0xEEEAF6, 0xF2C03A, 0x96E6FF, 0xF4F2FF);

	/** The body part of each upgrade slot, in unlock order (a tier with N slots has the first N). */
	public static final BodyPart[] SLOT_ORDER = { BodyPart.HELMET, BodyPart.ARMS, BodyPart.BACK, BodyPart.LEGS, BodyPart.CHEST,
			BodyPart.ARMS, BodyPart.HELMET, BodyPart.CHEST };

	/** The design's tier number (Gold is "1+"). */
	public final int rank;
	public final int slots;
	/** Armour points and toughness the sealed suit gives (it replaces worn armour, which is stored while you are inside). */
	public final float armour, toughness;
	/** Knockback resistance (0..1). Netherite is immune. */
	public final float knockback;
	/** Walk speed change while sealed (multiplied onto the total). */
	public final float speed;
	/** The suit's own durability. */
	public final float integrity;
	/** Multiplier on every kit move's damage. */
	public final float damage;
	/** Multiplier on the core's drain (Gold drains 25% slower). */
	public final float drain;
	/** Multiplier on the kit's cooldowns (servo speed: Celestial is fastest). */
	public final float cooldown;
	/** Default paint: plates, trim, glow (RGB). */
	public final int primary, secondary, glow;
	/** Tint of the under-frame layer. */
	public final int frameTint;

	ArmourTier(int rank, int slots, float armour, float toughness, float knockback, float speed, float integrity, float damage,
			float drain, float cooldown, int primary, int secondary, int glow, int frameTint) {
		this.rank = rank;
		this.slots = slots;
		this.armour = armour;
		this.toughness = toughness;
		this.knockback = knockback;
		this.speed = speed;
		this.integrity = integrity;
		this.damage = damage;
		this.drain = drain;
		this.cooldown = cooldown;
		this.primary = primary;
		this.secondary = secondary;
		this.glow = glow;
		this.frameTint = frameTint;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public String translationKey() {
		return "projecthero.power_armour.tier." + id();
	}

	public BodyPart slotPart(int index) {
		return SLOT_ORDER[index];
	}

	/** How many slots this tier has for {@code part}. */
	public int slotsFor(BodyPart part) {
		int n = 0;
		for (int i = 0; i < slots; i++) {
			if (SLOT_ORDER[i] == part) {
				n++;
			}
		}
		return n;
	}

	/** Netherite and up shrug off fire and lava. */
	public boolean fireproof() {
		return ordinal() >= NETHERITE.ordinal();
	}

	/** Vibranium and up: falls and explosions charge the core a little. */
	public boolean absorbsImpacts() {
		return ordinal() >= VIBRANIUM.ordinal();
	}

	/** The next tier a smithing table upgrades this one to (null for Celestial). */
	public ArmourTier next() {
		return this == CELESTIAL ? null : values()[ordinal() + 1];
	}

	/** The raw material that repairs this frame, and how much of the integrity one item restores. */
	public Item repairItem() {
		return switch (this) {
			case IRON -> Items.IRON_INGOT;
			case GOLD -> Items.GOLD_INGOT;
			case HERO_ALLOY -> com.projecthero.mod.loot.LootItems.HERO_ALLOY_INGOT;
			case DIAMOND -> Items.DIAMOND;
			case NETHERITE -> Items.NETHERITE_SCRAP;
			case VIBRANIUM -> com.projecthero.mod.loot.LootItems.VIBRANIUM_SHARD;
			case CELESTIAL -> com.projecthero.mod.loot.LootItems.CELESTIAL_CORE;
		};
	}

	public float repairFraction() {
		return switch (this) {
			case IRON, GOLD -> 0.10f;
			case HERO_ALLOY -> 0.125f;
			case DIAMOND, NETHERITE, VIBRANIUM -> 0.15f;
			case CELESTIAL -> 0.5f;
		};
	}

	public static ArmourTier byId(String id) {
		for (ArmourTier t : values()) {
			if (t.id().equals(id)) {
				return t;
			}
		}
		return IRON;
	}
}
