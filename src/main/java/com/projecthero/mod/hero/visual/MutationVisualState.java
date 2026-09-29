package com.projecthero.mod.hero.visual;

import java.util.List;
import java.util.Map;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * v0.13.22: what OTHER players need to see of someone's experimental mutations -- the move animation
 * currently playing and which visual overlays (stone skin, glowing eyes, frost armour, ...) are on.
 *
 * <p>{@code ExperimentalState} is synced to its owner only, so it cannot drive anything a second viewer
 * renders. This small immutable snapshot lives in its own attachment, synced to everyone tracking the
 * player and never persisted (the server rebuilds the flags from the real power state every few ticks,
 * see {@link MutationVisuals#tick}).
 *
 * @param anim      id of the pose animation playing (a key into the client pose library), or "" for none
 * @param animStart game time the animation started -- poses are sampled from this shared clock
 * @param flags     visual overlay flags currently on, sorted (e.g. {@code p05.stone_skin})
 * @param values    small named floats a renderer may scale by (heat level, charge, ...), rounded to 2 dp
 */
public record MutationVisualState(String anim, long animStart, List<String> flags, Map<String, Float> values) {
	public static final MutationVisualState EMPTY = new MutationVisualState("", 0L, List.of(), Map.of());

	public MutationVisualState {
		anim = anim == null ? "" : anim;
		flags = List.copyOf(flags);
		values = Map.copyOf(values);
	}

	public boolean has(String flag) {
		return flags.contains(flag);
	}

	public float value(String name, float fallback) {
		Float v = values.get(name);
		return v == null ? fallback : v;
	}

	public static final StreamCodec<ByteBuf, MutationVisualState> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, MutationVisualState::anim,
			ByteBufCodecs.VAR_LONG, MutationVisualState::animStart,
			ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), MutationVisualState::flags,
			ByteBufCodecs.map(java.util.HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.FLOAT), MutationVisualState::values,
			MutationVisualState::new);
}
