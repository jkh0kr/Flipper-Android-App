package com.flipperdevices.info.impl.viewmodel

import androidx.lifecycle.viewModelScope
import com.flipperdevices.bridge.api.model.wrapToRequest
import com.flipperdevices.bridge.service.api.FlipperServiceApi
import com.flipperdevices.bridge.service.api.provider.FlipperServiceProvider
import com.flipperdevices.core.ui.lifecycle.LifecycleViewModel
import com.flipperdevices.protobuf.main
import com.flipperdevices.protobuf.system.playAudiovisualAlertRequest
import kotlinx.coroutines.launch
import javax.inject.Inject

class AlarmViewModel @Inject constructor(
    private val serviceProvider: FlipperServiceProvider
) : LifecycleViewModel() {
    fun alarmOnFlipper() {
        serviceProvider.provideServiceApi(this) { serviceApi ->
            viewModelScope.launch {
                alarmOnFlipperInternal(serviceApi)
            }
        }
    }

    private suspend fun alarmOnFlipperInternal(serviceApi: FlipperServiceApi) {
        serviceApi.requestApi.requestWithoutAnswer(
            main {
                systemPlayAudiovisualAlertRequest = playAudiovisualAlertRequest { }
            }.wrapToRequest()
        )
    }
}
