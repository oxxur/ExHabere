# Invalid ExHabere backup fixtures

These fixtures contain synthetic data only. In ExHabere, use **Settings → Restore from file** and select a fixture. Each should be rejected before any database write; check that the current habit and logs remain unchanged afterward.

- `malformed-json.json` — invalid JSON syntax.
- `unsupported-version.json` — valid backup shape with an unsupported format version.
- `invalid-phase.json` — valid JSON with an unknown phase enum.
- `blank-habit-name.json` — valid JSON with a value rejected by backup validation.
- `missing-trigger-logs.json` — valid JSON missing a required top-level property.
- Matching `.txt` copies are also provided as a fallback for file pickers that hide JSON files. The importer validates the file contents, not the extension.

The files can be copied to an attached Android device with:

```bash
adb push test-data/invalid-backups/. /sdcard/Download/ExHabere-invalid-backups/
```

In some Android file pickers, the **Downloads** shortcut may show only indexed downloads. To browse the copied folder, open the picker menu, choose device storage, then navigate through **Download → ExHabere-invalid-backups**. The matching `.txt` fixtures are also copied to the Downloads root as an easy-to-find fallback; the app validates their contents, not their extension.

To copy only the `.txt` fallback files to the Downloads root:

```bash
adb push test-data/invalid-backups/*.txt /sdcard/Download/
```
