package com.projecthero.mod.ironman.suit;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.projecthero.mod.ironman.data.StarkPlatformRegistry;
import com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity;
import com.projecthero.mod.ironman.item.IronManArmorItem;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;

/**
 * v0.14.29 (agent D): putting armour back on a Suit Platform no matter where that platform is.
 *
 * <ul>
 *   <li>{@link #depositNow} loads the platform's chunk with an {@link IronManChunkTickets} ticket and racks the real
 *       stacks straight away (the send-home flight and the death return used to wait for the return queue's 5 s
 *       sweep, during which the picker could not see the suit at all).</li>
 *   <li>{@link #tagHome} / {@link #homeOf} remember on each piece which platform it was last called off, so "send it
 *       home for repair" goes back to <em>that</em> platform rather than merely the nearest one.</li>
 * </ul>
 */
public final class IronManPlatformReturn {
	/** Custom-data key on an armour stack: the platform it was last called off ({@code dim} + packed {@code pos}). */
	public static final String HOME_KEY = "projecthero_home_platform";

	private IronManPlatformReturn() {
	}

	/**
	 * Rack every stack in {@code stacks} on the platform at {@code at} now, loading its chunk if it has to. A piece whose
	 * slot is taken (or a different mark is racked) is dropped on top of the platform -- never lost. Returns false, with
	 * nothing consumed, if there is no platform there to take them (the caller then queues them as before); a registry
	 * record for a platform that is gone is dropped.
	 */
	public static boolean depositNow(MinecraftServer server, UUID owner, GlobalPos at, List<ItemStack> stacks) {
		ServerLevel level = server.getLevel(at.dimension());
		if (level == null) {
			return false;
		}
		IronManSuitPlatformBlockEntity be = IronManChunkTickets.loadPlatform(level, at.pos());
		if (be == null) {
			StarkPlatformRegistry.get(level).remove(level, at.pos());
			return false;
		}
		if (be.owner().isEmpty() && owner != null) {
			be.bindTo(owner);
		}
		BlockPos pos = at.pos();
		for (ItemStack s : stacks) {
			if (s.isEmpty()) {
				continue;
			}
			ItemStack one = s.copy();
			clearHome(one);
			if (!be.store(one)) {
				level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, one));
			}
		}
		return true;
	}

	// ---------------- "its own platform" ----------------

	/** Stamp {@code stack} with the platform it is leaving. */
	public static void tagHome(ItemStack stack, ServerLevel level, BlockPos platform) {
		if (!(stack.getItem() instanceof IronManArmorItem)) {
			return;
		}
		String dim = level.dimension().location().toString();
		long packed = platform.asLong();
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			CompoundTag home = new CompoundTag();
			home.putString("dim", dim);
			home.putLong("pos", packed);
			tag.put(HOME_KEY, home);
		});
	}

	/** The platform {@code stack} was last called off, if it carries one. */
	public static Optional<GlobalPos> homeOf(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null || !data.contains(HOME_KEY)) {
			return Optional.empty();
		}
		CompoundTag home = data.copyTag().getCompound(HOME_KEY);
		ResourceLocation dim = ResourceLocation.tryParse(home.getString("dim"));
		if (dim == null || !home.contains("pos")) {
			return Optional.empty();
		}
		return Optional.of(GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dim), BlockPos.of(home.getLong("pos"))));
	}

	/** Drop the home stamp (once the piece is racked again it has no need for it). */
	public static void clearHome(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data != null && data.contains(HOME_KEY)) {
			CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(HOME_KEY));
		}
	}
}
