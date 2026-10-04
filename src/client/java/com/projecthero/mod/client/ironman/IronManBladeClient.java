package com.projecthero.mod.client.ironman;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.ironman.IronManBladeLook;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/**
 * v0.14.21 round two: how far each player's Mark V blades are out, for every viewer. Stepped once per client tick from
 * the synced {@code IRON_MAN_BLADES} flag ({@link IronManBladeLook#step}, {@value IronManBladeLook#EXTEND_TICKS} ticks
 * each way) and interpolated across the partial tick, so the third-person bones and the first-person blade slide out
 * and back in instead of popping.
 */
public final class IronManBladeClient {
	/** entity id -> {previous tick, this tick} linear progress. */
	private static final Map<Integer, float[]> PROGRESS = new HashMap<>();

	private IronManBladeClient() {
	}

	public static void initialize() {
		ClientTickEvents.END_CLIENT_TICK.register(IronManBladeClient::tick);
	}

	private static void tick(Minecraft mc) {
		if (mc.level == null || mc.isPaused()) {
			if (mc.level == null) {
				PROGRESS.clear();
			}
			return;
		}
		Set<Integer> seen = new HashSet<>();
		for (Player p : mc.level.players()) {
			boolean out = p.getAttachedOrElse(ModAttachments.IRON_MAN_BLADES, false);
			float[] s = PROGRESS.get(p.getId());
			if (s == null) {
				if (!out) {
					continue;
				}
				s = new float[2];
				PROGRESS.put(p.getId(), s);
			}
			s[0] = s[1];
			s[1] = IronManBladeLook.step(s[1], out);
			seen.add(p.getId());
		}
		PROGRESS.keySet().removeIf(id -> !seen.contains(id) || PROGRESS.get(id)[0] == 0f && PROGRESS.get(id)[1] == 0f);
	}

	/** The drawn (eased) blade extension for {@code player}, 0 = stowed .. 1 = fully out. */
	public static float extension(Player player, float partialTick) {
		float[] s = PROGRESS.get(player.getId());
		if (s == null) {
			return 0f;
		}
		return IronManBladeLook.eased(s[0] + (s[1] - s[0]) * partialTick);
	}
}
