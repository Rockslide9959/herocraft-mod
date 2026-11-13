package com.projecthero.mod.client.raven;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.client.quirk.QuirkFirstPersonPose;
import com.projecthero.mod.raven.Raven;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3 Raven, first person: bare-handed, both arms rise into view, palms out, as the Soul Shield snaps up and while the Dark
 * Energy Wave gathers (what everyone else sees in third person, {@code RavenPoseMixin}). Fed to the shared first-person arm poser
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
		var s = Raven.peek(p);
		long gt = p.level().getGameTime();
		boolean guardUp = s != null && s.guardUntil > gt && gt - (s.guardUntil - com.projecthero.mod.raven.RavenConfig.GUARD_TICKS) < 14;
		boolean up = (guardUp || RavenFx.running(com.projecthero.mod.raven.network.RavenFxPayload.CHARGE, p.getId(), gt)) && !Raven.inShadow(p);
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
