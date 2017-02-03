package com.shuffle.player;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.shuffle.bitcoin.Address;
import com.shuffle.bitcoin.DecryptionKey;
import com.shuffle.bitcoin.EncryptionKey;
import com.shuffle.bitcoin.Signatures;
import com.shuffle.bitcoin.Transaction;
import com.shuffle.bitcoin.VerificationKey;
import com.shuffle.chan.packet.Marshaller;
import com.shuffle.chan.packet.Packet;
import com.shuffle.chan.packet.Signed;
import com.shuffle.p2p.Bytestring;
import com.shuffle.player.proto.Proto;
import com.shuffle.protocol.FormatException;
import com.shuffle.protocol.blame.Blame;
import com.shuffle.protocol.blame.Reason;
import com.shuffle.protocol.message.Phase;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Created by Daniel Krawisz on 7/2/16.
 */
public abstract class Protobuf implements Messages.ShuffleMarshaller {

    // Unmarshall an address from its string representation.
    public abstract Address unmarshallAdress(String str) throws FormatException;

    // Unmarshall an encryption key from a string.
    public abstract EncryptionKey unmarshallEncryptionKey(String str) throws InvalidKeySpecException, NoSuchAlgorithmException;

    // Unmarshall a decryption key.
    public abstract DecryptionKey unmarshallDecryptionKey(String privString, String pubString);

    // Unmarshall a verification key.
    public abstract VerificationKey unmarshallVerificationKey(String str);

    // Unmarshall a Transaction
    public abstract Transaction unmarshallTransaction(byte[] bytes) throws FormatException;

    public static Proto.Signed.Builder marshallSignedPacket(com.shuffle.protocol.message.Packet p) {
        if (p == null || !(p instanceof Messages.SignedPacket)) {
            throw new IllegalArgumentException("Unknown implementation of Packet.");
        }

        return marshallSignedPacket(((Messages.SignedPacket) p).packet);
    }

    public static Proto.Packet.Builder marshallPacket(com.shuffle.chan.packet.Packet<VerificationKey, Payload> p) {
        Proto.Phase phase;
        switch(p.payload.phase) {
            case Announcement: {
                phase = Proto.Phase.ANNOUNCEMENT;
                break;
            }
            case Shuffling: {
                phase = Proto.Phase.SHUFFLE;
                break;
            }
            case BroadcastOutput: {
                phase = Proto.Phase.BROADCAST;
                break;
            }
            case EquivocationCheck: {
                phase = Proto.Phase.EQUIVOCATION_CHECK;
                break;
            }
            case VerificationAndSubmission: {
                phase = Proto.Phase.VERIFICATION_AND_SUBMISSION;
                break;
            }
            case Blame: {
                phase = Proto.Phase.BLAME;
                break;
            }
            default : {
                throw new IllegalArgumentException("Invalid phase " + p.payload.phase);
            }
        }

        Proto.Message.Builder mb = Proto.Message.newBuilder();
        Message msg = p.payload.message;

        if (msg == null) {
           throw new IllegalArgumentException("Null or unknown Message format.");
        }

        Proto.Packet.Builder pb = Proto.Packet.newBuilder()
                .setSession(ByteString.copyFrom(p.session.bytes))
                .setTo(Proto.VerificationKey.newBuilder()
                        .setKey(p.to.toString()))
                .setFrom(Proto.VerificationKey.newBuilder()
                        .setKey(p.from.toString()))
                .setNumber(p.sequenceNumber)
                .setPhase(phase)
                .setMessage(mb);

        Message.Atom atom = (msg).atoms;
        if (atom != null) {
            pb.setMessage(marshallAtom(atom));
        }

        return pb;
    }

    public static Proto.Message.Builder marshallAtom(Message.Atom atom) {
        Proto.Message.Builder ab = Proto.Message.newBuilder();

        if (atom.addr != null) {
            ab.setAddress(Proto.Address.newBuilder().setAddress(atom.addr.toString()));
        } else if (atom.ek != null) {
            ab.setKey(Proto.EncryptionKey.newBuilder().setKey(atom.ek.toString()));
        } else if (atom.secureHash != null) {
            ab.setHash(Proto.Hash.newBuilder().setHash(
                    ByteString.copyFrom(atom.secureHash.hashed.bytes)));
        } else if (atom.sigs != null) {
            Proto.Signatures.Builder builder = Proto.Signatures.newBuilder();
            for (Bytestring b : atom.sigs.sigs) {
                builder.addSignatures(ByteString.copyFrom(b.bytes));
            }
            ab.setSignatures(builder);
        } else if (atom.string != null) {
            ab.setStr(atom.string);
        } else if (atom.blame != null) {
            ab.setBlame(marshallBlame(atom.blame));
        } else {
            throw new IllegalArgumentException("Atom cannot be empty.");
        }

        if (atom.next != null) {
            ab.setNext(marshallAtom(atom.next));
        }

        return ab;
    }

