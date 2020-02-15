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

import org.junit.Before;
import org.junit.Test;
import tigase.kernel.core.Kernel;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.StanzaType;
import tigase.xmpp.XMPPResourceConnection;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.Assert.*;

/**
 * @author andrzej
 */
public class MessageTest
		extends ProcessorTestCase {

	private MessageDeliveryLogic messageDeliveryLogic;
	private Message messageProcessor;

	@Override
	protected void registerBeans(Kernel kernel) {
		super.registerBeans(kernel);
		kernel.registerBean(MessageDeliveryLogic.class).exec();
		kernel.registerBean(Message.class).setActive(true).exec();
	}

	@Before
	@Override
	public void setUp() throws Exception {
		super.setUp();
		messageProcessor = getInstance(Message.class);
		messageDeliveryLogic = getInstance(MessageDeliveryLogic.class);
	}

	@Test
	public void testSilentlyIgnoringMessages() throws Exception {
		BareJID userJid = BareJID.bareJIDInstance("user1@example.com");
		JID res1 = JID.jidInstance(userJid, "res1");

		// testing default behaviour - error message
		Element packetEl = new Element("message", new String[]{"from", "to"},
									   new String[]{"remote-user@test.com/res1", res1.toString()});
		Packet packet = Packet.packetInstance(packetEl);
		Queue<Packet> results = new ArrayDeque<Packet>();
		messageProcessor.process(packet, null, null, results, null);
		assertTrue("no error was generated", !results.isEmpty());
		assertTrue("generated result is not an error", results.poll().getType().equals(StanzaType.error));

		// testing silently ignoring error responses
		results.clear();
		Field f = messageDeliveryLogic.getClass().getDeclaredField("silentlyIgnoreError");
		f.setAccessible(true);
		f.set(messageDeliveryLogic, true);

		messageProcessor.process(packet, null, null, results, null);
		assertTrue("result was generated", results.isEmpty());
	}

	@Test
	public void testResourceSelectionForMessageDeliveryMethods() throws Exception {
		BareJID userJid = BareJID.bareJIDInstance("user1@example.com");
		JID res1 = JID.jidInstance(userJid, "res1");
		JID res2 = JID.jidInstance(userJid, "res2");
		XMPPResourceConnection session1 = getSession(res1, res1);
		XMPPResourceConnection session2 = getSession(res2, res2);

		assertEquals(Arrays.asList(session1, session2), session1.getActiveSessions());
		assertEquals(Collections.emptyList(), messageDeliveryLogic.getConnectionsForMessageDelivery(session1));

		assertFalse("found XMPPResourceConnection for delivery of message",
					messageDeliveryLogic.hasConnectionForMessageDelivery(session1));
		session1.setPriority(1);
		assertTrue("found XMPPResourceConnection for delivery of message",
				   messageDeliveryLogic.hasConnectionForMessageDelivery(session1));

		session1.setPresence(new Element("presence"));
		assertTrue("could not find XMPPResourceConnection for delivery of message",
				   messageDeliveryLogic.hasConnectionForMessageDelivery(session1));

		assertEquals(Arrays.asList(session1), messageDeliveryLogic.getConnectionsForMessageDelivery(session2));

		session2.setPresence(new Element("presence"));
		assertTrue("could not find XMPPResourceConnection for delivery of message",
				   messageDeliveryLogic.hasConnectionForMessageDelivery(session1));
		assertEquals(Arrays.asList(session1, session2), messageDeliveryLogic.getConnectionsForMessageDelivery(session2));
	}

	@Test
	public void testResourceSelectionForMessageDeliveryForBareJid() throws Exception {
		BareJID userJid = BareJID.bareJIDInstance("user1@example.com");
		JID res1 = JID.jidInstance(userJid, "res1");
		JID res2 = JID.jidInstance(userJid, "res2");
		XMPPResourceConnection session1 = getSession(JID.jidInstance("c2s@example.com/" + UUID.randomUUID().toString()),
													 res1);
		XMPPResourceConnection session2 = getSession(JID.jidInstance("c2s@example.com/" + UUID.randomUUID().toString()),
													 res2);

		assertEquals(Arrays.asList(session1, session2), session1.getActiveSessions());
		assertEquals(Collections.emptyList(), messageDeliveryLogic.getConnectionsForMessageDelivery(session1));

		Element packetEl = new Element("message", new String[]{"type", "from", "to"},
									   new String[]{"chat", "remote-user@test.com/res1", userJid.toString()});
		Packet packet = Packet.packetInstance(packetEl);
		Queue<Packet> results = new ArrayDeque<Packet>();
		messageProcessor.process(packet, session2, null, results, null);
		assertTrue("generated result even than no resource had nonnegative priority", results.isEmpty());

//		session1.setPriority(1);
//		results = new ArrayDeque<Packet>();
//		messageProcessor.process(packet, session2, null, results, null);
//		assertTrue("generated result even than no resource had nonnegative priority", results.isEmpty());

		session1.setPresence(new Element("presence"));
		assertTrue("could not find XMPPResourceConnection for delivery of message",
				   messageDeliveryLogic.hasConnectionForMessageDelivery(session1));
		results = new ArrayDeque<Packet>();
		messageProcessor.process(packet, session2, null, results, null);
		assertEquals("not generated result even than 1 resource had nonnegative priority", 1, results.size());
		assertEquals("packet sent to wrong jids", Arrays.asList(session1.getConnectionId()), collectPacketTo(results));

		session2.setPresence(new Element("presence"));
		assertTrue("could not find XMPPResourceConnection for delivery of message",
				   messageDeliveryLogic.hasConnectionForMessageDelivery(session1));
		results = new ArrayDeque<Packet>();
		messageProcessor.process(packet, session1, null, results, null);
		assertEquals("not generated result even than 2 resource had nonnegative priority", 2, results.size());
		assertEquals("packet sent to wrong jids", Arrays.asList(session1.getConnectionId(), session2.getConnectionId()),
					 collectPacketTo(results));
	}

	@Test
	public void testResourceSelectionForMessageDeliveryForFullJid() throws Exception {
		BareJID userJid = BareJID.bareJIDInstance("user1@example.com");
		JID res1 = JID.jidInstance(userJid, "res1");
		JID res2 = JID.jidInstance(userJid, "res2");
		XMPPResourceConnection session1 = getSession(JID.jidInstance("c2s@example.com/" + UUID.randomUUID().toString()),
													 res1);
		XMPPResourceConnection session2 = getSession(JID.jidInstance("c2s@example.com/" + UUID.randomUUID().toString()),
													 res2);

		assertEquals(Arrays.asList(session1, session2), session1.getActiveSessions());
		assertEquals(Collections.emptyList(), messageDeliveryLogic.getConnectionsForMessageDelivery(session1));

		Element packetEl = new Element("message", new String[]{"type", "from", "to"},
									   new String[]{"chat", "remote-user@test.com/res1", res1.toString()});
		Packet packet = Packet.packetInstance(packetEl);
		Queue<Packet> results = new ArrayDeque<Packet>();
		messageProcessor.process(packet, session2, null, results, null);
		assertEquals("not generated result even than no resource had nonnegative priority", 1, results.size());
		assertEquals("packet sent to wrong jids", Arrays.asList(session1.getConnectionId()), collectPacketTo(results));

		session1.setPriority(1);
		results = new ArrayDeque<Packet>();
		messageProcessor.process(packet, session2, null, results, null);
		assertEquals("not generated result even than no resource had nonnegative priority", 1, results.size());
		assertEquals("packet sent to wrong jids", Arrays.asList(session1.getConnectionId()), collectPacketTo(results));

		session1.setPresence(new Element("presence"));
		assertTrue("could not find XMPPResourceConnection for delivery of message",
				   messageDeliveryLogic.hasConnectionForMessageDelivery(session1));
		results = new ArrayDeque<Packet>();
		messageProcessor.process(packet, session2, null, results, null);
		assertEquals("not generated result even than 1 resource had nonnegative priority", 1, results.size());
		assertEquals("packet sent to wrong jids", Arrays.asList(session1.getConnectionId()), collectPacketTo(results));

		session2.setPresence(new Element("presence"));
		assertTrue("could not find XMPPResourceConnection for delivery of message",
				   messageDeliveryLogic.hasConnectionForMessageDelivery(session1));
		results = new ArrayDeque<Packet>();
		messageProcessor.process(packet, session1, null, results, null);
		assertEquals("not generated result even than 2 resource had nonnegative priority", 1, results.size());
		assertEquals("packet sent to wrong jids", Arrays.asList(session1.getConnectionId()), collectPacketTo(results));
	}

	protected List<JID> collectPacketTo(Queue<Packet> packets) {
		List<JID> result = new ArrayList<JID>();
		Packet p;
		while ((p = packets.poll()) != null) {
			result.add(p.getPacketTo());
		}
		return result;
	}
}
