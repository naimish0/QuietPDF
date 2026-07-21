You are working on the existing QuietPDF Android repository.

Feature ID: T43
Branch: Home-Production-Design-Add-Interstital-ad-Add-App-Open-Ad

## Objective

Upgrade the existing QuietPDF Home and file-management interface to a
production-grade Material 3 design.

The current Home hero card looks good and must be preserved. Existing feature
icons also look good and must be reused.

Implement:

- A premium Home screen
- A full-width Search PDFs banner at the top
- Production-quality Quick Tool cards
- Meaningful Home, Files, Tools, History and Search destinations
- Policy-conscious interstitial advertisements after every two successful
  eligible workflows
- App Open advertisements for established returning users
- Centralized full-screen advertisement frequency protection

T0 through T42 are already complete. Preserve their implementations. Do not
reimplement, rename or duplicate any completed feature.

The business objective is to increase sustainable AdMob revenue without
damaging user trust, retention, Play Store ratings or QuietPDF’s privacy-first
positioning.

Do not stop after producing a plan. Inspect, implement, test, review and complete
the documented Git workflow in the same run unless a genuine blocker is found.

## Required Context

Before making changes:

- Read and obey all applicable AGENTS.md files.
- Read quietpdf-product-context.md.
- Read the authoritative feature queue.
- Inspect the source code before deciding what needs to change.
- Inspect git status before editing.
- Inspect the current Home, Files, Tools, History, Favorites, Search, navigation,
  theme and advertisement implementations.
- Inspect the existing Mobile Ads SDK and consent implementation.
- Inspect existing ad-unit configuration.
- Treat source code as the source of truth.
- Preserve all unrelated and uncommitted user changes.
- Use targeted rg searches rather than repeatedly scanning the entire repository.

## Git Requirements

If the following branch is already active and unmerged, continue working on it:

Home-Production-Design-Add-Interstital-ad-Add-App-Open-Ad

Otherwise:

1. Start from a clean main branch.
2. Pull main using fast-forward-only behavior.
3. Configure only this repository-local Git identity:

   git config user.name "Naimish Gupta"
   git config user.email "naimishgupta983842377@gmail.com"

4. Verify both values before committing.
5. Never use git config --global.
6. Create the exact branch:

   Home-Production-Design-Add-Interstital-ad-Add-App-Open-Ad

7. Implement only T43.
8. Add focused tests.
9. Run all relevant verification.
10. Review the diff for unrelated changes.
11. Create one intentional feature commit.
12. Push without force-pushing.
13. Merge only after scoped verification succeeds.
14. Pull and verify the updated main.
15. Mark T43 completed in the feature queue.

Suggested commit message:

T43: redesign Home and add interstitial and App Open ads

Never:

- Force-push
- Rewrite published history
- Discard unrelated changes
- Automatically resolve uncertain conflicts
- Merge failing code
- Bypass branch protection
- Use another Git author identity

If an external runner owns Git operations, Codex must not independently stage,
commit, switch branches, merge, pull or push.

## Non-Negotiable Scope

Keep all existing PDF functionality unchanged.

Preserve:

- PDF opening
- PDF rendering
- Reader navigation
- Zoom and pan gestures
- Reader modes
- Scanner processing
- Images-to-PDF behavior
- Merge, split and page-management behavior
- Compression algorithms
- Password operations
- Watermark operations
- Form filling, signing and annotations
- File creation and saved locations
- Sharing
- Search results
- Favorites
- Recent files
- History
- Existing navigation contracts
- Existing high-quality icons
- Existing Home hero card

Do not:

- Rewrite the PDF engine
- Rewrite databases or repositories
- Change document-processing algorithms
- Add cloud processing
- Add login or accounts
- Add AI features
- Add broad storage permission
- Display fake or unfinished tools
- Create blank destinations
- Create artificial screens solely to display advertisements
- Add extra workflow steps only for advertisements
- Copy competitor branding, artwork, assets, layouts or trade dress
- Perform unrelated architectural changes