    public static Proto.Signed.Builder marshallSignedPacket(Signed<com.shuffle.chan.packet.Packet<VerificationKey, Payload>> p) {
        return Proto.Signed.newBuilder().setPacket(marshallPacket(p.message)).setSignature(
                Proto.Signature.newBuilder().setSignature(ByteString.copyFrom(p.signature.bytes)));
    }

    public static Proto.Blame.Builder marshallBlame(Blame b) {
        Proto.Reason reason;

        if (b.reason == Reason.InsufficientFunds) {
            reason = Proto.Reason.INSUFFICIENTFUNDS;
        } else if (b.reason == Reason.DoubleSpend) {
            reason = Proto.Reason.DOUBLESPEND;
        } else if (b.reason == Reason.EquivocationFailure) {
            reason = Proto.Reason.EQUIVOCATIONFAILURE;
        } else if (b.reason == Reason.ShuffleFailure) {
            reason = Proto.Reason.SHUFFLEFAILURE;
        } else if (b.reason == Reason.ShuffleAndEquivocationFailure) {
            reason = Proto.Reason.SHUFFLEANDEQUIVOCATIONFAILURE;
        } else if (b.reason == Reason.InvalidSignature) {
            reason = Proto.Reason.INVALIDSIGNATURE;
        } else if (b.reason == Reason.MissingOutput) {
            reason = Proto.Reason.MISSINGOUTPUT;
        } else if (b.reason == Reason.Liar) {
            reason = Proto.Reason.LIAR;
        } else if (b.reason == Reason.InvalidFormat) {
            reason = Proto.Reason.INVALIDFORMAT;
        } else {
            throw new IllegalArgumentException("Invalid blame reason " + b.reason);
        }

        Proto.Blame.Builder bb = Proto.Blame.newBuilder().setReason(reason);

        if (b.accused != null) {
            bb.setAccused(Proto.VerificationKey.newBuilder().setKey(b.accused.toString()));
        }

        if (b.privateKey != null) {
            bb.setKey(Proto.DecryptionKey.newBuilder()
                    .setKey(b.privateKey.toString())
                    .setPublic(b.privateKey.EncryptionKey().toString()));
        }

        if (b.t != null) {
            bb.setTransaction(Proto.Transaction.newBuilder().setTransaction(ByteString.copyFrom(b.t.serialize().bytes)));
        }

        if (b.invalid != null) {
            bb.setInvalid(Proto.Invalid.newBuilder().setInvalid(ByteString.copyFrom(b.invalid.bytes)));
        }

        if (b.packets != null) {
            Proto.Packets.Builder builder = Proto.Packets.newBuilder();

            for (com.shuffle.protocol.message.Packet p : b.packets) {
                builder.addPacket(marshallSignedPacket(p));
            }

            bb.setPackets(builder);
        }

        return bb;
    }

