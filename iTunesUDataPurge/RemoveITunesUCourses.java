import java.io.*;

import org.xml.sax.*;
import org.xml.sax.helpers.*;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import java.util.StringTokenizer;
import java.util.Vector;
import java.util.List;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Date;
import java.util.Map;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URLConnection;
import java.net.URL;
import java.net.URLEncoder;

import org.apache.http.client.*;
import org.apache.http.params.*;
import org.apache.http.client.methods.*;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.HttpResponse;
import org.apache.http.HttpEntity;
import org.apache.commons.lang.*;

import java.io.*;;

import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;

// using SAX
public class RemoveITunesUCourses {
	
	// contact info
	static HashMap<String, String> siteDownloadUrl = new HashMap<String, String>();
	
	final static int size=1024;
	
	static final String WS_DELETE_COURSE = "DeleteCourse";
	
    public static void main(String[] args) throws Exception {
		
		
		// site createdOn file
		HashMap<String, String> removeCoursesIds = new HashMap<String, String>();
		String removeCoursesFileName = args[0];
		
		String displayName = "Admin DisplayName";//args[1];
		String emailAddress = "Admin Email Address";//args[2];
		String username = "Admin Username";//args[3];
		String userIdentifier = "Admin ID";//args[4];
		
		File removeCoursesFile = new File(removeCoursesFileName);
		int row = 0;
		try
		{
			BufferedReader bufRdr  = new BufferedReader(new FileReader(removeCoursesFile));
			String line = null;
			//read each line of text file
			int count = 0;
			while((line = bufRdr.readLine()) != null)
			{	
				StringTokenizer st = new StringTokenizer(line,"\t");
				String siteId = "";
				String siteTitle = "";
				// reset count
				count = 0;
				while (st.hasMoreTokens())
				{
					String tokenValue = st.nextToken().trim();
					if (count == 0)
					{
						// this is the site id
						siteTitle = tokenValue;
					}
					else if (count == 1)
					{
						// this is the site id
						siteId = tokenValue;
					}
					count++;
				}
				removeCoursesIds.put(siteId, siteTitle);
				
				row++;
			}
			
			// how many site ids 
			System.out.println("The total size of download site id list = " + removeCoursesIds.size());
			
		}
		catch (FileNotFoundException e)
		{
			System.out.println("FileNotFoundException " + removeCoursesFileName);
		}
		catch (IOException e)
		{
			System.out.println("IOException " + removeCoursesFileName);
		}
		
		if (!removeCoursesIds.isEmpty())
		{
			Hashtable<String, String> t = Utils.getITunesUCreds(true,false, displayName, emailAddress, username, userIdentifier);
			String prefix = t.get("prefix")!=null?(String) t.get("prefix"):"";
			String destination= t.get("destination")!=null?(String) t.get("destination"):"";
    		removeCourses(removeCoursesIds, prefix, destination, displayName, emailAddress, username, userIdentifier);
		}
    	
	}
	
	static public String getCredentialToken(String displayName, String emailAddress, String username, String userIdentifier)
	{
		// get itunesu credential
		Hashtable<String, String> t = Utils.getITunesUCreds(true,false, displayName, emailAddress, username, userIdentifier);
		return t.get("token")!=null?(String) t.get("token"):"";
	}
	
	static public void removeCourses(HashMap<String, String> siteIds, String prefix, String destination, String displayName, String emailAddress, String username, String userIdentifier)
    {  
    	HashMap<String, String> rv = new HashMap<String, String>();
		// the downloadUrl attribute will be availabel on the "most" level
		String token = getCredentialToken(displayName, emailAddress, username, userIdentifier);
		Document doc = Utils.getShowtreeDocument(prefix, destination, "minimal", token);
		if (doc != null)
		{
			// get the mapping of site id and site handler
			HashMap<String, String> map = parseShowTreeXMLDoc(doc);
			
			int totalSites = siteIds.size();
			int count = 0;
			for (Map.Entry<String, String> entry : siteIds.entrySet()) {
				String siteId = entry.getKey();
				String siteTitle = entry.getValue();
				siteId = StringUtils.trimToNull(siteId.replaceAll("[\t\r\n]", ""));
				String siteHandle = map.containsKey(siteId)? map.get(siteId):"";
				if (!siteHandle.isEmpty()) 
				{
					count++;
					System.out.println("found " + count + " title=" + siteTitle + " id=" + siteId + "\tsite handle=" + siteHandle);
					String xmlDocument = getDeleteCourseXml(siteHandle);
					
					token = getCredentialToken(displayName, emailAddress, username, userIdentifier);
					
					// upload url
					String uploadURL = getUploadURL(siteHandle, prefix, destination, token);
					
					// delete course xml
					//System.out.println(xmlDocument);
					//wsCall(WS_DELETE_COURSE, uploadURL, xmlDocument, prefix, destination, token);
				}
			}
		}
    }
	
