const fs=require('fs');
function edit(f,pairs){let s=fs.readFileSync(f,'utf8').replace(/\r\n/g,'\n');for(const [a,b] of pairs){const n=s.split(a).length-1;if(n!==1)throw f+': '+n+' matches for: '+a.slice(0,80);s=s.replace(a,b);}fs.writeFileSync(f,s.replace(/\n/g,'\r\n'));}
const P='src/main/java/com/projecthero/mod/ironman/';
const AIM='com.projecthero.mod.ironman.IronManTargeting.aimLook';
edit(P+'IronManTargeting.java',[[
`	private static void setLock(ServerPlayer player, LivingEntity target) {`,
`	/**
	 * v0.14.30: the direction every aimed ability fires in -- straight from the eyes at the locked target when there is
	 * one within {@code range}, otherwise along the crosshair. Explicit user request: "the player's abilities are
	 * automatically aimed at whatever the player is currently targeting".
	 */
	public static Vec3 aimLook(ServerPlayer player, double range) {
		return aim(player, player.getEyePosition(), player.getLookAngle().normalize(), range);
	}

	private static void setLock(ServerPlayer player, LivingEntity target) {`]]);
const A=P+'ability/IronManAbilities.java';
edit(A,[
 // classic micro-missiles (Mark 7 wheel)
 [`		Vec3 look = player.getLookAngle();
		Vec3 dir = look.add((level.random.nextDouble() - 0.5) * 0.12,`,
  `		Vec3 look = ${AIM}(player, 100); // v0.14.30: launched at the lock
		Vec3 dir = look.add((level.random.nextDouble() - 0.5) * 0.12,`],
 // homing missiles target pick
 [`	public static LivingEntity homingTarget(net.minecraft.world.entity.player.Player player) {
		Vec3 eye = player.getEyePosition();`,
  `	public static LivingEntity homingTarget(net.minecraft.world.entity.player.Player player) {
		if (player instanceof ServerPlayer sp) { // v0.14.30: the targeting lock wins
			LivingEntity lock = com.projecthero.mod.ironman.IronManTargeting.lockedWithin(sp, HOMING_RANGE);
			if (lock != null) {
				return lock;
			}
		}
		Vec3 eye = player.getEyePosition();`],
 // flamethrower damage cone
 [`		ServerLevel level = (ServerLevel) player.level();
		Vec3 look = player.getLookAngle();
		Vec3 origin = player.getEyePosition();

		// v0.14.28`,
  `		ServerLevel level = (ServerLevel) player.level();
		Vec3 origin = player.getEyePosition();
		// v0.14.28 / v0.14.30: the stream's reach, and its damage cone points at the lock when there is one in reach
		Vec3 look = ${AIM}(player, flamethrowerReach(suit));

		// v0.14.28`],
 // wrist laser
 [`		LivingEntity target = AbilityHelpers.raycastEntity(player, WRIST_LASER_RANGE);
		com.projecthero.mod.ironman.IronManAbilityFx.hold(player, com.projecthero.mod.ironman.IronManAbilityFx.LASER); // v0.14.26 pose`,
  `		LivingEntity target = com.projecthero.mod.ironman.IronManTargeting.lockedWithin(player, WRIST_LASER_RANGE); // v0.14.30: the lock first
		if (target == null) {
			target = AbilityHelpers.raycastEntity(player, WRIST_LASER_RANGE);
		}
		com.projecthero.mod.ironman.IronManAbilityFx.hold(player, com.projecthero.mod.ironman.IronManAbilityFx.LASER); // v0.14.26 pose`],
]);
const F=P+'ability/IronManFlares.java';
edit(F,[
 [`	public static boolean fire(ServerPlayer player, boolean advanced, int cooldownTicks, float burnDamage) {`, `	public static boolean fire(ServerPlayer player, boolean advanced, int cooldownTicks, float burnDamage) {`],
]);
let fl=fs.readFileSync(F,'utf8').replace(/\r\n/g,'\n');
const flN=fl.split('		Vec3 look = player.getLookAngle().normalize();\n').length-1; if(flN!==2) throw 'flares count '+flN;
fl=fl.split('		Vec3 look = player.getLookAngle().normalize();\n').join(`		Vec3 look = ${AIM}(player, RANGE); // v0.14.30: thrown at the lock\n`);
fs.writeFileSync(F,fl.replace(/\n/g,'\r\n'));
const S=P+'ability/IronManSonicClap.java';
let sc=fs.readFileSync(S,'utf8').replace(/\r\n/g,'\n');
const scN=sc.split('		Vec3 look = player.getLookAngle().normalize();\n').length-1; if(scN!==2) throw 'clap count '+scN;
sc=sc.split('		Vec3 look = player.getLookAngle().normalize();\n').join(`		Vec3 look = ${AIM}(player, RANGE + 4.0); // v0.14.30: the cone points at the lock\n`);
fs.writeFileSync(S,sc.replace(/\n/g,'\r\n'));
edit(P+'ability/IronManDash.java',[[
`		Vec3 look = player.getLookAngle();
		// on the ground`,
`		Vec3 look = ${AIM}(player, 24.0); // v0.14.30: dash at the locked target
		// on the ground`]]);
console.log('ok');