    public final Message.Atom unmarshallAtom(Proto.Message atom) throws FormatException {

        Object o;
        // Only one field is allowed to be set in the Atom.
        if (!atom.getStr().equals("")) {
            if (atom.hasAddress() || atom.hasKey() || atom.hasHash() || atom.hasSignatures() || atom.hasBlame()) {
                throw new FormatException("Atom contains more than one value.");
            }

            o = atom.getStr();
        } else if (atom.hasAddress()) {
            if (atom.hasKey() || atom.hasHash() || atom.hasSignatures() || atom.hasBlame()) {
                throw new FormatException("Atom contains more than one value.");
            }

            o = unmarshallAdress(atom.getAddress().getAddress());
        } else if (atom.hasKey()) {
            if (atom.hasHash() || atom.hasSignatures() || atom.hasBlame()) {
                throw new FormatException("Atom contains more than one value.");
            }

            try {
                o = unmarshallEncryptionKey(atom.getKey().getKey());
            } catch (NumberFormatException
                    | NoSuchAlgorithmException
                    | InvalidKeySpecException e) {
                throw new FormatException("Could not read " + atom.getKey().getKey() + " as number.");
            }
        } else if (atom.hasHash()) {
            if (atom.hasSignatures() || atom.hasBlame()) {
                throw new FormatException("Atom contains more than one value.");
            }

            o = new Message.SecureHash(new Bytestring(atom.getHash().getHash().toByteArray()));
        } else if (atom.hasSignatures()) {
            if (atom.hasBlame()) throw new FormatException("Atom contains more than one value.");

            Bytestring[] arr = new Bytestring[atom.getSignatures().getSignaturesList().size()];

            int index = 0;
            for (ByteString b : atom.getSignatures().getSignaturesList()) {
                arr[index] = new Bytestring(b.toByteArray());
                index++;
            }

            o = new Signatures(arr);
        } else if (atom.hasBlame()) {
            o = unmarshallBlame(atom.getBlame());
        } else {
            throw new FormatException("Atom contains no values.");
        }

        if (atom.hasNext()) {
            return Message.Atom.make(o, unmarshallAtom(atom.getNext()));
        } else {
            return Message.Atom.make(o);
        }
    }

    public final Blame unmarshallBlame(Proto.Blame blame) throws FormatException {
        Reason reason;
        switch (blame.getReason()) {
            case INSUFFICIENTFUNDS: {
                reason = Reason.InsufficientFunds;
                break;
            }
            case DOUBLESPEND: {
                reason = Reason.DoubleSpend;
                break;
            }
            case EQUIVOCATIONFAILURE: {
                reason = Reason.EquivocationFailure;
                break;
            }
            case SHUFFLEFAILURE: {
                reason = Reason.ShuffleFailure;
                break;
            }
            case SHUFFLEANDEQUIVOCATIONFAILURE: {
                reason = Reason.ShuffleAndEquivocationFailure;
                break;
            }
            case INVALIDSIGNATURE: {
                reason = Reason.InvalidSignature;
                break;
            }
            case MISSINGOUTPUT: {
                reason = Reason.MissingOutput;
                break;
            }
            case LIAR: {
                reason = Reason.Liar;
                break;
            }
            case INVALIDFORMAT: {
                reason = Reason.InvalidFormat;
                break;
            }
            default: {
                throw new FormatException("Unknown blame reason " + blame.getReason());
            }
        }

        VerificationKey accused = null;
        if (blame.hasAccused()) {
            try {
                accused = unmarshallVerificationKey(blame.getAccused().getKey());
            } catch (NumberFormatException e) {
                throw new FormatException(e.getMessage());
            }
        }

        DecryptionKey key = null;
        if (blame.hasKey()) {
            try {
                key = unmarshallDecryptionKey(blame.getKey().getKey(), blame.getKey().getPublic());
            } catch (NumberFormatException e) {
                throw new FormatException(e.getMessage());
            }
        }

        Transaction t = null;
        if (blame.hasTransaction()) {
            t = unmarshallTransaction(blame.getTransaction().getTransaction().toByteArray());
        }

        Bytestring invalid = null;
        if (blame.hasInvalid()) {
            invalid = new Bytestring(blame.getInvalid().getInvalid().toByteArray());
        }

        Queue<com.shuffle.protocol.message.Packet> packets = null;
        if (blame.hasPackets()) {
            packets = unmarshallPackets(blame.getPackets());
        }

        return new Blame(reason, accused, t, key, invalid, packets);
    }

    public final Queue<com.shuffle.protocol.message.Packet> unmarshallPackets(Proto.Packets pp) throws FormatException {
        Queue<com.shuffle.protocol.message.Packet> packets = new LinkedList<>();

        for (Proto.Signed p : pp.getPacketList()) {
            packets.add(new Messages.SignedPacket(unmarshallSignedPacket(p)));
        }

        return packets;
    }

    public final Signed<com.shuffle.chan.packet.Packet<VerificationKey, Payload>> unmarshallSignedPacket(Proto.Signed sp) throws FormatException {
        if (!(sp.hasSignature() && sp.hasPacket() && sp.getPacket().hasFrom())) {
            throw new FormatException("All entries in Signed must be filled:" + sp);
        }

        return new Signed<>(
                new Bytestring(sp.getPacket().toByteArray()),
                new Bytestring(sp.getSignature().getSignature().toByteArray()),
                unmarshallVerificationKey(sp.getPacket().getFrom().getKey()),
                packetMarshaller);
    }

