package com.projecthero.mod.worthy;

import com.projecthero.mod.hammer.ThorWeapon;
import com.projecthero.mod.supersoldier.entity.SoldierShieldEntity;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;

/**
 * v0.17.7 Thor x Super Soldier ("worthy by grant"): one init call from {@code ProjectHeroMod}. Package map:
 * <ul>
 *   <li>{@link WorthyLending} -- lending (sneak + right-click), the Call back, every way a loan ends, the upkeep;</li>
 *   <li>{@link WorthyLoans} -- the per-world record of loans (SavedData: survives relogs and restarts);</li>
 *   <li>{@link WorthyKit} -- which kit his hands give him, the key tables, the routing; {@link WorthyMoves} -- the moves;</li>
 *   <li>{@link WorthyFlight} -- flight with a lent weapon; {@link WorthyFx} -- the look and sound; {@link WorthyConfig} -- numbers;</li>
 *   <li>{@link WorthyState} / {@link WorthyAttachments} -- the synced per-player state; {@link WorthyActionPayload} -- C2S.</li>
 * </ul>
 * Hooks elsewhere (kept to single lines): {@code SuperSoldierAbilityManager.handle} (the kit takes the keys), {@code SuperSoldierHud}
 * (the kit's HUD), {@code MjolnirItem.use} / {@code StormbreakerItem.use} (the right-click throw), {@code MjolnirEntity.interact} /
 * {@code StormbreakerEntity.interact} (he may pick a lent weapon back up), {@code WorthinessEnforcer} (a lent Mjolnir is not
 * ejected from its Super Soldier), {@code ThorPowers.wieldsThorWeapon} and {@code WeaponCombo.beforeAttack} (Thor's key routing,
 * HUD and melee combo ignore a borrower), {@code MjolnirRecall} (R leaves a lent weapon alone; the Call back), the Thor weapon
 * screen, client flight ({@code DirectionalFlight}, {@code FlightPoseHelper}, the double-tap in {@code ProjectHeroModClient}).
 * No static collections anywhere in the package.
 */
public final class WorthyMod {
	private WorthyMod() {
	}

	public static void initialize() {
		WorthyAttachments.initialize();
		PayloadTypeRegistry.playC2S().register(WorthyActionPayload.TYPE, WorthyActionPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(WorthyActionPayload.TYPE, (payload, context) -> {
			ServerPlayer p = context.player();
			if (payload.action() == WorthyActionPayload.CALL_BACK) {
				if (payload.arg() >= 0 && payload.arg() < ThorWeapon.values().length) {
					WorthyLending.callBack(p, ThorWeapon.values()[payload.arg()]);
				}
			} else if (payload.action() == WorthyActionPayload.TOGGLE_FLIGHT) {
				if (com.projecthero.mod.hero.PowerSuppression.refuse(p)) {
					return;
				}
				WorthyFlight.toggle(p);
			}
		});

		UseEntityCallback.EVENT.register(WorthyLending::onUseEntity);

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				WorthyLending.tick(p);
				WorthyMoves.tick(p);
				WorthyFlight.tick(p);
			}
		});

		ServerPlayerEvents.JOIN.register(WorthyLending::onJoin);

		// a dying Super Soldier gives everything lent to him back before anything drops (keepInventory or not)
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer p) {
				WorthyLending.onDeath(p);
			}
			return true;
		});

		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer p)) {
				return true;
			}
			WorthyState s = WorthyState.of(p);
			if (s == WorthyState.EMPTY) {
				return true;
			}
			long now = p.level().getGameTime();
			if (source.is(DamageTypeTags.IS_FALL) && (s.flying || s.noFallUntil > now)) {
				return false;
			}
			return !WorthyMoves.guardBlocks(p, source);
		});

		// the Charged Shield Throw: every enemy his charged shield hits sends lightning on to two more (the shield's hits are the Super
		// Soldier's own ability hits -- SuperSoldierAbilities.strike -- so: his hit, an ability hit, his thrown shield right there)
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (blocked || !(source.getEntity() instanceof ServerPlayer owner) || owner == entity
					|| !com.projecthero.mod.supersoldier.SuperSoldierAbilities.isAbilityHit()
					|| WorthyState.of(owner).chargedUntil <= owner.level().getGameTime()) {
				return;
			}
			if (!entity.level().getEntitiesOfClass(SoldierShieldEntity.class, entity.getBoundingBox().inflate(3.0),
					sh -> owner.getUUID().equals(sh.ownerId())).isEmpty()) {
				WorthyMoves.chain(owner, entity);
			}
		});
	}
}
