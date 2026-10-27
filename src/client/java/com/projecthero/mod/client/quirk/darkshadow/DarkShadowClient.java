package com.projecthero.mod.client.quirk.darkshadow;

import java.util.List;

import com.projecthero.mod.client.quirk.QuirkClient;
import com.projecthero.mod.client.quirk.QuirkClientKit;
import com.projecthero.mod.hero.PowerSuppression;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.darkshadow.DarkShadow;
import com.projecthero.mod.quirk.darkshadow.DarkShadowCombat;
import com.projecthero.mod.quirk.darkshadow.DarkShadowConfig;

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
		registerFirstPerson();
	}

	/**
	 * v0.17.1: Tokoyami's first-person arms for every command (pitch negative = up into view; yaw + toward the centre; offsets x
	 * centre-ward, y up, z into the screen) -- he points, closes his fist, braces, throws, flings his arms wide.
	 */
	private static void registerFirstPerson() {
		Quirk q = Quirk.DARK_SHADOW;
		// R: the point -- "Dark Shadow!"
		com.projecthero.mod.client.quirk.QuirkFirstPersonPose.register(q, DarkShadow.ANIM_STRIKE, clip(
				fp(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
				fp(2, -10, -18, -20, -0.06f, 0.04f, 0.06f, 0, 0, 0, 0, 0, 0),
				fp(4, -30, 14, 6, 0.1f, 0.16f, -0.36f, 0, 0, 0, 0, 0, 0),
				fp(9, -28, 12, 6, 0.1f, 0.15f, -0.34f, 0, 0, 0, 0, 0, 0),
				fp(12, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)));
		// Shift+R: both clawed hands forward, juddering with the bites
		com.projecthero.mod.client.quirk.QuirkFirstPersonPose.register(q, DarkShadow.ANIM_FANG, clip(
				fp(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
				fp(3, -26, 20, 20, 0.12f, 0.14f, -0.3f, -26, 20, 20, 0.12f, 0.14f, -0.3f),
				fp(7, -20, 18, 16, 0.12f, 0.1f, -0.24f, -28, 22, 20, 0.12f, 0.16f, -0.32f),
				fp(11, -28, 22, 20, 0.12f, 0.16f, -0.32f, -20, 18, 16, 0.12f, 0.1f, -0.24f),
				fp(15, -22, 18, 16, 0.12f, 0.12f, -0.26f, -26, 20, 20, 0.12f, 0.14f, -0.3f),
				fp(20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)).twoHanded());
		// G: the open hand out, then the fist clenching as it squeezes
		com.projecthero.mod.client.quirk.QuirkFirstPersonPose.register(q, DarkShadow.ANIM_GRAB, clip(
				fp(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
				fp(3, -28, 14, -10, 0.1f, 0.14f, -0.36f, 0, 0, 0, 0, 0, 0),
				fp(6, -28, 14, -10, 0.1f, 0.14f, -0.36f, 0, 0, 0, 0, 0, 0),
				fp(9, -34, 18, 30, 0.08f, 0.2f, -0.2f, 0, 0, 0, 0, 0, 0),
				fp(12, -32, 18, 28, 0.08f, 0.2f, -0.2f, 0, 0, 0, 0, 0, 0)));
		// the slam: the fist raised and brought down
		com.projecthero.mod.client.quirk.QuirkFirstPersonPose.register(q, DarkShadow.ANIM_SLAM, clip(
				fp(0, -32, 18, 28, 0.08f, 0.2f, -0.2f, 0, 0, 0, 0, 0, 0),
				fp(3, -62, 10, 20, 0.04f, 0.4f, -0.14f, 0, 0, 0, 0, 0, 0),
				fp(6, 30, 10, 20, 0.06f, -0.24f, -0.28f, 0, 0, 0, 0, 0, 0),
				fp(11, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)));
		// C: Abyssal Rampage -- braced, the commanding arm thrown up and swept with each swing
		com.projecthero.mod.client.quirk.QuirkFirstPersonPose.register(q, DarkShadow.ANIM_RAMPAGE, clip(
				fp(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
				fp(2, -58, -10, -10, 0.02f, 0.36f, -0.2f, 6, -10, -10, -0.04f, -0.06f, 0.04f),
				fp(3.5f, -40, 40, 10, 0.22f, 0.24f, -0.3f, 6, -10, -10, -0.04f, -0.06f, 0.04f),
				fp(6, -56, -16, -10, -0.02f, 0.34f, -0.22f, 6, -10, -10, -0.04f, -0.06f, 0.04f),
				fp(8.5f, -40, 44, 10, 0.24f, 0.24f, -0.3f, 6, -10, -10, -0.04f, -0.06f, 0.04f),
				fp(11, -66, 6, 0, 0.06f, 0.42f, -0.16f, -60, 6, 0, 0.06f, 0.38f, -0.16f),
				fp(13.5f, 18, 10, 0, 0.06f, -0.16f, -0.32f, 14, 10, 0, 0.06f, -0.14f, -0.3f),
				fp(18, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)).twoHanded());
		// X tap: Shadow Lunge -- point, then the arm out as it yanks you
		com.projecthero.mod.client.quirk.QuirkFirstPersonPose.register(q, DarkShadow.ANIM_LUNGE, clip(
				fp(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
				fp(2, -30, 14, 6, 0.1f, 0.16f, -0.38f, 0, 0, 0, 0, 0, 0),
				fp(4, -34, 10, 6, 0.08f, 0.2f, -0.44f, 10, -20, -20, -0.06f, -0.1f, 0.08f),
				fp(12, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)).twoHanded());
		// Shift+X: Fetch -- an arm sent out at the point
		com.projecthero.mod.client.quirk.QuirkFirstPersonPose.register(q, DarkShadow.ANIM_FETCH, clip(
				fp(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
				fp(3, -28, 12, 0, 0.1f, 0.14f, -0.36f, 0, 0, 0, 0, 0, 0),
				fp(8, -28, 12, 0, 0.1f, 0.14f, -0.36f, 0, 0, 0, 0, 0, 0),
				fp(14, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)));
		// the toss: the raised fist flung forward
		com.projecthero.mod.client.quirk.QuirkFirstPersonPose.register(q, DarkShadow.ANIM_TOSS, clip(
				fp(0, -64, 4, 10, 0.04f, 0.42f, -0.1f, 0, 0, 0, 0, 0, 0),
				fp(3, -22, 14, 0, 0.12f, 0.1f, -0.44f, 0, 0, 0, 0, 0, 0),
				fp(10, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)));
		// Z: Shadow Scout -- an arm sent up and away
		com.projecthero.mod.client.quirk.QuirkFirstPersonPose.register(q, DarkShadow.ANIM_SCOUT, clip(
				fp(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
				fp(4, -62, 12, -10, 0.06f, 0.42f, -0.24f, 0, 0, 0, 0, 0, 0),
				fp(12, -58, 10, -10, 0.06f, 0.4f, -0.22f, 0, 0, 0, 0, 0, 0)));
		// Shift+Z: Sunshine Ankh -- curled in, then flung wide
		com.projecthero.mod.client.quirk.QuirkFirstPersonPose.register(q, DarkShadow.ANIM_NOVA, clip(
				fp(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
				fp(5, -20, 44, 10, 0.3f, 0.04f, -0.12f, -20, 44, 10, 0.3f, 0.04f, -0.12f),
				fp(7.5f, -40, -40, -30, -0.18f, 0.24f, -0.12f, -40, -40, -30, -0.18f, 0.24f, -0.12f),
				fp(18, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)).twoHanded());
		// Shift+C: Unleash -- arms crossed, then thrown out
		com.projecthero.mod.client.quirk.QuirkFirstPersonPose.register(q, DarkShadow.ANIM_UNLEASH, clip(
				fp(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
				fp(4, -22, 48, 10, 0.34f, 0.08f, -0.12f, -24, 46, 10, 0.32f, 0.1f, -0.12f),
				fp(8, -46, -36, -30, -0.16f, 0.3f, -0.14f, -46, -36, -30, -0.16f, 0.3f, -0.14f),
				fp(22, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)).twoHanded());
		// V: Black Ankh -- arms flung wide, then the clawed fists clenched
		com.projecthero.mod.client.quirk.QuirkFirstPersonPose.register(q, DarkShadow.ANIM_ANKH, clip(
				fp(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
				fp(4, -14, -40, -30, -0.18f, 0.06f, 0.0f, -14, -40, -30, -0.18f, 0.06f, 0.0f),
				fp(10, -30, 18, 30, 0.1f, 0.14f, -0.22f, -30, 18, 30, 0.1f, 0.14f, -0.22f),
				fp(14, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)).twoHanded());
		// Shift+V: Light Flare -- the light held high
		com.projecthero.mod.client.quirk.QuirkFirstPersonPose.register(q, DarkShadow.ANIM_FLARE, clip(
				fp(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
				fp(4, -60, 10, 0, 0.06f, 0.42f, -0.18f, 0, 0, 0, 0, 0, 0),
				fp(20, -60, 10, 0, 0.06f, 0.42f, -0.18f, 0, 0, 0, 0, 0, 0),
				fp(28, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)));
		// Abyssal Black Body: the merge -- curled in, then thrown wide
		com.projecthero.mod.client.quirk.QuirkFirstPersonPose.register(q, DarkShadow.ANIM_BLACKBODY, clip(
				fp(0, -20, 44, 10, 0.3f, 0.06f, -0.12f, -20, 44, 10, 0.3f, 0.06f, -0.12f),
				fp(5, -24, 46, 12, 0.32f, 0.08f, -0.12f, -24, 46, 12, 0.32f, 0.08f, -0.12f),
				fp(9, -50, -44, -34, -0.2f, 0.3f, -0.1f, -50, -44, -34, -0.2f, 0.3f, -0.1f),
				fp(26, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)).twoHanded());
		// the claw dive: talons out ahead, then swept back
		com.projecthero.mod.client.quirk.QuirkFirstPersonPose.register(q, DarkShadow.ANIM_DIVE, clip(
				fp(0, -40, 26, 24, 0.14f, 0.24f, -0.4f, -40, 26, 24, 0.14f, 0.24f, -0.4f),
				fp(3, -34, 10, 20, 0.08f, 0.2f, -0.46f, -34, 10, 20, 0.08f, 0.2f, -0.46f),
				fp(6, 10, -30, -20, -0.12f, -0.12f, 0.06f, 10, -30, -20, -0.12f, -0.12f, 0.06f),
				fp(12, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)).twoHanded());
		// the fall catch: arms out for balance as it takes him
		com.projecthero.mod.client.quirk.QuirkFirstPersonPose.register(q, DarkShadow.ANIM_CATCH, clip(
				fp(0, 10, -40, -40, -0.2f, 0.0f, 0.04f, 10, -40, -40, -0.2f, 0.0f, 0.04f),
				fp(16, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)).twoHanded());
	}

	private static com.projecthero.mod.client.quirk.QuirkFirstPersonPose.Clip clip(float[]... f) {
		return com.projecthero.mod.client.quirk.QuirkFirstPersonPose.clip(f);
	}

	private static float[] fp(float tick, float mp, float my, float mr, float mx, float mY, float mz, float op, float oy, float or, float ox,
			float oY, float oz) {
		return com.projecthero.mod.client.quirk.QuirkFirstPersonPose.f(tick, mp, my, mr, mx, mY, mz, op, oy, or, ox, oY, oz);
	}

	@Override
	public void applyPose(Player player, HumanoidModel<?> model) {
		DarkShadowPose.apply(player, model);
	}

	/** X held toward the glide; G carrying (Shadow Toss) fills the G ring. */
	@Override
	public float holdCharge(Player player, com.projecthero.mod.hero.AbilitySlot slot) {
		return switch (slot) {
			case SLOT_3 -> {
				long at = DarkShadow.val(player, DarkShadow.V_HOLD_X);
				if (DarkShadow.abyssal(player) && at != 0L) {
					yield 1f;
				}
				yield at == 0L ? -1f : Math.min(1f, (player.level().getGameTime() - at) / (float) DarkShadowConfig.get().moves.holdTicks);
			}
			case SLOT_2 -> DarkShadow.val(player, DarkShadow.V_TOSS) == 1L ? 1f : -1f;
			default -> -1f;
		};
	}

	/** The Plus Ultra hold: hunched, arms wrapped round himself, the shadow pouring over his shoulders. */
	@Override
	public boolean ultimateHoldPose(Player player, HumanoidModel<?> model, float charge) {
		float shake = Mth.sin(player.tickCount * 2.6f) * 0.03f * (0.4f + charge);
		float w = Math.min(1f, 0.35f + charge);
		model.rightArm.xRot = Mth.lerp(w, model.rightArm.xRot, -1.35f + shake);
		model.rightArm.yRot = Mth.lerp(w, model.rightArm.yRot, -0.85f);
		model.leftArm.xRot = Mth.lerp(w, model.leftArm.xRot, -1.35f - shake);
		model.leftArm.yRot = Mth.lerp(w, model.leftArm.yRot, 0.85f);
		com.projecthero.mod.client.HipBend.pitch(model, w, 0.3f);
		model.head.xRot = Mth.lerp(w, model.head.xRot, 0.45f);
		model.hat.copyFrom(model.head);
		return true;
	}

	@Override
	public Component overloadLine(Player player) {
		if (DarkShadow.unleashed(player)) {
			return Component.translatable("hud.projecthero.dark_shadow.unleashed");
		}
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
		if (DarkShadow.blackBody(player)) {
			state = Component.translatable("hud.projecthero.dark_shadow.state.blackbody", (DarkShadow.val(player, DarkShadow.V_BLACKBODY) - now + 19) / 20);
		} else if (DarkShadow.recalled(player) && DarkShadow.running(player, DarkShadow.V_HIDE_LOCK)) {
			state = Component.translatable("hud.projecthero.dark_shadow.state.spent", (DarkShadow.val(player, DarkShadow.V_HIDE_LOCK) - now + 19) / 20);
		} else if (DarkShadow.unleashed(player)) {
			state = Component.translatable("hud.projecthero.dark_shadow.state.unleash", (DarkShadow.val(player, DarkShadow.V_UNLEASH) - now + 19) / 20);
		} else if (DarkShadow.recalled(player)) {
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
