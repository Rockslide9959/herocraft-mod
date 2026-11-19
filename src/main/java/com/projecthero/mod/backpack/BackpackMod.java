package com.projecthero.mod.backpack;

import java.util.List;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.item.ModCreativeTab;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.RecipeSerializer;

/**
 * v0.17.5 (user: "can you add backpacks to the mod please"): registration and the "worn on your back" sync. Five tiers
 * ({@link BackpackTier}), the menu, the upgrade recipe type, the Open Backpack key's payload and {@link #WORN}. No static
 * state of its own (the worn backpack is a player attachment), so nothing to clear in {@code ServerStateReset}. See
 * {@code docs/BACKPACKS_REFERENCE.md}.
 */
public final class BackpackMod {
	public static final Item LEATHER = item(BackpackTier.LEATHER, new Item.Properties().stacksTo(1));
	public static final Item IRON = item(BackpackTier.IRON, new Item.Properties().stacksTo(1));
	public static final Item GOLD = item(BackpackTier.GOLD, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
	public static final Item DIAMOND = item(BackpackTier.DIAMOND, new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
	public static final Item NETHERITE = item(BackpackTier.NETHERITE, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());

	public static final MenuType<BackpackMenu> MENU = Registry.register(BuiltInRegistries.MENU, ProjectHeroMod.id("backpack"),
			new ExtendedScreenHandlerType<>(BackpackMenu::new, BackpackMenu.OpenData.CODEC));

	public static final RecipeSerializer<BackpackUpgradeRecipe> UPGRADE_SERIALIZER = Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
			ProjectHeroMod.id("backpack_upgrade"), new BackpackUpgradeRecipe.Serializer());

	/**
	 * The backpack drawn on this player's back (absent = none): a contents-free copy of the best one carried, so trackers see the
	 * tier and the dye without being sent what is inside. Synced to everyone, not saved.
	 */
	public static final AttachmentType<ItemStack> WORN = AttachmentRegistry.create(ProjectHeroMod.id("backpack_worn"),
			builder -> builder.syncWith(ItemStack.OPTIONAL_STREAM_CODEC, AttachmentSyncPredicate.all()));

	private static final int WORN_PERIOD = 5;

	private BackpackMod() {
	}

	public static void initialize() {
		PayloadTypeRegistry.playC2S().register(OpenBackpackPayload.TYPE, OpenBackpackPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(OpenBackpackPayload.TYPE, (payload, context) -> openKey(context.player()));
		ItemGroupEvents.modifyEntriesEvent(ModCreativeTab.SUPERHEROES_KEY).register(entries -> {
			for (Item i : all()) {
				entries.accept(i);
			}
		});
		for (Item i : all()) {
			CauldronInteraction.WATER.map().put(i, CauldronInteraction.DYED_ITEM); // wash the dye off, like leather armour
		}
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % WORN_PERIOD != 0) {
				return;
			}
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				updateWorn(p);
			}
		});
	}

	/** Every backpack, lowest tier first. */
	public static List<Item> all() {
		return List.of(LEATHER, IRON, GOLD, DIAMOND, NETHERITE);
	}

	public static Item forTier(BackpackTier tier) {
		return switch (tier) {
			case LEATHER -> LEATHER;
			case IRON -> IRON;
			case GOLD -> GOLD;
			case DIAMOND -> DIAMOND;
			case NETHERITE -> NETHERITE;
		};
	}

	/**
	 * The inventory slot of the best backpack carried: highest tier, then the earliest slot (hotbar, main rows, then the offhand).
	 * With {@code includeHands} false the main-hand stack and the offhand are skipped (it is drawn in the hand instead). -1 = none.
	 */
	public static int bestSlot(Player p, boolean includeHands) {
		Inventory inv = p.getInventory();
		int best = -1;
		int bestTier = -1;
		for (int i = 0; i <= Inventory.SLOT_OFFHAND; i++) {
			if (i >= inv.items.size() && i != Inventory.SLOT_OFFHAND) {
				continue; // armour slots
			}
			if (!includeHands && (i == inv.selected || i == Inventory.SLOT_OFFHAND)) {
				continue;
			}
			ItemStack s = inv.getItem(i);
			BackpackTier t = BackpackItem.tierOf(s);
			if (t != null && t.ordinal() > bestTier) {
				best = i;
				bestTier = t.ordinal();
			}
		}
		return best;
	}

	/** Open Backpack key: the one on your back, else one in your hands. */
	public static void openKey(ServerPlayer p) {
		int slot = bestSlot(p, false);
		if (slot < 0) {
			slot = bestSlot(p, true);
		}
		if (slot < 0 || !BackpackItem.open(p, slot)) {
			p.displayClientMessage(Component.translatable("message.projecthero.backpack.none").withStyle(ChatFormatting.GRAY), true);
		}
	}

	/** Re-syncs the backpack on this player's back when it changed (public for tests). */
	public static void updateWorn(ServerPlayer p) {
		int slot = p.isSpectator() ? -1 : bestSlot(p, false);
		ItemStack have = p.getAttached(WORN);
		if (slot < 0) {
			if (have != null) {
				p.removeAttached(WORN);
			}
			return;
		}
		ItemStack want = p.getInventory().getItem(slot).copyWithCount(1);
		want.remove(DataComponents.CONTAINER);
		if (have == null || !ItemStack.isSameItemSameComponents(have, want)) {
			p.setAttached(WORN, want);
		}
	}

	private static Item item(BackpackTier tier, Item.Properties props) {
		return Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id(tier.id() + "_backpack"), new BackpackItem(tier, props));
	}
}
