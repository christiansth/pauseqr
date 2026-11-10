package de.pauseqr.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SCAN = "extra_scan"
    }

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var toolbar: MaterialToolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawerLayout)
        navView = findViewById(R.id.navView)
        toolbar = findViewById(R.id.toolbar)

        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.app_name, R.string.app_name
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        toggle.drawerArrowDrawable.color = Color.WHITE

        toolbar.menu.findItem(R.id.action_about)?.icon?.setTint(Color.WHITE)

        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_about) {
                navView.setCheckedItem(R.id.nav_about)
                showTopLevelFragment(AboutFragment())
                true
            } else false
        }

        navView.setNavigationItemSelectedListener { item ->
            item.isChecked = true
            selectNavItem(item.itemId)
            drawerLayout.closeDrawers()
            true
        }

        if (savedInstanceState == null) {
            if (intent.getBooleanExtra(EXTRA_SCAN, false)) {
                showDashboardAndScan()
            } else {
                navView.setCheckedItem(R.id.nav_overview)
                showTopLevelFragment(DashboardFragment())
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_SCAN, false)) {
            showDashboardAndScan()
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        when {
            drawerLayout.isDrawerOpen(GravityCompat.START) -> drawerLayout.closeDrawers()
            supportFragmentManager.backStackEntryCount > 0 -> supportFragmentManager.popBackStack()
            else -> super.onBackPressed()
        }
    }

    private fun selectNavItem(id: Int) {
        when (id) {
            R.id.nav_overview -> showTopLevelFragment(DashboardFragment())
            R.id.nav_apps -> startActivity(Intent(this, AppSelectActivity::class.java))
            R.id.nav_qr -> showTopLevelFragment(QrFragment())
            R.id.nav_stats -> showTopLevelFragment(StatsFragment())
            R.id.nav_settings -> showTopLevelFragment(SettingsFragment())
            R.id.nav_help -> showTopLevelFragment(HelpFragment())
            R.id.nav_about -> showTopLevelFragment(AboutFragment())
        }
    }

    /** Zeigt einen Drawer-Top-Level-Bildschirm und verwirft dabei einen evtl. offenen Push-Stack. */
    private fun showTopLevelFragment(fragment: Fragment) {
        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    /** Schiebt einen Bildschirm auf den Back-Stack (z. B. Dashboard → QR-Code verwalten → Erstellen). */
    fun pushFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .addToBackStack(null)
            .commit()
    }

    fun popFragment() {
        supportFragmentManager.popBackStack()
    }

    private fun showDashboardAndScan() {
        val fragment = DashboardFragment()
        navView.setCheckedItem(R.id.nav_overview)
        supportFragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commitNow()
        fragment.startScanFromHost()
    }
}
