# ExHabere Specifications

This document consolidates the product, design, technical, and validation specifications for ExHabere. Build prerequisites and commands are maintained in the [README](README.md)

## Contents

- [Product Requirements and Functional Specification](#product-requirements-and-functional-specification)
- [UI/UX Specification](#uiux-specification)
- [Welcome and Onboarding Design Specification](#welcome-and-onboarding-design-specification)
- [Guided Plan Setup Specification](#guided-plan-setup-specification)
- [Technical Stack and Architecture Specification](#technical-stack-and-architecture-specification)
- [Testing Plan](#testing-plan)

---

## Product Requirements and Functional Specification

### 1. Executive Summary & Vision

#### 1.1 Product Vision
An open-source, local-only Android application designed to help users systematically break unwanted habits (such as doom scrolling, smoking, or sugar consumption) through evidence-based behavioral psychology. Instead of relying solely on willpower or day-counting streaks, the app focuses on trigger identification, active friction, and structured replacement behaviors.

#### 1.2 Core Principles
* **Single-Habit Focus:** The user targets only **one habit at a time** to maximize focus and likelihood of success.
* **Privacy & Local-Only First:** Zero network dependencies, zero tracking servers, and local SQLite/Room storage. Users can log sensitive triggers without privacy concerns.
* **Observation Before Action:** Users are discouraged from quitting "cold turkey" on day one. Instead, they begin with a 1-week observation period to gather data on their habit triggers.
* **Actionable Emergency Intervention:** Active urges are met with real-time grounding or replacement choices and a visible pause. The overlay adds friction; it is not a guaranteed blocker for other apps.

---

### 2. Behavioral Psychology Foundations

The application is informed by five behavioral concepts:

1. **The Habit Loop (Cue $\rightarrow$ Routine $\rightarrow$ Reward):** To eliminate a habit, the trigger (cue) must be identified, and the routine swapped while fulfilling the underlying reward.
2. **Urge Surfing & Peak Waves:** Urges often rise and fall over a few minutes, though their duration varies. A short pause and grounding exercise can help users ride out an urge; the timer does not guarantee that it will pass.
3. **Implementation Intentions (If-Then Planning):** Pre-determining replacement behaviors ("If [Trigger], then I will [Alternative Action]") reduces decision fatigue during moments of stress or craving.
4. **Friction Injection:** Injecting physical and digital delays during peak craving windows interrupts automatic, compulsive behavior loops.
5. **Habit formation as a reference, not a deadline:** One habit-formation study reported a median of 66 days for a behavior to approach automaticity, with substantial variation between people ([Lally et al.](https://doi.org/10.1002/ejsp.674)). ExHabere uses 66 days as its default configurable mastery target; it does not measure neural change or treat that duration as a universal requirement.

---

### 3. Product Architecture & Phases

```
┌─────────────────────────────────────────────────────────┐
│               PHASE 1: OBSERVATION PHASE                │
│    (Suggested: 1 Week of logging triggers & urges)      │
└────────────────────────────┬────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────┐
│              PLAN SETUP & TRIGGER ANALYSIS              │
│   (Review top triggers, select relief techniques, and  │
│          configure replacement "If-Then" routines)      │
└────────────────────────────┬────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────┐
│                PHASE 2: ACTION PHASE                    │
│   (Clean Tracker + Active Emergency Floating Lockout)    │
└────────────────────────────┬────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────┐
│             MASTERY TARGET & CELEBRATION                │
│  (7-Day Spark, 21-Day Anchor, 66-Day Default Target)     │
└─────────────────────────────────────────────────────────┘
```

---

### 4. Detailed Functional Requirements

#### 4.1 Phase 1: Observation Phase
* **Objective:** Track urge frequency, contextual triggers, and emotional states without pressure to perform perfectly.
* **Features:**
  * **Frictionless Logging Button:** A quick-log sheet records an urge-only event or an engaged-in-habit event with its context.
  * **Quick Context Tagging:**
    * *Emotional State:* Bored, Stressed, Tired, Anxious, Lonely, Neutral.
    * *Location/Context:* Bed, Desk, Couch, Transit, Social.
    * *Dynamic Tag Expansion:* Selecting "Other" allows custom tag creation, which automatically saves into the quick-select tag list for future logs.
  * **Observation Dashboard:** Keeps logging and plan setup uncluttered; a **View insights** link opens the separate reflection space for heatmaps, trends, and trigger summaries.
  * **Persistent Trigger History:** Observation logs, peak-hour patterns, and emotional/context shares are retained in Room and remain available in later phases as the baseline for comparison. Returning to Observation adds new observations; it does not erase earlier logs.

#### 4.2 Plan Setup & Transition Engine
* **Objective:** Synthesize observation data into a concrete action plan when the user decides to move to Phase 2 (suggested after 7 days).
* **Features:**
  * **Trigger Insights Summary:** Highlights top identified triggers (e.g., *"65% of urges happen in Bed when Bored around 10:00 PM"*).
  * **"If-Then" Replacement Builder:** Prompts the user to set a primary alternative todo for their dominant trigger.
  * **Urge Relief Selection Menu:** User selects their favored physical/psychological grounding techniques from a curated library (Box Breathing, Physiological Sigh, Cold Water Reset, 5-4-3-2-1 Grounding).

#### 4.3 Phase 2: Action Phase & Clean Tracker
* **Objective:** Support active habit cessation through progress tracking and emergency craving management.
* **Features:**
  * **Clean Counter:** Measures time clean (hours/days) while prioritizing resistance success rate over strict punitive streak resets.
  * **Dual-Entry Urge Logging:** The dashboard presents two distinct actions:
    1. **`+ Log an urge` (Quick Log):** Opens the shared modal bottom sheet to record intensity, context/tags, and whether the user only experienced an urge or engaged in the habit. It records an Action-origin event and does **not** reset or otherwise mutate the clean-time baseline.
    2. **`🚨 EMERGENCY SUPPORT` (Emergency Lockout):** Starts the emergency decision flow and five-minute floating lockout by default (duration remains configurable). Its post-timer outcome is recorded separately from quick logs; an emergency give-in follows the existing clean-time reset policy.
  * **Action-Phase Urge Patterns:** The dashboard keeps analytics off the primary action surface and provides a **View insights** link. In Insights, the Action heatmap includes Action-origin quick logs and emergency outcomes and compares them with the retained Observation baseline on a shared scale.
  * **Outcome and Origin Semantics:** Quick-log outcomes are `URGE_ONLY` and `ENGAGED_IN_HABIT`; emergency outcomes are `EMERGENCY_PASSED` and `EMERGENCY_GAVE_IN`. Each event stores its `OBSERVATION` or `ACTION` phase origin independently, so transitions do not relabel history.

#### 4.4 Emergency Support & Floating Lockout System
* **Objective:** Add a deliberate pause and support an alternative response during an active urge.
* **Workflow:**
  1. **User Taps Emergency Button:** App presents two binary choices:
     * **Option A: Urge Relief Method** (A favored grounding method randomly selected from the user's pre-approved menu, e.g., Box Breathing, Mammalian Dive Reflex).
     * **Option B: Alternative Todo** (The pre-set replacement behavior tied to their primary trigger).
  2. **Commitment & Timer Start:** User selects Option A or B and taps **"Commit"** (Default: **5 minutes**, configurable: 2 min, 5 min, 10 min).
  3. **Floating Overlay (`SYSTEM_ALERT_WINDOW`):**
     * Main app minimizes.
      * A persistent floating widget displays the remaining countdown over normal app screens. Android may suppress overlays on protected system screens.
      * Serves as visual friction and a reminder to pause; it does not guarantee that other apps are blocked.
  4. **Post-Timer Prompt:**
      * *"Urge Passed"* $\rightarrow$ Logs an `EMERGENCY_PASSED` resistance event and updates resistance stats.
      * *"Gave In"* $\rightarrow$ Logs an `EMERGENCY_GAVE_IN` event gently and non-judgmentally; this is distinct from an engaged-in-habit quick log.

#### 4.5 Settings & Habit Management
* **Return to Observation Phase:** Allows users to re-enter Observation mode to log emerging triggers. Existing trigger history, custom tags, and clean-time statistics/baselines remain unchanged; only the Observation-period clock restarts.
* **App Reset:** Reset the active habit's data to start fresh; archived mastered habits remain stored on-device.
* **Mastered Habit Archive:** Choosing to archive a mastered habit saves its configuration, complete trigger history, and context tags locally before starting a fresh habit. After archiving, the user returns to the Welcome flow at habit naming (step 2). Settings lists archived habits without exposing their statistics until the user selects one.
* **Timer & Duration Configuration:** Customize default emergency lockout times, observation target length, milestone targets, and preferred pause overlay position (top, middle, bottom, or full screen).
* **Import / Export:** Export the current habit configuration, complete trigger history, and preset/custom context-tag usage to a versioned JSON file through Android's document picker. Restore accepts only valid supported ExHabere backups and, after an explicit replacement warning, atomically replaces the current habit data. The user chooses the file location; the app requests no broad storage permission.

#### 4.6 Milestone Progression & Celebration System
* **Milestone 1 (Day 7 — "The Circuit Breaker"):** Recognizes the first week of Action-phase practice; it does not assume a specific withdrawal experience.
* **Milestone 2 (Day 21 — "The Structural Anchor"):** Recognizes continued practice without implying a guaranteed change in effort or brain activity.
* **Milestone 3 (Configured mastery target — 66 days by default):** Opens the Celebration Screen when the user reaches their chosen clean-time target. This is a progress milestone, not a scientific test of automaticity; users can archive the habit or continue tracking.

#### 4.7 Dedicated Insights View
* **Objective:** Provide an intentional reflection space without adding analytical cognitive load to the Observation or Action dashboard.
* **Progressive disclosure:** Insights opens with three collapsed summary cards—Recovery trend, Peak trigger windows, and Core patterns & context. Tapping expands a card with a Compose size/visibility animation; only one card is expanded at a time.
* **Recovery trend:** Shows two aligned, individually labeled weekly Action quick-log measures: event count and average user-reported urge intensity. The count track uses a count scale; the intensity track uses mild/moderate/strong. Emergency outcomes are excluded because they do not capture a reported intensity. Only elapsed/history weeks are plotted (future weeks are not shown as zero-count weeks). The configured mastery target (66 days / 10 weeks by default) is a milestone, not an endpoint; new weeks are appended beyond Week 10 as Action history continues. A Week 1 comparison is shown only when both Week 1 and a later logged week contain data; sparse data uses neutral copy rather than a fabricated percentage.
* **Peak trigger windows:** Provides a plain-language weekday and four-hour local-time summary. Expanded detail compares Observation events with Action events (quick logs and emergency outcomes) using Monday-first heatmaps on one shared color scale and outlines the most frequent window.
* **Core patterns & context:** Provides top feeling and location/context tags with percentages and expanded top-five bars. Includes preset and custom tags from quick logs in both phases; emergency-only events are excluded because they contain no user-selected context.
* **Privacy and availability:** Insights are calculated from the existing on-device Room log history and require no schema change, network access, or analytics SDK. Empty and sparse states clearly explain what data is not yet available.

---

### 5. Non-Functional Requirements

* **Platform:** Android-native (Jetpack Compose, Kotlin).
* **Privacy & Security:** Local storage only (SQLite / Room DB). Zero network dependencies or third-party tracking SDKs.
* **Open Source:** Clean, modular codebase suitable for open-source contribution and community audits.
* **System Permissions:** Requires `SYSTEM_ALERT_WINDOW` ("Display over other apps") for the floating overlay timer feature.

---

## UI/UX Specification

### 1. Design System & Visual Guidelines

* **Color Palette:** Calming, low-dopamine palette: deep slate background `#0F172A`, dark slate/steel Material 3 surfaces (base surface `#1E293B`, with tonal container and border variations), and soft mint `#34D399` accents. High contrast reserved primarily for the **Emergency Support** button.
* **Typography:** Clean sans-serif (Roboto / System default) with clear hierarchy and readable line spacing.
* **Tone:** Empowering, objective, and non-judgmental.

---

### 2. Navigation Architecture

```
                    ┌───────────────────────────────────┐
                    │       Onboarding & Setup          │
                    └─────────────────┬─────────────────┘
                                      │
                   ┌──────────────────┴──────────────────┐
                   ▼                                     ▼
      ┌─────────────────────────┐           ┌─────────────────────────┐
      │ Phase 1: Observation    │           │ Phase 2: Action Phase   │
      │ Dashboard               │           │ Dashboard               │
      └────────────┬────────────┘           └────────────┬────────────┘
                   │                                     │
         ┌─────────┴─────────┐                 ┌─────────┴─────────┐
         ▼                   ▼                 ▼                   ▼
    [ Quick Log ] [ Plan Setup ] [ Insights ]  [ Emergency Support ] [ Settings ]
          ▲                                      ▲
          └──── available from either dashboard ┘
                                               │
                                               ▼
                                      [ Floating Overlay ]
```

The Quick Log Dialog is reachable from both the Phase 1 Observation dashboard and the Phase 2 Action dashboard. Both entry points use the same context, intensity, and tag controls; the saved event's phase origin reflects the dashboard from which it was opened. Both dashboards link to a separate Insights View; analytical summaries and visualizations do not appear on the primary dashboards.

Guided Plan Setup is the required transition from Observation into Action, and is also available from Settings during Action/Mastery for re-tuning. Back navigation returns to the previous screen without changing phase or clean time.

---

### 3. Screen Wireframes & Detailed Component Layouts

The wireframes below are illustrative. Habit names, counts, percentages, and timestamps shown in examples are synthetic and do not represent user data.

#### 3.1 Phase 1: Observation Dashboard

```
+-------------------------------------------------------+
|  Habit: [ Doomscrolling ]                 [⚙️ Settings] |
|  Phase 1: Observation • Day 3 of 7                    |
+-------------------------------------------------------+
|                                                       |
|  +-------------------------------------------------+  |
|  |                                                 |  |
|  |                [ LOG AN URGE ]                  |  |
|  |           (Primary Full-Width Button)           |  |
|  |                                                 |  |
|  +-------------------------------------------------+  |
|                                                       |
|   "Observe your patterns without pressure to stop."   |
|                                                       |
+-------------------------------------------------------+
|  [ View insights ]                                    |
|  [ Review patterns & set up plan > ]                  |
+-------------------------------------------------------+
```

The Observation dashboard keeps logging and plan setup prominent. Trigger counts, dominant tags, peak windows, and heatmaps are available from **View insights**, not inline on the dashboard.

---

#### 3.2 Quick Log Popup Dialog (Dynamic Tag Expansion)

When the user taps **"LOG AN URGE"**, a modal bottom sheet pops up:

```
+-------------------------------------------------------+
|  Log Urge Context                                    |
+-------------------------------------------------------+
|  EMOTION / FEELING:                                   |
|  [ Bored ]  [ Stressed ]  [ Tired ]  [ Anxious ]      |
|  [ + Custom Tag ] <-- (User-created tag saved here)   |
|  [ + Other... ]   <-- (Opens text input field)       |
|                                                       |
|  LOCATION / ENVIRONMENT:                              |
|  [ Bed ]  [ Desk ]  [ Couch ]  [ Transit ]            |
|  [ + Other... ]                                       |
|                                                       |
|  INTENSITY:                                           |
|  ( Mild )    ( Moderate )    ( Strong )               |
|                                                       |
|  [ CANCEL ]                              [ SAVE LOG ] |
+-------------------------------------------------------+
```

> **Dynamic Tag Expansion Rule:** Entering text under "Other" creates a new tag. The app automatically saves this tag to the user's local Room database and places a quick-select chip in future log dialogs.

---

#### 3.3 Phase 2: Action Phase Dashboard (Clean Tracker)

```
+-------------------------------------------------------+
|  Habit: [ Doomscrolling ]                  [ Settings] |
+-------------------------------------------------------+
|  +-------------------------------------------------+  |
|  | MILESTONE PROGRESS                 4 / 66 days  |  |
|  | ━━━⚡━━━━━━━━⚓━━━━━━━━━━━━━━━━━━━━━━🏆         |  |
|  |    7          21                       66      |  |
|  | CURRENT CLEAN TIME                             |  |
|  | 4 DAYS, 12 HOURS                                |  |
|  | Resistance success: 88%                         |  |
|  +-------------------------------------------------+  |
|  [             EMERGENCY SUPPORT                  ]  |
|  [ + Log an urge                                    ]  |
|  +-------------------------------------------------+  |
|  YOUR REPLACEMENT PLAN                               |
|  "When I feel Bored in Bed, I will Read 2 pages..." |
|  +-------------------------------------------------+  |
|  [ View insights ]                                    |
+-------------------------------------------------------+
```

The mastery progress bar appears immediately above the clean-time counter. The 7-day Circuit Breaker (⚡), 21-day Structural Anchor (⚓), and configured mastery target (🏆) are marked along the bar, with their day numbers below; completed milestones are highlighted and their symbols enlarged. Tapping any milestone icon shows a short explanatory tooltip near that icon, including for upcoming milestones. This replaces the separate milestone list to keep the dashboard compact. The secondary **[ + Log an urge ]** action directly beneath Emergency Support opens the same context/intensity bottom sheet as Observation; saving a quick log never resets clean time. The dashboard contains no analytical charts; **[ View insights ]** opens the dedicated reflection space.

---

#### 3.4 Dedicated Insights View (Filtered Progressive Disclosure)

The Insights View is reachable from either phase dashboard. It presents a top outcome filter segment followed by three collapsed, human-readable cards; tapping a card expands its detail with a size animation. One card is expanded at a time.

```
+-------------------------------------------------------+
|  Insights                                      [Back] |
|  A quiet space to reflect. Filter by outcome or tap   |
|  a card to explore.                                   |
|                                                       |
|  [ All Urges ]  [ Acted-On Only ]  [ Logged Only ] |
|  ---------------------------------------------------  |
|                                                       |
|  Recovery trend (Acted-On Only)               [+]     |
|  Your acted-on urge frequency is down 22%             |
|  since Week 1.                                 [spark]|
|                                                       |
|  Peak trigger windows                         [+]     |
|  Acted-on urges most often appear around              |
|  8 PM–12 AM on Fridays.                 [20:00–00:00] |
|                                                       |
|  Core patterns & context                      [+]     |
|  Top feeling: Stressed (62%) · Top context: Bed (51%) |
|  [#Stressed] [#Bed]                                   |
+-------------------------------------------------------+
```

##### Outcome Filter Segment Rules:
1. **`[ All Urges ]` (Default):** Evaluates all recorded events (quick logs and emergency interventions).
2. **`[ Acted-On Only ]`:** Filters history to show only `ENGAGED_IN_HABIT` quick logs and `EMERGENCY_GAVE_IN` outcomes. Highlighted using Desaturated Terracotta-Rose (`#C2637E`).
3. **`[ Logged Only ]`:** Filters history to show only `URGE_ONLY` quick logs and `EMERGENCY_PASSED` outcomes. Highlighted using Soft Mint (`#34D399`).

##### Detailed Card Behaviors:
* **Recovery trend:**
  * **All / Logged Only:** Displays standard Soft Mint (`#34D399`) weekly count track and reported intensity line.
  * **Acted-On Only:** Displays weekly frequency of gave-in events plotted with a `#C2637E` accent trendline. Intensity calculations are omitted since emergency give-in events do not record user-reported intensity.
* **Peak trigger windows:**
  * **Single Gradient Rule:** When a specific filter (*Acted-On Only* or *Logged Only*) is active, the heatmap grid renders using **only** that filter's corresponding color gradient scale.
    * **Acted-On Heatmap:** Scaled from transparent slate (`0% opacity`) to solid Desaturated Terracotta-Rose (`#C2637E`). Peak recurrence cells are outlined in `#C2637E`.
    * **Logged-Only Heatmap:** Scaled from transparent slate to solid Soft Mint (`#34D399`). Peak cells are outlined in `#34D399`.
  * **All Urges View:** Displays Monday-first heatmaps on a unified scale with dual-legend indicators.
* **Core patterns & context:**
  * Displays top feeling and location micro-chips (`[#Stressed]`, `[#Bed]`) directly on the collapsed card summary row.
  * Expanding displays top five tags as percentage bars, calculated using only logs matching the active filter selection.

---

#### 3.5 Emergency Support Screen & Floating Lockout Overlay

##### Full-Screen Emergency Decision Screen
```
+-------------------------------------------------------+
|  AN URGE STRUCK — CHOOSE YOUR RESPONSE                |
+-------------------------------------------------------+
|                                                       |
|  +-------------------------------------------------+  |
|  | OPTION A: IMMEDIATE RELIEF                      |  |
|  | 🧘 Box Breathing Exercise (2–4 Min)             |  |
|  |                                                 |  |
|  | [ Select Option A ]                             |  |
|  +-------------------------------------------------+  |
|                                                       |
|  +-------------------------------------------------+  |
|  | OPTION B: ALTERNATIVE ACTION                    |  |
|  | 📖 Read 2 pages of a physical book             |  |
|  |                                                 |  |
|  | [ Select Option B ]                             |  |
|  +-------------------------------------------------+  |
|                                                       |
|  LOCKOUT TIMER DURATION:                              |
|  ( ) 2 Min    (*) 5 Min (Default)    ( ) 10 Min       |
|                                                       |
|  [ COMMIT & LOCKOUT ]                                 |
+-------------------------------------------------------+
```

##### Floating System Overlay Widget (`SYSTEM_ALERT_WINDOW`)
When the user taps "COMMIT", the main app minimizes and the floating timer appears over normal app screens at the position configured in Settings (top, middle, bottom, or full screen), where Android permits overlays. It adds visual friction but is not a guaranteed app blocker:

```
+-----------------------------------------------+
| ⏱️ 04:32 | Active: Box Breathing Exercise    |
+-----------------------------------------------+
```

When the timer reaches `00:00`, the overlay changes to the **Post-Emergency Prompt**. The user can also end the pause early; no outcome is recorded until they choose one:

```
+-------------------------------------------------------+
|  TIMER COMPLETE                                       |
+-------------------------------------------------------+
|  Did the urge pass?                                   |
|                                                       |
|  [ 🎉 YES, URGE PASSED ]       [ 😔 GAVE IN ]         |
+-------------------------------------------------------+
```

---

#### 3.6 Settings & Habit Configuration Screen

```
+-------------------------------------------------------+
|  SETTINGS & HABIT MANAGEMENT                          |
+-------------------------------------------------------+
|  HABIT STATE                                          |
|  Current Habit: Doomscrolling                         |
|  Current Mode: Action Phase (Day 12)                  |
|                                                       |
|  [ 🔄 Re-enter Observation Phase ]                     |
|  (Log emerging triggers; retain history, tags, and    |
|   clean-time stats)                                   |
|  [ Revisit Guided Plan Setup ]                         |
|                                                       |
|  [ More settings  + ]  (collapsed by default)          |
|  When expanded: pause timer and overlay position;      |
|  observation and milestone targets; import/export;    |
|  custom context-tag management                        |
+-------------------------------------------------------+
|  ARCHIVED MASTERED HABITS                              |
|  [ Doomscrolling · View stats ]                         |
|  (Stats appear after selecting an archived habit)      |
+-------------------------------------------------------+
|  [ ⚠️ Reset Habit Data ]                              |
|  (Resets active logs; archived habits are retained)    |
+-------------------------------------------------------+
```

Re-entering Observation restarts only the observation-period clock. It does not delete Observation or Action logs, clear custom tags, or change the clean-time timestamp.

Export uses Android's system document picker to save a versioned JSON backup containing the current habit configuration, all trigger logs, and context tags/usage counts. Restore first validates a selected ExHabere backup, then asks for confirmation because it replaces the current habit, all logs, and tags. The replacement is transactional, so an invalid or failed restore leaves current data unchanged. The JSON file contains sensitive personal data; the UI advises choosing a secure location.

---

#### 3.7 Mastery Target Celebration Screen (66-Day Default)

The example below uses the default 66-day target. The target is configurable; reaching it is an app milestone, not a claim that a habit has become automatic.

```
+-------------------------------------------------------+
|  🎉 HABIT MASTERY ACHIEVED!                           |
+-------------------------------------------------------+
|                                                       |
|                   66 DAYS CLEAN                       |
|                [ 🏆 MASTERY TARGET REACHED ]           |
|                                                       |
|  "You built a new response through practice. This is  |
|   a meaningful milestone, not a requirement for        |
|   perfection."                                         |
|                                                       |
+-------------------------------------------------------+
|  MASTERY STATS:                                       |
|  • Total Urges Resisted: 42                           |
|  • Top Relief Method: Box Breathing (65% of 20)       |
|  • Alternative Plan Selected: 4 pauses                 |
|  (Alternative-action stat is a selection counter.)     |
+-------------------------------------------------------+
|  WHAT WOULD YOU LIKE TO DO NEXT?                      |
|                                                       |
|  [ 🔒 Archive Mastered & Start New Habit ]           |
|  [ 📈 Maintain Active Mastery Tracker ]               |
+-------------------------------------------------------+
```

The available emergency history supports total passed outcomes, the most-used currently configured relief method, and a counter for how often the saved replacement plan was selected. Choosing to archive saves the full mastered habit locally, then starts the Welcome flow at Step 2 so the user can name the next habit. Settings lists archived habit names; show mastery statistics only after a user selects an archived habit.

---

## Welcome and Onboarding Design Specification

### 1. Overview & Objective
The first-run **Welcome & Onboarding Flow** introduces ExHabere's observation-first approach, one-habit focus, and local-only data handling before the user names a habit and begins logging.

---

### 2. Visual Design System & Palette
The welcome screen strictly adheres to ExHabere's low-dopamine, distraction-free design system:
* **Background:** Deep Slate (`#0F172A`)
* **Card Containers / Surface Elements:** Dark slate (`#1E293B`), with muted steel blue (`#334155`) for borders and secondary accents
* **Primary Interactive Actions:** Soft Mint Green (`#34D399`)
* **Typography Hierarchy:**
  * Headlines: Primary White (`#F8FAFC`), Bold, Sans-Serif
  * Body Copy: Muted Gray (`#CBD5E1`), Regular
  * Captions & Badges: Steel Muted (`#94A3B8`) / Mint Accent (`#34D399`)

---

### 3. Screen Structure (Step 1 of 3: Philosophy & Privacy)

#### A. Header Navigation
* **Step Indicator:** Centered text reading `"STEP 1 OF 3"`, styled in `#64748B` with a 1px letter-spacing.

#### B. Central Geometric Graphic (App Icon Motif)
* **Container:** The app's rounded-square launcher icon on deep slate (`#0F172A`); there is no separate circular container.
* **Geometric Elements:**
  * **Habit Loop:** A closed, muted steel-blue (`#334155`) circular vector path representing the automated neural loop.
  * **Breakthrough Arrow:** A soft mint-green (`#34D399`) arc breaking out upward, symbolizing intentional interruption and choosing a new response.

#### C. Core Messaging & Value Proposition
* **Headline:** `"Rewire your neural loops"`
* **Sub-headline / Body:**
  > `"Breaking a habit isn't about raw willpower—it's about understanding your automatic cues."`

#### D. The 3-Phase Framework Card
A structured container outlining the user's journey to set calm expectations and eliminate performance anxiety:
1. **Observe:** Log urges without judgment for 7 days by default; the target is configurable.
2. **Plan:** Set explicit If-Then replacement friction.
3. **Rewire:** Track urges and practice progress toward a configurable mastery target (66 days by default).

#### E. Privacy & Local-Only Assurance Badge
* **Pill Container:** `#0F172A` background with a `#334155` border.
* **Text:** `"🔒 100% Local & Private • On-Device"` in `#34D399`.

#### F. Primary Action CTA Button
* **Button Style:** Full-width pill button filled with Soft Mint Green (`#34D399`).
* **Label:** `"Get Started"` in deep slate (`#0F172A`), font-weight 700.

---

### 4. Jetpack Compose Implementation Notes
* **Container:** Built using a scrollable `Column` wrapped inside a `Surface` with `MaterialTheme.colorScheme.background`.
* **Animations:** Smooth cross-fade transitions between onboarding steps (Step 1 $\rightarrow$ Step 2: Habit Naming $\rightarrow$ Step 3: Observation Intro) using Jetpack Compose `AnimatedContent`.
* **State Management:** Stored locally via `DataStore` to ensure the onboarding flow only triggers on first app installation.

---

## Guided Plan Setup Specification

### 1. Executive Summary & Purpose

Between **Phase 1 (Observation)** and **Phase 2 (Action)**, users must synthesize raw observational data into an evidence-based behavioral action plan before actively breaking their habit.

Currently, the app collects urges, tags, and times in Observation mode, and allows manual editing of a replacement plan and relief methods in Settings. The **Guided Plan Setup** bridges this gap: it analyzes observed trigger patterns (dominant emotions, locations, and peak hours) and guides the user through setting up:
1. An **Implementation Intention ("If-Then" routine)** tailored to their primary trigger cue.
2. An **Urge Relief Toolkit** of evidence-based grounding techniques for peak cravings.
3. A **Default Pause Lockout Duration** for emergency interventions.

---

### 2. Behavioral Psychology Foundations

1. **The Cue-Routine Swap (The Habit Loop):** Compulsive urges cannot simply be suppressed with willpower. The cue must be acknowledged and paired with a pre-decided alternative action satisfying the underlying emotional need.
2. **Implementation Intentions (Gollwitzer et al.):** Formatting plans as *"If [Cue / Trigger], then I will [Alternative Action]"* creates automatic mental associations that reduce cognitive load and decision fatigue during stress.
3. **Urge Surfing (Marlatt et al.):** Craving neurochemistry follows an acute wave peaking within 3–5 minutes. Having a pre-approved menu of somatic grounding techniques (parasympathetic nervous system activators) gives users practical tools to ride the wave out.

---

### 3. Navigation & Flow Architecture

```
┌─────────────────────────────────┐
│   Observation Dashboard (P1)   │
│   (Logged triggers & patterns)  │
└────────────────┬────────────────┘
                 │ "Review Patterns & Build Plan"
                 ▼
┌────────────────────────────────────────────────────────────────────────┐
│                      GUIDED PLAN SETUP SCREEN                          │
│                                                                        │
│  Section 1: Trigger Pattern Synthesis (Top cue, context, peak hour)    │
│  Section 2: "If-Then" Replacement Routine Builder                      │
│  Section 3: Somatic Urge Relief Toolkit Selection                      │
│  Section 4: Lockout Duration & Action Phase Commitment                 │
└────────────────┬───────────────────────────────────────────────────────┘
                 │ "Commit & Begin Action Phase"
                 ▼
┌─────────────────────────────────┐
│     Action Dashboard (P2)       │
│  (Clean counter begins; custom  │
│   If-Then plan & relief active) │
└─────────────────────────────────┘
```

- **Entry Point 1 (Primary):** From `ObservationScreen`, replacing the direct transition button with *"Review patterns & set up plan"*.
- **Entry Point 2 (Re-tuning):** From `SettingsScreen`, adding a button *"Revisit guided plan setup"* so users in Action phase can re-evaluate and refine their plan using their baseline data at any time.
- **Back Navigation:** Pressing Back on the Plan Setup screen cleanly returns to the previous screen without mutating the phase or resetting clean time.

---

### 4. UI Screen Wireframe & Specifications

```
+-------------------------------------------------------+
|  [<- Back]                           Plan Setup       |
+-------------------------------------------------------+
|  STEP 1: YOUR OBSERVED TRIGGERS                       |
|  +-------------------------------------------------+  |
|  | 🎯 Primary Cue Pattern                          |  |
|  | "Urges most often strike when you feel Bored   |  |
|  |  in Bed around 10:00 PM."                       |  |
|  |                                                 |  |
|  | • Most common feeling: Boredom (58%)            |  |
|  | • Most common context: Bed (42%)                |  |
|  | • Peak window: 10:00 PM – 11:00 PM              |  |
|  | • Total observations: 14 urges                  |  |
|  +-------------------------------------------------+  |
|                                                       |
|  STEP 2: "IF-THEN" REPLACEMENT ROUTINE                |
|  Pre-committing to an action reduces friction when a   |
|  craving strikes.                                     |
|                                                       |
|  [ When I feel Bored in Bed, I will...              ] |
|  [ Read 2 pages of a physical book                  ] |
|                                                       |
|  Quick starter ideas:                                 |
|  [ 📖 Read 2 pages ]     [ 💧 Drink water & stretch ] |
|  [ 🚶 Take 10 steps ]    [ ✍️ Journal 1 sentence ]    |
|                                                       |
|  STEP 3: GROUNDING RELIEF TOOLKIT                     |
|  Select at least one technique to ride out peak waves:|
|  [x] Box Breathing Exercise                           |
|      4s inhale, 4s hold, 4s exhale, 4s hold.          |
|  [x] Physiological Sigh                               |
|      Two quick inhales, long sighing exhale.          |
|  [ ] Cold Water Reset                                 |
|      Cool water splash to trigger dive reflex.        |
|  [x] 5-4-3-2-1 Sensory Grounding                      |
|      Engage all five physical senses sequentially.     |
|                                                       |
|  STEP 4: EMERGENCY LOCKOUT DURATION                   |
|  Timer window before making any impulsive choice:     |
|  ( ) 2 min       (*) 5 min (Default)       ( ) 10 min |
|                                                       |
|  +-------------------------------------------------+  |
|  |       [ COMMIT & BEGIN ACTION PHASE ]           |  |
|  +-------------------------------------------------+  |
+-------------------------------------------------------+
```

---

### 5. Component Functional Specifications

#### 5.1 Trigger Pattern Synthesis
- **Co-occurrence Analysis:** Determines the most frequent `(emotionalState, location)` combination among Observation logs.
- **Synthesized Headline:** Formatted as *"Urges most often strike when you feel [Emotion] in [Location] around [Peak Hour]."*
- **Sparse Data Graceful Fallback:** If `< 3` urges were recorded or observation time was minimal, displays a non-judgmental note:
  *"Few urges logged during observation. Standard recommendations are provided below, and you can customize your plan."*

#### 5.2 Implementation Intention Builder
- **Dynamic Template:** Auto-generates an initial prompt based on observed data:
  `When I feel [Dominant Emotion] in [Dominant Location], I will [Alternative Action]`
- **Starter Idea Chips:** Clicking a chip appends or sets standard friction-free replacement behaviors (e.g., *"Read 2 pages"*, *"Drink water and stretch"*, *"Take 10 mindful breaths"*, *"Write down what I'm feeling"*).
- **Custom Input:** Editable text area allowing up to 300 characters, validated to ensure it is not blank before commitment.

#### 5.3 Somatic Urge Relief Toolkit
- Multi-select list with clear mechanism explanations:
  1. **Box Breathing Exercise:** Activates parasympathetic recovery through equal-ratio breath control.
  2. **Physiological Sigh:** Rapid autonomic reset using double-inhalation and extended exhalation.
  3. **Cold Water Reset:** Triggers the mammalian dive reflex to quickly downregulate heart rate.
  4. **5-4-3-2-1 Sensory Grounding:** Disrupts obsessive rumination loops by refocusing cognitive load onto immediate sensory input.
- **Validation:** At least 1 technique must be selected.

#### 5.4 Emergency Pause Timer
- Radio/Chip selector for 2, 5, or 10 minutes (default: 5 minutes).
- Informs the user that tapping Emergency Support in Action phase will enforce this pause before any habit choice can be made.

#### 5.5 Commitment & Phase Transition
- Clicking **"Commit & Begin Action Phase"**:
  1. Saves the updated `replacementPlan` to Room.
  2. Saves the `approvedReliefTechniques` list to Room.
  3. Saves the `defaultTimerMinutes` to Room.
  4. Calls `habitRepository.transitionToPhase(HabitPhase.ACTION)`, establishing the clean-time anchor timestamp.
  5. Navigates directly to the Action Dashboard.

---

### 6. Technical Architecture & File Map

#### New & Modified Files

| File | Role | Changes |
| --- | --- | --- |
| `ui/ExHabereApp.kt` | Navigation | Add `Destination.PLAN_SETUP`; wire routing and BackHandler. |
| `ui/observation/ObservationScreen.kt` | UI | Change switch button to navigate to `Destination.PLAN_SETUP`. |
| `ui/setup/PlanSetupScreen.kt` | New UI Screen | Implements the guided review layout, cards, and commit button. |
| `viewmodel/PlanSetupViewModel.kt` | New ViewModel | Manages draft plan, relief techniques, timer, and commit action. |
| `viewmodel/TriggerLogInsights.kt` | Domain Logic | Add co-occurrence `primaryTriggerPattern()` calculation. |
| `ui/settings/SettingsScreen.kt` | UI | Add "Revisit Guided Plan Setup" button. |
| `app/src/test/java/org/exhabere/viewmodel/UiStateTest.kt` | Unit Tests | Add test coverage for trigger synthesis and plan setup state. |

---

### 7. Verification & Quality Assurance Checklist

- [ ] **Data Preservation:** Transitioning from Observation through Plan Setup into Action initializes clean time without dropping earlier Observation logs.
- [ ] **Validation Guardrails:** Commit button is disabled if the replacement plan is blank or if 0 relief techniques are selected.
- [ ] **Low-Dopamine Theme:** Consistent use of dark slate surface, soft mint accent, clear typography hierarchy, and non-judgmental copy.
- [ ] **Unit Tests:** Verify `primaryTriggerPattern` handles empty lists, single items, ties, and formatted peak hours.
- [ ] **Build & Lint Verification:** `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` passes with 0 errors.

---

## Technical Stack and Architecture Specification

### 1. Core Technology Stack

| Category | Technology | Rationale & Purpose |
| :--- | :--- | :--- |
| **Language** | **Kotlin** | Official modern Android language, offering safety, conciseness, and strong readability for open-source contributors. |
| **UI Framework** | **Jetpack Compose** | Declarative modern UI framework that reduces boilerplate, simplifies animations, and makes screen design intuitive. |
| **Local Database** | **Room DB (SQLite)** | Official Android ORM. 100% offline, local-only storage guaranteeing privacy with zero cloud or server dependencies. |
| **State & Reactivity** | **Kotlin Coroutines + Flow** | Modern, lightweight asynchronous streams and state management for reactive UI updates without third-party bloat. |
| **Dependency Injection** | **Manual DI (`AppContainer`)** | Pure Kotlin singleton container pattern. Avoids heavy annotation-processing frameworks (like Hilt or Dagger), ensuring the codebase is 100% transparent and easy to build for anyone cloning the repo. |
| **Build System** | **Gradle (Kotlin DSL)** | Standard, self-contained build configuration allowing immediate source compilation via `./gradlew assembleDebug`. |

---

### 2. Project Architecture & Directory Structure

The project follows a clean, modular **MVVM (Model-View-ViewModel)** architecture:

```
app/src/main/java/org/exhabere/
├── data/                  # Local storage and database layer
│   ├── db/                # Room entities, DAOs, and database definition
│   ├── preferences/       # Local first-run onboarding state
│   ├── backup/            # Versioned JSON backup model, codec, and validation
│   └── repository/        # Repositories managing local data queries and transactions
├── ui/                    # Jetpack Compose UI layer
│   ├── welcome/           # First-run welcome and habit setup flow
│   ├── observation/       # Observation phase dashboard & quick-log bottom sheet
│   ├── insights/          # Progressive-disclosure analytics and reflection view
│   ├── setup/             # Guided plan setup and trigger-pattern synthesis
│   ├── action/            # Clean tracker, milestones, and emergency decision view
│   ├── components/        # Shared quick-log, heatmap sheet, and heatmap grid
│   ├── overlay/           # Compose-backed views for the floating overlay service
│   ├── settings/          # Settings, phase-toggle, and custom tag management
│   └── theme/             # Low-dopamine color palette, typography, and styling
├── service/               # Background Android Foreground Service for the lockout timer
└── viewmodel/             # ViewModels and pure trigger/insight calculations
```

---

### 3. Advanced Features & System Integration

#### A. Floating Lockout Overlay Service
* **Implementation:** Built using an Android `Foreground Service` coupled with `WindowManager` (`TYPE_APPLICATION_OVERLAY`).
* **UI Rendering:** Embeds a Jetpack Compose `ComposeView` directly into the overlay service so the floating timer matches the exact visual theme of the main application.

#### B. Dynamic Tag Management
* Custom tags created via the quick-log "Other" option are saved directly into the Room database and instantly injected into the local tag list cache for subsequent log entries.

#### C. Trigger History, Phase Origin, and Outcomes
* `TriggerLogEntity` stores a `phaseOrigin` (`OBSERVATION` or `ACTION`) independently from the app's current phase, and an `outcomeType` (`URGE_ONLY`, `ENGAGED_IN_HABIT`, `EMERGENCY_PASSED`, or `EMERGENCY_GAVE_IN`). This preserves the original context and distinguishes a quick log from an emergency intervention.
* `TriggerLogDao` supports phase-origin-filtered flows, time-window queries, outcome counts, and all-log history queries for comparing Observation baselines with Action-phase trends.
* `TriggerLogRepository.recordLog` writes quick logs and custom-tag usage updates transactionally. It does not update the clean-time baseline. Only an emergency `EMERGENCY_GAVE_IN` outcome updates the clean-time timestamp.
* Room schema version 4 migrates existing v1–v3 data in place. Legacy `MASTERY` log origins map to `ACTION`; the v2→v3 migration adds the local mastered-habit archive table, and v3→v4 adds the configurable pause-overlay position (defaulting existing installs to `MIDDLE`). No destructive migration fallback is used.
* `HabitArchiveRepository` snapshots the completed config, all trigger logs, and context tags as validated versioned JSON inside Room, then resets the active habit in the same transaction. Settings loads an archived snapshot only after selection and derives its mastery statistics on-device.

#### D. Dedicated Insights View
* The Observation and Action dashboards expose navigation to a separate Compose Insights screen; charts and trigger breakdowns stay off the primary dashboard surface.
* `InsightsData.kt` calculates the weekly Action quick-log trend, paired phase heatmaps, peak-window summaries, and top preset/custom tag breakdowns from the existing log/config flows. It adds no Room tables, columns, or network access.
* Recovery frequency/intensity use Action quick logs only. Peak heatmaps include Action emergency outcomes to preserve existing event semantics; context breakdowns exclude emergency-only rows because those rows do not contain user-selected tags.
* Cards use `animateContentSize` and `AnimatedVisibility` for progressive disclosure. The two aligned weekly trend tracks have separate scales and chart-tap/week-chip selection; the timeline grows beyond the configured mastery target and scrolls for longer histories.

#### E. Habit Import and Export
* `HabitBackupRepository` captures a consistent snapshot of the single habit configuration, all trigger logs, and all context tags in a Room read transaction. Restore replaces those tables in one Room transaction.
* A versioned JSON codec validates the file format, enum values, entity IDs, field bounds, and duplicate tags before any database writes.
* Compose launches Android's `CreateDocument` / `OpenDocument` contracts. Import requests `*/*` so users can browse all file types and document providers exposed by Android; the app then validates the selected content as an ExHabere JSON backup before any write. Export and import remain user-directed through the system picker; no broad storage permission or network service is added. Restore requires explicit confirmation because it replaces current data.

#### F. Compose UI Tests
* Instrumented screen tests use `androidx.compose.ui:ui-test-junit4` and the debug `ui-test-manifest` dependency; run them with `./gradlew :app:connectedDebugAndroidTest` on an emulator or device.
* `app/src/androidTest/java/org/exhabere/ui/ComposeScreenUiTest.kt` covers Welcome, both dashboard quick-log entry points, custom quick-log tags/cancel, guided-plan validation, Insights progressive disclosure, emergency decision controls, the mastery celebration, the new-habit step-2 entry, and on-demand archive stats. App-level picker automation and broader system/upgrade checks remain; TalkBack is a nice-to-have. See the [Testing Plan](#testing-plan).

---

### 4. F-Droid & Google Play Store Compliance

To ensure seamless distribution across both open-source repositories and commercial storefronts:

1. **Zero Proprietary SDKs:** The app includes **no** third-party analytics (like Firebase), crash reporting SDKs, or ad networks. All data stays strictly on-device.
2. **Open-Source Dependencies:** Every library utilized in `build.gradle.kts` adheres strictly to open-source licenses (Apache 2.0 / MIT).
3. **System Permissions Management:**
    * Requires `SYSTEM_ALERT_WINDOW` ("Display over other apps") for the floating lockout widget.
    * Includes a clear, transparent in-app disclosure explaining why the overlay is required prior to requesting the permission, satisfying Google Play policy requirements.

---

### 5. Building and Testing

Build prerequisites, SDK setup, and build/test commands are maintained in the repository [README](README.md). Use the checked-in Gradle Wrapper; no machine-specific SDK path or local signing credential is required by the project configuration.

Instrumented tests require an Android emulator or connected device. See the [Testing Plan](#testing-plan) for the verification matrix and release checks.

---

## Testing Plan

**Status:** Automated unit, instrumented, and Compose UI coverage is in place. Backup/restore and onboarding persistence have been smoke-tested on Android. Broader system/upgrade validation and a full large-text layout pass remain; TalkBack review is optional.

The app has unit tests for UI-state/insight calculations and backup validation, Room/repository instrumentation tests for transactions, migration, reset, guided-plan commits, clean-time behavior, and backup replacement, and screen-level Compose tests for onboarding, Quick Log, plan validation, Insights disclosure, and emergency decisions. Remaining checks focus on broader rendered UI behavior, optional app-level document-picker automation, and system/upgrade integration.

### 1. Compose UI tests

Use Compose test rules with deterministic screen state and callback fakes for isolated screens. Add a smaller number of app-flow tests with an in-memory Room database where phase and persistence behavior must be verified. Tests should assert user-visible semantics and callbacks/state changes rather than implementation details or exact animation timing.

#### Welcome/onboarding

- **Implemented:** Verify the introduction, habit-name, and Observation-intro steps appear in order; Back returns to the previous step.
- **Implemented/confirmed:** The screen test verifies blank-name validation and the completion callback. On-device, onboarding completion persisted across a normal app relaunch.
- **Confirmed on device:** Clearing the app cache opens Welcome; after completing onboarding, a normal relaunch keeps Welcome dismissed.

#### Observation and shared Quick Log

- Open the log sheet; verify emotion, context, intensity, and outcome controls are available and labeled.
- Choose a preset feeling/context and intensity, save, and verify the callback receives those values with the selected outcome.
- Enter a custom feeling or context and save; verify the custom value and custom flag reach the callback. In a repository-backed test, verify it is available as a quick-select tag on the next log.
- Dismiss/cancel the sheet and verify no save callback occurs.
- Verify the dashboard’s Insights and plan-setup actions navigate to their destinations without changing phase.

#### Guided Plan Setup

- Verify the commit action is disabled for a blank plan or no selected relief technique, and enabled for a valid plan/toolkit/timer.
- Verify starter ideas update the editable plan and the character limit is enforced.
- Verify Back does not commit or change phase/clean-time state.
- **Implemented:** The screen test verifies required fields gate commit and a valid draft invokes it. The repository instrumentation test verifies settings, Action transition, and retained Observation logs.

#### Action and emergency flow

- Verify the quick-log sheet uses the same controls as Observation and saves an Action-origin event.
- Verify a quick log does not change the clean-time anchor.
- Verify Emergency Support opens the decision flow; selecting relief/alternative and 2, 5, or 10 minutes produces the expected launch request.
- Verify backing out of the decision flow launches no timer and writes no emergency outcome.
- For the post-timer outcome UI, verify each choice submits the correct outcome and that give-in resets clean time while passed does not. The service/persistence behavior should remain covered by instrumentation tests as well.

#### Insights, Settings, and Mastery

- Insights: expand each summary card, verify only one is expanded at a time, and verify selecting a week updates the displayed trend values. Include empty/sparse-data states.
- Settings: verify reset and restore confirmations can be cancelled without invoking destructive callbacks; verify confirmed actions invoke the expected callback.
- Mastery: verify the milestone, resisted-urge count, relief-method percentage, and alternative-plan selection counter. The counter is the accepted measure; completed activity quantities are not required. Verify both choices: archiving preserves the habit and starts the new-habit flow, while maintaining tracking returns to the Action tracker.
- Archive flow: verify archiving snapshots config/logs/tags and resets active data atomically; after archiving, Welcome starts at Step 2. In Settings, archive rows initially show no statistics; selecting a row reveals its stored mastery stats. Verify archive failure rolls back and leaves active data unchanged.

### 2. SAF backup/restore device smoke test

Run on an Android device using the system document picker. Record Android version, file provider/location, and result for each case.

1. Export a backup and restore a valid file.
2. Cancel the picker and verify the app and data remain unchanged.
3. Import a valid backup from a location other than Downloads. The picker requests `*/*` to avoid MIME filtering.
4. Try malformed JSON, an unsupported format version, an invalid phase, a blank habit name, and a missing required field; verify each is rejected without changing current data.
5. Select a valid backup, cancel the replacement confirmation, and verify current data remains unchanged.
6. Exercise read/write failure handling for an unavailable or unreadable provider without corrupting existing data.

These manual checks complement codec/validator and repository transaction tests, which do not exercise Android's picker, provider URIs, or file I/O on-device.

### 3. System and upgrade validation

The database migration and service have automated coverage and some device smoke testing, but a repeatable system/version and in-place upgrade pass remains. Use a real device or emulator and record the Android/API version, app build/signing variant, permission choices, and result.

#### In-place app/database upgrade

1. Install a supported older app build with the same application ID and signing key as the build under test. To verify the latest migration, use a schema-v3 build and realistic active-habit data (phase/configuration, quick logs with tags, and clean-time anchor), then confirm v3→v4 adds the pause-overlay position without loss. Also test an older schema-v2 build through v2→v3, confirming the archive table initializes and archived habits work, followed by v3→v4 if a supported build is available.
2. Do **not** clear app data or uninstall. Install the new APK over the old one (for a local debug build, `adb install -r app/build/outputs/apk/debug/app-debug.apk`; the signing key must match the installed build).
3. Launch the upgraded app. Confirm migration completes without a crash or data-loss warning, the correct phase/dashboard opens, and the previous habit name, plan/settings, timestamps, logs, tags, and archive data remain usable. Open Insights and save a new quick log; verify the old history remains and the new row is recorded with the correct phase origin. Check `adb logcat` for Room migration/database errors.
4. Repeat using the oldest supported upgrade path that has a real prior app/database artifact. Existing migration tests cover schema transformations, but this smoke test validates the installed-app upgrade and post-migration UI together. If no signed older APK is available, a prior-schema database fixture can test migration-to-UI, but does not substitute for verifying APK installation over an existing install.

#### Android permission/service matrix

Run at least the following representative cases; include the minimum supported API (23) for basic launch, persistence, and logging when practical, Android 13/API 33+ for notification permission, and a current Android version. A device need not cover every API if a matching emulator is used.

- **Overlay permission:** With “Display over other apps” denied, start Emergency Support and verify the app explains the permission and does not start a lockout timer. Grant it through the system settings handoff, return, and verify the timer starts. Revoke permission and repeat.
- **Notification permission (Android 13+):** Test both grant and deny. Confirm the service/timer behavior is understandable in either case, and that the notification/its early-end action appears when permission is granted. Verify denial does not crash or silently record an outcome.
- **Timer lifecycle:** Start a timer, move to the launcher and a regular app, then check countdown, early-end, completion, and both post-timer outcomes. No outcome should be written before the user chooses. Confirm “passed” preserves clean time and “gave in” resets it, and that each choice is recorded only once.
- **Service recovery:** While a timer is active, kill only the app process (do not Force stop); verify Android restarts the service with the original end time and no duplicate outcome. Note that protected system screens such as Android Settings may suppress overlays by design.
- **Upgrade while idle:** Also install a newer APK over an existing app without clearing data and verify onboarding remains dismissed and current habit data remains available. If an upgrade during an active timer is in scope, test it separately and document the expected behavior before treating it as supported.

These checks focus on version-specific permission/service behavior and the full migration-to-UI path.

### 4. Layout and optional accessibility pass

- Recheck all screens at larger system font/display sizes and a narrow device width. Confirm text does not clip, buttons remain reachable, sheets/screens scroll, and overlay controls fit.
- Check text and control contrast against the slate/mint theme, visible focus, and that color is not the only way to understand chart series, selected options, or milestone completion.

TalkBack is a **nice-to-have**, not a current release blocker. If completed, audit focus order, labels, selected/expanded states, and reachability of actions across logging, emergency support, celebration/archive, dialogs, and the overlay.

### 5. Release verification

- Configure a release signing process without committing private signing credentials; build and install the release variant.
- Verify upgrade/install behavior, manifest permissions and disclosures, backup exclusion behavior, and that no network/tracking SDK has been introduced.
- Archive the exact tested artifact and document reproducible build/signing instructions before distribution.

---
