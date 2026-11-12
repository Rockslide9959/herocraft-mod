package com.projecthero.mod.doctorfate;

import java.lang.reflect.Method;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3 Doctor Fate: who Nabu calls Chaos and who he calls Order. Client-safe (the Pull drift and Sight of Nabu run on the
 * client too).
 * <ul>
 *   <li><b>Chaos</b>: Nether mobs, the undead, End mobs, anything in {@code #projecthero:chaos}, and Raven players at Rage.</li>
 *   <li><b>Order</b>: villagers, golems, tamed animals and other Helmet wearers. Nabu won't let you hurt villagers, golems or
 *   tamed animals once he has a say (Override and up).</li>
 * </ul>
 */
public final class DoctorFateChaos {
	/** Extra creatures of Chaos (other mods, our bosses): {@code data/projecthero/tags/entity_type/chaos.json}. */
	public static final TagKey<EntityType<?>> CHAOS_TAG = TagKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id("chaos"));
	/** A server-side entity tag (scoreboard tag) that marks anything as Chaos for Nabu, e.g. {@code /tag @s add projecthero.chaos}. */
	public static final String CHAOS_ENTITY_TAG = "projecthero.chaos";

	private static volatile boolean ravenLooked;
	private static volatile Method ravenRage;

	private DoctorFateChaos() {
	}

	public static boolean isChaos(Entity e) {
		if (!(e instanceof LivingEntity) || !e.isAlive()) {
			return false;
		}
		if (e instanceof Player p) {
			return ravenAtRage(p);
		}
		EntityType<?> type = e.getType();
		if (type.is(EntityTypeTags.UNDEAD) || type.is(CHAOS_TAG) || e.getTags().contains(CHAOS_ENTITY_TAG)) {
			return true;
		}
		return type == EntityType.BLAZE || type == EntityType.GHAST || type == EntityType.MAGMA_CUBE || type == EntityType.PIGLIN
				|| type == EntityType.PIGLIN_BRUTE || type == EntityType.HOGLIN || type == EntityType.STRIDER
				|| type == EntityType.ENDERMAN || type == EntityType.ENDERMITE || type == EntityType.SHULKER || type == EntityType.ENDER_DRAGON;
	}

	/** Villagers, golems and tamed animals: Nabu protects them (from Override on). */
	public static boolean isProtected(Entity e) {
		return e instanceof AbstractVillager || e instanceof AbstractGolem || (e instanceof TamableAnimal t && t.isTame());
	}

	/** Order, for Sight of Nabu's gold: the protected ones and other Helmet wearers. */
	public static boolean isOrder(Entity e) {
		return isProtected(e) || (e instanceof Player p && DoctorFate.wearsHelmet(p));
	}

	/**
	 * Raven at Rage counts as Chaos. Raven is built in parallel, so this never links against her classes: it looks once for a
	 * static {@code boolean inRage(Player)} on {@code com.projecthero.mod.raven.Raven} (TODO: switch to a direct call once Raven
	 * is merged) and otherwise falls back to the {@link #CHAOS_ENTITY_TAG} tag.
	 */
	static boolean ravenAtRage(Player p) {
		if (p.getTags().contains(CHAOS_ENTITY_TAG)) {
			return true;
		}
		if (!ravenLooked) {
			ravenLooked = true;
			try {
				Class<?> c = Class.forName("com.projecthero.mod.raven.Raven");
				Method m = c.getMethod("inRage", Player.class);
				if (m.getReturnType() == boolean.class && java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
					ravenRage = m;
				}
			} catch (ReflectiveOperationException | LinkageError ignored) {
				ravenRage = null;
			}
		}
		Method m = ravenRage;
		if (m == null) {
			return false;
		}
		try {
			return (boolean) m.invoke(null, p);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}
}
