package com.projecthero.mod.miraculous.catnoir;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.MiraculousKits;
import com.projecthero.mod.miraculous.catnoir.entity.CatNoirEntityTypes;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;

/**
 * v0.16.6: Cat Noir's server entry point (called from {@code MiraculousMod.initialize}): the kit, the thrown baton,
 * Cat Reflexes' dodge (a damage veto), CATACLYSM's touch (attack / use a block, attack a creature -- answered on both
 * sides from the synced charge, so the client never starts mining the block) and the crumble clock.
 */
public final class CatNoirMod {
	private CatNoirMod() {
	}

	public static void initialize() {
		MiraculousKits.register(Kwami.PLAGG, new CatNoirKit());
		CatNoirEntityTypes.initialize();

		// Cat Reflexes: the next hits slip past (the sidestep is in tryDodge)
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer p && CatNoir.active(p) && CatNoir.reflexesLeft(p) > 0) {
				return !CatNoirAbilities.tryDodge(p, source);
			}
			return true;
		});

		// CATACLYSM: one touch
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
			if (hand != InteractionHand.MAIN_HAND || !CatNoir.charged(player)) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer sp) {
				CatNoirCataclysm.touchBlock(sp, pos);
			}
			return InteractionResult.SUCCESS;
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !CatNoir.charged(player)) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer sp) {
				CatNoirCataclysm.touchBlock(sp, hit.getBlockPos());
			}
			return InteractionResult.SUCCESS;
		});
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!CatNoir.charged(player)) {
				return InteractionResult.PASS;
			}
			LivingEntity target = AbilityHelpers.asLiving(entity);
			if (target == null) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer sp) {
				return CatNoirCataclysm.touchEntity(sp, target) ? InteractionResult.SUCCESS : InteractionResult.PASS;
			}
			return InteractionResult.PASS; // the client sends the attack as usual; the server answers it
		});

		ServerTickEvents.END_WORLD_TICK.register(CatNoirCataclysm::worldTick);
	}
}
