package com.projecthero.mod.quirk.overhaul;

import com.projecthero.mod.hero.power.p05.GeoTerrain;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.QuirkKits;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/**
 * v0.16.7: the Overhaul Quirk's server init -- the kit, and one safety hook: breaking the base of one of his spikes
 * takes its dripstone tip back first (so the tip never pops off as an item). The temporary blocks themselves are the
 * Geokinesis ledger's ({@link GeoTerrain}: ticked, restored on server stop, never drop when broken).
 */
public final class OverhaulMod {
	private static final ResourceLocation EARLY = com.projecthero.mod.ProjectHeroMod.id("overhaul_spike_tip");

	private OverhaulMod() {
	}

	public static void initialize() {
		QuirkKits.register(Quirk.OVERHAUL, new OverhaulKit());
		OverhaulMeter.register(); // v0.17.1 framework: Focus bands + pacing
		// v0.16.7 revamp: gloves off, a bare-handed punch disassembles a little too
		net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!level.isClientSide() && player instanceof net.minecraft.server.level.ServerPlayer sp
					&& entity instanceof net.minecraft.world.entity.LivingEntity le && com.projecthero.mod.quirk.Quirks.activeAs(sp, Quirk.OVERHAUL)) {
				OverhaulMoves.bareTouch(sp, le);
			}
			return net.minecraft.world.InteractionResult.PASS;
		});
		PlayerBlockBreakEvents.BEFORE.addPhaseOrdering(EARLY, Event.DEFAULT_PHASE);
		PlayerBlockBreakEvents.BEFORE.register(EARLY, (level, player, pos, state, be) -> {
			if (level instanceof ServerLevel sl && GeoTerrain.isTemp(sl, pos)) {
				var up = pos.above();
				if (sl.getBlockState(up).is(Blocks.POINTED_DRIPSTONE) && GeoTerrain.isTemp(sl, up)) {
					GeoTerrain.restoreNow(sl, up);
				}
			}
			return true;
		});
	}
}
