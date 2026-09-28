# Clear Third Person Aim

[Modrinth](https://modrinth.com/project/clear-third-person-aim) | [Source](https://github.com/yoima-jp/clear-third-person-aim) | [Issues](https://github.com/yoima-jp/clear-third-person-aim/issues)

### Overview

Clear Third Person Aim is a client-side mod that makes only the parts of your player model blocking your aim transparent when you ready a bow or similar item in third person. It does not need to be installed on the server and does not change how other players see you.

### Supported environment

- Minecraft: 1.21.1, 1.21.11, 26.1.x (built for 26.1.2), 26.2, and 26.3
- Loaders: Fabric (Fabric API required; Mod Menu optional) or NeoForge
- Jar filename: `clear_third_person_aim-<loader>-<modversion>+<mc>.jar` (`<loader>` is `fabric` or `neoforge`)

### Triggers

Each condition can be enabled or disabled in the settings screen.

- Drawing a bow (ON by default)
- Loading a crossbow (ON by default)
- Holding a loaded crossbow (ON by default)
- Aiming a trident (ON by default)
- Holding throwables (OFF by default; snowballs, eggs, ender pearls, and similar items)
- Extra item IDs from other mods, configured separately for “while using” and “while held”

The mod does not work in first person. The front-facing third-person view is disabled by default.

### Modes and transitions

- **Full body**: Makes the whole player model transparent.
- **Upper body**: Makes the head, torso, and arms transparent while leaving the legs visible.
- **Around crosshair** (default): Makes the area inside an ellipse centered on screen transparent, with a soft edge.
- **Instant**: Switches immediately when you start or stop aiming.
- **Fade** (default): Transitions smoothly. Fade-in and fade-out durations can be set separately.

### Open the settings

- Run `/clearaim` in chat (works with Fabric and NeoForge; Mod Menu is not required)
- Fabric: Use the settings button in Mod Menu
- NeoForge: Use the settings button in the mod list

Open the settings screen in a world to preview changes live. The **“Toggle Aim Transparency”** key binding enables or disables the feature (unbound by default).

The crosshair appears by default while transparency is active in the back third-person view. Toggle it with **Show crosshair** on the Look page.

### Configuration file

Settings are saved to `config/clear_third_person_aim.json`. Numeric values are clamped to the ranges below when loaded. If the file is corrupted, it is backed up as `.broken` and recreated with defaults.

| Settings label | JSON key | Default | Range or values |
| --- | --- | --- | --- |
| Enabled | `enabled` | `true` | — |
| Drawing a bow | `triggerBowDrawing` | `true` | — |
| Loading a crossbow | `triggerCrossbowCharging` | `true` | — |
| Holding a loaded crossbow | `triggerCrossbowLoaded` | `true` | — |
| Aiming a trident | `triggerTridentAiming` | `true` | — |
| Holding throwables | `triggerThrowableHeld` | `false` | — |
| Extra items (while using) | `extraItemsWhileUsing` | `[]` | List of item IDs |
| Extra items (while held) | `extraItemsWhileHolding` | `[]` | List of item IDs |
| Front view too | `activeInFrontView` | `false` | — |
| Mode | `mode` | `AIM_ELLIPSE` | `FULL_BODY` / `UPPER_BODY` / `AIM_ELLIPSE` |
| Remaining opacity | `remainingOpacity` | `0.2` | `0.0–1.0` |
| Ellipse width | `ellipseRadiusX` | `0.10` | `0.02–1.0` |
| Ellipse height | `ellipseRadiusY` | `0.15` | `0.02–1.0` |
| Edge softness | `feather` | `0.06` | `0.0–0.5` |
| Adjust for distance and FOV | `distanceCompensation` | `true` | — |
| Include held items | `hideHeldItems` | `true` | — |
| Show crosshair | `showCrosshair` | `true` | — |
| Switching | `transition` | `FADE` | `INSTANT` / `FADE` |
| Fade-in time | `fadeInMs` | `150` ms | `0–2000` ms |
| Fade-out time | `fadeOutMs` | `250` ms | `0–2000` ms |
| Force simple translucency | `forceFallbackRenderer` | `false` | — |

Ellipse width and height are ratios of screen height. Radius compensation follows the actual camera distance and FOV, including bow zoom. Values in existing config files are preserved. Enter item IDs for extra items, for example `somemod:longbow`.

### Shader packs and limitations

When a shader pack is active in Iris or a similar mod, rendering automatically switches to simple translucency. The whole player model then uses one opacity regardless of the selected mode; armor, capes, and held items remain opaque. Overlapping model parts may show through each other. See [Known issues](KNOWN_ISSUES.md) for details.

### For developers

- Build: `./gradlew build`
- Build and collect jars for all versions and loaders: `./gradlew buildAndCollectAll` (output: `build/libs/<modversion>/`)
- Unit tests: `./gradlew :common:test` (PowerShell: `.\gradlew.bat :common:test --quiet`)
- Client tests on Fabric 1.21.4 and later: `./gradlew :<version>-fabric:runClientGameTest`
- Client tests on NeoForge and Fabric 1.21.1: `./gradlew :<version>-<loader>:runSelfTest`
- Compatibility variants: pass `-Pcta.compat=sodium`, `-Pcta.compat=iris`, or `-Pcta.compat=iris-shader` to a test task
- Run the client test matrix: `bash testing/run-all-client-tests.sh [version]`
- Test screenshots: `build/test-screenshots/`
- Smoke-test a Fabric release jar: `./gradlew :<version>-fabric:runProductionSmoke`

License: MIT
Author: yoima-jp
