package com.projecthero.mod.client.raven;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.client.quirk.QuirkFirstPersonPose;
import com.projecthero.mod.raven.Raven;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3 tester round ("Raven bubble doesn't show the player model"): in first person, inside the Dark Shield, nothing of her
 * showed -- just two wisps floating in the dome. Now, bare-handed, both arms rise into view, palms out, holding the dome up (what
 * everyone else sees in third person, {@code RavenPoseMixin}). Fed to the shared first-person arm poser
 * ({@link QuirkFirstPersonPose#current}). Local player only: the blend is a single float, not per-entity state.
 */
public final class RavenFirstPerson {
	private static float blend;
	private static long lastNanos;

	private RavenFirstPerson() {
	}

	public static @Nullable QuirkFirstPersonPose.Sample shield(Player p, float partial) {
		if (p != Minecraft.getInstance().player) {
			return null;
		}
		long now = System.nanoTime();
		float dt = lastNanos == 0L ? 0f : Math.min(0.1f, (now - lastNanos) / 1.0e9f);
		lastNanos = now;
		boolean up = Raven.shielding(p) && !Raven.inShadow(p);
		blend = Mth.clamp(blend + (up ? dt * 6f : -dt * 6f), 0f, 1f);
		if (blend <= 0f) {
			return null;
		}
		float k = blend * blend * (3f - 2f * blend);
		float breathe = Mth.sin((p.tickCount + partial) * 0.12f) * 2f;
		// {tick, main pitch/yaw/roll/x/y/z, off pitch/yaw/roll/x/y/z}: both fists up and in, palms toward the dome
		float[] v = { 0f, -58f + breathe, 20f, -8f, 0.06f, 0.1f, -0.04f, -58f - breathe, 20f, -8f, 0.06f, 0.1f, -0.04f };
		return new QuirkFirstPersonPose.Sample(v, k, true);
	}
}
