package com.herocraft.mod.network;

import com.herocraft.mod.HeroCraftMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client &rarr; server: the attack button was pressed or released while holding a firearm. The
 * server ({@link com.herocraft.mod.firearm.FirearmManager}) owns everything from here -- a semi-auto
 * weapon fires once on the press, an automatic weapon fires on its own timer while {@code pressed}
 * stays true. A modified client that spams this gains nothing: fire rate, ammo and cooldown are all
 * checked server-side.
 */
public record FirearmFirePayload(boolean pressed) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<FirearmFirePayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HeroCraftMod.MOD_ID, "firearm_fire"));

	public static final StreamCodec<RegistryFriendlyByteBuf, FirearmFirePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, FirearmFirePayload::pressed,
			FirearmFirePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
