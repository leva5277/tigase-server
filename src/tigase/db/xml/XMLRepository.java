/*  Package Tigase XMPP/Jabber Server
 *  Copyright (C) 2001, 2002, 2003, 2004, 2005
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
package tigase.db.xml;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;
import tigase.db.UserExistsException;
import tigase.db.UserNotFoundException;
import tigase.db.UserRepository;
import tigase.xml.db.NodeExistsException;
import tigase.xml.db.NodeNotFoundException;
import tigase.xml.db.XMLDB;

/**
 * Class <code>XMLRepository</code> is a <em>XML</em> implementation of
 * <code>UserRepository</code>.
 * It uses <code>tigase.xml.db</code> package as repository backend and uses
 * <em>Bridge</em> design pattern to translate <code>XMLDB</code> calls to
 * <code>UserRepository</code> functions.
 *
 * <p>
 * Created: Tue Oct 26 15:27:33 2004
 * </p>
 * @author <a href="mailto:artur.hefczyc@tigase.org">Artur Hefczyc</a>
 * @version $Rev$
 */
public class XMLRepository implements UserRepository {

	//	public static final String PUBLIC_DATA_NODE = "public";

	private static final String USER_STR = "User: ";
  private static final String NOT_FOUND_STR =
    " has not been found in repository.";

  private static final Logger log =
    Logger.getLogger("tigase.xmpp.rep.xml.XMLRepository");

	private static ConcurrentMap<String, UserRepository> instances =
		new ConcurrentHashMap<String, UserRepository>();

	private XMLDB xmldb = null; // NOPMD

  /**
   * Creates a new <code>XMLRepository</code> instance using default repository
   * <em>XML</em> file name.
   */
  private XMLRepository(final String file_name) {
    try {
      xmldb = new XMLDB(file_name);
    } catch (Exception e) {
      log.warning("Can not open existing user repository file, creating new one, "
        + e);
      xmldb = XMLDB.createDB(file_name, "users", "user");
    } // end of try-catch
  }

  // Implementation of tigase.xmpp.rep.UserRepository

	public static UserRepository getInstance(String resource) {
		UserRepository rep = instances.get(resource);
		if (rep == null) {
			rep = new XMLRepository(resource);
			instances.put(resource, rep);
		} // end of if (rep == null)
		return rep;
	}

  /**
   * This <code>addUser</code> method allows to add new user to reposiotry.
   * It <b>must</b> throw en exception <code>UserExistsException</code> if such
   * user already exists because user <b>must</b> be unique within user
   * repository data base.<br/>
   * As one <em>XMPP</em> server can support many virtual internet domains it
   * is required that <code>user</code> id consists of user name and domain
   * address: <em>username@domain.address.net</em> for example.
   *
   * @param user a <code>String</code> value of user id consisting of user name
   * and domain address.
   * @exception UserExistsException if user with the same id already exists.
   */
  public final void addUser(final String user) throws UserExistsException {
    try {
      xmldb.addNode1(user);
    } // end of try
    catch (NodeExistsException e) {
      throw new UserExistsException(USER_STR+user+" already exists.", e);
    } // end of try-catch
  }

  /**
   * This <code>removeUser</code> method allows to remove user and all his data
   * from user repository.
   * If given user id does not exist <code>UserNotFoundException</code> must be
   * thrown. As one <em>XMPP</em> server can support many virtual internet
   * domains it is required that <code>user</code> id consists of user name and
   * domain address: <em>username@domain.address.net</em> for example.
   *
   * @param user a <code>String</code> value of user id consisting of user name
   * and domain address.
   * @exception UserNotFoundException if user id hasn't been found in reository.
   */
  public final void removeUser(final String user) throws UserNotFoundException {
    try {
      xmldb.removeNode1(user);
    } // end of try
    catch (NodeNotFoundException e) {
      throw new UserNotFoundException(USER_STR+user+
        NOT_FOUND_STR, e);
    } // end of try-catch
  }

