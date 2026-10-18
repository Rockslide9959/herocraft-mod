package com.projecthero.mod.client.miraculous.pegasus;

import com.projecthero.mod.miraculous.pegasus.Pegasus;
import com.projecthero.mod.miraculous.pegasus.PegasusConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6, Shift+V (Sky Gallop): the client half -- the owning client moves its player (the server only times it and
 * keeps him from being kicked for floating). At the start of every client tick, while it runs: no falling (he stands on
 * the air as on a floor, so he walks and sprints at ground speed and can jump -- each jump climbs, and he stays at the
 * top), Sneak sinks him at {@link PegasusConfig#SKY_SINK_SPEED}.
 */
public final class PegasusSkyClient {
	private PegasusSkyClient() {
	}

	static void tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.isPaused() || !Pegasus.skyGalloping(p) || p.isInWater() || p.getAbilities().flying) {
			return;
		}
		Vec3 v = p.getDeltaMovement();
		if (p.input.shiftKeyDown) {
			p.setDeltaMovement(v.x, -PegasusConfig.SKY_SINK_SPEED, v.z);
		} else if (v.y < 0.0) {
			p.setDeltaMovement(v.x, 0.0, v.z);
			p.setOnGround(true); // the air holds him: ground friction, ground speed, and Jump works
		}
		p.resetFallDistance();
	}
}
