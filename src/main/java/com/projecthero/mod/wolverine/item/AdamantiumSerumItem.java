package com.projecthero.mod.wolverine.item;

import java.util.List;

import com.projecthero.mod.wolverine.Wolverine;
import com.projecthero.mod.wolverine.WolverineConfig;
import com.projecthero.mod.wolverine.data.ClawTier;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Adamantium Serum: the upgrade that bonds adamantium to a Wolverine's Bone Claws (BONE -> ADAMANTIUM).
 * It is a hold-to-use item ({@link WolverineConfig#TRANSFORM_TICKS}, 3 s): vanilla's use-item machinery
 * makes the transformation impossible to start twice or to duplicate by clicking, and releasing early
 * cancels it without consuming the serum. The upgrade and the consumption happen only in
 * {@link #finishUsingItem}, on the server, after the requirements are re-checked.
 */
public class AdamantiumSerumItem extends Item {
	public AdamantiumSerumItem(Properties properties) {
		super(properties.stacksTo(1));
	}

	/** Returns a translation key explaining why the serum cannot be used, or null when it can. */
	private static String refusal(ServerPlayer sp) {
		if (!Wolverine.hasPower(sp)) {
			return "message.projecthero.adamantium_serum.not_wolverine";
		}
		return switch (Wolverine.clawTier(sp)) {
			case NONE -> "message.projecthero.adamantium_serum.need_bone";
			case ADAMANTIUM -> "message.projecthero.adamantium_serum.already_adamantium";
			case BONE -> null;
		};
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (!(player instanceof ServerPlayer sp)) {
			// the client only predicts the hold; the server decides
			return Wolverine.clawTier(player) == ClawTier.BONE
					? InteractionResultHolder.consume(held) : InteractionResultHolder.fail(held);
		}
		String why = refusal(sp);
		if (why != null) {
			sp.displayClientMessage(Component.translatable(why).withStyle(ChatFormatting.RED), true);
			return InteractionResultHolder.fail(held);
		}
		if (Wolverine.clawsOut(sp)) {
			Wolverine.setClaws(sp, false);
		}
		player.startUsingItem(hand);
		level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.7f, 0.5f);
		return InteractionResultHolder.consume(held);
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return WolverineConfig.TRANSFORM_TICKS;
	}

	@Override
	public UseAnim getUseAnimation(ItemStack stack) {
		return UseAnim.BOW;
	}

	@Override
	public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
		if (!(entity instanceof ServerPlayer sp) || !(level instanceof ServerLevel sl)) {
			return;
		}
		if (refusal(sp) != null) {
			sp.stopUsingItem();
			return;
		}
		int elapsed = WolverineConfig.TRANSFORM_TICKS - remaining;
		sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 4, false, false, false));
		sp.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 25, 3, false, false, false));
		if (elapsed % 2 == 0) {
			Vec3 c = sp.position().add(0, 1.0, 0);
			sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 6, 0.4, 0.7, 0.4, 0.2);
			sl.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 3, 0.3, 0.6, 0.3, 0.1);
		}
		if (elapsed % 10 == 0) {
			sl.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5f,
					0.5f + elapsed / 60.0f);
		}
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		if (!(entity instanceof ServerPlayer sp) || refusal(sp) != null || !Wolverine.upgradeToAdamantium(sp)) {
			return stack;
		}
		ServerLevel sl = (ServerLevel) level;
		Vec3 c = sp.position().add(0, 1.0, 0);
		sl.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		sl.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 60, 0.5, 0.9, 0.5, 0.25);
		sl.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 50, 0.5, 0.9, 0.5, 0.3);
		sl.sendParticles(ParticleTypes.HEART, c.x, c.y, c.z, 8, 0.4, 0.8, 0.4, 0.0);
		sl.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.0f, 0.6f);
		sl.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.WOLF_GROWL, SoundSource.PLAYERS, 1.0f, 0.6f);
		sl.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.IRON_GOLEM_REPAIR, SoundSource.PLAYERS, 1.0f, 0.7f);
		sl.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 0.6f);
		sp.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
		sp.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 1, false, true, true));
		sp.displayClientMessage(Component.translatable("message.projecthero.adamantium_serum.success")
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		stack.shrink(1);
		return stack;
	}

	@Override
	public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
		if (entity instanceof ServerPlayer sp) {
			sp.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.adamantium_serum.desc0").withStyle(ChatFormatting.GOLD));
		tooltip.add(Component.translatable("item.projecthero.adamantium_serum.desc1").withStyle(ChatFormatting.DARK_GRAY));
		tooltip.add(Component.translatable("item.projecthero.adamantium_serum.desc2").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}
}