  /**
   * <code>setData</code> method <!-- beauty loves beast --> sets data value for
   * given user ID in repository under given node path and associates it with
   * given key.
   * If there already exists value for given key in given node, old value is
   * replaced with new value. No warning or exception is thrown in case if
   * methods overwrites old value.
   *
   * @param user a <code>String</code> value of user ID for which data must be
   * stored. User ID consists of user name and domain name.
   * @param subnode a <code>String</code> value is a node path where data is
   * stored. Node path has the same form as directory path on file system:
   * <pre>/root/subnode1/subnode2</pre>.
   * @param key a <code>String</code> with which the specified value is to be
   * associated.
   * @param value a <code>String</code> value to be associated with the
   * specified key.
   * @exception UserNotFoundException if user id hasn't been found in reository.
   */
  public final void setData(final String user, final String subnode,
    final String key, final String value)
    throws UserNotFoundException {
    try {
      xmldb.setData(user, subnode, key, value);
    } // end of try
    catch (NodeNotFoundException e) {
      throw new UserNotFoundException(USER_STR+user+NOT_FOUND_STR, e);
    } // end of try-catch
  }

  /**
   * This <code>setData</code> method sets data value for given user ID
   * associated with given key in default repository node.
   * Default node is dependent on implementation and usually it is root user
   * node. If there already exists value for given key in given node, old value
   * is replaced with new value. No warning or exception is thrown in case if
   * methods overwrites old value.
   *
   * @param user a <code>String</code> value of user ID for which data must be
   * stored. User ID consists of user name and domain name.
   * @param key a <code>String</code> with which the specified value is to be
   * associated.
   * @param value a <code>String</code> value to be associated with the
   * specified key.
   * @exception UserNotFoundException if user id hasn't been found in reository.
   */
  public final void setData(final String user, final String key,
    final String value)
    throws UserNotFoundException {
    setData(user, null, key, value);
  }

  /**
   * <code>setDataList</code> method sets list of values for given user
   * associated given key in repository under given node path.
   * If there already exist values for given key in given node, all old values are
   * replaced with new values. No warning or exception is thrown in case if
   * methods overwrites old value.
   *
   * @param user a <code>String</code> value of user ID for which data must be
   * stored. User ID consists of user name and domain name.
   * @param subnode a <code>String</code> value is a node path where data is
   * stored. Node path has the same form as directory path on file system:
   * <pre>/root/subnode1/subnode2</pre>.
   * @param key a <code>String</code> with which the specified values list is to
   * be associated.
   * @param list a <code>String[]</code> is an array of values to be assosiated
   * with the specified key.
   * @exception UserNotFoundException if user id hasn't been found in reository.
   */
  public final void setDataList(final String user, final String subnode,
    final String key, final String[] list)
    throws UserNotFoundException {
    try {
      xmldb.setData(user, subnode, key, list);
    } // end of try
    catch (NodeNotFoundException e) {
      throw new UserNotFoundException(USER_STR+user+
        NOT_FOUND_STR, e);
    } // end of try-catch
  }

	/**
	 * <code>addDataList</code> method adds mode entries to existing data list
	 * associated with given key in repository under given node path.
	 * This method is very similar to <code>setDataList(...)</code> except it
	 * doesn't remove existing data.
	 *
   * @param user a <code>String</code> value of user ID for which data must be
   * stored. User ID consists of user name and domain name.
   * @param subnode a <code>String</code> value is a node path where data is
   * stored. Node path has the same form as directory path on file system:
   * <pre>/root/subnode1/subnode2</pre>.
   * @param key a <code>String</code> with which the specified values list is to
   * be associated.
   * @param list a <code>String[]</code> is an array of values to be assosiated
   * with the specified key.
   * @exception UserNotFoundException if user id hasn't been found in reository.
	 */
  public final void addDataList(final String user, final String subnode,
    final String key, final String[] list)
    throws UserNotFoundException {
    try {
			String[] old_data = getDataList(user, subnode, key);
			String[] all = null;
			if (old_data != null) {
				all = new String[old_data.length+list.length];
				System.arraycopy(old_data, 0, all, 0, old_data.length);
				System.arraycopy(list, 0, all, old_data.length, list.length);
				xmldb.setData(user, subnode, key, all);
			} else {
				xmldb.setData(user, subnode, key, list);
			} // end of else
    } // end of try
    catch (NodeNotFoundException e) {
      throw new UserNotFoundException(USER_STR+user+
        NOT_FOUND_STR, e);
    } // end of try-catch
  }

