/**
 *
 * Copyright © 2016 Mycelium.
 * Use of this source code is governed by an ISC
 * license that can be found in the LICENSE file.
 *
 */

package com.shuffle.bitcoin.blockchain;

import com.neemre.btcdcli4j.core.BitcoindException;
import com.neemre.btcdcli4j.core.CommunicationException;
import com.shuffle.bitcoin.Address;
import com.shuffle.bitcoin.CoinNetworkException;
import com.shuffle.bitcoin.impl.AddressUtxoImpl;

import org.apache.commons.codec.binary.Base64;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.store.BlockStoreException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.xml.bind.DatatypeConverter;
import javax.xml.bind.annotation.adapters.HexBinaryAdapter;

/**
 * Created by Eugene Siegel on 4/22/16.
 */

/**
 *
 * This class allows lookup of transactions, UTXOs, and address balances via Btcd.
 *
 *
 * Instructions:
 *
 * In your Btcd.conf file, set an rpcuser and rpcpass (rpclimituser & rpclimitpass also works).
 * Make sure addrindex = 1 (this indexes Bitcoin addresses) and notls = 1 (currently TLS is not supported)
 *
 * Alternatively, if you do not wish to edit the config file for addrindex and notls,
 * you can use the two flags in the command line:
 * "./btcd --addrindex --notls"
 * 
 * Note: Currently, Btcd requires the "addrindex" flag to search for conflicting transactions in the mempool.
 *       However, it can be removed if one changes getConflictingTransactionsInner() to search through the
 *       mempool.  This option might come in a future update.
 *
 */
public class Btcd extends Bitcoin {

    private final String rpcuser;
    private final String rpcpass;
    private final URL url;

    public Btcd(NetworkParameters netParams, String rpcuser, String rpcpass)
            throws MalformedURLException {

        // minPeers = 0 because we connect to peers via Btcd
        super(netParams, 0);
        this.rpcuser = rpcuser;
        this.rpcpass = rpcpass;

        if (netParams.equals(NetworkParameters.fromID(NetworkParameters.ID_MAINNET))) {
            url = new URL("http://127.0.0.1:8334");
        } else if (netParams.equals(NetworkParameters.fromID(NetworkParameters.ID_TESTNET))) {
            url = new URL("http://127.0.0.1:18334");
        } else {
            throw new IllegalArgumentException("Invalid network parameters passed to btcd. ");
        }
    }

    /**
     * This method takes in a transaction hash and returns a bitcoinj transaction object.
     */
    synchronized org.bitcoinj.core.Transaction getTransaction(String transactionHash) throws IOException {

        org.bitcoinj.core.Transaction tx = null;
        String requestBody = "{\"jsonrpc\":\"2.0\",\"id\":\"null\",\"method\":\"getrawtransaction\", \"params\":[\"" + transactionHash + "\"]}";

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        Base64 b = new Base64();
        String authString = rpcuser + ":" + rpcpass;
        String encoding = b.encodeAsString(authString.getBytes());
        connection.setRequestProperty("Authorization", "Basic " + encoding);
        connection.setRequestProperty("Content-Length", Integer.toString(requestBody.getBytes().length));
        connection.setDoInput(true);
        OutputStream out = connection.getOutputStream();
        out.write(requestBody.getBytes());

        if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
            InputStream is = connection.getInputStream();
            BufferedReader rd = new BufferedReader(new InputStreamReader(is));
            String line;
            StringBuffer response = new StringBuffer();
            while ((line = rd.readLine()) != null) {
                response.append(line);
                response.append('\r');
            }
            rd.close();

            JSONObject json = new JSONObject(response.toString());
            String hexTx = (String) json.get("result");
            HexBinaryAdapter adapter = new HexBinaryAdapter();
            byte[] bytearray = adapter.unmarshal(hexTx);
            Context context = Context.getOrCreate(netParams);
            tx = new org.bitcoinj.core.Transaction(netParams, bytearray);

        } else {
            throw new IOException("Could not query Btcd.");
        }

        out.flush();
        out.close();

