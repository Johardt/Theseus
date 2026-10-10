# Dock opening and closing animations

Scope: Minecraft 26.1 development; animate the left chapter dock expanding/collapsing and the right quest-details/authoring dock opening/closing. Preserve held-button feedback. No modal animations, fades, camera easing, or changes to quest mutations/networking in this pass.

## Proposed behavior

- Slide horizontally over 100 ms by default with linear interpolation. The duration is variable through the JVM property `theseus.dockAnimationMillis` (milliseconds; `0` disables motion). Development runs can pass `-PdockAnimationMillis=100` to `runClient`. Keep content at its normal width and clip it as the dock slides; do not squash text or controls.
- The left dock collapses to its existing 18-pixel rail. Its toggle follows the moving inner edge and remains available to reverse the animation.
- The right dock slides through the right screen edge. Keep its existing overlay relationship to the graph: `graphCanvasRight()` already spans the screen; opening it must not introduce a new camera resize.
- Retarget from the currently displayed position on rapid toggles. Use monotonic elapsed time, not frame counts or a fixed tick step. Sample once for each render/input operation so all consumers use the same geometry.
- Draw opening controls at their presented positions and route input to those positions. Outgoing dock content is visual only and blocks click-through over the area it still covers; it cannot perform actions or retain keyboard focus.
- Switching quests or detail/editor tabs while a dock stays open updates content without restarting the open animation. Switching between the different details/editor widths requires explicit retargeting from the current edge, without squashing either content layout.
- Initialize the screen at its intended dock state, with no automatic first-frame slide. Resizing or changing GUI scale settles/reflows the presentation safely. A server-driven screen replacement should preserve an in-flight transition only when it represents the same dock/content lifetime; physical button holds remain screen-local and are cancelled as before.

## Why this needs a focused refactor

`QuestScreenLayout.sidebarWidth()` currently combines content width and open/closed geometry. The renderer, header placement, graph canvas, widgets, and input router all consume it. Interpolating this getter alone would resize content and leave rebuilt widget positions out of sync.

The right dock is similarly spread across `QuestScreenLayout`, `QuestScreenRenderer`, `QuestScreenInput`, `QuestDetailsPanel`, and `QuestAuthoringPanelDock`. Close callbacks can clear `selectedQuestId`, discard the authoring session, and rebuild immediately. Closing visuals must therefore retain their own content/widget lifetime rather than reading the cleared live state.

## Implementation steps

1. **Add retained dock motion state.** A small `DockMotion` module exposes target changes and sampled presentation geometry, with an injectable clock for deterministic tests. Keep logical openness, content width, and presented bounds distinct. Use it for both docks; avoid introducing a general animation framework.
2. **Group dock presentation.** Give each dock a presentation group owning its widgets and manual render data in local coordinates. Separate left/right content drawing from screen-wide foreground and modal drawing. Rebuild a dock group when its content changes, without resetting its motion. This group is also the outgoing visual retained on close.
3. **Share presented geometry.** Add a sampled dock layout at the `QuestScreenLayout` seam. Derive dock offsets, clip rectangles, header placement, visible left canvas edge, and pointer routing from it. Keep stable content dimensions separately. Move widget groups and manually rendered content together; translate pointer coordinates for manual link/card/reward picking. Do not rebuild the entire screen on each animation frame.
4. **Handle outgoing lifetime and input.** Preserve the outgoing presentation before the logical close clears selection or discards the draft. Disable its callbacks, text editing, focus, tooltips, and held state immediately. Keep only its drawing and occlusion until the transition completes, then release it. Modal precedence stays above docks. Ensure releases still reach the existing screen-level press cleanup.
5. **Integrate all dock transitions.** Route sidebar toggles, details open/close, draft open/close, edit-mode changes, and server-result changes through the same presentation lifecycle. Preserve existing logical action timing and dirty-draft confirmation behavior. Handle reversal and screen removal explicitly.
6. **Verify and deliver.** Run the complete existing suite/build plus focused motion/layout tests. Confirm the visual and input acceptance checks below in the running client before claiming full verification.

## Acceptance checks

- Correct endpoints and duration independent of frame rate; reversal starts at the current position without a jump.
- Left dock text retains its width while clipped; rail/toggle remain usable throughout collapse and expansion.
- Right details and authoring docks both open and close with their outgoing content visible until completion, including after selection/draft state is cleared.
- Buttons, scroll areas, links, reward choices, and editor fields follow visible geometry; hidden/clipped controls cannot receive input and visible closing docks block graph click-through.
- Rapid toggles, quest changes, switching details/editor widths, async rebuilds, resizing, GUI scaling, modals, and screen transitions do not restart or strand motion/focus.
- Held-button darkening survives ordinary dock-group rebuilds and moving controls, and clears on matching release or removal. No pink sprites or timed pressed flash.
- No new animation on initial screen display; no per-frame widget-tree rebuilds or visible graph/header snapping.

The previous running-client verification was limited by the computer-control tool not recognizing the unbundled Java window. Treat visual acceptance as pending until direct client inspection is possible or the maintainer confirms it.

## Relevant source seams

- [Layout and dock bounds](../neoforge/main/java/me/johardt/theseus/client/QuestScreenLayout.java)
- [Mixed screen/dock rendering](../neoforge/main/java/me/johardt/theseus/client/QuestScreenRenderer.java)
- [Widget construction and sidebar toggle](../neoforge/main/java/me/johardt/theseus/client/QuestScreenWidgets.java)
- [Dock and graph input routing](../neoforge/main/java/me/johardt/theseus/client/QuestScreenInput.java)
- [Details presentation and manual picking](../neoforge/main/java/me/johardt/theseus/client/QuestDetailsPanel.java)
- [Authoring dock and nested layout](../neoforge/main/java/me/johardt/theseus/client/QuestAuthoringPanelDock.java)
- [Draft teardown](../neoforge/main/java/me/johardt/theseus/client/QuestScreenEditor.java)
- [Held-button lifetime](../neoforge/main/java/me/johardt/theseus/client/TheseusButtons.java)
