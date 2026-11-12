package com.projecthero.mod.doctorfate.worldgen;

import com.mojang.serialization.MapCodec;
import com.projecthero.mod.doctorfate.DoctorFate;
import com.projecthero.mod.doctorfate.network.FateServePayload;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * v0.17.3 Doctor Fate: the Tomb of Nabu's pedestal. While {@link #HAS_HELMET} the golden Helmet of Fate rests on it; using it
 * opens Nabu's "Will you serve Order?" (Accept / Refuse, {@code FateServeScreen}). Accepting grants the Doctor Fate power and the
 * helmet leaves the pedestal (it's no item: H wears it from then on); refusing leaves it there for someone else. Unbreakable, like
 * the tomb's other holy parts.
 */
public class HelmetPedestalBlock extends Block {
	public static final MapCodec<HelmetPedestalBlock> CODEC = simpleCodec(HelmetPedestalBlock::new);
	public static final BooleanProperty HAS_HELMET = BooleanProperty.create("has_helmet");
	private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 14, 14);

	public HelmetPedestalBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(HAS_HELMET, false));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(HAS_HELMET);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!state.getValue(HAS_HELMET)) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		ServerPlayer sp = (ServerPlayer) player;
		if (DoctorFate.hasPower(sp)) {
			sp.displayClientMessage(Component.translatable("message.projecthero.doctor_fate.already").withStyle(ChatFormatting.GOLD), true);
			return InteractionResult.CONSUME;
		}
		DoctorFate.send(sp, new FateServePayload(pos));
		return InteractionResult.CONSUME;
	}
}
