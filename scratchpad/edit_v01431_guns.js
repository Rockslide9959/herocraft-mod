const fs=require('fs');
function edit(f,pairs){let s=fs.readFileSync(f,'utf8').replace(/\r\n/g,'\n');for(const [a,b] of pairs){const n=s.split(a).length-1;if(n!==1)throw f+': '+n+' matches for: '+a.slice(0,80);s=s.replace(a,b);}fs.writeFileSync(f,s.replace(/\n/g,'\r\n'));}
const P='src/main/java/com/projecthero/mod/';
fs.writeFileSync(P+'firearm/Gunfire.java',`package com.projecthero.mod.firearm;

import java.util.function.BooleanSupplier;

/**
 * v0.14.31: marks a hit as gunfire. Guns deal ordinary attack damage (a player attack, a mob attack), so nothing in the
 * {@code DamageSource} says "bullet" -- every gun wraps its {@code hurt} call in {@link #hit} and damage listeners
 * (Iron Man's bulletproof armour) read {@link #active}. Server thread only; re-entrant.
 */
public final class Gunfire {
	private static int depth;

	private Gunfire() {
	}

	public static boolean hit(BooleanSupplier hurt) {
		depth++;
		try {
			return hurt.getAsBoolean();
		} finally {
			depth--;
		}
	}

	/** True while a gun's damage is being applied. */
	public static boolean active() {
		return depth > 0;
	}
}
`.replace(/\n/g,'\r\n'));
edit(P+'firearm/FirearmShooting.java',[['				target.hurt(source, dmg);\n','				final float bullet = dmg;\n				Gunfire.hit(() -> target.hurt(source, bullet)); // v0.14.31: gunfire marker (Iron Man armour is bulletproof)\n']]);
edit(P+'syndicate/SyndicateGunfire.java',[['			boolean hurt = target.hurt(source, damage);\n','			final float bullet = damage;\n			boolean hurt = com.projecthero.mod.firearm.Gunfire.hit(() -> target.hurt(source, bullet)); // v0.14.31: gunfire marker\n']]);
edit(P+'ironman/IronManDamage.java',[[`		String suitId = IronManArmor.wornSuitId(player);
		IronManSuit suit = suitId == null ? null : IronManSuits.byId(suitId);
`,`		String suitId = IronManArmor.wornSuitId(player);
		IronManSuit suit = suitId == null ? null : IronManSuits.byId(suitId);

		// v0.14.31, explicit user request: Iron Man armour is bulletproof -- gunfire does nothing to a wearer whose
		// chestplate is on (powered or not), and never touches the suit's integrity or energy
		if (suit != null && IronManArmor.hasChestplate(player, suitId) && com.projecthero.mod.firearm.Gunfire.active()) {
			return false;
		}
`]]);
// knockback 0.175 per piece = 70% with the full suit
const M=P+'ironman/item/IronManArmorMaterials.java';
let m=fs.readFileSync(M,'utf8').replace(/\r\n/g,'\n');
let n=0;
m=m.replace(/(public static final Holder<ArmorMaterial> (MARK_[0-9A-Z]+) = register\("[a-z0-9_]+",\n\t\t\tMap\.of\([^\n]*\),\n\t\t\t\d+, SoundEvents\.[A-Z_]+, [0-9.]+f, )([0-9.]+f)/g,(all,pre,name,kb)=>{n++;return pre+'0.175f';});
if(n!==7) throw 'materials matched '+n;
m=m.replace('public final class IronManArmorMaterials {','public final class IronManArmorMaterials {\n\t// v0.14.31, explicit user request: every mark\'s pieces carry 0.175 knockback resistance each -- 70% with the full suit.');
fs.writeFileSync(M,m.replace(/\n/g,'\r\n'));
// repulsor range 50
const A=P+'ironman/ability/IronManAbilities.java';
let a=fs.readFileSync(A,'utf8').replace(/\r\n/g,'\n');
const cnt=(a.match(/fireRepulsor\(player, [^;]*?, (24\.0|32\.0)\);/g)||[]).length; if(cnt!==3) throw 'repulsor calls '+cnt;
a=a.replace(/fireRepulsor\(player, ([^;]*?), (24\.0|32\.0)\);/g,'fireRepulsor(player, $1, REPULSOR_RANGE);');
a=a.replace('public final class IronManAbilities {','public final class IronManAbilities {\n\t/** v0.14.31, explicit user request: every repulsor blast (tap, charged, gadget) reaches 50 blocks. */\n\tpublic static final double REPULSOR_RANGE = 50.0;');
fs.writeFileSync(A,a.replace(/\n/g,'\r\n'));
console.log('ok');
