package com.shuffle.p2p;

import com.shuffle.chan.BasicChan;
import com.shuffle.chan.Chan;
import com.shuffle.chan.Send;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by Daniel Krawisz on 5/27/16.
 */
public class MappedChannel<Identity> implements Channel<Identity, Bytestring> {
    private final Channel<Object, Bytestring> inner;
    private final Map<Identity, Object> hosts;
    private final Map<Object, Identity> inverse = new HashMap<>();
    private final Identity me;
    private final Map<Identity, Session> halfOpenSessions = new ConcurrentHashMap<>();
    private final Object lock = new Object();
    private final Random rand = new Random();

    // You can add two or more MappedChannels together like a linked list if you
    // have two different kinds of channels that you want to use at the same time.
    private final MappedChannel<Identity> next;

    public MappedChannel(Channel inner, Map hosts, Identity me) {
        if (inner == null || hosts == null || me == null) throw new NullPointerException();

        this.inner = inner;
        this.hosts = hosts;
        this.me = me;
        this.next = null;
    }

    public MappedChannel(Channel inner, Map hosts, Identity me, MappedChannel<Identity> next) {
        if (inner == null || hosts == null || me == null) throw new NullPointerException();

        this.inner = inner;
        this.hosts = hosts;
        this.me = me;
        this.next = next;
    }

    private class MappedSession implements Session<Identity, Bytestring> {
        private final Session<Object, Bytestring> inner;
        private final Identity identity;

        private MappedSession(Session<Object, Bytestring> inner, Identity identity) {
            if (inner == null || identity == null) throw new NullPointerException();

            this.inner = inner;
            this.identity = identity;
        }

        @Override
        public boolean closed() {
            return inner.closed();
        }

        @Override
        public Peer<Identity, Bytestring> peer() {
            return new MappedPeer(inner.peer(), identity);
        }

        @Override
        public boolean send(Bytestring b) throws InterruptedException, IOException {
            return inner.send(b);
        }

        @Override
        public void close() {
            inner.close();
        }

        @Override
        public String toString() {
            return "MappedSession[" + inner + "]";
        }
    }

    private class MappedAliceSend implements Send<Bytestring> {
        private final Send<Bytestring> z;
        private Send<Boolean> chan;
        private boolean initialized = false;
        private boolean closed = false;

        private MappedAliceSend(Send<Bytestring> z, Send<Boolean> chan) throws InterruptedException, IOException {
            this.z = z;
            this.chan = chan;
        }

        @Override
        public synchronized boolean send(Bytestring message) throws InterruptedException, IOException {
            if (closed) return false;

            if (!initialized) {
                String msg = new String(message.bytes);
                if (msg.equals("received")) {
                    chan.send(true);
                    initialized = true;
                    chan.close();
                    return true;
                } else {
                    chan.send(false);
                    close();
                    return false;
                }
            }

            return this.z.send(message);
        }

        @Override
        public void close() {
            closed = true;
            z.close();
            chan.close();
        }
    }

    private class MappedBobSend implements Send<Bytestring> {
        private final Listener<Identity, Bytestring> l;
        private final Session<Object, Bytestring> s;
        private boolean initialized = false;
        private Send<Bytestring> z;
        boolean closed = false;

        private MappedBobSend(Listener<Identity, Bytestring> l, Session<Object, Bytestring> s) {
            this.l = l;
            this.s = s;
        }

        @Override
        public synchronized boolean send(Bytestring message) throws InterruptedException, IOException {
            if (closed) return false;

            if (!initialized) {
                Identity you = null;

                String msg = new String(message.bytes);
                if (msg.equals("received-ack")) {
                    initialized = true;
                    return true;
                } else {
                    for (Map.Entry<Object, Identity> e : inverse.entrySet()) {
                        if (e.getValue().toString().equals(msg)) {
                            you = e.getValue();
                            break;
                        }
                    }

                    if (you == null) {
                        close();
                        return false;
                    }

                    synchronized (lock) {
                        if (halfOpenSessions.containsKey(you)) {

                            // flip coin
                            if (rand.nextBoolean()) {
                                // Close this session.
                                close();
                                return false;
                            } else {
                                // Close the half-open session.
                                Session<Object, Bytestring> halfOpen = halfOpenSessions.get(you);
                                halfOpen.close();
                                halfOpenSessions.remove(you);
                            }
                        }

                        MappedSession m = new MappedSession(s, you);
                        if (!m.send(new Bytestring("received".getBytes()))) {
                            this.s.close();
                            return false;
                        }
                        this.z = l.newSession(m);
                    }
                    return true;
                }
            }

            return this.z.send(message);
        }