  /**
   * <code>getDataList</code> method returns array of values associated with
   * given key or <code>null</code> if given key does not exist for given user
   * ID in given node path.
   *
   * @param user a <code>String</code> value of user ID for which data must be
   * stored. User ID consists of user name and domain name.
   * @param subnode a <code>String</code> value is a node path where data is
   * stored. Node path has the same form as directory path on file system:
   * <pre>/root/subnode1/subnode2</pre>.
   * @param key a <code>String</code> with which the needed values list is
   * associated.
   * @return a <code>String[]</code> value
   * @exception UserNotFoundException if user id hasn't been found in reository.
   */
  public final String[] getDataList(final String user, final String subnode,
    final String key)
    throws UserNotFoundException {
    try {
      return xmldb.getDataList(user, subnode, key);
    } // end of try
    catch (NodeNotFoundException e) {
      throw new UserNotFoundException(USER_STR+user+
        NOT_FOUND_STR, e);
    } // end of try-catch
  }

  /**
   * <code>getData</code> method returns a value associated with given key for
   * user repository in given subnode.
   * If key is not found in repository given default value is returned.
   *
   * @param user a <code>String</code> value of user ID for which data must be
   * stored. User ID consists of user name and domain name.
   * @param subnode a <code>String</code> value is a node path where data is
   * stored. Node path has the same form as directory path on file system:
   * <pre>/root/subnode1/subnode2</pre>.
   * @param key a <code>String</code> with which the needed value is
   * associated.
   * @param def a <code>String</code> value which is returned in case if data
   * for specified key does not exixist in repository.
   * @return a <code>String</code> value
   * @exception UserNotFoundException if user id hasn't been found in reository.
   */
  public final String getData(final String user, final String subnode,
    final String key, final String def)
    throws UserNotFoundException {
    try {
      return (String)xmldb.getData(user, subnode, key, def);
    } // end of try
    catch (NodeNotFoundException e) {
      throw new UserNotFoundException(USER_STR+user+NOT_FOUND_STR, e);
    } // end of try-catch
  }

  /**
   * <code>getData</code> method returns a value associated with given key for
   * user repository in given subnode.
   * If key is not found in repository <code>null</code> value is returned.
   *
   * @param user a <code>String</code> value of user ID for which data must be
   * stored. User ID consists of user name and domain name.
   * @param subnode a <code>String</code> value is a node path where data is
   * stored. Node path has the same form as directory path on file system:
   * <pre>/root/subnode1/subnode2</pre>.
   * @param key a <code>String</code> with which the needed value is
   * associated.
   * @return a <code>String</code> value
   * @exception UserNotFoundException if user id hasn't been found in reository.
   */
  public final String getData(final String user, final String subnode,
    final String key)
    throws UserNotFoundException {
    return getData(user, subnode, key, null);
  }

  /**
   * <code>getData</code> method returns a value associated with given key for
   * user repository in default subnode.
   * If key is not found in repository <code>null</code> value is returned.
   *
   * @param user a <code>String</code> value of user ID for which data must be
   * stored. User ID consists of user name and domain name.
   * @param key a <code>String</code> with which the needed value is
   * associated.
   * @return a <code>String</code> value
   * @exception UserNotFoundException if user id hasn't been found in reository.
   */
  public final String getData(final String user, final String key)
    throws UserNotFoundException {
    return getData(user, null, key, null);
  }

  /**
   * <code>getSubnodes</code> method returns list of all direct subnodes from
   * given node.
   *
   * @param user a <code>String</code> value of user ID for which data must be
   * stored. User ID consists of user name and domain name.
   * @param subnode a <code>String</code> value is a node path where data is
   * stored. Node path has the same form as directory path on file system:
   * <pre>/root/subnode1/subnode2</pre>.
   * @return a <code>String[]</code> value is an array of all direct subnodes.
   * @exception UserNotFoundException if user id hasn't been found in reository.
   */
  public final String[] getSubnodes(final String user, final String subnode)
    throws UserNotFoundException {
    try {
      return xmldb.getSubnodes(user, subnode);
    } // end of try
    catch (NodeNotFoundException e) {
      throw new UserNotFoundException(USER_STR+user+NOT_FOUND_STR, e);
    } // end of try-catch
  }

