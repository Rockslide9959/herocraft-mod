package com.projecthero.mod.client.quirk.engine;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.projecthero.mod.quirk.engine.Engine;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7 (revamp): the engines' running sound, for every Engine player in earshot -- two looping layers that follow him:
 * a <b>rumble</b> (the minecart roll, pitched up) and, from Gear 2 or in Recipro, a <b>whine</b> (the beacon hum, pitched
 * high). Both rise in pitch and volume with the gear, the momentum and his actual speed, so shifting up is heard. They
 * fade out when he drops to gear 0, stalls or leaves. Client-only state, dropped on disconnect ({@link #reset}).
 */
public final class EngineSound extends AbstractTickableSoundInstance {
	private static final Map<Integer, EngineSound[]> PLAYING = new HashMap<>();

	private final Player player;
	private final boolean whine;
	private float fade;

	private EngineSound(Player player, SoundEvent event, boolean whine) {
		super(event, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
		this.player = player;
		this.whine = whine;
		this.looping = true;
		this.delay = 0;
		this.volume = 0.01f;
		this.pitch = 0.6f;
		this.x = player.getX();
		this.y = player.getY();
		this.z = player.getZ();
	}

	/** The rumble's / whine's pitch for this drive (0..5), momentum (0..1) and speed (blocks a tick). */
	public static float pitchFor(boolean whine, float drive, float momentum, float speed) {
		if (whine) {
			return Mth.clamp(0.9f + 0.18f * drive + 0.25f * momentum + 0.2f * Math.min(1.5f, speed), 0.5f, 2.0f);
		}
		return Mth.clamp(0.55f + 0.2f * drive + 0.2f * momentum + 0.25f * Math.min(1.5f, speed), 0.5f, 2.0f);
	}

	@Override
	public void tick() {
		if (player.isRemoved() || !Engine.is(player)) {
			stop();
			return;
		}
		float drive = Engine.drive(player);
		float target = drive <= 0f ? 0f : whine ? (drive >= 2f ? 0.18f + 0.06f * drive : 0f) : 0.3f + 0.08f * drive;
		fade = Mth.approach(fade, target, 0.05f);
		if (fade <= 0.001f && drive <= 0f) {
			stop();
			return;
		}
		float speed = (float) Math.hypot(player.getX() - player.xo, player.getZ() - player.zo);
		this.volume = Math.max(0.01f, fade);
		this.pitch = pitchFor(whine, drive, Engine.momentum(player), speed);
		this.x = player.getX();
		this.y = player.getY() + 0.4;
		this.z = player.getZ();
	}

	/** Each client tick: start the sounds for any Engine player in gear that has none playing. */
	static void tick(Minecraft mc) {
		if (mc.level == null) {
			reset();
			return;
		}
		var sm = mc.getSoundManager();
		Iterator<Map.Entry<Integer, EngineSound[]>> it = PLAYING.entrySet().iterator();
		while (it.hasNext()) {
			EngineSound[] pair = it.next().getValue();
			if (pair[0].isStopped() && pair[1].isStopped()) {
				it.remove();
			}
		}
		for (Player p : mc.level.players()) {
			if (!Engine.is(p) || Engine.drive(p) <= 0f || p.isSpectator() || PLAYING.containsKey(p.getId())) {
				continue;
			}
			EngineSound rumble = new EngineSound(p, SoundEvents.MINECART_RIDING, false);
			EngineSound whine = new EngineSound(p, SoundEvents.BEACON_AMBIENT, true);
			PLAYING.put(p.getId(), new EngineSound[] { rumble, whine });
			sm.play(rumble);
			sm.play(whine);
		}
	}

	static void reset() {
		for (EngineSound[] pair : PLAYING.values()) {
			pair[0].stop();
			pair[1].stop();
		}
		PLAYING.clear();
	}
}
