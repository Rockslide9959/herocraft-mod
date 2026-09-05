package com.herocraft.mod.hero.item;

import java.util.List;

import com.herocraft.mod.hero.Power;
import com.herocraft.mod.hero.Powers;
import com.herocraft.mod.hero.data.ResearchStage;
import com.herocraft.mod.hero.mutation.MutationManager;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * A damaged research note found in structures. Right-click to "study" it: advances the reader's
 * research on the documented power to at least {@link ResearchStage#RESEARCH_FOUND}, unlocking the
 * partial guide entry and the first advancement in the chain. Consumed on use.
 */
public final class ResearchNoteItem extends Item {
	public ResearchNoteItem(Properties properties) {
		super(properties);
	}

	public static ItemStack forPower(Power power, int count) {
		ItemStack stack = new ItemStack(com.herocraft.mod.hero.item.HeroPackItems.RESEARCH_NOTE, count);
		stack.set(HeroPackComponents.RESEARCH_POWER, power.key());
		return stack;
	}

	private static Power power(ItemStack stack) {
		String key = stack.get(HeroPackComponents.RESEARCH_POWER);
		return key == null ? null : Powers.byKey(key);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		Power power = power(stack); // null = a "blank" note that reveals a random undiscovered power
		if (level.isClientSide()) {
			return InteractionResultHolder.success(stack);
		}
		if (player instanceof ServerPlayer serverPlayer) {
			boolean learned = power != null
					? MutationManager.studyResearchNote(serverPlayer, power)
					: MutationManager.studyRandomResearch(serverPlayer);
			if (learned && !serverPlayer.getAbilities().instabuild) {
				stack.shrink(1);
			}
		}
		return InteractionResultHolder.sidedSuccess(stack, false);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		Power power = power(stack);
		if (power != null) {
			tooltip.add(Component.translatable("item.herocraft.research_note.about",
					Component.translatable(power.nameKey())).withStyle(ChatFormatting.GRAY));
		}
		tooltip.add(Component.translatable("item.herocraft.research_note.hint").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}
}
