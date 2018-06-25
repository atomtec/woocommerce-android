package com.woocommerce.android.ui.login

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.content.res.AppCompatResources
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import com.woocommerce.android.R
import com.woocommerce.android.di.GlideApp
import com.woocommerce.android.tools.SelectedSite
import com.woocommerce.android.ui.login.SiteListAdapter.OnSiteClickListener
import com.woocommerce.android.ui.main.MainActivity
import com.woocommerce.android.util.ActivityUtils
import dagger.android.AndroidInjection
import kotlinx.android.synthetic.main.activity_login_epilogue.*
import org.wordpress.android.fluxc.store.AccountStore
import org.wordpress.android.fluxc.store.SiteStore
import org.wordpress.android.login.LoginMode
import javax.inject.Inject

class LoginEpilogueActivity : AppCompatActivity(), LoginEpilogueContract.View, OnSiteClickListener {
    @Inject lateinit var presenter: LoginEpilogueContract.Presenter
    @Inject lateinit var accountStore: AccountStore
    @Inject lateinit var siteStore: SiteStore
    @Inject lateinit var selectedSite: SelectedSite

    private lateinit var siteAdapter: SiteListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login_epilogue)

        ActivityUtils.setStatusBarColor(this, R.color.wc_grey_mid)
        presenter.takeView(this)

        recycler.layoutManager = LinearLayoutManager(this)
        siteAdapter = SiteListAdapter(this, this)
        recycler.adapter = siteAdapter

        showUserInfo()
        showStoreList()
    }

    override fun onDestroy() {
        presenter.dropView()
        super.onDestroy()
    }

    override fun onBackPressed() {
        finish()
    }

    override fun showUserInfo() {
        text_displayname.text = accountStore.account?.displayName
        text_username.text = String.format(getString(R.string.at_username), accountStore.account?.userName)

        GlideApp.with(this)
                .load(accountStore.account?.avatarUrl)
                .placeholder(R.drawable.ic_placeholder_gravatar_grey_lighten_20_100dp)
                .circleCrop()
                .into(image_avatar)
    }

    override fun showStoreList() {
        val wcSites = presenter.getWooCommerceSites()
        if (wcSites.isEmpty()) {
            showNoStoresView()
            return
        }

        text_list_label.text = if (wcSites.size == 1)
            getString(R.string.login_connected_store)
        else
            getString(R.string.login_pick_store)

        if (selectedSite.isSet()) {
            siteAdapter.selectedSiteId = selectedSite.get().siteId
        } else {
            siteAdapter.selectedSiteId = wcSites[0].siteId
        }

        siteAdapter.siteList = wcSites

        button_continue.setOnClickListener {
            val site = siteStore.getSiteBySiteId(siteAdapter.selectedSiteId)
            if (site != null) {
                selectedSite.set(site)
                showMainActivityAndFinish()
            }
        }
    }

    override fun onSiteClick(siteId: Long) {
        val site = siteStore.getSiteBySiteId(siteId)
        if (site != null) {
            selectedSite.set(site)
        }
    }

    private fun showNoStoresView() {
        frame_list_container.visibility = View.GONE
        no_stores_view.visibility = View.VISIBLE

        val noStoresImage = AppCompatResources.getDrawable(this, R.drawable.ic_woo_no_store)
        no_stores_view.setCompoundDrawablesWithIntrinsicBounds(null, noStoresImage, null, null)

        button_continue.text = getString(R.string.login_with_a_different_account)
        button_continue.setOnClickListener {
            presenter.logout()
        }
    }

    /**
     * called by the presenter after logout completes
     */
    override fun cancel() {
        showLoginActivityAndFinish()
    }

    private fun showLoginActivityAndFinish() {
        val intent = Intent(this, LoginActivity::class.java)
        LoginMode.WPCOM_LOGIN_ONLY.putInto(intent)
        startActivity(intent)
        finish()
    }

    private fun showMainActivityAndFinish() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
