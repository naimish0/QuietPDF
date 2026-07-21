# T40-Smart-Home

Status: PENDING  
Branch: `T40-Smart-Home`

## Goal

Build QuietPDF’s production-quality Smart Home experience.

The Home screen must make PDF work immediately understandable, surface the most useful tools in one tap, adapt to each user’s activity, and communicate QuietPDF’s offline privacy advantage.

This feature should make QuietPDF feel polished, trustworthy, fast and suitable for millions of users. However, do not claim that UI alone guarantees downloads or ratings.

## Dependencies

Use the existing implementations from:

- T0–T34: PDF reader and PDF tools
- T35: Recent Files
- T36: Favorites
- T37: History
- T38: File Search and Sort
- T39: Sharing

Do not duplicate their repositories, state holders, databases or business logic.

## Product Context

Current high-install Android competitors demonstrate useful patterns:

- Adobe Acrobat: document-first Home, recent documents and contextual actions.
- CamScanner and Adobe Scan: unmistakable scanning action and guided workflows.
- iLovePDF and Smallpdf: tools organized by user intent.
- Xodo and Foxit: powerful contextual controls without placing every action on one screen.

QuietPDF should adopt their clearest interaction principles while avoiding:

- Dense walls of equally weighted tool icons
- AI-first clutter
- Cloud or account prompts
- Hidden save locations
- Intrusive advertisements
- Subscription pressure
- Frequently moving controls
- Unclear navigation
- Unfinished or fake tool cards

Do not copy competitor artwork, colors, layouts, assets, screenshots, branding or trade dress.

## Important Scope Boundary

Implement only the Smart Home experience.

Do not:

- Rewrite existing PDF operations
- Change reader rendering or gestures
- Change scanner image processing
- Implement missing PDF functionality
- Add cloud functionality
- Add login or account creation
- Add AI features
- Add new advertisement formats
- Perform a broad architecture rewrite
- Display non-functional feature cards
- Create blank destinations

Preserve all existing behavior and navigation contracts.

## Required Home Information Architecture

### 1. Top App Bar

Display:

- QuietPDF product name
- Search action
- Settings action
- Any existing navigation behavior required by the architecture

Use a clean Material 3 app bar.

Do not overload it with unrelated actions.

### 2. Privacy Trust Indicator

Display a compact trust indicator near the top:

> Offline • Files stay on your device

Requirements:

- Use a shield or privacy icon.
- Include an accessible text description.
- Do not use a disruptive dialog.
- Do not repeatedly animate it.
- Do not make privacy look like a paid feature.
- The claim must remain accurate even when advertisements use network access.
- Never imply that ad traffic is offline.
- Document contents, filenames and PDF metadata must never be sent to advertising services.

### 3. Context-Aware Hero Area

The first useful content should adapt to the user.

For a returning user with reading history:

- Display Continue Reading prominently.
- Show document thumbnail when available.
- Show filename.
- Show current page and total pages.
- Show reading progress.
- Provide a Resume action.
- Provide an overflow menu for supported file actions.

For a new user or a user without recent files:

- Display Open PDF as the strongest primary action.
- Display Scan Document as the secondary action when scanning is fully implemented.
- Explain the value briefly without creating a large onboarding carousel.

Never show a broken or unavailable action.

### 4. Primary Actions

Provide large, clearly labelled actions for:

- Open PDF
- Scan Document

Only display Scan Document as enabled when its complete production workflow exists.

Requirements:

- Large touch targets
- Text and icon together
- Clear pressed and loading states
- Prevent duplicate navigation from rapid taps
- No advertisement before either action
- No permission request until the user invokes the relevant action

### 5. Favorite Tools

Display up to four user-selected favorite tools.

Requirements:

- Use existing T36 favorite data.
- Allow users to add or remove favorites using existing supported behavior.
- Keep ordering stable.
- Provide a useful default selection only when the user has not selected favorites.
- Defaults must include only implemented tools.
- Do not silently overwrite user ordering.
- Do not show more than four favorites in the primary section.
- Provide access to remaining tools below.

Suggested defaults when implemented:

- Images → PDF
- Merge PDF
- Compress PDF
- Split PDF

### 6. Guided Tool Discovery

Use a visible section such as:

> What would you like to do?

