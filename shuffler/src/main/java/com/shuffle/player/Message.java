package com.shuffle.player;

import com.shuffle.bitcoin.Address;
import com.shuffle.bitcoin.EncryptionKey;
import com.shuffle.bitcoin.VerificationKey;
import com.shuffle.chan.packet.Marshaller;
import com.shuffle.p2p.Bytestring;
import com.shuffle.protocol.FormatException;
import com.shuffle.protocol.blame.Blame;
import com.shuffle.protocol.message.Phase;

import java.io.IOException;
import java.io.Serializable;
import java.security.MessageDigest;

/**
 * Implementation of coin shuffle messages.
 *
 * Created by Daniel Krawisz on 5/26/16.
 */
public class Message implements com.shuffle.protocol.message.Message, Serializable {

    public static class SecureHash implements Serializable {
        public final Bytestring hashed;

        public SecureHash(MessageDigest digest, Marshaller<Atom> m, Atom toHash) throws FormatException, IOException {
            digest.update(m.marshall(toHash).bytes);

            hashed = new Bytestring(digest.digest());

            digest.reset();
        }

        public SecureHash(Bytestring b) {
            hashed = b;
        }

        public String toString() {
            return "hashed[" + hashed + "]";
        }

        public boolean equals(Object o) {
            if (o == null) {
                return false;
            }

            if (!(o instanceof SecureHash)) {
                return false;
            }

            SecureHash mockHashed = (SecureHash) o;

            return hashed.equals(mockHashed.hashed);
        }
    }

    public static class Atom implements Serializable {
        public final Address addr;
        public final EncryptionKey ek;
        public final SecureHash secureHash;
        public final Bytestring sig;
        public final String string;
        public final Blame blame;

        public final Atom next;

        public Atom(
                Address addr,
                EncryptionKey ek,
                SecureHash secureHash,
                Bytestring sig,
                String string,
                Blame blame,
                //com.shuffle.protocol.message.Packet packet,
                Atom next
        ) {
            // Enforce the correct format.
            format:
            {
                if (addr != null) {
                    if (ek != null || sig != null || secureHash != null
                            || blame != null || string != null) {

                        throw new IllegalArgumentException();
                    }
                    break format;
                }

                if (ek != null) {
                    if (sig != null || secureHash != null || string != null || blame != null) {
                        throw new IllegalArgumentException();
                    }
                    break format;
                }

                if (sig != null) {
                    if (secureHash != null || string != null || blame != null) {
                        throw new IllegalArgumentException();
                    }
                    break format;
                }

                if (secureHash != null) {
                    if (string != null || blame != null) {
                        throw new IllegalArgumentException();
                    }
                    break format;
                }

                if (string != null) {
                    if (blame != null) {
                        throw new IllegalArgumentException();
                    }
                    break format;
                }

                if (blame != null) {
                    break format;
                }

                throw new IllegalArgumentException();
            }

            this.addr = addr;
            this.ek = ek;
            this.sig = sig;
            this.secureHash = secureHash;
            this.string = string;
            this.blame = blame;
            this.next = next;
        }

        public static Atom make(Object o, Atom next) {
            if (o instanceof Address) {
                return new Atom((Address) o, null, null, null, null, null, next);
            }
            if (o instanceof EncryptionKey) {
                return new Atom(null, (EncryptionKey) o, null, null, null, null, next);
            }
            if (o instanceof SecureHash) {
                return new Atom(null, null, (SecureHash) o, null, null, null, next);
            }
            if (o instanceof Bytestring) {
                return new Atom(null, null, null, (Bytestring) o, null, null, next);
            }
            if (o instanceof String) {
                return new Atom(null, null, null, null, (String)o, null, next);
            }
            if (o instanceof Blame) {
                return new Atom(null, null, null, null, null, (Blame) o, next);
            }

            throw new IllegalArgumentException();
        }

        public static Atom make(Object o) {
            return make(o, null);
        }

        private static Atom attach(Atom a, Atom o) {
            if (a == null) {
                return o;
            }

            return new Atom(a.addr, a.ek, a.secureHash, a.sig, a.string, a.blame, attach(a.next, o));
        }

        @Override
        public boolean equals(Object o) {
            if (o == null) {
                return false;
            }

            if (!(o instanceof Atom)) {
                return false;
            }

            Atom a = (Atom) o;

            return this == a || (a.sig == null && sig == null || sig != null && sig.equals(a.sig))
                    && (a.ek == null && ek == null || ek != null && ek.equals(a.ek))
                    && (a.addr == null && addr == null || addr != null && addr.equals(a.addr))
                    && (a.blame == null && blame == null || blame != null && blame.equals(a.blame))
                    && (a.secureHash == null && secureHash == null || secureHash != null && secureHash.equals(a.secureHash))
                    && (a.string == null && string == null || string != null && string.equals(a.string))
                    && (a.next == null && next == null || next != null && next.equals(a.next));
        }

