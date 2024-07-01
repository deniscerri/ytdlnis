package com.deniscerri.ytdl.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.PopupMenu
import androidx.core.view.forEach
import androidx.core.view.get
import androidx.core.view.isGone
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.navigation.NavigationView
import java.util.*


object NavbarUtil {

    lateinit var settings : SharedPreferences

    fun init(context: Context){
        settings = PreferenceManager.getDefaultSharedPreferences(context)
    }

    private var navItems = mapOf(
        "Home" to R.id.homeFragment,
        "History" to R.id.historyFragment,
        "Downloads" to R.id.downloadQueueMainFragment,
        "Terminal" to R.id.terminalActivity,
        "More" to R.id.moreFragment
    )

    fun getStartFragmentId(context: Context) : Int {
        val pref = settings.getString("start_destination", "")!!
        val items = getDefaultNavBarItems(context)
        return if (pref == "") {
            R.id.homeFragment
        }else {
            items.firstOrNull { it.itemId == navItems[pref] }!!.itemId
        }
    }

    fun setNavBarItems(items: List<MenuItem>, context: Context) {
        val prefString = mutableListOf<String>()
        val defaultNavBarItems = getDefaultNavBarItems(context)
        items.forEach { newItem ->
            val index = defaultNavBarItems.indexOfFirst { newItem.itemId == it.itemId }
            prefString.add(if (newItem.isVisible) index.toString() else "-$index")
        }
        settings.edit().putString("navigation_bar", prefString.joinToString(",")).apply()
    }

    fun setStartFragment(itemId: Int) {
        settings.edit().putString("start_destination", navItems.filter { it.value == itemId }.keys.first()).apply()
    }

    fun getNavBarItems(context: Context): List<MenuItem> {
        val prefItems = try {
            getNavBarPrefs()
        } catch (e: Exception) {
            Log.e("fail to parse nav items", e.toString())
            return getDefaultNavBarItems(context)
        }
        val p = PopupMenu(context, null)
        MenuInflater(context).inflate(R.menu.bottom_nav_menu, p.menu)

        if (prefItems.size == p.menu.size()) {
            val navBarItems = mutableListOf<MenuItem>()
            prefItems.forEach {
                navBarItems.add(
                    p.menu[it.replace("-", "").toInt()].apply {
                        this.isVisible = !it.contains("-")
                    }
                )
            }
            return navBarItems
        }
        return getDefaultNavBarItems(context)
    }

    private fun getNavBarPrefs(): List<String> {
        return settings
            .getString("navigation_bar", "Home,History,More")!!
            .split(",")
    }

    private fun getDefaultNavBarItems(context: Context): List<MenuItem> {
        val p = PopupMenu(context, null)
        MenuInflater(context).inflate(R.menu.bottom_nav_menu, p.menu)
        val navBarItems = mutableListOf<MenuItem>()
        p.menu.forEach {
            navBarItems.add(it)
        }
        return navBarItems
    }

    fun NavigationBarView.applyNavBarStyle(): Int {
        val labelVisibilityMode = when (
            settings.getString("label_visibility", "always")
        ) {
            "always" -> NavigationBarView.LABEL_VISIBILITY_LABELED
            "selected" -> NavigationBarView.LABEL_VISIBILITY_SELECTED
            "never" -> NavigationBarView.LABEL_VISIBILITY_UNLABELED
            else -> NavigationBarView.LABEL_VISIBILITY_AUTO
        }
        this.labelVisibilityMode = labelVisibilityMode

        val navBarItems = getNavBarItems(this.context)

        val menuItems = mutableListOf<MenuItem>()
        // remove the old items
        navBarItems.forEach {
            menuItems.add(
                this.menu.findItem(it.itemId)
            )
            this.menu.removeItem(it.itemId)
        }

        navBarItems.forEach { navBarItem ->
            if (navBarItem.isVisible) {
                val menuItem = menuItems.first { it.itemId == navBarItem.itemId }

                this.menu.add(
                    menuItem.groupId,
                    menuItem.itemId,
                    Menu.NONE,
                    menuItem.title
                ).icon = menuItem.icon
            }
        }
        return getStartFragmentId(this.context)
    }
}