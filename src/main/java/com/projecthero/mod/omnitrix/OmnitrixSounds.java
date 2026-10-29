package com.projecthero.mod.omnitrix;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.omnitrix.OmnitrixSfx.Cue;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: the Omnitrix's sounds. Rebuilt (user: the demo sounds were "tring sounds and loud and annoying") as
 * {@link OmnitrixSfx} recipes -- vanilla samples, pitched, layered and quiet -- plus six synthesised files for what vanilla
 * can't do (the whirr, the transform sweep, the power-down whirs, the warning blip and the refusal buzz;
 * {@code scratchpad/synth_omnitrix_v2_sounds.js} writes them, {@code scratchpad/analyse_omnitrix_sounds.js} checks them).
 * Heatblast's and Four Arms' Cues are here because they shipped with the framework; every other alien builds its own in
 * its kit. Design notes and levels: {@code docs/OMNITRIX_SOUNDS_0171.md}.
 */
public final class OmnitrixSounds {
	// ---------------------------------------------------------------- the only mod-owned (synthesised) files
	/** A soft rising electronic whirr (rough pulse train + filtered noise). */
	public static final SoundEvent WHIRR = register("omnitrix_core_up");
	/** A soft thump, then a fast bright noise sweep up with electric crackle. */
	public static final SoundEvent SWEEP = register("omnitrix_transform");
	/** A short descending power-down whir with a soft zap. */
	public static final SoundEvent POWER_DOWN = register("omnitrix_revert");
	/** A longer power-down whir that sputters out, a red-flash zap at the start. */
	public static final SoundEvent POWER_DOWN_LONG = register("omnitrix_timeout");
	/** One soft, rounded, mid-pitched electronic blip. */
	public static final SoundEvent BLIP = register("omnitrix_warning");
	/** A dull low buzz. */
	public static final SoundEvent BUZZ = register("omnitrix_denied");
	/** Every synthesised event, for the sound gametest. */
	public static final SoundEvent[] SYNTH = { WHIRR, SWEEP, POWER_DOWN, POWER_DOWN_LONG, BLIP, BUZZ };
	/**
	 * v0.17.1 (user: "use this sound effect for transforming and the second sound effect ... for detransforming"): the user's own
	 * two recordings, mono OGG (trimmed, peaks -6 dBFS; docs/OMNITRIX_SOUNDS_0171.md). The transform swell starts with the slap;
	 * the power-down (five beeps, then the power dying) drives the detransform sequence's timing ({@code Omnitrix.POWER_DOWN_BEEPS}).
	 */
	public static final SoundEvent USER_TRANSFORM = register("omnitrix_transform_user");
	public static final SoundEvent USER_POWER_DOWN = register("omnitrix_power_down_user");
	public static final SoundEvent[] USER = { USER_TRANSFORM, USER_POWER_DOWN };

