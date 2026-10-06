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
 * v0.14.31 adds the move sounds ({@link #MOVE_SOUNDS}): repulsor charge / blast, Unibeam, missiles, miniguns, flares,
 * sonic clap, wrist laser, shields, dash, surge, JARVIS scan, blades, flamethrower, punch -- same vanilla-layer recipe.
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

	// v0.14.31: the move sounds -- every one layered / re-pitched from vanilla files in sounds.json (no custom audio).
	// Events whose name ends in a layer word (_zap, _crackle, _clang, _ring, _chirp) are played ON TOP of a move's main
	// event and share its subtitle, so a layered move shows one subtitle line.
	public static final SoundEvent REPULSOR_CHARGE = register("ironman_repulsor_charge");
	public static final SoundEvent REPULSOR_READY = register("ironman_repulsor_ready");
	public static final SoundEvent REPULSOR_BLAST = register("ironman_repulsor_blast");
	public static final SoundEvent REPULSOR_ZAP = register("ironman_repulsor_zap");
	public static final SoundEvent REPULSOR_CHARGED_BLAST = register("ironman_repulsor_charged_blast");
	public static final SoundEvent ENERGY_IMPACT = register("ironman_energy_impact");
	public static final SoundEvent UNIBEAM_CHARGE = register("ironman_unibeam_charge");
	public static final SoundEvent UNIBEAM_LOOP = register("ironman_unibeam_loop");
	public static final SoundEvent BEAM_CRACKLE = register("ironman_beam_crackle");
	public static final SoundEvent UNIBEAM_END = register("ironman_unibeam_end");
	public static final SoundEvent MISSILE_POD = register("ironman_missile_pod");
	public static final SoundEvent MISSILE_LAUNCH = register("ironman_missile_launch");
	public static final SoundEvent ROCKET_LAUNCH = register("ironman_rocket_launch");
	public static final SoundEvent MISSILE_IMPACT = register("ironman_missile_impact");
	public static final SoundEvent MINIGUN_SPIN = register("ironman_minigun_spin");
	public static final SoundEvent MINIGUN_FIRE = register("ironman_minigun_fire");
	public static final SoundEvent MINIGUN_STOP = register("ironman_minigun_stop");
	public static final SoundEvent FLARE_LAUNCH = register("ironman_flare_launch");
	public static final SoundEvent FLARE_CRACKLE = register("ironman_flare_crackle");
	public static final SoundEvent FLARE_HIT = register("ironman_flare_hit");
	public static final SoundEvent SONIC_CLAP = register("ironman_sonic_clap");
	public static final SoundEvent CLAP_CLANG = register("ironman_clap_clang");
	public static final SoundEvent LASER_START = register("ironman_laser_start");
	public static final SoundEvent LASER = register("ironman_laser");
	public static final SoundEvent LASER_END = register("ironman_laser_end");
	public static final SoundEvent POWER_FAIL = register("ironman_power_fail");
	public static final SoundEvent SHIELD_UP = register("ironman_shield_up");
	public static final SoundEvent SHIELD_HUM = register("ironman_shield_hum");
	public static final SoundEvent SHIELD_DOWN = register("ironman_shield_down");
	public static final SoundEvent SHIELD_DEFLECT = register("ironman_shield_deflect");
	public static final SoundEvent DASH = register("ironman_dash");
	public static final SoundEvent FLIGHT_BURST = register("ironman_flight_burst");
	public static final SoundEvent SUPERSONIC = register("ironman_supersonic");
	public static final SoundEvent SUPERSONIC_END = register("ironman_supersonic_end");
	public static final SoundEvent SURGE = register("ironman_surge");
	public static final SoundEvent SURGE_CRACKLE = register("ironman_surge_crackle");
	public static final SoundEvent SURGE_PULSE = register("ironman_surge_pulse");
	public static final SoundEvent SURGE_END = register("ironman_surge_end");
	public static final SoundEvent SCAN = register("ironman_scan");
	public static final SoundEvent SCAN_CHIRP = register("ironman_scan_chirp");
	public static final SoundEvent TARGET_LOCK = register("ironman_target_lock");
	public static final SoundEvent HUD_ON = register("ironman_hud_on");
	public static final SoundEvent HUD_OFF = register("ironman_hud_off");
	public static final SoundEvent WEAPON_SELECT = register("ironman_weapon_select");
	public static final SoundEvent BLADE_EXTEND = register("ironman_blade_extend");
	public static final SoundEvent BLADE_RING = register("ironman_blade_ring");
	public static final SoundEvent BLADE_RETRACT = register("ironman_blade_retract");
	public static final SoundEvent FLAMETHROWER_IGNITE = register("ironman_flamethrower_ignite");
	public static final SoundEvent FLAMETHROWER = register("ironman_flamethrower");
	public static final SoundEvent PUNCH = register("ironman_punch");
	public static final SoundEvent MK1_CLUNK = register("ironman_mk1_clunk");
	public static final SoundEvent COMBO_FINISHER = register("ironman_combo_finisher");
	public static final SoundEvent SUIT_STORED = register("ironman_suit_stored");

	/** Every v0.14.31 move event, for the registration GameTest. */
	public static final SoundEvent[] MOVE_SOUNDS = {
		REPULSOR_CHARGE,
		REPULSOR_READY,
		REPULSOR_BLAST,
		REPULSOR_ZAP,
		REPULSOR_CHARGED_BLAST,
		ENERGY_IMPACT,
		UNIBEAM_CHARGE,
		UNIBEAM_LOOP,
		BEAM_CRACKLE,
		UNIBEAM_END,
		MISSILE_POD,
		MISSILE_LAUNCH,
		ROCKET_LAUNCH,
		MISSILE_IMPACT,
		MINIGUN_SPIN,
		MINIGUN_FIRE,
		MINIGUN_STOP,
		FLARE_LAUNCH,
		FLARE_CRACKLE,
		FLARE_HIT,
		SONIC_CLAP,
		CLAP_CLANG,
		LASER_START,
		LASER,
		LASER_END,
		POWER_FAIL,
		SHIELD_UP,
		SHIELD_HUM,
		SHIELD_DOWN,
		SHIELD_DEFLECT,
		DASH,
		FLIGHT_BURST,
		SUPERSONIC,
		SUPERSONIC_END,
		SURGE,
		SURGE_CRACKLE,
		SURGE_PULSE,
		SURGE_END,
		SCAN,
		SCAN_CHIRP,
		TARGET_LOCK,
		HUD_ON,
		HUD_OFF,
		WEAPON_SELECT,
		BLADE_EXTEND,
		BLADE_RING,
		BLADE_RETRACT,
		FLAMETHROWER_IGNITE,
		FLAMETHROWER,
		PUNCH,
		MK1_CLUNK,
		COMBO_FINISHER,
		SUIT_STORED,
	};

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

	/** v0.14.31: play at a world position for everyone nearby (server side) -- impacts, flare strikes. */
	public static void playAt(net.minecraft.world.level.Level level, double x, double y, double z, SoundEvent event, float volume, float pitch) {
		if (level instanceof ServerLevel server) {
			server.playSound(null, x, y, z, event, SoundSource.PLAYERS, volume, pitch);
		}
	}

	/** v0.14.31: the Mark 1 is a cave-built prototype -- everything it does plays lower and cruder. */
	public static float markPitch(String suitId) {
		return "mark_1".equals(suitId) ? 0.8f : 1.0f;
	}

	/**
	 * v0.14.31: a one-shot move sound at the wearer for everyone nearby, pitched for the worn mark; on the Mark 1 it also
	 * clanks ({@link #MK1_CLUNK}) so the prototype sounds bolted together. Use {@link #play} for repeating loop ticks.
	 */
	public static void move(net.minecraft.world.entity.player.Player player, SoundEvent event, float volume, float pitch) {
		String suitId = IronManArmor.wornSuitId(player);
		play(player, event, volume, pitch * markPitch(suitId));
		if ("mark_1".equals(suitId)) {
			play(player, MK1_CLUNK, Math.min(1.0f, volume * 0.6f), 1.0f);
		}
	}

	/** v0.14.31: a repeating loop tick at the wearer, pitched for the worn mark (no clunk layer). */
	public static void loop(net.minecraft.world.entity.player.Player player, SoundEvent event, float volume, float pitch) {
		play(player, event, volume, pitch * markPitch(IronManArmor.wornSuitId(player)));
	}

	private static SoundEvent register(String path) {
		ResourceLocation id = ProjectHeroMod.id(path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}
}
