// v0.16.7 (user): Kwami chatter lines -- message.projecthero.kwami_chat.<kwami>.<event>.<i> -- inserted after the
// "message.projecthero.miraculous.kwami_hidden" line. Usage: node scratchpad/kwami_chatter_lang_v0167.js
'use strict';
const fs = require('fs');
const path = require('path');
const F = path.join(__dirname, '..', 'src/main/resources/assets/projecthero/lang/en_us.json');
const L = {
	tikki: {
		countdown: ['Hurry! Your Miraculous is about to run out!', 'Only one spot left -- find somewhere to hide, quick!'],
		danger: ['I sense something dangerous nearby. Be careful!', 'Something is wrong here. Maybe it is time to transform?'],
		hurt: ['You are hurt! Please be careful!', "Don't give up! You can do this!"],
		victory: ['You did it! I knew you could!', 'That was amazing! Paris -- er, everyone -- is safe thanks to you!'],
		idle: ['Have you got any cookies? Just asking...', 'Being a hero is about helping people, not about being noticed.', 'I believe in you. You know that, right?'],
	},
	plagg: {
		countdown: ['Tick tock, kid. You are about to turn back into a pumpkin.', "One spot left. Don't make me miss my cheese break."],
		danger: ['Ugh, something nasty is out there. Can we just go home?', "I'd say transform, but honestly I'd rather nap."],
		hurt: ['Hey, you look terrible. Try not to get flattened, okay?', 'Careful! If you go down, who buys my Camembert?'],
		victory: ['Not bad, kid. That deserves a whole wheel of Camembert.', 'See? Destruction has its perks.'],
		idle: ['Is it cheese time yet? It feels like cheese time.', 'You know what this world needs? More Camembert.', 'Wake me when something interesting happens.'],
	},
	trixx: {
		countdown: ['Time is nearly up! Better find a good hiding spot -- foxes always do.', "One spot left! Let's not get caught, shall we?"],
		danger: ["Something sneaky is around. Lucky we're sneakier.", 'I smell trouble. Want to play a little trick on it?'],
		hurt: ["You're hurt! Even foxes need to retreat sometimes.", 'Careful! A good illusion beats a bruise any day.'],
		victory: ["Ha! They never saw that coming. Well -- they saw something...", 'Wonderfully done! Not even a fox could have planned it better.'],
		idle: ['Have you ever fooled someone so well they thanked you for it?', "Macarons, please. For energy. Definitely not because they're delicious.", 'Truth and illusion are closer than you think.'],
	},
	nooroo: {
		countdown: ['M-Master, your time is almost up!', 'Please, the transformation will end very soon...'],
		danger: ['I feel strong emotions nearby... something dangerous.', 'Please be careful, Master. Something is out there.'],
		hurt: ["You're badly hurt! Please, rest...", 'Master, please... this is too dangerous.'],
		victory: ['You... you did it. Please use this power for good.', "It's over. Thank you for being careful."],
		idle: ['The butterfly is meant to give hope, not fear.', 'Thank you for treating me kindly.', 'A little honey would be lovely, if you have some.'],
	},
	wayzz: {
		countdown: ['Patience -- but not too much. Your time is nearly spent.', 'The last spot remains. Withdraw while you still can.'],
		danger: ['Danger is near. A wise hero is prepared before it arrives.', 'I sense a threat. Stay calm and stay together.'],
		hurt: ['You are wounded. Protecting others starts with protecting yourself.', 'Fall back and gather your strength.'],
		victory: ['Well done. You stood firm, and your friends are safe.', 'Victory belongs to those who protect, not those who strike.'],
		idle: ['A turtle does not rush. And yet, it always arrives.', 'Remember: a shield is only as strong as the heart behind it.', 'A little dried kelp would be most appreciated.'],
	},
	kaalki: {
		countdown: ['Your time grows short. Do not embarrass us both.', 'One spot remains. Depart with dignity, please.'],
		danger: ['A threat approaches. I suggest a swift departure -- or a swift transformation.', 'Danger, nearby. How tiresome.'],
		hurt: ['You are injured. That is most unbecoming.', 'Withdraw at once. A rider does not fall.'],
		victory: ['Adequate. Perhaps even impressive.', 'A fine gallop. I approve.'],
		idle: ['I trust you are keeping my glasses polished.', 'Sugar would be appropriate at this time.', 'Every door leads somewhere. Choose wisely.'],
	},
	sass: {
		countdown: ['The last moments are slipping away. Choose your next step carefully.', 'Time runs short. Even second chances end.'],
		danger: ['Something stirs nearby. Listen before you act.', 'I sense danger. Patience will serve you better than haste.'],
		hurt: ['You are hurt. Wisdom is knowing when to try again.', 'Breathe. A wise snake strikes only when ready.'],
		victory: ['Well chosen. Every path led here.', 'Calm, and clever. You did well.'],
		idle: ['Every moment is a chance to choose differently.', 'An egg, if you have one, would be most welcome.', 'Time is a river. Learn its currents.'],
	},
};
let s = fs.readFileSync(F, 'utf8');
const crlf = s.includes('\r\n');
s = s.replace(/\r\n/g, '\n');
if (s.includes('"message.projecthero.kwami_chat.')) { console.log('already there'); process.exit(0); }
const anchor = s.match(/  "message\.projecthero\.miraculous\.kwami_hidden": "[^\n]*\n/);
if (!anchor) throw new Error('anchor missing');
const lines = [];
for (const [k, ev] of Object.entries(L)) for (const [e, arr] of Object.entries(ev)) arr.forEach((t, i) =>
	lines.push(`  "message.projecthero.kwami_chat.${k}.${e}.${i + 1}": ${JSON.stringify(t)},`));
s = s.replace(anchor[0], anchor[0] + lines.join('\n') + '\n');
JSON.parse(s);
fs.writeFileSync(F, crlf ? s.replace(/\n/g, '\r\n') : s);
console.log('added', lines.length);