	// ---------------------------------------------------------------- the watch (UI: quiet, 0.1-0.45)
	/** V: the wrist comes up and the core pops: a small mechanical clack, then the whirr rising. */
	public static final Cue CORE_UP = OmnitrixSfx.cue("omnitrix_core_up")
			.layer(SoundEvents.IRON_TRAPDOOR_OPEN, 0.18f, 1.7f)
			.layer(SoundEvents.PISTON_EXTEND, 0.12f, 1.9f)
			.layer(WHIRR, 0.38f, 1.0f, 1)
			.jitter(0.03f);
	/** V again: the core sinks back: a quick wind-down and a soft click home. */
	public static final Cue CORE_DOWN = OmnitrixSfx.cue("omnitrix_core_down")
			.layer(POWER_DOWN, 0.18f, 1.7f)
			.layer(SoundEvents.PISTON_CONTRACT, 0.12f, 1.9f, 2)
			.layer(SoundEvents.IRON_TRAPDOOR_CLOSE, 0.14f, 1.6f, 3)
			.jitter(0.03f);
	/** The scroll wheel: one quiet, crisp ratchet click (it repeats, so it is tiny). */
	public static final Cue DIAL = OmnitrixSfx.cue("omnitrix_dial")
			.layer(SoundEvents.LEVER_CLICK, 0.12f, 1.9f)
			.layer(SoundEvents.TRIPWIRE_CLICK_ON, 0.08f, 2.0f)
			.jitter(0.04f).gap(1);
	/** Picking an alien on the dial screen: a slightly firmer double click. */
	public static final Cue SELECT = OmnitrixSfx.cue("omnitrix_select")
			.layer(SoundEvents.LEVER_CLICK, 0.18f, 1.5f)
			.layer(SoundEvents.IRON_TRAPDOOR_CLOSE, 0.12f, 1.9f, 1)
			.jitter(0.03f).gap(1);
	/** The core slammed down: a punchy mechanical clack-thump. */
	public static final Cue SLAP = OmnitrixSfx.cue("omnitrix_slap")
			.layer(SoundEvents.IRON_TRAPDOOR_CLOSE, 0.35f, 0.8f)
			.layer(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.3f, 0.7f)
			.layer(SoundEvents.PISTON_CONTRACT, 0.15f, 1.4f)
			.jitter(0.04f);
	/** The green flash: a thump, then the whoosh-zap sweeping up the body. */
	public static final Cue TRANSFORM = OmnitrixSfx.cue("omnitrix_transform")
			.layer(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.3f, 0.55f)
			.layer(SWEEP, 0.55f, 1.0f)
			.jitter(0.04f);
	/** The user's transform sound (no jitter: the slap / body change is timed to it). */
	public static final Cue TRANSFORM_USER = OmnitrixSfx.cue("omnitrix_transform_user")
			.layer(USER_TRANSFORM, 0.7f, 1.0f)
			.jitter(0.0f).gap(4);
	/** The user's power-down: five beeps, then the power dying (no jitter: the beeps / revert are timed to it). */
	public static final Cue POWER_DOWN_USER = OmnitrixSfx.cue("omnitrix_power_down_user")
			.layer(USER_POWER_DOWN, 0.7f, 1.0f)
			.jitter(0.0f).gap(4);
	/** An immediate revert (suppression, the pod, a revoke): the short whir -- V and the timeout use {@link #POWER_DOWN_USER}. */
	public static final Cue REVERT = OmnitrixSfx.cue("omnitrix_revert")
			.layer(POWER_DOWN, 0.45f, 1.0f)
			.layer(SoundEvents.PISTON_CONTRACT, 0.12f, 1.6f, 8)
			.jitter(0.04f);
	/** Time's up: the red flash zap and the power-down whir sputtering out, the core clunking home. */
	public static final Cue TIMEOUT = OmnitrixSfx.cue("omnitrix_timeout")
			.layer(POWER_DOWN_LONG, 0.5f, 1.0f)
			.layer(SoundEvents.IRON_TRAPDOOR_CLOSE, 0.16f, 0.9f, 20)
			.jitter(0.02f);
	/** The pod's flicker: a brief, quieter flash-and-fade. */
	public static final Cue FLICKER = OmnitrixSfx.cue("omnitrix_flicker")
			.layer(SWEEP, 0.22f, 1.3f)
			.layer(POWER_DOWN, 0.18f, 1.4f, 3)
			.gap(6);
	/** The watch jumping onto the wrist (pod / item / command): clack, whirr, a soft flash. */
	public static final Cue GRANT = OmnitrixSfx.cue("omnitrix_grant")
			.layer(SoundEvents.IRON_TRAPDOOR_CLOSE, 0.35f, 0.8f)
			.layer(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.3f, 0.7f)
			.layer(WHIRR, 0.35f, 0.9f, 2)
			.layer(SWEEP, 0.3f, 1.2f, 8)
			.jitter(0.02f);
	/** The timeout warning: one soft blip (Omnitrix#tick plays it on a cadence that quickens in the last seconds). */
	public static final Cue WARNING = OmnitrixSfx.cue("omnitrix_warning")
			.layer(BLIP, 0.32f, 1.0f)
			.jitter(0.0f).gap(4);
	/** Refused (recharging, held, rejected): a dull low buzz. */
	public static final Cue DENIED = OmnitrixSfx.cue("omnitrix_denied")
			.layer(BUZZ, 0.35f, 1.0f)
			.layer(SoundEvents.LEVER_CLICK, 0.08f, 0.7f)
			.jitter(0.02f).gap(6);
	/** The recharge done: the whirr, higher and short, and a click. */
	public static final Cue RECHARGED = OmnitrixSfx.cue("omnitrix_recharged")
			.layer(WHIRR, 0.3f, 1.25f)
			.layer(SoundEvents.LEVER_CLICK, 0.14f, 1.6f, 6)
			.jitter(0.0f);

