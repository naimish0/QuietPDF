# QuietPDF: Offline PDF Tools — Persistent Product Context

## Important Execution Boundary

This document provides permanent product, quality, privacy, UX and engineering context.

It is not an instruction to implement the entire roadmap in one run.

Each Codex run receives one feature identifier and one feature prompt, such as:

- T0-PDF-Open
- T1-PDF-Read
- T2-PDF-Zoom
- T3-PDF-Reader-Modes
  T4-PDF-Fullscreen
  T5-PDF-Dark-Mode
  T6-PDF-Remember-Page
  T7-PDF-Search
  T8-PDF-Bookmarks
  T9-PDF-Table-Of-Contents
  T10-PDF-Health
  T11-Images-To-PDF
  T12-Images-PDF-Layout
  T13-Merge-PDF
  T14-Split-PDF
  T15-Extract-Pages
  T16-Delete-Pages
  T17-Rearrange-Pages
  T18-Rotate-Pages
  T19-Duplicate-Pages
  T20-Compress-PDF
  T21-Target-File-Size
  T22-Scanner-Capture
  T23-Scanner-Crop-Correction
  T24-Scanner-Enhancement
  T25-Scanner-Multi-Page
  T26-Protect-PDF
  T27-Remove-Password
  T28-Change-Password
  T29-Text-Watermark
  T30-Image-Watermark
  T31-Extract-Images
  T32-Fill-Forms
  T33-Sign-PDF
  T34-Annotate-PDF
  T35-Recent-Files
  T36-Favorites
  T37-History
  T38-File-Search-Sort
  T39-Share-PDF
  T40-Smart-Home
  T41-AdMob
  T42-Production-Hardening

Implement only the feature assigned to the current run.

Do not start, scaffold, partially implement or refactor for future features unless a minimal reusable change is strictly required by the current feature.

---

## Product

Product name:

QuietPDF: Offline PDF Tools

Product vision:

Build the highest-quality offline PDF toolkit for Android.

QuietPDF should become the PDF application users trust because it is:

- Fast
- Reliable
- Private
- Easy to understand
- Professionally designed
- Respectful of the user
- Fully functional without document uploads
- Free from intrusive advertising

The goal is not to provide the largest number of PDF tools.

The goal is to provide the highest-quality implementation of the tools people use regularly.

Business aspirations:

- 10M+ installs
- 4.8+ Play Store rating
- Sustainable policy-compliant AdMob revenue
- Strong reputation for privacy and reliability

These are aspirations, not claims that may be displayed as already achieved.

---

## Product Decision Test

Every design and engineering decision must answer:

“Does this make PDF work easier, faster and more reliable for the user?”

If the answer is no, do not add the complexity.

When priorities conflict, use this order:

1. Document integrity and prevention of data loss
2. Crash, ANR and corruption prevention
3. Privacy and offline processing
4. Functional correctness
5. Performance and memory safety
6. Accessibility and ease of use
7. Visual polish
8. Monetization

Revenue must never take priority over document safety or user experience.

---

## Core Product Principles

### Offline First

All PDF, image, scanner, compression, encryption, extraction and editing operations must run locally on the device.

The application must not upload documents to a server.

The application must not require:

- Account creation
- Login
- Cloud processing
- Internet access for core PDF functionality

User-initiated sharing through the Android Sharesheet is allowed. QuietPDF itself must not silently upload the document.

### Privacy First

Documents may contain personal, financial, medical, legal or business information.

Never send or expose:

- PDF contents
- Extracted text
- Scanned images
- Passwords
- Signatures
- Document filenames
- Document URIs
- Raw file paths
- Form values
- Watermark text
- Document metadata

Do not include sensitive information in:

- Logs
- Analytics
- Crash reports
- Ad requests
- Debug messages
- Saved state
- Telemetry
- Filenames generated from document contents

Persist only the minimum local metadata required for explicitly requested features such as Recents, Favorites, History and reading-position restoration.

### Reliability Over Feature Count

Never release or report a feature as complete if it:

- Produces corrupted output
- Drops pages
- Creates blank pages
- Changes colors unexpectedly
- Rotates pages incorrectly
- Loses user changes
- Produces misleading file sizes
- Fails sharing
- Leaks resources
- Works only for the simplest test fixture
- Is only a visual placeholder
- Fakes an unsupported PDF capability

If a capability cannot be implemented safely with the approved PDF engine, report the exact blocker instead of creating a partial or deceptive substitute.

---

## Core Tools Must Remain Free

Do not place these tools behind subscriptions, payment gates or mandatory rewarded ads:

- PDF Reader
- Images to PDF
- Merge PDF
- Split PDF
- Extract pages
- Delete pages
- Rearrange pages
- Rotate pages
- Compression
- Password protection
- Password removal
- Scanner
- Watermark
- Basic Fill and Sign
- Basic sharing

