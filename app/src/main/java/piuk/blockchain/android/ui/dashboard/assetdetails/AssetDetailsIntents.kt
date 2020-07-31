package piuk.blockchain.android.ui.dashboard.assetdetails

import info.blockchain.balance.CryptoCurrency
import info.blockchain.wallet.prices.data.PriceDatum
import piuk.blockchain.android.coincore.AssetFilter
import piuk.blockchain.android.coincore.BlockchainAccount
import piuk.blockchain.android.coincore.CryptoAsset
import piuk.blockchain.android.ui.base.mvi.MviIntent
import piuk.blockchain.androidcore.data.charts.TimeSpan

sealed class AssetDetailsIntent : MviIntent<AssetDetailsState>

class ShowAssetActionsIntent(
    val account: BlockchainAccount,
    val assetFilter: AssetFilter
) : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState.copy(
            selectedAccount = account,
            assetDetailsCurrentStep = AssetDetailsStep.ASSET_ACTIONS,
            assetFilter = assetFilter
        )
}

class LoadAsset(
    val asset: CryptoAsset
) : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState.copy(asset = asset)
}

object LoadAssetDisplayDetails : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState = oldState
}

object LoadAssetFiatValue : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState = oldState
}

object LoadHistoricPrices : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState = oldState
}

class UpdateTimeSpan(
    val updatedTimeSpan: TimeSpan
) : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState.copy(timeSpan = updatedTimeSpan)
}

object ChartLoading : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState.copy(chartLoading = true)
}

class AssetExchangeRateLoaded(
    val exchangeRate: String
) : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState.copy(assetFiatValue = exchangeRate)
}

class AssetDisplayDetailsLoaded(
    val assetDisplayMap: AssetDisplayMap
) : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState.copy(assetDisplayMap = assetDisplayMap)
}

class ChartDataLoaded(
    val chartData: List<PriceDatum>
) : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState.copy(
            chartData = chartData,
            chartLoading = false
        )
}

object ShowCustodyIntroSheetIntent : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState.copy(assetDetailsCurrentStep = AssetDetailsStep.CUSTODY_INTRO_SHEET)
}

object ShowAssetDetailsIntent : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState.copy(assetDetailsCurrentStep = AssetDetailsStep.ASSET_DETAILS)
}

class ShowRelevantAssetDetailsSheet(
    val cryptoCurrency: CryptoCurrency
): AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState = oldState
}

object ReturnToPreviousStep : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState {
        val steps = AssetDetailsStep.values()
        val currentStep = oldState.assetDetailsCurrentStep.ordinal
        if (currentStep == 0) {
            throw IllegalStateException("Cannot go back")
        }
        val previousStep = steps[currentStep - 1]

        return oldState.copy(
            assetDetailsCurrentStep = previousStep
        )
    }
}
