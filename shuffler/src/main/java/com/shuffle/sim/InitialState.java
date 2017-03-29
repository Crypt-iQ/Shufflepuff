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
import com.shuffle.bitcoin.Crypto;
import com.shuffle.bitcoin.SigningKey;
import com.shuffle.bitcoin.Transaction;
import com.shuffle.bitcoin.VerificationKey;
import com.shuffle.chan.packet.Packet;
import com.shuffle.monad.Either;
import com.shuffle.p2p.Bytestring;
import com.shuffle.player.Messages;
import com.shuffle.player.Payload;
import com.shuffle.protocol.CoinShuffle;
import com.shuffle.protocol.FormatException;
import com.shuffle.protocol.MaliciousMachine;
import com.shuffle.protocol.blame.Evidence;
import com.shuffle.protocol.blame.Matrix;
import com.shuffle.protocol.blame.Reason;
import com.shuffle.protocol.message.MessageFactory;
import com.shuffle.sim.init.Initializer;
import com.shuffle.sim.init.Communication;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;

/**
 * A representation of an initial state for a protocol. Can specify various kinds of
 * malicious behavior.
 *
 * Created by Simulator on 2/8/16.
 */
public class InitialState {
    private static final Logger log = LogManager.getLogger(InitialState.class);

    // An expected return state that matches any blame matrix, even a null one.
    // Used for ensuring a test can't fail no matter what value
    // simulated adversaries return, since we only care about testing the response of the
    // honest players.
    public static final class ExpectedPatternAny extends Matrix {

        public ExpectedPatternAny() {

        }

        @Override
        public boolean match(Matrix m) {
            return true;
        }

        @Override
        public String toString() {
            return "Any";
        }
    }


    // An expected return state that matches any null blame matrix.
    public static final class ExpectedPatternNull extends Matrix {

        public ExpectedPatternNull() {

        }

        @Override
        public boolean match(Matrix m) {
            return m == null;
        }

        @Override
        public String toString() {
            return "Null";
        }
    }

    private static final class EvidencePatternAny extends Evidence {
        private EvidencePatternAny(VerificationKey accused) {
            super(accused, Reason.InsufficientFunds, null, null, null, null, null, null, null, null);

        }

        @Override
        public boolean match(Evidence e) {
            return true;
        }

        @Override
        public String toString() {
            return "Any";
        }
    }

    private static final class EvidencePatternOr extends Evidence {
        private final Evidence or;

        EvidencePatternOr(VerificationKey accused, Reason reason, Evidence or) {
            super(accused, reason);
            this.or = or;
        }

        @Override
        public boolean match(Evidence e) {

            if (super.match(e)) {
                return true;
            }

            if (or == null) {
                return e == null;
            }

            return or.match(e);
        }

        @Override
        public String toString() {
            return super.toString() + "|" + or;
        }
    }

    private static final ExpectedPatternAny anyMatrix = new ExpectedPatternAny();
    private static final ExpectedPatternNull nullMatrix = new ExpectedPatternNull();

    private final TestCase testCase;
    private final LinkedList<PlayerInitialState> players = new LinkedList<>();
    private MockCoin mockCoin = null;

    // The initial state of an individual player. (includes malicious players)
    public final class PlayerInitialState {
        final SigningKey sk;
        final VerificationKey vk;
        final SortedSet<VerificationKey> keys = new TreeSet<>();
        final Address addr;
        long initialAmount = 0;
        long spend = 0;
        long doubleSpend = 0;
        Address change = null; // A change address.
        int viewpoint = 1;
        boolean mutate = false; // Whether to create a mutated transaction.

        // Whether the adversary should equivocate during the announcement phase and to whom.
        int[] equivocateAnnouncement = new int[]{};

        // Whether the adversary should equivocate during the broadcast phase and to whom.
        int[] equivocateOutputVector = new int[]{};

        int drop = 0; // Whether to drop an address in phase 2.
        int duplicate = 0; // Whether to duplicate another address to replace it with.
        boolean replace = false; // Whether to replace dropped address with a new one.

