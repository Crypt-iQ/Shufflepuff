package com.shuffle.bitcoin.blockchain;

import com.neemre.btcdcli4j.core.BitcoindException;
import com.neemre.btcdcli4j.core.CommunicationException;
import com.shuffle.bitcoin.Address;
import com.shuffle.bitcoin.CoinNetworkException;
import com.shuffle.bitcoin.impl.AddressUtxoImpl;
import com.shuffle.p2p.Bytestring;

import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.store.BlockStoreException;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import javax.xml.bind.DatatypeConverter;
import javax.xml.bind.annotation.adapters.HexBinaryAdapter;

/**
 * Created by Daniel Krawisz on 7/26/16.
 */
public class TestBitcoin {
    private class MockBitcoin extends Bitcoin {
        public MockBitcoin() {
            super(NetworkParameters.fromID(NetworkParameters.ID_TESTNET), 3);
        }

        @Override
        public boolean isUtxo(String transactionHash, int vout) {
            return true;
        }

        @Override
        public com.shuffle.bitcoin.Transaction getConflictingTransactionInner(com.shuffle.bitcoin.Transaction t, Address a, long amount) throws CoinNetworkException, AddressFormatException, BlockStoreException, BitcoindException, CommunicationException, IOException {
            return null;
        }

        @Override
        protected List<Transaction> getTransactionsFromUtxosInner(AddressUtxoImpl address) throws IOException, CoinNetworkException, AddressFormatException {
            return null;
        }

        @Override
        org.bitcoinj.core.Transaction getTransaction(String transactionHash) throws IOException {
            return null;
        }

