/**
 *
 * Copyright (c) 2014, the Railo Company Ltd. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either 
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public 
 * License along with this library.  If not, see <http://www.gnu.org/licenses/>.
 * 
 **/
package org.lucee.extension.resource.smb;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.loader.util.Util;

import org.codelibs.jcifs.smb.CIFSContext;
import org.codelibs.jcifs.smb.CIFSException;
import org.codelibs.jcifs.smb.CloseableIterator;
import org.codelibs.jcifs.smb.SmbConstants;
import org.codelibs.jcifs.smb.SmbResource;
import org.codelibs.jcifs.smb.impl.NtlmPasswordAuthenticator;
import org.codelibs.jcifs.smb.impl.SmbFile;
import lucee.commons.io.res.Resource;
import lucee.commons.io.res.ResourceProvider;
import lucee.commons.io.res.filter.ResourceFilter;
import lucee.commons.io.res.filter.ResourceNameFilter;
import org.lucee.extension.resource.ResourceSupport;

public class SMBResource extends ResourceSupport implements Resource {

	private static final long serialVersionUID = 1L;

	private SMBResourceProvider provider;
	private String path;
	private NtlmPasswordAuthenticator auth;
	private SmbResource _smbFile;
	private SmbResource _smbDir;

	private SMBResource(CFMLEngine engine, SMBResourceProvider provider) {
		super(engine);
		this.provider = provider;
	}

	public SMBResource(CFMLEngine engine, SMBResourceProvider provider, String path) {
		this(engine, provider);
		_init(_stripAuth(path), _extractAuth(path));
	}

	public SMBResource(CFMLEngine engine, SMBResourceProvider provider, String path, NtlmPasswordAuthenticator auth) {
		this(engine, provider);
		_init(path, auth);
	}

	public SMBResource(CFMLEngine engine, SMBResourceProvider provider, String parent, String child) {
		this(engine, provider);
		_init(engine.getResourceUtil().merge(_stripAuth(parent), child), _extractAuth(parent));
	}

	public SMBResource(CFMLEngine engine, SMBResourceProvider provider, String parent, String child, NtlmPasswordAuthenticator auth) {
		this(engine, provider);
		_init(engine.getResourceUtil().merge(_stripAuth(parent), child), auth);
	}

	private void _init(String path, NtlmPasswordAuthenticator auth) {
		// String[] pathName=CFMLEngineFactory.getInstance().getResourceUtil().translatePathName(path);
		this.path = _stripScheme(path);
		this.auth = auth;

	}

	private String _stripScheme(String path) {
		return path.replace(_scheme(), "/");
	}

	private String _userInfo(String path) {

		try {
			// use http scheme just so we can parse the url and get the user info out
			String schemeless = _stripScheme(path);
			schemeless = schemeless.replaceFirst("^/", "");
			String result = new URL("http://".concat(schemeless)).getUserInfo();
			return SMBResourceProvider.unencryptUserInfo(result);
		}
		catch (MalformedURLException e) {
			return "";
		}
	}
	
	private static String _userInfo(NtlmPasswordAuthenticator auth, boolean addAtSign) {
		String result = "";
		CFMLEngineFactory.getInstance().getStringUtil();
		if (auth != null) {
			if (!CFMLEngineFactory.getInstance().getStringUtil().isEmpty(auth.getUserDomain())) {
				result += auth.getUserDomain() + ";";
			}
			if (!CFMLEngineFactory.getInstance().getStringUtil().isEmpty(auth.getUsername())) {
				result += auth.getUsername() + ":";
			}
			if (!CFMLEngineFactory.getInstance().getStringUtil().isEmpty(auth.getPassword())) {
				result += auth.getPassword();
			}
			if (addAtSign && !CFMLEngineFactory.getInstance().getStringUtil().isEmpty(result)) {
				result += "@";
			}
		}
		return result;
	}

	private NtlmPasswordAuthenticator _extractAuth(String path) {
		String userInfo = _userInfo(path);
		return _parseUserInfo(userInfo);
	}

