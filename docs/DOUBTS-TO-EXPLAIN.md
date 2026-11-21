# Doubts to explain at the end of the project

Neelam's list of topics she did NOT fully understand yet.
At the END of the project, explain each one again from zero (very simple English,
picture first, small examples), together with the final study document.

## Helm (noted 2026-11-21)
Understood: why Helm, chart folder structure (Chart.yaml, values.yaml, templates, values files).
NOT understood yet — everything after the folder structure:
- [ ] `helm lint` — what it checks and how to read its output
- [ ] `helm template` — how it shows the final YAML, how to read/check it
- [ ] Template syntax: `{{ }}`, `.Values` / `.Release` / `.Chart`, `include`, `define`, `required`, `default`, `quote`, `toYaml`, `nindent`, `range`
- [ ] The checksum/config annotation (why pods restart when config changes)
- [ ] Adopting existing kubectl objects into Helm (label + 2 annotations)
- [ ] `helm upgrade --install` vs `helm upgrade`, release, revision
- [ ] Server-side apply, field managers, "conflict with kubectl-client-side-apply", `--force-conflicts`, managedFields reset
- [ ] Why the new pods restarted at ~2 minutes (startup probe limit, many Java apps starting together)
- [ ] Practise a real Helm upgrade (change a value → helm upgrade → watch only that service restart) — skipped on 2026-11-21
- [ ] `helm history` and `helm rollback` (go back to the last good version) — practise it
