package com.projecthero.mod.omnitrix.item;

import java.util.List;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixMeteors;
import com.projecthero.mod.omnitrix.OmnitrixSounds;
import com.projecthero.mod.omnitrix.OmnitrixWorld;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** v0.17.1: the Omnitrix (the popped-off watch), the Meteor Scanner, and the Containment Pod's crafting chain. */
public final class OmnitrixItems {
	public static Item OMNITRIX;
	public static Item METEOR_SCANNER;
	public static Item PLUMBER_ALLOY;
	public static Item CONTAINMENT_CORE;

	private OmnitrixItems() {
	}

	public static void initialize() {
		OMNITRIX = register("omnitrix", new OmnitrixItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));
		METEOR_SCANNER = register("meteor_scanner", new MeteorScannerItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
		PLUMBER_ALLOY = register("plumber_alloy", new HintItem(new Item.Properties().rarity(Rarity.EPIC).fireResistant(),
				"item.projecthero.plumber_alloy.hint"));
		CONTAINMENT_CORE = register("containment_core", new HintItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC).fireResistant(),
				"item.projecthero.containment_core.hint"));
	}

	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(OMNITRIX);
		output.accept(METEOR_SCANNER);
		output.accept(PLUMBER_ALLOY);
		output.accept(CONTAINMENT_CORE);
		output.accept(com.projecthero.mod.omnitrix.pod.OmnitrixPodBlocks.POD_ITEM);
	}

	private static Item register(String path, Item item) {
		return Registry.register(BuiltInRegistries.ITEM, ResourceKey.create(Registries.ITEM, ProjectHeroMod.id(path)), item);
	}

	/** A crafting ingredient with a grey hint line. */
	public static final class HintItem extends Item {
		private final String hint;

		public HintItem(Properties p, String hint) {
			super(p);
			this.hint = hint;
		}

		@Override
		public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
			tooltip.add(Component.translatable(hint).withStyle(ChatFormatting.GRAY));
		}
	}

	/** The watch itself, once the Containment Pod has pulled it off someone. Right-click to put it on -- and be locked in. */
	public static final class OmnitrixItem extends Item {
		public OmnitrixItem(Properties p) {
			super(p);
		}

		@Override
		public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
			ItemStack stack = player.getItemInHand(hand);
			if (!(player instanceof ServerPlayer sp)) {
				return InteractionResultHolder.success(stack);
			}
			if (Omnitrix.hasPower(sp)) {
				sp.displayClientMessage(Component.translatable("message.projecthero.omnitrix.already_wearing").withStyle(ChatFormatting.GRAY), true);
				return InteractionResultHolder.fail(stack);
			}
			if (!OmnitrixWorld.putOnLoose(sp)) {
				return InteractionResultHolder.fail(stack);
			}
			stack.shrink(1);
			return InteractionResultHolder.consume(stack);
		}

		@Override
		public boolean isFoil(ItemStack stack) {
			return true;
		}

		@Override
		public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
			tooltip.add(Component.translatable("item.projecthero.omnitrix.hint").withStyle(ChatFormatting.GREEN));
			tooltip.add(Component.translatable("item.projecthero.omnitrix.warning").withStyle(ChatFormatting.RED));
		}
	}

	/** Mid-game: calls the Omnitrix's pod down within 50 blocks. Consumed. */
	public static final class MeteorScannerItem extends Item {
		public MeteorScannerItem(Properties p) {
			super(p);
		}

		@Override
		public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
			ItemStack stack = player.getItemInHand(hand);
			if (!(player instanceof ServerPlayer sp)) {
				return InteractionResultHolder.success(stack);
			}
			Vec3 at = OmnitrixMeteors.scannerDrop(sp);
			if (at == null) {
				sp.getCooldowns().addCooldown(this, 20);
				return InteractionResultHolder.fail(stack);
			}
			OmnitrixSounds.play(sp.serverLevel(), sp.position(), OmnitrixSounds.SCANNER, 1.0f, 1.0f);
			sp.displayClientMessage(Component.translatable("message.projecthero.meteor_scanner.found",
					(int) Math.round(at.distanceTo(sp.position()))).withStyle(ChatFormatting.GREEN), true);
			if (!sp.getAbilities().instabuild) {
				stack.shrink(1);
			}
			return InteractionResultHolder.consume(stack);
		}

		@Override
		public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
			tooltip.add(Component.translatable("item.projecthero.meteor_scanner.hint").withStyle(ChatFormatting.GRAY));
		}
	}
}
