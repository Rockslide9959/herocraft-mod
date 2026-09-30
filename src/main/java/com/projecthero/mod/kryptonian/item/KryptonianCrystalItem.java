package com.projecthero.mod.kryptonian.item;

import java.util.List;

import com.projecthero.mod.kryptonian.Kryptonian;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import org.joml.Vector3f;

/**
 * v0.14.8: the Kryptonian Crystal -- mined out of the core of a Kryptonite Meteor, one per meteor. Hold right-click
 * under the open daytime sky for two seconds and it pours the sunlight it has stored into you: you become a Kryptonian
 * (a Hero-Tier Primary power, so it replaces whatever power you had). Away from direct sunlight it stays dark.
 */
public class KryptonianCrystalItem extends Item {
	public static final int USE_TICKS = 40;
	private static final DustParticleOptions SUN = new DustParticleOptions(new Vector3f(1.0f, 0.85f, 0.35f), 1.2f);

	public KryptonianCrystalItem(Properties properties) {
		super(properties);
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return true;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (Kryptonian.hasPower(player)) {
			if (!level.isClientSide()) {
				player.displayClientMessage(Component.translatable("message.projecthero.kryptonian_crystal.already")
						.withStyle(ChatFormatting.GRAY), true);
			}
			return InteractionResultHolder.fail(stack);
		}
		if (player instanceof ServerPlayer sp && !inSunlight(sp)) {
			sp.displayClientMessage(Component.translatable("message.projecthero.kryptonian_crystal.needs_sun")
					.withStyle(ChatFormatting.YELLOW), true);
			return InteractionResultHolder.fail(stack);
		}
		return ItemUtils.startUsingInstantly(level, player, hand);
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return USE_TICKS;
	}

	@Override
	public UseAnim getUseAnimation(ItemStack stack) {
		return UseAnim.BOW;
	}

	@Override
	public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
		if (level instanceof ServerLevel sl && remaining % 2 == 0) {
			double t = 1.0 - remaining / (double) USE_TICKS;
			sl.sendParticles(SUN, entity.getX(), entity.getY() + 1.0, entity.getZ(), 3 + (int) (t * 8), 0.5 + t, 1.0, 0.5 + t, 0.0);
			sl.sendParticles(ParticleTypes.END_ROD, entity.getX(), entity.getY() + 2.6, entity.getZ(), 1, 0.2, 0.2, 0.2, 0.02);
		}
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		if (level.isClientSide() || !(entity instanceof ServerPlayer player)) {
			return stack;
		}
		infuse(player, stack, true);
		return stack;
	}

	/**
	 * Pours the crystal into {@code player}: the Kryptonian power, and the crystal is used up. {@code requireSun} is the
	 * survival rule (tests skip it). True if the player became a Kryptonian.
	 */
	public static boolean infuse(ServerPlayer player, ItemStack stack, boolean requireSun) {
		if (Kryptonian.hasPower(player)) {
			return false;
		}
		if (requireSun && !inSunlight(player)) {
			player.displayClientMessage(Component.translatable("message.projecthero.kryptonian_crystal.needs_sun")
					.withStyle(ChatFormatting.YELLOW), true);
			return false;
		}
		if (!Kryptonian.grant(player)) {
			return false;
		}
		if (!player.getAbilities().instabuild) {
			stack.shrink(1);
		}
		return true;
	}

	/** Daytime, open sky above, not raining, in a world with a sun. */
	public static boolean inSunlight(ServerPlayer player) {
		Level level = player.level();
		BlockPos eye = BlockPos.containing(player.getEyePosition());
		return level.dimensionType().hasSkyLight() && !level.dimensionType().hasCeiling() && level.isDay()
				&& level.canSeeSky(eye) && !level.isRainingAt(eye);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.kryptonian_crystal.tooltip").withStyle(ChatFormatting.GOLD));
		tooltip.add(Component.translatable("item.projecthero.kryptonian_crystal.tooltip2").withStyle(ChatFormatting.GRAY));
	}
}
