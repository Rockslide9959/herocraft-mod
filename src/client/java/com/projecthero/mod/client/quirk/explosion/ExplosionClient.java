package com.projecthero.mod.client.quirk.explosion;

import com.projecthero.mod.client.quirk.QuirkClient;
import com.projecthero.mod.client.quirk.QuirkClientKit;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;
import com.projecthero.mod.quirk.explosion.Explosion;
import com.projecthero.mod.quirk.explosion.ExplosionConfig;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7: the Explosion Quirk's client side -- the poses ({@link ExplosionPose}), Blast Rush's flight
 * ({@link ExplosionRushClient}) and the Howitzer Impact spin. The blasts' particles and sounds all come from the server
 * (every viewer sees them); the costume is drawn by the core's costume layer from
 * {@code textures/entity/quirk/explosion_wide.png} / {@code _slim.png}.
 */
public final class ExplosionClient implements QuirkClientKit {
	private ExplosionClient() {
	}

	public static void initialize() {
		QuirkClient.register(Quirk.EXPLOSION, new ExplosionClient());
		ClientTickEvents.START_CLIENT_TICK.register(ExplosionRushClient::startTick);
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			ExplosionRushClient.endTick(mc);
			spinBodies(mc);
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ExplosionRushClient.reset());
	}

	/**
	 * Howitzer Impact: every spinning Bakugo in view turns his whole body round {@link ExplosionConfig#HOWITZER_SPIN_DEGREES}
	 * a tick (the body yaw and its previous value both set, so the render interpolates the turn; vanilla's head-turn
	 * clamp would otherwise pull it back each tick).
	 */
	static void spinBodies(Minecraft mc) {
		if (mc.level == null || mc.isPaused()) {
			return;
		}
		long now = mc.level.getGameTime();
		for (Player pl : mc.level.players()) {
			if (!Explosion.spinning(pl)) {
				continue;
			}
			QuirkState s = Quirks.peek(pl);
			if (s == null) {
				continue;
			}
			float deg = ExplosionConfig.HOWITZER_SPIN_DEGREES;
			float angle = pl.getYRot() + (now - s.animStart) * deg;
			pl.yBodyRotO = angle - deg;
			pl.yBodyRot = angle;
		}
	}

	@Override
	public void applyPose(Player player, HumanoidModel<?> model) {
		ExplosionPose.apply(player, model);
	}

	/** The costume is drawn over his own skin while it is on: his own second layer goes. */
	@Override
	public boolean hidesSkinOverlay(Player player) {
		return Quirks.costumeOn(player);
	}
}
