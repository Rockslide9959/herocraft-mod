package com.projecthero.mod.client.sound;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.ironman.IronManFlightPose;
import com.projecthero.mod.ironman.IronManFlightLook;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * v0.14.21: the looping repulsor-thruster sound for one flying Iron Man (or Repulsor Boots wearer) -- the local player
 * and every other one the client can see. Modelled on {@link ThorFlightSoundInstance} / vanilla's elytra loop: it
 * follows the player each tick, drives volume / pitch from the flight speed ({@link IronManFlightLook}), fades in on
 * take-off and fades out (then stops itself) once flight ends.
 *
 * <p>Three layers, each its own instance, so they mix: {@link Layer#ROAR} the jet burn (louder and higher with speed),
 * {@link Layer#WHINE} the repulsor whirr (strongest hovering), {@link Layer#WIND} the air rush (silent hovering,
 * swelling in at speed). The events are client-only (never registered) and point at re-pitched vanilla sound files in
 * {@code sounds.json} -- the SoundManager resolves them by id.
 */
public class IronManThrusterSoundInstance extends AbstractTickableSoundInstance {
	public enum Layer { ROAR, WHINE, WIND }

	public static final SoundEvent ROAR = SoundEvent.createVariableRangeEvent(ProjectHeroMod.id("ironman_thruster_roar"));
	public static final SoundEvent WHINE = SoundEvent.createVariableRangeEvent(ProjectHeroMod.id("ironman_thruster_whine"));
	public static final SoundEvent WIND = SoundEvent.createVariableRangeEvent(ProjectHeroMod.id("ironman_thruster_wind"));

	private final Player player;
	private final Layer layer;
	private float fade;

	public IronManThrusterSoundInstance(Player player, Layer layer) {
		super(switch (layer) {
			case ROAR -> ROAR;
			case WHINE -> WHINE;
			case WIND -> WIND;
		}, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
		this.player = player;
		this.layer = layer;
		this.looping = true;
		this.delay = 0;
		this.volume = 0.0f;
		this.pitch = 1.0f;
		this.x = player.getX();
		this.y = player.getY();
		this.z = player.getZ();
	}

	@Override
	public boolean canStartSilent() {
		return true; // starts at volume 0 and fades in -- the engine would otherwise skip a silent start
	}

	@Override
	public void tick() {
		IronManFlightPose.Anim a = player.isRemoved() ? null : IronManFlightPose.anim(player);
		boolean on = a != null && a.flying;
		if (on) {
			fade = Math.min(1f, fade + 1f / IronManFlightLook.SOUND_FADE_IN_TICKS);
		} else {
			fade -= 1f / IronManFlightLook.SOUND_FADE_OUT_TICKS;
			if (fade <= 0f) {
				this.stop();
				return;
			}
		}
		double speed = a == null ? 0.0 : a.speed;
		boolean supersonic = a != null && a.supersonic;
		boolean boots = a != null && a.shownKind == IronManFlightPose.Kind.BOOTS;
		boolean markOne = a != null && a.shownKind == IronManFlightPose.Kind.MARK_ONE;
		float base;
		float pitch;
		switch (layer) {
			case ROAR -> {
				base = IronManFlightLook.thrusterVolume(speed, supersonic);
				pitch = IronManFlightLook.thrusterPitch(speed, supersonic) * (markOne ? 0.8f : 1f);
			}
			case WHINE -> {
				// the Mark 1 is crude rockets, no repulsor whine
				base = markOne ? 0f : IronManFlightLook.whineVolume(speed);
				pitch = 1.35f + (float) Math.min(1.0, speed / 2.0) * 0.3f;
			}
			default -> {
				base = IronManFlightLook.windVolume(speed);
				pitch = 0.8f + (float) Math.min(1.0, speed / 3.0) * 0.4f;
			}
		}
		this.volume = base * fade * (boots ? 0.6f : 1f);
		this.pitch = pitch;
		this.x = player.getX();
		this.y = player.getY();
		this.z = player.getZ();
	}
}
