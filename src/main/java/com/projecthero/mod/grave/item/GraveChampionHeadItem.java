package com.projecthero.mod.grave.item;

import java.util.List;

import com.projecthero.mod.event.boss.BossPowers;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * The Grave Champion Head -- the wave-12 boss trophy, now wearable and placeable exactly like a
 * vanilla mob head (v0.9.22).
 *
 * <ul>
 *   <li><b>Place it</b>: it is a {@link BlockItem} for {@link com.projecthero.mod.grave.GraveChampionHeadBlock}.</li>
 *   <li><b>Wear it</b>: {@link Equipable} routes it to the {@link EquipmentSlot#HEAD} slot, so
 *       shift-clicking it, dragging it onto the head slot, or a dispenser all equip it, and mobs can
 *       wear it -- the same mechanism {@code AbstractSkullBlock} uses. Right-clicking the air also
 *       swaps it onto the head for convenience.</li>
 * </ul>
 *
 * <p>Keeps {@link BossTrophyItem}'s "named for the boss's power" behaviour ({@code "Geokinetic Grave
 * Champion Head"} etc.) while the item is held, plus the enchant-glint on this final-boss variant.
 */
public class GraveChampionHeadItem extends BlockItem implements Equipable {
	public GraveChampionHeadItem(Block block, Properties properties) {
		super(block, properties);
	}

	public static ItemStack of(net.minecraft.world.item.Item item, String powerKey) {
		ItemStack stack = new ItemStack(item);
		stack.set(GraveComponents.POWER_KEY, powerKey);
		return stack;
	}

	@Override
	public EquipmentSlot getEquipmentSlot() {
		return EquipmentSlot.HEAD;
	}

	@Override
	public Holder<SoundEvent> getEquipSound() {
		return SoundEvents.ARMOR_EQUIP_GENERIC;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		return this.swapWithEquipmentSlot(this, level, player, hand);
	}

	@Override
	public Component getName(ItemStack stack) {
		String key = stack.get(GraveComponents.POWER_KEY);
		if (key == null || key.isEmpty()) {
			return super.getName(stack);
		}
		return Component.translatable("item.projecthero.final_boss_trophy.named", BossPowers.displayName(key));
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(Component.translatable("item.projecthero.boss_trophy.hint")
				.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return true;
	}
}
