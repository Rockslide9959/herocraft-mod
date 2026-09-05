package com.herocraft.mod.sound;

import com.herocraft.mod.HeroCraftMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * Mjolnir's own sound events -- replacing the vanilla trident throw/return sounds it used to borrow,
 * which is exactly why they sounded like a trident being thrown rather than a god's hammer.
 *
 * <p>None of these ship a HeroCraft-authored .ogg (custom Thor-hammer audio is outside what this
 * environment can author or legally source), and for a long time they pointed at
 * {@code assets/herocraft/sounds/mjolnir/*.ogg} paths with nothing on disk -- so all three events were
 * completely silent and each logged a "File ... does not exist" warning on every single launch.
 *
 * <p>They are now built in {@code sounds.json} out of vanilla sound FILES instead (paths verified
 * against the 1.21.1 asset index): trident throw/return for the throw and recall, anvil-land plus iron
 * armour clanks for the catch, each with its own volume/pitch and pooled for variation. Referencing
 * files rather than aliasing a vanilla sound EVENT is what allows that per-entry shaping. Every place
 * that plays these still layers real vanilla sounds alongside them (see {@code ThorPowers#throwMjolnir},
 * {@code MjolnirEntity#recall}/{@code #catchBy}).
 *
 * <p>To replace them with bespoke audio: drop the recording at {@code assets/herocraft/sounds/mjolnir/}
 * and point that event's {@code sounds} entry back at {@code herocraft:mjolnir/<name>}. Nothing in
 * this class changes. A suitable recording: heavy metallic launch + deep air displacement for the
 * throw, a rising whoosh with electric energy for the recall, a solid metallic thud for the catch.
 *
 * <p><b>Do not put a {@code _comment} key in sounds.json.</b> Its codec reads every top-level key as a
 * sound-event name, so a stray one makes the whole file "Invalid sounds.json in resourcepack" and all
 * three events go missing again. Explanations belong here.
 */
public final class HeroCraftSounds {
	public static final SoundEvent MJOLNIR_THROW = register("mjolnir_throw");
	public static final SoundEvent MJOLNIR_RECALL = register("mjolnir_recall");
	public static final SoundEvent MJOLNIR_CATCH = register("mjolnir_catch");

	private HeroCraftSounds() {
	}

	public static void initialize() {
		// Classes are loaded (and the events registered) simply by referencing this class; this
		// method exists so HeroCraftMod has an explicit, readable init call.
	}

	private static SoundEvent register(String path) {
		ResourceLocation id = HeroCraftMod.id(path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}
}
