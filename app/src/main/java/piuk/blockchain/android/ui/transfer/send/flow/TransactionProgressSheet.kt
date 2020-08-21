package piuk.blockchain.android.ui.transfer.send.flow

import android.util.DisplayMetrics
import android.view.View
import kotlinx.android.synthetic.main.dialog_send_in_progress.view.*
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.base.SlidingModalBottomDialog
import piuk.blockchain.android.ui.transfer.send.FlowInputSheet
import piuk.blockchain.android.ui.transfer.send.SendState
import piuk.blockchain.android.ui.transfer.send.SendStep
import piuk.blockchain.android.ui.transfer.send.TxExecutionStatus
import piuk.blockchain.android.util.maskedAsset
import timber.log.Timber

class TransactionProgressSheet(
    host: SlidingModalBottomDialog.Host
) : FlowInputSheet(host) {
    override val layoutResource: Int = R.layout.dialog_send_in_progress

    private val customiser: SendFlowCustomiser by inject()

    override fun render(newState: SendState) {
        Timber.d("!SEND!> Rendering! TransactionProgressSheet")
        require(newState.currentStep == SendStep.IN_PROGRESS)

        dialogView.send_tx_progress.setAssetIcon(newState.sendingAccount.asset.maskedAsset())

        when (newState.executionStatus) {
            TxExecutionStatus.IN_PROGRESS -> dialogView.send_tx_progress.showTxInProgress(
                customiser.transactionProgressTitle(newState),
                customiser.transactionProgressMessage(newState)
            )
            TxExecutionStatus.COMPLETED -> dialogView.send_tx_progress.showTxSuccess(
                customiser.transactionCompleteTitle(newState),
                customiser.transactionCompleteMessage(newState)
            )
            TxExecutionStatus.ERROR -> dialogView.send_tx_progress.showTxError(
                getString(R.string.send_progress_error_title),
                getString(R.string.send_progress_error_subtitle)
            )
            else -> {
            } // do nothing
        }
    }

    override fun initControls(view: View) {
        view.send_tx_progress.onCtaClick {
            dismiss()
        }

        // this is needed to show the expanded dialog, with space at the top and bottom
        val metrics = DisplayMetrics()
        requireActivity().windowManager?.defaultDisplay?.getMetrics(metrics)
        dialogView.layoutParams.height = (metrics.heightPixels - (48 * metrics.density)).toInt()
        dialogView.requestLayout()
    }
}