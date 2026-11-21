# Days to Hours Plugin

A minimal RuneLite external plugin scaffold based on RuneLite's official example plugin template.

## Requirements

- Java 11+ (`java -version`)
- Network access to RuneLite Maven repos on first build

## Local development

1. Build and test compile:
   ```bash
   ./gradlew test
   ```
2. Run RuneLite in developer mode with this plugin loaded:
   ```bash
   ./gradlew run
   ```

## Plugin behavior

- Adds a **Days** config value.
- On login, posts a chat message converting configured days to hours.

## Structure

- `runelite-plugin.properties`: plugin hub metadata.
- `src/main/java/com/daystohours`: plugin and config classes.
- `src/test/java/com/daystohours/DaysToHoursPluginTest.java`: local launch entrypoint.
