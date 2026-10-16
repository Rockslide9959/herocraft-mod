package com.projecthero.mod.wardrobe;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * v0.16.5 (user): the Mannequin -- an armour stand shaped like a blank player (a wooden tailor's dummy with real arms and
 * legs, drawn by {@code MannequinRenderer} on the player model). Everything else is the vanilla armour stand it extends:
 * right-click a piece of armour or a held item onto it or off it (by where you click, like an armour stand), punch it
 * twice to break it, and it drops the Mannequin plus whatever it wore ({@code ArmorStandMannequinMixin} swaps the dropped
 * armour stand for a Mannequin). It always shows its arms. The Wardrobe also draws its figures with one of these.
 */
public class MannequinEntity extends ArmorStand {
	public MannequinEntity(EntityType<? extends ArmorStand> type, Level level) {
		super(type, level);
		setShowArms(true);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		setShowArms(true); // a mannequin always has arms, whatever the tag says
	}

	@Override
	public ItemStack getPickResult() {
		return new ItemStack(WardrobeMod.MANNEQUIN_ITEM);
	}
}
