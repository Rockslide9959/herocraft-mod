package com.projecthero.mod.temple.block;

import java.util.Locale;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * v0.17.7: one of the Hall of Trials' three <b>Seals</b> -- a glowing gold-and-lapis tablet on a pedestal. Unbreakable;
 * it breaks only when its trial is solved: {@link Kind#GLYPH} (the glyph plates walked in the mural's order),
 * {@link Kind#LIGHT} (the sun beam reflected onto it by the mirrors) or {@link Kind#WEIGHT} (the weight altar balanced).
 * When all three in a hall are broken the hall's {@link TempleGateBlock}s ({@code gate=trials}) open. Using a seal says
 * what its trial wants.
 */
public class TempleSealBlock extends BaseEntityBlock {
	public static final MapCodec<TempleSealBlock> CODEC = simpleCodec(TempleSealBlock::new);
	public static final EnumProperty<Kind> KIND = EnumProperty.create("kind", Kind.class);
	public static final BooleanProperty BROKEN = BooleanProperty.create("broken");
	private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 16, 14);

	public enum Kind implements StringRepresentable {
		GLYPH, LIGHT, WEIGHT;

		@Override
		public String getSerializedName() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	public TempleSealBlock(BlockBehaviour.Properties props) {
		super(props);
		registerDefaultState(stateDefinition.any().setValue(KIND, Kind.GLYPH).setValue(BROKEN, false));
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(KIND, BROKEN);
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
		return new TempleSealBlockEntity(pos, state);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel) {
			String k = state.getValue(BROKEN) ? "broken" : state.getValue(KIND).getSerializedName();
			player.displayClientMessage(Component.translatable("block.projecthero.temple_seal." + k).withStyle(s -> s.withColor(0xE8C060)), true);
		}
		return InteractionResult.sidedSuccess(level.isClientSide());
	}
}