	private NtlmPasswordAuthenticator _parseUserInfo(String userInfo) {
		if (userInfo == null || userInfo.isEmpty()) {
			return new NtlmPasswordAuthenticator();
		}
		// Format: domain;username:password or username:password
		String domain = null;
		String username = null;
		String password = null;

		int semiIdx = userInfo.indexOf(';');
		if (semiIdx >= 0) {
			domain = userInfo.substring(0, semiIdx);
			userInfo = userInfo.substring(semiIdx + 1);
		}

		int colonIdx = userInfo.indexOf(':');
		if (colonIdx >= 0) {
			username = userInfo.substring(0, colonIdx);
			password = userInfo.substring(colonIdx + 1);
		} else {
			username = userInfo;
		}

		return new NtlmPasswordAuthenticator(domain, username, password);
	}

	private String _stripAuth(String path) {
		return _calculatePath(path).replaceFirst(_scheme().concat("[^/]*@"), "");
	}

	private SmbResource _file() {
		return _file(false);
	}

	private SmbResource _file(boolean expectDirectory) {
		String _path = _calculatePath(getInnerPath());
		SmbResource result;
		if (expectDirectory) {
			if (!_path.endsWith("/")) _path += "/";
			if (_smbDir == null) {
				_smbDir = provider.getFile(_path, auth);
			}
			result = _smbDir;
		}
		else {
			if (_smbFile == null) {
				_smbFile = provider.getFile(_path, auth);
			}
			result = _smbFile;
		}
		return result;
	}

	private String _calculatePath(String path) {
		return _calculatePath(path, null);
	}

	private String _calculatePath(String path, NtlmPasswordAuthenticator auth) {

		if (!path.startsWith(_scheme())) {
			if (path.startsWith("/") || path.startsWith("\\")) {
				path = path.substring(1);
			}
			if (auth != null) {
				path = SMBResourceProvider.encryptUserInfo(_userInfo(auth, false)).concat("@").concat(path);
			}
			path = _scheme().concat(path);
		}
		return path;
	}

	private String _scheme() {
		return provider.getScheme().concat("://");

	}

	@Override
	public boolean isReadable() {
		SmbResource file = _file();
		try {
			return file != null && file.canRead();
		}
		catch (CIFSException e) {
			return false;
		}
	}

	@Override
	public boolean isWriteable() {
		SmbResource file = _file();
		if (file == null) return false;
		try {
			if (file.canWrite()) return true;
		}
		catch (CIFSException e1) {
			return false;
		}

		try {
			if (file.getType() == SmbConstants.TYPE_SHARE) {
				// canWrite() doesn't work on shares. always returns false even if you can truly write, test this by
				// opening a file on the share

				SmbResource testFile = _getTempFile(file, auth);
				if (testFile == null) return false;
				if (testFile.canWrite()) return true;

				OutputStream os = null;
				try {
					os = testFile.openOutputStream();
				}
				catch (IOException e) {
					return false;
				}
				finally {
					if (os != null) engine.getIOUtil().closeSilent(os);
					testFile.delete();
				}
				return true;

			}
			return file.canWrite();
		}
		catch (CIFSException e) {
			return false;
		}
	}

	private SmbResource _getTempFile(SmbResource directory, NtlmPasswordAuthenticator auth) throws CIFSException {
		if (!directory.isDirectory()) return null;
		Random r = new Random();

		SmbResource result = provider.getFile(directory.getLocator().getURL().toString() + "/write-test-file.unknown." + r.nextInt(), auth);
		if (result.exists()) return _getTempFile(directory, auth); // try again
		return result;
	}

	@Override
	public void remove(boolean alsoRemoveChildren) throws IOException {
		if (alsoRemoveChildren) engine.getResourceUtil().removeChildren(this);
		_delete();
	}

	private void _delete() throws IOException {
		provider.lock(this);
		try {
			SmbResource file = _file();
			if (file == null) throw new IOException("Can't delete [" + getPath() + "], SMB path is invalid or inaccessible");
			if (file.isDirectory()) {
				file = _file(true);
			}
			file.delete();
		}
		catch (CIFSException e) {
			throw new IOException(e);// for cfcatch type="java.io.IOException"
		}
		finally {
			provider.unlock(this);
		}
	}

	@Override
	public boolean exists() {
		SmbResource file = _file();
		try {
			return file != null && file.exists();
		}
		catch (CIFSException e) {
			return false;
		}
	}

