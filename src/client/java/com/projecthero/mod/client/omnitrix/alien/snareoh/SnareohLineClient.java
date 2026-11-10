package com.projecthero.mod.client.omnitrix.alien.snareoh;

import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.snareoh.SnareohKit;
import com.projecthero.mod.spider.SpiderManConfig;
import com.projecthero.mod.spider.SpiderSwing;
import com.projecthero.mod.spider.SpiderSwingInput;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3: the client half of Snare-oh's Bandage Swing / Bandage Zip -- a copy of {@code SpidermonkeyLineClient} (itself Ladybug's
 * yo-yo line), run on the local player at the start of every client tick. The server picks the hook and ends the line
 * ({@link SnareohKit#lineMode}, {@link SnareohKit#hook}); this client moves him: the swing is Spider-Man's rope ({@link SpiderSwing#applyRope},
 * Jump / Sneak reel in / out) and letting go in the air carries the arc's speed on; the zip pulls straight at the hook
 * ({@link SnareohKit#zipVelocity}) and hops him up onto the ledge when it lets go.
 */
public final class SnareohLineClient {
	private static long lineAt;
	private static int was;
	private static double rope;
	private static boolean registered;

	private SnareohLineClient() {
	}

	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		ClientTickEvents.START_CLIENT_TICK.register(SnareohLineClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> reset());
	}

	static void tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.isPaused()) {
			return;
		}
		boolean his = SnareohKit.ID.equals(Omnitrix.active(p));
		int line = his ? SnareohKit.lineMode(p) : 0;
		Vec3 hook = line == 0 ? null : SnareohKit.hook(p);
		if (line == 0 || hook == null) {
			if (his && was == 1 && !p.onGround() && !p.isInWater()) {
				// let go mid-swing: carry the arc's speed on (Ladybug's / Spider-Man's release)
				SpiderManConfig.Moves cfg = SpiderManConfig.moves();
				Vec3 v = p.getDeltaMovement().scale(cfg.swingReleaseBoost).add(0.0, cfg.swingReleaseLift, 0.0);
				if (v.length() > cfg.swingMaxSpeed) {
					v = v.normalize().scale(cfg.swingMaxSpeed);
				}
				p.setDeltaMovement(v);
			} else if (his && was == 2) {
				// the zip arrives: a little hop up onto the ledge
				Vec3 v = p.getDeltaMovement();
				p.setDeltaMovement(v.x * 0.3, Math.max(v.y * 0.3, 0.42), v.z * 0.3);
			}
			was = 0;
			return;
		}
		Vec3 eye = p.getEyePosition();
		double dist = hook.distanceTo(eye);
		long at = SnareohKit.lineAt(p);
		if (was != line || at != lineAt) {
			was = line;
			lineAt = at;
			rope = SnareohKit.rope(p) > 0.0 ? SnareohKit.rope(p) : dist;
			if (line == 1 && p.onGround()) {
				Vec3 v0 = p.getDeltaMovement();
				p.setDeltaMovement(v0.x, Math.max(v0.y, 0.48), v0.z); // off the ground into the arc
			}
		}
		if (line == 2) {
			if (dist > SnareohKit.ZIP_ARRIVE) {
				p.setDeltaMovement(SnareohKit.zipVelocity(eye, hook));
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
