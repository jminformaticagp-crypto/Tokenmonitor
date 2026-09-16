#!/usr/bin/env bash
set -euo pipefail

echo "Aplicando diagnóstico persistente da carteira..."

cat > app/src/main/java/com/jean/tokenmonitor/CrashReporter.java <<'JAVA'
package com.jean.tokenmonitor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.widget.TextView;

import java.io.PrintWriter;
import java.io.StringWriter;

public final class CrashReporter {
    private static final String PREFS = "token_monitor_crash";
    private static final String KEY_CRASH = "last_crash";
    private static final String KEY_STEP = "last_step";
    private static final String KEY_PENDING = "wallet_pending";

    private CrashReporter() {}

    public static void install(Context context) {
        final Context app = context.getApplicationContext();
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                StringWriter sw = new StringWriter();
                throwable.printStackTrace(new PrintWriter(sw));
                SharedPreferences p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                String step = p.getString(KEY_STEP, "desconhecido");
                p.edit()
                        .putString(KEY_CRASH, "Etapa: " + step + "\n\n" + sw)
                        .putBoolean(KEY_PENDING, true)
                        .commit();
            } catch (Throwable ignored) {}

            if (previous != null) {
                previous.uncaughtException(thread, throwable);
            }
        });
    }

    public static void begin(Context context, String step) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_PENDING, true)
                .putString(KEY_STEP, step)
                .commit();
    }

    public static void mark(Context context, String step) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_STEP, step)
                .commit();
    }

    public static void finish(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_PENDING, false)
                .putString(KEY_STEP, "concluido")
                .commit();
    }

    public static void showPreviousIssue(Activity activity) {
        SharedPreferences p = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String crash = p.getString(KEY_CRASH, null);
        boolean pending = p.getBoolean(KEY_PENDING, false);
        String step = p.getString(KEY_STEP, "desconhecido");

        if (crash == null && !pending) return;

        p.edit()
                .remove(KEY_CRASH)
                .putBoolean(KEY_PENDING, false)
                .apply();

        String details = crash != null
                ? crash
                : "O app foi interrompido durante a criação da carteira.\n\nÚltima etapa registrada: " + step;

        TextView view = new TextView(activity);
        view.setText(details);
        view.setTextColor(Color.DKGRAY);
        view.setTextSize(12);
        view.setTextIsSelectable(true);
        int pad = Math.round(16 * activity.getResources().getDisplayMetrics().density);
        view.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(activity)
                .setTitle("Diagnóstico da carteira")
                .setMessage("Tire uma captura desta tela e envie para análise.")
                .setView(view)
                .setPositiveButton("Fechar", null)
                .show();
    }
}
JAVA

python3 - <<'PY'
from pathlib import Path

path = Path("app/src/main/java/com/jean/tokenmonitor/MainActivity.java")
s = path.read_text(encoding="utf-8")

if "CrashReporter.install(this);" not in s:
    s = s.replace(
        "super.onCreate(savedInstanceState);",
        "super.onCreate(savedInstanceState);\n        CrashReporter.install(this);"
    )

if "CrashReporter.showPreviousIssue(this);" not in s:
    s = s.replace(
        "setContentView(buildUi());",
        "setContentView(buildUi());\n        CrashReporter.showPreviousIssue(this);"
    )

old = 'create.setOnClickListener(v -> confirmCreateWallet());'
new = '''create.setOnClickListener(v -> {
            CrashReporter.mark(this, "toque_no_botao_criar");
            confirmCreateWallet();
        });'''
if old in s:
    s = s.replace(old, new)

start = '''    private void createWalletNow() {
        try {
            WalletManager.CreatedWallet wallet = walletManager.createNewWallet();
            updateWalletPanel();
            showNewMnemonic(wallet.mnemonic, wallet.address);'''
replacement = '''    private void createWalletNow() {
        CrashReporter.begin(this, "inicio_createWalletNow");
        try {
            CrashReporter.mark(this, "antes_createNewWallet");
            WalletManager.CreatedWallet wallet = walletManager.createNewWallet();
            CrashReporter.mark(this, "depois_createNewWallet");
            updateWalletPanel();
            CrashReporter.mark(this, "depois_updateWalletPanel");
            showNewMnemonic(wallet.mnemonic, wallet.address);
            CrashReporter.mark(this, "depois_showNewMnemonic");
            CrashReporter.finish(this);'''
if start in s:
    s = s.replace(start, replacement)

catch_old = '''        } catch (Throwable e) {
            new AlertDialog.Builder(this)'''
catch_new = '''        } catch (Throwable e) {
            CrashReporter.mark(this, "catch_" + e.getClass().getName());
            CrashReporter.finish(this);
            new AlertDialog.Builder(this)'''
if catch_old in s:
    s = s.replace(catch_old, catch_new)

path.write_text(s, encoding="utf-8")
PY

python3 - <<'PY'
from pathlib import Path

p = Path("app/build.gradle")
s = p.read_text(encoding="utf-8")
s = s.replace("versionCode 4", "versionCode 5")
s = s.replace("versionName '0.2.2-beta'", "versionName '0.2.3-diagnostico'")
p.write_text(s, encoding="utf-8")

w = Path(".github/workflows/build-apk.yml")
x = w.read_text(encoding="utf-8")
x = x.replace("TokenMonitorJean-Beta-0.2.2-Wallet", "TokenMonitorJean-Beta-0.2.3-Diagnostico")
w.write_text(x, encoding="utf-8")
PY

git add app .github
git commit -m "Adicionar diagnostico persistente da carteira" || true
git push origin HEAD

echo "Concluído. Aguarde o Actions gerar TokenMonitorJean-Beta-0.2.3-Diagnostico."
