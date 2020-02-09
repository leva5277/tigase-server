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
package tigase.xmpp.impl;

import tigase.db.NonAuthUserRepository;
import tigase.db.TigaseDBException;
import tigase.db.UserRepository;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.server.Iq;
import tigase.server.Packet;
import tigase.server.PolicyViolationException;
import tigase.server.xmppsession.SessionManager;
import tigase.util.Base64;
import tigase.xml.Element;
import tigase.xmpp.*;
import tigase.xmpp.impl.annotation.*;
import tigase.xmpp.impl.roster.RosterAbstract;
import tigase.xmpp.impl.roster.RosterFactory;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.function.Consumer;

import static tigase.xmpp.impl.MIXProcessor.ID;

@Id(ID)
@Bean(name = ID, parent = SessionManager.class, active = false)
@Handles({
		@Handle(path = {Iq.ELEM_NAME, "client-join"}, xmlns = "urn:xmpp:mix:pam:2"),
		@Handle(path = {Iq.ELEM_NAME, "client-leave"}, xmlns = "urn:xmpp:mix:pam:2"),
		@Handle(path = {Iq.ELEM_NAME, "join"}, xmlns = "urn:xmpp:mix:core:1"),
		@Handle(path = {Iq.ELEM_NAME, "leave"}, xmlns = "urn:xmpp:mix:core:1")
})
@DiscoFeatures({ ID })
public class MIXProcessor
		extends AnnotatedXMPPProcessor implements XMPPProcessorIfc {

	public static final String ID = "urn:xmpp:mix:pam:2";

	@Inject
	private UserRepository userRepository;

	private static final RosterAbstract rosterUtil = RosterFactory.getRosterImplementation(true);

	@Override
	public Authorization canHandle(Packet packet, XMPPResourceConnection conn) {
		Authorization result = super.canHandle(packet, conn);
		if (result == Authorization.AUTHORIZED) {
			if (packet.getElement().findChild(el -> el.getXMLNS() == ID) != null) {
				// we need packet to be sent from local connection to process PAM request
				return conn != null ? result : null;
			} else {
				// join/leave response is only processed if returned to bare JID
				return packet.getStanzaTo() != null && packet.getStanzaTo().getResource() == null ? result : null;
			}
		}
		return result;
	}

	@Override
	public void process(Packet packet, XMPPResourceConnection session, NonAuthUserRepository repo,
						Queue<Packet> results, Map<String, Object> settings) throws XMPPException {
		try {
			if (XMPPProcessorAbstract.isFromUserSession(packet, session)) {
				// processing packet coming fom the user..
				Element actionEl = packet.getElement().findChild(el -> el.getXMLNS() == ID);
				if (actionEl == null || !EnumSet.of(StanzaType.set).contains(packet.getType())) {
					// TODO: Add a way to forward iq's sent from the client directly to MIX component (leave/join)
					// FIXED: we do not need that as leave/join are no longer processed here
					throw new XMPPProcessorException(Authorization.BAD_REQUEST);
				}

				BareJID channel = Optional.ofNullable(actionEl.getAttributeStaticStr("channel"))
						.map(BareJID::bareJIDInstanceNS)
						.orElseThrow(() -> new XMPPProcessorException(Authorization.BAD_REQUEST));

				String id = packet.getStanzaId();
				if (id == null) {
					throw new XMPPProcessorException(Authorization.BAD_REQUEST);
				}

				switch (actionEl.getName()) {
					case "client-join":
						Element joinEl = Optional.ofNullable(actionEl.getChildStaticStr("join", "urn:xmpp:mix:core:1")).orElseThrow(() -> new XMPPProcessorException(Authorization.BAD_REQUEST));
						session.setData(ID, generateId(channel, id), session.getResource());
						sendToChannel(session.getBareJID(), channel, id, joinEl, results::offer);
						break;
					case "client-leave":
						Element leaveEl = Optional.ofNullable(actionEl.getChildStaticStr("leave", "urn:xmpp:mix:core:1"))
								.orElseThrow(() -> new XMPPProcessorException(Authorization.BAD_REQUEST));
						session.setData(ID, generateId(channel, id), session.getResource());
						sendToChannel(session.getBareJID(), channel, id, leaveEl, results::offer);
						break;
					default:
						throw new XMPPProcessorException(Authorization.BAD_REQUEST);
				}
			} else {
				// processing packet from the outside, ie. from other component or MIX component.. but always sent to the bare JID
				BareJID channel = packet.getStanzaFrom().getBareJID();
				if (packet.getStanzaId() != null &&
						EnumSet.of(StanzaType.result, StanzaType.error).contains(packet.getType())) {
					BareJID userJID = packet.getStanzaTo().getBareJID();
					String requestId = generateId(channel, packet.getStanzaId());
					String resource = userRepository.getData(userJID, ID, requestId, null);
					if (resource == null) {
						// this is error or result, so lets just ignore that as most likely it was already handled...
						return;
					}

					Element actionEl = packet.getElement().findChild(el -> el.getXMLNS() == "urn:xmpp:mix:core:1");
					userRepository.removeData(userJID, ID, requestId);

					if (packet.getType() == StanzaType.result) {
						// we need to modify roster..
						// FIXME: also for leaving the channel
						if (session != null && session.isAuthorized()) {
							JID channelJID = JID.jidInstance(channel);
							switch (actionEl.getName()) {
								case "join":
									rosterUtil.addBuddy(session, channelJID, null, null, RosterAbstract.SubscriptionType.both, actionEl.getAttributeStaticStr("id"),
														null);
									Element new_buddy = rosterUtil.getBuddyItem(session, channelJID);
									rosterUtil.updateBuddyChange(session, results, new_buddy);
									break;
								case "leave":
									Element it = new Element("item");

									it.setAttribute("jid", channelJID.toString());
									it.setAttribute("subscription", "remove");
									rosterUtil.removeBuddy(session, channelJID);
									rosterUtil.updateBuddyChange(session, results, it);
									break;
							}
							Optional.ofNullable(session.getParentSession())
									.map(parent -> parent.getResourceForResource(resource))
									.map(conn -> {
										try {
											return conn.getConnectionId();
										} catch (NoConnectionIdException ex) {
											return null;
										}
									})
									.ifPresent(connJID -> sendToUser(userJID, resource, connJID, packet.getType(),
																	 packet.getStanzaId(), actionEl, results::offer));
						} else {
							results.offer(Authorization.SERVICE_UNAVAILABLE.getResponseMessage(packet, null, true));
						}
					}
				} else {
					throw new XMPPProcessorException(Authorization.BAD_REQUEST);
				}
			}
		} catch (PolicyViolationException ex) {
			throw new XMPPProcessorException(Authorization.POLICY_VIOLATION, ex.getMessage(), ex);
		} catch (TigaseDBException ex) {
			throw new XMPPProcessorException(Authorization.INTERNAL_SERVER_ERROR, "Cound not access database", ex);
		}
	}

	protected void sendToChannel(BareJID userJID, BareJID channel, String id, Element actionEl, Consumer<Packet> writer) {
		Element iqEl = new Element("iq");
		iqEl.setXMLNS(Iq.CLIENT_XMLNS);
		iqEl.setAttribute("id", id);
		iqEl.setAttribute("type", "set");

		iqEl.addChild(actionEl);

		writer.accept(Packet.packetInstance(iqEl, JID.jidInstance(userJID), JID.jidInstance(channel)));
	}

	protected void sendToUser(BareJID userJID, String resource, JID connectionJID, StanzaType stanzaType, String id, Element actionEl, Consumer<Packet> writer) {
		Element iqEl = new Element("iq");
		iqEl.setXMLNS(Iq.CLIENT_XMLNS);
		iqEl.setAttribute("id", id);
		if (stanzaType != null) {
			iqEl.setAttribute("type", stanzaType.toString());
		}

		if (actionEl != null) {
			Element wrapEl = null;
			switch (actionEl.getName()) {
				case "join":
					wrapEl = new Element("client-join");
					break;
				case "leave":
					wrapEl = new Element("client-leave");
					break;
				default:
					break;
			}
			if (wrapEl != null) {
				wrapEl.setXMLNS(ID);
				wrapEl.addChild(actionEl);
				iqEl.addChild(wrapEl);
			}
		}

		Packet response = Packet.packetInstance(iqEl, JID.jidInstance(userJID), JID.jidInstanceNS(userJID, resource));
		response.setPacketTo(connectionJID);
		writer.accept(response);
	}

	protected String generateId(BareJID channel, String packetID) throws XMPPProcessorException {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			md.update(channel.toString().getBytes(StandardCharsets.UTF_8));
			byte[] hash = md.digest(packetID.getBytes(StandardCharsets.UTF_8));
			return Base64.encode(hash);
		} catch (NoSuchAlgorithmException ex) {
			throw new XMPPProcessorException(Authorization.INTERNAL_SERVER_ERROR);
		}
	}
}
