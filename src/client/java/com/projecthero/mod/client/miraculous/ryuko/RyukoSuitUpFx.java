package com.projecthero.mod.client.miraculous.ryuko;

import java.util.HashMap;
import java.util.Map;

import com.projecthero.mod.client.miraculous.MiraculousSequenceClient;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.MiraculousConfig;
import com.projecthero.mod.miraculous.data.MiraculousState;
import com.projecthero.mod.miraculous.ryuko.Ryuko;
import com.projecthero.mod.miraculous.ryuko.RyukoMoves;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3 (tester: the suit-up "could use some work"): Ryuko's quick change ("Bring the storm!") is her element's. While the
 * suit rises (its frontier glows in the element's colour, {@link RyukoRender#edgeColor}) a three-armed helix of that element
 * -- wind, water or lightning -- swirls up her body with the frontier, and a short element sound marks the start. Every
 * viewer's own client plays it off the synced state; the long scene ({@code RyukoSequenceClient}) has its own storm and is
 * left alone.
 */
public final class RyukoSuitUpFx {
	/** The formChangeAt each player's quick change was last sounded for (so it sounds once per change). */
	private static final Map<Integer, Long> SOUNDED = new HashMap<>();

	private RyukoSuitUpFx() {
	}

	static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null) {
			SOUNDED.clear();
			return;
		}
		if (mc.isPaused()) {
			return;
		}
		long now = level.getGameTime();
		for (Player p : level.players()) {
			MiraculousState s = Miraculous.peek(p);
			if (s == null || !s.hasPower || !s.transformed || !Kwami.LONGG.id.equals(s.kwami) || s.formChangeAt <= 0L) {
				continue;
			}
			long age = now - s.formChangeAt;
			if (age < 0L || age > MiraculousConfig.TRANSFORM_TICKS || MiraculousSequenceClient.suitCover(p, s, 0f) >= 0f) {
				continue;
			}
			int element = Ryuko.element(p);
			Long sounded = SOUNDED.get(p.getId());
			if (sounded == null || sounded != s.formChangeAt) {
				SOUNDED.put(p.getId(), s.formChangeAt);
				sound(level, p, element);
			}
			swirl(level, p, age / (float) MiraculousConfig.TRANSFORM_TICKS, (int) age, element);
		}
	}

	/** The short element sound as the change begins. */
	private static void sound(ClientLevel level, Player p, int element) {
		SoundEvent ev;
		float vol;
		float pitch;
		switch (element) {
			case Ryuko.WATER -> {
				ev = SoundEvents.PLAYER_SPLASH_HIGH_SPEED;
				vol = 0.7f;
				pitch = 1.3f;
			}
			case Ryuko.LIGHTNING -> {
				ev = SoundEvents.LIGHTNING_BOLT_IMPACT;
				vol = 0.45f;
				pitch = 1.7f;
			}
			default -> {
				ev = SoundEvents.ENDER_DRAGON_FLAP;
				vol = 0.8f;
				pitch = 1.5f;
			}
		}
		level.playLocalSound(p.getX(), p.getY() + 1.0, p.getZ(), ev, SoundSource.PLAYERS, vol, pitch, false);
	}

	/** Three arms of the element spiralling round her at the height the suit has reached ({@code k}, 0..1). */
	private static void swirl(ClientLevel level, Player p, float k, int age, int element) {
		double h = p.getBbHeight();
		double r = 0.75 - 0.2 * k;
		for (int arm = 0; arm < 3; arm++) {
			double a = age * 0.55 + arm * (Math.PI * 2.0 / 3.0);
			for (int j = 0; j < 2; j++) {
				double aa = a - j * 0.25;
				double y = p.getY() + h * Math.min(1.0, k + 0.05 - j * 0.04);
				double x = p.getX() + Math.cos(aa) * r;
				double z = p.getZ() + Math.sin(aa) * r;
				// moving along the swirl, a little upward
				double vx = -Math.sin(aa) * 0.06, vz = Math.cos(aa) * 0.06;
				level.addParticle(j == 0 ? element(element) : dust(element), x, y, z, vx, 0.02, vz);
			}
		}
	}

	private static ParticleOptions element(int element) {
		return switch (element) {
			case Ryuko.WATER -> ParticleTypes.FALLING_WATER;
			case Ryuko.LIGHTNING -> ParticleTypes.ELECTRIC_SPARK;
			default -> ParticleTypes.CLOUD;
		};
	}

	private static ParticleOptions dust(int element) {
		return switch (element) {
			case Ryuko.WATER -> RyukoMoves.WATER;
			case Ryuko.LIGHTNING -> RyukoMoves.BOLT;
			default -> RyukoMoves.WIND;
		};
	}
}
