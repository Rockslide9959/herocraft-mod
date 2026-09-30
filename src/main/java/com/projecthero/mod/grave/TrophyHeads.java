package com.projecthero.mod.grave;

import com.projecthero.mod.event.boss.BossPowers;
import com.projecthero.mod.grave.item.GraveItems;
import com.projecthero.mod.grave.item.TrophyRecord;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.PowerCategory;
import com.projecthero.mod.hero.Powers;

import com.mojang.serialization.Codec;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;

/**
 * v0.14.4: the shared rules for the two boss trophy heads -- the <b>Grave Champion Head</b> (wave-12 boss, always
 * dropped) and the <b>Empowered Zombie Head</b> (any Powered Zombie Boss, sometimes dropped). Both are real heads now:
 * wearable in the head slot, placeable on the floor or a wall, and they remember the power and the kill.
 *
 * <h2>The perk while worn</h2>
 * Vanilla's mob heads halve how far their own mob notices you. These follow the same rule, scaled to the trophy: the
 * Empowered Zombie Head halves how far <em>zombies</em> notice you, the Grave Champion Head halves it for <em>every
 * undead</em>. It only shortens the detection range ({@code getVisibilityPercent}); anything you hit, or anything
 * already on you, still fights.
 */
public final class TrophyHeads {
	/** How much of its normal detection range a matching undead uses against someone wearing the head. */
	public static final double DETECTION_FACTOR = 0.5;

	/** Glow colour of a head whose power is unknown (a chest-loot head, or a creative-menu one). */
	public static final int CHAMPION_DEFAULT_GLOW = 0x5FF2FF;
	public static final int ZOMBIE_DEFAULT_GLOW = 0x9CFF5A;

	private TrophyHeads() {
	}

	/** Which trophy a head block is. */
	public enum Kind implements StringRepresentable {
		CHAMPION("champion"),
		ZOMBIE("zombie");

		public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);
		private final String name;

		Kind(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}

	// ---------------------------------------------------------------- perk

	/** The multiplier {@code wearer}'s head slot applies to {@code looker}'s detection range (1 = no effect). */
	public static double detectionFactor(LivingEntity wearer, Entity looker) {
		if (looker == null || GraveItems.FINAL_BOSS_TROPHY == null) {
			return 1.0;
		}
		ItemStack head = wearer.getItemBySlot(EquipmentSlot.HEAD);
		if (head.is(GraveItems.FINAL_BOSS_TROPHY) && isUndead(looker)) {
			return DETECTION_FACTOR;
		}
		if (head.is(GraveItems.BOSS_TROPHY) && isZombie(looker)) {
			return DETECTION_FACTOR;
		}
		return 1.0;
	}

	static boolean isZombie(Entity e) {
		return e instanceof Zombie || e.getType().is(EntityTypeTags.ZOMBIES);
	}

	static boolean isUndead(Entity e) {
		return isZombie(e) || e instanceof AbstractSkeleton || e.getType().is(EntityTypeTags.UNDEAD);
	}

	// ---------------------------------------------------------------- look

	/**
	 * The RGB its eyes, sigil and crown gem glow in: one colour per power family, so an Elemental boss's head burns
	 * orange-red and a Mental one's violet. Heads with no power use the kind's default.
	 */
	public static int glowColor(String powerKey, boolean champion) {
		Power power = powerKey == null || powerKey.isEmpty() ? null : Powers.byKey(powerKey);
		if (power == null) {
			return champion ? CHAMPION_DEFAULT_GLOW : ZOMBIE_DEFAULT_GLOW;
		}
		return categoryColor(power.category());
	}

	public static int categoryColor(PowerCategory category) {
		return switch (category) {
			case PHYSICAL -> 0xFF8A3D;
			case ELEMENTAL -> 0xFF4E2A;
			case MENTAL -> 0xC46BFF;
			case MOLECULAR -> 0x4DFFB8;
			case MOVEMENT -> 0x7FE3FF;
			case ENERGY -> 0xFFE14D;
			case KINETIC -> 0xFF6FB5;
			case FORCE -> 0x6F8BFF;
			case NATURE -> 0x7CFF4F;
			case LIGHT -> 0xFFF4C2;
		};
	}

	// ---------------------------------------------------------------- text

	/** "Slain by Steve on day 12" -- or null if the head carries no record. */
	public static Component recordLine(TrophyRecord record) {
		if (record == null) {
			return null;
		}
		return Component.translatable("item.projecthero.boss_trophy.record", record.slayer(), record.day())
				.withStyle(ChatFormatting.GRAY);
	}

	/** The one-line plaque shown when someone right-clicks a placed head. */
	public static Component plaque(Kind kind, String powerKey, TrophyRecord record) {
		MutableComponent line = Component.translatable(kind == Kind.CHAMPION
				? "block.projecthero.grave_champion_head" : "block.projecthero.empowered_zombie_head")
				.withStyle(kind == Kind.CHAMPION ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.DARK_GREEN);
		if (powerKey != null && !powerKey.isEmpty()) {
			line.append(Component.literal(" — ").withStyle(ChatFormatting.DARK_GRAY))
					.append(BossPowers.displayName(powerKey).copy().withStyle(ChatFormatting.GOLD));
		}
		Component rec = recordLine(record);
		if (rec != null) {
			line.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY)).append(rec);
		}
		return line;
	}
}
