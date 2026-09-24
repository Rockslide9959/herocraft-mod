package com.projecthero.mod.client.mixin;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.data.ExperimentalState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Laser Vision "Thermal Vision", local player only: nearby living entities glow, but <em>only in the
 * thermal-viewer's own client</em>. Nothing is sent to the server and no {@code GLOWING} mob-effect is
 * applied, so other players get no benefit from this power.
 *
 * <p>Forcing {@link Entity#isCurrentlyGlowing()} true is exactly what the vanilla Glowing effect does
 * client-side (it sets shared entity flag 6, which this method reads); driving it from here keeps the
 * outline strictly per-viewer.
 */
@Mixin(Entity.class)
public abstract class EntityGlowMixin {
	@Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true)
	private void projecthero$thermalVision(CallbackInfoReturnable<Boolean> cir) {
		Entity self = (Entity) (Object) this;
		if (self instanceof LocalPlayer) {
			return;
		}
		LocalPlayer viewer = Minecraft.getInstance().player;
		if (viewer == null || viewer == self) {
			return;
		}
		// Iron Man: while the viewer wears a powered Iron Man helmet AND has toggled mob-highlight on (V),
		// nearby hostiles are outlined. Client-only, no server GLOWING effect -- purely the wearer's view
		// ("only happen for me"). "changes 18": when the viewer wears an Iron Man helmet we take an
		// authoritative yes/no decision for every entity the highlight could touch, so toggling it off
		// can't leave a mob stuck glowing.
		Boolean ironMan = projecthero$ironManHighlightDecision(viewer, self);
		if (ironMan != null) {
			cir.setReturnValue(ironMan);
			return;
		}

		// v0.10.19: Magnetic Sense is the one detection highlight that DOES light up another player --
		// specifically one wearing magnetic (iron-family) equipment, since that gear is exactly what the
		// power senses. Checked before the generic player exclusion below.
		if (self instanceof net.minecraft.world.entity.player.Player otherPlayer) {
			ExperimentalState magState = viewer.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
			if (magState != null && magState.ownedPowers.contains("power_26_magnetic_manipulation")
					&& magState.activeToggles.contains("power_26_magnetic_manipulation/magnetic_sense")
					&& self.distanceToSqr(viewer) <= 20.0 * 20.0
					&& com.projecthero.mod.hero.power.p26.MagneticMaterials.hasMagneticEquipment(otherPlayer)) {
				cir.setReturnValue(true);
			}
			return;
		}

		// v0.10.13: mob-detection highlights (Spider-Sense, Predator Vision, Thermal / Echolocation /
		// Magnetic Sense) never outline a player -- not the viewer and not anyone else. Lighting up
		// players just makes people trivial to spot, which is not what "see the monsters around you" is
		// meant to do. The Iron Man threat highlight above is a deliberate targeting HUD and is exempt.

		// v0.9.3: Spider-Sense red threat glow -- purely this viewer's own render, fed by
		// SpiderSenseGlowPayload. Nothing is set on the mob server-side, so no other player sees it.
		if (self instanceof LivingEntity) {
			com.projecthero.mod.spider.data.SpiderManState spider =
					viewer.getAttachedOrElse(ModAttachments.SPIDER_MAN_STATE, null);
			if (spider != null && spider.hasPower) {
				long now = viewer.level() != null ? viewer.level().getGameTime() : 0L;
				if (com.projecthero.mod.client.spider.SpiderSenseGlowClient.isThreat(self.getId(), now)) {
					cir.setReturnValue(true);
					return;
				}
			}
		}

		// v0.9.23: Symbiote "Predator Vision" -- a bonded host sees every living thing within 20 blocks
		// outlined. Purely this viewer's own render, like the powers above.
		if (self instanceof LivingEntity) {
			com.projecthero.mod.symbiote.SymbioteState symb =
					viewer.getAttachedOrElse(ModAttachments.SYMBIOTE_STATE, null);
			if (symb != null && symb.hasSymbiote && com.projecthero.mod.client.symbiote.SymbioteFxClient.predatorVisionOn()
					&& self.distanceToSqr(viewer) <= 20.0 * 20.0) {
				cir.setReturnValue(true);
				return;
			}
		}

		// v0.12.1: Wolverine enhanced senses -- hostile mobs within 12 blocks are outlined, for the
		// Wolverine own client only (nothing is set on the mob, no packet, no wallhack for anyone else).
		if (self instanceof net.minecraft.world.entity.monster.Enemy && com.projecthero.mod.wolverine.Wolverine.hasPower(viewer)
				&& self.distanceToSqr(viewer) <= com.projecthero.mod.wolverine.WolverineConfig.SENSE_RADIUS
						* com.projecthero.mod.wolverine.WolverineConfig.SENSE_RADIUS) {
			cir.setReturnValue(true);
			return;
		}

		// v0.11.10: Green Lantern Ring Scan -- purely this viewer's own render, fed by
		// GreenLanternRingScanPayload. Nothing is set on the target server-side any more, so no other
		// player's client is told anything (fixes "everyone in the world can see the glowing creatures").
		if (self instanceof LivingEntity && com.projecthero.mod.greenlantern.GreenLantern.hasPower(viewer)) {
			long now = viewer.level() != null ? viewer.level().getGameTime() : 0L;
			if (com.projecthero.mod.client.greenlantern.GreenLanternRingScanClient.isHostile(self.getId(), now)
					|| com.projecthero.mod.client.greenlantern.GreenLanternRingScanClient.isPassive(self.getId(), now)) {
				cir.setReturnValue(true);
				return;
			}
		}

		ExperimentalState st = viewer.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		if (st == null) {
			return;
		}
		// v0.9.3: these are toggled modes, which stay live for any OWNED Experimental Tier power even
		// while a different one holds the six slots -- so check ownership, not selection.
		boolean thermal = self instanceof LivingEntity && st.ownedPowers.contains("power_02_laser_vision")
				&& st.activeToggles.contains("power_02_laser_vision/thermal_vision");
		// v0.10.14: the Echolocation TOGGLE is now Enhanced Senses -- the actual sonar ping is a
		// Shift + right-click that arms a short "echo_until" window on the synced attachment.
		Float echoUntil = st.resources.get("power_14_sonic_scream/echo_until");
		boolean echo = self instanceof LivingEntity && st.ownedPowers.contains("power_14_sonic_scream")
				&& echoUntil != null && echoUntil > (viewer.level() != null ? viewer.level().getGameTime() : 0L);
		boolean magnetic = st.ownedPowers.contains("power_26_magnetic_manipulation")
				&& st.activeToggles.contains("power_26_magnetic_manipulation/magnetic_sense");
		// v0.10.19: Shadow Manipulation -- while the viewer is in nighttime darkness (or the Deep Dark),
		// nearby hostiles get a black outline.
		boolean shadowSight = self instanceof net.minecraft.world.entity.monster.Enemy
				&& "power_19_shadow_manipulation".equals(st.activePower)
				&& com.projecthero.mod.hero.power.p19.ShadowManipulationHandlers.tier(viewer.level(), viewer.blockPosition()) >= 1.0f;
		if (shadowSight && self.distanceToSqr(viewer) <= 24.0 * 24.0) {
			cir.setReturnValue(true);
			return;
		}
		// Enhanced hearing: anything within 40 blocks that just moved flashes for this viewer alone.
		boolean hearing = self instanceof LivingEntity && st.ownedPowers.contains("power_14_sonic_scream")
				&& com.projecthero.mod.client.SonicMotionClient.isFlashing(self.getId(),
						viewer.level() != null ? viewer.level().getGameTime() : 0L);
		if (!thermal && !echo && !magnetic && !hearing) {
			return;
		}
		if (hearing && !thermal && !echo && !magnetic) {
			cir.setReturnValue(self.distanceToSqr(viewer) <= 40.0 * 40.0);
			return;
		}
		double range = magnetic ? 20.0 : (echo ? 20.0 : 24.0);
		if (self.distanceToSqr(viewer) > range * range) {
			return;
		}
		if (thermal || echo) {
			cir.setReturnValue(true);
			return;
		}
		// magnetic sense: only magnetically reactive objects, never Mjolnir
		if (com.projecthero.mod.hero.power.p26.MagneticMaterials.isMagneticEntity(self)) {
			cir.setReturnValue(true);
		}
	}

	/**
	 * "changes 18": {@code true}/{@code false} when the Iron Man threat highlight owns the decision for
	 * this entity (viewer wears an Iron Man helmet and {@code self} is an entity the highlight could
	 * outline), or {@code null} to fall through to vanilla / the other powers. Returning {@code false}
	 * for a candidate entity that should NOT glow is what stops a mob staying lit after the toggle is
	 * turned off.
	 */
	@org.spongepowered.asm.mixin.Unique
	private static Boolean projecthero$ironManHighlightDecision(net.minecraft.client.player.LocalPlayer viewer, Entity self) {
		if (!(self instanceof LivingEntity)) {
			return null;
		}
		if (!(viewer.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).getItem()
				instanceof com.projecthero.mod.ironman.item.IronManArmorItem helmet)) {
			return null;
		}
		com.projecthero.mod.ironman.suit.IronManSuit wornSuit =
				com.projecthero.mod.ironman.suit.IronManSuits.byId(helmet.suitId());
		boolean coloured = wornSuit != null && wornSuit.coloredEntityGlow();
		// candidate = an entity this suit's highlight could plausibly light up. A coloured-glow suit
		// (Mark 6/7) can light ANY living entity; every other mark only ever lights hostiles.
		boolean candidate = coloured || self instanceof net.minecraft.world.entity.monster.Enemy;
		if (!candidate) {
			return null;
		}
		if (projecthero$ironManThreatHighlight(viewer, self)) {
			return Boolean.TRUE;
		}
		// Not highlighted right now. Only take the authoritative "off" decision inside the scan radius
		// this suit's highlight actually works in -- that is exactly where a stale outline could linger,
		// and it leaves anything further out to vanilla / the other powers.
		double range = wornSuit == null ? 34.0 : wornSuit.targetScanRange();
		return self.distanceToSqr(viewer) <= range * range ? Boolean.FALSE : null;
	}

	@org.spongepowered.asm.mixin.Unique
	private static boolean projecthero$ironManThreatHighlight(net.minecraft.client.player.LocalPlayer viewer, Entity self) {
		if (!(self instanceof LivingEntity target) || !target.isAlive()) {
			return false;
		}
		if (!(viewer.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).getItem()
				instanceof com.projecthero.mod.ironman.item.IronManArmorItem helmet)) {
			return false;
		}
		com.projecthero.mod.ironman.suit.IronManSuit wornSuit =
				com.projecthero.mod.ironman.suit.IronManSuits.byId(helmet.suitId());
		com.projecthero.mod.ironman.data.TonyStarkState st =
				viewer.getAttachedOrElse(ModAttachments.TONY_STARK_STATE, null);
		// "powered on": the worn suit still has some energy in the pool.
		if (st == null || !st.hasPower || st.suitEnergy.getOrDefault(helmet.suitId(), 0.0f) <= 0.0f) {
			return false;
		}
		// "changes 16": a suit with a passive-highlight range always outlines EVERY living entity within
		// that radius -- no toggle, no filter. (No shipped suit uses this now; kept for compatibility.)
		double passive = wornSuit == null ? 0.0 : wornSuit.passiveHighlightRange();
		if (passive > 0.0) {
			return self.distanceToSqr(viewer) <= passive * passive;
		}
		if (!st.mobHighlightOn) {
			return false;
		}
		double range = wornSuit == null ? 34.0 : wornSuit.targetScanRange();
		if (self.distanceToSqr(viewer) > range * range) {
			return false;
		}
		// "changes 17": a coloured-glow suit (Mark 6 / Mark 7) outlines EVERY nearby entity; every other
		// mark's toggle stays hostiles-only ("changes 13").
		if (wornSuit != null && wornSuit.coloredEntityGlow()) {
			return true;
		}
		return self instanceof net.minecraft.world.entity.monster.Enemy;
	}

	/**
	 * "changes 17": colour the outline of an Iron Man coloured-glow highlight (Mark 6 / Mark 7) by
	 * entity type -- hostile mobs red, other players yellow, everything else blue. Purely the viewer's
	 * own render (the same per-client path {@link #projecthero$thermalVision} drives); no team is set on
	 * the server.
	 */
	/**
	 * Spider-Sense danger glow colour. For a Spider-Man viewer, any entity currently in their own
	 * Spider-Sense threat set (v0.9.3: {@code SpiderSenseGlowClient}, per-viewer, never synced to
	 * others) is outlined <em>red</em>. Purely the viewer's own render.
	 */
	@Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
	private void projecthero$spiderSenseGlowColor(CallbackInfoReturnable<Integer> cir) {
		if (cir.isCancelled()) {
			return;
		}
		Entity self = (Entity) (Object) this;
		if (self instanceof LocalPlayer || !(self instanceof LivingEntity)) {
			return;
		}
		LocalPlayer viewer = Minecraft.getInstance().player;
		if (viewer == null || viewer == self) {
			return;
		}
		com.projecthero.mod.spider.data.SpiderManState st =
				viewer.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.SPIDER_MAN_STATE, null);
		if (st == null || !st.hasPower) {
			return;
		}
		long now = viewer.level() != null ? viewer.level().getGameTime() : 0L;
		if (com.projecthero.mod.client.spider.SpiderSenseGlowClient.isThreat(self.getId(), now)) {
			cir.setReturnValue(0xFF3355);
		}
	}

	/**
	 * Symbiote Predator Vision colour: hostile mobs red, other players dark purple, everything else
	 * dark blue. Purely the viewer's own render.
	 */
	@Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
	private void projecthero$predatorVisionColor(CallbackInfoReturnable<Integer> cir) {
		if (cir.isCancelled()) {
			return;
		}
		Entity self = (Entity) (Object) this;
		if (self instanceof LocalPlayer || !(self instanceof LivingEntity)) {
			return;
		}
		LocalPlayer viewer = Minecraft.getInstance().player;
		if (viewer == null || viewer == self) {
			return;
		}
		com.projecthero.mod.symbiote.SymbioteState symb =
				viewer.getAttachedOrElse(ModAttachments.SYMBIOTE_STATE, null);
		if (symb == null || !symb.hasSymbiote || !com.projecthero.mod.client.symbiote.SymbioteFxClient.predatorVisionOn()
				|| self.distanceToSqr(viewer) > 20.0 * 20.0) {
			return;
		}
		if (self instanceof net.minecraft.world.entity.monster.Enemy) {
			cir.setReturnValue(0xC01818);
		} else if (self instanceof net.minecraft.world.entity.player.Player) {
			cir.setReturnValue(0x4B0F7A);
		} else {
			cir.setReturnValue(0x1B2C7A);
		}
	}

	/**
	 * Green Lantern Ring Scan colour: red for a hostile the scan picked up, green for a passive/neutral
	 * one -- "change colour depending on hostile or passive mobs". Purely the viewer's own render.
	 */
	@Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
	private void projecthero$ringScanColor(CallbackInfoReturnable<Integer> cir) {
		if (cir.isCancelled()) {
			return;
		}
		Entity self = (Entity) (Object) this;
		if (self instanceof LocalPlayer || !(self instanceof LivingEntity)) {
			return;
		}
		LocalPlayer viewer = Minecraft.getInstance().player;
		if (viewer == null || viewer == self || !com.projecthero.mod.greenlantern.GreenLantern.hasPower(viewer)) {
			return;
		}
		long now = viewer.level() != null ? viewer.level().getGameTime() : 0L;
		if (com.projecthero.mod.client.greenlantern.GreenLanternRingScanClient.isHostile(self.getId(), now)) {
			cir.setReturnValue(0xFF3B3B);
		} else if (com.projecthero.mod.client.greenlantern.GreenLanternRingScanClient.isPassive(self.getId(), now)) {
			cir.setReturnValue(0x3BFF6B);
		}
	}

	/** Shadow Manipulation's black outline for hostiles seen in darkness. Purely the viewer's own render. */
	@Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
	private void projecthero$shadowOutlineColor(CallbackInfoReturnable<Integer> cir) {
		if (cir.isCancelled()) {
			return;
		}
		Entity self = (Entity) (Object) this;
		if (self instanceof LocalPlayer || !(self instanceof net.minecraft.world.entity.monster.Enemy)) {
			return;
		}
		LocalPlayer viewer = Minecraft.getInstance().player;
		if (viewer == null || viewer == self) {
			return;
		}
		ExperimentalState st = viewer.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		if (st == null || !"power_19_shadow_manipulation".equals(st.activePower)
				|| self.distanceToSqr(viewer) > 24.0 * 24.0
				|| com.projecthero.mod.hero.power.p19.ShadowManipulationHandlers.tier(viewer.level(), viewer.blockPosition()) < 1.0f) {
			return;
		}
		cir.setReturnValue(0x0A0A0C);
	}

	@Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
	private void projecthero$ironManGlowColor(CallbackInfoReturnable<Integer> cir) {
		Entity self = (Entity) (Object) this;
		if (self instanceof LocalPlayer) {
			return;
		}
		LocalPlayer viewer = Minecraft.getInstance().player;
		if (viewer == null || viewer == self) {
			return;
		}
		if (!(viewer.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).getItem()
				instanceof com.projecthero.mod.ironman.item.IronManArmorItem helmet)) {
			return;
		}
		com.projecthero.mod.ironman.suit.IronManSuit wornSuit =
				com.projecthero.mod.ironman.suit.IronManSuits.byId(helmet.suitId());
		if (wornSuit == null || !wornSuit.coloredEntityGlow()) {
			return;
		}
		if (!projecthero$ironManThreatHighlight(viewer, self)) {
			return;
		}
		int color;
		if (self instanceof net.minecraft.world.entity.player.Player) {
			color = 0xFFE64A; // yellow
		} else if (self instanceof net.minecraft.world.entity.monster.Enemy) {
			color = 0xFF4A4A; // red
		} else {
			color = 0x5AA0FF; // blue
		}
		cir.setReturnValue(color);
	}
}
