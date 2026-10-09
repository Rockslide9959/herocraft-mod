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
		// v0.13.9: the Oathbreaker never glows -- no outline from any sense, highlight or Glowing effect
		if (self instanceof com.projecthero.mod.oathbreaker.entity.OathbreakerEntity) {
			cir.setReturnValue(false);
			return;
		}
		// v0.15.4 -- PRIVACY RULE, DO NOT REMOVE: every highlight below is the LOCAL viewer's own private view and must
		// only ever be answered for a CLIENT-side entity. In single-player / LAN the integrated server shares this JVM,
		// and vanilla's LivingEntity#updateGlowingStatus asks isCurrentlyGlowing() on the SERVER thread to set shared
		// flag 6 -- which is synced to every player tracking the mob. Answering there leaked the host's Iron Man (and
		// every other sense) highlight to all other players and squad mates. See IronManHighlight's class javadoc.
		if (!com.projecthero.mod.ironman.IronManHighlight.mayDecide(self)) {
			return;
		}
		LocalPlayer viewer = Minecraft.getInstance().player;
		if (viewer == null || viewer == self) {
			return;
		}
		// v0.15.12: the Mind Stone in the viewer's off hand outlines hostiles within 24 blocks -- the viewer's own view only
		if (com.projecthero.mod.client.ultron.UltronClient.mindStoneOutlines(self)) {
			cir.setReturnValue(true);
			return;
		}
		// Iron Man: while the viewer wears a powered Iron Man helmet with the highlight on, nearby mobs are outlined.
		// Client-only, decided from the viewer's OWN state -- never a server GLOWING effect / glowing tag / shared flag,
		// so no other player (squad mates included) ever sees it. "changes 18": when the viewer wears an Iron Man helmet
		// we take an authoritative yes/no decision for every entity the highlight could touch, so toggling it off can't
		// leave a mob stuck glowing.
		Boolean ironMan = com.projecthero.mod.ironman.IronManHighlight.decision(viewer, self);
		if (ironMan != null) {
			cir.setReturnValue(ironMan);
			return;
		}

		// Wolverine senses (v0.12.10): mobs hunting him glow orange at all times, and a Sniff lights up every
		// living thing nearby for 20 s. Fed by WolverineSensePayload to this viewer alone -- nothing is
		// set on the entity, so only a Wolverine ever sees it. Checked before the player exclusion below
		// because a Sniff deliberately marks players too.
		if (self instanceof LivingEntity && com.projecthero.mod.wolverine.Wolverine.hasPower(viewer)) {
			long wnow = viewer.level() != null ? viewer.level().getGameTime() : 0L;
			if (com.projecthero.mod.client.wolverine.WolverineSenseClient.isHunter(self.getId(), wnow)
					|| com.projecthero.mod.client.wolverine.WolverineSenseClient.isSniffed(self.getId(), wnow)) {
				cir.setReturnValue(true);
				return;
			}
			// v0.13.4: Adamantium Execution's lock-on target, red, caster-only -- fed by
			// WolverineExecutionTargetPayload, cleared the instant the ability resolves.
			if (com.projecthero.mod.client.wolverine.WolverineExecutionTargetClient.isTarget(self.getId())) {
				cir.setReturnValue(true);
				return;
			}
		}

		// v0.15.18: the Punisher's Target Designation mark and Threat Assessment -- fed by PunisherIntelPayload to him alone,
		// nothing is set on the entity. Checked before the player exclusion below: a marked player is outlined too.
		if (self instanceof LivingEntity && com.projecthero.mod.punisher.Punisher.hasPower(viewer)) {
			long pnow = viewer.level() != null ? viewer.level().getGameTime() : 0L;
			if (com.projecthero.mod.client.punisher.PunisherIntelClient.isMarked(self.getId(), pnow)
					|| com.projecthero.mod.client.punisher.PunisherIntelClient.isThreat(self.getId(), pnow)) {
				cir.setReturnValue(true);
				return;
			}
		}

		// v0.12.43: Titan Roar -- everything the roar reached within 50 blocks is outlined blue for the roaring shifter alone.
		if (self instanceof LivingEntity && com.projecthero.mod.client.titanshifter.TitanRoarSenseClient.isMarked(self.getId(),
				viewer.level() != null ? viewer.level().getGameTime() : 0L)) {
			cir.setReturnValue(true);
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
			if (symb != null && symb.hasSymbiote && com.projecthero.mod.symbiote.Symbiote.isNormalHost(viewer)
					&& com.projecthero.mod.client.symbiote.SymbioteFxClient.predatorVisionOn()
					&& self.distanceToSqr(viewer) <= 20.0 * 20.0) {
				cir.setReturnValue(true);
				return;
			}
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
		// v0.14.5: Laser Vision's Thermal Vision reaches 50 blocks
		double range = thermal ? com.projecthero.mod.hero.power.p02.LaserVisionHandlers.THERMAL_RANGE
				: magnetic ? 20.0 : (echo ? 20.0 : 24.0);
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

	/** v0.15.12: the Mind Stone's gold outline (the viewer's own render only, client-side entities only). */
	@Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
	private void projecthero$mindStoneColor(CallbackInfoReturnable<Integer> cir) {
		Entity self = (Entity) (Object) this;
		if (self instanceof LocalPlayer || !com.projecthero.mod.ironman.IronManHighlight.mayDecide(self)) {
			return;
		}
		if (com.projecthero.mod.client.ultron.UltronClient.mindStoneOutlines(self)) {
			cir.setReturnValue(com.projecthero.mod.client.ultron.UltronClient.MIND_STONE_COLOR);
		}
	}

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
		if (symb == null || !symb.hasSymbiote || !com.projecthero.mod.symbiote.Symbiote.isNormalHost(viewer)
				|| !com.projecthero.mod.client.symbiote.SymbioteFxClient.predatorVisionOn()
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

	/** v0.12.43: Titan Roar's blue outline (the viewer's own render only). */
	@Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
	private void projecthero$titanRoarColor(CallbackInfoReturnable<Integer> cir) {
		if (cir.isCancelled()) {
			return;
		}
		Entity self = (Entity) (Object) this;
		LocalPlayer viewer = Minecraft.getInstance().player;
		if (viewer == null || viewer == self || !(self instanceof LivingEntity)) {
			return;
		}
		if (com.projecthero.mod.client.titanshifter.TitanRoarSenseClient.isMarked(self.getId(),
				viewer.level() != null ? viewer.level().getGameTime() : 0L)) {
			cir.setReturnValue(0x3C9BFF);
		}
	}

	/** Wolverine: orange for hostile / hunting mobs, pale gold for anything else a Sniff marked. */
	@Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
	private void projecthero$wolverineSenseColor(CallbackInfoReturnable<Integer> cir) {
		if (cir.isCancelled()) {
			return;
		}
		Entity self = (Entity) (Object) this;
		if (self instanceof LocalPlayer || !(self instanceof LivingEntity)) {
			return;
		}
		LocalPlayer viewer = Minecraft.getInstance().player;
		if (viewer == null || viewer == self || !com.projecthero.mod.wolverine.Wolverine.hasPower(viewer)) {
			return;
		}
		if (com.projecthero.mod.client.wolverine.WolverineExecutionTargetClient.isTarget(self.getId())) {
			cir.setReturnValue(0xFF3333);
			return;
		}
		long now = viewer.level() != null ? viewer.level().getGameTime() : 0L;
		boolean hunter = com.projecthero.mod.client.wolverine.WolverineSenseClient.isHunter(self.getId(), now);
		boolean sniffed = com.projecthero.mod.client.wolverine.WolverineSenseClient.isSniffed(self.getId(), now);
		if (hunter || (sniffed && self instanceof net.minecraft.world.entity.monster.Enemy)) {
			cir.setReturnValue(0xFF8A00);
		} else if (sniffed) {
			cir.setReturnValue(0xFFE9A0);
		}
	}

	/** v0.15.18: the Punisher's mark is red; his Threat Assessment shows hostiles orange, anything else pale grey. */
	@Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
	private void projecthero$punisherIntelColor(CallbackInfoReturnable<Integer> cir) {
		if (cir.isCancelled()) {
			return;
		}
		Entity self = (Entity) (Object) this;
		if (self instanceof LocalPlayer || !(self instanceof LivingEntity)
				|| !com.projecthero.mod.ironman.IronManHighlight.mayDecide(self)) {
			return;
		}
		LocalPlayer viewer = Minecraft.getInstance().player;
		if (viewer == null || viewer == self || !com.projecthero.mod.punisher.Punisher.hasPower(viewer)) {
			return;
		}
		long now = viewer.level() != null ? viewer.level().getGameTime() : 0L;
		if (com.projecthero.mod.client.punisher.PunisherIntelClient.isMarked(self.getId(), now)) {
			cir.setReturnValue(com.projecthero.mod.client.punisher.PunisherIntelClient.MARK_COLOR);
		} else if (com.projecthero.mod.client.punisher.PunisherIntelClient.isThreat(self.getId(), now)) {
			cir.setReturnValue(self instanceof net.minecraft.world.entity.monster.Enemy
					? com.projecthero.mod.client.punisher.PunisherIntelClient.THREAT_HOSTILE_COLOR
					: com.projecthero.mod.client.punisher.PunisherIntelClient.THREAT_OTHER_COLOR);
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

	/**
	 * "changes 17": colour the Iron Man coloured-glow highlight (Mark 6 / Mark 7) by entity type -- hostiles red, other
	 * players yellow, everything else blue. Purely the LOCAL viewer's own render (v0.15.4: never for a server-side entity
	 * and never from anyone else's state -- see IronManHighlight); no team is ever set on the server.
	 */
	@Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
	private void projecthero$ironManGlowColor(CallbackInfoReturnable<Integer> cir) {
		Entity self = (Entity) (Object) this;
		if (self instanceof LocalPlayer || !com.projecthero.mod.ironman.IronManHighlight.mayDecide(self)) {
			return;
		}
		LocalPlayer viewer = Minecraft.getInstance().player;
		if (viewer == null || viewer == self) {
			return;
		}
		com.projecthero.mod.ironman.suit.IronManSuit wornSuit = com.projecthero.mod.ironman.IronManHighlight.helmetSuit(viewer);
		if (wornSuit == null || !wornSuit.coloredEntityGlow()
				|| !com.projecthero.mod.ironman.IronManHighlight.outlines(viewer, self)) {
			return;
		}
		cir.setReturnValue(com.projecthero.mod.ironman.IronManHighlight.colour(self));
	}
}
