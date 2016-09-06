/**
 *
 * Copyright © 2016 Mycelium.
 * Use of this source code is governed by an ISC
 * license that can be found in the LICENSE file.
 *
 */

package com.shuffle.p2p;

import com.shuffle.chan.Send;

import net.java.otr4j.OtrEngineHost;
import net.java.otr4j.OtrException;
import net.java.otr4j.OtrPolicy;
import net.java.otr4j.OtrPolicyImpl;
import net.java.otr4j.crypto.OtrCryptoEngineImpl;
import net.java.otr4j.crypto.OtrCryptoException;
import net.java.otr4j.session.FragmenterInstructions;
import net.java.otr4j.session.InstanceTag;
import net.java.otr4j.session.SessionID;
import net.java.otr4j.session.SessionImpl;

import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Created by Eugene Siegel on 5/10/16.
 */

public class OtrChannel<Address> implements Channel<Address, Bytestring> {

    public class SendClient {

        private net.java.otr4j.session.Session session;
        private OtrPolicy policy;
        public SendConnection connection;
        public MessageProcessor processor;
        public Queue<ProcessedMessage> processedMsgs = new LinkedList<>();
        private Send<Bytestring> send;

        public SendClient(Send<Bytestring> send) {
            this.send = send;
        }

        public net.java.otr4j.session.Session getSession() {
            return this.session;
        }

        public void setPolicy(OtrPolicy policy) {
            this.policy = policy;
        }

        public void send(Bytestring s) throws OtrException, InterruptedException, IOException {
            if (session == null) {
                final SessionID sessionID = new SessionID("", "", "CoinShuffle Encrypted Chat");
                session = new SessionImpl(sessionID, new SendOtrEngineHost());
            }

            String[] outgoingMessage = session.transformSending(new String(s.bytes), null);

            for (String part : outgoingMessage) {
                connection.send(part);
            }
        }

        public void exit() throws OtrException {
            this.processor.stop();
            this.send.close();
            this.connection.close();
            if (session != null) {
                session.endSession();
            }
        }

        public synchronized void receive(Bytestring s) throws OtrException {
            this.processor.enqueue(new String(s.bytes));
        }

        public void connect() {
            this.processor = new MessageProcessor();
            new Thread(this.processor).start();
            this.connection = new SendConnection(this, "CoinShuffle Encrypted Chat", this.send);
        }

        // TODO
        public void secureSession() throws OtrException {
            if (session == null) {
                final SessionID sessionID = new SessionID("", "", "CoinShuffle Encrypted Chat");
                session = new SessionImpl(sessionID, new SendOtrEngineHost());
            }

            session.startSession();
        }

        public SendConnection getConnection() {
            return connection;
        }

        public ProcessedMessage pollReceivedMessage() throws InterruptedException {
            synchronized (processedMsgs) {
                ProcessedMessage m;
                while ((m = processedMsgs.poll()) == null) {
                    processedMsgs.wait();
                }

                return m;
            }
        }


        public class Message {
            public Message(String content){
                this.content = content;
            }

            private final String content;

            public String getContent() {
                return content;
            }
        }

        public class ProcessedMessage extends Message {
            final Message originalMessage;

            public ProcessedMessage(Message originalMessage, String content) {
                super(content);
                this.originalMessage = originalMessage;
            }
        }


        public class MessageProcessor implements Runnable {

            public final Queue<Message> messageQueue = new LinkedList<>();
            private boolean stopped;

            private void process(Message m) throws OtrException {
                if (session == null) {
                    final SessionID sessionID = new SessionID("", "", "CoinShuffle Encrypted Chat");
                    session = new SessionImpl(sessionID, new SendOtrEngineHost());
                }

                String receivedMessage = session.transformReceiving(m.getContent());

                synchronized (processedMsgs) {
                    processedMsgs.add(new ProcessedMessage(m, receivedMessage));
                    processedMsgs.notify();
                }
            }

            public void run() {
                synchronized (messageQueue) {
                    while (true) {

                        Message m = messageQueue.poll();

                        if (m == null) {
                            try {
                                messageQueue.wait();
                            } catch (InterruptedException e) {

                            }
                        } else {
                            try {
                                process(m);
                            } catch (OtrException e) {

                            }
                        }

                        if (stopped)
                            break;
                    }
                }
            }

