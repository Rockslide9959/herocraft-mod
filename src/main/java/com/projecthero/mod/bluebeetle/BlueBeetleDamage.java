package com.projecthero.mod.bluebeetle;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.bluebeetle.data.BlueBeetleState;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * What hurts Blue Beetle, and the Bubble Prison (v0.16.5).
 * <ul>
 *   <li>A creature in a Bubble Prison can't be hurt by anything (the bubble cuts both ways).</li>
 *   <li>Suited: no fall damage; the held Energy Shield soaks hits and projectiles from the front out of a 100-point pool
 *       (it breaks when the pool runs dry).</li>
 *   <li><b>Adaptation:</b> the same kind of damage 3 times within 10 s and the armour adapts -- that kind does 40% less for
 *       60 s (one kind at a time).</li>
 *   <li>Camouflage ends the moment he hurts something.</li>
 * </ul>
 * A change cancels the hit and re-issues the new amount behind a re-entrancy guard (vanilla's difficulty scaling undone
 * first, so it applies once).
 */
public final class BlueBeetleDamage {
	private static final ThreadLocal<Boolean> REENTRANT = ThreadLocal.withInitial(() -> false);

	/** Entity id -> game time its bubble pops. */
	private static final Map<Integer, Long> BUBBLES = new ConcurrentHashMap<>();
	/** Per player: the recent hits as (damage kind, game time). */
	private static final Map<UUID, Deque<Object[]>> RECENT = new ConcurrentHashMap<>();

	private BlueBeetleDamage() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(BlueBeetleDamage::allowDamage);
	}

	static void clearSessionState() {
		BUBBLES.clear();
		RECENT.clear();
	}

	static void bubble(LivingEntity e, int ticks) {
		BUBBLES.put(e.getId(), e.level().getGameTime() + ticks);
	}

	static void pop(LivingEntity e) {
		BUBBLES.remove(e.getId());
	}

	public static boolean inBubble(Entity e) {
		Long until = BUBBLES.get(e.getId());
		if (until == null) {
			return false;
		}
		if (until < e.level().getGameTime() || !e.isAlive()) {
			BUBBLES.remove(e.getId());
			return false;
		}
		return true;
	}

	/** The kind of damage, for Adaptation: its damage-type id. */
	public static String kind(DamageSource source) {
		return source.typeHolder().unwrapKey().map(k -> k.location().getPath()).orElse("unknown");
	}

	public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (REENTRANT.get() || amount <= 0.0f) {
			return true;
		}
		boolean bypass = source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
		if (!bypass && inBubble(entity)) {
			return false;
		}
		// a camouflaged Blue Beetle who hurts anything shows himself
		if (source.getEntity() instanceof ServerPlayer attacker && BlueBeetle.hasPower(attacker)) {
			BlueBeetleAbilities.breakCamo(attacker);
		}
		if (!(entity instanceof ServerPlayer player) || !BlueBeetle.suited(player) || bypass) {
			return true;
		}
		if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.FLY_INTO_WALL)) {
			player.resetFallDistance();
			return false;
		}
		if (BlueBeetle.shieldUp(player) && fromFront(player, source)) {
			// v0.16.5 (user): like Cyborg's Defense Matrix -- the hit comes out of the shield's 100-point pool
			BlueBeetleState sh = BlueBeetle.state(player);
			float soak = Math.min(sh.fx.shieldHp, amount);
			boolean broke = sh.fx.shieldHp - soak <= 0.01f;
			if (broke) {
				BlueBeetleAbilities.breakShield(player);
			} else {
				BlueBeetleState n = sh.copy();
				n.fx.shieldHp = sh.fx.shieldHp - soak;
				BlueBeetle.save(player, n);
			}
			ServerLevel level = (ServerLevel) player.level();
			Vec3 at = player.getEyePosition().add(player.getLookAngle().scale(0.8));
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y - 0.4, at.z, broke ? 20 : 8, 0.3, 0.3, 0.3, 0.1);
			level.playSound(null, at.x, at.y, at.z, broke ? SoundEvents.SHIELD_BREAK : SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0f, 1.4f);
			float rest = amount - soak;
			if (rest > 0.05f) {
				reissue(player, source, rest);
			}
			return false;
		}
		String kind = kind(source);
		float factor = 1f;
		BlueBeetleState s = BlueBeetle.state(player);
		long now = player.level().getGameTime();
		if (s.adaptUntil > now && kind.equals(s.adaptType)) {
			factor = BlueBeetleConfig.ADAPT_FACTOR;
		}
		countForAdaptation(player, s, kind, now);
		if (factor < 1f) {
			reissue(player, source, amount * factor);
			return false;
		}
		return true;
	}

	private static void countForAdaptation(ServerPlayer player, BlueBeetleState s, String kind, long now) {
		Deque<Object[]> recent = RECENT.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>());
		recent.addLast(new Object[] { kind, now });
		while (!recent.isEmpty() && now - (long) recent.peekFirst()[1] > BlueBeetleConfig.ADAPT_WINDOW) {
			recent.pollFirst();
		}
		if (s.adaptUntil > now && kind.equals(s.adaptType)) {
			return; // already adapted to it
		}
		int n = 0;
		for (Object[] r : recent) {
			if (kind.equals(r[0])) {
				n++;
			}
		}
		if (n >= BlueBeetleConfig.ADAPT_HITS) {
			BlueBeetleState c = player.getAttachedOrCreate(ModAttachments.BLUE_BEETLE_STATE).copy();
			c.adaptType = kind;
			c.adaptUntil = now + BlueBeetleConfig.ADAPT_TICKS;
			BlueBeetle.save(player, c);
			recent.removeIf(r -> kind.equals(r[0]));
			ServerLevel level = (ServerLevel) player.level();
			level.sendParticles(BlueBeetle.BLUE_BIG, player.getX(), player.getY() + 1.0, player.getZ(), 25, 0.4, 0.7, 0.4, 0.0);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 1.0f, 1.6f);
			KhajiDa.speak(player, KhajiDa.Topic.ADAPTED, net.minecraft.network.chat.Component.translatable(
					"projecthero.blue_beetle.damage_kind", kind.replace('_', ' ')));
		}
	}

	private static boolean fromFront(ServerPlayer player, DamageSource source) {
		Entity from = source.getDirectEntity() != null ? source.getDirectEntity() : source.getEntity();
		Vec3 pos = from != null ? from.position() : source.getSourcePosition();
		if (pos == null) {
			return false;
		}
		Vec3 to = pos.subtract(player.position());
		Vec3 flat = new Vec3(to.x, 0, to.z);
		if (flat.lengthSqr() < 1.0e-4) {
			return true;
		}
		Vec3 look = player.getLookAngle();
		Vec3 lookFlat = new Vec3(look.x, 0, look.z);
		if (lookFlat.lengthSqr() < 1.0e-4) {
			return true;
		}
		return flat.normalize().dot(lookFlat.normalize()) >= BlueBeetleConfig.SHIELD_ARC;
	}

	private static void reissue(ServerPlayer player, DamageSource source, float amount) {
		if (source.scalesWithDifficulty()) {
			amount = switch (player.level().getDifficulty()) {
				case HARD -> amount / 1.5f;
				case EASY -> amount >= 2f ? 2f * (amount - 1f) : amount;
				default -> amount;
			};
		}
		REENTRANT.set(true);
		try {
			player.hurt(source, amount);
		} finally {
			REENTRANT.set(false);
		}
	}
}
