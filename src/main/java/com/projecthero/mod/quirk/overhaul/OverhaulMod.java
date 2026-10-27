package com.projecthero.mod.quirk.overhaul;

import com.projecthero.mod.hero.power.p05.GeoTerrain;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.QuirkKits;
import com.projecthero.mod.quirk.Quirks;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Blocks;

/**
 * v0.16.7: the Overhaul Quirk's server init -- the kit, its sounds and meter, the bare-hand touch on a punch, v0.17.1
 * Disassembly mining (Sneak + a bare-hand punch on stone / earth), and one safety hook: breaking the base of a temporary
 * column under a pointed-dripstone tip takes the tip back first (so it never pops off as an item). The temporary blocks
 * themselves are the Geokinesis ledger's ({@link GeoTerrain}: ticked, restored on server stop, never drop when broken).
 */
public final class OverhaulMod {
	private static final ResourceLocation EARLY = com.projecthero.mod.ProjectHeroMod.id("overhaul_spike_tip");

	private OverhaulMod() {
	}

	public static void initialize() {
		OverhaulSounds.initialize();
		QuirkKits.register(Quirk.OVERHAUL, new OverhaulKit());
		OverhaulMeter.register(); // v0.17.1: Matter bands + pacing
		// gloves off (a move tugged them off), a bare-handed punch disassembles a little too
		net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!level.isClientSide() && player instanceof ServerPlayer sp
					&& entity instanceof net.minecraft.world.entity.LivingEntity le && Quirks.activeAs(sp, Quirk.OVERHAUL)) {
				OverhaulMoves.bareTouch(sp, le);
			}
			return InteractionResult.PASS;
		});
		// v0.17.1 survival: Sneak + a bare-hand punch on stone or earth takes a 3x3 of it apart into its drops
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
			if (level.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp) || !sp.isShiftKeyDown()
					|| !sp.getMainHandItem().isEmpty() || sp.isSpectator() || !Quirks.activeAs(sp, Quirk.OVERHAUL) || Quirks.overloaded(sp)) {
				return InteractionResult.PASS;
			}
			return OverhaulMoves.mine(sp, pos, direction) > 0 ? InteractionResult.SUCCESS : InteractionResult.PASS;
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
