package com.projecthero.mod.wolverine.item;

import com.projecthero.mod.armor.SuperheroArmorItem;
import com.projecthero.mod.wolverine.WolverineSuitMend;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A piece of the craftable Wolverine Suit (v0.12.14) -- the yellow-and-blue costume. Like the
 * Spider-Man Suit it is purely visual (worn by anyone, grants nothing) and renders through the shared
 * GeckoLib armour path using {@code geo/wolverine.geo.json} over {@code textures/armor/wolverine.png}.
 */
public class WolverineArmorItem extends SuperheroArmorItem {
	public WolverineArmorItem(Holder<ArmorMaterial> material, ArmorItem.Type type, Properties properties) {
		super(material, type, properties);
	}

	@Override
	public String armorSetId() {
		return "wolverine";
	}

	/**
	 * v0.16.1: the suit knits itself back together -- 1 durability a second, worn or carried, once whoever has it has been
	 * out of combat for 30 s (see {@link WolverineSuitMend}). Was a flat 1 point every 5 s (v0.15.21).
	 */
	@Override
	public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
		if (!level.isClientSide() && stack.isDamaged() && level.getGameTime() % WolverineSuitMend.REPAIR_INTERVAL_TICKS == 0
				&& entity instanceof LivingEntity living && WolverineSuitMend.outOfCombat(living)) {
			WolverineSuitMend.repairTick(stack);
		}
	}
}
