package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.17.5 Symbiote moveset v3: client -> server, the Normal host's inputs that are not an ability key edge. The server re-checks
 * everything ({@code SymbioteAbilityManager}).
 * <ul>
 *   <li>{@link #CRUSH}: the attack key was clicked while holding a Tendril Grab (the client swallowed the click).</li>
 *   <li>{@link #WHEEL_OPENED}: V was held 0.5 s and the Form wheel opened -- V's pending press must not become a tap.</li>
 *   <li>{@link #PICK_FORM}: a Form was picked on the wheel ({@code value} = the Form id, {@code SymbioteVitalsManager.FORM_*}).</li>
 * </ul>
 */
public record SymbioteKitPayload(int action, int value) implements CustomPacketPayload {
	public static final int CRUSH = 0;
	public static final int WHEEL_OPENED = 1;
	public static final int PICK_FORM = 2;

	public static final CustomPacketPayload.Type<SymbioteKitPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "symbiote_kit"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SymbioteKitPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, SymbioteKitPayload::action,
			ByteBufCodecs.VAR_INT, SymbioteKitPayload::value,
			SymbioteKitPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
