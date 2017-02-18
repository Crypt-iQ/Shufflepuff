/**
 *
 * Copyright © 2016 Mycelium.
 * Use of this source code is governed by an ISC
 * license that can be found in the LICENSE file.
 *
 */

package com.shuffle.sim;

import com.shuffle.bitcoin.Address;
import com.shuffle.bitcoin.CoinNetworkException;
import com.shuffle.bitcoin.SigningKey;
import com.shuffle.bitcoin.Transaction;
import com.shuffle.bitcoin.VerificationKey;
import com.shuffle.chan.BasicChan;
import com.shuffle.chan.Chan;
import com.shuffle.monad.Either;
import com.shuffle.monad.Summable;
import com.shuffle.monad.SummableMap;
import com.shuffle.monad.SummableMaps;
import com.shuffle.protocol.CoinShuffle;
import com.shuffle.protocol.FormatException;
import com.shuffle.protocol.InvalidParticipantSetException;
import com.shuffle.protocol.TimeoutException;
import com.shuffle.protocol.blame.Matrix;

import org.bitcoinj.core.AddressFormatException;

import java.io.IOException;
import java.util.Map;
import java.util.SortedSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * The adversary provides for an environment in which the protocol can run in a separate thread.
 * Messages can be delivered to it as it runs.
 *
 * Created by Daniel Krawisz on 2/8/16.
 */
public class Adversary {

    private final CoinShuffle shuffle;
    private final SigningKey sk;
    private final Address anon;
    private final SortedSet<VerificationKey> players;
    private final long amount;
    private final long fee;
    private final Address change; // Change address. (can be null)

    Adversary(
            long amount,
            long fee,
            SigningKey sk,
            SortedSet<VerificationKey> players,
            Address anon,
            Address change,
            CoinShuffle shuffle) {

        if (sk == null || players == null || shuffle == null || anon == null)
            throw new NullPointerException();

        this.amount = amount;
        this.fee = fee;
        this.sk = sk;
        this.players = players;
        this.shuffle = shuffle;
        this.anon = anon;
        this.change = change;
    }

    // Run the protocol in a separate thread and get a future to the final state.
    private Future<Summable.SummableElement<Map<SigningKey,
            Either<Transaction, Matrix>>>> runProtocolFuture(

            final CoinShuffle shuffle,
            final long amount, // The amount to be shuffled per player.
            final long fee, // A map of VerificationKey to miner fee (Long) per player. 
            final SigningKey sk, // The signing key of the current player.
            // The set of players, sorted alphabetically by address.
            final SortedSet<VerificationKey> players
    ) {
        final Chan<Either<Transaction, Matrix>> q = new BasicChan<>();

        if (amount <= 0) {
            throw new IllegalArgumentException();
        }
        if (sk == null || players == null) {
            throw new NullPointerException();
        }

        new Thread(new Runnable() {
            @Override
            public void run() {

                try {
                    try {
                        q.send(new Either<>(shuffle.runProtocol(
                                amount, fee, sk, players, null, anon, change, null
                        ), null));

                    } catch (Matrix m) {
                        q.send(new Either<>(null, m));
                    } catch (ExecutionException
                            | TimeoutException
                            | FormatException
                            | CoinNetworkException
                            | AddressFormatException
                            | InvalidParticipantSetException e) {

                        e.printStackTrace();

                    } finally {
                        q.close();
                    }

                }  catch (InterruptedException | IOException e) {
                    // This should not happen! Indicates that something
                    // went wrong with the environment our program is in.
                    e.printStackTrace();
                }

            }
        }).start();

        return new Future<Summable.SummableElement<Map<SigningKey, Either<Transaction, Matrix>>>>(
        ) {
            Summable.SummableElement<Map<SigningKey, Either<Transaction, Matrix>>> result = null;
            boolean done = false;

            @Override
            public boolean cancel(boolean b) {
                return false;
            }

            @Override
            public boolean isCancelled() {
                return false;
            }

            @Override
            public boolean isDone() {
                return done || q.closed();
            }

            private Summable.SummableElement<Map<SigningKey, Either<Transaction, Matrix>>> g(
                    Either<Transaction, Matrix> m
            ) {
                if (m == null) {
                    return new SummableMaps.Zero<>();
                }
                return new SummableMap<>(sk, m);
            }

            @Override
            public synchronized Summable.SummableElement<Map<SigningKey,
                    Either<Transaction, Matrix>>> get()
                    throws InterruptedException, ExecutionException {
                if (done) {
                    return result;
                }

                result = g(q.receive());
                done = true;
                return result;
            }

            @Override
            public synchronized Summable.SummableElement<Map<SigningKey,
                    Either<Transaction, Matrix>>> get(long l, TimeUnit timeUnit)
                    throws InterruptedException, ExecutionException,
                    java.util.concurrent.TimeoutException {

                if (done) {
                    return result;
                }

                Either<Transaction, Matrix> r = q.receive(l, timeUnit);

                if (r != null) {
                    result = g(r);
                    done = true;
                    return result;
                }

                if (q.closed()) {
                    done = true;
                }

                return null;
            }
        };
    }

    // Return a future that can be composed with others.
    public Future<Summable.SummableElement<Map<SigningKey, Either<Transaction, Matrix>>>> turnOn() {

        return runProtocolFuture(shuffle, amount, fee, sk, players);
    }

    public SigningKey identity() {
        return sk;
    }
}
