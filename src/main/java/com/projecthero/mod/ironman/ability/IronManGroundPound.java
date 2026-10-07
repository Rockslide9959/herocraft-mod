package com.projecthero.mod.ironman.ability;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.ironman.IronManAbilityFx;
import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.IronManSounds;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.squad.Squads;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.11, explicit user request: the Mark 1's <b>Shift + R</b> ground pound. Both fists come down on the ground
 * ({@link IronManAbilityFx#GROUND_POUND} pose) and a shockwave rolls out {@value #RADIUS} blocks: everything harmable in
 * range takes {@value #DAMAGE} damage (less towards the edge), is thrown outward and popped up into the air, and a ring
 * of the ground's own block chunks bursts outward. Never the wearer, never a squadmate, and the usual
 * {@link com.projecthero.mod.combat.HeroTargets#canHarm} rules (pets, armour stands, PvP off...).
 *
 * <p>Pounding in the air (the Mark 1 flight burst hovers half a block up) still works: the shockwave starts on the ground
 * up to {@value #GROUND_REACH} blocks below; higher than that the suit refuses (nothing to hit).
 */
public final class IronManGroundPound {
	/** Ability id the cooldown is stored under (per suit). */
	public static final String ABILITY_ID = "ground_pound";
	public static final double RADIUS = 5.0;
	/** Full damage at the centre; it falls off linearly to {@link #EDGE_FRACTION} of this at the edge. */
	public static final float DAMAGE = 12.0f;
	public static final float EDGE_FRACTION = 0.5f;
	public static final float ENERGY_COST = 40f;
	public static final int COOLDOWN_TICKS = 8 * 20;
	/** Outward knockback (blocks/tick) at the centre, and the upward pop every target gets. */
	public static final double KNOCKBACK = 1.3;
	public static final double POP = 0.55;
	/** How far below the wearer's feet the ground may be. */
	public static final double GROUND_REACH = 3.0;
	public static final int POSE_TICKS = 12;

	private IronManGroundPound() {
	}

	/** Shift + R on the Mark 1. Returns true if it went off. */
	public static boolean fire(ServerPlayer player, IronManSuit suit) {
		String suitId = suit.id();
		if (!IronManArmor.hasChestplate(player, suitId)) {
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.need_chest"), true);
			return false;
		}
		Vec3 centre = groundBelow(player);
		if (centre == null) {
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.ground_pound_no_ground"), true);
			return false;
		}
		if (!IronManAbilities.cooldownReady(player, suitId, ABILITY_ID)) {
			return false;
		}
		float cost = ENERGY_COST * suit.energyCostMultiplier();
		if (!IronManEnergy.spend(player, suitId, cost)) {
			IronManAbilities.noEnergy(player, cost);
			return false;
		}
		TonyStark.triggerCooldown(player, suitId, ABILITY_ID, COOLDOWN_TICKS);
		IronManAbilityFx.play(player, IronManAbilityFx.GROUND_POUND, POSE_TICKS);
		pound(player, centre);
		return true;
	}

	/** The ground point under the wearer the shockwave starts from, or null with no ground within reach. */
	public static Vec3 groundBelow(ServerPlayer player) {
		if (player.onGround()) {
			return player.position();
		}
		Vec3 from = player.position().add(0, 0.05, 0);
		BlockHitResult hit = player.level().clip(new ClipContext(from, from.subtract(0, GROUND_REACH, 0),
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.MISS ? null : hit.getLocation();
	}

	/** Damage, knock back and pop everything harmable around {@code centre}; returns how many were hit. Public for tests. */
	public static int pound(ServerPlayer player, Vec3 centre) {
		ServerLevel level = (ServerLevel) player.level();
		int n = 0;
		// a sphere around the impact (a target standing on a ledge or a step still gets hit), squadmates never
		for (LivingEntity e : AbilityHelpers.enemiesAround(player, centre.add(0, 0.5, 0), RADIUS)) {
			if (e == player || e instanceof Player other && Squads.areAllies(player, other)) {
				continue;
			}
			double dist = Math.sqrt(AbilityHelpers.distanceSqToBox(e, centre.add(0, 0.5, 0)));
			float falloff = (float) Math.max(0.0, Math.min(1.0, dist / RADIUS));
			float dmg = DAMAGE * (1f - (1f - EDGE_FRACTION) * falloff);
			AbilityHelpers.hurtBurst(player, e, dmg);
			Vec3 away = e.position().subtract(centre);
			Vec3 flat = new Vec3(away.x, 0.0, away.z);
			flat = flat.lengthSqr() < 1.0e-4 ? player.getLookAngle().multiply(1, 0, 1).normalize() : flat.normalize();
			double kb = KNOCKBACK * (1.0 - 0.4 * falloff) * (1.0 - e.getAttributeValue(
					net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE) * 0.6);
			e.setDeltaMovement(e.getDeltaMovement().multiply(0.3, 0.0, 0.3).add(flat.x * kb, POP, flat.z * kb));
			e.hurtMarked = true;
			e.hasImpulse = true;
			n++;
		}
		shockwave(level, centre);
		level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.9f, 0.75f);
		level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.6f, 0.55f);
		IronManSounds.play(player, IronManSounds.PUNCH, 1.0f, 0.6f);
		return n;
	}

	/** The visual: a burst at the fists, then rings of the ground's own block chunks flying outward to {@link #RADIUS}. */
	private static void shockwave(ServerLevel level, Vec3 centre) {
		BlockState ground = level.getBlockState(BlockPos.containing(centre.x, centre.y - 0.2, centre.z));
		if (ground.isAir() || ground.getRenderShape() == RenderShape.INVISIBLE) {
			ground = Blocks.DIRT.defaultBlockState();
		}
		BlockParticleOption chunks = new BlockParticleOption(ParticleTypes.BLOCK, ground);
		level.sendParticles(ParticleTypes.EXPLOSION, centre.x, centre.y + 0.3, centre.z, 1, 0, 0, 0, 0);
		level.sendParticles(chunks, centre.x, centre.y + 0.1, centre.z, 40, 0.6, 0.1, 0.6, 0.25);
		// three rings, each particle launched outward along its spoke (count 0 = the offsets are a velocity)
		for (int ring = 1; ring <= 3; ring++) {
			double r = RADIUS * ring / 3.0;
			int spokes = 12 + ring * 10;
			for (int i = 0; i < spokes; i++) {
				double a = (Math.PI * 2.0 * i) / spokes + ring * 0.21;
				double cx = Math.cos(a);
				double cz = Math.sin(a);
				double x = centre.x + cx * r;
				double z = centre.z + cz * r;
				level.sendParticles(chunks, x, centre.y + 0.15, z, 0, cx, 0.6, cz, 0.35);
				// a little spray of chunks kicked up where the wave passes (thicker at the outer rim)
				if (ring == 3 || i % 2 == 0) {
					level.sendParticles(chunks, x, centre.y + 0.1, z, 3, 0.2, 0.05, 0.2, 0.15);
				}
				if (i % 2 == 0) {
					level.sendParticles(ParticleTypes.CLOUD, x, centre.y + 0.1, z, 0, cx, 0.05, cz, 0.12);
				}
			}
		}
		level.sendParticles(ParticleTypes.CRIT, centre.x, centre.y + 0.5, centre.z, 20, RADIUS * 0.4, 0.2, RADIUS * 0.4, 0.3);
	}
}
