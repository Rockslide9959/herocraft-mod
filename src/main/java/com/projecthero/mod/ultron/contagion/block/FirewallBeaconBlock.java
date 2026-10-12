package com.projecthero.mod.ultron.contagion.block;

import java.util.List;

import com.mojang.serialization.MapCodec;
import com.projecthero.mod.ultron.contagion.ContagionConfig;
import com.projecthero.mod.ultron.contagion.ContagionState;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * v0.16.1: the <b>Firewall Beacon</b>. Fuel it with redstone blocks (right-click; one block = one in-game day, up to
 * {@link ContagionConfig.Beacon#maxFuelBlocks}; Sneak puts in the whole stack). While fuelled ({@link #LIT}) a
 * {@link ContagionConfig.Beacon#radius}-block radius is immune to the corruption, to new Nexus spires and to suit hijacks;
 * it slowly lowers the Saturation; and after Ultron Omega is beaten it keeps him from resurfacing. When Ultron "probes" it,
 * a blue hex dome flickers at its edge (client {@code FirewallBeaconRenderer}). Its fuel lives in {@link ContagionState},
 * so an unloaded beacon still counts.
 */
public class FirewallBeaconBlock extends BaseEntityBlock {
	public static final MapCodec<FirewallBeaconBlock> CODEC = simpleCodec(FirewallBeaconBlock::new);
	public static final BooleanProperty LIT = BooleanProperty.create("lit");
	private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 16, 15);

	public FirewallBeaconBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(LIT, false));
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LIT);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
		return SHAPE;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new FirewallBeaconBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null : createTickerHelper(type, ContagionBlocks.FIREWALL_BEACON_BE, FirewallBeaconBlockEntity::serverTick);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level instanceof ServerLevel server && server.dimension() == Level.OVERWORLD) {
			ContagionState s = ContagionState.get(server);
			s.beacons.putIfAbsent(pos.immutable(), server.getGameTime());
			s.setDirty();
		}
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.is(newState.getBlock()) && level instanceof ServerLevel server && server.dimension() == Level.OVERWORLD) {
			ContagionState s = ContagionState.get(server);
			if (s.beacons.remove(pos) != null) {
				s.setDirty();
			}
		}
		super.onRemove(state, level, pos, newState, moved);
	}

	/** Adds up to {@code blocks} redstone blocks of fuel to the beacon at {@code pos}; returns how many were taken. */
	public static int refuel(ServerLevel level, BlockPos pos, int blocks) {
		ContagionConfig.Beacon cfg = ContagionConfig.beacon();
		ContagionState s = ContagionState.get(level);
		long now = level.getGameTime();
		long until = Math.max(now, s.beacons.getOrDefault(pos, now));
		long cap = now + (long) cfg.maxFuelBlocks * cfg.ticksPerRedstoneBlock;
		int taken = 0;
		while (taken < blocks && until + cfg.ticksPerRedstoneBlock <= cap) {
			until += cfg.ticksPerRedstoneBlock;
			taken++;
		}
		if (taken > 0) {
			s.beacons.put(pos.immutable(), until);
			s.setDirty();
			BlockState st = level.getBlockState(pos);
			if (st.hasProperty(LIT) && !st.getValue(LIT)) {
				level.setBlock(pos, st.setValue(LIT, true), Block.UPDATE_ALL);
			}
		}
		return taken;
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hit) {
		if (!stack.is(Items.REDSTONE_BLOCK)) {
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (level instanceof ServerLevel server) {
			if (server.dimension() != Level.OVERWORLD) {
				player.displayClientMessage(Component.translatable("block.projecthero.firewall_beacon.overworld").withStyle(ChatFormatting.GRAY), true);
				return ItemInteractionResult.FAIL;
			}
			int want = player.isShiftKeyDown() ? stack.getCount() : 1;
			int taken = refuel(server, pos, want);
			if (taken == 0) {
				player.displayClientMessage(Component.translatable("block.projecthero.firewall_beacon.full").withStyle(ChatFormatting.AQUA), true);
				return ItemInteractionResult.FAIL;
			}
			if (!player.getAbilities().instabuild) {
				stack.shrink(taken);
			}
			server.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.0f, 1.4f);
			server.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 12, 0.3, 0.2, 0.3, 0.1);
			showStatus(server, pos, player);
		}
		return ItemInteractionResult.sidedSuccess(level.isClientSide());
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel server) {
			showStatus(server, pos, player);
		}
		return InteractionResult.sidedSuccess(level.isClientSide());
	}

	private static void showStatus(ServerLevel server, BlockPos pos, Player player) {
		ContagionState s = ContagionState.get(server);
		long left = s.beacons.getOrDefault(pos, 0L) - server.getGameTime();
		if (left <= 0) {
			player.displayClientMessage(Component.translatable("block.projecthero.firewall_beacon.unpowered").withStyle(ChatFormatting.RED), true);
		} else {
			double days = left / (double) ContagionConfig.beacon().ticksPerRedstoneBlock;
			player.displayClientMessage(Component.translatable("block.projecthero.firewall_beacon.fuel",
					String.format(java.util.Locale.ROOT, "%.1f", days)).withStyle(ChatFormatting.AQUA), true);
		}
	}

	/** The item tooltip. */
	public static void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("block.projecthero.firewall_beacon.hint").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("block.projecthero.firewall_beacon.hint2").withStyle(ChatFormatting.DARK_AQUA));
	}
}
