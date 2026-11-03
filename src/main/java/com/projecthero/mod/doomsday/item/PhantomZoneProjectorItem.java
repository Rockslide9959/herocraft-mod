package com.projecthero.mod.doomsday.item;

import java.util.List;

import com.projecthero.mod.doomsday.DoomsdayConfig;
import com.projecthero.mod.doomsday.DoomsdayFx;
import com.projecthero.mod.doomsday.entity.DoomsdayEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2: <b>the Phantom Zone Projector</b> -- the only way to be rid of Doomsday. Hold right-click
 * ({@link DoomsdayConfig.Projector#useTicks}) aimed at his corpse after his third death: a Phantom Zone pane opens over
 * him and pulls him in. On a living Doomsday, or on a corpse that still has lives in it, it refuses.
 */
public class PhantomZoneProjectorItem extends Item {
	public PhantomZoneProjectorItem(Properties properties) {
		super(properties);
	}

	/** The corpse the player is aiming at (within range, inside a fat cone), or null. */
	public static DoomsdayEntity aimed(Player player) {
		double range = DoomsdayConfig.projector().range;
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		DoomsdayEntity best = null;
		double bestDot = 0.85;
		for (DoomsdayEntity d : player.level().getEntitiesOfClass(DoomsdayEntity.class, new AABB(eye, eye).inflate(range + 4))) {
			Vec3 c = d.position().add(0, Math.min(1.0, d.getBbHeight() * 0.3), 0);
			Vec3 to = c.subtract(eye);
			if (to.length() > range + d.getBbWidth()) {
				continue;
			}
			double dot = to.normalize().dot(look);
			if (dot > bestDot) {
				bestDot = dot;
				best = d;
			}
		}
		return best;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		DoomsdayEntity d = aimed(player);
		if (d == null || !d.isCorpse() || !d.finalDeath()) {
			if (!level.isClientSide) {
				String key = d == null ? "message.projecthero.doomsday.projector.no_target"
						: d.isCorpse() ? "message.projecthero.doomsday.projector.lives_left" : "message.projecthero.doomsday.projector.alive";
				player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.AQUA), true);
			}
			return InteractionResultHolder.fail(stack);
		}
		player.startUsingItem(hand);
		if (!level.isClientSide) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 2.0f, 1.6f);
		}
		return InteractionResultHolder.consume(stack);
	}

	@Override
	public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
		if (!(level instanceof ServerLevel server) || !(user instanceof Player player)) {
			return;
		}
		DoomsdayEntity d = aimed(player);
		if (d == null || !d.isCorpse() || !d.finalDeath()) {
			player.stopUsingItem();
			return;
		}
		int used = getUseDuration(stack, user) - remaining;
		if (used % 3 == 0) {
			DoomsdayFx.line(server, DoomsdayFx.PHANTOM, player.getEyePosition().add(player.getLookAngle().scale(0.6)), d.position().add(0, 0.8, 0), 0.5);
		}
		if (used % 20 == 0) {
			player.displayClientMessage(Component.translatable("message.projecthero.doomsday.projector.charging",
					Math.max(0, (getUseDuration(stack, user) - used + 19) / 20)).withStyle(ChatFormatting.AQUA), true);
		}
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
		if (level instanceof ServerLevel server && user instanceof ServerPlayer player) {
			DoomsdayEntity d = aimed(player);
			if (d != null && d.banish(server, player)) {
				player.displayClientMessage(Component.translatable("message.projecthero.doomsday.projector.fired").withStyle(ChatFormatting.AQUA,
						ChatFormatting.BOLD), true);
				if (!player.getAbilities().instabuild) {
					stack.shrink(1);
				}
			}
		}
		return stack;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return Math.max(10, DoomsdayConfig.projector().useTicks);
	}

	@Override
	public UseAnim getUseAnimation(ItemStack stack) {
		return UseAnim.BOW;
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return true;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.phantom_zone_projector.hint").withStyle(ChatFormatting.AQUA));
		tooltip.add(Component.translatable("item.projecthero.phantom_zone_projector.hint2").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}
}
