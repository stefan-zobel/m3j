@echo off
rem Copyright (c) 2026 the m3j authors.
rem SPDX-License-Identifier: MIT
rem
rem The compiler of m3j, in bin of a release: m3j [OPTION]... FILE...
rem (docs\USING.md). Runs java of JAVA_HOME if it is set, else java from
rem the PATH (JDK 25 or later).

setlocal
set "dir=%~dp0.."
set "java=java"
if defined JAVA_HOME set "java=%JAVA_HOME%\bin\java"
"%java%" -cp "%dir%\m3j.jar" m3j.jvm.Compile %*
endlocal & exit /b %ERRORLEVEL%
