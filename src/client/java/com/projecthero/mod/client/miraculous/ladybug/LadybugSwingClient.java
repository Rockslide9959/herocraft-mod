package com.projecthero.mod.client.miraculous.ladybug;

import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.data.MiraculousState;
import com.projecthero.mod.miraculous.ladybug.LadybugConfig;
import com.projecthero.mod.miraculous.ladybug.LadybugKeys;
import com.projecthero.mod.spider.SpiderManConfig;
import com.projecthero.mod.spider.SpiderSwing;
import com.projecthero.mod.spider.SpiderSwingInput;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6: the client half of the Yo-yo Swing / Zip Line, run on the local player at the start of every client tick
 * (before vanilla moves him) -- Spider-Man's split: the server picks the hook and ends the line, the owning client
 * moves. The swing is Spider-Man's own rope model ({@link SpiderSwing#applyRope}, with Jump / Sneak reeling in / out
 * through {@link SpiderSwing#adjustRope}); letting go in the air carries the arc's momentum like his release. The zip
 * pulls straight at the hook at {@link LadybugConfig#ZIP_SPEED}.
 */
public final class LadybugSwingClient {
	private static long lineAt;
	private static int was;
	private static double rope;

	private LadybugSwingClient() {
	}

	static void tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.isPaused()) {
			return;
		}
		MiraculousState s = LadybugKeys.state(p);
		int line = s == null ? 0 : (int) s.val(LadybugKeys.V_LINE);
		Vec3 hook = s == null ? null : Miraculous.vec(s, LadybugKeys.P_HOOK);
		if (line == 0 || hook == null) {
			if (was == 1 && !p.onGround() && !p.isInWater()) {
				SpiderManConfig.Moves cfg = SpiderManConfig.moves();
				Vec3 v = p.getDeltaMovement().scale(cfg.swingReleaseBoost).add(0.0, cfg.swingReleaseLift, 0.0);
				if (v.length() > cfg.swingMaxSpeed) {
					v = v.normalize().scale(cfg.swingMaxSpeed);
				}
				p.setDeltaMovement(v);
			} else if (was == 2) {
				// the zip arrives: a little hop up onto the ledge
				Vec3 v = p.getDeltaMovement();
				p.setDeltaMovement(v.x * 0.3, Math.max(v.y * 0.3, 0.42), v.z * 0.3);
			}
			was = 0;
			return;
		}
		Vec3 eye = p.getEyePosition();
		double dist = hook.distanceTo(eye);
		long at = s.val(LadybugKeys.V_LINE_AT);
		if (was != line || at != lineAt) {
			was = line;
			lineAt = at;
			rope = LadybugKeys.rope(s) > 0.0 ? LadybugKeys.rope(s) : dist;
			if (line == 1 && p.onGround()) {
				Vec3 v0 = p.getDeltaMovement();
				p.setDeltaMovement(v0.x, Math.max(v0.y, 0.48), v0.z);
			}
		}
		if (line == 2) {
			if (dist > LadybugConfig.ZIP_ARRIVE) {
				p.setDeltaMovement(hook.subtract(eye).normalize().scale(Math.min(LadybugConfig.ZIP_SPEED, dist * 0.6)));
			}
			p.resetFallDistance();
			return;
		}
		int input = SpiderSwingInput.pack(p.input.up, p.input.down, p.input.left, p.input.right, p.input.jumping, p.input.shiftKeyDown);
		rope = SpiderSwing.adjustRope(rope, dist, input);
		p.setDeltaMovement(SpiderSwing.applyRope(p, hook, rope, p.getDeltaMovement(), input, false, 0.0));
		p.resetFallDistance();
	}

	static void reset() {
		was = 0;
		lineAt = 0L;
		rope = 0.0;
	}
}
