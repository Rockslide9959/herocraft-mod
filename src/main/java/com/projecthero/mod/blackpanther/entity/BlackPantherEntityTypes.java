package com.projecthero.mod.blackpanther.entity;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** v0.17.3: Black Panther's thrown things -- the Vibranium Dagger (and the Throwing Knife, the same blade). */
public final class BlackPantherEntityTypes {
	public static final EntityType<VibraniumDaggerEntity> VIBRANIUM_DAGGER = register("vibranium_dagger",
			EntityType.Builder.<VibraniumDaggerEntity>of(VibraniumDaggerEntity::new, MobCategory.MISC)
					.sized(0.4f, 0.3f).noSave().noSummon().fireImmune().clientTrackingRange(8).updateInterval(1).build("vibranium_dagger"));
	// v0.17.7: the Kimoyo bead entity went with Kimoyo Flash (removed from the suited kit); it was never saved (noSave)

	private BlackPantherEntityTypes() {
	}

	public static void initialize() {
	}

	private static <T extends Entity> EntityType<T> register(String path, EntityType<T> type) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id(path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
	}
}
