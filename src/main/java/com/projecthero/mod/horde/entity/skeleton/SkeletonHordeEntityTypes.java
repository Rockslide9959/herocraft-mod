package com.projecthero.mod.horde.entity.skeleton;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.item.ModCreativeTab;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

/**
 * v0.14.16: the Skeleton Horde's six skeletons ({@link SkeletonHordeRoster}), the Bone Bomber's {@link BlastArrow}, and
 * the six spawn eggs (added to the Superheroes tab after the Horde blocks). Kept apart from {@code HordeEntityTypes} so
 * each horde owns its own registrations. No natural spawns: only a horde (or a command / egg) makes them.
 */
public final class SkeletonHordeEntityTypes {
	public static final EntityType<BoneRunner> BONE_RUNNER = register("bone_runner",
			EntityType.Builder.<BoneRunner>of(BoneRunner::new, MobCategory.MONSTER)
					.sized(0.6f, 1.99f).eyeHeight(1.74f).clientTrackingRange(8).build("bone_runner"));
	public static final EntityType<BoneKnight> BONE_KNIGHT = register("bone_knight",
			EntityType.Builder.<BoneKnight>of(BoneKnight::new, MobCategory.MONSTER)
					.sized(0.6f, 1.99f).eyeHeight(1.74f).clientTrackingRange(8).build("bone_knight"));
	public static final EntityType<BlightArcher> BLIGHT_ARCHER = register("blight_archer",
			EntityType.Builder.<BlightArcher>of(BlightArcher::new, MobCategory.MONSTER)
					.sized(0.6f, 1.99f).eyeHeight(1.74f).clientTrackingRange(8).build("blight_archer"));
	public static final EntityType<BoneBomber> BONE_BOMBER = register("bone_bomber",
			EntityType.Builder.<BoneBomber>of(BoneBomber::new, MobCategory.MONSTER)
					.sized(0.6f, 1.99f).eyeHeight(1.74f).clientTrackingRange(8).build("bone_bomber"));
	public static final EntityType<BoneBrute> BONE_BRUTE = register("bone_brute",
			EntityType.Builder.<BoneBrute>of(BoneBrute::new, MobCategory.MONSTER)
					.sized(0.6f, 1.99f).eyeHeight(1.74f).clientTrackingRange(10).build("bone_brute"));
	public static final EntityType<Necromancer> NECROMANCER = register("necromancer",
			EntityType.Builder.<Necromancer>of(Necromancer::new, MobCategory.MONSTER)
					.sized(0.6f, 1.99f).eyeHeight(1.74f).clientTrackingRange(8).build("necromancer"));

	public static final EntityType<BlastArrow> BLAST_ARROW = register("blast_arrow",
			EntityType.Builder.<BlastArrow>of(BlastArrow::new, MobCategory.MISC)
					.sized(0.5f, 0.5f).eyeHeight(0.13f).clientTrackingRange(4).updateInterval(20)
					// an arrow in flight when its chunk unloads is gone; saving it would leave stray bombs behind
					.noSave()
					.build("blast_arrow"));

	/** Every skeleton type above, in roster order (the tests walk this). */
	public static final List<EntityType<? extends HordeSkeleton>> VARIANTS = List.of(BONE_RUNNER, BONE_KNIGHT, BLIGHT_ARCHER,
			BONE_BOMBER, BONE_BRUTE, NECROMANCER);
	public static final List<Item> SPAWN_EGGS = new ArrayList<>();

	private SkeletonHordeEntityTypes() {
	}

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(BONE_RUNNER, BoneRunner.createAttributes());
		FabricDefaultAttributeRegistry.register(BONE_KNIGHT, BoneKnight.createAttributes());
		FabricDefaultAttributeRegistry.register(BLIGHT_ARCHER, BlightArcher.createAttributes());
		FabricDefaultAttributeRegistry.register(BONE_BOMBER, BoneBomber.createAttributes());
		FabricDefaultAttributeRegistry.register(BONE_BRUTE, BoneBrute.createAttributes());
		FabricDefaultAttributeRegistry.register(NECROMANCER, Necromancer.createAttributes());
		egg("bone_runner", BONE_RUNNER, 0xEDEBE4, 0xB02020);
		egg("bone_knight", BONE_KNIGHT, 0x6C6E78, 0x4A8CFF);
		egg("blight_archer", BLIGHT_ARCHER, 0x3E4A2C, 0x86FF50);
		egg("bone_bomber", BONE_BOMBER, 0x2A2420, 0xFF9020);
		egg("bone_brute", BONE_BRUTE, 0xD8C890, 0xFF3020);
		egg("necromancer", NECROMANCER, 0x2A1236, 0x82FFFF);
		ItemGroupEvents.modifyEntriesEvent(ModCreativeTab.SUPERHEROES_KEY).register(entries -> {
			for (Item egg : SPAWN_EGGS) {
				entries.accept(egg);
			}
		});
	}

	private static void egg(String name, EntityType<? extends Mob> type, int base, int spots) {
		SPAWN_EGGS.add(Registry.register(BuiltInRegistries.ITEM, ResourceKey.create(Registries.ITEM, ProjectHeroMod.id(name + "_spawn_egg")),
				new SpawnEggItem(type, base, spots, new Item.Properties())));
	}

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(String path, EntityType<T> type) {
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id(path)), type);
	}
}
