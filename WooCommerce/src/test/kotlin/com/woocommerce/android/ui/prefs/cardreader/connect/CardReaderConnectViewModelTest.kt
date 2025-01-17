package com.woocommerce.android.ui.prefs.cardreader.connect

import com.woocommerce.android.AppPrefs
import com.woocommerce.android.R
import com.woocommerce.android.cardreader.CardReaderManager
import com.woocommerce.android.cardreader.connection.CardReader
import com.woocommerce.android.cardreader.connection.CardReaderDiscoveryEvents.Failed
import com.woocommerce.android.cardreader.connection.CardReaderDiscoveryEvents.ReadersFound
import com.woocommerce.android.cardreader.connection.CardReaderStatus
import com.woocommerce.android.cardreader.connection.CardReaderTypesToDiscover
import com.woocommerce.android.cardreader.connection.SpecificReader
import com.woocommerce.android.cardreader.connection.event.SoftwareUpdateStatus
import com.woocommerce.android.initSavedStateHandle
import com.woocommerce.android.model.UiString.UiStringRes
import com.woocommerce.android.model.UiString.UiStringText
import com.woocommerce.android.tools.SelectedSite
import com.woocommerce.android.ui.prefs.cardreader.CardReaderTracker
import com.woocommerce.android.ui.prefs.cardreader.CardReaderTrackingInfoKeeper
import com.woocommerce.android.ui.prefs.cardreader.connect.CardReaderConnectEvent.*
import com.woocommerce.android.ui.prefs.cardreader.connect.CardReaderConnectViewModel.ListItemViewState.CardReaderListItem
import com.woocommerce.android.ui.prefs.cardreader.connect.CardReaderConnectViewModel.ListItemViewState.ScanningInProgressListItem
import com.woocommerce.android.ui.prefs.cardreader.connect.CardReaderConnectViewModelTest.ScanResult.*
import com.woocommerce.android.ui.prefs.cardreader.connect.CardReaderConnectViewState.*
import com.woocommerce.android.ui.prefs.cardreader.onboarding.CardReaderOnboardingChecker
import com.woocommerce.android.ui.prefs.cardreader.onboarding.CardReaderOnboardingState
import com.woocommerce.android.ui.prefs.cardreader.onboarding.PluginType
import com.woocommerce.android.ui.prefs.cardreader.update.CardReaderUpdateViewModel
import com.woocommerce.android.viewmodel.BaseUnitTest
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runBlockingTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.kotlin.*
import org.wordpress.android.fluxc.model.SiteModel

@ExperimentalCoroutinesApi
@InternalCoroutinesApi
class CardReaderConnectViewModelTest : BaseUnitTest() {
    private lateinit var viewModel: CardReaderConnectViewModel

    private val tracker: CardReaderTracker = mock()
    private val readerStatusFlow = MutableStateFlow<CardReaderStatus>(CardReaderStatus.NotConnected())
    private val cardReaderManager: CardReaderManager = mock {
        on { readerStatus }.thenReturn(readerStatusFlow)
        on { softwareUpdateStatus }.thenReturn(flow { SoftwareUpdateStatus.Unknown })
    }
    private val appPrefs: AppPrefs = mock()
    private val onboardingChecker: CardReaderOnboardingChecker = mock()
    private val reader = mock<CardReader>().also { whenever(it.id).thenReturn("Dummy1") }
    private val reader2 = mock<CardReader>().also { whenever(it.id).thenReturn("Dummy2") }
    private val locationRepository: CardReaderLocationRepository = mock()
    private val siteModel: SiteModel = mock()
    private val selectedSite: SelectedSite = mock {
        on { getIfExists() }.thenReturn(siteModel)
        on { get() }.thenReturn(siteModel)
    }
    private val cardReaderTrackingInfoKeeper: CardReaderTrackingInfoKeeper = mock()
    private val locationId = "location_id"
    private val countryCode = "US"

    @Before
    fun setUp() = coroutinesTestRule.testDispatcher.runBlockingTest {
        viewModel = initVM(CardReaderOnboardingState.OnboardingCompleted(PluginType.WOOCOMMERCE_PAYMENTS, countryCode))
    }

    @Test
    fun `given onboarding not completed, when flow started, then app starts onboarding flow`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            val vm = initVM(
                CardReaderOnboardingState.SetupNotCompleted(
                    PluginType.WOOCOMMERCE_PAYMENTS
                ),
                skipOnboarding = false
            )

