package aQute.bnd.maven.support;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;

import javax.xml.xpath.*;

import org.w3c.dom.*;

import aQute.lib.io.*;

public class ProjectPom extends Pom {

	final List<URI>	repositories	= new ArrayList<URI>();
	final Properties	properties		= new Properties();
	String 		packaging;
	String 		url;
	
	ProjectPom(Maven maven, File pomFile) throws Exception {
		super(maven, pomFile, pomFile.toURI());
	}

	@Override protected void parse(Document doc, XPath xp) throws Exception {
		super.parse(doc, xp);

		packaging = xp.evaluate("project/packaging", doc);
		url = xp.evaluate("project/url", doc);
		
		Node parent = (Node) xp.evaluate("project/parent", doc, XPathConstants.NODE);
		if (parent != null && parent.hasChildNodes()) {
			File parentFile = IO.getFile(getPomFile().getParentFile(), "../pom.xml");

			String parentGroupId = xp.evaluate("groupId", parent).trim();
			String parentArtifactId = xp.evaluate("artifactId", parent).trim();
			String parentVersion = xp.evaluate("version", parent).trim();
			String parentPath = xp.evaluate("relativePath", parent).trim();
			if (parentPath != null && !parentPath.isEmpty()) {
				parentFile = IO.getFile(getPomFile().getParentFile(), parentPath);
			}
			if (parentFile.isFile()) {
				ProjectPom parentPom = new ProjectPom(maven, parentFile);
				parentPom.parse();
				dependencies.addAll(parentPom.dependencies);
				for ( String key : parentPom.properties.stringPropertyNames()) {
					if ( ! properties.contains(key))
						properties.put(key, parentPom.properties.get(key));
				}
				repositories.addAll(parentPom.repositories);
				
				setNames(parentPom);
			} else {
				// This seems to be a bit bizarre, extending an external pom?
				CachedPom parentPom = maven.getPom(parentGroupId, parentArtifactId, parentVersion);
				dependencies.addAll(parentPom.dependencies);
				setNames(parentPom);
			}
		}

		NodeList propNodes = (NodeList) xp.evaluate("project/properties/*", doc,
				XPathConstants.NODESET);
		for (int i = 0; i < propNodes.getLength(); i++) {
			Node node = propNodes.item(i);
			String key = node.getNodeName();
			String value = node.getTextContent();
			if ( key == null || key.isEmpty())
				throw new IllegalArgumentException("Pom has an empty or null key");
			if ( value == null || value.isEmpty())
				throw new IllegalArgumentException("Pom has an empty or null value for property " + key);
			properties.setProperty(key, value.trim());
		}

		NodeList repos = (NodeList) xp.evaluate("project/repositories/repository/url", doc,
				XPathConstants.NODESET);
		for (int i = 0; i < repos.getLength(); i++) {
			Node node = repos.item(i);
			String URIString = node.getTextContent().trim();
			URI uri = new URI(URIString);
			if ( uri.getScheme() ==null )
				uri = IO.getFile(pomFile.getParentFile(),URIString).toURI();
			repositories.add(uri);
		}

	}

//	private void print(Node node, String indent) {
//		System.out.print(indent);
//		System.out.println(node.getNodeName());
//		Node rover = node.getFirstChild();
//		while ( rover != null) {
//			print( rover, indent+" ");
//			rover = rover.getNextSibling();
//		}
//	}

	/**
	 * @param parentArtifactId
	 * @param parentGroupId
	 * @param parentVersion
	 * @throws Exception
	 */
	private void setNames(Pom pom) throws Exception {
		if (artifactId == null || artifactId.isEmpty())
			artifactId = pom.getArtifactId();
		if (groupId == null || groupId.isEmpty())
			groupId = pom.getGroupId();
		if (version == null || version.isEmpty())
			version = pom.getVersion();
		if ( description == null )
			description = pom.getDescription();
		else
			description = pom.getDescription() + "\n" + description;
	
	}

	class Rover {

		public Rover(Rover rover, Dependency d) {
			this.previous = rover;
			this.dependency = d;
		}

		final Rover			previous;
		final Dependency	dependency;

		public boolean excludes(String name) {
			return dependency.exclusions.contains(name) && previous != null
					&& previous.excludes(name);
		}
	}

	public Set<Pom> getDependencies(Scope scope) throws Exception {
		return getDependencies(scope, repositories.toArray(new URI[0]));
	}

	// Match any macros
	final static Pattern	MACRO	= Pattern.compile("(\\$\\{\\s*([^}\\s]+)\\s*\\})");

	protected String replace(String in) {
		Matcher matcher = MACRO.matcher(in);
		int last = 0;
		StringBuilder sb = new StringBuilder();
		while (matcher.find()) {
			int n = matcher.start();
			sb.append( in, last, n);
			String replacement = get(matcher.group(2));
			if ( replacement == null )
				sb.append( matcher.group(1));
			else
				sb.append( replacement );
			last = matcher.end();
		}
		if ( last == 0)
			return in;
		
		sb.append( in, last, in.length());
		return sb.toString();
	}

	private String get(String key) {
		if (key.equals("pom.artifactId"))
			return artifactId;
		if (key.equals("pom.groupId"))
			return groupId;
		if (key.equals("pom.version"))
			return version;
		
		if (key.equals("pom.name"))
			return name;
		
		String prop = properties.getProperty(key);
		if ( prop != null )
			return prop;
		
		return System.getProperty(key);
	}

	public Properties getProperties() {
		return properties;
	}

	public String getPackaging() {
		return packaging;
	}

	public String getUrl() {
		return url;
	}

	public String getProperty(String key) {
		String s = properties.getProperty(key);
		return replace(s);
	}

	@Override public File getArtifact() throws Exception {
		return null;
	}
}
