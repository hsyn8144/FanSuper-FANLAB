# Code on the Go – Chaquopy Python build fix

The project uses Chaquopy 15.0.1 with Python 3.11. Chaquopy requires a **host Python 3.11 interpreter** during the Android build; this is separate from the Python runtime bundled into the APK.

The Gradle script now resolves Code on the Go's embedded Termux/Python installation via `TERMUX_PREFIX` when available, then checks the known Code on the Go prefix and finally falls back to `python3.11`, `python3`, or `python` on PATH.

If the build still reports `Couldn't find Python`, install/enable Code on the Go's **Python/Flask** add-on (it provides Python on the IDE's terminal), restart Code on the Go, and run `:app:assembleDebug` again. No project source changes are required after that.

You can also explicitly set the executable with:

```text
-PchaquopyBuildPython=/path/to/python3.11
```

or the environment variable `CHAQUOPY_BUILD_PYTHON`.
