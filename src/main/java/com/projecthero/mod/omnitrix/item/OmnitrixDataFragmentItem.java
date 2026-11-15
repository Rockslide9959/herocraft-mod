package com.projecthero.mod.omnitrix.item;

import java.util.List;

import com.projecthero.mod.omnitrix.OmnitrixCodes;

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
 * v0.17.5 (user: "add a way for the player to slowly discover random numbers for the master control"): the Omnitrix Data
 * Fragment -- a sliver of Galvan data crystal found in Hero Vaults (more often the higher the tier). Used while wearing an
 * Omnitrix it shows one Master Control digit (and its place), or now and then a whole hidden-alien code
 * ({@link OmnitrixCodes#useFragment}). Without a watch, or when it holds nothing new, nothing happens and it is kept.
 */
public final class OmnitrixDataFragmentItem extends Item {
	public OmnitrixDataFragmentItem(Properties p) {
		super(p);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!(player instanceof ServerPlayer sp)) {
			return InteractionResultHolder.success(stack);
		}
		OmnitrixCodes.FragmentResult r = OmnitrixCodes.useFragment(sp);
		sp.getCooldowns().addCooldown(this, 20);
		if (r == OmnitrixCodes.FragmentResult.DIGIT || r == OmnitrixCodes.FragmentResult.ALIEN) {
			if (!sp.getAbilities().instabuild) {
				stack.shrink(1);
			}
			sp.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, sp.getX(), sp.getY() + 1.2, sp.getZ(), 8,
					0.3, 0.3, 0.3, 0.0);
			return InteractionResultHolder.consume(stack);
		}
		return InteractionResultHolder.fail(stack);
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return true;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.omnitrix_data_fragment.hint").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.projecthero.omnitrix_data_fragment.use").withStyle(ChatFormatting.GREEN));
	}
}
