package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -&gt; server gestures that are not one of the six ability slots.
 *
 * <p>Only two exist, and both are edge-triggered -- a jump press while already airborne, and a jump
 * press while stuck to a wall or ceiling -- so nothing here is sent on a timer. The server
 * re-validates every one of them ({@code SpiderAbilities.doubleJump} checks the power, the cooldown,
 * and that the player really is in the air), so spamming the packet buys a modified client nothing.
 */
public record SpiderActionPayload(Action action) implements CustomPacketPayload {
	public enum Action {
		/** Jump pressed in mid-air: the passive second jump. */
		DOUBLE_JUMP,
		/** Jump pressed while adhered: shove off the held surface. */
		SURFACE_LEAP,
		/** Double-tapped jump against a wall/ceiling: arm surface adhesion (v0.6.6). */
		CLIMB_GRAB,
		/** Double-tapped sneak while adhered: let go of the surface (v0.6.6). */
		CLIMB_RELEASE,
		/** Sneak + jump on the ground: the 6-block super leap (v0.6.17). */
		SUPER_JUMP,
		/** H key while wearing the Spider-Man costume head piece: pull the mask off / on (v0.6.20). */
		TOGGLE_MASK,
		/** H key for a Spider-Man who has bonded with the Symbiote: engage / retract the black suit (v0.9.10). */
		TOGGLE_SYMBIOTE,
		/** N key: switch between Traversal Mode and Combat Mode (v0.12.20). */
		TOGGLE_MODE
	}

	public static final CustomPacketPayload.Type<SpiderActionPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "spider_action"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SpiderActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.map(i -> Action.values()[Math.floorMod(i, Action.values().length)], Action::ordinal),
			SpiderActionPayload::action,
			SpiderActionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
