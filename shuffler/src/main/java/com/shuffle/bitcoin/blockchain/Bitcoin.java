/**
 *
 * Copyright © 2016 Mycelium.
 * Use of this source code is governed by an ISC
 * license that can be found in the LICENSE file.
 *
 */

package com.shuffle.bitcoin.blockchain;


import com.neemre.btcdcli4j.core.BitcoindException;
import com.neemre.btcdcli4j.core.CommunicationException;
import com.shuffle.bitcoin.Address;
import com.shuffle.bitcoin.Coin;
import com.shuffle.bitcoin.CoinNetworkException;
import com.shuffle.bitcoin.Signatures;
import com.shuffle.bitcoin.SigningKey;
import com.shuffle.bitcoin.VerificationKey;
import com.shuffle.bitcoin.impl.AddressUtxoImpl;
import com.shuffle.bitcoin.impl.SigningKeyImpl;
import com.shuffle.p2p.Bytestring;
import com.shuffle.protocol.FormatException;

import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.store.BlockStoreException;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public abstract class Bitcoin implements Coin {
    static long cache_expire = 10000; // Ten seconds.

    final NetworkParameters netParams;
    final PeerGroup peerGroup;
    final int minPeers;
    final Context context;

    /**
     *
     * The constructor takes in a NetworkParameters variable that determines whether we
     * are connecting to the Production Net or the Test Net.  It also takes in an int
     * which determines the minimum number of peers to connect to before broadcasting a transaction.
     *
     */

    public Bitcoin(NetworkParameters netParams, int minPeers) {
        this.netParams = netParams;
        this.minPeers = minPeers;
        if (minPeers == 0) {
            peerGroup = null;
        } else {
            peerGroup = new PeerGroup(netParams);
            peerGroup.setMinBroadcastConnections(minPeers);
            peerGroup.addPeerDiscovery(new DnsDiscovery(netParams));
            peerGroup.start();
        }
        this.context = Context.getOrCreate(this.netParams);
    }

    public class Transaction implements com.shuffle.bitcoin.Transaction {
        final String hash;
        private org.bitcoinj.core.Transaction bitcoinj;
        final boolean canSend;
        boolean confirmed;
        boolean sent = false;

        public Transaction(String hash, boolean canSend) {
            this.hash = hash;
            this.canSend = canSend;
        }

        public Transaction(String hash, org.bitcoinj.core.Transaction bitcoinj, boolean canSend) {
            this.hash = hash;
            this.bitcoinj = bitcoinj;
            this.canSend = canSend;
        }

        public Transaction(String hash, boolean canSend, boolean confirmed) {
            this.hash = hash;
            this.canSend = canSend;
            this.confirmed = confirmed;
        }

        public Transaction(String hash, org.bitcoinj.core.Transaction bitcoinj, boolean canSend, boolean confirmed) {
            this.hash = hash;
            this.bitcoinj = bitcoinj;
            this.canSend = canSend;
            this.confirmed = confirmed;
        }

        // Get the underlying bitcoinj representation of this transaction.
        public org.bitcoinj.core.Transaction bitcoinj() throws BlockStoreException, IOException {
            if (bitcoinj == null) {
                bitcoinj = getTransaction(hash);
            }

            return bitcoinj;
        }

        /**
         *
         * The send() method broadcasts a transaction into the Bitcoin network.  The canSend boolean
         * variable tells us if the transaction was created by us, or taken from the blockchain.
         * If we created the transaction, then we are able to broadcast it.  Otherwise, we cannot.
         *
         */

        @Override
        public synchronized void send()
                throws ExecutionException, InterruptedException, CoinNetworkException {

            if (!Bitcoin.this.send(this)) {
                throw new CoinNetworkException("Could not send transaction.");
            }
        }

        @Override
        public Bytestring serialize() {
            return new Bytestring(bitcoinj.bitcoinSerialize());
        }

        @Override
        public Signatures sign(SigningKey sk) {
            if (!(sk instanceof SigningKeyImpl)) {
                return null;
            }
            return Bitcoin.this.getSignature(this.bitcoinj, ((SigningKeyImpl) sk).signingKey);
        }

        @Override
        public boolean addInputScript(Bytestring b) throws FormatException {
            List<Bytestring> programSignatures = new LinkedList<>();
            programSignatures.add(b);
            return Bitcoin.this.signTransaction(this.bitcoinj, programSignatures) != null;
        }

        @Override
        public boolean isValid() {
            for (TransactionInput input : this.bitcoinj.getInputs()) {
                TransactionOutput output = input.getConnectedOutput();
                if (input.getScriptSig() == null) {
                    return false;
                }
                try {
                    input.verify(output);
                } catch (VerificationException e) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public String toString() {
            return hash;
        }
    }

    public PeerGroup getPeerGroup(){
        return peerGroup;
    }

    public com.shuffle.bitcoin.Transaction fromBytes(byte[] bytes) {
        org.bitcoinj.core.Transaction tx = new org.bitcoinj.core.Transaction(this.netParams, bytes);
        return new Transaction(tx.getHashAsString(), tx, false);
    }

    protected class Cached {
        public final Address a;
        public final List<Bitcoin.Transaction> txList;
        public final long last;

        private Cached(Address a, List<Bitcoin.Transaction> txList, long last) {
            this.a = a;
            this.txList = txList;
            this.last = last;
        }
    }

    protected Map<AddressUtxoImpl, Cached> cache = new HashMap<>();

    public NetworkParameters getNetParams(){
        return netParams;
    }

    /**
     *
     * The shuffleTransaction method returns a Bitcoin.Transaction object that contains a bitcoinj
     * Transaction member which sends "amount" satoshis from the addresses listed in the "from"
     * variable to addresses listed in the "to" variable, in their respective orders.  The bitcoinj
     * Transaction member also sends change from the addresses listed in the "from" variable to
     * addresses listed in the "changeAddresses" variable, in their respective order.
     *
     * To calculate the amount in change to send to the "changeAddresses", we get the summed balance of
     * UTXOs contained in each provided Address.  We then subtract the "amount" and "fee" (retrieved from
     * the playerFees Map) from this value - this amount is then sent to the changeAddress.
     */

    @Override
    public Bitcoin.Transaction shuffleTransaction(long amount,
                                                  Map<VerificationKey, Long> playerFees,
                                                  Map<VerificationKey, Address> peers,
                                                  Queue<Address> to,
                                                  Map<VerificationKey, Address> changeAddresses)
            throws CoinNetworkException, AddressFormatException {

        // this section adds inputs and outputs to the transaction being constructed.
        org.bitcoinj.core.Transaction tx = new org.bitcoinj.core.Transaction(netParams);
        HashMap<org.bitcoinj.core.Address, org.bitcoinj.core.Coin> map = new HashMap<>();

        for (Map.Entry<VerificationKey, Address> entry : peers.entrySet()) {

            VerificationKey key = entry.getKey();
            AddressUtxoImpl a = (AddressUtxoImpl) entry.getValue();
            long keyAmount = valueHeld(a);
            if (keyAmount >= amount + playerFees.get(key).intValue()) {
                try {
                    List<Bitcoin.Transaction> transactions = getTransactionsFromUtxos(a);

                    // sanitize transaction list to remove unconfirmed tx's
                    for (Iterator<Bitcoin.Transaction> it = transactions.iterator(); it.hasNext(); ) {
                        Bitcoin.Transaction tr = it.next();
                        if (!tr.confirmed) it.remove();
                    }

                    for (Bitcoin.Transaction t : transactions) {
                        for (TransactionOutput output : t.bitcoinj().getOutputs()) {
                            if (a.getUtxos().contains(output.getOutPointFor())) {
                                tx.addInput(output);
                            }
                        }
                    }

                    if (changeAddresses.get(key) != null) {
                        Address current = changeAddresses.get(key);
                        try {
                            // change amount is amount of ((all utxos of key) - (amount + playerFee)), in satoshis
                            org.bitcoinj.core.Coin coin = org.bitcoinj.core.Coin.valueOf(keyAmount).subtract(org.bitcoinj.core.Coin.SATOSHI.multiply(amount + playerFees.get(key)));
                            org.bitcoinj.core.Address addr = new org.bitcoinj.core.Address(netParams, current.toString());
                            // this map ensures we don't have duplicate change addresses if a player enters multiple utxos
                            if (map.containsKey(addr)) {
                                org.bitcoinj.core.Coin c = map.get(addr);
                                map.put(addr, coin.add(c));
                            } else {
                                map.put(addr, coin);
                            }
                        } catch (AddressFormatException e) {
                            throw new RuntimeException(e);
                        }
                    }

                } catch (IOException | BlockStoreException e) {
                    throw new CoinNetworkException("Could not generate shuffle tx: " + e.getMessage());
                }
            } else {
                throw new RuntimeException("UTXOs do not satisfy amount + fee");
            }

        }

        for (Address sendto : to) {
            String address = sendto.toString();
            try {
                tx.addOutput(org.bitcoinj.core.Coin.SATOSHI.multiply(amount),
                        new org.bitcoinj.core.Address(netParams, address));
            } catch (AddressFormatException e) {
                throw new RuntimeException(e);
            }
        }

        for (org.bitcoinj.core.Address addr : map.keySet()) {
            tx.addOutput(map.get(addr), addr);
        }

        // TODO
        // getHashAsString()
        return new Transaction(tx.getHashAsString(), tx, true);
    }

    /**
     * The valueHeld method takes in an Address variable and returns the balance held using
     * satoshis as the unit.
     */

    @Override
    public long valueHeld(Address a) throws CoinNetworkException, AddressFormatException {

        long sum = 0;

        for (TransactionOutPoint t : ((AddressUtxoImpl) a).getUtxos()) {
            try {
                sum += getUtxoBalance(t);
            } catch (IOException | BitcoindException | CommunicationException e) {
                throw new CoinNetworkException("Could not look up balance: " + e.getMessage());
            }
        }

        return sum;
    }

    /**
     * The getUtxoBalance method takes one UTXO and returns the amount of spendable Bitcoin that it holds.
     * This amount is a long value represented as the number of satoshis.
     */

    protected synchronized long getUtxoBalance(TransactionOutPoint t) throws IOException, CoinNetworkException, AddressFormatException, BitcoindException, CommunicationException {

        if (isUtxo(t.getHash().toString(), (int) t.getIndex())) {
            TransactionOutput tx = getTransaction(t.getHash().toString()).getOutput(t.getIndex());
            return tx.getValue().getValue();
        } else {
            return 0;
        }

    }

    @Override
    public final boolean sufficientFunds(Address a, long amount) throws CoinNetworkException, AddressFormatException {
        return valueHeld(a) >= amount;
    }

    @Override
    public synchronized com.shuffle.bitcoin.Transaction getConflictingTransaction(com.shuffle.bitcoin.Transaction transaction, Address a, long amount) 
            throws CoinNetworkException, AddressFormatException, BlockStoreException, BitcoindException, CommunicationException, IOException {
        return getConflictingTransactionInner(transaction, a, amount);
    }

    public org.bitcoinj.core.Transaction signTransaction(org.bitcoinj.core.Transaction signTx, List<Bytestring> programSignatures) {

        List<Script> inputScripts = new LinkedList<>();
        for (Bytestring programs : programSignatures) {
            if (!inputScripts.add(bytestringToInputScript(programs))) {
                return null;
            }
        }

        for (Script inScript : inputScripts) {
            for (int i = 0; i < signTx.getInputs().size(); i++) {
                TransactionInput input = signTx.getInput(i);
                TransactionOutput connectedOutput = input.getConnectedOutput();
                byte[] originalScript = input.getScriptBytes().clone();
                input.setScriptSig(inScript);
                try {
                    input.verify(connectedOutput);
                    break;
                } catch (VerificationException e) {
                    input.setScriptSig(this.bytestringToInputScript(new Bytestring(originalScript)));
                    if (i == signTx.getInputs().size() - 1) {
                        return null;
                    }
                }
            }
        }

        return signTx;
    }

    /**
     * Takes in a transaction and a private key and returns a signature (if possible)
     * as a Bytestring object.
     */
    public Signatures getSignature(org.bitcoinj.core.Transaction signTx, ECKey privKey) {

        org.bitcoinj.core.Transaction copyTx = signTx;

        HashSet<Bytestring> sigs = new HashSet<>();

        for (int i = 0; i < copyTx.getInputs().size(); i++) {
            TransactionInput input = copyTx.getInput(i);
            TransactionOutput connectedOutput = input.getConnectedOutput();
            Sha256Hash hash = copyTx.hashForSignature(i, connectedOutput.getScriptPubKey(), org.bitcoinj.core.Transaction.SigHash.ALL, false);
            ECKey.ECDSASignature ecSig = privKey.sign(hash);
            TransactionSignature txSig = new TransactionSignature(ecSig, org.bitcoinj.core.Transaction.SigHash.ALL, false);
            byte[] originalScript = input.getScriptBytes().clone();
            Script inputScript = ScriptBuilder.createInputScript(txSig, ECKey.fromPublicOnly(privKey.getPubKey()));
            input.setScriptSig(inputScript);
            try {
                input.verify(connectedOutput);
                sigs.add(new Bytestring(inputScript.getProgram()));
            } catch (VerificationException e) {
                input.setScriptSig(this.bytestringToInputScript(new Bytestring(originalScript)));
            }
        }

        Bytestring[] arr = new Bytestring[sigs.size()];

        int i = 0;
        for (Bytestring b : sigs) {
            arr[i] = b;
            i++;
        }

        return new Signatures(arr);

    }

    // Converts a Bytestring object to a Script object.
    public Script bytestringToInputScript(Bytestring program) {
        return new Script(program.bytes);
    }

    // Since we rely on 3rd party services to query the blockchain, by
    // default we cache the result.
    protected synchronized List<Bitcoin.Transaction> getTransactionsFromUtxos(AddressUtxoImpl a)
            throws IOException, CoinNetworkException, AddressFormatException {

        long now = System.currentTimeMillis();
        Cached cached = cache.get(a);
        if (cached != null) {
            if (now - cached.last < cache_expire) {
                return cached.txList;
            }
        }

        List<Bitcoin.Transaction> txList = getTransactionsFromUtxosInner(a);

        cache.put(a, new Cached(a, txList, System.currentTimeMillis()));

        return txList;
    }

    protected boolean send(Bitcoin.Transaction t) throws ExecutionException, InterruptedException, CoinNetworkException {
        if (!t.canSend || t.sent) {
            return false;
        }

        //checks to see if transaction was broadcast
        if (peerGroup == null) {
            return false;
        }

        peerGroup.broadcastTransaction(t.bitcoinj).future().get();
        t.sent = true;
        return true;
    }

    public static Map<VerificationKey, Long> getPlayersP2PKHFees(Map<VerificationKey, HashSet<TransactionOutPoint>> utxos, long satPerByte) {

        // TODO
        // compressed keys?

        Map<VerificationKey, Long> playerFees = new HashMap<>();
        Set<TransactionOutPoint> allUtxos = new HashSet<>();

        for (Set<TransactionOutPoint> t : utxos.values()) {
            allUtxos.addAll(t);
        }

        long totalBytes = 0;
        long totalFee;

        // version (4 bytes)
        totalBytes += 4;

        // txin count (# of UTXOs)
        totalBytes += 1;

        // txin
        // we are only spending from P2PKH addresses, so each input is 146 bytes in size
        totalBytes += allUtxos.size() * 146;

        // txout count (# of outputs)
        totalBytes += 1;

        // txout
        /*
         * we are only paying to P2PKH addresses, so the upper bound is 33 bytes
         * the size of 'utxos' is the number of players, we multiply by 2 because we assume
         * each player has a change address
         */
        totalBytes += utxos.size() * 2 * 33;

        // locktime (4 bytes)
        totalBytes += 4;

        totalFee = totalBytes * satPerByte;

        for (VerificationKey vk : utxos.keySet()) {
            // rough estimate (long / int)
            double ratio = ((double) utxos.get(vk).size()) / ((double) allUtxos.size());
            playerFees.put(vk, (long) (totalFee * ratio));
        }

        return playerFees;
    }

    // If there is a conflicting transaction in the mempool or blockchain, this function returns that transaction.
    abstract com.shuffle.bitcoin.Transaction getConflictingTransactionInner(com.shuffle.bitcoin.Transaction t, Address a, long amount) throws CoinNetworkException, AddressFormatException, BlockStoreException, BitcoindException, CommunicationException, IOException;

    abstract boolean isUtxo(String transactionHash, int vout) throws IOException, BitcoindException, CommunicationException;

    abstract protected List<Bitcoin.Transaction> getTransactionsFromUtxosInner(AddressUtxoImpl a)
            throws IOException, CoinNetworkException, AddressFormatException;

    // includes mempool transactions
    abstract org.bitcoinj.core.Transaction getTransaction(String transactionHash)
            throws IOException;
}