  /**
   * <code>getSubnodes</code> method returns list of all <em>root</em> nodes for
   * given user.
   *
   * @param user a <code>String</code> value of user ID for which data must be
   * stored. User ID consists of user name and domain name.
   * @return a <code>String[]</code> value is an array of all <em>root</em>
   * nodes for given user.
   * @exception UserNotFoundException if user id hasn't been found in reository.
   */
  public final String[] getSubnodes(final String user)
    throws UserNotFoundException {
    return getSubnodes(user, null);
  }

  /**
   * <code>getKeys</code> method returns list of all keys stored in given
   * subnode in user repository.
   * There is a value (or list of values) associated with each key. It is up to
   * user (developer) to know what key keeps one value and what key keeps list
   * of values.
   *
   * @param user a <code>String</code> value of user ID for which data must be
   * stored. User ID consists of user name and domain name.
   * @param subnode a <code>String</code> value is a node path where data is
   * stored. Node path has the same form as directory path on file system:
   * <pre>/root/subnode1/subnode2</pre>.
   * @return a <code>String[]</code> value
   * @exception UserNotFoundException if user id hasn't been found in reository.
   */
  public final String[] getKeys(final String user, final String subnode)
    throws UserNotFoundException {
    try {
      return xmldb.getKeys(user, subnode);
    } // end of try
    catch (NodeNotFoundException e) {
      throw new UserNotFoundException(USER_STR+user+NOT_FOUND_STR, e);
    } // end of try-catch
  }

  /**
   * <code>getKeys</code> method returns list of all keys stored in default user
   * repository node.
   * There is some a value (or list of values) associated with each key. It is
   * up to user (developer) to know what key keeps one value and what key keeps
   * list of values.
   *
   * @param user a <code>String</code> value of user ID for which data must be
   * stored. User ID consists of user name and domain name.
   * @return a <code>String[]</code> value
   * @exception UserNotFoundException if user id hasn't been found in reository.
   */
  public final String[] getKeys(final String user)
    throws UserNotFoundException {
    return getKeys(user, null);
  }

  /**
   * <code>removeData</code> method removes pair (key, value) from user
   * reposiotry in given subnode.
   * If the key exists in user repository there is always a value
   * associated with this key - even empty <code>String</code>. If key does not
   * exist the <code>null</code> value is returned from repository backend or
   * given default value.
   *
   * @param user a <code>String</code> value of user ID for which data must be
   * stored. User ID consists of user name and domain name.
   * @param subnode a <code>String</code> value is a node path where data is
   * stored. Node path has the same form as directory path on file system:
   * <pre>/root/subnode1/subnode2</pre>.
   * @param key a <code>String</code> for which the value is to be removed.
   * @exception UserNotFoundException if user id hasn't been found in reository.
   */
  public final void removeData(final String user, final String subnode,
    final String key)
    throws UserNotFoundException {
    try {
      xmldb.removeData(user, subnode, key);
    } // end of try
    catch (NodeNotFoundException e) {
      throw new UserNotFoundException(USER_STR+user+NOT_FOUND_STR, e);
    } // end of try-catch
  }

  /**
   * <code>removeData</code> method removes pair (key, value) from user
   * reposiotry in default repository node.
   * If the key exists in user repository there is always a value
   * associated with this key - even empty <code>String</code>. If key does not
   * exist the <code>null</code> value is returned from repository backend or
   * given default value.
   *
   * @param user a <code>String</code> value of user ID for which data must be
   * stored. User ID consists of user name and domain name.
   * @param key a <code>String</code> for which the value is to be removed.
   * @exception UserNotFoundException if user id hasn't been found in reository.
   */
  public final void removeData(final String user, final String key)
    throws UserNotFoundException {
    removeData(user, null, key);
  }

