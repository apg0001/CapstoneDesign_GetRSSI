package com.example.rssi

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.media.MediaScannerConnection
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "wifi_rssi.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS rssi_data (id INTEGER PRIMARY KEY AUTOINCREMENT, ssid TEXT, mac TEXT, rssi INTEGER, time TEXT, location TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS rssi_data")
        onCreate(db)
    }
}

class MainActivity : AppCompatActivity() {
    private lateinit var wifiManager: WifiManager
    private lateinit var editTextLocation: EditText
    private lateinit var buttonScan: Button
    private lateinit var buttonStopScan: Button
    private lateinit var buttonExport: Button
    private lateinit var buttonView: Button
    private lateinit var buttonClear: Button // 🔹 추가
    private lateinit var textViewStatus: TextView
    private lateinit var scanResultsReceiver: BroadcastReceiver
    private lateinit var databaseHelper: DatabaseHelper
    private val handler = Handler()
    private var scanCount = 0
    private var isScanning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        editTextLocation = findViewById(R.id.editTextLocation)
        buttonScan = findViewById(R.id.buttonScan)
        buttonStopScan = findViewById(R.id.buttonStopScan)
        buttonExport = findViewById(R.id.buttonExport)
        buttonView = findViewById(R.id.buttonView)
        buttonClear = findViewById(R.id.buttonClear) // 🔹 연결
        textViewStatus = findViewById(R.id.textViewStatus)
        databaseHelper = DatabaseHelper(this)

