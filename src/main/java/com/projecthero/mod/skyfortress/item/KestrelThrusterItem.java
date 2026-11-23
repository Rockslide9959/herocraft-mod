package com.projecthero.mod.skyfortress.item;

import java.util.List;

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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7: <b>Kestrel's Thruster</b> (Legendary), the Sky Fortress artifact -- a single jet pod torn off the Admiral's
 * flight suit. Right-click: a burst of thrust along your look ({@link #THRUST} blocks/tick, a little lift), the fall
 * forgiven and {@link #GLIDE_TICKS} of Slow Falling to glide on; {@link #COOLDOWN} ticks before it fires again. A
 * movement tool, not a power: no flight, no damage.
 */
public class KestrelThrusterItem extends Item {
	public static final double THRUST = 1.55;
	public static final int GLIDE_TICKS = 70;
	public static final int COOLDOWN = 300;

	public KestrelThrusterItem(Properties props) {
		super(props);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
			fire(server, sp);
			sp.getCooldowns().addCooldown(this, COOLDOWN);
		}
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
	}

	/** One burst: the push, the glide, the effects. */
	public static void fire(ServerLevel level, ServerPlayer player) {
		Vec3 look = player.getLookAngle();
		Vec3 v = look.scale(THRUST).add(0, 0.35, 0);
		player.setDeltaMovement(v);
		player.hurtMarked = true;
		player.resetFallDistance();
		player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, GLIDE_TICKS, 0, false, false, true));
		Vec3 back = player.position().add(0, 0.9, 0).subtract(look.scale(0.6));
		level.sendParticles(ParticleTypes.FLAME, back.x, back.y, back.z, 14, 0.15, 0.15, 0.15, 0.05);
		level.sendParticles(ParticleTypes.CLOUD, back.x, back.y, back.z, 10, 0.2, 0.2, 0.2, 0.08);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.2f, 0.7f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.6f, 1.4f);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
		lines.add(Component.translatable("item.projecthero.kestrel_thruster.hint").withStyle(ChatFormatting.GRAY));
	}
}
