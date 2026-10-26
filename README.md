# FT's Geology: Lost Signal

Nights at a fire lookout, for [FT's Geology](https://github.com/Jelada-Studios/fts-geology) and
[Feel the Nature](https://github.com/bearstate/FT-s-Feel-the-Nature). Forge 1.20.1.

Feel the Nature puts fire lookouts on FT's Geology's summits. **Lost Signal gives them people, a radio, and nights.**
Some are kept by a keeper you can talk to, some stand empty, one was left in a hurry with its door open. On the rescue
frequency the District calls its checks, the lookouts round you answer, and some evenings a hiker lost on the
mountain calls for help. And there is a man in the woods who is friendly by day, who listens to the radio too, and
who comes to the door at night in a mask.

---

## Key Highlights

- **Lookouts that are lived in**: each lookout you find is empty, kept by a keeper, abandoned in a hurry, or kept by
  someone who is not a keeper. They change from dawn to dawn: a keeper goes down for supplies, a new one arrives, one
  is not heard from again.
- **Keepers**: talk to them face to face, ask what's new and what they've seen, run their errands (coal for the generator,
  a look at a neighbour who has gone quiet), stay the night with them once they trust you.
- **The radio network** on 145.500 MHz: call the District and the lookouts round you from the rescue panel (`J`, at a
  station). The District keeps its day: the morning radio check, the weather, the end of day, the rules at dusk.
- **Lost hikers**, real walkers out on the mountain, not scripts: they describe what they see, follow the water down
  or the trail or a board's arrow when told, fire flares and make smoke, walk toward the lamp on your walkway, tire,
  fall and get hurt. Bring them in before dawn.
- **The fire finder**: sight a flare, a smoke or a lamp on the cartography table in the cab for its bearing and
  distance, and send the hiker the way back.
- **Lost yourself**: out on the mountain with a handheld on 145.500, call the lookouts round you. A keeper puts a
  flare up from the tower to walk towards, lights the lamp at night, and takes a bearing on yours to tell you the way
  in. Flares come from the keepers and the supply crates, or are made (paper, gunpowder, redstone, string); a lantern
  used at the air is swung over your head, seen from far off at night.
- **The lookout terminal**: a 1994 tube monitor on the desk, run off the station's power: the District's messages,
  the duty roster, the one o'clock fire weather observation (read the instruments yourself and type it in; the District
  checks your figures), smoke reports, orders against your weekly pay credit, and your record. Posted at a lookout for
  the season, by Serene Seasons' seasons or a calendar of its own; a fire danger rated each morning, and a sign to
  set by it; lightning on the lookouts in a storm (stand on the glass-footed stool); a Lookout advancement tab.
- **Forest fires**: smoke columns far off through the haze, a glow at night. Read them off the fire finder, report
  them, and another keeper's bearing crosses yours; the crew's headlamps come up the valley, and they may ask you to
  relay for them. A compass tape in the spyglass. Nothing in the world burns; supplies come up for good eyes.
- **The stranger**: an ordinary man by day, who says things about the forest that stay with you. At night, a black
  horned mask, red eyes, a ritual robe: footsteps on the stair, three knocks, a face at the window. Hide under the bed
  (crouch and use it). Don't open the door.
- **What he does with the radio**: calls from someone who is not who they say, and sometimes your own voice, taped
  from what you said on the frequency, played back to you from somewhere you are not (Simple Voice Chat).
- **What is left**: where someone was taken, a broken camp, a tent down, dark stains and a trail into the trees,
  square footprints, a journal.
- **Flowing sleep**: the night runs by fast while you sleep instead of jumping to morning; the radio and a knock wake
  you.
- **Every fright can be turned down or off**, per world.
- **English and Turkish.**

## Requirements

- Minecraft 1.20.1, Forge 47.2 or newer
- **FT's Geology 0.11.4** or newer
- **Feel the Nature 1.2.0** or newer
- **Simple Voice Chat** (Forge 1.20.1, 2.6 or newer)
- **The Simple Radio ham fork** (simpleradio 1.20.1-4.5.7.9-ham or newer) and Lexiconfig. Simple Radio as published
  does not have the lookouts' stations and power; the game says so at start if it is the wrong one.

Install on the server and on every client.

## Settings

In each world's `serverconfig/fts_lostsignal-server.toml` (change it with the world closed or the server stopped):

| Section | Setting | What it does |
|---|---|---|
| `stranger` | `enabled` | The stranger at all: sightings, visits, the radio, taking anyone, keeping a lookout |
| | `sightings`, `visits` | How often he is seen, and comes to the door (0 to 5, 1 as designed) |
| | `talks` | By day he may come up to talk |
| | `fakeCalls`, `echoVoices` | Calls from someone who is not who they say; a player's own voice played back. Off, nothing said is kept |
| | `takesHikers`, `takesKeepers`, `keepsLookouts` | Whom he may take, and whether some lookouts are his |
| `remains` | `stains`, `bodies` | Dark stains and drag marks; a body at some scenes (else the journal is found in the tent) |
| `hikers` | `enabled`, `frequency`, `falls`, `monsters` | Lost hikers, how often, whether they fall and are hurt, and whether the night's monsters hunt them |
| `lookouts` | `districtCalls`, `supplies`, `fires`, `fireFrequency`, `lightning` | The District's day, supply crates, fires, lightning on the lookouts |
| `desk` | `enabled`, `horror` | The terminal, the duty roster, pay credit and orders, the season; what a duty let go costs at night |
| `sleep` | `flowing` | The flowing night; turn it off if another mod handles sleep |

## Building

The addon compiles against the mods it stands on, which are not in this repository. Copy into `libs/`, named so:
`ls-core-0.11.4.jar` (FT's Geology), `ls-ftn-1.2.0.jar` (Feel the Nature), `ls-simpleradio-4.5.7.9.jar` (the ham
fork), `ls-voicechat-2.6.22.jar` (Simple Voice Chat, Forge) and `ls-lexiconfig-1.4.21.jar`; then

```
./gradlew build
```

The jar is written to `build/libs/`. `./gradlew runServer` starts a dedicated server in `run/server` to test against.

## License

Copyright (C) 2026 Jelada Studios. Lost Signal is free software under the GNU General Public License v3.0 or later:
see `LICENSE`. The recordings are credited in `CREDITS-sounds.md`.
