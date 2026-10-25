@echo off
rem Abre el programa para llenar e imprimir notas de entrega. No necesita instalar nada.
title Nota de entrega
mode con: cols=100 lines=42 >nul 2>&1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0nota-entrega.ps1"
