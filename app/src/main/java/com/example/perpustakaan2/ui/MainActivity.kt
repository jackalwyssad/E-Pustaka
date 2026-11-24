package com.example.perpustakaan2.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.perpustakaan2.R
import com.example.perpustakaan2.databinding.ActivityMainBinding
import com.example.perpustakaan2.ui.fragment.BookshelfFragment
import com.example.perpustakaan2.ui.fragment.HomeFragment
import com.example.perpustakaan2.ui.fragment.ProfileFragment
import com.example.perpustakaan2.ui.fragment.SearchFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val homeFragment = HomeFragment()
    private val searchFragment = SearchFragment()
    private val bookshelfFragment = BookshelfFragment()
    private val profileFragment = ProfileFragment()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBottomNavigation()

        if (savedInstanceState == null) {
            loadFragment(homeFragment)
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    loadFragment(homeFragment)
                    true
                }
                R.id.nav_search -> {
                    loadFragment(searchFragment)
                    true
                }
                R.id.nav_bookshelf -> {
                    loadFragment(bookshelfFragment)
                    true
                }
                R.id.nav_profile -> {
                    loadFragment(profileFragment)
                    true
                }
                else -> false
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    fun navigateToSearch() {
        binding.bottomNavigation.selectedItemId = R.id.nav_search
    }
}
