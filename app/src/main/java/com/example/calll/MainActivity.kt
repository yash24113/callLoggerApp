package com.example.calll

import android.Manifest
import android.app.DatePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Bundle
import android.provider.CallLog
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var loader: ProgressBar
    private lateinit var spinner: Spinner
    private lateinit var datePickerButton: Button
    private lateinit var refreshButton: AppCompatImageButton
    private lateinit var adapter: CallLogAdapter
    private lateinit var firestore: FirebaseFirestore

    private val callLogs = mutableListOf<CallLogEntry>()
    private val filteredCallLogs = mutableListOf<CallLogEntry>()
    private var selectedDate: String = ""
    private val PERMISSION_REQUEST_CODE = 101
    private val PREFS_NAME = "CallLogPrefs"
    private val gson = Gson()
    private lateinit var uniqueCollectionId: String
    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_PHONE_STATE
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        FirebaseApp.initializeApp(this)
        firestore = FirebaseFirestore.getInstance()
        uniqueCollectionId = getOrCreateUUID()

        recyclerView = findViewById(R.id.recycler_view)
        loader = findViewById(R.id.loader)
        spinner = findViewById(R.id.filter_spinner)
        datePickerButton = findViewById(R.id.date_picker_button)
        refreshButton = findViewById(R.id.refresh_button)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = CallLogAdapter(filteredCallLogs)
        recyclerView.adapter = adapter

        ArrayAdapter.createFromResource(
            this,
            R.array.call_status_filter,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = adapter
        }

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                filterCallLogs(spinner.selectedItem.toString(), selectedDate)
            }
            override fun onNothingSelected(parent: AdapterView<*>) {
                filterCallLogs("All", selectedDate)
            }
        }

        datePickerButton.setOnClickListener { showDatePicker() }
        refreshButton.setOnClickListener { fetchCallLogsFromDevice() }

        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        selectedDate = formatter.format(Calendar.getInstance().time)

        loadCachedLogs()
        filterCallLogs("All", selectedDate)
        checkPermissions()
    }

    override fun onResume() {
        super.onResume()
        fetchCallLogsFromDevice()
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, day ->
            val picked = Calendar.getInstance().apply { set(year, month, day) }
            val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            selectedDate = formatter.format(picked.time)
            filterCallLogs(spinner.selectedItem.toString(), selectedDate)
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun checkPermissions() {
        val missingPermissions = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        } else {
            fetchCallLogsFromDevice()
        }
    }

    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("This app requires access to your call logs to display your call history.")
            .setPositiveButton("OK") { _, _ ->
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CALL_LOG), PERMISSION_REQUEST_CODE)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(this, "Permission denied. The app cannot function properly.", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun fetchCallLogsFromDevice() {
        loader.visibility = View.VISIBLE
        loader.progress = 0

        val (simSerial, carrier) = getSimAndCarrierInfo()

        val cursor: Cursor? = contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            null,
            null,
            null,
            "${CallLog.Calls.DATE} DESC"
        )

        val existingLogs = loadCachedLogs()
        callLogs.clear()

        var count = 0
        val maxLogs = 50

        cursor?.use {
            val numberIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
            val durationIndex = it.getColumnIndex(CallLog.Calls.DURATION)
            val typeIndex = it.getColumnIndex(CallLog.Calls.TYPE)
            val dateIndex = it.getColumnIndex(CallLog.Calls.DATE)

            val total = minOf(it.count, maxLogs)

            while (it.moveToNext() && count < maxLogs) {
                val fromNumber = it.getString(numberIndex) ?: "Unknown"
                val duration = it.getString(durationIndex)?.toIntOrNull() ?: 0
                val type = it.getInt(typeIndex)
                val dateInMillis = it.getLong(dateIndex)

                val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(dateInMillis))
                val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(dateInMillis))

                val callStatus = when (type) {
                    CallLog.Calls.INCOMING_TYPE -> if (duration == 0) "Rejected" else "Incoming"
                    CallLog.Calls.OUTGOING_TYPE -> if (duration == 0) "Not Answered" else "Outgoing"
                    CallLog.Calls.MISSED_TYPE -> "Missed"
                    else -> "Not Answered"
                }

                val newLog = CallLogEntry(fromNumber, duration, callStatus, date, time, "Pending", simSerial, carrier)

                val existingLog = existingLogs.find {
                    it.fromNumber == newLog.fromNumber &&
                            it.date == newLog.date &&
                            it.time == newLog.time &&
                            it.duration == newLog.duration
                }

                callLogs.add(existingLog ?: newLog)

                count++
                loader.progress = (count * 100) / total
            }
        }

        saveLogsToPrefs(callLogs)
        loader.visibility = View.GONE
        filterCallLogs(spinner.selectedItem.toString(), selectedDate)

        if (isNetworkAvailable()) {
            sendCallLogsToFirebase()
        }
    }

    private fun sendCallLogsToFirebase() {
        val pendingLogs = callLogs.filter { it.serverStatus == "Pending" }
        val collection = firestore.collection("call_logs_$uniqueCollectionId")

        for (log in pendingLogs) {
            val docId = "${log.fromNumber}_${log.date}_${log.time}"
            collection.document(docId).set(log).addOnSuccessListener {
                val index = callLogs.indexOf(log)
                if (index >= 0) {
                    val updatedLog = log.copy(serverStatus = "Posted")
                    callLogs[index] = updatedLog

                    val filteredIndex = filteredCallLogs.indexOfFirst {
                        it.fromNumber == log.fromNumber &&
                                it.date == log.date &&
                                it.time == log.time &&
                                it.duration == log.duration
                    }
                    if (filteredIndex >= 0) {
                        filteredCallLogs[filteredIndex] = updatedLog
                    }

                    saveLogsToPrefs(callLogs)
                    adapter.notifyDataSetChanged()
                }
            }.addOnFailureListener {
                Toast.makeText(this, "Firebase error: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun filterCallLogs(filter: String, date: String) {
        filteredCallLogs.clear()
        val sortedLogs = callLogs.sortedByDescending {
            SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.getDefault()).parse("${it.date} ${it.time}")
        }

        for (log in sortedLogs) {
            val matchStatus = filter == "All" || log.callStatus.equals(filter, ignoreCase = true)
            val matchDate = log.date == date
            if (matchStatus && matchDate) {
                filteredCallLogs.add(log)
            }
        }

        adapter.notifyDataSetChanged()
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = cm.getNetworkCapabilities(cm.activeNetwork)
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    private fun saveLogsToPrefs(logs: List<CallLogEntry>) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit().putString("call_logs", gson.toJson(logs)).apply()
    }

    private fun loadCachedLogs(): MutableList<CallLogEntry> {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val json = prefs.getString("call_logs", null)
        val type = object : TypeToken<MutableList<CallLogEntry>>() {}.type
        val cached = gson.fromJson<MutableList<CallLogEntry>>(json, type) ?: mutableListOf()
        callLogs.clear()
        callLogs.addAll(cached)
        return cached
    }

    private fun getOrCreateUUID(): String {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        var uuid = prefs.getString("device_uuid", null)
        if (uuid == null) {
            uuid = UUID.randomUUID().toString()
            prefs.edit().putString("device_uuid", uuid).apply()
        }
        return uuid
    }

    private fun getSimAndCarrierInfo(): Pair<String?, String?> {
        val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val subscriptionManager = getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
        var simSerial: String? = null
        var carrier: String? = null
        try {
            val activeSubscriptionInfoList = subscriptionManager.activeSubscriptionInfoList
            if (!activeSubscriptionInfoList.isNullOrEmpty()) {
                val info: SubscriptionInfo = activeSubscriptionInfoList[0]
                simSerial = info.iccId
                carrier = info.carrierName?.toString()
            } else {
                simSerial = telephonyManager.simSerialNumber
                carrier = telephonyManager.networkOperatorName
            }
        } catch (e: SecurityException) {
            // Permissions not granted
        }
        return Pair(simSerial, carrier)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            fetchCallLogsFromDevice()
        } else {
            Toast.makeText(this, "Permission denied.", Toast.LENGTH_SHORT).show()
        }
    }
}
