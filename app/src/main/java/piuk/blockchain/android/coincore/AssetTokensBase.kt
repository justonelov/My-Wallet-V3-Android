package piuk.blockchain.android.coincore

import info.blockchain.api.data.Transaction
import info.blockchain.balance.AccountReference
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.FiatValue
import info.blockchain.wallet.prices.TimeInterval
import io.reactivex.Single
import piuk.blockchain.androidcore.data.charts.PriceSeries
import piuk.blockchain.androidcore.data.charts.TimeSpan
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager

enum class BalanceFilter {
    Total,
    Wallet,
    ColdStorage,
//    Lockbox
//
//    fun entireBalance(): Single<CryptoValue>
//    fun watchOnlyBalance(): Single<CryptoValue>
//    fun importedAddressBalance(): Single<CryptoValue>
}

typealias TransactionList = List<Transaction>

// TODO: For account fetching/default accounts look steal the code from xxxAccountListAdapter in core

interface AssetTokens {
    val asset: CryptoCurrency

    fun defaultAccount(): Single<AccountReference>
//    fun accounts(): Single<AccountsList>

    fun totalBalance(filter: BalanceFilter = BalanceFilter.Total): Single<CryptoValue>
    fun balance(account: AccountReference): Single<CryptoValue>

    fun exchangeRate(): Single<FiatValue>
    fun historicRate(epochWhen: Long): Single<FiatValue>
    fun historicRateSeries(period: TimeSpan, interval: TimeInterval): Single<PriceSeries>

//    fun transactions(): Single<TransactionList>
//    fun transactions(account: AccountReference): Single<TransactionList>

//    interface PendingTransaction { }
//    fun computeFees(priority: FeePriority, pending: PendingTransaction): Single<PendingTransaction>
//    fun validate(pending: PendingTransaction): Boolean
//    fun execute(pending: PendingTransaction)
}

fun ExchangeRateDataManager.fetchLastPrice(
    cryptoCurrency: CryptoCurrency,
    currencyName: String
): Single<FiatValue> =
    updateTickers()
        .andThen(Single.just(getLastPrice(cryptoCurrency, currencyName)))
        .map { FiatValue.fromMajor(currencyName, it.toBigDecimal()) }