        @Override
        public void close() {
            s.close();
            closed = true;
        }
    }

    private class MappedPeer implements Peer<Identity,Bytestring> {
        private final Peer<Object, Bytestring> inner;
        private final Identity identity;

        private MappedPeer(Peer<Object, Bytestring> inner, Identity identity) {
            if (inner == null || identity == null) throw new NullPointerException();

            this.inner = inner;
            this.identity = identity;
        }

        @Override
        public Identity identity() {
            return identity;
        }

        @Override
        public Session<Identity, Bytestring> openSession(Send<Bytestring> send) throws InterruptedException, IOException {
            Chan<Boolean> chan;
            Session<Object, Bytestring> session;
            
            synchronized (lock) {
                if (send == null) return null;
                chan = new BasicChan<>(1);

                // remove identity?
                if (halfOpenSessions.containsKey(identity)) return null;

                MappedAliceSend alice = new MappedAliceSend(send, chan);

                session = inner.openSession(alice);
                if (session == null) return null;

                // initialization string - sends Alice's identity to Bob
                if (!session.send(new Bytestring(myIdentity().toString().getBytes()))) {
                    if (1==1) throw new NullPointerException("Ze-Ze");
                    return null;
                }

                halfOpenSessions.put(identity, session);
            }

            Boolean result = chan.receive();
            
            synchronized (lock) {
                if (result == null || !result) {
                    session.close();
                    halfOpenSessions.remove(identity);
                    return null;
                }
                
                if (!session.send(new Bytestring("received-ack".getBytes()))) {
                    if (1==1) throw new NullPointerException("Ak-Ak");
                    return null;
                }

                // success, remove from halfOpenSessions
                halfOpenSessions.remove(identity);
            }
            
            return new MappedSession(session, identity);
        }

        @Override
        public void close() throws InterruptedException {
            inner.close();
        }
    }

    @Override
    public Peer<Identity, Bytestring> getPeer(Identity you) {
        Object addr = hosts.get(you);
        if (addr == null) {
            if (next == null) {
                return null;
            } else {
                return next.getPeer(you);
            }
        }

        return new MappedPeer(inner.getPeer(addr), you);
    }

    private class MappedConnection implements Connection<Identity> {
        private final Connection connection;
        private final Connection next;

        MappedConnection(Connection<Object> connection) {
            this.connection = connection;
            this.next = null;
        }

        MappedConnection(Connection<Object> connection, Connection next) {
            this.connection = connection;
            this.next = next;
        }

        @Override
        public void close() {
            if (connection != null) {
                connection.close();
            }
            if (next != null) {
                next.close();
            }
        }

        @Override
        public boolean closed() {
            return connection.closed();
        }
    }

    private class MappedListener implements Listener<Object, Bytestring> {
        private final Listener<Identity, Bytestring> inner;

        private MappedListener(Listener<Identity, Bytestring> inner) {
            this.inner = inner;
        }

        @Override
        public Send<Bytestring> newSession(Session<Object, Bytestring> session) throws InterruptedException {
            return new MappedBobSend(inner, session);
        }
    }

    @Override
    public Connection<Identity> open(Listener<Identity, Bytestring> listener) throws InterruptedException, IOException {
        Connection c = inner.open(new MappedListener(listener));
        if (c == null) return null;

        for (Map.Entry<Identity, Object> e : hosts.entrySet()) {
            if (inverse.containsKey(e.getValue())) {
                hosts.remove(e.getKey());
            }

            inverse.put(e.getValue(), e.getKey());
        }

        if (next != null) {
            Connection n = next.open(listener);
            return new MappedConnection(c, n);
        }

        return new MappedConnection(c);
    }
    
    Identity myIdentity() {
        return me;
    }

    @Override
    public String toString() {
        return "Mapped[" + hosts + ", " + inner +  "]";
    }
}
