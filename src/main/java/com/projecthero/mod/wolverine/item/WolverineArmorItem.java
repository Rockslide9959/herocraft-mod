package com.projecthero.mod.wolverine.item;

import com.projecthero.mod.armor.SuperheroArmorItem;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;

/**
 * A piece of the craftable Wolverine Suit (v0.12.14) -- the yellow-and-blue costume. Like the
 * Spider-Man Suit it is purely visual (worn by anyone, grants nothing) and renders through the shared
 * GeckoLib armour path using {@code geo/wolverine.geo.json} over {@code textures/armor/wolverine.png}.
 */
public class WolverineArmorItem extends SuperheroArmorItem {
	public WolverineArmorItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
		super(material, type, properties);
	}

	@Override
	public String armorSetId() {
		return "wolverine";
	}

	/** v0.15.21: the suit knits itself back together -- one durability point every 5 s, worn or carried. */
	public static final int REPAIR_INTERVAL_TICKS = 5 * 20;

	@Override
	public void inventoryTick(net.minecraft.world.item.ItemStack stack, net.minecraft.world.level.Level level,
			net.minecraft.world.entity.Entity entity, int slot, boolean selected) {
		if (!level.isClientSide() && stack.isDamaged() && level.getGameTime() % REPAIR_INTERVAL_TICKS == 0) {
			repairTick(stack);
		}
	}

	/** One point of self-repair (a gametest hook as well as the 5 s tick). */
	public static void repairTick(net.minecraft.world.item.ItemStack stack) {
		if (stack.isDamaged()) {
			stack.setDamageValue(stack.getDamageValue() - 1);
		}
	}
}
