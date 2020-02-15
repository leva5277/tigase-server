/*
 * Tigase XMPP Server - The instant messaging server
 * Copyright (C) 2004 Tigase, Inc. (office@tigase.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 */
package tigase.cluster.strategy;

import tigase.cluster.api.ClusterControllerIfc;
import tigase.cluster.api.CommandListener;
import tigase.cluster.api.SessionManagerClusteredIfc;
import tigase.cluster.strategy.cmd.PacketForwardCmd;
import tigase.eventbus.EventBus;
import tigase.kernel.beans.Inject;
import tigase.kernel.beans.config.ConfigField;
import tigase.server.Iq;
import tigase.server.Message;
import tigase.server.Packet;
import tigase.stats.StatisticsList;
import tigase.util.Algorithms;
import tigase.util.dns.DNSResolverFactory;
import tigase.xml.Element;
import tigase.xmpp.StanzaType;
import tigase.xmpp.XMPPResourceConnection;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.logging.Level;
import java.util.logging.Logger;

import static tigase.cluster.api.SessionManagerClusteredIfc.SESSION_FOUND_KEY;

/**
 * Created: May 13, 2009 9:53:44 AM
 *
 * @author <a href="mailto:artur.hefczyc@tigase.org">Artur Hefczyc</a>
 */
