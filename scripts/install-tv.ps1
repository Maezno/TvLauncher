# Instala el release en el TV box y lo compila AOT (speed-profile), igual que Play Store.
# Sin el paso de compilación, cada `adb install` deja la app en estado "verify" (interpretada/JIT)
# hasta que el sistema ejecute el dexopt en segundo plano (normalmente de noche), y el scroll va a ~20 FPS.
param([string]$Device = "192.168.1.225:5555")
$ErrorActionPreference = "Stop"
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$apk = "$PSScriptRoot\..\app\build\outputs\apk\release\app-release.apk"

& $adb connect $Device | Out-Null
& $adb -s $Device install -r $apk
# "speed" y no "speed-profile": recién instalada, el perfil está vacío (ProfileInstaller lo escribe
# en el primer arranque) y speed-profile deja la app en "verify" igual.
& $adb -s $Device shell cmd package compile -m speed -f com.tvlauncher.app
& $adb -s $Device shell "am force-stop com.tvlauncher.app; am start -n com.tvlauncher.app/.MainActivity"
& $adb -s $Device shell "dumpsys package dexopt | grep -A2 '\[com.tvlauncher.app\]'"
