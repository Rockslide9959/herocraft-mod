package com.projecthero.mod.client.omnitrix;

import java.util.HashMap;
import java.util.Map;

import org.joml.Vector3f;

import com.projecthero.mod.client.omnitrix.alien.FourArmsClient;
import com.projecthero.mod.client.omnitrix.alien.HeatblastClient;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixEntities;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.omnitrix.network.OmnitrixActionPayload;
import com.projecthero.mod.omnitrix.pod.OmnitrixPodBlocks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.1: the Omnitrix's client side -- the alien client kits, the renderers (the meteor, the fallen pod, the alien
 * projectile, the Containment Pod's field), the player layer (the watch on the wrist, the alien's extra cubes, the badge),
 * the HUD, the transform helix, and the watch's controls while it is up (Alien Evo style: no screen):
 * <ul>
 *   <li>the scroll wheel turns the dial ({@code CYCLE}; the hotbar does not move);</li>
 *   <li>a left-click or right-click slaps the core down ({@code SLAP}; the click never hits or breaks anything).</li>
 * </ul>
 * V itself is an ordinary ability key (the server toggles the watch).
 */
public final class OmnitrixClient {
	private static final DustParticleOptions GREEN = new DustParticleOptions(new Vector3f(0.35f, 1.0f, 0.3f), 0.8f);
	private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(1.0f, 0.2f, 0.15f), 0.8f);
	/** entity id -> the anim start the helix was last played for. */
	private static final Map<Integer, Long> HELIX_SEEN = new HashMap<>();
	/** The last time a click slapped (so a held button does not re-send every tick). */
	private static long lastSlap;

	private OmnitrixClient() {
	}

	public static void initialize() {
		// the alien client kits -- ONE LINE PER KIT (the other eight add theirs here)
		AlienClients.register(Aliens.HEATBLAST.id(), new HeatblastClient());
		AlienClients.register(Aliens.FOUR_ARMS.id(), new FourArmsClient());
		AlienClients.register(Aliens.STINKFLY.id(), new com.projecthero.mod.client.omnitrix.alien.StinkflyClient());
		AlienClients.register(Aliens.WILDVINE.id(), new com.projecthero.mod.client.omnitrix.alien.WildvineClient());
		AlienClients.register(Aliens.RIPJAWS.id(), new com.projecthero.mod.client.omnitrix.alien.RipjawsClient());
		AlienClients.register(Aliens.GREY_MATTER.id(), new com.projecthero.mod.client.omnitrix.alien.GreyMatterClient());

		EntityRendererRegistry.register(OmnitrixEntities.OMNITRIX_METEOR, OmnitrixRenderers.Meteor::new);
		EntityRendererRegistry.register(OmnitrixEntities.OMNITRIX_POD, OmnitrixRenderers.Pod::new);
		EntityRendererRegistry.register(OmnitrixEntities.ALIEN_PROJECTILE, OmnitrixRenderers.Projectile::new);
		BlockEntityRendererRegistry.register(OmnitrixPodBlocks.POD_BE, OmnitrixRenderers.PodField::new);
		net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(OmnitrixPodBlocks.POD,
				net.minecraft.client.renderer.RenderType.cutout());
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof PlayerRenderer pr) {
				helper.register(new OmnitrixLayer(pr));
			}
		});
		HudRenderCallback.EVENT.register(OmnitrixHud::render);
		ClientTickEvents.END_CLIENT_TICK.register(OmnitrixClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> HELIX_SEEN.clear());
	}

	// ---------------------------------------------------------------- the watch's controls while it is up

	static boolean watchUp(LocalPlayer p) {
		return p != null && Omnitrix.selecting(p) && Minecraft.getInstance().screen == null;
	}

	/** The scroll wheel: true if it turned the dial (the hotbar is left alone). */
	public static boolean onScroll(double dy) {
		LocalPlayer p = Minecraft.getInstance().player;
		if (!watchUp(p)) {
			return false;
		}
		if (dy != 0) {
			// scrolling down turns to the next alien, up to the previous one (the hotbar's own sense)
			ClientPlayNetworking.send(new OmnitrixActionPayload(OmnitrixActionPayload.Action.CYCLE, dy < 0 ? "1" : "-1"));
		}
		return true;
	}

	/** A left or right click while the watch is up: the slap. True = vanilla must not attack / use / mine. */
	public static boolean onClick() {
		LocalPlayer p = Minecraft.getInstance().player;
		if (!watchUp(p)) {
			return false;
		}
		long now = p.level().getGameTime();
		if (now - lastSlap > 4) {
			lastSlap = now;
			ClientPlayNetworking.send(new OmnitrixActionPayload(OmnitrixActionPayload.Action.SLAP, ""));
		}
		return true;
	}

	/** While the watch is up a held attack never mines. */
	public static boolean blocksHeldAttack() {
		return watchUp(Minecraft.getInstance().player);
	}

	/** Right-click on air with an empty hand while transformed (vanilla sends nothing for it): the kit's "use". */
	public static boolean onUseAir() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || !Omnitrix.transformed(p) || !p.getMainHandItem().isEmpty()
				|| (mc.hitResult != null && mc.hitResult.getType() != net.minecraft.world.phys.HitResult.Type.MISS)) {
			return false;
		}
		ClientPlayNetworking.send(new OmnitrixActionPayload(OmnitrixActionPayload.Action.USE_AIR, ""));
		p.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
		return true;
	}

	// ---------------------------------------------------------------- ticks: the helix, the kits' ambience

	private static void tick(Minecraft mc) {
		if (mc.level == null || mc.isPaused()) {
			if (mc.level == null) {
				HELIX_SEEN.clear();
			}
			return;
		}
		for (Player pl : mc.level.players()) {
			if (!(pl instanceof AbstractClientPlayer p) || !Omnitrix.hasPower(p)) {
				continue;
			}
			OmnitrixState s = Omnitrix.state(p);
			long now = mc.level.getGameTime();
			boolean helixAnim = s.animId == Omnitrix.ANIM_TRANSFORM || s.animId == Omnitrix.ANIM_REVERT || s.animId == Omnitrix.ANIM_TIMEOUT;
			boolean ownView = p == mc.player && mc.options.getCameraType().isFirstPerson();
			if (helixAnim && now - s.animStart < 14 && !ownView) {
				Long seen = HELIX_SEEN.get(p.getId());
				if (seen == null || seen != s.animStart || now - s.animStart < 14) {
					HELIX_SEEN.put(p.getId(), s.animStart);
					helix(p, (int) (now - s.animStart), s.animId == Omnitrix.ANIM_TIMEOUT);
				}
			}
			AlienClientKit kit = AlienClients.shownKit(p);
			if (kit != null) {
				kit.tick(p);
			}
		}
	}

	/** One tick of the DNA helix: two strands spiralling up the body, {@code t} ticks in. */
	private static void helix(AbstractClientPlayer p, int t, boolean red) {
		Minecraft mc = Minecraft.getInstance();
		double h = p.getBbHeight() * 1.1;
		double r = p.getBbWidth() * 0.95 + 0.25;
		for (int k = 0; k < 6; k++) {
			double f = (t + k / 6.0) / 14.0;
			double y = p.getY() + f * h;
			double a = f * Math.PI * 6;
			for (int strand = 0; strand < 2; strand++) {
				double aa = a + strand * Math.PI;
				mc.level.addParticle(red ? RED : GREEN, p.getX() + Math.cos(aa) * r, y, p.getZ() + Math.sin(aa) * r, 0, 0.01, 0);
			}
			if (k % 2 == 0) {
				// the rungs between the strands
				double rr = r * (k / 6.0 * 2 - 1);
				mc.level.addParticle(red ? RED : GREEN, p.getX() + Math.cos(a) * rr, y, p.getZ() + Math.sin(a) * rr, 0, 0, 0);
			}
		}
	}
}
