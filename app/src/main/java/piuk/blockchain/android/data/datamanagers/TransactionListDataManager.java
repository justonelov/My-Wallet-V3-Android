package piuk.blockchain.android.data.datamanagers;

import android.support.annotation.NonNull;
import android.util.Log;

import info.blockchain.wallet.multiaddress.TransactionSummary;
import info.blockchain.wallet.payload.PayloadManager;
import info.blockchain.wallet.payload.data.Account;
import info.blockchain.wallet.payload.data.LegacyAddress;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import piuk.blockchain.android.data.rxjava.RxUtil;
import piuk.blockchain.android.data.stores.TransactionListStore;
import piuk.blockchain.android.ui.account.ConsolidatedAccount;
import piuk.blockchain.android.ui.account.ConsolidatedAccount.Type;

public class TransactionListDataManager {

    private static final String TAG = TransactionListDataManager.class.getSimpleName();

    private PayloadManager payloadManager;
    private TransactionListStore transactionListStore;
    private Subject<List<TransactionSummary>> listUpdateSubject;

    public TransactionListDataManager(PayloadManager payloadManager,
                                      TransactionListStore transactionListStore) {
        this.payloadManager = payloadManager;
        this.transactionListStore = transactionListStore;
        listUpdateSubject = PublishSubject.create();
    }

    public Observable<List<TransactionSummary>> fetchTransactions(Object object, int limit, int offset) {
        return Observable.fromCallable(() -> {
            List<TransactionSummary> result = new ArrayList<>();

            if (object instanceof ConsolidatedAccount) {
                ConsolidatedAccount consolidate = (ConsolidatedAccount) object;
                if (consolidate.getType() == Type.ALL_ACCOUNTS) {
                    result = payloadManager.getAllTransactions(limit, offset);
                } else if (consolidate.getType() == Type.ALL_IMPORTED_ADDRESSES) {
                    result = payloadManager.getImportedAddressesTransactions(limit, offset);
                } else {
                    throw new IllegalArgumentException("ConsolidatedAccount did not have a type set");
                }
            } else if (object instanceof Account) {
                // V3
                result = payloadManager.getAccountTransactions(((Account) object).getXpub(), limit, offset);
            } else {
                throw new IllegalArgumentException("Cannot fetch transactions for object type: " + object.getClass().getSimpleName());
            }

            insertTransactionList(result);

            return result;
        }).compose(RxUtil.applySchedulersToObservable());
    }

    /**
     * Returns a list of {@link TransactionSummary} objects generated by {@link
     * #getTransactionList()}
     *
     * @return A list of Txs sorted by date.
     */
    @NonNull
    public List<TransactionSummary> getTransactionList() {
        return transactionListStore.getList();
    }

    /**
     * Resets the list of Transactions.
     */
    public void clearTransactionList() {
        transactionListStore.clearList();
    }

    /**
     * Allows insertion of a single new {@link TransactionSummary} into the main transaction list.
     *
     * @param transaction A new, most likely temporary {@link TransactionSummary}
     * @return An updated list of Txs sorted by date
     */
    @NonNull
    public List<TransactionSummary> insertTransactionIntoListAndReturnSorted(TransactionSummary transaction) {
        transactionListStore.insertTransactionIntoListAndSort(transaction);
        return transactionListStore.getList();
    }

    /**
     * Returns a subject that lets ViewModels subscribe to changes in the transaction list -
     * specifically this subject will return the transaction list when it's first updated and then
     * call onCompleted()
     *
     * @return The list of transactions after initial sync
     */
    public Subject<List<TransactionSummary>> getListUpdateSubject() {
        return listUpdateSubject;
    }

    /**
     * Get total BTC balance from an {@link Account} or {@link LegacyAddress}.
     *
     * @param object Either a {@link Account} or a {@link LegacyAddress}
     * @return A BTC value as a long.
     */
    public long getBtcBalance(Object object) {

        long result = 0;

        if (object instanceof ConsolidatedAccount) {
            ConsolidatedAccount consolidate = (ConsolidatedAccount) object;

            if (consolidate.getType() == Type.ALL_ACCOUNTS) {
                result = payloadManager.getWalletBalance().longValue();
            } else if (consolidate.getType() == Type.ALL_IMPORTED_ADDRESSES) {
                result = payloadManager.getImportedAddressesBalance().longValue();
            } else {
                Log.e(TAG, "ConsolidatedAccount did not have a type set");
            }
        } else if (object instanceof Account) {
            // V3
            result = payloadManager.getAddressBalance(((Account) object).getXpub()).longValue();
        } else if (object instanceof LegacyAddress) {
            // V2
            result = payloadManager.getAddressBalance(((LegacyAddress) object).getAddress()).longValue();
        } else {
            Log.e(TAG, "Cannot fetch transactions for object type: " + object.getClass().getSimpleName());
        }

        return result;
    }

    /**
     * Get a specific {@link TransactionSummary} from a hash
     *
     * @param transactionHash The hash of the Tx to be returned
     * @return An Observable object wrapping a Tx. Will call onError if not found with a
     * NullPointerException
     */
    public Observable<TransactionSummary> getTxFromHash(String transactionHash) {
        return Observable.create(emitter -> {
            //noinspection Convert2streamapi
            for (TransactionSummary tx : getTransactionList()) {
                if (tx.getHash().equals(transactionHash)) {
                    if (!emitter.isDisposed()) {
                        emitter.onNext(tx);
                        emitter.onComplete();
                    }
                    return;
                }
            }

            if (!emitter.isDisposed()) emitter.onError(new NullPointerException("Tx not found"));
        });
    }

    /**
     * Update notes for a specific transaction hash and then sync the payload to the server
     *
     * @param transactionHash The hash of the transaction to be updated
     * @param notes           Transaction notes
     * @return If save was successful
     */
    public Observable<Boolean> updateTransactionNotes(String transactionHash, String notes) {
        payloadManager.getPayload().getTxNotes().put(transactionHash, notes);
        return Observable.fromCallable(() -> payloadManager.save())
                .compose(RxUtil.applySchedulersToObservable());
    }

    private void insertTransactionList(List<TransactionSummary> txList) {
        clearTransactionList();
        transactionListStore.insertTransactions(txList);
        transactionListStore.sort(new TransactionSummary.TxMostRecentDateComparator());
        listUpdateSubject.onNext(transactionListStore.getList());
        listUpdateSubject.onComplete();
    }

}
