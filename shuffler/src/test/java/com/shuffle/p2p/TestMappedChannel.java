package com.shuffle.p2p;

import com.shuffle.bitcoin.VerificationKey;
import com.shuffle.chan.Send;
import com.shuffle.mock.MockNetwork;
import com.shuffle.mock.MockVerificationKey;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Created by nsa on 12/23/16.
 */

/**
 * A test of MappedChannel in conjunction with TcpChannel
 */

public class TestMappedChannel {

    class TestCase {
        final String[] names;

        // Keys that we use for identification
        Map<String, VerificationKey> keys = new HashMap<>();

        Map<VerificationKey, String> invKeys = new HashMap<>();

        // InetSocketAddresses that are used for TCP communication
        Map<String, InetSocketAddress> inetAddrs = new HashMap<>();
        Map<String, Integer> mockAddrs = new HashMap<>();

        // Alice & Bob's Map of peers' VerificationKey to peers' InetSocketAddress
        Map<String, Map<VerificationKey, InetSocketAddress>> inetMaps = new HashMap<>();
        Map<String, Map<VerificationKey, Integer>> mockMaps = new HashMap<>();

        MockNetwork<Integer, Bytestring> mockNetwork = new MockNetwork<>();

        // TCPChannels
        Map<String, TcpChannel> tcp = new HashMap<>();
        Map<String, Channel<Integer, Bytestring>> mock = new HashMap<>();

        // Alice & Bob's respective MappedChannels
        Map<String, MappedChannel<VerificationKey>> mapped = new HashMap<>();

        // Alice & Bob's respective MappedConnections
        Map<String, Connection<VerificationKey>> connections = new HashMap<>();

        // The listeners.
        Map<String, Listener<VerificationKey, Bytestring>> listeners = new HashMap<>();

        // Alice & Bob's respective Peer objects - these represent the Peer they are connected to
        Map<String, Map<String, Peer<VerificationKey, Bytestring>>> peers = new HashMap<>();

        // Alice & Bob's respective Session objects - these represent a Session object that can send messages to
        // the connected Peer
        Map<String, Map<String, Session<VerificationKey, Bytestring>>> sessions = new HashMap<>();

        // Messages received by different players.
        Map<String, Map<String, List<Bytestring>>> received = new HashMap<>();

        public TestCase(String[] names, String[][] mocknets, int port) throws IOException, InterruptedException {
            this.names = names;

            // Create a key for each player, starting at 5000.
            // Make a InetSocketAddresses and a mock network for everybody.
            for (String name : names) {
                VerificationKey key = new MockVerificationKey(port);
                keys.put(name, key);
                invKeys.put(key, name);
                InetSocketAddress ip = new InetSocketAddress(InetAddress.getLocalHost(), port);
                inetAddrs.put(name, ip);
                mockAddrs.put(name, port);
                mock.put(name, mockNetwork.node(port));
                tcp.put(name, new TcpChannel(ip));
                mockMaps.put(name, new HashMap<>());
                port++;
            }

            for (String[] group : mocknets) for (String from : group)
                for (String to : group) if (!to.equals(from)) mockMaps.get(from).put(keys.get(to), mockAddrs.get(to));

            // Initialize the Map<VerificationKey, InetSocketAddress>
            // These Maps contain a peer's VerificationKey mapped to the peer's InetSocketAddress
            // Alice & Bob each initialize their own TCPChannel with their InetSocketAddress
            for (String from : names) {
                HashMap<VerificationKey, InetSocketAddress> inetMap = new HashMap<>();

                for (String to : names)
                    if (!to.equals(from)) inetMap.put(keys.get(to), inetAddrs.get(to));

                inetMaps.put(from, inetMap);
            }

            // Alice & Bob create their own MappedChannel
            for (String name : names) {
                mapped.put(name, new MappedChannel<>(mock.get(name), mockMaps.get(name), keys.get(name),
                        new MappedChannel<>(tcp.get(name), inetMaps.get(name), keys.get(name))));
            }

            for (String name : names) {
                peers.put(name, new HashMap<>());
                sessions.put(name, new HashMap<>());
            }

            /**
             * Create send and listener objects.
             */
            for (final String name : names) {

                listeners.put(name, new Listener<VerificationKey, Bytestring>() {
                    @Override
                    public Send<Bytestring> newSession(Session<VerificationKey, Bytestring> session) throws InterruptedException {
                        // We initialize bobToAliceSession here since Alice initiated the connection & sent the first message.
                        String from = invKeys.get(session.peer().identity());
                        sessions.get(name).put(from, session);
                        System.out.println(name + "'s listener caught: " + session);
                        final List<Bytestring> receive = new LinkedList<>();

                        return newSend(name, from, receive);
                    }
                });
            }

            // Open MappedChannels with their respective listeners.
            // They are now both listening for incoming connections.
            for (String name : names)
                connections.put(name, mapped.get(name).open(listeners.get(name)));

            // Alice gets a peer by using Bob's VerificationKey.
            // The returned Peer object represents Bob.
            for (String from : names) for (String to : names) if (!from.equals(to)) {
                System.out.println("About to get peer from " + from + " to " + to);
                Peer<VerificationKey, Bytestring> peer = mapped.get(from).getPeer(keys.get(to));
                peers.get(from).put(to, peer);
            }

            /**
             * Alice opens up the session to Bob and a Session object is returned.
             * This Session object allows Alice to send messages to Bob.
             *
             * As a direct consequence of Alice calling openSession, Bob's newSession method is called
             * on Bob's Listener and Bob's bobToAliceSession object is set.
             * Bob can now send messages to Alice.
             */
            for (int i = 0; i < names.length; i++)
                for (int j = i + 1; j < names.length; j++) {
                    System.out.println("About to open session from " + names[i] + " to " + names[j]);
                    sessions.get(names[i]).put(names[j], peers.get(names[i]).get(names[j]).openSession(
                            newSend(names[i], names[j], new LinkedList<>())));
                }
        }