## Production Design Direction

Use interaction principles commonly found in mature applications:

- Document-first Home and Recent Files
- Prominent Search surface
- Clear primary actions
- Tools grouped by user intent
- Separate Files and Tools destinations
- Stable Material 3 navigation
- Contextual actions instead of dense menus

Do not copy any competitor’s exact visual design.

## Screen Architecture

Use the existing completed implementations from T35–T40.

Refine the following meaningful destinations:

1. Home
2. Files
3. Tools
4. History
5. Search

Do not duplicate existing screens, repositories or navigation routes.

Search opens from the prominent search banner rather than occupying a bottom
navigation destination.

Settings remains available from the Home app bar unless the existing
architecture already uses a justified Settings destination.

Use:

- Material 3 NavigationBar on compact devices
- NavigationRail or an appropriate adaptive alternative on wider devices

Navigation must:

- Preserve the selected destination
- Preserve scroll position
- Prevent duplicate destinations
- Prevent double navigation from rapid taps
- Follow correct Android back behavior
- Survive configuration changes
- Restore state after process recreation where supported
- Avoid unnecessarily recreating repositories or ViewModels
- Hide appropriately inside immersive PDF workflows

## Home Screen

Use this information hierarchy:

1. Top app bar
2. Search banner
3. Existing Home hero card
4. Continue Reading
5. Quick Tools
6. Recent Files preview
7. Privacy or storage information
8. One permitted advertisement after useful content

## Top App Bar

Display:

- QuietPDF
- Privacy/offline indicator where appropriate
- Settings action

Keep the app bar clean and restrained.

Do not overload it with tool icons.

## Search Banner

Replace the existing Search Files text or basic button with a production-grade,
full-width Search PDFs surface directly below the top app bar.

### Appearance

- Approximately 56dp high
- Material 3 rounded container
- Leading search icon
- Placeholder: “Search PDFs”
- Optional trailing filter or sort action only when functional
- Entire surface clickable
- Clear pressed and focused states
- Minimum 48dp touch targets
- Correct TalkBack role and description
- Light-theme support
- Dark-theme support
- Dynamic-color support

The Search banner must look interactive, not like ordinary text.

### Behavior

- Tapping anywhere opens the existing dedicated Search screen.
- Reuse the completed T38 search implementation.
- Do not duplicate search repositories or business logic.
- Preserve existing search results, sorting and filtering.
- Back returns to the previous destination with its state preserved.
- Search queries, filenames, paths and metadata remain on the device.
- Do not request broad storage permission.
- Do not display an interstitial when Search is opened or closed.

## Preserve the Existing Hero Card

The current Home hero card is visually successful.

Preserve:

- Its visual hierarchy
- Existing icons
- Existing actions
- Existing functionality
- General appearance

Only correct issues such as:

- Internal spacing
- Text clipping
- Large-font behavior
- Minimum touch targets
- Dark-theme contrast
- Responsive width
- Tablet presentation
- Unnecessary recomposition

Do not redesign the hero card without a source-grounded reason.

## Replace the Basic Feature List

Remove the plain vertical feature list below the hero card.

Replace it with a production-quality Quick Tools section.

### Quick Tools

On phones, use a responsive two-column grid.

Each card should contain:

- Existing icon
- Short tool name
- Optional single-line benefit
- Rounded Material container
- Consistent height
- Pressed, focused, selected and disabled states where applicable
- Entire card clickable
- Correct accessibility semantics

Show a maximum of four Quick Tools on Home.

Use the existing favorite-tool implementation when available. Otherwise use only
implemented defaults such as:

- Images → PDF
- Merge PDF
- Compress PDF
- Split PDF

Requirements:

