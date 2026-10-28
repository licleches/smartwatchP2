package com.example.medtime.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.medtime.presentation.auth.AuthRepository
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AuthUiState {
    data object Loading : AuthUiState
    data class SignedOut(val message: String? = null) : AuthUiState
    data class SignedIn(val userId: String, val email: String?) : AuthUiState
}

class AuthViewModel(
    private val repository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow<AuthUiState>(AuthUiState.Loading)
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.sessionStatus.collect { status ->
                _state.value = status.toUiState()
            }
        }
    }

    fun signIn(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _state.value = AuthUiState.SignedOut("Introduce tu correo y contraseña")
            return
        }

        viewModelScope.launch {
            _state.value = AuthUiState.Loading
            runCatching { repository.signIn(email.trim(), password) }
                .onFailure { error ->
                    _state.value = AuthUiState.SignedOut(
                        error.message?.takeIf(String::isNotBlank)
                            ?: "No se pudo iniciar sesión"
                    )
                }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            runCatching { repository.signOut() }
        }
    }

    private fun SessionStatus.toUiState(): AuthUiState = when (this) {
        SessionStatus.Initializing -> AuthUiState.Loading
        is SessionStatus.Authenticated -> {
            val user = session.user
            if (user == null) {
                AuthUiState.SignedOut("No se pudo cargar el usuario")
            } else {
                AuthUiState.SignedIn(user.id, user.email)
            }
        }
        is SessionStatus.NotAuthenticated -> AuthUiState.SignedOut()
        is SessionStatus.RefreshFailure -> AuthUiState.SignedOut("La sesión ha expirado")
    }

    class Factory(
        private val repository: AuthRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AuthViewModel::class.java))
            return AuthViewModel(repository) as T
        }
    }
}
