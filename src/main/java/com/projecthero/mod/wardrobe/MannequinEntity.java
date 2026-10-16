package com.projecthero.mod.wardrobe;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
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

	/**
	 * v0.16.5 (user): Shift+right-click puts the whole suit you wear on the Mannequin -- yours and its four armour pieces
	 * change places (wear nothing and you take its suit). A cursed piece of Binding stays on you outside creative.
	 */
	@Override
	public InteractionResult interactAt(Player player, Vec3 at, InteractionHand hand) {
		if (!player.isSecondaryUseActive() || hand != InteractionHand.MAIN_HAND || player.isSpectator() || isMarker()) {
			return super.interactAt(player, at, hand);
		}
		if (level().isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		return swapSuit(player) > 0 ? InteractionResult.SUCCESS : InteractionResult.PASS;
	}

	/** The worn suit and the Mannequin's change places, piece by piece. Returns how many slots changed. */
	public int swapSuit(Player player) {
		int moved = 0;
		for (EquipmentSlot slot : new EquipmentSlot[] { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
			ItemStack worn = player.getItemBySlot(slot);
			ItemStack on = getItemBySlot(slot);
			if (worn.isEmpty() && on.isEmpty()) {
				continue;
			}
			if (!worn.isEmpty() && !player.getAbilities().instabuild
					&& EnchantmentHelper.has(worn, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)) {
				continue;
			}
			setItemSlot(slot, worn);
			player.setItemSlot(slot, on);
			moved++;
		}
		if (moved > 0) {
			level().playSound(null, getX(), getY(), getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.NEUTRAL, 1.0f, 0.9f);
		}
		return moved;
	}

	@Override
	public ItemStack getPickResult() {
		return new ItemStack(WardrobeMod.MANNEQUIN_ITEM);
	}
}