        @Override
        public int hashCode() {
            int hash = addr == null ? 0 : addr.hashCode();
            hash = hash * 15 + (ek == null ? 0 : ek.hashCode());
            hash = hash * 15 + (sig == null ? 0 : sig.hashCode());
            hash = hash * 15 + (this.secureHash == null ? 0 : this.secureHash.hashCode());
            hash = hash * 15 + (blame == null ? 0 : blame.hashCode());
            hash = hash * 15 + (next == null ? 0 : next.hashCode());
            return hash;
        }

        @Override
        public String toString() {
            String str = "";

            if (addr != null) str += ("\"" + addr.toString() + "\"");

            if (ek != null) str += ("\"" + ek.toString() + "\"");

            if (sig != null) str += sig.toString();

            if (secureHash != null) str += secureHash.toString();

            if (string != null) str += ("\"" + string + "\"");

            if (blame != null) str += blame.toString();

            if (next != null) str += "⊕" + next.toString();

            return str;
        }
    }

    public final Atom atoms;

    // If this message can be sent, then this is the network by
    // which it is sent. Otherwise, it's null.
    final transient Messages messages;
    final transient Marshaller<Address> addressMarshaller;

    public Message(Messages messages) {
        if (messages == null) throw new NullPointerException();

        atoms = null;
        this.messages = messages;
        this.addressMarshaller = messages.addressMarshaller;
    }

    public Message(Atom atom, Marshaller<Address> addressMarshaller, Messages messages) {
        if (addressMarshaller == null) throw new NullPointerException();

        atoms = atom;
        this.messages = messages;
        this.addressMarshaller = addressMarshaller;
    }

    @Override
    public boolean isEmpty() {
        return atoms == null;
    }

    @Override
    public com.shuffle.protocol.message.Message attach(EncryptionKey ek) {
        if (ek == null) throw new NullPointerException();

        return new Message(Atom.attach(atoms, Atom.make(ek)), addressMarshaller, messages);
    }

    @Override
    public com.shuffle.protocol.message.Message attach(Address addr) {
        if (addr == null) throw new NullPointerException();

        return new Message(Atom.attach(atoms, Atom.make(addr)), addressMarshaller, messages);
    }

    @Override
    public com.shuffle.protocol.message.Message attach(String str) {
        if (str == null) throw new NullPointerException();

        return new Message(Atom.attach(atoms, Atom.make(str)), addressMarshaller, messages);
    }

    @Override
    public com.shuffle.protocol.message.Message attach(Bytestring sig) {
        if (sig == null) throw new NullPointerException();

        return new Message(Atom.attach(atoms, Atom.make(sig)), addressMarshaller, messages);
    }

    @Override
    public com.shuffle.protocol.message.Message attach(Blame blame) {
        if (blame == null) throw new NullPointerException();

        return new Message(Atom.attach(atoms, Atom.make(blame)), addressMarshaller, messages);
    }

    public com.shuffle.protocol.message.Message hashed() throws FormatException, IOException {

        return new Message(Atom.make(
                new SecureHash(messages.sha256, messages.atomMarshaller, this.atoms)),
                addressMarshaller, messages);
    }

    @Override
    public EncryptionKey readEncryptionKey() throws FormatException {
        if (atoms == null || atoms.ek == null) throw new FormatException("Encryption key not found.");

        return atoms.ek;
    }

    @Override
    public Address readAddress() throws FormatException {
        if (atoms == null) throw new FormatException("Address not found");
        if (atoms.addr != null) return atoms.addr;
        else if (atoms.string == null) throw new FormatException("Address not found.");
        // Attempt to read string as an address.
        return addressMarshaller.unmarshall(new Bytestring(atoms.string.getBytes()));
    }

    @Override
    public String readString() throws FormatException {
        if (atoms == null || atoms.string == null)
            throw new FormatException("String not found.");

        return atoms.string;
    }

    @Override
    public Blame readBlame() throws FormatException {
        if (atoms == null || atoms.blame == null) throw new FormatException("Blame not found");

        return atoms.blame;
    }

    @Override
    public Bytestring readSignature() throws FormatException {
        if (atoms == null || atoms.sig == null) throw new FormatException("Signature not found");

        return atoms.sig;
    }

    @Override
    public com.shuffle.protocol.message.Message rest() throws FormatException {

        if (atoms == null) throw new FormatException("Rest called on last element.");

        return new Message(atoms.next, addressMarshaller, messages);
    }

    @Override
    public com.shuffle.protocol.message.Packet send(Phase phase, VerificationKey to)
            throws InterruptedException, IOException {

        if (messages == null) {
            System.out.println("Unable-1");
            return null;
        }

        Messages.SignedPacket sp = messages.send(this, phase, to);
        if (sp == null) {System.out.println("Unable-3");}
        return sp;
    }

    @Override
    public boolean equals(Object o) {

        if (o == null) return false;

        if (!(o instanceof Message)) return false;

        Message mock = (Message) o;

        return ((atoms == null && mock.atoms == null)
                || (atoms != null && atoms.equals(mock.atoms)));
    }

    @Override
    public int hashCode() {
        if (atoms == null) return 0;

        int hash = 0;
        hash = hash * 15 + atoms.hashCode();
        return hash;
    }

    @Override
    public String toString() {
        if (atoms == null) return "[]";

        return atoms.toString();
    }
}
