package com.frostether.node;

import com.frostether.frostchain.Consensus;
import com.frostether.frostchain.Log;
import com.frostether.frostchain.Node;
import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Headless Frostchain node for a server: no wallet, no miner.
 *
 * Phones add this server's address under Graysons Wallet → Node → Add, then sync blocks and send
 * transactions through it. Mining still happens on phones locked on the tone; this node stores and
 * relays the chain, and stays up when the phones sleep. It never holds anyone's 25 words.
 *
 *   frostnode [--data DIR] [--port 7830] [--peer HOST[:PORT]]...
 */
public final class FrostNode {

    private FrostNode() {
    }

    public static void main(String[] args) throws Exception {
        String dir = "data";
        int port = Consensus.P2P_PORT;
        List<String> peers = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            boolean needsValue = a.equals("--data") || a.equals("--port") || a.equals("--peer");
            if (needsValue && i + 1 >= args.length) {
                System.err.println(a + " needs a value");
                usage();
                System.exit(2);
            }
            switch (a) {
                case "--data":
                    dir = args[++i];
                    break;
                case "--port":
                    port = Integer.parseInt(args[++i]);
                    break;
                case "--peer":
                    peers.add(args[++i]);
                    break;
                case "-h":
                case "--help":
                    usage();
                    return;
                default:
                    System.err.println("unknown option: " + a);
                    usage();
                    System.exit(2);
            }
        }

        Log.setSink((level, tag, msg) -> System.out.println(Instant.now() + " " + level + " " + tag + ": " + msg));

        File data = new File(dir).getAbsoluteFile();
        Node node = new Node(data, null);
        // A server has no Wi-Fi neighbours to find, so skip the LAN beacons.
        node.putSetting("discovery", Boolean.FALSE);
        node.start(port, true);
        if (node.port() != port) {
            System.err.println("port " + port + " is busy. Is another frostnode already running?");
            node.stop();
            System.exit(1);
        }
        for (String p : peers) {
            System.out.println("peer added: " + node.addPeer(p));
        }
        System.out.println("frostnode listening on 0.0.0.0:" + node.port() + ", chain " + node.chain.tip().hashHex().substring(0, 16)
                + ", height " + node.chain.height() + ", data in " + data);

        Runtime.getRuntime().addShutdownHook(new Thread(node::stop, "frostnode-stop"));
        while (true) {
            Thread.sleep(5 * 60 * 1000L);
            System.out.println(Instant.now() + " status: height " + node.chain.height() + ", " + node.syncStatus()
                    + ", mempool " + node.mempool.size());
        }
    }

    private static void usage() {
        System.out.println("usage: frostnode [--data DIR] [--port 7830] [--peer HOST[:PORT]]...");
    }
}
