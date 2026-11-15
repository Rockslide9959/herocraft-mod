package com.projecthero.mod.hideout;

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
 * v0.17.5: loot condition {@code projecthero:near_hideout} -- passes when the loot's origin is within {@code radius}
 * blocks of a <b>cleared</b> hideout of {@code faction}. The Tier 3 vault's artifact sub-table
 * ({@code chests/vault_tier3_artifacts}) uses it so each hideout's vault can only roll its own faction's artifact.
 * <pre>{"condition": "projecthero:near_hideout", "faction": "ultron", "radius": 32}</pre>
 */
public record NearHideoutCondition(HideoutFaction faction, double radius) implements LootItemCondition {
	public static final MapCodec<NearHideoutCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			HideoutFaction.CODEC.fieldOf("faction").forGetter(NearHideoutCondition::faction),
			Codec.DOUBLE.optionalFieldOf("radius", 32.0).forGetter(NearHideoutCondition::radius)).apply(i, NearHideoutCondition::new));

	public static final LootItemConditionType TYPE = Registry.register(BuiltInRegistries.LOOT_CONDITION_TYPE,
			ProjectHeroMod.id("near_hideout"), new LootItemConditionType(CODEC));

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
		return HideoutState.get(ctx.getLevel()).clearedNear(faction, BlockPos.containing(origin), radius) != null;
	}
}
