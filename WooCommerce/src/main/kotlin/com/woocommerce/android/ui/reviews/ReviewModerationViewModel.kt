package com.woocommerce.android.ui.reviews

import android.os.Parcelable
import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import com.woocommerce.android.R
import com.woocommerce.android.analytics.AnalyticsTracker
import com.woocommerce.android.model.ActionStatus
import com.woocommerce.android.tools.NetworkStatus
import com.woocommerce.android.tools.SelectedSite
import com.woocommerce.android.viewmodel.LiveDataDelegate
import com.woocommerce.android.viewmodel.MultiLiveEvent
import com.woocommerce.android.viewmodel.ScopedViewModel
import com.woocommerce.android.viewmodel.SingleLiveEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.store.WCProductStore
import javax.inject.Inject

@HiltViewModel
class ReviewModerationViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val networkStatus: NetworkStatus,
    private val dispatcher: Dispatcher,
    private val wcProductStore:WCProductStore,
    private val selectedSite: SelectedSite,
) : ScopedViewModel(savedState) {

    private val _moderateProductReview = SingleLiveEvent<ProductReviewModerationRequest?>()
    val moderateProductReview: LiveData<ProductReviewModerationRequest?> = _moderateProductReview

    var pendingModerationRemoteReviewId: Long? = null
    var pendingModerationNewStatus: String? = null

    var pendingModerationRequest: ProductReviewModerationRequest? = null

    val viewStateData = LiveDataDelegate(savedState, ViewState())
    private var viewState by viewStateData





    // region Review Moderation
    fun submitReviewStatusChange(request: ProductReviewModerationRequest) {
        pendingModerationRemoteReviewId = request.productReview.remoteId
        pendingModerationNewStatus = request.newStatus.toString()
        if (networkStatus.isConnected()) {
            val payload = WCProductStore.UpdateProductReviewStatusPayload(
                selectedSite.get(),
                request.productReview.remoteId,
                request.newStatus.toString()
            )
            launch {
                var onReviewChanged = wcProductStore.updateProductReviewStatus(payload)
                viewState = viewState.copy(isRefreshing = false)
                if(onReviewChanged.isError) {
                    triggerEvent(MultiLiveEvent.Event.ShowSnackbar(R.string.wc_moderate_review_error))
                    sendReviewModerationUpdate(ActionStatus.ERROR)
                    viewState = viewState.copy(isReviewUpdateSuccessfull = false)
                }
                else{
                    viewState = viewState.copy(isReviewUpdateSuccessfull = true)
                    sendReviewModerationUpdate(ActionStatus.SUCCESS)
                }

            }

            //dispatcher.dispatch(WCProductActionBuilder.newUpdateProductReviewStatusAction(payload))


            AnalyticsTracker.track(
                AnalyticsTracker.Stat.REVIEW_ACTION,
                mapOf(AnalyticsTracker.KEY_TYPE to request.newStatus.toString())
            )

            sendReviewModerationUpdate(ActionStatus.SUBMITTED)
        } else {
            // Network is not connected
            showOfflineSnack()
            sendReviewModerationUpdate(ActionStatus.ERROR)
        }
    }

    fun revertPendingModerationState(){
        viewState = viewState.copy(isRefreshing = false)
    }

    private fun showOfflineSnack() {
        // Network is not connected
        triggerEvent(MultiLiveEvent.Event.ShowSnackbar(R.string.offline_error))
    }

    private fun sendReviewModerationUpdate(newRequestStatus: ActionStatus) {
        _moderateProductReview.value = _moderateProductReview.value?.apply { actionStatus = newRequestStatus }

        // If the request has been completed, set the event to null to prevent issues later.
        if (newRequestStatus.isComplete()) {
            _moderateProductReview.value = null
        }
    }

    // endregion


    fun moderateReviewRequest(event: OnRequestModerateReviewEvent) {
        if (networkStatus.isConnected()) {
            // Send the request to the UI to show the UNDO snackbar
            viewState = viewState.copy(isRefreshing = true)
            _moderateProductReview.value = event.request
        } else {
            // Network not connected
            showOfflineSnack()
        }
    }

    fun resetPendingModerationVariables() {
        pendingModerationNewStatus = null
        pendingModerationRemoteReviewId = null
    }

    @Parcelize
    data class ViewState(
        val isRefreshing: Boolean? = null,
        val isReviewUpdateSuccessfull: Boolean? = null
    ) : Parcelable


}
