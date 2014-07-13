import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;


public class SVNreadTest {

	@Test
	public void testRead() {
		Map<String,String> result = new HashMap<String,String>();
		
		SVNread repo = new SVNread();
		
		result = repo.read(/* user */ "anonymous", /* password*/ "anonymous", 
				/* url */  "http://svn.apache.org/repos/asf/subversion/trunk", 
				/* file */ "README", /* revision */ "HEAD");
		
		if (result.get("returnResult").length() > 1) fail("Return Result < 0");
		
		String revision = result.get("revision");
		if (revision.isEmpty()) fail("revision empty");
		
		assertTrue("revision not correct: ", Long.parseLong(revision) >= 0);
	}
}
