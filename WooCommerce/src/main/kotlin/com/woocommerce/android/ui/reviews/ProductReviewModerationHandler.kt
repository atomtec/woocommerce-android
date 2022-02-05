package com.woocommerce.android.ui.reviews

interface ProductReviewModerationHandler {
    fun revertPendingModerationState()
    fun processNewModerationRequest(request: ProductReviewModerationRequest)
    fun setUpReviewModerationObserver()
    fun checkPendingReviewModerationRequest()
}
