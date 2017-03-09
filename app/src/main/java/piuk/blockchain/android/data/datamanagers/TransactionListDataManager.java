package piuk.blockchain.android.data.datamanagers;

import android.support.annotation.NonNull;

import android.util.Log;
import info.blockchain.wallet.multiaddress.TransactionSummary;
import info.blockchain.wallet.payload.PayloadManager;

import info.blockchain.wallet.payload.data.Account;
import info.blockchain.wallet.payload.data.LegacyAddress;
import java.math.BigInteger;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import piuk.blockchain.android.data.rxjava.RxUtil;
import piuk.blockchain.android.data.services.BlockExplorerService;
import piuk.blockchain.android.data.stores.TransactionListStore;
import piuk.blockchain.android.ui.account.ConsolidatedAccount;
import piuk.blockchain.android.ui.account.ConsolidatedAccount.Type;

public class TransactionListDataManager {

    private PayloadManager payloadManager;
    private TransactionListStore transactionListStore;
    private Subject<List<TransactionSummary>> listUpdateSubject;

    public TransactionListDataManager(PayloadManager payloadManager,
                                      TransactionListStore transactionListStore) {
        this.payloadManager = payloadManager;
        this.transactionListStore = transactionListStore;
        listUpdateSubject = PublishSubject.create();
    }

    public void generateTransactionList(Object object) {
        transactionListStore.clearList();

        if(object instanceof ConsolidatedAccount) {

            ConsolidatedAccount consolidate = (ConsolidatedAccount)object;

            if(consolidate.getType() == Type.ALL_ACCOUNTS) {
                transactionListStore.insertTransactions(payloadManager.getWalletTransactions());
            } else  if (consolidate.getType() == Type.ALL_IMPORTED_ADDRESSES) {
                transactionListStore.insertTransactions(payloadManager.getImportedAddressesTransactions());
            } else {
                Log.e(TransactionListDataManager.class.getSimpleName(), "getBtcBalance: " + object);
                return;
            }
        } else if (object instanceof Account) {
            // V3
            transactionListStore.insertTransactions(payloadManager.getAddressTransactions(((Account) object).getXpub()));
        } else if (object instanceof LegacyAddress) {
            // V2
            transactionListStore.insertTransactions(payloadManager.getAddressTransactions(((LegacyAddress) object).getAddress()));
        } else {
            Log.e(TransactionListDataManager.class.getSimpleName(), "getBtcBalance: " + object);
            return;
        }

        listUpdateSubject.onNext(transactionListStore.getList());
        listUpdateSubject.onComplete();
    }

    /**
     * Returns a list of {@link TransactionSummary} objects generated by {@link #getTransactionList()}
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
     * @return A BTC value as a double.
     */
    public double getBtcBalance(Object object) {

        long result = 0;

        if(object instanceof ConsolidatedAccount) {
            ConsolidatedAccount consolidate = (ConsolidatedAccount)object;

            if(consolidate.getType() == Type.ALL_ACCOUNTS) {
                result = payloadManager.getWalletBalance().longValue();
            } else if (consolidate.getType() == Type.ALL_IMPORTED_ADDRESSES) {
                result = payloadManager.getImportedAddressesBalance().longValue();
            } else {
                Log.e(TransactionListDataManager.class.getSimpleName(), "getBtcBalance: " + object);
            }
        } else if (object instanceof Account) {
            // V3
            result = payloadManager.getAddressBalance(((Account) object).getXpub()).longValue();
        } else if (object instanceof LegacyAddress) {
            // V2
            result = payloadManager.getAddressBalance(((LegacyAddress) object).getAddress()).longValue();
        } else {
            Log.e(TransactionListDataManager.class.getSimpleName(), "getBtcBalance: " + object);
        }

        // TODO: 03/03/2017  long to double, why?
        return (double)result;
    }

    public double getWalletBalance() {
        // TODO: 03/03/2017  long to double, why?
        return (double)payloadManager.getWalletBalance().longValue();
    }

    public double getImportedAddressesBalance() {
        // TODO: 03/03/2017  long to double, why?
        return (double)payloadManager.getImportedAddressesBalance().longValue();
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
}
