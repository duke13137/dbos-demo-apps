## Type Checking Quality Gate

Before completing any task that creates or modifies Python files, you MUST ensure the codebase passes static analysis:

1. Run `uvx pyrefly check --baseline=.codex/pyrefly-baseline.json --update-baseline` at the project root.
2. If Pyrefly returns any type errors, read the diagnostics, fix the source code, and run `pyrefly check` again.
3. Do not declare a task complete until Pyrefly reports 0 errors.