- Display only functional tools.
- Preserve user-selected favorites.
- Keep ordering stable.
- Do not silently overwrite user ordering.
- Provide View All Tools.
- View All Tools opens the existing Tools destination.
- Use subtle theme-derived category colors.
- Do not assign arbitrary colors to every tool.
- Preserve existing icons.

On tablets, reflow to three or four columns without excessively stretching
individual cards.

## Continue Reading

When a meaningful recent reading position exists, display:

- Document thumbnail
- Filename
- Current page
- Total pages
- Reading progress
- Resume action
- Supported overflow actions

Reuse existing reader-history state.

Do not display the section when no meaningful reading position exists.

Do not load full-resolution PDF pages for Home thumbnails.

## Recent Files

Display a concise Home preview of approximately three recent documents.

Each file card should include:

- Thumbnail
- Filename
- Page count or reading progress
- File size or last-opened time
- Favorite state
- Overflow menu

Provide See All to open Files.

Use an elegant empty state when no recent files exist.

Reuse the completed T35 recent-file implementation. Do not create another recent
file database or index.

## Files Screen

Refine the existing Files screen into a production-quality destination.

Include existing supported functionality:

- Files title
- Full-width Search PDFs banner
- Recent filter
- Favorites filter
- All Files filter when genuinely supported
- Sort by Name
- Sort by Date
- Sort by Size
- Grid/list switch only if already implemented
- Accurate file count
- Loading, empty and error states

Each file item should show:

- Thumbnail
- Filename
- Page count when known
- File size
- Modified or last-opened date
- Favorite state
- Overflow menu

Reuse existing actions:

- Open
- Share
- Rename
- Favorite or remove favorite
- File information
- Remove from Recents
- Delete with confirmation

Do not duplicate storage, indexing, history or database logic.

Do not make advertisement rows resemble documents.

## Tools Screen

Refine the existing Tools destination.

Do not show one large, unstructured wall of icons.

Group implemented tools into categories such as:

### Create

- Scan Document
- Images → PDF

### Organize

- Merge PDF
- Split PDF
- Rearrange Pages
- Extract Pages
- Rotate Pages
- Delete Pages

### Optimize

- Compress PDF
- Target File Size

### Secure

- Protect PDF
- Remove Password
- Change Password

### Edit and Sign

- Fill Forms
- Sign PDF
- Annotate PDF
- Watermark

### Extract

- Extract Images

Requirements:

- Display only implemented tools.
- Reuse existing icons.
- Use visible category headings.
- Use consistent responsive cards.
- Do not display Coming Soon cards.
- Preserve every existing navigation target.
- Keep frequently used tools available in one tap through Home favorites.
- Do not place advertisements between a category heading and its tools.

## History Screen

Refine the existing History destination using the completed T37 implementation.

Display:

- Operation type
- Date and time
- Input and output filenames where safe
- Output size when available
- Open action
- Share action
- Remove-from-history action
- Clear History only after confirmation

Removing a history record must not delete the generated file unless the user
explicitly confirms file deletion.

## Material 3 Visual Quality

Requirements:

- Material 3 components
- Existing brand palette expressed through Material color roles
- Light theme
- Dark theme
- Android 12+ dynamic color
- Polished branded fallback colors
- Consistent 16–20dp cards
- Restrained tonal elevation
- 8dp-based spacing system
- Clear typography hierarchy
- Minimum 48dp touch targets
- Prefer 52–56dp primary controls
- Stable loading and pressed states
- No excessive gradients
- No glass effects
- No glowing controls
- No decorative continuous animation
- No scattered hardcoded colors
- Red reserved for errors and destructive operations

## Motion

Use restrained production motion:

- Approximately 150–250ms transitions
- Subtle press feedback
- Stable destination transitions
- No bouncing cards
- No exaggerated scaling
- Respect system animation settings
- Never delay navigation for decorative animation

## Accessibility and Adaptive UI

Verify:

