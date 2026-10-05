package com.projecthero.mod.ironman.ability;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.ironman.IronManAbilityFx;
import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.TonyStark;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.27: the repulsor dash (Shift+R on the Mark 2 / Mark III). The wearer is driven forward along the look --
 * {@link #DASH_DISTANCE} blocks over {@link #DASH_TICKS} ticks -- firing repulsors all round while they go: every
 * living thing within {@link #HIT_RADIUS} blocks of the path is hit <b>once</b> for the dash damage and knocked aside,
 * with a repulsor beam drawn to it. Plays the {@link IronManAbilityFx#DASH} pose.
 *
 * <p>The live dashes are static server state keyed by player UUID, cleared in {@code ServerStateReset}; each one is
 * driven from {@link com.projecthero.mod.ironman.IronManSuitTicker} via {@link #tick}.
 */
public final class IronManDash {
	/** Ability id the cooldown is stored under (per suit). */
	public static final String ABILITY_ID = "repulsor_dash";
	public static final int DASH_TICKS = 10;           // ~0.5 s
	public static final double DASH_DISTANCE = 10.0;   // blocks
	public static final double HIT_RADIUS = 4.0;

	private static final Map<UUID, Dash> ACTIVE = new HashMap<>();

	private static final class Dash {
		final Vec3 dir;
		final long endTick;
		final float damage;
		final Set<Integer> hit = new HashSet<>();

		Dash(Vec3 dir, long endTick, float damage) {
			this.dir = dir;
			this.endTick = endTick;
			this.damage = damage;
		}
	}

	private IronManDash() {
	}

	public static void clearSessionState() {
		ACTIVE.clear();
	}

	/** True while this player is mid-dash. */
	public static boolean dashing(ServerPlayer player) {
		return ACTIVE.containsKey(player.getUUID());
	}

	/**
	 * Start a dash. Checks the worn chestplate, the {@link #ABILITY_ID} cooldown and the energy itself; on success
	 * spends {@code energyCost} (scaled by the suit's cost multiplier), starts {@code cooldownTicks} and returns true.
	 */
	public static boolean start(ServerPlayer player, float damage, float energyCost, int cooldownTicks) {
		String suitId = IronManArmor.wornSuitId(player);
		if (suitId == null || !IronManArmor.canOperate(player)) {
			return false;
		}
		if (!IronManArmor.hasChestplate(player, suitId)) {
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.need_chest"), true);
			return false;
		}
		if (dashing(player) || !IronManAbilities.cooldownReady(player, suitId, ABILITY_ID)) {
			return false;
		}
		float cost = energyCost * costMultiplier(suitId);
		if (!IronManEnergy.spend(player, suitId, cost)) {
			IronManAbilities.noEnergy(player, cost);
			return false;
		}
		Vec3 look = player.getLookAngle();
		// on the ground, keep the dash level-ish so it doesn't plough into the floor
		Vec3 dir = player.onGround() && look.y < 0 ? new Vec3(look.x, 0, look.z) : look;
		if (dir.lengthSqr() < 1.0e-4) {
			dir = Vec3.directionFromRotation(0, player.getYRot());
		}
		dir = dir.normalize();
		long now = player.level().getGameTime();
		ACTIVE.put(player.getUUID(), new Dash(dir, now + DASH_TICKS, damage));
		TonyStark.triggerCooldown(player, suitId, ABILITY_ID, cooldownTicks);
		IronManAbilityFx.play(player, IronManAbilityFx.DASH, DASH_TICKS + 4);
		AbilityHelpers.sound(player, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 1.2f, 0.8f);
		AbilityHelpers.sound(player, SoundEvents.BEACON_POWER_SELECT, 1.0f, 1.6f);
		push(player, dir);
		return true;
	}

	/** Per tick for every Iron Man wearer -- drives an active dash and lands its hits. */
	public static void tick(ServerPlayer player) {
		Dash d = ACTIVE.get(player.getUUID());
		if (d == null) {
			return;
		}
		long now = player.level().getGameTime();
		if (now >= d.endTick || !player.isAlive()) {
			ACTIVE.remove(player.getUUID());
			// bleed most of the speed off so the dash ends crisply instead of sliding on
			player.setDeltaMovement(player.getDeltaMovement().scale(0.25));
			player.hurtMarked = true;
			player.connection.send(new ClientboundSetEntityMotionPacket(player));
			return;
		}
		push(player, d.dir);
		player.resetFallDistance();
		ServerLevel level = (ServerLevel) player.level();
		Vec3 centre = player.position().add(0, player.getBbHeight() * 0.5, 0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, centre.x, centre.y, centre.z, 6, 0.4, 0.4, 0.4, 0.05);
		level.sendParticles(ParticleTypes.CLOUD, centre.x - d.dir.x, centre.y - d.dir.y, centre.z - d.dir.z, 2, 0.1, 0.1, 0.1, 0.01);
		hitAround(player, d, centre);
	}

	private static void hitAround(ServerPlayer player, Dash d, Vec3 centre) {
		ServerLevel level = (ServerLevel) player.level();
		for (LivingEntity e : AbilityHelpers.enemiesAround(player, centre, HIT_RADIUS)) {
			if (!d.hit.add(e.getId())) {
				continue;
			}
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0);
			IronManAbilities.broadcastBeam(player, centre, to, 0);
			if (AbilityHelpers.hurtBurst(player, e, d.damage)) com.projecthero.mod.ironman.IronManCombo.onRepulsorHit(player, e); // v0.14.29 agent F
			AbilityHelpers.knockbackFrom(e, player.position(), 1.2);
			AbilityHelpers.burst(level, to, ParticleTypes.ELECTRIC_SPARK, 10, 0.3);
			AbilityHelpers.sound(player, SoundEvents.GENERIC_EXPLODE, 0.3f, 1.7f);
		}
	}

	private static void push(ServerPlayer player, Vec3 dir) {
		double perTick = DASH_DISTANCE / DASH_TICKS;
		player.setDeltaMovement(dir.scale(perTick));
		player.hurtMarked = true;
		player.connection.send(new ClientboundSetEntityMotionPacket(player));
	}

	private static float costMultiplier(String suitId) {
		var suit = com.projecthero.mod.ironman.suit.IronManSuits.byId(suitId);
		return suit == null ? 1f : suit.energyCostMultiplier();
	}
}
