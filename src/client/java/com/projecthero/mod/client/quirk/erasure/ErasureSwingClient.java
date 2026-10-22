package com.projecthero.mod.client.quirk.erasure;

import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;
import com.projecthero.mod.quirk.erasure.ErasureConfig;
import com.projecthero.mod.spider.SpiderManConfig;
import com.projecthero.mod.spider.SpiderSwing;
import com.projecthero.mod.spider.SpiderSwingInput;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7: the client half of Capture Swing / Capture Zip, run on the local player at the start of every client tick
 * (Spider-Man's split: the server picks the hook and ends the line, the owning client moves). The swing is Spider-Man's
 * rope model ({@link SpiderSwing#applyRope}; Jump / Sneak reel in / out); the zip reels him straight at the creature.
 */
public final class ErasureSwingClient {
	private static long lineAt;
	private static int was;
	private static double rope;

	private ErasureSwingClient() {
	}

	static void tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.isPaused()) {
			return;
		}
		QuirkState s = Quirks.holds(p, Quirk.ERASURE) ? Quirks.peek(p) : null;
		int line = s == null ? 0 : (int) s.val(ErasureConfig.V_LINE);
		Vec3 hook = s == null ? null : Quirks.vec(s, ErasureConfig.P_HOOK);
		if (line == 2 && s != null && s.val(ErasureConfig.V_ZIP_TARGET) != 0L) {
			Entity t = p.level().getEntity((int) (s.val(ErasureConfig.V_ZIP_TARGET) - 1L));
			if (t != null) {
				hook = t.position().add(0, t.getBbHeight() * 0.5, 0);
			}
		}
		if (line == 0 || hook == null) {
			if (was == 1 && !p.onGround() && !p.isInWater()) {
				SpiderManConfig.Moves cfg = SpiderManConfig.moves();
				Vec3 v = p.getDeltaMovement().scale(cfg.swingReleaseBoost).add(0.0, cfg.swingReleaseLift, 0.0);
				if (v.length() > cfg.swingMaxSpeed) {
					v = v.normalize().scale(cfg.swingMaxSpeed);
				}
				p.setDeltaMovement(v);
			} else if (was == 2 || was == 3) {
				Vec3 v = p.getDeltaMovement();
				p.setDeltaMovement(v.x * 0.25, Math.max(v.y * 0.25, 0.25), v.z * 0.25);
			}
			was = 0;
			return;
		}
		Vec3 eye = p.getEyePosition();
		double dist = hook.distanceTo(eye);
		long at = s.val(ErasureConfig.V_LINE_AT);
		if (was != line || at != lineAt) {
			was = line;
			lineAt = at;
			double synced = s.val(ErasureConfig.V_ROPE) / 1000.0;
			rope = synced > 0.0 ? synced : dist;
			if (line == 1 && p.onGround()) {
				Vec3 v0 = p.getDeltaMovement();
				p.setDeltaMovement(v0.x, Math.max(v0.y, 0.48), v0.z);
			}
		}
		if (line == 2 || line == 3) {
			// Scarf Pull: reeled straight to the creature (2) or the latched point (3)
			if (dist > ErasureConfig.ZIP_ARRIVE) {
				p.setDeltaMovement(hook.subtract(eye).normalize().scale(Math.min(ErasureConfig.ZIP_SPEED, dist * 0.6)));
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
