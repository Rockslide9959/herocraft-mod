package com.projecthero.mod.symbiote;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.power.AbilityHelpers;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 weapons: the Z move of the Axe, the Mace and the Whip (the Blade keeps Blade Slash in {@link SymbioteAbilityManager}). Each
 * plays its pose at once and lands a few ticks later, when the swing comes down ({@link #tick}); all three share Z's tap cooldown entry
 * with Barrage / Blade Slash, each with its own length ({@link SymbioteWeapon#zCooldown}).
 *
 * <ul>
 *   <li><b>Cleave</b> (Axe): an overhead chop that lands 4 ticks in on a 4.5-block line ahead (1.3 blocks either side): 16 damage + 4
 *       that ignores armour, a raised player shield is knocked away first (like a vanilla axe), and the ground splits where it lands.</li>
 *   <li><b>Ground Smash</b> (Mace): the spiked ball slams down 6 ticks in, 2.2 blocks ahead: 14 damage to everything within 4 blocks,
 *       thrown out (1.2) and up (0.5; not bosses), Slowness II 2 s.</li>
 *   <li><b>Lash</b> (Whip): cracks 3 ticks in at the first living thing within 7 blocks of the aim (blocks stop it): 12 damage, and
 *       it is yanked to 2 blocks in front of the host (not bosses / dummies / anything the grab rules refuse), Slowness I 1 s.</li>
 * </ul>
 */
public final class SymbioteWeaponMoves {
	public static final int CLEAVE_DELAY = 4;
	public static final double CLEAVE_LENGTH = 4.5;
	public static final double CLEAVE_HALF_WIDTH = 1.3;
	public static final float CLEAVE_DAMAGE = 16.0f;
	public static final float CLEAVE_ARMOUR_PIERCE = 4.0f;

	public static final int SMASH_DELAY = 6;
	public static final double SMASH_AHEAD = 2.2;
	public static final double SMASH_RADIUS = 4.0;
	public static final float SMASH_DAMAGE = 14.0f;

	public static final int LASH_DELAY = 3;
	public static final double LASH_RANGE = 7.0;
	public static final float LASH_DAMAGE = 12.0f;
	public static final double LASH_PULL_TO = 2.0;

	/** playerId -> {weapon ordinal, game time it lands}. Entity ids and numbers only; cleared with the rest of the kit. */
	private static final Map<Integer, long[]> PENDING = new ConcurrentHashMap<>();

	private SymbioteWeaponMoves() {
	}

	/** Z with a weapon out (not the Blade): starts the swing; it lands from {@link #tick}. */
	static boolean start(ServerPlayer player, SymbioteWeapon w, long now) {
		int anim;
		int delay;
		switch (w) {
			case AXE -> {
				anim = SymbioteAnim.AXE_CLEAVE;
				delay = CLEAVE_DELAY;
				AbilityHelpers.sound(player, SoundEvents.PLAYER_ATTACK_STRONG, 0.9f, 0.6f);
			}
			case MACE -> {
				anim = SymbioteAnim.MACE_SMASH;
				delay = SMASH_DELAY;
				SymbioteSounds.organic(player, 0.9f, 0.35f);
			}
			case WHIP -> {
				anim = SymbioteAnim.WHIP_LASH;
				delay = LASH_DELAY;
				SymbioteSounds.lash(player, 0.9f, 1.6f);
			}
			default -> {
				return false;
			}
		}
		SymbioteAnim.play(player, anim);
		PENDING.put(player.getId(), new long[]{w.ordinal(), now + delay});
		return true;
	}

	/** Is a weapon swing on its way down? (tests) */
	public static boolean pending(ServerPlayer player) {
		return PENDING.containsKey(player.getId());
	}

	static void tick(ServerPlayer player, long now) {
		long[] p = PENDING.get(player.getId());
		if (p == null || now < p[1]) {
			return;
		}
		PENDING.remove(player.getId());
		if (!SymbioteVitalsManager.bladeActive(player)) {
			return; // the weapon melted back mid-swing
		}
		switch (SymbioteWeapon.byOrdinal((int) p[0])) {
			case AXE -> cleave(player);
			case MACE -> groundSmash(player);
			case WHIP -> lash(player);
			default -> {
			}
		}
	}

	static void clear(ServerPlayer player) {
		PENDING.remove(player.getId());
	}

	static void clearAll() {
		PENDING.clear();
	}

	private static Vec3 flatLook(Player player) {
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		return flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
	}

	// ---------------- Cleave (Axe) ----------------

	private static void cleave(ServerPlayer player) {
		ServerLevel level = AbilityHelpers.level(player);
		double scale = Math.max(1.0, player.getScale());
		Vec3 dir = flatLook(player);
		Vec3 a = player.position();
		double len = CLEAVE_LENGTH * scale;
		for (LivingEntity e : SymbioteAbilityManager.enemiesAround(player, a.add(dir.scale(len * 0.5)), len)) {
			Vec3 rel = e.position().subtract(a);
			double along = rel.dot(dir);
			if (along < -0.3 || along > len) {
				continue;
			}
			double side = rel.subtract(dir.scale(along)).multiply(1, 0, 1).length();
			if (side > CLEAVE_HALF_WIDTH * scale + e.getBbWidth() * 0.5 || rel.y < -1.5 || rel.y > 3.0 * scale) {
				continue;
			}
			if (e instanceof Player victim && victim.isBlocking()) {
				victim.disableShield(); // like a vanilla axe: the shield is knocked away before the blow
			}
			if (AbilityHelpers.hurtBurst(player, e, CLEAVE_DAMAGE)) {
				e.invulnerableTime = 0;
				e.hurt(player.damageSources().magic(), CLEAVE_ARMOUR_PIERCE);
				if (!SymbioteAbilityManager.isBoss(e)) {
					AbilityHelpers.push(e, new Vec3(dir.x * 0.3, -0.4, dir.z * 0.3));
				}
				SymbioteVfx.impact(player, e, 1.0f);
			}
		}
		Vec3 ground = SymbioteAbilityManager.groundPoint(level, a.add(dir.scale(2.4 * scale)));
		SymbioteVfx.groundBurst(player, ground, 1.8f);
		AbilityHelpers.sound(player, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, 0.7f, 0.7f);
		AbilityHelpers.sound(player, SoundEvents.PLAYER_ATTACK_CRIT, 1.0f, 0.6f);
		SymbioteSounds.organic(player, 0.8f, 0.4f);
	}

	// ---------------- Ground Smash (Mace) ----------------

	private static void groundSmash(ServerPlayer player) {
		ServerLevel level = AbilityHelpers.level(player);
		double scale = Math.max(1.0, player.getScale());
		Vec3 point = SymbioteAbilityManager.groundPoint(level, player.position().add(flatLook(player).scale(SMASH_AHEAD * scale)));
		double r = SMASH_RADIUS * scale;
		for (LivingEntity e : SymbioteAbilityManager.enemiesAround(player, point, r)) {
			if (e.position().distanceTo(point) > r + e.getBbWidth() * 0.5) {
				continue;
			}
			if (AbilityHelpers.hurtBurst(player, e, SMASH_DAMAGE)) {
				AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 40, 1);
				if (!SymbioteAbilityManager.isBoss(e)) {
					AbilityHelpers.knockbackFrom(e, point, 1.2);
					AbilityHelpers.push(e, new Vec3(0, 0.5, 0));
				}
				SymbioteVfx.impact(player, e, 0.9f);
			}
		}
		SymbioteVfx.groundBurst(player, point, (float) r);
		AbilityHelpers.sound(player, SoundEvents.GENERIC_EXPLODE.value(), 0.7f, 0.8f);
		AbilityHelpers.sound(player, SoundEvents.ANVIL_LAND, 0.6f, 0.5f);
		SymbioteSounds.organic(player, 1.0f, 0.3f);
	}

	// ---------------- Lash (Whip) ----------------

	private static void lash(ServerPlayer player) {
		ServerLevel level = AbilityHelpers.level(player);
		LivingEntity[] hit = new LivingEntity[1];
		Vec3 end = SymbioteAbilityManager.traceTendril(player, player.getLookAngle(), LASH_RANGE * Math.max(1.0, player.getScale()), hit);
		SymbioteSounds.lash(player, 1.1f, 1.8f);
		if (hit[0] == null) {
			SymbioteVfx.impact(player, end, player.getLookAngle(), 0.5f);
			return;
		}
		LivingEntity target = hit[0];
		if (AbilityHelpers.hurtBurst(player, target, LASH_DAMAGE)) {
			AbilityHelpers.applyControl(target, MobEffects.MOVEMENT_SLOWDOWN, 20, 0);
			SymbioteVfx.impact(player, target, 0.9f);
		}
		if (target.isAlive() && !SymbioteAbilityManager.isBoss(target) && AbilityHelpers.isValidGrabTarget(target, player)) {
			Vec3 dest = player.position().add(flatLook(player).scale(LASH_PULL_TO * Math.max(1.0, player.getScale())));
			Vec3 to = dest.subtract(target.position());
			if (to.length() > 0.5) {
				// a hard yank toward the host that falls short of the host itself (~8 airborne ticks of 0.91 drag carry it ~6x the
				// starting speed)
				target.setDeltaMovement(to.x * 0.19, 0.22, to.z * 0.19); // filming pass: 0.32 lift threw a golem 3 blocks up
				target.hurtMarked = true;
			}
			SymbioteVfx.wrap(player, target, 8, 0.7f);
		}
		AbilityHelpers.sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7f, 1.6f);
		level.playSound(null, end.x, end.y, end.z, SoundEvents.FISHING_BOBBER_RETRIEVE, net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 0.6f);
	}
}
