package com.woocommerce.android.ui.reviews

import android.os.Parcelable
import androidx.lifecycle.*
import com.woocommerce.android.R
import com.woocommerce.android.annotations.OpenClassOnDebug
import com.woocommerce.android.model.ActionStatus
import com.woocommerce.android.model.ProductReview
import com.woocommerce.android.model.RequestResult.NO_ACTION_NEEDED
import com.woocommerce.android.model.RequestResult.SUCCESS
import com.woocommerce.android.network.ConnectionChangeReceiver.ConnectionChangeEvent
import com.woocommerce.android.push.UnseenReviewsCountHandler
import com.woocommerce.android.tools.NetworkStatus
import com.woocommerce.android.ui.reviews.ReviewListViewModel.ReviewListEvent.MarkAllAsRead
import com.woocommerce.android.ui.reviews.domain.MarkAllReviewsAsSeen
import com.woocommerce.android.ui.reviews.domain.MarkAllReviewsAsSeen.Fail
import com.woocommerce.android.ui.reviews.domain.MarkAllReviewsAsSeen.Success
import com.woocommerce.android.util.WooLog
import com.woocommerce.android.util.WooLog.T.REVIEWS
import com.woocommerce.android.viewmodel.LiveDataDelegate
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event.ShowSnackbar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import javax.inject.Inject

@OpenClassOnDebug
@HiltViewModel
class ReviewListViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val networkStatus: NetworkStatus,
    private val reviewRepository: ReviewListRepository,
    reviewModerationHandler: ReviewModerationHandler,
    private val markAllReviewsAsSeen: MarkAllReviewsAsSeen,
    private val unseenReviewsCountHandler: UnseenReviewsCountHandler
) : BaseReviewModerationViewModel(savedState, reviewModerationHandler),
    ReviewModeration.Relay {
    companion object {
        private const val TAG = "ReviewListViewModel"
    }

    private val _reviewList = MutableLiveData<List<ProductReview>>()
    val reviewList: LiveData<List<ProductReview>> = _reviewList

    final val viewStateData = LiveDataDelegate(savedState, ViewState())
    private var viewState by viewStateData

    init {
        EventBus.getDefault().register(this)
        /* This called from derived class to avoid the warning
         * of calling non final method in base class constructor */
        this.collectModerationEvents()
        observeReviewUpdates()
    }

    override fun onCleared() {
        super.onCleared()
        EventBus.getDefault().unregister(this)
        reviewRepository.onCleanup()
    }

    /**
     * Fetch and load cached reviews from the database, then fetch fresh reviews
     * from the API.
     */
    fun start() {
        launch {
            // Initial load. Get and show reviewList from the db if any
            val reviewsInDb = reviewRepository.getCachedProductReviews()
            if (reviewsInDb.isNotEmpty()) {
                _reviewList.value = reviewsInDb
                viewState = viewState.copy(isSkeletonShown = false)
            } else {
                viewState = viewState.copy(isSkeletonShown = true)
            }
            fetchReviewList(loadMore = false)
        }
    }

    /**
     * Reload reviews from the database. Useful when a change happens on the backend
     * when the list view was not visible.
     */
    fun reloadReviewsFromCache() {
        launch {
            _reviewList.value = reviewRepository.getCachedProductReviews()
        }
    }

    fun loadMoreReviews() {
        if (!reviewRepository.canLoadMore) {
            WooLog.d(REVIEWS, "$TAG : No more product reviews to load")
            return
        }

        viewState = viewState.copy(isLoadingMore = true)
        launch {
            fetchReviewList(loadMore = true)
        }
    }

    fun forceRefreshReviews() {
        viewState = viewState.copy(isRefreshing = true)
        launch {
            fetchReviewList(loadMore = false)
        }
    }

    fun checkForUnreadReviews() {
        launch {
            viewState = viewState.copy(hasUnreadReviews = reviewRepository.getHasUnreadCachedProductReviews())
        }
    }

    fun markAllReviewsAsRead() {
        if (networkStatus.isConnected()) {
            triggerEvent(MarkAllAsRead(ActionStatus.SUBMITTED))

            launch {
                when (markAllReviewsAsSeen()) {
                    Fail -> {
                        triggerEvent(MarkAllAsRead(ActionStatus.ERROR))
                        triggerEvent(ShowSnackbar(R.string.wc_mark_all_read_error))
                    }
                    Success -> {
                        triggerEvent(MarkAllAsRead(ActionStatus.SUCCESS))
                        triggerEvent(ShowSnackbar(R.string.wc_mark_all_read_success))
                        _reviewList.value = reviewRepository.getCachedProductReviews()
                    }
                }
            }
        } else {
            showOfflineSnack()
        }
    }

    private suspend fun fetchReviewList(loadMore: Boolean) {
        if (networkStatus.isConnected()) {
            when (reviewRepository.fetchProductReviews(loadMore)) {
                SUCCESS, NO_ACTION_NEEDED -> {
                    _reviewList.value = reviewRepository.getCachedProductReviews()
                }
                else -> triggerEvent(ShowSnackbar(R.string.review_fetch_error))
            }

            checkForUnreadReviews()
        } else {
            // Network is not connected
            showOfflineSnack()
        }

        viewState = viewState.copy(
            isSkeletonShown = false,
            isLoadingMore = false,
            isRefreshing = false
        )
    }

    private fun showOfflineSnack() {
        // Network is not connected
        triggerEvent(ShowSnackbar(R.string.offline_error))
    }

    private fun observeReviewUpdates() {
        viewModelScope.launch {
            unseenReviewsCountHandler.observeUnseenCount()
                .collect { forceRefreshReviews() }
        }
    }

    @Suppress("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: ConnectionChangeEvent) {
        if (event.isConnected) {
            // Refresh data now that a connection is active if needed
            forceRefreshReviews()
        }
    }

    @Parcelize
    data class ViewState(
        val isSkeletonShown: Boolean? = null,
        val isLoadingMore: Boolean? = null,
        val isRefreshing: Boolean? = null,
        val hasUnreadReviews: Boolean? = null
    ) : Parcelable

    sealed class ReviewListEvent : Event() {
        data class MarkAllAsRead(val status: ActionStatus) : ReviewListEvent()
    }
}
