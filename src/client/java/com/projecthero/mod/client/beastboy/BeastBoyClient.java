package com.projecthero.mod.client.beastboy;

import org.joml.Vector3f;

import com.projecthero.mod.beastboy.BeastBoy;
import com.projecthero.mod.beastboy.network.BeastBoyFxPayload;
import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.hero.AbilitySlot;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.SoundType;

/**
 * v0.17.3 Beast Boy: the client side, called once from {@code ProjectHeroModClient}: the HUD, the green tint layer, the V Animal
 * Wheel (opened here while V is held -- the key's press still goes to the server, which ignores it), the roar's screen shake,
 * and two of the senses:
 * <ul>
 *   <li><b>Animal Senses</b> (Shift+V): every moving creature and player within 32 blocks leaves glowing green footprints.</li>
 *   <li><b>Instincts</b>: hostile mobs' footsteps are heard from twice as far (quiet, directional, with subtitles).</li>
 * </ul>
 * The bodies are drawn through {@code BeastBoyPlayerRendererMixin}; the green ocelot through {@code BeastBoyOcelotMixin}.
 */
public final class BeastBoyClient {
	private static final DustParticleOptions PRINT = new DustParticleOptions(new Vector3f(0.42f, 1.0f, 0.3f), 0.9f);
	private static boolean vWasDown;

	private BeastBoyClient() {
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static void initialize() {
		HudRenderCallback.EVENT.register(BeastBoyHud::render);
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof PlayerRenderer pr) {
				helper.register(new BeastTintLayer(pr));
				helper.register(new BeastQuickLayer(pr)); // morph round: quick-morph animal parts on his model
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(BeastBoyFxPayload.TYPE, (payload, context) -> {
			if (payload.kind() == BeastBoyFxPayload.SHAKE) {
				com.projecthero.mod.client.quirk.QuirkCameraFx.shake(payload.strength(), payload.ticks());
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(BeastBoyClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			BeastGreen.clear();
			BeastBodyRenderer.clear();
			com.projecthero.mod.client.beastboy.morph.BeastMorph.clear();
		});
	}

	private static void tick(Minecraft mc) {
		com.projecthero.mod.client.beastboy.morph.BeastMorph.clientTick(mc); // morph round: sparks for everyone mid-morph
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null) {
			vWasDown = false;
			return;
		}
		if (!BeastBoy.hasPower(p)) {
			vWasDown = false;
			return;
		}
		// hold V: the Animal Wheel
		boolean v = ModKeyBindings.ABILITY_SLOTS[AbilitySlot.SLOT_5.ordinal()].isDown();
		if (v && !vWasDown && mc.screen == null && !Screen.hasShiftDown() && !p.isShiftKeyDown() && !BeastBoy.isBeast(p) && p.isAlive()) {
			mc.setScreen(new BeastWheelScreen());
		}
		vWasDown = v;
		if (BeastBoy.sensing(p) && p.tickCount % 3 == 0) {
			footprints(mc, p);
		}
		if (p.tickCount % 4 == 0) {
			farFootsteps(mc, p);
		}
	}

	/** Animal Senses: a green print under the feet of everything moving nearby (it fades like a scent). */
	private static void footprints(Minecraft mc, LocalPlayer p) {
		for (LivingEntity e : mc.level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(32.0), e -> e != p && e.isAlive())) {
			double dx = e.getX() - e.xOld, dz = e.getZ() - e.zOld;
			if (dx * dx + dz * dz < 1.0e-4) {
				continue;
			}
			mc.level.addParticle(PRINT, e.getX(), e.getY() + 0.06, e.getZ(), 0, 0, 0);
		}
	}

	/** Instincts: a hostile mob walking 16-32 blocks away is heard (its step, played quietly at its position). */
	private static void farFootsteps(Minecraft mc, LocalPlayer p) {
		int played = 0;
		for (LivingEntity e : mc.level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(32.0),
				e -> e instanceof Enemy && e.isAlive() && e.onGround())) {
			double d2 = e.distanceToSqr(p);
			if (d2 < 16 * 16 || d2 > 32 * 32) {
				continue;
			}
			double dx = e.getX() - e.xOld, dz = e.getZ() - e.zOld;
			if (dx * dx + dz * dz < 0.002 || (e.tickCount + e.getId()) % 8 >= 4) {
				continue;
			}
			SoundType type = e.getBlockStateOn().getSoundType();
			mc.level.playLocalSound(e.getX(), e.getY(), e.getZ(), type.getStepSound(), SoundSource.HOSTILE, 2.0f /* a volume over 1 carries 32 blocks; it fades with distance */,
					type.getPitch(), false);
			if (++played >= 3) {
				break;
			}
		}
	}
}
