/**
 *
 * Copyright © 2016 Mycelium.
 * Use of this source code is governed by an ISC
 * license that can be found in the LICENSE file.
 *
 */

package com.shuffle.p2p;

import com.shuffle.bitcoin.Crypto;
import com.shuffle.chan.BasicChan;
import com.shuffle.chan.Chan;
import com.shuffle.chan.Inbox;
import com.shuffle.chan.Receive;
import com.shuffle.chan.Send;
import com.shuffle.mock.InsecureRandom;
import com.shuffle.mock.MockNetwork;
import com.shuffle.mock.MockCrypto;
import com.shuffle.monad.NaturalSummableFuture;
import com.shuffle.monad.Summable;
import com.shuffle.monad.SummableFuture;
import com.shuffle.monad.SummableFutureZero;
import com.shuffle.monad.SummableMap;
import com.shuffle.monad.SummableMaps;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;

/**
 * Test class for connect.
 *
 * Created by Daniel Krawisz on 3/2/16.
 */
public class TestConnect {
    private interface Network {
        Channel<Integer, Bytestring> node(Integer i) throws UnknownHostException;
    }

    private interface TestCase {
        int rounds();
        Network network(int n);
    }

    private static class ConnectRun implements Runnable {
        private final Send<Collector<Integer, Bytestring>> net;
        private final Connect<Integer, Bytestring> conn;

        private final SortedSet<Integer> addresses;
        private final int maxRetries;
        private final Integer me;

        ConnectRun(
                Connect<Integer, Bytestring> conn,
                Integer me,
                SortedSet<Integer> addresses,
                int maxRetries,
                Send<Collector<Integer, Bytestring>> net) {

            if (conn == null || net == null)
                throw new NullPointerException();

            this.addresses = addresses;
            this.maxRetries = maxRetries;
            this.net = net;
            this.conn = conn;
            this.me = me;
        }

        @Override
        public void run() {
            try {

                SortedSet<Integer> connectTo = new TreeSet<>();

                connectTo.addAll(addresses);
                connectTo.remove(me);

                Collector<Integer, Bytestring> m = conn.connect(connectTo, maxRetries);

                if (m != null) {
                    net.send(m);
                }
            } catch (IOException | InterruptedException | NullPointerException e) {
                e.printStackTrace();
            }

            net.close();
        }
    }

