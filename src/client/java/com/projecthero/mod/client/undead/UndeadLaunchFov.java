package com.projecthero.mod.client.undead;

import com.projecthero.mod.undead.Undead;
import com.projecthero.mod.undead.UndeadConfig;
import com.projecthero.mod.undead.data.UndeadState;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3: the first-person cue for Undead's X launches (from {@code UndeadLaunchFovMixin}): the view pulls in as the Head Launch
 * winds up, then kicks out as he goes (more for the head than for the Leg Launch). Driven by the synced animation.
 */
public final class UndeadLaunchFov {
	private UndeadLaunchFov() {
	}

	public static float scale(Player player) {
		UndeadState s = Undead.peek(player);
		if (s == null || !s.hasPower || player.level() == null) {
			return 1f;
		}
		float now = player.level().getGameTime() + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float age = now - s.animStart;
		if (age < 0f) {
			return 1f;
		}
		switch (s.animId) {
			case UndeadState.ANIM_HEAD_CHARGE -> {
				float span = UndeadConfig.HEAD_LAUNCH_HOLD - UndeadConfig.LAUNCH_CHARGE_SHOW;
				if (age > span + 10f) {
					return 1f;
				}
				float k = Mth.clamp(age / span, 0f, 1f);
				return 1f - 0.16f * k * k;
			}
			case UndeadState.ANIM_HEAD_LAUNCH -> {
				return age < 16f ? 1f + 0.22f * (1f - age / 16f) : 1f;
			}
			case UndeadState.ANIM_LEG_LAUNCH -> {
				return age < 10f ? 1f + 0.12f * (1f - age / 10f) : 1f;
			}
			default -> {
				return 1f;
			}
		}
	}
}
