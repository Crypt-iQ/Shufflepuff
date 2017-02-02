package com.shuffle.bitcoin.impl;

import com.shuffle.bitcoin.Address;
import com.shuffle.protocol.FormatException;

import org.bitcoinj.core.AddressFormatException;

/**
 * Created by conta on 10.03.16.
 */
public class AddressImpl implements Address {

   org.bitcoinj.core.Address address;

   AddressImpl(org.bitcoinj.core.Address address) {
      this.address = address;
   }

   public AddressImpl(String address) throws FormatException {
      try {
         this.address = new org.bitcoinj.core.Address(
                 org.bitcoinj.core.Address.getParametersFromAddress(address), address);
      } catch (AddressFormatException e) {
         throw new FormatException("Could not parse address " + address);
      }
   }

   @Override
   public int hashCode() {
      return address.hashCode();
   }

   public String toString() {
      return this.address.toString();
   }

   @Override
   public int compareTo(Address o) {
      if (!(o instanceof AddressImpl)) {
         throw new IllegalArgumentException("unable to compare with other address");
      }
      return address.compareTo(((AddressImpl)o).address);
   }

   @Override
   public boolean equals(Object obj) {
      return obj instanceof AddressImpl && address.equals(((AddressImpl) obj).address);
   }
}
