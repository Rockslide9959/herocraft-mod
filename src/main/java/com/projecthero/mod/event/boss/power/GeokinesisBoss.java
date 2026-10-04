package com.projecthero.mod.event.boss.power;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.projecthero.mod.event.boss.BossPowerController;
import com.projecthero.mod.event.entity.EmpoweredZombie;
import com.projecthero.mod.hero.power.p05.GroundType;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Geokinesis (v0.14.1 kit): Rock Shot, the racing Earth Spike line, Tectonic Pillar under the target, Earthquake (a
 * charged falloff quake with Slowness and Nausea) and Earth Armor once hurt. Like the player's, every move reads the
 * ground under the boss ({@link GroundType}): deepslate hits harder, sand blinds, netherrack burns, ice slows.
 */
public class GeokinesisBoss extends BossPowerController {
	public static final String POWER_KEY = "power_05_geokinesis";

	private static final int ROCK = 0;
	private static final int SPIKE = 1;
	private static final int PILLAR = 2;
	private static final int QUAKE = 3;
	private static final int ARMOR = 4;
	private static final List<String> IDS = List.of("rock_shot", "earth_spike", "tectonic_pillar", "earthquake", "earth_armor");

	private int armorTicks;

	public GeokinesisBoss(EmpoweredZombie boss) {
		super(boss);
	}

	@Override
	public String powerKey() {
		return POWER_KEY;
	}

	@Override
	public List<String> abilityIds() {
		return IDS;
	}

	@Override
	public double preferredRange() {
		return 9.0;
	}

