/*
 *  Package Tigase XMPP/Jabber Server
 *  Copyright (C) 2004, 2005, 2006
 *  "Artur Hefczyc" <artur.hefczyc@tigase.org>
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software Foundation,
 *  Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * $Rev$
 * Last modified by $Author$
 * $Date$
 */

package tigase.server;

import java.util.List;
import java.util.Set;
import java.util.HashSet;
import tigase.xml.Element;
import tigase.xmpp.StanzaType;

/**
 * Class Packet
 *
 * Represent one XMPP packet.
 *
 * Created: Tue Nov 22 07:07:11 2005
 *
 * @author <a href="mailto:artur.hefczyc@tigase.org">Artur Hefczyc</a>
 * @version $Rev$
 */
public class Packet {

  private static final String ERROR_NS = "urn:ietf:params:xml:ns:xmpp-stanzas";

	private Set<String> processorsIds = new HashSet<String>();

	private final Element elem;
	private final Command command;
	private final String strCommand;
	private final boolean cmd;
	private final StanzaType type;
	private final boolean routed;
	private String to = null;
	private String from = null;

  public Packet(final Element elem) {
		if (elem == null) {
			throw new NullPointerException();
		} // end of if (elem == null)
		this.elem = elem;
		if (elem.getName().equals("iq")) {
			Element child = elem.getChild("command", Command.XMLNS);
			if (child != null) {
				cmd = true;
				strCommand = child.getAttribute("node");
				command = Command.valueof(strCommand);
			} else {
				strCommand = null;
				command = null;
				cmd = false;
			}
		} else {
			strCommand = null;
			command = null;
			cmd = false;
		}
		if (elem.getAttribute("type") != null) {
			type = StanzaType.valueof(elem.getAttribute("type"));
		} else {
			type = null;
		} // end of if (elem.getAttribute("type") != null) else
		if (elem.getName().equals("route")) {
			routed = true;
		} // end of if (elem.getName().equals("route"))
		else {
			routed = false;
		} // end of if (elem.getName().equals("route")) else
	}

	public void processedBy(String id) {
		processorsIds.add(id);
	}

	public boolean wasProcessed() {
		return processorsIds.size() > 0;
	}

	public boolean wasProcessedBy(String id) {
		return processorsIds.contains(id);
	}

	public Set<String> getProcessorsIds() {
		return processorsIds;
	}

	public Command getCommand() {
		return command;
	}

	public String getStrCommand() {
		return strCommand;
	}

	public StanzaType getType() {
		return type;
	}

	public Element getElement() {
		return elem;
	}

	public String getElemName() {
		return elem.getName();
	}

	public boolean isCommand() {
		return cmd;
	}

	public String getTo() {
		return to != null ? to : getElemTo();
	}

	public void setTo(final String to) {
		this.to = to;
	}

	public String getFrom() {
		return from != null ? from : getElemFrom();
	}

	public void setFrom(final String from) {
		this.from = from;
	}

	/**
   * Returns packet destination address.
   */
  public String getElemTo() {
    return elem.getAttribute("to");
  }

  /**
   * Returns packet source address.
   */
  public String getElemFrom() {
    return elem.getAttribute("from");
  }

	public String getElemId() {
    return elem.getAttribute("id");
	}

	public String getElemCData(final String path) {
		return elem.getCData(path);
	}

	public List<Element> getElemChildren(final String path) {
		return elem.getChildren(path);
	}

	public String getElemCData() {
		return elem.getCData();
	}

  public byte[] getByteData() {
    return elem.toString().getBytes();
  }

  public String getStringData() {
    return elem.toString();
  }

  public char[] getCharData() {
    return elem.toString().toCharArray();
  }

	public String toString() {
		return "to="+to+", from="+from+", data="+elem.toString();
	}

	public boolean isRouted() {
		return routed;
	}

	public Packet unpackRouted() {
		Packet result = new Packet(elem.getChildren().get(0));
		result.setTo(getTo());
		result.setFrom(getFrom());
		return result;
	}

// 	public Packet packRouted(final String from, final String to) {
// 		Element routed = new Element("route", null, new String[] {"to", "from"},
// 			new String[] {to, from});
// 		routed.addChild(elem);
// 		return new Packet(routed);
// 	}

	public Packet packRouted() {
		Element routed = new Element("route", new String[] {"to", "from"},
			new String[] {getTo(), getFrom()});
		routed.addChild(elem);
		return new Packet(routed);
	}

