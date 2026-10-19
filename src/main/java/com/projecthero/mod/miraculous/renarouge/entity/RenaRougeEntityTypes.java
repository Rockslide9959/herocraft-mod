package com.projecthero.mod.miraculous.renarouge.entity;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * v0.16.6: Rena Rouge's entities -- the illusions of her (decoy, Double, Mirage copies, feint), the thrown Flute, the
 * illusion wall and the Mirage bubble. None of them is ever saved ({@code noSave}): an illusion never outlives the
 * session that made it.
 */
public final class RenaRougeEntityTypes {
	public static final EntityType<RenaIllusionEntity> ILLUSION = register("rena_rouge_illusion",
			EntityType.Builder.<RenaIllusionEntity>of(RenaIllusionEntity::new, MobCategory.MISC)
					.sized(0.6f, 1.8f).eyeHeight(1.62f).noSave().noSummon().clientTrackingRange(10).updateInterval(2)
					.build("rena_rouge_illusion"));
	public static final EntityType<FluteBoomerangEntity> FLUTE = register("rena_rouge_flute",
			EntityType.Builder.<FluteBoomerangEntity>of(FluteBoomerangEntity::new, MobCategory.MISC)
					.sized(0.8f, 0.4f).noSave().noSummon().clientTrackingRange(8).updateInterval(1)
					.build("rena_rouge_flute"));
	public static final EntityType<FakeWallEntity> FAKE_WALL = register("rena_rouge_fake_wall",
			EntityType.Builder.<FakeWallEntity>of(FakeWallEntity::new, MobCategory.MISC)
					.sized(1.0f, 1.0f).noSave().noSummon().clientTrackingRange(10).updateInterval(20)
					.build("rena_rouge_fake_wall"));
	public static final EntityType<MirageBubbleEntity> BUBBLE = register("rena_rouge_bubble",
			EntityType.Builder.<MirageBubbleEntity>of(MirageBubbleEntity::new, MobCategory.MISC)
					.sized(0.5f, 0.5f).noSave().noSummon().clientTrackingRange(10).updateInterval(1)
					.build("rena_rouge_bubble"));
	/** v0.16.7: the Army of Foxes. */
	public static final EntityType<RenaFoxEntity> FOX = register("rena_rouge_fox",
			EntityType.Builder.<RenaFoxEntity>of(RenaFoxEntity::new, MobCategory.MISC)
					.sized(0.6f, 0.7f).eyeHeight(0.4f).noSave().noSummon().clientTrackingRange(10).updateInterval(2)
					.build("rena_rouge_fox"));
	/** v0.16.7: the big visuals of the Mirage wheel's illusions (drawn client-side). */
	public static final EntityType<MirageFxEntity> MIRAGE_FX = register("rena_rouge_mirage_fx",
			EntityType.Builder.<MirageFxEntity>of(MirageFxEntity::new, MobCategory.MISC)
					.sized(0.5f, 0.5f).noSave().noSummon().clientTrackingRange(12).updateInterval(2)
					.build("rena_rouge_mirage_fx"));

	private RenaRougeEntityTypes() {
	}

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(ILLUSION, RenaIllusionEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(FOX, RenaFoxEntity.createAttributes());
	}

	private static <T extends Entity> EntityType<T> register(String path, EntityType<T> type) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id(path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
	}
}
