package com.projecthero.mod.omnitrix.alien;

import com.projecthero.mod.quirk.QuirkThreat;

import net.fabricmc.fabric.api.tag.convention.v2.ConventionalEntityTypeTags;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.core.Registry;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.1 (added with Stinkfly, Wildvine, Ripjaws and Grey Matter; backwards-compatible, nothing else depends on it): the small
 * status helpers the crowd-control aliens share -- a root (pinned in place), a stun, "lose track of me", the boss check
 * (bosses shrug a root down to a short one, never forget their target and keep their threat tables) and a sound registrar
 * for kit-owned sound constants.
 */
public final class AlienStatus {
	private AlienStatus() {
	}

	/** A boss: the conventional {@code c:bosses} tag, or anything big enough to be one (raid / world bosses here are all 150+). */
	public static boolean isBoss(LivingEntity e) {
		return e.getType().is(ConventionalEntityTypeTags.BOSSES) || e.getMaxHealth() >= 150f;
	}

	/**
	 * Roots {@code e} for {@code ticks} (Slowness X: no walking, no jumping off; a boss only for a quarter of it), and leaves a
	 * weaker {@code slowAfter}-tick Slowness II behind (vanilla keeps it as the hidden effect). A no-damage move: the boss's
	 * threat table still hears about it.
	 */
	public static void root(ServerPlayer by, LivingEntity e, int ticks, int slowAfter) {
		int t = isBoss(e) ? Math.max(5, ticks / 4) : ticks;
		if (slowAfter > 0) {
			e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, t + slowAfter, 1), by);
		}
		e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, t, 9, false, true, true), by);
		e.setDeltaMovement(0, Math.min(0, e.getDeltaMovement().y), 0);
		e.hurtMarked = true;
		QuirkThreat.add(e, by, 4f);
	}

	/** A short stun: rooted, Weakness (no real hits), and a mob forgets what it was doing. */
	public static void stun(ServerPlayer by, LivingEntity e, int ticks) {
		root(by, e, ticks, 0);
		int t = isBoss(e) ? Math.max(5, ticks / 4) : ticks;
		e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, t, 4, false, true, true), by);
		if (e instanceof Mob m && !isBoss(e)) {
			m.setTarget(null);
			m.getNavigation().stop();
		}
	}

	/** Whether {@code m} is hunting {@code p}. */
	public static boolean hunting(Mob m, Player p) {
		return m.getTarget() == p;
	}

	/** {@code m} loses track of {@code p} (never a boss: they keep their threat tables). True if it did. */
	public static boolean loseTrack(Mob m, Player p) {
		if (m.getTarget() != p || isBoss(m)) {
			return false;
		}
		m.setTarget(null);
		m.setLastHurtByMob(null);
		m.getNavigation().stop();
		return true;
	}

	/** Registers a kit-owned sound ({@code projecthero:<path>}). Call from a kit's static field initialisers. */
	public static SoundEvent sound(String path) {
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("projecthero", path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}
}
