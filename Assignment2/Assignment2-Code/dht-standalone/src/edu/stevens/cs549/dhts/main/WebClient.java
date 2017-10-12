package edu.stevens.cs549.dhts.main;

import edu.stevens.cs549.dhts.activity.DHTBase;
import edu.stevens.cs549.dhts.activity.NodeInfo;
import edu.stevens.cs549.dhts.resource.TableRep;
import edu.stevens.cs549.dhts.resource.TableRow;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.xml.bind.JAXBElement;
import java.net.URI;
import java.util.logging.Logger;

public class WebClient {

	private Logger log = Logger.getLogger(WebClient.class.getCanonicalName());

	private void error(String msg) {
		log.severe(msg);
	}

	/*
	 * Encapsulate Web client operations here.
	 * 
	 * TODO: Fill in missing operations.
	 */


	/*
	 * Creation of client instances is expensive, so just create one.
	 */
	protected Client client;
	
	public WebClient() {
		client = ClientBuilder.newClient();
	}

	private void info(String mesg) {
		Log.info(mesg);
	}

	private Response getRequest(URI uri) {
		try {
			Response cr = client.target(uri)
					.request(MediaType.APPLICATION_XML_TYPE)
					.header(Time.TIME_STAMP, Time.advanceTime())
					.get();
			processResponseTimestamp(cr);
			return cr;
		} catch (Exception e) {
			error("Exception during GET request: " + e);
			return null;
		}
	}

	private Response putRequest(URI uri, Entity<?> entity) {
		// TODO
		try {
			Response cr = client.target(uri)
					.request(MediaType.APPLICATION_XML_TYPE)
					.header(Time.TIME_STAMP, Time.advanceTime())
					.put(entity);
			processResponseTimestamp(cr);
			return cr;
		} catch (Exception e) {
			error("Exception during PUT request: " + e);
			return null;
		}
	}
	
	private Response putRequest(URI uri) {
		return putRequest(uri, Entity.text(""));
	}

	private Response deleteRequest(URI uri) {
		try {
			Response cr = client.target(uri)
					.request(MediaType.APPLICATION_XML_TYPE)
					.header(Time.TIME_STAMP, Time.advanceTime())
					.delete();
			processResponseTimestamp(cr);
			return cr;
		} catch (Exception e) {
			error("Exception during DELETE request: " + e);
			return null;
		}
	}


	private void processResponseTimestamp(Response cr) {
		Time.advanceTime(Long.parseLong(cr.getHeaders().getFirst(Time.TIME_STAMP).toString()));
	}

	/*
	 * Jersey way of dealing with JAXB client-side: wrap with run-time type
	 * information.
	 */
	private GenericType<JAXBElement<NodeInfo>> nodeInfoType = new GenericType<JAXBElement<NodeInfo>>() {
	};

	private GenericType<JAXBElement<TableRow>> tableRowType = new GenericType<JAXBElement<TableRow>>() {
	};

	/*
	 * Ping a remote site to see if it is still available.
	 */
	public boolean isFailed(URI base) {
		URI uri = UriBuilder.fromUri(base).path("info").build();
		Response c = getRequest(uri);
		return c.getStatus() >= 300;
	}

	public NodeInfo findSuccessor(URI addr, int id) throws DHTBase.Failed {
		URI findPath = UriBuilder.fromUri(addr).path("find").queryParam("id", id).build();
		info("client findSuccessor(" + findPath + ")");
		Response response = getRequest(findPath);
		if (response == null || response.getStatus() >= 300) {
			throw new DHTBase.Failed("GET /find");
		} else {
			return response.readEntity(nodeInfoType).getValue();
		}
	}

	public NodeInfo getSucc(NodeInfo info) throws DHTBase.Failed {
		URI succPath = UriBuilder.fromUri(info.addr).path("succ").build();
		info("client getSucc(" + succPath + ")");
		Response response = getRequest(succPath);
		if (response == null || response.getStatus() >= 300) {
			throw new DHTBase.Failed("GET /succ");
		} else {
			return response.readEntity(nodeInfoType).getValue();
		}
	}

	public String[] get(URI addr, String k) throws DHTBase.Failed {
		URI getPath = UriBuilder.fromUri(addr).path(k).build();
		info("client add(" + getPath + ")");
		Response response = getRequest(getPath);
		if (response == null || response.getStatus() >= 300) {
			throw new DHTBase.Failed(String.format("GET /%s", k));
		} else {
			return response.readEntity(tableRowType).getValue().vals;
		}
	}

	public void add(URI addr, String k, String v) throws DHTBase.Failed{
		URI addPath = UriBuilder.fromUri(addr).path(k).path(v).build();
		info("client add(" + addPath + ")");
		Response response = putRequest(addPath);
		if (response == null || response.getStatus() >= 300) {
			throw new DHTBase.Failed(String.format("POST /%s/%s", k, v));
		}
	}

	public void delete(URI addr, String k, String v) throws DHTBase.Failed {
		URI delPath = UriBuilder.fromUri(addr).path(k).path(v).build();
		info("client delete(" + delPath + ")");
		Response response = deleteRequest(delPath);
		if (response == null || response.getStatus() >= 300) {
			throw new DHTBase.Failed(String.format("DELETE /%s/%s", k, v));
		}
	}

	/*
	 * Get the predecessor pointer at a node.
	 */
	public NodeInfo getPred(NodeInfo node) throws DHTBase.Failed {
		URI predPath = UriBuilder.fromUri(node.addr).path("pred").build();
		info("client getPred(" + predPath + ")");
		Response response = getRequest(predPath);
		if (response == null || response.getStatus() >= 300) {
			throw new DHTBase.Failed("GET /pred");
		} else {
			NodeInfo pred = response.readEntity(nodeInfoType).getValue();
			return pred;
		}
	}

	/*
	 * Notify node that we (think we) are its predecessor.
	 */
	public TableRep notify(NodeInfo node, TableRep predDb) throws DHTBase.Failed {
		/*
		 * The protocol here is more complex than for other operations. We
		 * notify a new successor that we are its predecessor, and expect its
		 * bindings as a result. But if it fails to accept us as its predecessor
		 * (someone else has become intermediate predecessor since we found out
		 * this node is our successor i.e. race condition that we don't try to
		 * avoid because to do so is infeasible), it notifies us by returning
		 * null. This is represented in HTTP by RC=304 (Not Modified).
		 */
		NodeInfo thisNode = predDb.getInfo();
		UriBuilder ub = UriBuilder.fromUri(node.addr).path("notify");
		URI notifyPath = ub.queryParam("id", thisNode.id).build();
		info("client notify(" + notifyPath + ")");
		Response response = putRequest(notifyPath, Entity.xml(predDb));
		if (response != null && response.getStatusInfo() == Response.Status.NOT_MODIFIED) {
			/*
			 * Do nothing, the successor did not accept us as its predecessor.
			 */
			return null;
		} else if (response == null || response.getStatus() >= 300) {
			throw new DHTBase.Failed("PUT /notify?id=ID");
		} else {
			TableRep bindings = response.readEntity(TableRep.class);
			return bindings;
		}
	}

	public NodeInfo closestPrecedingFinger(NodeInfo node, int id) throws DHTBase.Failed {
		URI getPath = UriBuilder.fromUri(node.addr).path("finger").queryParam("id",id).build();
		info("client findColsestfinger(" + getPath + ")");
		Response response = getRequest(getPath);
		if (response == null || response.getStatus() >= 300) {
			throw new DHTBase.Failed("GET /finger");
		} else {
			NodeInfo finger = response.readEntity(nodeInfoType).getValue();
			return finger;
		}
	}

}
