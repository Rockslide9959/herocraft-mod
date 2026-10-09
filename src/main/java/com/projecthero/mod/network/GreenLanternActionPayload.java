package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server "I double-tapped the jump key" packet for Ring Flight (v0.11.5), mirroring
 * {@code ThorActionPayload}/{@code IronManActionPayload}'s own double-tap-jump gesture. The server
 * re-validates power/context/energy in {@code GreenLanternAbilityManager#toggleFlight}.
 */
public record GreenLanternActionPayload(Action action) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<GreenLanternActionPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "green_lantern_action"));

	public static final StreamCodec<RegistryFriendlyByteBuf, GreenLanternActionPayload> CODEC = StreamCodec.composite(
			StreamCodec.of(
					(buf, action) -> buf.writeEnum(action),
					buf -> buf.readEnum(Action.class)),
			GreenLanternActionPayload::action,
			GreenLanternActionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public enum Action {
		TOGGLE_FLIGHT,
		/** v0.14.3: H -- the Giant Hand (grab / hurl). v0.15.15: unused by the client (the Giant Hand is on V). */
		GIANT_HAND,
		/** v0.14.3: N -- dismiss every construct (was Shift+C). */
		CLEAR_CONSTRUCTS,
		/** v0.14.3: Shift + N pressed / N released -- take the ring off after a 5 s hold. */
		RING_REMOVE_START,
		RING_REMOVE_STOP,
		/** v0.15.15: the N suit screen -- one action per {@code GreenLanternSuitStyle}, in its order (append only). */
		SUIT_STYLE_DEFAULT,
		SUIT_STYLE_CORPS,
		SUIT_STYLE_STEWART,
		SUIT_STYLE_CLASSIC,
		/** v0.15.15: H -- suit up / down (V before). */
		SUIT_TOGGLE,
		/** v0.15.15: the Remove Ring button on the Shift+N suit screen (replaces Shift + hold N). */
		REMOVE_RING,
		/** v0.15.16: the two new suits (appended, so the actions above keep their wire ordinals). */
		SUIT_STYLE_MIDNIGHT,
		SUIT_STYLE_ARMORED;

		/** One action per {@code GreenLanternSuitStyle}, in the suits' order. */
		private static final Action[] STYLE_ACTIONS = { SUIT_STYLE_DEFAULT, SUIT_STYLE_CORPS, SUIT_STYLE_STEWART, SUIT_STYLE_CLASSIC,
				SUIT_STYLE_MIDNIGHT, SUIT_STYLE_ARMORED };

		/** The {@code GreenLanternSuitStyle} ordinal this action picks, or -1. */
		public int suitStyle() {
			for (int i = 0; i < STYLE_ACTIONS.length; i++) {
				if (STYLE_ACTIONS[i] == this) {
					return i;
				}
			}
			return -1;
		}

		public static Action forSuitStyle(int style) {
			return STYLE_ACTIONS[style];
		}
	}
}
