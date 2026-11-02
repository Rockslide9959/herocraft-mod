package com.projecthero.mod.omnitrix.pod;

import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixAttachments;
import com.projecthero.mod.omnitrix.OmnitrixConfig;
import com.projecthero.mod.omnitrix.OmnitrixDamage;
import com.projecthero.mod.omnitrix.OmnitrixSounds;
import com.projecthero.mod.omnitrix.OmnitrixWorld;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.item.OmnitrixItems;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: the Containment Pod's machine -- its fuel and the extraction it runs.
 *
 * <h2>The extraction (40 s)</h2>
 * <ul>
 *   <li><b>Start</b> ({@link #tryStart}): the wearer stands inside, as a human, and right-clicks it; it burns
 *       {@link OmnitrixConfig#FUEL_PER_EXTRACTION} fuel.</li>
 *   <li><b>Damage</b>: {@link OmnitrixConfig#EXTRACTION_PULSES} -- {@code projecthero:omnitrix_extraction} pulses that ignore
 *       armour, enchantments and Resistance and never let a totem pop, but Absorption soaks them and healing works. Tuned so a
 *       full-health player who just ate an enchanted golden apple ends on 2 hearts (see the config's calibration note).</li>
 *   <li><b>The Omnitrix fights back</b>: random forced transforms the wearer is snapped back out of (shown only -- the body
 *       never changes, so the calibration holds), energy surges that jolt them about (no damage beyond the schedule), and the
 *       red SELF-DESTRUCT warning on their HUD (the synced {@code EXTRACTION} attachment).</li>
 *   <li><b>Failure</b>: dying (the watch stays: it is kept through death) or stepping out (the field lashes out:
 *       {@link OmnitrixConfig#STEP_OUT_DAMAGE}, the watch stays).</li>
 *   <li><b>Success</b>: the watch pops off as an item at the top of the pod and the power is gone. Whoever puts it on next is
 *       locked in the same way.</li>
 * </ul>
 */
public class OmnitrixPodBlockEntity extends BlockEntity {
	private static final DustParticleOptions GREEN = new DustParticleOptions(new Vector3f(0.35f, 1.0f, 0.3f), 0.8f);
	private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(1.0f, 0.15f, 0.1f), 0.8f);

	private int fuel;
	@Nullable
	private UUID subject;
	/** The subject itself while it lives (a mock / fake player is not always in the player list). */
	private java.lang.ref.WeakReference<ServerPlayer> subjectRef = new java.lang.ref.WeakReference<>(null);
	private int ticks;
	private int nextPulse;
	private long nextSurge;
	private long nextFlicker;
	/** v0.17.2: the running job is a recalibration (no damage) rather than an extraction. */
	private boolean recal;
	/** v0.17.2: the Recalibration Chip a running recalibration holds (handed back if it fails). */
	private ItemStack chip = ItemStack.EMPTY;

	public OmnitrixPodBlockEntity(BlockPos pos, BlockState state) {
		super(OmnitrixPodBlocks.POD_BE, pos, state);
	}

	public int fuel() {
		return fuel;
	}

	public void addFuel(int n) {
		fuel = Math.min(OmnitrixConfig.MAX_FUEL, fuel + n);
		setChanged();
		sync();
	}

	public boolean running() {
		return subject != null;
	}

	/** Ticks the running extraction has done (0 when idle). */
	public int ticks() {
		return ticks;
	}

	@Nullable
	public UUID subject() {
		return subject;
	}

	/** The pod's centre at floor level (on the base plate). */
	public Vec3 centre() {
		return Vec3.atBottomCenterOf(worldPosition).add(0, 2 / 16.0, 0);
	}

	public boolean inside(ServerPlayer p) {
		Vec3 c = centre();
		double dx = p.getX() - c.x;
		double dz = p.getZ() - c.z;
		return dx * dx + dz * dz <= OmnitrixConfig.POD_RADIUS * OmnitrixConfig.POD_RADIUS && p.getY() > c.y - 0.6 && p.getY() < c.y + 2.2;
	}

	/** Right-click from inside: every check, then {@link #start}. */
	public boolean tryStart(ServerPlayer p) {
		if (running()) {
			p.displayClientMessage(msg("running", ChatFormatting.RED), true);
			return false;
		}
		if (!Omnitrix.hasPower(p)) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix_pod.fuel", fuel, OmnitrixConfig.FUEL_PER_EXTRACTION)
					.withStyle(ChatFormatting.GREEN), true);
			return false;
		}
		if (!inside(p)) {
			p.displayClientMessage(msg("step_inside", ChatFormatting.YELLOW), true);
			return false;
		}
		if (Omnitrix.transformed(p)) {
			p.displayClientMessage(msg("turn_back", ChatFormatting.YELLOW), true);
			return false;
		}
		if (fuel < OmnitrixConfig.FUEL_PER_EXTRACTION) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix_pod.needs_fuel", fuel, OmnitrixConfig.FUEL_PER_EXTRACTION)
					.withStyle(ChatFormatting.RED), true);
			return false;
		}
		return start(p);
	}

	/** v0.17.2: the job running is a recalibration. */
	public boolean recalibrating() {
		return running() && recal;
	}

	/**
	 * v0.17.2: right-click from inside holding a Recalibration Chip -- every check (the prototype worn, as a human, inside, broken in
	 * with {@link OmnitrixConfig#RECAL_MIN_TRANSFORMS} transformations, {@link OmnitrixConfig#FUEL_PER_RECAL} fuel), then
	 * {@link #startRecalibration}. The chip is taken.
	 */
	public boolean tryRecalibrate(ServerPlayer p, ItemStack held) {
		if (running()) {
			p.displayClientMessage(msg("running", ChatFormatting.RED), true);
			return false;
		}
		if (!Omnitrix.hasPower(p)) {
			p.displayClientMessage(msg("recal_no_watch", ChatFormatting.YELLOW), true);
			return false;
		}
		if (Omnitrix.recalibrated(p)) {
			p.displayClientMessage(msg("recal_already", ChatFormatting.GRAY), true);
			return false;
		}
		if (!inside(p)) {
			p.displayClientMessage(msg("step_inside", ChatFormatting.YELLOW), true);
			return false;
		}
		if (Omnitrix.transformed(p)) {
			p.displayClientMessage(msg("turn_back", ChatFormatting.YELLOW), true);
			return false;
		}
		int done = Omnitrix.prototypeTransforms(p);
		if (done < OmnitrixConfig.RECAL_MIN_TRANSFORMS) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix_pod.recal_untrained", done, OmnitrixConfig.RECAL_MIN_TRANSFORMS)
					.withStyle(ChatFormatting.YELLOW), true);
			return false;
		}
		if (fuel < OmnitrixConfig.FUEL_PER_RECAL) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix_pod.needs_fuel", fuel, OmnitrixConfig.FUEL_PER_RECAL)
					.withStyle(ChatFormatting.RED), true);
			return false;
		}
		ItemStack taken = held.copyWithCount(1);
		if (!startRecalibration(p, taken)) {
			return false;
		}
		if (!p.getAbilities().instabuild) {
			held.shrink(1);
		}
		return true;
	}

	/**
	 * v0.17.2: starts the recalibration on {@code p} (no checks beyond "wears the prototype, idle": tests call this). 30 s, no
	 * damage: the wearer is held in the field, the dial turns on its own through the new aliens (the user's dial-turn sound) and
	 * they flicker on the wearer, the HUD shows the green RECALIBRATING box; at the end {@link Omnitrix#recalibrate}.
	 */
	public boolean startRecalibration(ServerPlayer p, ItemStack chipStack) {
		if (running() || !Omnitrix.hasPower(p) || Omnitrix.recalibrated(p) || Omnitrix.transformed(p) || !(level instanceof ServerLevel server)) {
			return false;
		}
		if (Omnitrix.selecting(p)) {
			Omnitrix.deactivate(p);
		}
		fuel = Math.max(0, fuel - OmnitrixConfig.FUEL_PER_RECAL);
		subject = p.getUUID();
		subjectRef = new java.lang.ref.WeakReference<>(p);
		recal = true;
		chip = chipStack == null ? ItemStack.EMPTY : chipStack;
		ticks = 0;
		nextPulse = 0;
		p.setAttached(OmnitrixAttachments.EXTRACTION, -1);
		Vec3 c = centre();
		server.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.6f, 1.2f);
		OmnitrixSounds.play(server, c.add(0, 1, 0), OmnitrixSounds.RECAL_CORE_UP, 1.0f, 1.0f, SoundSource.BLOCKS);
		p.displayClientMessage(msg("recal_start", ChatFormatting.GREEN).withStyle(ChatFormatting.BOLD), false);
		setChanged();
		sync();
		return true;
	}

	/** One tick of a recalibration (the subject is alive, here and still wearing it). */
	private void recalTick(ServerLevel server, ServerPlayer p) {
		if (!inside(p)) {
			fail(p, "recal_failed_step_out");
			return;
		}
		if (Omnitrix.transformed(p)) {
			fail(p, "recal_failed");
			return;
		}
		ticks++;
		p.setAttached(OmnitrixAttachments.EXTRACTION, -1 - ticks);
		Vec3 c = centre();
		if (ticks % OmnitrixConfig.RECAL_DIAL_TICKS == 0 && ticks < OmnitrixConfig.RECAL_TICKS - 20) {
			OmnitrixSounds.play(server, p.position(), OmnitrixSounds.RECAL_DIAL, 1.0f, 1.0f, SoundSource.PLAYERS);
			List<Aliens.Alien> pool = new java.util.ArrayList<>();
			for (Aliens.Alien a : Aliens.recalibrated()) {
				if (Aliens.playable(a.id())) {
					pool.add(a);
				}
			}
			if (!pool.isEmpty() && (ticks / OmnitrixConfig.RECAL_DIAL_TICKS) % 2 == 0) {
				int step = ticks / (OmnitrixConfig.RECAL_DIAL_TICKS * 2);
				Omnitrix.flicker(p, pool.get(step % pool.size()).id(), 16);
			}
		}
		if (ticks % 40 == 1) {
			OmnitrixSounds.play(server, c.add(0, 1, 0), OmnitrixSounds.RECAL_HUM, 1.0f, 0.9f + ticks / 1200f, SoundSource.BLOCKS);
		}
		// hold the wearer in the middle of the field
		double dx = c.x - p.getX();
		double dz = c.z - p.getZ();
		if (dx * dx + dz * dz > 0.04) {
			p.push(dx * 0.05, 0, dz * 0.05);
			p.hurtMarked = true;
		}
		if (ticks % 3 == 0) {
			double a = ticks * 0.3;
			double y = c.y + ((ticks * 0.05) % 1.9);
			server.sendParticles(GREEN, c.x + Math.cos(a) * 0.65, y, c.z + Math.sin(a) * 0.65, 1, 0, 0, 0, 0);
			server.sendParticles(GREEN, c.x - Math.cos(a) * 0.65, y, c.z - Math.sin(a) * 0.65, 1, 0, 0, 0, 0);
		}
		if (ticks >= OmnitrixConfig.RECAL_TICKS) {
			finishRecalibration(server, p);
		}
	}

	private void finishRecalibration(ServerLevel server, ServerPlayer p) {
		chip = ItemStack.EMPTY; // used up
		Omnitrix.recalibrate(p);
		Vec3 c = centre().add(0, 1.0, 0);
		server.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.7f, 1.2f);
		server.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(6, 60, 20));
		p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
				Component.translatable("message.projecthero.omnitrix.recal_subtitle").withStyle(ChatFormatting.GRAY)));
		p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
				Component.translatable("message.projecthero.omnitrix.recal_title").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)));
		p.displayClientMessage(msg("recal_success", ChatFormatting.GREEN).withStyle(ChatFormatting.BOLD), false);
		reset(p);
	}

	/** Starts the extraction on {@code p} (no checks beyond "wears it, not already running": tests call this). */
	public boolean start(ServerPlayer p) {
		if (running() || !Omnitrix.hasPower(p) || !(level instanceof ServerLevel server)) {
			return false;
		}
		fuel = Math.max(0, fuel - OmnitrixConfig.FUEL_PER_EXTRACTION);
		recal = false;
		subject = p.getUUID();
		subjectRef = new java.lang.ref.WeakReference<>(p);
		ticks = 0;
		nextPulse = 0;
		long now = server.getGameTime();
		nextSurge = now + 60 + server.random.nextInt(40);
		nextFlicker = now + 100 + server.random.nextInt(60);
		p.setAttached(OmnitrixAttachments.EXTRACTION, 0);
		Vec3 c = centre();
		server.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.6f, 0.6f);
		OmnitrixSounds.play(server, c, OmnitrixSounds.SELF_DESTRUCT, 1.0f, 1.0f, SoundSource.BLOCKS);
		p.displayClientMessage(msg("start", ChatFormatting.RED).withStyle(ChatFormatting.BOLD), false);
		setChanged();
		sync();
		return true;
	}

	void serverTick() {
		if (subject == null || !(level instanceof ServerLevel server)) {
			return;
		}
		ServerPlayer p = subjectRef.get();
		if (p == null || p.isRemoved()) {
			p = server.getServer().getPlayerList().getPlayer(subject);
		}
		if (p == null || p.level() != level || p.isDeadOrDying() || p.isRemoved()) {
			fail(p, "failed_death");
			return;
		}
		if (!Omnitrix.hasPower(p)) {
			reset(p);
			return;
		}
		if (recal) {
			recalTick(server, p);
			return;
		}
		if (!inside(p)) {
			p.invulnerableTime = 0;
			p.hurt(OmnitrixDamage.extraction(server), OmnitrixConfig.STEP_OUT_DAMAGE);
			fail(p, "failed_step_out");
			return;
		}
		ticks++;
		p.setAttached(OmnitrixAttachments.EXTRACTION, ticks);
		long now = server.getGameTime();
		Vec3 c = centre();
		// the damage schedule
		float[][] pulses = OmnitrixConfig.EXTRACTION_PULSES;
		while (nextPulse < pulses.length && pulses[nextPulse][0] <= ticks) {
			float dmg = pulses[nextPulse][1];
			nextPulse++;
			p.invulnerableTime = 0;
			p.hurt(OmnitrixDamage.extraction(server), dmg);
			OmnitrixSounds.play(server, c.add(0, 1, 0), OmnitrixSounds.EXTRACT_PULSE, 1.0f, 0.9f + server.random.nextFloat() * 0.2f, SoundSource.BLOCKS);
			server.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1.0, p.getZ(), 14, 0.35, 0.6, 0.35, 0.2);
			if (p.isDeadOrDying()) {
				fail(p, "failed_death");
				return;
			}
		}
		// the Omnitrix fights back
		if (now >= nextSurge) {
			nextSurge = now + 70 + server.random.nextInt(60);
			p.push((server.random.nextDouble() - 0.5) * 0.12, 0.45, (server.random.nextDouble() - 0.5) * 0.12);
			p.hurtMarked = true;
			server.playSound(null, c.x, c.y + 1, c.z, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.BLOCKS, 0.3f, 1.8f);
			server.sendParticles(GREEN, c.x, c.y + 1, c.z, 40, 0.4, 0.9, 0.4, 0.0);
			com.projecthero.mod.quirk.fx.QuirkCamera.shakeAround(server, p.position(), 6, 0.6f, 12);
		}
		if (now >= nextFlicker) {
			nextFlicker = now + 110 + server.random.nextInt(80);
			List<Aliens.Alien> pool = Aliens.playable(p);
			if (!pool.isEmpty()) {
				Omnitrix.flicker(p, pool.get(server.random.nextInt(pool.size())).id(), 24);
			}
		}
		// pull a swaying wearer back toward the centre
		double dx = c.x - p.getX();
		double dz = c.z - p.getZ();
		if (dx * dx + dz * dz > 0.09) {
			p.push(dx * 0.04, 0, dz * 0.04);
			p.hurtMarked = true;
		}
		if (ticks % 40 == 0) {
			OmnitrixSounds.play(server, c.add(0, 1, 0), OmnitrixSounds.SELF_DESTRUCT, 1.0f, 1.0f + ticks / 3200f, SoundSource.BLOCKS);
		}
		if (ticks % 2 == 0) {
			for (int k = 0; k < 2; k++) {
				double a = (ticks * 0.35) + k * Math.PI;
				double y = c.y + ((ticks * 0.06 + k * 0.5) % 1.8);
				server.sendParticles(ticks > OmnitrixConfig.EXTRACTION_TICKS * 3 / 4 ? RED : GREEN, c.x + Math.cos(a) * 0.7, y,
						c.z + Math.sin(a) * 0.7, 1, 0, 0, 0, 0);
			}
		}
		if (ticks >= OmnitrixConfig.EXTRACTION_TICKS && nextPulse >= pulses.length) {
			succeed(p);
		}
	}

	private void succeed(ServerPlayer p) {
		ServerLevel server = (ServerLevel) level;
		boolean wasRecal = Omnitrix.recalibrated(p); // v0.17.2: the recalibrated watch comes off as itself
		Omnitrix.revoke(p);
		Vec3 c = centre().add(0, 1.7, 0);
		ItemEntity item = new ItemEntity(server, c.x, c.y, c.z, new ItemStack(wasRecal ? OmnitrixItems.RECALIBRATED_OMNITRIX : OmnitrixItems.OMNITRIX));
		item.setDeltaMovement(0, 0.25, 0);
		item.setGlowingTag(true);
		server.addFreshEntity(item);
		OmnitrixWorld.markLoose(server.getServer());
		OmnitrixSounds.play(server, c, OmnitrixSounds.EXTRACT_DONE, 1.2f, 1.0f, SoundSource.BLOCKS);
		server.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.6f, 0.8f);
		server.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		server.sendParticles(GREEN, c.x, c.y, c.z, 80, 0.6, 0.6, 0.6, 0.0);
		p.displayClientMessage(msg("success", ChatFormatting.GREEN).withStyle(ChatFormatting.BOLD), false);
		reset(p);
	}

	private void fail(@Nullable ServerPlayer p, String why) {
		if (recal && !why.startsWith("recal")) {
			why = "recal_failed"; // v0.17.2: a recalibration never hurts -- it just stops
		}
		if (p != null) {
			p.displayClientMessage(msg(why, ChatFormatting.RED).withStyle(ChatFormatting.BOLD), false);
		}
		if (level instanceof ServerLevel server) {
			Vec3 c = centre();
			server.playSound(null, c.x, c.y + 1, c.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.6f, 0.6f);
			server.sendParticles(RED, c.x, c.y + 1, c.z, 40, 0.4, 0.8, 0.4, 0.0);
		}
		reset(p);
	}

	private void reset(@Nullable ServerPlayer p) {
		if (p != null) {
			p.removeAttached(OmnitrixAttachments.EXTRACTION);
		}
		if (!chip.isEmpty() && level instanceof ServerLevel server) {
			// v0.17.2: a recalibration that failed hands its chip back
			Vec3 c = centre().add(0, 1.2, 0);
			ItemEntity back = new ItemEntity(server, c.x, c.y, c.z, chip);
			back.setDeltaMovement(0, 0.2, 0);
			server.addFreshEntity(back);
		}
		chip = ItemStack.EMPTY;
		recal = false;
		subject = null;
		subjectRef = new java.lang.ref.WeakReference<>(null);
		ticks = 0;
		nextPulse = 0;
		setChanged();
		sync();
	}

	private static net.minecraft.network.chat.MutableComponent msg(String key, ChatFormatting colour) {
		return Component.translatable("message.projecthero.omnitrix_pod." + key).withStyle(colour);
	}

	private void sync() {
		if (level != null && !level.isClientSide()) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
		}
	}

	/** v0.17.2: client copy -- the pod's field runs a recalibration (stays green). */
	private boolean clientRecal;

	public boolean clientRecalibrating() {
		return clientRecal;
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		fuel = tag.getInt("fuel");
		ticks = tag.getInt("client_ticks");
		clientRecal = tag.getBoolean("recal");
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putInt("fuel", fuel);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("fuel", fuel);
		tag.putInt("client_ticks", running() ? Math.max(1, ticks) : 0);
		tag.putBoolean("recal", recalibrating());
		return tag;
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}
}
