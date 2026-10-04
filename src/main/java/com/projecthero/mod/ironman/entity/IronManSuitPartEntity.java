package com.projecthero.mod.ironman.entity;

import java.util.UUID;

import com.projecthero.mod.ironman.IronManSounds;
import com.projecthero.mod.ironman.item.IronManArmorItem;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;

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
		e.setPos(from.x, from.y, from.z);
		e.ownerId = owner.getUUID();
		e.launchDelay = Math.max(0, launchDelay);
		e.setPiece(piece);
		e.getEntityData().set(OWNER_ENTITY, owner.getId());
		level.addFreshEntity(e);
		return e;
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
			level().addParticle(ParticleTypes.END_ROD, getX(), getY(), getZ(), 0, 0, 0);
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
		if (t >= 1.0 || next.distanceTo(target) < 0.2) {
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
	}

	@Override
	public boolean isPickable() {
		return false;
	}
}