Possible future premium functionality may include:

- OCR
- AI translation
- Explicit opt-in cloud backup
- Advanced professional annotations
- Certificate-backed digital signing

Do not implement future premium functionality unless it has a separately approved feature specification, licensing review and privacy review.

Never insert a mandatory QuietPDF watermark into exported files.

---

## Primary Product Experiences

### PDF Reader

The PDF Reader is one of the highest-priority product experiences.

It should feel lightweight, smooth and distraction-free.

Target capabilities include:

- Lazy, memory-safe page rendering
- Smooth vertical scrolling
- Horizontal reading
- Continuous mode
- Single-page mode
- Pinch-to-zoom
- Double-tap zoom
- Pan while zoomed
- Fullscreen reading
- Auto-hiding toolbar
- Dark theme
- Optional non-destructive page appearance mode
- Remember last page
- Text search
- User bookmarks
- Embedded table of contents
- Fast navigation
- Accurate page indicator
- Large-document support

Never retain every rendered page or full-size bitmap in memory.

Reader UI must not permanently consume significant reading space.

### Scanner

Scanner reliability is more important than additional editing tools.

Target capabilities include:

- Camera capture
- Automatic document-edge detection
- Automatic crop
- Manual corner adjustment
- Perspective correction
- Automatic and manual rotation
- Shadow reduction
- Brightness and contrast adjustment
- Color, grayscale and black-and-white modes
- Multi-page capture
- Per-page preview
- Delete page
- Reorder pages
- Rotate individual pages
- Edit every page before export
- Local PDF generation

The scanner must not silently lose pages or produce blank or sideways output.

When automatic detection fails, manual crop must remain available.

Scanner processing must stay offline.

### Compression

Compression must provide real, measurable compression while preserving document integrity.

Modes:

- High Quality
- Balanced
- Maximum Compression
- Target File Size

Display:

- Original size
- Estimated output size
- Estimated percentage saved
- Actual output size after processing
- Actual percentage saved

Target File Size is best-effort. Never guarantee an exact size that cannot be reached safely.

Never claim compression by:

- Copying the source unchanged
- Renaming the file
- ZIP-wrapping the PDF
- Producing a larger file without warning
- Rasterizing everything without disclosing the quality trade-off

Validate compressed output by reopening it.

### Page Operations

Page operations must preserve:

- Original content
- Text
- Vectors
- Images
- Annotations
- Page dimensions
- Crop and media boxes
- Rotation
- Colors
- Forms where supported

Supported workflows include:

- Merge PDFs
- Split by page
- Split by range
- Split every X pages
- Extract pages
- Delete pages into a new PDF
- Rearrange pages
- Rotate pages
- Duplicate pages

Never modify a source PDF implicitly.

### Images to PDF

Expected workflow:

Select images
→ Preview
→ Reorder
→ Rotate
→ Remove unwanted images
→ Choose paper size
→ Choose orientation
→ Choose margins
→ Generate
→ Preview result
→ Save or share

Respect EXIF orientation, image aspect ratio, transparency and color.

Avoid loading every full-resolution image into memory.

### Password Operations

Support:

- Add password
- Remove password
- Change password
- Standards-compliant PDF AES encryption

Never encrypt a PDF as an arbitrary byte file.

Never persist, log, cache or include passwords in saved state.

Prefer AES-256 when it is supported by the selected PDF engine and remains compatible with expected readers.

### Watermarks

Support:

- Text watermark
- Image watermark
- Page selection
- Position
- Opacity
- Rotation
- Scale
- Accurate preview

Watermarks must always be optional.

Never add QuietPDF branding automatically.

### Fill, Sign and Annotation

Supported initial scope may include:

- Supported AcroForm fields
- Visible handwritten signature
- Imported signature image
- Free text
- Ink
- Highlighting when accurate text coordinates are available
- Precise positioning
- Zoom while positioning
- Large manipulation handles
- Undo
- Redo
- Delete

Clearly distinguish a visible signature from certificate-backed cryptographic signing.

Do not claim to edit existing PDF text unless the selected engine genuinely supports reliable text-object editing.

Do not fake form filling using cosmetic overlays when safe form writeback is unavailable.

### Extract Images

Extract actual embedded PDF image objects.

Do not describe rendered page screenshots as extracted images.

Support:

- Save selected
- Save all
- Share
- ZIP export

Keep extraction bounded and protect against extremely large decoded images or decompression bombs.

### Sharing

After successful operations, provide:

- Open
- Rename
- Share
- Delete
- Open folder or locate file where Android permits it

Use Android content URIs and temporary read permissions.

Never expose `file://` URIs.

Use accurate MIME types for PDFs, images and ZIP files.

Sharing cancellation or an unavailable target must not crash the app.

---

## Home and Navigation

