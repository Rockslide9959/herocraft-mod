package com.projecthero.mod.miraculous.entity;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** v0.16.6: the Miraculous core's entities -- the Kwami (one type, three variants). */
public final class MiraculousEntityTypes {
	/** Never saved (the core re-creates it), never summoned by hand; the client simulates its follow itself. */
	public static final EntityType<KwamiEntity> KWAMI = Registry.register(BuiltInRegistries.ENTITY_TYPE,
			ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id("kwami")),
			EntityType.Builder.<KwamiEntity>of(KwamiEntity::new, MobCategory.MISC)
					.sized(0.45f, 0.6f).fireImmune().noSave().noSummon().clientTrackingRange(10).updateInterval(2)
					.build("kwami"));

	private MiraculousEntityTypes() {
	}

	/** Class-load the registration (called from {@code MiraculousMod.initialize}). */
	public static void initialize() {
	}
}