public abstract class DefaultClusteringStrategyAbstract<E extends ConnectionRecordIfc>
		implements ClusteringStrategyIfc<E> {

	private static final String ERROR_FORWARDING_KEY = "error-forwarding";

	private static final Logger log = Logger.getLogger(DefaultClusteringStrategyAbstract.class.getName());
	private static final String PACKET_FORWARD_CMD = "packet-forward-sm-cmd";
	@Inject
	protected ClusterControllerIfc cluster = null;
	protected String comp = "sess-man";

	@Inject
	protected EventBus eventBus = null;
	protected String prefix = "strategy/" + this.getClass().getSimpleName() + "/";
	@Inject
	protected SessionManagerClusteredIfc sm = null;
	private JID ampJID = JID.jidInstanceNS("amp", DNSResolverFactory.getInstance().getDefaultHost());
	private Set<CommandListener> commands = new CopyOnWriteArraySet<CommandListener>();
	@ConfigField(desc="Forward error packets within cluster", alias = "error-forwarding")
	private ErrorForwarding errorForwarding = ErrorForwarding.forward;
	private static EnumSet<StanzaType> ERROR_OR_RESULT = EnumSet.of(StanzaType.error, StanzaType.result);

	private Map<String, JID> iqResourceToClusterNode = new ConcurrentHashMap<>();

	public DefaultClusteringStrategyAbstract() {
		super();
		addCommandListener(new PacketForwardCmd(PACKET_FORWARD_CMD, this));
	}

	// private ClusteringMetadataIfc<E> metadata = null;

	@Override
	public void statisticExecutedIn(long executionTime) {

	}

	@Override
	public void everyHour() {

	}

	@Override
	public void everyMinute() {

	}

	@Override
	public void everySecond() {

	}

	@Override
	public void getStatistics(String compName, StatisticsList list) {

	}

	@Override
	public void setStatisticsPrefix(String prefix) {
		this.prefix = prefix;
	}

	public final void addCommandListener(CommandListener cmd) {
		commands.add(cmd);
	}

	@Override
	public boolean containsJid(BareJID jid) {
		return false;
	}

	@Override
	public boolean containsJidLocally(BareJID jid) {
		return false;
	}

	@Override
	public boolean containsJidLocally(JID jid) {
		return false;
	}

	@Override
	public void handleLocalPacket(Packet packet, XMPPResourceConnection conn) {
	}

	@Override
	public void handleLocalPresenceSet(XMPPResourceConnection conn) {
		// Do nothing
	}

	@Override
	public void handleLocalResourceBind(XMPPResourceConnection conn) {
		// Do nothing
	}

	@Override
	public void handleLocalUserLogin(BareJID userId, XMPPResourceConnection conn) {

		// Do nothing
	}

	@Override
	public void handleLocalUserLogout(BareJID userId, XMPPResourceConnection conn) {

		// Do nothing
	}

	@Override
	public void handleLocalUserChangedConnId(BareJID userId, XMPPResourceConnection conn, JID oldConnId,
											 JID newConnId) {
		// Do nothing
	}

	@Override
	public boolean processPacket(Packet packet, XMPPResourceConnection conn) {
		List<JID> toNodes = getNodesForPacketForward(sm.getComponentId(), null, packet);
		boolean result = (toNodes != null) && (toNodes.size() > 0);

		if (result) {
			if (log.isLoggable(Level.FINEST)) {
				log.log(Level.FINEST, "Forwarding packet {0} to nodes: {1}", new Object[]{packet, toNodes});
			}

			Map<String, String> data = null;

			if (conn != null || packet.getPacketFrom() != null) {
				data = new LinkedHashMap<String, String>();
				if (conn != null) {
					data.put(SESSION_FOUND_KEY, sm.getComponentId().toString());
				}
				if (packet.getPacketFrom() != null) {
					data.put(PacketForwardCmd.PACKET_FROM_KEY, packet.getPacketFrom().toString());
				}
				if (packet.getStanzaId() != null) {
					data.put(PacketForwardCmd.STABLE_ID_KEY, packet.getStableId());
				}
			}
			cluster.sendToNodes(PACKET_FORWARD_CMD, data, packet.getElement(), sm.getComponentId(), null,
								toNodes.toArray(new JID[toNodes.size()]));
		} else {
			if (log.isLoggable(Level.FINEST)) {
				log.log(Level.FINEST, "No cluster nodes found for packet forward: {0}", new Object[]{packet});
			}
		}

		return result;
	}

	/**
	 * Method attempts to send the packet to the next cluster node. Returns true on successful attempt and false on
	 * failure. The true result does not mean that the packet has been delivered though. Only that it was sent. The send
	 * attempt may fail if there is no more cluster nodes to send the packet or if the clustering strategy logic decided
	 * that the packet does not have to be sent.
	 *
	 * @param packet to be sent to a next cluster node
	 * @param visitedNodes a list of nodes already visited by the packet.
	 *
	 * @return true if the packet was sent to next cluster node and false otherwise.
	 */
	public boolean sendToNextNode(JID fromNode, Set<JID> visitedNodes, Map<String, String> data, Packet packet) {
		boolean result = false;
		List<JID> nextNodes = getNodesForPacketForward(fromNode, visitedNodes, packet);

		if ((nextNodes != null) && (nextNodes.size() > 0)) {
			cluster.sendToNodes(PACKET_FORWARD_CMD, data, packet.getElement(), fromNode, visitedNodes,
								nextNodes.toArray(new JID[nextNodes.size()]));
			result = true;
		}
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "Called for packet: {0}, visitedNodes: {1}, result: {2}",
					new Object[]{packet, visitedNodes, result});
		}

		return result;
	}

	@Override
	public String toString() {
		return getInfo();
	}

	@Override
	public List<JID> getNodesConnected() {
		return sm.getNodesConnected();
	}

	@Override
	public JID[] getConnectionIdsForJid(BareJID jid) {
		return null;
	}

	@Override
	public E getConnectionRecord(JID jid) {
		return null;
	}

	@Override
	public E getConnectionRecordInstance() {
		return (E) (new ConnectionRecord());
	}

	@Override
	public Set<E> getConnectionRecords(BareJID bareJID) {
		return null;
	}

	@Override
	public Map<String, Object> getDefaults(Map<String, Object> params) {
		Map<String, Object> props = new HashMap<String, Object>();

		props.put(ERROR_FORWARDING_KEY, ErrorForwarding.drop.name());

		return props;
	}

	@Override
	public String getInfo() {
		return "basic strategy";
	}

	public List<JID> getNodesForPacketForward(JID fromNode, Set<JID> visitedNodes, Packet packet) {

		// If visited nodes is not null then we return null as this strategy never
		// sends packets in ring, the first node decides where to send a packet
		if (visitedNodes != null) {
			return null;
		}

		List<JID> nodes = null;

//  JID jidLookup = packet.getStanzaTo();
//
//  // Presence status change set by the user have a special treatment:
//  if (presenceStatusUpdate(packet)) {
//    jidLookup = packet.getStanzaFrom();
//  }
		if (isSuitableForForward(packet)) {
			if (isIqResponseToNode(packet)) {
				nodes = getNodesForIqResponse(packet);
			} else {
				// nodes = metadata.getNodesForJid(jidLookup);
				nodes = getNodesConnected();
			}
			if (log.isLoggable(Level.FINEST)) {
				log.log(Level.FINEST, "Selected nodes: {0}, for packet: {1}", new Object[]{nodes, packet});
			}
		} else {
			if (log.isLoggable(Level.FINEST)) {
				log.log(Level.FINEST, "Packet not suitable for forwarding: {0}", new Object[]{packet});
			}
		}

		return nodes;
	}

	public boolean isIqResponseToNode(Packet packet) {
		if (packet.getElemName() == Iq.ELEM_NAME && ERROR_OR_RESULT.contains(packet.getType())) {
			JID to = packet.getStanzaTo();
			return to != null && sm.isLocalDomain(to.getBareJID().toString(), false) && to.getResource() != null;
		}
		return false;
	}

	public List<JID> getNodesForIqResponse(Packet packet) {
		JID clusterNode = iqResourceToClusterNode.get(packet.getStanzaTo().getResource());
		if (clusterNode != null) {
			return Collections.singletonList(clusterNode);
		}
		return null;
	}

	@Override
	public void getStatistics(StatisticsList list) {
		for (CommandListener cmd : commands) {
			cmd.getStatistics(list);
		}
		getStatistics("sess-man/", list);
	}

	@Override
	public boolean hasCompleteJidsInfo() {
		return false;
	}

	@Override
	public void nodeConnected(JID node) {
		String hash = Algorithms.sha256(node.getDomain());
		iqResourceToClusterNode.put(hash, node);
	}

	@Override
	public void nodeDisconnected(JID node) {
		String hash = Algorithms.sha256(node.getDomain());
		iqResourceToClusterNode.remove(hash, node);
	}

	@Override
	public void setClusterController(ClusterControllerIfc clComp) {
		cluster = clComp;
		for (CommandListener cmd : commands) {
			cluster.removeCommandListener(cmd);
			cluster.setCommandListener(cmd);
		}
	}

	@Override
	public void setProperties(Map<String, Object> props) {

		// This code is bad as some commands are added in other methods and in
		// constructors - this code would break those commands!
//		// we need to remember that this method can be called more than once
//		// and we need to clean list of commands if we are adding any command here
//		CommandListener[] oldCmds = commands.toArray(new CommandListener[commands.size()]);
//
//		for (CommandListener oldCmd : oldCmds) {
//			if (PACKET_FORWARD_CMD.equals(oldCmd.getName())) {
//				commands.remove(oldCmd);
//			}
//		}
//		addCommandListener(new PacketForwardCmd(PACKET_FORWARD_CMD, sm, this));
		if (props.containsKey(ERROR_FORWARDING_KEY)) {
			errorForwarding = ErrorForwarding.valueOf((String) props.get(ERROR_FORWARDING_KEY));
		}
	}

	public SessionManagerClusteredIfc getSM() {
		return this.sm;
	}

	public void fireEvent(Object event) {
		eventBus.fire(event);
	}

	protected boolean isSuitableForForward(Packet packet) {
		switch (errorForwarding) {
			case forward:
				break;

			default:

				// Do not forward any error packets by default
				if (packet.getType() == StanzaType.error) {
					return false;
				}

				break;
		}

		// Artur: Moved it to the front of the method for performance reasons.
		// TODO: make sure it does not affect logic.
		// Andrzej: this blocks sending responses from other components if
		// packet was processed on other node that node of user session, ie.
		// when new PubSub clustered component is used
//		if ((packet.getPacketFrom() != null) &&!sm.getComponentId().equals(packet
//				.getPacketFrom())) {
//			return false;
//		}
		// Andrzej: Added in place of condition which was checked above as due to
		// lack of this condition messages sent from client with "from" attribute
		// set are duplicated
		if (packet.getPacketFrom() != null && sm.hasXMPPResourceConnectionForConnectionJid(packet.getPacketFrom())) {
			return false;
		}

		// This is for packet forwarding logic.
		// Some packets are for certain not forwarded like packets without to
		// attribute set.
		if ((packet.getStanzaTo() == null) || sm.isLocalDomain(packet.getStanzaTo().toString(), false) ||
				sm.getComponentId().getBareJID().equals((packet.getStanzaTo().getBareJID()))) {
			return false;
		}

//		if (sm.isLocalDomain(packet.getStanzaTo().getBareJID().toString(), false)) {
//			if (packet.getElemName() == Iq.ELEM_NAME &&
//					(packet.getType() == StanzaType.error || packet.getType() == StanzaType.result) &&
//					packet.getStanzaTo().getResource() != null) {
//				return true;
//			}
//			return false;
//		}

		// Also packets sent from the server to user are not being forwarded like
		// service discovery perhaps?
		if ((packet.getStanzaFrom() == null) || sm.isLocalDomain(packet.getStanzaFrom().toString(), false) ||
				sm.getComponentId().getBareJID().equals((packet.getStanzaFrom().getBareJID()))) {
			return false;
		}

		// If the packet is to some external domain, it is not forwarded to other
		// nodes either. It is also not forwarded if it is addressed to some
		// component.
		if (!sm.isLocalDomain(packet.getStanzaTo().getDomain(), false)) {
			return false;
		}

		// server needs to respond on Iq stanzas sent to bare jid, but there is
		// no need to forward it to cluster node with users session
		if (packet.getElemName() == Iq.ELEM_NAME && packet.getStanzaTo() != null &&
				packet.getStanzaTo().getResource() == null) {
			return false;
		}

		if (packet.getElemName() == Message.ELEM_NAME && packet.getType() != StanzaType.error &&
				ampJID.equals(packet.getPacketFrom())) {
			Element amp = packet.getElement().getChild("amp", "http://jabber.org/protocol/amp");
			if (amp != null && amp.getAttributeStaticStr("status") == null) {
				return false;
			}
		}

		return true;
	}

	private static enum ErrorForwarding {
		forward,
		drop
	}
}

