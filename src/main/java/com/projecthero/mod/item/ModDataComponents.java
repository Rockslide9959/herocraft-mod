package com.projecthero.mod.item;

import java.util.UUID;

import com.projecthero.mod.ProjectHeroMod;

import com.mojang.serialization.Codec;

import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;

/**
 * Custom item data components.
 *
 * <p>{@link #BOUND_OWNER} persists which player's summon key an individual Mjolnir stack answers
 * to (set via shift-right-click -- see {@link com.projecthero.mod.item.MjolnirItem}), independent of
 * {@code Projectile}'s transient per-entity owner field, which only exists while a specific
 * throw/drop is actually in flight.
 *
 * <p>{@link #HAMMER_ID} and {@link #HAMMER_GENERATION} together are the hammer's <em>identity</em>,
 * and they are what makes recall-from-an-unloaded-chunk safe. See
 * {@link com.projecthero.mod.hammer.MjolnirRegistry} for the full scheme; in short, the id names one
 * specific hammer across item stacks, entities, dimensions and server restarts, and the generation
 * counter lets a reconstructed hammer invalidate every older physical copy of itself on sight, so
 * "original + reconstructed" can never coexist.
 */
public final class ModDataComponents {
	public static final DataComponentType<UUID> BOUND_OWNER = register("bound_owner",
			DataComponentType.<UUID>builder()
					.persistent(UUIDUtil.CODEC)
					.networkSynchronized(UUIDUtil.STREAM_CODEC)
					.build());

	/**
	 * The display name of {@link #BOUND_OWNER}, captured at bind time. A UUID is the identity that
	 * matters, but it's meaningless to read -- and a client rendering the tooltip has no way to look
	 * a UUID back up to a name, so the name has to travel with the stack for "Bound to: Thor" to be
	 * renderable at all. Purely cosmetic: nothing ever compares against it.
	 */
	public static final DataComponentType<String> BOUND_OWNER_NAME = register("bound_owner_name",
			DataComponentType.<String>builder()
					.persistent(Codec.STRING)
					.networkSynchronized(ByteBufCodecs.STRING_UTF8)
					.build());

	/**
	 * Stable per-hammer identity. Assigned the first time the server sees a Mjolnir stack anywhere
	 * (inventory tick, entity tick, or bind) and never changed afterward, so the same hammer can be
	 * followed from hand to chest to thrown entity to an unloaded chunk and back.
	 */
	public static final DataComponentType<UUID> HAMMER_ID = register("hammer_id",
			DataComponentType.<UUID>builder()
					.persistent(UUIDUtil.CODEC)
					.networkSynchronized(UUIDUtil.STREAM_CODEC)
					.build());

	/**
	 * Duplication guard. Every physical copy of a hammer carries the generation it was created at;
	 * the authoritative current generation lives in the registry. A recall that has to
	 * <em>reconstruct</em> a hammer (because the real one is in an unloaded chunk or another
	 * dimension) bumps the generation, which instantly turns every older copy into a stale ghost --
	 * they delete themselves the moment they are ticked again. See
	 * {@link com.projecthero.mod.hammer.MjolnirRegistry#isStale}.
	 */
	public static final DataComponentType<Integer> HAMMER_GENERATION = register("hammer_generation",
			DataComponentType.<Integer>builder()
					.persistent(Codec.INT)
					.networkSynchronized(ByteBufCodecs.VAR_INT)
					.build());

	/**
	 * Suit charge carried on an Iron Man armour {@link net.minecraft.world.item.ItemStack} while it is
	 * out of a suit -- in an inventory or on a Suit Platform. Stamped when a suit is taken off / stored,
	 * read back when it is put on / deployed, so a suit's energy travels with the armour instead of
	 * resetting to zero (spec "changes 9"). Absent means "assume a full charge" (a freshly fabricated
	 * suit ships charged).
	 */
	public static final DataComponentType<Float> SUIT_ENERGY = register("suit_energy",
			DataComponentType.<Float>builder()
					.persistent(Codec.FLOAT)
					.networkSynchronized(ByteBufCodecs.FLOAT)
					.build());

	/** Companion to {@link #SUIT_ENERGY}: suit integrity (0..100). Absent means 100. */
	public static final DataComponentType<Float> SUIT_INTEGRITY = register("suit_integrity",
			DataComponentType.<Float>builder()
					.persistent(Codec.FLOAT)
					.networkSynchronized(ByteBufCodecs.FLOAT)
					.build());

	/**
	 * Firearm magazine: rounds currently loaded (0..the weapon's magazine size). Absent means "full"
	 * so a freshly crafted or {@code /give}n gun is ready to fire. Persistent + network-synced so the
	 * HUD reads it with no round trip and the count survives drop / relog / inventory transfer.
	 */
	public static final DataComponentType<Integer> FIREARM_MAGAZINE = register("firearm_magazine",
			DataComponentType.<Integer>builder()
					.persistent(Codec.INT)
					.networkSynchronized(ByteBufCodecs.VAR_INT)
					.build());

	/**
	 * Absolute game time at which the in-progress reload (or, for the shotgun, the current shell)
	 * finishes. {@code 0} / absent means "not reloading". Server-authoritative; synced so the client
	 * can draw the reload bar.
	 */
	public static final DataComponentType<Long> FIREARM_RELOAD_END = register("firearm_reload_end",
			DataComponentType.<Long>builder()
					.persistent(Codec.LONG)
					.networkSynchronized(ByteBufCodecs.VAR_LONG)
					.build());

	/**
	 * Absolute game time of this weapon's last shot -- gates fire rate and the pump / bolt cycle.
	 * Server-authoritative; synced only so the client can grey the HUD during a cycle.
	 */
	public static final DataComponentType<Long> FIREARM_LAST_FIRED = register("firearm_last_fired",
			DataComponentType.<Long>builder()
					.persistent(Codec.LONG)
					.networkSynchronized(ByteBufCodecs.VAR_LONG)
					.build());

	private ModDataComponents() {
	}

	public static void initialize() {
		// Classes are loaded (and their component types registered) simply by referencing this
		// class; this method exists so ProjectHeroMod has an explicit, readable init call.
	}

	private static <T> DataComponentType<T> register(String path, DataComponentType<T> type) {
		ResourceKey<DataComponentType<?>> key = ResourceKey.create(Registries.DATA_COMPONENT_TYPE, ProjectHeroMod.id(path));
		return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, key, type);
	}
}
