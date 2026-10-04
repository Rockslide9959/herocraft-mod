package com.projecthero.mod.event.boss.power;

import java.util.List;

import com.projecthero.mod.event.boss.BossPowerController;
import com.projecthero.mod.event.entity.EmpoweredZombie;
import com.projecthero.mod.hero.power.p09.FrostStacks;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Cryokinesis (v0.14.1 kit): Ice Bolt, Freeze Beam (held), Ice Spikes (Sneak Glacier Wall: spikes burst up under the
 * target), Flash Freeze around itself and -- once badly hurt -- Absolute Zero. Every hit adds the player power's real
 * frost stacks ({@link FrostStacks}); five freeze the victim solid, exactly as the player's do.
 */
public class CryokinesisBoss extends BossPowerController {
	public static final String POWER_KEY = "power_09_cryokinesis";

	private static final int BOLT = 0;
	private static final int BEAM = 1;
	private static final int SPIKES = 2;
	private static final int FLASH_FREEZE = 3;
	private static final int ABSOLUTE_ZERO = 4;
	private static final List<String> IDS = List.of("ice_bolt", "freeze_beam", "ice_wall", "flash_freeze", "absolute_zero");

	private static final ParticleOptions ICE = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ICE.defaultBlockState());
	private static final ParticleOptions FROST_BEAM = BatchCFx.dust(0x9EE6FF, 1.0f);

	private int beamTicks;

	public CryokinesisBoss(EmpoweredZombie boss) {
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
		return 8.0;
	}

	@Override
	public ParticleOptions auraParticle() {
		return ParticleTypes.SNOWFLAKE;
	}

	@Override
	public BossEvent.BossBarColor barColor() {
		return BossEvent.BossBarColor.BLUE;
	}

	/** Frost: the player power's stacks, plus the cold visuals for a player when hard crowd control is off. */
	private void frost(LivingEntity e, int n) {
		if (!isVictim(e)) {
			return;
		}
		FrostStacks.add(null, e, n);
		e.setTicksFrozen(Math.max(e.getTicksFrozen(), e.getTicksRequiredToFreeze() / 2 + 20 * n));
	}

	@Override
	public void tick(ServerLevel level, LivingEntity target) {
		if (beamTicks > 0) {
			return;
		}
		double d = boss.distanceTo(target);
		if (lowHealth() && d < 14.0 && ready(ABSOLUTE_ZERO)) {
			beginCast(level, target, ABSOLUTE_ZERO, 760, 4, SoundEvents.POWDER_SNOW_STEP, ParticleTypes.SNOWFLAKE,
					t -> absoluteZero(level));
			return;
		}
		if (d < 7.0 && ready(FLASH_FREEZE) && freshChoice(FLASH_FREEZE)) {
			flashFreeze(level);
			startCooldown(FLASH_FREEZE, 240);
			return;
		}
		if (d < 16.0 && sees(target) && ready(BEAM) && freshChoice(BEAM)) {
			beamTicks = 40;
			startCooldown(BEAM, 160);
			return;
		}
		if (d < 18.0 && ready(SPIKES) && freshChoice(SPIKES)) {
			Vec3 mark = target.position();
			BatchCFx.flatRing(level, mark.add(0, 0.2, 0), 2.5, 24, ParticleTypes.SNOWFLAKE, 0.0);
			sound(level, SoundEvents.GLASS_HIT, 1.0f, 0.6f);
			schedule(1, () -> iceSpikes(level, mark));
			startCooldown(SPIKES, 140);
			return;
		}
		if (d < 24.0 && sees(target) && ready(BOLT)) {
			face(target);
			Vec3 from = boss.getEyePosition();
			Vec3 to = clipEnd(level, from, from.add(aimFromEyes(lead(target, 2.0)).scale(24.0)));
			particleLine(level, FROST_BEAM, from, to, 3.0);
			particleLine(level, ParticleTypes.SNOWFLAKE, from, to, 1.5);
			for (LivingEntity e : victimsOnSegment(level, from, to, 0.6)) {
				hurt(e, bossDamage(8.5f));
				frost(e, 1);
				control(e, MobEffects.MOVEMENT_SLOWDOWN, 60, 0);
				particles(level, ICE, mid(e), 12, 0.3);
			}
			sound(level, SoundEvents.GLASS_BREAK, 0.8f, 1.6f);
			startCooldown(BOLT, 40);
		}
	}

	@Override
	public void serverTick(ServerLevel level, LivingEntity target) {
		if (beamTicks <= 0) {
			return;
		}
		beamTicks--;
		if (target == null) {
			return;
		}
		boss.getNavigation().stop();
		face(target);
		Vec3 from = boss.getEyePosition();
		Vec3 to = clipEnd(level, from, from.add(aimFromEyes(mid(target)).scale(16.0)));
		if (beamTicks % 2 == 0) {
			particleLine(level, FROST_BEAM, from, to, 2.0);
		}
		for (LivingEntity e : victimsOnSegment(level, from, to, 0.6)) {
			control(e, MobEffects.MOVEMENT_SLOWDOWN, 40, 3);
			e.clearFire();
			if (beamTicks % 10 == 0) {
				hurt(e, bossDamage(6.0f));
				frost(e, 1);
			}
		}
		if (beamTicks % 8 == 0) {
			sound(level, SoundEvents.POWDER_SNOW_STEP, 1.0f, 0.8f);
		}
	}

	private void iceSpikes(ServerLevel level, Vec3 mark) {
		for (int i = 0; i < 7; i++) {
			double a = random().nextDouble() * Math.PI * 2.0;
			double r = random().nextDouble() * 2.5;
			BlockPos base = BlockPos.containing(mark.x + Math.cos(a) * r, mark.y, mark.z + Math.sin(a) * r);
			int h = 2 + random().nextInt(3);
			for (int y = 0; y < h; y++) {
				placeTemp(level, base.above(y), Blocks.PACKED_ICE.defaultBlockState(), 200);
			}
		}
		for (LivingEntity e : victimsAround(level, mark.add(0, 0.5, 0), 3.5)) {
			hurt(e, bossDamage(10.0f));
			frost(e, 2);
			fling(e, new Vec3(e.getDeltaMovement().x, 0.85, e.getDeltaMovement().z));
		}
		particles(level, ICE, mark.add(0, 1, 0), 40, 1.0);
		soundAt(level, mark, SoundEvents.GLASS_BREAK, 1.2f, 0.7f);
	}

	private void flashFreeze(ServerLevel level) {
		Vec3 c = boss.position();
		for (LivingEntity e : victimsAround(level, c, 8.0)) {
			hurt(e, bossDamage(5.0f) + 1.5f);
			frost(e, 2);
			e.clearFire();
		}
		ring(level, ParticleTypes.SNOWFLAKE, c, 6.4, 48);
		particles(level, ICE, c.add(0, 1, 0), 60, 2.0);
		sound(level, SoundEvents.GLASS_BREAK, 1.0f, 0.6f);
		sound(level, SoundEvents.POWDER_SNOW_BREAK, 1.2f, 0.6f);
	}

	private void absoluteZero(ServerLevel level) {
		Vec3 c = boss.position();
		for (LivingEntity e : strikeArea(level, c, 14.0, bossDamage(42.0f), true, 0.0, 0.0)) {
			e.clearFire();
			frost(e, FrostStacks.MAX);
			control(e, MobEffects.MOVEMENT_SLOWDOWN, 200, 2);
		}
		level.sendParticles(ParticleTypes.SNOWFLAKE, c.x, c.y + 1, c.z, 240, 7.0, 2.0, 7.0, 0.05);
		particles(level, ICE, c.add(0, 1, 0), 80, 3.0);
		sound(level, SoundEvents.GLASS_BREAK, 1.6f, 0.4f);
		sound(level, SoundEvents.PLAYER_HURT_FREEZE, 1.4f, 0.5f);
	}
}
