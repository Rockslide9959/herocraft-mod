package com.projecthero.mod.client.starfire;

import com.projecthero.mod.client.eyebeam.EyeBeamFx;
import com.projecthero.mod.starfire.Starfire;
import com.projecthero.mod.starfire.StarfireConfig;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Starfire, Shift+G Eye Beams: two green beams from her eyes to whatever they hit, drawn since v0.17.5 by the shared
 * eye-beam look ({@link EyeBeamFx#STARFIRE}). Driven by the synced {@code beams} flag, so every player sees everyone's beams; each
 * client ray-casts the far end along the shooter's interpolated look. Hidden while locked in a clash ({@code BeamClashClient.hides}).
 */
public final class StarfireBeamRenderer {
	private StarfireBeamRenderer() {
	}

	static void render(WorldRenderContext context) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		for (Player player : level.players()) {
			if (Starfire.beaming(player) && !com.projecthero.mod.client.beamclash.BeamClashClient.hides(player)) {
				Vec3 eye = player.getEyePosition(partial);
				Vec3 dir = player.getViewVector(partial);
				EyeBeamFx.twin(player, 0, EyeBeamFx.STARFIRE, dir, EyeBeamFx.ray(level, player, eye, dir, StarfireConfig.BEAM_RANGE, true), 1.1f, 1f);
			}
		}
	}
}