	public Packet swapFromTo(final Element el) {
		Packet packet = new Packet(el);
		packet.setTo(getFrom());
		packet.setFrom(getTo());
		return packet;
	}

// 	public Packet commandResult(final String data) {
// 		Packet result = command.getPacket(getTo(), getFrom(),
// 			StanzaType.result, elem.getAttribute("id"), data);
// 		//		result.getElement().setCData(data);
// 		result.getElement().setCData(data);
// 		return result;
// 	}

	public Packet commandResult() {
		Packet result = command.getPacket(getTo(), getFrom(),
			StanzaType.result, elem.getAttribute("id"));
		return result;
	}

// 	public Packet commandResult(final Element child) {
// 		Packet result = command.getPacket(getTo(), getFrom(),
// 			StanzaType.result, elem.getAttribute("id"));
// 		result.getElement().addChild(child);
// 		return result;
// 	}

// 	public Packet commandResult(final List<Element> children) {
// 		Packet result = command.getPacket(getTo(), getFrom(),
// 			StanzaType.result, elem.getAttribute("id"));
// 		result.getElement().addChildren(children);
// 		return result;
// 	}

	public Packet errorResult(final String errorType, final String errorCondition,
		final String errorText, final boolean includeOriginalXML) {
		Element reply = new Element(elem.getName());
		reply.setAttribute("type", StanzaType.error.toString());
		if (getElemFrom() != null) {
			reply.setAttribute("to", getElemFrom());
		} // end of if (getElemFrom() != null)
		if (getElemId() != null) {
			reply.setAttribute("id", getElemId());
		} // end of if (getElemId() != null)
		if (includeOriginalXML) {
			reply.addChildren(elem.getChildren());
		} // end of if (includeOriginalXML)
		Element error = new Element("error");
		error.setAttribute("type", errorType);
		Element cond = new Element(errorCondition);
		cond.setXMLNS(ERROR_NS);
		error.addChild(cond);
		if (errorText != null) {
			Element t = new Element("text", errorText,
				new String[] {"xml:lang", "xmlns"},
				new String[] {"en", ERROR_NS});
			error.addChild(t);
		} // end of if (text != null && text.length() > 0)
		reply.addChild(error);
		return swapFromTo(reply);
	}

	public Packet okResult(final String includeXML, final int originalXML) {
		Element reply = new Element(elem.getName());
		reply.setAttribute("type", StanzaType.result.toString());
		if (getElemFrom() != null) {
			reply.setAttribute("to", getElemFrom());
		} // end of if (getElemFrom() != null)
		if (getElemTo() != null) {
			reply.setAttribute("from", getElemTo());
		} // end of if (getElemFrom() != null)
		if (getElemId() != null) {
			reply.setAttribute("id", getElemId());
		} // end of if (getElemId() != null)
		Element old_child = elem;
		Element new_child = reply;
		for (int i = 0; i < originalXML; i++) {
			old_child = old_child.getChildren().get(0);
			Element tmp = new Element(old_child.getName());
			tmp.setXMLNS(old_child.getXMLNS());
			new_child.addChild(tmp);
			new_child = tmp;
		} // end of for (int i = 0; i < originalXML; i++)
		if (includeXML != null) {
			new_child.setCData(includeXML);
		} // end of if (includeOriginalXML)
		return swapFromTo(reply);
	}

	public Packet okResult(final Element includeXML, final int originalXML) {
		Element reply = new Element(elem.getName());
		reply.setAttribute("type", StanzaType.result.toString());
		if (getElemFrom() != null) {
			reply.setAttribute("to", getElemFrom());
		} // end of if (getElemFrom() != null)
		if (getElemTo() != null) {
			reply.setAttribute("from", getElemTo());
		} // end of if (getElemFrom() != null)
		if (getElemId() != null) {
			reply.setAttribute("id", getElemId());
		} // end of if (getElemId() != null)
		Element old_child = elem;
		Element new_child = reply;
		for (int i = 0; i < originalXML; i++) {
			old_child = old_child.getChildren().get(0);
			Element tmp = new Element(old_child.getName());
			tmp.setXMLNS(old_child.getXMLNS());
			new_child.addChild(tmp);
			new_child = tmp;
		} // end of for (int i = 0; i < originalXML; i++)
		if (includeXML != null) {
			new_child.addChild(includeXML);
		} // end of if (includeOriginalXML)
		return swapFromTo(reply);
	}

	public Packet swapElemFromTo() {
		Element copy = (Element)elem.clone();
		copy.setAttribute("to", getElemFrom());
		copy.setAttribute("from", getElemTo());
		return new Packet(copy);
	}

	public Packet swapElemFromTo(final StanzaType type) {
		Element copy = (Element)elem.clone();
		copy.setAttribute("to", getElemFrom());
		copy.setAttribute("from", getElemTo());
		copy.setAttribute("type", type.toString());
		return new Packet(copy);
	}

}
