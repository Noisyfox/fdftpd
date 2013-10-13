@echo off
rem Licensed to the Apache Software Foundation (ASF) under one or more
rem contributor license agreements.  See the NOTICE file distributed with
rem this work for additional information regarding copyright ownership.
rem The ASF licenses this file to You under the Apache License, Version 2.0
rem (the "License"); you may not use this file except in compliance with
rem the License.  You may obtain a copy of the License at
rem
rem     http://www.apache.org/licenses/LICENSE-2.0
rem
rem Unless required by applicable law or agreed to in writing, software
rem distributed under the License is distributed on an "AS IS" BASIS,
rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
rem See the License for the specific language governing permissions and
rem limitations under the License.

if "%OS%" == "Windows_NT" setlocal
rem ---------------------------------------------------------------------------
rem Stop script for the FDFTPD Server
rem
rem $Id: shutdown.bat 895392 2010-01-03 14:02:31Z kkolinko $
rem ---------------------------------------------------------------------------

rem Guess FDFTPD_HOME if not defined
set "CURRENT_DIR=%cd%"
if not "%FDFTPD_HOME%" == "" goto gotHome
set "FDFTPD_HOME=%CURRENT_DIR%"
if exist "%FDFTPD_HOME%\bin\fdftpd.bat" goto okHome
cd ..
set "FDFTPD_HOME=%cd%"
cd "%CURRENT_DIR%"
:gotHome
if exist "%FDFTPD_HOME%\bin\fdftpd.bat" goto okHome
echo The FDFTPD_HOME environment variable is not defined correctly
echo This environment variable is needed to run this program
goto end
:okHome

set "EXECUTABLE=%FDFTPD_HOME%\bin\fdftpd.bat"

rem Check that target executable exists
if exist "%EXECUTABLE%" goto okExec
echo Cannot find "%EXECUTABLE%"
echo This file is needed to run this program
goto end
:okExec

rem Get remaining unshifted command line arguments and save them in the
set CMD_LINE_ARGS=
:setArgs
if ""%1""=="""" goto doneSetArgs
set CMD_LINE_ARGS=%CMD_LINE_ARGS% %1
shift
goto setArgs
:doneSetArgs

call "%EXECUTABLE%" stop %CMD_LINE_ARGS%

:end
