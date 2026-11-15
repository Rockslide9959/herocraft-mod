package com.projecthero.mod.vaultdungeon.item;

import java.util.List;

import com.projecthero.mod.gear.SmokeCloudEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * v0.17.5 artifact (The Vault): <b>Warden's Keyring</b> -- Warden Kross's master keys. Use: <b>Lockdown</b> -- every
 * hostile within {@link #RADIUS} blocks is shackled for {@link #SHACKLE_TICKS} (Slowness VI + Weakness II; bosses a third
 * as long), 40 s cooldown; with nobody to shackle nothing is spent. The keys also fit any iron door or iron trapdoor
 * (use one on it to open / close it, no cooldown).
 */
public class WardensKeyringItem extends Item {
	public static final double RADIUS = 10.0;
	public static final int SHACKLE_TICKS = 80;
	public static final int COOLDOWN = 800;

	public WardensKeyringItem(Properties props) {
		super(props);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!(level instanceof ServerLevel server)) {
			return InteractionResultHolder.sidedSuccess(stack, true);
		}
		int n = shackle(server, player);
		if (n == 0) {
			player.displayClientMessage(Component.translatable("item.projecthero.wardens_keyring.none").withStyle(ChatFormatting.GRAY), true);
			return InteractionResultHolder.fail(stack);
		}
		player.getCooldowns().addCooldown(this, COOLDOWN);
		return InteractionResultHolder.success(stack);
	}

	/** Shackles every hostile around {@code player}. Returns how many. */
	public static int shackle(ServerLevel level, Player player) {
		List<Mob> mobs = level.getEntitiesOfClass(Mob.class, new AABB(player.blockPosition()).inflate(RADIUS),
				m -> m.isAlive() && m instanceof Enemy && m.distanceToSqr(player) <= RADIUS * RADIUS);
		for (Mob m : mobs) {
			int ticks = SmokeCloudEntity.resists(m) ? SHACKLE_TICKS / 3 : SHACKLE_TICKS;
			m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 5), player);
			m.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 1), player);
			m.getNavigation().stop();
			level.sendParticles(ParticleTypes.ENCHANTED_HIT, m.getX(), m.getY() + m.getBbHeight() * 0.5, m.getZ(), 14, 0.35, 0.5, 0.35, 0.05);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, m.getX(), m.getY() + 0.2, m.getZ(), 8, 0.4, 0.05, 0.4, 0.02);
		}
		if (!mobs.isEmpty()) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.5f, 0.6f);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.PLAYERS, 1.0f, 0.7f);
		}
		return mobs.size();
	}

	@Override
	public InteractionResult useOn(UseOnContext ctx) {
		Level level = ctx.getLevel();
		BlockPos pos = ctx.getClickedPos();
		BlockState s = level.getBlockState(pos);
		if (s.is(Blocks.IRON_DOOR) && s.getBlock() instanceof DoorBlock door) {
			if (!level.isClientSide()) {
				door.setOpen(ctx.getPlayer(), level, s, pos, !s.getValue(DoorBlock.OPEN));
			}
			return InteractionResult.sidedSuccess(level.isClientSide());
		}
		if (s.is(Blocks.IRON_TRAPDOOR)) {
			if (!level.isClientSide()) {
				boolean open = !s.getValue(TrapDoorBlock.OPEN);
				level.setBlock(pos, s.setValue(TrapDoorBlock.OPEN, open), 3);
				level.playSound(null, pos, open ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1.0f, 1.0f);
			}
			return InteractionResult.sidedSuccess(level.isClientSide());
		}
		return InteractionResult.PASS;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.wardens_keyring.tooltip").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.projecthero.wardens_keyring.tooltip2").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("projecthero.vault.artifact.from").withStyle(ChatFormatting.DARK_GRAY));
	}
}
