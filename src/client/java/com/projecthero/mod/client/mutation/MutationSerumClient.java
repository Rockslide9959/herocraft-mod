package com.projecthero.mod.client.mutation;

import java.util.Map;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.grave.TrophyHeads;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.item.HeroPackItems;
import com.projecthero.mod.hero.mutation.ModSerums;

import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.object.builder.v1.client.model.FabricModelPredicateProviderRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

/**
 * v0.16.7: the mutation serums (vanilla {@code minecraft:potion} stacks holding a {@link ModSerums} potion) and the
 * per-power reagents get their own glassware icons instead of the plain purple vanilla bottle / shared purple flask.
 * <ul>
 *   <li>{@code projecthero:mutation_serum} item predicate on {@code minecraft:potion} = 1 for a mod serum; the override
 *       in {@code assets/minecraft/models/item/potion.json} then swaps in {@code projecthero:item/mutation_serum}
 *       (layer0 = greyscale liquid, tinted; layer1 = glass / cap / label, untinted).</li>
 *   <li>A potion colour provider that reproduces vanilla's exactly, except that a mod serum's liquid takes its POWER's
 *       colour (they all share the one Unstable Mutation effect, so vanilla painted every serum the same purple).</li>
 *   <li>The reagents' liquid layer is tinted the same per-power colour.</li>
 * </ul>
 * Colours are kept in sync with {@code scratchpad/gen_serums_v0167.js} (the preview sheet).
 */
public final class MutationSerumClient {
	/** Short power name ({@link ModSerums#shortName}) -> liquid colour. Others fall back to their category colour. */
	private static final Map<String, Integer> COLORS = Map.ofEntries(
			Map.entry("super_strength", 0xE0482B),
			Map.entry("laser_vision", 0xFF2F5A),
			Map.entry("flight", 0x5FB0FF),
			Map.entry("super_speed", 0xFFD52A),
			Map.entry("geokinesis", 0xA8743C),
			Map.entry("pyrokinesis", 0xFF8A1F),
			Map.entry("cryokinesis", 0xC8FAFF),
			Map.entry("teleportation", 0xB05CFF),
			Map.entry("super_regeneration", 0x4CE06A),
			Map.entry("elasticity", 0xFF6FD0),
			Map.entry("wind_manipulation", 0xB8F0D8),
			Map.entry("water_manipulation", 0x2F5FFF));

	private MutationSerumClient() {
	}

	public static void initialize() {
		FabricModelPredicateProviderRegistry.register(Items.POTION, ProjectHeroMod.id("mutation_serum"),
				(stack, level, entity, seed) -> serumPower(stack) != null ? 1.0f : 0.0f);

		ColorProviderRegistry.ITEM.register((stack, tintIndex) -> {
			if (tintIndex > 0) {
				return -1;
			}
			Power power = serumPower(stack);
			if (power != null) {
				return FastColor.ARGB32.opaque(color(power));
			}
			// vanilla's own potion tint (ItemColors.createDefault), unchanged for every other potion
			return FastColor.ARGB32.opaque(stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).getColor());
		}, Items.POTION);

		for (Power power : Powers.all()) {
			Item reagent = HeroPackItems.reagent(power);
			if (reagent == null) {
				continue;
			}
			int c = FastColor.ARGB32.opaque(color(power));
			ColorProviderRegistry.ITEM.register((stack, tintIndex) -> tintIndex == 0 ? c : -1, reagent);
		}
	}

	/** The power whose mutation serum {@code stack} is, or null. */
	public static Power serumPower(ItemStack stack) {
		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		if (contents == null || contents.potion().isEmpty()) {
			return null;
		}
		return ModSerums.powerOf(contents.potion().get());
	}

	public static int color(Power power) {
		Integer c = COLORS.get(ModSerums.shortName(power));
		return c != null ? c : TrophyHeads.categoryColor(power.category());
	}
}
