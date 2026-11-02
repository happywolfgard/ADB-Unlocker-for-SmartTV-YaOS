package com.adbenabler.tv;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends Activity {

    private static final String TAG = "AdbEnabler";

    private TextView tvIp;
    private TextView tvStatus;
    private TextView tvRoot;
    private TextView tvLog;
    private TextView tvFoundHeader;
    private LinearLayout layoutFoundActivities;

    private Button btnActivate;
    private Button btnFactoryMenu;
    private Button btnScanFactory;
    private Button btnDevSettings;
    private Button btnWirelessDebug;
    private Button btnAllSettings;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean isPortOpen = false;
    private volatile boolean hasRoot = false;
    private volatile boolean isRunningLoop = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvIp = findViewById(R.id.tvIp);
        tvStatus = findViewById(R.id.tvStatus);
        tvRoot = findViewById(R.id.tvRoot);
        tvLog = findViewById(R.id.tvLog);
        tvFoundHeader = findViewById(R.id.tvFoundHeader);
        layoutFoundActivities = findViewById(R.id.layoutFoundActivities);

        btnActivate = findViewById(R.id.btnActivate);
        btnFactoryMenu = findViewById(R.id.btnFactoryMenu);
        btnScanFactory = findViewById(R.id.btnScanFactory);
        btnDevSettings = findViewById(R.id.btnDevSettings);
        btnWirelessDebug = findViewById(R.id.btnWirelessDebug);
        btnAllSettings = findViewById(R.id.btnAllSettings);

        btnActivate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isRunningLoop) {
                    isRunningLoop = false;
                    btnActivate.setText("🚀 Цикл команд для включения ADB (5555)");
                    log("Цикл команд остановлен пользователем.");
                } else {
                    startAdbCommandLoop();
                }
            }
        });

        btnFactoryMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                triggerFactoryMenu();
            }
        });

        btnScanFactory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scanSystemForFactoryActivities();
            }
        });

        btnDevSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openCleanDevSettings();
            }
        });

        btnWirelessDebug.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openWirelessDebugSettings();
            }
        });

        btnAllSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openPictureSettings();
            }
        });

        // Run initial diagnostics & auto-scan for factory apps in background
        refreshStatus();
        scanSystemForFactoryActivities();
    }

    private void log(final String message) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                String current = tvLog.getText().toString();
                String[] lines = current.split("\n");
                StringBuilder sb = new StringBuilder();
                int start = Math.max(0, lines.length - 12);
                for (int i = start; i < lines.length; i++) {
                    sb.append(lines[i]).append("\n");
                }
                sb.append("• ").append(message);
                tvLog.setText(sb.toString());
            }
        });
    }

    private void refreshStatus() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String ip = getLocalIpAddress();
                hasRoot = checkSuExists();
                isPortOpen = testPort5555();

                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        tvIp.setText("IP: " + (ip != null ? ip : "Не определен"));
                        tvRoot.setText("Root (SU): " + (hasRoot ? "ДОСТУПЕН ✅" : "Не обнаружен ❌"));
                        tvRoot.setTextColor(hasRoot ? Color.parseColor("#22C55E") : Color.parseColor("#94A3B8"));

                        if (isPortOpen) {
                            tvStatus.setText("Порт 5555: ОТКРЫТ! ✅ (Подключайтесь!)");
                            tvStatus.setTextColor(Color.parseColor("#22C55E"));
                        } else {
                            tvStatus.setText("Порт 5555: ЗАКРЫТ ❌");
                            tvStatus.setTextColor(Color.parseColor("#EF4444"));
                        }
                    }
                });
            }
        }).start();
    }

    /**
     * Поочередно выполняет различные команды и методы активации ADB,
     * проверяя порт после каждого шага до достижения успеха.
     */
    private void startAdbCommandLoop() {
        isRunningLoop = true;
        btnActivate.setText("⏳ Идет цикл команд (нажмите для стоп)...");
        log("Начало цикла команд для активации ADB...");

        new Thread(new Runnable() {
            @Override
            public void run() {
                int attempt = 1;

                while (isRunningLoop && !isPortOpen) {
                    log("--- Проход цикла #" + attempt + " ---");

                    // 1. Попытка через ROOT (SU)
                    if (hasRoot) {
                        log("1.1. Выполнение через su (setprop port 5555)...");
                        executeSu("setprop service.adb.tcp.port 5555\nsetprop persist.adb.tcp.port 5555\nstop adbd\nstart adbd\n");
                        if (checkSuccess("su setprop 5555")) return;

                        log("1.2. Выполнение через su (ctl.restart adbd)...");
                        executeSu("setprop ctl.restart adbd\n");
                        if (checkSuccess("su ctl.restart")) return;

                        log("1.3. Выполнение через su (settings global)...");
                        executeSu("settings put global adb_enabled 1\nsettings put global adb_wifi_enabled 1\n");
                        if (checkSuccess("su settings global")) return;
                    }

                    // 2. Попытка через Reflection SystemProperties
                    log("2. Reflection SystemProperties.set...");
                    callSystemProperty("service.adb.tcp.port", "5555");
                    callSystemProperty("persist.adb.tcp.port", "5555");
                    callSystemProperty("ctl.restart", "adbd");
                    callSystemProperty("ctl.start", "adbd");
                    if (checkSuccess("SystemProperties reflection")) return;

                    // 3. Попытка через прямой shell (sh)
                    log("3. Вызов через /system/bin/sh...");
                    executeSh("setprop service.adb.tcp.port 5555");
                    executeSh("setprop persist.adb.tcp.port 5555");
                    executeSh("setprop ctl.restart adbd");
                    executeSh("settings put global adb_enabled 1");
                    executeSh("settings put global adb_wifi_enabled 1");
                    if (checkSuccess("sh commands")) return;

                    // 4. ContentResolver Settings.Global / Secure
                    log("4. ContentResolver Settings...");
                    try {
                        Settings.Global.putInt(getContentResolver(), "adb_enabled", 1);
                        Settings.Global.putInt(getContentResolver(), "adb_wifi_enabled", 1);
                    } catch (Exception ignored) {}
                    try {
                        Settings.Secure.putInt(getContentResolver(), "adb_enabled", 1);
                    } catch (Exception ignored) {}
                    if (checkSuccess("Settings Provider")) return;

                    // 5. Secret Code Broadcasts (CVTE / MStar)
                    log("5. Отправка широковещательных Secret Codes (2580, 1147)...");
                    sendSecretCode("2580");
                    sendSecretCode("1147");
                    sendSecretCode("3138");
                    sendSecretCode("815");
                    if (checkSuccess("Secret Codes")) return;

                    // 6. Broadcast intent ADB_PORT
                    try {
                        Intent portIntent = new Intent("android.intent.action.ADB_PORT");
                        portIntent.putExtra("port", 5555);
                        sendBroadcast(portIntent);
                    } catch (Exception ignored) {}

                    // Пауза перед следующим проходом
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        break;
                    }

                    attempt++;
                    if (attempt > 10) {
                        // Если после 10 циклов закрыт, делаем паузу и продолжаем проверять
                        log("Команды отправлены. Порт 5555 проверяется каждые 3 сек...");
                        try {
                            Thread.sleep(3000);
                        } catch (InterruptedException ignored) {}
                    }
                }

                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        isRunningLoop = false;
                        btnActivate.setText("🚀 Цикл команд для включения ADB (5555)");
                    }
                });
            }
        }).start();
    }

    private boolean checkSuccess(String stageName) {
        try {
            Thread.sleep(800);
        } catch (InterruptedException ignored) {}

        if (testPort5555()) {
            isPortOpen = true;
            isRunningLoop = false;
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    tvStatus.setText("Порт 5555: ОТКРЫТ! ✅ (Подключайтесь!)");
                    tvStatus.setTextColor(Color.parseColor("#22C55E"));
                    btnActivate.setText("✅ ADB АКТИВИРОВАН!");
                    log("🎉 УСПЕХ! Порт 5555 открыт после: " + stageName);
                    Toast.makeText(MainActivity.this, "ADB успешно включен на порту 5555!", Toast.LENGTH_LONG).show();
                }
            });
            return true;
        }
        return false;
    }

    private void executeSu(String script) {
        try {
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes(script);
            os.writeBytes("exit\n");
            os.flush();
            p.waitFor();
        } catch (Exception e) {
            log("su error: " + e.getMessage());
        }
    }

    private void executeSh(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
            p.waitFor();
        } catch (Exception ignored) {}
    }

    private void callSystemProperty(String key, String value) {
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Method m = c.getMethod("set", String.class, String.class);
            m.invoke(null, key, value);
        } catch (Exception ignored) {}
    }

    private void sendSecretCode(String code) {
        try {
            Intent intent = new Intent("android.provider.Telephony.SECRET_CODE");
            intent.setData(Uri.parse("android_secret_code://" + code));
            sendBroadcast(intent);
        } catch (Exception ignored) {}
    }

    /**
     * Попытка открыть именно заводское сервисное меню платы (без fallback на AboutActivity).
     */
    private void triggerFactoryMenu() {
        log("Запуск заводского меню платы CVTE / MStar...");

        // 1. Посылаем Secret Code broadcast
        sendSecretCode("2580");
        sendSecretCode("1147");

        // 2. Проверяем точные компоненты Factory меню
        ComponentName[] factoryComponents = new ComponentName[] {
            new ComponentName("com.cvte.tv.factory", "com.cvte.tv.factory.MainActivity"),
            new ComponentName("com.cvte.tv.factorymenu", "com.cvte.tv.factorymenu.MainActivity"),
            new ComponentName("com.cvte.factory", "com.cvte.factory.MainActivity"),
            new ComponentName("com.cvte.designmenu", "com.cvte.designmenu.MainActivity"),
            new ComponentName("com.cvte.tv.designmenu", "com.cvte.tv.designmenu.MainActivity"),
            new ComponentName("com.mstar.android.tv.service", "com.mstar.android.tv.service.FactoryMenuActivity"),
            new ComponentName("com.mstar.tv.service", "com.mstar.tv.service.FactoryMenuActivity"),
            new ComponentName("com.mstar.tv.service", "com.mstar.tv.service.MainActivity"),
            new ComponentName("com.mediatek.engineermode", "com.mediatek.engineermode.EngineerMode"),
            new ComponentName("com.mediatek.factory", "com.mediatek.factory.FactoryMode"),
            new ComponentName("com.droidlogic.factorymenu", "com.droidlogic.factorymenu.FactoryMenuActivity"),
            new ComponentName("com.android.settings", "com.android.settings.TestingSettings")
        };

        for (ComponentName cn : factoryComponents) {
            try {
                Intent it = new Intent(Intent.ACTION_MAIN);
                it.setComponent(cn);
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(it);
                log("Успешно запущен компонент: " + cn.getShortClassName());
                return;
            } catch (Exception ignored) {}
        }

        log("Ни один стандартный компонент не открылся. Выполняю сканирование...");
        scanSystemForFactoryActivities();
        Toast.makeText(this, "Выберите найденное сервисное меню из списка ниже!", Toast.LENGTH_LONG).show();
    }

    /**
     * Сканирует абсолютно ВСЕ установленные в системе пакеты
     * и ищет любые активности со словами factory, engineer, cvte, mstar, service, design.
     */
    private void scanSystemForFactoryActivities() {
        log("Сканирование системы на наличие скрытых сервисных меню...");

        new Thread(new Runnable() {
            @Override
            public void run() {
                PackageManager pm = getPackageManager();
                final List<ActivityInfo> matched = new ArrayList<ActivityInfo>();

                try {
                    List<PackageInfo> packages = pm.getInstalledPackages(PackageManager.GET_ACTIVITIES);
                    String[] keywords = new String[] {
                        "factory", "engineer", "service", "cvte", "mstar", "design", "hidden", "menu", "debug", "test"
                    };

                    for (PackageInfo pi : packages) {
                        if (pi.activities == null) continue;
                        for (ActivityInfo ai : pi.activities) {
                            String nameLower = (ai.packageName + "/" + ai.name).toLowerCase();
                            // Исключаем обычные системные настройки и google/android framework
                            if (nameLower.contains("com.google.") || nameLower.contains("com.yandex.tv.launcher")) {
                                continue;
                            }
                            for (String kw : keywords) {
                                if (nameLower.contains(kw)) {
                                    matched.add(ai);
                                    break;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log("Ошибка сканирования: " + e.getMessage());
                }

                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        layoutFoundActivities.removeAllViews();

                        if (matched.isEmpty()) {
                            tvFoundHeader.setVisibility(View.GONE);
                            log("Сервисные активности с явными именами не найдены в списке пакетов.");
                        } else {
                            tvFoundHeader.setVisibility(View.VISIBLE);
                            tvFoundHeader.setText("Найдено скрытых меню (" + matched.size() + "): Нажимайте для входа:");
                            log("Найдено " + matched.size() + " сервисных компонентов! Они добавлены в список справа.");

                            for (final ActivityInfo ai : matched) {
                                Button b = new Button(MainActivity.this);
                                b.setFocusable(true);
                                b.setClickable(true);
                                b.setBackgroundResource(R.drawable.btn_selector);
                                b.setTextColor(Color.WHITE);
                                b.setTextSize(13);
                                b.setPadding(12, 10, 12, 10);
                                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                );
                                lp.setMargins(0, 8, 0, 0);
                                b.setLayoutParams(lp);

                                String simpleName = ai.name.substring(ai.name.lastIndexOf('.') + 1);
                                b.setText("▶ " + simpleName + "\n(" + ai.packageName + ")");

                                b.setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        try {
                                            Intent it = new Intent();
                                            it.setComponent(new ComponentName(ai.packageName, ai.name));
                                            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                            startActivity(it);
                                            log("Запуск: " + ai.name);
                                        } catch (Exception ex) {
                                            log("Ошибка запуска " + simpleName + ": " + ex.getMessage());
                                            Toast.makeText(MainActivity.this, "Не удалось открыть: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
                                        }
                                    }
                                });

                                layoutFoundActivities.addView(b);
                            }
                        }
                    }
                });
            }
        }).start();
    }

    /**
     * Открывает экран настроек Изображения / Экрана (где нажимается комбинация пульта)
     */
    private void openPictureSettings() {
        log("Открытие меню настроек экрана (для ввода комбинации пульта)...");
        Intent[] intents = new Intent[] {
            new Intent().setComponent(new ComponentName("com.android.tv.settings", "com.android.tv.settings.display.DisplayActivity")),
            new Intent().setComponent(new ComponentName("com.android.tv.settings", "com.android.tv.settings.device.display.daydream.DaydreamActivity")),
            new Intent().setComponent(new ComponentName("com.android.tv.settings", "com.android.tv.settings.device.sound.SoundActivity")),
            new Intent(Settings.ACTION_DISPLAY_SETTINGS),
            new Intent(Settings.ACTION_SETTINGS)
        };

        for (Intent it : intents) {
            try {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(it);
                log("Открыт экран настроек. Нажмите на пульте: ➡️ Вправо -> ⬆️ Вверх -> ➡️ Вправо -> ⬆️ Вверх -> ⬅️ Назад!");
                return;
            } catch (Exception ignored) {}
        }
        log("Не удалось запустить активность дисплея напрямую.");
    }

    private void openCleanDevSettings() {
        log("Открытие меню разработчика Android...");
        Intent[] intents = new Intent[] {
            new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
            new Intent().setComponent(new ComponentName("com.android.tv.settings", "com.android.tv.settings.device.apps.specialaccess.DevelopmentSettingsActivity")),
            new Intent().setComponent(new ComponentName("com.android.settings", "com.android.settings.DevelopmentSettings")),
            new Intent().setComponent(new ComponentName("com.android.tv.settings", "com.android.tv.settings.MainSettings"))
        };

        for (Intent intent : intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                log("Экран разработчика запущен.");
                return;
            } catch (Exception ignored) {}
        }
    }

    private void openWirelessDebugSettings() {
        log("Открытие беспроводной отладки...");
        Intent[] intents = new Intent[] {
            new Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS"),
            new Intent().setComponent(new ComponentName("com.android.settings", "com.android.settings.development.WirelessDebuggingFragment")),
            new Intent().setComponent(new ComponentName("com.android.tv.settings", "com.android.tv.settings.development.WirelessDebuggingActivity")),
            new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        };

        for (Intent intent : intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                log("Экран беспроводной отладки запущен.");
                return;
            } catch (Exception ignored) {}
        }
        Toast.makeText(this, "Беспроводная отладка недоступна на данной версии Android", Toast.LENGTH_SHORT).show();
    }

    private boolean testPort5555() {
        Socket socket = null;
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress("127.0.0.1", 5555), 400);
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (Exception ignored) {}
            }
        }
    }

    private boolean checkSuExists() {
        String[] paths = new String[] {
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/data/local/su",
            "/vendor/bin/su"
        };
        for (String path : paths) {
            if (new File(path).exists()) {
                return true;
            }
        }
        return false;
    }

    private String getLocalIpAddress() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
