package com.projecthero.mod.ironman;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

/**
 * v0.14.21: the Iron Man suit-up sound events. No OGG encoder is available in this build environment, so -- like
 * {@code DarkseidSounds} -- none ships a bespoke .ogg: each is assembled in {@code sounds.json} from vanilla sound
 * FILES (paths checked against the 1.21.1 asset index), re-pitched and pooled:
 * <ul>
 *   <li>{@link #SERVO}: piston out/in pitched up + the crafter's whirr -- a piece swinging into place;</li>
 *   <li>{@link #CLAMP}: netherite armour-equip clanks + the lodestone lock -- a piece locking on;</li>
 *   <li>{@link #RELEASE}: piston in + vault eject -- a piece unlatching;</li>
 *   <li>{@link #FACEPLATE_SEAL} / {@link #FACEPLATE_OPEN}: iron trapdoor + vault insert, pitched up;</li>
 *   <li>{@link #POWER_UP}: beacon power hums pitched high -- the suit coming online;</li>
 *   <li>{@link #THRUSTER}: firework launch + breeze charge pitched down -- the delivery pod's engines;</li>
 *   <li>{@link #POD_LAND}: mace ground smash + heavy-core thud;</li>
 *   <li>{@link #CASE_UNFOLD}: vault ejects + piston -- the Mark V suitcase springing open.</li>
 * </ul>
 * To replace any with real audio, drop the file under {@code assets/projecthero/sounds/ironman/} and point that event at
 * {@code projecthero:ironman/<name>} -- nothing in code changes.
 */
public final class IronManSounds {
	public static final SoundEvent SERVO = register("ironman_servo");
	public static final SoundEvent CLAMP = register("ironman_clamp");
	public static final SoundEvent RELEASE = register("ironman_release");
	public static final SoundEvent FACEPLATE_SEAL = register("ironman_faceplate_seal");
	public static final SoundEvent FACEPLATE_OPEN = register("ironman_faceplate_open");
	public static final SoundEvent POWER_UP = register("ironman_power_up");
	public static final SoundEvent THRUSTER = register("ironman_thruster");
	public static final SoundEvent POD_LAND = register("ironman_pod_land");
	public static final SoundEvent CASE_UNFOLD = register("ironman_case_unfold");

	private IronManSounds() {
	}

	public static void initialize() {
		// referencing the class registers every event
	}

	/** Play at an entity for everyone nearby (server side). */
	public static void play(Entity at, SoundEvent event, float volume, float pitch) {
		if (at.level() instanceof ServerLevel level) {
			level.playSound(null, at.getX(), at.getY(), at.getZ(), event, SoundSource.PLAYERS, volume, pitch);
		}
	}

	private static SoundEvent register(String path) {
		ResourceLocation id = ProjectHeroMod.id(path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}
}
