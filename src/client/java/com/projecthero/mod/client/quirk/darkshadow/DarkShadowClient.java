package com.projecthero.mod.client.quirk.darkshadow;

import java.util.List;

import com.projecthero.mod.client.quirk.QuirkClient;
import com.projecthero.mod.client.quirk.QuirkClientKit;
import com.projecthero.mod.hero.PowerSuppression;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.darkshadow.DarkShadow;
import com.projecthero.mod.quirk.darkshadow.DarkShadowCombat;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7 (user) Quirk revamp: the Dark Shadow Quirk's client side -- the creature in the world ({@link DarkShadowRender},
 * every viewer), the Black Ankh armour layer ({@link DarkShadowAnkhLayer}), Tokoyami's poses ({@link DarkShadowPose}) and
 * its HUD block:
 * <pre>
 *   Night  x1.43  (or Daylight / Pitch dark / Hidden ... -- wrapped)     the darkness it feels and its strength
 *   N: hide it                                                           (v0.16.7 user: the N hide key, dim, while it is out)
 *   [darkness gauge, light-to-dark]
 *   Shadow [==========-----] 63                                          the meter (red, pulsing, at Berserk)
 * </pre>
 * While berserk the overload line reads "BERSERK -- bring light!" instead of a countdown (it lasts until light).
 */
public final class DarkShadowClient implements QuirkClientKit {
	private static final int BORDER = 0xFF4A4A4A;
	private static final int BAR_BG = 0xC0141414;
	private static final int NAME = 0xFFE2E2E2;
	private static final int HINT = 0xFF9A9A9A;
	private static final int RED = 0xFFFF5A4A;

	private DarkShadowClient() {
	}

	public static void initialize() {
		QuirkClient.register(Quirk.DARK_SHADOW, new DarkShadowClient());
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof PlayerRenderer playerRenderer) {
				helper.register(new DarkShadowAnkhLayer(playerRenderer));
			}
		});
		WorldRenderEvents.AFTER_ENTITIES.register(DarkShadowRender::render);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			DarkShadowRender.reset();
			DarkShadowAnkhLayer.reset();
		});
	}

	@Override
	public void applyPose(Player player, HumanoidModel<?> model) {
		DarkShadowPose.apply(player, model);
	}

	@Override
	public Component overloadLine(Player player) {
		return DarkShadow.on(player, DarkShadow.V_BERSERK) ? Component.translatable("hud.projecthero.dark_shadow.berserk") : null;
	}

	@Override
	public int drawStatus(GuiGraphics g, Player player, int x0, int bottom, int width) {
		Minecraft mc = Minecraft.getInstance();
		boolean erased = PowerSuppression.isSuppressed(player);
		boolean berserk = DarkShadow.berserk(player);
		long now = player.level().getGameTime();
		float dark = DarkShadow.darkness(player);
		int y = bottom;

		// ---- the meter
		float meter = Mth.clamp(Quirks.clientMeter(player), 0f, 100f);
		int barH = 4;
		y -= barH + 1;
		Component label = Component.translatable("projecthero.quirk.meter.shadow");
		int labelW = mc.font.width(label);
		String value = String.valueOf(Math.round(meter));
		int valueW = mc.font.width("100");
		int barX = x0 + labelW + 4;
		int barW = Math.max(20, width - labelW - 4 - valueW - 3);
		int colour = berserk ? ((now / 5) % 2 == 0 ? RED : 0xFFB02030) : meter >= 85f ? 0xFFB050FF : Quirk.DARK_SHADOW.meterColour | 0xFF000000;
		if (erased) {
			colour = 0xFF505050;
		}
		g.drawString(mc.font, label, x0, y - 2, erased ? HINT : NAME, false);
		g.fill(barX - 1, y - 1, barX + barW + 1, y + barH + 1, BORDER);
		g.fill(barX, y, barX + barW, y + barH, BAR_BG);
		com.projecthero.mod.client.quirk.QuirkHud.bandedFill(g, player, com.projecthero.mod.quirk.Quirk.DARK_SHADOW, barX, y, barW, barH, meter, colour); // v0.17.1 bands
		g.drawString(mc.font, value, x0 + width - mc.font.width(value), y - 2, erased ? HINT : NAME, false);
		y -= 7;

		// ---- the darkness gauge: light (left, pale) to pitch dark (right, deep violet), a marker at the darkness it feels
		int gh = 3;
		y -= gh;
		for (int i = 0; i < width; i++) {
			float k = i / (float) Math.max(1, width - 1);
			int r = (int) Mth.lerp(k, 236, 40), gg = (int) Mth.lerp(k, 222, 12), b = (int) Mth.lerp(k, 170, 70);
			g.fill(x0 + i, y, x0 + i + 1, y + gh, 0xFF000000 | (r << 16) | (gg << 8) | b);
		}
		int mx = x0 + Math.round((width - 1) * dark);
		g.fill(mx - 1, y - 2, mx + 2, y + gh + 2, 0xFFFFFFFF);
		y -= 3;

		// ---- v0.16.7 (user): the N hide key -- a dim hint while it is out (wrapped)
		String nKey = com.projecthero.mod.client.ModKeyBindings.MAX_STEEL_TRANSFORM.getTranslatedKeyMessage().getString();
		if (!DarkShadow.recalled(player) && !berserk) {
			List<FormattedCharSequence> hint = mc.font.split(Component.translatable("hud.projecthero.dark_shadow.hide_hint", nKey), width);
			y -= hint.size() * 9;
			int hy = y;
			for (FormattedCharSequence line : hint) {
				g.drawString(mc.font, line, x0, hy, HINT, false);
				hy += 9;
			}
			y -= 1;
		}

		// ---- the state line (wrapped)
		Component state;
		if (DarkShadow.recalled(player)) {
			state = Component.translatable("hud.projecthero.dark_shadow.hidden", nKey);
		} else if (berserk) {
			state = Component.translatable("hud.projecthero.dark_shadow.state.berserk");
		} else {
			String key = dark < 0.3f ? "daylight" : DarkShadowCombat.lightAt(player) <= 3 ? "pitch" : dark < 0.65f ? "dim" : "night";
			float strength = DarkShadowCombat.strengthAt(dark);
			state = Component.translatable("hud.projecthero.dark_shadow.state." + key, String.format("%.2f", strength));
		}
		List<FormattedCharSequence> lines = mc.font.split(state, width);
		y -= lines.size() * 9;
		int sy = y;
		int sc = berserk ? RED : DarkShadow.recalled(player) ? HINT : dark >= 0.65f ? 0xFFC8A0FF : 0xFFE8DDB0;
		for (FormattedCharSequence line : lines) {
			g.drawString(mc.font, line, x0, sy, erased ? HINT : sc, false);
			sy += 9;
		}
		return y - 2;
	}
}
