package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -&gt; server Iron Man gestures that are not one of the six universal ability slots:
 * toggling repulsor flight and summoning a suit, both driven by a double-tap of the vanilla jump key
 * (mirroring Thor's flight input, so no new keybind is introduced). Everything is re-validated
 * server-side ({@link com.projecthero.mod.ironman.IronManFlight} /
 * {@link com.projecthero.mod.ironman.suit.IronManSuitSummonManager}).
 */
public record IronManActionPayload(Action action) implements CustomPacketPayload {
	public enum Action {
		TOGGLE_FLIGHT,
		SUMMON_SUIT,
		/** "changes 19": open / close the worn helmet's faceplate (H key). */
		TOGGLE_FACEPLATE
	}

	public static final CustomPacketPayload.Type<IronManActionPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "iron_man_action"));

	public static final StreamCodec<RegistryFriendlyByteBuf, IronManActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.idMapper(i -> Action.values()[i], Action::ordinal), IronManActionPayload::action,
			IronManActionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
