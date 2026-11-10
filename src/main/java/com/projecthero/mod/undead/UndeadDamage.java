package com.projecthero.mod.undead;

import java.util.UUID;

import com.projecthero.mod.undead.data.UndeadState;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;

/**
 * What hurts Undead (v0.17.3):
 * <ul>
 *   <li>every hit is placed on a part ({@link UndeadParts#assign}) and taken off its HP -- at 0 the part breaks; a hard hit to the
 *       head snaps back as the Repair Headbutt. Vanilla health takes the hit as usual (the parts only decide what breaks);</li>
 *   <li>while the body re-forms after the Undead shatter nothing hurts him;</li>
 *   <li>what would kill him shatters him instead ({@link Undead#shatter}) -- unless it is {@code /kill}, the void or another
 *       {@code BYPASSES_INVULNERABILITY} damage, so nobody is ever stuck.</li>
 * </ul>
 */
public final class UndeadDamage {
	private UndeadDamage() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(UndeadDamage::allowDamage);
		ServerLivingEntityEvents.ALLOW_DEATH.register(UndeadDamage::allowDeath);
	}

	static void clearSessionState() {
	}

	static void forget(UUID id) {
	}

	/** {@code /kill}, the void and the like: these still really kill. */
	public static boolean bypasses(DamageSource source) {
		return source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
	}

	public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer player) || amount <= 0f || !Undead.hasPower(player)) {
			return true;
		}
		if (bypasses(source)) {
			return true;
		}
		if (Undead.reforming(player)) {
			return false;
		}
		if (UndeadBoneSword.blocks(player, source)) {
			return false; // v0.17.3 Bone Sword: the Rib Cage Guard or a parry
		}
		if (source.is(DamageTypeTags.IS_FALL) && UndeadLaunch.fallImmune(player)) {
			return false; // a Leg / Head Launch lands without fall damage
		}
		if (player.invulnerableTime > 10 || player.getAbilities().invulnerable) {
			return true; // vanilla will ignore (most of) this hit: the parts ignore it too
		}
		float[] parts = UndeadParts.assign(player, source, amount, player.getRandom());
		boolean headHit = parts[UndeadState.HEAD] >= UndeadConfig.HEADBUTT_TRIGGER && !Undead.missing(player, UndeadState.HEAD);
		for (int i = 0; i < UndeadState.PARTS; i++) {
			if (parts[i] > 0f) {
				Undead.damagePart(player, i, parts[i]);
			}
		}
		if (headHit && !Undead.missing(player, UndeadState.HEAD)) {
			UndeadAbilities.repairHeadbutt(player, false);
		}
		return true;
	}

	public static boolean allowDeath(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer player) || !Undead.hasPower(player) || bypasses(source)) {
			return true;
		}
		Undead.shatter(player);
		return false;
	}
}
