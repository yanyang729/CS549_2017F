package edu.stevens.cs549.dhts.state;

import edu.stevens.cs549.dhts.activity.DHT;
import edu.stevens.cs549.dhts.activity.DHTBase;
import edu.stevens.cs549.dhts.activity.IDHTNode;
import edu.stevens.cs549.dhts.activity.NodeInfo;
import edu.stevens.cs549.dhts.resource.TableRep;
import org.glassfish.jersey.media.sse.EventOutput;
import org.glassfish.jersey.media.sse.EventSource;
import org.glassfish.jersey.media.sse.OutboundEvent;
import org.glassfish.jersey.media.sse.SseBroadcaster;

import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

/**
 *
 * @author dduggan
 */
public class State implements IState, IRouting {

    static final long serialVersionUID = 0L;

    public static Logger log = Logger.getLogger(State.class.getCanonicalName());

    protected NodeInfo info;

    public State(NodeInfo info) {
        super();
        this.info = info;
        this.predecessor = null;
        this.successor = info;

        this.finger = new NodeInfo[NKEYS];
        for (int i = 0; i < NKEYS; i++) {
            finger[i] = info;
        }

    }

    /*
     * Get the info for this DHT node.
     */
    public NodeInfo getNodeInfo() {
        return info;
    }

    /*
     * Local table operations.
     */
    private Persist.Table dict = Persist.newTable();

    @SuppressWarnings("unused")
    private Persist.Table backup = Persist.newTable();

    @SuppressWarnings("unused")
    private NodeInfo backupSucc = null;

    public synchronized String[] get(String k) {
        List<String> vl = dict.get(k);
        if (vl == null) {
            return null;
        } else {
            String[] va = new String[vl.size()];
            return vl.toArray(va);
        }
    }

    public synchronized void add(String k, String v) {
        List<String> vl = dict.get(k);
        if (vl == null) {
            vl = new ArrayList<String>();
            dict.put(k, vl);
        }
        vl.add(v);
        // TODO: broadcast an event to any listeners
        broadcastAddition(k, v);
    }

    public synchronized void delete(String k, String v) {
        List<String> vs = dict.get(k);
        if (vs != null)
            vs.remove(v);
    }

    public synchronized void clear() {
        dict.clear();
    }

	/*
	 * Operations for transferring state between predecessor and successor.
	 */

    /*
     * Successor: Extract the bindings from the successor node.
     */
    public synchronized TableRep extractBindings(int predId) {
        return Persist.extractBindings(predId, info, successor, dict);
    }

    public synchronized TableRep extractBindings() {
        return Persist.extractBindings(info, successor, dict);
    }

    /*
     * Successor: Drop the bindings that are transferred to the predecessor.
     */
    public synchronized void dropBindings(int predId) {
        Persist.dropBindings(dict, predId, getNodeInfo().id);
    }

    /*
     * Predecessor: Install the transferred bindings.
     */
    public synchronized void installBindings(TableRep db) {
        dict = Persist.installBindings(dict, db);
    }

    /*
     * Predecessor: Back up bindings from the successor.
     */
    public synchronized void backupBindings(TableRep db) {
        backup = Persist.backupBindings(db);
        // backupSucc = db.getSucc();
    }

    public synchronized void backupSucc(TableRep db) {
        backupSucc = db.getSucc();
    }

    /*
     * A never-used operation for storing state in a file.
     */
    public synchronized void backup(String filename) throws IOException {
        Persist.save(info, successor, dict, filename);
    }

    public synchronized void reload(String filename) throws IOException {
        dict = Persist.load(filename);
    }

    public synchronized void display() {
        PrintWriter wr = new PrintWriter(System.out);
        Persist.display(dict, wr);
    }

	/*
	 * Routing operations.
	 */

    private NodeInfo predecessor = null;
    private NodeInfo successor = null;

    private NodeInfo[] finger;

    public synchronized void setPred(NodeInfo pred) {
        predecessor = pred;
    }

    public NodeInfo getPred() {
        return predecessor;
    }

    public synchronized void setSucc(NodeInfo succ) {
        successor = succ;
    }

    public NodeInfo getSucc() {
        return successor;
    }

    public synchronized void setFinger(int i, NodeInfo info) {
		/*
		 * TODO: Set the ith finger.
		 */
        finger[i] = info;
    }

    public synchronized NodeInfo getFinger(int i) {
		/*
		 * TODO: Get the ith finger.
		 */
        return finger[i];
    }

    public synchronized NodeInfo closestPrecedingFinger(int id) {
		/*
		 * TODO: Get closest preceding finger for id, to continue search at that
		 * node. Hint: See DHTBase.inInterval()
		 */
        NodeInfo prev = info, curr;
        for (int i = 0; i < IRouting.NFINGERS; i++) {
            curr = finger[i];
            if (DHT.inInterval(id, prev.id, curr.id)) {
                return prev;
            }
            prev = curr;
        }
        return finger[IRouting.NFINGERS - 1];
    }