    private static class ConnectFuture
            implements Future<Summable.SummableElement<Map<Integer, Collector<Integer, Bytestring>>>> {
        
        final Receive<Collector<Integer, Bytestring>> netChan;
        SummableMap<Integer, Collector<Integer, Bytestring>> net = null;

        volatile boolean cancelled = false;

        int me;

        ConnectFuture(
                int i,
                Connect<Integer, Bytestring> conn,
                SortedSet<Integer> addresses) throws InterruptedException {

            if (conn == null || addresses == null)
                throw new NullPointerException();

            me = i;

            Chan<Collector<Integer, Bytestring>> netChan = new BasicChan<>();
            this.netChan = netChan;

            new Thread(new ConnectRun(conn, i, addresses, 3, netChan)).start();
        }

        @Override
        public boolean cancel(boolean b) {
            // TODO
            return false;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public boolean isDone() {
            return net != null || netChan.closed();
        }

        SummableMap<Integer, Collector<Integer, Bytestring>> getMap(Collector<Integer, Bytestring> net) {
            if (net == null) {
                return null;
            }

            Map<Integer, Collector<Integer, Bytestring>> map = new HashMap<>();
            map.put(me, net);
            this.net = new SummableMap<>(map);
            return this.net;
        }

        @Override
        public SummableMap<Integer, Collector<Integer, Bytestring>> get() throws InterruptedException {
            if (net != null) {
                return net;
            }

            if (netChan.closed()) {
                return null;
            }

            return getMap(netChan.receive());
        }

        @Override
        public SummableMap<Integer, Collector<Integer, Bytestring>> get(long l, TimeUnit timeUnit)
                throws InterruptedException, ExecutionException, TimeoutException {

            if (net != null) {
                return net;
            }

            if (netChan.closed()) {
                return null;
            }

            return getMap(netChan.receive(l, timeUnit));
        }
    }

    private Map<Integer, Collector<Integer, Bytestring>> simulation(int n, int seed, Network network) throws InterruptedException, IOException {
        if (n <= 0) {
            return new HashMap<>();
        }

        System.out.println("Running connect test with " + n + " addresses. ");

        Crypto crypto = new MockCrypto(new InsecureRandom(seed));

        SortedSet<Integer> addresses = new TreeSet<>();

        // Create the set of known hosts for each player.
        for (int i = 1; i <= n; i++) {
            addresses.add(i);
        }

        // Construct the future which represents all players trying to connect to one another.
        SummableFuture<Map<Integer, Collector<Integer, Bytestring>>> future = new SummableFutureZero<>(
                new SummableMaps<Integer, Collector<Integer, Bytestring>>()
        );

        // Create the set of known hosts for each player.
        Map<Integer, Connect<Integer, Bytestring>> connections = new HashMap<>();
        for (Integer i : addresses) {
            Channel<Integer, Bytestring> channel = network.node(i);
            Assert.assertNotNull(channel);
            Connect<Integer, Bytestring> conn = new Connect<>(channel, crypto, 10);
            connections.put(i, conn);
        }

        // Start the connection (this must be done after all Channel objects have been created
        // because everyone must be connected to the internet at the time they attempt to start
        // connecting to one another.
        for (Map.Entry<Integer, Connect<Integer, Bytestring>> e : connections.entrySet()) {
            future = future.plus(new NaturalSummableFuture<>(
                    new ConnectFuture(e.getKey(), e.getValue(), addresses)));
        }

        // Get the result of the computation.
        Map<Integer, Collector<Integer, Bytestring>> nets = null;
        try {
            nets = future.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

        return nets;
    }

    // There are two test cases. One uses a MockChannel, the other a TcpChannel.
    private TestCase[] cases = new TestCase[]{
        new TestCase() {
            @Override
            public int rounds() {
                return 13;
            }

            @Override
            public Network network(int n) {
                return new Network() {
                    private final MockNetwork<Integer, Bytestring> network = new MockNetwork<>();

                    @Override
                    public Channel<Integer, Bytestring> node(Integer i) {
                        return network.node(i);
                    }
                };
            }
        }, new TestCase() {
            @Override
            public int rounds() {
                return 5;
            }

            @Override
            public Network network(int n) {
                return new Network() {

                    @Override
                    public Channel<Integer, Bytestring> node(Integer i) throws UnknownHostException {
						
						int port = 5000;
						final Map<Integer, InetSocketAddress> hosts = new HashMap<>();
						
						for (int j = 1; j <= n; j ++) {
							InetSocketAddress address = new InetSocketAddress(InetAddress.getLocalHost(), port);
							hosts.put(j, address);
							port ++;
						}
						
						System.out.println(hosts);
						TcpChannel tcp = new TcpChannel(hosts.get(i));
                        MappedChannel<Integer> mapped = new MappedChannel<>(tcp, hosts, i);

                        return mapped;
                    }
                };
            }
        }
    };

    @Test
    public void testConnect() throws IOException, InterruptedException {
        int seed = 245;
        int msgNo = 100;
        for (TestCase tc: cases) {
            for (int i = 3; i <= tc.rounds(); i++) {
                System.out.println("Trial " + i + ": ");
                Map<Integer, Collector<Integer, Bytestring>> nets = simulation(i, seed + i, tc.network(i));
                Assert.assertTrue(nets != null);
                System.out.println("Trial " + i + ": " + nets);
                Assert.assertTrue(nets.size() == i);

                // Check that messages can be sent in all directions.
                for (Map.Entry<Integer, Collector<Integer, Bytestring>> e : nets.entrySet()) {
                    Integer from = e.getKey();
                    Collector<Integer, Bytestring> sender = e.getValue();

                    for (Map.Entry<Integer, Collector<Integer, Bytestring>> a : nets.entrySet()) {
                        Integer to = a.getKey();
                        if (from.equals(to)) continue;
                        System.out.println("  Sending messages between " + from + " and " + to);

                        Collector<Integer, Bytestring> recipient = a.getValue();

                        String j = "Oooo! " + msgNo;
                        sender.connected.get(to).send(new Bytestring(j.getBytes()));
                        Inbox.Envelope<Integer, Bytestring> q = recipient.inbox.receive();

                        Assert.assertNotNull(q);
                        Assert.assertTrue(q.from.equals(from));
                        Assert.assertTrue(new String(q.payload.bytes).equals(j));
                        msgNo++;
                    }
                }
            }
        }
    }
}
