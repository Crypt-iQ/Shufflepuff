package com.shuffle.p2p;

import com.shuffle.chan.BasicChan;
import com.shuffle.chan.Chan;
import com.shuffle.chan.Send;
import com.shuffle.mock.MockCrypto;

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

        private MappedAliceSend(Send<Bytestring> z, Send<Boolean> chan) {
            this.z = z;
            this.chan = chan;
        }

        @Override
        public boolean send(Bytestring message) throws InterruptedException, IOException {
            if (!initialized) {
                String msg = new String(message.bytes);
                if (msg.equals("received")) {
                    chan.send(true);
                    initialized = true;
                    return true;
                }
            }

            return this.z.send(message);
        }

        @Override
        public void close() {
            z.close();
            chan.close();
        }
    }

    private class MappedBobSend implements Send<Bytestring> {
        private final Listener<Identity, Bytestring> l;
        private final Session<Object, Bytestring> s;
        private boolean initialized = false;
        private Send<Bytestring> z;

        private MappedBobSend(Listener<Identity, Bytestring> l, Session<Object, Bytestring> s) {
            this.l = l;
            this.s = s;
        }

        @Override
        public boolean send(Bytestring message) throws InterruptedException, IOException {
            if (!initialized) {
                Identity you = null;

                String msg = new String(message.bytes);
                System.out.println("+++ Received " + msg + " ; " + inverse);

                for (Map.Entry<Object, Identity> e : inverse.entrySet()) {
                    if (e.getValue().toString().equals(msg)) {
                        you = e.getValue();
                        break;
                    }
                }

                if (you == null) throw new NullPointerException();

                MappedSession m = new MappedSession(s, you);
                if (!m.send(new Bytestring("received".getBytes()))) {
                    // TODO
                    // Failed, Bob chose the wrong session
                    // Now what?
                }
                this.z = l.newSession(m);

                initialized = true;
                return true;
            }

            return this.z.send(message);
        }

        @Override
        public void close() {
            s.close();
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
            if (send == null) return null;
            Chan<Boolean> chan = new BasicChan<>(1);

            MappedAliceSend alice = new MappedAliceSend(send, chan);
			Session<Object, Bytestring> session = inner.openSession(alice);
			if (session == null) return null;

            // remove identity?
            if (halfOpenSessions.containsKey(identity)) return null;

            halfOpenSessions.put(identity, session);

            // MappedChannel initialization string - sends my identity
			session.send(new Bytestring(myIdentity().toString().getBytes()));

            // TODO
            // If result is false, we retry and repeat process above N times until success?
            Boolean result = chan.receive();
            if (!result) {
                chan.close();
                session.close();
                halfOpenSessions.remove(identity);
                return null;
            }

            // success, remove from halfOpenSessions
            halfOpenSessions.remove(identity);

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
        System.out.println("Get peer " + you + "; " + hosts);
        if (addr == null) {
            if (next == null) {
                return null;
            } else {
                System.out.println("not found; calling next.");
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
            next = null;
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
            Session<Object, Bytestring> currentSession = session;
            Identity peerIdentity = (Identity) session.peer().identity();

            if (halfOpenSessions.containsKey(peerIdentity)) {
                // flip coin
                Random rand = new Random();
                if (rand.nextBoolean()) {
                    currentSession = halfOpenSessions.get(peerIdentity);
                }
            } else {
                halfOpenSessions.put(peerIdentity, currentSession);
            }

            return new MappedBobSend(inner, currentSession);
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
