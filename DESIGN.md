
# other

- armor that does something (e.g. small durability but resistant to arrows)
- custom killer tracker: any hit you get on them and then they die will count within n seconds of their hit. maybe track who did the most damage if theres multiple
  - DONE: make it only count if you live the next or 2 seconds after to prevent suicide bombs
- IDEA: if do this, make everything else have damage falloff
  - especially do this if hitscan
  - NO
- CONTINUE to add interactions between items, whether thats having one make another vulnerable (countering?) or use them together (chaining)
- DONE: party items (items you get when you die that help you and are chaotic)
  - DONE: horn shuffle
  - DONE: enderpearl that teleports someone else. maybe the guy ur looking at
  - DONE: make everyone glow so you can find them
  - add cooldown to an item lol
    - DONE: snowball does this now
  - spawn anvil above someone LOL
  - IDEA: remove on death? i dont really wanna do this
  - IDEA: spawn a buncha tnt minecarts around the player or in random places ig
- DONE: add custom death messages
- DONE: show scores on game end
- IDEA: beds can set spawn until destroyed
- DONE: accessibility: switch to previous slot on use offhand
- TODO: configurable map timer
  - nope too annoying to implement and too hacky
- IDEA: "10 kills without dying gets a nuke" so reverse party item
  - meh ig

- removed scoreboard so nametag hiding can work. maybe have message for time limit. not being able to see kills is kinda neat 

# flying

- GOAL: pure movement, transition between short/med/long
- 
- bigger radius for wither skulls? and higher knockback from above
- notify on reach lethal velocity for wall/floor hit??? or remove that entirely
- DONE-ISH: let players have more air control, while still requiring them to be grounded
  - done via wind charges. have to be near wall = more vulnerable
- higher gravity? limited rockets per air? higher knockback from skull/fireball?
- tunnel via breaking blocks while gliding?

# bow

- GOAL: good medium, inaccurate long, bad slow for short
- 
- arrow explode strength stronger for faster arrow. this makes spamming short range really hard
- bigger hitbox for flying players so you can hit them. but they stick to walls to go up, so explosion damage makes them more vulnerable there too
- cooldown on bow shots? overpowered
- 
- MAKE PLAYERS IN AIR WEAKER
    - already done needing to be near wall -> explosion radius -> die
    - make them bigger in air?
    - make arrows hit in air if near? teleport or just do explosion radius
    - !!!knock out of sky if not direct hit
    - okay rn this is way too powerful
    - made it just knock, no explode. brings em down. we fight on ground
- IDEA: turn into crossbow?
  - lose arrow speed control, but i can control charge duration
  - briefly testing, this makes it the bow TERRIBLE at short range (very slow). it hits the goal much better. idk if i like it tho
- IDEA: left click gives slowness for zoom?
  - no. doesnt work in air

# tnt

- GOAL: good for short, bad for long. mostly a cheeky funny item, something different from bow
- 
- arrows have no gravity, weaker explosion. tnt have gravity like grenade, stronger explosion
- short/medium range big explosion guy
- tnt left click to throw
- snow in ground, tnt too maybe occasionally. this means ability to break blocks instead of shoot skull
- big explosion = you die. how to fix?? smaller precision explosion
- 
- IDEA: air shot? just do arrow logic but explode.
  - this might throw balance off for diving in with mace? or could be cool counter 
  - could just reflect arrow instead? counter
  - OR do uh ultrakill coin thing
  - RESULT: coin cool, not too op. you can use it as compass
- IDEA: explode after seconds for cool kill and prevent long range?
  - or just make it longer between shots
  - or make it so you can manually explode like stickybomb
  - UPDATE: still kinda wanna try this...? you can already just explode small tnt by shooting an arrow at it
- might be too hard to escape? so if u shoot it the other person just dies lol
  - or make it counterable with arrow (ie shoot tnt to make it explode early)
  - UPDATE: u can do that now sorta lol with the passenger thing or fish
- IDEA: switch small tnt for minecart that slide along da ground
  - nope, just made small tnt better (and longer cooldown to balance it out)
- PROBLEM?: small tnt might be too overpowered, gotta test if u can win with only it lol
- make it extinguish fires???? idk why this was wanted but it was
- IDEA: big guy spawns another big guy on land that pops up in da air

# fish

