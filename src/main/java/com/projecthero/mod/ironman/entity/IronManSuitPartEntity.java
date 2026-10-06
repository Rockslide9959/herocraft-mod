package com.projecthero.mod.ironman.entity;

import java.util.UUID;

import com.projecthero.mod.ironman.IronManSounds;
import com.projecthero.mod.ironman.item.IronManArmorItem;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * One flying Iron Man armour piece, in transit from storage (a Suit Platform, the pack, the Mark VII delivery pod) to
 * its owner. Reusable for every mark and every part.
 *
 * <h2>v0.14.21</h2>
 * <ul>
 *   <li><b>It carries the real stack.</b> The exact {@link ItemStack} that left the platform / inventory rides in the
 *       courier (synced for the renderer, saved in the entity's NBT -- the type is no longer {@code noSave}), is the
 *       stack that ends up worn, and is the stack dropped if delivery fails. Enchantments, names, charge stamps and
 *       every other component survive.</li>
 *   <li><b>Curved approach.</b> It flies a quadratic Bezier from its launch point, bowed up and to one side, to the
 *       piece's own place on the owner's body (the target end moves with the owner every tick), accelerating out of
 *       the launch and decelerating over the last few blocks.</li>
 *   <li><b>Orientation + clamp-on.</b> {@link #progress()} is synced; over the last stretch the renderer stops the
 *       tumble and turns the piece to the owner's body yaw at full scale, so it arrives already lined up on its slot.
 *       On arrival {@link IronManSuitUpManager#receivePart} equips it, which starts the piece's lock-on reveal for every
 *       viewer and plays the clamp.</li>
 * </ul>
 * If the owner logs out, changes dimension or the courier times out, the stack is dropped at the courier's position --
 * never destroyed. A courier loaded from disk whose owner is offline drops it the same way.
 */
public class IronManSuitPartEntity extends Entity {
	private static final EntityDataAccessor<String> SUIT_ID =
			SynchedEntityData.defineId(IronManSuitPartEntity.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<Integer> PART_ORDINAL =
			SynchedEntityData.defineId(IronManSuitPartEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<ItemStack> PIECE =
			SynchedEntityData.defineId(IronManSuitPartEntity.class, EntityDataSerializers.ITEM_STACK);
	private static final EntityDataAccessor<Float> PROGRESS =
			SynchedEntityData.defineId(IronManSuitPartEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> OWNER_ENTITY =
			SynchedEntityData.defineId(IronManSuitPartEntity.class, EntityDataSerializers.INT);
	/** v0.14.28: 0 = courier to the owner, 1 = send-home (builds in front of the owner, then flies to a Suit Platform). */
	private static final EntityDataAccessor<Integer> MODE =
			SynchedEntityData.defineId(IronManSuitPartEntity.class, EntityDataSerializers.INT);
	/** v0.14.28: send-home build progress of this piece, 0..1 (synced for the renderer). */
	private static final EntityDataAccessor<Float> BUILD =
			SynchedEntityData.defineId(IronManSuitPartEntity.class, EntityDataSerializers.FLOAT);

	public static final int MODE_COURIER = 0;
	public static final int MODE_HOME = 1;
	/** v0.14.28 send-home: each piece builds itself on the standing suit over this many ticks (boots first). */
	public static final int HOME_BUILD_TICKS = 20;
	/** v0.14.28 send-home: the finished suit stands this long before it takes off. */
	public static final int HOME_HOLD_TICKS = 10;
	/** v0.14.28 send-home: a platform out of reach -- the suit climbs this high, then goes on the return queue. */
	public static final double HOME_SKY_CLIMB = 40.0;

	// send-home state (server)
	private BlockPos homeDock;
	private Vec3 homeFeet;
	private int homeIndex;
	private int homeCount;
	private float homeYaw;
	private boolean homeSkyward;
	private double homeSpeed;

	/** Hard cap on a courier's life (ticks) before it gives up and drops its piece. */
	public static final int MAX_LIFE = 800;
	/** Distance (blocks) over which the courier eases down to its final crawl. */
	private static final double DECEL_DISTANCE = 3.5;

	private UUID ownerId;
	private int life;
	/** Ticks this courier hovers at its spawn point before it flies -- the one-at-a-time launch stagger. */
	private int launchDelay;
	private Vec3 launchPos;
	private Vec3 bow;
	private double t;

	public IronManSuitPartEntity(EntityType<? extends IronManSuitPartEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	/**
	 * Launch {@code piece} (the real stack; it is taken over by the courier) from {@code from} toward {@code owner},
	 * after hovering for {@code launchDelay} ticks.
	 */
	public static IronManSuitPartEntity spawn(ServerLevel level, Vec3 from, ServerPlayer owner, ItemStack piece, int launchDelay) {
		IronManSuitPartEntity e = new IronManSuitPartEntity(IronManEntityTypes.SUIT_PART, level);
		if (!com.projecthero.mod.ironman.suit.IronManChunkTickets.entityTicking(level, from)) {
			// v0.14.29: never launch from a chunk where the courier would freeze (and be saved with its piece) --
			// start a few blocks out from the owner on the same bearing instead
			Vec3 d = new Vec3(from.x - owner.getX(), 0, from.z - owner.getZ());
			d = d.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : d.normalize();
			from = owner.position().add(d.scale(6.0)).add(0, 4.0, 0);
			if (!com.projecthero.mod.ironman.suit.IronManChunkTickets.entityTicking(level, from)) {
				from = owner.position().add(0, 4.0, 0); // the owner's own chunk always ticks
			}
		}
		e.setPos(from.x, from.y, from.z);
		e.ownerId = owner.getUUID();
		e.launchDelay = Math.max(0, launchDelay);
		e.setPiece(piece);
		e.getEntityData().set(OWNER_ENTITY, owner.getId());
		level.addFreshEntity(e);
		return e;
	}

	/**
	 * v0.14.28 send-home: launch {@code piece} (the real stack; taken over) as piece {@code index} of {@code count} of a
	 * suit that builds itself standing at {@code feet} (facing {@code yaw}), boots first, {@link #HOME_BUILD_TICKS} each,
	 * then flies to the Suit Platform at {@code dock} and docks there. All pieces of one suit share the same timeline,
	 * so they stay one standing suit the whole way.
	 */
	public static IronManSuitPartEntity spawnHome(ServerLevel level, Vec3 feet, float yaw, ServerPlayer owner, ItemStack piece,
			int index, int count, BlockPos dock) {
		IronManSuitPartEntity e = new IronManSuitPartEntity(IronManEntityTypes.SUIT_PART, level);
		e.ownerId = owner.getUUID();
		e.setPiece(piece);
		e.getEntityData().set(OWNER_ENTITY, owner.getId());
		e.getEntityData().set(MODE, MODE_HOME);
		e.homeDock = dock.immutable();
		e.homeFeet = feet;
		e.homeIndex = index;
		e.homeCount = Math.max(1, count);
		e.homeYaw = yaw;
		Vec3 at = e.homeAt(feet);
		e.moveTo(at.x, at.y, at.z, yaw, 0f);
		level.addFreshEntity(e);
		return e;
	}

	public boolean homeMode() {
		return getEntityData().get(MODE) == MODE_HOME;
	}

	/** v0.14.28 send-home build progress of this piece, 0..1. */
	public float build() {
		return getEntityData().get(BUILD);
	}

	/** v0.14.28: the tick (of this entity's life) at which a send-home suit of {@code count} pieces takes off. */
	public static int homeTakeOffTick(int count) {
		return count * HOME_BUILD_TICKS + HOME_HOLD_TICKS;
	}

	/** v0.14.28: build progress of send-home piece {@code index} at life tick {@code life}. */
	public static float homeBuild(int index, int life) {
		return Mth.clamp((life - index * HOME_BUILD_TICKS) / (float) HOME_BUILD_TICKS, 0f, 1f);
	}

	private Vec3 homeAt(Vec3 feet) {
		return feet.add(0, IronManSuitUpManager.slotHeight(IronManSuitUpManager.slotFor(part())), 0);
	}

	private void setPiece(ItemStack piece) {
		getEntityData().set(PIECE, piece.copy());
		if (piece.getItem() instanceof IronManArmorItem a) {
			getEntityData().set(SUIT_ID, a.suitId());
			getEntityData().set(PART_ORDINAL, a.getType().ordinal());
		}
	}

	/** The real stack in transit (a copy -- the courier keeps the original until it is delivered or dropped). */
	public ItemStack piece() {
		return getEntityData().get(PIECE).copy();
	}

	public String suitId() {
		return getEntityData().get(SUIT_ID);
	}

	public ArmorItem.Type part() {
		return ArmorItem.Type.values()[getEntityData().get(PART_ORDINAL)];
	}

	/** 0 at launch .. 1 on arrival (synced; drives the renderer's orientation blend). */
	public float progress() {
		return getEntityData().get(PROGRESS);
	}

	/** The owner's entity id (synced), so the renderer can line the piece up with the owner's body yaw. */
	public int ownerEntityId() {
		return getEntityData().get(OWNER_ENTITY);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(SUIT_ID, "mark_iii");
		builder.define(PART_ORDINAL, ArmorItem.Type.CHESTPLATE.ordinal());
		builder.define(PIECE, ItemStack.EMPTY);
		builder.define(PROGRESS, 0f);
		builder.define(OWNER_ENTITY, -1);
		builder.define(MODE, MODE_COURIER);
		builder.define(BUILD, 1f);
	}

	/** Where on the owner this piece clamps on: its slot's height on the body. */
	public static Vec3 attachPoint(ServerPlayer owner, ArmorItem.Type part) {
		EquipmentSlot slot = IronManSuitUpManager.slotFor(part);
		return owner.position().add(0, IronManSuitUpManager.slotHeight(slot) * owner.getScale(), 0);
	}

	/** Point on the quadratic Bezier p0 -> (bow) -> p2 at {@code t}. */
	public static Vec3 bezier(Vec3 p0, Vec3 c, Vec3 p2, double t) {
		double u = 1 - t;
		return p0.scale(u * u).add(c.scale(2 * u * t)).add(p2.scale(t * t));
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			if (!homeMode() || tickCount > homeTakeOffTick(4)) {
				level().addParticle(ParticleTypes.END_ROD, getX(), getY(), getZ(), 0, 0, 0);
			}
			return;
		}
		if (homeMode()) {
			tickHome((ServerLevel) level());
			return;
		}
		life++;
		ServerLevel level = (ServerLevel) level();
		ServerPlayer owner = ownerId == null ? null : (ServerPlayer) level.getPlayerByUUID(ownerId);
		if (owner == null || owner.level() != level || life > MAX_LIFE || getEntityData().get(PIECE).isEmpty()) {
			dropAndDiscard();
			return;
		}
		if (getEntityData().get(OWNER_ENTITY) != owner.getId()) {
			getEntityData().set(OWNER_ENTITY, owner.getId());
		}

		// Hold at the staging point until this piece's turn in the sequence.
		if (life < launchDelay) {
			setPos(getX(), getY() + Math.sin(life * 0.3) * 0.01, getZ());
			setYRot(getYRot() + 8f);
			level.sendParticles(ParticleTypes.END_ROD, getX(), getY(), getZ(), 1, 0.05, 0.05, 0.05, 0.005);
			return;
		}
		if (launchPos == null) {
			launchPos = position();
			IronManSounds.play(this, IronManSounds.THRUSTER, 0.5f, 1.6f);
		}
		Vec3 target = attachPoint(owner, part());
		if (bow == null) {
			// bow the path up and out to one side (side chosen from the entity id, so each piece in a set curves its own way)
			Vec3 chord = target.subtract(launchPos);
			Vec3 side = new Vec3(-chord.z, 0, chord.x);
			side = side.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : side.normalize();
			double len = chord.length();
			double sideAmt = (getId() % 2 == 0 ? 1 : -1) * Math.min(6.0, 0.25 * len + 1.0);
			bow = side.scale(sideAmt).add(0, Math.min(8.0, 2.0 + 0.2 * len), 0);
		}
		Vec3 control = launchPos.add(target).scale(0.5).add(bow);
		double arc = launchPos.distanceTo(control) + control.distanceTo(target);
		double remaining = position().distanceTo(target);
		int flightTicks = life - launchDelay;
		double speed = Math.min(1.3, 0.25 + flightTicks * 0.06 + arc * 0.01);
		speed *= Mth.clamp(remaining / DECEL_DISTANCE, 0.22, 1.0);
		t = Math.min(1.0, t + speed / Math.max(0.5, arc));

		Vec3 next = bezier(launchPos, control, target, t);
		if (t >= 1.0 || next.distanceTo(target) < 0.2
				|| !com.projecthero.mod.ironman.suit.IronManChunkTickets.entityTicking(level, next)) {
			// v0.14.29: the next step would leave entity-ticking chunks (the owner is moving fast / the bow swings
			// out) -- clamp on now rather than freeze out there with the piece
			arrive(owner);
			return;
		}
		setDeltaMovement(next.subtract(position()));
		setPos(next.x, next.y, next.z);
		getEntityData().set(PROGRESS, (float) t);
		level.sendParticles(ParticleTypes.END_ROD, getX(), getY(), getZ(), 1, 0.05, 0.05, 0.05, 0.01);
		if (t < 0.8) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY(), getZ(), 1, 0.03, 0.03, 0.03, 0.02);
		}
	}

	// ---------------- v0.14.28: send-home ----------------

	private void tickHome(ServerLevel level) {
		life++;
		if (getEntityData().get(PIECE).isEmpty()) {
			discard();
			return;
		}
		double slotH = IronManSuitUpManager.slotHeight(IronManSuitUpManager.slotFor(part()));
		if (homeFeet == null) {
			homeFeet = position().subtract(0, slotH, 0);
		}
		if (homeDock == null) {
			homeFallback(level, getEntityData().get(PIECE).copy());
			return;
		}
		float b = homeBuild(homeIndex, life);
		if (getEntityData().get(BUILD) != b) {
			getEntityData().set(BUILD, b);
		}
		int takeOff = homeTakeOffTick(homeCount);
		if (life < takeOff) {
			// standing in front of the owner, building itself on piece by piece
			Vec3 at = homeAt(homeFeet);
			setPos(at.x, at.y, at.z);
			setYRot(homeYaw);
			if (b > 0f && b < 1f && life % 3 == 0) {
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY(), getZ(), 2, 0.25, 0.15, 0.25, 0.04);
			}
			if (life == homeIndex * HOME_BUILD_TICKS + 1) {
				IronManSounds.play(this, IronManSounds.SERVO, 0.6f, 0.9f + homeIndex * 0.08f);
			}
			if (life == (homeIndex + 1) * HOME_BUILD_TICKS) {
				IronManSounds.play(this, IronManSounds.CLAMP, 0.7f, 1.0f);
			}
			return;
		}
		int flight = life - takeOff;
		if (flight == 0 && homeIndex == 0) {
			IronManSounds.play(this, IronManSounds.THRUSTER, 0.8f, 1.2f);
		}
		if (flight > MAX_LIFE) {
			queueHome(level);
			return;
		}
		// v0.14.29: "reachable" = the dock is somewhere entities keep ticking, not merely loaded
		boolean reachable = !homeSkyward && level.isLoaded(homeDock)
				&& com.projecthero.mod.ironman.suit.IronManChunkTickets.entityTicking(level, homeDock)
				&& level.getBlockEntity(homeDock) instanceof com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity;
		if (!reachable) {
			homeSkyward = true;
		}
		homeSpeed = Math.min(1.6, homeSpeed + 0.07);
		Vec3 dir;
		if (homeSkyward) {
			// the platform is out of reach: climb out of sight, then go on the return queue (it docks when it loads)
			dir = new Vec3(0, 1, 0);
			if (flight * 1.2 > HOME_SKY_CLIMB) {
				queueHome(level);
				return;
			}
		} else {
			Vec3 to = Vec3.atBottomCenterOf(homeDock.above());
			Vec3 d = to.subtract(homeFeet);
			double dist = d.length();
			if (dist <= Math.max(0.35, homeSpeed)) {
				arriveHome(level);
				return;
			}
			dir = d.normalize();
			if (flight < 12 && dist > 4.0) {
				dir = dir.add(0, 1.0 - flight / 12.0, 0).normalize(); // lift off first, then streak home
			}
			homeSpeed = Math.min(homeSpeed, Math.max(0.25, dist * 0.35)); // ease into the dock
		}
		Vec3 next = homeFeet.add(dir.scale(homeSpeed));
		if (!com.projecthero.mod.ironman.suit.IronManChunkTickets.entityTicking(level, next)) {
			queueHome(level); // flying into unloaded terrain: hand over to the queue instead of freezing there
			return;
		}
		homeFeet = next;
		Vec3 at = homeAt(next);
		if (dir.horizontalDistanceSqr() > 1.0e-4) {
			homeYaw = (float) (Mth.atan2(dir.z, dir.x) * (180.0 / Math.PI)) - 90f;
		}
		setYRot(homeYaw);
		setDeltaMovement(at.subtract(position()));
		setPos(at.x, at.y, at.z);
		if (homeIndex == 0 && flight % 2 == 0) {
			level.sendParticles(ParticleTypes.FLAME, homeFeet.x, homeFeet.y, homeFeet.z, 2, 0.12, 0.02, 0.12, 0.01);
		}
	}

	/** Reached the platform: dock the real stack on it, or hand it on so it is never lost. */
	private void arriveHome(ServerLevel level) {
		ItemStack stack = getEntityData().get(PIECE).copy();
		getEntityData().set(PIECE, ItemStack.EMPTY);
		if (level.getBlockEntity(homeDock) instanceof com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity be
				&& (be.owner().isEmpty() || ownerId == null || be.owner().get().equals(ownerId))) {
			if (be.owner().isEmpty() && ownerId != null) {
				be.bindTo(ownerId);
			}
			if (be.store(stack) || stack.isEmpty()) {
				Vec3 c = Vec3.atBottomCenterOf(homeDock.above());
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y + 0.8, c.z, 10, 0.3, 0.5, 0.3, 0.05);
				if (homeIndex == 0) {
					IronManSounds.play(this, IronManSounds.CLAMP, 0.9f, 0.8f);
				}
				discard();
				return;
			}
		}
		homeFallback(level, stack);
	}

	/** Out of reach: put the piece on {@link com.projecthero.mod.ironman.data.StarkSuitReturnQueue} for its platform. */
	private void queueHome(ServerLevel level) {
		ItemStack stack = getEntityData().get(PIECE).copy();
		getEntityData().set(PIECE, ItemStack.EMPTY);
		if (homeDock != null && ownerId != null && stack.getItem() instanceof IronManArmorItem a) {
			// v0.14.29: rack it now -- the platform's chunk is loaded with a short ticket; the queue is only the fallback
			net.minecraft.core.GlobalPos gp = net.minecraft.core.GlobalPos.of(level.dimension(), homeDock);
			if (com.projecthero.mod.ironman.suit.IronManPlatformReturn.depositNow(level.getServer(), ownerId, gp,
					java.util.List.of(stack))) {
				discard();
				return;
			}
			com.projecthero.mod.ironman.data.StarkSuitReturnQueue.get(level).enqueue(ownerId,
					net.minecraft.core.GlobalPos.of(level.dimension(), homeDock), a.suitId(), maskOf(a.getType()),
					com.projecthero.mod.ironman.IronManEnergy.stackEnergy(stack, a.suitId()),
					com.projecthero.mod.ironman.IronManEnergy.stackIntegrity(stack, a.suitId()), java.util.List.of(stack));
			discard();
			return;
		}
		homeFallback(level, stack);
	}

	/** Last resort: back to the owner (or dropped where it is) -- never destroyed. */
	private void homeFallback(ServerLevel level, ItemStack stack) {
		getEntityData().set(PIECE, ItemStack.EMPTY);
		if (!stack.isEmpty()) {
			ServerPlayer owner = ownerId == null ? null : (ServerPlayer) level.getPlayerByUUID(ownerId);
			if (owner != null) {
				IronManSuitUpManager.giveBack(owner, stack);
			} else {
				spawnAtLocation(stack);
			}
		}
		discard();
	}

	private static int maskOf(ArmorItem.Type type) {
		return switch (type) {
			case HELMET -> 1;
			case CHESTPLATE -> 2;
			case LEGGINGS -> 4;
			case BOOTS -> 8;
			default -> 0;
		};
	}

	/** Clamp-on: hand the real stack to the suit-up manager; if that slot is already taken it goes back to the owner. */
	private void arrive(ServerPlayer owner) {
		ItemStack stack = getEntityData().get(PIECE).copy();
		getEntityData().set(PIECE, ItemStack.EMPTY);
		if (!IronManSuitUpManager.receivePart(owner, stack)) {
			IronManSuitUpManager.giveBack(owner, stack);
		}
		discard();
	}

	private void dropAndDiscard() {
		if (!level().isClientSide()) {
			ItemStack stack = getEntityData().get(PIECE);
			if (!stack.isEmpty()) {
				spawnAtLocation(stack.copy());
				getEntityData().set(PIECE, ItemStack.EMPTY);
			}
		}
		discard();
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
		if (tag.hasUUID("Owner")) {
			ownerId = tag.getUUID("Owner");
		}
		life = tag.getInt("Life");
		launchDelay = tag.getInt("LaunchDelay");
		if (tag.getInt("Mode") == MODE_HOME) {
			getEntityData().set(MODE, MODE_HOME);
			homeDock = tag.contains("HomeDock") ? BlockPos.of(tag.getLong("HomeDock")) : null;
			homeFeet = tag.contains("HomeX") ? new Vec3(tag.getDouble("HomeX"), tag.getDouble("HomeY"), tag.getDouble("HomeZ")) : null;
			homeIndex = tag.getInt("HomeIndex");
			homeCount = Math.max(1, tag.getInt("HomeCount"));
			homeYaw = tag.getFloat("HomeYaw");
			homeSkyward = tag.getBoolean("HomeSky");
			homeSpeed = tag.getDouble("HomeSpeed");
		}
		if (tag.contains("Piece")) {
			setPiece(ItemStack.parseOptional(registryAccess(), tag.getCompound("Piece")));
		} else if (tag.contains("SuitId")) {
			// pre-0.14.21 courier (those were never saved, but be safe): rebuild the piece it stood for
			int rawPart = tag.getInt("Part");
			int partCount = ArmorItem.Type.values().length;
			ArmorItem.Type type = ArmorItem.Type.values()[rawPart >= 0 && rawPart < partCount ? rawPart
					: ArmorItem.Type.CHESTPLATE.ordinal()];
			var item = com.projecthero.mod.ironman.item.IronManItems.armor(tag.getString("SuitId"), type);
			if (item != null) {
				setPiece(new ItemStack(item));
			}
		}
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
		if (ownerId != null) {
			tag.putUUID("Owner", ownerId);
		}
		tag.putInt("Life", life);
		tag.putInt("LaunchDelay", launchDelay);
		ItemStack stack = getEntityData().get(PIECE);
		if (!stack.isEmpty()) {
			tag.put("Piece", stack.save(registryAccess()));
		}
		if (homeMode()) {
			tag.putInt("Mode", MODE_HOME);
			if (homeDock != null) {
				tag.putLong("HomeDock", homeDock.asLong());
			}
			if (homeFeet != null) {
				tag.putDouble("HomeX", homeFeet.x);
				tag.putDouble("HomeY", homeFeet.y);
				tag.putDouble("HomeZ", homeFeet.z);
			}
			tag.putInt("HomeIndex", homeIndex);
			tag.putInt("HomeCount", homeCount);
			tag.putFloat("HomeYaw", homeYaw);
			tag.putBoolean("HomeSky", homeSkyward);
			tag.putDouble("HomeSpeed", homeSpeed);
		}
	}

	@Override
	public boolean isPickable() {
		return false;
	}
}
