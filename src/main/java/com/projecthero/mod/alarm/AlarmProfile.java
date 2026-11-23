package com.projecthero.mod.alarm;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;

/**
 * v0.17.7: how one kind of guarded place behaves when its alarm goes off. Registered once at start-up with
 * {@link AlarmProfiles#register} (an immutable description, no world state).
 *
 * @param id          unique id, e.g. {@code "hideout/kingpin"}, {@code "sky_fortress"}
 * @param tier        place tier 1-4: reinforcement wave size ({@link AlarmProfiles#waveSize})
 * @param colour      chat / HUD accent colour (0xRRGGBB)
 * @param placeName   the place's name (keycard tooltips, alarm messages)
 * @param reinforcer  makes one reinforcement mob at a free spot (already added to the level); null = no waves
 * @param siren       the sound the siren loops while Alert
 * @param sirenPitch  its pitch
 */
public record AlarmProfile(String id, int tier, int colour, Component placeName, Reinforcer reinforcer, SoundEvent siren, float sirenPitch) {
	/** Spawns one reinforcement at {@code spot} (feet position). Return the mob (already in the level) or null. */
	@FunctionalInterface
	public interface Reinforcer {
		Mob spawn(ServerLevel level, BlockPos spot, RandomSource random);
	}
}