- GOAL: get em out of short range
- GOAL: make em vulnerable
- 
- cause fire ticks, harms flying significantly. could make it random velocity on fire tick too
- reason for melee weapon? make it knockback. this should make it possible to hit and then impair their movement. could chain well with range -> use ranged weapon
  - also counters someone flying into you?
- this is a salmon now. bigger range to make it slap better. fire ticks might not impair movement enough rn
- could slap arrows/tnt for counter there
- okay we do that now, now its just this and avoiding that counters tnt
- PROBLEM: fire ticks inhibit movement (good), but they make it KILLS PACING (fast paced to none)
  - it means ur vulnerable, but you dont really get to do anything while vulnerable since you cant move much
  - how do you remain vulnerable while still giving a goal to the player lit on fire?
  - IDEA: glowing effect? mark for death? slowness?
  - okay i did that. i like
- IDEA: make YOU also bounce back in opposite direction
  - mace used to do this, but now just makes you reflect off in same direction but up
  - you can hit off ANYTHING, including your own wither skulls :P now you cant fly from ur own stuff
  - you also get the same velocity every time, you more than wind charge hit from ground
  - UPDATE: you go forward now. is cool
- bootleg suggested it go straight up. is kinda funny

# mace

- GOAL: move you to med/long, general movement, melee hit at close at speed. pairs well with flying
- 
- wind charge
  - GOOD: wind charges work really well. explosion on arrow is more forgiving then insta kill perfect aim move wind charge to separate item to enforce switch?
  - IDEA: kinda meh custom wind charge hit response (manually set velocity). rn its kinda finicky (you can only really go up).
- wider range for mace hit. bounce. will try. might want a way to block?
  - also counters someone just standing under you to try and get you? maybe?
- IDEA: less damage but mark for death, to chain with arrow
  - meh, you continue in direction so for now nah

# arrow
- wither skull
  - tunneling?
  - also u can pepper someone until they die
  - also wither effect 30 seconds :(
  - kinda makes bow obselete?
  - okay now its just tunneling thats it
  - IDEA: make it do a bit of damage
    - okay
  - IDEA: mark for death: arrows in range will explode like they used to (which was op). lasts for like 10 seconds
    - done for fish. wither skulls shouldnt be damager really... but maybe it should be
    - okay i did that aaaand it rarely happens
- IDEA: backstab melee... does this make grenade obsolete?
  - nope its fine, barely useful lol
  - okay it makes u silent when moving in the air
  - okay also i prevent knockback now but its not that useful
  - its another situational mace! like small tnt. but thats okay it works

# hitscan weapon?
- GOAL: the only long range weapon (bow has deviation and may get damage falloff). really hard to hit shots with no lag compensation
- 
- probably no big hitbox
- mc has bad hitreg but could be fun
- 
- PROBLEM: mc hitreg is so bad its impossible to hit. making hitboxes bigger might defeat the point of the bow lol
- 
- every kill i got with this is player standing still and felt way cheaper than the other weapons. i just made this a long punch for now

















# new unorganized ideas
multishot everything party item
horn is meh. make it different? like make it so no one can do any damage for n seconds? for evasion
nerf wind a bunch? make ride arrow primary air movement
give mace wind charge abiliy as party item? so you get that movement but only sometimes. kinda makes redundant the horn idea





More gravity guild ideas

Party items are dropped nearby players every minute, within 10 blocks with sound effect isntead of pop up message instead of on death

Every player's inventory is cleared every five minutes (besides main weapons) to make people use party items more often as opposed to just holding onto them
to balnce this out more, item drops form destroyed blocks could be removed so people dont have to search thu the inventory for party items (gamerule does this)


a possible command to disable party items before game all together

Instead of party items having a time duration they should have a usage duration, like tree wand would be 10 saplings stack with it using one each time its used
(this would get rid of the bug of refreshing the duration by putting it in offhand manually)

tnt on arrow movve slower 
certain game modifiers could change how the game is started

gg join arena -alpha

would give only bow and arrow, as opposed to the main items

gg join arena -party

would give 5 counts of every party item and fish but no main weapons, for instance 
im realizing half of these ideas remove the need to look at the inventory ever so often




tnt on arrow moves slow, and every secound it drops a tnt that falls and explodes 


ender pearl teleports behind instead of at guy (unless it would put u inside wall)




put windcharges on 3 sec cooldown and tnt can ride it



every time die u, u get upgrade to an item (no this seems annoying to design)