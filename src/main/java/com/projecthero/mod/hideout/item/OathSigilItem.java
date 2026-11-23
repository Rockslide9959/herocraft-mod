package com.projecthero.mod.hideout.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * v0.17.7 artifact (Oathbreaker Keep): <b>Champion's Oath Sigil</b> -- the Oathbound Champion's seal. Use it to swear the
 * oath: Resistance II and Strength I for {@link #DURATION} ticks (8 s) in a ring of soul fire. {@link #COOLDOWN} ticks
 * (60 s).
 */
public class OathSigilItem extends Item {
	public static final int DURATION = 160;
	public static final int COOLDOWN = 1200;

	public OathSigilItem(Properties props) {
		super(props);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level instanceof ServerLevel server) {
			swear(server, player);
		}
		player.getCooldowns().addCooldown(this, COOLDOWN);
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
	}

	/** Swears the oath for {@code player} (test hook too). */
	public static void swear(ServerLevel level, Player player) {
		player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, DURATION, 1));
		player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, DURATION, 0));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 1.0f, 0.8f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 1.0f, 0.7f);
		for (int i = 0; i < 30; i++) {
			double a = i * Mth.TWO_PI / 30;
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX() + Math.cos(a) * 1.4, player.getY() + 0.1, player.getZ() + Math.sin(a) * 1.4,
					1, 0, 0.06, 0, 0.01);
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.oath_sigil.tooltip").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("projecthero.hideout.artifact.from", Component.translatable("structure.projecthero.oathbreaker_keep"))
				.withStyle(ChatFormatting.DARK_GRAY));
	}
}
