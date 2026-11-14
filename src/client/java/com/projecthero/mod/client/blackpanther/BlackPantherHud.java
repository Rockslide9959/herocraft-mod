package com.projecthero.mod.client.blackpanther;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.blackpanther.BlackPanther;
import com.projecthero.mod.blackpanther.BlackPantherAbilityManager;
import com.projecthero.mod.blackpanther.BlackPantherConfig;
import com.projecthero.mod.blackpanther.data.BlackPantherState;
import com.projecthero.mod.client.gui.MoveListOverlay;
import com.projecthero.mod.client.gui.VanillaHud;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.visual.MutationMeters;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * v0.17.3: the Black Panther HUD on the Minecraft-style look of the newest powers ({@link VanillaHud.Stack}, bottom-right):
 * <pre>
 *   BLACK PANTHER
 *   Absorb  [Hairline ====---]  60%    what the suit can still absorb, only while absorbing
 *   Kinetic [Hairline ======-]  84     the Kinetic charge (purple), in the Hairline style it always had
 *   +----+----+----+----+----+----+    R G Z X C V: shown move's cooldown (sweep + seconds), the Shift move's as the durability
 *   | R  | G  | Z  | X  | C  | V  |    bar; V lit while stalking / Senses run, C while absorbing; yellow while Shift shows the
 *   +----+----+----+----+----+----+    Shift moves
 * </pre>
 * Kept as they were: the hold-H necklace bar over the hotbar, and the Ancestral Plane (a purple sky over the whole screen for
 * the ten seconds after the soup).
 */
public final class BlackPantherHud {
	private static final int BAR_H = 3;
	private static final int NAME = 0xFFE2E2E2;
	private static final int BAR_BG = 0xAA0C0C0C;
	private static final int KINETIC = 0xFF9A4CFF;
	private static final int KINETIC_TEXT = 0xFFC08CFF;
	private static final int ABSORB = 0xFFD9B8FF;