- TalkBack
- Logical traversal order
- Large font scaling
- RTL
- Small phones
- Standard phones
- Landscape
- Tablets
- Foldables and resized windows where supported
- Light theme
- Dark theme
- Dynamic-color fallback

Never communicate selection, errors or progress through color alone.

## Performance

- Never perform file I/O on the main thread.
- Load thumbnails asynchronously.
- Use bounded thumbnail caches.
- Cancel obsolete thumbnail requests.
- Avoid full-resolution PDF renders for file thumbnails.
- Use lazy lists and grids with stable keys.
- Avoid repeated database queries during recomposition.
- Preserve destination and scroll state.
- Keep navigation responsive on low-end devices.
- Close PDF and stream resources deterministically.

# ADVERTISING SYSTEM

The objective is strong, sustainable AdMob revenue without interrupting
document work.

Use one centralized advertising layer for:

- Banner advertisements
- Native advertisements
- Interstitial advertisements
- App Open advertisements
- Consent
- Eligibility
- Full-screen frequency control

Inspect the existing Mobile Ads SDK version and use the matching API.

Do not perform an unrelated migration from the existing SDK generation unless
required for correctness.

Use separate ad-unit IDs for:

- Banner
- Native
- Interstitial
- App Open

Never interchange ad-unit IDs.

Use existing production IDs from the project’s secure configuration.

If the production App Open ad-unit ID is missing:

- Add a safe configuration seam.
- Keep release App Open ads disabled until a valid ID is supplied.
- Do not invent an ID.
- Do not reuse the interstitial or banner ID.
- Report the missing value clearly.

Use Google test IDs in debug and automated tests.

Never send these values to advertising systems:

- Document contents
- Filenames
- File paths
- Content URIs
- Passwords
- Search queries
- Page contents
- Document metadata
- Recent-file data
- Favorite-file data

Resolve required consent before requesting advertisements.

## Banner and Native Placements

### Home

Allowed:

- One banner or native advertisement after Quick Tools or Recent Files

Never place it above:

- Search
- Open PDF
- Scan Document
- Continue Reading

### Files

Allowed:

- One fixed-height banner
- Or one native advertisement between complete sections

Never make an advertisement resemble a file item.

### Tools

Allowed:

- One clearly labelled native advertisement between complete tool categories

Never:

- Insert an ad between a category heading and its tool cards
- Make an advertisement resemble a PDF tool

### History

Allowed:

- One banner after meaningful history content

### Result Screens

Allowed:

- One banner after the saved result and useful actions

Requirements:

- Reserve fixed advertisement space to prevent layout shifts.
- Clearly label native advertisements as Ad or Sponsored.
- Do not keep permanent empty ad space after loading failure.
- Advertisements must not overlap navigation or document actions.
- Failed advertisements must never block UI content.

# INTERSTITIAL ADS

## Eligible Workflows

Count only workflows that successfully create and safely save an output:

- Scan to PDF
- Images to PDF
- Merge
- Split
- Compress
- Rearrange and export
- Password protection
- Watermark
- Extract images
- Fill or sign export

Do not count:

- Opening a PDF
- Reading
- Search
- Share
- Rename
- Open Folder
- Failed operations
- Cancelled operations
- Operations producing no output
- Reopening an existing result
- Duplicate completion callbacks

## Interstitial Frequency

An interstitial becomes eligible only when:

- At least two successful eligible workflows have completed since the previous
  interstitial.
- The output was safely saved.
- The Result screen has already been displayed.
- The user taps Done or Return to Home.
- At least three minutes have passed since any previous full-screen ad.
- Consent permits advertising.
- A loaded interstitial is available.
- The current session has not exceeded three interstitial impressions.
- No App Open ad or other full-screen ad is active.

Reset the successful-workflow counter only after an interstitial is actually
displayed.

If an ad is unavailable, failed or not permitted:

- Continue immediately.
- Never block navigation.
- Keep the successful-workflow counter eligible.
- Evaluate again only at the next relevant workflow-completion transition.
- Never show the pending advertisement later at an unrelated moment.

