@echo off
rem ===================================================================
rem  ref-repro.cmd  --  loose-ref deletion isolation tester
rem
rem  Purpose: this machine has a user-mode "safe delete" component that
rem  sends newly created loose refs under .git\refs\... to the Recycle Bin
rem  (git reports success, then the ref is gone).  %TEMP% is exempt, all
rem  other paths are affected.  Use this script to find the culprit:
rem  run it once as a baseline, then exit one suspect app at a time and
rem  run it again.  When the F: count becomes 5/5, the last app you
rem  exited is the culprit.
rem
rem  Self-check: the %TEMP% column should always be 5/5.  If BOTH columns
rem  are 5/5 there is nothing intercepting right now.
rem ===================================================================
setlocal enabledelayedexpansion
set "GIT=C:\Program Files\Git\cmd\git.exe"
if not exist "%GIT%" set "GIT=git"

call :probe "%TEMP%\_refcheck_tmp" TMP
call :probe "F:\MCProject\_refcheck"   F_

echo.
echo ===================================================================
echo   %TEMP%      : %TMP% / 5   (control, expected 5)
echo   F:\MCProject: %F_% / 5   (tested path)
echo -------------------------------------------------------------------
if "%F_%"=="5" (
  echo   RESULT: no interceptor right now  ^<== fixed^!
) else (
  echo   RESULT: interceptor still active
)
echo ===================================================================
echo.
if "%~1"=="" pause
exit /b

:probe
set "R=%~1"
if exist "%R%" rmdir /s /q "%R%" >nul 2>&1
mkdir "%R%" >nul 2>&1
"%GIT%" -C "%R%" init -q                          >nul 2>&1
"%GIT%" -C "%R%" config user.email t@t            >nul 2>&1
"%GIT%" -C "%R%" config user.name t               >nul 2>&1
echo x > "%R%\f.txt"
"%GIT%" -C "%R%" add -A                           >nul 2>&1
"%GIT%" -C "%R%" commit -q -m i                   >nul 2>&1
set /a %~2=0
for %%i in (1 2 3 4 5) do (
  "%GIT%" -C "%R%" update-ref refs/heads/n%%i/deep HEAD >nul 2>&1
  if exist "%R%\.git\refs\heads\n%%i\deep" set /a %~2+=1
)
exit /b
