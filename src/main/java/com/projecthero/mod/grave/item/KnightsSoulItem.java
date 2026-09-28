package com.projecthero.mod.grave.item;

import java.util.List;

import com.projecthero.mod.oathbreaker.entity.OathbreakerEntity;
import com.projecthero.mod.oathbreaker.entity.OathbreakerEntityTypes;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * Knight's Soul -- crafted from an Abyssal Core surrounded by Gravebound Essence. Right-clicked on a
 * Lodestone, it consumes itself and calls forth The Oathbreaker beside the stone.
 *
 * <p>Every gate is server-side, same shape as {@link GraveRitualTotemItem}: the block really has to be
 * a Lodestone, there has to be open ground to actually place the boss on, and one already summoned
 * nearby refuses a second so a player can't flood the area with them.
 */
public class KnightsSoulItem extends Item {
	/** How far away an already-summoned Oathbreaker blocks a new one. */
	private static final double DEDUP_RADIUS = 48.0;

	public KnightsSoulItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		if (!level.getBlockState(context.getClickedPos()).is(Blocks.LODESTONE)) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide() || !(context.getPlayer() instanceof ServerPlayer player)
				|| !(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.SUCCESS;
		}

		BlockPos lodestone = context.getClickedPos();
		if (activeNearby(serverLevel, lodestone)) {
			fail(player, "message.projecthero.knights_soul.already_active");
			return InteractionResult.FAIL;
		}
		BlockPos spawnPos = findSpot(serverLevel, lodestone);
		if (spawnPos == null) {
			fail(player, "message.projecthero.knights_soul.no_room");
			return InteractionResult.FAIL;
		}

		OathbreakerEntity oathbreaker = OathbreakerEntityTypes.OATHBREAKER.create(serverLevel);
		if (oathbreaker == null) {
			return InteractionResult.FAIL;
		}
		oathbreaker.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5,
				serverLevel.random.nextFloat() * 360f, 0f);
		oathbreaker.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(spawnPos), MobSpawnType.MOB_SUMMONED, null);
		oathbreaker.setPersistenceRequired();
		serverLevel.addFreshEntity(oathbreaker);

		context.getItemInHand().shrink(1);
		serverLevel.playSound(null, lodestone, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1.5f, 0.5f);
		serverLevel.playSound(null, lodestone, SoundEvents.IRON_GOLEM_DAMAGE, SoundSource.PLAYERS, 1.5f, 0.6f);
		serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME,
				lodestone.getX() + 0.5, lodestone.getY() + 1.0, lodestone.getZ() + 0.5, 50, 0.5, 0.8, 0.5, 0.05);
		player.sendSystemMessage(Component.translatable("message.projecthero.knights_soul.summoned")
				.withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
		return InteractionResult.CONSUME;
	}

	private boolean activeNearby(ServerLevel level, BlockPos center) {
		AABB box = new AABB(center).inflate(DEDUP_RADIUS);
		return !level.getEntitiesOfClass(OathbreakerEntity.class, box).isEmpty();
	}

	/** A legal, open spot for the boss right beside the lodestone. */
	private BlockPos findSpot(ServerLevel level, BlockPos lodestone) {
		for (int radius = 1; radius <= 3; radius++) {
			for (Direction dir : Direction.Plane.HORIZONTAL) {
				BlockPos candidate = lodestone.relative(dir, radius);
				if (isOpen(level, candidate)) {
					return candidate;
				}
			}
		}
		return isOpen(level, lodestone.above()) ? lodestone.above() : null;
	}

	private boolean isOpen(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return false;
		}
		for (int dy = 0; dy < 4; dy++) {
			BlockPos p = pos.above(dy);
			if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
				return false;
			}
		}
		BlockPos below = pos.below();
		return level.getBlockState(below).isSolidRender(level, below);
	}

	private static void fail(ServerPlayer player, String messageKey) {
		player.displayClientMessage(Component.translatable(messageKey).withStyle(ChatFormatting.RED), true);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(Component.translatable("item.projecthero.knights_soul.hint").withStyle(ChatFormatting.GRAY));
	}
}
