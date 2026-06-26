# Disguises

A powerful, server-side Fabric disguise mod inspired by **LibsDisguises**.  
Disguise players or any entity as virtually any mob, animal, player, block display, item, vehicle, or projectile, with fine-grained per-entity customisation, equipment overrides, self-view support, and a fully configurable permission system.

---

## Features

### Disguise anything as anything
- **Players** can disguise as any mob, animal, player skin, or inanimate entity
- **Server-side mobs and entities** can also be disguised (e.g. disguise a zombie as a villager)
- **Observer-specific overrides**, make one player see a different disguise than everyone else
- Disguises persist across chunks and dimensions (configurable)

### Self-view support
Players can optionally see their own disguise from a third-person puppet perspective, mirroring their crouch, sprint, swim, and elytra state in real time.

### Sound replacement
Ambient, hurt, death, and footstep sounds are automatically replaced with the disguise type's sounds.

### Extensive entity customisation
Every disguise type supports entity-specific flags set inline with the disguise command:

| Category | Example flags |
|----------|--------------|
| Universal | `setFire`, `setInvisible`, `setGlowing`, `setCustomName`, `setCrouching` |
| Living entities | `setHealth`, `setBaby` |
| Sheep | `setColor RED`, `setSheared` |
| Creeper | `setPowered`, `setIgnited` |
| Wolf / Cat | `setTamed`, `setAngry`, `setCollarColor BLUE` |
| Villager | `setVillagerType plains`, `setProfession librarian`, `setVillagerLevel 3` |
| Horse | `setHorseColor chestnut`, `setHorseStyle white_field` |
| Panda | `setMainGene playful`, `setHiddenGene brown` |
| Shulker | `setAttachFace north`, `setPeek 50`, `setShulkerColor CYAN` |
| Armor Stand | `setSmall`, `setShowArms`, `setHeadPose 0 45 0` |
| Block Display / Falling Block / Minecart | `setBlock diamond_block` |
| Item entity | `setItem diamond` |
| All living disguises | `setHelmet iron_helmet`, `setMainHand diamond_sword` |

Full tab-completion for all flags and their values is supported.

---

## Supported Entity Types

**100+ entity types** across all categories:

- **Passive animals**: Cow, Sheep, Pig, Horse, Donkey, Mule, Cat, Wolf, Fox, Panda, Axolotl, Frog, Goat, Bee, Strider, Parrot, Rabbit, Llama, Turtle, Mooshroom, Villager, Iron Golem, Snow Golem, and more
- **Hostile / neutral mobs**: Zombie, Skeleton, Creeper, Enderman, Blaze, Warden, Breeze, Pillager, Piglin, Ravager, Shulker, Phantom, Slime, Magma Cube, and more  
- **Player**: Disguise as any player (with their skin)
- **Block / display entities**: Falling Block, Block Display, Item
- **Vehicles**: All 20 boat variants, all 7 minecart types
- **Projectiles**: Arrow, Trident, Firework Rocket, Ender Pearl, Snowball, and more

---

## Commands

> Permissions are configurable per-command in `disguises.json`.

| Command | Description |
|---------|-------------|
| `/disguise <type> [flags...]` | Disguise yourself |
| `/disguise <type> [flags...] self` | Disguise yourself with self-view enabled |
| `/disguise <player> <type> [flags...]` | Disguise another player |
| `/disguise entity <target> <type> [flags...]` | Disguise a targeted entity |
| `/disguise radius <r> <type> [flags...]` | Disguise all entities within radius |
| `/undisguise [player]` | Remove a disguise |
| `/disguises reload` | Reload the config |
| `/disguises info [player]` | Show active disguise info |
| `/disguises list` | List all currently disguised entities |
| `/disguises observer <target> <viewer> <type> [flags]` | Set a per-observer disguise override |

---

## Configuration

`disguises.json` (in your config folder):

| Option | Default | Description |
|--------|---------|-------------|
| `showDisguiseActionBar` | `true` | Show the disguise type in the action bar |
| `actionBarIntervalTicks` | `100` | How often the action bar reminder appears (0 = only on change) |
| `disguiseSounds` | `true` | Replace sounds with the disguise type's sounds |
| `selfDisguiseDefault` | `false` | Players see their own disguise by default |
| `undisguiseOnDeath` | `false` | Remove disguise when the player dies |
| `undisguiseOnWorldChange` | `false` | Remove disguise on dimension change |
| `persistDisguises` | `false` | Save and restore disguises across server restarts |
| `showEquipmentThroughDisguise` | `false` | Show the real entity's equipment through the disguise |
| `enforceTypePermissions` | `false` | Require `disguises.type.<type>` permission per entity type |
| `permLevelSelf/Others/Entity/Radius` | `2` | Op levels for each command category |
| `disabledEntityTypes` | `[]` | Block specific entity types from being used |

---

## Requirements

- **Fabric Loader**
- **Fabric API**
- Minecraft 1.21.x

---

## Inspired by
[LibsDisguises](https://www.spigotmc.org/resources/libs-disguises.81/), the gold standard for Bukkit/Spigot disguise plugins, reimagined for Fabric.
