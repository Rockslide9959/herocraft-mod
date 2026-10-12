package com.projecthero.mod.ultron.contagion.entity;

import java.util.List;

import com.projecthero.mod.ultron.UltronCombat;
import com.projecthero.mod.ultron.UltronFx;
import com.projecthero.mod.ultron.contagion.Contagion;
import com.projecthero.mod.ultron.contagion.ContagionConfig;
import com.projecthero.mod.ultron.contagion.ContagionState;
import com.projecthero.mod.ultron.contagion.ContagionUnits;
import com.projecthero.mod.ultron.contagion.NaniteCorruption;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: an <b>Ultron Nexus</b> -- one of the Contagion's infection hubs. A 9-block spire of black metal and red veins
 * that grows out of the ground over {@link ContagionConfig.Nexus#growTicks} (a red beam up to the clouds meanwhile), then
 * spreads Nanite Corruption ({@link NaniteCorruption}) out to {@link ContagionConfig.Nexus#maxRadius} blocks.
 *
 * <p><b>Taking one down</b>: its red shield blocks all damage until it is <b>Overridden</b>. Use an Override Spike on it
 * ({@link #startCapture}): a {@link ContagionConfig.Nexus#captureRadius}-block circle appears and its timer runs only
 * while a living player stands in it (twice as fast with two or more; a Mind Stone in someone's off hand makes it 40%
 * faster). Ultron throws waves at the circle the whole time, a Heavy at 50% and an Ultron Prime echo at 80%. When the
 * timer completes the shield flips blue and the spire can be broken (800 health). Its fall ({@link Contagion#onNexusDestroyed})
 * reverts the corruption, frees what it held, lowers the Saturation and hands JARVIS a Trace.
 *
 * <p>Its corruption record lives in {@link ContagionState} under this entity's UUID; with no record the spire removes
 * itself (the Contagion was stopped).
 */
public class UltronNexusEntity extends ContagionStructureEntity {
	public static final byte SHIELD_RED = 0;
	public static final byte SHIELD_CAPTURING = 1;
	public static final byte SHIELD_BLUE = 2;

	private static final EntityDataAccessor<Long> DATA_BORN = SynchedEntityData.defineId(UltronNexusEntity.class, EntityDataSerializers.LONG);
	private static final EntityDataAccessor<Byte> DATA_SHIELD = SynchedEntityData.defineId(UltronNexusEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Float> DATA_CAPTURE = SynchedEntityData.defineId(UltronNexusEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> DATA_RADIUS = SynchedEntityData.defineId(UltronNexusEntity.class, EntityDataSerializers.INT);

	private int spreadTicks;
	private float captureTicks;
	private long lastWave;
	private boolean heavySent;
	private boolean echoSent;
	private int capturePlayers;
	private boolean captureMind;

	public UltronNexusEntity(EntityType<? extends UltronNexusEntity> type, Level level) {
		super(type, level);
		this.xpReward = 60;
	}

	public static AttributeSupplier.Builder createAttributes() {
		ContagionConfig.Nexus cfg = ContagionConfig.nexus();
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, cfg.health)
				.add(Attributes.ARMOR, cfg.armor)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.MOVEMENT_SPEED, 0.0)
				.add(Attributes.FOLLOW_RANGE, 1.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_BORN, 0L);
		builder.define(DATA_SHIELD, SHIELD_RED);
		builder.define(DATA_CAPTURE, 0f);
		builder.define(DATA_RADIUS, 0);
	}

	@Override
	public String modelName() {
		return "ultron_nexus";
	}

	/** Called once when the Contagion plants it: its health for Ultron {@code version} and the growth clock. */
	public void configure(int version) {
		double hp = ContagionConfig.nexus().health * ContagionConfig.unitHealthScale(version);
		getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
		setHealth((float) hp);
		entityData.set(DATA_BORN, level().getGameTime());
	}

	/** 0..1: how far it has grown out of the ground. */
	public float growth(float partialTick) {
		long born = entityData.get(DATA_BORN);
		if (born == 0L) {
			return 1f;
		}
		return Math.max(0f, Math.min(1f, (level().getGameTime() - born + partialTick) / (float) Math.max(1, ContagionConfig.nexus().growTicks)));
	}

	public boolean grown() {
		return growth(0f) >= 1f;
	}

	public byte shield() {
		return entityData.get(DATA_SHIELD);
	}

	public boolean overridden() {
		return shield() == SHIELD_BLUE;
	}

	public boolean capturing() {
		return shield() == SHIELD_CAPTURING;
	}

	/** 0..1 through the Override capture. */
	public float captureProgress() {
		return entityData.get(DATA_CAPTURE);
	}

	/** The corruption's current radius (synced for the client's ground glow). */
	public int corruptionRadius() {
		return entityData.get(DATA_RADIUS);
	}

	public int capturePlayers() {
		return capturePlayers;
	}

	public boolean captureMindStone() {
		return captureMind;
	}

	/** Seconds left on the capture at the current rate (for the HUD). */
	public int captureSecondsLeft() {
		ContagionConfig.Nexus cfg = ContagionConfig.nexus();
		float left = Math.max(0f, cfg.captureTicks - captureTicks);
		double rate = rate(capturePlayers, captureMind);
		return rate <= 0 ? (int) Math.ceil(left / 20f) : (int) Math.ceil(left / rate / 20.0);
	}

	/** Capture-timer ticks per game tick for {@code players} players inside, with or without a Mind Stone. */
	public static double rate(int players, boolean mindStone) {
		ContagionConfig.Nexus cfg = ContagionConfig.nexus();
		if (players <= 0) {
			return 0.0;
		}
		double r = players >= 2 ? cfg.groupSpeed : 1.0;
		return mindStone ? r * (1.0 + cfg.mindStoneBonus) : r;
	}

	// ---------------------------------------------------------------- tick

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(level() instanceof ServerLevel server) || isDeadOrDying()) {
			return;
		}
		ContagionState st = ContagionState.get(server);
		ContagionState.NexusRecord rec = st.nexus.get(getUUID());
		if (rec == null) {
			discard(); // the Contagion was stopped, or this record was already purged
			return;
		}
		ContagionConfig.Nexus cfg = ContagionConfig.nexus();
		float g = growth(0f);
		if (g < 1f) {
			if (tickCount % 3 == 0) {
				server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.3, getZ(), 2, 1.2, 0.1, 1.2, 0.01);
				server.sendParticles(UltronFx.RED_SMALL, getX(), getY() + g * 9.0, getZ(), 3, 0.8, 0.3, 0.8, 0.02);
			}
			if (tickCount % 40 == 0) {
				server.playSound(null, getX(), getY(), getZ(), SoundEvents.DEEPSLATE_BREAK, SoundSource.HOSTILE, 1.5f, 0.5f);
			}
		} else {
			spreadTicks++;
			int radius = Math.min(cfg.maxRadius, 1 + spreadTicks / Math.max(1, cfg.radiusGrowTicks));
			if (radius != rec.radius) {
				rec.radius = radius;
				st.setDirty();
			}
			if (entityData.get(DATA_RADIUS) != radius) {
				entityData.set(DATA_RADIUS, radius);
			}
			if (ContagionConfig.get().terrainCorruption) {
				int share = Math.max(1, cfg.blocksPerTick / Math.max(1, st.nexus.size()));
				NaniteCorruption.spread(server, st, rec, cfg.columnsPerTick, share);
			} else if (tickCount % 10 == 0) {
				particlesOnly(server, radius);
			}
		}
		if (tickCount % 4 == 0) {
			int colour = overridden() ? 1 : 0;
			server.sendParticles(colour == 1 ? UltronFx.CYAN : UltronFx.RED, getX(), getY() + 7.6 * g, getZ(), 1, 0.3, 0.3, 0.3, 0.0);
		}
		if (tickCount % 60 == 0) {
			server.playSound(null, getX(), getY() + 6, getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 1.2f, 0.45f);
		}
		if (capturing()) {
			tickCapture(server, st, rec);
		}
	}

	/** The particles-only corruption (config {@code terrainCorruption = false}): a red haze on the ground. */
	private void particlesOnly(ServerLevel server, int radius) {
		for (int i = 0; i < 6; i++) {
			double a = random.nextDouble() * Math.PI * 2;
			double r = random.nextDouble() * radius;
			double x = getX() + Math.cos(a) * r;
			double z = getZ() + Math.sin(a) * r;
			int y = server.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
			server.sendParticles(UltronFx.RED_SMALL, x, y + 0.2, z, 1, 0.2, 0.05, 0.2, 0.0);
		}
	}

	// ---------------------------------------------------------------- the Override capture

	/** {@code player} drives an Override Spike into it. False (with a message) if it can't be captured now. */
	public boolean startCapture(ServerPlayer player) {
		if (!(level() instanceof ServerLevel server)) {
			return false;
		}
		if (!grown()) {
			player.displayClientMessage(Component.translatable("message.projecthero.contagion.spike_growing").withStyle(ChatFormatting.RED), true);
			return false;
		}
		if (shield() != SHIELD_RED) {
			player.displayClientMessage(Component.translatable(overridden() ? "message.projecthero.contagion.spike_done"
					: "message.projecthero.contagion.spike_running").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		entityData.set(DATA_SHIELD, SHIELD_CAPTURING);
		captureTicks = 0f;
		entityData.set(DATA_CAPTURE, 0f);
		lastWave = server.getGameTime() - ContagionConfig.nexus().waveIntervalTicks + 60;
		heavySent = false;
		echoSent = false;
		server.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 1.5f, 0.6f);
		server.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2.0f, 1.3f);
		UltronFx.beam(server, player.position().add(0, 1, 0), position().add(0, 4, 0), UltronFx.TETHER, 20);
		Contagion.sayNear(server, position(), "capture_start", 64);
		for (ServerPlayer p : server.players()) {
			if (p.distanceToSqr(this) < 64 * 64) {
				com.projecthero.mod.ironman.JarvisDialogue.speak(p, "contagion_capture");
			}
		}
		return true;
	}

	private void tickCapture(ServerLevel server, ContagionState st, ContagionState.NexusRecord rec) {
		ContagionConfig.Nexus cfg = ContagionConfig.nexus();
		List<Player> inside = playersInCircle(server);
		capturePlayers = inside.size();
		captureMind = false;
		for (Player p : inside) {
			if (Contagion.holdsMindStone(p)) {
				captureMind = true;
			}
		}
		captureTicks += (float) rate(capturePlayers, captureMind);
		float frac = Math.min(1f, captureTicks / Math.max(1, cfg.captureTicks));
		entityData.set(DATA_CAPTURE, frac);
		long now = server.getGameTime();
		if (tickCount % 5 == 0) {
			Vec3 c = position().add(0, 0.2, 0);
			UltronFx.ring(server, capturePlayers > 0 ? UltronFx.CYAN : UltronFx.RED_SMALL, c, cfg.captureRadius, 56);
			int lit = (int) (56 * frac);
			for (int i = 0; i < lit; i += 2) {
				double a = i * Math.PI * 2 / 56.0 - Math.PI / 2;
				server.sendParticles(UltronFx.CYAN, c.x + Math.cos(a) * (cfg.captureRadius - 0.6), c.y + 0.3, c.z + Math.sin(a) * (cfg.captureRadius - 0.6),
						1, 0, 0, 0, 0);
			}
		}
		boolean anyNear = !UltronCombat.playersNear(server, position(), 48).isEmpty();
		if (anyNear && now - lastWave >= cfg.waveIntervalTicks) {
			lastWave = now;
			ContagionUnits.captureWave(server, this, st.version);
		}
		if (!heavySent && frac >= 0.5f) {
			heavySent = true;
			ContagionUnits.spawnHeavy(server, this, st.version);
			Contagion.sayNear(server, position(), "capture_heavy", 64);
		}
		if (!echoSent && frac >= 0.8f) {
			echoSent = true;
			ContagionUnits.spawnPrimeEcho(server, this, st.version);
			Contagion.sayNear(server, position(), "capture_echo", 64);
		}
		if (frac >= 1f) {
			entityData.set(DATA_SHIELD, SHIELD_BLUE);
			rec.captured = true;
			st.setDirty();
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 3.0f, 0.8f);
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.HOSTILE, 2.0f, 1.2f);
			UltronFx.forced(server, UltronFx.CYAN, getX(), getY() + 5, getZ(), 80, 1.5, 3.0, 1.5, 0.1);
			for (ServerPlayer p : server.players()) {
				if (p.distanceToSqr(this) < 64 * 64) {
					p.displayClientMessage(Component.translatable("message.projecthero.contagion.overridden").withStyle(ChatFormatting.AQUA,
							ChatFormatting.BOLD), true);
					com.projecthero.mod.ironman.JarvisDialogue.speak(p, "contagion_overridden");
				}
			}
		}
	}

	/** Living players standing in the capture circle (horizontal radius, within 8 blocks up or down). */
	public List<Player> playersInCircle(ServerLevel server) {
		double r = ContagionConfig.nexus().captureRadius;
		List<Player> out = new java.util.ArrayList<>();
		for (Player p : server.players()) {
			double dx = p.getX() - getX(), dz = p.getZ() - getZ();
			if (UltronCombat.isFighter(p) && dx * dx + dz * dz <= r * r && Math.abs(p.getY() - getY()) <= 8.0) {
				out.add(p);
			}
		}
		return out;
	}

	/** Test hook: jump the capture straight to {@code frac}. */
	public void setCaptureForTest(float ticks) {
		captureTicks = ticks;
	}

	public float captureTicks() {
		return captureTicks;
	}

	/** Test / command hook: skip the capture. */
	public void forceOverride() {
		entityData.set(DATA_SHIELD, SHIELD_BLUE);
		entityData.set(DATA_CAPTURE, 1f);
		if (level() instanceof ServerLevel server) {
			ContagionState.NexusRecord rec = ContagionState.get(server).nexus.get(getUUID());
			if (rec != null) {
				rec.captured = true;
			}
		}
	}

	/** Test hook: finish growing now. */
	public void finishGrowing() {
		entityData.set(DATA_BORN, level().getGameTime() - ContagionConfig.nexus().growTicks - 1);
	}

	// ---------------------------------------------------------------- damage and death

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (!overridden() && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			if (level() instanceof ServerLevel server && tickCount % 4 == 0) {
				Vec3 at = source.getSourcePosition() != null ? source.getSourcePosition() : position().add(0, 3, 0);
				Vec3 hit = position().add(0, Math.min(8, Math.max(1, at.y - getY())), 0).add(at.subtract(position()).multiply(1, 0, 1).normalize().scale(1.6));
				server.sendParticles(UltronFx.RED, hit.x, hit.y, hit.z, 6, 0.3, 0.3, 0.3, 0.02);
				server.playSound(null, getX(), getY() + 3, getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.0f, 0.6f);
				if (source.getEntity() instanceof ServerPlayer p) {
					p.displayClientMessage(Component.translatable("message.projecthero.contagion.nexus_shielded").withStyle(ChatFormatting.RED), true);
				}
			}
			return false;
		}
		return super.hurt(source, amount);
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (level() instanceof ServerLevel server) {
			ContagionConfig.Nexus cfg = ContagionConfig.nexus();
			int scrap = cfg.scrapMin + random.nextInt(Math.max(1, cfg.scrapMax - cfg.scrapMin + 1));
			spawnAtLocation(new net.minecraft.world.item.ItemStack(com.projecthero.mod.ultron.contagion.item.ContagionItems.ULTRON_SCRAP, scrap), 2f);
			spawnAtLocation(new net.minecraft.world.item.ItemStack(com.projecthero.mod.ultron.contagion.item.ContagionItems.NANITE_SAMPLE, 1), 2f);
			Contagion.onNexusDestroyed(server, this);
		}
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putLong("Born", entityData.get(DATA_BORN));
		tag.putByte("Shield", shield());
		tag.putInt("SpreadTicks", spreadTicks);
		tag.putFloat("CaptureTicks", captureTicks);
		tag.putBoolean("HeavySent", heavySent);
		tag.putBoolean("EchoSent", echoSent);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		entityData.set(DATA_BORN, tag.getLong("Born"));
		entityData.set(DATA_SHIELD, tag.getByte("Shield"));
		spreadTicks = tag.getInt("SpreadTicks");
		captureTicks = tag.getFloat("CaptureTicks");
		heavySent = tag.getBoolean("HeavySent");
		echoSent = tag.getBoolean("EchoSent");
		entityData.set(DATA_CAPTURE, Math.min(1f, captureTicks / Math.max(1, ContagionConfig.nexus().captureTicks)));
		setNoGravity(true);
	}
}
