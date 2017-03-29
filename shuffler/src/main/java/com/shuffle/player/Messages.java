/**
 *
 * Copyright © 2016 Mycelium.
 * Use of this source code is governed by an ISC
 * license that can be found in the LICENSE file.
 *
 */

package com.shuffle.player;

import com.shuffle.bitcoin.Address;
import com.shuffle.bitcoin.SigningKey;
import com.shuffle.bitcoin.VerificationKey;
import com.shuffle.chan.HistoryReceive;
import com.shuffle.chan.HistorySend;
import com.shuffle.chan.IgnoreSend;
import com.shuffle.chan.Receive;
import com.shuffle.chan.Send;
import com.shuffle.chan.packet.Marshaller;
import com.shuffle.chan.packet.OutgoingPacketSend;
import com.shuffle.chan.packet.Packet;
import com.shuffle.chan.packet.Signed;
import com.shuffle.chan.packet.SigningSend;
import com.shuffle.p2p.Bytestring;
import com.shuffle.chan.Inbox;
import com.shuffle.protocol.message.MessageFactory;
import com.shuffle.protocol.message.Phase;

import java.io.IOException;
import java.io.Serializable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * All Message handling for CoinShuffle in one convenient package!
 *
 * Created by Daniel Krawisz on 12/9/15.
 */
public class Messages implements MessageFactory {
    public interface ShuffleMarshaller {
        Marshaller<Message.Atom> atomMarshaller();
        Marshaller<Address> addressMarshaller();
        Marshaller<Packet<VerificationKey, Payload>> packetMarshaller();
        Marshaller<Signed<Packet<VerificationKey, Payload>>> signedMarshaller();
    }

    /**
     * Represents a packet that has been digitally signed.
     *
     * Created by Daniel Krawisz on 1/22/16.
     */
    public static class SignedPacket implements com.shuffle.protocol.message.Packet, Serializable {

        public final Signed<com.shuffle.chan.packet.Packet<VerificationKey, Payload>> packet;

        public SignedPacket(
                Signed<com.shuffle.chan.packet.Packet<VerificationKey, Payload>> packet) {

            if (packet == null) {
                throw new NullPointerException();
            }

            this.packet = packet;
        }

        @Override
        public String toString() {
            return packet.toString();
        }

        @Override
        public boolean equals(Object o) {
            if (o == null) {
                return false;
            }

            if (!(o instanceof SignedPacket)) {
                return false;
            }

            SignedPacket sp = (SignedPacket)o;

            return packet.equals(sp.packet) && packet.signature.equals(sp.packet.signature);
        }

        @Override
        public int hashCode() {
            return 17 * packet.hashCode();
        }


        @Override
        public com.shuffle.protocol.message.Message payload() {
            return packet.message.payload.message;
        }

        @Override
        public Phase phase() {
            return packet.message.payload.phase;
        }

        @Override
        public VerificationKey from() {
            return packet.message.from;
        }

        @Override
        public VerificationKey to() {
            return packet.message.to;
        }

        @Override
        public Bytestring signature() {
            return packet.signature;
        }
    }

    private class Outgoing {
        public final Send<Payload> out;
        private final HistorySend<Signed<Packet<VerificationKey, Payload>>> h;

        Outgoing(Send<Payload> out, HistorySend<Signed<Packet<VerificationKey, Payload>>> h, VerificationKey k) {

            if (k == null || out == null || h == null) throw new NullPointerException();

            this.out = out;
            this.h = h;
        }

        public List<Signed<Packet<VerificationKey, Payload>>> history() {
            return h.history();
        }

        public Signed<com.shuffle.chan.packet.Packet<VerificationKey, Payload>> last() {
            return h.last();
        }
    }

    // Outgoing channels.
    final Map<VerificationKey, Outgoing> net = new HashMap<>();

    // Where new messages come in. All messages are logged and have their signature
    // checked as they come in.
    private final Receive<Inbox.Envelope<VerificationKey,
            Signed<com.shuffle.chan.packet.Packet<VerificationKey, Payload>>>> receive;

    final Bytestring session;
    final SigningKey me;

    public final MessageDigest sha256;
    public final Marshaller<Message.Atom> atomMarshaller;
    public final Marshaller<Address> addressMarshaller;

