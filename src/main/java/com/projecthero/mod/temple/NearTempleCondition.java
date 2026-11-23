package com.projecthero.mod.temple;

import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7: loot condition {@code projecthero:near_ancient_temple} -- passes when the loot's origin is within
 * {@code radius} blocks of a <b>cleared</b> Ancient Temple's throne. The Tier 4 vault's artifact sub-table
 * ({@code chests/vault_tier4_artifacts}) uses it (the Bound King's Ankh pool, added in code by TempleMod) so the temple's artifact only rolls in the temple itself.
 * <pre>{"condition": "projecthero:near_ancient_temple", "radius": 32}</pre>
 */
public record NearTempleCondition(double radius) implements LootItemCondition {
	public static final MapCodec<NearTempleCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.DOUBLE.optionalFieldOf("radius", 32.0).forGetter(NearTempleCondition::radius)).apply(i, NearTempleCondition::new));

	public static final LootItemConditionType TYPE = Registry.register(BuiltInRegistries.LOOT_CONDITION_TYPE,
			ProjectHeroMod.id("near_ancient_temple"), new LootItemConditionType(CODEC));

	public static void initialize() {
	}

	@Override
	public LootItemConditionType getType() {
		return TYPE;
	}

	@Override
	public Set<LootContextParam<?>> getReferencedContextParams() {
		return Set.of(LootContextParams.ORIGIN);
	}

	@Override
	public boolean test(LootContext ctx) {
		Vec3 origin = ctx.getParamOrNull(LootContextParams.ORIGIN);
		if (origin == null) {
			return false;
		}
		return TempleState.get(ctx.getLevel()).clearedNear(BlockPos.containing(origin), radius) != null;
	}
}
