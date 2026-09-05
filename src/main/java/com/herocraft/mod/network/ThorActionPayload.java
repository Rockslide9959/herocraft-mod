package com.herocraft.mod.network;

import com.herocraft.mod.HeroCraftMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server "I pressed/released a Thor keybind" packet. The server re-validates everything.
 * {@code state} is only meaningful for {@link Action#LIGHTNING_LASER}, which is a held-key,
 * continuous ability rather than a one-shot trigger -- it carries whether the key is currently
 * down (rising/falling edge), sent whenever that changes. Every other action ignores it.
 */
public record ThorActionPayload(Action action, boolean state) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ThorActionPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HeroCraftMod.MOD_ID, "thor_action"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ThorActionPayload> CODEC = StreamCodec.composite(
			StreamCodec.of(
					(buf, action) -> buf.writeEnum(action),
					buf -> buf.readEnum(Action.class)),
			ThorActionPayload::action,
			ByteBufCodecs.BOOL,
			ThorActionPayload::state,
			ThorActionPayload::new);

	public ThorActionPayload(Action action) {
		this(action, false);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public enum Action {
		TOGGLE_FLIGHT,
		LIGHTNING_STRIKE,
		CALL_HAMMER,
		LIGHTNING_LASER,
		THUNDERCLAP,
		STORM_CALL,
		CHAIN_LIGHTNING
	}
}
