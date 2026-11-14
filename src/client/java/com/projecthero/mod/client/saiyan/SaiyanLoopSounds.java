package com.projecthero.mod.client.saiyan;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.saiyan.Saiyan;
import com.projecthero.mod.saiyan.SaiyanForm;
import com.projecthero.mod.saiyan.SaiyanSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3: the Saiyan's looping sounds, for every Saiyan in earshot (the server plays only the one-shots): the aura hum (gold
 * in Super Saiyan, red under Kaioken, none in a plain Base) and the charge loop while V charges (after the power-up swell,
 * about 1.5 s in). Each follows its player and fades out when its state ends.
 *
 * <p>v0.17.4: with the Dragon Ball test clips the aura is the charge's own loop (user: "make it the same sound as the charging
 * sfx"), so while the charge loop plays the aura fades out and comes back after -- one roar, never the same clip twice.
 */
public final class SaiyanLoopSounds {
	private static final float AURA_VOLUME = 0.35f;
	private static final float CHARGE_VOLUME = 0.45f;
	/** The charge loop starts this long into a charge (KI_CHARGE_START, the server's swell, plays first). */
	private static final int CHARGE_LOOP_DELAY = 30;
	private static final double RANGE = 32.0;

	private static final Map<UUID, Loop> AURA = new HashMap<>();
	private static final Map<UUID, Loop> CHARGE = new HashMap<>();
	private static final Map<UUID, Integer> CHARGE_TICKS = new HashMap<>();

	private SaiyanLoopSounds() {
	}

	public static void reset() {
		AURA.values().forEach(Loop::end);
		CHARGE.values().forEach(Loop::end);
		AURA.clear();
		CHARGE.clear();
		CHARGE_TICKS.clear();
	}

	/** The aura hum: red under Kaioken, gold in Super Saiyan, none in a plain Base. */
	static SoundEvent auraOf(Player p) {
		if (Saiyan.kaioken(p)) {
			return SaiyanSounds.AURA_LOOP_RED;
		}
		return Saiyan.form(p) != SaiyanForm.BASE ? SaiyanSounds.AURA_LOOP_GOLD : null; // (Ultra Instinct shares the hum)
	}

	public static void tick(Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			if (!AURA.isEmpty() || !CHARGE.isEmpty()) {
				reset();
			}
			return;
		}
		AURA.entrySet().removeIf(e -> e.getValue().isStopped());
		CHARGE.entrySet().removeIf(e -> e.getValue().isStopped());
		for (Player p : mc.level.players()) {
			if (!Saiyan.hasPower(p) || p.distanceToSqr(mc.player) > RANGE * RANGE) {
				CHARGE_TICKS.remove(p.getUUID());
				continue;
			}
			UUID id = p.getUUID();
			SoundEvent aura = auraOf(p);
			Loop a = AURA.get(id);
			if (aura != null && !CHARGE.containsKey(id) && (a == null || a.event != aura)) {
				if (a != null) {
					a.end();
				}
				Loop n = new Loop(p, aura, AURA_VOLUME, () -> auraOf(p) == aura && !CHARGE.containsKey(id));
				AURA.put(id, n);
				mc.getSoundManager().play(n);
			}
			if (Saiyan.charging(p)) {
				int t = CHARGE_TICKS.merge(id, 1, Integer::sum);
				if (t >= CHARGE_LOOP_DELAY && !CHARGE.containsKey(id)) {
					Loop n = new Loop(p, SaiyanSounds.KI_CHARGE_LOOP, CHARGE_VOLUME, () -> Saiyan.charging(p));
					CHARGE.put(id, n);
					mc.getSoundManager().play(n);
				}
			} else {
				CHARGE_TICKS.remove(id);
			}
		}
	}

	/** A loop that follows its player while {@code alive} holds, then fades out over a few ticks. */
	private static final class Loop extends AbstractTickableSoundInstance {
		private final Player player;
		final SoundEvent event;
		private final float full;
		private final java.util.function.BooleanSupplier alive;
		private boolean ending;

		Loop(Player player, SoundEvent event, float volume, java.util.function.BooleanSupplier alive) {
			super(event, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
			this.player = player;
			this.event = event;
			this.full = volume;
			this.alive = alive;
			this.looping = true;
			this.delay = 0;
			this.volume = 0.01f;
			this.x = player.getX();
			this.y = player.getY() + 1.0;
			this.z = player.getZ();
		}

		void end() {
			ending = true;
		}

		@Override
		public void tick() {
			if (player.isRemoved() || !Saiyan.hasPower(player) || !alive.getAsBoolean()) {
				ending = true;
			}
			this.x = player.getX();
			this.y = player.getY() + 1.0;
			this.z = player.getZ();
			if (ending) {
				this.volume -= full / 6f;
				if (this.volume <= 0.01f) {
					this.stop();
				}
			} else if (this.volume < full) {
				this.volume = Math.min(full, this.volume + full / 8f);
			}
		}
	}
}
