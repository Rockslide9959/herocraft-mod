package com.projecthero.mod.vaultdungeon;

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
 * v0.17.5: loot condition {@code projecthero:near_vault_dungeon} -- passes when the loot's origin is within
 * {@code radius} blocks of a <b>cleared</b> Vault dungeon's anchor. The Tier 4 vault's artifact sub-table
 * ({@code chests/vault_tier4_artifacts}) uses it so the Vault's three artifacts only roll in the Vault itself.
 * <pre>{"condition": "projecthero:near_vault_dungeon", "radius": 32}</pre>
 */
public record NearVaultCondition(double radius) implements LootItemCondition {
	public static final MapCodec<NearVaultCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.DOUBLE.optionalFieldOf("radius", 32.0).forGetter(NearVaultCondition::radius)).apply(i, NearVaultCondition::new));

	public static final LootItemConditionType TYPE = Registry.register(BuiltInRegistries.LOOT_CONDITION_TYPE,
			ProjectHeroMod.id("near_vault_dungeon"), new LootItemConditionType(CODEC));

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
		return VaultDungeonState.get(ctx.getLevel()).clearedNear(BlockPos.containing(origin), radius) != null;
	}
}
