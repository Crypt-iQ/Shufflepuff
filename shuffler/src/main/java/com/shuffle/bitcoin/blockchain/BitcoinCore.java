package com.shuffle.bitcoin.blockchain;

/**
 * Created by nsa on 2/21/17.
 */

import com.neemre.btcdcli4j.core.BitcoindException;
import com.neemre.btcdcli4j.core.CommunicationException;
import com.neemre.btcdcli4j.core.domain.RawTransaction;
import com.shuffle.bitcoin.Address;
import com.shuffle.bitcoin.CoinNetworkException;
import com.shuffle.bitcoin.Transaction;
import com.shuffle.bitcoin.impl.AddressUtxoImpl;

import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.store.BlockStoreException;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import javax.xml.bind.annotation.adapters.HexBinaryAdapter;

/**
 * 1. We cannot lookup address balances with Bitcoin Core easily.  There is no address index.
 * 
 * 2. So, the users know each other's Bitcoin addresses - and send each other the UTXO(s) they want
 *    to spend from.
 */

public class BitcoinCore extends Bitcoin {

    private BtcdQueryClient client;

    public BitcoinCore(NetworkParameters netParams, String rpcuser, String rpcpass) throws MalformedURLException, BitcoindException, CommunicationException {
        // 0 because we connect to peers via BitcoinCore
        super(netParams, 0);

        Integer rpcport;

        if (netParams.equals(NetworkParameters.fromID(NetworkParameters.ID_MAINNET))) {
            rpcport = 8332;
        } else if (netParams.equals(NetworkParameters.fromID(NetworkParameters.ID_TESTNET))) {
            rpcport = 18332;
        } else {
            throw new IllegalArgumentException("Invalid network parameters passed to Bitcoin Core.");
        }

        client = new BtcdQueryClient("127.0.0.1", rpcport, rpcuser, rpcpass);
    }

    // TODO -- VERIFY {address, vout, & don't need mempool)
    public synchronized boolean isUtxo(String transactionHash, int vout) throws IOException, BitcoindException, CommunicationException {
        return client.getTxOut(transactionHash, vout, false);
    }

    public synchronized com.shuffle.bitcoin.Transaction getConflictingTransactionInner(com.shuffle.bitcoin.Transaction t, Address a, long amount)
        throws CoinNetworkException, AddressFormatException, BlockStoreException, BitcoindException, CommunicationException, IOException {

        // TODO
        /**
         *  Is this even possible with Bitcoin-Core ? We can only query the mempool, we can't
         *  realistically search the blockchain for a UTXO that was spent.  (Searching the
         *  blockchain for a spent UTXO would take far too long...)
         */
        return null;
    }

    public class TransactionWithConfirmations extends org.bitcoinj.core.Transaction {
        byte[] bytes;
        boolean confirmed;

        public TransactionWithConfirmations(NetworkParameters netParams, byte[] bytes, boolean confirmed) {
            super(netParams, bytes);
            this.bytes = bytes;
            this.confirmed = confirmed;
        }

        public boolean getConfirmed() {
            return this.confirmed;
        }

        public byte[] getBytes() {
            return this.bytes;
        }
    }

    /**
     * This method takes in a transaction hash and returns a bitcoinj transaction object.
     */
    public synchronized TransactionWithConfirmations getTransaction(String transactionHash) throws IOException {
        RawTransaction rx;
        Object rawObject;

        try {
            rawObject = client.getRawTransaction(transactionHash, 1);
        } catch (BitcoindException | CommunicationException e) {
            return null;
        }

        rx = (RawTransaction) rawObject;

        // only confirmed transactions
        int confirmations = rx.getConfirmations();
        boolean confirmed = (confirmations != 0);

        String rawHex = rx.getHex();
        HexBinaryAdapter adapter = new HexBinaryAdapter();
        byte[] bytearray = adapter.unmarshal(rawHex);
        TransactionWithConfirmations tx = new TransactionWithConfirmations(netParams, bytearray, confirmed);

        return tx;
    }

    /**
     * This method will take in a set of UTXOs (AddressUtxoImpl) and return a List of all associated transactions.
     */
    public synchronized List<Transaction> getTransactionsFromUtxosInner(AddressUtxoImpl a) throws IOException {
        List<Transaction> txList = new ArrayList<>();
        HashSet<Transaction> checkDuplicateTx = new HashSet<>();
        for (TransactionOutPoint tO : a.getUtxos()) {
            TransactionWithConfirmations tx;
            try {
                tx = getTransaction(tO.getHash().toString());
                String txid = tx.getHash().toString();
                byte[] bytes = tx.getBytes();
                boolean confirmed = tx.getConfirmed();
                org.bitcoinj.core.Transaction bitTx = new org.bitcoinj.core.Transaction(netParams, bytes);
                Transaction bTx = new Transaction(txid, bitTx, false, confirmed);
                if (!checkDuplicateTx.contains(bTx)) {
                    txList.add(bTx);
                }
                checkDuplicateTx.add(bTx);
            } catch (IOException e) {
                return null;
            }
        }

        return txList;
    }
}
