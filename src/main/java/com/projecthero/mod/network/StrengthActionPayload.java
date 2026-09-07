package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -&gt; server gestures for Super Strength that vanilla never reports on its own. Both are
 * wound up by holding a key and thrown on release, tracked entirely client-side so the timing is
 * deterministic; the server re-validates power and cooldown, so spamming the packet buys nothing.
 *
 * @param action what was done
 * @param value  an action-specific number -- for {@link Action#PERFORM_POWER_LEAP} the number of
 *               ticks the leap key was held (0 for the others)
 */
public record StrengthActionPayload(Action action, int value) implements CustomPacketPayload {
	public enum Action {
		/** Attack key released after being held ~2 s: throw the charged punch now. */
		PERFORM_CHARGED_PUNCH,
		/** The X key released after being held: leap, with {@code value} = ticks held. */
		PERFORM_POWER_LEAP
	}

	public static final CustomPacketPayload.Type<StrengthActionPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "strength_action"));

	public static final StreamCodec<RegistryFriendlyByteBuf, StrengthActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.map(i -> Action.values()[Math.floorMod(i, Action.values().length)], Action::ordinal),
			StrengthActionPayload::action,
			ByteBufCodecs.VAR_INT, StrengthActionPayload::value,
			StrengthActionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
