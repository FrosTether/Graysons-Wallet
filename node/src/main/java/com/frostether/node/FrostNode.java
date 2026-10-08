package com.frostether.node;

import com.frostether.frostchain.Bytes;
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
 * Phones add this server's address under Graysons Vault → Node → Add, then sync blocks and send
 * transactions through it. Mining still happens on phones locked on the tone; this node stores and
 * relays the chain, and stays up when the phones sleep. It never holds anyone's 25 words.
 *
 *   frostnode [--data DIR] [--port 7830] [--explorer-port 7831] [--peer HOST[:PORT]]...
 *
 * It also serves FrostExplorer, a read-only block explorer, on the explorer port (0 turns it off).
 */
public final class FrostNode {

    private FrostNode() {
    }

    public static void main(String[] args) throws Exception {
        String dir = "data";
        int port = Consensus.P2P_PORT;
        int explorerPort = Consensus.P2P_PORT + 1;
        List<String> peers = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            boolean needsValue = a.equals("--data") || a.equals("--port") || a.equals("--peer") || a.equals("--explorer-port");
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
                case "--explorer-port":
                    explorerPort = Integer.parseInt(args[++i]);
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
        System.out.println("frostnode listening on 0.0.0.0:" + node.port() + ", chain " + Bytes.hex(node.chain.chainId).substring(0, 16)
                + ", height " + node.chain.height() + ", data in " + data);

        Explorer explorer = null;
        if (explorerPort > 0) {
            try {
                explorer = new Explorer(node);
                explorer.start(explorerPort);
                System.out.println("FrostExplorer on http://0.0.0.0:" + explorer.port() + "/");
            } catch (java.io.IOException e) {
                System.err.println("FrostExplorer didn't start on port " + explorerPort + ": " + e.getMessage());
                explorer = null;
            }
        }
        final Explorer runningExplorer = explorer;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (runningExplorer != null) {
                runningExplorer.stop();
            }
            node.stop();
        }, "frostnode-stop"));
        while (true) {
            Thread.sleep(5 * 60 * 1000L);
            System.out.println(Instant.now() + " status: height " + node.chain.height() + ", " + node.syncStatus()
                    + ", mempool " + node.mempool.size());
        }
    }

    private static void usage() {
        System.out.println("usage: frostnode [--data DIR] [--port 7830] [--explorer-port 7831] [--peer HOST[:PORT]]...");
    }
}
