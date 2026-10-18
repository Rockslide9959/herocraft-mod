package com.projecthero.mod.client.quirk.zerogravity;

import com.projecthero.mod.client.quirk.QuirkClient;
import com.projecthero.mod.client.quirk.QuirkClientKit;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.zerogravity.ZeroGravity;
import com.projecthero.mod.quirk.zerogravity.ZeroGravityConfig;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7: Zero Gravity's client side -- the poses ({@link ZeroGravityPose}) and Zero G Self's movement: the owning
 * client moves its player while she floats (the server turned her gravity off and keeps her from being kicked). At the
 * start of every client tick: Jump rises, Sneak sinks, otherwise she slows to a hover and drifts; a little more air
 * control than vanilla so she can steer.
 */
public final class ZeroGravityClient implements QuirkClientKit {
	private ZeroGravityClient() {
	}

	public static void initialize() {
		QuirkClient.register(Quirk.ZERO_GRAVITY, new ZeroGravityClient());
		ClientTickEvents.START_CLIENT_TICK.register(ZeroGravityClient::tick);
	}

	private static void tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.isPaused() || !ZeroGravity.selfFloating(p) || p.getAbilities().flying || p.isPassenger()) {
			return;
		}
		Vec3 v = p.getDeltaMovement();
		double vy;
		if (p.input.jumping) {
			vy = ZeroGravityConfig.SELF_RISE_SPEED;
		} else if (p.input.shiftKeyDown) {
			vy = -ZeroGravityConfig.SELF_SINK_SPEED;
		} else {
			vy = v.y * ZeroGravityConfig.SELF_HOVER_DRAG;
		}
		// steering: a gentle push the way the keys say, capped
		float fwd = p.input.forwardImpulse;
		float side = p.input.leftImpulse;
		double hx = v.x;
		double hz = v.z;
		if (fwd != 0f || side != 0f) {
			float yaw = p.getYRot() * Mth.DEG_TO_RAD;
			double sin = Mth.sin(yaw);
			double cos = Mth.cos(yaw);
			double ax = (side * cos - fwd * sin) * 0.03;
			double az = (fwd * cos + side * sin) * 0.03;
			hx += ax;
			hz += az;
			double h = Math.sqrt(hx * hx + hz * hz);
			if (h > 0.55) {
				hx *= 0.55 / h;
				hz *= 0.55 / h;
			}
		}
		p.setDeltaMovement(hx, vy, hz);
		p.resetFallDistance();
	}

	@Override
	public void applyPose(Player player, HumanoidModel<?> model) {
		ZeroGravityPose.apply(player, model);
	}

	@Override
	public boolean hidesSkinOverlay(Player player) {
		return Quirks.costumeOn(player);
	}
}