        PlayerInitialState(SigningKey sk, Address addr) {
            this.sk = sk;
            this.addr = addr;
            vk = sk.VerificationKey();
        }

        PlayerInitialState(VerificationKey vk, Address addr) {
            this.addr = addr;
            this.sk = null;
            this.vk = vk;
        }

        public Bytestring getSession() {
            return InitialState.this.testCase.session;
        }

        public long getAmount() {
            return InitialState.this.testCase.amount;
        }

        public long getFee() {
            return InitialState.this.testCase.fee;
        }

        public Crypto crypto() {
            return InitialState.this.testCase.crypto();
        }

        public MockCoin coin() throws InterruptedException, ExecutionException, CoinNetworkException, FormatException {
            if (networkPoints == null) {
                networkPoints = new HashMap<>();
            }

            MockCoin coin = networkPoints.get(viewpoint);

            if (coin != null) {
                return coin;
            }

            if (mockCoin == null) {

                mockCoin = new com.shuffle.mock.MockCoin();

                for (PlayerInitialState player : players) {
                    if (player.initialAmount > 0) {
                        Address address = player.sk.VerificationKey().address();

                        SigningKey previous = testCase.crypto().makeSigningKey();

                        mockCoin.put(previous.VerificationKey().address(), player.initialAmount);
                        mockCoin.makeSpendingTransaction(
                                previous, address, player.initialAmount
                        ).send();

                        // Plot twist! We spend it all!
                        if (player.spend > 0) {
                            mockCoin.makeSpendingTransaction(
                                    player.sk,
                                    testCase.crypto().makeSigningKey().VerificationKey().address(),
                                    player.spend
                            ).send();
                        }
                    }
                }
            }

            MockCoin copy = mockCoin.copy();
            networkPoints.put(viewpoint, copy);
            return copy;
        }

        // Turn the initial state into an Adversary object that can be run in the simulator.
        public Adversary adversary(
                MessageFactory messages
        ) throws InterruptedException, ExecutionException, CoinNetworkException, FormatException {

            if (sk == null) {
                return null;
            }

            Address address = sk.VerificationKey().address();
            MockCoin coin = coin();
            CoinShuffle shuffle;

            if (equivocateAnnouncement != null && equivocateAnnouncement.length > 0) {
                shuffle = MaliciousMachine.announcementEquivocator(
                        messages, testCase.crypto(), coin, fromSet(keys, equivocateAnnouncement)
                );
            } else if (equivocateOutputVector != null && equivocateOutputVector.length > 0) {
                shuffle = MaliciousMachine.broadcastEquivocator(
                        messages, testCase.crypto(), coin, fromSet(keys, equivocateOutputVector)
                );
            } else if (replace && drop != 0) {
                shuffle = MaliciousMachine.addressReplacer(messages, testCase.crypto(), coin, drop);
            } else if (duplicate != 0 && drop != 0) {
                shuffle = MaliciousMachine.addressDropperDuplicator(
                        messages, testCase.crypto(), coin, drop, duplicate
                );
            } else if (drop != 0) {
                shuffle = MaliciousMachine.addressDropper(messages, testCase.crypto(), coin, drop);
            } else if (doubleSpend > 0) {
                // is he going to double spend? If so, make a new transaction for him.
                shuffle = MaliciousMachine.doubleSpender(messages, testCase.crypto(), coin,
                        coin.makeSpendingTransaction(sk,
                                testCase.crypto().makeSigningKey().VerificationKey().address(),
                                doubleSpend
                        )
                );
            } else if (mutate) {
                shuffle = new CoinShuffle(messages, testCase.crypto(), coin.mutated());
            } else {
                shuffle = new CoinShuffle(messages, testCase.crypto(), coin);
            }

            return new Adversary(testCase.amount, testCase.fee, sk, keys, addr, change, shuffle);
        }

