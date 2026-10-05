package com.projecthero.mod.syndicate.entity;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.event.EventInstance;
import com.projecthero.mod.event.EventManager;
import com.projecthero.mod.syndicate.SyndicateBust;
import com.projecthero.mod.syndicate.SyndicateGunfire;
import com.projecthero.mod.syndicate.SyndicateItems;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.25: the Kingpin -- the Syndicate Bust's boss. A huge man in a white suit (the player model at 1.35x, wide), slow
 * to start and terrible once moving. 600 health for one fighter (+200 for each extra, up to 2,400), 10 armour, can't be
 * knocked back, and his suit is armoured: projectiles do a quarter less. His moves:
 * <ul>
 *   <li><b>Cane</b> -- heavy melee swings with knockback.</li>
 *   <li><b>Bull Rush</b> (6-20 blocks) -- a one-second wind-up (he lowers his head, the floor shakes), then a straight
 *       charge that tramples and launches anyone in the way. Make him hit a wall: he's <em>stunned</em> for 1.5 s and
 *       takes half again as much damage.</li>
 *   <li><b>Grab</b> (close) -- he lifts you by the throat for a second, then hurls you across the room. Hit him hard
 *       enough while he holds someone (30 damage) and he drops them.</li>
 *   <li><b>Cane Gun</b> (7+ blocks) -- the cane hides a barrel: three quick shots.</li>
 *   <li><b>Ground Pound</b> (phase 2+) -- he leaps and lands with a shockwave that throws everyone near him up.</li>
 *   <li><b>"Boys!"</b> -- at two-thirds and one-third health he whistles up reinforcements through the doors.</li>
 *   <li><b>Enrage</b> at 30% -- faster, harder, shorter cooldowns.</li>
 * </ul>
 * He talks: a line on arrival and for most of his moves (rate-limited so it never spams).
 */
public class KingpinEntity extends SyndicateCriminal {
	public static final double BASE_HEALTH = 600.0;
	public static final double HEALTH_PER_FIGHTER = 200.0;
	public static final double MAX_HEALTH = 2400.0;
	public static final float SCALE = 1.35f;

	public static final byte ACTION_RUSH_WINDUP = 2;
	public static final byte ACTION_RUSH = 3;
	public static final byte ACTION_STUNNED = 4;
	public static final byte ACTION_GRAB = 5;
	public static final byte ACTION_HOLD = 6;
	public static final byte ACTION_GUN = 7;
	public static final byte ACTION_POUND = 8;
	public static final byte ACTION_WHISTLE = 9;

	private static final ResourceLocation ENRAGED_SPEED = ProjectHeroMod.id("kingpin_enraged_speed");
	private static final ResourceLocation ENRAGED_DAMAGE = ProjectHeroMod.id("kingpin_enraged_damage");

	private final ServerBossEvent bossBar = new ServerBossEvent(Component.translatable("entity.projecthero.kingpin"),
			BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_10);
	private boolean calledAt66;
	private boolean calledAt33;
	private boolean enraged;
	private boolean introduced;
	private int stunned;
	private long lastLineTick = -1000;

