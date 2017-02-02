package com.shuffle.bitcoin.impl;

import com.shuffle.bitcoin.Address;

import org.bitcoinj.core.TransactionOutPoint;

import java.util.HashSet;

/**
 * Created by nsa on 2/1/17.
 * 
 * AddressUtxoImpl is an implementation of the Address interface that contains a HashSet of
 * Unspent Transaction Outputs.  The use of this class allows a player to enter multiple UTXOs
 * into ShufflePuff.
 */
public class AddressUtxoImpl implements Address {

    HashSet<TransactionOutPoint> utxos;

    public AddressUtxoImpl(HashSet<TransactionOutPoint> utxos) {
        this.utxos = utxos;
    }

    public HashSet<TransactionOutPoint> getUtxos() {
        return utxos;
    }

    @Override
    public int hashCode() {
        return utxos.hashCode();
    }

    @Override
    public int compareTo(Address o) {
        return 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AddressUtxoImpl)) {
            return false;
        }

        AddressUtxoImpl o = (AddressUtxoImpl) obj;

        if (utxos.size() != o.utxos.size()) return false;

        for (TransactionOutPoint utxo : utxos) {
            if (!o.utxos.contains(utxo)) return false;
        }

        return true;
    }

}
