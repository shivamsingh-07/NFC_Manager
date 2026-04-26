package com.nfcmanager.app.presentation.actions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nfcmanager.app.domain.model.NfcAction
import com.nfcmanager.app.domain.repository.NfcActionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ActionsViewModel @Inject constructor(
    private val repository: NfcActionRepository,
) : ViewModel() {

    val actions: StateFlow<List<NfcAction>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }
}
