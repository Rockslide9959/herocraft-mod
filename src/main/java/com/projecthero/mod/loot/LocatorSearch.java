package com.projecthero.mod.loot;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mojang.datafixers.util.Pair;
import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * v0.17.5: where a Locator looks. Each place tier is a structure tag, {@code #projecthero:locator/tier2|3|4}
 * ({@code data/projecthero/tags/worldgen/structure/locator/}). Add a structure to a tier by listing its id in that tag
 * file -- nothing else is needed. The search is the vanilla "nearest map structure" ring search, {@link #RADIUS_CHUNKS}
 * chunks out.
 */
public final class LocatorSearch {
	public static final int RADIUS_CHUNKS = 100;

	/** A found place: its position (structure start, y from the generator) and structure id. */
	public record Found(BlockPos pos, ResourceLocation structure) {
	}

	@FunctionalInterface
	public interface Finder {
		Optional<Found> find(ServerLevel level, BlockPos origin, int tier);
	}

	/**
	 * The search the Locator uses. Swappable for gametests only (the gametest world is a flat preset with no mod
	 * structure placements); always restore {@link #DEFAULT_FINDER} afterwards.
	 */
	public static final Finder DEFAULT_FINDER = LocatorSearch::search;
	public static Finder finder = DEFAULT_FINDER;

	private LocatorSearch() {
	}

	public static TagKey<Structure> tag(int tier) {
		return TagKey.create(Registries.STRUCTURE, ProjectHeroMod.id("locator/tier" + LocatorTarget.clampTier(tier)));
	}

	/** The structure ids currently in a tier's tag (empty when the tag is empty / missing). */
	public static List<ResourceLocation> members(RegistryAccess access, int tier) {
		List<ResourceLocation> out = new ArrayList<>();
		Registry<Structure> reg = access.registryOrThrow(Registries.STRUCTURE);
		reg.getTag(tag(tier)).ifPresent(set -> set.forEach(h -> h.unwrapKey().map(ResourceKey::location).ifPresent(out::add)));
		return out;
	}

	/** The nearest structure of the tier's tag around {@code origin}, or empty (none in range / empty tag / structures off). */
	public static Optional<Found> search(ServerLevel level, BlockPos origin, int tier) {
		if (!level.getServer().getWorldData().worldGenOptions().generateStructures()) {
			return Optional.empty();
		}
		Registry<Structure> reg = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
		Optional<HolderSet.Named<Structure>> set = reg.getTag(tag(tier));
		if (set.isEmpty() || set.get().size() == 0) {
			return Optional.empty();
		}
		Pair<BlockPos, Holder<Structure>> hit = level.getChunkSource().getGenerator()
				.findNearestMapStructure(level, set.get(), origin, RADIUS_CHUNKS, false);
		if (hit == null) {
			return Optional.empty();
		}
		return hit.getSecond().unwrapKey().map(k -> new Found(hit.getFirst(), k.location()));
	}
}
