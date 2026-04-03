# ToDoWallApp — Feature Map

> **Functional reference** of every implemented user-facing capability.
> Organized by interaction domain, not by file or class.
> Collapsible sections use `<details>` — click to expand in any Markdown viewer.

---

<details open>
<summary><h2>Task Management</h2></summary>

<details>
<summary><h3>Viewing Tasks</h3></summary>

| Feature | Trigger | Details |
|---------|---------|---------|
| Accordion folder view | Automatic | All task lists shown as collapsible sections in a single scrollable view |
| Folder expand/collapse | Encoder focus lands on header | Blooms open (~300ms ease-in-out); tasks fade in. Collapses when focus leaves |
| Folder sort order | Automatic | Lists sorted by nearest child due date; fallback to most-recently-updated |
| Collapsed peek | Automatic | Collapsed folder shows first task as faded, truncated text |
| Subtask hierarchy | Always visible | Children indented with vertical connecting line on left — never collapsed |
| Subtask progress badge | Automatic | Shows "X/Y" (completed/total) on parent tasks |
| Task title | Displayed | Up to 2 lines, ellipsis overflow |
| Task notes | Displayed | Single line below title, secondary color, hidden when completed |
| Due date badge | Displayed | Relative labels ("Today", "Tomorrow", "Mon 3/15"), color-coded by urgency |
| Urgency color bar | Left edge (8dp) | Gradient bar changes color: normal / due-soon (yellow) / due-today (orange) / overdue (red) |
| Completed tasks section | Automatic | Completed tasks drift to bottom of list into a "Completed" folder |
| Completed visual state | Automatic | Strikethrough text, 35% opacity fade |

</details>

<details>
<summary><h3>Completing Tasks</h3></summary>

| Feature | Trigger | Details |
|---------|---------|---------|
| Toggle completion | Encoder click (on focused task) | Marks task done or undone |
| Ripple animation | On completion | Visual ripple emanates from checkbox |
| Haptic feedback | On completion | Short crisp pulse via `VibrationEffect` |
| Drift-to-bottom | On completion | Task animates downward into Completed section (~300ms ease-in-out) |
| Undo toast | After completion | 5-second window to restore; auto-dismisses |
| Optimistic update | Immediate | UI updates instantly; reverts on API failure |

</details>

<details>
<summary><h3>Context Menu (Task Actions)</h3></summary>

| Feature | Trigger | Details |
|---------|---------|---------|
| Open context menu | Double-click on focused task | Shows action options (configurable via `DOUBLE_CLICK_WINDOW_MS`) |
| Schedule task | Context menu option | Opens promotion draft (see Calendar > Task Promotion) |
| Delete task | Context menu option | Permanently removes task from Google Tasks |
| Restore task | Context menu on completed task | Marks task incomplete again |
| Navigate options | Encoder rotate (while menu open) | Cycles through available actions |
| Confirm action | Encoder click (while menu open) | Executes selected action |
| Dismiss menu | Any unrelated key / Escape | Closes menu without action |

</details>

<details>
<summary><h3>Task Selection & Focus</h3></summary>

| Feature | Trigger | Details |
|---------|---------|---------|
| Focus navigation | Encoder rotate CW/CCW | Moves highlight down/up through tasks and folder headers |
| Focus glow | Automatic on selected item | Soft warm edge halo (outer shadow in accent color, visible from 6+ feet) |
| Elevation shift | Automatic on selected item | 24dp shadow, filled background (`surfaceCard`) |
| Auto-scroll | On focus change | Keeps focused task visible in viewport |
| Urgency-tinted glow | Automatic | Focus glow color shifts to match task urgency level |

</details>

</details>

---

<details open>
<summary><h2>Voice Assistant (Gemini AI)</h2></summary>

<details>
<summary><h3>Activating Voice Input</h3></summary>

| Feature | Trigger | Details |
|---------|---------|---------|
| Start voice input | Click header voice button | Screen dims, waveform visualizer appears, mic activates |
| Waveform visualizer | While listening | Thin-line audio waveform in accent color, responds to mic amplitude |
| Full-screen dim | While listening | Entire task list dims (like ambient mode) |
| Listening timeout | 30 seconds | Auto-stops if no speech or continuous silence |
| Silence detection | ~2 seconds of silence | Ends listening and begins processing |
| Stop listening | Click encoder during listening | Stops listening and processes captured speech |

</details>

<details>
<summary><h3>Voice Intents (What You Can Say)</h3></summary>