    public synchronized void routes() {
        PrintWriter wr = new PrintWriter(System.out);
        wr.println("Predecessor: " + predecessor);
        wr.println("Successor  : " + successor);
        wr.println("Fingers:");
        wr.printf("%7s  %3s  %s\n", "Formula", "Key", "Succ");
        wr.printf("%7s  %3s  %s\n", "-------", "---", "----");
        for (int i = 0, exp = 1; i < IRouting.NFINGERS; i++, exp = 2 * exp) {
            wr.printf(" %2d+2^%1d  %3d  [id=%2d,uri=%s]%n", info.id, i, (info.id + exp) % IRouting.NKEYS, finger[i].id,
                    finger[i].addr);
        }
        wr.flush();
    }


	/*
	 * Used to prevent a race condition in the join protocol.
	 */

    public static enum JoinState {
        NOT_JOINED,
        JOINING,
        JOINED
    }

    private JoinState joinState = JoinState.NOT_JOINED;

    private Lock joinStateLock = new ReentrantLock();

    private Condition joined = joinStateLock.newCondition();

    public void startJoin() {
        joinStateLock.lock();
        try {
            joinState = JoinState.JOINING;
        } finally {
            joinStateLock.unlock();
        }
    }

    public void joinCheck() {
        // Called by any operations that should block during join protocol.
        // Currently that is getPred() (for the case where we are joining a
        // single-node network).
        joinStateLock.lock();
        try {
            while (joinState == JoinState.JOINING) {
                joined.await();
            }
        } catch (InterruptedException e) {
            log.info("Join check loop was interrupted.");
        } finally {
            joinStateLock.unlock();
        }
    }

    public void finishJoin() {
        joinStateLock.lock();
        try {
            joinState = JoinState.JOINED;
            joined.signalAll();
        } finally {
            joinStateLock.unlock();
        }
    }

	/*
	 * Server-side listeners for new bindings.
	 */

    private Map<String,SseBroadcaster> listeners = new HashMap<String,SseBroadcaster>();

    private Map<Integer,Map<String,EventOutput>> outputs = new HashMap<Integer,Map<String,EventOutput>>();

    public void addListener(int id, String key, EventOutput os) {
        Map<String, EventOutput> map = outputs.get(id);
        if (map == null) {
            map = new HashMap<>();
            outputs.put(id, map);
        }
        map.put(key, os);

        SseBroadcaster sseBroadcaster = listeners.get(key);
        if (sseBroadcaster == null) {
            sseBroadcaster = new SseBroadcaster();
            listeners.put(key, sseBroadcaster);
        }
        sseBroadcaster.add(os);
    }

    public void removeListener(int id, String key) {
        // TODO Close the event output stream.
        SseBroadcaster sseBroadcaster = listeners.get(key);
        if (sseBroadcaster != null) {
            Map<String, EventOutput> map = outputs.get(id);
            EventOutput os;
            if (map != null && (os = map.remove(key)) != null) {
                sseBroadcaster.remove(os);
                try {
                    os.close();
                } catch (IOException e) {
                }
            }
        }
    }

    private void broadcastAddition(String key, String value) {
        // TODO broadcast an added binding (use IDHTNode.NEW_BINDING_EVENT for event name).
        SseBroadcaster sseBroadcaster = listeners.get(key);
        if (sseBroadcaster != null) {
            OutboundEvent.Builder eventBuilder = new OutboundEvent.Builder().mediaType(MediaType.TEXT_PLAIN_TYPE);
            sseBroadcaster.broadcast(eventBuilder.name(IDHTNode.NEW_BINDING_EVENT).data(value).build());
        }
    }

    /**
     * when bindings are transferred to a new node, server node should
     * notify client node and shut down the event output stream
     */
    public void notifyListener() {
        for (String key : listeners.keySet()) {
            int id = DHTBase.NodeKey(key);
            if (!DHT.inInterval(id, predecessor.id, info.id)) {
                SseBroadcaster sseBroadcaster = listeners.remove(key);
                if (sseBroadcaster != null) {
                    OutboundEvent.Builder eventBuilder = new OutboundEvent.Builder().mediaType(MediaType.TEXT_PLAIN_TYPE);
                    sseBroadcaster.broadcast(eventBuilder.name(IDHTNode.TRANSFER_BINDINGD_EVENT).data(predecessor.addr.toString()).build());
                }
                // not remove from outputs... any problem ???
            }
        }
    }

	/*
	 * Client-side callbacks for new binding notifications.
	 */

    private Map<String,EventSource> callbacks = new HashMap<String,EventSource>();

    public void addCallback(String key, EventSource is) {
        removeCallback(key);
        callbacks.put(key, is);
    }

    public void removeCallback(String key) {
        // TODO remove an existing callback (if any) for bindings on key.
        // Be sure to close the event stream from the broadcaster.
        EventSource eventSource = callbacks.remove(key);
        if (eventSource != null) {
            eventSource.close();
        }
    }

    public void listCallbacks() {
        PrintWriter wr = new PrintWriter(System.out);
        if (callbacks.isEmpty()) {
            wr.println("No listeners defined.");
        } else {
            wr.println("Listeners defined for:");
            for (Entry<String, EventSource> entry : callbacks.entrySet()) {
                if (entry.getValue().isOpen()) {
                    wr.println("  " + entry.getKey());
                } else {
                    wr.println("  " + entry.getKey() + " (closed)");
                    callbacks.remove(entry.getKey());
                }
            }
        }
        wr.flush();
    }

}