        protected List<Transaction> getAddressTransactions(String address) {
            HexBinaryAdapter adapter = new HexBinaryAdapter();
            Context context = Context.getOrCreate(this.netParams);

            List<Transaction> txList = new LinkedList<>();


            /**
             * Mining addresses -- outputs are NOT all P2PKH.
             */

            byte[] bytex2 = adapter.unmarshal("01000000010000000000000000000000000000000000000000000000000000000000000000ffffffff0d03491c030155062f503253482fffffffff0100f2052a010000002321020cf3600b55a8782a2ef594d8c4b1d5cdf5d29f826ae72fb8b0ceb43a1200fabeac00000000");
            org.bitcoinj.core.Transaction btx2 = new org.bitcoinj.core.Transaction(this.netParams, bytex2);
            Transaction tx2 = new Transaction("77b291dcbf6483719ca4287045ee8faef19f03b3728fc8b0f697296b9da9978e",btx2,false,true);
            //txList.add(tx2);

            byte[] bytex3 = adapter.unmarshal("01000000010000000000000000000000000000000000000000000000000000000000000000ffffffff0e03d01c0302fa21062f503253482fffffffff011019062a01000000232102a525e0acc35f97b9b742f3d881910951c4e5303fe5e295ad723d08893aa86942ac00000000");
            org.bitcoinj.core.Transaction btx3 = new org.bitcoinj.core.Transaction(this.netParams, bytex3);
            Transaction tx3 = new Transaction("f4a4a742ef159e93e1133f85079536f7f7c0ff467a7b7fbf47cdeb72d30fcbb3",btx3,false,true);
            //txList.add(tx3);

            // This transaction works for sufficientFunds because the output address is P2PKH
            byte[] bytex4 = adapter.unmarshal("01000000010000000000000000000000000000000000000000000000000000000000000000ffffffff2503ca7b04184b6e434d696e657242510cb670ab1c4b086d542ee8d32b8d01000000d4040000ffffffff0100f90295000000001976a9149e8985f82bc4e0f753d0492aa8d11cc39925774088ac00000000");
            org.bitcoinj.core.Transaction btx4 = new org.bitcoinj.core.Transaction(this.netParams, bytex4);
            Transaction tx4 = new Transaction("47d37f362f400cec3e9366b0701b2c54a8a85aba0ceda48c8d27343031e7f2ae",btx4,false,true);
            //txList.add(tx4);

            byte[] bytex5 = adapter.unmarshal("01000000010000000000000000000000000000000000000000000000000000000000000000ffffffff0d03ca9504015c062f503253482fffffffff0110fd0295000000002321030fff984dad779eb2804c73ae41038b3db2402dd33e64753f2eab5ad37784792eac00000000");
            org.bitcoinj.core.Transaction btx5 = new org.bitcoinj.core.Transaction(this.netParams, bytex5);
            Transaction tx5 = new Transaction("635635211f84e5e89bb197ccf514c531e51f5ca2ba8ce4da720250b4a29f3478",btx5,false,true);
            //txList.add(tx5);


            /**
             * Non-mining transaction -- sufficientFunds works
             */

            byte[] bytex6 = adapter.unmarshal("0100000001d7b3d434fad5961b5a1ca8083b9c83f8bc7ba283c027dc34cbea7a42fefc7ebe010000006b483045022100ee44af88e7295dfe6ab0f556c84bb722fea7271626f79dc914f5a1b4991f51a102204fd79b0a062cadad1682aed8094bf639f957f0e43d9337145f48521c8509ba92012102230d12de280ef92660c166054b194d09000e8328dbb33ef2667d1dd1ccf52369feffffff02c0fc9b01000000001976a91423e077ffac6f109795a82021dc1698bd9ce4011988ac1a52641a000000001976a9140c878fdf88351cc9f4ced15df3aea8183b604f0688acd5cb0800");
            org.bitcoinj.core.Transaction btx6 = new org.bitcoinj.core.Transaction(this.netParams, bytex6);
            Transaction tx6 = new Transaction("20b2ec5e7d8127241230104d6c776f3bb87d5a20c2ba9e9d92f4658017181201",btx6,false,true);
            //txList.add(tx6);




            // Daniel's transactions
            byte[] bytex7 = adapter.unmarshal("0100000001d38c06624d53f0ed48d05046d485d739f3076ce701655bb9ce2157fe412cf118000000006a47304402202c0e654149820ca5e95bfda24a9ef26cc3956560f0569c7157707d0c3f784c0402204337fcd8c6f86c23416bbecb2509ad897f8576e15c47b6456645d03bb4c6af7a012102bfa469ffb4404523987c018d7ba52d3929c75fc41e7a6f635640f506d715ed84ffffffff0210339041000000001976a9140b353d33f64efb36576cd4fcf0261b1aeb5ba96688ac0046c323000000001976a9143567de06aa57a188a8e9c47b3bc9778151f1a6e888ac00000000");
            org.bitcoinj.core.Transaction btx7 = new org.bitcoinj.core.Transaction(this.netParams, bytex7);
            Transaction tx7 = new Transaction("a8d1cd860df2c345fd83764413f7809fe42344a8715b03a597751f22b7a1b33d",btx7,false,true);
            //txList.add(tx7);

            byte[] bytex8 = adapter.unmarshal("01000000013db3a1b7221f7597a5035b71a84423e49f80f713447683fd45c3f20d86cdd1a8000000006a4730440220382af068eaae9d39861ca811a7d57cbe5291f3704adad248df683a7756446d7302200dc487a00944b36b8a77bb55297813da3f0982bfe2584cf24d0197286ecc8819012102b42731c4b6ec382d6889878072cd342067976eafa49e32465e44dabea3c1865affffffff020046c323000000001976a91480f6016d03ca2198d2bc74a6c887f0d1501ca8d588ac95e3cc1d000000001976a914bfb1f67bcaaa7b49979377e027ea1e693b4cac0088ac00000000");
            org.bitcoinj.core.Transaction btx8 = new org.bitcoinj.core.Transaction(this.netParams, bytex8);
            Transaction tx8 = new Transaction("a5b003f1edd4444d8de9fb86097c4790158c545c4ffcaad9a8d92fad3ff88161",btx8,false,true);
            //txList.add(tx8);

            byte[] bytex9 = adapter.unmarshal("0100000002dc9055a01974444eb293434d159f5ee321514f58b22336c8410b0789f4edc702000000006a473044022029653ab637be76a7673c561843d4686a3c9c2a6d11dbc2934a7264cbe7364d5a022025c34e05a78fc47efeef1f2f5e3390abf3133ed9910a16cbebf4f682f69acbb7012103eefd47852fe88d1f3391f2bbc1cf62d61327971e560819ea053c4c16b19680c3ffffffffcc38cfe25972f0be05da68581dba57c1f143b0069bdee2cf02a849ae47de861c000000006a47304402201d114868eeffa5f09bc6b1c6efe0c7c02516ad1186a54d330ce43564048a11ec02202bf19a775d1fa3b915fc085463a56bdbcfda48efece795eee35df0141d52b853012102da00dc5013cfe328b5c31a6e0c7c71bafbea4f8d7c10b2018808e0ca00e41e4cffffffff02fedcbe1d000000001976a914baf01e398ef7a41bb5a05733615db0ebbd1f0cdd88ac0046c323000000001976a91480e7a71a6b7c31d5be86b65b23a9e14b0658824d88ac00000000");
            org.bitcoinj.core.Transaction btx9 = new org.bitcoinj.core.Transaction(this.netParams, bytex9);
            Transaction tx9 = new Transaction("2ec2c1dec0359f25d4f25c222aa7a048686d8807934fd23bdff144827e9e73b8",btx9,false,true);
            //txList.add(tx9);

            byte[] bytex10 = adapter.unmarshal("010000000168caa4f1414da05b586b3ef8f199603d462cd80db3cbab9cb38c459259040e13000000006a4730440220151a6c5d948e67b63752cdf159c3b5f8f8317706edb1f8209463243f87e866570220340440275d0d2816d322af64fff8d71194655e241e1a54c8fdcb8770851131b70121036ef17d98ac6e3bd92a030c61bdd3becad4544b91e322edc3edf61d04d6138e88ffffffff028b825365000000001976a9144ce851eb52d6fbb76c2601c0c2f977b3bcb2178588ac0046c323000000001976a914e34284a919e5d649cf2e7e2d9f7516d7b408eda688ac00000000");
            org.bitcoinj.core.Transaction btx10 = new org.bitcoinj.core.Transaction(this.netParams, bytex10);
            Transaction tx10 = new Transaction("18f12c41fe5721ceb95b6501e76c07f339d785d44650d048edf0534d62068cd3",btx10,false,true);
            //txList.add(tx10);

            byte[] bytex11 = adapter.unmarshal("0100000001fe7b407130ea292e02e592b362b2e614c8415222f78669f5851090e78d359764000000006b483045022100d82bcfab85f3e6a10368ad01314e4866c8090f88b1cd8e7c98056e9611a9bc6802207d7eca055d0ffaf5c2922118f4e45d644faa685d1f7c5094403973758b325961012103a88556dd5c5cf2e1dadd3de21d9ce1a31f0cc2038f20e0e4eda17620171d9185ffffffff02f117ea05000000001976a914bfc3c9c13ef4f75468ee4871ed1fd38d20b2809088ac0065cd1d000000001976a91404324ba8ff598bc38498db83649985e5f33bd53088ac00000000");
            org.bitcoinj.core.Transaction btx11 = new org.bitcoinj.core.Transaction(this.netParams, bytex11);
            Transaction tx11 = new Transaction("78dbb096a1e1652888789737f3ed6efc383cbb7e9109b5b4aa392f77d54320ed",btx11,false,true);
            //txList.add(tx11);

            return txList;
        }

    }

