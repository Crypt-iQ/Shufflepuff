package com.shuffle.p2p;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by Eugene Siegel on 4/12/16.
 */

public class MediatorClientChannel<Name, Address, Payload> implements Channel<Name, Payload> {

    public class Envelope {
        private final Name to; // Null means to the mediator.
        private final Name from; // Null means from the mediator.
        private final Payload payload;

        // The mediator is a virtual channel, so the envelope
        // class has to be able to act like the kind of actions
        // we could take with an ordinary channel. In this case,
        // we can close and open sessions.
        private final Name openSession;
        private final Name closeSession;

        private final boolean success; // Server response field.

        Envelope(Name to, Payload payload, Name openSession, Name closeSession) {
            if (!((to != null && payload != null && openSession == null && closeSession == null)
                    || to == null && payload == null
                        && ((openSession == null && closeSession != null)
                            || (openSession != null && closeSession == null)))) {

                throw new IllegalArgumentException();
            }

            this.to = to;
            this.payload = payload;
            this.openSession = openSession;
            this.closeSession = closeSession;
            this.success = true;
            this.from = me;
        }

        // Send a response to the server if we receive a request to open
        // a session.
        Envelope(Name openSession, boolean response) {
            this.openSession = openSession;
            this.success = response;

            this.to = null;
            this.from = me;
            this.payload = null;
            this.closeSession = null;
        }
    }

    // Methods for creating Envelope objects.
    // (use these for creating Envelope objects for safety.)
    public Envelope OpenSessionResponse(Name openSession, boolean response) {
        return new Envelope(openSession, response);
    }

    public Envelope OpenSessionRequest(Name openSession) {
        return new Envelope(null, null, openSession, null);
    }

    public Envelope CloseSessionRequest(Name closeSession) {
        return new Envelope(null, null, null, closeSession);
    }

    public Envelope PeerMessage(Name to, Payload payload) {
        return new Envelope(to, payload, null, null);
    }

    private final Peer<Address, Envelope> virtualChannel;
    private final Name me;

    private Session<Address, Envelope> session;

    // TODO Need to have a map that keeps track of names -> open sessions.
    // (should be a concurrent map.)

    // Map containing receivers that we have been given.
    private final Map<Name, Receiver<Payload>> receivers = new HashMap<>();

    public MediatorClientChannel(Name me, final Peer<Address, Envelope> peer) {
        this.me = me;
        this.virtualChannel = peer;
    }

    // TODO need a MediatorClientSession class

    // TODO need a MediatorClientPeer class

    private class MediatorClientConnection implements Connection<Name, Payload> {

        @Override
        public void close() {
            // TODO empty map of open sessions.

            session.close();
            session = null;
        }
    }

    private class MediatorClientReceiver implements Receiver<Envelope> {

        private final Listener<Name, Payload> listener;

        private MediatorClientReceiver(Listener<Name, Payload> listener) {
            this.listener = listener;
        }

        @Override
        public void receive(Envelope envelope) throws InterruptedException {
            // TODO is this message from the server or from a peer?
            // If the message is from the server and has openSession set, then
            // create a new session and send it to the listener.

            // if it is from a peer, send it to the appropriate receiver.
        }
    }

    public Connection<Name, Payload> open(final Listener<Name, Payload> listener) throws InterruptedException {
        session = virtualChannel.openSession(new MediatorClientReceiver(listener));

        if (session == null) return null;

        return new MediatorClientConnection();
    }


    public Peer<Name,Payload> getPeer(Name you) {
        // TODO
        return null;
    }
}