    public Messages(Bytestring session,
                    SigningKey me,
                    Map<VerificationKey,
                            Send<Signed<Packet<VerificationKey, Payload>>>> net,
                    Receive<Inbox.Envelope<VerificationKey,
                            Signed<Packet<VerificationKey, Payload>>>> receive,
                    ShuffleMarshaller m) throws NoSuchAlgorithmException {

        if (session == null || me == null || net == null || receive == null) {
            System.out.println("Unable-99");
            throw new NullPointerException();
        }
        
        // net will have one less entry than it should...

        this.session = session;
        this.me = me;
        this.receive = new HistoryReceive<>(receive);

        sha256 = MessageDigest.getInstance("SHA-256");
        this.atomMarshaller = m.atomMarshaller();
        this.addressMarshaller = m.addressMarshaller();
        Marshaller<Packet<VerificationKey, Payload>> pm = m.packetMarshaller();

        VerificationKey vk = me.VerificationKey();

        for (Map.Entry<VerificationKey, Send<Signed<Packet<VerificationKey, Payload>>>> z : net.entrySet()) {
            
            System.out.println("me: " + vk + " peer: " + z.getKey());
            VerificationKey k = z.getKey();
            if (vk.equals(k)) continue;
            
            HistorySend<Signed<Packet<VerificationKey, Payload>>> h = new HistorySend<>(z.getValue());
            Send<Packet<VerificationKey, Payload>> signer = new SigningSend<>(h, pm, me);
            Send<Payload> p = new OutgoingPacketSend<>(signer, session, vk, k);
            this.net.put(k, new Outgoing(p, h, vk));
        }

        // We have a special channel for sending messages to ourselves.
        HistorySend<Signed<Packet<VerificationKey, Payload>>> h = new HistorySend<>(
                new IgnoreSend<Signed<Packet<VerificationKey, Payload>>>());

        Send<Payload> p = new OutgoingPacketSend<>(new SigningSend<>(h, pm, me), session, vk, vk);
        this.net.put(vk, new Outgoing(p, h, vk));
        
        for (Map.Entry<VerificationKey, Send<Signed<Packet<VerificationKey, Payload>>>> z : net.entrySet()) {
            VerificationKey k = z.getKey();
            if (!this.net.containsKey(k)) {
                System.out.println("me: " + vk + " Error " + k + " is not contained");
            } else {
                System.out.println("me: " + vk + " peer: " + k + " is in net");
                if (this.net.get(k) == null) {
                    System.out.println("NULL: me: " + vk + " peer: " + k + " is null");
                }
            }
        }
        System.out.println("Net " + net.size());
        System.out.println("Net2 " + this.net.size());
    }

    @Override
    public com.shuffle.protocol.message.Message make() {
        return new Message(this);
    }

    @Override
    public com.shuffle.protocol.message.Packet receive() throws InterruptedException, IOException {

        // TODO make this a parameter.
        Inbox.Envelope<VerificationKey,
                Signed<com.shuffle.chan.packet.Packet<VerificationKey, Payload>>> e
                = receive.receive(10000, TimeUnit.MILLISECONDS);

        if (e == null) return null;

        return new SignedPacket(e.payload);
    }

    public VerificationKey identity() {
        return me.VerificationKey();
    }

    // Some cleanup after we're done.
    public void close() throws InterruptedException {
        for (Outgoing p : net.values()) {
            p.out.close();
        }

        net.clear();
    }

    public SignedPacket send(Message m, Phase phase, VerificationKey to) throws InterruptedException, IOException {

        Outgoing x = m.messages.net.get(to);

        if (x == null) {
            if (m.messages.net.containsKey(to)) {
                System.out.println("me: " + this.me.VerificationKey() + " peer: " +  to + " Unable-20");
            } else {
                System.out.println("me: " + this.me.VerificationKey() + " peer: " + to + " Unable-21");
            }
            System.out.println("Unable-10" + m.messages.net.values());
            return null;
        }

        // About to send message.
        if (!x.out.send(new Payload(phase, m))) {
            System.out.println("Unable");
            return null;
        }

        SignedPacket sp = new SignedPacket(x.last());
        if (sp == null || x.last() == null) {System.out.println("Unable-2");}

        return sp;
    }
}
