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
 *   <li>{@code TRANSFORM} -- H pressed (v0.16.4: Banner starts holding toward the change; the Hulk asks to change back);
 *       {@code TRANSFORM_RELEASE} -- H let go (calls an unfinished hold off).</li>
 *   <li>{@code CONTROL (a = arrow 1 up, 2 left, 3 down, 4 right)} -- an arrow key pressed while the rampage warning shows.</li>
 *   <li>{@code ROAR} -- N as the Hulk (v0.16.4).</li>
 *   <li>{@code RING (a = 1 hit, 0 miss)} -- one Space press of the rampage's regain-control ring (v0.16.4).</li>
 *   <li>{@code MIND_MIRROR} -- H inside the Gamma origin's mind room: the Hulk comes through the mirror (v0.16.4).</li>
 *   <li>{@code MIND_SCREEN} -- the player walked the mind hall of a death rampage up to the screen and right-clicked it
 *       (v0.16.4; the server checks the rampage kind and the time).</li>
 * </ul>
 * v0.16.4: the calm-down actions are gone with calming (client and server always run the same jar -- the version handshake).
 */
public record HulkActionPayload(Action action, int a, int b) implements CustomPacketPayload {
	public enum Action {
		TRANSFORM,
		CONTROL,
		TRANSFORM_RELEASE,
		ROAR,
		RING,
		MIND_MIRROR,
		MIND_SCREEN
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