    public final com.shuffle.chan.packet.Packet<VerificationKey, Payload> unmarshallPacket(Proto.Packet p) throws FormatException {
        if (!(p.hasFrom() && p.hasTo() && p.hasMessage())) {
            throw new FormatException("All entries in Packet must be filled: " + p);
        }

        Phase phase;
        switch (p.getPhase()) {
            case ANNOUNCEMENT: {
                phase = Phase.Announcement;
                break;
            }
            case SHUFFLE: {
                phase = Phase.Shuffling;
                break;
            }
            case BROADCAST: {
                phase = Phase.BroadcastOutput;
                break;
            }
            case EQUIVOCATION_CHECK: {
                phase = Phase.EquivocationCheck;
                break;
            }
            case VERIFICATION_AND_SUBMISSION: {
                phase = Phase.VerificationAndSubmission;
                break;
            }
            case BLAME: {
                phase = Phase.Blame;
                break;
            }
            default: {
                throw new FormatException("Invalid phase " + p.getPhase());
            }
        }

        return new com.shuffle.chan.packet.Packet<>(
                new Bytestring(p.getSession().toByteArray()),
                unmarshallVerificationKey(p.getFrom().getKey()),
                unmarshallVerificationKey(p.getTo().getKey()),
                p.getNumber(),
                new Payload(phase, new Message(unmarshallAtom(p.getMessage()), addressMarshaller(), null)));

    }

    public final Marshaller<Packet<VerificationKey, Payload>> packetMarshaller;
    public final Marshaller<Message.Atom> atomMarshaller;
    public final Marshaller<Signed<Packet<VerificationKey, Payload>>> signedMarshaller;

    public Protobuf() {
        packetMarshaller = new PacketMarshaller();
        atomMarshaller = new AtomMarshaller();
        signedMarshaller = new SignedMarshaller();
    }

    @Override
    public Marshaller<Message.Atom> atomMarshaller() {
        return atomMarshaller;
    }

    @Override
    public Marshaller<com.shuffle.chan.packet.Packet<VerificationKey, Payload>> packetMarshaller() {
        return packetMarshaller;
    }

    @Override
    public Marshaller<Signed<Packet<VerificationKey, Payload>>> signedMarshaller() {
        return signedMarshaller;
    }

    class AtomMarshaller implements Marshaller<Message.Atom> {

        @Override
        public Bytestring marshall(Message.Atom atom) {
            return new Bytestring(marshallAtom(atom).build().toByteArray());
        }

        @Override
        public Message.Atom unmarshall(Bytestring string) throws FormatException {

            Proto.Message atom;
            try {
                atom = Proto.Message.parseFrom(string.bytes);
            } catch (InvalidProtocolBufferException e) {
                throw new FormatException("Could not read " + Arrays.toString(string.bytes));
            }

            return unmarshallAtom(atom);
        }
    }

    class PacketMarshaller implements Marshaller<Packet<VerificationKey, Payload>> {

        @Override
        public Bytestring marshall(com.shuffle.chan.packet.Packet<VerificationKey, Payload> p) throws IOException {
            return new Bytestring(marshallPacket(p).build().toByteArray());
        }

        @Override
        public com.shuffle.chan.packet.Packet<VerificationKey, Payload> unmarshall(Bytestring string) throws FormatException {
            try {
                return unmarshallPacket(Proto.Packet.parseFrom(string.bytes));
            } catch (InvalidProtocolBufferException e) {
                throw new FormatException("Could not read " + string + " as Packet.");
            }
        }
    }

    private class SignedMarshaller implements Marshaller<Signed<Packet<VerificationKey, Payload>>> {

        @Override
        public Bytestring marshall(Signed<Packet<VerificationKey, Payload>> signed) throws IOException {
            return new Bytestring(marshallSignedPacket(signed).build().toByteArray());
        }

        @Override
        public Signed<Packet<VerificationKey, Payload>> unmarshall(Bytestring string) throws FormatException {
            try {
                return unmarshallSignedPacket(Proto.Signed.parseFrom(string.bytes));
            } catch (InvalidProtocolBufferException e) {
                throw new FormatException("Could not read " + string + " as Packet.");
            }
        }
    }
}
