package com.projecthero.mod.quirk.fx;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.17.1 Quirk framework, server &rarr; client: one camera "juice" cue (see {@link QuirkCamera}). Purely cosmetic -- a
 * client that ignores it gains nothing. {@code kind}: {@link QuirkCamera#SHAKE}, {@link QuirkCamera#HIT_STOP},
 * {@link QuirkCamera#FOV_PUNCH} or {@link QuirkCamera#VIGNETTE}; {@code colour} is only read by the vignette.
 */
public record QuirkCameraPayload(int kind, float strength, int ticks, int colour) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<QuirkCameraPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "quirk_camera"));

	public static final StreamCodec<RegistryFriendlyByteBuf, QuirkCameraPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, QuirkCameraPayload::kind,
			ByteBufCodecs.FLOAT, QuirkCameraPayload::strength,
			ByteBufCodecs.VAR_INT, QuirkCameraPayload::ticks,
			ByteBufCodecs.INT, QuirkCameraPayload::colour,
			QuirkCameraPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
