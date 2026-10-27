package com.projecthero.mod.quirk.explosion;

import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.QuirkKits;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

/**
 * v0.16.7: the Explosion Quirk's server init (called from {@code QuirkMod.initialize}): the kit, and its hooks --
 * {@code ALLOW_DAMAGE} cancels the fall / wall damage of his own launches, the Spin Dodge's moment and a long fall broken
 * by the landing blast (the same one-shot check as {@link ExplosionKit#modifyIncomingDamage}), {@code AFTER_DAMAGE} makes
 * him sweat in a fight. v0.17.1: his own sounds, the WASD hint payload (the Dash / Spin Dodge direction) and blast mining
 * (Sneak + punch with an empty hand).
 */
public final class ExplosionMod {
	private ExplosionMod() {
	}

	public static void initialize() {
		ExplosionSounds.initialize();
		ExplosionTerrain.load(); // v0.17.1: config/projecthero_quirk_explosion.json (terrain.blockDamage)
		QuirkKits.register(Quirk.EXPLOSION, new ExplosionKit());
		ExplosionMeter.register(); // v0.17.1 framework: Sweat bands, Arm Strain pacing, the combo routes
		PayloadTypeRegistry.playC2S().register(ExplosionInputPayload.TYPE, ExplosionInputPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ExplosionInputPayload.TYPE, (payload, ctx) -> {
			ServerPlayer p = ctx.player();
			ctx.server().execute(() -> {
				if (Explosion.is(p)) {
					ExplosionMoves.input(p, payload.forward(), payload.strafe());
				}
			});
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer p && Explosion.is(p) && ExplosionMoves.cancelsDamage(p, source)) {
				return false;
			}
			return true;
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseAmount, dealtAmount, blocked) -> {
			if (dealtAmount <= 0f) {
				return;
			}
			if (entity instanceof ServerPlayer victim && Explosion.is(victim)) {
				ExplosionMoves.markCombat(victim, ExplosionConfig.SWEAT_HIT_TAKEN, false);
			}
			if (source.getEntity() instanceof ServerPlayer attacker && attacker != entity && Explosion.is(attacker)) {
				ExplosionMoves.markCombat(attacker, ExplosionConfig.SWEAT_HIT_DEALT, true);
			}
		});
		// v0.17.1 survival: blast mining -- Sneak + punch a block with an empty hand
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
			if (level.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp) || !sp.isShiftKeyDown()
					|| !sp.getMainHandItem().isEmpty() || !Explosion.is(sp) || com.projecthero.mod.quirk.Quirks.overloaded(sp)) {
				return InteractionResult.PASS;
			}
			return ExplosionMoves.blastMine(sp, pos, direction) > 0 ? InteractionResult.SUCCESS : InteractionResult.PASS;
		});
	}
}
