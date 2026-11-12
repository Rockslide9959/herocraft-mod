package com.projecthero.mod.doctorfate;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.doctorfate.worldgen.HelmetPedestalBlock;
import com.projecthero.mod.doctorfate.worldgen.TombGuardian;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * v0.17.3 Doctor Fate: the power's items, block and mob.
 * <ul>
 *   <li><b>Ankh Charm</b>: Order's command reward. Right-click to quiet Nabu (-25 Dominion).</li>
 *   <li><b>Helmet Pedestal</b>: the Tomb of Nabu's altar the helmet rests on ({@link HelmetPedestalBlock}).</li>
 *   <li><b>Tomb Guardian</b>: the mummy-wrapped husks guarding the tomb ({@link TombGuardian}).</li>
 * </ul>
 * The Helmet of Fate is no item (user, v0.17.3: "make it similar to the Nova ability"): accepting Nabu's offer at the pedestal grants
 * the power and H puts the helmet on / takes it off. A {@code projecthero:helmet_of_fate} stack saved in an old test world is an
 * unknown id now, which vanilla drops with a log line when it loads (the gametests check that).
 */
public final class DoctorFateItems {
	public static Item ANKH_CHARM;
	public static Block HELMET_PEDESTAL;
	public static Item HELMET_PEDESTAL_ITEM;
	public static EntityType<TombGuardian> TOMB_GUARDIAN;

	private DoctorFateItems() {
	}

	public static void initialize() {
		if (ANKH_CHARM != null) {
			return;
		}
		ANKH_CHARM = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("ankh_charm"),
				new AnkhCharmItem(new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));
		HELMET_PEDESTAL = Registry.register(BuiltInRegistries.BLOCK, ProjectHeroMod.id("helmet_pedestal"),
				new HelmetPedestalBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(-1.0f, 3600000.0f)
						.sound(SoundType.STONE).noOcclusion().lightLevel(s -> s.getValue(HelmetPedestalBlock.HAS_HELMET) ? 10 : 3)));
		HELMET_PEDESTAL_ITEM = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("helmet_pedestal"),
				new BlockItem(HELMET_PEDESTAL, new Item.Properties()));
		TOMB_GUARDIAN = Registry.register(BuiltInRegistries.ENTITY_TYPE, ProjectHeroMod.id("tomb_guardian"),
				EntityType.Builder.of(TombGuardian::new, MobCategory.MONSTER).sized(0.6f, 1.95f).clientTrackingRange(8).build("tomb_guardian"));
		FabricDefaultAttributeRegistry.register(TOMB_GUARDIAN, TombGuardian.createAttributes());
	}

	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(ANKH_CHARM);
		output.accept(HELMET_PEDESTAL_ITEM);
	}

	/** Order's command reward: quiets Nabu. */
	public static class AnkhCharmItem extends Item {
		public AnkhCharmItem(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
			ItemStack stack = player.getItemInHand(hand);
			if (!DoctorFate.hasPower(player) || DoctorFate.dominion(player) <= 0f) {
				return InteractionResultHolder.pass(stack);
			}
			if (!level.isClientSide()) {
				ServerPlayer sp = (ServerPlayer) player;
				DoctorFate.addDominion(sp, -DoctorFateConfig.CHARM_DOMINION);
				DoctorFate.sound(sp, SoundEvents.AMETHYST_BLOCK_CHIME, 0.7f, 1.2f);
				sp.displayClientMessage(Component.translatable("message.projecthero.doctor_fate.charm").withStyle(ChatFormatting.GOLD), true);
				if (!sp.getAbilities().instabuild) {
					stack.shrink(1);
				}
			}
			return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
		}

		@Override
		public void appendHoverText(ItemStack stack, TooltipContext context, java.util.List<Component> tooltip, TooltipFlag flag) {
			tooltip.add(Component.translatable("item.projecthero.ankh_charm.tip").withStyle(ChatFormatting.GRAY));
		}
	}

}
