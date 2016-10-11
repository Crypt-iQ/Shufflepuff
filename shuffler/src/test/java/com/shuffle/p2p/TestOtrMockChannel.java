package com.shuffle.p2p;

import com.shuffle.bitcoin.SigningKey;
import com.shuffle.bitcoin.VerificationKey;
import com.shuffle.chan.HistoryReceive;
import com.shuffle.chan.Inbox;
import com.shuffle.chan.Receive;
import com.shuffle.chan.Send;
import com.shuffle.chan.packet.Marshaller;
import com.shuffle.chan.packet.Packet;
import com.shuffle.chan.packet.Signed;
import com.shuffle.mock.MockNetwork;
import com.shuffle.mock.MockProtobuf;
import com.shuffle.mock.MockSigningKey;
import com.shuffle.player.Messages;
import com.shuffle.player.P;
import com.shuffle.player.Protobuf;
import com.shuffle.protocol.FormatException;
import com.shuffle.protocol.message.Message;
import com.shuffle.protocol.message.Phase;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Created by nsa on 10/4/16.
 */

public class TestOtrMockChannel {

    private MockNetwork<Integer, Bytestring> mock;
    private Channel<Integer, Bytestring> n;
    private Channel<Integer, Bytestring> m;
    private OtrChannel<Integer> o_n;
    private OtrChannel<Integer> o_m;
    private Channel<Integer, Packet<VerificationKey, P>> m_n;
    private Channel<Integer, Packet<VerificationKey, P>> m_m;
    private Send<Packet<VerificationKey, P>> s_n;
    private Send<Packet<VerificationKey, P>> s_m;
    private Listener<Integer,Packet<VerificationKey, P>> l_n;
    private Listener<Integer,Packet<VerificationKey, P>> l_m;
    private Peer<Integer,Packet<VerificationKey, P>> p_n;
    private Peer<Integer,Packet<VerificationKey, P>> p_m;
    private Session<Integer,Packet<VerificationKey, P>> z_n;
    private Session<Integer,Packet<VerificationKey, P>> z_m;

    @Before
    public void setup() throws InterruptedException, IOException {
        mock = new MockNetwork<>();
        n = mock.node(0);
        m = mock.node(1);
        o_n = new OtrChannel<>(n);
        o_m = new OtrChannel<>(m);
        m_n = new MarshallChannel<>(o_n,new MockProtobuf().packetMarshaller());
        m_m = new MarshallChannel<>(o_m,new MockProtobuf().packetMarshaller());

        s_n = new Send<Packet<VerificationKey, P>>() {
            @Override
            public boolean send(Packet<VerificationKey, P> packetSigned) throws InterruptedException, IOException {
                System.out.println("n: received");
                return true;
            }

            @Override
            public void close() {
                System.out.println("n: closed");
            }
        };

        l_n = new Listener<Integer, Packet<VerificationKey, P>>() {
            @Override
            public Send<Packet<VerificationKey, P>> newSession(Session<Integer, Packet<VerificationKey, P>> session) throws InterruptedException {
                System.out.println("n: caught");
                return s_n;
            }
        };

        s_m = new Send<Packet<VerificationKey, P>>() {
            @Override
            public boolean send(Packet<VerificationKey, P> packetSigned) throws InterruptedException, IOException {
                System.out.println("m: received");
                return true;
            }

            @Override
            public void close() {
                System.out.println("m: closed");
            }
        };

        l_m = new Listener<Integer, Packet<VerificationKey, P>>() {
            @Override
            public Send<Packet<VerificationKey, P>> newSession(Session<Integer, Packet<VerificationKey, P>> session) throws InterruptedException {
                System.out.println("m: caught");
                z_m = session;
                return s_m;
            }
        };

        m_n.open(l_n);
        m_m.open(l_m);

    }

    @Test
    public void test() throws InterruptedException, IOException, FormatException, NoSuchAlgorithmException {

        p_n = m_n.getPeer(1);
        p_m = m_m.getPeer(0);
        z_n = p_n.openSession(s_n);


        // MockProtobuf
        MockProtobuf z = new MockProtobuf();
        // SigningKey
        SigningKey sk = new MockSigningKey(0);
        SigningKey sk2 = new MockSigningKey(1);
        // PacketMarshaller
        Marshaller<Packet<VerificationKey, P>> y = z.packetMarshaller();

        Bytestring message;
        Bytestring signature;
        VerificationKey vk = sk.VerificationKey();
        Marshaller<Signed<Packet<VerificationKey, P>>> x = z.signedMarshaller();

        Bytestring session = new Bytestring("s".getBytes());
        Map<VerificationKey,Send<Signed<Packet<VerificationKey, P>>>> net = new HashMap<>();
        Receive<Inbox.Envelope<VerificationKey,Signed<Packet<VerificationKey, P>>>> rec = new Receive<Inbox.Envelope<VerificationKey, Signed<Packet<VerificationKey, P>>>>() {
            @Override
            public Inbox.Envelope<VerificationKey, Signed<Packet<VerificationKey, P>>> receive() throws InterruptedException {
                return null;
            }

            @Override
            public Inbox.Envelope<VerificationKey, Signed<Packet<VerificationKey, P>>> receive(long l, TimeUnit u) throws InterruptedException {
                return null;
            }

            @Override
            public boolean closed() {
                return false;
            }
        };
        Receive<Inbox.Envelope<VerificationKey,Signed<Packet<VerificationKey, P>>>> rr = new HistoryReceive<>(rec);
        Messages mm = new Messages(session,sk,net, rr, z);
        com.shuffle.player.Message mmm = new com.shuffle.player.Message(mm);
        P pp = new P(Phase.Announcement,mmm);
        Packet<VerificationKey, P> mu = new Packet<>(session,sk.VerificationKey(),sk2.VerificationKey(),0,pp);

        message = y.marshall(mu);
        //signature = sk.sign(message);

        //Signed<Packet<VerificationKey, P>> e = new Signed<>(message, signature, vk, y);

        z_n.send(mu);
        //z_m.send(e);

    }

    @After
    public void shutdown() {

    }

}
