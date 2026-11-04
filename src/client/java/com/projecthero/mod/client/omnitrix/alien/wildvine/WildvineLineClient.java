package com.projecthero.mod.client.omnitrix.alien.wildvine;

import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.wildvine.WildvineKit;
import com.projecthero.mod.spider.SpiderManConfig;
import com.projecthero.mod.spider.SpiderSwing;
import com.projecthero.mod.spider.SpiderSwingInput;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2: the client half of Wildvine's Vine Swing (X, held) and the zip of his Vine Grab (Shift+R at a block) -- Spidermonkey's
 * {@code SpidermonkeyLineClient} (itself Ladybug's), run on the local player at the start of every client tick. The server picks the
 * hook and ends the vine ({@link WildvineKit#lineMode}, {@link WildvineKit#hook}); this moves him: the swing is Spider-Man's rope
 * ({@link SpiderSwing#applyRope}: Jump / Sneak reel in / out), letting go in the air carries the arc's speed on; the zip pulls straight
 * at the hook ({@link WildvineKit#zipVelocity}) and hops him up onto the ledge when it lets go.
 */
public final class WildvineLineClient {
	private static long lineAt;
	private static int was;
	private static double rope;
	private static boolean registered;

	private WildvineLineClient() {
	}

	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		ClientTickEvents.START_CLIENT_TICK.register(WildvineLineClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> reset());
	}

	static void tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.isPaused()) {
			return;
		}
		boolean his = WildvineKit.ID.equals(Omnitrix.active(p));
		int line = his ? WildvineKit.lineMode(p) : 0;
		Vec3 hook = line == 0 ? null : WildvineKit.hook(p);
		if (line == 0 || hook == null) {
			if (his && was == 1 && !p.onGround() && !p.isInWater()) {
				SpiderManConfig.Moves cfg = SpiderManConfig.moves();
				Vec3 v = p.getDeltaMovement().scale(cfg.swingReleaseBoost).add(0.0, cfg.swingReleaseLift, 0.0);
				if (v.length() > cfg.swingMaxSpeed) {
					v = v.normalize().scale(cfg.swingMaxSpeed);
				}
				p.setDeltaMovement(v);
			} else if (his && was == 2) {
				Vec3 v = p.getDeltaMovement();
				p.setDeltaMovement(v.x * 0.3, Math.max(v.y * 0.3, 0.42), v.z * 0.3);
			}
			was = 0;
			return;
		}
		Vec3 eye = p.getEyePosition();
		double dist = hook.distanceTo(eye);
		long at = WildvineKit.lineAt(p);
		if (was != line || at != lineAt) {
			was = line;
			lineAt = at;
			rope = WildvineKit.rope(p) > 0.0 ? WildvineKit.rope(p) : dist;
			if (line == 1 && p.onGround()) {
				Vec3 v0 = p.getDeltaMovement();
				p.setDeltaMovement(v0.x, Math.max(v0.y, 0.48), v0.z); // off the ground into the arc
			}
		}
		if (line == 2) {
			if (dist > WildvineKit.ZIP_ARRIVE) {
				p.setDeltaMovement(WildvineKit.zipVelocity(eye, hook));
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