	@Override
	public String getName() {
		SmbResource file = _file();
		if (file == null) return "";
		return file.getName().replaceFirst("/$", ""); // remote trailing slash for directories
	}

	@Override
	public String getParent() {
		// SmbFile's getParent function seems to return just smb:// no matter what, implement custom
		// getParent Function()
		String path = getPath().replaceFirst("[\\\\/]+$", "");

		int location = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
		if (location == -1 || location == 0) return "";
		return path.substring(0, location);
	}

	@Override
	public Resource getParentResource() {
		String p = getParent();
		if (p == null) return null;
		return new SMBResource(engine, provider, _stripAuth(p), auth);
	}

	@Override
	public Resource getRealResource(String realpath) {
		realpath = engine.getResourceUtil().merge(getInnerPath() + "/", realpath);

		if (realpath.startsWith("../")) return null;
		return new SMBResource(engine, provider, _calculatePath(realpath, auth), auth);
	}

	private String getInnerPath() {
		if (path == null) return "/";
		return path;
	}

	@Override
	public String getPath() {
		return _calculatePath(path, auth);
	}

	@Override
	public boolean isAbsolute() {
		return _file() != null;
	}

	@Override
	public boolean isDirectory() {
		SmbResource file = _file();
		try {
			return file != null && _file().isDirectory();
		}
		catch (CIFSException e) {
			return false;
		}
	}

	@Override
	public boolean isFile() {
		SmbResource file = _file();
		try {
			return file != null && file.isFile();
		}
		catch (CIFSException e) {
			return false;
		}
	}

	@Override
	public boolean isHidden() {
		return _isFlagSet(_file(), SmbConstants.ATTR_HIDDEN);
	}

	@Override
	public boolean isArchive() {
		return _isFlagSet(_file(), SmbConstants.ATTR_ARCHIVE);
	}

	@Override
	public boolean isSystem() {
		return _isFlagSet(_file(), SmbConstants.ATTR_SYSTEM);
	}

	private boolean _isFlagSet(SmbResource file, int flag) {
		if (file == null) return false;
		try {
			return (file.getAttributes() & flag) == flag;
		}
		catch (CIFSException e) {
			return false;
		}
	}

	@Override
	public long lastModified() {
		SmbResource file = _file();
		if (file == null) return 0;
		try {
			return file.lastModified();
		}
		catch (CIFSException e) {
			return 0;
		}

	}

	@Override
	public long length() {
		SmbResource file = _file();
		if (file == null) return 0;
		try {
			return file.length();
		}
		catch (CIFSException e) {
			return 0;
		}
	}

	@Override
	public Resource[] listResources(ResourceNameFilter nameFilter, ResourceFilter filter) {
		if (isFile()) return null;
		try {
			SmbResource dir = _file(true);
			List<Resource> list = new ArrayList<Resource>();
			try (CloseableIterator<SmbResource> iter = dir.children()) {
				while (iter.hasNext()) {
					SmbResource file = iter.next();
					SMBResource res = new SMBResource(engine, provider, file.getLocator().getURL().toString(), auth);

					// apply filters
					if (nameFilter != null && !nameFilter.accept(this, res.getName())) continue;
					if (filter != null && !filter.accept(res)) continue;

					list.add(res);
				}
			}
			return list.toArray(new Resource[list.size()]);
		}
		catch (CIFSException e) {
			return new Resource[0];
		}
	}

	@Override
	public boolean setLastModified(long time) {
		SmbResource file = _file();
		if (file == null) return false;
		try {
			provider.lock(this);
			file.setLastModified(time);
		}
		catch (CIFSException e) {
			return false;
		}
		catch (IOException e) {
			return false;
		}
		finally {
			provider.unlock(this);
		}
		return true;
	}

	@Override
	public boolean setWritable(boolean writable) {
		SmbResource file = _file();
		if (file == null) return false;
		try {
			setAttribute((short) SmbConstants.ATTR_READONLY, !writable);
		}
		catch (IOException e1) {
			return false;
		}
		return true;

	}

	@Override
	public boolean setReadable(boolean readable) {
		return setWritable(!readable);
	}

	@Override
	public void createFile(boolean createParentWhenNotExists) throws IOException {
		engine.getResourceUtil().checkCreateFileOK(this, createParentWhenNotExists);
		engine.getIOUtil().copy(new ByteArrayInputStream(new byte[0]), getOutputStream(), true, true);
	}