## Never Show Interstitial Ads

- Before opening a PDF
- Before saving
- Immediately after Save is tapped
- Before the Result screen
- Before Open, Share, Rename or Open Folder
- During reading
- During scanning
- During editing
- During compression or processing
- During file selection
- During password entry
- On application launch
- On application resume
- On Back
- On exit
- Immediately after another advertisement

Use the official Android interstitial test ID in debug builds:

ca-app-pub-3940256099942544/1033173712

# APP OPEN ADS

Implement a lifecycle-safe AppOpenAdManager.

## First-Time User Protection

Never show an App Open advertisement:

- On first launch
- During onboarding
- Before the user reaches Home for the first time
- During the first two completed application sessions
- Before consent is resolved

Persist the completed-session count using the existing preferences or DataStore
implementation.

The first App Open advertisement becomes eligible on the third qualified
application launch.

Do not count these as new sessions:

- Activity recreation
- Orientation change
- Configuration change
- Permission-dialog return
- File-picker return
- Camera return
- Share-sheet return
- Android Settings return
- Returning from an advertisement click

Use ProcessLifecycleOwner or the project’s equivalent process lifecycle instead
of individual Activity start events.

## Eligible App Open Placement

Show an App Open advertisement only when:

- At least two prior sessions were completed.
- Consent permits the request.
- The app enters the foreground through a genuine cold launch or qualified
  resume.
- The advertisement can be shown from the loading/splash transition or before
  returning to Home.
- The application was backgrounded for at least five minutes for resume cases.
- No document workflow or external PDF intent is waiting.
- No reader, scanner, editor, password, Result or processing screen is active.
- No other full-screen advertisement is visible.
- The loaded advertisement is less than four hours old.
- The shared full-screen cooldown has passed.

If Home becomes interactive before the App Open advertisement is ready:

- Continue to Home.
- Skip the advertisement.
- Never show it late over interactive content.
- Do not delay startup solely to wait for an advertisement.

## App Open Frequency

Use these safeguards:

- First eligible display on the third qualified launch
- Minimum four hours between App Open impressions
- Maximum two App Open impressions per calendar day
- Minimum five-minute background period for resume impressions
- Shared three-minute cooldown between any two full-screen advertisements

## Never Show App Open Ads

- To first-time users
- During onboarding
- Before an externally requested PDF is opened
- During document loading
- During reading
- During scanning
- During editing
- During processing
- During password entry
- On return from file picker
- On return from camera
- On return from share sheet
- On orientation change
- Over permission or consent dialogs
- Immediately after an interstitial
- After Home is already interactive

## App Open Loading

- Initialize Mobile Ads only after required consent handling.
- Do not load again while an advertisement is loaded or loading.
- Track loading, loaded and showing states separately.
- Discard advertisements older than four hours.
- Clear references after display or failure.
- Preload the next eligible advertisement after dismissal.
- Do not retain Activity references inside the Application object.
- Suppress foreground handling while another full-screen advertisement is
  active.
- Handle process death safely.

Use the official Android App Open test ID in debug builds:

ca-app-pub-3940256099942544/9257395921

# SHARED FULL-SCREEN AD COORDINATOR

Interstitial and App Open advertisements must use one centralized coordinator.

It must guarantee:

- Only one full-screen advertisement is displayed at a time.
- App Open cannot immediately follow an interstitial.
- Interstitial cannot immediately follow App Open.
- Both formats share a minimum three-minute cooldown.
- Returning from an ad click cannot trigger another advertisement.
- Failed advertisements never block user actions.
- App Open impressions do not reset the successful-workflow counter.
- Interstitial impressions do not reset the App Open interval.
- Lifecycle callbacks cannot produce duplicate advertisements.
- Stale Activity references are never retained.
- Processing and reader states suppress full-screen advertisements.
- Full-screen state is released after dismissal or failure.
- Navigation continues after dismissal or failure.