    // TODO
    @Test
    public void testSufficientFunds() throws Exception {
        MockBitcoin mock = new MockBitcoin();

        /**
         * The first five are mining addresses
         */

        // NullPointerException
        //MockAddress addr = new MockAddress("mhsU4iQ2XoFvgZck69z6zdyMMs9PYwNzof");

        // NullPointerException
        //MockAddress addr = new MockAddress("mhFYAUik5P3BBc2KuKnhKs23DdoXCKXWUQ");

        // works? For some reason, the output address is P2PKH.
        //MockAddress addr = new MockAddress("muyDoehpBExCbRRXLtDUpw5DaTb33UZeyG");

        // NullPointerException
        //MockAddress addr = new MockAddress("mkubqAKU27qfvE5DFsr1H8ZyvoFAm666Ns");

        /**
         * Can only test non-mining addresses -- output.getAddressFromP2PKHScript only returns non-mining transactions
         * Returns null if the output script is not the form OP_DUP OP_HASH160 OP_EQUALVERIFY OP_CHECKSIG, i.e. P2PKH
         */

        /*
        // Non-mining address
        // The transaction we are using for this address contains 4.42782234 BTC or 442,782,234 Satoshis
        MockAddress addr = new MockAddress("mgfCoNNDAx32p1PuRL4puqsZ3Q3wcawYqw");

        // One satoshi below the transaction amount
        Assert.assertTrue(mock.sufficientFunds(addr, 442782233l));

        // Equal to the transaction amount
        Assert.assertTrue(mock.sufficientFunds(addr, 442782234l));

        // One satoshi above the transaction amount
        Assert.assertTrue(!mock.sufficientFunds(addr, 442782235l));
        */


        /*
        // Daniel's addresses
        MockAddress addr = new MockAddress("mkPLY3fiDgynpt5XfMLjd4LXtQT9sCM5Sr");
        Assert.assertTrue(mock.sufficientFunds(addr, 600000000l));

        MockAddress addr = new MockAddress("msGqVNhbS5GjafQhmN49gvwLN1hsfik47T");
        Assert.assertTrue(mock.sufficientFunds(addr, 600000000l));

        MockAddress addr = new MockAddress("msGYJ87B6bxjmsUW6uYrsfTHq7Am1qesot");
        Assert.assertTrue(mock.sufficientFunds(addr, 600000000l));

        MockAddress addr = new MockAddress("n2EbNN9e8nG6pATMURT998tu6AF74t88qh");
        Assert.assertTrue(mock.sufficientFunds(addr, 600000000l));

        MockAddress addr = new MockAddress("mfu9FR9rugkYZLDGMh12ACpmicdaFuYkf1");
        Assert.assertTrue(mock.sufficientFunds(addr, 500000001l));
        */

    }

