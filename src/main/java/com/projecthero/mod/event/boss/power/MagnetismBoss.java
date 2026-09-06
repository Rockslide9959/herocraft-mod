package com.projecthero.mod.event.boss.power;

import com.projecthero.mod.event.boss.BossPowerController;
import com.projecthero.mod.event.entity.EmpoweredZombie;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Magnetism: specifically dangerous to anyone in metal, which in this mod means Iron Man above all.
 *
 * <ul>
 *   <li><b>Haul</b> -- drags an armoured player bodily toward the boss. The more metal they are
 *       wearing, the harder the pull.</li>
 *   <li><b>Seize</b> -- clamps their own armour down on them: heavy Slowness and Mining Fatigue for a
 *       few seconds, scaled by how much metal they have on.</li>
 *   <li><b>Repel</b> -- the reverse, used when several armoured players are on top of it.</li>
 * </ul>
 *
 * <h2>It never takes anything</h2>
 * The design is explicit: <b>do not permanently steal, destroy or delete valuable player equipment.</b>
 * So this controller never removes, damages, drops or unequips a single item -- it only reads how much
 * metal is worn and turns that into movement and a timed effect. An Iron Man player emerges from a
 * Magnetism boss thrown around and slowed, with their suit intact. Someone in leather barely notices
 * it, which is the intended counterplay.
 */
public class MagnetismBoss extends BossPowerController {
	public static final String POWER_KEY = "power_26_magnetic_manipulation";

	private static final int SLOT_HAUL = 0;
	private static final int SLOT_SEIZE = 1;
	private static final int SLOT_REPEL = 2;
	private static final int SLOT_PULSE = 3;

	public MagnetismBoss(EmpoweredZombie boss) {
		super(boss);
	}

	@Override
	public String powerKey() {
		return POWER_KEY;
	}

	@Override
	public double preferredRange() {
		return 7.0;
	}

	@Override
	public ParticleOptions auraParticle() {
		return ParticleTypes.ELECTRIC_SPARK;
	}

	@Override
	public BossEvent.BossBarColor barColor() {
		return BossEvent.BossBarColor.BLUE;
	}

	@Override
	public void tick(ServerLevel level, LivingEntity target) {
		int armoured = 0;
		for (Player player : playersNear(level, 6.0)) {
			if (metalPieces(player) >= 2) {
				armoured++;
			}
		}
		if (armoured >= 2 && ready(SLOT_REPEL)) {
			repel(level);
			startCooldown(SLOT_REPEL, 220);
			return;
		}

		int metal = target instanceof Player p ? metalPieces(p) : 0;
		double distance = boss.distanceTo(target);

		if (metal > 0 && distance > 5.0 && distance < 24.0 && ready(SLOT_HAUL) && freshChoice(SLOT_HAUL)) {
			haul(level, target, metal);
			startCooldown(SLOT_HAUL, 150);
			return;
		}
		if (metal >= 2 && distance < 14.0 && ready(SLOT_SEIZE)) {
			seize(level, target, metal);
			startCooldown(SLOT_SEIZE, 200);
			return;
		}
		// Against a target wearing little or no metal, Haul and Seize do nothing -- so the boss still
		// has a magnetic-field pulse that shoves anyone nearby (harder if they are wearing metal, but
		// never zero). Without it a Magnetism boss fighting an unarmoured player is just a melee zombie.
		if (distance < 6.0 && ready(SLOT_PULSE)) {
			pulse(level);
			startCooldown(SLOT_PULSE, 160);
		}
	}

	private void pulse(ServerLevel level) {
		sound(level, SoundEvents.IRON_GOLEM_REPAIR, 1.0f, 1.3f);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, boss.getX(), boss.getY() + 1.0, boss.getZ(),
				30, 2.0, 1.0, 2.0, 0.08);
		for (Player player : playersNear(level, 5.0)) {
			int metal = metalPieces(player);
			hurt(player, 3.0f + metal);
			knockAway(player, boss.position(), 0.7 + 0.3 * metal, 0.35);
		}
	}

	/** How many worn armour pieces are metallic. Read-only -- nothing is modified. */
	private int metalPieces(Player player) {
		int count = 0;
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) {
				continue;
			}
			ItemStack stack = player.getItemBySlot(slot);
			if (stack.isEmpty()) {
				continue;
			}
			// Reuses the mod's own magnetic-material test, so anything the player-side Magnetism power
			// already treats as metal is metal here too -- including the Iron Man marks.
			if (com.projecthero.mod.hero.power.p26.MagneticMaterials.isMagnetic(stack)) {
				count++;
			}
		}
		return count;
	}

	private void haul(ServerLevel level, LivingEntity target, int metal) {
		Vec3 pull = boss.position().subtract(target.position());
		if (pull.lengthSqr() < 1.0e-4) {
			return;
		}
		double strength = 0.4 + 0.22 * metal;
		pull = pull.normalize().scale(strength);
		target.setDeltaMovement(target.getDeltaMovement().add(pull.x, 0.25, pull.z));
		target.hurtMarked = true;
		sound(level, SoundEvents.IRON_GOLEM_REPAIR, 1.0f, 0.7f);
		particleLine(level, ParticleTypes.ELECTRIC_SPARK, boss.getEyePosition(), target.getEyePosition(), 2.0);
	}

	private void seize(ServerLevel level, LivingEntity target, int metal) {
		int amplifier = Math.min(3, metal);
		target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, amplifier));
		target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 80, 1));
		sound(level, SoundEvents.ANVIL_LAND, 0.8f, 1.4f);
		particles(level, ParticleTypes.ELECTRIC_SPARK, target.position().add(0, target.getBbHeight() * 0.5, 0), 20, 0.4);
	}

	private void repel(ServerLevel level) {
		sound(level, SoundEvents.IRON_GOLEM_DAMAGE, 1.0f, 0.6f);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, boss.getX(), boss.getY() + 1.0, boss.getZ(),
				40, 2.0, 1.0, 2.0, 0.1);
		for (Player player : playersNear(level, 8.0)) {
			int metal = metalPieces(player);
			if (metal == 0) {
				continue;
			}
			hurt(player, 3.0f + metal);
			knockAway(player, boss.position(), 0.8 + 0.35 * metal, 0.45);
		}
	}
}
