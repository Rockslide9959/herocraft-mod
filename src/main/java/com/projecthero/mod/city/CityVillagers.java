package com.projecthero.mod.city;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

/**
 * v0.16.2 City part B: the per-villager hook ({@code CityVillagerMixin}, end of {@code Villager.customServerAiStep}, so
 * only villagers with AI). Dispatches on profession; everything else returns at once.
 */
public final class CityVillagers {
	private CityVillagers() {
	}

	public static void tick(Villager v) {
		if (!(v.level() instanceof ServerLevel level)) {
			return;
		}
		if ((v.tickCount + v.getId()) % 100 == 0) {
			CityCops.syncStats(v);
		}
		VillagerProfession prof = v.getVillagerData().getProfession();
		if (prof == CityBlocks.CITY_COP) {
			CityCops.tick(v, level);
		} else if (prof == VillagerProfession.CLERIC) {
			CityServices.tickDoctor(v, level);
		} else if (prof == VillagerProfession.LEATHERWORKER) {
			CityServices.tickFirefighter(v, level);
		} else if (prof == VillagerProfession.LIBRARIAN) {
			CityServices.tickLibrarian(v, level);
		}
	}
}
