package com.projecthero.mod.omnitrix.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.17.1: client to server Omnitrix requests that are not ability slots, all while the watch is up except USE_AIR:
 * <ul>
 *   <li>SELECT -- pick {@code alien} (an id) directly;</li>
 *   <li>CYCLE -- the scroll wheel turned the dial ({@code alien} = "1" or "-1");</li>
 *   <li>SLAP -- a left / right click slapped the core down (transform);</li>
 *   <li>USE_AIR -- right-click on air with an empty hand while transformed (vanilla sends nothing for it);</li>
 *   <li>MASTER_PICK -- v0.17.2: an alien clicked on the Master Control alien wheel ({@code alien} = its id).</li>
 * </ul>
 */
public record OmnitrixActionPayload(Action action, String alien) implements CustomPacketPayload {
	public enum Action {
		SELECT, USE_AIR, CYCLE, SLAP, MASTER_PICK
	}

	public static final CustomPacketPayload.Type<OmnitrixActionPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "omnitrix_action"));

	public static final StreamCodec<RegistryFriendlyByteBuf, OmnitrixActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.map(i -> Action.values()[Math.floorMod(i, Action.values().length)], Action::ordinal),
			OmnitrixActionPayload::action,
			ByteBufCodecs.STRING_UTF8, OmnitrixActionPayload::alien,
			OmnitrixActionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
