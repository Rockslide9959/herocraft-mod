package com.projecthero.mod.hero.power.p26;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.entity.MjolnirEntity;
import com.projecthero.mod.item.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The single authority on what Magnetic Manipulation (power 26) can affect. Everything is
 * magnetically-reactive iron-family metal only — <b>not</b> copper or gold — plus lodestone (a strong
 * anchor) and netherite (reactive but very resistant). {@link com.projecthero.mod.item.ModItems#MJOLNIR
 * Mjolnir} is explicitly excluded from every check so Magnetism can never touch Thor's hammer.
 *
 * <p>Data-driven where it can be: the {@code #projecthero:magnetic} block and item tags hold the
 * concrete vanilla list; this class adds the tier/entity/special-case logic on top.
 */
public final class MagneticMaterials {
	public static final TagKey<Block> MAGNETIC_BLOCKS = TagKey.create(Registries.BLOCK, ProjectHeroMod.id("magnetic"));
	public static final TagKey<Item> MAGNETIC_ITEMS = TagKey.create(Registries.ITEM, ProjectHeroMod.id("magnetic"));

	private MagneticMaterials() {
	}

	// ---------------- Mjolnir: hard-excluded from all magnetism ----------------

	public static boolean isMjolnir(ItemStack stack) {
		return !stack.isEmpty() && stack.is(ModItems.MJOLNIR);
	}

	public static boolean isMjolnir(Entity e) {
		return e instanceof MjolnirEntity
				|| (e instanceof ItemEntity ie && ie.getItem().is(ModItems.MJOLNIR));
	}

	// ---------------- items ----------------

	public static boolean isMagnetic(ItemStack stack) {
		if (stack.isEmpty() || isMjolnir(stack)) {
			return false;
		}
		if (stack.is(MAGNETIC_ITEMS)) {
			return true;
		}
		// modded (or vanilla) iron / netherite tools not in the tag
		return stack.getItem() instanceof TieredItem ti
				&& (ti.getTier() == Tiers.IRON || ti.getTier() == Tiers.NETHERITE);
	}

	public static boolean isNetherite(ItemStack stack) {
		if (stack.getItem() instanceof TieredItem ti) {
			return ti.getTier() == Tiers.NETHERITE;
		}
		return stack.is(Items.NETHERITE_INGOT) || stack.is(Items.NETHERITE_SCRAP) || stack.is(Items.NETHERITE_BLOCK)
				|| stack.is(Items.NETHERITE_HELMET) || stack.is(Items.NETHERITE_CHESTPLATE)
				|| stack.is(Items.NETHERITE_LEGGINGS) || stack.is(Items.NETHERITE_BOOTS);
	}

	// ---------------- blocks ----------------

	public static boolean isMagnetic(BlockState state) {
		return state.is(MAGNETIC_BLOCKS);
	}

	public static boolean isNetherite(BlockState state) {
		return state.is(Blocks.NETHERITE_BLOCK);
	}

	public static boolean isLodestone(BlockState state) {
		return state.is(Blocks.LODESTONE);
	}

	/**
	 * A magnetic block that can be physically torn loose and flung without risking data loss (block
	 * entities / inventories) or multi-block jank (doors, beds).
	 */
	public static boolean canDisplace(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!isMagnetic(state) || state.hasBlockEntity()) {
			return false;
		}
		Block b = state.getBlock();
		if (b instanceof DoorBlock || b instanceof BedBlock) {
			return false;
		}
		float hardness = state.getDestroySpeed(level, pos);
		return hardness >= 0.0f; // not bedrock / unbreakable
	}

	// ---------------- entities ----------------

	public static boolean isMagneticEntity(Entity e) {
		if (isMjolnir(e)) {
			return false;
		}
		if (e instanceof AbstractMinecart || e instanceof IronGolem) {
			return true;
		}
		if (e instanceof ItemEntity ie) {
			return isMagnetic(ie.getItem());
		}
		if (e instanceof FallingBlockEntity fbe) {
			return isMagnetic(fbe.getBlockState());
		}
		return e instanceof LivingEntity le && hasMagneticEquipment(le);
	}

	public static boolean isMagneticDrop(Entity e) {
		return e instanceof ItemEntity ie && isMagnetic(ie.getItem());
	}

	public static boolean hasMagneticEquipment(LivingEntity e) {
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (isMagnetic(e.getItemBySlot(slot))) {
				return true;
			}
		}
		return false;
	}

	/** How much magnetic metal an entity is wearing / holding — drives Magnetic Crush and Sense. */
	public static Loadout loadout(LivingEntity e) {
		int pieces = 0;
		boolean netherite = false;
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			ItemStack s = e.getItemBySlot(slot);
			if (isMagnetic(s)) {
				pieces++;
				if (isNetherite(s)) {
					netherite = true;
				}
			}
		}
		return new Loadout(pieces, netherite);
	}

	public record Loadout(int pieces, boolean netherite) {
		public boolean any() {
			return pieces > 0;
		}
	}
}
