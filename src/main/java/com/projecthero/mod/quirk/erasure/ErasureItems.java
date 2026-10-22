package com.projecthero.mod.quirk.erasure;

import java.util.List;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.Quirks;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * v0.16.7 revamp: the <b>Eye Drops</b> ({@code projecthero:eye_drops}) -- Eraser Head's refill for the Dry Eyes meter.
 * Used by his Shift+V (it takes one from the inventory) or right-clicked by hand: an Erasure holder's Dry Eyes go back
 * to 0 and forced-shut eyes open; anyone else just gets clear eyes (Blindness / Darkness gone). Crafted shapeless from a
 * glass bottle, sugar and kelp (two drops); see {@code data/projecthero/recipe/eye_drops.json}.
 */
public final class ErasureItems {
	public static Item EYE_DROPS;

	private ErasureItems() {
	}

	static void initialize() {
		EYE_DROPS = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("eye_drops"), new EyeDropsItem(new Item.Properties().stacksTo(16)));
	}

	public static final class EyeDropsItem extends Item {
		EyeDropsItem(Properties props) {
			super(props);
		}

		@Override
		public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
			ItemStack st = player.getItemInHand(hand);
			if (!level.isClientSide() && player instanceof ServerPlayer sp) {
				if (Quirks.holds(sp, Quirk.ERASURE)) {
					ErasureMoves.refresh(sp);
				} else {
					sp.removeEffect(MobEffects.BLINDNESS);
					sp.removeEffect(MobEffects.DARKNESS);
					level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 0.7f, 1.6f);
				}
				player.getCooldowns().addCooldown(this, 20);
				if (!player.getAbilities().instabuild) {
					st.shrink(1);
				}
			}
			return InteractionResultHolder.sidedSuccess(st, level.isClientSide());
		}

		@Override
		public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
			lines.add(Component.translatable("item.projecthero.eye_drops.desc").withStyle(ChatFormatting.GRAY));
		}
	}
}