| Intent | Example Utterance | Behavior |
|--------|-------------------|----------|
| **Add task** | "Buy groceries tomorrow" | Creates new task with parsed title and due date |
| **Add with subtasks** | "Plan birthday party — book venue, send invites, order cake" | Creates parent task with child tasks |
| **Add to specific list** | "Add drill to the hardware list" | Routes task to named list |
| **Create new list** | "Create a shopping list and add milk" | Creates list then adds task to it |
| **Multi-task utterance** | "Buy groceries tomorrow and call the dentist by Friday" | Extracts multiple tasks from one sentence |
| **Complete task** | "Mark buy groceries as done" | Matches spoken text to existing task, completes it |
| **Delete task** | "Delete the dentist appointment" | Matches and removes existing task |
| **Reschedule task** | "Move groceries to Friday" | **NOT YET IMPLEMENTED** — intercepted with error message |
| **Query tasks** | "What's on my list?" | **NOT YET IMPLEMENTED** — recognized but no response UI |
| **Amend last input** | "Actually, change that to Thursday" | Modifies most recent voice draft before confirmation |

</details>

<details>
<summary><h3>AI Processing Features</h3></summary>

| Feature | Automatic | Details |
|---------|-----------|---------|
| Conversational noise stripping | Yes | Removes fillers ("um", "uh", "like"), hedging, self-corrections |
| Title shortening | Yes | Verbose speech → concise action phrase (e.g., "figure out the water heater" → "Check water heater") |
| Temporal reasoning | Yes | Parses "tomorrow", "Friday", "end of week", "in 3 days" into dates |
| Time-of-day preference | Yes | Extracts "morning", "afternoon", "evening" hints |
| List inference | Yes | Routes task to most appropriate list based on content |
| Duplicate detection | Yes | Warns if spoken task matches an existing task title |
| Confidence scoring | Yes | Shows clarification prompt if AI confidence < 50% |

</details>

<details>
<summary><h3>Voice Preview & Confirmation</h3></summary>

| Feature | Trigger | Details |
|---------|---------|---------|
| Draft card | After processing | Appears at top of current list, visually distinct from real tasks |
| Parsed task display | Automatic | Shows extracted title, due date, target list |
| Confirm | Encoder click | Commits task to Google Tasks, card settles into list |
| Cancel | Encoder navigate to cancel option + click | Discards draft, returns to normal view |
| No auto-commit | By design | Draft waits indefinitely for explicit user action |
| Multi-task preview | When multiple tasks parsed | Shows all extracted tasks in preview |

</details>

<details>
<summary><h3>Voice Error States</h3></summary>

| Error | Cause | User Sees |
|-------|-------|-----------|
| No speech detected | Silence for ~2 seconds | "No speech detected" message |
| Network error | No internet / API timeout | "Network error — check connection" |
| Recognition unavailable | Device doesn't support speech | "Speech recognition not available" |
| Gemini parse failure | API error or unparseable input | Falls back to raw transcription as task title |
| Listening timeout | 30 seconds elapsed | Auto-stops, processes whatever was captured |

</details>

</details>

---

<details open>
<summary><h2>Calendar</h2></summary>

<details>
<summary><h3>View Modes</h3></summary>

| Mode | Layout | Details |
|------|--------|---------|
| **Month** (default) | 7-column grid, up to 6 rows | Sun–Sat headers, square cells (aspectRatio 1:1), up to 3 event titles per day, "+N more" overflow |
| **Week** | 7-day vertical list | Today gets 1.4x height weight, tomorrow 1.2x, near-future 1.1x; up to 4 events for near days, 3 for distant |
| **Day** | Single-day detail | Expanded event list with full details, weather row, time slots |
| View switching | Sub-mode pill (Month \| Week \| Day) | AnimatedContent fade transition between modes |
| Drill-down | Tap a day in Month view | Month → Week → Day progressive drill-down |

</details>

<details>
<summary><h3>Event Display</h3></summary>

| Feature | Details |
|---------|---------|
| Event chips | Time + title inline (compact) or time above title (full) depending on day proximity |
| Color bar | Left-side indicator: warm accent for promoted tasks, standard for regular events |
| All-day events | Labeled "All day" instead of time |
| Promoted task indicator | Warm orange accent distinguishes tasks promoted from the task wall |
| Overflow counter | "+2 more" when more events than display space allows |
| Time format | Lowercase "h:mm a" (e.g., "2:30 pm") |

</details>

<details>
<summary><h3>Task-to-Calendar Promotion</h3></summary>

