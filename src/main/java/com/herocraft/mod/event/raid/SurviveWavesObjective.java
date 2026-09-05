package com.herocraft.mod.event.raid;

import com.herocraft.mod.event.EventObjective;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * The Zombie Raid's objective: clear every wave. Deliberately thin -- the wave machinery lives in
 * {@link ZombieRaid}, and this only reports progress -- but it is a real {@link EventObjective}
 * rather than a hard-coded win check, so a later event can drop in "protect the villagers" or "stop
 * the ritual" beside it without the event loop changing.
 */
public class SurviveWavesObjective implements EventObjective {
	public static final String TYPE_ID = "survive_waves";

	private final int totalWaves;
	private int wavesCleared;

	public SurviveWavesObjective(int totalWaves) {
		this.totalWaves = totalWaves;
	}

	@Override
	public String typeId() {
		return TYPE_ID;
	}

	public void noteWaveCleared(int waveNumber) {
		wavesCleared = Math.max(wavesCleared, waveNumber);
	}

	public int wavesCleared() {
		return wavesCleared;
	}

	@Override
	public boolean isComplete() {
		return wavesCleared >= totalWaves;
	}

	@Override
	public Component describe() {
		return Component.translatable("event.herocraft.zombie_raid.progress",
				Math.min(totalWaves, wavesCleared + 1), totalWaves).withStyle(ChatFormatting.GRAY);
	}

	@Override
	public void save(CompoundTag tag) {
		tag.putInt("WavesCleared", wavesCleared);
	}

	@Override
	public void load(CompoundTag tag) {
		wavesCleared = tag.getInt("WavesCleared");
	}
}
