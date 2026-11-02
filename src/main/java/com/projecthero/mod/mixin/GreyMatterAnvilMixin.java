package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.omnitrix.alien.greymatter.GreyMatterKit;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

/**
 * v0.17.2 Grey Matter revamp, passive Galvan Intellect: an anvil REPAIR (the left item comes out less damaged) costs him 30%
 * less XP ({@link GreyMatterKit#anvilCost}). Runs on both sides alike (his client works the cost out too, from synced state).
 */
@Mixin(AnvilMenu.class)
public abstract class GreyMatterAnvilMixin extends ItemCombinerMenu {
	@Shadow
	@Final
	private DataSlot cost;

	private GreyMatterAnvilMixin(MenuType<?> type, int id, Inventory inv, ContainerLevelAccess access) {
		super(type, id, inv, access);
	}

	@Inject(method = "createResult", at = @At("TAIL"))
	private void projecthero$galvanRepairDiscount(CallbackInfo ci) {
		if (!GreyMatterKit.galvan(this.player)) {
			return;
		}
		ItemStack in = this.inputSlots.getItem(0);
		ItemStack out = this.resultSlots.getItem(0);
		if (!in.isEmpty() && !out.isEmpty() && in.isDamageableItem() && in.getDamageValue() > 0 && out.getDamageValue() < in.getDamageValue()) {
			this.cost.set(GreyMatterKit.anvilCost(this.cost.get()));
		}
	}
}
