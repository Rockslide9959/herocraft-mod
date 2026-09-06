package com.projecthero.mod.hero.guide;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;

/**
 * The {@code HeroPack Guide} book. Right-click opens the custom chapter GUI. The screen lives in the
 * client source set; common code cannot see it, so the client initializer installs an opener into
 * {@link #clientOpener} (same pattern as {@code MjolnirTooltip}). On a dedicated server the default
 * no-op opener stands, which is correct -- nothing renders a screen there.
 *
 * <p>Adding this item has no effect on any Thor mechanic.
 */
public class HeroPackGuideItem extends Item {
	/** Replaced by the client initializer with {@code () -> Minecraft.getInstance().setScreen(new HeroPackGuideScreen())}. */
	public static Runnable clientOpener = () -> {
	};

	public HeroPackGuideItem(Properties properties) {
		super(properties.stacksTo(1).rarity(Rarity.UNCOMMON));
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide()) {
			clientOpener.run();
		}
		return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
	}
}
