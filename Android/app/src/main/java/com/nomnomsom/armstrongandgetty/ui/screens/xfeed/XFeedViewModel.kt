package com.nomnomsom.armstrongandgetty.ui.screens.xfeed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nomnomsom.armstrongandgetty.data.model.XFeedItem
import com.nomnomsom.armstrongandgetty.data.remote.XFeedParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class XFeedUiState(
    val items: List<XFeedItem> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class XFeedViewModel @Inject constructor(
    private val xFeedParser: XFeedParser
) : ViewModel() {

    private val _uiState = MutableStateFlow(XFeedUiState())
    val uiState: StateFlow<XFeedUiState> = _uiState.asStateFlow()

    init {
        loadFeed(isInitial = true)
    }

    fun refresh() {
        loadFeed(isInitial = false)
    }

    private fun loadFeed(isInitial: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = isInitial && _uiState.value.items.isEmpty(),
                isRefreshing = !isInitial,
                error = null
            )

            val result = xFeedParser.fetchFeed()
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    items = result.getOrThrow(),
                    isLoading = false,
                    isRefreshing = false
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = result.exceptionOrNull()?.message ?: "Failed to load feed"
                )
            }
        }
    }
}
