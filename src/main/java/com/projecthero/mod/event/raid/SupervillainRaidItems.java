package com.projecthero.mod.event.raid;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.event.boss.BossPowers;
import com.projecthero.mod.grave.item.GraveComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * The Supervillain Village Raid's reward items (spec sections 36-39). Its own registration class,
 * matching how {@code GraveItems} / {@code SpiderItems} / {@code MaxSteelItems} each own their
 * feature's items and creative-tab contribution.
 *
 * <ul>
 *   <li>{@link #VILLAIN_CACHE} -- right-click to open for a bundle of high-level rewards.</li>
 *   <li>{@link #SUPERVILLAIN_TOKEN} -- a rare progression / crafting material (no recipe yet).</li>
 *   <li>{@link #POWER_FRAGMENT} -- a fragment of the boss's power (named from the power it carries,
 *       one item + a component, the same trick a Corrupted Power Core uses). Not a way to gain the
 *       power -- a future crafting / progression ingredient only.</li>
 *   <li>{@link #CHIMERA_CORE} / {@link #ARSENAL_REACTOR} / {@link #OMEGA_CRYSTAL} -- prestige trophy
 *       drops, one per villain appearance.</li>
 * </ul>
 */
public final class SupervillainRaidItems {
	public static Item VILLAIN_CACHE;
	public static Item SUPERVILLAIN_TOKEN;
	public static Item POWER_FRAGMENT;
	public static Item CHIMERA_CORE;
	public static Item ARSENAL_REACTOR;
	public static Item OMEGA_CRYSTAL;

	private SupervillainRaidItems() {
	}

	public static void initialize() {
		VILLAIN_CACHE = register("villain_cache", new VillainCacheItem(new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));
		SUPERVILLAIN_TOKEN = register("supervillain_token", new Item(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC)) {
			@Override
			public boolean isFoil(ItemStack stack) {
				return true;
			}
		});
		POWER_FRAGMENT = register("power_fragment", new PowerFragmentItem(new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));
		CHIMERA_CORE = register("chimera_core", trophy());
		ARSENAL_REACTOR = register("arsenal_reactor", trophy());
		OMEGA_CRYSTAL = register("omega_crystal", trophy());
	}

	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(VILLAIN_CACHE);
		output.accept(SUPERVILLAIN_TOKEN);
		output.accept(POWER_FRAGMENT);
		output.accept(CHIMERA_CORE);
		output.accept(ARSENAL_REACTOR);
		output.accept(OMEGA_CRYSTAL);
	}

	private static Item trophy() {
		return new Item(new Item.Properties().stacksTo(4).rarity(Rarity.EPIC)) {
			@Override
			public boolean isFoil(ItemStack stack) {
				return true;
			}
		};
	}

	private static Item register(String path, Item item) {
		return Registry.register(BuiltInRegistries.ITEM,
				ResourceKey.create(Registries.ITEM, ProjectHeroMod.id(path)), item);
	}

	// ---------------- Villain Cache ----------------

	/** A hand-rolled reward table -- no datapack loot table needed for a fixed set of high-tier drops. */
	public static final class VillainCacheItem extends Item {
		public VillainCacheItem(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
			ItemStack held = player.getItemInHand(hand);
			if (level.isClientSide() || !(player instanceof ServerPlayer sp)) {
				return InteractionResultHolder.success(held);
			}
			var random = sp.getRandom();
			List<ItemStack> rewards = new ArrayList<>();
			// guaranteed core materials
			rewards.add(new ItemStack(Items.EMERALD, 8 + random.nextInt(9)));
			rewards.add(new ItemStack(Items.DIAMOND, 2 + random.nextInt(4)));
			rewards.add(new ItemStack(com.projecthero.mod.grave.item.GraveItems.GRAVE_ESSENCE, 6 + random.nextInt(10)));
			// two random high-tier rolls
			for (int i = 0; i < 2; i++) {
				rewards.add(switch (random.nextInt(8)) {
					case 0 -> new ItemStack(Items.NETHERITE_SCRAP, 1 + random.nextInt(2));
					case 1 -> new ItemStack(Items.DIAMOND_BLOCK);
					case 2 -> new ItemStack(Items.ENCHANTED_GOLDEN_APPLE);
					case 3 -> new ItemStack(Items.EXPERIENCE_BOTTLE, 12 + random.nextInt(20));
					case 4 -> new ItemStack(Items.ANCIENT_DEBRIS, 1 + random.nextInt(3));
					case 5 -> new ItemStack(Items.NETHERITE_INGOT);
					case 6 -> new ItemStack(Items.EMERALD_BLOCK, 1 + random.nextInt(2));
					default -> new ItemStack(Items.GOLD_BLOCK, 2 + random.nextInt(3));
				});
			}

			for (ItemStack stack : rewards) {
				if (!sp.getInventory().add(stack)) {
					sp.drop(stack, false);
				}
			}
			held.shrink(1);
			level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.ENDER_CHEST_OPEN, SoundSource.PLAYERS, 0.8f, 1.2f);
			sp.displayClientMessage(Component.translatable("item.projecthero.villain_cache.opened")
					.withStyle(ChatFormatting.GOLD), true);
			return InteractionResultHolder.success(held);
		}

		@Override
		public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
			tooltip.add(Component.translatable("item.projecthero.villain_cache.hint")
					.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		}
	}

	// ---------------- Power Fragment ----------------

	public static final class PowerFragmentItem extends Item {
		public PowerFragmentItem(Properties properties) {
			super(properties);
		}

		public static ItemStack of(String powerKey) {
			ItemStack stack = new ItemStack(POWER_FRAGMENT);
			stack.set(GraveComponents.POWER_KEY, powerKey);
			return stack;
		}

		private static String powerKey(ItemStack stack) {
			String key = stack.get(GraveComponents.POWER_KEY);
			return key == null ? "" : key;
		}

		@Override
		public Component getName(ItemStack stack) {
			String key = powerKey(stack);
			if (key.isEmpty()) {
				return super.getName(stack);
			}
			return Component.translatable("item.projecthero.power_fragment.named", BossPowers.displayName(key));
		}

		@Override
		public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
			tooltip.add(Component.translatable("item.projecthero.power_fragment.hint")
					.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		}

		@Override
		public boolean isFoil(ItemStack stack) {
			return true;
		}
	}
}
