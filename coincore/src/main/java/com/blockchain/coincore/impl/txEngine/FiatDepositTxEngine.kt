package com.blockchain.coincore.impl.txEngine

import androidx.annotation.VisibleForTesting
import com.blockchain.banking.BankPartnerCallbackProvider
import com.blockchain.banking.BankPaymentApproval
import com.blockchain.banking.BankTransferAction
import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.nabu.models.data.BankPartner
import info.blockchain.balance.FiatValue
import info.blockchain.balance.Money
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import com.blockchain.coincore.AssetAction
import com.blockchain.coincore.BankAccount
import com.blockchain.coincore.FeeLevel
import com.blockchain.coincore.FeeSelection
import com.blockchain.coincore.FiatAccount
import com.blockchain.coincore.NeedsApprovalException
import com.blockchain.coincore.PendingTx
import com.blockchain.coincore.TxConfirmationValue
import com.blockchain.coincore.TxEngine
import com.blockchain.coincore.TxResult
import com.blockchain.coincore.TxValidationFailure
import com.blockchain.coincore.ValidationState
import com.blockchain.coincore.fiat.LinkedBankAccount
import com.blockchain.coincore.updateTxValidity
import com.blockchain.network.PollService
import java.security.InvalidParameterException

class FiatDepositTxEngine(
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val walletManager: CustodialWalletManager,
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val bankPartnerCallbackProvider: BankPartnerCallbackProvider
) : TxEngine() {

    override fun assertInputsValid() {
        check(sourceAccount is BankAccount)
        check(txTarget is FiatAccount)
    }

    override fun doInitialiseTx(): Single<PendingTx> {
        check(sourceAccount is BankAccount)
        check(txTarget is FiatAccount)
        val sourceAccountCurrency = (sourceAccount as LinkedBankAccount).fiatCurrency
        return walletManager.getBankTransferLimits(sourceAccountCurrency, true).map { limits ->
            val zeroFiat = FiatValue.zero(sourceAccountCurrency)
            PendingTx(
                amount = zeroFiat,
                totalBalance = zeroFiat,
                availableBalance = zeroFiat,
                feeForFullAvailable = zeroFiat,
                maxLimit = limits.max,
                minLimit = limits.min,
                feeAmount = zeroFiat,
                selectedFiat = userFiat,
                feeSelection = FeeSelection()
            )
        }
    }

    override val canTransactFiat: Boolean
        get() = true

    override fun doUpdateAmount(amount: Money, pendingTx: PendingTx): Single<PendingTx> =
        Single.just(
            pendingTx.copy(
                amount = amount
            )
        )

    override fun doBuildConfirmations(pendingTx: PendingTx): Single<PendingTx> {
        return Single.just(
            pendingTx.copy(
                confirmations = listOfNotNull(
                    TxConfirmationValue.PaymentMethod(
                        sourceAccount.label,
                        (sourceAccount as LinkedBankAccount).accountNumber,
                        (sourceAccount as LinkedBankAccount).accountType,
                        AssetAction.FiatDeposit
                    ),
                    TxConfirmationValue.To(txTarget, AssetAction.FiatDeposit),
                    if (!isOpenBankingCurrency()) {
                        TxConfirmationValue.EstimatedCompletion
                    } else null,
                    TxConfirmationValue.Amount(pendingTx.amount, true)
                )
            )
        )
    }

    override fun doUpdateFeeLevel(pendingTx: PendingTx, level: FeeLevel, customFeeAmount: Long): Single<PendingTx> {
        require(pendingTx.feeSelection.availableLevels.contains(level))
        // This engine only supports FeeLevel.None, so
        return Single.just(pendingTx)
    }

    override fun doValidateAmount(pendingTx: PendingTx): Single<PendingTx> {
        return if (pendingTx.validationState == ValidationState.UNINITIALISED && pendingTx.amount.isZero) {
            Single.just(pendingTx)
        } else {
            validateAmount(pendingTx).updateTxValidity(pendingTx)
        }
    }

    private fun validateAmount(pendingTx: PendingTx): Completable =
        Completable.fromCallable {
            if (pendingTx.maxLimit != null && pendingTx.minLimit != null) {
                when {
                    pendingTx.amount.isZero -> throw TxValidationFailure(ValidationState.INVALID_AMOUNT)
                    pendingTx.amount < pendingTx.minLimit -> throw TxValidationFailure(
                        ValidationState.UNDER_MIN_LIMIT
                    )
                    pendingTx.amount > pendingTx.maxLimit -> throw TxValidationFailure(
                        ValidationState.OVER_MAX_LIMIT
                    )
                    else -> Completable.complete()
                }
            } else {
                throw TxValidationFailure(ValidationState.UNKNOWN_ERROR)
            }
        }

    override fun doValidateAll(pendingTx: PendingTx): Single<PendingTx> =
        doValidateAmount(pendingTx).updateTxValidity(pendingTx)

    override fun doExecute(pendingTx: PendingTx, secondPassword: String): Single<TxResult> =
        sourceAccount.receiveAddress.flatMap {
            walletManager.startBankTransfer(
                it.address, pendingTx.amount, pendingTx.amount.currencyCode, if (isOpenBankingCurrency()) {
                    bankPartnerCallbackProvider.callback(BankPartner.YAPILY, BankTransferAction.PAY)
                } else null
            )
        }.map {
            TxResult.HashedTxResult(it, pendingTx.amount)
        }

    override fun doPostExecute(pendingTx: PendingTx, txResult: TxResult): Completable =
        if (isOpenBankingCurrency()) {
            val paymentId = (txResult as TxResult.HashedTxResult).txId
            PollService(walletManager.getBankTransferCharge(paymentId)) {
                it.authorisationUrl != null
            }.start().map { it.value }.flatMap { bankTransferDetails ->
                walletManager.getLinkedBank(bankTransferDetails.id).map { linkedBank ->
                    bankTransferDetails.authorisationUrl?.let {
                        BankPaymentApproval(
                            paymentId,
                            it,
                            linkedBank,
                            bankTransferDetails.amount
                        )
                    } ?: throw InvalidParameterException("No auth url was returned")
                }
            }.flatMapCompletable {
                Completable.error(NeedsApprovalException(it))
            }
        } else {
            Completable.complete()
        }

    private fun isOpenBankingCurrency(): Boolean {
        val sourceAccountCurrency = (sourceAccount as LinkedBankAccount).fiatCurrency
        return sourceAccountCurrency == "EUR" || sourceAccountCurrency == "GBP"
    }
}