    /**
     *
     */
    @Test
    public void testGetSignatureAndSign() throws AddressFormatException {
        MockBitcoin mock = new MockBitcoin();
        HexBinaryAdapter adapter = new HexBinaryAdapter();
        ECKey privKey1 = ECKey.fromPrivate(Hex.decode("bd28acf50b7304b098aefa12fd1bb1cbeb4975cc20e86055b3b9ea65b1c80972"));
        ECKey privKey2 = ECKey.fromPrivate(Hex.decode("224de2bacfba369f854e8db918540d40f45f7459c83f8b47a9501306bce29715"));
        byte[] parentBytes1 = adapter.unmarshal("01000000013607cf0cd0c29a6b5d6dd4d7e95c85be0577ee62bf3685c26ea6f36a55bba167000000006b483045022100c75ca43c81d9ecd0aaf1327b80512c1f386accbe2684ae8bcd302bc6e164979f02203efbc08bc5aaaaeb98cda97adf34903f67b803c364b0dde03c9284891d5fc2ee012102834755244b98488d24dd643c81403f0660abf9dff5804ce7d1a2076924e45862ffffffff01a001da60000000001976a914dea86c67b46e5d5bd89ab24d40590f871591ffae88ac00000000");
        byte[] parentBytes2 = adapter.unmarshal("01000000018fff9cf295ab3545b974b4dbf87c8cf268f93b4fd88b0acfb9504ca187e1e548000000006a4730440220709d86aa33fabee055f6df16596ac3f171465edd1551856d46ce31a9a9b7bfb202204e482f8d2161322fc7665ad9184f5f0910934cf92b6b91e4176ba17982ee6948012103a3b087c39703146c561c7e9ddd9637de3176e2dfba1773307d9c6e7a5373fd25ffffffff01f0c6f601000000001976a9149c294ec749de349f114e19a32b6c3c585aa0f83588ac00000000");
        Transaction parentTx1 = new Transaction(mock.netParams, parentBytes1);
        Transaction parentTx2 = new Transaction(mock.netParams, parentBytes2);
        Transaction tx = new Transaction(mock.netParams);
        tx.addOutput(Coin.SATOSHI.multiply(parentTx1.getOutput(0).getValue().value - 50000l), new org.bitcoinj.core.Address(mock.netParams, "mivwStMcpCfVqnDw5zmHYtEffCNgy7uqj6"));
        tx.addOutput(Coin.SATOSHI.multiply(parentTx2.getOutput(0).getValue().value - 50000l), new org.bitcoinj.core.Address(mock.netParams, "mthh7gRXtEwznD1tqbxaWggS8QjtfqkiSP"));
        tx.addInput(parentTx1.getOutput(0));
        tx.addInput(parentTx2.getOutput(0));
        Assert.assertNotNull(mock.getSignature(tx, privKey1));
        Assert.assertNotNull(mock.getSignature(tx, privKey2));
        Bytestring sig1 = mock.getSignature(tx, privKey1).sigs[0];
        Bytestring sig2 = mock.getSignature(tx, privKey2).sigs[1];
        List<Bytestring> signatures = new LinkedList<>();
        signatures.add(sig1);
        signatures.add(sig2);
        Assert.assertNotNull(mock.signTransaction(tx, signatures));
        Transaction signedTx = mock.signTransaction(tx, signatures);
        System.out.println(DatatypeConverter.printHexBinary(signedTx.bitcoinSerialize()));
        // this transaction can be seen here
        // https://live.blockcypher.com/btc-testnet/tx/c48d33e9dc2585c9d096dae9be6b4f78503904d3b59c9d615c0e015f9584e2d7/


        // this transaction has one input whose connected transaction output is already spent
        // trying to broadcast the associated raw hex will fail
        ECKey privKey3 = ECKey.fromPrivate(Hex.decode("06b44f756b5dbbbc86bd673ba8ec28b2e74772545f2390d199c84c0663848048"));
        ECKey privKey4 = ECKey.fromPrivate(Hex.decode("224de2bacfba369f854e8db918540d40f45f7459c83f8b47a9501306bce29715"));
        byte[] parentBytes3 = adapter.unmarshal("01000000018ae4722cc5174589e386bf67dda5d7572636812b302b0f9abe43dd602f043517010000006a473044022016c8b29e15135271ea7ecd76d4963f69d50bf932ad8ab9e9366aa5ddb0193816022062814e4dfff12124747554e117a80e164a7eef3882f38ec7b2c965d9c95af35d01210359374b5da4105fe505d46a65d3372b3bfbf26e299473b10ae34e65313db408d4feffffff0240164000000000001976a914191697ea01d67e6a981ead0ad719aa52d62989e088ace0c86d07000000001976a9148b0adad35d2f13a6894e248fd2f1fc18a977909c88ac84120e00");
        byte[] parentBytes4 = adapter.unmarshal("01000000018fff9cf295ab3545b974b4dbf87c8cf268f93b4fd88b0acfb9504ca187e1e548000000006a4730440220709d86aa33fabee055f6df16596ac3f171465edd1551856d46ce31a9a9b7bfb202204e482f8d2161322fc7665ad9184f5f0910934cf92b6b91e4176ba17982ee6948012103a3b087c39703146c561c7e9ddd9637de3176e2dfba1773307d9c6e7a5373fd25ffffffff01f0c6f601000000001976a9149c294ec749de349f114e19a32b6c3c585aa0f83588ac00000000");
        Transaction parentTx3 = new Transaction(mock.netParams, parentBytes3);
        Transaction parentTx4 = new Transaction(mock.netParams, parentBytes4);
        Transaction tx2 = new Transaction(mock.netParams);
        tx2.addOutput(Coin.SATOSHI.multiply(parentTx3.getOutput(0).getValue().value - 50000l), new org.bitcoinj.core.Address(mock.netParams, "mhnL4vqyHDXohpwFUL9YDkJvxhCZVK7tzE"));
        tx2.addOutput(Coin.SATOSHI.multiply(parentTx4.getOutput(0).getValue().value - 50000l), new org.bitcoinj.core.Address(mock.netParams, "mnx5s59NbfN3UfbfgUvTRpaU2MZ3vW7LNY"));
        tx2.addInput(parentTx3.getOutput(0));
        tx2.addInput(parentTx4.getOutput(0));
        Assert.assertNotNull(mock.getSignature(tx2, privKey3));
        Assert.assertNotNull(mock.getSignature(tx2, privKey4));
        Bytestring sig3 = mock.getSignature(tx2, privKey3).sigs[0];
        Bytestring sig4 = mock.getSignature(tx2, privKey4).sigs[0];
        List<Bytestring> signatures2 = new LinkedList<>();
        signatures2.add(sig3);
        signatures2.add(sig4);
        Assert.assertNotNull(mock.signTransaction(tx2, signatures2));
        Transaction signedTx2 = mock.signTransaction(tx2, signatures2);
        System.out.println(DatatypeConverter.printHexBinary(signedTx2.bitcoinSerialize()));
        /**
         * https://live.blockcypher.com/btc-testnet/pushtx/ gives the following error:
         *
         * Error validating transaction: Transaction 1c7646221a165358ad87cb172b050b4f1e7b0042156f5288995bdcdb6307cc38
         * referenced by input 1 of 1c01532c4335c47bc94cd6302b8c873f38bb58098a44d74172e24cff6d5e35f7
         * has already been spent..
         */
    }
}
