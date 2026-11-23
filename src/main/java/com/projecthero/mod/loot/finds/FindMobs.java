package com.projecthero.mod.loot.finds;

import com.projecthero.mod.darkseid.entity.DarkseidEntityTypes;
import com.projecthero.mod.darkseid.entity.ParademonEntity;
import com.projecthero.mod.syndicate.SyndicateEntityTypes;
import com.projecthero.mod.syndicate.entity.SyndicateCriminal;
import com.projecthero.mod.syndicate.entity.SyndicateGunman;
import com.projecthero.mod.syndicate.entity.SyndicateSkin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * v0.17.7 small world places: the few existing mobs that loiter at some of them -- Parademons by a crashed pod, Syndicate
 * smugglers in their den. All existing player-model / existing-body mobs, persistent, tagged {@link #TAG} (not the
 * Tier 2 site-guard tag: these places have no alarm and nobody counts them as guards), held near where they stand.
 * Works on a {@code WorldGenLevel} (the pieces) and a {@code ServerLevel} (tests, harness). No static state.
 */
public final class FindMobs {
	public static final String TAG = "projecthero.find_mob";

	public enum Kind {
		PARADEMON, PARADEMON_RANGED, SMUGGLER, SMUGGLER_PISTOL, SMUGGLER_SHOTGUN
	}

	private FindMobs() {
	}

	public static Mob spawn(ServerLevelAccessor level, Kind kind, BlockPos pos, float yaw, int leash) {
		EntityType<? extends Mob> type = switch (kind) {
			case PARADEMON, PARADEMON_RANGED -> DarkseidEntityTypes.PARADEMON;
			case SMUGGLER -> SyndicateEntityTypes.THUG;
			case SMUGGLER_PISTOL, SMUGGLER_SHOTGUN -> SyndicateEntityTypes.GUNMAN;
		};
		Mob mob = type.create(level.getLevel());
		if (mob == null) {
			return null;
		}
		mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0f);
		mob.setYHeadRot(yaw);
		mob.setYBodyRot(yaw);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.STRUCTURE, null);
		switch (kind) {
			case PARADEMON -> ((ParademonEntity) mob).setup(ParademonEntity.Variant.STANDARD, null);
			case PARADEMON_RANGED -> ((ParademonEntity) mob).setup(ParademonEntity.Variant.RANGED, null);
			case SMUGGLER -> ((SyndicateCriminal) mob).setSkin(Math.floorMod(pos.getX() + pos.getZ(), 2) == 0 ? SyndicateSkin.HOOD : SyndicateSkin.MASKED);
			case SMUGGLER_PISTOL -> ((SyndicateGunman) mob).setKind(SyndicateGunman.Kind.PISTOL);
			case SMUGGLER_SHOTGUN -> ((SyndicateGunman) mob).setKind(SyndicateGunman.Kind.SHOTGUN);
		}
		mob.setPersistenceRequired();
		mob.addTag(TAG);
		if (leash > 0) {
			mob.restrictTo(pos, leash);
		}
		level.addFreshEntityWithPassengers(mob);
		return mob;
	}
}