            assertThat(vm.event.value)
                .isInstanceOf(NavigateToOnboardingFlow::class.java)
        }

    @Test
    fun `when skip onboarding flag is true, then onboarding state ignored`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            val vm = initVM(
                CardReaderOnboardingState.SetupNotCompleted(
                    PluginType.WOOCOMMERCE_PAYMENTS
                ),
                skipOnboarding = true
            )

            assertThat(vm.event.value).isNotInstanceOf(NavigateToOnboardingFlow::class.java)
        }

    @Test
    fun `given onboarding check fails, when flow started, then scanning failed state shown`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            val vm = initVM(CardReaderOnboardingState.GenericError, skipOnboarding = false)

            assertThat(vm.viewStateData.value).isInstanceOf(ScanningFailedState::class.java)
        }

    @Test
    fun `given connection not available, when flow started, then scanning failed state shown`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            val vm = initVM(CardReaderOnboardingState.GenericError, skipOnboarding = false)

            assertThat(vm.viewStateData.value).isInstanceOf(ScanningFailedState::class.java)
        }

    @Test
    fun `when onboarding completed, then location permissions check requested`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            assertThat(viewModel.event.value).isInstanceOf(CheckLocationPermissions::class.java)
        }

    @Test
    fun `given permissions enabled, when connection flow started, then location enabled check emitted`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            (viewModel.event.value as CheckLocationPermissions).onLocationPermissionsCheckResult(true)

            assertThat(viewModel.event.value).isInstanceOf(CheckLocationEnabled::class.java)
        }

    @Test
    fun `given permissions not enabled, when connection flow started, then permissions requested`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            (viewModel.event.value as CheckLocationPermissions).onLocationPermissionsCheckResult(false)

            assertThat(viewModel.event.value).isInstanceOf(RequestLocationPermissions::class.java)
        }

    @Test
    fun `given permissions granted, when permissions requested, then location enabled check emitted`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            (viewModel.event.value as CheckLocationPermissions).onLocationPermissionsCheckResult(false)

            (viewModel.event.value as RequestLocationPermissions).onPermissionsRequestResult(true)

            assertThat(viewModel.event.value).isInstanceOf(CheckLocationEnabled::class.java)
        }

    @Test
    fun `given permissions not granted, when permissions requested, then missing permissions error shown`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            (viewModel.event.value as CheckLocationPermissions).onLocationPermissionsCheckResult(false)

            (viewModel.event.value as RequestLocationPermissions).onPermissionsRequestResult(false)

            assertThat(viewModel.viewStateData.value).isInstanceOf(MissingLocationPermissionsError::class.java)
        }

    @Test
    fun `when Open app settings button clicked, then user redirected to app settings`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            (viewModel.event.value as CheckLocationPermissions).onLocationPermissionsCheckResult(false)
            (viewModel.event.value as RequestLocationPermissions).onPermissionsRequestResult(false)

            (viewModel.viewStateData.value as MissingLocationPermissionsError).onPrimaryActionClicked.invoke()

            assertThat(viewModel.event.value).isInstanceOf(OpenPermissionsSettings::class.java)
        }

    @Test
    fun `given app on missing permissions error screen, when apps comes to foreground, then permissions re-checked`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            (viewModel.event.value as CheckLocationPermissions).onLocationPermissionsCheckResult(false)
            (viewModel.event.value as RequestLocationPermissions).onPermissionsRequestResult(false)

            viewModel.onScreenStarted()

            assertThat(viewModel.event.value).isInstanceOf(CheckLocationPermissions::class.java)
        }

    @Test
    fun `given app on missing bt permissions screen, when apps comes to foreground, then permissions re-checked`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            (viewModel.event.value as CheckLocationPermissions).onLocationPermissionsCheckResult(true)
            (viewModel.event.value as CheckLocationEnabled).onLocationEnabledCheckResult(true)
            (viewModel.event.value as CheckBluetoothPermissionsGiven).onBluetoothPermissionsGivenCheckResult(false)
            (viewModel.event.value as RequestBluetoothRuntimePermissions)
                .onBluetoothRuntimePermissionsRequestResult(false)

            viewModel.onScreenStarted()

            assertThat(viewModel.event.value).isInstanceOf(CheckLocationPermissions::class.java)
        }

    @Test
    fun `given NOT on missing permissions screen, when apps comes to foreground, then permissions not re-checked`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            (viewModel.event.value as CheckLocationPermissions).onLocationPermissionsCheckResult(true)

            viewModel.onScreenStarted()

            assertThat(viewModel.event.value).isNotInstanceOf(CheckLocationPermissions::class.java)
        }

    @Test
    fun `given NOT on bt missing permissions screen, when apps comes to foreground, then permissions not re-checked`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            (viewModel.event.value as CheckLocationPermissions).onLocationPermissionsCheckResult(true)
            (viewModel.event.value as CheckLocationEnabled).onLocationEnabledCheckResult(true)
            (viewModel.event.value as CheckBluetoothPermissionsGiven).onBluetoothPermissionsGivenCheckResult(true)

            viewModel.onScreenStarted()

            assertThat(viewModel.event.value).isNotInstanceOf(CheckBluetoothPermissionsGiven::class.java)
        }

    @Test
    fun `given app on missing permissions, when apps comes to foreground, then permissions not re-requested`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            (viewModel.event.value as CheckLocationPermissions).onLocationPermissionsCheckResult(false)
            (viewModel.event.value as RequestLocationPermissions).onPermissionsRequestResult(false)
            viewModel.onScreenStarted()

            (viewModel.event.value as CheckLocationPermissions).onLocationPermissionsCheckResult(false)

            assertThat(viewModel.event.value).isNotInstanceOf(RequestLocationPermissions::class.java)
        }

    @Test
    fun `given location disabled, when connection flow started, then location disabled error shown`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            (viewModel.event.value as CheckLocationPermissions).onLocationPermissionsCheckResult(true)
            (viewModel.event.value as CheckLocationEnabled).onLocationEnabledCheckResult(false)

            assertThat(viewModel.viewStateData.value).isInstanceOf(LocationDisabledError::class.java)
        }

    @Test
    fun `given location enabled, when connection flow started, then check bluetooth permission emitted`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            (viewModel.event.value as CheckLocationPermissions).onLocationPermissionsCheckResult(true)
            (viewModel.event.value as CheckLocationEnabled).onLocationEnabledCheckResult(true)

            assertThat(viewModel.event.value).isInstanceOf(CheckBluetoothPermissionsGiven::class.java)
        }

    @Test
    fun `when user clicks on open location settings, then openLocationSettings emitted`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            (viewModel.event.value as CheckLocationPermissions).onLocationPermissionsCheckResult(true)
            (viewModel.event.value as CheckLocationEnabled).onLocationEnabledCheckResult(false)

            (viewModel.viewStateData.value as? LocationDisabledError)?.let {
                it.onPrimaryActionClicked.invoke()
            }

            assertThat(viewModel.event.value).isInstanceOf(OpenLocationSettings::class.java)
        }

    @Test
    fun `when location settings closed, then checkLocationEnabled emitted`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            (viewModel.event.value as CheckLocationPermissions).onLocationPermissionsCheckResult(true)
            (viewModel.event.value as CheckLocationEnabled).onLocationEnabledCheckResult(false)
            (viewModel.viewStateData.value as? LocationDisabledError)?.let {
                it.onPrimaryActionClicked.invoke()
            }

            (viewModel.event.value as OpenLocationSettings).onLocationSettingsClosed()

            assertThat(viewModel.event.value).isInstanceOf(CheckLocationEnabled::class.java)
        }

    @Test
    fun `given bluetooth disabled, when connection flow started, then enable-bluetooth request emitted`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            (viewModel.event.value as CheckLocationPermissions).onLocationPermissionsCheckResult(true)
            (viewModel.event.value as CheckLocationEnabled).onLocationEnabledCheckResult(true)
            (viewModel.event.value as CheckBluetoothPermissionsGiven).onBluetoothPermissionsGivenCheckResult(true)
            (viewModel.event.value as CheckBluetoothEnabled).onBluetoothCheckResult(false)

            assertThat(viewModel.event.value).isInstanceOf(RequestEnableBluetooth::class.java)
        }

    @Test
    fun `given request rejected, when enable-bluetooth requested, then bluetooth disabled error shown`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            (viewModel.event.value as CheckLocationPermissions).onLocationPermissionsCheckResult(true)
            (viewModel.event.value as CheckLocationEnabled).onLocationEnabledCheckResult(true)
            (viewModel.event.value as CheckBluetoothPermissionsGiven).onBluetoothPermissionsGivenCheckResult(true)
            (viewModel.event.value as CheckBluetoothEnabled).onBluetoothCheckResult(false)

            (viewModel.event.value as RequestEnableBluetooth).onEnableBluetoothRequestResult(false)

            assertThat(viewModel.viewStateData.value).isInstanceOf(BluetoothDisabledError::class.java)
        }

    @Test
    fun `given request accepted, when enable-bluetooth requested, then card manager initialized`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            (viewModel.event.value as CheckLocationPermissions).onLocationPermissionsCheckResult(true)
            (viewModel.event.value as CheckLocationEnabled).onLocationEnabledCheckResult(true)
            (viewModel.event.value as CheckBluetoothPermissionsGiven).onBluetoothPermissionsGivenCheckResult(true)
            (viewModel.event.value as CheckBluetoothEnabled).onBluetoothCheckResult(false)
            (viewModel.event.value as RequestEnableBluetooth).onEnableBluetoothRequestResult(true)

            verify(cardReaderManager).initialize()
        }

    @Test
    fun `given request accepted, when bt permissions requested, then card manager initialized`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            (viewModel.event.value as CheckLocationPermissions).onLocationPermissionsCheckResult(true)
            (viewModel.event.value as CheckLocationEnabled).onLocationEnabledCheckResult(true)
            (viewModel.event.value as CheckBluetoothPermissionsGiven).onBluetoothPermissionsGivenCheckResult(false)
            (viewModel.event.value as RequestBluetoothRuntimePermissions)
                .onBluetoothRuntimePermissionsRequestResult(true)
            (viewModel.event.value as CheckBluetoothEnabled).onBluetoothCheckResult(true)

            verify(cardReaderManager).initialize()
        }

    @Test
    fun `given request not accepted, when bt permissions requested, then card manager not initialized`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            (viewModel.event.value as CheckLocationPermissions).onLocationPermissionsCheckResult(true)
            (viewModel.event.value as CheckLocationEnabled).onLocationEnabledCheckResult(true)
            (viewModel.event.value as CheckBluetoothPermissionsGiven).onBluetoothPermissionsGivenCheckResult(false)
            (viewModel.event.value as RequestBluetoothRuntimePermissions)
                .onBluetoothRuntimePermissionsRequestResult(false)

            verify(cardReaderManager, never()).initialize()
        }

    @Test
    fun `given request accepted and manager init, when enable-bluetooth requested, then manager is not initialized`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            whenever(cardReaderManager.initialized).thenReturn(true)
            (viewModel.event.value as CheckLocationPermissions).onLocationPermissionsCheckResult(true)
            (viewModel.event.value as CheckLocationEnabled).onLocationEnabledCheckResult(true)
            (viewModel.event.value as CheckBluetoothPermissionsGiven).onBluetoothPermissionsGivenCheckResult(true)
            (viewModel.event.value as CheckBluetoothEnabled).onBluetoothCheckResult(false)

            (viewModel.event.value as RequestEnableBluetooth).onEnableBluetoothRequestResult(true)

            verify(cardReaderManager, never()).initialize()
        }

    @Test
    fun `when user clicks on open bluetooth settings, then enable-bluetooth request emitted`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            (viewModel.event.value as CheckLocationPermissions).onLocationPermissionsCheckResult(true)
            (viewModel.event.value as CheckLocationEnabled).onLocationEnabledCheckResult(true)
            (viewModel.event.value as CheckBluetoothPermissionsGiven).onBluetoothPermissionsGivenCheckResult(true)
            (viewModel.event.value as CheckBluetoothEnabled).onBluetoothCheckResult(false)
            (viewModel.event.value as RequestEnableBluetooth).onEnableBluetoothRequestResult(false)

            (viewModel.viewStateData.value as? BluetoothDisabledError)?.onPrimaryActionClicked?.invoke()

            assertThat(viewModel.event.value).isInstanceOf(RequestEnableBluetooth::class.java)
        }

    @Test
    fun `when cardReaderManager gets initialized, then scan is started`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            init()

            verify(cardReaderManager).discoverReaders(anyBoolean(), any())
        }

    @Test
    fun `given installation started, when cardReaderManager gets initialized, then show update in progress emitted`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            whenever(cardReaderManager.softwareUpdateStatus).thenReturn(
                flow {
                    emit(SoftwareUpdateStatus.InstallationStarted)
                }
            )

            init()

            assertThat(viewModel.event.value).isEqualTo(
                ShowUpdateInProgress
            )
        }

    @Test
    fun `given installing update, when cardReaderManager gets initialized, then show update in progress emitted`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            whenever(cardReaderManager.softwareUpdateStatus).thenReturn(
                flow {
                    emit(SoftwareUpdateStatus.Installing(0.1f))
                }
            )

            init()

            assertThat(viewModel.event.value).isEqualTo(
                ShowUpdateInProgress
            )
        }

    @Test
    fun `given connection in progress, when cardReaderManager gets initialized, then connecting status emitted`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            whenever(cardReaderManager.readerStatus).thenReturn(MutableStateFlow(CardReaderStatus.Connecting))

            init()

            assertThat(viewModel.viewStateData.value).isInstanceOf(ConnectingState::class.java)
        }

    @Test
    fun `given connection in progress and connected, when cardReaderManager gets initialized, then exits with true`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            whenever(cardReaderManager.softwareUpdateStatus).thenReturn(
                flow { emit(SoftwareUpdateStatus.Unknown) }
            )
            val readerStatusStateFlow = MutableStateFlow<CardReaderStatus>(CardReaderStatus.Connecting)
            whenever(cardReaderManager.readerStatus).thenReturn(readerStatusStateFlow)

            init()
            readerStatusStateFlow.emit(CardReaderStatus.Connected(mock()))

            assertThat(viewModel.event.value).isEqualTo(Event.ExitWithResult(true))
        }

    @Test
    fun `when scan fails, then scanning failed state shown`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            init(scanState = FAILED)

            assertThat(viewModel.viewStateData.value).isInstanceOf(ScanningFailedState::class.java)
        }

    @Test
    fun `given scanning failed screen shown, when user clicks on retry, then flow restarted`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            init(scanState = FAILED)

            (viewModel.viewStateData.value as ScanningFailedState).onPrimaryActionClicked.invoke()

            assertThat(viewModel.viewStateData.value).isInstanceOf(ScanningState::class.java)
        }

    @Test
    fun `when reader found, then reader found state shown`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            init(scanState = READER_FOUND)

            assertThat(viewModel.viewStateData.value).isInstanceOf(ReaderFoundState::class.java)
        }

    @Test
    fun `given last connected reader is null, when reader found, then reader found state shown`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            whenever(appPrefs.getLastConnectedCardReaderId()).thenReturn(null)

            init(scanState = READER_FOUND)

            assertThat(viewModel.viewStateData.value).isInstanceOf(ReaderFoundState::class.java)
        }

    @Test
    fun `given last connected reader is matching, when reader found, then reader connecting state shown`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            val readerStatusStateFlow = MutableStateFlow<CardReaderStatus>(CardReaderStatus.Connecting)
            whenever(cardReaderManager.readerStatus).thenReturn(readerStatusStateFlow)

            init(scanState = READER_FOUND)
            readerStatusStateFlow.emit(CardReaderStatus.Connected(mock()))

            assertThat(viewModel.viewStateData.value).isInstanceOf(ConnectingState::class.java)
        }

    @Test
    fun `given last connected reader is matching, when reader found, then auto reconnection event tracked`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            whenever(appPrefs.getLastConnectedCardReaderId()).thenReturn("Dummy1")

            init(scanState = READER_FOUND)

            verify(tracker).trackAutoConnectionStarted()
        }

    @Test
    fun `given last connected reader is not matching, when reader found, then reader found state shown`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            whenever(appPrefs.getLastConnectedCardReaderId()).thenReturn("Dummy2")

            init(scanState = READER_FOUND)

            assertThat(viewModel.viewStateData.value).isInstanceOf(ReaderFoundState::class.java)
        }

    @Test
    fun `given reader id is null, when reader found, then reader is ignored`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            whenever(reader.id).thenReturn(null)

            init(scanState = READER_FOUND)

            assertThat(viewModel.viewStateData.value).isInstanceOf(ScanningState::class.java)
        }

    @Test
    fun `when multiple readers found, then multiple readers found state shown`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            init(scanState = MULTIPLE_READERS_FOUND)

            assertThat(viewModel.viewStateData.value).isInstanceOf(MultipleReadersFoundState::class.java)
        }

    @Test
    fun `when scanning fails, then event tracked`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            init(scanState = FAILED)

            verify(tracker).trackReaderDiscoveryFailed(any())
        }

    @Test
    fun `when reader found, then event tracked`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            init(scanState = READER_FOUND)

            verify(tracker)
                .trackReadersDiscovered(1)
        }

    @Test
    fun `when multiple readers found, then event tracked`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            init(scanState = MULTIPLE_READERS_FOUND)

            verify(tracker)
                .trackReadersDiscovered(2)
        }

    @Test
    fun `given location fetching fails address, when user clicks on connect to reader button, then track failure`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            init()
            whenever(locationRepository.getDefaultLocationId(any())).thenReturn(
                CardReaderLocationRepository.LocationIdFetchingResult.Error.MissingAddress("")
            )

            (viewModel.viewStateData.value as ReaderFoundState).onPrimaryActionClicked.invoke()

            verify(tracker).trackFetchingLocationFailed("Missing Address")
        }

    @Test
    fun `given location fetching invalid postcode, when user clicks on connect to reader button, then track failure`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            init()
            whenever(locationRepository.getDefaultLocationId(any())).thenReturn(
                CardReaderLocationRepository.LocationIdFetchingResult.Error.InvalidPostalCode
            )

            (viewModel.viewStateData.value as ReaderFoundState).onPrimaryActionClicked.invoke()

            verify(tracker).trackFetchingLocationFailed("Invalid Postal Code")
        }

    @Test
    fun `given location fetching fails, when user clicks on connect to reader button, then track failure event`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            init()
            whenever(locationRepository.getDefaultLocationId(any())).thenReturn(
                CardReaderLocationRepository.LocationIdFetchingResult.Error.Other("selected site missing")
            )

            (viewModel.viewStateData.value as ReaderFoundState).onPrimaryActionClicked.invoke()

            verify(tracker).trackFetchingLocationFailed("selected site missing")
        }

    @Test
    fun `given location fetching fails address, when user clicks on update address, then track tapped event`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            init()
            whenever(locationRepository.getDefaultLocationId(any())).thenReturn(
                CardReaderLocationRepository.LocationIdFetchingResult.Error.MissingAddress("")
            )

            (viewModel.viewStateData.value as ReaderFoundState).onPrimaryActionClicked.invoke()
            (viewModel.viewStateData.value as MissingMerchantAddressError)
                .onPrimaryActionClicked.invoke()

            verify(tracker).trackMissingLocationTapped()
        }

    @Test
    fun `given location fetching passes, when user clicks on connect to reader button, then track success event`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            init()
            whenever(locationRepository.getDefaultLocationId(any())).thenReturn(
                CardReaderLocationRepository.LocationIdFetchingResult.Success("")
            )

            (viewModel.viewStateData.value as ReaderFoundState).onPrimaryActionClicked.invoke()

            verify(tracker).trackFetchingLocationSucceeded()
        }

    @Test
    fun `given location fetching fails, when user clicks on connect to reader button, then show error state`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            init()
            whenever(locationRepository.getDefaultLocationId(any())).thenReturn(
                CardReaderLocationRepository.LocationIdFetchingResult.Error.Other("Error")
            )

            (viewModel.viewStateData.value as ReaderFoundState).onPrimaryActionClicked.invoke()

            verify(cardReaderManager, never()).startConnectionToReader(reader, locationId)
            assertThat(viewModel.viewStateData.value).isInstanceOf(ConnectingFailedState::class.java)
        }

    @Test
    fun `given location fetching missing address, when user clicks on connect button, then show address error state`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            init()
            whenever(locationRepository.getDefaultLocationId(any())).thenReturn(
                CardReaderLocationRepository.LocationIdFetchingResult.Error.MissingAddress("")
            )

            (viewModel.viewStateData.value as ReaderFoundState).onPrimaryActionClicked.invoke()

            verify(cardReaderManager, never()).startConnectionToReader(reader, locationId)
            assertThat(viewModel.viewStateData.value).isInstanceOf(
                MissingMerchantAddressError::class.java
            )
        }

    @Test
    fun `given location fetching invalid postcode, when user clicks on connect button, then invalid pc error state`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            init()
            whenever(locationRepository.getDefaultLocationId(any())).thenReturn(
                CardReaderLocationRepository.LocationIdFetchingResult.Error.InvalidPostalCode
            )

            (viewModel.viewStateData.value as ReaderFoundState).onPrimaryActionClicked.invoke()

            verify(cardReaderManager, never()).startConnectionToReader(reader, locationId)
            assertThat(viewModel.viewStateData.value).isInstanceOf(
                InvalidMerchantAddressPostCodeError::class.java
            )
        }

    @Test
    fun `given address empty on wp com, when user clicks enter address, then opens authenticated webview`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            whenever(siteModel.isWPCom).thenReturn(true)
            init()
            val url = "https://wordpress.com"
            whenever(locationRepository.getDefaultLocationId(any())).thenReturn(
                CardReaderLocationRepository.LocationIdFetchingResult.Error.MissingAddress(url)
            )
            (viewModel.viewStateData.value as ReaderFoundState).onPrimaryActionClicked.invoke()

            (viewModel.viewStateData.value as MissingMerchantAddressError)
                .onPrimaryActionClicked.invoke()

            assertThat(viewModel.event.value).isInstanceOf(
                OpenWPComWebView::class.java
            )
            assertThat(
                (viewModel.event.value as OpenWPComWebView).url
            ).isEqualTo(url)
        }

    @Test
    fun `given address empty on atomic, when user clicks enter address, then opens authenticated webview`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            whenever(siteModel.isWPComAtomic).thenReturn(true)
            init()
            val url = "https://wordpress.com"
            whenever(locationRepository.getDefaultLocationId(any())).thenReturn(
                CardReaderLocationRepository.LocationIdFetchingResult.Error.MissingAddress(url)
            )
            (viewModel.viewStateData.value as ReaderFoundState).onPrimaryActionClicked.invoke()

            (viewModel.viewStateData.value as MissingMerchantAddressError)
                .onPrimaryActionClicked.invoke()

            assertThat(viewModel.event.value).isInstanceOf(
                OpenWPComWebView::class.java
            )
            assertThat(
                (viewModel.event.value as OpenWPComWebView).url
            ).isEqualTo(url)
        }

    @Test
    fun `given address empty on selfhosted, when user clicks enter address, then opens unauthenticated webview`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            val events = mutableListOf<Event>()
            viewModel.event.observeForever {
                events.add(it)
            }

            whenever(siteModel.isWPComAtomic).thenReturn(false)
            whenever(siteModel.isWPCom).thenReturn(false)
            init()
            val url = "https://wordpress.com"
            whenever(locationRepository.getDefaultLocationId(any())).thenReturn(
                CardReaderLocationRepository.LocationIdFetchingResult.Error.MissingAddress(url)
            )
            (viewModel.viewStateData.value as ReaderFoundState).onPrimaryActionClicked.invoke()

            (viewModel.viewStateData.value as MissingMerchantAddressError)
                .onPrimaryActionClicked.invoke()

            assertThat(events[events.size - 2]).isInstanceOf(
                OpenGenericWebView::class.java
            )
            assertThat(
                (events[events.size - 2] as OpenGenericWebView).url
            ).isEqualTo(url)
        }

    @Test
    fun `given address empty on selfhosted, when user clicks enter address, then emits exit event`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            whenever(siteModel.isWPComAtomic).thenReturn(false)
            whenever(siteModel.isWPCom).thenReturn(false)
            init()
            val url = "https://wordpress.com"
            whenever(locationRepository.getDefaultLocationId(any())).thenReturn(
                CardReaderLocationRepository.LocationIdFetchingResult.Error.MissingAddress(url)
            )
            (viewModel.viewStateData.value as ReaderFoundState).onPrimaryActionClicked.invoke()
            (viewModel.viewStateData.value as MissingMerchantAddressError)
                .onPrimaryActionClicked.invoke()

            assertThat(viewModel.event.value).isEqualTo(Event.ExitWithResult(false))
        }

    @Test
    fun `when user clicks on connect to reader button, then app starts connecting to reader`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            init()

            (viewModel.viewStateData.value as ReaderFoundState).onPrimaryActionClicked.invoke()

            verify(cardReaderManager).startConnectionToReader(reader, locationId)
        }

    @Test
    fun `when user clicks on connect to reader button, then card reader model stored for tracking`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            val readerType = "STRIPE_M2"
            whenever(reader.type).thenReturn(readerType)

            init()

            (viewModel.viewStateData.value as ReaderFoundState).onPrimaryActionClicked.invoke()

            verify(cardReaderTrackingInfoKeeper, times(2)).setCardReaderModel(readerType)
        }

    @Test
    fun `given multiple readers found, when user clicks on connect, then app connects to the correct reader`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            init(scanState = MULTIPLE_READERS_FOUND)

            val reader = (viewModel.viewStateData.value as MultipleReadersFoundState).listItems[1] as CardReaderListItem
            reader.onConnectClicked()

            verify(cardReaderManager).startConnectionToReader(argThat { this.id == reader.readerId }, eq(locationId))
        }

    @Test
    fun `given card reader has location id, when connect to, then readers location id used`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            init(scanState = MULTIPLE_READERS_FOUND)
            val locationId = "old_location_id"
            whenever(reader2.locationId).thenReturn(locationId)

            val reader = (viewModel.viewStateData.value as MultipleReadersFoundState).listItems[1] as CardReaderListItem
            reader.onConnectClicked()

            verify(cardReaderManager).startConnectionToReader(argThat { this.id == reader.readerId }, eq(locationId))
        }

    @Test
    fun `when multiple readers found, then scanning in progress item shown`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            init(scanState = MULTIPLE_READERS_FOUND)

            assertThat((viewModel.viewStateData.value as MultipleReadersFoundState).listItems.last())
                .isInstanceOf(ScanningInProgressListItem::class.java)
        }

    @Test
    fun `given user clicks on connect, when reader found, then event tracked`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            init(scanState = READER_FOUND)

            (viewModel.viewStateData.value as ReaderFoundState).onPrimaryActionClicked.invoke()

            verify(tracker).trackOnConnectTapped()
        }

    @Test
    fun `given user clicks on connect, when multiple readers found, then event tracked`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            init(scanState = MULTIPLE_READERS_FOUND)

            val reader = (viewModel.viewStateData.value as MultipleReadersFoundState).listItems[1] as CardReaderListItem
            reader.onConnectClicked()

            verify(tracker).trackOnConnectTapped()
        }

    @Test
    fun `when app is connecting to reader, then connecting state shown`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            init()

            (viewModel.viewStateData.value as ReaderFoundState).onPrimaryActionClicked.invoke()
            readerStatusFlow.emit(CardReaderStatus.Connecting)

            assertThat(viewModel.viewStateData.value).isInstanceOf(ConnectingState::class.java)
        }

    @Test
    fun `when app successfully connects to reader, then connection flow finishes`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            init()

            (viewModel.viewStateData.value as ReaderFoundState).onPrimaryActionClicked.invoke()
            readerStatusFlow.emit(CardReaderStatus.Connected(reader))

            assertThat(viewModel.event.value).isEqualTo(Event.ExitWithResult(true))
        }

    @Test
    fun `when app successfully connects to reader, then reader id stored`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            init()

            (viewModel.viewStateData.value as ReaderFoundState).onPrimaryActionClicked.invoke()
            readerStatusFlow.emit(CardReaderStatus.Connected(reader))

            verify(appPrefs).setLastConnectedCardReaderId("Dummy1")
        }

    @Test
    fun `when connecting to reader succeeds, then event tracked`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            init()
            (viewModel.viewStateData.value as ReaderFoundState).onPrimaryActionClicked.invoke()
            readerStatusFlow.emit(CardReaderStatus.Connected(reader))

            verify(tracker).trackConnectionSucceeded()
        }

    @Test
    fun `when connecting to reader for the first time, then the tutorial shows`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            whenever(appPrefs.getShowCardReaderConnectedTutorial()).thenReturn(true)
            init()
            (viewModel.viewStateData.value as ReaderFoundState).onPrimaryActionClicked.invoke()
            readerStatusFlow.emit(CardReaderStatus.Connected(reader))
            assertThat(viewModel.event.value).isInstanceOf(ShowCardReaderTutorial::class.java)
        }

    @Test
    fun `when connecting to reader not for the first time, then the tutorial doesn't show`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            whenever(appPrefs.getShowCardReaderConnectedTutorial()).thenReturn(false)
            init()
            (viewModel.viewStateData.value as ReaderFoundState).onPrimaryActionClicked.invoke()
            readerStatusFlow.emit(CardReaderStatus.Connected(reader))
            assertThat(viewModel.event.value).isEqualTo(Event.ExitWithResult(true))
        }

    @Test
    fun `when connecting to reader fails, then connecting failed state shown`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            init()

            (viewModel.viewStateData.value as ReaderFoundState).onPrimaryActionClicked.invoke()
            readerStatusFlow.emit(CardReaderStatus.Connecting)
            readerStatusFlow.emit(CardReaderStatus.NotConnected())

            assertThat(viewModel.viewStateData.value).isInstanceOf(ConnectingFailedState::class.java)
        }

    @Test
    fun `given error message is not null, when connecting to reader fails, then toast is shown`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            val errorMessage = "error_message"

            init()

            (viewModel.viewStateData.value as ReaderFoundState).onPrimaryActionClicked.invoke()
            readerStatusFlow.emit(CardReaderStatus.Connecting)
            readerStatusFlow.emit(CardReaderStatus.NotConnected(errorMessage))

            assertThat(viewModel.event.value).isEqualTo(ShowToastString(errorMessage))
        }

    @Test
    fun `given error message is null, when connecting to reader fails, then toast is not shown`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            init()

            (viewModel.viewStateData.value as ReaderFoundState).onPrimaryActionClicked.invoke()
            readerStatusFlow.emit(CardReaderStatus.Connecting)
            readerStatusFlow.emit(CardReaderStatus.NotConnected())

            assertThat(viewModel.event.value).isNotInstanceOf(ShowToastString::class.java)
        }

    @Test
    fun `when connecting to reader fails, then event tracked`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            init()
            (viewModel.viewStateData.value as ReaderFoundState).onPrimaryActionClicked.invoke()
            readerStatusFlow.emit(CardReaderStatus.Connecting)
            readerStatusFlow.emit(CardReaderStatus.NotConnected())

            verify(tracker).trackConnectionFailed()
        }

    @Test
    fun `given connecting failed screen shown, when user clicks on retry, then flow restarted`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            init()
            (viewModel.viewStateData.value as ReaderFoundState).onPrimaryActionClicked.invoke()
            readerStatusFlow.emit(CardReaderStatus.Connecting)
            readerStatusFlow.emit(CardReaderStatus.NotConnected())

            (viewModel.viewStateData.value as ConnectingFailedState).onPrimaryActionClicked()

            assertThat(viewModel.viewStateData.value).isInstanceOf(ScanningState::class.java)
        }

    @Test
    fun `given invalid postcode screen shown, when user clicks on retry, then flow restarted`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            init()

            whenever(locationRepository.getDefaultLocationId(any())).thenReturn(
                CardReaderLocationRepository.LocationIdFetchingResult.Error.InvalidPostalCode
            )

            (viewModel.viewStateData.value as ReaderFoundState).onPrimaryActionClicked.invoke()

            (viewModel.viewStateData.value as InvalidMerchantAddressPostCodeError).onPrimaryActionClicked()

            assertThat(viewModel.viewStateData.value).isInstanceOf(ScanningState::class.java)
        }

    @Test
    fun `given app in scanning state, when user clicks on cancel, then flow finishes`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            init(scanState = SCANNING)

            (viewModel.viewStateData.value as ScanningState).onSecondaryActionClicked.invoke()

            assertThat(viewModel.event.value).isEqualTo(Event.ExitWithResult(false))
        }

    @Test
    fun `given app in reader found state, when user clicks on cancel, then flow finishes`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            init(scanState = READER_FOUND)

            (viewModel.viewStateData.value as ReaderFoundState).onSecondaryActionClicked.invoke()

            assertThat(viewModel.event.value).isEqualTo(Event.ExitWithResult(false))
        }

    @Test
    fun `given app in connecting state, when user clicks on cancel, then flow finishes`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            init(scanState = READER_FOUND)

            (viewModel.viewStateData.value as ReaderFoundState).onPrimaryActionClicked.invoke()
            readerStatusFlow.emit(CardReaderStatus.Connecting)
            (viewModel.viewStateData.value as ConnectingState).onSecondaryActionClicked.invoke()

            assertThat(viewModel.event.value).isEqualTo(Event.ExitWithResult(false))
        }

    @Test
    fun `given app in scanning failed state, when user clicks on cancel, then flow finishes`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            init(scanState = FAILED)

            (viewModel.viewStateData.value as ScanningFailedState).onSecondaryActionClicked.invoke()
        }

    @Test
    fun `given app in connecting failed state, when user clicks on cancel, then flow finishes`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            init()
            (viewModel.viewStateData.value as ReaderFoundState).onPrimaryActionClicked.invoke()
            readerStatusFlow.emit(CardReaderStatus.Connecting)
            readerStatusFlow.emit(CardReaderStatus.NotConnected())

            (viewModel.viewStateData.value as ConnectingFailedState).onSecondaryActionClicked()

            assertThat(viewModel.event.value).isEqualTo(Event.ExitWithResult(false))
        }

    @Test
    fun `when app in scanning state, then correct labels and illustrations shown`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            init(scanState = SCANNING)

            assertThat(viewModel.viewStateData.value).isInstanceOf(ScanningState::class.java)
            assertThat(viewModel.viewStateData.value!!.headerLabel)
                .describedAs("Check header")
                .isEqualTo(UiStringRes(R.string.card_reader_connect_scanning_header))
            assertThat(viewModel.viewStateData.value!!.hintLabel)
                .describedAs("Check hint")
                .isEqualTo(R.string.card_reader_connect_scanning_hint)
            assertThat(viewModel.viewStateData.value!!.primaryActionLabel)
                .describedAs("Check primaryActionLabel")
                .isNull()
            assertThat(viewModel.viewStateData.value!!.secondaryActionLabel)
                .describedAs("Check secondaryActionLabel")
                .isEqualTo(R.string.cancel)
            assertThat(viewModel.viewStateData.value!!.illustration)
                .describedAs("Check illustration")
                .isEqualTo(R.drawable.img_card_reader_scanning)
            assertThat(viewModel.viewStateData.value!!.illustrationTopMargin)
                .describedAs("Check illustration vertical margin")
                .isEqualTo(R.dimen.major_200)
        }

    @Test
    fun `when app in readers found state, then correct labels and illustrations shown`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            init(scanState = READER_FOUND)

            assertThat(viewModel.viewStateData.value).isInstanceOf(ReaderFoundState::class.java)
            assertThat(viewModel.viewStateData.value!!.headerLabel)
                .describedAs("Check header")
                .isEqualTo(
                    UiStringRes(
                        R.string.card_reader_connect_reader_found_header,
                        listOf(UiStringText("<b>${reader.id}</b>")),
                        true
                    )
                )
            assertThat(viewModel.viewStateData.value!!.hintLabel)
                .describedAs("Check hint")
                .isNull()
            assertThat(viewModel.viewStateData.value!!.primaryActionLabel)
                .describedAs("Check primaryActionLabel")
                .isEqualTo(R.string.card_reader_connect_to_reader)
            assertThat(viewModel.viewStateData.value!!.secondaryActionLabel)
                .describedAs("Check secondaryActionLabel")
                .isEqualTo(R.string.cancel)
            assertThat(viewModel.viewStateData.value!!.illustration)
                .describedAs("Check illustration")
                .isEqualTo(R.drawable.img_card_reader)
            assertThat(viewModel.viewStateData.value!!.illustrationTopMargin)
                .describedAs("Check illustration vertical margin")
                .isEqualTo(R.dimen.major_275)
        }

    @Test
    fun `when app in connecting state, then correct labels and illustrations shown`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            init(scanState = READER_FOUND)

            viewModel.viewStateData.value!!.onPrimaryActionClicked!!.invoke()
            readerStatusFlow.emit(CardReaderStatus.Connecting)

            assertThat(viewModel.viewStateData.value).isInstanceOf(ConnectingState::class.java)
            assertThat(viewModel.viewStateData.value!!.headerLabel)
                .describedAs("Check header")
                .isEqualTo(UiStringRes(R.string.card_reader_connect_connecting_header))
            assertThat(viewModel.viewStateData.value!!.hintLabel)
                .describedAs("Check hint")
                .isEqualTo(R.string.card_reader_connect_connecting_hint)
            assertThat(viewModel.viewStateData.value!!.primaryActionLabel)
                .describedAs("Check primaryActionLabel")
                .isNull()
            assertThat(viewModel.viewStateData.value!!.secondaryActionLabel)
                .describedAs("Check secondaryActionLabel")
                .isEqualTo(R.string.cancel)
            assertThat(viewModel.viewStateData.value!!.illustration)
                .describedAs("Check illustration")
                .isEqualTo(R.drawable.img_card_reader_connecting)
            assertThat(viewModel.viewStateData.value!!.illustrationTopMargin)
                .describedAs("Check illustration vertical margin")
                .isEqualTo(R.dimen.major_275)
        }

    @Test
    fun `when app in scanning failed state, then correct labels and illustrations shown`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            init(scanState = FAILED)

            assertThat(viewModel.viewStateData.value).isInstanceOf(ScanningFailedState::class.java)
            assertThat(viewModel.viewStateData.value!!.headerLabel)
                .describedAs("Check header")
                .isEqualTo(UiStringRes(R.string.card_reader_connect_scanning_failed_header))
            assertThat(viewModel.viewStateData.value!!.primaryActionLabel)
                .describedAs("Check primaryActionLabel")
                .isEqualTo(R.string.try_again)
            assertThat(viewModel.viewStateData.value!!.secondaryActionLabel)
                .describedAs("Check secondaryActionLabel")
                .isEqualTo(R.string.cancel)
            assertThat(viewModel.viewStateData.value!!.illustration)
                .describedAs("Check illustration")
                .isEqualTo(R.drawable.img_products_error)
            assertThat(viewModel.viewStateData.value!!.illustrationTopMargin)
                .describedAs("Check illustration vertical margin")
                .isEqualTo(R.dimen.major_150)
        }

    @Test
    fun `when app in connecting failed state, then correct labels and illustrations shown`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            init(scanState = READER_FOUND)

            (viewModel.viewStateData.value as ReaderFoundState).onPrimaryActionClicked.invoke()
            readerStatusFlow.emit(CardReaderStatus.Connecting)
            readerStatusFlow.emit(CardReaderStatus.NotConnected())

            assertThat(viewModel.viewStateData.value).isInstanceOf(ConnectingFailedState::class.java)
            assertThat(viewModel.viewStateData.value!!.headerLabel)
                .describedAs("Check header")
                .isEqualTo(UiStringRes(R.string.card_reader_connect_failed_header))
            assertThat(viewModel.viewStateData.value!!.primaryActionLabel)
                .describedAs("Check primaryActionLabel")
                .isEqualTo(R.string.try_again)
            assertThat(viewModel.viewStateData.value!!.secondaryActionLabel)
                .describedAs("Check secondaryActionLabel")
                .isEqualTo(R.string.cancel)
            assertThat(viewModel.viewStateData.value!!.illustration)
                .describedAs("Check illustration")
                .isEqualTo(R.drawable.img_products_error)
            assertThat(viewModel.viewStateData.value!!.illustrationTopMargin)
                .describedAs("Check illustration vertical margin")
                .isEqualTo(R.dimen.major_150)
        }

    @Test
    fun `when app in missing address failed state, then correct labels and illustrations shown`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            init(scanState = READER_FOUND)
            readerStatusFlow.emit(CardReaderStatus.NotConnected())
            val url = "https://wordpress.com"
            whenever(locationRepository.getDefaultLocationId(any())).thenReturn(
                CardReaderLocationRepository.LocationIdFetchingResult.Error.MissingAddress(url)
            )

            (viewModel.viewStateData.value as ReaderFoundState).onPrimaryActionClicked.invoke()

            assertThat(viewModel.viewStateData.value).isInstanceOf(
                MissingMerchantAddressError::class.java
            )
            assertThat(viewModel.viewStateData.value!!.headerLabel)
                .isEqualTo(UiStringRes(R.string.card_reader_connect_missing_address))
            assertThat(viewModel.viewStateData.value!!.primaryActionLabel)
                .isEqualTo(R.string.card_reader_connect_missing_address_button)
            assertThat(viewModel.viewStateData.value!!.secondaryActionLabel)
                .isEqualTo(R.string.cancel)
            assertThat(viewModel.viewStateData.value!!.illustration)
                .isEqualTo(R.drawable.img_products_error)
            assertThat(viewModel.viewStateData.value!!.illustrationTopMargin)
                .isEqualTo(R.dimen.major_150)
        }

    @Test
    fun `given invalid postcode state, when connecting to reader, then correct labels and illustrations shown`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            init(scanState = READER_FOUND)
            readerStatusFlow.emit(CardReaderStatus.NotConnected())
            whenever(locationRepository.getDefaultLocationId(any())).thenReturn(
                CardReaderLocationRepository.LocationIdFetchingResult.Error.InvalidPostalCode
            )

            (viewModel.viewStateData.value as ReaderFoundState).onPrimaryActionClicked.invoke()

            assertThat(viewModel.viewStateData.value).isInstanceOf(
                InvalidMerchantAddressPostCodeError::class.java
            )
            assertThat(viewModel.viewStateData.value!!.headerLabel)
                .isEqualTo(UiStringRes(R.string.card_reader_connect_invalid_postal_code_header))
            assertThat(viewModel.viewStateData.value!!.hintLabel)
                .isEqualTo(R.string.card_reader_connect_invalid_postal_code_hint)
            assertThat(viewModel.viewStateData.value!!.primaryActionLabel)
                .isEqualTo(R.string.try_again)
            assertThat(viewModel.viewStateData.value!!.secondaryActionLabel).isNull()
            assertThat(viewModel.viewStateData.value!!.illustration)
                .isEqualTo(R.drawable.img_products_error)
            assertThat(viewModel.viewStateData.value!!.illustrationTopMargin)
                .isEqualTo(R.dimen.major_150)
        }

    @Test
    fun `when app in missing location permissions state, then correct labels and illustrations shown`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            (viewModel.event.value as CheckLocationPermissions).onLocationPermissionsCheckResult(false)
            (viewModel.event.value as RequestLocationPermissions).onPermissionsRequestResult(false)

            assertThat(viewModel.viewStateData.value).isInstanceOf(MissingLocationPermissionsError::class.java)
            assertThat(viewModel.viewStateData.value!!.headerLabel)
                .describedAs("Check header")
                .isEqualTo(UiStringRes(R.string.card_reader_connect_missing_permissions_header))
            assertThat(viewModel.viewStateData.value!!.hintLabel)
                .describedAs("Check hint")
                .isNull()
            assertThat(viewModel.viewStateData.value!!.primaryActionLabel)
                .describedAs("Check primaryActionLabel")
                .isEqualTo(R.string.card_reader_connect_open_permission_settings)
            assertThat(viewModel.viewStateData.value!!.secondaryActionLabel)
                .describedAs("Check secondaryActionLabel")
                .isEqualTo(R.string.cancel)
            assertThat(viewModel.viewStateData.value!!.illustration)
                .describedAs("Check illustration")
                .isEqualTo(R.drawable.img_products_error)
            assertThat(viewModel.viewStateData.value!!.illustrationTopMargin)
                .describedAs("Check illustration vertical margin")
                .isEqualTo(R.dimen.major_150)
        }

    @Test
    fun `when app in location disabled state, then correct labels and illustrations shown`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            (viewModel.event.value as CheckLocationPermissions).onLocationPermissionsCheckResult(true)
            (viewModel.event.value as CheckLocationEnabled).onLocationEnabledCheckResult(false)

            assertThat(viewModel.viewStateData.value).isInstanceOf(LocationDisabledError::class.java)
            assertThat(viewModel.viewStateData.value!!.headerLabel)
                .describedAs("Check header")
                .isEqualTo(UiStringRes(R.string.card_reader_connect_location_provider_disabled_header))
            assertThat(viewModel.viewStateData.value!!.hintLabel)
                .describedAs("Check hint")
                .isNull()
            assertThat(viewModel.viewStateData.value!!.primaryActionLabel)
                .describedAs("Check primaryActionLabel")
                .isEqualTo(R.string.card_reader_connect_open_location_settings)
            assertThat(viewModel.viewStateData.value!!.secondaryActionLabel)
                .describedAs("Check secondaryActionLabel")
                .isEqualTo(R.string.cancel)
            assertThat(viewModel.viewStateData.value!!.illustration)
                .describedAs("Check illustration")
                .isEqualTo(R.drawable.img_products_error)
            assertThat(viewModel.viewStateData.value!!.illustrationTopMargin)
                .describedAs("Check illustration vertical margin")
                .isEqualTo(R.dimen.major_150)
        }

    @Test
    fun `when app in bluetooth disabled state, then correct labels and illustrations shown`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            (viewModel.event.value as CheckLocationPermissions).onLocationPermissionsCheckResult(true)
            (viewModel.event.value as CheckLocationEnabled).onLocationEnabledCheckResult(true)
            (viewModel.event.value as CheckBluetoothPermissionsGiven).onBluetoothPermissionsGivenCheckResult(true)
            (viewModel.event.value as CheckBluetoothEnabled).onBluetoothCheckResult(false)
            (viewModel.event.value as RequestEnableBluetooth).onEnableBluetoothRequestResult(false)

            assertThat(viewModel.viewStateData.value).isInstanceOf(BluetoothDisabledError::class.java)
            assertThat(viewModel.viewStateData.value!!.headerLabel)
                .describedAs("Check header")
                .isEqualTo(UiStringRes(R.string.card_reader_connect_bluetooth_disabled_header))
            assertThat(viewModel.viewStateData.value!!.hintLabel)
                .describedAs("Check hint")
                .isNull()
            assertThat(viewModel.viewStateData.value!!.primaryActionLabel)
                .describedAs("Check primaryActionLabel")
                .isEqualTo(R.string.card_reader_connect_open_bluetooth_settings)
            assertThat(viewModel.viewStateData.value!!.secondaryActionLabel)
                .describedAs("Check secondaryActionLabel")
                .isEqualTo(R.string.cancel)
            assertThat(viewModel.viewStateData.value!!.illustration)
                .describedAs("Check illustration")
                .isEqualTo(R.drawable.img_products_error)
            assertThat(viewModel.viewStateData.value!!.illustrationTopMargin)
                .describedAs("Check illustration vertical margin")
                .isEqualTo(R.dimen.major_150)
        }

    @Test
    fun `when app bluetooth permission not given state, then correct labels and illustrations shown`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            (viewModel.event.value as CheckLocationPermissions).onLocationPermissionsCheckResult(true)
            (viewModel.event.value as CheckLocationEnabled).onLocationEnabledCheckResult(true)
            (viewModel.event.value as CheckBluetoothPermissionsGiven).onBluetoothPermissionsGivenCheckResult(false)
            (viewModel.event.value as RequestBluetoothRuntimePermissions)
                .onBluetoothRuntimePermissionsRequestResult(false)

            assertThat(viewModel.viewStateData.value).isInstanceOf(MissingBluetoothPermissionsError::class.java)
            assertThat(viewModel.viewStateData.value!!.headerLabel)
                .isEqualTo(UiStringRes(R.string.card_reader_connect_missing_bluetooth_permissions_header))
            assertThat(viewModel.viewStateData.value!!.hintLabel).isNull()
            assertThat(viewModel.viewStateData.value!!.primaryActionLabel)
                .isEqualTo(R.string.card_reader_connect_missing_bluetooth_permission_button)
            assertThat(viewModel.viewStateData.value!!.secondaryActionLabel)
                .isEqualTo(R.string.cancel)
            assertThat(viewModel.viewStateData.value!!.illustration)
                .isEqualTo(R.drawable.img_products_error)
            assertThat(viewModel.viewStateData.value!!.illustrationTopMargin)
                .isEqualTo(R.dimen.major_150)
        }

    @Test
    fun `given update reader result failed, when on update result called, then flow exits`() {
        val result = CardReaderUpdateViewModel.UpdateResult.FAILED

        viewModel.onUpdateReaderResult(result)

        assertThat(viewModel.event.value).isEqualTo(Event.ExitWithResult(false))
    }

    @Test
    fun `given update reader result failed, when on update result called, then toast event emitted`() {
        val result = CardReaderUpdateViewModel.UpdateResult.FAILED

        val events = mutableListOf<Event>()
        viewModel.event.observeForever {
            events.add(it)
        }

        viewModel.onUpdateReaderResult(result)

        assertThat(events[events.size - 2]).isEqualTo(
            ShowToast(
                R.string.card_reader_detail_connected_update_failed
            )
        )
    }

    @Test
    fun `given update reader result success, when on update result called, then event is check location`() {
        val result = CardReaderUpdateViewModel.UpdateResult.SUCCESS

        viewModel.onUpdateReaderResult(result)

        (viewModel.event.value as CheckLocationPermissions).onLocationPermissionsCheckResult(true)
    }

    @Test
    fun `when discovery readers, then supported readers list used`() {
        coroutinesTestRule.testDispatcher.runBlockingTest {
            val captor = argumentCaptor<CardReaderTypesToDiscover.SpecificReaders>()

            init()

            verify(cardReaderManager).discoverReaders(anyBoolean(), captor.capture())
            assertThat(captor.firstValue).isEqualTo(
                CardReaderTypesToDiscover.SpecificReaders(
                    listOf(
                        SpecificReader.Chipper2X, SpecificReader.StripeM2, SpecificReader.WisePade3
                    )
                )
            )
        }
    }

    private suspend fun initVM(
        onboardingState: CardReaderOnboardingState,
        skipOnboarding: Boolean = false
    ): CardReaderConnectViewModel {
        val savedState = CardReaderConnectDialogFragmentArgs(skipOnboarding = skipOnboarding).initSavedStateHandle()
        whenever(onboardingChecker.getOnboardingState()).thenReturn(onboardingState)
        return CardReaderConnectViewModel(
            savedState,
            coroutinesTestRule.testDispatchers,
            tracker,
            appPrefs,
            onboardingChecker,
            locationRepository,
            selectedSite,
            cardReaderManager,
            cardReaderTrackingInfoKeeper,
        )
    }

    private suspend fun init(scanState: ScanResult = READER_FOUND) {
        whenever(cardReaderManager.discoverReaders(anyBoolean(), any())).thenAnswer {
            flow {
                when (scanState) {
                    SCANNING -> { // no-op
                    }
                    READER_FOUND -> emit(ReadersFound(listOf(reader)))
                    MULTIPLE_READERS_FOUND -> emit(ReadersFound(listOf(reader, reader2)))
                    FAILED -> emit(Failed("dummy msg"))
                }
            }
        }
        whenever(locationRepository.getDefaultLocationId(any())).thenReturn(
            CardReaderLocationRepository.LocationIdFetchingResult.Success(locationId)
        )
        (viewModel.event.value as CheckLocationPermissions).onLocationPermissionsCheckResult(true)
        (viewModel.event.value as CheckLocationEnabled).onLocationEnabledCheckResult(true)
        (viewModel.event.value as CheckBluetoothPermissionsGiven).onBluetoothPermissionsGivenCheckResult(true)
        (viewModel.event.value as CheckBluetoothEnabled).onBluetoothCheckResult(true)
        whenever(appPrefs.getCardReaderPreferredPlugin(any(), any(), any())).thenReturn(
            PluginType.WOOCOMMERCE_PAYMENTS
        )
    }

    private enum class ScanResult {
        SCANNING, READER_FOUND, MULTIPLE_READERS_FOUND, FAILED
    }
}
