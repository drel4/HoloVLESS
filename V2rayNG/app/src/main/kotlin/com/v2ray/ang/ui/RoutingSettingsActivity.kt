package com.v2ray.ang.ui

import android.app.ActionBar
import android.app.FragmentTransaction
import android.os.Bundle
import com.v2ray.ang.R
import androidx.fragment.app.Fragment
import com.v2ray.ang.AppConfig
import com.v2ray.ang.databinding.ActivityRoutingSettingsBinding
import androidx.viewpager2.widget.ViewPager2

class RoutingSettingsActivity : BaseActivity() {
    private lateinit var binding: ActivityRoutingSettingsBinding

    private val titles: Array<out String> by lazy {
        resources.getStringArray(R.array.routing_tag)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRoutingSettingsBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        title = getString(R.string.routing_settings_title)
        actionBar?.setDisplayHomeAsUpEnabled(true)

        val fragments = ArrayList<Fragment>()
        fragments.add(RoutingSettingsFragment().newInstance(AppConfig.PREF_V2RAY_ROUTING_AGENT))
        fragments.add(RoutingSettingsFragment().newInstance(AppConfig.PREF_V2RAY_ROUTING_DIRECT))
        fragments.add(RoutingSettingsFragment().newInstance(AppConfig.PREF_V2RAY_ROUTING_BLOCKED))

        val adapter = FragmentAdapter(this, fragments)
        binding.viewpager.adapter = adapter

        actionBar?.let { bar ->
            bar.navigationMode = ActionBar.NAVIGATION_MODE_TABS
            titles.forEachIndexed { position, tabTitle ->
                bar.addTab(bar.newTab().setText(tabTitle).setTabListener(object : ActionBar.TabListener {
                    override fun onTabSelected(tab: ActionBar.Tab, transaction: FragmentTransaction?) {
                        binding.viewpager.currentItem = position
                    }

                    override fun onTabUnselected(tab: ActionBar.Tab, transaction: FragmentTransaction?) = Unit
                    override fun onTabReselected(tab: ActionBar.Tab, transaction: FragmentTransaction?) = Unit
                }))
            }
        }
        binding.viewpager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                actionBar?.setSelectedNavigationItem(position)
            }
        })
    }
}
