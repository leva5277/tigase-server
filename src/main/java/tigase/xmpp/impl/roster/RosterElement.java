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
package tigase.xmpp.impl.roster;

import tigase.util.stringprep.TigaseStringprepException;
import tigase.util.stringprep.XMPPStringPrepFactory;
import tigase.xml.Element;
import tigase.xml.XMLUtils;
import tigase.xmpp.impl.roster.RosterAbstract.SubscriptionType;
import tigase.xmpp.jid.JID;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Describe class RosterElement here.
 * <br>
 * Created: Wed Oct 29 14:21:16 2008
 *
 * @author <a href="mailto:artur.hefczyc@tigase.org">Artur Hefczyc</a>
 */
public class RosterElement {

	protected static final long INITIAL_LAST_SEEN_VAL = 1000l;
	private static final String ACTIVITY_ATT = "activity";
	private static final String ELEM_NAME = "contact";
	private static final String GRP_ATT = "groups";
	private static final double INITIAL_ACTIVITY_VAL = 1d;
	private static final double INITIAL_WEIGHT_VAL = 1d;
	private static final String JID_ATT = "jid";
	private static final String LAST_SEEN_ATT = "last-seen";
	private static final Logger log = Logger.getLogger(RosterElement.class.getName());
	private static final String NAME_ATT = "name";
	private static final String OTHER_ATT = "other";
	private static final String STRINGPREP_ATT = "preped";
	private static final String SUBS_ATT = "subs";
	private static final String WEIGHT_ATT = "weight";
	private static final String MIX_PARTICIPANT_ID_ATT = "mix-id";

	private double activity = INITIAL_ACTIVITY_VAL;
	private String[] groups = null;
	private JID jid = null;
	private long lastSeen = INITIAL_LAST_SEEN_VAL;
	private String mixParticipantId = null;
	// private Element item = null;
	// private boolean online = false;
	private boolean modified = false;
	private String name = null;
	private Map<String, Boolean> onlineMap = new ConcurrentHashMap<String, Boolean>();
	private String otherData = null;
	private boolean persistent = true;
	private boolean presence_sent = false;
	private String stringpreped = null;
	private SubscriptionType subscription = null;
	private double weight = INITIAL_WEIGHT_VAL;

	public RosterElement(Element roster_el) throws TigaseStringprepException {
		if (roster_el.getName() == ELEM_NAME) {
			this.stringpreped = roster_el.getAttributeStaticStr(STRINGPREP_ATT);
			setJid(roster_el.getAttributeStaticStr(JID_ATT));
			setName(roster_el.getAttributeStaticStr(NAME_ATT));
			if (roster_el.getAttributeStaticStr(SUBS_ATT) == null) {
				subscription = SubscriptionType.none;
			} else {
				subscription = SubscriptionType.valueOf(roster_el.getAttributeStaticStr(SUBS_ATT));
			}

			String grps = roster_el.getAttributeStaticStr(GRP_ATT);

			if ((grps != null) && !grps.trim().isEmpty()) {
				setGroups(grps.split(","));
			}

			mixParticipantId = roster_el.getAttributeStaticStr(MIX_PARTICIPANT_ID_ATT);

			String other_data = roster_el.getAttributeStaticStr(OTHER_ATT);

			if ((other_data != null) && !other_data.trim().isEmpty()) {
				otherData = other_data;
			}

			String num_str = roster_el.getAttributeStaticStr(ACTIVITY_ATT);

			if (num_str != null) {
				try {
					activity = Double.parseDouble(num_str);
				} catch (NumberFormatException nfe) {
					log.warning("Incorrect activity field: " + num_str);
					activity = INITIAL_ACTIVITY_VAL;
				}
			}
			num_str = roster_el.getAttributeStaticStr(WEIGHT_ATT);
			if (num_str != null) {
				try {
					weight = Double.parseDouble(num_str);
				} catch (NumberFormatException nfe) {
					log.warning("Incorrect weight field: " + num_str);
					weight = INITIAL_WEIGHT_VAL;
				}
			}
			num_str = roster_el.getAttributeStaticStr(LAST_SEEN_ATT);
			if (num_str != null) {
				try {
					lastSeen = Long.parseLong(num_str);
				} catch (NumberFormatException nfe) {
					log.warning("Incorrect last seen field: " + num_str);
					lastSeen = INITIAL_LAST_SEEN_VAL;
				}
			}
			// how roster can be modified as it is read from DB?
			modified = false;
		} else {
			log.warning("Incorrect roster data: " + roster_el.toString());
		}
	}

	public RosterElement(JID jid, String name, String[] groups) {
		this.stringpreped = XMPPStringPrepFactory.STRINGPREP_PROCESSOR;
		setJid(jid);
		setName(name);
		setGroups(groups);
		this.subscription = SubscriptionType.none;
	}

	public void addGroups(String[] groups) {
		if (groups != null) {
			if (this.groups == null) {
				setGroups(groups);
			} else {

				// Groups names must be unique
				Set<String> groupsSet = new HashSet<String>();

				for (String group : this.groups) {
					groupsSet.add(group);
				}
				for (String group : groups) {
					groupsSet.add(group);
				}
				setGroups(groupsSet.toArray(new String[groupsSet.size()]));
			}
		}

	}

