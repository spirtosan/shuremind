# ShuRemind — SMOKE_TEST.md
_One on-device pass before v1 install. Paper checklist — check each box as you go._

## (a) Pending M4/M5 device items

- [ ] Restock notification action opens task detail with the restock dialog pre-opened
- [ ] Weekly-review notification tap routes to the review screen, not task detail
- [ ] First meter reading for a new meter name seeds the monthly "log your reading" prompt exactly once
- [ ] Language switch: English → Bulgarian → Russian all render correctly (incl. RU plurals), then back to English
- [ ] Auto-backup toggle: picking a folder **from the toggle** enables auto-backup
- [ ] Auto-backup folder row: picking a folder **from the folder row** does NOT change the toggle state
- [ ] "Back up now" writes a file into the chosen folder
- [ ] Export via the file picker (CREATE_DOCUMENT) produces a file
- [ ] Import that exported file: confirmation dialog shows plausible entity counts
- [ ] After import: data intact (tasks/tags/completions/meter readings), and no overdue-summary notification flood
- [ ] Retention prunes backup files to the newest 7 (create 8+ files, or temporarily lower the retention constant, to trigger pruning)
- [ ] Reboot the device: alarms still fire afterward

## (b) PROJECT.md acceptance cases (the 11 real-life cases)

- [ ] 1. Dentist (NAG, not_before, 24h interval): nags daily from the start date until marked done
- [ ] 2. Shower tray (SOMEDAY): appears in every weekly review, never alarms
- [ ] 3. Car oil (RECURRING/COMPLETION, 12mo + 10,000 km meter): due when either time or km threshold is crossed
- [ ] 4. Property tax (DEADLINE, P14D/P7D/P1D): reminders fire on schedule with escalation near due
- [ ] 5. Residence declaration (WINDOW → DEADLINE): monthly check reminders, converts cleanly once a date is entered
- [ ] 6. Secondary user's meds intake (RECURRING/CALENDAR daily 08:00 + 20:00): both daily alarms fire
- [ ] 7. Secondary user's meds stock (CONSUMABLE, stock 30 / dose 2 per day, lead 5 days): reminder lands ~10 days out
- [ ] 8. Flowers (RECURRING/COMPLETION every 3 days): next occurrence is 3 days after marking done, not a fixed grid
- [ ] 9. Birthday (ANNIVERSARY, P14D + P1D leads): both lead reminders fire yearly
- [ ] 10. Washing machine (quick timer +2h): fires 2 hours after creation
- [ ] 11. Eggs (#shop tag): visible when the #shop filter chip is tapped
