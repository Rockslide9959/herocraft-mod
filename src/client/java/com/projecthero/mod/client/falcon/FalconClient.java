package com.projecthero.mod.client.falcon;

import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.falcon.Falcon;
import com.projecthero.mod.falcon.FalconConfig;
import com.projecthero.mod.falcon.entity.FalconEntityTypes;
import com.projecthero.mod.hero.AbilitySlot;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;

/**
 * v0.17.1: the Falcon's client side -- the suit / Wing Pack layer, Redwing's renderer, the HUD, the wingsuit flight
 * ({@link FalconFlightClient}) and the Tactical Goggles: every hostile creature within 60 blocks outlined red through walls
 * (this client only, through the shared mark mixins), and holding V with the goggles on zooms the view.
 */
public final class FalconClient {
	public static final int RED = 0xFF3040;

	private static int vHeld;
	private static int markedCount;

	private FalconClient() {
	}

	public static void initialize() {
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof PlayerRenderer playerRenderer) {
				helper.register(new FalconLayer(playerRenderer));
			}
		});
		EntityRendererRegistry.register(FalconEntityTypes.REDWING, RedwingRenderer::new);
		HudRenderCallback.EVENT.register(FalconHud::render);
		ClientTickEvents.END_CLIENT_TICK.register(FalconClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			FalconRender.clearSessionState();
			FalconFlightClient.reset();
			vHeld = 0;
			markedCount = 0;
		});
	}

	private static void tick(Minecraft mc) {
		FalconFlightClient.tick(mc);
		if (mc.player == null || mc.level == null) {
			vHeld = 0;
			return;
		}
		boolean v = Falcon.goggles(mc.player) && ModKeyBindings.ABILITY_SLOTS[AbilitySlot.SLOT_5.ordinal()].isDown() && !Screen.hasShiftDown()
				&& mc.screen == null;
		vHeld = v ? vHeld + 1 : 0;
		if (Falcon.goggles(mc.player) && mc.level.getGameTime() % 10 == 0) {
			int n = 0;
			for (Entity e : mc.level.entitiesForRendering()) {
				if (marked(e)) {
					n++;
				}
			}
			markedCount = n;
		}
	}

	/** How many creatures the goggles mark (for the HUD hint). */
	public static int markedCount() {
		return markedCount;
	}

	/** The goggles outline this creature (hostile, within 60 blocks, the local player's goggles on). */
	public static boolean marked(Entity e) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || e == mc.player || !(e instanceof LivingEntity le) || !le.isAlive() || !Falcon.goggles(mc.player)) {
			return false;
		}
		if (!(e instanceof Enemy) && !(e instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() == mc.player)) {
			return false;
		}
		return e.distanceToSqr(mc.player) <= FalconConfig.GOGGLES_RANGE * FalconConfig.GOGGLES_RANGE;
	}

	/** The goggles' zoom: V held with them on. 1 = none. */
	public static float fovScale() {
		if (vHeld < FalconConfig.GOGGLES_HOLD_TICKS) {
			return 1f;
		}
		float t = Math.min(1f, (vHeld - FalconConfig.GOGGLES_HOLD_TICKS) / 5f);
		return 1f - 0.7f * t;
	}
}
