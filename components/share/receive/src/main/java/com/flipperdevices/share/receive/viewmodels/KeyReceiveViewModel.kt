package com.flipperdevices.share.receive.viewmodels

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.flipperdevices.bridge.dao.api.model.FlipperKey
import com.flipperdevices.bridge.synchronization.api.SynchronizationApi
import com.flipperdevices.core.ktx.android.toast
import com.flipperdevices.core.log.LogTagProvider
import com.flipperdevices.core.log.error
import com.flipperdevices.core.log.info
import com.flipperdevices.core.ui.lifecycle.AndroidLifecycleViewModel
import com.flipperdevices.deeplink.model.Deeplink
import com.flipperdevices.deeplink.model.DeeplinkConstants
import com.flipperdevices.share.receive.R
import com.flipperdevices.share.receive.helpers.FlipperKeyParserHelper
import com.flipperdevices.share.receive.helpers.ReceiveKeyActionHelper
import com.flipperdevices.share.receive.models.ReceiveState
import com.flipperdevices.share.receive.models.ReceiverError
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import tangle.inject.TangleParam
import tangle.viewmodel.VMInject
import java.net.UnknownHostException
import java.net.UnknownServiceException

class KeyReceiveViewModel @VMInject constructor(
    @TangleParam(DeeplinkConstants.KEY)
    initialDeeplink: Deeplink?,
    application: Application,
    private val synchronizationApi: SynchronizationApi,
    private val flipperKeyParserHelper: FlipperKeyParserHelper,
    private val receiveKeyActionHelper: ReceiveKeyActionHelper
) : AndroidLifecycleViewModel(application), LogTagProvider {
    override val TAG = "KeyReceiveViewModel"
    private val internalDeeplinkFlow = MutableStateFlow(initialDeeplink)

    private val state = MutableStateFlow<ReceiveState>(ReceiveState.NotStarted)
    fun getState() = state.asStateFlow()

    init {
        internalDeeplinkFlow.onEach {
            parseFlipperKey()
        }.launchIn(viewModelScope)
    }

    private suspend fun parseFlipperKey() {
        internalDeeplinkFlow.onEach {
            val flipperKey = flipperKeyParserHelper.toFlipperKey(it)
            flipperKey.onSuccess { localFlipperKey ->
                processSuccessfullyParseKey(localFlipperKey)
            }
            flipperKey.onFailure { exception ->
                processFailureParseKey(exception)
            }
        }.launchIn(viewModelScope)
    }

    private suspend fun processSuccessfullyParseKey(flipperKey: FlipperKey) {
        val newKey = receiveKeyActionHelper.findNewPathAndCloneKey(flipperKey)
        val keyParsed = receiveKeyActionHelper.parseKey(newKey)
        state.emit(ReceiveState.Pending(newKey, keyParsed))
    }

    private suspend fun processFailureParseKey(exception: Throwable) {
        error(exception) { "Error on parse flipperKey" }
        val errorType = when (exception) {
            is UnknownHostException -> ReceiverError.NO_INTERNET_CONNECTION
            is UnknownServiceException -> ReceiverError.CANT_CONNECT_TO_SERVER
            is ClientRequestException -> {
                val exceptionStatus = exception.response.status
                if (exceptionStatus == HttpStatusCode.NotFound) {
                    ReceiverError.EXPIRED_LINK
                } else {
                    ReceiverError.CANT_CONNECT_TO_SERVER
                }
            }
            else -> ReceiverError.INVALID_FILE_FORMAT
        }
        state.emit(ReceiveState.Error(errorType))
    }

    fun onRetry() {
        internalDeeplinkFlow.onEach {
            state.emit(ReceiveState.NotStarted)
            parseFlipperKey()
        }.launchIn(viewModelScope)
    }

    fun onSave() {
        val localState = state.value
        if (localState !is ReceiveState.Pending) {
            info { "You can save key only from pending state, now is $state" }
            return
        }
        val newState = localState.copy(isSaving = true)

        val isStateSaved = state.compareAndSet(localState, newState)
        if (!isStateSaved) {
            onSave()
            return
        }

        viewModelScope.launch {
            val saveKeyResult = receiveKeyActionHelper.saveKey(newState.flipperKey)

            saveKeyResult.onFailure { exception ->
                error(exception) { "While save key ${localState.flipperKey}" }
                getApplication<Application>().toast(R.string.receive_error_conflict)
                state.emit(ReceiveState.Pending(localState.flipperKey, localState.parsed))
                return@launch
            }

            saveKeyResult.onSuccess {
                state.emit(ReceiveState.Finished)
            }
        }
    }

    fun onFinish() {
        synchronizationApi.startSynchronization(force = true)
    }
}
