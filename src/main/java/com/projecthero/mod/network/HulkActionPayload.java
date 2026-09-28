package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server Hulk requests that are not one of the six ability slots. Only requests -- the server checks the
 * Gamma power, the rage and the current state every time.
 *
 * <ul>
 *   <li>{@code TRANSFORM} -- H (let the Hulk out at 75+ rage).</li>
 *   <li>{@code CALM_START} -- N held for 2 s; {@code CALM_REPORT (a = ticks in rhythm, b = out of rhythm)} once a
 *       second from the breathing screen; {@code CALM_STOP} -- Esc.</li>
 *   <li>{@code CONTROL (a = key 1-4)} -- the movement key pressed while a keep-control prompt was showing.</li>
 * </ul>
 */
public record HulkActionPayload(Action action, int a, int b) implements CustomPacketPayload {
	public enum Action {
		TRANSFORM,
		CALM_START,
		CALM_REPORT,
		CALM_STOP,
		CONTROL
	}

	public HulkActionPayload(Action action) {
		this(action, 0, 0);
	}

	public static final CustomPacketPayload.Type<HulkActionPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "hulk_action"));

	public static final StreamCodec<RegistryFriendlyByteBuf, HulkActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.map(i -> Action.values()[Math.floorMod(i, Action.values().length)], Action::ordinal),
			HulkActionPayload::action,
			ByteBufCodecs.VAR_INT, HulkActionPayload::a,
			ByteBufCodecs.VAR_INT, HulkActionPayload::b,
			HulkActionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
