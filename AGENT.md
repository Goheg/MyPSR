# Agent Instructions

Agent.md — Mobile PSR (Problem Steps Recorder)

1. Project Overview

Mobile PSR is an Android utility application inspired by the Windows Problem Steps Recorder. It automatically documents troubleshooting and how-to steps by capturing UI interactions (clicks, text inputs, scrolls) alongside corresponding screenshots, then compiling them into a single, lightweight, self-contained HTML report.

Primary Goal: Enable users, support agents, and QA engineers to share clear, visual step-by-step guides over low-bandwidth networks without recording massive video files. The app also serves as an in-app reader for viewing generated or received HTML reports.

2. Tech Stack & Environment

Language: Kotlin (100% Kotlin, no Java unless required by interop)

UI Framework: Jetpack Compose with Material 3

Architecture: MVVM + Clean Architecture (UI -> ViewModel -> Repository -> Data Sources / Services)

Local Persistence: Room Database (sessions, step metadata, report file paths)

Concurrency: Kotlin Coroutines & StateFlow / SharedFlow

Minimum SDK: API 26 (Android 8.0)

Target SDK: Latest stable Android API (ensure Android 14+ foreground service compliance)

HTML Viewer: Android WebView wrapped in Compose AndroidView

Sharing: Android FileProvider with Intent.ACTION_SEND

3. Recommended Package Structure

Keep the codebase strictly organized under the app's root package:

com.mobilepsr.app/
├── data/
│   ├── local/          # Room entities, DAOs, AppDatabase
│   └── repository/     # SessionRepository & file storage helpers
├── model/              # Domain models (StepEvent, CapturedStep, RecordingState, RedactionBox)
├── service/
│   ├── CaptureAccessibilityService.kt  # Intercepts UI events & sensitive fields
│   ├── CaptureService.kt               # Foreground service managing MediaProjection & VirtualDisplay
│   ├── OverlayService.kt               # Floating Start/Pause/Stop widget (WindowManager)
│   └── SessionManager.kt               # Singleton coordinating Accessibility + MediaProjection
├── generator/
│   └── HtmlReportGenerator.kt          # Single-file HTML/CSS/JS compiler & Base64 encoder
├── ui/
│   ├── theme/          # Material 3 futuristic/clean theme, typography, colors
│   ├── onboarding/     # Permissions checklist & setup flow
│   ├── home/           # Dashboard, session history, New Recording FAB
│   ├── review/         # Post-capture step review & manual blur/redaction canvas
│   └── viewer/         # In-app WebView HTML reader
└── util/               # Image compression, storage monitor, XSS sanitizer, FileProvider utils


4. Core Functional Requirements

4.1. Permissions & Lifecycle Safety

Manage runtime requests and system settings intents for:

SYSTEM_ALERT_WINDOW (Floating overlay controls)

AccessibilityService (UI interaction interception)

MediaProjection (Screen capture consent)

POST_NOTIFICATIONS (Android 13+ foreground service notification)

FOREGROUND_SERVICE & FOREGROUND_SERVICE_MEDIA_PROJECTION

Android 14+ Compliance: Always call startForeground() with ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION before invoking MediaProjectionManager.getMediaProjection().

Permission Revocation Handling: Detect if Accessibility or MediaProjection is revoked mid-session. Pause/save the active session safely and prompt re-authorization without crashing.

4.2. Floating Overlay Controller (OverlayService)

Draggable floating widget using WindowManager with Start, Pause, and Stop controls.

Display a clear, unobtrusive visual indicator (e.g., pulsing red dot or compact status pill) while recording is active.

4.3. Data Capture Engine

Accessibility Service (CaptureAccessibilityService):

Listen for TYPE_VIEW_CLICKED, TYPE_VIEW_TEXT_CHANGED, and TYPE_VIEW_SCROLLED.

Extract human-readable step descriptions (e.g., "Clicked 'Wi-Fi' in Settings"), target view Rect bounds, package name, and timestamp.

Automatic Privacy Masking: Inspect interacted and visible nodes for isPassword == true or sensitive input types (e.g., credit cards, PINs). Pass sensitive bounding boxes to the capture engine so those regions are automatically masked/blurred on the Bitmap before saving.

Screen Capture (CaptureService):

Manage MediaProjection, VirtualDisplay, and ImageReader.

Capture screenshots on qualifying accessibility events.

Adaptive Throttling: Debounce/throttle captures during rapid scrolling or identical static frames to conserve battery, CPU, and RAM.

