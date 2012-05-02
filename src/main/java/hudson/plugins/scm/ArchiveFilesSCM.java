package hudson.plugins.scm;

import static hudson.FilePath.TarCompression.GZIP;
import static hudson.FilePath.TarCompression.NONE;
import static java.util.logging.Level.ALL;
import static java.util.logging.Level.INFO;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.ProxyConfiguration;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.scm.ChangeLogParser;
import hudson.scm.NullChangeLogParser;
import hudson.scm.PollingResult;
import hudson.scm.SCMDescriptor;
import hudson.scm.SCMRevisionState;
import hudson.scm.SCM;
import hudson.util.FormValidation;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import javax.servlet.ServletException;

import net.sf.json.JSONObject;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.input.CountingInputStream;
import org.jvnet.robust_http_client.RetryableHttpStream;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

//import sun.net.www.protocol.http.AuthCacheImpl;
//import sun.net.www.protocol.http.AuthCacheValue;

/**
 * The Class ArchiveFilesSCM. ArchiveFilesSCM plugin for Jenkins supports
 * checkout, extraction and also pooling of archives files. The current version
 * supports extraction of zip,tar,gz,jar,war,ear files. The type detection of
 * archive file is based on file name (i.e URL must end with
 * zip,tar,tar.gz,jar,war,ear). Polling is done by checking the last-modified
 * header returned when connecting to a URL. If the type is unknown the plugin
 * will simply copy the file to workspace. Features
 * 
 * Supports Basic Authentication Supports Connection through Proxy Supports
 * running on slave Supports http:// and file:// portocols e.g - URL can be
 * http:
 * //www.apache.org/dyn/closer.cgi/maven/binaries/apache-maven-3.0.4-bin.tar.gz
 * file:///C:/Arjun/felix.jar (On Windows) file:///home/arjun/felix.jar (On
 * Unix/Linux)
 */
@SuppressWarnings("restriction")
public class ArchiveFilesSCM extends hudson.scm.SCM {

	/** The urls. */
	private final List<URLTuple> urls = new ArrayList<URLTuple>();

	/** The clear workspace. */
	private final boolean clearWorkspace;

	/** The Constant LOGGER. */
	private static final Logger LOGGER = Logger.getLogger(ArchiveFilesSCM.class
			.getName());

	/**
	 * Instantiates a new archive files scm.
	 * 
	 * @param urls
	 *            the yourls - urls
	 * @param clear
	 *            the clear - clear workspace flag
	 */
	@DataBoundConstructor
	public ArchiveFilesSCM(String[] yourls, boolean clear) {
		this(yourls, clear, null, null);
	}

	/**
	 * Instantiates a new archive files scm.
	 * 
	 * @param u
	 *            the yourls - urls
	 * @param clear
	 *            the clear -clear workspace flag
	 * @param username
	 *            the username - username
	 * @param password
	 *            the password - password
	 */
	public ArchiveFilesSCM(String[] yourls, boolean clear, String[] username,
			String[] password) {
		LOGGER.log(ALL, "ArchiveFilesSCM() Enter >>>");
		for (int i = 0; i < yourls.length; i++) {
			urls.add(new URLTuple(yourls[i], username[i], password[i]));
		}
		this.clearWorkspace = clear;
		LOGGER.log(ALL, "ArchiveFilesSCM() Exit >>>");
	}

	/**
	 * Checks if is clear workspace.
	 * 
	 * @return true, if is clear workspace
	 */
	public boolean isClearWorkspace() {
		return clearWorkspace;
	}

	/**
	 * Gets the urls.
	 * 
	 * @return the urls
	 */
	public URLTuple[] getUrls() {
		return urls.toArray(new URLTuple[urls.size()]);
	}

