package com.projecthero.mod.doctorfate.helmet;

import java.util.UUID;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

/**
 * v0.17.4 (user: "when player presses H they get the doctor fate helmet item when removing the helmet..."): the Helmet of Fate is a
 * real thing again whenever it isn't on its owner's head.
 * <ul>
 *   <li><b>{@link HelmetOfFateItem}</b> ({@code projecthero:helmet_of_fate}): what H gives you when you take it off. Shift+right-click
 *   to put it on; right-click a block to set it down; Q drops it like any item (fireproof, never despawns).</li>
 *   <li><b>{@link FateHelmetEntity}</b>: the helmet set down on a block, hovering and glowing. Right-click picks it up.</li>
 *   <li><b>{@link #TAG}</b>: the owner and generation on every copy ({@link FateHelmetTag}), checked against
 *   {@link FateHelmetRegistry} so a summoned-away copy can never come back as a second helmet ({@link FateHelmetGuard}).</li>
 * </ul>
 * The summon itself (H with no helmet on) is {@code DoctorFateHelmet}.
 */
public final class FateHelmet {
	public static DataComponentType<FateHelmetTag> TAG;
	public static Item ITEM;
	public static EntityType<FateHelmetEntity> ENTITY;

	private FateHelmet() {
	}

	public static void initialize() {
		if (ITEM != null) {
			return;
		}
		TAG = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, ProjectHeroMod.id("fate_helmet"),
				DataComponentType.<FateHelmetTag>builder().persistent(FateHelmetTag.CODEC).networkSynchronized(FateHelmetTag.STREAM_CODEC).build());
		ITEM = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("helmet_of_fate"),
				new HelmetOfFateItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));
		ENTITY = Registry.register(BuiltInRegistries.ENTITY_TYPE, ProjectHeroMod.id("helmet_of_fate"),
				EntityType.Builder.<FateHelmetEntity>of(FateHelmetEntity::new, MobCategory.MISC).sized(0.6f, 0.6f).fireImmune()
						.clientTrackingRange(10).updateInterval(10).build("helmet_of_fate"));
		FateHelmetGuard.initialize();
	}

	public static boolean is(ItemStack stack) {
		return !stack.isEmpty() && stack.is(ITEM);
	}

	/** A fresh stack of {@code owner}'s helmet at generation {@code gen}. */
	public static ItemStack stack(ServerPlayer owner, int gen) {
		ItemStack s = new ItemStack(ITEM);
		s.set(TAG, new FateHelmetTag(owner.getUUID(), owner.getGameProfile().getName(), gen));
		return s;
	}

	/** This stack is a helmet belonging to {@code owner} (any generation). */
	public static boolean belongsTo(ItemStack stack, UUID owner) {
		if (!is(stack)) {
			return false;
		}
		FateHelmetTag t = stack.get(TAG);
		return t != null && t.owner().equals(owner);
	}

	/** This stack is {@code owner}'s helmet and not a ghost. */
	public static boolean isLive(FateHelmetRegistry registry, ItemStack stack, UUID owner) {
		return belongsTo(stack, owner) && !registry.isStale(stack);
	}
}
