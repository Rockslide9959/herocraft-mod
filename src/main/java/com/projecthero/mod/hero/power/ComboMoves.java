package com.projecthero.mod.hero.power;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.data.ExperimentalState;
import com.projecthero.mod.hero.visual.MutationVisuals;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.13.22: power combos as real moves. Owning a matching pair of mutations unlocks a combo, fired with
 * <b>Sneak + Utility 2 (N)</b> whichever of the two is selected (the router intercepts it before the selected
 * power's own N ability). Each combo has its own cooldown, stored in the experimental cooldown map under
 * {@code combo/<id>}. When several pairs are owned the first in {@link #COMBOS} order fires.
 */
public final class ComboMoves {
	/** @param cooldownTicks cooldown after firing */
	private record Combo(String id, String a, String b, int cooldownTicks, Fire fire) {
	}

	@FunctionalInterface
	private interface Fire {
		void fire(ServerPlayer player, ServerLevel level);
	}

	private static final List<Combo> COMBOS = List.of(
			new Combo("meteor_slam", PowerCombos.STRENGTH, PowerCombos.FLIGHT, 40 * 20, ComboMoves::meteorSlam),
			new Combo("colossus_stomp", PowerCombos.DURABILITY, PowerCombos.SIZE, 40 * 20, ComboMoves::colossusStomp),
			new Combo("boulder_barrage", PowerCombos.GEO, PowerCombos.STRENGTH, 30 * 20, ComboMoves::boulderBarrage),
			new Combo("thunder_sprint", PowerCombos.SPEED, PowerCombos.ELECTRO, 30 * 20, ComboMoves::thunderSprint),
			new Combo("storm_surge", PowerCombos.WATER, PowerCombos.ELECTRO, 35 * 20, ComboMoves::stormSurge),
			new Combo("glacier_flood", PowerCombos.CRYO, PowerCombos.WATER, 35 * 20, ComboMoves::glacierFlood),
			new Combo("comet_dash", PowerCombos.PYRO, PowerCombos.FLIGHT, 30 * 20, ComboMoves::cometDash),
			new Combo("fire_tornado", "power_24_wind_manipulation", PowerCombos.PYRO, 45 * 20, ComboMoves::fireTornado),
			new Combo("solar_lance", PowerCombos.ENERGY, PowerCombos.LASER, 40 * 20, ComboMoves::solarLance),
			new Combo("umbral_leap", "power_19_shadow_manipulation", "power_11_teleportation", 25 * 20, ComboMoves::umbralLeap));

	/** Players mid-Meteor-Slam -> ticks left before it gives up waiting for a landing. */
	private static final Map<UUID, Integer> METEORS = new HashMap<>();
	/** Live fire tornadoes: owner -> (centre, ticks left). */
	private static final Map<UUID, Object[]> TORNADOES = new HashMap<>();

	private ComboMoves() {
	}

	/** The id of the combo {@code player} would fire now, or null if they own no matching pair. */
	public static String available(ServerPlayer player) {
		for (Combo c : COMBOS) {
			if (PowerCombos.has(player, c.a(), c.b())) {
				return c.id();
			}
		}
		return null;
	}

	/** Sneak+N: fires the first combo the player owns. Returns true if the input was consumed. */
	public static boolean tryFire(ServerPlayer player) {
		ServerLevel level = AbilityHelpers.level(player);
		for (Combo c : COMBOS) {
			if (!PowerCombos.has(player, c.a(), c.b())) {
				continue;
			}
			ExperimentalState s = ExperimentalPowers.state(player);
			long now = level.getGameTime();
			Long ready = s.abilityReadyAt.get("combo/" + c.id());
			if (ready != null && ready > now) {
				player.displayClientMessage(Component.translatable("message.projecthero.ability.on_cooldown",
						Component.translatable("projecthero.combo_move." + c.id()),
						String.format(java.util.Locale.ROOT, "%.1f", (ready - now) / 20.0f)), true);
				return true;
			}
			s.abilityReadyAt.put("combo/" + c.id(), now + c.cooldownTicks());
			player.setAttached(com.projecthero.mod.attachment.ModAttachments.EXPERIMENTAL_STATE, s);
			player.displayClientMessage(Component.translatable("projecthero.combo_move." + c.id())
					.withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.BOLD), true);
			c.fire().fire(player, level);
			return true;
		}
		player.displayClientMessage(Component.translatable("message.projecthero.combo.none"), true);
		return true;
	}

	// ---------------- the moves ----------------

	/** Strength + Flight: rocket up, then come down like a meteor -- the landing is the hit. */
	private static void meteorSlam(ServerPlayer p, ServerLevel level) {
		MutationVisuals.play(p, "leap");
		AbilityHelpers.launchSelf(p, new Vec3(0, 2.2, 0));
		METEORS.put(p.getUUID(), 120);
		AbilityHelpers.sound(p, SoundEvents.WIND_CHARGE_BURST, 1.2f, 0.7f);
	}

	private static void meteorImpact(ServerPlayer p, ServerLevel level) {
		MutationVisuals.play(p, "hero_landing");
		Vec3 c = p.position();
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, c, 6.0)) {
			AbilityHelpers.hurt(p, e, 26.0f);
			AbilityHelpers.knockbackFrom(e, c, 2.2);
		}
		AbilityHelpers.burst(level, c, ParticleTypes.EXPLOSION_EMITTER, 2, 0.5);
		AbilityHelpers.burst(level, c, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()), 120, 3.0);
		AbilityHelpers.sound(p, SoundEvents.GENERIC_EXPLODE, 1.6f, 0.6f);
		p.fallDistance = 0;
	}

	/** Durability + Size: a ground-shaking stomp that scales with your size. */
	private static void colossusStomp(ServerPlayer p, ServerLevel level) {
		MutationVisuals.play(p, "stomp");
		float scale = (float) p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.SCALE);
		double r = 7.0 + 2.0 * Math.max(0, scale - 1);
		Vec3 c = p.position();
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, c, r)) {
			AbilityHelpers.hurt(p, e, 24.0f);
			AbilityHelpers.push(e, e.position().subtract(c).normalize().scale(1.2).add(0, 0.9, 0));
		}
		for (int i = 0; i < 24; i++) {
			double a = i * Math.PI * 2 / 24;
			AbilityHelpers.burst(level, c.add(Math.cos(a) * r * 0.7, 0.1, Math.sin(a) * r * 0.7), ParticleTypes.CLOUD, 4, 0.3);
		}
		AbilityHelpers.sound(p, SoundEvents.GENERIC_EXPLODE, 1.4f, 0.5f);
	}

	/** Geokinesis + Strength: tear up three boulders and hurl them. */
	private static void boulderBarrage(ServerPlayer p, ServerLevel level) {
		MutationVisuals.play(p, "throw_right");
		Vec3 look = AbilityHelpers.lookDir(p);
		for (int i = -1; i <= 1; i++) {
			Vec3 side = new Vec3(-look.z, 0, look.x).normalize().scale(i * 0.35);
			Vec3 at = p.getEyePosition().add(look.scale(1.5)).add(0, 0.5, 0);
			FallingBlockEntity rock = FallingBlockEntity.fall(level, BlockPos.containing(at), Blocks.COBBLESTONE.defaultBlockState());
			rock.setPos(at.x, at.y, at.z);
			rock.dropItem = false;
			rock.setHurtsEntities(3.0f, 22);
			rock.setDeltaMovement(look.add(side).normalize().scale(1.6).add(0, 0.25, 0));
			rock.hurtMarked = true;
		}
		AbilityHelpers.sound(p, SoundEvents.STONE_BREAK, 1.4f, 0.6f);
	}

	/** Speed + Electrokinesis: a lightning-fast dash that shocks everything along the line. */
	private static void thunderSprint(ServerPlayer p, ServerLevel level) {
		MutationVisuals.play(p, "dash_forward");
		Vec3 look = AbilityHelpers.lookDir(p);
		Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
		Vec3 start = p.position();
		Vec3 end = start.add(flat.scale(14));
		java.util.Set<LivingEntity> hit = new java.util.HashSet<>();
		for (int i = 0; i <= 14; i++) {
			Vec3 at = start.add(flat.scale(i));
			hit.addAll(AbilityHelpers.enemiesAround(p, at.add(0, 1, 0), 1.8));
			AbilityHelpers.burst(level, at.add(0, 1, 0), ParticleTypes.ELECTRIC_SPARK, 6, 0.4);
		}
		for (LivingEntity e : hit) {
			AbilityHelpers.hurt(p, e, 18.0f);
			AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 60, 3);
		}
		SafeTeleport.blink(p, flat, 14.0);
		AbilityHelpers.line(level, start.add(0, 1, 0), end.add(0, 1, 0), ParticleTypes.END_ROD, 3);
		AbilityHelpers.sound(p, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.8f, 1.6f);
	}

	/** Water + Electrokinesis: an electrified tidal ring. */
	private static void stormSurge(ServerPlayer p, ServerLevel level) {
		MutationVisuals.play(p, "cast_raise_both");
		Vec3 c = p.position();
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, c, 7.0)) {
			AbilityHelpers.hurt(p, e, 22.0f);
			AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 80, 2);
			AbilityHelpers.knockbackFrom(e, c, 1.2);
		}
		for (int i = 0; i < 32; i++) {
			double a = i * Math.PI * 2 / 32;
			Vec3 at = c.add(Math.cos(a) * 5, 0.4, Math.sin(a) * 5);
			AbilityHelpers.burst(level, at, ParticleTypes.SPLASH, 8, 0.4);
			AbilityHelpers.burst(level, at.add(0, 0.6, 0), ParticleTypes.ELECTRIC_SPARK, 3, 0.3);
		}
		AbilityHelpers.sound(p, SoundEvents.TRIDENT_THUNDER, 1.2f, 1.0f);
	}

	/** Cryokinesis + Water: a flood that freezes solid around everything it touches. */
	private static void glacierFlood(ServerPlayer p, ServerLevel level) {
		MutationVisuals.play(p, "summon_ground");
		Vec3 c = p.position();
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, c, 8.0)) {
			AbilityHelpers.hurt(p, e, AbilityHelpers.freeze(p), 16.0f);
			AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 120, 5);
			e.setTicksFrozen(Math.max(e.getTicksFrozen(), 300));
		}
		for (int i = 0; i < 40; i++) {
			double a = i * Math.PI * 2 / 40;
			AbilityHelpers.burst(level, c.add(Math.cos(a) * 6, 0.3, Math.sin(a) * 6), ParticleTypes.SNOWFLAKE, 6, 0.5);
		}
		AbilityHelpers.sound(p, SoundEvents.GLASS_BREAK, 1.2f, 0.5f);
	}

	/** Pyrokinesis + Flight: burn forward as a comet, igniting everything in the path. */
	private static void cometDash(ServerPlayer p, ServerLevel level) {
		MutationVisuals.play(p, "dash_forward");
		Vec3 look = AbilityHelpers.lookDir(p);
		Vec3 start = p.getEyePosition();
		java.util.Set<LivingEntity> hit = new java.util.HashSet<>();
		for (int i = 0; i <= 16; i++) {
			Vec3 at = start.add(look.scale(i));
			hit.addAll(AbilityHelpers.enemiesAround(p, at, 2.0));
			AbilityHelpers.burst(level, at, ParticleTypes.FLAME, 10, 0.5);
		}
		for (LivingEntity e : hit) {
			AbilityHelpers.hurt(p, e, AbilityHelpers.fire(p), 16.0f);
			e.igniteForSeconds(6);
		}
		AbilityHelpers.launchSelf(p, look.scale(2.6));
		AbilityHelpers.sound(p, SoundEvents.BLAZE_SHOOT, 1.2f, 0.7f);
	}

	/** Wind + Pyrokinesis: a burning vortex where you aim, dragging enemies in for 6 s. */
	private static void fireTornado(ServerPlayer p, ServerLevel level) {
		MutationVisuals.play(p, "cast_two_hand");
		Vec3 at = AbilityHelpers.aimPoint(p, 24);
		TORNADOES.put(p.getUUID(), new Object[] { at, 120 });
		AbilityHelpers.sound(p, SoundEvents.BLAZE_AMBIENT, 1.4f, 0.6f);
	}

	/** Energy Absorption + Laser Vision: one colossal lance along your aim. */
	private static void solarLance(ServerPlayer p, ServerLevel level) {
		MutationVisuals.play(p, "cast_two_hand");
		Vec3 look = AbilityHelpers.lookDir(p);
		Vec3 start = p.getEyePosition();
		java.util.Set<LivingEntity> hit = new java.util.HashSet<>();
		for (int i = 1; i <= 40; i++) {
			Vec3 at = start.add(look.scale(i));
			if (!level.getBlockState(BlockPos.containing(at)).getCollisionShape(level, BlockPos.containing(at)).isEmpty()) {
				break;
			}
			hit.addAll(AbilityHelpers.enemiesAround(p, at, 1.6));
			AbilityHelpers.burst(level, at, ParticleTypes.END_ROD, 3, 0.15);
			AbilityHelpers.burst(level, at, ParticleTypes.FLAME, 2, 0.2);
		}
		for (LivingEntity e : hit) {
			AbilityHelpers.hurt(p, e, 30.0f);
			e.igniteForSeconds(4);
		}
		AbilityHelpers.sound(p, SoundEvents.BEACON_POWER_SELECT, 1.4f, 1.6f);
	}

	/** Shadow + Teleportation: vanish and reappear up to 28 blocks away, blinding everyone near the landing. */
	private static void umbralLeap(ServerPlayer p, ServerLevel level) {
		MutationVisuals.play(p, "cast_right");
		AbilityHelpers.burst(level, p.position().add(0, 1, 0), ParticleTypes.SQUID_INK, 30, 0.6);
		if (SafeTeleport.blink(p, AbilityHelpers.lookDir(p), 28.0)) {
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), 5.0)) {
				AbilityHelpers.applyControl(e, MobEffects.BLINDNESS, 100, 0);
				AbilityHelpers.hurt(p, e, 10.0f);
			}
			p.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.INVISIBILITY, 60, 0, false, false, true));
		}
		AbilityHelpers.burst(level, p.position().add(0, 1, 0), ParticleTypes.SQUID_INK, 30, 0.6);
		AbilityHelpers.sound(p, SoundEvents.ENDERMAN_TELEPORT, 1.0f, 0.5f);
	}

	// ---------------- per-tick ----------------

	/** Called once per server tick. Bounded: iterates only players currently mid-combo. */
	public static void tick(MinecraftServer server) {
		for (Iterator<Map.Entry<UUID, Integer>> it = METEORS.entrySet().iterator(); it.hasNext();) {
			var e = it.next();
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			int left = e.getValue() - 1;
			if (p == null || !p.isAlive() || left <= 0) {
				it.remove();
				continue;
			}
			e.setValue(left);
			if (left < 110 && p.getDeltaMovement().y < 0.1) {
				// peak passed: drive it down hard
				AbilityHelpers.addImpulse(p, new Vec3(0, -0.6, 0));
				AbilityHelpers.burst(AbilityHelpers.level(p), p.position(), ParticleTypes.FLAME, 4, 0.3);
			}
			if (left < 110 && (p.onGround() || p.isInWater())) {
				meteorImpact(p, AbilityHelpers.level(p));
				it.remove();
			}
		}
		for (Iterator<Map.Entry<UUID, Object[]>> it = TORNADOES.entrySet().iterator(); it.hasNext();) {
			var e = it.next();
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			Vec3 c = (Vec3) e.getValue()[0];
			int left = (Integer) e.getValue()[1] - 1;
			if (p == null || left <= 0) {
				it.remove();
				continue;
			}
			e.getValue()[1] = left;
			ServerLevel level = AbilityHelpers.level(p);
			double spin = level.getGameTime() * 0.6;
			for (int h = 0; h < 6; h++) {
				double r = 0.6 + h * 0.45;
				for (int k = 0; k < 3; k++) {
					double a = spin + h * 0.7 + k * Math.PI * 2 / 3;
					level.sendParticles(ParticleTypes.FLAME, c.x + Math.cos(a) * r, c.y + h * 0.9, c.z + Math.sin(a) * r, 1, 0, 0.05, 0, 0.01);
				}
			}
			if (left % 5 == 0) {
				for (LivingEntity t : AbilityHelpers.enemiesAround(p, c, 7.0)) {
					Vec3 pull = c.subtract(t.position()).normalize().scale(0.35).add(0, 0.12, 0);
					AbilityHelpers.push(t, pull);
					if (t.distanceToSqr(c) < 9.0) {
						AbilityHelpers.hurt(p, t, AbilityHelpers.fire(p), 5.0f);
						t.igniteForSeconds(3);
					}
				}
			}
		}
	}

	/** Clears all transient state (server stop). */
	public static void reset() {
		METEORS.clear();
		TORNADOES.clear();
	}
}
