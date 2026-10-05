package com.projecthero.mod.carnage.entity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import org.joml.Vector3f;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.carnage.CarnageConfig;
import com.projecthero.mod.carnage.CarnageEntityTypes;
import com.projecthero.mod.carnage.CarnageItems;
import com.projecthero.mod.carnage.CarnageSpawner;
import com.projecthero.mod.combat.SonicVulnerability;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.symbiote.entity.SymbioteSpikeEntity;
import com.projecthero.mod.symbiote.entity.SymbioteTendrilEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.25: Carnage -- the Symbiote's red, insane offspring, and the Symbiote power's own rival boss. He arrives in a
 * crimson meteor ({@link CrimsonMeteorEntity}), climbs out of the impact, and fights like a blender: blade-arm
 * combos, a ballistic pounce, crimson tendrils that whip and drag everyone within ten blocks, and fans of crimson
 * spikes. 700 health for one fighter (+250 each extra, configurable in {@code projecthero_carnage.json}), and he
 * regenerates.
 *
 * <p><b>He splits.</b> At three-quarters, half and a quarter health he wraps himself in a crimson cocoon (untouchable)
 * and lets loose a brood of {@link CrimsonSpawnEntity}. Kill every one and he bursts out <em>staggered</em> (half again
 * as much damage for 5 s); leave them alive for 20 s and they crawl back into him and heal him. After the third split
 * he is in a frenzy: faster, with shorter cooldowns and double regeneration.
 *
 * <p><b>His weaknesses</b> are the comic's: <b>fire</b> does double damage, makes him flinch and stops his regeneration
 * for 6 s; <b>sound</b> -- a rung bell, a goat horn, a Warden's boom, anything that marks him
 * {@link SonicVulnerability#isDisrupted disrupted} -- makes him writhe helplessly and take half again as much damage.
 * Explosions hurt him a quarter more.
 */
public class CarnageEntity extends Monster {
	public static final float SCALE = 1.2f;
	public static final byte ACTION_NONE = 0, ACTION_CLAW = 1, ACTION_POUNCE = 2, ACTION_LASH = 3, ACTION_SPIKES = 4,
			ACTION_COCOON = 5, ACTION_EMERGE = 6, ACTION_WRITHE = 7;

	private static final EntityDataAccessor<Byte> DATA_ACTION = SynchedEntityData.defineId(CarnageEntity.class, EntityDataSerializers.BYTE);
	private static final ResourceLocation FRENZY_SPEED = ProjectHeroMod.id("carnage_frenzy_speed");
	private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.75f, 0.04f, 0.06f), 1.1f);

	private final ServerBossEvent bossBar = new ServerBossEvent(Component.translatable("entity.projecthero.carnage"),
			BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_6);
	private int splitsDone;
	private boolean frenzied;
	private int emerging;
	private int cocoonTicks;
	private int staggered;
	private int writhe;
	private int writheCooldown;
	private int regenLockout;
	private int lonelyTicks;
	private long lastLineTick = -1000;
	private final List<UUID> brood = new ArrayList<>();

	public CarnageEntity(EntityType<? extends CarnageEntity> type, Level level) {
		super(type, level);
		this.xpReward = 300;
		refreshDimensions(); // the SCALE attribute sizes his hit-box
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 700.0)
				.add(Attributes.MOVEMENT_SPEED, 0.34)
				.add(Attributes.ATTACK_DAMAGE, 10.0)
				.add(Attributes.ATTACK_KNOCKBACK, 0.6)
				.add(Attributes.ARMOR, 6.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
				.add(Attributes.FOLLOW_RANGE, 48.0)
				.add(Attributes.STEP_HEIGHT, 1.5)
				.add(Attributes.SAFE_FALL_DISTANCE, 24.0)
				.add(Attributes.SCALE, SCALE);
	}

	/** Health for {@code players} fighters, from {@link CarnageConfig}. */
	public void configure(int players) {
		double hp = CarnageConfig.healthFor(players);
		getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
		setHealth((float) hp);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_ACTION, ACTION_NONE);
	}

	public byte action() {
		return entityData.get(DATA_ACTION);
	}

	public void setAction(byte a) {
		if (entityData.get(DATA_ACTION) != a) {
			entityData.set(DATA_ACTION, a);
		}
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(2, new Brain(this));
		goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 16.0f));
		goalSelector.addGoal(9, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this, CrimsonSpawnEntity.class));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
		targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, SpawnGroupData data) {
		SpawnGroupData out = super.finalizeSpawn(level, difficulty, reason, data);
		configure(Math.max(1, level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(48), p -> !p.isSpectator()).size()));
		return out;
	}

	/** Called by the meteor: two seconds of climbing out of the crater, untouchable. */
	public void emerge() {
		emerging = 40;
		setAction(ACTION_EMERGE);
	}

	public boolean inCocoon() {
		return cocoonTicks > 0;
	}

	// ---------------------------------------------------------------- damage

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if ((cocoonTicks > 0 || emerging > 0) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			if (level() instanceof ServerLevel server && cocoonTicks > 0) {
				server.sendParticles(BLOOD, getX(), getY() + getBbHeight() * 0.5, getZ(), 4, 0.3, 0.4, 0.3, 0.0);
			}
			return false;
		}
		boolean fire = source.is(DamageTypeTags.IS_FIRE);
		if (fire) {
			amount *= 2.0f;
		}
		if (source.is(DamageTypeTags.IS_EXPLOSION)) {
			amount *= 1.25f;
		}
		if (staggered > 0 || writhe > 0) {
			amount *= 1.5f;
		}
		boolean hurt = super.hurt(source, amount);
		if (hurt && level() instanceof ServerLevel server) {
			if (fire) {
				regenLockout = 120;
				if (writheCooldown <= 0) {
					startWrithe(server, 15, "fire");
				}
			}
			checkSplits(server);
		}
		return hurt;
	}

	private void startWrithe(ServerLevel level, int ticks, String line) {
		writhe = Math.max(writhe, ticks);
		writheCooldown = 80;
		regenLockout = Math.max(regenLockout, 120);
		setAction(ACTION_WRITHE);
		getNavigation().stop();
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_HURT, SoundSource.HOSTILE, 1.4f, 1.5f);
		say(level, line, false);
	}

	private void checkSplits(ServerLevel level) {
		float f = getHealth() / getMaxHealth();
		float next = switch (splitsDone) {
			case 0 -> 0.75f;
			case 1 -> 0.50f;
			case 2 -> 0.25f;
			default -> -1f;
		};
		if (next > 0 && f <= next && cocoonTicks <= 0) {
			splitsDone++;
			startCocoon(level);
		}
	}

	private void startCocoon(ServerLevel level) {
		cocoonTicks = 1;
		setAction(ACTION_COCOON);
		getNavigation().stop();
		say(level, "split", true);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 2.0f, 0.5f);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.WITCH_CELEBRATE, SoundSource.HOSTILE, 1.4f, 0.55f);
		int players = Math.max(1, level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(40), p -> !p.isSpectator() && !p.isCreative()).size());
		int n = Math.min(9, 3 + 2 * (players - 1));
		brood.clear();
		for (int i = 0; i < n; i++) {
			CrimsonSpawnEntity spawn = CarnageEntityTypes.CRIMSON_SPAWN.create(level);
			if (spawn == null) {
				continue;
			}
			double a = i * Math.PI * 2 / n;
			spawn.moveTo(getX() + Math.cos(a) * 1.2, getY() + 0.5, getZ() + Math.sin(a) * 1.2, (float) Math.toDegrees(a), 0f);
			spawn.finalizeSpawn(level, level.getCurrentDifficultyAt(spawn.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
			spawn.setParent(getUUID());
			spawn.setDeltaMovement(Math.cos(a) * 0.6, 0.5, Math.sin(a) * 0.6);
			if (getTarget() != null) {
				spawn.setTarget(getTarget());
			}
			level.addFreshEntity(spawn);
			brood.add(spawn.getUUID());
			SymbioteTendrilEntity.fromBody(this, spawn.position(), spawn, 12, 4, 0.1f);
		}
		level.sendParticles(BLOOD, getX(), getY() + 1, getZ(), 60, 0.8, 1.0, 0.8, 0.0);
	}

	private int liveBrood(ServerLevel level) {
		int n = 0;
		for (UUID u : brood) {
			if (level.getEntity(u) instanceof CrimsonSpawnEntity s && s.isAlive()) {
				n++;
			}
		}
		return n;
	}

	private void tickCocoon(ServerLevel level) {
		cocoonTicks++;
		setDeltaMovement(0, getDeltaMovement().y, 0);
		if (cocoonTicks % 3 == 0) {
			level.sendParticles(BLOOD, getX(), getY() + getBbHeight() * 0.5, getZ(), 6, 0.5, 0.7, 0.5, 0.0);
		}
		if (cocoonTicks % 20 == 0) {
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 1.6f, 1.3f);
		}
		int alive = liveBrood(level);
		if (alive == 0 && cocoonTicks > 20) {
			// the brood is dead: he bursts out, reeling
			cocoonTicks = 0;
			staggered = 100;
			setAction(ACTION_WRITHE);
			writhe = 30;
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_HURT, SoundSource.HOSTILE, 2.0f, 1.2f);
			level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 1, getZ(), 2, 0.4, 0.4, 0.4, 0);
			say(level, "brood_dead", true);
			return;
		}
		if (cocoonTicks >= CarnageConfig.get().reabsorbAfterTicks) {
			// they crawl back in
			int back = 0;
			for (UUID u : brood) {
				if (level.getEntity(u) instanceof CrimsonSpawnEntity s && s.isAlive()) {
					SymbioteTendrilEntity.fromBody(this, s.position(), s, 10, 5, 0.12f);
					s.discard();
					back++;
				}
			}
			heal(getMaxHealth() * 0.04f * back);
			cocoonTicks = 0;
			setAction(ACTION_NONE);
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 2.0f, 0.7f);
			say(level, "reabsorb", true);
		}
		if (cocoonTicks == 0 && splitsDone >= 3 && !frenzied) {
			frenzy(level);
		}
	}

	private void frenzy(ServerLevel level) {
		frenzied = true;
		AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null && speed.getModifier(FRENZY_SPEED) == null) {
			speed.addTransientModifier(new AttributeModifier(FRENZY_SPEED, 0.3, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.WITCH_CELEBRATE, SoundSource.HOSTILE, 2.0f, 0.45f);
		say(level, "frenzy", true);
	}

	public boolean frenzied() {
		return frenzied;
	}

	// ---------------------------------------------------------------- tick

	@Override
	protected void customServerAiStep() {
		super.customServerAiStep();
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		bossBar.setProgress(getHealth() / getMaxHealth());
		long now = server.getGameTime();
		if (emerging > 0) {
			emerging--;
			setDeltaMovement(0, Math.max(0, getDeltaMovement().y), 0);
			server.sendParticles(BLOOD, getX(), getY() + 0.3, getZ(), 5, 0.6, 0.2, 0.6, 0.0);
			if (emerging == 0) {
				setAction(ACTION_NONE);
				server.playSound(null, getX(), getY(), getZ(), SoundEvents.WITCH_CELEBRATE, SoundSource.HOSTILE, 2.0f, 0.5f);
				say(server, "intro", true);
			}
			return;
		}
		if (cocoonTicks > 0) {
			tickCocoon(server);
			if (cocoonTicks == 0 && splitsDone >= 3 && !frenzied) {
				frenzy(server);
			}
			return;
		}
		if (writheCooldown > 0) {
			writheCooldown--;
		}
		if (SonicVulnerability.isDisrupted(this, now) && writheCooldown <= 0) {
			startWrithe(server, (int) Math.min(60, Math.max(20, SonicVulnerability.remaining(this, now))), "sonic");
		}
		if (writhe > 0) {
			writhe--;
			getNavigation().stop();
			setDeltaMovement(0, getDeltaMovement().y, 0);
			if (writhe % 4 == 0) {
				server.sendParticles(BLOOD, getX(), getY() + getBbHeight() * 0.7, getZ(), 3, 0.4, 0.3, 0.4, 0.0);
			}
			if (writhe == 0 && action() == ACTION_WRITHE) {
				setAction(ACTION_NONE);
			}
		}
		if (staggered > 0) {
			staggered--;
		}
		if (regenLockout > 0) {
			regenLockout--;
		} else if (tickCount % 20 == 0 && getHealth() < getMaxHealth()) {
			heal(frenzied ? 3.0f : 1.5f);
		}
		if (tickCount % 4 == 0) {
			server.sendParticles(BLOOD, getX(), getY() + getBbHeight() * 0.5, getZ(), 1, 0.35, 0.6, 0.35, 0.0);
		}
		// nobody to fight for ten minutes: he goes looking elsewhere
		if (tickCount % 20 == 0) {
			Player near = server.getNearestPlayer(this, 96);
			lonelyTicks = near == null ? lonelyTicks + 20 : 0;
			if (lonelyTicks >= 12000) {
				discard();
			}
		}
	}

	boolean busy() {
		return emerging > 0 || cocoonTicks > 0 || writhe > 0;
	}

	/** A line to everyone within 48 blocks ({@code force} skips the 7-second limit). */
	void say(ServerLevel level, String key, boolean force) {
		long now = level.getGameTime();
		if (!force && now - lastLineTick < 140) {
			return;
		}
		lastLineTick = now;
		Component line = Component.translatable("boss.projecthero.carnage.chat",
				Component.translatable("entity.projecthero.carnage").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
				Component.translatable("boss.projecthero.carnage.say." + key).withStyle(ChatFormatting.RED, ChatFormatting.ITALIC));
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(this) < 48 * 48) {
				p.sendSystemMessage(line);
			}
		}
	}

	@Override
	public boolean killedEntity(ServerLevel level, LivingEntity entity) {
		if (entity instanceof Player) {
			say(level, "kill", true);
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.WITCH_CELEBRATE, SoundSource.HOSTILE, 1.6f, 0.6f);
		}
		return super.killedEntity(level, entity);
	}

	// ---------------------------------------------------------------- bar, death, misc

	@Override
	public void startSeenByPlayer(ServerPlayer player) {
		super.startSeenByPlayer(player);
		bossBar.addPlayer(player);
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		bossBar.removePlayer(player);
	}

	@Override
	public void die(DamageSource source) {
		if (level() instanceof ServerLevel server) {
			say(server, "death", true);
			for (UUID u : brood) {
				if (server.getEntity(u) instanceof CrimsonSpawnEntity s) {
					s.kill();
				}
			}
			CarnageSpawner.onKilled(server);
			server.sendParticles(BLOOD, getX(), getY() + 1, getZ(), 120, 1.0, 1.2, 1.0, 0.0);
		}
		super.die(source);
		bossBar.removeAllPlayers();
	}

	@Override
	public void remove(RemovalReason reason) {
		bossBar.removeAllPlayers();
		super.remove(reason);
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
		super.dropCustomDeathLoot(level, source, recentlyHit);
		int players = level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(32), p -> !p.isSpectator()).size();
		if (CarnageItems.CRIMSON_BIOMASS != null) {
			spawnAtLocation(new ItemStack(CarnageItems.CRIMSON_BIOMASS, 1 + random.nextInt(3) + Math.max(0, players - 1)));
		}
	}

	@Override
	public boolean removeWhenFarAway(double distanceSq) {
		return false;
	}

	@Override
	public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	public boolean canBeAffected(MobEffectInstance effect) {
		return !effect.is(MobEffects.POISON) && !effect.is(MobEffects.WITHER) && super.canBeAffected(effect);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.WITCH_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.SLIME_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.WARDEN_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 0.55f;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 160;
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putInt("Splits", splitsDone);
		tag.putBoolean("Frenzied", frenzied);
		tag.putInt("Cocoon", cocoonTicks);
		ListTag list = new ListTag();
		for (UUID u : brood) {
			list.add(NbtUtils.createUUID(u));
		}
		tag.put("Brood", list);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		splitsDone = tag.getInt("Splits");
		cocoonTicks = tag.getInt("Cocoon");
		brood.clear();
		ListTag list = tag.getList("Brood", Tag.TAG_INT_ARRAY);
		for (int i = 0; i < list.size(); i++) {
			brood.add(NbtUtils.loadUUID(list.get(i)));
		}
		if (tag.getBoolean("Frenzied")) {
			frenzied = true;
			AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
			if (speed != null && speed.getModifier(FRENZY_SPEED) == null) {
				speed.addTransientModifier(new AttributeModifier(FRENZY_SPEED, 0.3, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
			}
		}
	}

	// ---------------------------------------------------------------- the brain

	/** Claws, Pounce, Tendril Lash and Spike Volley, as one state machine. */
	static final class Brain extends Goal {
		private enum Move { NONE, CLAW, POUNCE, LASH, SPIKES }

		private final CarnageEntity c;
		private Move move = Move.NONE;
		private int timer;
		private int clawCd = 10;
		private int pounceCd = 60;
		private int lashCd = 80;
		private int spikeCd = 100;
		private boolean leftGround;
		private final List<LivingEntity> lashed = new ArrayList<>();

		Brain(CarnageEntity c) {
			this.c = c;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			LivingEntity t = c.getTarget();
			return t != null && t.isAlive();
		}

		@Override
		public boolean canContinueToUse() {
			return canUse() || move != Move.NONE;
		}

		@Override
		public void stop() {
			move = Move.NONE;
			if (!c.busy()) {
				c.setAction(ACTION_NONE);
			}
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		private int cd(int base) {
			return (int) (base * (c.frenzied ? 0.6f : 1.0f));
		}

		@Override
		public void tick() {
			if (!(c.level() instanceof ServerLevel level)) {
				return;
			}
			clawCd--;
			pounceCd--;
			lashCd--;
			spikeCd--;
			if (c.busy()) {
				move = Move.NONE;
				return;
			}
			LivingEntity t = c.getTarget();
			if (move != Move.NONE) {
				timer++;
				switch (move) {
					case CLAW -> tickClaw(level, t);
					case POUNCE -> tickPounce(level, t);
					case LASH -> tickLash(level);
					case SPIKES -> tickSpikes(level, t);
					default -> move = Move.NONE;
				}
				return;
			}
			if (t == null || !t.isAlive()) {
				return;
			}
			c.getLookControl().setLookAt(t, 40f, 40f);
			double d = c.distanceTo(t);
			boolean sees = c.getSensing().hasLineOfSight(t);
			double reach = 1.4 + c.getBbWidth();
			if (lashCd <= 0 && d <= 10 && sees) {
				begin(Move.LASH, ACTION_LASH);
				return;
			}
			if (pounceCd <= 0 && d >= 5 && d <= 16 && sees && c.onGround()) {
				begin(Move.POUNCE, ACTION_POUNCE);
				return;
			}
			if (spikeCd <= 0 && d >= 6 && d <= 24 && sees) {
				begin(Move.SPIKES, ACTION_SPIKES);
				c.say(level, "spikes", false);
				return;
			}
			if (d <= reach && clawCd <= 0) {
				begin(Move.CLAW, ACTION_CLAW);
				return;
			}
			if (d > reach - 0.3) {
				c.getNavigation().moveTo(t, 1.15);
			} else {
				c.getNavigation().stop();
			}
		}

		private void begin(Move m, byte action) {
			move = m;
			timer = 0;
			lashed.clear();
			c.getNavigation().stop();
			c.setAction(action);
		}

		private void end() {
			move = Move.NONE;
			c.setAction(ACTION_NONE);
		}

		// ---- three quick blade swipes
		private void tickClaw(ServerLevel level, LivingEntity t) {
			if (t != null) {
				c.getLookControl().setLookAt(t, 60f, 60f);
			}
			if (timer == 4 || timer == 10 || timer == 16) {
				c.swing(timer == 10 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
				if (t != null && t.isAlive() && c.distanceTo(t) <= 1.8 + c.getBbWidth()) {
					t.invulnerableTime = 0;
					c.doHurtTarget(t);
					level.sendParticles(BLOOD, t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 6, 0.2, 0.3, 0.2, 0.0);
				}
				level.playSound(null, c.getX(), c.getY(), c.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.0f, 0.7f + timer * 0.02f);
			}
			if (timer >= 20) {
				clawCd = cd(26);
				end();
			}
		}

		// ---- a ballistic leap onto the target
		private void tickPounce(ServerLevel level, LivingEntity t) {
			if (timer == 8) {
				if (t == null) {
					end();
					return;
				}
				Vec3 to = t.position().subtract(c.position());
				Vec3 v = AbilityHelpers.ballisticLaunch(to, to.length(), true);
				c.setDeltaMovement(v);
				c.hasImpulse = true;
				leftGround = false;
				level.playSound(null, c.getX(), c.getY(), c.getZ(), SoundEvents.WITCH_CELEBRATE, SoundSource.HOSTILE, 1.4f, 0.7f);
				return;
			}
			if (timer > 8 && !c.onGround()) {
				leftGround = true;
			}
			if (timer > 11 && leftGround && c.onGround() || timer > 50) {
				level.playSound(null, c.getX(), c.getY(), c.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 1.6f, 0.5f);
				level.sendParticles(BLOOD, c.getX(), c.getY() + 0.2, c.getZ(), 30, 1.2, 0.2, 1.2, 0.0);
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, c.getBoundingBox().inflate(2.5, 1.0, 2.5),
						e -> e != c && e.isAlive() && !(e instanceof CrimsonSpawnEntity))) {
					e.hurt(c.damageSources().mobAttack(c), 12.0f);
					Vec3 out = e.position().subtract(c.position());
					Vec3 h = out.lengthSqr() < 1.0e-4 ? Vec3.ZERO : new Vec3(out.x, 0, out.z).normalize();
					e.push(h.x * 0.8, 0.45, h.z * 0.8);
					e.hurtMarked = true;
				}
				pounceCd = cd(110);
				end();
			}
		}

		// ---- crimson tendrils whip out to everyone close and drag them in
		private void tickLash(ServerLevel level) {
			if (timer == 1) {
				for (Player p : level.getEntitiesOfClass(Player.class, c.getBoundingBox().inflate(10),
						p -> p.isAlive() && !p.isCreative() && !p.isSpectator() && c.getSensing().hasLineOfSight(p))) {
					if (lashed.size() >= 4) {
						break;
					}
					lashed.add(p);
				}
				if (lashed.isEmpty() && c.getTarget() != null) {
					lashed.add(c.getTarget());
				}
				for (LivingEntity e : lashed) {
					SymbioteTendrilEntity.fromBody(c, e.position().add(0, e.getBbHeight() * 0.5, 0), e, 14, 5, 0.16f);
				}
				level.playSound(null, c.getX(), c.getY(), c.getZ(), SoundEvents.SLIME_ATTACK, SoundSource.HOSTILE, 1.6f, 0.6f);
			}
			if (timer == 5) {
				for (LivingEntity e : lashed) {
					if (!e.isAlive()) {
						continue;
					}
					e.hurt(c.damageSources().mobAttack(c), 7.0f);
					Vec3 in = c.position().subtract(e.position());
					Vec3 h = in.lengthSqr() < 1.0e-4 ? Vec3.ZERO : new Vec3(in.x, 0, in.z).normalize();
					double pull = Math.min(1.2, in.length() * 0.12);
					e.setDeltaMovement(h.x * pull, 0.3, h.z * pull);
					e.hurtMarked = true;
				}
				c.say(level, "lash", false);
			}
			if (timer >= 14) {
				lashCd = cd(100);
				end();
			}
		}

		// ---- a fan of seven crimson spikes
		private void tickSpikes(ServerLevel level, LivingEntity t) {
			if (t != null) {
				c.getLookControl().setLookAt(t, 60f, 60f);
			}
			if (timer == 10 && t != null) {
				Vec3 from = c.position().add(0, c.getBbHeight() * 0.65, 0);
				Vec3 aim = t.position().add(0, t.getBbHeight() * 0.5, 0).subtract(from).normalize();
				for (int i = -3; i <= 3; i++) {
					double a = Math.toRadians(i * 8.0);
					Vec3 dir = new Vec3(aim.x * Math.cos(a) - aim.z * Math.sin(a), aim.y, aim.x * Math.sin(a) + aim.z * Math.cos(a));
					SymbioteSpikeEntity.shootCrimson(c, from.add(dir.scale(0.8)), dir, 5.0f, 2.4f);
				}
				level.playSound(null, c.getX(), c.getY(), c.getZ(), SoundEvents.TRIDENT_THROW.value(), SoundSource.HOSTILE, 1.4f, 0.6f);
			}
			if (timer >= 20) {
				spikeCd = cd(120);
				end();
			}
		}
	}

	/** The spawn egg / command path: an instant Carnage (no meteor) still says hello. */
	public void announce(ServerLevel level) {
		say(level, "intro", true);
	}

	public List<UUID> brood() {
		return brood;
	}

	@Override
	public boolean isPushable() {
		return cocoonTicks <= 0 && super.isPushable();
	}

	@Override
	protected void doPush(Entity entity) {
		if (cocoonTicks <= 0) {
			super.doPush(entity);
		}
	}
}