  /**
   * <code>removeSubnode</code> method removes given subnode with all subnodes
   * in this node and all data stored in this node and in all subnodes.
   * Effectively it removes entire repository tree starting from given node.
   *
   * @param user a <code>String</code> value of user ID for which data must be
   * stored. User ID consists of user name and domain name.
   * @param subnode a <code>String</code> value is a node path to subnode which
   * has to be removed. Node path has the same form as directory path on file
   * system: <pre>/root/subnode1/subnode2</pre>.
   * @exception UserNotFoundException if user id hasn't been found in reository.
   */
  public final void removeSubnode(final String user, final String subnode)
    throws UserNotFoundException {
    try {
      xmldb.removeSubnode(user, subnode);
    } // end of try
    catch (NodeNotFoundException e) {
      throw new UserNotFoundException(USER_STR+user+NOT_FOUND_STR, e);
    } // end of try-catch
  }

// 	/**
// 	 * Method <code>userExists</code> checks whether specified user exists in
// 	 * repository. Return <code>true</code> if user exists, <code>false</code>
// 	 * otherwise.
// 	 *
// 	 * @param user a <code>String</code> value if user node it that is
// 	 * user <em>JID</em> without resource part.
// 	 * @return a <code>boolean</code> value of <code>true</code> if user exists
// 	 * and <code>false</code> otherwise.
// 	 */
// 	public boolean userExists(String user) {
// 		return xmldb.findNode1(user) != null ? true : false;
// 	}

//   /**
//    * <code>setData</code> method <!-- beauty loves beast --> sets data value for
//    * given user ID in repository under given node path and associates it with
//    * given key.
//    * If there already exists value for given key in given node, old value is
//    * replaced with new value. No warning or exception is thrown in case if
//    * methods overwrites old value.
//    *
//    * @param user a <code>String</code> value of user ID for which data must be
//    * stored. User ID consists of user name and domain name.
//    * @param subnode a <code>String</code> value is a node path where data is
//    * stored. Node path has the same form as directory path on file system:
//    * <pre>/root/subnode1/subnode2</pre>.
//    * @param key a <code>String</code> with which the specified value is to be
//    * associated.
//    * @param value a <code>String</code> value to be associated with the
//    * specified key.
//    * @exception UserNotFoundException if user id hasn't been found in reository.
//    */
//   public void setPublicData(String user, String subnode, String key,
// 		String value) throws UserNotFoundException {
// 		if (subnode != null) {
// 			setData(user, PUBLIC_DATA_NODE + "/" + subnode, key, value);
// 		} else {
// 			setData(user, PUBLIC_DATA_NODE, key, value);
// 		} // end of if (subnode != null) else
// 	}

//   /**
//    * <code>setDataList</code> method sets list of values for given user
//    * associated given key in repository under given node path.
//    * If there already exist values for given key in given node, all old values are
//    * replaced with new values. No warning or exception is thrown in case if
//    * methods overwrites old value.
//    *
//    * @param user a <code>String</code> value of user ID for which data must be
//    * stored. User ID consists of user name and domain name.
//    * @param subnode a <code>String</code> value is a node path where data is
//    * stored. Node path has the same form as directory path on file system:
//    * <pre>/root/subnode1/subnode2</pre>.
//    * @param key a <code>String</code> with which the specified values list is to
//    * be associated.
//    * @param list a <code>String[]</code> is an array of values to be assosiated
//    * with the specified key.
//    * @exception UserNotFoundException if user id hasn't been found in reository.
//    */
//   public void setPublicDataList(String user, String subnode, String key,
// 		String[] list) throws UserNotFoundException {
// 		if (subnode != null) {
// 			setDataList(user, PUBLIC_DATA_NODE + "/" + subnode, key, list);
// 		} else {
// 			setDataList(user, PUBLIC_DATA_NODE, key, list);
// 		} // end of if (subnode != null) else
// 	}

// 	/**
// 	 * <code>addDataList</code> method adds mode entries to existing data list
// 	 * associated with given key in repository under given node path.
// 	 * This method is very similar to <code>setDataList(...)</code> except it
// 	 * doesn't remove existing data.
// 	 *
//    * @param user a <code>String</code> value of user ID for which data must be
//    * stored. User ID consists of user name and domain name.
//    * @param subnode a <code>String</code> value is a node path where data is
//    * stored. Node path has the same form as directory path on file system:
//    * <pre>/root/subnode1/subnode2</pre>.
//    * @param key a <code>String</code> with which the specified values list is to
//    * be associated.
//    * @param list a <code>String[]</code> is an array of values to be assosiated
//    * with the specified key.
//    * @exception UserNotFoundException if user id hasn't been found in reository.
// 	 */
// 	public void addPublicDataList(String user, String subnode, String key,
// 		String[] list) throws UserNotFoundException {
// 		if (subnode != null) {
// 			addDataList(user, PUBLIC_DATA_NODE + "/" + subnode, key, list);
// 		} else {
// 			addDataList(user, PUBLIC_DATA_NODE, key, list);
// 		} // end of if (subnode != null) else
// 	}

//   /**
//    * <code>getData</code> method returns a value associated with given key for
//    * user repository in given subnode.
//    * If key is not found in repository given default value is returned.
//    *
//    * @param user a <code>String</code> value of user ID for which data must be
//    * stored. User ID consists of user name and domain name.
//    * @param subnode a <code>String</code> value is a node path where data is
//    * stored. Node path has the same form as directory path on file system:
//    * <pre>/root/subnode1/subnode2</pre>.
//    * @param key a <code>String</code> with which the needed value is
//    * associated.
//    * @param def a <code>String</code> value which is returned in case if data
//    * for specified key does not exixist in repository.
//    * @return a <code>String</code> value
//    * @exception UserNotFoundException if user id hasn't been found in reository.
//    */
//   public String getPublicData(String user, String subnode, String key,
// 		String def) throws UserNotFoundException {
// 		if (subnode != null) {
// 			return getData(user, PUBLIC_DATA_NODE + "/" + subnode, key, def);
// 		} else {
// 			return getData(user, PUBLIC_DATA_NODE, key, def);
// 		} // end of if (subnode != null) else
// 	}

//   /**
//    * <code>getDataList</code> method returns array of values associated with
//    * given key or <code>null</code> if given key does not exist for given user
//    * ID in given node path.
//    *
//    * @param user a <code>String</code> value of user ID for which data must be
//    * stored. User ID consists of user name and domain name.
//    * @param subnode a <code>String</code> value is a node path where data is
//    * stored. Node path has the same form as directory path on file system:
//    * <pre>/root/subnode1/subnode2</pre>.
//    * @param key a <code>String</code> with which the needed values list is
//    * associated.
//    * @return a <code>String[]</code> value
//    * @exception UserNotFoundException if user id hasn't been found in reository.
//    */
//   public String[] getPublicDataList(String user, String subnode, String key)
//     throws UserNotFoundException {
// 		if (subnode != null) {
// 			return getDataList(user, PUBLIC_DATA_NODE + "/" + subnode, key);
// 		} else {
// 			return getDataList(user, PUBLIC_DATA_NODE, key);
// 		} // end of if (subnode != null) else
// 	}

//   /**
//    * <code>removeData</code> method removes pair (key, value) from user
//    * reposiotry in default repository node.
//    * If the key exists in user repository there is always a value
//    * associated with this key - even empty <code>String</code>. If key does not
//    * exist the <code>null</code> value is returned from repository backend or
//    * given default value.
//    *
//    * @param user a <code>String</code> value of user ID for which data must be
//    * stored. User ID consists of user name and domain name.
//    * @param key a <code>String</code> for which the value is to be removed.
//    * @exception UserNotFoundException if user id hasn't been found in reository.
//    */
//   public void removePublicData(String user, String subnode, String key)
//     throws UserNotFoundException {
// 		if (subnode != null) {
// 			removeData(user, PUBLIC_DATA_NODE + "/" + subnode, key);
// 		} else {
// 			removeData(user, PUBLIC_DATA_NODE, key);
// 		} // end of if (subnode != null) else
// 	}

//   /**
//    * <code>removeSubnode</code> method removes given subnode with all subnodes
//    * in this node and all data stored in this node and in all subnodes.
//    * Effectively it removes entire repository tree starting from given node.
//    *
//    * @param user a <code>String</code> value of user ID for which data must be
//    * stored. User ID consists of user name and domain name.
//    * @param subnode a <code>String</code> value is a node path to subnode which
//    * has to be removed. Node path has the same form as directory path on file
//    * system: <pre>/root/subnode1/subnode2</pre>.
//    * @exception UserNotFoundException if user id hasn't been found in reository.
//    */
//   public void removePublicSubnode(String user, String subnode)
//     throws UserNotFoundException {
// 		if (subnode != null) {
// 			removeSubnode(user, PUBLIC_DATA_NODE + "/" + subnode);
// 		}
// 	}

} // XMLRepository
