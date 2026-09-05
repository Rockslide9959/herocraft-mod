package com.herocraft.mod.firearm;


import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * Every tunable for one firearm in one immutable value. Adding a new gun is one {@link Firearms}
 * entry plus assets and a recipe -- no constants scattered across handler classes (spec section 36).
 *
 * <p>All times are in ticks (20/sec). {@link #fireIntervalTicks} is the minimum gap between shots
 * (so 4 = 5 shots/sec). {@link #spreadDegrees} is the half-angle cone at rest; recoil adds to it
 * temporarily. {@link #zoomLevels} drives the scope (empty = iron sights / hip only, a single 1.0
 * entry = simple ADS, several = a cycling scope).
 */
public final class FirearmData {
	public final String id;
	public final AmmoKind ammo;

	public final int magazineSize;
	public final int reloadTicks;
	/** Shotgun-style: reload one round at a time, interruptible. */
	public final boolean shellReload;
	public final int fireIntervalTicks;
	public final boolean automatic;
	/** After firing, the weapon cannot fire again until this many ticks of "cycle" (pump/bolt) pass. */
	public final int cycleTicks;

	public final float bodyDamage;
	public final float headDamage;
	/** Shotgun: pellets per trigger pull. 1 for everything else. */
	public final int pellets;

	/** Rest spread half-angle, degrees. Lower = more accurate. */
	public final float spreadDegrees;
	/** Extra spread added per shot while firing, degrees (assault-rifle bloom). */
	public final float recoilPerShotDegrees;
	/** Max accumulated recoil spread, degrees. */
	public final float maxRecoilDegrees;
	/** Vertical camera kick per shot, degrees (client-side, cosmetic + slight aim disturbance). */
	public final float verticalKickDegrees;
	/** How fast accumulated recoil decays, degrees/tick. */
	public final float recoilRecoveryPerTick;
	/** Spread multiplier while aiming down sights (scope) -- e.g. 0.15 = much tighter. */
	public final float adsSpreadFactor;

	public final double range;
	/** Beyond this distance, damage begins to fall off. */
	public final double effectiveRange;
	/** At or past {@link #range}, damage is multiplied by this (linear ramp from effectiveRange). */
	public final float minRangeDamageFactor;
	/** Close-range knockback strength on a direct hit. */
	public final double knockback;

	/** ADS / scope zoom steps. Empty = no ADS zoom. Values are FOV multipliers (< 1 = zoomed in). */
	public final float[] zoomLevels;
	/** Reduce mouse sensitivity to this fraction while scoped past the first zoom step. */
	public final float scopedSensitivityFactor;
	/** Draw the full sniper-scope overlay while aiming. */
	public final boolean scopeOverlay;

	public final SoundEvent fireSound;
	public final SoundEvent reloadSound;
	public final SoundEvent emptySound;
	public final SoundEvent cycleSound;
	/** Fire-sound pitch, so the four guns are distinguishable with vanilla placeholder audio. */
	public final float firePitch;
	public final float fireVolume;

	private FirearmData(Builder b) {
		this.id = b.id;
		this.ammo = b.ammo;
		this.magazineSize = b.magazineSize;
		this.reloadTicks = b.reloadTicks;
		this.shellReload = b.shellReload;
		this.fireIntervalTicks = b.fireIntervalTicks;
		this.automatic = b.automatic;
		this.cycleTicks = b.cycleTicks;
		this.bodyDamage = b.bodyDamage;
		this.headDamage = b.headDamage;
		this.pellets = b.pellets;
		this.spreadDegrees = b.spreadDegrees;
		this.recoilPerShotDegrees = b.recoilPerShotDegrees;
		this.maxRecoilDegrees = b.maxRecoilDegrees;
		this.verticalKickDegrees = b.verticalKickDegrees;
		this.recoilRecoveryPerTick = b.recoilRecoveryPerTick;
		this.adsSpreadFactor = b.adsSpreadFactor;
		this.range = b.range;
		this.effectiveRange = b.effectiveRange;
		this.minRangeDamageFactor = b.minRangeDamageFactor;
		this.knockback = b.knockback;
		this.zoomLevels = b.zoomLevels;
		this.scopedSensitivityFactor = b.scopedSensitivityFactor;
		this.scopeOverlay = b.scopeOverlay;
		this.fireSound = b.fireSound;
		this.reloadSound = b.reloadSound;
		this.emptySound = b.emptySound;
		this.cycleSound = b.cycleSound;
		this.firePitch = b.firePitch;
		this.fireVolume = b.fireVolume;
	}

	public boolean hasAds() {
		return zoomLevels.length > 0;
	}

	public static Builder builder(String id, AmmoKind ammo) {
		return new Builder(id, ammo);
	}

	/** Normalise a vanilla sound reference -- {@code SoundEvents} mixes raw {@link SoundEvent} and
	 *  {@link Holder} typed fields -- so the builder can take either. */
	public static SoundEvent s(SoundEvent e) {
		return e;
	}

	public static SoundEvent s(Holder<SoundEvent> h) {
		return h.value();
	}

	/** Mutable during construction only. */
	public static final class Builder {
		private final String id;
		private final AmmoKind ammo;
		private int magazineSize = 12;
		private int reloadTicks = 44;
		private boolean shellReload = false;
		private int fireIntervalTicks = 7;
		private boolean automatic = false;
		private int cycleTicks = 0;
		private float bodyDamage = 5f;
		private float headDamage = 7.5f;
		private int pellets = 1;
		private float spreadDegrees = 1.0f;
		private float recoilPerShotDegrees = 0f;
		private float maxRecoilDegrees = 0f;
		private float verticalKickDegrees = 0.6f;
		private float recoilRecoveryPerTick = 0.4f;
		private float adsSpreadFactor = 0.45f;
		private double range = 96.0;
		private double effectiveRange = 96.0;
		private float minRangeDamageFactor = 1.0f;
		private double knockback = 0.15;
		private float[] zoomLevels = new float[0];
		private float scopedSensitivityFactor = 1.0f;
		private boolean scopeOverlay = false;
		private SoundEvent fireSound = s(SoundEvents.GENERIC_EXPLODE);
		private SoundEvent reloadSound = s(SoundEvents.ARMOR_EQUIP_IRON);
		private SoundEvent emptySound = s(SoundEvents.TRIPWIRE_CLICK_ON);
		private SoundEvent cycleSound = s(SoundEvents.PISTON_CONTRACT);
		private float firePitch = 1.4f;
		private float fireVolume = 0.7f;

		private Builder(String id, AmmoKind ammo) {
			this.id = id;
			this.ammo = ammo;
		}

		public Builder magazine(int n) { this.magazineSize = n; return this; }
		public Builder reload(int ticks) { this.reloadTicks = ticks; return this; }
		public Builder shellReload(boolean v) { this.shellReload = v; return this; }
		public Builder fireInterval(int ticks) { this.fireIntervalTicks = ticks; return this; }
		public Builder automatic(boolean v) { this.automatic = v; return this; }
		public Builder cycle(int ticks) { this.cycleTicks = ticks; return this; }
		public Builder damage(float body, float head) { this.bodyDamage = body; this.headDamage = head; return this; }
		public Builder pellets(int n) { this.pellets = n; return this; }
		public Builder spread(float deg) { this.spreadDegrees = deg; return this; }
		public Builder recoil(float perShot, float max, float verticalKick, float recovery) {
			this.recoilPerShotDegrees = perShot; this.maxRecoilDegrees = max;
			this.verticalKickDegrees = verticalKick; this.recoilRecoveryPerTick = recovery; return this;
		}
		public Builder adsSpreadFactor(float f) { this.adsSpreadFactor = f; return this; }
		public Builder range(double r) { this.range = r; this.effectiveRange = r; return this; }
		public Builder falloff(double effective, double max, float minFactor) {
			this.effectiveRange = effective; this.range = max; this.minRangeDamageFactor = minFactor; return this;
		}
		public Builder knockback(double k) { this.knockback = k; return this; }
		public Builder zoom(float[] levels, float sensitivity, boolean overlay) {
			this.zoomLevels = levels; this.scopedSensitivityFactor = sensitivity; this.scopeOverlay = overlay; return this;
		}
		public Builder sounds(SoundEvent fire, SoundEvent reload,
				SoundEvent empty, SoundEvent cycle) {
			this.fireSound = fire; this.reloadSound = reload; this.emptySound = empty; this.cycleSound = cycle; return this;
		}
		public Builder firePitch(float p, float vol) { this.firePitch = p; this.fireVolume = vol; return this; }

		public FirearmData build() {
			return new FirearmData(this);
		}
	}
}