Organize tools by user intent:

#### Create

- Scan Document
- Images → PDF

#### Organize

- Merge PDF
- Split PDF
- Rearrange Pages
- Extract Pages
- Delete Pages
- Rotate Pages

#### Optimize

- Compress PDF
- Target File Size

#### Secure

- Protect PDF
- Remove Password
- Change Password

#### Edit and Sign

- Fill Forms
- Sign PDF
- Annotate PDF
- Add Watermark

#### Extract

- Extract Images

Requirements:

- Show only functional tools.
- Every major tool displayed on Home must be reachable in one tap.
- Use clear labels rather than relying on icons.
- Use consistent icons from the existing icon system.
- Do not use a different arbitrary color for every tool.
- Use subtle category container colors derived from Material color roles.
- Keep tool ordering stable across launches.
- Support tool search through the existing T38 implementation.

### 7. Recent Files

Use the existing T35 recent-files implementation.

Display a concise list containing:

- Thumbnail when available
- Filename
- Page count when available
- File size
- Last opened or modified time
- Favorite state
- Reading progress when available

Supported actions should include only implemented operations, such as:

- Open
- Share
- Rename
- Favorite or remove favorite
- File information
- Remove from Recents
- Delete only with explicit confirmation

Requirements:

- Display three to five recent files on Home.
- Provide See All when additional files exist.
- Show an elegant empty state.
- Do not scan the entire device storage on the main thread.
- Do not request broad storage permission.
- Use existing SAF/content URI access.
- Handle revoked URI permissions gracefully.

### 8. History

Surface History as a secondary destination using the existing T37 implementation.

Do not duplicate the entire history list on Home.

A small shortcut or summary is sufficient.

### 9. Storage Statistics

Display a compact storage card only when accurate data can be calculated cheaply.

Prefer showing:

- Number of QuietPDF-generated files
- Storage used by QuietPDF outputs
- Manage Files action

Do not:

- Scan all device storage
- Request broad storage access
- Misrepresent total device storage as QuietPDF storage
- Perform expensive calculations during composition

Load statistics asynchronously and cache them appropriately.

### 10. Navigation

Use predictable primary navigation.

Preferred destinations:

- Home
- Files
- Tools

Keep Settings in the top app bar unless the existing application already has a justified Settings destination.

Requirements:

- Use Material 3 NavigationBar on compact screens.
- Use NavigationRail or appropriate adaptive navigation on medium and expanded widths.
- Preserve navigation state.
- Preserve scroll position when switching destinations.
- Prevent duplicate destinations on repeated taps.
- Support correct Android back behavior.
- Do not introduce empty destinations.

## Visual Direction

Create a restrained, professional Material 3 experience.

### Brand

- Deep indigo or ink-blue primary family
- Subtle teal accent for privacy and offline information
- Warm near-white light background
- Neutral charcoal dark background
- Red reserved for errors and destructive operations

### Theme

- Use Material color roles.
- Do not hardcode colors inside individual Home composables.
- Support light theme.
- Support dark theme.
- Support Android 12+ dynamic color.
- Provide a polished branded fallback.
- Ensure sufficient contrast.
- Do not use excessive gradients or glass effects.

### Components

- Consistent 16–20dp card shapes
- Restrained tonal elevation
- 8dp-based spacing system
- 48dp minimum touch targets
- Prefer 52–56dp primary actions
- Clear typography hierarchy
- Icon and text labels together
- Stable pressed, focused, disabled and loading states

### Motion

- Use restrained 150–250ms transitions.
- Respect system animation settings.
- Avoid bouncing, pulsing and continuous decorative animation.
- Do not animate large document thumbnails unnecessarily.
- Prevent animation from delaying navigation.

## Adaptive Layout

Support:

- Compact phones
- Small phones
- Landscape
- Tablets
- Foldables
- Resizable windows where applicable

Requirements:

- Do not stretch cards across very wide screens.
- Constrain maximum content width.
- Reflow tool grids based on available width.
- Use navigation rail or panes where appropriate.
- Preserve the same information hierarchy on every screen size.
- Do not lock the application to portrait orientation.

## Accessibility

