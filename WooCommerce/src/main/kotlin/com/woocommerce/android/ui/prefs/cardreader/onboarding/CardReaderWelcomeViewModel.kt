package com.woocommerce.android.ui.prefs.cardreader.onboarding

import androidx.lifecycle.SavedStateHandle
import com.woocommerce.android.viewmodel.ScopedViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class CardReaderWelcomeViewModel @Inject constructor(
    savedState: SavedStateHandle,
) : ScopedViewModel(savedState) {
}
