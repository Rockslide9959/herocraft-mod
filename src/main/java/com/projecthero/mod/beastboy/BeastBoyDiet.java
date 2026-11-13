package com.projecthero.mod.beastboy;

import java.util.List;
import java.util.Set;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * v0.17.3 Beast Boy is vegetarian: he can't eat meat or fish of any kind, in any form (human too). Checked when he tries to use the
 * item (the start of eating), on both sides, so the eating animation never starts. Meat is anything in the conventional
 * {@code c:foods/*meat*} / {@code c:foods/*fish*} tags (so other mods' meats count), vanilla's {@code #minecraft:meat} and
 * {@code #minecraft:fishes}, plus a list of vanilla foods those miss (rabbit stew, rotten flesh, spider eye...).
 */
public final class BeastBoyDiet {
	private BeastBoyDiet() {
	}

	private static TagKey<Item> c(String path) {
		return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", path));
	}

	/** Conventional food tags (1.21.1 Fabric: singular ones; the plural ones are the old names, still filled by some mods). */
	private static final List<TagKey<Item>> MEAT_TAGS = List.of(c("foods/raw_meat"), c("foods/cooked_meat"), c("foods/raw_fish"),
			c("foods/cooked_fish"), c("foods/raw_meats"), c("foods/cooked_meats"), c("foods/raw_fishes"), c("foods/cooked_fishes"),
			ItemTags.MEAT, ItemTags.FISHES);

	/** Vanilla meats and fish dishes, listed in case a pack strips the tags. */
	private static final Set<Item> MEAT_ITEMS = Set.of(Items.BEEF, Items.COOKED_BEEF, Items.PORKCHOP, Items.COOKED_PORKCHOP, Items.CHICKEN,
			Items.COOKED_CHICKEN, Items.MUTTON, Items.COOKED_MUTTON, Items.RABBIT, Items.COOKED_RABBIT, Items.RABBIT_STEW, Items.ROTTEN_FLESH,
			Items.COD, Items.COOKED_COD, Items.SALMON, Items.COOKED_SALMON, Items.TROPICAL_FISH, Items.PUFFERFISH, Items.SPIDER_EYE);

	/** True for meat or fish (any mod's, via the tags). */
	public static boolean isMeat(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		if (MEAT_ITEMS.contains(stack.getItem())) {
			return true;
		}
		for (TagKey<Item> tag : MEAT_TAGS) {
			if (stack.is(tag)) {
				return true;
			}
		}
		return false;
	}

	/** True when {@code player} (Beast Boy, any form) may not eat {@code stack}; tells him why on the server. */
	public static boolean refuses(Player player, ItemStack stack) {
		if (!BeastBoy.hasPower(player) || !isMeat(stack)) {
			return false;
		}
		if (player instanceof ServerPlayer sp) {
			BeastBoy.say(sp, "message.projecthero.beast_boy.no_meat", ChatFormatting.GREEN);
		}
		return true;
	}
}
