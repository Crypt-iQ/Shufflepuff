package com.shuffle.p2p;

import com.shuffle.chan.Chan;
import com.shuffle.chan.Send;
import com.shuffle.mock.MockNetwork;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

/**
 * Created by nsa on 10/4/16.
 */

public class TestOtrMockChannel {

    MockNetwork<String, Bytestring> network;

    Channel<String, Bytestring> aNode;
    Channel<String, Bytestring> bNode;

    Channel<String, Bytestring> a;
    Channel<String, Bytestring> b;

    Channel<String, Bytestring> aHist;
    Channel<String, Bytestring> bHist;

    Send<Bytestring> aliceSend;
    Send<Bytestring> bobSend;

    Peer<String, Bytestring> aliceToBob;
    Peer<String, Bytestring> bobToAlice;

    Session<String, Bytestring> aliceToBobSession;
    Session<String, Bytestring> bobToAliceSession;

    @Before
    public void setup() {
        network = new MockNetwork<>();

        aNode = network.node("a");
        bNode = network.node("b");

        a = new OtrChannel<>(aNode);
        b = new OtrChannel<>(bNode);

        aHist = new HistoryChannel<>(a);
        bHist = new HistoryChannel<>(b);

        aliceSend = new Send<Bytestring>() {
            @Override
            public boolean send(Bytestring message) throws InterruptedException {
                System.out.println("Alice received: " + new String(message.bytes));
                return true;
            }

            @Override
            public void close() {
                System.out.println("aliceSend closed");
            }
        };

        Listener<String, Bytestring> aliceListener = new Listener<String, Bytestring>() {
            @Override
            public Send<Bytestring> newSession(Session<String, Bytestring> session) throws InterruptedException {
                System.out.println("Alice's listener caught: " + session);
                return aliceSend;
            }
        };

        bobSend = new Send<Bytestring>() {
            @Override
            public boolean send(Bytestring message) throws InterruptedException {
                System.out.println("Bob received: " + new String(message.bytes));
                return true;
            }

            @Override
            public void close() {
                System.out.println("bobSend closed");
            }
        };

        Listener<String, Bytestring> bobListener = new Listener<String, Bytestring>() {
            @Override
            public Send<Bytestring> newSession(Session<String, Bytestring> session) throws InterruptedException {

                /**
                 * This Session object is an OtrSession object because of how we constructed
                 * OtrListener's newSession() method.
                 */

                bobToAliceSession = session;
                System.out.println("Bob's listener caught: " + session);
                return bobSend;
            }
        };

    }

    @Test
    public void test() throws InterruptedException, IOException {

        aliceToBob = aHist.getPeer("b");
        bobToAlice = bHist.getPeer("a");

        aliceToBobSession = aliceToBob.openSession(aliceSend);

        if (aliceToBobSession == null) throw new NullPointerException("e");

        aliceToBobSession.send(new Bytestring("test1".getBytes()));

    }

    @After
    public void shutdown() {

    }

}