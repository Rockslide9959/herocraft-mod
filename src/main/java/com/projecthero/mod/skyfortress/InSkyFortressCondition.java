package com.projecthero.mod.skyfortress;

import java.util.Set;

import com.mojang.serialization.MapCodec;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.alarm.AlarmZone;
import com.projecthero.mod.alarm.AlarmZones;

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
 * v0.17.7: loot condition {@code projecthero:in_sky_fortress} -- passes when the loot's origin lies inside a Sky Fortress
 * alarm zone ({@code AlarmZones}, profile {@link SkyFortressMod#PROFILE}) whose Admiral is no longer alive. The Tier 4
 * vault's artifact pool for Kestrel's Thruster uses it, so the artifact only rolls from the Fortress Vault itself.
 * <pre>{"condition": "projecthero:in_sky_fortress"}</pre>
 */
public record InSkyFortressCondition() implements LootItemCondition {
	public static final InSkyFortressCondition INSTANCE = new InSkyFortressCondition();
	public static final MapCodec<InSkyFortressCondition> CODEC = MapCodec.unit(INSTANCE);

	public static final LootItemConditionType TYPE = Registry.register(BuiltInRegistries.LOOT_CONDITION_TYPE,
			ProjectHeroMod.id("in_sky_fortress"), new LootItemConditionType(CODEC));

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
		BlockPos pos = BlockPos.containing(origin);
		AlarmZone z = AlarmZones.get(ctx.getLevel()).zoneAt(pos);
		return z != null && SkyFortressMod.PROFILE.equals(z.profileId) && SkyFortressClearing.guardianNear(ctx.getLevel(), pos) == null;
	}
}
