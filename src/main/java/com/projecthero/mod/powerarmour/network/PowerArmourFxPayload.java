package com.projecthero.mod.powerarmour.network;

import java.util.List;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * v0.17.5 Power Armour, server -&gt; client: an effect with a list of points. {@link #ORES}: block positions the Ore Scanner found
 * (x, y, z triples, for the wearer's visor); {@link #ARC}: a Tesla Coil chain (x, y, z of the muzzle then each target).
 */
public record PowerArmourFxPayload(int kind, int entityId, List<Double> data) implements CustomPacketPayload {
	public static final int ORES = 0, ARC = 1;

	public static final Type<PowerArmourFxPayload> TYPE = new Type<>(ProjectHeroMod.id("power_armour_fx"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PowerArmourFxPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, PowerArmourFxPayload::kind,
			ByteBufCodecs.VAR_INT, PowerArmourFxPayload::entityId,
			ByteBufCodecs.DOUBLE.apply(ByteBufCodecs.list(1024)), PowerArmourFxPayload::data,
			PowerArmourFxPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