        Send<Bytestring> newSend(String name, String from, List<Bytestring> receive) {
            Map<String, List<Bytestring>> mailbox = received.get(name);
            if (mailbox == null) {
                mailbox = new HashMap<>();
                received.put(name, mailbox);
            }

            mailbox.put(from, receive);

            return new Send<Bytestring>() {
                boolean closed = false;

                @Override
                public boolean send(Bytestring bytestring) throws InterruptedException, IOException {
                    if (closed) return false;

                    System.out.println(name + " received: " + new String(bytestring.bytes));
                    receive.add(bytestring);
                    return true;
                }

                @Override
                public void close() {
                    closed = true;
                }
            };
        }

        public void shutdown() {

            // Close all sessions.
            for (Map<String, Session<VerificationKey, Bytestring>> map : sessions.values())
                if (map != null)
                    for (Session<VerificationKey, Bytestring> session : map.values())
                        if (session != null) session.close();

            // Close channels.
            for (String name : names)
                connections.get(name).close();

            /**
             * We assert that the above Session & Connection objects are indeed closed.
             */
            for (Map<String, Session<VerificationKey, Bytestring>> map : sessions.values())
                if (map != null)
                    for (Session<VerificationKey, Bytestring> session : map.values())
                        if (session != null) Assert.assertTrue(session.closed());

            for (String name : names)
                Assert.assertTrue(connections.get(name).closed());
        }

        public void send(String from, String to, String message) throws IOException, InterruptedException {
            sessions.get(from).get(to).send(new Bytestring(message.getBytes()));
        }
    }

    @Test
    public void testBasic() throws IOException, InterruptedException {
        String[] names = new String[]{"Alice", "Bob"};

        TestCase testCase = new TestCase(names, new String[][]{}, 5000);

        testCase.shutdown();
    }
	
	@Test
	public void testSendMessages() throws InterruptedException, IOException {
        String[] names = new String[]{"Alice", "Bob", "Carlos", "Daniel"};
        String[][] mocknets = new String[][]{{"Alice", "Bob"}, {"Carlos", "Daniel"}};

        TestCase testCase = new TestCase(names, mocknets, 5010);

        testCase.send("Alice", "Carlos", "Carlos, this is a mapped channel test!");
        testCase.send("Carlos", "Alice", "Alice, testing testing testing");

        testCase.shutdown();
	}
}