- Minimum 48dp touch targets
- TalkBack labels for every interactive element
- Logical traversal order
- Correct roles and state descriptions
- Support large font scaling without clipping
- Support RTL layouts
- Do not communicate state through color alone
- Provide accessible alternatives to gesture-only interactions
- Use clear content descriptions for file thumbnails and privacy indicators
- Announce loading, completion and errors appropriately

## Performance

The Smart Home must remain fast on low-end devices.

Requirements:

- Never perform file I/O on the main thread.
- Load thumbnails asynchronously.
- Use bounded thumbnail caching.
- Cancel obsolete thumbnail requests.
- Avoid decoding full-resolution PDF pages for Home thumbnails.
- Use lazy lists or grids.
- Use stable item keys.
- Minimize unnecessary recomposition.
- Avoid querying recent files, favorites or history repeatedly during composition.
- Restore Home state after configuration changes and process recreation where supported.

## Empty, Loading and Error States

Provide explicit states for:

- First launch
- No recent documents
- No favorite tools
- Loading recent files
- Revoked file permission
- Missing or moved file
- Corrupted recent file
- Insufficient storage
- Database or index failure

Never display only:

> Something went wrong

Explain what happened and provide a relevant recovery action.

## Advertisement Placement

Preserve the QuietPDF advertisement policy.

Allowed on Smart Home:

- One reserved-height banner after useful Home content
- A clearly labelled native advertisement between non-critical sections

Requirements:

- No ad above Open PDF, Continue Reading or Scan Document.
- Never place an ad between a section title and its tools.
- Never make an ad resemble a PDF tool.
- Ad loading must not move controls or change scroll position.
- Do not show an interstitial when opening a PDF or launching a tool.
- Do not add new placements as part of T40.
- Use test ad identifiers in debug builds.

## Testing

Add focused tests covering:

- New-user Home state
- Returning-user Home state
- Continue Reading
- Open PDF action
- Scan action when available
- Favorite tool ordering
- Recent file rendering
- See All behavior
- File overflow actions
- Missing or revoked file access
- Tool categories and ordering
- No clickable unfinished tools
- Search navigation
- Back navigation
- Bottom navigation state restoration
- Light theme
- Dark theme
- Dynamic-color fallback
- Large font scaling
- TalkBack semantics
- Compact layout
- Expanded layout
- RTL layout where test infrastructure permits
- Ad placeholder does not cause layout shift

Use existing screenshot or golden-test infrastructure if present. Do not add a large dependency solely for screenshots without justification.

Run applicable:

- Unit tests
- Compose UI or instrumentation tests
- Debug assembly
- Android lint

## Acceptance Criteria

T40 is complete only when:

1. Home immediately communicates what QuietPDF does.
2. Open PDF is reachable in one tap.
3. Continue Reading works for returning users.
4. All displayed tool cards perform real actions.
5. No unfinished or fake tools are clickable.
6. Favorite tools use existing persisted data.
7. Recent files use existing persisted data.
8. Search uses the existing search implementation.
9. Home works without an account or network connection.
10. Document information is never transmitted.
11. The exact output location remains discoverable through existing file flows.
12. UI works in light, dark and dynamic-color modes.
13. Home adapts correctly to phones, tablets and foldables.
14. Accessibility checks pass.
15. No file I/O blocks the main thread.
16. Advertisements do not obscure or delay useful actions.
17. Existing PDF operations and reader behavior remain unchanged.
18. Relevant tests, build and lint pass.
19. Final diff contains no unrelated changes.

## Git Requirements

Follow the repository’s documented feature-branch workflow.

Before committing, configure and verify only this repository-local identity:

git config user.name "Naimish Gupta"
git config user.email "naimishgupta983842377@gmail.com"

Never use `--global`.

Use:

- Branch: `T40-Smart-Home`
- Suggested commit: `T40: implement Smart Home experience`

Do not force-push or bypass branch protection.

After successful verification:

1. Push `T40-Smart-Home`.
2. Merge it into `main`.
3. Pull and verify the updated `main`.
4. Mark T40 completed in the feature queue.
5. Continue only according to the documented queue workflow.

## Final Report

Report:

- Changed files
- Home information architecture
- Reused T35–T39 components
- Visual-system decisions
- Accessibility improvements
- Adaptive-layout behavior
- Tests and verification performed
- Remaining limitations
- Branch, commit and merge status