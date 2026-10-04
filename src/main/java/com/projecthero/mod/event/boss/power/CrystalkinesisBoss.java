package com.projecthero.mod.event.boss.power;

import java.util.List;

import com.projecthero.mod.event.boss.BossPowerController;
import com.projecthero.mod.event.entity.EmpoweredZombie;
import com.projecthero.mod.hero.revamp.BatchCFx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Crystalkinesis (v0.14.1 kit): Crystal Shard volleys, Refract (a beam that splits off onto nearby victims), Crystal
 * Prison (a root -- caged in amethyst for a pet, held in place for a player) and Crystal Eruption (a charged falloff
 * burst that raises amethyst pillars). The player's shard entity and crystal nodes need a player owner, so the boss
 * fires visible particle shards with the same amethyst look.
 */
public class CrystalkinesisBoss extends BossPowerController {
	public static final String POWER_KEY = "power_06_crystalkinesis";

	private static final int SHARD = 0;
	private static final int REFRACT = 1;
	private static final int PRISON = 2;
	private static final int ERUPTION = 3;
	private static final List<String> IDS = List.of("crystal_shard", "refract", "crystal_prison", "crystal_eruption");

	private static final ParticleOptions CRYSTAL = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.AMETHYST_BLOCK.defaultBlockState());
	private static final ParticleOptions BEAM = BatchCFx.dust(0xC785FF, 1.1f);

	public CrystalkinesisBoss(EmpoweredZombie boss) {
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
		return 10.0;
	}

	@Override
	public ParticleOptions auraParticle() {
		return ParticleTypes.END_ROD;
	}

	@Override
	public BossEvent.BossBarColor barColor() {
		return BossEvent.BossBarColor.PURPLE;
	}

	@Override
	public void tick(ServerLevel level, LivingEntity target) {
		double d = boss.distanceTo(target);
		if (d < 14.0 && ready(ERUPTION) && (lowHealth() || freshChoice(ERUPTION))) {
			beginCast(level, target, ERUPTION, 1000, 3, SoundEvents.AMETHYST_BLOCK_RESONATE, CRYSTAL, t -> eruption(level));
			return;
		}
		if (d < 16.0 && sees(target) && ready(PRISON) && freshChoice(PRISON)) {
			prison(level, target);
			startCooldown(PRISON, 220);
			return;
		}
		if (d < 32.0 && sees(target) && ready(REFRACT) && freshChoice(REFRACT)) {
			beginRangedCast(level, target, REFRACT, 85, SoundEvents.AMETHYST_BLOCK_CHIME, BEAM, t -> refract(level, t));
			return;
		}
		if (d > 2.5 && d < 30.0 && sees(target) && ready(SHARD)) {
			// the Sneak volley: five shards, one every 4 ticks, fanned
			face(target);
			for (int i = 0; i < 5; i++) {
				int n = i;
				task(level, age -> {
					if (age < n * 4) {
						return true;
					}
					if (!target.isAlive()) {
						return false;
					}
					Vec3 from = boss.getEyePosition().add(boss.getLookAngle().scale(0.8));
					Vec3 aim = aimFromEyes(lead(target, 5.0)).add((random().nextDouble() - 0.5) * 0.08,
							(random().nextDouble() - 0.5) * 0.05, (random().nextDouble() - 0.5) * 0.08);
					projectile(level, from, aim, 1.8, 24, 0.5, ParticleTypes.END_ROD, 2, (at, hit) -> {
						if (hit != null) {
							hurt(hit, bossDamage(13.0f) * 0.6f);
							control(hit, MobEffects.MOVEMENT_SLOWDOWN, 40, 1);
						}
						particles(level, CRYSTAL, at, 14, 0.2);
						soundAt(level, at, SoundEvents.AMETHYST_BLOCK_HIT, 1.0f, 1.5f);
					});
					sound(level, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.4f);
					return false;
				});
			}
			startCooldown(SHARD, 90);
		}
	}

	/** Refract: an 11-damage beam that splits into up to two more beams at victims within 16 of where it lands. */
	private void refract(ServerLevel level, LivingEntity target) {
		Vec3 from = boss.getEyePosition();
		Vec3 to = clipEnd(level, from, from.add(aimFromEyes(mid(target)).scale(32.0)));
		particleLine(level, BEAM, from, to, 3.0);
		particleLine(level, ParticleTypes.END_ROD, from, to, 0.7);
		List<LivingEntity> hit = strikeLine(level, from, to, 0.6, bossDamage(11.0f));
		particles(level, ParticleTypes.FLASH, to, 1, 0.0);
		sound(level, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2f, 1.8f);
		Vec3 node = hit.isEmpty() ? to : mid(hit.get(0));
		int splits = 0;
		for (LivingEntity e : victimsAround(level, node, 16.0)) {
			if (hit.contains(e) || splits >= 2) {
				continue;
			}
			particleLine(level, BEAM, node, mid(e), 3.0);
			hurt(e, bossDamage(9.0f));
			splits++;
		}
		if (splits > 0) {
			soundAt(level, node, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.0f, 1.2f);
		}
	}

	private void prison(ServerLevel level, LivingEntity target) {
		control(target, MobEffects.MOVEMENT_SLOWDOWN, 140, 9);
		control(target, MobEffects.JUMP, 140, -10);
		if (!(target instanceof Player)) {
			// the player's cage: four sides two high and a roof, for a pet (players are rooted, not walled in)
			BlockPos p = target.blockPosition();
			for (BlockPos side : new BlockPos[] { p.north(), p.south(), p.east(), p.west() }) {
				placeTemp(level, side, Blocks.AMETHYST_BLOCK.defaultBlockState(), 140);
				placeTemp(level, side.above(), Blocks.AMETHYST_BLOCK.defaultBlockState(), 140);
			}
			placeTemp(level, p.above(2), Blocks.AMETHYST_BLOCK.defaultBlockState(), 140);
		}
		particleLine(level, BEAM, boss.getEyePosition(), mid(target), 2.0);
		particles(level, CRYSTAL, mid(target), 40, 0.5);
		BatchCFx.flatRing(level, target.position().add(0, 0.2, 0), 1.0, 16, ParticleTypes.END_ROD, 0.0);
		soundAt(level, target.position(), SoundEvents.AMETHYST_CLUSTER_PLACE, 1.0f, 0.5f);
	}

	private void eruption(ServerLevel level) {
		Vec3 c = boss.position();
		for (LivingEntity e : strikeArea(level, c, 14.0, bossDamage(48.0f), true, 1.3, 0.3)) {
			control(e, MobEffects.MOVEMENT_SLOWDOWN, 80, 2);
		}
		// amethyst pillars 1-4 tall between 4 and 14 blocks out (the player's: 60 of them out to 20)
		for (int i = 0; i < 24; i++) {
			double a = random().nextDouble() * Math.PI * 2.0;
			double r = 4.0 + random().nextDouble() * 10.0;
			BlockPos base = BlockPos.containing(c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r);
			int h = 1 + random().nextInt(4);
			for (int y = 0; y < h; y++) {
				placeTemp(level, base.above(y), Blocks.AMETHYST_BLOCK.defaultBlockState(), 160);
			}
		}
		level.sendParticles(CRYSTAL, c.x, c.y + 0.5, c.z, 160, 6.0, 0.5, 6.0, 0.1);
		BatchCFx.flatRing(level, c.add(0, 0.3, 0), 3.0, 48, BEAM, 0.8);
		sound(level, SoundEvents.AMETHYST_BLOCK_BREAK, 1.6f, 0.5f);
		sound(level, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.4f, 0.4f);
	}
}
