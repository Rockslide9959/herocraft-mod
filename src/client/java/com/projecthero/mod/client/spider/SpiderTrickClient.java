package com.projecthero.mod.client.spider;

import com.projecthero.mod.network.SpiderFreerunPayload;
import com.projecthero.mod.spider.SpiderClimb;
import com.projecthero.mod.spider.SpiderMan;
import com.projecthero.mod.spider.SpiderManConfig;
import com.projecthero.mod.spider.SpiderMoves;
import com.projecthero.mod.spider.SpiderSwing;
import com.projecthero.mod.spider.SpiderTricks;
import com.projecthero.mod.spider.data.SpiderMoveState;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * v0.16.2: the swing tricks on the owning client ({@link SpiderTricks} has the rules). A Jump press inside the window
 * after a release is claimed here (so it is not also the double jump / zip boost) and becomes a trick only if Jump is let
 * go again within {@link SpiderTricks#TAP_TICKS} -- held longer it is left alone (gliding owns a held Space). The kick is
 * applied to his own motion at once; the server confirms the count and starts the animation everyone sees. The combo
 * shows as a small "x2" / "x3" under the crosshair.
 */
public final class SpiderTrickClient {
	private static final int HUD_TICKS = 30;

	private static boolean wasSwinging;
	private static long releaseAt = Long.MIN_VALUE / 2;
	private static long lastTrickAt = Long.MIN_VALUE / 2;
	private static int count;
	private static boolean pending;
	private static long pressAt;
	private static int lastKind = -1;
	private static long hudUntil;

	private SpiderTrickClient() {
	}

	/** Diagnostics: the combo so far this airtime. */
	public static int count() {
		return count;
	}

	private static boolean eligible(LocalPlayer p) {
		return SpiderMan.hasPower(p) && !p.onGround() && !p.isInWater() && !p.isInLava() && !p.isFallFlying()
				&& !p.getAbilities().flying && !p.isPassenger() && !SpiderSwing.isSwinging(p) && !SpiderClimb.attached(p)
				&& !SpiderMoves.state(p).has(SpiderMoveState.FLAG_HANGING);
	}

	public static void clientTick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null) {
			reset();
			return;
		}
		long now = p.level().getGameTime();
		boolean swinging = SpiderSwing.isSwinging(p);
		boolean grounded = p.onGround() || p.isInWater() || SpiderClimb.attached(p)
				|| SpiderMoves.state(p).has(SpiderMoveState.FLAG_HANGING);
		if (wasSwinging && !swinging && !grounded) {
			releaseAt = now;
		}
		wasSwinging = swinging;
		if (grounded) {
			count = 0;
			pending = false;
		}
		if (pending) {
			if (!mc.options.keyJump.isDown()) {
				pending = false;
				if (now - pressAt <= SpiderTricks.TAP_TICKS) {
					trick(p, now);
				}
			} else if (now - pressAt > SpiderTricks.TAP_TICKS) {
				pending = false; // a hold, not a tap: gliding's
			}
		}
	}

	/**
	 * Jump was just pressed in the air (from {@code SpiderInputClient}): claim it when it could start a trick. Never
	 * during a Spider-Sense warning -- that Jump belongs to the dodge.
	 */
	public static boolean claimJump(LocalPlayer p) {
		long now = p.level().getGameTime();
		if (!eligible(p) || SpiderMoves.state(p).tingleUntil > now
				|| !SpiderTricks.allowed(count, now, releaseAt, lastTrickAt, 0)) {
			return false;
		}
		pending = true;
		pressAt = now;
		return true;
	}

	private static void trick(LocalPlayer p, long now) {
		if (!eligible(p) || !SpiderTricks.allowed(count, now, releaseAt, lastTrickAt, SpiderTricks.TAP_TICKS)) {
			return;
		}
		int kind = SpiderTricks.kindForKeys(p.input.up, p.input.down, p.input.left, p.input.right, p.getRandom().nextInt());
		count++;
		lastTrickAt = now;
		lastKind = kind;
		hudUntil = now + HUD_TICKS;
		p.setDeltaMovement(SpiderTricks.reward(p.getDeltaMovement(), count, SpiderManConfig.moves().swingMaxSpeed));
		ClientPlayNetworking.send(new SpiderFreerunPayload(SpiderFreerunPayload.TRICK, kind));
	}

	private static final String[] NAMES = {"front_flip", "backflip", "corkscrew", "split", "twist"};

	/** The trick name and combo under the crosshair. */
	public static void renderHud(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.options.hideGui || lastKind < 0 || count <= 0) {
			return;
		}
		float left = hudUntil - (p.level().getGameTime() + delta.getGameTimeDeltaPartialTick(false));
		if (left <= 0f) {
			return;
		}
		int alpha = (int) (255 * Mth.clamp(left / 8f, 0f, 1f));
		if (alpha < 8) {
			return;
		}
		Component name = Component.translatable("hud.projecthero.spider_man.trick." + NAMES[lastKind]);
		Component text = count >= 2 ? Component.translatable("hud.projecthero.spider_man.trick.combo", name, count) : name;
		int x = g.guiWidth() / 2 - mc.font.width(text) / 2;
		int y = g.guiHeight() / 2 + 14;
		int colour = count >= 3 ? 0xFFD040 : count == 2 ? 0xFFE890 : 0xF0F0F0;
		g.drawString(mc.font, text, x, y, (alpha << 24) | colour, true);
	}

	public static void reset() {
		wasSwinging = false;
		releaseAt = Long.MIN_VALUE / 2;
		lastTrickAt = Long.MIN_VALUE / 2;
		count = 0;
		pending = false;
		lastKind = -1;
		hudUntil = 0L;
	}
}
