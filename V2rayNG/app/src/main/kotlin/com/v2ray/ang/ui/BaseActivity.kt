package com.v2ray.ang.ui

import android.view.MenuItem
import androidx.fragment.app.FragmentActivity

abstract class BaseActivity : FragmentActivity() {
    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        android.R.id.home -> {
            onBackPressed()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }
}