	/**
	 * This method downloads the file and also extracts it.
	 * 
	 * @see hudson.scm.SCM#checkout(hudson.model.AbstractBuild, hudson.Launcher,
	 *      hudson.FilePath, hudson.model.BuildListener, java.io.File)
	 */
	@Override
	public boolean checkout(AbstractBuild<?, ?> build, Launcher launcher,
			FilePath workspace, BuildListener listener, File changelogFile)
			throws IOException, InterruptedException {
		LOGGER.log(ALL, "checkout() Enter >>>");

		if (clearWorkspace) {
			workspace.deleteContents();
			listener.getLogger().println("Cleared workspace");
		}
		long start = System.currentTimeMillis();
		LastModifiedDateAction action = new LastModifiedDateAction(build);
		Hudson h = Hudson.getInstance(); // this code might run on slaves

		ProxyConfiguration proxyConfiguration = h != null ? h.proxy : null;
		String proxyUserName = null;
		String proxyPassword = null;
		if (proxyConfiguration != null
				&& proxyConfiguration.getUserName() != null
				&& proxyConfiguration.getUserName().trim().length() > 0) {
			proxyUserName = proxyConfiguration.getUserName();
			proxyPassword = proxyConfiguration.getPassword();
		}

		for (URLTuple tuple : urls) {
			String urlString = tuple.getUrlString();
			InputStream is = null;
			try {
				URLConnection connection = null;
				listener.getLogger().println("File URL : " + urlString);
				URL url = new URL(urlString);

				if (proxyConfiguration == null) {
					listener.getLogger().println("Proxy is not configured");
					connection = url.openConnection();
				} else {
					listener.getLogger().println(
							"Proxy is configured"
									+ "\n"
									+ ProxyConfiguration.getXmlFile()
											.asString());
					// Proxy should be used when it is configured in Jenkins
					// global configuration
					connection = url.openConnection(proxyConfiguration
							.createProxy());
					// setting authentication parameters for proxy.
					// Authenticator class is not used here because of two
					// reasons. The actual URL to download file may be secured
					// and we can not set two Authenticators at
					// the same time using Authenticator.setDefault and the
					// second reason is username/password caching
					// bug in JDK.
					// (say if user gives incorrect username/password and when
					// validation is performed, that
					// username/password cached and even
					// if user gives correct password next time
					// java.net.URLConnection uses cached password only.
					// User name and password is not cached when it is set as
					// request parameter.)
					// http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6626700
					if (proxyUserName != null) {
						listener.getLogger()
								.println(
										"User Name and Password is configured to connect through proxy");
						connection.setRequestProperty(
								"Proxy-Authorization",
								"Basic "
										+ new String(Base64
												.encodeBase64((proxyUserName
														+ ":" + proxyPassword)
														.getBytes())));
					}
				}
				// Do not use cached file
				connection.setUseCaches(false);

				// Saving last modified time stamp for later use while polling
				// for source code change
				action.setLastModified(urlString, connection.getLastModified());

				long sourceLastUpdatedTimestamp = connection.getLastModified();
				File f = new File(url.getPath());
				String fileName = f.getName();
				// creating a timestamp file which will be used to see if source
				// file is updated since last download
				FilePath timestamp = workspace.child("." + fileName
						+ "-timestamp");

				if (!workspace.exists()) {
					workspace.mkdirs();
				}
				if (timestamp.exists()
						&& sourceLastUpdatedTimestamp == timestamp
								.lastModified()) {
					listener.getLogger().println("File is up to date");
				} else {
					// for HTTP downloads, enable automatic retry for added
					// resilience
					is = new CountingInputStream(url.getProtocol().equals(
							"http") ? new RetryableHttpStream(url)
							: connection.getInputStream());

					if (url.toExternalForm().endsWith(".zip")
							|| url.toExternalForm().endsWith(".jar")
							|| url.toExternalForm().endsWith(".war")
							|| url.toExternalForm().endsWith(".ear")) {
						listener.getLogger().println(
								"Compression type is zip/jar/war");
						workspace.unzipFrom(is);
					} else if (url.toExternalForm().endsWith(".gz")) {
						listener.getLogger().println("Compression type is gz");
						workspace.untarFrom(is, GZIP);
					} else if (url.toExternalForm().endsWith(".tar")) {
						listener.getLogger().println("Compression type is tar");
						workspace.untarFrom(is, NONE);
					} else {
						listener.getLogger()
								.println(
										"Compression type unknow. Hence directly downloding the file");
						workspace.child(fileName).copyFrom(is);
					}
					listener.getLogger().println(
							"Downloaded " + urlString + " to "
									+ workspace.toURI());
					// update the last modified timestamp of timestamp file
					timestamp.touch(sourceLastUpdatedTimestamp);
				}

			} catch (Exception e) {
				listener.error("Unable to copy " + urlString + "\n");
				e.printStackTrace(listener.getLogger());
				LOGGER.log(ALL, " checkout() Exit >>>");
				return false;
			} finally {
				if (is != null)
					is.close();
			}

			this.createEmptyChangeLog(changelogFile, listener, "log");
		}
		// Adding LastModificationDateAction to build for later use while
		// pooling. This is optimized code as
		// calcRevisionsFromBuild method will not be invoked
		build.addAction(action);
		listener.getLogger().println(
				"Total time taken to download files in millis: "
						+ (System.currentTimeMillis() - start));

		LOGGER.log(ALL, " checkout() Exit >>>");
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see hudson.scm.SCM#createChangeLogParser()
	 */
	@Override
	public ChangeLogParser createChangeLogParser() {
		return new NullChangeLogParser();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see hudson.scm.SCM#requiresWorkspaceForPolling()
	 */
	@Override
	public boolean requiresWorkspaceForPolling() {
		// workspace is not used for pooling
		return false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * hudson.scm.SCM#compareRemoteRevisionWith(hudson.model.AbstractProject,
	 * hudson.Launcher, hudson.FilePath, hudson.model.TaskListener,
	 * hudson.scm.SCMRevisionState)
	 */
	@Override
	protected PollingResult compareRemoteRevisionWith(
			AbstractProject<?, ?> project, Launcher launcher,
			FilePath workspace, TaskListener listener, SCMRevisionState baseline)
			throws IOException, InterruptedException {
		LOGGER.log(ALL, "compareRemoteRevisionWith() Enter >>>");
		PollingResult pollingResult = PollingResult.NO_CHANGES;

		AbstractBuild<?, ?> lastBuild = project.getLastBuild();
		// When no previous build exits and this is the first build we got to
		// build
		if (lastBuild != null) {
			listener.getLogger().println(
					"[poll] Last Build : #" + lastBuild.getNumber());
		} else {
			listener.getLogger()
					.println(
							"[poll] No previous build, so forcing an initial build.\ncompareRemoteRevisionWith() Exit >>>");
			LOGGER.log(ALL, "compareRemoteRevisionWith() Exit >>>");
			return PollingResult.BUILD_NOW;
		}
		// Rebuild if working directory does not exits
		if (workspace != null && !workspace.exists()) {
			listener.getLogger()
					.println(
							"Rebuilding as working directory doesnot exits.\ncompareRemoteRevisionWith() Exit >>>");
			LOGGER.log(ALL, "compareRemoteRevisionWith() Exit >>>");
			return PollingResult.BUILD_NOW;
		}
		LastModifiedDateAction action = lastBuild
				.getAction(LastModifiedDateAction.class);
		if (action == null) {
			listener.getLogger()
					.println(
							"There are significant changes.\ncompareRemoteRevisionWith() Exit >>>");
			LOGGER.log(ALL, "compareRemoteRevisionWith() Exit >>>");
			return PollingResult.SIGNIFICANT;
		}
		for (URLTuple tuple : urls) {
			String urlString = tuple.getUrlString();
			try {
				URL url = new URL(urlString);
				URLConnection conn = url.openConnection();
				conn.setUseCaches(false);
				long lastMod = conn.getLastModified();
				long lastBuildMod = action.getLastModified(urlString);
				if (lastBuildMod != lastMod) {
					listener.getLogger().println(
							"Found change: " + urlString + " modified "
									+ new Date(lastMod)
									+ " previous modification was "
									+ new Date(lastBuildMod));
					pollingResult = PollingResult.SIGNIFICANT;
					break;
				}
			} catch (Exception e) {
				listener.error("Unable to check " + urlString + "\n"
						+ e.getMessage());
				e.printStackTrace(listener.getLogger());
			}
		}
		LOGGER.log(ALL, "compareRemoteRevisionWith() Exit >>>");
		return pollingResult;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see hudson.scm.SCM#calcRevisionsFromBuild(hudson.model.AbstractBuild,
	 * hudson.Launcher, hudson.model.TaskListener)
	 */
	@Override
	public SCMRevisionState calcRevisionsFromBuild(AbstractBuild<?, ?> arg0,
			Launcher arg1, TaskListener arg2) throws IOException,
			InterruptedException {
		return SCMRevisionState.NONE;
	}

	/**
	 * The Class ArchiveFilesSCMDescriptorImpl.
	 */
	@Extension
	public static final class ArchiveFilesSCMDescriptorImpl extends
			SCMDescriptor<ArchiveFilesSCM> {

		/**
		 * Instantiates a new archive files scm descriptor impl.
		 */
		public ArchiveFilesSCMDescriptorImpl() {
			super(ArchiveFilesSCM.class, null);
			load();
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see hudson.model.Descriptor#getDisplayName()
		 */
		public String getDisplayName() {
			return "Archive Files SCM";
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * hudson.model.Descriptor#newInstance(org.kohsuke.stapler.StaplerRequest
		 * , net.sf.json.JSONObject)
		 */
		@Override
		public SCM newInstance(StaplerRequest req, JSONObject formData)
				throws FormException {
			LOGGER.log(ALL, "newInstance() Enter >>>");
			String urls[] = req.getParameterValues("archive_files_scm_url");
			String usernames[] = req
					.getParameterValues("archive_files_scm_username");
			LOGGER.log(INFO, "URL :" + Arrays.deepToString(urls)
					+ "\n User Name :" + Arrays.deepToString(usernames));
			LOGGER.log(ALL, "newInstance() Exit >>>");
			return new ArchiveFilesSCM(urls,
					req.getParameter("archive_files_scm_clear") != null,
					usernames,
					req.getParameterValues("archive_files_scm_password"));
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * hudson.model.Descriptor#configure(org.kohsuke.stapler.StaplerRequest,
		 * net.sf.json.JSONObject)
		 */
		@Override
		public boolean configure(StaplerRequest req, JSONObject formData)
				throws FormException {
			return true;
		}

		/**
		 * Do required check.
		 * 
		 * @param value
		 *            the value
		 * @return the form validation
		 * @throws ServletException
		 *             the servlet exception
		 */
		public FormValidation doRequiredCheck(@QueryParameter final String value)
				throws ServletException {
			LOGGER.log(ALL, "doRequiredCheck() Enter >>>");
			LOGGER.log(INFO, "value :" + value);
			LOGGER.log(ALL, "doRequiredCheck() Exit >>>");
			return FormValidation.validateRequired(value);
		}
	}

	/**
	 * The Class URLTuple.
	 */
	public static final class URLTuple {

		/** The url string. */
		private final String urlString;

		/** The username. */
		private final String username;

		/** The password. */
		private final String password;

		/**
		 * Instantiates a new uRL tuple.
		 * 
		 * @param urlString
		 *            the url string
		 * @param username
		 *            the username
		 * @param password
		 *            the password
		 */
		public URLTuple(String urlString, String username, String password) {
			LOGGER.log(ALL, "URLTuple() Enter >>>");
			this.urlString = urlString;
			// In the url is not secured initialize user name and password with
			// empty strings
			if (username == null || username.trim().length() == 0) {
				this.username = "";
				this.password = "";
			} else {
				this.username = username;
				this.password = password;
				this.setAuthenticator();
			}
			LOGGER.log(ALL, "URLTuple() Exit >>>");
		}

		/**
		 * Gets the url string.
		 * 
		 * @return the url string
		 */
		public String getUrlString() {
			return urlString;
		}

		/**
		 * Gets the username.
		 * 
		 * @return the username
		 */
		public String getUsername() {
			return username;
		}

		/**
		 * Gets the password.
		 * 
		 * @return the password
		 */
		public String getPassword() {
			return password;
		}

		/**
		 * The Class SecuredResourceAuthenticator.
		 */
		private class SecuredResourceAuthenticator extends Authenticator {

			/*
			 * (non-Javadoc)
			 * 
			 * @see java.net.Authenticator#getPasswordAuthentication()
			 */
			public PasswordAuthentication getPasswordAuthentication() {
				return new PasswordAuthentication(username,
						password.toCharArray());
			}
		}

		/**
		 * Sets the authenticator.
		 */
		public void setAuthenticator() {
			// clearing cache - we require to clear cache because of JDK bug
			// AuthCacheValue.setAuthCache(new AuthCacheImpl());
			Authenticator.setDefault(new SecuredResourceAuthenticator());
		}
	}
}
