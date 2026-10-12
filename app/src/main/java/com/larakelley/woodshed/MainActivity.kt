package com.larakelley.woodshed

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.larakelley.woodshed.theme.WoodshedTheme
import com.larakelley.woodshed.ui.browse.BrowseScreen
import com.larakelley.woodshed.ui.browse.BrowseViewModel

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val repository = (application as WoodshedApplication).repository

    enableEdgeToEdge()
    setContent {
      WoodshedTheme {
        val viewModel: BrowseViewModel = viewModel { BrowseViewModel(repository) }
        val state by viewModel.state.collectAsStateWithLifecycle()
        BrowseScreen(state, viewModel::selectTransposition, viewModel::selectFamily)
      }
    }
  }
}