Compression: Scale down if needed, compress Bitmap to JPEG (balanced quality for readability and low file size), and encode to Base64.

Storage Safeguards: Check available internal storage before saving frames. Auto-save and cleanly stop the session if storage drops below a safe threshold (e.g., 50 MB).

4.4. Post-Capture Review & Manual Redaction

Before generating the final HTML, show a review screen where users can:

Reorder or delete unwanted steps.

Edit step descriptions.

Manually draw blur/blackout rectangles over any additional sensitive areas on a screenshot.

4.5. Single-File HTML Generator & Sharing

Output a standalone .html file saved to context.filesDir/reports/.

Embed all screenshots as Base64 data URIs (data:image/jpeg;base64,...) so reports work 100% offline.

Design & UX: Clean, modern, slightly futuristic aesthetic (dark-mode friendly, crisp typography, subtle borders).

Interactive Features (Embedded JS/CSS):

Collapsible/expandable step cards.

Search/filter bar across step descriptions and app names.

Timestamp badges and visual click-location markers overlaid on screenshots.

Sharing: Use FileProvider to share .html files to external apps (WhatsApp, Email, etc.).

4.6. In-App HTML Reader & Dashboard

Home Screen: Displays saved sessions from Room (title, date, step count, file size) with quick View, Share, and Delete actions, plus a primary New Recording FAB.

Report Viewer: Renders local .html files inside an in-app WebView and supports opening external .html reports received from other users.

5. Coding Standards & Safeguards

Memory Management: Always close Image objects from ImageReader in a finally block, recycle temporary Bitmap instances when no longer needed, and release VirtualDisplay and MediaProjection cleanly on stop.

HTML Security (XSS Prevention): Always HTML-escape all dynamically captured text (app names, button labels, typed text) before injecting it into the HTML template.

WebView Security: Configure WebView safely when rendering local files; avoid exposing unnecessary @JavascriptInterface bridges.

State Management: Expose UI state from ViewModels using immutable data classes wrapped in StateFlow.

No Silent Failures: Log meaningful diagnostics and surface clear, user-friendly error messages via snackbars or dialogs.

6. Phased Implementation Roadmap

Follow these phases sequentially. Complete one phase at a time, summarize all files created/modified, and wait for user approval before proceeding to the next phase.

[x] Phase 1: Project Setup, Dependencies & Manifest

Configure build.gradle.kts (Compose Material 3, Room with KSP, Coroutines, Navigation Compose).

Declare permissions, services, accessibility_service_config.xml, and FileProvider (file_paths.xml) in AndroidManifest.xml.

Set up the base Material 3 theme.

[x] Phase 2: Data Layer

Create Room entities (SessionEntity, StepEntity), DAOs, AppDatabase, and SessionRepository.

Define domain models (StepEvent, CapturedStep, RecordingState).

[x] Phase 3: Capture Engine

Implement CaptureAccessibilityService with event filtering, human-readable descriptions, and password/sensitive-node bounding box detection.

Implement CaptureService with MediaProjection, VirtualDisplay, ImageReader, adaptive throttling, automatic bounding-box masking, JPEG/Base64 compression, and storage checks.

[x] Phase 4: Floating Overlay & Session Coordination

Implement SessionManager singleton bridging Accessibility events and MediaProjection captures.

Implement OverlayService with draggable Start/Pause/Stop controls and active recording indicator.

Add graceful handling for mid-session permission revocation.

[x] Phase 5: HTML Generator & Exporter

Build HtmlReportGenerator with embedded futuristic CSS/JS, Base64 image injection, XSS escaping, collapsible steps, search bar, and click markers.

Implement local file saving (context.filesDir/reports/) and FileProvider sharing utility.

[x] Phase 6: Jetpack Compose UI

Build Onboarding/Permissions screen.

Build Home Dashboard with session history and New Recording trigger.

Build Post-Capture Review & Manual Redaction screen.

Build WebView-based In-App Report Reader with support for opening received .html files.

7. Agent Working Rules

Respect the Phases: Never attempt to generate the entire app in one shot. Finish the current phase cleanly, verify imports and Gradle compatibility, and pause for confirmation.

Keep Context Updated: When a phase is completed, update the checkboxes in Section 6 of this Agent.md file.

Ask Before Deviating: If an Android API version or dependency conflict requires a structural change, explain the trade-off briefly and confirm before refactoring.
