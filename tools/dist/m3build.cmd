@echo off
rem Copyright (c) 2026 the m3j authors.
rem SPDX-License-Identifier: MIT
rem
rem m3build, the build system of m3j, in bin of a release: m3build
rem [OPTION]... in the directory of a package (docs\USING.md). Runs java of
rem JAVA_HOME if it is set, else java from the PATH (JDK 25 or later).

setlocal
set "dir=%~dp0.."
set "java=java"
if defined JAVA_HOME set "java=%JAVA_HOME%\bin\java"
"%java%" -jar "%dir%\m3build.jar" %*
endlocal & exit /b %ERRORLEVEL%
