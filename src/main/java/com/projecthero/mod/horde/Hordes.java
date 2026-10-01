package com.projecthero.mod.horde;

import java.util.UUID;

import com.projecthero.mod.event.EventInstance;
import com.projecthero.mod.event.EventManager;
import com.projecthero.mod.event.EventTypes;
import com.projecthero.mod.horde.entity.HordeEntityTypes;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * v0.14.12: the Horde blocks -- one entry point, called from {@code ProjectHeroMod.onInitialize}: the mobs, the
 * blocks, the three event types and the "a fighter died" hook. See {@link HordeRaid}.
 */
public final class Hordes {
	private Hordes() {
	}

	public static void initialize() {
		com.projecthero.mod.horde.entity.skeleton.SkeletonHordeEntityTypes.initialize(); // v0.14.16: the Skeleton Horde's own skeletons
		HordeEntityTypes.initialize();
		HordeBlocks.initialize();
		for (HordeKind kind : HordeKind.values()) {
			EventTypes.register(kind.typeId(), (UUID id) -> new HordeRaid(id, kind));
		}
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p) {
				for (EventInstance e : EventManager.active(p.server)) {
					if (e instanceof HordeRaid raid && raid.isFighter(p.getUUID())) {
						raid.onFighterDied(p.serverLevel(), p);
					}
				}
			}
		});
	}

	/** Starts {@code kind}'s horde on the block at {@code pos}. False if one is already running nearby. */
	public static boolean start(ServerLevel level, BlockPos pos, HordeKind kind) {
		HordeRaid raid = new HordeRaid(UUID.randomUUID(), kind);
		if (!EventManager.start(level, raid, pos)) {
			return false;
		}
		level.setBlock(pos, level.getBlockState(pos).setValue(HordeBlock.ACTIVE, true), 3);
		return true;
	}
}
