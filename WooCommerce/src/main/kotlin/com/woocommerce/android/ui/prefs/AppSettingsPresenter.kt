package com.woocommerce.android.ui.prefs

import com.woocommerce.android.ui.orders.cardreader.ClearCardReaderData
import com.woocommerce.android.util.FeatureFlag
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.generated.AccountActionBuilder
import org.wordpress.android.fluxc.generated.NotificationActionBuilder
import org.wordpress.android.fluxc.generated.SiteActionBuilder
import org.wordpress.android.fluxc.store.AccountStore
import org.wordpress.android.fluxc.store.AccountStore.OnAuthenticationChanged
import org.wordpress.android.fluxc.store.NotificationStore
import org.wordpress.android.fluxc.store.NotificationStore.OnDeviceUnregistered
import javax.inject.Inject

class AppSettingsPresenter @Inject constructor(
    private val dispatcher: Dispatcher,
    private val accountStore: AccountStore,
    @Suppress("unused") // We keep it here to make sure that the store is subscribed to the event bus
    private val notificationStore: NotificationStore,
    private val clearCardReaderData: ClearCardReaderData
) : AppSettingsContract.Presenter {
    private var appSettingsView: AppSettingsContract.View? = null

    override fun takeView(view: AppSettingsContract.View) {
        dispatcher.register(this)
        appSettingsView = view
    }

    override fun dropView() {
        dispatcher.unregister(this)
        appSettingsView = null
    }

    override fun logout() {
        if (FeatureFlag.CARD_READER.isEnabled())
            coroutineScope.launch { clearCardReaderData() }
        // First unregister the device for push notifications
        dispatcher.dispatch(NotificationActionBuilder.newUnregisterDeviceAction())
    }

    override fun userIsLoggedIn(): Boolean {
        return accountStore.hasAccessToken()
    }

    override fun getAccountDisplayName(): String {
        return accountStore.account?.displayName ?: ""
    }

    @Suppress("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDeviceUnregistered(event: OnDeviceUnregistered) {
        // Now that we've unregistered the device, we can clear the token and logout
        appSettingsView?.clearNotificationPreferences()
        dispatcher.dispatch(AccountActionBuilder.newSignOutAction())
        dispatcher.dispatch(SiteActionBuilder.newRemoveWpcomAndJetpackSitesAction())
    }

    @Suppress("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onAuthenticationChanged(event: OnAuthenticationChanged) {
        if (!event.isError && !userIsLoggedIn()) {
            appSettingsView?.finishLogout()
        }
    }
}
