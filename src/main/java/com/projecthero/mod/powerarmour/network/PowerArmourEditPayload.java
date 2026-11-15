package com.projecthero.mod.powerarmour.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * v0.17.5 Power Armour, client -&gt; server: an Armour Editor button. {@code action}: {@link #PAINT_PRIMARY} /
 * {@link #PAINT_SECONDARY} / {@link #PAINT_GLOW} ({@code value} the RGB, or -1 for the tier's own colour) or {@link #PICK_UP}.
 * The server re-checks that the sender has this frame's editor open.
 */
public record PowerArmourEditPayload(int frameId, int action, int value) implements CustomPacketPayload {
	public static final int PAINT_PRIMARY = 0, PAINT_SECONDARY = 1, PAINT_GLOW = 2, PICK_UP = 3;

	public static final Type<PowerArmourEditPayload> TYPE = new Type<>(ProjectHeroMod.id("power_armour_edit"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PowerArmourEditPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, PowerArmourEditPayload::frameId,
			ByteBufCodecs.VAR_INT, PowerArmourEditPayload::action,
			ByteBufCodecs.INT, PowerArmourEditPayload::value,
			PowerArmourEditPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
