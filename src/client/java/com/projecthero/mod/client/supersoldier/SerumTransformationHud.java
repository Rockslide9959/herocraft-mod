package com.projecthero.mod.client.supersoldier;

import com.projecthero.mod.client.gui.VanillaHud;
import com.projecthero.mod.hero.visual.MutationMeters;
import com.projecthero.mod.supersoldier.SerumTransformation;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;

/**
 * v0.17.7 (user: "a bar at the bottom right of your screen will show the timer completion, dont label it just have a blue bar fill
 * up slowly"): the Super Soldier Serum's one-minute transformation -- one bare blue Slab bar (no label, no numbers), the width of a
 * six-key power column, in the bottom-right corner where the power HUDs stand (lifted clear of the hotbar in a narrow window, the
 * same rule as {@link VanillaHud.Stack#bottomRight}). It fills from empty to full over the minute, interpolated between ticks.
 *
 * <p>For that minute the bar owns the corner: {@link VanillaHud#muteOthers} also answers true, so the Primary power he is about to
 * lose hides its HUD instead of drawing over the bar. The chain is put on once the client has started, after every power's own
 * client setup (Power Armour sets the supplier during its setup).
 */
public final class SerumTransformationHud {
	/** A blue the boss-bar palette maps to its blue bar. */
	private static final int BLUE = 0xFF3C78FF;

	private SerumTransformationHud() {
	}

	public static void initialize() {
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			java.util.function.BooleanSupplier before = VanillaHud.muteOthers;
			VanillaHud.muteOthers = () -> before.getAsBoolean() || transforming();
		});
	}

	private static boolean transforming() {
		LocalPlayer p = Minecraft.getInstance().player;
		return p != null && SerumTransformation.active(p);
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.options.hideGui) {
			return;
		}
		int t = SerumTransformation.elapsed(p);
		if (t < 0) {
			return;
		}
		float frac = Math.min(1f, (t + delta.getGameTimeDeltaPartialTick(false)) / SerumTransformation.DURATION);
		VanillaHud.Stack st = VanillaHud.Stack.bottomRightOwned(g, 6);
		VanillaHud.bar(g, st.x0, st.bottom - 6, st.width, frac, BLUE, MutationMeters.Style.SLAB);
	}
}