| Feature | Trigger | Details |
|---------|---------|---------|
| Initiate promotion | Context menu → "Schedule Task" on any pending task | Opens promotion draft UI |
| Duration cycling | Encoder rotate in promotion draft | Cycles through 15min, 30min, 1hr, 2hr options |
| Time adjustment | Encoder rotate on time field | Shifts start time forward/backward |
| Calendar selection | Encoder navigate to calendar picker | Choose target Google Calendar |
| Confirm promotion | Encoder click on confirm | Creates Google Calendar event, shows success toast |
| Cancel promotion | Navigate to cancel + click | Returns to normal view without creating event |
| Scheduled badge | After promotion | Task shows "Today 2:30 pm" or "Tomorrow 9:00 am" badge |

</details>

<details>
<summary><h3>Calendar Navigation</h3></summary>

| Feature | Trigger | Details |
|---------|---------|---------|
| Select day | Click/focus on day cell | Highlights day with primary accent border |
| Today highlight | Automatic | Today always has accent border + subtle background tint |
| Navigate by month | Arrow keys in Month mode | Shifts calendar forward/backward one month |
| Navigate by day | Arrow keys in Week/Day mode | Moves selection forward/backward one day |
| Month/year label | Displayed in Month mode header | "March 2026" format |
| Weather icons | On calendar day cells | Shows forecast condition icon per day |
| Weather tints | Automatic | Background color tint on cells based on weather condition |

</details>

<details>
<summary><h3>Calendar Sync</h3></summary>

| Feature | Details |
|---------|---------|
| Google Calendar read/write | Full two-way sync via Google Calendar API |
| Multiple calendar support | User can select which calendar for promotion |
| Event deletion | Can remove events from Google Calendar |
| Separate permission scope | Calendar access requested independently from Tasks; 403 handling with re-consent prompt |
| Event creation | Creates events with title, start/end time, calendar ID |

</details>

</details>

---

<details open>
<summary><h2>Display Modes</h2></summary>

<details>
<summary><h3>Active Mode (Default)</h3></summary>

| Feature | Details |
|---------|---------|
| Full brightness | All elements at 100% opacity |
| Full interactivity | Encoder navigation, voice input, context menus all active |
| Real-time clock | Updates every second |
| Sync indicators | Green/red dot, relative time label, offline badge all visible |
| Settings button | Gear icon visible in header |

</details>

<details>
<summary><h3>Quiet Mode (Tier 1 Ambient)</h3></summary>

| Feature | Trigger | Details |
|---------|---------|---------|
| Activation | ~30 seconds of no interaction | Automatic transition |
| Simplified display | Automatic | Only next 2–3 urgent tasks shown as faint text |
| Reduced opacity | Unselected items at 42%, selected at 90% | Low visual noise |
| Transparent cards | Automatic | Card backgrounds go transparent, no shadows |
| Hidden UI chrome | Automatic | Sync indicators, settings button, offline badge hidden |
| Spotlight task | Automatic | One highlighted "task of the moment" with gentle animation |
| Ambient prompt | Floating snippet | Task snippet with subtle float animation |
| Wake | Any encoder interaction | Instant return to Active mode |

</details>

<details>
<summary><h3>Sleep Mode (Tier 2 Ambient)</h3></summary>

| Feature | Trigger | Details |
|---------|---------|---------|
| True black screen | Schedule or light sensor | OLED-friendly, zero light emission |
| Sleep schedule | Configurable in Settings | e.g., 11pm–7am automatic sleep |
| Light sensor trigger | Camera-based ambient light check | Enters sleep when room is dark |
| Wake | Any encoder interaction | Instant return to Active mode |
| Bedroom-safe | By design | Eliminates light pollution for nightstand/bedroom installations |

</details>

<details>
<summary><h3>Theme System</h3></summary>

| Feature | Trigger | Details |
|---------|---------|---------|
| Dark theme | Default | Scandinavian muted dark palette |
| Light theme | Settings toggle | Full light palette variant |
| Auto theme | Settings → Auto mode | Switches based on configurable light hours (e.g., 8am–6pm) |
| Light hours config | Settings panel | Set start/end hour for daily light theme window |

</details>

</details>

---

<details open>
<summary><h2>Clock & Status Bar</h2></summary>

<details>
<summary><h3>Time Display</h3></summary>

| Feature | Details |
|---------|---------|
| Real-time clock | HH:mm format, updates every second |
| Date display | "Day, Month #" format |
| Corner placement | Small footprint (~48dp height), tucked into corner — tasks are the star |

</details>

<details>
<summary><h3>Sync Status</h3></summary>