        return tx;

    }

    /**
     * This method will take in a set of UTXOs (AddressUtxoImpl) and return a List of all associated transactions.
     * We don't use Btcd's "searchrawtransactions" RPC call for two reasons:
     * 
     * 1. We don't enable the "addrindex" flag.
     * 
     * 2. We are not looking for the transactions pertaining to a particular address, but
     *    rather the transactions related to a set of UTXOs.
     */
    @Override
    public synchronized List<Transaction> getTransactionsFromUtxosInner(AddressUtxoImpl a) throws IOException {

        List<Transaction> txList = new ArrayList<>();
        HashSet<Transaction> checkDuplicateTx = new HashSet<>();
        for (TransactionOutPoint tO : a.getUtxos()) {
            try {
                String txid = tO.getHash().toString();
                org.bitcoinj.core.Transaction tx = getTransaction(txid);
                boolean confirmed = !inMempool(txid);
                Transaction bTx = new Transaction(txid, tx, false, confirmed);
                if (!checkDuplicateTx.contains(bTx)) {
                    txList.add(bTx);
                }
                checkDuplicateTx.add(bTx);
            } catch (IOException e) {
                throw new IOException();
            }
        }

        return txList;

    }

    /**
     * This method will take in an address hash and return a List of all transactions associated with 
     * this address.  These transactions are in our Bitcoin.Transaction object format.
     */
    public synchronized List<Transaction> getAddressTransactions(String address) throws IOException {

        List<Transaction> txList = null;
        String requestBody = "{\"jsonrpc\":\"2.0\",\"id\":\"null\",\"method\":\"searchrawtransactions\", \"params\":[\"" + address + "\"]}";

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        Base64 b = new Base64();
        String authString = rpcuser + ":" + rpcpass;
        String encoding = b.encodeAsString(authString.getBytes());
        connection.setRequestProperty("Authorization", "Basic " + encoding);
        connection.setRequestProperty("Content-Length", Integer.toString(requestBody.getBytes().length));
        connection.setDoInput(true);
        OutputStream out = connection.getOutputStream();
        out.write(requestBody.getBytes());

        if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
            InputStream is = connection.getInputStream();
            BufferedReader rd = new BufferedReader(new InputStreamReader(is));
            String line;
            StringBuffer response = new StringBuffer();
            while ((line = rd.readLine()) != null) {
                response.append(line);
                response.append('\r');
            }
            rd.close();

            JSONObject json = new JSONObject(response.toString());
            JSONArray jsonarray = null;
            txList = new LinkedList<>();
            if (json.isNull("result")) {
                return txList;
            } else {
                jsonarray = json.getJSONArray("result");
            }
            for (int i = 0; i < jsonarray.length(); i++) {
                JSONObject currentJson = jsonarray.getJSONObject(i);
                String txid = currentJson.get("txid").toString();
                HexBinaryAdapter adapter = new HexBinaryAdapter();
                byte[] bytearray = adapter.unmarshal(currentJson.get("hex").toString());
                Context context = Context.getOrCreate(netParams);
                int confirmations;
                try {
                    confirmations = Integer.parseInt(currentJson.get("confirmations").toString());
                } catch (JSONException e) {
                    throw new RuntimeException("The transaction " + txid + " does not seem to have any confirmations", e);
                }
                boolean confirmed = confirmations != 0;
                org.bitcoinj.core.Transaction bitTx = new org.bitcoinj.core.Transaction(netParams, bytearray);
                Transaction tx = new Transaction(txid, bitTx, false, confirmed);
                txList.add(tx);
            }

        }

        out.flush();
        out.close();

        return txList;

    }

    @Override
    protected boolean send(Bitcoin.Transaction t) throws ExecutionException, InterruptedException, CoinNetworkException {
        if (!t.canSend || t.sent) {
            return false;
        }

        String hexTx = null;
        try {
            hexTx = DatatypeConverter.printHexBinary(t.bitcoinj().bitcoinSerialize());
        } catch (BlockStoreException e) {
            return false;
        } catch (IOException er) {
            return false;
        }
        String requestBody = "{\"jsonrpc\":\"2.0\",\"id\":\"null\",\"method\":\"sendrawtransaction\", \"params\":[\"" + hexTx + "\"]}";

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) url.openConnection();
        } catch (IOException e) {
            return false;
        }
        connection.setDoOutput(true);
        try {
            connection.setRequestMethod("POST");
        } catch (java.net.ProtocolException e) {
            return false;
        }
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        Base64 b = new Base64();
        String authString = rpcuser + ":" + rpcpass;
        String encoding = b.encodeAsString(authString.getBytes());
        connection.setRequestProperty("Authorization", "Basic " + encoding);
        connection.setRequestProperty("Content-Length", Integer.toString(requestBody.getBytes().length));
        connection.setDoInput(true);
        OutputStream out;
        try {
            out = connection.getOutputStream();
        } catch (IOException e) {
            return false;
        }
        try {
            out.write(requestBody.getBytes());
        } catch (IOException e) {
            return false;
        }

        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) return false;
        } catch (IOException e) {
            return false;
        }

        InputStream is;
        try {
            is = connection.getInputStream();
        } catch (IOException e) {
            return false;
        }
        BufferedReader rd = new BufferedReader(new InputStreamReader(is));
        String line;
        StringBuffer response = new StringBuffer();
        while (true) {
            try {
                if ((line = rd.readLine()) == null) break;
            } catch (IOException e) {
                return false;
            }
            response.append(line);
            response.append('\r');
        }

        try {
            rd.close();
        } catch (IOException e) {
            return false;
        }

        JSONObject json = new JSONObject(response.toString());
        if (json.isNull("result")) {
            JSONObject errorObj = json.getJSONObject("error");
            String errorMsg = errorObj.getString("message");
            String parsed = errorMsg.substring(0,37);
            // transaction is already in mempool, return true
            if (parsed.equals("TX rejected: already have transaction")) {
                return true;
            }
            throw new CoinNetworkException(errorMsg);
        }

        return true;
    }

    synchronized boolean isUtxo(String hexTx, int vout) throws IOException {
        String requestBody = "{\"jsonrpc\":\"2.0\",\"id\":\"null\",\"method\":\"gettxout\", \"params\":[\"" + hexTx + "\"";
        requestBody = requestBody.concat("," + Integer.toString(vout) + ",false]}");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoInput(true);
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        Base64 b = new Base64();
        String authString = rpcuser + ":" + rpcpass;
        String encoding = b.encodeAsString(authString.getBytes());
        connection.setRequestProperty("Authorization", "Basic " + encoding);
        connection.setRequestProperty("Content-Length", Integer.toString(requestBody.getBytes().length));
        OutputStream out = connection.getOutputStream();
        out.write(requestBody.getBytes());
        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) throw new IOException();
        InputStream is = connection.getInputStream();
        BufferedReader rd = new BufferedReader(new InputStreamReader(is));
        String line;
        StringBuffer response = new StringBuffer();
        while (true) {
            try {
                if ((line = rd.readLine()) == null) break;
            } catch (IOException e) {
                throw new IOException();
            }
            response.append(line);
            response.append('\r');
        }
        rd.close();

        JSONObject json = new JSONObject(response.toString());

        return !(json.isNull("result"));
    }

    private synchronized boolean inMempool(String transactionHash) throws IOException {
        String requestBody = "{\"jsonrpc\":\"2.0\",\"id\":\"null\",\"method\":\"getrawmempool\", \"params\":[false]}";
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoInput(true);
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        Base64 b = new Base64();
        String authString = rpcuser + ":" + rpcpass;
        String encoding = b.encodeAsString(authString.getBytes());
        connection.setRequestProperty("Authorization", "Basic " + encoding);
        connection.setRequestProperty("Content-Length", Integer.toString(requestBody.getBytes().length));
        OutputStream out = connection.getOutputStream();
        out.write(requestBody.getBytes());
        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) throw new IOException();
        InputStream is = connection.getInputStream();
        BufferedReader rd = new BufferedReader(new InputStreamReader(is));
        String line;
        StringBuilder response = new StringBuilder();
        while (true) {
            try {
                if ((line = rd.readLine()) == null) break;
            } catch (IOException e) {
                throw new IOException();
            }
            response.append(line);
            response.append('\r');
        }
        rd.close();
        JSONObject json = new JSONObject(response.toString());
        // TODO
        // What about an empty Btcd mempool ?
        if (json.isNull("result")) {
            JSONObject errorObj = json.getJSONObject("error");
            String errorMsg = errorObj.getString("message");
            if (errorMsg.isEmpty()) {
                throw new IOException();
            }
        }

        JSONArray jsonArray = json.getJSONArray("result");

        return jsonArray.toString().contains("\"" + transactionHash + "\"");
    }

    @Override
    synchronized com.shuffle.bitcoin.Transaction getConflictingTransactionInner(com.shuffle.bitcoin.Transaction t, Address a, long amount)
            throws CoinNetworkException, AddressFormatException, BlockStoreException, BitcoindException, CommunicationException, IOException {

        if (!(t instanceof Transaction)) throw new IllegalArgumentException();
        Transaction transaction = (Transaction) t;

        AddressUtxoImpl addrUtxo = (AddressUtxoImpl) a;

        for (TransactionOutPoint to : addrUtxo.getUtxos()) {

            org.bitcoinj.core.Transaction tx = getTransaction(to.getHash().toString());
            String addressP2pkh = tx.getOutput(to.getIndex()).getAddressFromP2PKHScript(netParams).toString();
            List<Transaction> addressTransactions = getAddressTransactions(addressP2pkh);

            for (Transaction addressTx : addressTransactions) {
                for (TransactionInput addressTxInput : addressTx.bitcoinj().getInputs()) {
                    for (TransactionInput transactionInput : transaction.bitcoinj().getInputs()) {
                        if (addressTxInput.equals(transactionInput)) return addressTx;
                    }
                }
            }
        }

        return null;
    }

    @Override
    protected synchronized List<Transaction> getTransactionsFromUtxos(AddressUtxoImpl a) throws IOException, CoinNetworkException, AddressFormatException {
        return getTransactionsFromUtxosInner(a);
    }

}
