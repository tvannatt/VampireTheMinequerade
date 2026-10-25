# Vampire: The Minequerade

A Minecraft mod (NeoForge 1.21.1) that turns you into a vampire, based on Vampire: The Masquerade.

This is a fan project. It's free, non-commercial, and not official or affiliated with Paradox Interactive. I don't own any of the World of Darkness stuff.

## What it does right now

You can be turned into a vampire, kinda. (WIP lol.)

- **Vampire data.** Clans, Blood Potency, Blood levels, and Discipline lists are built, but none of these have behavior currently.
- **Clans.** Toreador, Ventrue, Gangrel, Brujah and Tremere. Maybe more in the future, idk.
- **Blood.** You lose a point once a minute. Can't eat food either.
- **Sunlight.** Standing in the sun gives you Wither and Nausea. Stops (almost) immediately upon leaving the sun.

### Commands

`/vampire ...`

| Command | What it does | Who |
|---|---|---|
| `info` | Shows your clan, blood, blood potency and disciplines | Anyone |
| `turnplayer [target]` | Turns you (or the target) into a vampire with a random clan | Ops |
| `removevampirism [target]` | Removes all vampire data | Ops |
| `heal [target]` | Fills blood and health | Ops |
| `setblood <blood> [target]` | Sets blood | Ops |
| `setbloodpotency <bloodpotency> [target]` | Sets blood potency | Ops |
| `changeclan <clan> [target]` | Changes clan and gives the new clan's starting disciplines | Ops |

The commands are mostly for testing. `info` is going to turn into a proper screen.

## What it's going for

I want the vampire status of the player to become more of a new system of play, rather than entire progression-based mechanic. Less farming, more 'you are this now.'

- **Disciplines.** Each one has 5 levels with a set of powers per level. This is V5 inspired.
- **Clans that matter.** Right now your clan is random. When I add mobs and events, it'll be based on that.
- **Everything runs on the server.** The client sends input and shows the result. Blood, cooldowns and effects are the server's job.

## Where it's at

Pretty early. Vampire state managemet exists and some of the 'mechanics' exist (sun damage, blood hunger), but it's **far** from playable.
I haven't tested it on a dedicated server yet.

Next up, roughly in order:
1. Blood bar that replaces the food bar
2. Block eating
3. Feeding
4. First discipline, all the way through (keybind, packet, effect)
5. Picking a clan instead of getting a random one

## Building

You need JDK 21. 

```
./gradlew runClient     # launch Minecraft with the mod
./gradlew runServer     # launch a dedicated server
./gradlew build         # build the jar into build/libs
```

On Windows PowerShell, use `.\gradlew` instead of `./gradlew`.

If you're missing libraries in your IDE, or something is broken, run `gradlew --refresh-dependencies` to refresh the local cache. `gradlew clean` resets everything (this doesn't touch your code), then start over.

## Mapping Names

By default this uses the official mapping names from Mojang for methods and fields in the Minecraft codebase. These names are covered by a specific license. All modders should be aware of this license. For the latest license text, refer to the mapping file itself, or the reference copy here:
https://github.com/NeoForged/NeoForm/blob/main/Mojang.md

## Additional Resources

Community Documentation: https://docs.neoforged.net/  
(Maybe I'll do a wiki if I like the end product, we'll see.)
