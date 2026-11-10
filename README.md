# Course Companion

Course Companion is a native Windows desktop planner for college assignments and study sessions.

## Install

Download `Course-Companion-Portable-0.2.0.zip` from the repository's **Releases** page, extract it, and open `Course Companion.exe`. The package includes Java, so students do not need to install Java separately.

This package is for Windows. The application runs as a desktop program and does not open a browser when it starts. Moodle and room-booking links open in the browser only when selected.

To build the Windows installer instead, install a JDK 21 or newer and WiX Toolset 3.14.1, make sure `jpackage`, `candle.exe`, and `light.exe` are available on `PATH`, then run:

```powershell
.\package-windows.ps1
```

The installer will be written to `release\Course-Companion-Setup-0.2.0.exe`.

## Features

- Track assignments, modules, deadlines, priorities, and brief files.
- Schedule study sessions and use a Pomodoro focus and break timer.
- Review weekly study time, calendar items, and room-booking links.
- Choose dark or light mode and export personal data from Settings.

## Build and run from source

To build the Java application yourself, install a JDK 17 or newer on Windows. In PowerShell, run these commands from the repository folder:

```powershell
New-Item -ItemType Directory -Force .\build\classes
javac -encoding UTF-8 -d .\build\classes .\src\AssignmentTracker.java
jar --create --file .\build\CourseCompanion.jar --main-class AssignmentTracker -C .\build\classes .
java -jar .\build\CourseCompanion.jar
```

Alternatively, run `build.ps1` to create the runnable JAR. This build path requires a JDK; the portable package does not.

## Your data

Assignments, sessions, study logs, and preferences are saved locally in the current Windows user's profile. They are not uploaded or shared with the project. Use **Settings → Export data** to make a backup or move your data to another computer.

## Contributing

You can open an issue or submit a pull request with a proposed change.

## License

No license is included yet. Add a license file before granting permission for others to reuse, modify, or redistribute this code.