        // The sort of malicious behavior to be performed by this player, if any.
        public Reason maliciousBehavior() {
            // Does the player have enough funds?
            if (initialAmount == 0) {
                return Reason.InsufficientFunds;
            }

            if (initialAmount < testCase.amount || initialAmount - spend < testCase.amount) {
                return Reason.InsufficientFunds;
            }

            // Is the player going to equivocate in phase 1?
            if (equivocateAnnouncement != null && equivocateAnnouncement.length > 0) {
                return Reason.EquivocationFailure;
            }

            // Is the player going to drop an address during the shuffle phase?
            if (drop != 0) {
                if (replace) {
                    return Reason.ShuffleFailure;
                }
                return Reason.ShuffleFailure;
            }

            // Is the player going to equivocate in phase 3?
            if (equivocateOutputVector != null && equivocateOutputVector.length > 0) {
                return Reason.EquivocationFailure;
            }

            // Is the player going to double spend?
            if (doubleSpend > 0) {
                return Reason.DoubleSpend;
            }

            // Is the player going to produce the wrong transaction?
            if (mutate) {
                return Reason.InvalidSignature;
            }

            return null;
        }

        // How is the player expected to interpret what happened during the protocol?
        public Matrix expected() {
            // Malicious players aren't tested, so they can blame anyone.
            Reason mal = maliciousBehavior();
            if (mal != null) {
                return anyMatrix;
            }

            Matrix bm = new Matrix();

            for (PlayerInitialState i : players) {
                for (PlayerInitialState j : players) {
                    // We don't care who malicious players blame.
                    if (i.maliciousBehavior() != null) {
                        bm.put(i.vk, new EvidencePatternAny(j.vk));
                        continue;
                    }

                    // Don't let players blame themselves!
                    if (i.equals(j)) {
                        continue;
                    }

                    Reason reason = j.maliciousBehavior();

                    if (reason == null) {
                        continue;
                    }

                    if (reason == Reason.DoubleSpend) {
                        if (i.viewpoint == j.viewpoint) {
                            bm.put(i.vk, Evidence.Expected(j.vk, reason));
                        } else {
                            bm.put(i.vk, new EvidencePatternOr(j.vk, reason, null));
                        }
                        continue;
                    }

                    if (reason == Reason.InsufficientFunds
                            || reason == Reason.InvalidSignature) {

                        bm.put(i.vk, Evidence.Expected(j.vk, reason));
                        continue;
                    }

                    if (equals(i)) {
                        bm.put(i.vk, Evidence.Expected(j.vk, reason));
                    }
                }
            }

            return (bm.isEmpty() ? nullMatrix : bm);
        }

        public String toString() {
            return "init["+sk+"]";
        }
    }

