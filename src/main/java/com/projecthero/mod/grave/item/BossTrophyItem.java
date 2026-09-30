package com.projecthero.mod.grave.item;

import java.util.List;

import com.projecthero.mod.event.boss.BossPowers;
import com.projecthero.mod.grave.TrophyHeads;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * A Powered Zombie Boss's head, named for the power it had -- the <b>Empowered Zombie Head</b> ("Geokinetic Zombie
 * Head", ...) from any boss, and the <b>Grave Champion Head</b> ({@link Rarity#EPIC}, enchant glint) that the wave-12
 * boss always drops.
 *
 * <p>v0.14.4: both are real heads (this replaces v0.9.22's separate {@code GraveChampionHeadItem}):
 * <ul>
 *   <li><b>Wear it</b> -- {@link Equipable} with {@link EquipmentSlot#HEAD}: shift-click, drag onto the head slot,
 *       right-click the air, a dispenser, an armour stand or a mob all put it on, and it is drawn <em>as</em> the
 *       head (a 9.5-pixel skull on the neck, like a vanilla mob head) rather than floating above it.</li>
 *   <li><b>Place it</b> -- a {@link StandingAndWallBlockItem}: on the floor it stands facing you, on a wall it is
 *       mounted like a hunting trophy. The block keeps the power and the kill record.</li>
 *   <li><b>The record</b> -- {@link GraveComponents#TROPHY_RECORD}: who took it and on which day, in the tooltip.</li>
 *   <li><b>The perk</b> -- while worn, zombies (Empowered Zombie Head) or all undead (Grave Champion Head) notice you
 *       from half as far away; see {@link TrophyHeads}.</li>
 * </ul>
 * One item plus a {@link GraveComponents#POWER_KEY} component rather than one item per power, so the boss roster can
 * grow without new registrations.
 */
public class BossTrophyItem extends StandingAndWallBlockItem implements Equipable {
	private final boolean finalBoss;

	public BossTrophyItem(Block standing, Block wall, Properties properties, boolean finalBoss) {
		super(standing, wall, properties, Direction.DOWN);
		this.finalBoss = finalBoss;
	}

	public static ItemStack of(Item item, String powerKey) {
		ItemStack stack = new ItemStack(item);
		stack.set(GraveComponents.POWER_KEY, powerKey);
		return stack;
	}

	public boolean isFinalBoss() {
		return finalBoss;
	}

	@Override
	public EquipmentSlot getEquipmentSlot() {
		return EquipmentSlot.HEAD;
	}

	@Override
	public Holder<SoundEvent> getEquipSound() {
		return SoundEvents.ARMOR_EQUIP_GENERIC;
	}

	/** Right-clicking the air puts it on (right-clicking a block still places it). */
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
		return Component.translatable(finalBoss
				? "item.projecthero.final_boss_trophy.named"
				: "item.projecthero.boss_trophy.named", BossPowers.displayName(key));
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		Component record = TrophyHeads.recordLine(stack.get(GraveComponents.TROPHY_RECORD));
		if (record != null) {
			tooltip.add(record);
		}
		tooltip.add(Component.translatable(finalBoss
				? "item.projecthero.final_boss_trophy.perk" : "item.projecthero.boss_trophy.perk")
				.withStyle(ChatFormatting.BLUE));
		tooltip.add(Component.translatable("item.projecthero.boss_trophy.hint")
				.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return finalBoss;
	}
}
