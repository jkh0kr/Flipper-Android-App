package com.flipperdevices.infrared.editor.api

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.backhandler.BackCallback
import com.flipperdevices.bridge.dao.api.model.FlipperKeyPath
import com.flipperdevices.core.di.AppGraph
import com.flipperdevices.core.ui.ktx.viewModelWithFactory
import com.flipperdevices.infrared.api.InfraredEditorDecomposeComponent
import com.flipperdevices.infrared.editor.compose.screen.ComposableInfraredEditorScreen
import com.flipperdevices.infrared.editor.viewmodel.InfraredEditorViewModel
import com.flipperdevices.ui.decompose.DecomposeOnBackParameter
import com.squareup.anvil.annotations.ContributesBinding
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

class InfraredEditorDecomposeComponentImpl @AssistedInject constructor(
    @Assisted componentContext: ComponentContext,
    @Assisted private val keyPath: FlipperKeyPath,
    @Assisted private val onBack: DecomposeOnBackParameter,
    private val infraredEditorViewModelFactory: InfraredEditorViewModel.Factory
) : InfraredEditorDecomposeComponent, ComponentContext by componentContext {
    private val isBackPressHandledFlow = MutableStateFlow(false)
    private val backCallback = BackCallback { isBackPressHandledFlow.update { true } }

    init {
        backHandler.register(backCallback)
    }

    @Composable
    @Suppress("NonSkippableComposable")
    override fun Render() {
        val viewModel = viewModelWithFactory(key = keyPath.toString()) {
            infraredEditorViewModelFactory(keyPath)
        }
        val isBackPressHandled by isBackPressHandledFlow.collectAsState()
        val keyState by viewModel.getKeyState().collectAsState()

        LaunchedEffect(isBackPressHandled) {
            if (isBackPressHandled) {
                withContext(Dispatchers.Main) {
                    viewModel.processCancel(keyState, onBack::invoke)
                }
            }
        }

        ComposableInfraredEditorScreen(
            onBack = onBack::invoke,
            viewModel = viewModel
        )
    }

    @AssistedFactory
    @ContributesBinding(AppGraph::class, InfraredEditorDecomposeComponent.Factory::class)
    interface Factory : InfraredEditorDecomposeComponent.Factory {
        override fun invoke(
            componentContext: ComponentContext,
            keyPath: FlipperKeyPath,
            onBack: DecomposeOnBackParameter
        ): InfraredEditorDecomposeComponentImpl
    }
}