	@Override
	public ParticleOptions auraParticle() {
		return new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState());
	}

	@Override
	public BossEvent.BossBarColor barColor() {
		return BossEvent.BossBarColor.YELLOW;
	}

	private GroundType.Ground ground(ServerLevel level) {
		return GroundType.under(level, boss.position());
	}

	/** The ground type's rider, as the player's {@code GroundType.onHit}. */
	private void groundHit(ServerLevel level, GroundType.Ground g, LivingEntity e) {
		switch (g.type()) {
			case SAND -> control(e, MobEffects.BLINDNESS, 60, 0);
			case NETHER -> ignite(e, 5);
			case FROST -> control(e, MobEffects.MOVEMENT_SLOWDOWN, 80, 1);
			case DEEPSLATE -> knockAway(e, boss.position(), 0.7, 0.1);
			default -> {
			}
		}
		particles(level, g.dust(), mid(e), 16, 0.3);
	}

	@Override
	public void tick(ServerLevel level, LivingEntity target) {
		double d = boss.distanceTo(target);
		GroundType.Ground g = ground(level);
		if (d < 14.0 && ready(QUAKE) && (lowHealth() || playersNear(level, 14.0).size() >= 2 || freshChoice(QUAKE))) {
			// the player's 85 t charge, as a 1.5 s wind-up the whole arena can see
			beginCast(level, target, QUAKE, 900, 3, SoundEvents.STONE_BREAK, g.dust(), t -> earthquake(level));
			return;
		}
		if (d < 16.0 && sees(target) && ready(SPIKE) && freshChoice(SPIKE)) {
			earthSpikeLine(level, target, g);
			startCooldown(SPIKE, 85);
			return;
		}
		if (d < 20.0 && ready(PILLAR) && freshChoice(PILLAR)) {
			Vec3 mark = target.position();
			ring(level, g.dust(), mark, 1.2, 14);
			sound(level, SoundEvents.ROOTED_DIRT_BREAK, 1.0f, 0.6f);
			schedule(1, () -> tectonicPillar(level, mark, g));
			startCooldown(PILLAR, 153);
			return;
		}
		if (d > 3.0 && d < 30.0 && sees(target) && ready(ROCK)) {
			face(target);
			Vec3 from = boss.getEyePosition().add(boss.getLookAngle().scale(0.8));
			float dmg = g.damage(bossDamage(11.0f));
			projectile(level, from, aimFromEyes(lead(target, 6.0)), 1.6, 30, 0.6, g.dust(), 6, (at, hit) -> {
				if (hit != null) {
					hurt(hit, dmg);
					knockAway(hit, boss.position(), 1.2, 0.2);
					groundHit(level, g, hit);
				}
				particles(level, g.dust(), at, 24, 0.3);
				soundAt(level, at, SoundEvents.STONE_HIT, 1.0f, 0.8f);
			});
			sound(level, SoundEvents.STONE_BREAK, 1.0f, 0.7f);
			startCooldown(ROCK, 40);
		}
	}

	@Override
	public void serverTick(ServerLevel level, LivingEntity target) {
		if (armorTicks > 0) {
			armorTicks--;
			if (armorTicks % 10 == 0) {
				particles(level, ground(level).dust(), boss.position().add(0, 1.0, 0), 4, 0.6);
			}
		}
	}

	private void earthSpikeLine(ServerLevel level, LivingEntity target, GroundType.Ground g) {
		Vec3 dir = flatDirTo(target.position());
		Vec3 start = boss.position().add(dir.scale(1.8));
		BlockState build = g.type().buildBlock(g.state());
		float dmg = g.damage(bossDamage(15.0f));
		Set<LivingEntity> hit = new HashSet<>();
		sound(level, SoundEvents.POINTED_DRIPSTONE_LAND, 1.2f, 0.6f);
		task(level, age -> {
			if (age >= 8) {
				return false;
			}
			for (int i = 0; i < 2; i++) {
				Vec3 at = start.add(dir.scale(age * 2.0 + i));
				BlockPos pos = BlockPos.containing(at.x, boss.getY(), at.z);
				placeTemp(level, pos, (age + i) % 2 == 0 ? build : Blocks.POINTED_DRIPSTONE.defaultBlockState(), 50);
				level.sendParticles(g.dust(), at.x, boss.getY() + 0.5, at.z, 8, 0.3, 0.3, 0.3, 0.05);
				for (LivingEntity e : victimsAround(level, new Vec3(at.x, boss.getY() + 0.5, at.z), 1.6)) {
					if (hit.add(e)) {
						hurt(e, dmg);
						fling(e, new Vec3(e.getDeltaMovement().x, 0.85, e.getDeltaMovement().z));
						control(e, MobEffects.MOVEMENT_SLOWDOWN, 60, 1);
						groundHit(level, g, e);
					}
				}
			}
			soundAt(level, start.add(dir.scale(age * 2.0)), SoundEvents.POINTED_DRIPSTONE_LAND, 0.6f, 0.8f);
			return true;
		});
	}

	private void tectonicPillar(ServerLevel level, Vec3 mark, GroundType.Ground g) {
		BlockState build = g.type().buildBlock(g.state());
		for (LivingEntity e : victimsAround(level, mark.add(0, 0.5, 0), 1.6)) {
			hurt(e, g.damage(bossDamage(12.0f)));
			fling(e, new Vec3(e.getDeltaMovement().x * 0.3, 1.2, e.getDeltaMovement().z * 0.3));
			groundHit(level, g, e);
		}
		BlockPos base = BlockPos.containing(mark);
		task(level, age -> {
			if (age >= 5) {
				return false;
			}
			placeTemp(level, base.above(age), build, 80); // skips cells anyone stands in
			return true;
		});
		particles(level, g.dust(), mark.add(0, 1.0, 0), 30, 0.5);
		soundAt(level, mark, SoundEvents.DEEPSLATE_BREAK, 1.2f, 0.7f);
		soundAt(level, mark, SoundEvents.GENERIC_EXPLODE, 0.8f, 0.7f);
	}

	private void earthquake(ServerLevel level) {
		GroundType.Ground g = ground(level);
		Vec3 c = boss.position();
		double r = 16.0; // the player's reaches 25
		for (LivingEntity e : strikeArea(level, c, r, g.damage(bossDamage(54.0f)), true, 1.4, 0.6)) {
			if (e.onGround() || e.getY() < c.y + 3.0) {
				control(e, MobEffects.MOVEMENT_SLOWDOWN, 140, 2);
				control(e, MobEffects.CONFUSION, 120, 0);
				groundHit(level, g, e);
			}
		}
		particles(level, ParticleTypes.EXPLOSION_EMITTER, c, 2, 1.0);
		level.sendParticles(g.dust(), c.x, c.y + 0.3, c.z, 200, 8.0, 0.3, 8.0, 0.1);
		sound(level, SoundEvents.GENERIC_EXPLODE, 1.4f, 0.35f);
		sound(level, SoundEvents.STONE_BREAK, 1.4f, 0.5f);
	}

	@Override
	public float modifyIncomingDamage(DamageSource source, float amount) {
		return armorTicks > 0 ? amount * 0.6f : amount; // Earth Armor: Resistance II
	}

	@Override
	public void onDamaged(ServerLevel level, DamageSource source, float amount) {
		if (healthFraction() < 0.6f && armorTicks <= 0 && readyReactive(ARMOR)) {
			armorTicks = 200;
			particles(level, ground(level).dust(), boss.position().add(0, 1.0, 0), 40, 0.7);
			sound(level, SoundEvents.STONE_PLACE, 1.2f, 0.5f);
			sound(level, SoundEvents.ANVIL_PLACE, 0.6f, 0.6f);
			startCooldown(ARMOR, 600);
		}
	}
}
