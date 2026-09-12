# MobTag

A lightweight Minecraft plugin that displays health tags above mobs.

MobTag renders a health tag above every mob a player can see — as a client-side text display when
PacketEvents is installed, or as an invisible armor stand otherwise. Nothing is written to the world,
tags are per player, and every player can turn them off for themselves.

<img width="965" height="773" alt="imagez1" src="https://github.com/user-attachments/assets/c189a7f4-6bcc-4cee-bbef-5d680d28c36a" />

<img width="686" height="536" alt="imagec1" src="https://github.com/user-attachments/assets/0ee00a39-b254-45f9-b2a1-5336a44f21dc" />

<img width="872" height="675" alt="imagesh1" src="https://github.com/user-attachments/assets/2b807a65-95c6-4caa-8721-a6510417a042" />

<img width="770" height="550" alt="images1" src="https://github.com/user-attachments/assets/6c9b058a-c529-4e74-ad36-956ed6c37fc5" />

---

## Features

- **Health tags above mobs** with a configurable format, health bar and colour gradient.
- **Two rendering backends** — packet-based text displays (smooth, no server entities) or armor
  stands as a universal fallback. `AUTO` picks the best one available.
- **Per-player visibility.** Every tag is shown or hidden per viewer, in both backends.
- **Visibility modes** — `ALL`, `DAMAGER` (only whoever last hit the mob), or `LOOKING_AT`
  (only what you are aiming at, with an optional line-of-sight requirement).
- **Floating damage indicators** with motion, fade-out, critical-hit formatting, stacking of
  repeated hits and a per-player cap.
- **Player health tags**, off by default, with their own format, range and height offset.
- **Bedrock support** via Floodgate (or a username prefix), with separate height offsets, because
  Geyser anchors tags differently.
- **Filtering** for passive mobs, bosses, invisible mobs, vanished players, NPCs and pets.
- **Integrations** — PlaceholderAPI (both directions), MythicMobs, PacketEvents, Floodgate.
- **MiniMessage** support when the server provides it, alongside legacy `&` and `&#RRGGBB` colours.
- **In-game settings menu** at `/mobtag`, persisted per player.

---

## Requirements

| | |
|---|---|
| Server | Spigot, Paper or a fork, **1.21+** |
| Java | 17 or newer |
| Optional | PacketEvents (smooth tags + damage indicators), PlaceholderAPI, MythicMobs, Floodgate |

MobTag is built against the Spigot API for 26.2 and declares `api-version: 1.21`, which is the
oldest release its API usage is compatible with. Without PacketEvents it falls back to armor
stands; damage indicators and tag animations require PacketEvents.

---

## Installation

1. Drop `MobTag-x.y.z.jar` into your server's `plugins/` folder.
2. *(Recommended)* Install [PacketEvents](https://www.spigotmc.org/resources/80279/) for smooth
   tags and damage indicators.
3. Start the server once to generate `plugins/MobTag/config.yml`.
4. Edit the config, then run `/mobtag admin reload`.

Upgrading is drop-in: new config keys are merged into your existing `config.yml` automatically and
your current settings are preserved.

---

## Commands

| Command | Permission | Description |
|---|---|---|
| `/mobtag` | `mobtag.toggle` | Opens the settings menu |
| `/mobtag toggle mobs` | `mobtag.toggle` | Toggles mob health tags for yourself |
| `/mobtag toggle players` | `mobtag.toggle` | Toggles player health tags for yourself |
| `/mobtag toggle damage` | `mobtag.toggle` | Toggles damage indicators for yourself |
| `/mobtag admin reload` | `mobtag.admin` | Reloads `config.yml` |
| `/mobtag admin debug` | `mobtag.admin` | Shows tag counts, loop timing and hook status |

## Permissions

| Permission | Default | Description |
|---|---|---|
| `mobtag.use` | everyone | Required to run `/mobtag` at all |
| `mobtag.toggle` | everyone | Settings menu and personal toggles |
| `mobtag.admin` | op | Reload, debug, and update notifications on join |

---

## Configuration

Every option is documented inline in [`config.yml`](src/main/resources/config.yml). The settings
people reach for most often:

| Key | Default | What it does |
|---|---|---|
| `display-mode` | `AUTO` | `AUTO`, `PACKET` or `ARMORSTAND` |
| `visibility-mode` | `ALL` | `ALL`, `DAMAGER` or `LOOKING_AT` |
| `update-interval` | `4` | Ticks between refreshes. Lower is smoother and costs more CPU |
| `display-range` | `16.0` | How close a player must be to see a tag |
| `tag-format` | `&c❤ &f{current}&7/&f{max}` | The tag itself |
| `hide-at-full-health` | `false` | Only show tags on damaged mobs |
| `hide-after-seconds-out-of-combat` | `0` | Hide tags once a mob leaves combat. `0` disables |
| `ignore-passive` | `false` | Skip animals and other non-hostile mobs |
| `player-tags.enabled` | `false` | Health tags above players |
| `damage-indicator.enabled` | `true` | Floating damage numbers (needs PacketEvents) |

### Tag placeholders

`{name}` · `{type}` · `{current}` · `{max}` · `{bar}` · `{percent}` · `{mythic_name}`

PlaceholderAPI placeholders work inside tag formats too, and are resolved per viewer.

### Colours

Legacy `&a` codes, `&#RRGGBB` hex, and MiniMessage tags such as
`<gradient:#ff0000:#00ff00>` when `use-minimessage` is on and the server ships Adventure.
On servers without it, MobTag falls back to legacy colours automatically.

### Provided PlaceholderAPI placeholders

| Placeholder | Returns |
|---|---|
| `%mobtag_nearby_mob_health%` | Health of the nearest tagged mob |
| `%mobtag_nearby_mob_maxhealth%` | Its max health |
| `%mobtag_nearby_mob_percent%` | Its health percentage |
| `%mobtag_nearby_mob_bar%` | Its rendered health bar |
| `%mobtag_nearby_mob_name%` | Its name |
| `%mobtag_tags_hidden%` | Whether this player hid mob tags |
| `%mobtag_indicators_hidden%` | Whether this player hid damage indicators |

---

## Building

Requires JDK 17+ and Maven. The Maven wrapper is not committed, so use your own `mvn`.

```bash
# standard build -> target/MobTag-x.y.z.jar
mvn clean package

# release build with obfuscation
mvn clean package -Pobfuscate
```

The `obfuscate` profile runs ProGuard in rename-only mode (shrinking and optimisation are
deliberately disabled, since a Bukkit plugin reaches a lot of code reflectively). It keeps the main
class, `@EventHandler` methods, the PlaceholderAPI expansion and the shaded bStats package.

bStats is shaded and relocated into `me.abood8001.mobtag.bstats`. Everything else is `provided`
and expected on the server.

---

## License

See [LICENSE](LICENSE).