	public KingpinEntity(EntityType<? extends KingpinEntity> type, Level level) {
		super(type, level);
		this.xpReward = 250;
		refreshDimensions(); // the SCALE attribute sizes his hit-box
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, BASE_HEALTH)
				.add(Attributes.MOVEMENT_SPEED, 0.27)
				.add(Attributes.ATTACK_DAMAGE, 7.0) // + the cane's own +8 in his hand = 15 a swing
				.add(Attributes.ATTACK_KNOCKBACK, 1.4)
				.add(Attributes.ARMOR, 10.0)
				.add(Attributes.ARMOR_TOUGHNESS, 6.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.FOLLOW_RANGE, 48.0)
				.add(Attributes.SAFE_FALL_DISTANCE, 16.0)
				.add(Attributes.STEP_HEIGHT, 1.0)
				.add(Attributes.SCALE, SCALE);
	}

	/** Health for {@code players} fighters. */
	public void configure(int players) {
		double hp = Math.min(MAX_HEALTH, BASE_HEALTH + HEALTH_PER_FIGHTER * Math.max(0, players - 1));
		getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
		setHealth((float) hp);
	}

	@Override
	protected SyndicateSkin defaultSkin() {
		return SyndicateSkin.KINGPIN;
	}

	@Override
	protected void registerCombatGoals() {
		goalSelector.addGoal(2, new Brain(this));
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, SpawnGroupData data) {
		SpawnGroupData out = super.finalizeSpawn(level, difficulty, reason, data);
		setSkin(SyndicateSkin.KINGPIN);
		if (SyndicateItems.KINGPIN_CANE != null) {
			arm(new ItemStack(SyndicateItems.KINGPIN_CANE));
		}
		setHealth(getMaxHealth());
		return out;
	}

	// ---------------------------------------------------------------- damage

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (source.is(DamageTypeTags.IS_PROJECTILE)) {
			amount *= 0.75f; // the armoured suit
		}
		if (stunned > 0) {
			amount *= 1.5f;
		}
		boolean hurt = super.hurt(source, amount);
		if (hurt && level() instanceof ServerLevel server) {
			brainOf().onHurt(amount);
			checkThresholds(server);
		}
		return hurt;
	}

	private Brain brainOf() {
		for (var wrapped : goalSelector.getAvailableGoals()) {
			if (wrapped.getGoal() instanceof Brain b) {
				return b;
			}
		}
		return new Brain(this);
	}

	private void checkThresholds(ServerLevel level) {
		float f = getHealth() / getMaxHealth();
		if (!calledAt66 && f <= 0.66f) {
			calledAt66 = true;
			brainOf().queueWhistle();
		} else if (!calledAt33 && f <= 0.33f) {
			calledAt33 = true;
			brainOf().queueWhistle();
		}
		if (!enraged && f <= 0.30f) {
			enraged = true;
			enrage(level);
		}
	}

	private void enrage(ServerLevel level) {
		applyEnrage();
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.0f, 0.6f);
		level.sendParticles(ParticleTypes.ANGRY_VILLAGER, getX(), getEyeY() + 0.5, getZ(), 8, 0.6, 0.4, 0.6, 0.0);
		say(level, "enrage", true);
	}

	/** The enrage's stat changes alone (also re-applied, silently, when a saved enraged Kingpin loads). */
	private void applyEnrage() {
		AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null && speed.getModifier(ENRAGED_SPEED) == null) {
			speed.addTransientModifier(new AttributeModifier(ENRAGED_SPEED, 0.25, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
		AttributeInstance dmg = getAttribute(Attributes.ATTACK_DAMAGE);
		if (dmg != null && dmg.getModifier(ENRAGED_DAMAGE) == null) {
			dmg.addTransientModifier(new AttributeModifier(ENRAGED_DAMAGE, 0.2, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
		bossBar.setColor(BossEvent.BossBarColor.RED);
	}

	public boolean enraged() {
		return enraged;
	}

	public int phase() {
		return getHealth() / getMaxHealth() > 0.66f ? 1 : 2;
	}

	// ---------------------------------------------------------------- tick

	@Override
	protected void customServerAiStep() {
		super.customServerAiStep();
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		bossBar.setProgress(getHealth() / getMaxHealth());
		if (stunned > 0) {
			stunned--;
			getNavigation().stop();
			setDeltaMovement(0, getDeltaMovement().y, 0);
			if (tickCount % 4 == 0) {
				server.sendParticles(ParticleTypes.CRIT, getX(), getEyeY() + 0.6, getZ(), 3, 0.4, 0.1, 0.4, 0.0);
			}
			if (stunned == 0) {
				setAction(ACTION_NONE);
			}
		}
		if (!introduced && getTarget() instanceof Player) {
			introduced = true;
			say(server, "intro", true);
		}
		if (enraged && tickCount % 5 == 0) {
			server.sendParticles(ParticleTypes.SMOKE, getX(), getY() + getBbHeight() * 0.7, getZ(), 1, 0.4, 0.4, 0.4, 0.0);
		}
	}

	void stun(int ticks) {
		stunned = ticks;
		setAction(ACTION_STUNNED);
	}

	boolean isStunned() {
		return stunned > 0;
	}

	/** A line of dialogue to everyone within 48 blocks ({@code force} skips the rate limit). */
	void say(ServerLevel level, String key, boolean force) {
		long now = level.getGameTime();
		if (!force && now - lastLineTick < 160) {
			return;
		}
		lastLineTick = now;
		Component line = Component.translatable("boss.projecthero.kingpin.chat",
				Component.translatable("entity.projecthero.kingpin").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD),
				Component.translatable("boss.projecthero.kingpin.say." + key).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(this) < 48 * 48) {
				p.sendSystemMessage(line);
			}
		}
	}

	/** "Boys!" -- through the event's doors if he's part of a bust, otherwise right round him. */
	void callReinforcements(ServerLevel level) {
		int players = Math.max(1, level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(32), p -> !p.isSpectator() && !p.isCreative()).size());
		EventInstance event = EventManager.owning(this);
		if (event instanceof SyndicateBust bust) {
			bust.reinforce(level, players);
			return;
		}
		for (int i = 0; i < 2 + players; i++) {
			Mob m = i % 2 == 0 ? com.projecthero.mod.syndicate.SyndicateEntityTypes.THUG.create(level)
					: com.projecthero.mod.syndicate.SyndicateEntityTypes.GUNMAN.create(level);
			if (m == null) {
				continue;
			}
			double a = random.nextDouble() * Math.PI * 2;
			m.moveTo(getX() + Math.cos(a) * 5, getY(), getZ() + Math.sin(a) * 5, random.nextFloat() * 360f, 0f);
			m.finalizeSpawn(level, level.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
			if (getTarget() != null) {
				m.setTarget(getTarget());
			}
			level.addFreshEntity(m);
		}
	}

	// ---------------------------------------------------------------- boss bar, death, save

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
		// in a bust the stash pays out; a Kingpin fought on his own (spawn egg, command) drops his cane and his money
		if (EventManager.owning(this) == null) {
			if (SyndicateItems.KINGPIN_CANE != null) {
				spawnAtLocation(new ItemStack(SyndicateItems.KINGPIN_CANE));
			}
			spawnAtLocation(new ItemStack(Items.EMERALD, 8 + random.nextInt(9)));
			if (SyndicateItems.VILLAIN_DOSSIER != null) {
				spawnAtLocation(new ItemStack(SyndicateItems.VILLAIN_DOSSIER));
			}
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
	protected net.minecraft.sounds.SoundEvent getAmbientSound() {
		return null;
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.PLAYER_HURT;
	}

	@Override
	public float getVoicePitch() {
		return 0.6f;
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putBoolean("Called66", calledAt66);
		tag.putBoolean("Called33", calledAt33);
		tag.putBoolean("Enraged", enraged);
		tag.putBoolean("Introduced", introduced);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		calledAt66 = tag.getBoolean("Called66");
		calledAt33 = tag.getBoolean("Called33");
		introduced = tag.getBoolean("Introduced");
		if (tag.getBoolean("Enraged")) {
			enraged = true;
			applyEnrage();
		}
		if (hasCustomName()) {
			bossBar.setName(getDisplayName());
		}
	}

	// ---------------------------------------------------------------- the brain

	/** Everything he does, as one state machine: walking, the cane, and the five moves. */
	static final class Brain extends Goal {
		private enum Move { NONE, RUSH, GRAB, GUN, POUND, WHISTLE }

		private final KingpinEntity k;
		private Move move = Move.NONE;
		private int timer;
		private int caneCd = 20;
		private int rushCd = 100;
		private int grabCd = 140;
		private int gunCd = 80;
		private int poundCd = 200;
		private int whistlesQueued;
		private Vec3 dir = Vec3.ZERO;
		private LivingEntity held;
		private float damageWhileHolding;
		private int shotsLeft;
		private boolean leftGround;
		private final Set<LivingEntity> struck = new HashSet<>();

		Brain(KingpinEntity k) {
			this.k = k;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
		}

		void queueWhistle() {
			whistlesQueued++;
		}

		void onHurt(float amount) {
			if (move == Move.GRAB && held != null) {
				damageWhileHolding += amount;
			}
		}

		@Override
		public boolean canUse() {
			LivingEntity t = k.getTarget();
			return t != null && t.isAlive();
		}

		@Override
		public boolean canContinueToUse() {
			return canUse() || move != Move.NONE;
		}

		@Override
		public void stop() {
			release(false);
			move = Move.NONE;
			k.setAction(ACTION_NONE);
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		private float cdScale() {
			return k.enraged ? 0.65f : 1.0f;
		}

		@Override
		public void tick() {
			if (!(k.level() instanceof ServerLevel level)) {
				return;
			}
			caneCd--;
			rushCd--;
			grabCd--;
			gunCd--;
			poundCd--;
			if (k.isStunned()) {
				release(false);
				move = Move.NONE;
				return;
			}
			LivingEntity t = k.getTarget();
			if (move != Move.NONE) {
				timer++;
				switch (move) {
					case RUSH -> tickRush(level, t);
					case GRAB -> tickGrab(level, t);
					case GUN -> tickGun(level, t);
					case POUND -> tickPound(level, t);
					case WHISTLE -> tickWhistle(level);
					default -> move = Move.NONE;
				}
				return;
			}
			if (t == null || !t.isAlive()) {
				return;
			}
			k.getLookControl().setLookAt(t, 30f, 30f);
			double d = k.distanceTo(t);
			boolean sees = k.getSensing().hasLineOfSight(t);
			if (whistlesQueued > 0) {
				whistlesQueued--;
				begin(Move.WHISTLE, ACTION_WHISTLE);
				return;
			}
			if (poundCd <= 0 && k.phase() >= 2 && k.onGround() && (d <= 8 || crowd(level) >= 2)) {
				begin(Move.POUND, ACTION_POUND);
				return;
			}
			if (grabCd <= 0 && d <= 3.6 && k.onGround()) {
				begin(Move.GRAB, ACTION_GRAB);
				return;
			}
			if (rushCd <= 0 && d >= 6 && d <= 20 && sees && k.onGround()) {
				begin(Move.RUSH, ACTION_RUSH_WINDUP);
				k.say(level, "rush", false);
				return;
			}
			if (gunCd <= 0 && d >= 7 && d <= 30 && sees) {
				begin(Move.GUN, ACTION_GUN);
				shotsLeft = 3;
				k.say(level, "gun", false);
				return;
			}
			// walk in and use the cane
			double reach = 1.6 + k.getBbWidth();
			if (d > reach - 0.4) {
				k.getNavigation().moveTo(t, 1.0);
			} else {
				k.getNavigation().stop();
			}
			if (d <= reach && caneCd <= 0) {
				k.swing(InteractionHand.MAIN_HAND);
				k.doHurtTarget(t);
				level.playSound(null, k.getX(), k.getY(), k.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 1.0f, 0.7f);
				caneCd = (int) (24 * cdScale());
			}
		}

		private int crowd(ServerLevel level) {
			return level.getEntitiesOfClass(Player.class, k.getBoundingBox().inflate(6), p -> p.isAlive() && !p.isCreative() && !p.isSpectator()).size();
		}

		private void begin(Move m, byte action) {
			move = m;
			timer = 0;
			struck.clear();
			k.getNavigation().stop();
			k.setAction(action);
		}

		private void end() {
			move = Move.NONE;
			k.setAction(ACTION_NONE);
		}

		// ---- Bull Rush: 20-tick wind-up, then a 26-tick charge
		private void tickRush(ServerLevel level, LivingEntity t) {
			if (timer <= 20) {
				if (t != null && timer <= 18) {
					k.getLookControl().setLookAt(t, 60f, 60f);
					Vec3 to = t.position().subtract(k.position());
					dir = new Vec3(to.x, 0, to.z).normalize();
				}
				if (timer % 4 == 0) {
					BlockPos below = k.blockPosition().below();
					level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(below)), k.getX(), k.getY() + 0.1, k.getZ(),
							10, 0.6, 0.05, 0.6, 0.15);
					level.playSound(null, k.getX(), k.getY(), k.getZ(), SoundEvents.RAVAGER_STEP, SoundSource.HOSTILE, 1.4f, 0.6f);
				}
				if (timer == 20) {
					k.setAction(ACTION_RUSH);
					level.playSound(null, k.getX(), k.getY(), k.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.6f, 0.8f);
				}
				return;
			}
			k.setYRot((float) Math.toDegrees(Math.atan2(-dir.x, dir.z)));
			k.setYBodyRot(k.getYRot());
			k.setYHeadRot(k.getYRot());
			Vec3 v = dir.scale(k.enraged ? 1.05 : 0.9);
			k.setDeltaMovement(v.x, k.getDeltaMovement().y, v.z);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, k.getBoundingBox().inflate(0.8),
					e -> e != k && e.isAlive() && !(e instanceof SyndicateCriminal))) {
				if (struck.add(e)) {
					e.hurt(k.damageSources().mobAttack(k), 16.0f);
					e.push(dir.x * 2.0, 0.6, dir.z * 2.0);
					e.hurtMarked = true;
					level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.HOSTILE, 1.4f, 0.6f);
				}
			}
			if (timer % 3 == 0) {
				level.sendParticles(ParticleTypes.CLOUD, k.getX(), k.getY() + 0.2, k.getZ(), 2, 0.3, 0.05, 0.3, 0.02);
			}
			if (k.horizontalCollision && timer > 23) {
				level.playSound(null, k.getX(), k.getY(), k.getZ(), SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.HOSTILE, 1.6f, 0.6f);
				level.sendParticles(ParticleTypes.EXPLOSION, k.getX() + dir.x, k.getEyeY(), k.getZ() + dir.z, 1, 0, 0, 0, 0);
				k.stun(30);
				k.setDeltaMovement(-dir.x * 0.3, 0.2, -dir.z * 0.3);
				rushCd = (int) (180 * cdScale());
				end();
				k.setAction(ACTION_STUNNED);
				return;
			}
			if (timer >= 46) {
				k.setDeltaMovement(0, k.getDeltaMovement().y, 0);
				rushCd = (int) (160 * cdScale());
				end();
			}
		}

		// ---- Grab: 8-tick reach, hold 24 ticks, throw
		private void tickGrab(ServerLevel level, LivingEntity t) {
			if (timer == 8) {
				if (t != null && t.isAlive() && k.distanceTo(t) <= 4.2 && !(t instanceof Player p && (p.isCreative() || p.isSpectator()))) {
					held = t;
					damageWhileHolding = 0;
					k.setAction(ACTION_HOLD);
					k.say(level, "grab", false);
					level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 1.0f, 0.5f);
				} else {
					grabCd = (int) (80 * cdScale());
					end();
				}
				return;
			}
			if (held == null) {
				return;
			}
			if (!held.isAlive()) {
				release(false);
				end();
				return;
			}
			if (damageWhileHolding >= 30.0f) {
				level.playSound(null, k.getX(), k.getY(), k.getZ(), SoundEvents.PLAYER_HURT, SoundSource.HOSTILE, 1.4f, 0.5f);
				release(false);
				k.stun(20);
				grabCd = (int) (200 * cdScale());
				end();
				k.setAction(ACTION_STUNNED);
				return;
			}
			Vec3 look = Vec3.directionFromRotation(0, k.getYRot());
			Vec3 at = k.position().add(look.scale(1.3 * SCALE)).add(0, 1.5 * SCALE - held.getBbHeight() * 0.5, 0);
			if (held instanceof ServerPlayer sp) {
				sp.teleportTo(level, at.x, at.y, at.z, sp.getYRot(), sp.getXRot());
			} else {
				held.teleportTo(at.x, at.y, at.z);
			}
			held.setDeltaMovement(Vec3.ZERO);
			held.fallDistance = 0;
			if (timer >= 32) {
				Vec3 fling = look.scale(1.9).add(0, 0.75, 0);
				held.hurt(k.damageSources().mobAttack(k), 8.0f);
				held.setDeltaMovement(fling);
				held.hurtMarked = true;
				level.playSound(null, k.getX(), k.getY(), k.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.4f, 0.5f);
				k.swing(InteractionHand.OFF_HAND);
				held = null;
				grabCd = (int) (220 * cdScale());
				end();
			}
		}

		private void release(boolean thrown) {
			if (held != null && !thrown) {
				held.setDeltaMovement(0, 0.2, 0);
				held.hurtMarked = true;
			}
			held = null;
		}

		// ---- Cane Gun: 12-tick aim, three shots 6 ticks apart
		private void tickGun(ServerLevel level, LivingEntity t) {
			if (t == null || !t.isAlive()) {
				gunCd = 40;
				end();
				return;
			}
			k.getLookControl().setLookAt(t, 60f, 60f);
			if (timer >= 12 && (timer - 12) % 6 == 0 && shotsLeft > 0) {
				Vec3 aim = t.position().add(0, t.getBbHeight() * 0.6, 0);
				SyndicateGunfire.fire(level, k, aim, 32, 6.0f, 0.03, 1, SyndicateGunfire.Report.CANE);
				shotsLeft--;
			}
			if (shotsLeft <= 0 && timer >= 30) {
				gunCd = (int) (130 * cdScale());
				end();
			}
		}

		// ---- Ground Pound: leap, land, shockwave
		private void tickPound(ServerLevel level, LivingEntity t) {
			if (timer == 6) {
				Vec3 toward = t == null ? Vec3.ZERO : t.position().subtract(k.position());
				Vec3 h = toward.lengthSqr() < 1.0e-4 ? Vec3.ZERO : new Vec3(toward.x, 0, toward.z).normalize().scale(Math.min(0.7, toward.length() * 0.08));
				k.setDeltaMovement(h.x, 1.05, h.z);
				k.hasImpulse = true;
				leftGround = false;
				level.playSound(null, k.getX(), k.getY(), k.getZ(), SoundEvents.RAVAGER_ATTACK, SoundSource.HOSTILE, 1.4f, 0.6f);
				k.say(level, "pound", false);
				return;
			}
			if (timer > 6 && !k.onGround()) {
				leftGround = true;
			}
			if (timer > 9 && leftGround && k.onGround() || timer > 60) {
				shockwave(level);
				poundCd = (int) (260 * cdScale());
				end();
			}
		}

		private void shockwave(ServerLevel level) {
			level.playSound(null, k.getX(), k.getY(), k.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.6f, 0.7f);
			BlockPos below = k.blockPosition().below();
			for (int ring = 1; ring <= 3; ring++) {
				double r = ring * 2.3;
				for (int i = 0; i < 20 * ring; i++) {
					double a = i * Math.PI * 2 / (20 * ring);
					level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(below)), k.getX() + Math.cos(a) * r,
							k.getY() + 0.1, k.getZ() + Math.sin(a) * r, 2, 0.1, 0.1, 0.1, 0.15);
				}
			}
			level.sendParticles(ParticleTypes.EXPLOSION, k.getX(), k.getY() + 0.3, k.getZ(), 2, 0.5, 0.1, 0.5, 0);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, k.getBoundingBox().inflate(7, 2, 7),
					e -> e != k && e.isAlive() && !(e instanceof SyndicateCriminal))) {
				double d = e.distanceTo(k);
				if (d > 7.5) {
					continue;
				}
				float dmg = (float) (14.0 * (1.0 - 0.5 * d / 7.5));
				e.hurt(k.damageSources().mobAttack(k), dmg);
				Vec3 out = e.position().subtract(k.position());
				Vec3 h = out.lengthSqr() < 1.0e-4 ? Vec3.ZERO : new Vec3(out.x, 0, out.z).normalize();
				e.push(h.x * 0.9, 0.9, h.z * 0.9);
				e.hurtMarked = true;
			}
		}

		// ---- "Boys!"
		private void tickWhistle(ServerLevel level) {
			if (timer == 1) {
				k.say(level, "whistle", true);
				level.playSound(null, k.getX(), k.getY(), k.getZ(), SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.HOSTILE, 2.0f, 1.9f);
			}
			if (timer == 6) {
				level.playSound(null, k.getX(), k.getY(), k.getZ(), SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.HOSTILE, 2.0f, 1.5f);
			}
			if (timer == 20) {
				k.callReinforcements(level);
				end();
			}
		}
	}
}
