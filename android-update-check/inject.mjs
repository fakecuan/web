// Menyuntikkan cek-update APK ke proyek Android Capacitor hasil `npx cap add android`.
// Pemakaian (dari folder aplikasi): node ../android-update-check/inject.mjs <tag-rilis> <nama-apk> [dialog=true|false]
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const [tag, apk, dialog = "true"] = process.argv.slice(2);
if (!tag || !apk) throw new Error("Pemakaian: inject.mjs <tag> <apk> [dialog]");
const app = path.resolve("android/app/src/main");

const copyDir = (src, dst) => {
  fs.mkdirSync(dst, { recursive: true });
  for (const e of fs.readdirSync(src, { withFileTypes: true })) {
    const s = path.join(src, e.name), d = path.join(dst, e.name);
    e.isDirectory() ? copyDir(s, d) : fs.copyFileSync(s, d);
  }
};
copyDir(path.join(here, "java"), path.join(app, "java"));

// MainActivity: panggil UpdateCheck.run(this) di onCreate
const find = (dir) => {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) { const r = find(p); if (r) return r; }
    else if (e.name === "MainActivity.java") return p;
  }
  return null;
};
const mainPath = find(path.join(app, "java"));
let main = fs.readFileSync(mainPath, "utf8");
if (!main.includes("UpdateCheck.run(this)")) {
  if (/super\.onCreate\([^)]*\);/.test(main)) {
    main = main.replace(/(super\.onCreate\([^)]*\);)/, "$1\n        com.fakecuan.updatecheck.UpdateCheck.run(this);");
  } else {
    main = main.replace(/extends BridgeActivity\s*\{\s*\}/, `extends BridgeActivity {
    @Override
    public void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        com.fakecuan.updatecheck.UpdateCheck.run(this);
    }
}`);
  }
  if (!main.includes("UpdateCheck.run(this)")) throw new Error("Gagal memasang UpdateCheck di MainActivity");
  fs.writeFileSync(mainPath, main);
}

// AndroidManifest
const mfPath = path.join(app, "AndroidManifest.xml");
let mf = fs.readFileSync(mfPath, "utf8");
const perms = ["android.permission.INTERNET", "android.permission.POST_NOTIFICATIONS", "android.permission.RECEIVE_BOOT_COMPLETED"]
  .filter((p) => !mf.includes(`"${p}"`))
  .map((p) => `    <uses-permission android:name="${p}" />`).join("\n");
if (perms) mf = mf.replace(/<application\b/, `${perms}\n\n    <application`);
mf = mf.replace(/<\/application>/, `
        <meta-data android:name="updatecheck.tag" android:value="${tag}" />
        <meta-data android:name="updatecheck.apk" android:value="${apk}" />
        <meta-data android:name="updatecheck.dialog" android:value="${dialog}" />
        <service
            android:name="com.fakecuan.updatecheck.UpdateJob"
            android:permission="android.permission.BIND_JOB_SERVICE"
            android:exported="false" />
    </application>`);
fs.writeFileSync(mfPath, mf);
console.log(`Cek update dipasang: ${tag}/${apk} (dialog=${dialog})`);