The interface must be simple enough that a first-time user can complete a task without instructions.

Home should eventually provide:

- Recent Files
- Favorite Tools
- Main Tools
- Favorites
- History
- Storage statistics
- Guided workflows

Main tools include:

- Scan Document
- Images to PDF
- Merge PDF
- Split PDF
- Compress PDF
- Rearrange Pages
- Password Protect
- Remove Password
- PDF Reader
- Watermark
- Extract Images

Only expose a working tool as enabled.

Do not create navigation routes that lead to placeholders, blank screens or incomplete workflows.

Guided workflows may begin with:

“What would you like to do?”

Examples:

- Create PDF
- Compress PDF
- Merge PDFs
- Split PDF
- Scan Document
- Protect PDF
- Extract Images

Every major tool should be reachable with one clear action from Home.

Avoid:

- Deeply nested menus
- Hidden primary actions
- Overloaded toolbars
- Ambiguous icons without labels
- Multiple competing primary buttons
- Navigation that loses user progress
- Blank states without guidance

---

## Material 3 Design

Use the repository’s existing design system and architecture.

Design requirements:

- Material 3
- Dynamic color where supported
- Light and dark themes
- Rounded cards
- Professional typography
- Responsive layouts
- Smooth but restrained animations
- Clear hierarchy
- Large buttons
- Minimum 48dp touch targets
- Accessible content descriptions
- Predictable focus order
- Font scaling support
- TalkBack support
- Keyboard and switch-access consideration
- Phone, tablet and foldable layouts

Animations must not delay work or hide application state.

Respect reduced-motion accessibility preferences where supported.

---

## Performance and Memory

Never block the main thread with:

- PDF parsing
- Page rendering
- Image decoding
- Compression
- Encryption
- File copying
- Scanner processing
- Thumbnail generation
- ZIP generation
- Metadata inspection

Use:

- Structured concurrency
- Lifecycle-aware cancellation
- Bounded caches
- Controlled parallelism
- Lazy rendering
- Sampled image decoding
- Streaming file operations
- Stable identifiers
- Stale-result rejection
- Deterministic resource cleanup
- Transactional temporary output where practical

Always close:

- Streams
- ParcelFileDescriptors
- Renderers
- PDF pages
- Bitmaps
- Temporary files
- Camera resources

Support for 500MB files and 1000+ pages is a verification target, not a claim that may be made without measured evidence.

---

## Progress and Cancellation

Every operation lasting longer than approximately one second must provide visible progress.

Examples:

- Scanning
- Merging
- Splitting
- Compressing
- Extracting
- Encrypting
- Generating PDF
- Creating ZIP

Use determinate progress only when the engine provides meaningful measurable progress.

Never display fake percentages.

Provide cancellation where safe.

Cancellation must:

- Stop unnecessary background work
- Release resources
- Clean incomplete temporary output where possible
- Never show a false success result
- Never record a successful History item

---

## Error Handling

Never show only:

“Something went wrong.”

Provide specific, actionable messages for:

- Incorrect password
- Corrupted PDF
- Unsupported PDF
- Permission denied
- File no longer available
- Storage full
- Output provider failure
- Camera permission denied
- Camera unavailable
- No searchable text
- No embedded images
- Unsupported form
- Encryption not supported
- Insufficient memory
- Operation cancelled
- Output cleanup failure

Errors must not lead to blank screens or broken navigation.

---

## PDF Engine and Dependency Policy

Inspect existing dependencies before adding anything.

Android PdfRenderer can provide basic page rendering, but it does not automatically provide every advanced PDF capability.

Features such as these may require a more capable engine:

- Text extraction
- Search coordinates
- Table of contents
- Merge and split
- Content editing
- Form filling
- Encryption
- Compression
- Embedded-image extraction
- Annotations

Do not silently introduce:

- AGPL dependencies into a closed-source or ad-supported application
- A commercial SDK without an existing licence
- Cloud PDF processing
- A scanner or model requiring normal-operation network access
- An abandoned or security-sensitive library without review

Before adding a dependency, verify:

- Commercial-use licence
- Required notices
- Maintenance status
- Android compatibility
- APK size impact
- R8/ProGuard requirements
- Native ABI impact
- Offline behavior
- PDF fidelity
- Security history where relevant

If no compliant implementation is available, report the exact capability and licensing blocker and stop that feature.

---

## Advertisement Rules

AdMob monetization must remain isolated from PDF processing.

Allowed placements:

- Adaptive banner on Home
- Banner on Result screens
- Clearly labelled native ads between Home sections
- Interstitial only after successful completion and safe saving
- Rewarded ads for optional advanced convenience features

Never display ads:

- Before opening a PDF
- Before saving
- During reading
- During scanning
- During editing
- During compression
- During password entry
- During file selection
- While an operation is running
- After failure or cancellation

