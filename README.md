# Shopmod

Shopmod is a NeoForge item-shop mod workspace targeting Minecraft `26.1.2` and NeoForge `26.1.2.73`.

## Building

```bash
gradle build
```

The built mod jar is written to `build/libs/`. This repository intentionally does not commit the Gradle wrapper jar so it can be pushed through environments that reject binary files; install Gradle 9.2.1 locally or use the GitHub Actions build artifact.


## Downloading a build from GitHub Actions

If you cannot access a locally built jar, open the GitHub Actions **Build** workflow run for this branch and download the `shopmod-jar` artifact. The workflow installs Gradle 9.2.1, builds the mod with `gradle build`, and uploads every jar from `build/libs/`.

## Current item-shop flow

1. Place a chest in the world. Single chests and double chests are supported.
2. Place a sign on the side of the chest, or place a sign on top of the chest.
3. The shop setup screen opens automatically.
4. Enter fully namespaced item IDs, amounts, and a chest-key password.
5. Confirm to register the shop.

Players can right-click the registered chest or sign to open a trade confirmation screen. The shop trades one configured payment item stack for one configured output item stack.

## Shop protection

- The owner cannot buy from their own shop.
- Registered shop chests and signs cannot be broken unless the player holds a renamed item whose custom display name exactly matches the configured password.
- Holding the matching key item while right-clicking the chest allows direct chest access for restocking or collecting payments.
- Breaking the chest or sign while holding the matching key deregisters the shop and allows the block to break normally.

## Stored data

Registered shops are saved per world in `data/shopmod_shops.json`. The saved data includes the chest position, sign position, dimension, owner UUID/name, input/output item IDs and amounts, and a SHA-256 hash of the password.

## Additional Resources

- NeoForge documentation: https://docs.neoforged.net/
- NeoForge Discord: https://discord.neoforged.net/