        val requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
                if (!isGranted) {
                    Toast.makeText(this, "WiFi 권한이 필요합니다.", Toast.LENGTH_LONG).show()
                }
            }

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        buttonScan.setOnClickListener {
            if (editTextLocation.text.isEmpty()) {
                Toast.makeText(this, "위치 정보를 입력하세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            scanCount = 0
            isScanning = true
            textViewStatus.text = "측정 중..."
            startRepeatedScan()
        }

        buttonStopScan.setOnClickListener {
            isScanning = false
            textViewStatus.text = "측정 중지됨"
            Toast.makeText(this, "WiFi 스캔이 중지되었습니다.", Toast.LENGTH_SHORT).show()
        }

        buttonExport.setOnClickListener {
            exportData()
        }

        buttonView.setOnClickListener {
            viewData()
        }

        buttonClear.setOnClickListener { // 🔹 초기화 버튼
            showClearConfirmationDialog()
        }

        scanResultsReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val success =
                    intent?.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false) ?: false
                if (success) {
                    saveScanResults()
                    scanCount++
                    if (scanCount < 30 && isScanning) {
                        textViewStatus.text = "측정 중 ${scanCount}/30"
                        handler.postDelayed({ startWifiScan() }, 3000)
                    } else if (scanCount >= 30) {
                        textViewStatus.text = "측정 완료"
                        showCompletionNotification()
                        showCompletionDialog()
                    }
                } else {
                    if (isScanning) {
                        Toast.makeText(
                            this@MainActivity,
                            "WiFi 스캔 실패. 다시 시도 중...",
                            Toast.LENGTH_SHORT
                        ).show()
                        handler.postDelayed({ startWifiScan() }, 10000)
                    }
                }
            }
        }

        registerReceiver(
            scanResultsReceiver,
            IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        )
    }

    private fun startRepeatedScan() {
        startWifiScan()
    }

    private fun viewData() {
        val db = databaseHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM rssi_data", null)
        val builder = StringBuilder()
        while (cursor.moveToNext()) {
            builder.append(
                "SSID: ${cursor.getString(1)}, MAC: ${cursor.getString(2)}, RSSI: ${
                    cursor.getString(
                        3
                    )
                }db, Time: ${cursor.getString(4)}, Location: ${cursor.getString(5)}\n\n"
            )
        }
        cursor.close()

        AlertDialog.Builder(this)
            .setTitle("저장된 데이터")
            .setMessage(builder.toString())
            .setPositiveButton("확인", null)
            .show()
    }

    private fun startWifiScan() {
        if (!wifiManager.startScan()) {
            Toast.makeText(this, "WiFi 스캔 시작 실패", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveScanResults() {
        val scanResults = wifiManager.scanResults
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val location = editTextLocation.text.toString()
        val db = databaseHelper.writableDatabase

        for (result: ScanResult in scanResults) {
            db.execSQL(
                "INSERT INTO rssi_data (ssid, mac, rssi, time, location) VALUES (?, ?, ?, ?, ?)",
                arrayOf(result.SSID, result.BSSID, result.level, timestamp, location)
            )
        }

        Toast.makeText(this, "RSSI 데이터 저장 완료", Toast.LENGTH_SHORT).show()
    }

    private fun exportData() {
        val db = databaseHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM rssi_data", null)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "wifi_rssi_log_$timestamp.csv"
        val csvFile = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            fileName
        )
        try {
            val writer = FileWriter(csvFile)
            writer.append("SSID,MAC,RSSI,Time,Location\n")
            while (cursor.moveToNext()) {
                writer.append(
                    "${cursor.getString(1)},${cursor.getString(2)},${cursor.getInt(3)},${
                        cursor.getString(
                            4
                        )
                    },${cursor.getString(5)}\n"
                )
            }
            writer.flush()
            writer.close()
            cursor.close()
            MediaScannerConnection.scanFile(
                this,
                arrayOf(csvFile.absolutePath),
                arrayOf("text/csv"),
                null
            )
            Toast.makeText(
                this,
                "CSV 파일이 다운로드 폴더에 저장되었습니다:\n${csvFile.absolutePath}",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: IOException) {
            Toast.makeText(this, "파일 저장 오류: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showClearConfirmationDialog() { // 🔹 데이터 초기화 확인
        AlertDialog.Builder(this)
            .setTitle("데이터 초기화")
            .setMessage("정말 삭제하시겠습니까?")
            .setPositiveButton("예") { dialog, _ ->
                clearDatabase()
                Toast.makeText(this, "데이터가 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("아니오") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun clearDatabase() { // 🔹 실제 삭제
        val db = databaseHelper.writableDatabase
        db.execSQL("DELETE FROM rssi_data")
    }

    private fun showCompletionNotification() {
        val notification = NotificationCompat.Builder(this, "WIFI_SCAN")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("WiFi 스캔 완료")
            .setContentText("30회 측정이 완료되었습니다.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(this).notify(1, notification)
    }

    private fun showCompletionDialog() {
        AlertDialog.Builder(this)
            .setTitle("측정 완료")
            .setMessage("30번 측정이 완료되었습니다.")
            .setPositiveButton("확인", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(scanResultsReceiver)
    }
}

// v2
//package com.example.rssi
//
//import android.Manifest
//import android.app.*
//import android.content.*
//import android.content.pm.PackageManager
//import android.database.sqlite.SQLiteDatabase
//import android.database.sqlite.SQLiteOpenHelper
//import android.media.MediaScannerConnection
//import android.net.wifi.ScanResult
//import android.net.wifi.WifiManager
//import android.os.*
//import android.widget.*
//import androidx.activity.result.contract.ActivityResultContracts
//import androidx.appcompat.app.AppCompatActivity
//import androidx.core.app.NotificationCompat
//import androidx.core.app.NotificationManagerCompat
//import java.io.*
//import java.text.SimpleDateFormat
//import java.util.*
//
//class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "wifi_rssi.db", null, 1) {
//    override fun onCreate(db: SQLiteDatabase) {
//        db.execSQL("CREATE TABLE IF NOT EXISTS rssi_data (id INTEGER PRIMARY KEY AUTOINCREMENT, ssid TEXT, mac TEXT, rssi INTEGER, time TEXT, location TEXT)")
//    }
//
//    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
//        db.execSQL("DROP TABLE IF EXISTS rssi_data")
//        onCreate(db)
//    }
//}
//
//class MainActivity : AppCompatActivity() {
//    private lateinit var wifiManager: WifiManager
//    private lateinit var editTextLocation: EditText
//    private lateinit var buttonScan: Button
//    private lateinit var buttonStopScan: Button
//    private lateinit var buttonExport: Button
//    private lateinit var buttonView: Button
//    private lateinit var textViewStatus: TextView
//    private lateinit var scanResultsReceiver: BroadcastReceiver
//    private lateinit var databaseHelper: DatabaseHelper
//    private val handler = Handler()
//    private var scanCount = 0
//    private var isScanning = false
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_main)
//
//        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
//        editTextLocation = findViewById(R.id.editTextLocation)
//        buttonScan = findViewById(R.id.buttonScan)
//        buttonStopScan = findViewById(R.id.buttonStopScan)
//        buttonExport = findViewById(R.id.buttonExport)
//        buttonView = findViewById(R.id.buttonView)
//        textViewStatus = findViewById(R.id.textViewStatus)
//        databaseHelper = DatabaseHelper(this)
//
//        val requestPermissionLauncher =
//            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
//                if (!isGranted) {
//                    Toast.makeText(this, "WiFi 권한이 필요합니다.", Toast.LENGTH_LONG).show()
//                }
//            }
//
//        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
//            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
//        }
//
//        buttonScan.setOnClickListener {
//            if (editTextLocation.text.isEmpty()) {
//                Toast.makeText(this, "위치 정보를 입력하세요.", Toast.LENGTH_SHORT).show()
//                return@setOnClickListener
//            }
//            scanCount = 0
//            isScanning = true
//            textViewStatus.text = "측정 중..."
//            startRepeatedScan()
//        }
//
//        buttonStopScan.setOnClickListener {
//            isScanning = false
//            textViewStatus.text = "측정 중지됨"
//            Toast.makeText(this, "WiFi 스캔이 중지되었습니다.", Toast.LENGTH_SHORT).show()
//        }
//
//        buttonExport.setOnClickListener {
//            exportData()
//        }
//
//        buttonView.setOnClickListener {
//            viewData()
//        }
//
//        scanResultsReceiver = object : BroadcastReceiver() {
//            override fun onReceive(context: Context?, intent: Intent?) {
//                val success = intent?.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false) ?: false
//                if (success) {
//                    saveScanResults()
//                    scanCount++
//                    if (scanCount < 30 && isScanning) {
//                        textViewStatus.text = "측정 중 ${scanCount}/30"
//                        handler.postDelayed({ startWifiScan() }, 3000)
//                    } else if (scanCount >= 30) {
//                        textViewStatus.text = "측정 완료"
//                        showCompletionNotification()
//                        showCompletionDialog()
//                    }
//                } else {
//                    if (isScanning) {
//                        Toast.makeText(this@MainActivity, "WiFi 스캔 실패. 다시 시도 중...", Toast.LENGTH_SHORT).show()
//                        handler.postDelayed({ startWifiScan() }, 10000)  // 스캔 실패 시 다시 시도
//                    }
//                }
//            }
//        }
//
//        registerReceiver(scanResultsReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
//    }
//
//    private fun startRepeatedScan() {
//        startWifiScan()
//    }
//
//    private fun viewData() {
//        val db = databaseHelper.readableDatabase
//        val cursor = db.rawQuery("SELECT * FROM rssi_data", null)
//        val builder = StringBuilder()
//        while (cursor.moveToNext()) {
//            builder.append("SSID: ${cursor.getString(1)}, MAC: ${cursor.getString(2)}, RSSI: ${cursor.getString(3)}db, Time: ${cursor.getString(4)}, Location: ${cursor.getString(5)}\n\n")
//        }
//        cursor.close()
//
//        AlertDialog.Builder(this)
//            .setTitle("저장된 데이터")
//            .setMessage(builder.toString())
//            .setPositiveButton("확인", null)
//            .show()
//    }
//
//    private fun startWifiScan() {
//        if (!wifiManager.startScan()) {
//            Toast.makeText(this, "WiFi 스캔 시작 실패", Toast.LENGTH_SHORT).show()
//        }
//    }
//
//    private fun saveScanResults() {
//        val scanResults = wifiManager.scanResults
//        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
//        val location = editTextLocation.text.toString()
//        val db = databaseHelper.writableDatabase
//
//        for (result: ScanResult in scanResults) {
//            db.execSQL("INSERT INTO rssi_data (ssid, mac, rssi, time, location) VALUES (?, ?, ?, ?, ?)",
//                arrayOf(result.SSID, result.BSSID, result.level, timestamp, location))
//        }
//
//        Toast.makeText(this, "RSSI 데이터 저장 완료", Toast.LENGTH_SHORT).show()
//    }
//
//    private fun exportData() {
//        val db = databaseHelper.readableDatabase
//        val cursor = db.rawQuery("SELECT * FROM rssi_data", null)
//        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
//
//        // 파일명에 날짜 및 시간 추가
//        val fileName = "wifi_rssi_log_$timestamp.csv"
//
//        // 다운로드 폴더에 저장
//        val csvFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
//        try {
//            val writer = FileWriter(csvFile)
//            writer.append("SSID,MAC,RSSI,Time,Location\n")
//            while (cursor.moveToNext()) {
//                writer.append("${cursor.getString(1)},${cursor.getString(2)},${cursor.getInt(3)},${cursor.getString(4)},${cursor.getString(5)}\n")
//            }
//            writer.flush()
//            writer.close()
//            cursor.close()
//
//            MediaScannerConnection.scanFile(this, arrayOf(csvFile.absolutePath), arrayOf("text/csv"), null)
//
//            Toast.makeText(this, "CSV 파일이 다운로드 폴더에 저장되었습니다:\n${csvFile.absolutePath}", Toast.LENGTH_LONG).show()
//        } catch (e: IOException) {
//            Toast.makeText(this, "파일 저장 오류: ${e.message}", Toast.LENGTH_LONG).show()
//        }
//    }
//
//    private fun showCompletionNotification() {
//        val notification = NotificationCompat.Builder(this, "WIFI_SCAN")
//            .setSmallIcon(R.drawable.ic_launcher_foreground)
//            .setContentTitle("WiFi 스캔 완료")
//            .setContentText("30회 측정이 완료되었습니다.")
//            .setPriority(NotificationCompat.PRIORITY_HIGH)
//            .setAutoCancel(true)
//            .build()
//
//        NotificationManagerCompat.from(this).notify(1, notification)
//    }
//
//    private fun showCompletionDialog() {
//        AlertDialog.Builder(this)
//            .setTitle("측정 완료")
//            .setMessage("30번 측정이 완료되었습니다.")
//            .setPositiveButton("확인", null)
//            .show()
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        unregisterReceiver(scanResultsReceiver)
//    }
//}


// v1
//package com.example.rssi
//
//import android.util.Log
//import android.Manifest
//import android.app.AlertDialog
//import android.content.BroadcastReceiver
//import android.content.Context
//import android.content.Intent
//import android.content.IntentFilter
//import android.content.pm.PackageManager
//import android.database.sqlite.SQLiteDatabase
//import android.database.sqlite.SQLiteOpenHelper
//import android.media.MediaScannerConnection
//import android.net.wifi.ScanResult
//import android.net.wifi.WifiManager
//import android.os.Bundle
//import android.os.Environment
//import android.os.Handler
//import android.widget.Button
//import android.widget.EditText
//import android.widget.TextView
//import android.widget.Toast
//import androidx.activity.result.contract.ActivityResultContracts
//import androidx.appcompat.app.AppCompatActivity
//import com.example.rssi.R  // ✅ R 클래스를 명시적으로 추가
//import java.io.File
//import java.io.FileWriter
//import java.io.IOException
//import java.text.SimpleDateFormat
//import java.util.*
//
//class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "wifi_rssi.db", null, 1) {
//    override fun onCreate(db: SQLiteDatabase) {
//        db.execSQL("CREATE TABLE IF NOT EXISTS rssi_data (id INTEGER PRIMARY KEY AUTOINCREMENT, ssid TEXT, mac TEXT, rssi INTEGER, time TEXT, location TEXT)")
//    }
//
//    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
//        db.execSQL("DROP TABLE IF EXISTS rssi_data")
//        onCreate(db)
//    }
//}
//
//class MainActivity : AppCompatActivity() {
//    private lateinit var wifiManager: WifiManager
//    private lateinit var editTextLocation: EditText
//    private lateinit var buttonScan: Button
//    private lateinit var buttonExport: Button
//    private lateinit var buttonView: Button
//    private lateinit var textViewStatus: TextView
//    private lateinit var scanResultsReceiver: BroadcastReceiver
//    private lateinit var databaseHelper: DatabaseHelper
//    private val handler = Handler()
//    private var scanCount = 0
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_main)
//
//        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
//        editTextLocation = findViewById(R.id.editTextLocation)
//        buttonScan = findViewById(R.id.buttonScan)
//        buttonExport = findViewById(R.id.buttonExport)
//        buttonView = findViewById(R.id.buttonView)
//        textViewStatus = findViewById(R.id.textViewStatus)
//        databaseHelper = DatabaseHelper(this)
//
//        val requestPermissionLauncher =
//            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
//                if (!isGranted) {
//                    Toast.makeText(this, "WiFi 권한이 필요합니다.", Toast.LENGTH_LONG).show()
//                }
//            }
//
//        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
//            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
//        }
//
//        buttonScan.setOnClickListener {
//            if (editTextLocation.text.isEmpty()) {
//                Toast.makeText(this, "위치 정보를 입력하세요.", Toast.LENGTH_SHORT).show()
//                return@setOnClickListener
//            }
//            scanCount = 0
//            textViewStatus.text = "측정 중..."
//            startRepeatedScan()
//        }
//
//        buttonExport.setOnClickListener {
//            exportData()
//        }
//
//        buttonView.setOnClickListener {
//            viewData()
//        }
//
//        scanResultsReceiver = object : BroadcastReceiver() {
//            override fun onReceive(context: Context?, intent: Intent?) {
//                val success = intent?.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false) ?: false
//                if (success) {
//                    saveScanResults()
//                    scanCount++
//                    if (scanCount < 30) {
//                        textViewStatus.text = "측정 중 ${scanCount}/30"
//                        handler.postDelayed({ startWifiScan() }, 2000)
//                    } else {
//                        textViewStatus.text = "측정 완료"
//                    }
//                } else {
//                    Toast.makeText(this@MainActivity, "WiFi 스캔 실패", Toast.LENGTH_SHORT).show()
//                }
//            }
//        }
//
//        registerReceiver(scanResultsReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
//    }
//
//    private fun startRepeatedScan() {
//        startWifiScan()
//    }
//
//    private fun viewData() {
//        val db = databaseHelper.readableDatabase
//        val cursor = db.rawQuery("SELECT * FROM rssi_data", null)
//        val builder = StringBuilder()
//        while (cursor.moveToNext()) {
//            builder.append("SSID: ${cursor.getString(1)}, MAC: ${cursor.getString(2)}, RSSI: ${cursor.getString(3)}db, Time: ${cursor.getString(4)}, Location: ${cursor.getString(5)}\n\n")
//        }
//        cursor.close()
//
//        val alertDialog = AlertDialog.Builder(this)
//            .setTitle("저장된 데이터")
//            .setMessage(builder.toString())
//            .setPositiveButton("확인", null)
//            .create()
//        alertDialog.show()
//    }
//
//    private fun startWifiScan() {
//        if (!wifiManager.startScan()) {
//            Toast.makeText(this, "WiFi 스캔 시작 실패", Toast.LENGTH_SHORT).show()
//        }
//    }
//
//    private fun saveScanResults() {
//        val scanResults = wifiManager.scanResults
//        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
//        val location = editTextLocation.text.toString()
//        val db = databaseHelper.writableDatabase
//
//        for (result: ScanResult in scanResults) {
//            db.execSQL("INSERT INTO rssi_data (ssid, mac, rssi, time, location) VALUES (?, ?, ?, ?, ?)",
//                arrayOf(result.SSID, result.BSSID, result.level, timestamp, location))
//        }
//
//        Toast.makeText(this, "RSSI 데이터 저장 완료", Toast.LENGTH_SHORT).show()
//    }
//
//private fun exportData() {
//    val db = databaseHelper.readableDatabase
//    val cursor = db.rawQuery("SELECT * FROM rssi_data", null)
//
//    // 🔹 다운로드 폴더에 저장 (공식적인 경로)
//    val csvFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "wifi_rssi_log.csv")
//
//    try {
//        val writer = FileWriter(csvFile)
//        writer.append("SSID,MAC,RSSI,Time,Location\n")
//
//        while (cursor.moveToNext()) {
//            writer.append("${cursor.getString(1)},${cursor.getString(2)},${cursor.getInt(3)},${cursor.getString(4)},${cursor.getString(5)}\n")
//        }
//
//        writer.flush()
//        writer.close()
//        cursor.close()  // ✅ Cursor 닫기 추가
//
//        // 🔹 저장 후 미디어 스캐닝 (파일 탐색기에 즉시 반영)
//        MediaScannerConnection.scanFile(
//            this,
//            arrayOf(csvFile.absolutePath),
//            arrayOf("text/csv"),
//            null
//        )
//
//        Toast.makeText(this, "CSV 파일이 다운로드 폴더에 저장되었습니다:\n${csvFile.absolutePath}", Toast.LENGTH_LONG).show()
//    } catch (e: IOException) {
//        Toast.makeText(this, "파일 저장 오류: ${e.message}", Toast.LENGTH_LONG).show()
//    }
//}
//
//
//    override fun onDestroy() {
//        super.onDestroy()
//        unregisterReceiver(scanResultsReceiver)
//    }
//}