package edu.stevens.cs549.ftpserver;

import edu.stevens.cs549.common.IOUtils;
import edu.stevens.cs549.ftpinterface.IServer;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Enumeration;
import java.util.Stack;
import java.util.logging.Logger;

/**
 *
 *
 * @author dduggan
 */
public class Server extends UnicastRemoteObject
        implements IServer {
	
	static final long serialVersionUID = 0L;
	
	public static Logger log = Logger.getLogger("edu.stevens.cs.cs549.ftpserver");
    
	/*
	 * For multi-homed hosts, must specify IP address on which to 
	 * bind a server socket for file transfers.  See the constructor
	 * for ServerSocket that allows an explicit IP address as one
	 * of its arguments.
	 */
	private InetAddress host;
	
	final static int backlog = 5;
	
	/*
	 *********************************************************************************************
	 * Current working directory.
	 */
    static final int MAX_PATH_LEN = 1024;
    private Stack<String> cwd = new Stack<String>();
    
    /*
     *********************************************************************************************
     * Data connection.
     */
    
    enum Mode { NONE, PASSIVE, ACTIVE };
    
    private Mode mode = Mode.NONE;
    
    /*
     * If passive mode, remember the server socket.
     */
    
    private ServerSocket dataChan = null;
    
    private InetSocketAddress makePassive () throws IOException {
    	dataChan = new ServerSocket(0, backlog, host);
    	mode = Mode.PASSIVE;
    	return (InetSocketAddress)(dataChan.getLocalSocketAddress());
    }
    
    /*
     * If active mode, remember the client socket address.
     */
    private InetSocketAddress clientSocket = null;
    
    private void makeActive (InetSocketAddress s) {
    	clientSocket = s;
    	mode = Mode.ACTIVE;
    }
    
    /*
     **********************************************************************************************
     */
            
    /*
     * The server can be initialized to only provide subdirectories
     * of a directory specified at start-up.
     */
    private final String pathPrefix;

    public Server(InetAddress host, int port, String prefix) throws RemoteException {
    	super(port);
    	this.host = host;
    	this.pathPrefix = prefix + "/";
        log.info("A client has bound to a server instance.");
    }
    
    public Server(InetAddress host, int port) throws RemoteException {
        this(host, port, "/");
    }
    
    private boolean valid (String s) {
        // File names should not contain "/".
        return (s.indexOf('/')<0);
    }

    static void msg(String m) {
        System.out.print(m);
    }

    static void msgln(String m) {
        System.out.println(m);
    }

    static void err(Exception e) {
        System.err.println("Error : "+e);
        e.printStackTrace();
    }
    
    private static class GetThread implements Runnable {
    	private ServerSocket dataChan = null;
    	private FileInputStream file = null;
    	public GetThread (ServerSocket s, FileInputStream f) { dataChan = s; file = f; }


        public void run () {
    		try {
            /*
    		 * TODO: Process a client request to transfer a file.
    		 */

                Socket xfer = dataChan.accept(); // listen
                OutputStream out = xfer.getOutputStream();
                IOUtils.copyStream(file, out);

            } catch (IOException e) {
                msg("Exception: " + e);
                e.printStackTrace();
            }

        }

    }

    private static class  PutThread implements Runnable{
        private ServerSocket dataChan = null;
        private FileOutputStream file = null;
        public PutThread (ServerSocket s, FileOutputStream f) { dataChan = s; file = f;}

        public void run () {
            try {
                Socket xfer = dataChan.accept();
                InputStream in = xfer.getInputStream();
                IOUtils.copyStream(in, file);
            } catch (IOException e) {
                msg("Exception: " + e);
                e.printStackTrace();
            }
        }

    }
    
    public void get (String file) throws IOException, FileNotFoundException, RemoteException {
        if (!valid(file)) {
            throw new IOException("Bad file name: " + file);
        } else if (mode == Mode.ACTIVE) {
        	Socket xfer = new Socket (clientSocket.getAddress(), clientSocket.getPort());
        	/*
        	 * TODO: connect to client socket to transfer file.
        	 */
        	FileInputStream f = new FileInputStream(path()+file);
        	OutputStream out = xfer.getOutputStream();
            IOUtils.copyStream(f, out);


        } else if (mode == Mode.PASSIVE) {
            FileInputStream f = new FileInputStream(path() + file);
            new Thread (new GetThread(dataChan, f)).start(); // get file
        }
    }
    
    public void put (String file) throws IOException, FileNotFoundException, RemoteException {
    	/*
    	 * TODO: Finish put (both ACTIVE and PASSIVE).
    	 */
        if (!valid(file)) {
            throw new IOException("Bad file name: " + file);
        } else if ( mode == Mode.ACTIVE){
            Socket xfer = new Socket(clientSocket.getAddress(), clientSocket.getPort());
            FileOutputStream f = new FileOutputStream(path() + file);
            InputStream in = xfer.getInputStream();
            IOUtils.copyStream(in, f);
        } else if( mode == Mode.PASSIVE){
            FileOutputStream f = new FileOutputStream(path() + file);
            new Thread(new PutThread(dataChan, f)).start();
        }
    }
    
    public String[] dir () throws RemoteException {
        // List the contents of the current directory.
        return new File(path()).list();
    }

	public void cd(String dir) throws IOException, RemoteException {
		// Change current working directory (".." is parent directory)
		if (!valid(dir)) {
			throw new IOException("Bad file name: " + dir);
		} else {
			if ("..".equals(dir)) {
				if (cwd.size() > 0)
					cwd.pop();
				else
					throw new IOException("Already in root directory!");
			} else if (".".equals(dir)) {
				;
			} else {
				File f = new File(path());
				if (!f.exists())
					throw new IOException("Directory does not exist: " + dir);
				else if (!f.isDirectory())
					throw new IOException("Not a directory: " + dir);
				else
					cwd.push(dir);
			}
		}
	}

    public String pwd () throws RemoteException {
        // List the current working directory.
        String p = "/";
        for (Enumeration<String> e = cwd.elements(); e.hasMoreElements(); ) {
            p = p + e.nextElement() + "/";
        }
        return p;
    }
    
    private String path () throws RemoteException {
    	return pathPrefix+pwd();
    }
    
    public void port (InetSocketAddress s) {
    	makeActive(s);
    }
    
    public InetSocketAddress pasv () throws IOException {
    	return makePassive();
    }

}