| Feature | Trigger | Details |
|---------|---------|---------|
| Sync indicator dot | Automatic | Green = online, Red = offline |
| Relative sync time | Automatic | "Synced just now" / "5m ago" / "Stale: 45m ago" / "Stale: 2h ago" |
| Staleness color ramp | Time-based | Muted → yellow (10m+) → orange (30m+) → red (60m+) |
| Tap-to-sync | Click sync status pill | Triggers immediate manual refresh |
| Sync spinner | During sync | Pulsing animation while syncing |
| Sync confirmation | After sync | "Synced" message for 2 seconds |
| Offline badge | No network | Red "OFFLINE" indicator |
| Auto-sync | Background | Every N minutes (configurable, default 5 minutes) |

</details>

</details>

---

<details open>
<summary><h2>Settings</h2></summary>

<details>
<summary><h3>Accessing Settings</h3></summary>

| Feature | Trigger | Details |
|---------|---------|---------|
| Open panel | Gear icon (top-right) | Overlay panel appears |
| Close panel | Click background / Escape / Back | Dismisses overlay |
| Navigate options | Encoder rotate or Tab | Cycles through settings rows |
| Change value | Encoder click on focused setting | Cycles through options or opens input |

</details>

<details>
<summary><h3>Available Settings</h3></summary>

| Setting | Options | Persisted |
|---------|---------|-----------|
| Theme mode | Light / Dark / Auto | Yes (DataStore) |
| Light hours start | 0–23 (hour picker) | Yes |
| Light hours end | 0–23 (hour picker) | Yes |
| Sleep schedule start | Hour picker | Yes |
| Sleep schedule end | Hour picker | Yes |
| Sync interval | 1–60 minutes | Yes |
| Gemini API key | Text input (save/clear) | Yes (secure) |
| Weather API key | Text input (save/clear) | Yes |
| Weather location | City name input | Yes |
| Display mode | Wall / Phone | Yes |
| Sign out | Button with confirmation | Clears local auth data |

</details>

</details>

---

<details open>
<summary><h2>Authentication</h2></summary>

| Feature | Trigger | Details |
|---------|---------|---------|
| Google Sign-In | App launch / sign-in button | OAuth2 flow with Tasks API scope |
| Silent sign-in | App start | Attempts auto-sign-in with 3-second timeout |
| Calendar consent | First calendar access | Separate permission prompt (not bundled with Tasks) |
| Sign out | Settings → Sign Out | Clears credentials, returns to sign-in screen |
| Auth error display | On failure | Specific messages: network error, cancelled, scope denied |
| 403 re-consent | Calendar scope revoked | Prompts user to re-authorize calendar access |

</details>

---

<details open>
<summary><h2>Kiosk & Hardware</h2></summary>

<details>
<summary><h3>Immersive Display</h3></summary>

| Feature | Details |
|---------|---------|
| Full-screen mode | Hides status bar, navigation bar, system UI |
| Screen always on | `FLAG_KEEP_SCREEN_ON` prevents display timeout |
| Immersive sticky | System UI re-hidden on resume and window focus change |
| HOME category | Manifest configured to act as launcher replacement |

</details>

<details>
<summary><h3>Rotary Encoder (ESP32 BLE HID)</h3></summary>

| Input | Keycode Sent | App Action |
|-------|-------------|------------|
| Rotate CW | RIGHT_ARROW | Navigate down (next task/folder) |
| Rotate CCW | LEFT_ARROW | Navigate up (previous task/folder) |
| Quick click (0–350ms) | RETURN | Select / toggle completion |
| Medium hold (350–800ms) | — | Open context menu |
| Long hold (800ms+) | — | Activate voice input |

</details>

<details>
<summary><h3>Phone Mode</h3></summary>

| Feature | Details |
|---------|---------|
| Touch gestures | Tap, long-press, swipe all recognized |
| Touch-friendly spacing | Larger hit targets for finger input |
| Pager navigation | Swipe between Tasks and Calendar pages |
| One-tap voice | Single tap on mic area starts voice capture |

</details>

</details>

---

<details>
<summary><h2>Not Yet Implemented</h2></summary>

| Feature | Description | Blocker |
|---------|-------------|---------|
| **Voice: Reschedule** | "Move X to Friday" — change task due date via voice | Needs `updateTask()` in `GoogleTasksRepository` |
| **Voice: Query** | "What's on my list?" — spoken task queries | Intent recognized but no response UI |
| **Day Organizer** | Gemini-powered conversational day planning | Not started |
| **Weather Awareness** | Forecast tints on calendar, weather informs scheduling | Weather row exists in DAY mode but no real data source |
| **Community Events** | Local event discovery with preference learning | Not started |
| **Habit Tracking** | Gentle streak dots for recurring task patterns | Not started |
| **Energy-Aware Ordering** | Circadian task sorting based on user energy profile | Not started |

</details>
