/**
 *
 * Copyright © 2016 Mycelium.
 * Use of this source code is governed by an ISC
 * license that can be found in the LICENSE file.
 *
 */

package com.shuffle.bitcoin;

import com.shuffle.p2p.Bytestring;
import com.shuffle.protocol.FormatException;

import java.io.Serializable;
import java.util.concurrent.ExecutionException;

/**
 * A representation of a Bitcoin or other cryptocurrency transaction.
 *
 * Created by Daniel Krawisz on 12/26/15.
 */
public interface Transaction extends Serializable {
    // Send the transaction into the network.
    void send() throws CoinNetworkException, ExecutionException, InterruptedException;

    Bytestring serialize();

    // Sign the transaction and return the input script.
    Signatures sign(SigningKey sk);

    boolean addInputScript(Bytestring b) throws FormatException;

    // Whether a transaction has enough signatures to be valid.
    boolean isValid();
}
