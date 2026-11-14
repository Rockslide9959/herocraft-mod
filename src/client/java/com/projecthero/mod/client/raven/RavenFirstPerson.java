package com.projecthero.mod.client.raven;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.client.quirk.QuirkFirstPersonPose;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3 Raven, first person: her bare arms, fed to the shared first-person arm poser ({@link QuirkFirstPersonPose#current}).
 * v0.17.4: every move and spell, not only the Soul Shield and the wave ({@link RavenPose#firstPerson}: the bolt flick, the hand on a
 * Telekinesis hold, both hands lifting and slamming for the Crush, the wave gathering and flung out, the spells...). In flight the
 * arms stay vanilla. Local player only.
 */
public final class RavenFirstPerson {
	private RavenFirstPerson() {
	}

	/** (Kept under its v0.17.3 name, which {@code QuirkFirstPersonPose} calls.) */
	public static @Nullable QuirkFirstPersonPose.Sample shield(Player p, float partial) {
		if (p != Minecraft.getInstance().player) {
			return null;
		}
		return RavenPose.firstPerson(p, partial);
	}
}
