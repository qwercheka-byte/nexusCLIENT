@echo off
rem NEXUS Launcher (portable). Uses the bundled Java runtime from the "runtime" folder.
start "" "%~dp0runtime\bin\javaw.exe" -jar "%~dp0nexus-launcher.jar" %*
