package com.projecthero.mod.client.omnitrix;

import com.projecthero.mod.client.omnitrix.alien.FourArmsClient;
import com.projecthero.mod.client.omnitrix.alien.HeatblastClient;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixEntities;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.network.OmnitrixActionPayload;
import com.projecthero.mod.omnitrix.pod.OmnitrixPodBlocks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.1: the Omnitrix's client side -- the alien client kits, the renderers (the meteor, the fallen pod, the alien
 * projectile, the Containment Pod's field), the player layer (the watch on the wrist, the alien's extra cubes, the badge),
 * the HUD, the transformation flash ({@link OmnitrixTransformFx}), and the watch's controls while it is up (Alien Evo style: no screen):
 * <ul>
 *   <li>the scroll wheel turns the dial ({@code CYCLE}; the hotbar does not move);</li>
 *   <li>a left-click or right-click slaps the core down ({@code SLAP}; the click never hits or breaks anything).</li>
 * </ul>
 * V itself is an ordinary ability key (the server toggles the watch).
 */
public final class OmnitrixClient {
	/** The last time a click slapped (so a held button does not re-send every tick). */
	private static long lastSlap;

	private OmnitrixClient() {
	}

	public static void initialize() {
		// the alien client kits -- ONE LINE PER KIT (the other eight add theirs here)
		AlienClients.register(Aliens.HEATBLAST.id(), new HeatblastClient());
		AlienClients.register(Aliens.FOUR_ARMS.id(), new FourArmsClient());
		AlienClients.register(Aliens.STINKFLY.id(), new com.projecthero.mod.client.omnitrix.alien.StinkflyClient());
		AlienClients.register(Aliens.WILDMUTT.id(), new com.projecthero.mod.client.omnitrix.alien.WildmuttClient());
		AlienClients.register(Aliens.RIPJAWS.id(), new com.projecthero.mod.client.omnitrix.alien.RipjawsClient());
		AlienClients.register(Aliens.GREY_MATTER.id(), new com.projecthero.mod.client.omnitrix.alien.GreyMatterClient());
		AlienClients.register(Aliens.XLR8.id(), new com.projecthero.mod.client.omnitrix.alien.Xlr8Client());
		AlienClients.register(Aliens.DIAMONDHEAD.id(), new com.projecthero.mod.client.omnitrix.alien.DiamondheadClient());
		AlienClients.register(Aliens.UPGRADE.id(), new com.projecthero.mod.client.omnitrix.alien.UpgradeClient());
		AlienClients.register(Aliens.GHOSTFREAK.id(), new com.projecthero.mod.client.omnitrix.alien.GhostfreakClient());
		com.projecthero.mod.client.omnitrix.alien.CannonboltClient.register(); // v0.17.2
		com.projecthero.mod.client.omnitrix.alien.CannonboltClient.register(); // v0.17.2
		com.projecthero.mod.client.omnitrix.alien.WayBigClient.register(); // v0.17.2
		com.projecthero.mod.client.omnitrix.alien.WildvineClient.register(); // v0.17.2
		com.projecthero.mod.client.omnitrix.alien.DittoClient.register(); // v0.17.2
		com.projecthero.mod.client.omnitrix.alien.BuzzshockClient.register(); // v0.17.2
		// v0.17.2: the Recalibrated Omnitrix's ten
		com.projecthero.mod.client.omnitrix.alien.SwampfireClient.register();
		com.projecthero.mod.client.omnitrix.alien.EchoEchoClient.register();
		com.projecthero.mod.client.omnitrix.alien.HumungousaurClient.register();
		com.projecthero.mod.client.omnitrix.alien.JetrayClient.register();
		com.projecthero.mod.client.omnitrix.alien.BigChillClient.register();
		com.projecthero.mod.client.omnitrix.alien.ChromastoneClient.register();
		com.projecthero.mod.client.omnitrix.alien.BrainstormClient.register();
		com.projecthero.mod.client.omnitrix.alien.SpidermonkeyClient.register();
		com.projecthero.mod.client.omnitrix.alien.GoopClient.register();
		com.projecthero.mod.client.omnitrix.alien.AlienXClient.register();

		EntityRendererRegistry.register(OmnitrixEntities.OMNITRIX_METEOR, OmnitrixRenderers.Meteor::new);
		EntityRendererRegistry.register(OmnitrixEntities.OMNITRIX_POD, OmnitrixRenderers.Pod::new);
		EntityRendererRegistry.register(OmnitrixEntities.ALIEN_PROJECTILE, OmnitrixRenderers.Projectile::new);
		HeatblastClient.init(); // v0.17.2: his shots in Pyrokinesis' fireball look, the Fire Stream as its Flamethrower
		com.projecthero.mod.client.omnitrix.alien.StinkflyClient.init(); // v0.17.2: goo blobs, the spinning slime net, their drips
		com.projecthero.mod.client.omnitrix.alien.GhostfreakClient.init(); // v0.17.2: a possessed player's client takes Ghostfreak's keys
		com.projecthero.mod.client.omnitrix.alien.UpgradeFx.init(); // v0.17.2: Upgrade's merge coat + takeover, beams, tendrils, EMP, puddle
		com.projecthero.mod.client.omnitrix.alien.wildmutt.WildmuttVision.init(); // v0.17.2: his blind sense -- echolocation in the dark
		com.projecthero.mod.client.omnitrix.alien.wildmutt.WildmuttSense.init(); // v0.17.2 revamp: the roar's ping outlines
		BlockEntityRendererRegistry.register(OmnitrixPodBlocks.POD_BE, OmnitrixRenderers.PodField::new);
		// the loose watch is drawn as its 3D model everywhere
		net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry.INSTANCE.register(
				com.projecthero.mod.omnitrix.item.OmnitrixItems.OMNITRIX, new OmnitrixItemRenderer());
		net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry.INSTANCE.register(
				com.projecthero.mod.omnitrix.item.OmnitrixItems.RECALIBRATED_OMNITRIX, new OmnitrixItemRenderer()); // v0.17.2
		net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(OmnitrixPodBlocks.POD,
				net.minecraft.client.renderer.RenderType.cutout());
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof PlayerRenderer pr) {
				helper.register(new OmnitrixLayer(pr));
			}
		});
		HudRenderCallback.EVENT.register(OmnitrixHud::render);
		OmnitrixTransformFx.initialize();
		OmnitrixSequenceClient.initialize(); // v0.17.2: the long (right-click) transformation
		// v0.17.2: the Recalibration Chip's Yes / No
		com.projecthero.mod.omnitrix.OmnitrixRecal.openConfirm = p -> net.minecraft.client.Minecraft.getInstance().setScreen(new RecalChipConfirmScreen());
		ClientTickEvents.END_CLIENT_TICK.register(OmnitrixClient::tick);
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
		return onClick(false);
	}

	/** v0.17.2: {@code right} = the right-click -- the long, show-accurate transformation (Four Arms), the left-click the quick slap. */
	public static boolean onClick(boolean right) {
		LocalPlayer p = Minecraft.getInstance().player;
		// v0.17.2: the long transformation is a scene -- no clicks reach the world while it plays
		if (p != null && com.projecthero.mod.omnitrix.OmnitrixLongTransform.playing(p)) {
			return true;
		}
		// v0.17.1: both hands are busy fighting the watch during the shake-off
		if (p != null && Omnitrix.latching(p)) {
			return true;
		}
		if (!watchUp(p)) {
			return false;
		}
		long now = p.level().getGameTime();
		// v0.17.2 (user: "my friend ... can activate the omnitrix but left clicking ... doesnt seem to do anything"): lastSlap is game
		// time from whatever world this client slapped in last -- joining a younger world without restarting left "now - lastSlap"
		// negative, so every slap was swallowed here. A last slap in the future is from another world: ignore it.
		if (now - lastSlap > 4 || now < lastSlap) {
			lastSlap = now;
			ClientPlayNetworking.send(new OmnitrixActionPayload(OmnitrixActionPayload.Action.SLAP, right ? "long" : ""));
		}
		return true;
	}

	/** While the watch is up a held attack never mines. */
	public static boolean blocksHeldAttack() {
		LocalPlayer p = Minecraft.getInstance().player;
		return watchUp(p) || (p != null && Omnitrix.latching(p)) || (p != null && com.projecthero.mod.omnitrix.OmnitrixLongTransform.playing(p));
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

	// ---------------------------------------------------------------- ticks: the kits' ambience (v0.17.1 polish: the dust helix is
	// gone -- the transformation is OmnitrixTransformFx's green / red Kirby-dot burst)

	private static void tick(Minecraft mc) {
		if (mc.level == null || mc.isPaused()) {
			return;
		}
		for (Player pl : mc.level.players()) {
			if (!(pl instanceof AbstractClientPlayer p) || !Omnitrix.hasPower(p)) {
				continue;
			}
			AlienClientKit kit = AlienClients.shownKit(p);
			if (kit != null) {
				kit.tick(p);
			}
		}
	}
}
