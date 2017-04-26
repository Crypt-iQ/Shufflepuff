/**
 *
 * Copyright © 2016 Mycelium.
 * Use of this source code is governed by an ISC
 * license that can be found in the LICENSE file.
 *
 */

package com.shuffle.p2p;

import com.shuffle.bitcoin.Crypto;
import com.shuffle.chan.Inbox;
import com.shuffle.chan.Send;

import java.io.IOException;
import java.io.Serializable;
import java.net.ConnectException;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * A class for setting up Network objects. It manages setting up all all the necessary
 * connections between peers.
 *
 * Created by Daniel Krawisz on 2/16/16.
 */
public class Connect<Identity, P extends Serializable> implements Connection<Identity> {

    // The list of peers will be altered by two threads; the one for initiating connections
    // and the one for receiving connections. We set it in its own class to allow for some
    // synchronized functions.
    private class Peers {

        // The list of peers which we have not connected with yet.
        private final Queue<Identity> unconnected = new LinkedList<>();
        private final Set<Identity> remaining = new TreeSet<>();
        private final Object lock;

        private final Collector<Identity, P> collector;

        private Peers(Collector<Identity, P> collector, Object lock) {
            this.collector = collector;
            this.lock = lock;
        }

        public void queue(Identity identity) {
            if (identity == null) {
                throw new NullPointerException();
            }

            if (remaining.contains(identity)) return;

            unconnected.add(identity);
            remaining.add(identity);
        }

        public Identity peek() {
            return unconnected.peek();
        }

        boolean rotate() {
            Identity identity = unconnected.poll();
            if (identity == null) {
                return false;
            }

            unconnected.add(identity);
            return true;
        }

        private boolean connect(Session<Identity, P> session) throws InterruptedException {
            Identity addr = session.peer().identity();

            return remaining.remove(addr) && collector.put(session);

        }

        public synchronized boolean openSession(
                Identity identity,
                Peer<Identity, P> peer) throws InterruptedException, IOException {
            
            if (collector.connected.containsKey(identity)) {
                return false;
            }

            synchronized (lock) {

                if (collector.connected.containsKey(identity)) {
                    System.out.println("Collector contains " + identity + " " + System.currentTimeMillis() + " " + Thread.currentThread());
                    //remove();
                    //remaining.remove(identity);
                    return false;
                }
                
                Send<P> processor = collector.inbox.receivesFrom(identity);
                if (processor != null) {

                    System.out.println("start " + System.currentTimeMillis() + " " + Thread.currentThread());

                    Session<Identity, P> session = peer.openSession(processor);
                    
                    System.out.println("open " + System.currentTimeMillis() + " " + Thread.currentThread());
                    
                    if (collector.connected.containsKey(identity)) {
                        System.out.println("Collector number 2 contains " + identity + " " + System.currentTimeMillis() + " " + Thread.currentThread());
                    }

                    if (session != null) {
                        remove();
                        connect(session);
                        return true;
                    } else {
                        processor.close();
                        return false;
                    }
                }
            }

            return false;
        }

        boolean connected(Identity identity) {
            return collector.connected.containsKey(identity);
        }

        // Removes the first element from the list.
        void remove() {
            unconnected.remove();
        }

        @Override
        public String toString() {
            return unconnected.toString();
        }
    }

    // Keep track of the number of connection attempt retries for each address.
    private class Retries {
        final Map<Identity, Integer> retries = new HashMap<>();

        public int retries(Identity identity) {
            Integer r = retries.get(identity);
            if (r == null) {
                retries.put(identity, 0);
                return 0;
            }

            return r;
        }

        public int increment(Identity identity) {
            Integer r = retries.get(identity);

            if (r == null) {
                r = 0;
            }
            r++;

            retries.put(identity, r);
            return r;
        }
    }

    private final Channel<Identity, P> channel;
    private final Connection<Identity> connection;
    private final Collector<Identity, P> collector;
    private final Crypto crypto;

    private boolean finished = false;
    
    private final Object lock;

    public Connect(Channel<Identity, P> channel, Crypto crypto)
            throws InterruptedException, IOException {

        this(channel, crypto, 100);
    }

    public Connect(Channel<Identity, P> channel, Crypto crypto, int capacity)
            throws InterruptedException, IOException {

        if (channel == null || crypto == null) throw new NullPointerException();
        
        this.lock = new Object();

        collector = new Collector<>(new Inbox<Identity, P>(capacity), this.lock);

        connection = channel.open(collector);
        if (connection == null ) throw new IllegalArgumentException();

        this.channel = channel;
        this.crypto = crypto;
    }

    // Connect to all peers; remote peers can be initiating connections to us as well.
    public Collector<Identity, P> connect(
            SortedSet<Identity> addrs,
            int maxRetries) throws IOException, InterruptedException {

        if (addrs == null) throw new NullPointerException();

        if (finished) {
            connection.close();
            return null;
        }

        Peers peers = new Peers(this.collector, this.lock);

        // Randomly arrange the list of peers.
        // First, put all peers in an array.
        ArrayList<Identity> identities = new ArrayList<>();
        identities.addAll(addrs);

        // Then randomly select them one at a time and put them in peers.
        for (int rmax = addrs.size() - 1; rmax >= 0; rmax--) {
            int rand = crypto.getRandom(rmax);
            Identity addr = identities.get(rand);

            // Put the address at the end into the spot we just took. This way,
            // we are always selecting randomly from a set of unselected peers.
            identities.set(rand, identities.get(rmax));

            // Don't try to connect to myself.
            //if (addr.equals(me)) continue;

            peers.queue(addr);
        }

        final Retries retries = new Retries();

        while (true) {
            Identity identity = peers.peek();
            if (identity == null) {
                break;
            }

            if (peers.connected(identity)) {
                peers.remove();
                continue;
            }

            Peer<Identity, P> peer = channel.getPeer(identity);

            if (peer == null) {
                // TODO clean up properly and fail more gracefully.
                // This shouldn't actually happen either.
                throw new NullPointerException();
            }

            try{
                if (peers.openSession(identity, peer)) {
                    System.out.println("C " + System.currentTimeMillis() + " " + Thread.currentThread());
                    continue;
                }
            } catch (SocketException e) {
                // Try again later.
            }

            int r = retries.increment(peer.identity());

            if (r > maxRetries) {
                // Maximum number of retries has prevented us from making all connections.
                // TODO In some instances, it should be possible to run coin shuffle with fewer
                // players, so we should still return the network object.
                System.out.println("Reached maximum connection retry limit - could not connect to peer: " + peer.identity());
                connection.close();
                return null;
            }

            // We were not able to connect to this peer this time,
            // so we move on to the next one for now.
            if (!peers.rotate()) {
                break;
            }
        }

        finished = true;
        return collector;
    }

    @Override
    public void close() {
        connection.close();
    }

    @Override
    public boolean closed() {
        return connection.closed();
    }
}