	private BlackPantherHud() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null) {
			return;
		}
		BlackPantherState s = p.getAttachedOrElse(ModAttachments.BLACK_PANTHER_STATE, null);
		if (s == null) {
			return;
		}
		if (!s.hasPower && s.visionUntil > 0L) {
			renderVision(g, s, mc.level.getGameTime(), delta.getGameTimeDeltaPartialTick(false));
			return;
		}
		if (!s.hasPower || mc.options.hideGui) {
			return;
		}
		float hold = BlackPantherClient.holdProgress();
		if (hold > 0.1f) {
			// holding H: a thin bar over the hotbar fills toward the necklace coming off (or going back on) -- kept as it was
			int w = 82;
			int x = g.guiWidth() / 2 - w / 2;
			int y = g.guiHeight() - 62;
			g.fill(x, y, x + w, y + BAR_H, BAR_BG);
			g.fill(x, y, x + Math.round(w * hold), y + BAR_H, KINETIC);
			g.drawCenteredString(mc.font, Component.translatable(s.necklaceOff ? "hud.projecthero.black_panther.necklace_on"
					: "hud.projecthero.black_panther.necklace_off"), g.guiWidth() / 2, y - 10, NAME);
		}
		var exp = p.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		if (exp != null && !exp.activePower.isEmpty()) {
			return;
		}
		// v0.17.4: both movesets have a HUD -- the keys show whichever kit is live (the suit decides)
		boolean suited = s.suited;
		boolean shift = p.isShiftKeyDown();
		VanillaHud.Stack st = VanillaHud.Stack.bottomRight(g, VanillaHud.ORDER.length).keys(VanillaHud.ORDER.length);
		for (int i = 0; i < VanillaHud.ORDER.length; i++) {
			AbilitySlot slot = VanillaHud.ORDER[i];
			String[] ids = BlackPantherAbilityManager.idsOf(slot, suited);
			if (ids.length == 0) {
				continue;
			}
			String shown = ids[shift && ids.length > 1 ? 1 : 0];
			String other = ids.length > 1 ? ids[shift ? 0 : 1] : null;
			int cd = BlackPanther.cooldownRemaining(p, shown);
			float frac = cd / (float) Math.max(1, BlackPantherAbilityManager.maxCooldown(shown));
			float otherFrac = other == null ? 0f
					: BlackPanther.cooldownRemaining(p, other) / (float) Math.max(1, BlackPantherAbilityManager.maxCooldown(other));
			boolean active = (slot == AbilitySlot.SLOT_5 && (s.stalking || s.senses)) || (slot == AbilitySlot.SLOT_6 && s.absorbing);
			st.slot(i, VanillaHud.keyLabel(slot), true, shift, Math.min(1f, frac), VanillaHud.seconds(cd), Math.min(1f, otherFrac), active);
		}

		if (suited) {
			st.meter(Component.translatable("hud.projecthero.black_panther.kinetic"), s.kinetic / BlackPantherConfig.KINETIC_MAX, KINETIC,
					String.valueOf(Math.round(s.kinetic)), KINETIC_TEXT, MutationMeters.Style.HAIRLINE);
			if (s.absorbing) {
				float left = Mth.clamp(1f - s.absorbed / BlackPantherConfig.ABSORB_MAX, 0f, 1f);
				st.meter(Component.translatable("hud.projecthero.black_panther.absorb"), left, ABSORB, Math.round(left * 100) + "%", ABSORB,
						MutationMeters.Style.HAIRLINE);
			}
		}
		st.title(Component.translatable(suited ? "hud.projecthero.black_panther.title" : "hud.projecthero.black_panther.title_unsuited"), NAME);
		st.tags(passiveTags(p, s, mc.level.getGameTime()));

		if (MoveListOverlay.held()) {
			List<MoveListOverlay.Row> rows = new ArrayList<>();
			for (AbilitySlot slot : VanillaHud.ORDER) {
				String[] ids = BlackPantherAbilityManager.idsOf(slot, suited);
				if (ids.length > 0) {
					rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(slot), Component.translatable("projecthero.black_panther.ability." + ids[0]),
							ids.length > 1 ? Component.translatable("projecthero.black_panther.ability." + ids[1]) : null));
				}
			}
			// N has no HUD box (the project rule): the claws and the mask are listed here instead (the suit's)
			if (suited) {
				rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(AbilitySlot.SLOT_8), Component.translatable("projecthero.black_panther.ability.claws"),
						Component.translatable("projecthero.black_panther.ability.mask")));
			}
			rows.add(MoveListOverlay.Row.of("Hold " + MoveListOverlay.key(AbilitySlot.SLOT_7),
					Component.translatable("projecthero.black_panther.ability.necklace"), null));
			st.moveList(rows);
		}
	}

	private static float lastHealth = -1f;
	private static long lastDropAt;

	/**
	 * v0.17.4: the passives working right now, as status words over the HUD title -- Herb (out of the suit), Silent (in it),
	 * Night Eyes, Healing (Herb Vitality: hurt, and 5 s since the last hit), Hunted xN (Hunter's Instinct), Evade (a Backflip's
	 * window), Parry, and a grey No suit until the habit is crafted.
	 */
	private static List<VanillaHud.Tag> passiveTags(LocalPlayer p, BlackPantherState s, long now) {
		List<VanillaHud.Tag> tags = new ArrayList<>();
		float health = p.getHealth();
		if (lastHealth >= 0f && health < lastHealth - 0.01f) {
			lastDropAt = now;
		}
		lastHealth = health;
		if (s.suited) {
			tags.add(tag("silent", 0xFFC08CFF));
		} else {
			tags.add(tag("herb", 0xFFB070FF));
		}
		if (p.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION)) {
			tags.add(tag("night_eyes", 0xFF9AD0FF));
		}
		if (health < p.getMaxHealth() && now - lastDropAt >= BlackPantherConfig.HEAL_DELAY) {
			tags.add(tag("healing", 0xFFFF7A9A));
		}
		int hunted = PantherSensesClient.count(com.projecthero.mod.blackpanther.network.PantherSensesPayload.INSTINCT);
		if (hunted > 0) {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.black_panther.tag.hunted", hunted).getString(), 0xFFFF5040));
		}
		if (!s.suited && s.animId == BlackPantherState.ANIM_BACKFLIP && now - s.animStart <= BlackPantherConfig.EVADE_TICKS) {
			tags.add(tag("evade", 0xFFFFFFFF));
		}
		if (!s.suited && s.absorbing) {
			tags.add(tag("parry", 0xFFFFE08A));
		}
		if (!s.suited && !s.habit) {
			tags.add(tag("no_suit", 0xFF8A8A8A));
		}
		return tags;
	}

	private static VanillaHud.Tag tag(String key, int colour) {
		return new VanillaHud.Tag(Component.translatable("hud.projecthero.black_panther.tag." + key).getString(), colour);
	}

	/** The Ancestral Plane: a violet sky with slow stars, fading in and out. */
	private static void renderVision(GuiGraphics g, BlackPantherState s, long now, float partial) {
		float left = s.visionUntil - now - partial;
		float age = BlackPantherConfig.VISION_TICKS - left;
		float fade = Mth.clamp(Math.min(age, left) / 30f, 0f, 1f);
		int w = g.guiWidth();
		int h = g.guiHeight();
		int a = Math.round(fade * 245);
		g.fillGradient(0, 0, w, h, (a << 24) | 0x12062E, (a << 24) | 0x4A1A8C);
		for (int i = 0; i < 90; i++) {
			float sx = Mth.frac(Mth.sin(i * 12.9898f) * 43758.547f);
			float sy = Mth.frac(Mth.sin(i * 78.233f) * 24634.633f);
			float drift = (age * (0.0004f + sx * 0.0006f)) % 1f;
			int x = Math.round(((sx + drift) % 1f) * w);
			int y = Math.round(sy * h * 0.85f);
			float twinkle = 0.5f + 0.5f * Mth.sin(age * 0.09f + i);
			int sa = Math.round(fade * (110 + 145 * twinkle));
			g.fill(x, y, x + 1 + i % 2, y + 1 + i % 2, (sa << 24) | 0xE9D8FF);
		}
	}
}
