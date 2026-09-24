package com.projecthero.mod.item;

import java.util.List;

import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.TonyStark;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * "changes 18": a survival-craftable way to give up your superpowers -- the natural counterpart to
 * gaining them. Sneak + use to permanently strip <b>every</b> power -- Hero-Tier, experimental, Green
 * Lantern, Symbiote -- at once:
 * <ul>
 *   <li>the Tony Stark power (via {@link TonyStark#revoke});</li>
 *   <li>the Spider-Man Hero Class (the Spider Adhesion it grew out of is not handed back);</li>
 *   <li>every owned HeroPack experimental / mutation power (mirrors {@code /heropower revoke all});</li>
 *   <li>Mjolnir worthiness (score reset to 0 -- the hammer will reject the player again).</li>
 * </ul>
 * One is consumed on use. Refuses while an Iron Man suit is worn (take it off first) and does nothing
 * if the player has no powers to remove. A plain (non-sneak) use just prints the confirm hint.
 */
public class PowerSuppressorItem extends Item {
	public PowerSuppressorItem(Properties properties) {
		super(properties.stacksTo(16));
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (level.isClientSide() || !(player instanceof ServerPlayer sp)) {
			return InteractionResultHolder.success(held);
		}

		boolean any = com.projecthero.mod.hero.HeroTiers.hasHeroTier(sp)
				|| com.projecthero.mod.hero.HeroTiers.hasExperimental(sp)
				|| com.projecthero.mod.symbiote.Symbiote.hasSymbiote(sp);
		if (!any) {
			sp.displayClientMessage(Component.translatable("message.projecthero.power_suppressor.nothing"), true);
			return InteractionResultHolder.fail(held);
		}
		if (IronManArmor.wearingAnyIronMan(sp)) {
			sp.displayClientMessage(Component.translatable("commands.projecthero.superhero.remove_suit_first"), true);
			return InteractionResultHolder.fail(held);
		}
		if (com.projecthero.mod.maxsteel.MaxSteel.isTransformed(sp)) {
			sp.displayClientMessage(Component.translatable("commands.projecthero.superhero.remove_suit_first"), true);
			return InteractionResultHolder.fail(held);
		}
		if (!player.isShiftKeyDown()) {
			sp.displayClientMessage(Component.translatable("message.projecthero.power_suppressor.confirm")
					.withStyle(ChatFormatting.RED), true);
			return InteractionResultHolder.fail(held);
		}

		// Strips every power of every tier in one clean pass: all Primary powers (Hero-Tier + mutations) and
		// the Secondary Symbiote.
		com.projecthero.mod.hero.HeroTiers.wipeAll(sp);
		if (com.projecthero.mod.symbiote.Symbiote.hasSymbiote(sp)) {
			com.projecthero.mod.symbiote.Symbiote.remove(sp);
		}

		held.shrink(1);
		if (level instanceof ServerLevel serverLevel) {
			serverLevel.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
					SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0f, 0.5f);
			serverLevel.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
					SoundEvents.CONDUIT_DEACTIVATE, SoundSource.PLAYERS, 0.8f, 0.7f);
			serverLevel.sendParticles(ParticleTypes.SMOKE, sp.getX(), sp.getY() + 1.0, sp.getZ(),
					44, 0.4, 0.9, 0.4, 0.02);
			serverLevel.sendParticles(ParticleTypes.SQUID_INK, sp.getX(), sp.getY() + 1.0, sp.getZ(),
					24, 0.3, 0.7, 0.3, 0.01);
		}
		sp.displayClientMessage(Component.translatable("message.projecthero.power_suppressor.done")
				.withStyle(ChatFormatting.GRAY), false);
		return InteractionResultHolder.success(held);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.power_suppressor.desc1")
				.withStyle(ChatFormatting.DARK_GRAY));
		tooltip.add(Component.translatable("item.projecthero.power_suppressor.desc2")
				.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}
}