    public Map<SigningKey, Adversary> setup(
            Initializer<Packet<VerificationKey, Payload>> initializer)
            throws ExecutionException, InterruptedException, IOException {

        Map<SigningKey, Adversary> p = new HashMap<>();
        Map<SigningKey, Communication<Packet<VerificationKey, Payload>>> connections = new HashMap<>();

        for (final PlayerInitialState player : players) {
            if (player.sk == null) {
                continue;
            }

            connections.put(player.sk, initializer.connect(player.sk));
        }

        for (final PlayerInitialState player : players) {

            Communication<Packet<VerificationKey, Payload>> c = connections.get(player.sk);
            
            try {
                while (c.send.size() != players.size()-1);
                p.put(player.sk,
                        player.adversary(new Messages(testCase.session, player.sk, c.send, c.receive,
                                testCase.proto())));

            } catch (FormatException | CoinNetworkException | NoSuchAlgorithmException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }

        networkPoints = null;

        return p;
    }

    public List<VerificationKey> getKeys() {
        List<VerificationKey> keys = new LinkedList<>();

        for (PlayerInitialState player : players) {
            keys.add(player.vk);
        }

        return keys;
    }

    public PlayerInitialState getPlayer(int n) {
        return players.get(n);
    }

    public Map<VerificationKey, PlayerInitialState> getPlayers() {
        Map<VerificationKey, PlayerInitialState> map = new HashMap<>();
        for (PlayerInitialState p : players) {
            map.put(p.vk, p);
        }
        return map;
    }

    private Map<Integer, MockCoin> networkPoints = null;

    public InitialState(TestCase testCase) {

        this.testCase = testCase;
    }

    public InitialState player() {
        SigningKey key = testCase.crypto().makeSigningKey();
        Address addr = testCase.crypto().makeSigningKey().VerificationKey().address();

        PlayerInitialState next = new PlayerInitialState(key, addr);
        PlayerInitialState last = players.peekLast();
        if (last != null) {
            next.keys.addAll(last.keys);
        }
        players.addLast(next);

        for (PlayerInitialState player : players) {
            player.keys.add(key.VerificationKey());
        }
        return this;
    }

    public InitialState initialFunds(long amount) {
        players.getLast().initialAmount = amount;
        return this;
    }

    public InitialState spend(long amount) {
        players.getLast().spend = amount;
        return this;
    }

    public InitialState networkPoint(int i) {
        players.getLast().viewpoint = i;
        return this;
    }

    public InitialState doubleSpend(long amount) {
        players.getLast().doubleSpend = amount;
        return this;
    }

    public InitialState equivocateAnnouncement(int[] equivocate) {
        players.getLast().equivocateAnnouncement = equivocate;
        return this;
    }

    public InitialState equivocateOutputVector(int[] equivocate) {
        players.getLast().equivocateOutputVector = equivocate;
        return this;
    }

    public InitialState change(Address address) {
        players.getLast().change = address;
        return this;
    }

    public InitialState drop(int drop) {
        players.getLast().drop = drop;
        players.getLast().duplicate = 0;
        players.getLast().replace = false;
        return this;
    }

    public InitialState replace(int drop, int duplicate) {
        players.getLast().drop = drop;
        players.getLast().duplicate = duplicate;
        players.getLast().replace = false;
        return this;
    }

    public InitialState replace(int drop) {
        players.getLast().drop = drop;
        players.getLast().duplicate = 0;
        players.getLast().replace = true;
        return this;
    }

    public InitialState mutateTransaction() {
        players.getLast().mutate = true;
        return this;
    }

    public Map<SigningKey, Matrix> expected() {
        Map<SigningKey, Matrix> blame = new HashMap<>();

        for (PlayerInitialState player : players) {
            if (player.sk != null) {
                blame.put(player.sk, player.expected());
            }
        }

        return blame;
    }

    private Set<VerificationKey> fromSet(SortedSet<VerificationKey> identities, int[] array) {
        Set<VerificationKey> others = new TreeSet<>();

        int p = 1;
        int i = 0;
        for (VerificationKey player : identities) {
            while (i < array.length && array[i] < p) {
                i++;
            }

            if (i < array.length && array[i] == p) {
                others.add(player);
            }

            p++;
        }

        return others;
    }

    public Bytestring session() {
        return testCase.session;
    }

    public int size() {
        return players.size();
    }

    // Returns a map containing the set of results which did not match expectations. An empty map
    // represents a successful test.
    public Map<SigningKey, TestCase.Mismatch> run()
            throws ExecutionException, InterruptedException, IOException {

        // Create the simulator.
        Simulator sim;
        if (testCase.type == Initializer.Type.OTR || testCase.type == Initializer.Type.Marshall) {
            sim = new Simulator(testCase.type, testCase.proto().signedMarshaller);
        } else {
            sim = new Simulator(testCase.type);
        }

        // Run the simulation.
        Map<SigningKey, Either<Transaction, Matrix>> results = sim.run(this);

        if (results == null) {
            return null;
        }

        // Get the expected values.
        Map<SigningKey, Matrix> expected = expected();

        // The result to be returned.
        Map<SigningKey, TestCase.Mismatch> mismatch = new HashMap<>();

        // Check that the map of error states returned matches that which was expected.
        for (Map.Entry<SigningKey, Matrix> ex : expected.entrySet()) {
            SigningKey key = ex.getKey();
            Either<Transaction, Matrix> er = results.get(key);

            if (er == null) {
                return null;
            }

            Matrix result = er.second;
            Matrix expect = ex.getValue();

            if (!expect.match(result)) {
                log.error("  expected " + expect);
                log.error("  result   " + result);
                mismatch.put(key, new TestCase.Mismatch(key, expect, result));
            }
        }

        return mismatch;
    }
}
