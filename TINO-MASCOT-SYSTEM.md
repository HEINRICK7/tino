# TINO Mascot System

Status: implemented in the Android Design System.

## Canonical component

The only mascot renderer is `TinoMascot` in `ui/components/TinoComponents.kt`:

```kotlin
@Composable
fun TinoMascot(
    state: TinoMascotState,
    modifier: Modifier = Modifier,
    size: TinoMascotSize = TinoMascotSize.Medium,
    placement: TinoMascotPlacement = TinoMascotPlacement.Default,
    onClick: (() -> Unit)? = null,
)
```

The official frontal body asset is derived from `assets/branding/masters/mascot-asset-system.png` and bundled once as `tino_mascot_official_body.png`. The Compose renderer adds the two black eyes as a controllable layer. No state introduces a mouth, limbs, brows, accessories, white eyes or another anatomy.

## Controlled states

`Idle`, `Observing`, `LookingLeft`, `LookingRight`, `Thinking`, `Attention` and `Guiding` are the official semantic states. Runtime `TinoPresenceMode` values are mapped through `TinoMascotState.fromPresence` so screens do not invent their own mascot modes.

State expression uses gaze, small posture changes, timing and entry/exit motion. There are no thought bubbles or floating indicators rendered above the mascot; contextual copy/cards remain responsible for explaining the action.

## Tokens and composition

Sizes are `Icon` (48dp), `Small` (64dp), `Medium` (88dp), `Large` (128dp) and `Hero` (160dp). Composition intents are `Default`, `Inline`, `CardSide`, `CardTop`, `PeekLeft`, `PeekRight`, `PeekTop` and `Elevated`. Parent layouts continue to own final placement and collision avoidance.

The component is used by the voice FAB, voice input, presence card, getting-started carousel and contextual empty state. Generic empty states keep their domain icon, avoiding indiscriminate mascot repetition.

## Accessibility and performance

- A clickable mascot exposes a localized state description and button semantics.
- Decorative mascot instances expose no duplicate screen-reader label; interactive instances expose the current state and button semantics.
- `LocalTinoReduceMotion` disables bobbing, gaze interpolation and blinking loops.
- Motion uses Compose transforms and remembered animation state; it does not allocate sprites or images per frame. The official body is a single bundled resource reused by every mascot instance.
- The body color is always the canonical TINO green. Error/attention state is communicated by context and motion, not by recoloring the character.

## Verification

- `TinoMascotContractTest` validates runtime mapping and token ordering.
- `TinoMascot.kt` contains the required previews for all semantic states, small/large/dark variants, empty state and contextual card.
- `:app:compileDebugKotlin` passes after the migration.
