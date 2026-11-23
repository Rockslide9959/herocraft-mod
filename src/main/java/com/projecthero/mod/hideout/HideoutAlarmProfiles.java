package com.projecthero.mod.hideout;

import com.projecthero.mod.alarm.AlarmProfile;
import com.projecthero.mod.alarm.AlarmProfiles;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * v0.17.7: the five villain hideouts as alarm zones (tier 3). Profile id {@code hideout/<faction>}; every start of the
 * faction's structure is a zone; reinforcement waves are the faction's guards arriving their own way
 * ({@link HideoutGuards#reinforcement}: a Boom Tube, burst egg pods, sparks, snow).
 */
public final class HideoutAlarmProfiles {
	private HideoutAlarmProfiles() {
	}

	public static String id(HideoutFaction f) {
		return "hideout/" + f.getSerializedName();
	}

	public static void register() {
		for (HideoutFaction f : HideoutFaction.values()) {
			SoundEvent siren;
			float pitch;
			switch (f) {
				case KINGPIN -> {
					siren = SoundEvents.BELL_BLOCK;
					pitch = 1.4f;
				}
				case ULTRON -> {
					siren = SoundEvents.NOTE_BLOCK_BIT.value();
					pitch = 0.5f;
				}
				case APOKOLIPS -> {
					siren = SoundEvents.NOTE_BLOCK_DIDGERIDOO.value();
					pitch = 0.6f;
				}
				case SYMBIOTE -> {
					siren = SoundEvents.WARDEN_HEARTBEAT;
					pitch = 0.8f;
				}
				case GRAVE -> { // v0.17.7: a tolling crypt bell
					siren = SoundEvents.BELL_RESONATE;
					pitch = 0.5f;
				}
				case CARNAGE -> { // v0.17.7: the field station's old klaxon, half choked with goo
					siren = SoundEvents.NOTE_BLOCK_BASS.value();
					pitch = 0.5f;
				}
				case OATH -> { // v0.17.7: a keep's war horn
					siren = SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(3).value();
					pitch = 0.8f;
				}
				default -> {
					siren = SoundEvents.BELL_BLOCK;
					pitch = 0.6f;
				}
			}
			AlarmProfiles.register(new AlarmProfile(id(f), 3, f.colour(), f.hideoutName(),
					(level, spot, random) -> HideoutGuards.reinforcement(level, f, spot, random), siren, pitch));
			AlarmProfiles.registerStructure(f.structureId(), id(f));
		}
	}
}
