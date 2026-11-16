package com.projecthero.mod.powerarmour;

import java.util.List;

import com.mojang.serialization.Codec;
import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.ItemStack;

/** v0.17.5 Power Armour: the player attachments and item components (kept in the feature package, not ModAttachments). */
public final class PowerArmourAttachments {
	/** The worn suit and the enter / exit sequence (persistent, synced to everyone). */
	public static final AttachmentType<PowerArmourState> STATE = AttachmentRegistry.create(ProjectHeroMod.id("power_armour_state"),
			builder -> builder.persistent(PowerArmourState.CODEC).syncWith(PowerArmourState.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/** The armour the player wore when they stepped in (head, chest, legs, feet), given back when they step out. Server only. */
	public static final AttachmentType<List<ItemStack>> STASH = AttachmentRegistry.create(ProjectHeroMod.id("power_armour_stash"),
			builder -> builder.persistent(ItemStack.OPTIONAL_CODEC.listOf()));

	/** A frame item's whole suit (tier comes from the item; see {@link PowerArmourFrameItem#suitOf}). */
	public static final DataComponentType<SuitData> SUIT = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			ProjectHeroMod.id("power_armour_suit"),
			DataComponentType.<SuitData>builder().persistent(SuitData.CODEC).networkSynchronized(SuitData.STREAM_CODEC).build());

	/** A power core's charge (energy units). */
	public static final DataComponentType<Float> CORE_CHARGE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			ProjectHeroMod.id("power_core_charge"),
			DataComponentType.<Float>builder().persistent(Codec.FLOAT).networkSynchronized(ByteBufCodecs.FLOAT).build());

	private PowerArmourAttachments() {
	}

	static void initialize() {
		// class load registers everything above
	}
}
