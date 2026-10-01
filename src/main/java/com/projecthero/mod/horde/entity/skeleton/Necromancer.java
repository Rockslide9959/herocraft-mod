package com.projecthero.mod.horde.entity.skeleton;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * v0.14.16 (the Skeleton Horde's support caster): a soul-blue skeleton in a dark hooded robe carrying a soul torch. It
 * never fights -- it keeps away from players and, every ten seconds, <b>raises the dead</b>: a 1.5 s chant (a ring of
 * souls round its feet) and then two Bone Runners claw up beside it (three from wave 6; never more than {@link #maxMinions}
 * at once). Every three seconds it also mends the undead round it (Regeneration). Kill it first: its raised dead
 * crumble the moment it dies, and on their own after a minute.
 */
public class Necromancer extends HordeSkeleton {
	public static final int RAISE_COOLDOWN = 200;
	public static final int RAISE_CHANT = 30;
	static final int MINION_LIFETIME = 60 * 20;
	static final double HEAL_RADIUS = 8.0;
	private int raiseCooldown = 80;
	private int chant = -1;
	private final List<UUID> minions = new ArrayList<>();
	private final List<Long> minionBorn = new ArrayList<>();

	public Necromancer(EntityType<? extends Necromancer> type, Level level) {
		super(type, level);
		this.xpReward = 15;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return AbstractSkeleton.createAttributes()
				.add(Attributes.MAX_HEALTH, 30.0)
				.add(Attributes.MOVEMENT_SPEED, 0.26)
				.add(Attributes.FOLLOW_RANGE, 40.0);
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		this.goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Player.class, 7.0f, 1.0, 1.25));
	}

	/** It casts, it doesn't fight: no bow goal, no melee goal. */
	@Override
	public void reassessWeaponGoal() {
	}

	@Override
	protected void equip(RandomSource random) {
		setItemSlot(EquipmentSlot.HEAD, robe(Items.LEATHER_HELMET));
		setItemSlot(EquipmentSlot.CHEST, robe(Items.LEATHER_CHESTPLATE));
		setItemSlot(EquipmentSlot.LEGS, robe(Items.LEATHER_LEGGINGS));
		setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.SOUL_TORCH));
	}

	private static ItemStack robe(net.minecraft.world.item.Item item) {
		ItemStack s = new ItemStack(item);
		s.set(DataComponents.DYED_COLOR, new DyedItemColor(0x2A1236, false));
		return s;
	}

	public int maxMinions() {
		return hordeWave() >= 6 ? 6 : 4;
	}

	public int liveMinions(ServerLevel level) {
		prune(level);
		return minions.size();
	}

	@Override
	protected void customServerAiStep() {
		super.customServerAiStep();
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		if (tickCount % 20 == 0) {
			prune(level);
		}
		if (tickCount % 60 == 0) {
			mend(level);
		}
		if (chant >= 0) {
			tickChant(level);
			return;
		}
		if (raiseCooldown > 0) {
			raiseCooldown--;
			return;
		}
		LivingEntity target = getTarget();
		if (target != null && target.isAlive() && distanceTo(target) < 28.0 && liveMinions(level) < maxMinions()) {
			startChant(level);
		}
	}

	public void startChant(ServerLevel level) {
		chant = 0;
		getNavigation().stop();
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 1.0f, 0.7f);
	}

	/** One tick of the chant; the dead rise on the last. */
	public void tickChant(ServerLevel level) {
		if (chant < 0) {
			return;
		}
		chant++;
		getNavigation().stop();
		if (chant % 4 == 0) {
			ring(level, ParticleTypes.SOUL, position(), 1.6, 10);
		}
		if (chant < RAISE_CHANT) {
			return;
		}
		chant = -1;
		raiseCooldown = RAISE_COOLDOWN + getRandom().nextInt(60);
		raise(level);
	}

	/** Raises up to two (three from wave 6) Bone Runners, within the cap. */
	public int raise(ServerLevel level) {
		int count = Math.min(hordeWave() >= 6 ? 3 : 2, maxMinions() - liveMinions(level));
		int raised = 0;
		for (int i = 0; i < count; i++) {
			BoneRunner runner = SkeletonHordeEntityTypes.BONE_RUNNER.create(level);
			if (runner == null) {
				continue;
			}
			double a = getRandom().nextDouble() * Math.PI * 2;
			runner.moveTo(getX() + Math.cos(a) * 1.8, getY(), getZ() + Math.sin(a) * 1.8, getRandom().nextFloat() * 360f, 0);
			runner.setHordeWave(hordeWave());
			runner.finalizeSpawn(level, level.getCurrentDifficultyAt(runner.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
			if (getTarget() != null) {
				runner.setTarget(getTarget());
			}
			level.addFreshEntity(runner);
			minions.add(runner.getUUID());
			minionBorn.add(level.getGameTime());
			level.sendParticles(ParticleTypes.SOUL, runner.getX(), runner.getY() + 0.5, runner.getZ(), 10, 0.3, 0.4, 0.3, 0.02);
			raised++;
		}
		if (raised > 0) {
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 1.0f, 0.6f);
		}
		return raised;
	}

	/** Regeneration for every undead monster within {@link #HEAL_RADIUS}, itself included. */
	private void mend(ServerLevel level) {
		List<Monster> near = level.getEntitiesOfClass(Monster.class, new AABB(blockPosition()).inflate(HEAL_RADIUS),
				m -> m.isAlive() && m instanceof AbstractSkeleton);
		for (Monster m : near) {
			m.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 70, 0, false, true));
		}
		if (!near.isEmpty()) {
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 1.9, getZ(), 6, 0.2, 0.2, 0.2, 0.01);
		}
	}

	/** Forgets the dead and crumbles anything past its lifetime. */
	private void prune(ServerLevel level) {
		long now = level.getGameTime();
		Iterator<UUID> it = minions.iterator();
		Iterator<Long> born = minionBorn.iterator();
		while (it.hasNext()) {
			UUID id = it.next();
			long b = born.next();
			Entity e = level.getEntity(id);
			if (!(e instanceof Mob m) || !m.isAlive()) {
				it.remove();
				born.remove();
			} else if (now - b > MINION_LIFETIME) {
				crumble(level, m);
				it.remove();
				born.remove();
			}
		}
	}

	private static void crumble(ServerLevel level, Mob m) {
		level.sendParticles(ParticleTypes.WHITE_ASH, m.getX(), m.getY() + 0.6, m.getZ(), 20, 0.3, 0.5, 0.3, 0.02);
		m.discard();
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (level() instanceof ServerLevel level) {
			for (UUID id : minions) {
				if (level.getEntity(id) instanceof Mob m && m.isAlive()) {
					crumble(level, m);
				}
			}
			minions.clear();
			minionBorn.clear();
		}
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		ListTag list = new ListTag();
		for (UUID u : minions) {
			list.add(NbtUtils.createUUID(u));
		}
		tag.put("Minions", list);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		minions.clear();
		minionBorn.clear();
		ListTag list = tag.getList("Minions", Tag.TAG_INT_ARRAY);
		long now = level().getGameTime();
		for (int i = 0; i < list.size(); i++) {
			minions.add(NbtUtils.loadUUID(list.get(i)));
			minionBorn.add(now);
		}
	}
}
