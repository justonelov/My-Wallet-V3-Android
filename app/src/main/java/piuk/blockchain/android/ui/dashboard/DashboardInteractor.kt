package piuk.blockchain.android.ui.dashboard

import com.blockchain.notifications.analytics.Analytics
import com.blockchain.notifications.analytics.SimpleBuyAnalytics
import com.blockchain.preferences.CurrencyPrefs
import com.blockchain.preferences.SimpleBuyPrefs
import com.blockchain.swap.nabu.datamanagers.CustodialWalletManager
import com.blockchain.swap.nabu.datamanagers.repositories.AssetBalancesRepository
import com.blockchain.swap.nabu.models.nabu.KycTierLevel
import com.blockchain.swap.nabu.service.TierService
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.FiatValue
import info.blockchain.balance.CryptoValue
import info.blockchain.wallet.payload.PayloadManager
import info.blockchain.wallet.prices.TimeInterval
import info.blockchain.wallet.prices.data.PriceDatum
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.Singles
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import piuk.blockchain.android.coincore.AssetFilter
import piuk.blockchain.android.coincore.Coincore
import piuk.blockchain.androidcore.data.charts.TimeSpan
import timber.log.Timber

class DashboardInteractor(
    private val coincore: Coincore,
    private val payloadManager: PayloadManager,
    private val custodialWalletManager: CustodialWalletManager,
    private val simpleBuyPrefs: SimpleBuyPrefs,
    private val analytics: Analytics,
    private val assetBalancesRepository: AssetBalancesRepository,
    private val currencyPrefs: CurrencyPrefs,
    private val tierService: TierService
) {

    // We have a problem here, in that pax init depends on ETH init
    // Ultimately, we want to init metadata straight after decrypting (or creating) the wallet
    // but we can't move that somewhere sensible yet, because 2nd password. When we remove that -
    // which is on the radar - then we can clean up the entire app init sequence.
    // But for now, we'll catch any pax init failure here, unless ETH has initialised OK. And when we
    // get a valid ETH balance, will try for a PX balance. Yeah, this is a nasty hack TODO: Fix this
    fun refreshBalances(model: DashboardModel, balanceFilter: AssetFilter): Disposable {
        val cd = CompositeDisposable()

        CryptoCurrency.activeCurrencies()
            .filter { it != CryptoCurrency.PAX }
            .forEach {
                cd += coincore[it].accountGroup(balanceFilter)
                    .flatMap { asset -> asset.balance }
                    .map { balance -> balance as CryptoValue }
                    .doOnSuccess { value ->
                        if (value.currency == CryptoCurrency.ETHER) {
                            cd += coincore[CryptoCurrency.PAX].accountGroup(balanceFilter)
                                .flatMap { asset -> asset.balance }
                                .subscribeBy(
                                    onSuccess = { balance ->
                                        Timber.d("*****> Got balance for PAX")
                                        model.process(BalanceUpdate(CryptoCurrency.PAX, balance))
                                    },
                                    onError = { e ->
                                        Timber.e("Failed getting balance for PAX: $e")
                                        model.process(BalanceUpdateError(CryptoCurrency.PAX))
                                    }
                                )
                        }
                    }
                    .doOnError { _ ->
                        if (it == CryptoCurrency.ETHER) {
                            // If we can't get ETH, then we can't get PAX... so...
                            model.process(BalanceUpdateError(CryptoCurrency.PAX))
                        }
                    }
                    .subscribeBy(
                        onSuccess = { balance ->
                            Timber.d("*****> Got balance for $it")
                            model.process(BalanceUpdate(it, balance))
                        },
                        onError = { e ->
                            Timber.e("Failed getting balance for $it: $e")
                            model.process(BalanceUpdateError(it))
                        }
                    )
            }

        cd += checkForFiatBalances(model)

        return cd
    }

    private fun checkForFiatBalances(model: DashboardModel): Disposable =
        tierService.tiers().flatMap { tier ->
            Singles.zip(
                assetBalancesRepository.getBalanceForAsset("EUR").toSingle(FiatValue.zero("EUR")),
                assetBalancesRepository.getBalanceForAsset("GBP").toSingle(FiatValue.zero("GBP")),
                custodialWalletManager.getSupportedFundsFiats(
                    currencyPrefs.selectedFiatCurrency,
                    tier.isApprovedFor(KycTierLevel.GOLD)
                )
            )
        }.subscribeBy(
            onSuccess = { (euroValue, gbpValue, supportedFunds) ->
                val fiatBalances = supportedFunds.map {
                    when (it) {
                        "EUR" -> euroValue
                        "GBP" -> gbpValue
                        else -> FiatValue.zero(it)
                    }
                }

                if (fiatBalances.isNotEmpty()) {
                    model.process(FiatBalanceUpdate(fiatBalances))
                }
            },
            onError = {
                Timber.e("Error while loading Funds balances $it")
            }
        )

    fun refreshPrices(model: DashboardModel, crypto: CryptoCurrency): Disposable {
        val oneDayAgo = (System.currentTimeMillis() / 1000) - ONE_DAY

        return Singles.zip(
            coincore[crypto].exchangeRate(),
            coincore[crypto].historicRate(oneDayAgo)
        ) { rate, day -> PriceUpdate(crypto, rate, day) }
            .subscribeBy(
                onSuccess = { model.process(it) },
                onError = { Timber.e(it) }
            )
    }

    fun refreshPriceHistory(model: DashboardModel, crypto: CryptoCurrency): Disposable =
        if (crypto.hasFeature(CryptoCurrency.PRICE_CHARTING)) {
            coincore[crypto].historicRateSeries(TimeSpan.DAY, TimeInterval.ONE_HOUR)
        } else {
            Single.just(FLATLINE_CHART)
        }
            .map { PriceHistoryUpdate(crypto, it) }
            .subscribeBy(
                onSuccess = { model.process(it) },
                onError = { Timber.e(it) }
            )

    fun checkForCustodialBalance(model: DashboardModel, crypto: CryptoCurrency): Disposable? {
        return coincore[crypto].accountGroup(AssetFilter.Custodial)
            .flatMap { it.balance }
            .subscribeBy(
                onSuccess = { model.process(UpdateHasCustodialBalanceIntent(crypto, !it.isZero)) },
                onError = { Timber.e(it) }
            )
    }

    fun hasUserBackedUp(model: DashboardModel): Disposable? {
        return Single.just(payloadManager.isWalletBackedUp)
            .subscribeBy(
                onSuccess = { model.process(BackupStatusUpdate(it)) },
                onError = { Timber.e(it) }
            )
    }

    fun cancelSimpleBuyOrder(orderId: String): Disposable? {
        return custodialWalletManager.deleteBuyOrder(orderId)
            .subscribeBy(
                onComplete = { simpleBuyPrefs.clearState() },
                onError = { error ->
                    analytics.logEvent(SimpleBuyAnalytics.BANK_DETAILS_CANCEL_ERROR)
                    Timber.e(error)
                }
            )
    }

    companion object {
        private const val ONE_DAY = 24 * 60 * 60L
        private val FLATLINE_CHART = listOf(
            PriceDatum(price = 1.0, timestamp = 0),
            PriceDatum(price = 1.0, timestamp = System.currentTimeMillis() / 1000)
        )
    }
}
