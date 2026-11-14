package com.projecthero.mod.galactus.orbit;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.galactus.GalactusEvent;
import com.projecthero.mod.galactus.entity.GalactusMob;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.17.2 Galactus raid v2: <b>the Devourer</b> -- the colossus of the new raid (the head that rises in C2, the 180-block
 * giant leaning over the meteor field, the one who kneels over the Earth, the 100-block fighter of Stage 3). Its whole
 * pose is a few synced numbers (height, lean, a look target, a target point and weight per arm, eye glow, flags), solved
 * the same way on both sides by {@link GalactusRig}: the server knows exactly where his fists and head are, the client
 * draws exactly that. The raid moves him; he has no AI.
 *
 * <p>He takes no damage himself: his <b>parts</b> ({@link DevourerPartEntity} -- head, chest core, each upper arm,
 * forearm and hand) are separate hit-boxes he places on his skeleton every tick, and hand what they take to the raid.
 * Never saved: the raid re-makes him.
 */
public class DevourerEntity extends GalactusMob {
	private static final EntityDataAccessor<Float> DATA_HEIGHT = SynchedEntityData.defineId(DevourerEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_LEAN = SynchedEntityData.defineId(DevourerEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Vector3f> DATA_LOOK = SynchedEntityData.defineId(DevourerEntity.class, EntityDataSerializers.VECTOR3);
	private static final EntityDataAccessor<Vector3f> DATA_TR = SynchedEntityData.defineId(DevourerEntity.class, EntityDataSerializers.VECTOR3);
	private static final EntityDataAccessor<Vector3f> DATA_TL = SynchedEntityData.defineId(DevourerEntity.class, EntityDataSerializers.VECTOR3);
	private static final EntityDataAccessor<Float> DATA_WR = SynchedEntityData.defineId(DevourerEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_WL = SynchedEntityData.defineId(DevourerEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_EYES = SynchedEntityData.defineId(DevourerEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_CHARGE = SynchedEntityData.defineId(DevourerEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> DATA_FLAGS = SynchedEntityData.defineId(DevourerEntity.class, EntityDataSerializers.INT);

	public static final int FLAG_CRACKED = 1, FLAG_FIST_R = 2, FLAG_FIST_L = 4, FLAG_CORE_OPEN = 8, FLAG_DISSOLVE = 16, FLAG_MOUTH = 32,
			FLAG_FULLBRIGHT = 64, FLAG_STUCK_R = 128, FLAG_STUCK_L = 256;

	private final Map<DevourerPartEntity.Kind, UUID> parts = new EnumMap<>(DevourerPartEntity.Kind.class);
	private boolean partsOn;
	/** v0.17.4: which hit-boxes are out (Stage 1: the fists and forearms only; Stage 3: all of them). */
	private final java.util.EnumSet<DevourerPartEntity.Kind> partKinds = java.util.EnumSet.noneOf(DevourerPartEntity.Kind.class);
	/** The arms-and-fists set Stage 1 uses: the stuck fist is what you hit there. */
	public static final java.util.Set<DevourerPartEntity.Kind> FISTS = java.util.Collections.unmodifiableSet(java.util.EnumSet.of(
			DevourerPartEntity.Kind.HAND_R, DevourerPartEntity.Kind.FORE_R, DevourerPartEntity.Kind.HAND_L, DevourerPartEntity.Kind.FORE_L));
	private GalactusRig.Pose cached;
	private int cachedTick = -1;
	/** Client: last tick's inputs, for smooth frames between ticks. */
	public final GalactusRig.Input prevInput = new GalactusRig.Input();
	public final GalactusRig.Input curInput = new GalactusRig.Input();
	public float prevEyes, prevCharge;

	public DevourerEntity(EntityType<? extends DevourerEntity> type, Level level) {
		super(type, level);
		setNoGravity(true);
		noPhysics = true;
		setInvulnerable(false);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 1000.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.MOVEMENT_SPEED, 0.0)
				.add(Attributes.FOLLOW_RANGE, 256.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_HEIGHT, 100f);
		builder.define(DATA_LEAN, 0f);
		builder.define(DATA_LOOK, new Vector3f());
		builder.define(DATA_TR, new Vector3f());
		builder.define(DATA_TL, new Vector3f());
		builder.define(DATA_WR, 0f);
		builder.define(DATA_WL, 0f);
		builder.define(DATA_EYES, 0f);
		builder.define(DATA_CHARGE, 0f);
		builder.define(DATA_FLAGS, FLAG_FULLBRIGHT);
	}

	@Override
	public String model() {
		return "galactus";
	}

	// ---------------------------------------------------------------- the pose (server writes, both read)

	public float height() {
		return entityData.get(DATA_HEIGHT);
	}

	public void setHeight(float h) {
		entityData.set(DATA_HEIGHT, h);
	}

	public float lean() {
		return entityData.get(DATA_LEAN);
	}

	public void setLean(float l) {
		entityData.set(DATA_LEAN, l);
	}

	public void setLook(Vec3 at) {
		entityData.set(DATA_LOOK, at == null ? new Vector3f() : new Vector3f((float) (at.x - getX()), (float) (at.y - getY()), (float) (at.z - getZ())));
	}

	public Vec3 look() {
		Vector3f v = entityData.get(DATA_LOOK);
		return v.lengthSquared() < 1.0e-4f ? null : new Vec3(getX() + v.x, getY() + v.y, getZ() + v.z);
	}

	/** Arm {@code right} reaches for {@code at} (world) with {@code weight} (0 = resting). Stored relative to his feet. */
	public void setArm(boolean right, Vec3 at, float weight) {
		Vector3f v = at == null ? new Vector3f() : new Vector3f((float) (at.x - getX()), (float) (at.y - getY()), (float) (at.z - getZ()));
		entityData.set(right ? DATA_TR : DATA_TL, v);
		entityData.set(right ? DATA_WR : DATA_WL, at == null ? 0f : Math.max(0f, Math.min(1f, weight)));
	}

	public Vec3 armTarget(boolean right) {
		Vector3f v = entityData.get(right ? DATA_TR : DATA_TL);
		return new Vec3(getX() + v.x, getY() + v.y, getZ() + v.z);
	}

	public float armWeight(boolean right) {
		return entityData.get(right ? DATA_WR : DATA_WL);
	}

	public float eyes() {
		return entityData.get(DATA_EYES);
	}

	public void setEyes(float e) {
		entityData.set(DATA_EYES, e);
	}

	public float charge() {
		return entityData.get(DATA_CHARGE);
	}

	public void setCharge(float c) {
		entityData.set(DATA_CHARGE, c);
	}

	public boolean flag(int f) {
		return (entityData.get(DATA_FLAGS) & f) != 0;
	}

	public void setFlag(int f, boolean on) {
		int v = entityData.get(DATA_FLAGS);
		int n = on ? v | f : v & ~f;
		if (n != v) {
			entityData.set(DATA_FLAGS, n);
		}
	}

	/**
	 * The entity stands at his chest, {@link #DROP} of his height above his feet: an entity is only drawn when the chunk
	 * section it stands in has been built on the client, and his feet are usually deep underground (behind the horizon in
	 * C2) or in empty void. The renderer draws the model that far down again; the rig works from {@link #feet()}.
	 */
	public static final float DROP = 0.6f;

	public double drop() {
		return height() * DROP;
	}

	public Vec3 feet() {
		return position().subtract(0, drop(), 0);
	}

	public void setFeet(double x, double y, double z) {
		setPos(x, y + drop(), z);
	}

	public void moveToFeet(double x, double y, double z, float yaw) {
		moveTo(x, y + drop(), z, yaw, 0f);
		setFacing(yaw);
	}

	/** Faces {@code yawDeg} (body, head and look all together: he turns as one). */
	public void setFacing(float yawDeg) {
		setYRot(yawDeg);
		yBodyRot = yawDeg;
		yHeadRot = yawDeg;
		yRotO = yawDeg;
		yBodyRotO = yawDeg;
	}

	/** The rig inputs from the synced state (now). */
	public GalactusRig.Input input(GalactusRig.Input into) {
		into.feet = feet();
		into.yawDeg = getYRot();
		into.height = height();
		into.lean = lean();
		into.look = look();
		into.targetR = armTarget(true);
		into.weightR = armWeight(true);
		into.targetL = armTarget(false);
		into.weightL = armWeight(false);
		return into;
	}

	/** The solved pose this tick (cached). */
	public GalactusRig.Pose pose() {
		if (cachedTick != tickCount || cached == null) {
			cached = GalactusRig.solve(input(new GalactusRig.Input()));
			cachedTick = tickCount;
		}
		return cached;
	}

	public void invalidatePose() {
		cachedTick = -1;
	}

	// ---------------------------------------------------------------- parts

	public void setPartsOn(boolean on) {
		setParts(on ? java.util.EnumSet.allOf(DevourerPartEntity.Kind.class) : java.util.EnumSet.noneOf(DevourerPartEntity.Kind.class));
	}

	/** Puts out exactly {@code kinds} (the others are removed). */
	public void setParts(java.util.Set<DevourerPartEntity.Kind> kinds) {
		partKinds.clear();
		partKinds.addAll(kinds);
		this.partsOn = !partKinds.isEmpty();
		if (level() instanceof ServerLevel server) {
			for (java.util.Iterator<Map.Entry<DevourerPartEntity.Kind, UUID>> it = parts.entrySet().iterator(); it.hasNext();) {
				Map.Entry<DevourerPartEntity.Kind, UUID> e = it.next();
				if (!partKinds.contains(e.getKey())) {
					if (server.getEntity(e.getValue()) instanceof DevourerPartEntity p) {
						p.discard();
					}
					it.remove();
				}
			}
		}
	}

	public boolean partsOn() {
		return partsOn;
	}

	/** Exactly these hit-boxes are out. */
	public boolean partsAre(java.util.Set<DevourerPartEntity.Kind> kinds) {
		return partKinds.equals(kinds);
	}

	public DevourerPartEntity part(DevourerPartEntity.Kind kind) {
		UUID u = parts.get(kind);
		return u != null && level() instanceof ServerLevel s && s.getEntity(u) instanceof DevourerPartEntity p && p.isAlive() ? p : null;
	}

	private void placeParts(ServerLevel level) {
		GalactusRig.Pose p = pose();
		for (DevourerPartEntity.Kind kind : partKinds) {
			DevourerPartEntity e = part(kind);
			if (e == null) {
				// v0.17.4: never into a chunk that isn't entity-ticking: an entity added there is hidden (getEntity can't see
				// it), so a fresh part was made every tick while the old ones piled up out of sight
				Vec3 c = kind.centre(p);
				if (!level.isPositionEntityTicking(net.minecraft.core.BlockPos.containing(c))) {
					continue;
				}
				e = com.projecthero.mod.galactus.entity.GalactusEntityTypes.DEVOURER_PART.create(level);
				if (e == null) {
					continue;
				}
				e.configure(getUUID(), kind);
				Vec3 at = kind.centre(p);
				e.moveTo(at.x, at.y - e.getBbHeight() / 2, at.z, 0f, 0f);
				level.addFreshEntity(e);
				parts.put(kind, e.getUUID());
			}
			float size = kind.size(p.k);
			if (Math.abs(e.size() - size) > 0.05f) {
				e.setSize(size);
			}
			Vec3 at = kind.centre(p);
			e.setPos(at.x, at.y - e.getBbHeight() / 2, at.z);
		}
	}

	/** Is {@code p} the part he is using for its kind? (A replaced one, back from a chunk that was hidden, is not.) */
	public boolean ownsPart(DevourerPartEntity p) {
		return p.getUUID().equals(parts.get(p.kind()));
	}

	/** A part was hit: the raid decides what it does. */
	public boolean onPartHit(ServerLevel level, DevourerPartEntity part, DamageSource source, float amount) {
		GalactusEvent e = event();
		return e != null && e.onDevourerHit(level, this, part.kind(), source, amount);
	}

	// ---------------------------------------------------------------- tick

	@Override
	public void tick() {
		if (level().isClientSide) {
			// keep last tick's inputs for interpolation
			copy(curInput, prevInput);
			input(curInput);
			prevEyes = eyesCache;
			prevCharge = chargeCache;
			eyesCache = eyes();
			chargeCache = charge();
		}
		super.tick();
		if (level() instanceof ServerLevel server) {
			invalidatePose();
			if (partsOn) {
				placeParts(server);
			}
		}
	}

	private float eyesCache, chargeCache;

	private static void copy(GalactusRig.Input from, GalactusRig.Input to) {
		to.feet = from.feet;
		to.yawDeg = from.yawDeg;
		to.height = from.height;
		to.lean = from.lean;
		to.look = from.look;
		to.targetR = from.targetR;
		to.weightR = from.weightR;
		to.targetL = from.targetL;
		to.weightL = from.weightL;
	}

	@Override
	public void aiStep() {
		if (level().isClientSide) {
			// the client still has to follow the server (position interpolation lives in LivingEntity.aiStep)
			super.aiStep();
			return;
		}
		setDeltaMovement(Vec3.ZERO);
	}

	@Override
	protected void customServerAiStep() {
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return super.hurt(source, amount);
		}
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return true;
	}

	@Override
	public AABB getBoundingBoxForCulling() {
		float h = height();
		return new AABB(getX() - h, getY() - h * DROP - 4, getZ() - h, getX() + h, getY() + h * 1.1, getZ() + h);
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public void remove(RemovalReason reason) {
		if (level() instanceof ServerLevel server) {
			for (UUID u : parts.values()) {
				if (server.getEntity(u) instanceof DevourerPartEntity p) {
					p.discard();
				}
			}
			parts.clear();
		}
		super.remove(reason);
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
	}
}
