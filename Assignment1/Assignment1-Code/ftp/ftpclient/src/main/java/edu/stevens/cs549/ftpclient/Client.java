/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package edu.stevens.cs549.ftpclient;

import edu.stevens.cs549.common.IOUtils;
import edu.stevens.cs549.ftpinterface.IServer;
import edu.stevens.cs549.ftpinterface.IServerFactory;
import org.apache.log4j.PropertyConfigurator;

import java.io.*;
import java.net.*;
import java.rmi.Naming;
import java.util.Properties;
import java.util.logging.Logger;


/**
 * 
 * @author dduggan
 */
public class Client {

	private static String clientPropsFile = "/client.properties";
	private static String loggerPropsFile = "/log4j.properties";

	public static Logger log = Logger.getLogger("edu.stevens.cs.cs549.ftpclient");

	/**
	 * @param args
	 *            the command line arguments
	 */
	public static void main(String[] args) {
		if (System.getSecurityManager() == null) {
			System.setSecurityManager(new SecurityManager());
		}
		new Client();
	}
	
	InetAddress serverAddress;

	public Client() {
		try {
			PropertyConfigurator.configure(getClass().getResource(loggerPropsFile));
			/*
			 * Load server properties.
			 */
			Properties props = new Properties();
			InputStream in = getClass().getResourceAsStream(clientPropsFile);
			props.load(in);
			in.close();
			String serverMachine = (String) props.get("server.machine");  // localhost/ aws url
			String serverName = (String) props.get("server.name");  // ftp
			int serverPort = Integer.parseInt((String) props.get("server.port")); // port
			

			IServerFactory factory = (IServerFactory)Naming.lookup("//" + serverMachine + ":" + serverPort + "/" + serverName);
			IServer server = factory.createServer();
			
			/*
			 * Start CLI.  Second argument should be server proxy.
			 */
			cli(serverMachine, server);

		} catch (java.io.FileNotFoundException e) {
			log.severe("Client error: " + clientPropsFile + " file not found.");
		} catch (java.io.IOException e) {
			log.severe("Client error: IO exception.");
			e.printStackTrace();
		} catch (Exception e) {
			log.severe("Client exception:");
			e.printStackTrace();
		}

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

	/*
	 * cli listen to input and call dispatch
	 */

	public static void cli(String svrHost, IServer svr) {

		// Main command-line interface loop

		try {
			InetAddress serverAddress = InetAddress.getByName(svrHost);
			Dispatch d = new Dispatch(svr, serverAddress);
			BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

			while (true) {
				msg("ftp> ");
				String line = in.readLine();
				String[] inputs = line.split("\\s+");
				if (inputs.length > 0) {
					String cmd = inputs[0];
					if (cmd.length()==0)
						;
					else if ("get".equals(cmd))
						d.get(inputs);
					else if ("put".equals(cmd))
						d.put(inputs);
					else if ("cd".equals(cmd))
						d.cd(inputs);
					else if ("pwd".equals(cmd))
						d.pwd(inputs);
					else if ("dir".equals(cmd))
						d.dir(inputs);
					else if ("ldir".equals(cmd))
						d.ldir(inputs);
					else if ("port".equals(cmd))
						d.port(inputs);
					else if ("pasv".equals(cmd))
						d.pasv(inputs);
					else if ("help".equals(cmd))
						d.help(inputs);
					else if ("quit".equals(cmd))
						return;
					else
						msgln("Bad input.  Type \"help\" for more information.");
				}
			}
		} catch (EOFException e) {
		} catch (UnknownHostException e) {
			err(e);
			System.exit(-1);
		} catch (IOException e) {
			err(e);
			System.exit(-1);
		}
		

	}

	public static class Dispatch {

		private IServer svr;
		
		private InetAddress serverAddress;


		Dispatch(IServer s, InetAddress sa) {
			svr = s;
			serverAddress = sa;
		}

		public void help(String[] inputs) {
			if (inputs.length == 1) {
				msgln("Commands are:");
				msgln("  get filename: download file from server");
				msgln("  put filename: upload file to server");
				msgln("  pwd: current working directory on server");
				msgln("  cd filename: change working directory on server");
				msgln("  dir: list contents of working directory on server");
				msgln("  ldir: list contents of current directory on client");
				msgln("  port: server should transfer files in active mode");
				msgln("  pasv: server should transfer files in passive mode");
				msgln("  quit: exit the client");
			}
		}

		/*
		 * ********************************************************************************************
		 * Data connection.
		 */

		enum Mode {
			NONE, PASSIVE, ACTIVE
		};

		/*
		 * Note: This refers to the mode of the SERVER.
		 */
		private Mode mode = Mode.NONE;

		/*
		 * If active mode, remember the client socket.
		 */

		private ServerSocket dataChan = null;

		private InetSocketAddress makeActive() throws IOException {
			dataChan = new ServerSocket(0,5,InetAddress.getByName("172.31.44.198"));
			mode = Mode.ACTIVE;
			/*
			 * Note: this only works (for the server) if the client is not behind a NAT.
			 */
			return (InetSocketAddress) (dataChan.getLocalSocketAddress());
		}

		/*
		 * If passive mode, remember the server socket address.
		 * Client request with ip and port, server return socket address to let in.
		 */
		private InetSocketAddress serverSocket = null;

		private void makePassive(InetSocketAddress s) {
			serverSocket = s;
			mode = Mode.PASSIVE;
		}

		/*
		 * *********************************************************************************************
		 */

		private static class GetThread implements Runnable {
			/*
			 * This client-side thread runs when the server is active mode and a
			 * file download is initiated. This thread listens for a connection
			 * request from the server. The client-side server socket (...)
			 * should have been created when the port command put the server in
			 * active mode.
			 */
			private ServerSocket dataChan = null;
			private FileOutputStream file = null;

			public GetThread(ServerSocket s, FileOutputStream f) {
				dataChan = s;
				file = f;
			}

			public void run() {
				try {
					/*
					 * TODO: Complete this thread.
					 */
					Socket xfer = dataChan.accept();
					InputStream in = xfer.getInputStream();
					IOUtils.copyStream(in, file);

				} catch (IOException e) {
					msg("Exception: " + e);
					e.printStackTrace();
				}
			}
		}

		private static class PutThread implements Runnable {
			private ServerSocket dataChan = null;
			private FileInputStream file = null;

			public PutThread(ServerSocket s, FileInputStream f) {
				dataChan = s;
				file = f;
			}


			public void run() {
				try {
					Socket xfer = dataChan.accept(); // listen
					OutputStream out = xfer.getOutputStream();
					IOUtils.copyStream(file, out);

				} catch (IOException e) {
					msg("Exception: " + e);
					e.printStackTrace();
				}
			}
		}


		public void get(String[] inputs) {
			if (inputs.length == 2) {
				try {
					if (mode == Mode.PASSIVE) {
						svr.get(inputs[1]);
						FileOutputStream f = new FileOutputStream(inputs[1]);
						Socket xfer = new Socket(serverAddress, serverSocket.getPort());
						/*
						 * TODO: connect to server socket to transfer file.
						 */
						InputStream in = xfer.getInputStream();
						IOUtils.copyStream(in, f);


					} else if (mode == Mode.ACTIVE) {
						FileOutputStream f = new FileOutputStream(inputs[1]);
						new Thread(new GetThread(dataChan, f)).start();
						svr.get(inputs[1]);

					} else {
						msgln("GET: No mode set--use port or pasv command.");
					}
				} catch (Exception e) {
					err(e);
				}
			}
		}

		public void put(String[] inputs) {
			if (inputs.length == 2) {
				try {
					/*
					 * TODO: Finish put (both ACTIVE and PASSIVE mode supported).
					 */
					if( mode == Mode.PASSIVE) {
						svr.put(inputs[1]);
						FileInputStream f = new FileInputStream(inputs[1]);
						Socket xfer = new Socket(serverAddress, serverSocket.getPort());
						OutputStream out = xfer.getOutputStream();
						IOUtils.copyStream(f, out);
					} else if (mode == Mode.ACTIVE){
						FileInputStream f = new FileInputStream(inputs[1]);
						new Thread(new PutThread(dataChan, f)).start();
						svr.put(inputs[1]);
					}

				} catch (Exception e) {
					err(e);
				}
			}
		}

		public void cd(String[] inputs) {
			if (inputs.length == 2)
				try {
					svr.cd(inputs[1]);
					msgln("CWD: "+svr.pwd());
				} catch (Exception e) {
					err(e);
				}
		}

		public void pwd(String[] inputs) {
			if (inputs.length == 1)
				try {
					msgln("CWD: "+svr.pwd());
				} catch (Exception e) {
					err(e);
				}
		}

		public void dir(String[] inputs) {
			if (inputs.length == 1) {
				try {
					String[] fs = svr.dir();
					for (int i = 0; i < fs.length; i++) {
						msgln(fs[i]);
					}
				} catch (Exception e) {
					err(e);
				}
			}
		}




		public void pasv(String[] inputs) {
			if (inputs.length == 1) {
				try {
					makePassive(svr.pasv()); // return a data chanel on server
					msgln("PASV: Server in passive mode.");
				} catch (Exception e) {
					err(e);
				}
			}
		}



		public void port(String[] inputs) {
			if (inputs.length == 1) {
				try {
					InetSocketAddress s = makeActive();
					svr.port(s);
					msgln("PORT: Server in active mode.");
				} catch (Exception e) {
					err(e);
				}
			}
		}

		public void ldir(String[] inputs) {
			if (inputs.length == 1) {
				String[] fs = new File(".").list();
				for (int i = 0; i < fs.length; i++) {
					msgln(fs[i]);
				}
			}
		}

	}

}
