package com.projecthero.mod.temple.item;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.loot.HeroRarity;
import com.projecthero.mod.temple.block.TempleBlocks;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;

/** v0.17.7: the Ancient Temple's items -- the four weights of the Hall of Trials and its artifact, the Bound King's Ankh. */
public final class TempleItems {
	public static final String[] WEIGHT_NAMES = { "weight_feather", "weight_ibis", "weight_jackal", "weight_lion" };
	private static final Item[] WEIGHTS = new Item[5];
	public static Item BOUND_KINGS_ANKH;

	private TempleItems() {
	}

	public static void initialize() {
		for (int w = 1; w <= 4; w++) {
			WEIGHTS[w] = item(WEIGHT_NAMES[w - 1], new TempleWeightItem(w, HeroRarity.props(HeroRarity.UNCOMMON).stacksTo(16)));
		}
		BOUND_KINGS_ANKH = item("bound_kings_ankh", new BoundKingsAnkhItem(HeroRarity.props(HeroRarity.LEGENDARY).stacksTo(1).fireResistant()));
	}

	/** The ankh's trigger: a death about to happen. */
	public static void registerEvents() {
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer player && BoundKingsAnkhItem.tryCheatDeath(player, source)) {
				return false;
			}
			return true;
		});
	}

	/** The weight item of value 1-4. */
	public static Item weight(int w) {
		return WEIGHTS[Math.max(1, Math.min(4, w))];
	}

	public static Item[] weights() {
		return new Item[] { WEIGHTS[1], WEIGHTS[2], WEIGHTS[3], WEIGHTS[4] };
	}

	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(BOUND_KINGS_ANKH);
		for (Item i : weights()) {
			output.accept(i);
		}
		TempleBlocks.addToCreativeTab(output);
	}

	private static Item item(String path, Item item) {
		return Registry.register(BuiltInRegistries.ITEM, ResourceKey.create(Registries.ITEM, ProjectHeroMod.id(path)), item);
	}
}