	@Override
	public void createDirectory(boolean createParentWhenNotExists) throws IOException {
		SmbResource file = _file(true);
		if (file == null) throw new IOException("SMBFile is inaccessible");
		engine.getResourceUtil().checkCreateDirectoryOK(this, createParentWhenNotExists);
		try {
			provider.lock(this);
			file.mkdir();
		}
		catch (CIFSException e) {
			throw new IOException(e); // for cfcatch type="java.io.IOException"
		}
		finally {
			provider.unlock(this);
		}
	}

	@Override
	public InputStream getInputStream() throws IOException {
		try {
			return _file().openInputStream();
		}
		catch (CIFSException e) {
			throw new IOException(e);// for cfcatch type="java.io.IOException"
		}
	}

	@Override
	public OutputStream getOutputStream(boolean append) throws IOException {
		engine.getResourceUtil().checkGetOutputStreamOK(this);
		try {
			provider.lock(this);
			SmbResource file = _file();
			OutputStream os = file.openOutputStream(append);
			return engine.getIOUtil().toBufferedOutputStream(new SMBResourceOutputStream(this, os));
		}
		catch (IOException e) {
			provider.unlock(this);
			throw new IOException(e);// just in case it is a CIFSException too... for cfcatch type="java.io.IOException"
		}
	}

	@Override
	public ResourceProvider getResourceProvider() {
		return provider;
	}

	@Override
	public int getMode() {
		return 0;
	}

	@Override
	public void setMode(int mode) throws IOException {
		// TODO
	}

	@Override
	public void setHidden(boolean value) throws IOException {
		setAttribute((short) SmbConstants.ATTR_HIDDEN, value);
	}

	@Override
	public void setSystem(boolean value) throws IOException {
		setAttribute((short) SmbConstants.ATTR_SYSTEM, value);
	}

	@Override
	public void setArchive(boolean value) throws IOException {
		setAttribute((short) SmbConstants.ATTR_ARCHIVE, value);
	}

	@Override
	public void setAttribute(short attribute, boolean value) throws IOException {
		int newAttribute = _lookupAttribute(attribute);
		SmbResource file = _file();
		if (file == null) throw new IOException("SMB File is not valid");
		try {
			provider.lock(this);
			int atts = file.getAttributes();
			if (value) {
				atts = atts | newAttribute;
			}
			else {
				atts = atts & (~newAttribute);
			}
			file.setAttributes(atts);
		}
		catch (CIFSException e) {
			throw new IOException(e); // for cfcatch type="java.io.IOException"
		}
		finally {
			provider.unlock(this);
		}
	}

	@Override
	public void moveFile(Resource src, Resource dest) throws IOException {
		// If both are SMBResource on same provider, use native rename
		if (src instanceof SMBResource && dest instanceof SMBResource) {
			SMBResource smbSrc = (SMBResource) src;
			SMBResource smbDest = (SMBResource) dest;
			if (smbSrc.provider == smbDest.provider) {
				try {
					smbSrc._file().renameTo(smbDest._file());
					return;
				}
				catch (CIFSException e) {
					throw new IOException(e);
				}
			}
		}
		// Fallback: copy then delete
		if (!dest.exists()) dest.createFile(false);
		Util.copy(src, dest);
		src.remove(false);
	}

	@Override
	public boolean getAttribute(short attribute) {
		try {
			int newAttribute = _lookupAttribute(attribute);
			return (_file().getAttributes() & newAttribute) != 0;
		}
		catch (CIFSException e) {
			return false;
		}

	}

	public SmbResource getSmbFile() {
		return _file();
	}

	private int _lookupAttribute(short attribute) {
		int result = attribute;
		switch (attribute) {
		case Resource.ATTRIBUTE_ARCHIVE:
			result = SmbConstants.ATTR_ARCHIVE;
			break;
		case Resource.ATTRIBUTE_SYSTEM:
			result = SmbConstants.ATTR_SYSTEM;
			break;
		case Resource.ATTRIBUTE_HIDDEN:
			result = SmbConstants.ATTR_HIDDEN;
			break;
		}
		return result;
	}

}