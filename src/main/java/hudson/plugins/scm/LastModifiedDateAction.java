package hudson.plugins.scm;

import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import hudson.model.AbstractBuild;
import hudson.model.AbstractModelObject;
import hudson.model.Action;

/**
 * The Class LastModifiedDateAction.
 */
public class LastModifiedDateAction extends AbstractModelObject implements Action {

	/** The Constant serialVersionUID. */
	private static final long serialVersionUID = 1L;

	/** The last modified. */
	private HashMap<String, Long> lastModified = new HashMap<String, Long>();

	/** The build. */
	private final AbstractBuild<?, ?> build;

	/**
	 * Instantiates a new last modified date action.
	 * 
	 * @param build
	 *            the build
	 */
	protected LastModifiedDateAction(AbstractBuild<?, ?> build) {
		this.build = build;
	}

	/**
	 * Gets the builds the.
	 * 
	 * @return the builds the
	 */
	public AbstractBuild<?, ?> getBuild() {
		return build;
	}

	/**
	 * Gets the last modified.
	 * 
	 * @param url
	 *            the url
	 * @return the last modified
	 */
	public long getLastModified(String url) {
		Long l = lastModified.get(url);
		if (l == null)
			return 0;
		return l;
	}

	/**
	 * Sets the last modified.
	 * 
	 * @param url
	 *            the url
	 * @param lastModifiedTimeStamp
	 *            the last modified time stamp
	 */
	public void setLastModified(String url, long lastModifiedTimeStamp) {
		lastModified.put(url, lastModifiedTimeStamp);
	}

	/**
	 * Gets the url dates.
	 * 
	 * @return the url dates
	 */
	public Map<String, String> getUrlDates() {
		Map<String, String> ret = new HashMap<String, String>();
		for (Map.Entry<String, Long> e : lastModified.entrySet()) {
			long sinceEpoch = e.getValue();
			if (sinceEpoch == 0) {
				ret.put(e.getKey(), "Last-modified not supported");
			} else {
				ret.put(e.getKey(), DateFormat.getInstance().format(new Date(sinceEpoch)));
			}
		}
		return ret;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see hudson.model.ModelObject#getDisplayName()
	 */
	public String getDisplayName() {
		return "URL Modification Dates";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see hudson.model.Action#getIconFileName()
	 */
	public String getIconFileName() {
		return "save.gif";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see hudson.search.SearchItem#getSearchUrl()
	 */
	public String getSearchUrl() {
		return getUrlName();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see hudson.model.Action#getUrlName()
	 */
	public String getUrlName() {
		return "urlDates";
	}

	/**
	 * Do index.
	 * 
	 * @param req
	 *            the req
	 * @param rsp
	 *            the rsp
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 * @throws ServletException
	 *             the servlet exception
	 */
	public void doIndex(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
		req.getView(this, chooseAction()).forward(req, rsp);
	}

	/**
	 * Choose action.
	 * 
	 * @return the string
	 */
	protected String chooseAction() {
		return "tagForm.jelly";
	}

}
