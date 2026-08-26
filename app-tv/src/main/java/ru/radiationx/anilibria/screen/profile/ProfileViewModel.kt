package ru.radiationx.anilibria.screen.profile

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.screen.LifecycleViewModel
import ru.radiationx.data.entity.domain.other.ProfileItem
import ru.radiationx.data.repository.AuthRepository
import ru.radiationx.shared.ktx.coRunCatching
import timber.log.Timber
import javax.inject.Inject

class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : LifecycleViewModel() {

    val profileData = MutableStateFlow<ProfileItem?>(null)

    init {
        authRepository
            .observeUser()
            .onEach { profileData.value = it }
            .launchIn(viewModelScope)
    }

    fun onSignInClick() {
        // AniRu does not use AniLibria account login.
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun onSignOutClick() {
        GlobalScope.launch {
            coRunCatching {
                authRepository.signOut()
            }.onFailure {
                Timber.e(it)
            }
        }
    }
}