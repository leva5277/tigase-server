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
package tigase.server.amp.action;

import tigase.kernel.beans.Bean;
import tigase.server.Packet;
import tigase.server.amp.ActionAbstract;
import tigase.server.amp.AmpComponent;
import tigase.xml.Element;

/**
 * Created: May 1, 2010 11:28:40 AM
 *
 * @author <a href="mailto:artur.hefczyc@tigase.org">Artur Hefczyc</a>
*/
@Bean(name = "deliver", parent = AmpComponent.class, active = true)
public class Deliver
		extends ActionAbstract {

	private static final String name = "deliver";

	@Override
	public boolean execute(Packet packet, Element rule) {
		Packet result = packet.copyElementOnly();
		if (packet.getAttributeStaticStr(FROM_CONN_ID) == null) {
			result.setPacketFrom(packet.getPacketTo());
		}
		result.setStableId(packet.getStableId());
		removeTigasePayload(result);
		resultsHandler.addOutPacket(result);
		return true;
	}

	@Override
	public String getName() {
		return name;
	}
}
