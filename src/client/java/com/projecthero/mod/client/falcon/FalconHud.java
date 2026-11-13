package com.projecthero.mod.client.falcon;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.client.gui.MoveListOverlay;
import com.projecthero.mod.client.gui.VanillaHud;
import com.projecthero.mod.falcon.Falcon;
import com.projecthero.mod.falcon.FalconAbilityManager;
import com.projecthero.mod.falcon.FalconConfig;
import com.projecthero.mod.falcon.data.FalconState;
import com.projecthero.mod.falcon.entity.RedwingEntity;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.visual.MutationMeters;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3: the Falcon HUD on the Minecraft-style look of the newest powers ({@link VanillaHud.Stack}, bottom-right):
 * <pre>
 *   Goggles: 3 threats marked  34 b/s           status tags: the goggles line, and the speed while flying (JETS while X burns them)
 *   FALCON
 *   Thrust [Meter =========-----]  63          the thrust meter (red; pulses while the jets burn)
 *   Ammo   [Meter =============-]  88          the Wrist Gun rounds (brass; red and flashing when nearly dry)
 *   +----+----+----+----+----+----+            R G Z X C V: shown move's cooldown (sweep + seconds), the Shift move's as the
 *   | R  | G  | Z  | X  | C  | V  |            durability bar; greyed when he can't pay for it; lit while that key's move runs;
 *   +----+----+----+----+----+----+            yellow while Shift shows the Shift moves
 * </pre>
 * With the Tactical Goggles on, the creature in the crosshair (out to 60 blocks) gets a readout (unchanged): name, health,
 * distance. Hold Left-Alt for the move names.
 */
public final class FalconHud {
	private static final int ACCENT = 0xFFE8283C;
	private static final int NAME = 0xFFE2E2E2;
	private static final int HINT = 0xFF9A9A9A;
	private static final int THRUST = 0xFFE8283C;
	private static final int THRUST_TEXT = 0xFFFF6070;
	private static final int THRUST_BOOST = 0xFFFF9A3A;
	private static final int AMMO = 0xFFE8B23A;
	private static final int AMMO_LOW = 0xFFFF4040;

	/** Set only by the client screenshot harness: the HUD draws as if the player were sneaking. */
	public static volatile boolean forceShift;

