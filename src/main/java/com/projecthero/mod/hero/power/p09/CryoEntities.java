package com.projecthero.mod.hero.power.p09;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** v0.16.5 Cryokinesis entity types (none are ever saved). Registered by {@link #initialize} from the handlers. */
public final class CryoEntities {
	/** Ice Bolt / Ice Shard / Shatter debris. */
	public static final EntityType<IceBoltEntity> ICE_BOLT = register("ice_bolt",
			EntityType.Builder.<IceBoltEntity>of(IceBoltEntity::new, MobCategory.MISC)
					.sized(0.3f, 0.3f)
					.clientTrackingRange(6)
					.updateInterval(5)
					.noSave()
					.build("ice_bolt"));

	/** The Frost pips + ice block around a frosted / frozen creature. */
	public static final EntityType<FrostShellEntity> FROST_SHELL = register("frost_shell",
			EntityType.Builder.<FrostShellEntity>of(FrostShellEntity::new, MobCategory.MISC)
					.sized(0.2f, 0.2f)
					.clientTrackingRange(8)
					.updateInterval(10)
					.noSave()
					.noSummon()
					.fireImmune()
					.build("frost_shell"));

	/** A Glacier Spikes / Absolute Zero spike. */
	public static final EntityType<IceSpikeEntity> ICE_SPIKE = register("ice_spike",
			EntityType.Builder.<IceSpikeEntity>of(IceSpikeEntity::new, MobCategory.MISC)
					.sized(0.8f, 2.0f)
					.clientTrackingRange(8)
					.updateInterval(20)
					.noSave()
					.noSummon()
					.fireImmune()
					.build("ice_spike"));

	/** v0.16.6 hold-R: the Ice Javelin. */
	public static final EntityType<IceJavelinEntity> ICE_JAVELIN = register("cryo_javelin",
			EntityType.Builder.<IceJavelinEntity>of(IceJavelinEntity::new, MobCategory.MISC)
					.sized(0.4f, 0.4f)
					.clientTrackingRange(8)
					.updateInterval(2)
					.noSave()
					.noSummon()
					.build("cryo_javelin"));

	/** v0.16.6 hold-G Frozen Ground / hold-Z Blizzard. */
	public static final EntityType<CryoFieldEntity> CRYO_FIELD = register("cryo_field",
			EntityType.Builder.<CryoFieldEntity>of(CryoFieldEntity::new, MobCategory.MISC)
					.sized(0.5f, 0.5f)
					.clientTrackingRange(10)
					.updateInterval(20)
					.noSave()
					.noSummon()
					.fireImmune()
					.build("cryo_field"));

	/** v0.16.6 hold-C: the Ice Golem ally. */
	public static final EntityType<IceGolemEntity> ICE_GOLEM = register("ice_golem",
			EntityType.Builder.<IceGolemEntity>of(IceGolemEntity::new, MobCategory.MISC)
					.sized(1.4f, 2.7f)
					.clientTrackingRange(10)
					.noSave()
					.noSummon()
					.build("ice_golem"));

	private CryoEntities() {
	}

	public static void initialize() {
		net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(ICE_GOLEM,
				IceGolemEntity.createAttributes());
	}

	private static <T extends Entity> EntityType<T> register(String path, EntityType<T> type) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id(path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
	}
}
