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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * v0.17.7 artifact (Gravebound Crypt): <b>Gravecaller Bell</b> -- the Crypt Lord's tarnished funeral bell. Ring it (use):
 * every undead within {@link #RANGE} blocks is <em>shaken</em> -- Slowness IV and Weakness II for 6 s and it forgets its
 * target -- and other hostiles there flinch (Slowness I, 3 s). {@link #COOLDOWN} ticks (40 s). Nothing is shown on the
 * mobs beyond the effects (no outlines).
 */
public class GravecallerBellItem extends Item {
	public static final double RANGE = 16.0;
	public static final int COOLDOWN = 800;

	public GravecallerBellItem(Properties props) {
		super(props);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level instanceof ServerLevel server) {
			ring(server, player);
		}
		player.getCooldowns().addCooldown(this, COOLDOWN);
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
	}

	/** Rings the bell for {@code player}: returns how many undead were shaken (test hook too). */
	public static int ring(ServerLevel level, Player player) {
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.6f, 0.6f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.0f, 0.7f);
		for (int i = 0; i < 36; i++) {
			double a = i * Mth.TWO_PI / 36;
			level.sendParticles(ParticleTypes.SOUL, player.getX() + Math.cos(a) * 3, player.getY() + 1.0, player.getZ() + Math.sin(a) * 3, 1,
					Math.cos(a) * 0.4, 0.02, Math.sin(a) * 0.4, 0.15);
		}
		int shaken = 0;
		for (Mob m : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(RANGE), m -> m.isAlive() && m instanceof Enemy)) {
			if (m.distanceToSqr(player) > RANGE * RANGE) {
				continue;
			}
			if (m.isInvertedHealAndHarm()) {
				m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 3));
				m.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 1));
				m.setTarget(null);
				level.sendParticles(ParticleTypes.SOUL, m.getX(), m.getY() + m.getBbHeight(), m.getZ(), 6, 0.2, 0.2, 0.2, 0.02);
				shaken++;
			} else {
				m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0));
			}
		}
		return shaken;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.gravecaller_bell.tooltip").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("projecthero.hideout.artifact.from", Component.translatable("structure.projecthero.gravebound_crypt"))
				.withStyle(ChatFormatting.DARK_GRAY));
	}
}