	public String[] getGroups() {
		return groups;
	}

	public final void setGroups(String[] groups) {
		if ((groups != null) && (groups.length > 0)) {
			this.groups = new String[groups.length];
			for (int i = 0; i < groups.length; i++) {
				this.groups[i] = XMLUtils.unescape(groups[i]);
			}
		} else {
			this.groups = null;
		}
		modified = true;
	}

	public JID getJid() {
		return jid;
	}

	private void setJid(String jid) throws TigaseStringprepException {
		if (XMPPStringPrepFactory.STRINGPREP_PROCESSOR.equals(stringpreped)) {
			this.jid = JID.jidInstanceNS(jid);
		} else {
			this.jid = JID.jidInstance(jid);
			modified = true;
		}
		stringpreped = XMPPStringPrepFactory.STRINGPREP_PROCESSOR;
	}

	public String getName() {
		return name;
	}

	public final void setName(final String name) {
		if (name == this.name || (name != null && this.name != null && name.equals(this.name))) {
			return;
		} else {
			this.name = name == null ? null : XMLUtils.unescape(name);
			this.modified = true;
		}

//		String old_name = this.name;
//		if (name == null) {
//			this.name = this.jid.getLocalpart();
//			if ((this.name == null) || this.name.trim().isEmpty()) {
//				this.name = this.jid.getBareJID().toString();
//			}
//		} else {
//			this.name = XMLUtils.unescape(name);
//		}
//		if (!this.name.equals(old_name)) {
//			modified = true;
//		}
	}

	public String getOtherData() {
		return otherData;
	}

	public void setOtherData(String other_data) {
		otherData = other_data;
	}

	public String getMixParticipantId() {
		return mixParticipantId;
	}

	public void setMixParticipantId(String value) {
		this.mixParticipantId = value;
	}

	public Element getRosterElement() {
		Element elem = new Element(ELEM_NAME, new String[]{JID_ATT, SUBS_ATT, STRINGPREP_ATT},
								   new String[]{jid.toString(), subscription.toString(), "" + stringpreped});

		if (name != null) {
			elem.setAttribute(NAME_ATT, XMLUtils.escape(name));
		}

		if ((groups != null) && (groups.length > 0)) {
			String grps = "";

			for (String group : groups) {
				grps += XMLUtils.escape(group) + ",";
			}
			grps = grps.substring(0, grps.length() - 1);
			elem.setAttribute(GRP_ATT, grps);
		}
		if (mixParticipantId != null) {
			elem.setAttribute(MIX_PARTICIPANT_ID_ATT, mixParticipantId);
		}
		if (otherData != null) {
			elem.setAttribute(OTHER_ATT, otherData);
		}
		elem.setAttribute(ACTIVITY_ATT, Double.toString(activity));
		elem.setAttribute(WEIGHT_ATT, Double.toString(weight));
		elem.setAttribute(LAST_SEEN_ATT, Long.toString(lastSeen));
		modified = false;

		return elem;
	}

	public Element getRosterItem() {

		// This is actually not a good idea to cache the item element.
		// This causes a huge memory consumption and usually the item
		// is needed only once at the roster retrieving time.
		// if (item == null) {
		Element item = new Element("item");

		item.setAttribute("jid", jid.toString());
		item.addAttributes(subscription.getSubscriptionAttr());
		if (name != null) {
			item.setAttribute("name", XMLUtils.escape(name));
		}
		if (groups != null) {
			for (String gr : groups) {
				Element group = new Element("group");

				group.setCData(XMLUtils.escape(gr));
				item.addChild(group);
			}    // end of for ()
		}      // end of if-else
		return item;
	}

	@Override
	public String toString() {
		return getRosterItem().toString();
	}

	public SubscriptionType getSubscription() {
		return subscription;
	}

	public void setSubscription(SubscriptionType subscription) {
		if (subscription == null) {
			this.subscription = SubscriptionType.none;
		} else {
			this.subscription = subscription;
		}
		modified = true;
	}

	public boolean isModified() {
		return modified;
	}

	public boolean isOnline() {
		return onlineMap.size() > 0;
	}

	public boolean isPresence_sent() {
		return presence_sent;
	}

	public void setPresence_sent(boolean presence_sent) {
		this.presence_sent = presence_sent;
	}

	public void setOnline(String resource, boolean online) {
		if ((onlineMap != null) && (resource != null)) {
			if (online) {
				onlineMap.put(resource, Boolean.TRUE);
			} else {
				onlineMap.remove(resource);
			}
		}
	}

	public boolean isPersistent() {
		return persistent;
	}

	public void setPersistent(boolean persistent) {
		this.persistent = persistent;
	}

	public double getActivity() {
		return activity;
	}

	public void setActivity(double activity) {
		this.activity = activity;
		if (activity != 0) {
			weight = 1 / activity;
		}
		modified = true;
	}

	public double getWeight() {
		return weight;
	}

	public void setWeight(double weight) {
		this.weight = weight;
		modified = true;
	}

	public long getLastSeen() {
		return lastSeen;
	}

	public void setLastSeen(long lastSeen) {
		this.lastSeen = lastSeen;
		modified = true;
	}

	private void setJid(JID jid) {
		this.jid = jid;
		modified = true;
	}
}