	/**
	 * read the HttpEntity content stream into String object
	 * @param entity
	 * @return
	 * @throws IOException
	 */
	private static String getHttpEntityString(HttpEntity entity) throws IOException {
		String rv;
		StringBuffer buffer = new StringBuffer();
		InputStream instream = entity.getContent(); 
		int size = 2048;
		byte[] data = new byte[size];
		while ((size = instream.read(data, 0, data.length)) > 0)
			buffer.append(new String(data, 0, size));
		instream.close();
		rv = buffer.toString();
		return rv;
	}
	
	/**
     * @{inherit}
     */
	public static String getUploadURL(String handle, String prefix, String destination, String token) 
	{
		String rv = null;
		
		String url = prefix + "/API/getUploadURL/" + destination;
		if (handle != null)
		{
			url = url + "." + handle;
		}
		url = url + "?type=XMLControlFile&" + token;
		try
		{
			// get HttpClient instance
			HttpClient httpClient = Utils.getHttpClientInstance();
			
			// add the course site
			HttpPost httppost = new HttpPost(url);
			HttpResponse response = httpClient.execute(httppost);
			//System.out.println("get uploadurl " + response.getStatusLine());
			HttpEntity entity = response.getEntity();
			if (entity != null)
			{
				rv = getHttpEntityString(entity);
			    // When HttpClient instance is no longer needed, 
		        // shut down the connection manager to ensure
		        // immediate deallocation of all system resources
		        httpClient.getConnectionManager().shutdown();
			}
		}
		catch (IOException e)
		{
			System.out.println("getUploadURL IOException " + e.getMessage());
		}
		catch (Exception e)
		{
			System.out.println("getUploadURL Exception " + e.getMessage());
		}
		
		return rv;
	}
	
	/**
     *{@inherit}
     */
	public static String wsCall(String operation, String uploadURL, String xmlDocument, String prefix, String destination, String token) 
	{
		String rv = null;
		System.out.println("in wsCall");
		
		System.out.println("sendUploadRequest(String " + operation + ", String "
					  + uploadURL + ", String " + xmlDocument + ", String "
					  + prefix + ", String " + destination + ", String " + token
					  + ")");
        
		try
		{
			if (xmlDocument != null)
			{
				File tempFile = File.createTempFile("wsTmp", ".xml");
				tempFile.deleteOnExit();
				FileOutputStream fout = new FileOutputStream(tempFile);
				try {
	    			fout.write(xmlDocument.getBytes());
				}
				catch (Exception fileException)
				{
					System.out.println(" wscall: problem with writing FileOutputStream " + fileException.getMessage());
				}	
				finally {
					try
					{
		    			fout.flush();
						fout.close(); // The file channel needs to be closed before the deletion.
					}
					catch (IOException ioException)
					{
						System.out.println(" wscall: problem closing FileOutputStream " + ioException.getMessage());
					}
				}
				
				// get HttpClient instance
				HttpClient httpClient = Utils.getHttpClientInstance();
				MultipartEntity reqEntity = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE);  
				FileBody bin = new FileBody(tempFile);  
				reqEntity.addPart("file", bin );
		        HttpPost httppost = new HttpPost(uploadURL);	
				httppost.setEntity(reqEntity);
			    HttpResponse response = httpClient.execute(httppost);
			    if (response.getStatusLine() != null)
			    {
				    int status =response.getStatusLine().getStatusCode();
				    if (status == 200)
				    {
						HttpEntity httpEntity = response.getEntity();
						if (httpEntity != null)
						{
							rv = getHttpEntityString(httpEntity);
							
							// When HttpClient instance is no longer needed, 
							// shut down the connection manager to ensure
							// immediate deallocation of all system resources
							httpClient.getConnectionManager().shutdown();
						}
						System.out.println("Upload success ");
				    }
				    else
				    {
						System.out.println("Upload failed response = " + response.getStatusLine().toString());
				    }
				}
			}
		}
		catch (Exception e)
		{
			System.out.println("wsCall Exception"  + e.getMessage());
		}
		
