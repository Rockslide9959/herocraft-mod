package com.projecthero.mod.client.omnitrix.alien;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.AlienClients;
import com.projecthero.mod.client.omnitrix.OmnitrixHud;
import com.projecthero.mod.client.omnitrix.alien.alienx.AlienXCouncilScreen;
import com.projecthero.mod.client.omnitrix.alien.alienx.AlienXFx;
import com.projecthero.mod.client.omnitrix.alien.alienx.AlienXHall;
import com.projecthero.mod.client.omnitrix.alien.alienx.AlienXHallAmbience;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.alienx.AlienXBodyEntity;
import com.projecthero.mod.omnitrix.alien.alienx.AlienXKit;
import com.projecthero.mod.omnitrix.alien.alienx.AlienXCouncilStatePayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.17.2: Alien X's client kit. The body is ONLY the player model in the user's Alien X skin (user: "delete all the alien models
 * and only use the skins i provided"), with his three horns. v0.17.2 Celestial Council (user: "when the player transforms into
 * alien x i want his body to remain in place, make alien x 2.5 blocks tall ... the player gets transported to that dimension where
 * he talks to the two heads"): registers the body's renderer ({@link AlienXBodyRenderer}), the council screen
 * ({@link AlienXCouncilScreen}, opened by the server's state payload) and the council's hall ({@link AlienXHall}: v0.17.2 round 4, a
 * mind palace drawn over the screen, with the heads from {@link AlienXFx}); the HUD row shows only outside the hall.
 */
public final class AlienXClient implements AlienClientKit {
	public static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/omnitrix/alien_x.png");

	public static final int SERENA_COLOUR = 0xFF9FE8FF, BELLICUS_COLOUR = 0xFFFF5040, BEN_COLOUR = 0xFF7CFF6A;

	private AlienXClient() {
	}

	/** One line in {@code OmnitrixClient.initialize}. */
	public static void register() {
		AlienClients.register(Aliens.ALIEN_X.id(), new AlienXClient());
		AlienXHall.init();
		AlienXHallAmbience.init();
		EntityRendererRegistry.register(AlienXBodyEntity.TYPE, AlienXBodyRenderer::new);
		ClientPlayNetworking.registerGlobalReceiver(AlienXCouncilStatePayload.TYPE, (payload, context) -> context.client().execute(() -> {
			Minecraft mc = Minecraft.getInstance();
			if (mc.screen instanceof AlienXCouncilScreen s) {
				s.update(payload.state());
			} else if (payload.open() && (mc.screen == null || mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen)) {
				mc.setScreen(new AlienXCouncilScreen(payload.state()));
			}
		}));
	}

	/** His skin's own black (the horns). */
	private static final float[] BLACK = AlienHorns.pixel(21, 22, 64);

	/**
	 * v0.17.2 (user: "make alien xs spikes come out from his forehead ... make the spikes look smooth"): his three horns, in the
	 * head's space (pixels) -- shared by the player, the body left in the world and the council's heads.
	 */
	public static void horns(PoseStack pose, VertexConsumer vc, int light, ModelPart head) {
		com.projecthero.mod.client.omnitrix.AlienParts.on(pose, head, () -> {
			AlienHorns.tube(pose, vc, light, new float[] { 0f, -8.2f, -3.7f }, new float[] { 0f, -15f, -3.4f }, new float[] { 0f, -15.5f, 3.5f }, 0.85f, 14, BLACK);
			for (int side = -1; side <= 1; side += 2) {
				AlienHorns.tube(pose, vc, light, new float[] { side * 2.5f, -8.2f, -3.7f }, new float[] { side * 4.6f, -11.8f, -3.4f },
						new float[] { side * 3.9f, -13.6f, -0.6f }, 0.6f, 10, BLACK);
			}
		});
	}

	@Override
	public void renderExtras(PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light, net.minecraft.client.player.AbstractClientPlayer p,
			net.minecraft.client.model.PlayerModel<net.minecraft.client.player.AbstractClientPlayer> model, float partial, float age) {
		horns(pose, buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(SKIN)), light, model.head);
	}

	/** v0.17.2 (user: "raise our omnitrix badge slightly"). */
	@Override
	public float[] badge() {
		return new float[] { 0f, 3.5f, -2.05f, 3.2f };
	}

	@Override
	public ResourceLocation skin() {
		return SKIN;
	}

	@Override
	public int hudHeight() {
		return 10;
	}

	/** One row: in the hall (the HUD is put away there, so only for a moment), how to speak; otherwise, that the council is away. */
	@Override
	public void hud(GuiGraphics g, LocalPlayer p, int x, int y, int w, float partial) {
		Minecraft mc = Minecraft.getInstance();
		boolean inCouncil = AlienXKit.inHall(p);
		OmnitrixHud.meter(g, mc, x, y - 6, Component.translatable("hud.projecthero.omnitrix.alien_x.council"), inCouncil ? 1f : 0f, 0xFF9FC4FF,
				Component.translatable(inCouncil ? "hud.projecthero.omnitrix.alien_x.address" : "hud.projecthero.omnitrix.alien_x.away").getString(),
				0xFFBFD8FF);
	}
}
