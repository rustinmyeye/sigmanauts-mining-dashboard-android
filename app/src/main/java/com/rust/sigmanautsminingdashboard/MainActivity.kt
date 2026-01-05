package com.rust.sigmanautsminingdashboard

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.rust.sigmanautsminingdashboard.R
import org.json.JSONException
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var fabMenu: FloatingActionButton
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<LinearLayout>
    private lateinit var btnHome: Button
    private lateinit var btnAddWallet: Button
    private lateinit var rvWallets: RecyclerView

    private val homeUrl = "https://my.ergoport.dev/cgi-bin/mining/mining_SR_mts.html"
    private val walletsPrefKey = "wallets_map_v3" // Using a new key to avoid data type conflicts

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        fabMenu = findViewById(R.id.fabMenu)
        val bottomSheet: LinearLayout = findViewById(R.id.bottom_sheet)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        btnHome = findViewById(R.id.btnHome)
        btnAddWallet = findViewById(R.id.btnAddWallet)
        rvWallets = findViewById(R.id.rvWallets)

        webView.settings.javaScriptEnabled = true
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url != null) {
                    if (url.startsWith(homeUrl)) {
                        view?.loadUrl(url)
                        return false // Let WebView handle it
                    } else {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(intent)
                        return true // We've handled it
                    }
                }
                return true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (url != null && url.startsWith(homeUrl)) {
                    val prefs = getSharedPreferences("WebViewPrefs", Context.MODE_PRIVATE)
                    val editor = prefs.edit()
                    editor.putString("lastUrl", url)
                    editor.apply()
                }
            }
        }

        fabMenu.setOnClickListener {
            if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            } else {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            }
        }

        btnHome.setOnClickListener {
            webView.loadUrl(homeUrl)
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }

        btnAddWallet.setOnClickListener {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            showAddWalletDialog()
        }

        rvWallets.layoutManager = LinearLayoutManager(this)

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            val prefs = getSharedPreferences("WebViewPrefs", Context.MODE_PRIVATE)
            val lastUrl = prefs.getString("lastUrl", homeUrl)
            webView.loadUrl(lastUrl ?: homeUrl)
        }
        updateWalletList()
    }

    private fun showAddWalletDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_wallet, null)
        val etWalletName = dialogView.findViewById<EditText>(R.id.etWalletName)
        val etWalletAddress = dialogView.findViewById<EditText>(R.id.etWalletAddress)

        MaterialAlertDialogBuilder(this)
            .setTitle("Add Wallet")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val walletName = etWalletName.text.toString()
                val walletAddress = etWalletAddress.text.toString()
                if (walletName.isNotEmpty() && walletAddress.isNotEmpty()) {
                    saveWallet(walletName, walletAddress)
                    updateWalletList()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveWallet(name: String, address: String) {
        val wallets = getSavedWallets().toMutableMap()
        wallets[address] = name
        saveWallets(wallets)
    }

    private fun getSavedWallets(): Map<String, String> {
        val prefs = getSharedPreferences("WalletPrefs", Context.MODE_PRIVATE)
        val walletsJson = prefs.getString(walletsPrefKey, null)
        if (walletsJson != null) {
            try {
                val json = JSONObject(walletsJson)
                val wallets = mutableMapOf<String, String>()
                for (key in json.keys()) {
                    wallets[key] = json.getString(key)
                }
                return wallets
            } catch (e: JSONException) {
                return emptyMap()
            }
        }
        return emptyMap()
    }

    private fun saveWallets(wallets: Map<String, String>) {
        val prefs = getSharedPreferences("WalletPrefs", Context.MODE_PRIVATE)
        val json = JSONObject(wallets).toString()
        prefs.edit().putString(walletsPrefKey, json).apply()
    }

    private fun removeWallet(address: String) {
        val wallets = getSavedWallets().toMutableMap()
        wallets.remove(address)
        saveWallets(wallets)
        updateWalletList()
    }

    private fun updateWalletList() {
        val wallets = getSavedWallets()
        rvWallets.adapter = WalletAdapter(wallets, {
            webView.loadUrl("$homeUrl?wallet=$it")
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }, {
            MaterialAlertDialogBuilder(this)
                .setTitle("Remove Wallet")
                .setMessage("Are you sure you want to remove wallet \"${wallets[it]}\"?")
                .setPositiveButton("Remove") { _, _ ->
                    removeWallet(it)
                }
                .setNegativeButton("Cancel", null)
                .show()
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onBackPressed() {
        if (bottomSheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN) {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        } else if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    class WalletAdapter(
        private val wallets: Map<String, String>,
        private val onLaunch: (String) -> Unit,
        private val onDelete: (String) -> Unit
    ) : RecyclerView.Adapter<WalletAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvWalletName: TextView = view.findViewById(R.id.tvWalletName)
            val tvWalletAddress: TextView = view.findViewById(R.id.tvWalletAddress)
            val ibDelete: ImageButton = view.findViewById(R.id.ibDelete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.wallet_list_item, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val address = wallets.keys.elementAt(position)
            val name = wallets[address]
            holder.tvWalletName.text = name
            holder.tvWalletAddress.text = address
            holder.itemView.setOnClickListener { onLaunch(address) }
            holder.ibDelete.setOnClickListener { onDelete(address) }
        }

        override fun getItemCount() = wallets.size
    }
}