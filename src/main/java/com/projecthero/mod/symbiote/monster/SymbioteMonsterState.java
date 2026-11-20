package com.projecthero.mod.symbiote.monster;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * v0.17.5 Symbiote Monster form (Shift+Z ultimate): where a host is in the transformation and what the monster is doing. One small
 * immutable record under its own attachment ({@code projecthero:symbiote_monster}), <b>not saved</b> (a relog / server stop ends the
 * form -- {@code SymbioteMonster} reconciles the attributes every tick from this), not copied on death, synced to everyone so every
 * viewer draws the cocoon, the monster model and its move animations.
 *
 * <ul>
 *   <li>{@link #phase} / {@link #phaseStart}: {@link #NONE}, {@link #COCOON} (the tendrils and the cocoon, 2.5 s), {@link #MONSTER}
 *       (40 s), {@link #MELT} (1.5 s back into the suit), and the game time the phase began.</li>
 *   <li>{@link #move} / {@link #moveStart}: the monster move playing (the client picks the clip from it).</li>
 *   <li>{@link #ready}: per key (AbilitySlot ordinal, R G X Z V C) the game time the monster move is ready again.</li>
 * </ul>
 * The Shift+Z charge itself rides on {@code SymbioteState.onslaughtChargeStart} (the field the old Onslaught charge used), so the HUD
 * fill, the roaring mouth and the rooted wind-up tendrils follow it unchanged.
 */
public record SymbioteMonsterState(int phase, long phaseStart, int move, long moveStart, List<Long> ready) {
	public static final int NONE = 0;
	public static final int COCOON = 1;
	public static final int MONSTER = 2;
	public static final int MELT = 3;

	public static final int MOVE_NONE = 0;
	public static final int MOVE_SWIPE = 1;
	public static final int MOVE_POUND = 2;
	public static final int MOVE_LEAP = 3;
	public static final int MOVE_LAND = 4;
	public static final int MOVE_ROAR = 5;
	public static final int MOVE_DEVOUR = 6;
	public static final int MOVE_PULL = 7;

	public static final int KEYS = 6;
	public static final SymbioteMonsterState EMPTY = new SymbioteMonsterState(NONE, 0L, MOVE_NONE, 0L, List.of());

	public SymbioteMonsterState {
		ArrayList<Long> r = new ArrayList<>(Collections.nCopies(KEYS, 0L));
		if (ready != null) {
			for (int i = 0; i < Math.min(KEYS, ready.size()); i++) {
				r.set(i, ready.get(i) == null ? 0L : ready.get(i));
			}
		}
		ready = List.copyOf(r);
		if (phase < NONE || phase > MELT) {
			phase = NONE;
		}
	}

	public boolean inForm() {
		return phase != NONE;
	}

	public SymbioteMonsterState withPhase(int newPhase, long start) {
		return new SymbioteMonsterState(newPhase, start, MOVE_NONE, start, ready);
	}

	public SymbioteMonsterState withMove(int newMove, long start) {
		return new SymbioteMonsterState(phase, phaseStart, newMove, start, ready);
	}

	public SymbioteMonsterState withReady(int key, long at) {
		ArrayList<Long> r = new ArrayList<>(ready);
		r.set(key, at);
		return new SymbioteMonsterState(phase, phaseStart, move, moveStart, r);
	}

	public long readyAt(int key) {
		return key >= 0 && key < ready.size() ? ready.get(key) : 0L;
	}

	public static final Codec<SymbioteMonsterState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("phase", NONE).forGetter(SymbioteMonsterState::phase),
			Codec.LONG.optionalFieldOf("phase_start", 0L).forGetter(SymbioteMonsterState::phaseStart),
			Codec.INT.optionalFieldOf("move", MOVE_NONE).forGetter(SymbioteMonsterState::move),
			Codec.LONG.optionalFieldOf("move_start", 0L).forGetter(SymbioteMonsterState::moveStart),
			Codec.LONG.listOf().optionalFieldOf("ready", List.of()).forGetter(SymbioteMonsterState::ready)
	).apply(i, SymbioteMonsterState::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, SymbioteMonsterState> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, SymbioteMonsterState::phase,
			ByteBufCodecs.VAR_LONG, SymbioteMonsterState::phaseStart,
			ByteBufCodecs.VAR_INT, SymbioteMonsterState::move,
			ByteBufCodecs.VAR_LONG, SymbioteMonsterState::moveStart,
			ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list(KEYS)), SymbioteMonsterState::ready,
			SymbioteMonsterState::new);

	/** Not persistent, not copied on death, synced to everyone. Registered by {@link SymbioteMonster#registerEvents}. */
	public static final AttachmentType<SymbioteMonsterState> TYPE = AttachmentRegistry.create(ProjectHeroMod.id("symbiote_monster"),
			builder -> builder.initializer(() -> EMPTY).syncWith(STREAM_CODEC, AttachmentSyncPredicate.all()));
}
