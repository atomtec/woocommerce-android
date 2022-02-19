package com.woocommerce.android.ui.moremenu

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.MaterialTheme
import androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.woocommerce.android.R
import com.woocommerce.android.databinding.FragmentMoreMenuBinding
import com.woocommerce.android.extensions.navigateSafely
import com.woocommerce.android.tools.SelectedSite
import com.woocommerce.android.ui.base.TopLevelFragment
import com.woocommerce.android.ui.moremenu.MoreMenuViewModel.MoreMenuEvent.*
import com.woocommerce.android.ui.sitepicker.SitePickerActivity
import com.woocommerce.android.util.ChromeCustomTabUtils
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
@ExperimentalFoundationApi
class MoreMenuFragment : TopLevelFragment(R.layout.fragment_more_menu) {
    @Inject lateinit var selectedSite: SelectedSite

    override fun getFragmentTitle() = getString(R.string.more_menu)

    override fun shouldExpandToolbar(): Boolean = false

    private var _binding: FragmentMoreMenuBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MoreMenuViewModel by viewModels()

    override fun scrollToTop() {
        return
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMoreMenuBinding.inflate(inflater, container, false)
        hideToolbar()

        val view = binding.root
        binding.menu.apply {
            // Dispose of the Composition when the view's LifecycleOwner
            // is destroyed
            setViewCompositionStrategy(DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MaterialTheme {
                    MoreMenu(viewModel)
                }
            }
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupObservers()
    }

    private fun setupObservers() {
        viewModel.event.observe(viewLifecycleOwner) { event ->
            when (event) {
                is NavigateToSettingsEvent -> navigateToSettings()
                is StartSitePickerEvent -> startSitePicker()
                is ViewAdminEvent -> openInBrowser(event.url)
                is ViewStoreEvent -> openInBrowser(event.url)
                is ViewReviewsEvent -> navigateToReviews()
            }
        }
    }

    private fun navigateToSettings() {
        findNavController().navigateSafely(
            MoreMenuFragmentDirections.actionMoreMenuToSettingsActivity()
        )
    }

    private fun startSitePicker() {
        val sitePickerIntent = Intent(context, SitePickerActivity::class.java)
        requireActivity().startActivity(sitePickerIntent)
    }

    private fun openInBrowser(url: String) {
        ChromeCustomTabUtils.launchUrl(requireContext(), url)
    }

    private fun navigateToReviews() {
        findNavController().navigateSafely(
            MoreMenuFragmentDirections.actionMoreMenuToReviewList()
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
