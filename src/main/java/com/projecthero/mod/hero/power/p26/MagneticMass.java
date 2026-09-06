package com.projecthero.mod.hero.power.p26;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One weight class for every magnetic object. Mass drives launch speed, magnetic acceleration, throw
 * damage, knockback and grip-follow speed everywhere in power 26 — so no ability carries its own
 * scattered weight numbers.
 *
 * <p>Heavy objects fly slower, hit harder and knock back further; light objects fly fast and hit
 * soft. Netherite is handled by the abilities themselves (extra resistance), not here.
 */
public enum MagneticMass {
	//       launch  accel  dmg   knockback  gripFollow
	LIGHT   (1.35,   0.30,   6.0f, 0.35,     0.55),
	MEDIUM  (1.00,   0.20,   9.0f, 0.70,     0.38),
	HEAVY   (0.66,   0.12,  18.0f, 1.35,     0.22),
	EXTREME (0.42,   0.06,  28.0f, 1.95,     0.12);

	public final double launchSpeed;
	public final double accel;
	public final float damage;
	public final double knockback;
	public final double gripFollow;

	MagneticMass(double launchSpeed, double accel, float damage, double knockback, double gripFollow) {
		this.launchSpeed = launchSpeed;
		this.accel = accel;
		this.damage = damage;
		this.knockback = knockback;
		this.gripFollow = gripFollow;
	}

	public static MagneticMass of(ItemStack stack) {
		Item item = stack.getItem();
		if (item == Items.NETHERITE_BLOCK) {
			return EXTREME;
		}
		if (item == Items.IRON_NUGGET || item == Items.CHAIN || item == Items.IRON_INGOT || item == Items.RAW_IRON
				|| item == Items.NETHERITE_INGOT || item == Items.NETHERITE_SCRAP || item == Items.RAIL
				|| item == Items.DETECTOR_RAIL || item == Items.ACTIVATOR_RAIL || item == Items.IRON_NUGGET) {
			return LIGHT;
		}
		if (item == Items.IRON_BLOCK || item == Items.RAW_IRON_BLOCK
				|| item == Items.ANVIL || item == Items.CHIPPED_ANVIL || item == Items.DAMAGED_ANVIL
				|| item == Items.MINECART || item == Items.CHEST_MINECART || item == Items.FURNACE_MINECART
				|| item == Items.HOPPER_MINECART || item == Items.TNT_MINECART || item == Items.LODESTONE) {
			return HEAVY;
		}
		if (MagneticMaterials.isNetherite(stack)) {
			return HEAVY; // netherite tools / armour: unusually dense
		}
		return MEDIUM; // swords, tools, armour, bars, doors, buckets, hoppers, cauldrons, lanterns...
	}

	public static MagneticMass of(BlockState state) {
		if (MagneticMaterials.isNetherite(state)) {
			return EXTREME;
		}
		Block b = state.getBlock();
		if (b == Blocks.IRON_BLOCK || b == Blocks.RAW_IRON_BLOCK || b == Blocks.IRON_ORE
				|| b == Blocks.DEEPSLATE_IRON_ORE || b == Blocks.LODESTONE
				|| b == Blocks.ANVIL || b == Blocks.CHIPPED_ANVIL || b == Blocks.DAMAGED_ANVIL) {
			return HEAVY;
		}
		if (b == Blocks.CHAIN || b == Blocks.RAIL || b == Blocks.DETECTOR_RAIL || b == Blocks.ACTIVATOR_RAIL
				|| b == Blocks.LANTERN || b == Blocks.SOUL_LANTERN) {
			return LIGHT;
		}
		return MEDIUM; // bars, iron door/trapdoor, hopper, cauldron, heavy pressure plate
	}

	public static MagneticMass of(Entity e) {
		if (e instanceof IronGolem || e instanceof AbstractMinecart) {
			return HEAVY;
		}
		if (e instanceof FallingBlockEntity fbe) {
			return of(fbe.getBlockState());
		}
		if (e instanceof ItemEntity ie) {
			return of(ie.getItem());
		}
		return MEDIUM;
	}
}
