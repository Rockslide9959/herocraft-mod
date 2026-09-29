// v0.13.21: Gamma Serum + Gamma Reactor overload lang.
require('./langset.js')([
	{ anchor: null, entries: {
		'item.projecthero.gamma_serum.desc1': 'Drink it, then right-click a Gamma Reactor. It will explode -- the Gamma in your blood is what lets you walk out as the Hulk.',
		'message.projecthero.gamma_serum.dosed': 'Gamma radiation burns through your veins...',
		'message.projecthero.gamma_serum.dosed_hint': 'It is not enough on its own. Find a Gamma Reactor and right-click it -- if you dare.',
		'message.projecthero.gamma_serum.already_dosed': 'The Gamma Serum is already in your blood. Find a Gamma Reactor.',
		'message.projecthero.gamma_reactor.not_dosed': 'The reactor hums. Without Gamma in your blood, touching the core would kill you.',
		'message.projecthero.gamma_reactor.hulk': 'The reactor\'s glow makes your blood boil.',
		'message.projecthero.gamma_reactor.overload': 'THE REACTOR IS GOING CRITICAL!',
		'message.projecthero.gamma_reactor.survived': 'You should be dead. Instead, something inside you is awake.',
	} },
]);
