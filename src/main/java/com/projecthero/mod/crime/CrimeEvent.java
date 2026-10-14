package com.projecthero.mod.crime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import com.projecthero.mod.event.EventBossBar;
import com.projecthero.mod.event.EventConfig;
import com.projecthero.mod.event.EventInstance;
import com.projecthero.mod.event.EventState;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.2: a street crime -- a small, short {@link EventInstance} that happens on its own near a village (see
 * {@link CrimeScheduler}), needs no powers, and is open to any player who turns up. The base class owns everything the
 * four kinds share:
 * <ul>
 *   <li>the village it happens in (its bell, a name for it) and the alert: a chat line, a scream from the crime's
 *       direction, the bell ringing (villagers who hear it run indoors), a boss bar and an action-bar
 *       "~40 m NE" hint for everyone within {@code trigger.alertRadius};</li>
 *   <li>a time limit, after which the crooks get away;</li>
 *   <li>spawning crooks (the Syndicate's own thugs and gunmen, see {@link CrimeAI}) and remembering each one's role so a
 *       reloaded chunk gets the same behaviour back;</li>
 *   <li>who helped: damage dealt to the crooks, plus a bonus for each one put down. Stopping the crime pays emeralds and
 *       XP to every helper, and Hero of the Village plus villager gossip to whoever did the most;</li>
 *   <li>clean-up: crooks vanish in a puff of smoke when it ends, either way. Nobody within range for
 *       {@code trigger.abandonSeconds} and the crime ends with them. A server restart ends any crime that was running
 *       (see {@link Crimes}).</li>
 * </ul>
 */
public abstract class CrimeEvent extends EventInstance {
	/** Contribution credited for putting a crook down (on top of the damage dealt). */
	static final float KILL_CREDIT = 10.0f;

	protected BlockPos village = BlockPos.ZERO;
	protected boolean hasBell;
	protected String villageName = "";
	/** Ticks spent running (paused time does not count). */
	protected long runTicks;
	protected final Map<UUID, CrimeAI.Role> roles = new LinkedHashMap<>();
	private final Map<UUID, Float> contribution = new HashMap<>();
	private transient EventBossBar bar;
	private transient int sinceHint;

	protected CrimeEvent(UUID id) {
		super(id);
	}

	public abstract CrimeType crimeType();

	@Override
	public final String typeId() {
		return crimeType().typeId();
	}

	@Override
	public Component displayName() {
		return Component.translatable(crimeType().nameKey());
	}

	/** Sets the village before the crime is started. {@code bell} false = no real bell (a command away from villages). */
	public void setVillage(BlockPos centre, boolean bell) {
		this.village = centre.immutable();
		this.hasBell = bell;
		this.villageName = CrimeVillages.nameFor(centre);
	}

	public BlockPos village() {
		return village;
	}

	public String villageName() {
		return villageName;
	}

	public long runTicks() {
		return runTicks;
	}

	// ---------------------------------------------------------------- framework hooks

	@Override
	protected double presenceRadius() {
		return CrimeConfig.trigger().alertRadius + 32.0;
	}

	@Override
	protected double warnRadius() {
		return Double.MAX_VALUE / 4; // nobody is nagged for leaving a mugging
	}

	@Override
	protected int pauseAfterEmptyTicks() {
		return Math.max(20, CrimeConfig.trigger().abandonSeconds * 10);
	}

	@Override
	protected int failAfterEmptyTicks() {
		return Math.max(40, CrimeConfig.trigger().abandonSeconds * 20);
	}

	@Override
	protected void onPaused(ServerLevel level) {
		if (bar != null) {
			bar.clear(level);
		}
	}

	@Override
	protected void onResumed(ServerLevel level) {
	}

	// ---------------------------------------------------------------- the crime

	/** Spawns the crooks and sets the scene. May end the crime at once (no victim to be found, ...). */
	protected abstract void begin(ServerLevel level);

	/** One step of the crime (every half second while someone is around). Call {@link #succeed} / {@link #failCrime}. */
	protected abstract void tickCrime(ServerLevel level);

	/** Where the action is right now (the victim, the runner, the firebug...). */
	protected abstract Vec3 scene(ServerLevel level);

	/** The boss bar's text after "Mugging at Oakford - ". */
	protected abstract Component status(ServerLevel level);

	/** The boss bar's fill. */
	protected abstract float progress(ServerLevel level);

	protected abstract int timeLimitTicks();

	/** Time is up: by default the crooks get away. */
	protected void timeUp(ServerLevel level) {
		failCrime(level, "event.projecthero.crime.got_away");
	}

	/** A crook this crime owns went down. {@code killer} is the player who did it, if any. */
	protected void onCrookDown(ServerLevel level, Mob crook, ServerPlayer killer) {
	}

	/** Undo anything the crime did to the world that should not outlive it (victim tag, fires...). */
	protected void release(ServerLevel level) {
	}

	@Override
	protected final void onTick(ServerLevel level) {
		if (state() == EventState.PENDING) {
			setState(EventState.RUNNING);
			begin(level);
			if (state().finished()) {
				return;
			}
			announce(level);
			Crimes.fireBegin(level, this); // v0.16.2 City part B: City Cops are called to the scene
		}
		runTicks += Math.max(1, EventConfig.framework().tickIntervalTicks);
		tickCrime(level);
		if (state().finished()) {
			return;
		}
		if (runTicks >= timeLimitTicks()) {
			timeUp(level);
			if (state().finished()) {
				return;
			}
		}
		updateAlerts(level);
	}

	/** Tests and the debug command: one crime step now, whoever is (or isn't) around. */
	public void stepForTest(ServerLevel level) {
		onTick(level);
	}

	// ---------------------------------------------------------------- crooks

	/** Spawns one crook of {@code type} at {@code at}, owned by this crime, with {@code role}. */
	protected <T extends Mob> T spawnCrook(ServerLevel level, EntityType<T> type, BlockPos at, CrimeAI.Role role, Consumer<T> setup) {
		T mob = type.create(level);
		if (mob == null) {
			return null;
		}
		mob.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null);
		if (setup != null) {
			setup.accept(mob);
		}
		own(mob);
		mob.addTag(CrimeAI.CROOK_TAG);
		roles.put(mob.getUUID(), role);
		level.addFreshEntity(mob);
		CrimeAI.apply(mob, role);
		level.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + 0.8, mob.getZ(), 6, 0.25, 0.4, 0.25, 0.02);
		return mob;
	}

	/** Gives {@code mob} a loot sack to carry (shown in his off hand; it never drops by itself). */
	protected static void giveSack(Mob mob) {
		mob.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.CHEST));
		mob.setDropChance(EquipmentSlot.OFFHAND, 0.0f);
	}

	protected static boolean hasSack(Mob mob) {
		return mob.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.CHEST);
	}

	/** Called by the entity-load hook: a reloaded crook gets his role's goals back. */
	void reapply(Mob mob) {
		CrimeAI.Role role = roles.get(mob.getUUID());
		if (role != null) {
			CrimeAI.apply(mob, role);
		}
	}

	public List<Mob> crooks(ServerLevel level) {
		return liveOwnedMobs(level);
	}

	/** A crook gets away (or gives up): a puff of smoke and he's gone. */
	protected void vanish(ServerLevel level, Mob mob) {
		level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, mob.getX(), mob.getY() + 1.0, mob.getZ(), 8, 0.3, 0.5, 0.3, 0.01);
		disown(mob.getUUID());
		mob.discard();
	}

	/** True if a player hit {@code mob} in the last two seconds. */
	protected static boolean recentlyHitByPlayer(Mob mob) {
		LivingEntity by = mob.getLastHurtByMob();
		return by instanceof Player && mob.tickCount - mob.getLastHurtByMobTimestamp() < 40;
	}

	/** Moves {@code mob} a stretch further away from the village (re-issued every crime step). */
	protected void runAway(ServerLevel level, Mob mob, double speed) {
		double dx = mob.getX() - (village.getX() + 0.5);
		double dz = mob.getZ() - (village.getZ() + 0.5);
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 1.0e-3) {
			double a = level.random.nextDouble() * Math.PI * 2;
			dx = Math.cos(a);
			dz = Math.sin(a);
			len = 1.0;
		}
		dx /= len;
		dz /= len;
		if (mob.getNavigation().isDone() || mob.tickCount % 40 == 0) {
			// a little sideways wobble so a crook stuck on a wall tries another way round
			double wobble = (level.random.nextDouble() - 0.5) * (mob.getNavigation().isDone() ? 1.6 : 0.5);
			double cos = Math.cos(wobble);
			double sin = Math.sin(wobble);
			double rx = dx * cos - dz * sin;
			double rz = dx * sin + dz * cos;
			int x = Mth.floor(mob.getX() + rx * 12.0);
			int z = Mth.floor(mob.getZ() + rz * 12.0);
			if (level.isLoaded(new BlockPos(x, mob.getBlockY(), z))) {
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				mob.getNavigation().moveTo(x + 0.5, y, z + 0.5, speed);
			}
		}
	}

	protected double horizontalFromVillage(Vec3 pos) {
		double dx = pos.x - (village.getX() + 0.5);
		double dz = pos.z - (village.getZ() + 0.5);
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** A few village goods: what a loot sack holds (and spills when its carrier is put down). */
	public static List<ItemStack> rollSack(net.minecraft.util.RandomSource random) {
		List<ItemStack> out = new ArrayList<>();
		out.add(new ItemStack(Items.EMERALD, 1 + random.nextInt(3)));
		out.add(new ItemStack(Items.BREAD, 1 + random.nextInt(4)));
		out.add(switch (random.nextInt(6)) {
			case 0 -> new ItemStack(Items.IRON_INGOT, 1 + random.nextInt(2));
			case 1 -> new ItemStack(Items.WHEAT, 4 + random.nextInt(5));
			case 2 -> new ItemStack(Items.CARROT, 2 + random.nextInt(4));
			case 3 -> new ItemStack(Items.POTATO, 2 + random.nextInt(4));
			case 4 -> new ItemStack(Items.BOOK, 1 + random.nextInt(2));
			default -> new ItemStack(Items.GOLD_INGOT, 1);
		});
		return out;
	}

	/** Spills a loot sack at {@code mob}'s feet. */
	protected void spillSack(ServerLevel level, Mob mob) {
		for (ItemStack stack : rollSack(level.random)) {
			ItemEntity item = new ItemEntity(level, mob.getX(), mob.getY() + 0.5, mob.getZ(), stack);
			item.setDefaultPickUpDelay();
			level.addFreshEntity(item);
		}
		mob.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.NEUTRAL, 1.0f, 0.9f);
		tellNearby(level, Component.translatable("event.projecthero.crime.recovered").withStyle(ChatFormatting.GREEN), true);
	}

	// ---------------------------------------------------------------- who helped

	public void addContribution(UUID player, float amount) {
		if (amount > 0) {
			contribution.merge(player, amount, Float::sum);
		}
	}

	public float contributionOf(UUID player) {
		return contribution.getOrDefault(player, 0f);
	}

	/** Called from the death hook for every crook this crime owns. */
	void crookDown(ServerLevel level, Mob crook, DamageSource source) {
		ServerPlayer killer = source.getEntity() instanceof ServerPlayer p ? p : null;
		if (killer != null) {
			addContribution(killer.getUUID(), KILL_CREDIT);
		}
		onCrookDown(level, crook, killer);
	}

	/** The helper who did the most, or {@code null}. */
	public UUID topHelper() {
		UUID best = null;
		float most = 0f;
		for (Map.Entry<UUID, Float> e : contribution.entrySet()) {
			if (e.getValue() > most) {
				most = e.getValue();
				best = e.getKey();
			}
		}
		return best;
	}

	// ---------------------------------------------------------------- the end

	/** The crime is stopped. {@code share} (0-1] scales the emeralds and XP (a break-in where some got away). */
	protected void succeed(ServerLevel level, double share) {
		if (state().finished()) {
			return;
		}
		payRewards(level, Mth.clamp(share, 0.1, 1.0));
		complete(level);
	}

	protected void failCrime(ServerLevel level, String key) {
		if (state().finished()) {
			return;
		}
		tellNearby(level, Component.translatable(key, displayName(), villageName).withStyle(ChatFormatting.RED), false);
		abort(level); // the line above is the message; abort skips the framework's own broadcast
	}

	@Override
	protected void onFinished(ServerLevel level, boolean success) {
		if (bar != null) {
			bar.clear(level);
		}
		for (Mob m : liveOwnedMobs(level)) {
			level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, m.getX(), m.getY() + 1.0, m.getZ(), 8, 0.3, 0.5, 0.3, 0.01);
		}
		release(level);
	}

	@Override
	protected void onAborted(ServerLevel level) {
		onFinished(level, false);
	}

	private void payRewards(ServerLevel level, double share) {
		CrimeConfig.Rewards r = CrimeConfig.rewards();
		UUID top = topHelper();
		List<ServerPlayer> helpers = new ArrayList<>();
		for (UUID id : contribution.keySet()) {
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
			if (p != null && p.isAlive()) {
				helpers.add(p);
			}
		}
		if (helpers.isEmpty()) {
			tellNearby(level, Component.translatable("event.projecthero.crime.stopped_by_village", displayName(), villageName)
					.withStyle(ChatFormatting.GRAY), false);
			return;
		}
		List<Villager> villagers = level.getEntitiesOfClass(Villager.class, new AABB(village).inflate(48.0), Villager::isAlive);
		for (ServerPlayer p : helpers) {
			boolean isTop = p.getUUID().equals(top);
			int lo = Math.min(r.emeraldsMin, r.emeraldsMax);
			int hi = Math.max(r.emeraldsMin, r.emeraldsMax);
			int emeralds = Math.max(1, (int) Math.round((lo + level.random.nextInt(hi - lo + 1)) * share));
			if (isTop) {
				emeralds += r.topHelperBonusEmeralds;
			}
			give(p, new ItemStack(Items.EMERALD, emeralds));
			p.giveExperiencePoints(Math.max(1, (int) Math.round(r.experience * share)));
			int gossip = isTop ? r.gossipTopHelper : r.gossipHelpers;
			if (gossip > 0) {
				for (Villager v : villagers) {
					v.getGossips().add(p.getUUID(), GossipType.MINOR_POSITIVE, gossip);
				}
			}
			if (isTop && r.heroOfTheVillageSeconds > 0) {
				p.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, r.heroOfTheVillageSeconds * 20, 0, false, true, true));
			}
			p.sendSystemMessage(Component.translatable(isTop ? "event.projecthero.crime.thanks_top" : "event.projecthero.crime.thanks",
					displayName(), villageName, emeralds).withStyle(ChatFormatting.GREEN));
			p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket(
					Component.translatable("event.projecthero.crime.stopped", displayName()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
		}
		Vec3 at = scene(level);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.5f, 1.0f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.2f);
	}

	private static void give(ServerPlayer p, ItemStack stack) {
		if (!p.getInventory().add(stack) && !stack.isEmpty()) {
			p.drop(stack, false);
		}
	}

	// ---------------------------------------------------------------- alerts

	/** Players within the alert radius of the crime right now. */
	protected List<ServerPlayer> nearby(ServerLevel level) {
		Vec3 at = scene(level);
		double r = CrimeConfig.trigger().alertRadius;
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && p.distanceToSqr(at.x, p.getY(), at.z) <= r * r) {
				out.add(p);
			}
		}
		return out;
	}

	protected void tellNearby(ServerLevel level, Component message, boolean actionBar) {
		for (ServerPlayer p : nearby(level)) {
			p.displayClientMessage(message, actionBar);
		}
	}

	private Component title(ServerLevel level) {
		return Component.translatable("event.projecthero.crime.title", displayName(), villageName);
	}

	/** The opening: chat line + direction, a scream from that way, the bell. */
	private void announce(ServerLevel level) {
		Vec3 at = scene(level);
		for (ServerPlayer p : nearby(level)) {
			p.sendSystemMessage(Component.translatable("event.projecthero.crime.alert", title(level),
					Component.translatable("event.projecthero.crime." + crimeType().id() + ".desc"),
					CrimeVillages.hint(p.getX(), p.getZ(), at.x, at.z)).withStyle(ChatFormatting.RED));
			playToward(p, at, SoundEvents.VILLAGER_HURT, 1.4f);
			playToward(p, at, SoundEvents.VILLAGER_NO, 1.2f);
		}
		if (hasBell) {
			CrimeVillages.ringBell(level, village);
		}
	}

	/** Plays {@code sound} to {@code p} from 12 blocks along the line to the crime, so it is heard from that side. */
	protected static void playToward(ServerPlayer p, Vec3 at, SoundEvent sound, float pitch) {
		Vec3 eye = p.getEyePosition();
		Vec3 d = at.subtract(eye);
		double len = d.length();
		Vec3 from = len < 12.0 ? at : eye.add(d.scale(12.0 / len));
		p.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), SoundSource.NEUTRAL, from.x, from.y, from.z, 1.0f, pitch,
				p.getRandom().nextLong()));
	}

	private void updateAlerts(ServerLevel level) {
		Vec3 at = scene(level);
		if (bar == null) {
			bar = new EventBossBar(id(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS, false);
		}
		Component name = title(level).copy().append(Component.literal(" - ").withStyle(ChatFormatting.GRAY)).append(status(level));
		bar.update(level, BlockPos.containing(at), CrimeConfig.trigger().alertRadius, name.copy().withStyle(ChatFormatting.RED),
				progress(level));
		sinceHint += Math.max(1, EventConfig.framework().tickIntervalTicks);
		if (sinceHint >= 20) {
			sinceHint = 0;
			for (ServerPlayer p : nearby(level)) {
				if (p.distanceToSqr(at.x, p.getY(), at.z) > 12 * 12) {
					p.displayClientMessage(Component.translatable("event.projecthero.crime.in_progress", title(level),
							CrimeVillages.hint(p.getX(), p.getZ(), at.x, at.z)).withStyle(ChatFormatting.RED), true);
				}
			}
		}
	}

	// ---------------------------------------------------------------- save

	@Override
	protected void saveExtra(CompoundTag tag) {
		tag.put("CrimeVillage", NbtUtils.writeBlockPos(village));
		tag.putBoolean("CrimeBell", hasBell);
		tag.putString("CrimeVillageName", villageName);
		tag.putLong("CrimeRun", runTicks);
		ListTag list = new ListTag();
		for (Map.Entry<UUID, CrimeAI.Role> e : roles.entrySet()) {
			CompoundTag t = new CompoundTag();
			t.putUUID("Id", e.getKey());
			t.putString("Role", e.getValue().name());
			list.add(t);
		}
		tag.put("CrimeRoles", list);
		ListTag help = new ListTag();
		for (Map.Entry<UUID, Float> e : contribution.entrySet()) {
			CompoundTag t = new CompoundTag();
			t.putUUID("Id", e.getKey());
			t.putFloat("Amount", e.getValue());
			help.add(t);
		}
		tag.put("CrimeHelp", help);
		saveCrime(tag);
	}

	@Override
	protected void loadExtra(CompoundTag tag) {
		village = NbtUtils.readBlockPos(tag, "CrimeVillage").orElse(center() == null ? BlockPos.ZERO : center());
		hasBell = tag.getBoolean("CrimeBell");
		villageName = tag.getString("CrimeVillageName");
		runTicks = tag.getLong("CrimeRun");
		roles.clear();
		ListTag list = tag.getList("CrimeRoles", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag t = list.getCompound(i);
			try {
				roles.put(t.getUUID("Id"), CrimeAI.Role.valueOf(t.getString("Role")));
			} catch (IllegalArgumentException ignored) {
				// unknown role from a newer save: leave that crook on his default AI
			}
		}
		contribution.clear();
		ListTag help = tag.getList("CrimeHelp", Tag.TAG_COMPOUND);
		for (int i = 0; i < help.size(); i++) {
			CompoundTag t = help.getCompound(i);
			contribution.put(t.getUUID("Id"), t.getFloat("Amount"));
		}
		loadCrime(tag);
	}

	protected void saveCrime(CompoundTag tag) {
	}

	protected void loadCrime(CompoundTag tag) {
	}
}
