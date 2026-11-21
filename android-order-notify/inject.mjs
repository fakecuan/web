// Menyuntikkan pemantau order (notifikasi + suara) ke proyek Android Capacitor hasil `npx cap add android`.
// Pemakaian (dari folder aplikasi, mis. android-dva-admin):
//   node ../android-order-notify/inject.mjs <topik-ntfy>
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const topic = process.argv[2];
if (!topic) throw new Error("Topik ntfy wajib diisi");

const app = path.resolve("android/app/src/main");
const copyDir = (src, dst) => {
  fs.mkdirSync(dst, { recursive: true });
  for (const e of fs.readdirSync(src, { withFileTypes: true })) {
    const s = path.join(src, e.name), d = path.join(dst, e.name);
    e.isDirectory() ? copyDir(s, d) : fs.copyFileSync(s, d);
  }
};

// 1. Kode Java + resource (suara, ikon status bar)
copyDir(path.join(here, "java"), path.join(app, "java"));
copyDir(path.join(here, "res"), path.join(app, "res"));

// 2. MainActivity: nyalakan pemantau saat aplikasi dibuka
const find = (dir) => {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) { const r = find(p); if (r) return r; }
    else if (e.name === "MainActivity.java") return p;
  }
  return null;
};
const mainPath = find(path.join(app, "java"));
if (!mainPath) throw new Error("MainActivity.java tidak ditemukan");
const pkg = fs.readFileSync(mainPath, "utf8").match(/^package\s+([\w.]+);/m)[1];
fs.writeFileSync(mainPath, `package ${pkg};

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;
import com.fakecuan.ordernotify.OrderNotifySetup;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        OrderNotifySetup.run(this);
    }
}
`);

// 3. AndroidManifest: izin, layanan, penerima boot, topik
const mfPath = path.join(app, "AndroidManifest.xml");
let mf = fs.readFileSync(mfPath, "utf8");
const perms = [
  "android.permission.INTERNET",
  "android.permission.POST_NOTIFICATIONS",
  "android.permission.FOREGROUND_SERVICE",
  "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
  "android.permission.RECEIVE_BOOT_COMPLETED",
  "android.permission.WAKE_LOCK",
  "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
].filter((p) => !mf.includes(`"${p}"`))
  .map((p) => `    <uses-permission android:name="${p}" />`).join("\n");
mf = mf.replace(/<application\b/, `${perms}\n\n    <application`);
const esc = (s) => s.replace(/&/g, "&amp;").replace(/"/g, "&quot;").replace(/</g, "&lt;");
mf = mf.replace(/<\/application>/, `
        <meta-data android:name="ordernotify.topic" android:value="${esc(topic)}" />
        <service
            android:name="com.fakecuan.ordernotify.OrderWatchService"
            android:exported="false"
            android:foregroundServiceType="specialUse">
            <property
                android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                android:value="Notifikasi order masuk untuk admin toko" />
        </service>
        <receiver
            android:name="com.fakecuan.ordernotify.BootReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
                <action android:name="android.intent.action.QUICKBOOT_POWERON" />
            </intent-filter>
        </receiver>
    </application>`);
fs.writeFileSync(mfPath, mf);
console.log(`Pemantau order dipasang: paket ${pkg}, topik ${topic}`);
