package com.projecthero.mod.client.powerarmour;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.powerarmour.PowerArmour;
import com.projecthero.mod.powerarmour.PowerArmourMod;
import com.projecthero.mod.powerarmour.PowerArmourState;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 Power Armour, client: the frame renderer, the suit layer on players, the menus, the HUD, and the enter / exit walk --
 * while the sequence runs the local player's keys are held ({@link #holdsInput}) and {@link #drive} walks them along
 * {@link PowerArmour#drivenPose} (as velocity, so the legs walk). While in a suit H, N and the jump gestures are swallowed
 * ({@link #swallowHeroKeys}) and every other power's HUD column steps aside ({@code VanillaHud.muteOthers}).
 */
public final class PowerArmourClient {
	private PowerArmourClient() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(PowerArmourMod.FRAME, PowerArmourFrameRenderer::new);
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof PlayerRenderer playerRenderer) {
				helper.register(new PowerArmourLayer(playerRenderer));
			}
		});
		MenuScreens.register(PowerArmourMod.CHARGER_MENU, CoreChargerScreen::new);
		MenuScreens.register(PowerArmourMod.EDITOR_MENU, PowerArmourEditorScreen::new);
		HudRenderCallback.EVENT.register(PowerArmourHud::render);
		com.projecthero.mod.client.gui.VanillaHud.muteOthers = () -> {
			Player p = Minecraft.getInstance().player;
			// v0.17.5: and while the Falcon gear (the other Secondary power) owns the keys -- its HUD takes the corner then
			return p != null && (PowerArmour.ownsKeys(p) || com.projecthero.mod.falcon.Falcon.ownsKeys(p));
		};
		ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public ResourceLocation getFabricId() {
				return ProjectHeroMod.id("power_armour_meshes");
			}

			@Override
			public void onResourceManagerReload(ResourceManager manager) {
				SuitMesh.clearCache();
			}
		});
		PowerArmourFx.initialize();
	}

	/** In a suit (or stepping in / out): H, N and the Primary's own key handlers are swallowed this tick. */
	public static boolean swallowHeroKeys(Minecraft client) {
		if (client.player == null || !PowerArmour.ownsKeys(client.player)) {
			return false;
		}
		while (ModKeyBindings.POWER_SELECT.consumeClick()) {
			// the power selector waits until you step out
		}
		while (ModKeyBindings.MAX_STEEL_TRANSFORM.consumeClick()) {
			// N belongs to the Primary
		}
		return true;
	}

	/** The local player's movement keys are held: the sequence is walking them, or the Core Overload is winding up. */
	public static boolean holdsInput(Player player) {
		PowerArmourState s = PowerArmour.state(player);
		if (s == null) {
			return false;
		}
		if (s.busy()) {
			return true;
		}
		return s.sealed() && s.overloadAt > player.level().getGameTime() - 2;
	}

	/** Every input tick: held keys while the sequence walks you (or the overload winds up), the Thruster Pack's flight. */
	public static void input(LocalPlayer player, net.minecraft.client.player.Input in) {
		PowerArmourState s = PowerArmour.state(player);
		if (s == null) {
			return;
		}
		if (holdsInput(player)) {
			in.forwardImpulse = 0f;
			in.leftImpulse = 0f;
			in.up = false;
			in.down = false;
			in.left = false;
			in.right = false;
			in.jumping = false;
			in.shiftKeyDown = false;
			player.setSprinting(false);
			drive(player);
			return;
		}
		if (s.sealed() && s.thrusting) {
			// Thruster Pack: climb, steer along the look with WASD, Sneak to sink
			Vec3 v = player.getDeltaMovement();
			double vy = in.shiftKeyDown ? Math.max(v.y - 0.04, -0.25) : Math.min(v.y + 0.11, in.jumping ? 0.55 : 0.36);
			float yaw = player.getYRot() * Mth.DEG_TO_RAD;
			double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
			double ax = (fx * in.forwardImpulse + fz * in.leftImpulse) * 0.06, az = (fz * in.forwardImpulse - fx * in.leftImpulse) * 0.06;
			double hx = v.x + ax, hz = v.z + az;
			double h = Math.sqrt(hx * hx + hz * hz);
			if (h > 0.85) {
				hx *= 0.85 / h;
				hz *= 0.85 / h;
			}
			player.setDeltaMovement(hx, vy, hz);
			player.resetFallDistance();
		}
	}

	/** Walks the local player one tick along the sequence's path (velocity, so collisions and the walk cycle behave). */
	public static void drive(LocalPlayer player) {
		PowerArmourState s = PowerArmour.state(player);
		if (s == null || !s.busy()) {
			if (s != null && s.sealed()) {
				Vec3 v = player.getDeltaMovement();
				player.setDeltaMovement(0.0, Math.min(0.0, v.y), 0.0);
			}
			return;
		}
		double[] target = PowerArmour.drivenPose(s, player.level().getGameTime() + 1f);
		if (target == null) {
			return;
		}
		Vec3 v = new Vec3(target[0] - player.getX(), target[1] - player.getY(), target[2] - player.getZ());
		if (v.lengthSqr() > 9.0) {
			player.setPos(target[0], target[1], target[2]);
			v = Vec3.ZERO;
		}
		player.setDeltaMovement(v.x, Math.min(v.y, 0.0) + (v.y > 0 ? v.y : 0.0), v.z);
		float yaw = (float) target[3];
		float turned = Mth.rotLerp(0.5f, player.getYRot(), yaw);
		player.setYRot(turned);
		player.setYHeadRot(turned);
		player.yBodyRot = turned;
		player.setXRot(Mth.lerp(0.2f, player.getXRot(), 5f));
	}
}
