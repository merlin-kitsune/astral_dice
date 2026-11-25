$ErrorActionPreference = 'Continue'
Set-Location 'D:/MCProject/astral_dice_multiloader'
& ./gradlew.bat :fabric-1.20.1:build --console=plain -x pushToGame *> 'work/hotfix-150-build.log'
"GRADLE_EXIT=$LASTEXITCODE"
