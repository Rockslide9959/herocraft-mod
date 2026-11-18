package com.projecthero.mod.client.squad;

import com.mojang.blaze3d.systems.RenderSystem;
import com.projecthero.mod.network.SquadInfoPayload;
import com.projecthero.mod.squad.SquadKnockdown;
import com.projecthero.mod.squad.SquadKnockdownGiveUpPayload;
import com.projecthero.mod.squad.SquadKnockdownHudPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.5 squad knockdown, client half. Deliberately minimal (user: "the only indication for being knocked on the screen
 * should be a minecraft bar slowly depleting on the top of the screen"):
 * <ul>
 *   <li>The top bar is a real vanilla boss bar the server owns (red, draining over the countdown, white while paused), so it
 *       stacks with raid and boss bars like any other. Nothing else is drawn for a knocked player -- no vignette, no text.</li>
 *   <li>The middle bar, drawn here in the same boss-bar sprites, only appears while something is in progress: green
 *       "Being revived" / "Reviving &lt;name&gt;" while a squadmate holds right-click, red "Giving up" while the knocked player
 *       holds jump (which is also how they discover it: jumping is the first thing anyone tries).</li>
 *   <li>Knocked squadmates get a soft red outline, for squadmates only (see {@link #outlined}).</li>
 * </ul>
 * Jumping, sneaking and sprinting are locked by {@code SquadKnockdownInputMixin} and here; the crawl itself is vanilla's.
 */
public final class SquadKnockdownClient {
	private static final ResourceLocation GREEN_BG = ResourceLocation.withDefaultNamespace("boss_bar/green_background");
	private static final ResourceLocation GREEN_FILL = ResourceLocation.withDefaultNamespace("boss_bar/green_progress");
	private static final ResourceLocation RED_BG = ResourceLocation.withDefaultNamespace("boss_bar/red_background");
	private static final ResourceLocation RED_FILL = ResourceLocation.withDefaultNamespace("boss_bar/red_progress");
	private static final int BAR_W = 182;
	private static final int BAR_H = 5;
	/** Below the crosshair, clear of it and of the hotbar at every GUI scale. */
	private static final int BAR_BELOW_CENTRE = 24;
	/** The outline knocked squadmates get (squadmates only). */
	public static final int OUTLINE_COLOR = 0xE84A4A;
	/** A HUD state the server has stopped refreshing for this long is dropped. */
	private static final int STALE_TICKS = 10;

	private static SquadKnockdownHudPayload hud = SquadKnockdownHudPayload.none();
	private static long clientTicks;
	private static long hudAt;
	private static int giveUpHeld;
	private static boolean giveUpSent;

	private SquadKnockdownClient() {
	}

	public static void initialize() {
		ClientPlayNetworking.registerGlobalReceiver(SquadKnockdownHudPayload.TYPE,
				(payload, context) -> context.client().execute(() -> {
					hud = payload;
					hudAt = clientTicks;
				}));
		ClientTickEvents.END_CLIENT_TICK.register(SquadKnockdownClient::tick);
		HudRenderCallback.EVENT.register(SquadKnockdownClient::render);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(SquadKnockdownClient::clear));
	}

	private static void clear() {
		hud = SquadKnockdownHudPayload.none();
		giveUpHeld = 0;
		giveUpSent = false;
	}

	/** Is this client's own player knocked down? */
	public static boolean selfKnocked() {
		Player self = Minecraft.getInstance().player;
		return self != null && SquadKnockdown.isKnocked(self);
	}

	private static void tick(Minecraft client) {
		clientTicks++;
		if (hud.mode() != SquadKnockdownHudPayload.MODE_NONE && clientTicks - hudAt > STALE_TICKS) {
			hud = SquadKnockdownHudPayload.none();
		}
		Player self = client.player;
		if (self == null || !SquadKnockdown.isKnocked(self)) {
			giveUpHeld = 0;
			giveUpSent = false;
			return;
		}
		if (self.isSprinting()) {
			self.setSprinting(false);
		}
		// hold jump to give up (the key itself does nothing while knocked -- see SquadKnockdownInputMixin)
		boolean beingRevived = hud.mode() == SquadKnockdownHudPayload.MODE_KNOCKED && hud.progress() > 0;
		if (client.screen == null && client.options.keyJump.isDown() && !beingRevived) {
			giveUpHeld = Math.min(giveUpHeld + 1, SquadKnockdown.GIVE_UP_TICKS);
			if (giveUpHeld >= SquadKnockdown.GIVE_UP_TICKS && !giveUpSent && ClientPlayNetworking.canSend(SquadKnockdownGiveUpPayload.TYPE)) {
				ClientPlayNetworking.send(SquadKnockdownGiveUpPayload.INSTANCE);
				giveUpSent = true;
			}
		} else {
			giveUpHeld = 0;
			giveUpSent = false;
		}
	}

	private static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		if (client.options.hideGui || client.player == null) {
			return;
		}
		float partial = delta.getGameTimeDeltaPartialTick(false);
		boolean knocked = SquadKnockdown.isKnocked(client.player);
		Component label = null;
		float frac = 0.0f;
		boolean green = true;
		if (hud.mode() == SquadKnockdownHudPayload.MODE_REVIVING && hud.progress() > 0) {
			label = Component.translatable("projecthero.knockdown.reviving", hud.name());
			frac = (hud.progress() + partial) / Math.max(1, hud.needed());
		} else if (knocked && hud.mode() == SquadKnockdownHudPayload.MODE_KNOCKED && hud.progress() > 0) {
			label = Component.translatable("projecthero.knockdown.being_revived");
			frac = (hud.progress() + partial) / Math.max(1, hud.needed());
		} else if (knocked && giveUpHeld > 0) {
			label = Component.translatable("projecthero.knockdown.giving_up");
			frac = (giveUpHeld + (giveUpSent ? 0.0f : partial)) / SquadKnockdown.GIVE_UP_TICKS;
			green = false;
		}
		if (label == null) {
			return;
		}
		drawBar(g, client.font, label, Mth.clamp(frac, 0.0f, 1.0f), green);
	}

	/** A vanilla boss-bar-style bar, centred just under the crosshair, its label above it. */
	private static void drawBar(GuiGraphics g, Font font, Component label, float frac, boolean green) {
		int x = (g.guiWidth() - BAR_W) / 2;
		int y = g.guiHeight() / 2 + BAR_BELOW_CENTRE;
		RenderSystem.enableBlend();
		g.blitSprite(green ? GREEN_BG : RED_BG, x, y, BAR_W, BAR_H);
		int fill = Math.round(frac * BAR_W);
		if (fill > 0) {
			g.blitSprite(green ? GREEN_FILL : RED_FILL, BAR_W, BAR_H, 0, 0, x, y, fill, BAR_H);
		}
		RenderSystem.disableBlend();
		// never wider than the bar (a long name is trimmed rather than sprawling across the screen)
		String text = label.getString();
		if (font.width(text) > BAR_W) {
			text = font.plainSubstrByWidth(text, BAR_W - font.width("...")) + "...";
		}
		g.drawString(font, text, (g.guiWidth() - font.width(text)) / 2, y - 10, 0xFFFFFF, true);
	}

	// ---------------------------------------------------------------- squadmates

	/** Draw the squad-only outline on {@code entity}: a knocked player in this client's squad (never yourself). */
	public static boolean outlined(Entity entity) {
		if (!(entity instanceof Player p) || p == Minecraft.getInstance().player || p.level() == null || !p.level().isClientSide()
				|| !SquadKnockdown.isKnocked(p)) {
			return false;
		}
		for (SquadInfoPayload.Member m : SquadClient.get().members()) {
			if (m.id().equals(p.getUUID())) {
				return true;
			}
		}
		return false;
	}
}