Ad failure, missing inventory, offline state or consent failure must never block a PDF feature.

Debug builds must use official test ad IDs.

Production IDs must be configuration-driven and must never be invented.

Use current consent requirements where applicable.

Because ads require networking, privacy and Data Safety disclosures must distinguish:

- Document processing remains local.
- Ad SDKs may use network/device data according to disclosed configuration.
- Document contents and metadata are never supplied to the ad SDK.

---

## Testing Expectations

Test features using relevant fixtures from this matrix:

- Small PDFs
- Large PDFs
- 500MB PDFs where the environment permits
- 1000-page PDFs
- Password-protected PDFs
- Wrong-password cases
- Corrupted PDFs
- Unsupported PDFs
- Image-heavy PDFs
- Scanned PDFs
- PDFs containing forms
- PDFs containing bookmarks/outlines
- Mixed page sizes
- Rotated pages
- Transparent images
- Low storage
- Low memory
- Orientation changes
- Background and foreground transitions
- Process recreation
- Revoked URI permission
- Android 8 through Android 16 where available
- Phones
- Tablets
- Foldables

Do not claim a device, API level, file size or performance target was tested unless it was actually tested.

For an individual feature branch, run focused tests and the smallest relevant build checks.

Run broader regression, accessibility, release and performance validation during production-hardening work.

---

## Feature-Branch Workflow

Every feature must use an independent user-created branch, for example:

- `T0-PDF-Open`
- `T1-PDF-Read`
- `T2-PDF-Zoom`

### Required Git Identity

Every commit must use only this repository-local Git identity:

git config user.name "Naimish Gupta"
git config user.email "naimishgupta983842377@gmail.com"

Rules:

- Use these exact values for every commit.
- Never use another author name or email.
- Never use `git config --global`.
- Verify the identity before every commit:
    - `git config user.name`
    - `git config user.email`
- If the values are different, correct them before committing.
- If the identity cannot be configured or verified, stop and report the blocker.

### Feature Execution

1. The user creates the exact feature branch from an updated, clean `main`.
2. Verify that the current branch exactly matches the first pending queue item. Never create the
   feature branch on the user's behalf.
3. Configure and verify the required repository-local Git identity.
4. Implement only the assigned feature.
5. Add focused tests.
6. Run the relevant verification.
7. Review the diff for unrelated changes.
8. Verify the required Git identity again.
9. Create one intentional feature commit.
10. Push the feature branch without force-pushing.
11. Open a focused pull request for the feature branch.
12. Merge the pull request into `main` only after required checks and scoped verification pass.
13. Check out `main`, pull it using fast-forward-only behavior, and verify the merged result.
14. Mark the queue item `COMPLETED` only after the merge and verification, then push that queue-only
    status update and pull `main` again.
15. Do not create the next feature branch. Continue only after the user creates the exact next branch.

### Never

- Use a Git identity other than:
    - Name: `Naimish Gupta`
    - Email: `naimishgupta983842377@gmail.com`
- Configure Git identity globally.
- Create a feature branch; feature branches are created manually by the user.
- Start feature work while on `main` or on a branch that does not exactly match the first pending
  queue item.
- Mix multiple queued features in one branch.
- Begin the next feature before the current feature is merged.
- Force-push.
- Rewrite published history.
- Discard unrelated changes.
- Automatically resolve uncertain merge conflicts.
- Merge a feature with failing scoped tests.
- Continue after a genuine licensing or security blocker.
- Bypass branch protection.

Every feature must be merged through a pull request. Never bypass branch protection or replace the
pull-request merge with a direct feature merge into local `main`.

When an external runner owns Git operations, Codex must not independently stage, commit, switch branches, merge, pull, or push. The external runner must still use the required Git identity for every commit.

## Definition of Feature Complete

A feature is complete only when:

- Acceptance criteria are satisfied
- The user workflow is complete
- No placeholder or dead control remains
- Relevant unit/UI tests pass
- Relevant build checks pass
- Error and cancellation paths are handled
- UI work runs on the correct thread
- Background work is bounded and cancellable
- Owned resources are closed
- Output is reopened or otherwise validated where relevant
- Source documents remain unchanged unless explicitly requested
- Accessibility has been considered
- Privacy rules are preserved
- No sensitive logging was introduced
- No unrelated files were changed
- Limitations are documented honestly
- The feature branch contains only that feature

Quality is more important than marking the feature complete quickly.

If the implementation cannot meet these conditions, report it as blocked or failed rather than claiming completion.

---

## Final Principle

QuietPDF should earn trust through behavior.

Users should recommend it because it:

- Opens files reliably
- Reads smoothly
- Scans accurately
- Compresses without corruption
- Shares correctly
- Protects private documents
- Explains errors clearly
- Respects the user’s time
- Keeps core tools free
- Uses advertising responsibly
- Never gets in the way
