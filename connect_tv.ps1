$adb = "C:\Users\happy\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$tvIp = "192.168.1.144:5555"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Ожидание активации ADB на телевизоре ($tvIp)..." -ForegroundColor Yellow
Write-Host " Запустите приложение AdbTvEnabler на ТВ и нажмите кнопку." -ForegroundColor Gray
Write-Host " Нажмите Ctrl+C для отмены." -ForegroundColor DarkGray
Write-Host "==========================================================" -ForegroundColor Cyan

$attempt = 1
while ($true) {
    Write-Host "[$attempt] Попытка подключения к $tvIp..." -NoNewline
    $result = & $adb connect $tvIp 2>&1 | Out-String
    
    if ($result -match "connected to" -and -not ($result -match "cannot connect")) {
        Write-Host " УСПЕХ! Подключено!" -ForegroundColor Green
        Write-Host $result -ForegroundColor Green
        Write-Host "`nПодключенные устройства:" -ForegroundColor Cyan
        & $adb devices -l
        Write-Host "`nВнимание: Если на экране ТВ появился запрос 'Разрешить отладку?', выберите 'Всегда разрешать' и нажмите ОК." -ForegroundColor Yellow
        break
    } else {
        Write-Host " (порт еще не открыт)" -ForegroundColor DarkGray
    }
    
    $attempt++
    Start-Sleep -Seconds 3
}