Use a monotonic clock such as SystemClock.elapsedRealtime() for in-session
cooldown calculations.

Use an injectable clock or equivalent abstraction for deterministic tests.

# ADVERTISING TESTS

Use fake advertisement managers and deterministic clocks.

Test:

- First launch never shows App Open
- First two sessions never show App Open
- Third qualified launch can show App Open
- Consent unavailable
- App Open unavailable
- Home becomes ready before App Open
- Four-hour App Open interval
- Two-per-day App Open limit
- Five-minute background threshold
- File-picker return suppression
- Camera return suppression
- Share-sheet return suppression
- External PDF intent suppression
- Orientation-change suppression
- Interstitial after two successful workflows
- Failed workflow does not count
- Cancelled workflow does not count
- No-output workflow does not count
- Duplicate completion callbacks count once
- Three-minute shared cooldown
- Three-interstitial session limit
- Interstitial/App Open collision
- Dismissal callbacks
- Failure callbacks
- Expired loaded advertisements
- Missing production App Open ID
- Navigation is never blocked
- No advertisement appears during protected workflows

Automated tests must never request live advertisements.

# UI TESTING

Add or update tests for:

- Search banner rendering
- Search banner click behavior
- Search back navigation
- Existing hero card behavior
- Quick Tools
- View All Tools
- Files destination
- Tools destination
- History destination
- Bottom-navigation state restoration
- Recent files
- Favorite tool ordering
- Loading, empty and error states
- No clickable unfinished tools
- Light theme
- Dark theme
- Large fonts
- Accessibility semantics
- Compact layouts
- Expanded layouts
- Advertisement containers do not overlap content
- Failed advertisements do not leave permanent blank space
- No advertisement interrupts file actions

Run applicable:

- Unit tests
- Compose UI tests
- Instrumentation tests
- Debug assembly
- Android lint

# REGRESSION REVIEW

Before completion, verify:

- All T0–T42 functionality remains available.
- The existing Home hero card remains recognizable and functional.
- Existing icons remain unchanged.
- Search returns the same data.
- Files, Favorites and History remain persisted.
- Reader rendering and gestures are unchanged.
- Sharing remains unchanged.
- No broad storage permission was added.
- No document data reaches advertising requests.
- No advertisement interrupts PDF work.
- No advertisement causes duplicate navigation.
- No advertisement appears unexpectedly.
- No unrelated code was changed.

# DEFINITION OF DONE

T43 is complete only when:

1. Search PDFs is a production-grade banner at the top.
2. The existing Home hero card is preserved.
3. The plain feature list is replaced with premium Quick Tool cards.
4. Files, Tools, History and Search are meaningful dedicated destinations.
5. Existing functionality remains unchanged.
6. Existing icons remain unchanged.
7. Navigation is smooth and state-safe.
8. UI adapts across supported device sizes.
9. Banner and native placements are unobtrusive.
10. Interstitial ads become eligible after every two successful eligible
    workflows.
11. Interstitial ads appear only at natural Result-to-Home transitions.
12. App Open ads never appear to first-time users.
13. App Open ads become eligible no earlier than the third qualified launch.
14. Shared full-screen frequency protection works.
15. Ads never interrupt document work.
16. Consent and test-ad requirements are respected.
17. Relevant tests, build and lint pass.
18. The final diff contains no unrelated changes.

# FINAL REPORT

Provide:

- Changed files
- Updated screen structure
- Search banner implementation
- Preserved Home components
- Quick Tools implementation
- Files, Tools and History refinements
- Banner and native placements
- Interstitial eligibility and frequency behavior
- App Open eligibility and frequency behavior
- Consent handling
- Test IDs used in debug
- Tests executed and results
- Remaining limitations
- Missing production configuration, if any
- Branch name
- Commit
- Push status
- Merge status
- Final main verification