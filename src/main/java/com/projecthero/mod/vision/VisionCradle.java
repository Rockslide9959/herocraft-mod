package com.projecthero.mod.vision;

import com.mojang.serialization.MapCodec;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.ultron.item.UltronItems;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * v0.17.3 Vision: how you become him. The <b>Synthezoid Cradle</b> (Helen Cho's Regeneration Cradle, holding the vibranium body
 * Ultron grew for the Mind Stone) is crafted from the Ultron event's spoils: 3 Glass, 2 Vibranium Plating, an Ultron Core, 3 Iron
 * Blocks. <b>Right-click it holding the Mind Stone</b> (the first Ultron clear's reward): the stone sets into the body, a harmless
 * bolt strikes the Cradle and you wake up as Vision. The stone is used up (it's in his forehead now); the Cradle stays.
 */
public final class VisionCradle {
	public static Block CRADLE;
	public static Item CRADLE_ITEM;

	private VisionCradle() {
	}

	public static void initialize() {
		if (CRADLE != null) {
			return;
		}
		CRADLE = Registry.register(BuiltInRegistries.BLOCK, ProjectHeroMod.id("synthezoid_cradle"),
				new CradleBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0f, 1200.0f).sound(SoundType.NETHERITE_BLOCK)
						.noOcclusion().lightLevel(s -> 7)));
		CRADLE_ITEM = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("synthezoid_cradle"),
				new BlockItem(CRADLE, new Item.Properties().rarity(Rarity.EPIC).fireResistant()) {
					@Override
					public void appendHoverText(ItemStack stack, TooltipContext context, java.util.List<Component> tooltip, TooltipFlag flag) {
						tooltip.add(Component.translatable("block.projecthero.synthezoid_cradle.tip").withStyle(ChatFormatting.GOLD));
					}
				});
	}

	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(CRADLE_ITEM);
	}

	public static boolean isMindStone(ItemStack stack) {
		return UltronItems.MIND_STONE != null && !stack.isEmpty() && stack.is(UltronItems.MIND_STONE);
	}

	/**
	 * The Mind Stone goes into the Cradle at {@code pos}: {@code player} wakes up as Vision. Refused (the stone kept) if he already is,
	 * or unique powers stop it. Public for the tests.
	 */
	public static boolean awaken(ServerPlayer player, BlockPos pos, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!isMindStone(stack)) {
			return false;
		}
		if (Vision.hasPower(player)) {
			player.displayClientMessage(Component.translatable("message.projecthero.vision.already").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (!Vision.grant(player)) {
			return false;
		}
		if (!player.getAbilities().instabuild) {
			stack.shrink(1);
		}
		ServerLevel level = player.serverLevel();
		Vec3 c = Vec3.atBottomCenterOf(pos).add(0, 0.6, 0);
		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
		if (bolt != null) {
			bolt.moveTo(c.x, pos.getY() + 0.6, c.z);
			bolt.setVisualOnly(true);
			level.addFreshEntity(bolt);
		}
		level.sendParticles(Vision.dust(Vision.GOLD, 2.0f), c.x, c.y, c.z, 60, 0.6, 0.4, 0.6, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 0.4, c.z, 30, 0.4, 0.4, 0.4, 0.1);
		level.playSound(null, pos, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.BLOCKS, 0.4f, 1.3f);
		return true;
	}

	/** The Cradle: a vibranium bed under a glass lid. */
	public static class CradleBlock extends Block {
		public static final MapCodec<CradleBlock> CODEC = simpleCodec(CradleBlock::new);
		private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 6, 16), Block.box(1, 6, 1, 15, 11, 15));

		public CradleBlock(BlockBehaviour.Properties properties) {
			super(properties);
		}

		@Override
		protected MapCodec<? extends Block> codec() {
			return CODEC;
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return SHAPE;
		}

		@Override
		protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
				BlockHitResult hit) {
			if (!isMindStone(stack)) {
				if (!level.isClientSide() && hand == InteractionHand.MAIN_HAND && !Vision.hasPower(player)) {
					player.displayClientMessage(Component.translatable("message.projecthero.vision.cradle_hint").withStyle(ChatFormatting.GRAY), true);
				}
				return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
			}
			if (level.isClientSide()) {
				return ItemInteractionResult.SUCCESS;
			}
			awaken((ServerPlayer) player, pos, hand);
			return ItemInteractionResult.CONSUME;
		}
	}
}
