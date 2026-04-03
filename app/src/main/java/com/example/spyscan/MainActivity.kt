package com.example.spyscan


import java.util.Locale
import androidx.appcompat.app.AlertDialog
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import kotlin.collections.ArrayList
import kotlin.collections.HashMap



class 22222222MainActivity() : AppCompatActivity() {

    private lateinit var btnLast3: Button
    private lateinit var btnShowToday: Button
    private lateinit var inputSearch: EditText
    private lateinit var barChart: BarChart
    private lateinit var btnRiskScan: Button
    private lateinit var btnAccOverlay: Button
    private lateinit var btnSideloadScan: Button
    private lateinit var btnRootDebug: Button



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)



        btnLast3 = findViewById(R.id.btnLast3)
        btnShowToday = findViewById(R.id.btnShowToday)
        inputSearch = findViewById(R.id.inputSearch)
        barChart = findViewById(R.id.barChart)
        btnRiskScan = findViewById(R.id.btnRiskScan)
        btnRiskScan.setOnClickListener { showPermissionRiskScores() }
        btnAccOverlay = findViewById(R.id.btnAccOverlay)
        btnAccOverlay.setOnClickListener { showAccOverlayReport() }
        btnSideloadScan = findViewById(R.id.btnSideloadScan)
        btnSideloadScan.setOnClickListener { showSideloadApps() }
        btnRootDebug = findViewById(R.id.btnRootDebug)
        btnRootDebug.setOnClickListener { showRootDebugStatus() }



        if (!hasUsageStatsPermission()) {
            Toast.makeText(this, "Kullanım verilerine erişim izni gerekli.", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        // Açılışta grafiği doldur
        fillChartForToday()

        btnLast3.setOnClickListener { showLastThreeDialog() }

        btnShowToday.setOnClickListener {
            val q = inputSearch.text.toString().trim()
            if (q.isEmpty()) {
                Toast.makeText(this, "Önce uygulama adını yaz.", Toast.LENGTH_SHORT).show()
            } else {
                showTodayUsageForLabel(q)
            }
        }
    }

    // ====== İzin kontrolü ======
    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val op = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            AppOpsManager.OPSTR_GET_USAGE_STATS else "android:get_usage_stats"
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            appOps.unsafeCheckOpNoThrow(op, applicationInfo.uid, packageName)
        else @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(op, Binder.getCallingUid(), packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    // ====== Tarih aralığı (bugün) ======
    private fun todayRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val end = cal.timeInMillis
        return start to end
    }

    // ====== Model ======
    data class AppEvent(
        val packageName: String,
        val timeStamp: Long,
        val eventType: Int
    )

    // ====== Eventleri çek ======
    private fun queryEvents(start: Long, end: Long): List<AppEvent> {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val ue = usm.queryEvents(start, end)
        val out = ArrayList<AppEvent>()
        val e = UsageEvents.Event()
        while (ue.hasNextEvent()) {
            ue.getNextEvent(e)
            val pkg = e.packageName ?: continue
            when (e.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND,
                UsageEvents.Event.ACTIVITY_RESUMED,
                UsageEvents.Event.MOVE_TO_BACKGROUND,
                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    out.add(AppEvent(pkg, e.timeStamp, e.eventType))
                }
            }
        }
        return out
    }

    // ====== Oturum oluştur (paket, start, end) ======
    private fun buildSessions(events: List<AppEvent>): List<Triple<String, Long, Long>> {
        val startMap = HashMap<String, Long>()
        val sessions = ArrayList<Triple<String, Long, Long>>()
        for (ev in events) {
            when (ev.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND,
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    startMap[ev.packageName] = ev.timeStamp
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND,
                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    val s = startMap[ev.packageName]
                    if (s != null && ev.timeStamp > s) {
                        sessions.add(Triple(ev.packageName, s, ev.timeStamp))
                        startMap.remove(ev.packageName)
                    }
                }
            }
        }
        return sessions
    }

    // ====== Süreleri topla ======
    private fun aggregateDurations(sessions: List<Triple<String, Long, Long>>): Map<String, Long> {
        val totals = HashMap<String, Long>()
        for ((pkg, s, e) in sessions) {
            val dur = if (e > s) e - s else 0L
            totals[pkg] = (totals[pkg] ?: 0L) + dur
        }
        return totals
    }

    // ====== Sistem/launcher hariç ======
    private fun excludeLauncherOnly(totals: Map<String, Long>): Map<String, Long> {
        val launcherPkg = getDefaultLauncherPackage()
        val out = HashMap<String, Long>()
        for ((pkg, ms) in totals) {
            if (pkg != launcherPkg) out[pkg] = ms
        }
        return out
    }


    private fun getDefaultLauncherPackage(): String? {
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
        val cn: ComponentName? = intent.resolveActivity(packageManager)
        return cn?.packageName
    }

    private fun appLabel(pkg: String): String {
        return try {
            val ai = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(ai).toString()
        } catch (_: Exception) { pkg }
    }
    private fun isUserApp(pkg: String): Boolean {
        return try {
            val ai = packageManager.getApplicationInfo(pkg, 0)
            (ai.flags and ApplicationInfo.FLAG_SYSTEM) == 0
        } catch (_: Exception) { false }
    }

    private fun isLauncher(pkg: String?): Boolean {
        return pkg != null && pkg == getDefaultLauncherPackage()
    }


    private fun lastThreeApps(sessions: List<Triple<String, Long, Long>>): List<Triple<String, Long, Long>> {
        val launcherPkg = getDefaultLauncherPackage()
        val myPkg = packageName // SpyScan'ın kendi paketi

        return sessions
            .filter { (pkg, _, _) -> pkg != launcherPkg && pkg != myPkg } // Ana ekran ve SpyScan hariç
            .sortedByDescending { it.third }
            .distinctBy { it.first }
            .take(3)
    }



    // ====== UI Aksiyonları ======
    private fun fillChartForToday() {
        val (start, end) = todayRange()
        // fillChartForToday() içinde
        val totals = excludeLauncherOnly(aggregateDurations(buildSessions(queryEvents(start, end))))

        showBarChart(totals)
    }

    private fun showLastThreeDialog() {
        val (start, end) = todayRange()
        val sessions = buildSessions(queryEvents(start, end))
        val last3 = lastThreeApps(sessions)
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val msg = if (last3.isEmpty()) "Veri yok." else last3.joinToString("\n\n") { (pkg, a, k) ->
            "${appLabel(pkg)}\nAçılış: ${sdf.format(Date(a))}\nKapanış: ${sdf.format(Date(k))}"
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Son 3 Uygulama")
            .setMessage(msg)
            .setPositiveButton("Tamam", null)
            .show()
    }

    private fun showTodayUsageForLabel(queryLabel: String) {
        // SON 24 SAAT
        val end = System.currentTimeMillis()
        val start = end - 24L * 60L * 60L * 1000L

        val events = queryEvents(start, end)
        val launcherPkg = getDefaultLauncherPackage()

        // aranan etiketle eşleşen TÜM paketler (launcher hariç)
        val targetPkgs = events.map { it.packageName }.distinct().filter { pkg ->
            pkg != launcherPkg && appLabel(pkg).contains(queryLabel, ignoreCase = true)
        }

        val sessions = buildSessions(events).filter { (pkg, _, _) -> pkg in targetPkgs }

        if (sessions.isEmpty()) {
            Toast.makeText(this, "Son 24 saatte '$queryLabel' için kullanım yok.", Toast.LENGTH_SHORT).show()
            return
        }

        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        val text = sessions.joinToString("\n\n") { (pkg, s, e) ->
            "${appLabel(pkg)}\nAçılış: ${sdf.format(java.util.Date(s))}\nKapanış: ${sdf.format(java.util.Date(e))}"
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Son 24 Saat")
            .setMessage(text)
            .setPositiveButton("Tamam", null)
            .show()
    }



    // ====== Grafik ======
    private fun showBarChart(totals: Map<String, Long>) {
        val top = totals.entries.sortedByDescending { it.value }.take(5)
        val entries = ArrayList<BarEntry>()
        val labels = ArrayList<String>()
        top.forEachIndexed { i, (pkg, ms) ->
            entries.add(BarEntry(i.toFloat(), (ms / 1000f) / 60f)) // dakika
            labels.add(appLabel(pkg))
        }

        val ds = BarDataSet(entries, "Kullanım Süreleri (dk)")
        val data = BarData(ds)

        // 🌙 Tema kontrolü
        val isDarkTheme = resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        val textColor = if (isDarkTheme) android.graphics.Color.WHITE else android.graphics.Color.BLACK

        // Değer yazıları (bar üstü)
        ds.valueTextColor = textColor
        // Eksen ve lejant yazıları
        barChart.xAxis.textColor = textColor
        barChart.axisLeft.textColor = textColor
        barChart.axisRight.textColor = textColor
        barChart.legend.textColor = textColor
        // Eksen çizgileri ve grid (kontrast için)
        barChart.xAxis.axisLineColor = textColor
        barChart.xAxis.gridColor = textColor
        barChart.axisLeft.axisLineColor = textColor
        barChart.axisLeft.gridColor = textColor
        barChart.axisRight.axisLineColor = textColor
        barChart.axisRight.gridColor = textColor

        barChart.data = data
        barChart.description.isEnabled = false
        barChart.axisRight.isEnabled = false

        barChart.xAxis.apply {
            valueFormatter = IndexAxisValueFormatter(labels)
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)
            granularity = 1f
            labelCount = labels.size
            labelRotationAngle = -30f
        }

        barChart.invalidate()
    }
    // ============ ACCESSIBILITY & OVERLAY TESPİTİ ============

    // Etkin erişilebilirlik hizmetlerini (enabled services) oku → paket listesi
    private fun getEnabledAccessibilityPackages(): List<String> {
        val flat = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return emptyList()

        if (flat.isBlank()) return emptyList()

        // "com.pkg/.ServiceClass:com.pkg2/.Service2" formatını çözüyoruz
        return flat.split(':').mapNotNull { s ->
            val cn = ComponentName.unflattenFromString(s)
            cn?.packageName
        }.distinct()
    }

    // Overlay (ekran üstü) izni isteyen ve izni AÇIK olan uygulamaları bul
    private fun getOverlayApps(): Pair<List<String>, List<String>> {
        val pm = packageManager
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val installed = pm.getInstalledApplications(0)

        val hasOverlayGranted = mutableListOf<String>()
        val requestsButOff = mutableListOf<String>()

        for (ai in installed) {
            val pkg = ai.packageName

            // İzni manifest’te istiyor mu?
            val requestsOverlay = try {
                val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
                }
                pInfo.requestedPermissions?.contains("android.permission.SYSTEM_ALERT_WINDOW") == true
            } catch (_: Exception) { false }

            if (!requestsOverlay) continue

            // Uygulama için AppOps durumunu kontrol et
            val mode = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, ai.uid, pkg)
                } else {
                    @Suppress("DEPRECATION")
                    appOps.checkOpNoThrow(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, ai.uid, pkg)
                }
            } catch (_: Exception) {
                AppOpsManager.MODE_DEFAULT
            }

            if (mode == AppOpsManager.MODE_ALLOWED) {
                hasOverlayGranted += pkg
            } else {
                requestsButOff += pkg
            }
        }

        // Etiket (label) isimlerine çevir
        val grantedLabels = hasOverlayGranted.map { appLabel(it) }.sorted()
        val offLabels = requestsButOff.map { appLabel(it) }.sorted()

        return grantedLabels to offLabels
    }

    // Raporu göster
    private fun showAccOverlayReport() {
        val accPkgs = getEnabledAccessibilityPackages()            // etkin erişilebilirlik hizmetleri
        val (overlayOn, overlayOff) = getOverlayApps()             // overlay izni açık / kapalı

        val sb = StringBuilder()

        // Erişilebilirlik
        sb.append("ETKİN ERİŞİLEBİLİRLİK HİZMETLERİ: ${accPkgs.size}\n")
        if (accPkgs.isEmpty()) {
            sb.append("- Yok\n")
        } else {
            accPkgs.distinct().sorted().forEach { sb.append("• ").append(appLabel(it)).append('\n') }
        }

        // Overlay (izin AÇIK)
        sb.append("\nEKRAN ÜSTÜ İZNE SAHİP (AKTİF): ${overlayOn.size}\n")
        if (overlayOn.isEmpty()) {
            sb.append("- Yok\n")
        } else {
            overlayOn.forEach { sb.append("• ").append(it).append('\n') }
        }

        // Overlay (istiyor ama KAPALI)
        sb.append("\nOVERLAY İZNİNİ İSTİYOR AMA KAPALI: ${overlayOff.size}\n")
        if (overlayOff.isEmpty()) {
            sb.append("- Yok\n")
        } else {
            overlayOff.forEach { sb.append("• ").append(it).append('\n') }
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Accessibility & Overlay Tespiti")
            .setMessage(sb.toString())
            .setPositiveButton("Tamam", null)
            .show()
    }


    // 📌 Risk verisi modeli
    data class RiskItem(
        val packageName: String,
        val appLabel: String,
        val score: Int,
        val badge: String,
        val hits: List<String>
    )

    // 📌 Riskli izinler listesi (kategori, izinler, ağırlık)
    private val RISK_DEFS: List<Triple<String, Set<String>, Int>> = listOf(
        Triple("Kamera", setOf(
            "android.permission.CAMERA"
        ), 3),
        Triple("Mikrofon", setOf(
            "android.permission.RECORD_AUDIO"
        ), 3),
        Triple("SMS", setOf(
            "android.permission.READ_SMS",
            "android.permission.RECEIVE_SMS",
            "android.permission.SEND_SMS"
        ), 4),
        Triple("Konum", setOf(
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.ACCESS_BACKGROUND_LOCATION"   // arka plan konum
        ), 4),
        Triple("Kişiler", setOf(
            "android.permission.READ_CONTACTS",
            "android.permission.WRITE_CONTACTS",
            "android.permission.GET_ACCOUNTS"
        ), 2),
        Triple("Arama/Kayıtlar", setOf(
            "android.permission.READ_CALL_LOG",
            "android.permission.WRITE_CALL_LOG",
            "android.permission.CALL_PHONE",
            "android.permission.READ_PHONE_STATE",
            "android.permission.READ_PHONE_NUMBERS",         // ekledik
            "android.permission.PROCESS_OUTGOING_CALLS"
        ), 3),
        Triple("Bildirim", setOf(
            "android.permission.POST_NOTIFICATIONS"
        ), 1),
        Triple("Dosya/Medya", setOf(
            // Android 13+ medya izinleri
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_MEDIA_AUDIO",
            // eski depolama izinleri
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.MANAGE_EXTERNAL_STORAGE"
        ), 2),
        Triple("Sağlık/Hareket", setOf(
            "android.permission.BODY_SENSORS",
            "android.permission.ACTIVITY_RECOGNITION"
        ), 2),
        Triple("Takvim", setOf(
            "android.permission.READ_CALENDAR",
            "android.permission.WRITE_CALENDAR"
        ), 1)
    )


    // 📌 İzinleri çek
    private fun getRequestedPermissionsOf(pkg: String): Set<String> {
        return try {
            val pm = packageManager
            val pInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
            }
            pInfo.requestedPermissions?.toSet() ?: emptySet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    // 📌 Skor hesapla
    private fun riskScoreForPackage(pkg: String): Pair<Int, List<String>> {
        val req = getRequestedPermissionsOf(pkg)
        var score = 0
        val hits = mutableListOf<String>()
        for ((label, perms, weight) in RISK_DEFS) {
            if (req.any { it in perms }) {
                score += weight
                hits += label
            }
        }
        return score to hits
    }

    // 📌 Rozet belirle
    private fun badgeForScore(score: Int): String {
        return when {
            score >= 7 -> "Yüksek"
            score >= 3 -> "Orta"
            else -> "Düşük"
        }
    }

    // 📌 Kullanıcı uygulaması mı?
    private fun isUserInstalledApp(pkg: String): Boolean {
        return try {
            val ai = packageManager.getApplicationInfo(pkg, 0)
            (ai.flags and ApplicationInfo.FLAG_SYSTEM) == 0
        } catch (_: Exception) { false }
    }

    // 📌 Ana fonksiyon
    private fun showPermissionRiskScores() {
        val pm = packageManager
        val launcherPkg = getDefaultLauncherPackage()
        val myPkg = packageName

        // Cihazdaki uygulamaları al (launcher ve kendi uygulaman hariç)
        val apps = pm.getInstalledApplications(0)
            .map { it.packageName }
            .filter { it != launcherPkg && it != myPkg && isUserInstalledApp(it) }

        val items = mutableListOf<RiskItem>()
        for (pkg in apps) {
            val (score, hits) = riskScoreForPackage(pkg)
            if (score <= 0) continue
            val label = appLabel(pkg)
            items += RiskItem(pkg, label, score, badgeForScore(score), hits)
        }

        // Skora göre sırala
        items.sortByDescending { it.score }

        if (items.isEmpty()) {
            Toast.makeText(this, "Riskli izin içeren uygulama bulunamadı.", Toast.LENGTH_SHORT).show()
            return
        }

        // Mesaj (ilk 30 uygulama)
        val shown = items.take(30)
        val msg = shown.joinToString("\n\n") { it ->
            val cats = it.hits.joinToString(", ")
            "${it.appLabel}  —  Skor: ${it.score}  [${it.badge}]\n$cats"
        } + if (items.size > shown.size) "\n\n…ve ${items.size - shown.size} uygulama daha." else ""

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("İzin Risk Skoru (İlk ${shown.size})")
            .setMessage(msg)
            .setPositiveButton("Tamam", null)
            .show()
    }
    private fun showSideloadApps() {
        val pm = packageManager
        val launcherPkg = getDefaultLauncherPackage()
        val myPkg = packageName

        val sideloadList = mutableListOf<String>()

        val apps = pm.getInstalledApplications(0)
            .filter { app ->
                val pkg = app.packageName
                // 👉 Sistem hariç + launcher hariç + kendi uygulaman hariç
                pkg != launcherPkg && pkg != myPkg && isUserInstalledApp(pkg)
            }

        for (ai in apps) {
            val installer = try {
                pm.getInstallerPackageName(ai.packageName)
            } catch (_: Exception) {
                null
            }

            // Play Store (com.android.vending) dışındaki yükleyici → potansiyel sideload
            if (installer == null || installer != "com.android.vending") {
                sideloadList.add(appLabel(ai.packageName))
            }
        }

        if (sideloadList.isEmpty()) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Kaynak Dışı Uygulama Taraması")
                .setMessage("Kullanıcı uygulamaları içinde Play Store dışından yüklenen bulunmadı.")
                .setPositiveButton("Tamam", null)
                .show()
        } else {
            val msg = sideloadList.sorted().joinToString("\n") { "• $it" }
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Kaynak Dışı (Sideload) Uygulamalar")
                .setMessage(msg)
                .setPositiveButton("Tamam", null)
                .show()
        }
    }
    // -------- ROOT & USB DEBUG TESPİTİ --------

    private fun showRootDebugStatus() {
        val rooted = isDeviceRooted()
        val usbDbg = isUsbDebuggingEnabled()

        val statusRoot = if (rooted) "• CİHAZ ROOT'LU (RİSK YÜKSEK)" else "• Root izine rastlanmadı"
        val statusUsb  = if (usbDbg) "• USB HATA AYIKLAMA AÇIK (RİSK)" else "• USB hata ayıklama kapalı"

        val öneriRoot = if (rooted)
            "\n- Root’u kaldırın veya güvenilir yöneticilerle sınırlayın.\n- Bankacılık/kurumsal uygulamalarda ek dikkat."
        else ""

        val öneriUsb = if (usbDbg)
            "\n- Geliştirici seçeneklerinden USB hata ayıklamayı kapatın.\n- Yalnız güvenilir PC’lere bağlanın."
        else ""

        val msg = buildString {
            append(statusRoot).append('\n')
            append(statusUsb).append('\n')
            append(öneriRoot)
            append(öneriUsb)
        }.trim()

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Root / USB Debug Durumu")
            .setMessage(msg)
            .setPositiveButton("Tamam", null)
            .show()
    }

    /** Basit root tespiti: test-keys, su dosyaları, su komutu */
    private fun isDeviceRooted(): Boolean {
        // 1) Build.TAGS ipucu
        try {
            val tags = android.os.Build.TAGS
            if (tags != null && tags.contains("test-keys", ignoreCase = true)) return true
        } catch (_: Exception) {}

        // 2) Yaygın su yolları
        val suPaths = arrayOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/system/su",
            "/system/bin/.ext/su",
            "/system/usr/we-need-root/su",
            "/system/app/Superuser.apk",
            "/system/xbin/daemonsu"
        )
        try {
            for (p in suPaths) if (java.io.File(p).exists()) return true
        } catch (_: Exception) {}

        // 3) su çalıştırmayı dene (bazı cihazlarda bloklanır, exception normaldir)
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val rc = proc.waitFor()
            rc == 0
        } catch (_: Exception) {
            false
        }
    }

    /** USB debugging açık mı? */
    private fun isUsbDebuggingEnabled(): Boolean {
        return try {
            // Android 4.2+ için Settings.Global.ADB_ENABLED kullanılır
            val enabled = android.provider.Settings.Global.getInt(
                contentResolver,
                android.provider.Settings.Global.ADB_ENABLED,
                0
            )
            enabled == 1
        } catch (_: Exception) {
            false
        }
    }
    // --------- AĞ GÜVENLİK PANELİ (INTERNET + TrafficStats + Tahmini Pil Payı) ---------

    data class NetItem(
        val pkg: String,
        val label: String,
        val mbTotal: Double,
        val mbRx: Double,
        val mbTx: Double
    )

    private fun hasInternetPermission(pkg: String): Boolean {
        return try {
            val pm = packageManager
            val pInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(pkg, android.content.pm.PackageManager.PackageInfoFlags.of(android.content.pm.PackageManager.GET_PERMISSIONS.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, android.content.pm.PackageManager.GET_PERMISSIONS)
            }
            pInfo.requestedPermissions?.contains("android.permission.INTERNET") == true
        } catch (_: Exception) { false }
    }

    private fun bytesToMB(b: Long): Double = if (b <= 0) 0.0 else b / (1024.0 * 1024.0)

    private fun currentBatteryPercent(): Int {
        return try {
            val bm = getSystemService(android.content.Context.BATTERY_SERVICE) as android.os.BatteryManager
            val level = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (level in 0..100) level else -1
        } catch (_: Exception) { -1 }
    }

    /**
     * Boot'tan beri uid başına RX/TX byte okur. -1 gelebilir (desteklenmiyorsa).
     * Sadece kullanıcı uygulamaları + INTERNET izni olanlar listelenir.
     * Tahmini pil payı = uygulamanın MB / toplam MB * 100 (ağ kullanımına göre)
     */

    }