	// ---------------------------------------------------------------- the pod, the scanner, the meteor
	/** The pod's drain pulse (every few seconds of an extraction): a low energy sweep and a hum swell. */
	public static final Cue EXTRACT_PULSE = OmnitrixSfx.cue("omnitrix_extract_pulse")
			.layer(SWEEP, 0.3f, 0.55f)
			.layer(WHIRR, 0.25f, 0.6f)
			.gap(10);
	/** The pod's alarm (its start, then every 2 s): a low soft beep-beep. */
	public static final Cue SELF_DESTRUCT = OmnitrixSfx.cue("omnitrix_self_destruct")
			.layer(BLIP, 0.4f, 0.7f)
			.layer(BLIP, 0.4f, 0.7f, 4)
			.jitter(0.0f).gap(10);
	/** The extraction done: a soft boom, the field collapsing, the power going. */
	public static final Cue EXTRACT_DONE = OmnitrixSfx.cue("omnitrix_extract_done")
			.layer(SoundEvents.GENERIC_EXPLODE, 0.35f, 1.4f)
			.layer(SoundEvents.RESPAWN_ANCHOR_DEPLETE, 0.5f, 0.8f)
			.layer(POWER_DOWN_LONG, 0.4f, 0.8f, 2);
	/** The pod's lid: a pneumatic clunk and a hiss. */
	public static final Cue POD_OPEN = OmnitrixSfx.cue("omnitrix_pod_open")
			.layer(SoundEvents.PISTON_CONTRACT, 0.5f, 0.6f)
			.layer(SoundEvents.FIRE_EXTINGUISH, 0.4f, 0.7f)
			.layer(SoundEvents.IRON_DOOR_OPEN, 0.4f, 0.7f, 4);
	/** An open pod's "here I am" (every 2 s): a very quiet blip. */
	public static final Cue POD_BEACON = OmnitrixSfx.cue("omnitrix_pod_beacon")
			.layer(BLIP, 0.18f, 1.25f)
			.jitter(0.0f).gap(20);
	/** The meteor scanner locking on: two soft blips and a click. */
	public static final Cue SCANNER = OmnitrixSfx.cue("omnitrix_scanner")
			.layer(SoundEvents.LEVER_CLICK, 0.15f, 1.7f)
			.layer(BLIP, 0.3f, 1.2f)
			.layer(BLIP, 0.3f, 1.35f, 3)
			.jitter(0.0f);
	/** The meteor striking (with the vanilla explosion the entity already plays). */
	public static final Cue METEOR = OmnitrixSfx.cue("omnitrix_meteor")
			.layer(SoundEvents.GENERIC_EXPLODE, 0.7f, 0.55f)
			.layer(SoundEvents.BLAZE_SHOOT, 0.45f, 0.5f);

	// ---------------------------------------------------------------- Heatblast: roaring fire, fireballs, crackle
	public static final Cue HEATBLAST_BLAST = OmnitrixSfx.cue("heatblast_blast")
			.layer(SoundEvents.BLAZE_SHOOT, 0.5f, 1.0f)
			.layer(SoundEvents.FIRECHARGE_USE, 0.3f, 1.2f)
			.layer(SoundEvents.FURNACE_FIRE_CRACKLE, 0.35f, 1.3f, 2);
	public static final Cue HEATBLAST_STREAM = OmnitrixSfx.cue("heatblast_stream")
			.layer(SoundEvents.BLAZE_BURN, 0.45f, 0.9f)
			.layer(SoundEvents.FIRECHARGE_USE, 0.2f, 0.7f)
			.gap(8);
	public static final Cue HEATBLAST_NOVA = OmnitrixSfx.cue("heatblast_nova")
			.layer(SoundEvents.GENERIC_EXPLODE, 0.5f, 0.8f)
			.layer(SoundEvents.BLAZE_SHOOT, 0.45f, 0.6f)
			.layer(SoundEvents.FIRECHARGE_USE, 0.45f, 0.5f, 2)
			.layer(SoundEvents.FIRE_AMBIENT, 0.6f, 0.8f, 4);
	/** The Fire Jet taking off / boosting. */
	public static final Cue HEATBLAST_JET = OmnitrixSfx.cue("heatblast_jet")
			.layer(SoundEvents.FIRECHARGE_USE, 0.4f, 0.8f)
			.layer(SoundEvents.BLAZE_BURN, 0.35f, 1.1f, 1);
	/** The Fire Jet held in the air (once a second): a quiet roar. */
	public static final Cue HEATBLAST_JET_LOOP = OmnitrixSfx.cue("heatblast_jet_loop")
			.layer(SoundEvents.BLAZE_BURN, 0.16f, 1.3f)
			.jitter(0.1f).gap(16);
	public static final Cue HEATBLAST_SHIELD = OmnitrixSfx.cue("heatblast_shield")
			.layer(SoundEvents.BLAZE_BURN, 0.45f, 0.7f)
			.layer(SoundEvents.FIRE_AMBIENT, 0.5f, 0.6f)
			.layer(SoundEvents.FIRECHARGE_USE, 0.25f, 0.6f);
	public static final Cue HEATBLAST_IGNITE = OmnitrixSfx.cue("heatblast_ignite")
			.layer(SoundEvents.FIRECHARGE_USE, 0.45f, 0.8f)
			.layer(SoundEvents.BLAZE_BURN, 0.4f, 1.0f, 1)
			.layer(SoundEvents.FURNACE_FIRE_CRACKLE, 0.4f, 1.0f, 3);

