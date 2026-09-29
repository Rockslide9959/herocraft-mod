package com.projecthero.mod.moonknight.item;

import com.projecthero.mod.moonknight.MoonKnight;
import com.projecthero.mod.moonknight.MoonKnightAnim;
import com.projecthero.mod.moonknight.MoonKnightConfig;
import com.projecthero.mod.moonknight.data.MoonKnightAction;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;

/**
 * Z: the Truncheon of Khonshu -- a white-silver baton with a crescent head, summoned into the hand by a transformed
 * Moon Knight and gone again the moment it is stowed, dropped, stored or the suit comes off. HOLD Z extends it into
 * the staff (the {@code projecthero:staff} model predicate, driven by the synced {@code FLAG_STAFF}).
 *
 * <p>It is never crafted and cannot survive outside its owner's hands: any copy that is ticked in an inventory whose
 * owner is not a transformed Moon Knight with the truncheon out deletes itself; a dropped copy is discarded on load
 * ({@code MoonKnightCombat.initializeEvents}); container slots are swept while a menu is open
 * ({@code MoonKnightTruncheon.tick}).
 */
public class MoonKnightTruncheonItem extends Item {
	public MoonKnightTruncheonItem() {
		super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).attributes(attributes()));
	}

	private static ItemAttributeModifiers attributes() {
		return ItemAttributeModifiers.builder()
				.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID,
						MoonKnightConfig.TRUNCHEON_DAMAGE - 1.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
				.add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID,
						MoonKnightConfig.TRUNCHEON_ATTACK_SPEED, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
				.build();
	}

	/** May this player hold a truncheon right now? */
	public static boolean allowed(Player player) {
		return MoonKnight.isTransformed(player) && MoonKnightAnim.flag(player, MoonKnightAction.FLAG_TRUNCHEON);
	}

	@Override
	public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
		if (level.isClientSide) {
			return;
		}
		if (!(entity instanceof Player player) || !allowed(player)) {
			stack.setCount(0);
		}
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return false;
	}
}
