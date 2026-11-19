package com.projecthero.mod.client.symbiote;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.network.SymbioteWingsPayload;
import com.projecthero.mod.symbiote.SymbioteVitals;
import com.projecthero.mod.symbiote.SymbioteWings;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 Symbiote Wings, local player: the double-tap request ({@link #wantsWings} / {@link #requestWings}, from
 * {@code ProjectHeroModClient.handleDoubleJump}) and the flight itself, run at the start of every client tick (before the player moves),
 * like every other flight in the mod -- the client moves itself and the server accepts it.
 * <ul>
 *   <li><b>Flying</b> ({@link SymbioteWings#FLIGHT_TICKS}): toward the look direction at {@link #FLY_SPEED} blocks a tick, a strong
 *       lift on the first beat, a small lift on every beat (every 10 ticks); Space rises, Shift dives.</li>
 *   <li><b>Gliding</b> (the time is up, until landing): keeps its forward speed along the look (at least {@link #GLIDE_SPEED}), sinks
 *       at most 0.22 a tick; Shift dives.</li>
 * </ul>
 */
public final class SymbioteWingsClient {
	public static final double FLY_SPEED = 0.72;
	public static final double GLIDE_SPEED = 0.45;

	private SymbioteWingsClient() {
	}

	/** The double-tap in the air should grow the wings (the server re-checks everything). */
	public static boolean wantsWings(LocalPlayer player) {
		if (player == null || !SymbioteWings.canTryFrom(player)) {
			return false;
		}
		return !SymbioteWings.up(player);
	}

	public static void requestWings() {
		ClientPlayNetworking.send(new SymbioteWingsPayload());
	}

	public static void tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null || p.isPassenger() || p.isSpectator() || p.getAbilities().flying) {
			return;
		}
		SymbioteVitals v = p.getAttachedOrElse(ModAttachments.SYMBIOTE_VITALS, null);
		long now = p.level().getGameTime();
		SymbioteWings.Phase phase = SymbioteWings.phase(v, now);
		if (phase != SymbioteWings.Phase.FLYING && phase != SymbioteWings.Phase.GLIDING) {
			return;
		}
		if (p.onGround() && now - v.wingsStart > 3) {
			return; // landed: the server starts the dissolve
		}
		boolean up = mc.options.keyJump.isDown();
		boolean down = mc.options.keyShift.isDown();
		Vec3 cur = p.getDeltaMovement();
		Vec3 look = p.getLookAngle();
		if (phase == SymbioteWings.Phase.FLYING) {
			long a = now - v.wingsStart;
			Vec3 want = look.scale(FLY_SPEED);
			double lift = 0.2; // holds the height against gravity and drag when looking level
			if (a < 8) {
				lift += 0.75 * (1.0 - a / 8.0); // the strong first beat
			} else if (a % 10 < 3) {
				lift += 0.07; // each wing beat
			}
			if (up) {
				lift += 0.38;
			}
			if (down) {
				lift -= 0.6;
			}
			want = want.add(0, lift, 0);
			Vec3 next = cur.scale(0.72).add(want.scale(0.28));
			p.setDeltaMovement(next);
		} else {
			Vec3 flat = new Vec3(look.x, 0, look.z);
			flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 0) : flat.normalize();
			double h = Math.sqrt(cur.x * cur.x + cur.z * cur.z);
			double speed = Math.min(0.8, Math.max(GLIDE_SPEED, h * 0.995));
			double vy = down ? Math.max(cur.y - 0.08, -0.7) : Math.max(cur.y - 0.02, -0.22) + 0.08;
			p.setDeltaMovement(flat.x * speed, vy, flat.z * speed);
		}
		p.resetFallDistance();
	}
}
