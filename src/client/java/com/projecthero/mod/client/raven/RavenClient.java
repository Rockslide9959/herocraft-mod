package com.projecthero.mod.client.raven;

import com.projecthero.mod.client.render.FlowingCapeLayer;
import com.projecthero.mod.flight.DirectionalFlightModel;
import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.raven.Raven;
import com.projecthero.mod.raven.RavenConfig;
import com.projecthero.mod.raven.entity.RavenEntities;
import com.projecthero.mod.raven.network.RavenActionPayload;
import com.projecthero.mod.raven.network.RavenFxPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/**
 * v0.17.3 Raven: the client side, called once from {@code ProjectHeroModClient}: the HUD (Dark Energy Slab, Composure Hairline, the
 * keys, the picked spell in the V box), the aura overlay (white casting eyes, four red eyes at Rage, Demonic Ascension's aura, the
 * Wrath's shadow shell) and the cloak + hood, the three entity renderers, the effects payload and the world-render effects, the hand
 * wisps, the double-tap-Space flight request, a left click firing a Debris Storm block, and V: a tap casts the picked spell, a hold
 * opens the {@link RavenSpellWheelScreen}. Mystical Awareness's outlines are {@code RavenAwarenessGlowMixin}. The poses are
 * {@code RavenPoseMixin}. H stays the power selector and N does nothing for her.
 */
public final class RavenClient {
	private RavenClient() {
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static void initialize() {
		HudRenderCallback.EVENT.register(RavenHud::render);
		EntityRendererRegistry.register(RavenEntities.DARK_BOLT, DarkBoltRenderer::new);
		EntityRendererRegistry.register(RavenEntities.SOUL_SELF, SoulSelfRenderer::new);
		EntityRendererRegistry.register(RavenEntities.DEBRIS, RavenDebrisRenderer::new);
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof PlayerRenderer pr) {
				helper.register(new RavenAuraLayer(pr));
			}
		});
		FlowingCapeLayer.register(RavenCloakLayer::new);
		ClientPlayNetworking.registerGlobalReceiver(RavenFxPayload.TYPE, (payload, context) -> RavenFx.receive(payload));
		WorldRenderEvents.AFTER_ENTITIES.register(RavenFx::render);
		ClientTickEvents.END_CLIENT_TICK.register(RavenFx::tick);
		ClientTickEvents.END_CLIENT_TICK.register(RavenClient::spellKey);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> RavenFx.reset());
		// a left click while Debris Storm blocks orbit her fires one (instead of swinging)
		ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> {
			var s = Raven.peek(player);
			if (clickCount > 0 && s != null && s.hasPower && s.debris > 0 && client.screen == null) {
				ClientPlayNetworking.send(new RavenActionPayload(RavenActionPayload.Action.FIRE_DEBRIS));
				return true;
			}
			return false;
		});
	}

	/** Ticks plain V has been held (-1 = up); whether this press opened the wheel; whether it started with Shift (Meditate). */
	private static int vHeld = -1;
	private static boolean vWheel;
	private static boolean vShift;

	/** V: a tap (let go within {@link RavenConfig#WHEEL_HOLD_TICKS}) casts the picked spell, a hold opens the wheel. */
	private static void spellKey(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null || !Raven.hasPower(p)) {
			vHeld = -1;
			return;
		}
		boolean v = ModKeyBindings.ABILITY_SLOTS[AbilitySlot.SLOT_5.ordinal()].isDown();
		if (v && mc.screen == null) {
			if (vHeld < 0) {
				vHeld = 0;
				vWheel = false;
				vShift = p.isShiftKeyDown() || Screen.hasShiftDown();
			} else {
				vHeld++;
			}
			if (!vShift && !vWheel && vHeld >= RavenConfig.WHEEL_HOLD_TICKS && p.isAlive() && !Raven.meditating(p)) {
				vWheel = true;
				mc.setScreen(new RavenSpellWheelScreen());
			}
		} else if (!v) {
			if (vHeld >= 0 && !vShift && !vWheel && mc.screen == null) {
				ClientPlayNetworking.send(new RavenActionPayload(RavenActionPayload.Action.CAST_SPELL));
			}
			vHeld = -1;
		}
	}

	/** Double-tap Space in the air as Raven: take off / land (the server re-checks). */
	public static boolean wantsFlightToggle(LocalPlayer player) {
		return player != null && Raven.hasPower(player) && !Raven.meditating(player) && (!player.onGround() || Raven.isFlying(player));
	}

	public static void toggleFlight() {
		ClientPlayNetworking.send(new RavenActionPayload(RavenActionPayload.Action.TOGGLE_FLIGHT));
	}

	/** Meditation's tune: no movement at all, she just floats. */
	public static DirectionalFlightModel.Tune still() {
		DirectionalFlightModel.Tune k = DirectionalFlightModel.kryptonian(false, false);
		return new DirectionalFlightModel.Tune(0.0, 0.0, 0.0, k.acceleration(), k.reverse(), k.idle(), k.maxHorizontal(), 0.0, false, 0, false);
	}
}