            public void enqueue(String s) {
                synchronized (messageQueue) {
                    messageQueue.add(new Message(s));
                    messageQueue.notify();
                }
            }

            public void stop() {
                stopped = true;

                synchronized (messageQueue) {
                    messageQueue.notify();
                }
            }

        }

        /**
         * Most of the methods for SendOtrEngineHost are not filled out, but it does not matter
         * here.  If you need other jitsi/otr functionality, feel free to change the methods.
         */

        private class SendOtrEngineHost implements OtrEngineHost {

            public void injectMessage(SessionID sessionID, String msg) throws OtrException {
                try {
                    connection.send(msg);
                } catch (IOException e) {

                } catch (InterruptedException er) {

                }
            }

            public void smpError(SessionID sessionID, int tlvType, boolean cheated)
                    throws OtrException {
                return;
            }

            public void smpAborted(SessionID sessionID) throws OtrException {
                return;
            }

            public void finishedSessionMessage(SessionID sessionID, String msgText) throws OtrException {
                return;
            }

            public void finishedSessionMessage(SessionID sessionID) throws OtrException {
                return;
            }

            public void requireEncryptedMessage(SessionID sessionID, String msgText) throws OtrException {
                return;
            }

            public void unreadableMessageReceived(SessionID sessionID) throws OtrException {
                return;
            }

            public void unencryptedMessageReceived(SessionID sessionID, String msg) throws OtrException {
                return;
            }

            public void showError(SessionID sessionID, String error) throws OtrException {
                return;
            }

            public String getReplyForUnreadableMessage() {
                return "You sent me an unreadable encrypted message.";
            }

            public void sessionStatusChanged(SessionID sessionID) {
                return;
            }

            public KeyPair getLocalKeyPair(SessionID paramSessionID) {
                KeyPairGenerator kg;
                try {
                    kg = KeyPairGenerator.getInstance("DSA");
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                    return null;
                }
                return kg.genKeyPair();
            }

            public OtrPolicy getSessionPolicy(SessionID ctx) {
                return policy;
            }

            public void askForSecret(SessionID sessionID, String question) {
                return;
            }

            public void verify(SessionID sessionID, boolean approved) {
                return;
            }

            public void unverify(SessionID sessionID) {
                return;
            }

            public byte[] getLocalFingerprintRaw(SessionID sessionID) {
                try {
                    return new OtrCryptoEngineImpl()
                            .getFingerprintRaw(getLocalKeyPair(sessionID)
                                    .getPublic());
                } catch (OtrCryptoException e) {
                    e.printStackTrace();
                }
                return null;
            }

            public void askForSecret(SessionID sessionID, InstanceTag receiverTag, String question) {
                return;
            }

            public void verify(SessionID sessionID, String fingerprint, boolean approved) {
                return;
            }

            public void unverify(SessionID sessionID, String fingerprint) {
                return;
            }

            public String getReplyForUnreadableMessage(SessionID sessionID) {
                return null;
            }

            public String getFallbackMessage(SessionID sessionID) {
                return null;
            }

            public void messageFromAnotherInstanceReceived(SessionID sessionID) {
                throw new NullPointerException("Message from another instance received: " + sessionID.toString());
            }

            public void multipleInstancesDetected(SessionID sessionID) {
                return;
            }

            public String getFallbackMessage() {
                return "Off-the-Record private conversation has been requested. However, you do not have a plugin to support that.";
            }

            public FragmenterInstructions getFragmenterInstructions(SessionID sessionID) {
                return new FragmenterInstructions(FragmenterInstructions.UNLIMITED,
                        FragmenterInstructions.UNLIMITED);
            }

        }

        public class SendConnection implements Send<Bytestring> {

            private final SendClient client;
            private final String connectionName;
            private String sentMessage;
            private Send<Bytestring> send;
            public Session<Address, Bytestring> session;

            public SendConnection(SendClient client, String connectionName, Send<Bytestring> send) {
                this.client = client;
                this.connectionName = connectionName;
                this.send = send;
            }

            public String getSentMessage() {
                return sentMessage;
            }

            public SendClient getClient() {
                return client;
            }

            @Override
            public String toString() {
                return "PriorityConnection{" +
                        "connectionName='" + connectionName + '\'' +
                        '}';
            }

