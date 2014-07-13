import com.hp.oo.sdk.content.annotations.Action;
import com.hp.oo.sdk.content.annotations.Output;
import com.hp.oo.sdk.content.annotations.Param;
import com.hp.oo.sdk.content.annotations.Response;
import com.hp.oo.sdk.content.constants.OutputNames;
import com.hp.oo.sdk.content.constants.ResponseNames;
import com.hp.oo.sdk.content.plugin.ActionMetadata.MatchType;
import com.hp.oo.sdk.content.plugin.ActionMetadata.ResponseType;

import org.tmatesoft.svn.core.SVNAuthenticationException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/** 
 * this class reads a config file from svn and passes it for
 * further use in HP Operations Orchestration
 */

public class SVNread {
	
	@Action(name="get config from subversion",
			description="Reads a file from a subversion repository "+
					"and makes it available for further processing.\n" +
					"Primary purpose of this step is read configurations " +
					"from a svn repository and make use of them " +
					"(especialy with HP CSA).\n" +
					"\nInputs:\n" +
					"repository: url of the repository" +
					"file: name of the file to fetch" +
					"revision: input type is integer or \"HEAD\"; anything that can not be read defaults to HEAD\n" +
					"\nOutputs:\n" +
					"content: file content\n" +
					"revision: the revision read (might differ from input revision, i.e. if input revision was -1 for HEAD)" +
					"\n\n\n\nSource code is available at https://github.com/mknoefel/hpoo-svnread",
			outputs = {
				@Output("content"),
				@Output("revision"),
				@Output("attributes"),
                @Output(OutputNames.RETURN_RESULT),
                @Output("resultMessage")
				},
			responses = {
				@Response(text = ResponseNames.SUCCESS, field = OutputNames.RETURN_RESULT, value = "0", matchType = MatchType.COMPARE_GREATER_OR_EQUAL, responseType = ResponseType.RESOLVED),
				@Response(text = ResponseNames.FAILURE, field = OutputNames.RETURN_RESULT, value = "0", matchType = MatchType.COMPARE_LESS, responseType = ResponseType.ERROR)
    		})
	public Map<String, String> read(
			@Param(value="username") String username,
			@Param(value="password", encrypted=true) String password,
			@Param(value="repository", required=true) String url,
			@Param(value="file") String file,
			@Param(value="revision") String Revision)
	{
		Map<String, String> resultMap = new HashMap<String, String>();
		long revision = -1; /* defaults to HEAD */
		String attributes = ""; /* used to collect all files attributes and pass them to OO */
		
		DAVRepositoryFactory.setup(); /* setup http(s):// support */
		SVNRepositoryFactoryImpl.setup(); /* setup svn:// and svn+ssh:// support */
		FSRepositoryFactory.setup(); /* setup file:// support */
		
		SVNRepository repository = null;
		
		try {
			revision = Long.parseLong(Revision);
		} catch (Exception e) { 
			revision = -1; /* defaults to HEAD */
		}
				
		try {
			repository = SVNRepositoryFactory.create(SVNURL.parseURIEncoded(url));
			
			ISVNAuthenticationManager authManager = SVNWCUtil.createDefaultAuthenticationManager(username, password);
			repository.setAuthenticationManager(authManager);
			
			/* check that path and file are available */
			SVNNodeKind nodeKind = repository.checkPath(file, revision);
			
			if (nodeKind == SVNNodeKind.NONE ) {
				resultMap.put("resultMessage", file+": There is no entry.");
				resultMap.put(OutputNames.RETURN_RESULT, String.valueOf(-1));
				return resultMap;
			} else if (nodeKind == SVNNodeKind.DIR ) {
				resultMap.put("resultMessage", file+": The entry is a directory while a file was expected.");
				resultMap.put(OutputNames.RETURN_RESULT, String.valueOf(-1));
				return resultMap;
			} else if (nodeKind == SVNNodeKind.UNKNOWN ) {
				resultMap.put("resultMessage", file+": The entry is unknown.");
				resultMap.put(OutputNames.RETURN_RESULT, String.valueOf(-1));
				return resultMap;
			}
			/* at this point the node kind is FILE */
			
			/* next we read the file properties */
			SVNProperties fileProperties = new SVNProperties();
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			repository.getFile(file, revision, fileProperties, baos);
			
			/* if file is not of type text we will stop here */
			String mimeType = fileProperties.getStringValue(SVNProperty.MIME_TYPE);
			if (!SVNProperty.isTextMimeType(mimeType)) {
				resultMap.put("resultMessage", "not a text file");
				resultMap.put(OutputNames.RETURN_RESULT, String.valueOf(-2));
				return resultMap;
			}
			
			/* create the attributes-variable */
			attributes = "{";
			boolean isFirst = true; /* needed to set comma's in json output-file */
			for (Entry<String, SVNPropertyValue> entry: fileProperties.asMap().entrySet()) {
				if (!isFirst) attributes += ",";
				isFirst = false;
				attributes = attributes + "\n  \"" +entry.getKey() + "\" : \""
						+ entry.getValue().toString() + "\"";
			}
			attributes += "\n}";
			
			resultMap.put("resultMessage", "content available");
			resultMap.put(OutputNames.RETURN_RESULT, String.valueOf(0));
			resultMap.put("revision", fileProperties.getStringValue(SVNProperty.REVISION));
			resultMap.put("content", baos.toString());
			resultMap.put("attributes", attributes);
		} catch (SVNAuthenticationException e) {
			resultMap.put("resultMessage", "wrong authentication");
			resultMap.put(OutputNames.RETURN_RESULT, String.valueOf(-3));

		} catch (SVNException e) {
			resultMap.put("resultMessage", "error while creating an SVNRepository for the location '"
                            + url + "': " + e.getMessage());
			resultMap.put(OutputNames.RETURN_RESULT, String.valueOf(-3));

		} catch (Exception e) {
			resultMap.put("resultMessage", "something is wrong: "+e.getMessage());
			resultMap.put(OutputNames.RETURN_RESULT, String.valueOf(-3));
		}
		
		return resultMap;
	}
}