	// ---------------------------------------------------------------- Four Arms: meaty punches, ground thuds, a deep roar
	public static final Cue FOUR_ARMS_PUNCH = OmnitrixSfx.cue("four_arms_punch")
			.layer(SoundEvents.PLAYER_ATTACK_STRONG, 0.5f, 0.6f)
			.layer(SoundEvents.IRON_GOLEM_ATTACK, 0.45f, 0.9f)
			.layer(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.35f, 0.7f);
	public static final Cue FOUR_ARMS_CLAP = OmnitrixSfx.cue("four_arms_clap")
			.layer(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.55f, 0.5f)
			.layer(SoundEvents.GENERIC_EXPLODE, 0.35f, 1.5f)
			.layer(SoundEvents.WIND_CHARGE_BURST, 0.45f, 0.7f, 1);
	public static final Cue FOUR_ARMS_SLAM = OmnitrixSfx.cue("four_arms_slam")
			.layer(SoundEvents.GENERIC_EXPLODE, 0.4f, 0.7f)
			.layer(SoundEvents.STONE_BREAK, 0.55f, 0.6f)
			.layer(SoundEvents.GRAVEL_BREAK, 0.45f, 0.7f, 2);
	public static final Cue FOUR_ARMS_THROW = OmnitrixSfx.cue("four_arms_throw")
			.layer(SoundEvents.IRON_GOLEM_ATTACK, 0.5f, 0.7f)
			.layer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.35f, 0.6f);
	public static final Cue FOUR_ARMS_ROAR = OmnitrixSfx.cue("four_arms_roar")
			.layer(SoundEvents.RAVAGER_ROAR, 0.5f, 1.0f)
			.layer(SoundEvents.HOGLIN_ANGRY, 0.3f, 0.7f)
			.gap(10);
	/** The Brace stance planting: a heavy stomp (was a ringing anvil). */
	public static final Cue FOUR_ARMS_STANCE = OmnitrixSfx.cue("four_arms_stance")
			.layer(SoundEvents.HEAVY_CORE_PLACE, 0.45f, 0.6f)
			.layer(SoundEvents.GRAVEL_BREAK, 0.3f, 0.6f);

	private OmnitrixSounds() {
	}

	static void initialize() {
	}

	/** Plays a Cue at {@code at} for everyone nearby (the volume / pitch are the relative scales {@link OmnitrixSfx} shapes). */
	public static void play(ServerLevel level, Vec3 at, Cue cue, float volume, float pitch) {
		OmnitrixSfx.play(level, at, cue, volume, pitch);
	}

	public static void play(ServerLevel level, Vec3 at, Cue cue, float volume, float pitch, SoundSource source) {
		OmnitrixSfx.play(level, at, cue, volume, pitch, source);
	}

	/** Plays a Cue on {@code entity} (the sound follows it) for everyone nearby. */
	public static void playOn(net.minecraft.world.entity.Entity entity, Cue cue) {
		OmnitrixSfx.playOn(entity, cue, 1f, 1f, SoundSource.PLAYERS);
	}

	/** Cuts the power-down sound short for everyone near {@code p} (the sequence was interrupted: death, logout, a world change). */
	public static void stopPowerDown(ServerPlayer p) {
		var stop = new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(USER_POWER_DOWN.getLocation(), SoundSource.PLAYERS);
		for (ServerPlayer o : p.serverLevel().players()) {
			if (o == p || o.distanceToSqr(p) < 48 * 48) {
				o.connection.send(stop);
			}
		}
	}

	/** Plays a Cue to one player alone (the watch's UI). */
	public static void notify(ServerPlayer p, Cue cue) {
		OmnitrixSfx.notify(p, cue, 1f, 1f);
	}

	public static void notify(ServerPlayer p, Cue cue, float pitch) {
		OmnitrixSfx.notify(p, cue, 1f, pitch);
	}

	private static SoundEvent register(String path) {
		ResourceLocation id = ProjectHeroMod.id(path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}
}