            public void send(String msg) throws OtrException, InterruptedException, IOException {
                this.sentMessage = msg;
                Bytestring bytestring = new Bytestring(msg.getBytes());
                session.send(bytestring);
            }

            public boolean send(Bytestring msg) throws IOException, InterruptedException {

                try {
                    this.client.receive(msg);
                } catch (OtrException e) {
                    return false;
                }

                return send.send(msg);
            }

            public void close() {
                session.close();
            }

        }

    }

    public class OtrPeer implements Peer<Address, Bytestring> {

        Peer<Address, Bytestring> peer;
        SendClient sendClient;

        public OtrPeer(Peer<Address, Bytestring> peer) {
            this.peer = peer;
            sendClient = new SendClient(null);
            sendClient.setPolicy(new OtrPolicyImpl(OtrPolicy.ALLOW_V2 | OtrPolicy.ALLOW_V3
                    | OtrPolicy.ERROR_START_AKE)); // this assumes the user wants OTR v2 or v3.
        }

        // TODO
        // This method is ONLY for Alice
        @Override
        public synchronized OtrSession openSession(Send<Bytestring> send) throws InterruptedException, IOException {

            Session<Address, Bytestring> session;

            sendClient.send = send;
            sendClient.connect();
            session = peer.openSession(sendClient.connection);
            sendClient.connection.session = session;

            if (session == null) {
                return null;
            }

            OtrSession otrSession = new OtrSession(session);

            /*
            String query = "?OTRv23?"; // This string depends on the version / type of OTR encryption that the user wants.
            otrSession.send(new Bytestring(query.getBytes()))
            sendClient.pollReceivedMessage();
            sendClient.pollReceivedMessage();*/

            return otrSession;
        }

        // TODO
        // This method is the equivalent of openSession(), but for Bob
        public synchronized OtrSession openReceivingSession(Send<Bytestring> send, Session<Address, Bytestring> session) throws InterruptedException, IOException {
            // messageprocesser is connected to sendClient
            sendClient.send = send;
            // starts listening
            sendClient.connect();
            sendClient.connection.session = session;
            OtrSession otrSession = new OtrSession(session); // nothing passed to the internal peer

            /*
            sendClient.pollReceivedMessage();
            sendClient.pollReceivedMessage();
            sendClient.pollReceivedMessage();*/

            return otrSession;
        }

        public Address identity() {
            return peer.identity();
        }

        public void close() throws InterruptedException {
            peer.close();
            try {
                sendClient.exit();
            } catch (OtrException e) {
                throw new RuntimeException(e);
            }
        }

        public class OtrSession implements Session<Address, Bytestring> {

            Session<Address, Bytestring> session;

            public OtrSession(Session<Address, Bytestring> session) {
                this.session = session;
            }

            @Override
            public synchronized boolean send(Bytestring message) throws InterruptedException, IOException {
                try {
                    sendClient.send(message);
                    return true;
                } catch (OtrException e) {
                    return false;
                }
            }

            @Override
            public synchronized void close() {
                session.close();
            }

            @Override
            public synchronized boolean closed() {
                return session.closed();
            }

            @Override
            public Peer<Address, Bytestring> peer() {
                return OtrPeer.this;
            }

        }

    }

    private class OtrConnection implements Connection<Address> {

        Connection<Address> connection;

        public OtrConnection(Connection<Address> connection) {
            this.connection = connection;
        }

        @Override
        public void close() {
            running = false;
            this.connection.close();
        }

        @Override
        public boolean closed() {
            return connection.closed();
        }

    }

    Channel<Address, Bytestring> channel;
    private final Address me;
    private boolean running = false;
    Listener<Address, Bytestring> listener;

    public OtrChannel(Channel<Address, Bytestring> channel, Address me) {
        if (me == null) {
            throw new NullPointerException();
        }

        this.channel = channel;
        this.me = me;

    }

    @Override
    public OtrConnection open(Listener<Address, Bytestring> listener) throws InterruptedException, IOException {

        if (listener == null) {
            throw new NullPointerException();
        }

        if (running) {
            return null;
        }

        this.listener = listener;
        running = true;
        return new OtrConnection(this.channel.open(listener));

    }

    @Override
    public OtrPeer getPeer(Address you) {

        if (you.equals(me)) return null;

        return new OtrPeer(this.channel.getPeer(you));
    }

}