		return rv;
	}
	
	/**
	 * Write a DOM Document to an output stream.
	 * 
	 * @param doc
	 *        The DOM Document to write.
	 * @param out
	 *        The output stream.
	 */
	public static String writeDocumentToString(Document doc)
	{
		try
		{
			
			StringWriter sw = new StringWriter();
			
			DocumentBuilderFactory factory 
			= DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			DOMImplementation impl = builder.getDOMImplementation();
			
			
			DOMImplementationLS feature = (DOMImplementationLS) impl.getFeature("LS",
																				"3.0");
			LSSerializer serializer = feature.createLSSerializer();
			LSOutput output = feature.createLSOutput();
			output.setCharacterStream(sw);
			output.setEncoding("UTF-8");
			serializer.write(doc, output);
			
			sw.flush();
			return sw.toString();
		}
		catch (Exception any)
		{
			System.out.println("writeDocumentToString: " + any.toString());
			return null;
		}
	}
	
	/**
	 * Utility routine to write a string node to the DOM.
	 */
	protected static void writeStringNodeToDom(Document doc, Element parent, String nodeName, String nodeValue)
	{
		if (nodeValue != null && nodeValue.length() != 0)
		{
			Element name = doc.createElement(nodeName);
			Text t = doc.createTextNode(nodeValue);
			name.appendChild(t);
			parent.appendChild(name);
		}
		
		return;
	}
	
    /**
	 * {@inheritDoc}
	 * 
	 */
	public static String getDeleteCourseXml(String courseHandle)
	{
		// create xml doc for requry the course information
		
		try
		{
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = dbf.newDocumentBuilder();
			Document doc = builder.newDocument();
			
			Element root = doc.createElement("ITunesUDocument");
			doc.appendChild(root);
			
			// AddPermission node one: for the maintianer's permission
			Element deleteCourseNode = doc.createElement("DeleteCourse");
			writeStringNodeToDom(doc, deleteCourseNode, "CourseHandle", courseHandle);
			writeStringNodeToDom(doc, deleteCourseNode, "CoursePath", "");
			root.appendChild(deleteCourseNode);
			
			return writeDocumentToString(doc);
		}
		catch (Exception any)
		{
			System.out.println("createDocument: " + any.toString());
			return null;
		}
		
	}
	
	static public HashMap<String, String> parseShowTreeXMLDoc(Document doc)
    {
		// to return a map of site id and handler
		HashMap<String, String> rv = new HashMap<String, String>();
		
		String [] credentialsArray = new String [1];
		Hashtable<String, String> configs = Utils.getConfigs();
		credentialsArray[0] = configs.get("admin_credential");
		
		NodeList nodes = doc.getElementsByTagName("Course");
		
		//TODO: create course folder with course name, id
		
		// Search all course sites
		for (int i = 0; i < nodes.getLength(); i++)
		{
			Node courseNode = nodes.item(i);
			NodeList children = courseNode.getChildNodes();
			
			String courseHandle = "";
			String courseId = "";
			
			// search for identifier to see if this is course
			// wanted and its associated identifier
			for (int j = 0; j < children.getLength(); j++)
			{
				
				Node node = children.item(j);
				String nodeName = node.getNodeName();
				
				//System.out.println("nodeName");
				
				if ("Handle".equals(nodeName)) 
				{
					courseHandle = node.getTextContent();
				}
				else if (nodeName.equalsIgnoreCase("Identifier")) 
				{
					courseId = node.getTextContent();
				}
			}
			
			rv.put(courseId, courseHandle);
			//System.out.println(courseId + "\t" + courseHandle);
		} 
		return rv;
	}
	
	public static void fileUrl(String fAddress, String localFileName, String destinationDir) {
		OutputStream outStream = null;
		URLConnection  uCon = null;
		
		InputStream is = null;
		try {
			URL Url;
			byte[] buf;
			int ByteRead,ByteWritten=0;
			Url= new URL(fAddress);
			outStream = new BufferedOutputStream(new FileOutputStream(destinationDir+"/"+localFileName));
			
			uCon = Url.openConnection();
			is = uCon.getInputStream();
			buf = new byte[size];
			while ((ByteRead = is.read(buf)) != -1) {
				outStream.write(buf, 0, ByteRead);
				ByteWritten += ByteRead;
			}
			//System.out.println("Downloaded Successfully.");
			System.out.println
			("File name:\""+localFileName+ " No ofbytes :" + ByteWritten);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		finally {
			try {
				is.close();
				outStream.close();
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
}