@echo off
SET CSDK_TOOLS_PATH=c:\ProgramData\cerence\tools\cerence_asr_embedded_5_2_8\venv\Scripts

REM ========================================================================
SET TEST=mnc
SET ACMODBUF=acmod6_6000_mnc_tmjl_wuw_f16_v1_0_0.dat

set MODELFILE=..\acmod\%ACMODBUF%

REM ========================================================================
REM compile dictionary: 
REM ==============
echo ON
@set INPUT=.\wuw.dct
@set OUTPUT=.\wuw.dcb
@set PARAM1=
@set PARAM2=
@set PARAM3=
"%CSDK_TOOLS_PATH%\dictcpl.exe" --dictionaryFilepath=%INPUT% --outputFilepath=%OUTPUT% %PARAM1% %PARAM2% %PARAM3%
@IF %ERRORLEVEL% NEQ 0 @CALL :Sub_ReportError

@echo OFF

REM ========================================================================


REM ========================================================================
REM create context: 
REM ==============
echo ON

@set INPUT=.\wuw_anyspeech.bnf
@set OUTPUT=.\wuw_anyspeech.fcf
@set PARAM1=--clcOverrideDictionaryFilepath=.\wuw.dcb
@set PARAM2=
@set PARAM3=
"%CSDK_TOOLS_PATH%\grmcpl.exe" --grammarFilepaths=%INPUT% --contextBufferFilepaths=%OUTPUT% --modelFilepath=%MODELFILE% %PARAM1% %PARAM2% %PARAM3%
@IF %ERRORLEVEL% NEQ 0 @CALL :Sub_ReportError

@echo OFF

REM ========================================================================
:END
@IF "%1"=="" @PAUSE
@goto:EOF


@REM =======================================================================
@REM Subroutine to report errors
:Sub_ReportError
@ECHO **************************************************
@ECHO *                ERROR                           *
@ECHO **************************************************
@PAUSE
@GOTO:EOF

 