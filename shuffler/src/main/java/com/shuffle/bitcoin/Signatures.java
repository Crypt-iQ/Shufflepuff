package com.shuffle.bitcoin;

import com.shuffle.p2p.Bytestring;

import java.util.Arrays;

/**
 * Created by nsa on 2/1/17.
 * 
 * This class contains an array of encryption signatures as Bytestring objects.
 * The Signatures class was made so that users can enter in multiple Unspent Transaction
 * Outputs into one round of ShufflePuff.
 */
public class Signatures {

    public Bytestring[] sigs;

    public Signatures(Bytestring[] sigs) {
        this.sigs = sigs;
    }

    @Override
    public String toString() {
        return "signatures[" + sigs + "]";
    }

    @Override
    public int hashCode() {
        return sigs.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Signatures)) {
            return false;
        }

        Signatures o = (Signatures) obj;

        if (sigs.length != o.sigs.length) return false;

        for (Bytestring sig : sigs) {
            if (!Arrays.asList(o.sigs).contains(sig)) return false;
        }

        return true;
    }

}
