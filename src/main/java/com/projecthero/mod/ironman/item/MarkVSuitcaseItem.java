package com.projecthero.mod.ironman.item;

import java.util.List;
import java.util.function.Consumer;

import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;

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

import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The Mark V Suitcase -- an actual item (spec section 23). Right-click while holding it, and if you have the Tony Stark
 * power the Mark V unfolds around you from the case; right-click (or sneak + C) while wearing it folds it back in.
 *
 * <p>v0.14.21: the case holds the four <em>real</em> armour stacks ({@link SuitcaseContents}), and it is a GeckoLib
 * item: a red-and-silver 3D briefcase when held or dropped (the flat sprite stays in the inventory), which unfolds in
 * the right hand as the suit climbs out of it and snaps shut as the suit folds back in (client:
 * {@code MarkVSuitcaseRenderer} / {@code MarkVSuitcaseLayer}, posed from the synced suit-up clock). Logic unchanged.
 *
 * <p>A player without the Tony Stark power gets "Stark armor rejects unauthorized user." and nothing deploys.
 */
public class MarkVSuitcaseItem extends Item implements GeoItem {
	/** Installed by the client (like {@code SuperheroArmorItem.rendererFactory}); null on a dedicated server. */
	public static Consumer<Consumer<GeoRenderProvider>> rendererFactory;

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	public MarkVSuitcaseItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level.isClientSide()) {
			return InteractionResultHolder.success(stack);
		}
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResultHolder.pass(stack);
		}
		if (!TonyStark.hasPower(serverPlayer)) {
			serverPlayer.displayClientMessage(
					Component.translatable("message.projecthero.ironman.armor_rejects").withStyle(ChatFormatting.RED), true);
			return InteractionResultHolder.fail(stack);
		}
		// "changes 15": the case IS the stowed Mark V. Right-click builds it around you from the case
		// (chest-first, ~4 s); right-click while already wearing it folds it back into the case.
		if (com.projecthero.mod.ironman.IronManArmor.wearingAnyIronMan(serverPlayer)) {
			String worn = com.projecthero.mod.ironman.IronManArmor.wornSuitId(serverPlayer);
			if (IronManSuitUpManager.beginSuitDownToCase(serverPlayer, worn != null ? worn : "mark_v")) {
				return InteractionResultHolder.success(stack);
			}
			return InteractionResultHolder.fail(stack);
		}
		if (IronManSuitUpManager.beginSuitUpFromCase(serverPlayer, "mark_v")) {
			return InteractionResultHolder.success(stack);
		}
		return InteractionResultHolder.fail(stack);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.mark_v_suitcase.hint").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		if (!SuitcaseContents.isLegacyEmpty(stack)) {
			tooltip.add(Component.translatable("item.projecthero.mark_v_suitcase.contents",
					SuitcaseContents.nonEmpty(stack).size()).withStyle(ChatFormatting.GRAY));
		}
		tooltip.add(Component.translatable("item.projecthero.ironman.requires_tony_stark").withStyle(ChatFormatting.DARK_AQUA));
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		// none: the unfold / fold is posed straight from the synced suit-up clock by the client renderer
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	@Override
	public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
		if (rendererFactory != null) {
			rendererFactory.accept(consumer);
		}
	}
}
