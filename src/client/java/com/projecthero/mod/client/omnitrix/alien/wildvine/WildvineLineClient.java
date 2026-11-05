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
 * v0.17.2: the client half of Wildvine's Vine Swing (X, held) -- Ladybug's {@code LadybugSwingClient} (user: "Make vine swing work
 * similar to ladybugs yoyo swing ability please"), run on the local player at the start of every client tick. The server picks the
 * hook and ends the vine ({@link WildvineKit#lineMode}, {@link WildvineKit#hook}); this moves him: Spider-Man's rope
 * ({@link SpiderSwing#applyRope}: Jump / Sneak reel in / out), and letting go in the air carries the arc's speed on. Also the Green Walk's
 * climb: he walks through trees, so he cannot press into them -- Jump held inside one climbs it instead.
 */
public final class WildvineLineClient {
	/** Green Walk: climbing up inside a tree, blocks a tick. */
	private static final double TREE_CLIMB = 0.22;
	private static long lineAt;
	private static boolean was;
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
		Vec3 hook = !his || WildvineKit.lineMode(p) == 0 ? null : WildvineKit.hook(p);
		if (hook == null) {
			if (his && was && !p.onGround() && !p.isInWater()) {
				SpiderManConfig.Moves cfg = SpiderManConfig.moves();
				Vec3 v = p.getDeltaMovement().scale(cfg.swingReleaseBoost).add(0.0, cfg.swingReleaseLift, 0.0);
				if (v.length() > cfg.swingMaxSpeed) {
					v = v.normalize().scale(cfg.swingMaxSpeed);
				}
				p.setDeltaMovement(v);
			}
			was = false;
			if (his && p.input.jumping && (Omnitrix.state(p).kitFlags & WildvineKit.F_BURROW) == 0 && WildvineKit.inPlant(p)) {
				Vec3 v = p.getDeltaMovement();
				p.setDeltaMovement(v.x, Math.max(v.y, TREE_CLIMB), v.z);
				p.resetFallDistance();
			}
			return;
		}
		Vec3 eye = p.getEyePosition();
		double dist = hook.distanceTo(eye);
		long at = WildvineKit.lineAt(p);
		if (!was || at != lineAt) {
			was = true;
			lineAt = at;
			rope = WildvineKit.rope(p) > 0.0 ? WildvineKit.rope(p) : dist;
			if (p.onGround()) {
				Vec3 v0 = p.getDeltaMovement();
				p.setDeltaMovement(v0.x, Math.max(v0.y, 0.48), v0.z); // off the ground into the arc
			}
		}
		int input = SpiderSwingInput.pack(p.input.up, p.input.down, p.input.left, p.input.right, p.input.jumping, p.input.shiftKeyDown);
		rope = SpiderSwing.adjustRope(rope, dist, input);
		p.setDeltaMovement(SpiderSwing.applyRope(p, hook, rope, p.getDeltaMovement(), input, false, 0.0));
		p.resetFallDistance();
	}

	static void reset() {
		was = false;
		lineAt = 0L;
		rope = 0.0;
	}
}
