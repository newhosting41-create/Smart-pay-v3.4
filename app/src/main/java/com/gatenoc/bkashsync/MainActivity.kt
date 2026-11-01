package com.gatenoc.bkashsync

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var currentTab = "DASHBOARD"
    private var selectedTxFilter = "ALL"
    private var searchQuery = ""

    private lateinit var rootFrame: FrameLayout

    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (AppStorage.isLoggedIn(this@MainActivity)) {
                renderLoggedInScreen()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        rootFrame = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#050811"))
        }
        setContentView(rootFrame)

        checkAndRequestPermissions()
        promptBatteryOptimizationIfNeeded()

        if (AppStorage.isLoggedIn(this)) {
            SyncForegroundService.start(this, retryNow = true)
            renderLoggedInScreen()
        } else {
            renderLoginScreen()
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(SmsReceiver.ACTION_UI_REFRESH)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(refreshReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(refreshReceiver, filter)
        }
        if (AppStorage.isLoggedIn(this)) {
            renderLoggedInScreen()
        }
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(refreshReceiver) } catch (_: Exception) {}
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return flat.contains(packageName)
    }

    private fun boxBg(colorHex: String, borderHex: String? = null, radius: Float = 36f): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor(colorHex))
            cornerRadius = radius
            if (borderHex != null) {
                setStroke(2, Color.parseColor(borderHex))
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun renderLoginScreen() {
        rootFrame.removeAllViews()

        val scroll = ScrollView(this).apply {
            isFillViewport = true
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 80, 48, 80)
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = boxBg("#0D1322", "#1E293B", 44f)
            setPadding(52, 64, 52, 64)
        }

        val logoCircle = TextView(this).apply {
            text = "SYNC"
            setTextColor(Color.WHITE)
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = boxBg("#0284C7", null, 100f)
            layoutParams = LinearLayout.LayoutParams(120, 120).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = 28
            }
        }

        val title = TextView(this).apply {
            text = "Device Gateway Login"
            setTextColor(Color.parseColor("#F8FAFC"))
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "Enter your Server Sync Endpoint URL and Device Pairing Token to initialize gateway monitoring."
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 40)
        }

        val urlLabel = TextView(this).apply {
            text = "SYNC ENDPOINT URL"
            setTextColor(Color.parseColor("#64748B"))
            textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
        }

        val urlInput = EditText(this).apply {
            hint = "https://yourdomain.com/payment/bkash_sms_sync"
            setHintTextColor(Color.parseColor("#334155"))
            setTextColor(Color.WHITE)
            textSize = 13f
            inputType = InputType.TYPE_TEXT_VARIATION_URI
            background = boxBg("#050811", "#1E293B", 24f)
            setPadding(32, 28, 32, 28)
            setText(AppStorage.getEndpoint(this@MainActivity))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8; bottomMargin = 28 }
        }

        val keyLabel = TextView(this).apply {
            text = "DEVICE PAIRING TOKEN"
            setTextColor(Color.parseColor("#64748B"))
            textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
        }

        val keyInput = EditText(this).apply {
            hint = "Paste 24-character token from admin panel"
            setHintTextColor(Color.parseColor("#334155"))
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 13f
            background = boxBg("#050811", "#1E293B", 24f)
            setPadding(32, 28, 32, 28)
            setText(AppStorage.getDeviceKey(this@MainActivity))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8; bottomMargin = 24 }
        }

        val agreeCheck = CheckBox(this).apply {
            text = "I authorize official bKash payment SMS parsing and secure transmission."
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 11f
            isChecked = true
        }

        val connectBtn = Button(this).apply {
            text = "Initialize Connection"
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            background = boxBg("#0284C7", null, 24f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 32 }
        }

        connectBtn.setOnClickListener {
            val ep = urlInput.text.toString().trim()
            val dk = keyInput.text.toString().trim()
            if (ep.isBlank() || !ep.startsWith("http")) {
                Toast.makeText(this, "Enter valid Sync Endpoint URL", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (dk.length < 16) {
                Toast.makeText(this, "Device Key must be at least 16 characters", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (!agreeCheck.isChecked) {
                Toast.makeText(this, "Please check the agreement box", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            connectBtn.isEnabled = false
            connectBtn.text = "Establishing Connection..."

            uiScope.launch {
                val res = withContext(Dispatchers.IO) {
                    ApiClient.pingServer(ep, dk)
                }
                connectBtn.isEnabled = true
                connectBtn.text = "Initialize Connection"

                if (res.success) {
                    AppStorage.saveLoginConfig(this@MainActivity, ep, dk)
                    SyncForegroundService.start(this@MainActivity, retryNow = true)
                    Toast.makeText(this@MainActivity, "Device Connected Successfully!", Toast.LENGTH_SHORT).show()
                    renderLoggedInScreen()
                } else {
                    Toast.makeText(this@MainActivity, "Connection Failed: ${res.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        card.addView(logoCircle)
        card.addView(title)
        card.addView(subtitle)
        card.addView(urlLabel)
        card.addView(urlInput)
        card.addView(keyLabel)
        card.addView(keyInput)
        card.addView(agreeCheck)
        card.addView(connectBtn)

        container.addView(card)
        scroll.addView(container)
        rootFrame.addView(scroll)
    }

    @SuppressLint("SetTextI18n")
    private fun renderLoggedInScreen() {
        rootFrame.removeAllViews()

        val mainVertical = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#0D1322"))
            setPadding(36, 32, 36, 32)
        }

        val topTitle = TextView(this).apply {
            text = when (currentTab) {
                "DASHBOARD" -> "Overview Dashboard"
                "TRANSACTIONS" -> "Transaction Records"
                "MANUAL" -> "Manual SMS Intake"
                else -> "Gateway Settings"
            }
            setTextColor(Color.parseColor("#F8FAFC"))
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val syncTopBtn = TextView(this).apply {
            text = "Sync Now"
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
            background = boxBg("#050811", "#0284C7", 18f)
            setPadding(22, 12, 22, 12)
            setOnClickListener {
                SyncForegroundService.start(this@MainActivity, retryNow = true)
                Toast.makeText(this@MainActivity, "Scanning Inbox & Syncing...", Toast.LENGTH_SHORT).show()
            }
        }

        topBar.addView(topTitle)
        topBar.addView(syncTopBtn)
        mainVertical.addView(topBar)

        val contentScroll = ScrollView(this).apply {
            isFillViewport = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val contentContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 28, 32, 44)
        }

        when (currentTab) {
            "DASHBOARD" -> buildDashboardView(contentContainer)
            "TRANSACTIONS" -> buildTransactionsView(contentContainer)
            "MANUAL" -> buildManualEntryView(contentContainer)
            "SETTINGS" -> buildSettingsView(contentContainer)
        }

        contentScroll.addView(contentContainer)
        mainVertical.addView(contentScroll)

        val bottomNav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#0D1322"))
            setPadding(12, 12, 12, 12)
        }

        val tabs = listOf(
            "DASHBOARD" to "Dashboard",
            "TRANSACTIONS" to "Records",
            "MANUAL" to "Manual",
            "SETTINGS" to "Settings"
        )

        for ((tabKey, tabLabel) in tabs) {
            val active = (currentTab == tabKey)
            val tabBtn = TextView(this).apply {
                text = tabLabel
                gravity = Gravity.CENTER
                textSize = 11f
                setTypeface(typeface, if (active) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(if (active) Color.parseColor("#38BDF8") else Color.parseColor("#64748B"))
                background = if (active) boxBg("#050811", "#0284C7", 20f) else null
                setPadding(10, 16, 10, 16)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = 4
                    marginEnd = 4
                }
                setOnClickListener {
                    currentTab = tabKey
                    renderLoggedInScreen()
                }
            }
            bottomNav.addView(tabBtn)
        }

        mainVertical.addView(bottomNav)
        rootFrame.addView(mainVertical)
    }

    @SuppressLint("SetTextI18n")
    private fun buildDashboardView(parent: LinearLayout) {
        val records = AppStorage.getRecords(this)
        val totalCount = records.size
        val successCount = records.count { it.status == "Success" || it.status == "Used" }
        val pendingCount = records.count { it.status == "Pending" }
        val failedCount = records.count { it.status == "Failed" || it.status == "Flagged" }

        val smsPermGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

        if (!smsPermGranted) {
            val permBanner = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = boxBg("#450A0A", "#EF4444", 24f)
                setPadding(28, 24, 28, 24)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 20 }
            }
            permBanner.addView(TextView(this).apply {
                text = "SMS Permission Restricted"
                setTextColor(Color.WHITE)
                textSize = 13f
                setTypeface(typeface, Typeface.BOLD)
            })
            permBanner.addView(TextView(this).apply {
                text = "Open App Info > Tap top-right menu (3 dots) > Select 'Allow restricted settings' > Go to Permissions > Enable SMS access."
                setTextColor(Color.parseColor("#FECACA"))
                textSize = 11f
                setPadding(0, 4, 0, 12)
            })
            permBanner.addView(Button(this).apply {
                text = "Open App Settings"
                setTextColor(Color.WHITE)
                textSize = 12f
                background = boxBg("#DC2626", null, 16f)
                setOnClickListener {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                    startActivity(intent)
                }
            })
            parent.addView(permBanner)
        }

        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = boxBg("#064E3B", "#10B981", 24f)
            setPadding(28, 24, 28, 24)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 20 }
        }
        statusCard.addView(TextView(this).apply {
            text = "Gateway Online & Operational"
            setTextColor(Color.parseColor("#A7F3D0"))
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
        })
        statusCard.addView(TextView(this).apply {
            text = "Active Filter: Official 'bKash' & '16216' messages only."
            setTextColor(Color.parseColor("#D1FAE5"))
            textSize = 11f
            setPadding(0, 4, 0, 0)
        })
        parent.addView(statusCard)

        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 16 }
        }
        row1.addView(makeStatCard("TOTAL RECORDS", totalCount.toString(), "#38BDF8", true))
        row1.addView(makeStatCard("SUCCESSFUL", successCount.toString(), "#22C55E", false))
        parent.addView(row1)

        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 24 }
        }
        row2.addView(makeStatCard("PENDING QUEUE", pendingCount.toString(), "#FACC15", true))
        row2.addView(makeStatCard("FAILED / FLAGGED", failedCount.toString(), "#F87171", false))
        parent.addView(row2)

        val quickRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 28 }
        }
        val retryAllBtn = Button(this).apply {
            text = "Retry Pending ($pendingCount)"
            setTextColor(Color.WHITE)
            textSize = 12f
            background = boxBg("#0284C7", null, 20f)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = 8 }
            setOnClickListener {
                SyncForegroundService.start(this@MainActivity, retryNow = true)
                Toast.makeText(this@MainActivity, "Retrying pending queue...", Toast.LENGTH_SHORT).show()
            }
        }
        val addManualBtn = Button(this).apply {
            text = "Manual Entry"
            setTextColor(Color.WHITE)
            textSize = 12f
            background = boxBg("#7C3AED", null, 20f)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = 8 }
            setOnClickListener {
                currentTab = "MANUAL"
                renderLoggedInScreen()
            }
        }
        quickRow.addView(retryAllBtn)
        quickRow.addView(addManualBtn)
        parent.addView(quickRow)

        parent.addView(TextView(this).apply {
            text = "Recent Transactions"
            setTextColor(Color.parseColor("#F8FAFC"))
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, 12)
        })

        if (records.isEmpty()) {
            parent.addView(TextView(this).apply {
                text = "No transaction records found"
                setTextColor(Color.parseColor("#64748B"))
                textSize = 12f
                setPadding(0, 20, 0, 20)
            })
        } else {
            records.take(10).forEach { item ->
                parent.addView(buildSmsItemCard(item))
            }
        }
    }

    private fun makeStatCard(label: String, value: String, colorHex: String, isLeft: Boolean): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = boxBg("#0D1322", "#1E293B", 24f)
            setPadding(28, 24, 28, 24)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (isLeft) rightMargin = 8 else leftMargin = 8
            }
            addView(TextView(this@MainActivity).apply {
                text = value
                setTextColor(Color.parseColor(colorHex))
                textSize = 20f
                setTypeface(typeface, Typeface.BOLD)
            })
            addView(TextView(this@MainActivity).apply {
                text = label
                setTextColor(Color.parseColor("#64748B"))
                textSize = 10f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, 4, 0, 0)
            })
        }
    }

    @SuppressLint("SetTextI18n")
    private fun buildTransactionsView(parent: LinearLayout) {
        val searchInput = EditText(this).apply {
            hint = "Search TrxID, sender, amount..."
            setHintTextColor(Color.parseColor("#334155"))
            setTextColor(Color.WHITE)
            textSize = 13f
            background = boxBg("#0D1322", "#1E293B", 20f)
            setPadding(28, 22, 28, 22)
            setText(searchQuery)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 16 }
        }

        val listWrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        fun populateFilteredList() {
            listWrapper.removeAllViews()
            val all = AppStorage.getRecords(this)
            val filtered = all.filter { item ->
                val matchesTab = when (selectedTxFilter) {
                    "Pending" -> item.status == "Pending"
                    "Success" -> item.status == "Success" || item.status == "Used"
                    "Failed" -> item.status == "Failed" || item.status == "Flagged"
                    else -> true
                }
                val q = searchQuery.trim().lowercase()
                val matchesQuery = q.isEmpty() ||
                        item.trxId.lowercase().contains(q) ||
                        item.senderNumber.lowercase().contains(q) ||
                        item.amountBdt.toString().contains(q) ||
                        item.message.lowercase().contains(q)
                matchesTab && matchesQuery
            }

            if (filtered.isEmpty()) {
                listWrapper.addView(TextView(this).apply {
                    text = "No records found matching criteria"
                    setTextColor(Color.parseColor("#64748B"))
                    textSize = 12f
                    setPadding(8, 24, 8, 24)
                })
            } else {
                filtered.forEach { item ->
                    listWrapper.addView(buildSmsItemCard(item))
                }
            }
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString() ?: ""
                populateFilteredList()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        parent.addView(searchInput)

        val filterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 20 }
        }
        val filters = listOf("ALL", "Pending", "Success", "Failed")
        for (f in filters) {
            val active = (selectedTxFilter == f)
            val chip = TextView(this).apply {
                text = f
                gravity = Gravity.CENTER
                textSize = 11f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(if (active) Color.WHITE else Color.parseColor("#64748B"))
                background = boxBg(if (active) "#0284C7" else "#0D1322", "#1E293B", 18f)
                setPadding(12, 14, 12, 14)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = 6
                }
                setOnClickListener {
                    selectedTxFilter = f
                    renderLoggedInScreen()
                }
            }
            filterRow.addView(chip)
        }
        parent.addView(filterRow)

        parent.addView(listWrapper)
        populateFilteredList()
    }

    @SuppressLint("SetTextI18n")
    private fun buildSmsItemCard(item: SmsRecord): View {
        val statusColor = when (item.status) {
            "Used" -> "#10B981"
            "Success" -> "#38BDF8"
            "Pending" -> "#FACC15"
            else -> "#F87171"
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = boxBg("#0D1322", "#1E293B", 24f)
            setPadding(28, 22, 28, 22)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16 }
            setOnClickListener { showTransactionDetailsDialog(item) }
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        headerRow.addView(TextView(this).apply {
            text = "TrxID: ${item.trxId}  |  Tk ${String.format("%.2f", item.amountBdt)}"
            setTextColor(Color.parseColor("#F8FAFC"))
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        headerRow.addView(TextView(this).apply {
            text = item.status.uppercase()
            setTextColor(Color.parseColor(statusColor))
            textSize = 10f
            setTypeface(typeface, Typeface.BOLD)
            background = boxBg("#050811", statusColor, 12f)
            setPadding(14, 4, 14, 4)
        })

        val metaRow = TextView(this).apply {
            text = "Sender: ${item.senderNumber}  •  ${item.dateText} (${item.source})"
            setTextColor(Color.parseColor("#64748B"))
            textSize = 11f
            setPadding(0, 6, 0, 6)
        }

        val msgRow = TextView(this).apply {
            text = item.message
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 11f
            maxLines = 2
        }

        card.addView(headerRow)
        card.addView(metaRow)
        card.addView(msgRow)

        if (item.serverNote.isNotBlank()) {
            card.addView(TextView(this).apply {
                text = "Note: ${item.serverNote}"
                setTextColor(Color.parseColor(statusColor))
                textSize = 11f
                setPadding(0, 4, 0, 0)
            })
        }

        return card
    }

    private fun showTransactionDetailsDialog(item: SmsRecord) {
        val details = StringBuilder().apply {
            append("Transaction ID: ${item.trxId}\n")
            append("Amount: Tk ${String.format("%.2f", item.amountBdt)}\n")
            append("From Number: ${item.senderNumber}\n")
            append("SMS Time: ${item.smsTimeStr}\n")
            append("Recorded: ${item.dateText}\n")
            append("Source: ${item.source}\n")
            append("Status: ${item.status}\n")
            if (item.serverNote.isNotBlank()) append("Server Note: ${item.serverNote}\n")
            append("\nFull SMS Message:\n${item.message}")
        }.toString()

        AlertDialog.Builder(this)
            .setTitle("Transaction Details")
            .setMessage(details)
            .setPositiveButton("Retry Upload") { _, _ ->
                AppStorage.updateStatusByIdOrTrx(this, item.id, "Pending", "Retrying...")
                SmsReceiver.uploadRecordImmediately(applicationContext, item)
                Toast.makeText(this, "Retrying upload for ${item.trxId}...", Toast.LENGTH_SHORT).show()
                renderLoggedInScreen()
            }
            .setNeutralButton("Copy Message") { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("SMS", item.message))
                Toast.makeText(this, "Message copied", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Delete") { _, _ ->
                AppStorage.deleteRecord(this, item.id)
                Toast.makeText(this, "Transaction deleted", Toast.LENGTH_SHORT).show()
                renderLoggedInScreen()
            }
            .show()
    }

    @SuppressLint("SetTextI18n")
    private fun buildManualEntryView(parent: LinearLayout) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = boxBg("#0D1322", "#1E293B", 28f)
            setPadding(32, 32, 32, 32)
        }

        card.addView(TextView(this).apply {
            text = "Manual SMS Intake"
            setTextColor(Color.parseColor("#F8FAFC"))
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
        })

        card.addView(TextView(this).apply {
            text = "Paste the complete bKash payment SMS below for manual verification and server synchronization."
            setTextColor(Color.parseColor("#64748B"))
            textSize = 11f
            setPadding(0, 4, 0, 20)
        })

        val smsInput = EditText(this).apply {
            hint = "Paste payment SMS message..."
            setHintTextColor(Color.parseColor("#334155"))
            setTextColor(Color.WHITE)
            textSize = 12f
            minLines = 5
            gravity = Gravity.TOP or Gravity.START
            background = boxBg("#050811", "#1E293B", 20f)
            setPadding(24, 20, 24, 20)
        }
        card.addView(smsInput)

        val pasteClipBtn = Button(this).apply {
            text = "Paste from Clipboard"
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 12f
            background = boxBg("#050811", "#0284C7", 20f)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 16 }
            setOnClickListener {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val text = cm.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                if (text.isNotBlank()) {
                    smsInput.setText(text)
                } else {
                    Toast.makeText(this@MainActivity, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                }
            }
        }
        card.addView(pasteClipBtn)

        val submitBtn = Button(this).apply {
            text = "Verify & Upload Record"
            setTextColor(Color.WHITE)
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            background = boxBg("#16A34A", null, 20f)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 16 }
            setOnClickListener {
                val raw = smsInput.text.toString().trim()
                if (raw.isEmpty()) {
                    Toast.makeText(this@MainActivity, "Please enter SMS text", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val parsed = SmsParser.parseIncomingOrManual("bKash", raw, "Manual")
                if (parsed == null) {
                    Toast.makeText(this@MainActivity, "Invalid bKash SMS format! Must match received payment SMS.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                AppStorage.insertIfNew(this@MainActivity, parsed)
                SmsReceiver.uploadRecordImmediately(applicationContext, parsed)
                smsInput.setText("")
                Toast.makeText(this@MainActivity, "Data added & uploading TrxID: ${parsed.trxId}", Toast.LENGTH_LONG).show()
                currentTab = "TRANSACTIONS"
                renderLoggedInScreen()
            }
        }
        card.addView(submitBtn)

        parent.addView(card)
    }

    @SuppressLint("SetTextI18n")
    private fun buildSettingsView(parent: LinearLayout) {
        val guideCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = boxBg("#0D1322", "#CA8A04", 26f)
            setPadding(30, 26, 30, 26)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 20 }
        }
        guideCard.addView(TextView(this).apply {
            text = "System Protection & Permissions"
            setTextColor(Color.parseColor("#FDE047"))
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
        })
        guideCard.addView(TextView(this).apply {
            text = "1. If Android blocks SMS permission: Open App Info > Top-right menu (3 dots) > 'Allow restricted settings' > Permissions > SMS > Allow\n2. Enable Notification Listener (allows instant wake-up when incoming SMS arrives)\n3. Set Battery Usage to Unrestricted for background reliability"
            setTextColor(Color.parseColor("#E2E8F0"))
            textSize = 11f
            setPadding(0, 8, 0, 16)
        })
        guideCard.addView(Button(this).apply {
            text = "Open App Info Settings"
            setTextColor(Color.WHITE)
            textSize = 12f
            background = boxBg("#DC2626", null, 18f)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 12 }
            setOnClickListener {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
        })
        guideCard.addView(Button(this).apply {
            text = "Enable Notification Listener"
            setTextColor(Color.WHITE)
            textSize = 12f
            background = boxBg("#0284C7", null, 18f)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 12 }
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        })
        guideCard.addView(Button(this).apply {
            text = "Disable Battery Optimization"
            setTextColor(Color.WHITE)
            textSize = 12f
            background = boxBg("#CA8A04", null, 18f)
            setOnClickListener { promptBatteryOptimizationIfNeeded(forceOpen = true) }
        })
        parent.addView(guideCard)

        val storageCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = boxBg("#0D1322", "#1E293B", 26f)
            setPadding(30, 26, 30, 26)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 20 }
        }
        storageCard.addView(TextView(this).apply {
            text = "Storage Management"
            setTextColor(Color.parseColor("#F8FAFC"))
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, 14)
        })

        val clearButtons = listOf(
            "Clear Pending Records" to "Pending",
            "Clear Success Records" to "Success",
            "Clear Failed Records" to "Failed",
            "Clear All Records" to "ALL"
        )
        for ((btnTitle, typeKey) in clearButtons) {
            storageCard.addView(Button(this).apply {
                text = btnTitle
                setTextColor(Color.WHITE)
                textSize = 12f
                background = boxBg("#050811", "#334155", 18f)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 12 }
                setOnClickListener {
                    AppStorage.clearByCategory(this@MainActivity, typeKey)
                    Toast.makeText(this@MainActivity, "$btnTitle completed", Toast.LENGTH_SHORT).show()
                    renderLoggedInScreen()
                }
            })
        }
        parent.addView(storageCard)

        val accountCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = boxBg("#0D1322", "#1E293B", 26f)
            setPadding(30, 26, 30, 26)
        }
        accountCard.addView(TextView(this).apply {
            text = "Gateway Configuration"
            setTextColor(Color.parseColor("#F8FAFC"))
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
        })
        accountCard.addView(TextView(this).apply {
            text = "URL: ${AppStorage.getEndpoint(this@MainActivity)}\nToken: ${AppStorage.getDeviceKey(this@MainActivity)}"
            setTextColor(Color.parseColor("#64748B"))
            textSize = 11f
            setPadding(0, 6, 0, 20)
        })
        accountCard.addView(Button(this).apply {
            text = "Disconnect / Logout"
            setTextColor(Color.WHITE)
            textSize = 12f
            background = boxBg("#DC2626", null, 20f)
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Logout Gateway")
                    .setMessage("Are you sure you want to disconnect this device?")
                    .setPositiveButton("Logout") { _, _ ->
                        AppStorage.logout(this@MainActivity)
                        SyncForegroundService.stop(this@MainActivity)
                        renderLoginScreen()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        })
        parent.addView(accountCard)
    }

    private fun checkAndRequestPermissions() {
        val perms = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 101)
        }
    }

    @SuppressLint("BatteryLife")
    private fun promptBatteryOptimizationIfNeeded(forceOpen: Boolean = false) {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } else if (forceOpen) {
                Toast.makeText(this, "Battery unrestricted mode is already enabled!", Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {
            if (forceOpen) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }

    override fun onDestroy() {
        uiScope.cancel()
        super.onDestroy()
    }
}