	private FalconHud() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null || mc.options.hideGui || !Falcon.hasPower(p)) {
			return;
		}
		FalconState s = Falcon.peek(p);
		if (s == null) {
			return;
		}
		var exp = p.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.EXPERIMENTAL_STATE, null);
		if (exp != null && !exp.activePower.isEmpty()) {
			return;
		}
		long now = mc.level.getGameTime();
		boolean shift = forceShift || p.isShiftKeyDown();
		boolean redwingBusy = mc.level.getEntity(s.redwingId) instanceof RedwingEntity r && r.busy();
		VanillaHud.Stack st = VanillaHud.Stack.bottomRight(g, VanillaHud.ORDER.length).keys(VanillaHud.ORDER.length);
		for (int i = 0; i < VanillaHud.ORDER.length; i++) {
			AbilitySlot slot = VanillaHud.ORDER[i];
			String[] ids = FalconAbilityManager.idsOf(slot);
			if (ids.length == 0) {
				continue;
			}
			String id = ids[Math.min(ids.length - 1, shift ? 1 : 0)];
			String other = ids.length > 1 ? ids[shift ? 0 : 1] : null;
			int cd = Falcon.cooldownRemaining(p, id);
			float frac = cd / (float) Math.max(1, FalconAbilityManager.maxCooldown(id));
			float otherFrac = other == null ? 0f : Falcon.cooldownRemaining(p, other) / (float) Math.max(1, FalconAbilityManager.maxCooldown(other));
			float cost = FalconAbilityManager.cost(id);
			boolean usable = s.thrust + 1.0e-3f >= cost && (!id.equals(com.projecthero.mod.falcon.FalconAbilities.GUNS) || s.ammo >= FalconConfig.AMMO_PER_SHOT);
			boolean active = switch (slot) {
				case SLOT_1 -> s.firingUntil > now;
				case SLOT_2 -> Falcon.shieldUp(p);
				case SLOT_3 -> s.rollUntil > now || s.boosting || (s.animId == FalconState.ANIM_AFTERBURNER && now - s.animStart < 10);
				case SLOT_4 -> redwingBusy;
				case SLOT_5 -> s.goggles || s.carryId != -1;
				case SLOT_6 -> s.animId == FalconState.ANIM_DIVE && now - s.animStart < FalconConfig.DIVE_MAX_TICKS && s.flying;
				default -> false;
			};
			st.slot(i, VanillaHud.keyLabel(slot), usable || active, shift, Math.min(1f, frac), VanillaHud.seconds(cd), Math.min(1f, otherFrac), active);
		}

		// the two meters, Ammo under Thrust (the plain Meter style)
		float ammo = Mth.clamp(s.ammo, 0f, FalconConfig.AMMO_MAX);
		int ammoColour = ammo < FalconConfig.AMMO_MAX * 0.2f ? ((now / 4) % 2 == 0 ? AMMO_LOW : AMMO) : AMMO;
		st.meter(Component.translatable("hud.projecthero.falcon.ammo"), ammo / FalconConfig.AMMO_MAX, ammoColour, String.valueOf((int) Math.floor(ammo)),
				ammoColour, MutationMeters.Style.METER);
		float thrust = Mth.clamp(s.thrust, 0f, FalconConfig.THRUST_MAX);
		int thrustColour = s.boosting && s.flying ? ((now / 3) % 2 == 0 ? THRUST_BOOST : THRUST) : THRUST;
		st.meter(Component.translatable("hud.projecthero.falcon.thrust"), thrust / FalconConfig.THRUST_MAX, thrustColour,
				String.valueOf(Math.round(thrust)), thrustColour == THRUST ? THRUST_TEXT : thrustColour, MutationMeters.Style.METER);
		st.title(Component.translatable("hud.projecthero.falcon.title"), NAME);

		// status tags: the goggles line and the speed readout while flying (JETS while they burn)
		List<VanillaHud.Tag> tags = new ArrayList<>();
		if (s.goggles) {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.falcon.goggles", FalconClient.markedCount()).getString(), 0xFFFF6070));
		}
		if (s.flying) {
			boolean jets = s.boosting;
			tags.add(new VanillaHud.Tag(Component.translatable(jets ? "hud.projecthero.falcon.speed_jets" : "hud.projecthero.falcon.speed",
					(int) Math.round(FalconFlightClient.speedBps())).getString(), jets ? ((now / 3) % 2 == 0 ? THRUST_BOOST : 0xFFFFD08A) : HINT));
		}
		st.tags(tags);

		// Left-Alt: the move list (plain and Shift per key), above the whole HUD
		if (MoveListOverlay.held()) {
			List<MoveListOverlay.Row> rows = new ArrayList<>();
			for (AbilitySlot slot : VanillaHud.ORDER) {
				String[] ids = FalconAbilityManager.idsOf(slot);
				if (ids.length > 0) {
					rows.add(MoveListOverlay.Row.of(VanillaHud.keyLabel(slot), Component.translatable("projecthero.falcon.ability." + ids[0]),
							ids.length > 1 ? Component.translatable("projecthero.falcon.ability." + ids[1]) : null));
				}
			}
			st.moveList(rows);
		}

		if (s.goggles) {
			gogglesReadout(g, mc);
		}
	}

	/** The Tactical Goggles: the creature nearest the crosshair (within ~4 degrees, 60 blocks): name, health, distance. */
	private static void gogglesReadout(GuiGraphics g, Minecraft mc) {
		Vec3 eye = mc.player.getEyePosition();
		Vec3 look = mc.player.getLookAngle();
		LivingEntity best = null;
		double bestDot = 0.9975;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof LivingEntity le) || e == mc.player || !le.isAlive() || e.distanceToSqr(mc.player) > FalconConfig.GOGGLES_RANGE * FalconConfig.GOGGLES_RANGE) {
				continue;
			}
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
			double dot = to.normalize().dot(look);
			if (dot > bestDot) {
				bestDot = dot;
				best = le;
			}
		}
		int cx = g.guiWidth() / 2;
		int cy = g.guiHeight() / 2 + 12;
		if (best == null) {
			return;
		}
		Component name = best.getDisplayName();
		String stats = String.format(java.util.Locale.ROOT, "%.0f / %.0f HP   %.0f m", best.getHealth(), best.getMaxHealth(), best.distanceTo(mc.player));
		int w = Math.min(220, Math.max(mc.font.width(name), mc.font.width(stats)) + 8);
		g.fill(cx - w / 2, cy, cx + w / 2, cy + 22, 0xA0100608);
		g.renderOutline(cx - w / 2, cy, w, 22, ACCENT);
		List<FormattedCharSequence> nl = mc.font.split(name, w - 6);
		g.drawCenteredString(mc.font, nl.isEmpty() ? FormattedCharSequence.EMPTY : nl.get(0), cx, cy + 2, ACCENT);
		g.drawCenteredString(mc.font, stats, cx, cy + 12, NAME);
	}
}
