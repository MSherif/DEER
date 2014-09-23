package org.aksw.geolift.modules.nlp;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.NodeIterator;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.util.FileManager;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.net.URLEncoder;

import org.aksw.geolift.helper.vacabularies.DBpedia;
import org.aksw.geolift.helper.vacabularies.SCMSANN;
import org.aksw.geolift.io.Reader;
import org.aksw.geolift.modules.GeoLiftModule;
//import org.junit.Test;
import org.apache.log4j.Logger;

/**
 *
 * @author sherif
 */
public class NLPModule implements GeoLiftModule{
	private static final Logger logger = Logger.getLogger(NLPModule.class.getName());
	private static final String FOX_API_URL = "http://139.18.2.164:4444/api";
	private Model model;

	// parameters list
	private boolean 	extractAllNE = false;
	private Property 	literalProperty;
	private String 		useFoxLight		= "OFF"; //"org.aksw.fox.nertools.NERStanford"; ;
	private boolean 	askEndPoint 	= false;
	private String 		foxType 		= "TEXT";
	private String 		foxTask 		= "NER";
	private String 		foxInput 		= "";
	private String 		foxOutput		= "Turtle";
	private boolean 	foxUseNif		= false;
	private boolean 	foxReturnHtml 	= false;
	private String 		inputFile		= "";
	private String 		outputFile		= "";
	private Property 	addedProperty= ResourceFactory.createProperty("http://geoknow.org/ontology/relatedTo");
	
	private static String NEType = "location";


	/**
	 * @return the relatedToProperty
	 */
	public Property getRelatedToProperty() {
		return addedProperty;
	}

	/**
	 * @param relatedToProperty the relatedToProperty to set
	 */
	public void setRelatedToProperty(Property relatedToProperty) {
		this.addedProperty = relatedToProperty;
	}

	/**
	 * @param model
	 * @param literalProperty
	 *@author sherif
	 */
	public NLPModule(Model model, Property literalProperty) {
		super();
		this.model = model;
		this.literalProperty = literalProperty;
	}

	public NLPModule(String fileNameOrUri, String literalPropartyUri) {
		super();
		this.model = Reader.readModel(fileNameOrUri);
		this.literalProperty = ResourceFactory.createProperty(literalPropartyUri);
	}

	/**
	 * 
	 *@author sherif
	 */
	public NLPModule() {
		super();
		this.model = null;
		this.literalProperty = null;
	}

	/**
	 * @return the model
	 */
	public Model getModel() {
		return model;
	}


	/**
	 * @return the literalProperty
	 */
	public Property getliteralProperty() {
		return literalProperty;
	}

	/**
	 * @param model the model to setModel
	 */
	public void setModel(Model model) {
		this.model = model;
	}


	/**
	 * @param literalProperty the literalProperty to set
	 */
	public void setliteralProperty(Property p) {
		this.literalProperty = p;
	}


	public Model getNamedEntityModel( String inputText){
		String buffer = getNamedEntity(foxType, foxTask, foxOutput, inputText, useFoxLight, foxUseNif, foxReturnHtml);
		ByteArrayInputStream stream=null;
		try {
			stream = new ByteArrayInputStream(buffer.getBytes("UTF-8"));
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		Model NamedEntitymodel = ModelFactory.createDefaultModel();
		if(buffer.contains("<!--")){
			return NamedEntitymodel;
		}
		NamedEntitymodel.read(stream, "", "TTL");
		return NamedEntitymodel;
	}

	public String refineString(String inputString){
		String outputString=inputString;
		outputString.replace("<", "").replace(">", "").replace("//", "");
		return outputString;
	}


	private String getNamedEntity_old_FOX(String type, String task, String output, String text){
		String buffer = "", line; 
		boolean error = true;
		while (error) {
			try {
				text=refineString(text);
				// Construct data
				String data = URLEncoder.encode("type",	 	"UTF-8") 	+ "=" + URLEncoder.encode(type, 	"UTF-8");
				data += "&" + URLEncoder.encode("task",		"UTF-8") 	+ "=" + URLEncoder.encode(task,	 	"UTF-8");
				data += "&" + URLEncoder.encode("output", 	"UTF-8") 	+ "=" + URLEncoder.encode(output, 	"UTF-8");
				data += "&" + URLEncoder.encode("text", 	"UTF-8") 	+ "=" + URLEncoder.encode(text, 	"UTF-8");
				// Send data
				URL url = new URL(FOX_API_URL);
				URLConnection conn = url.openConnection();
				conn.setDoOutput(true);
				OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
				wr.write(data);
				wr.flush();

				// Get the response
				BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));

				while ((line = rd.readLine()) != null) {
					buffer = buffer + line + "\n";
				}
				wr.close();
				rd.close();
				error = false;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return buffer;
	}

	/**
	 * @param type: text or an url (e.g.: `G. W. Leibniz was born in Leipzig`, `http://en.wikipedia.org/wiki/Leipzig_University`)
	 * @param task: { NER }
	 * @param output: { JSON-LD | N-Triples | RDF/{ JSON | XML } | Turtle | TriG | N-Quads}
	 * @param input: text or an url
	 * @param foxlight: an implemented INER class name (e.g.: `org.aksw.fox.nertools.NEROpenNLP`) or `OFF`. 
	 * 		org.aksw.fox.nertools.NERIllinoisExtended
	 * 		org.aksw.fox.nertools.NEROpenNLP
	 * 		org.aksw.fox.nertools.NERBalie
	 * 		org.aksw.fox.nertools.NERStanford
	 * @param nif: { true | false }
	 * @param returnHtml: { true | false }
	 * @return Named entity buffer containing annotation of the input text
	 * @author sherif
	 */
	private String getNamedEntity(String type, String task, String output, String input, String foxlight, boolean nif, boolean returnHtml){
		String buffer = "", line; 
		boolean error = true;
		while (error) {
			try {
				input=refineString(input);
				// Construct data
				String data = URLEncoder.encode("type",	 	"UTF-8") 	+ "=" + URLEncoder.encode(type,						    "UTF-8");
				data += "&" + URLEncoder.encode("task",		"UTF-8") 	+ "=" + URLEncoder.encode(task, 						"UTF-8");
				data += "&" + URLEncoder.encode("output", 	"UTF-8") 	+ "=" + URLEncoder.encode(output,						"UTF-8");
				data += "&" + URLEncoder.encode("input", 	"UTF-8") 	+ "=" + URLEncoder.encode(input, 						"UTF-8");
				data += "&" + URLEncoder.encode("foxlight", "UTF-8")	+ "=" + URLEncoder.encode(foxlight,						"UTF-8");
				data += "&" + URLEncoder.encode("nif", 		"UTF-8")	+ "=" + URLEncoder.encode((nif)       ? "TRUE":"FALSE", "UTF-8");
				data += "&" + URLEncoder.encode("returnHtml", "UTF-8")	+ "=" + URLEncoder.encode((returnHtml)? "TRUE":"FALSE", "UTF-8");

				// Send data
				URL url = new URL(FOX_API_URL);
				URLConnection conn = url.openConnection();
				conn.setDoOutput(true);
				OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
				wr.write(data);
				wr.flush();

				// Get the response
				BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));

				while ((line = rd.readLine()) != null) {
					buffer = buffer + line + "\n";
				}
				wr.close();
				rd.close();
				error = false;
			} catch (Exception e) {
				logger.error("FOX Exception: " + e);
				e.printStackTrace();
			}
		}

		//TODO use a JASON parser
		buffer= URLDecoder.decode(buffer); 
		buffer = buffer.substring(buffer.indexOf("@"), buffer.lastIndexOf("log")-4).toString();

		return buffer;
	}


	/**
	 * @param namedEntityModel
	 * @param subject
	 * @return model of places contained in the input model 
	 * @author sherif
	 */
	public Model getNE(Model namedEntityModel, RDFNode subject, Resource type){

		Model resultModel = ModelFactory.createDefaultModel();
		String sparqlQueryString= 	"CONSTRUCT {?s ?p ?o} " +
				" WHERE {?s a "	+ type.toString() + ". ?s ?p ?o} " ;
		QueryFactory.create(sparqlQueryString);
		QueryExecution qexec = QueryExecutionFactory.create(sparqlQueryString, namedEntityModel);
		Model locationsModel =qexec.execConstruct();
		Property meansProperty = ResourceFactory.createProperty("http://ns.aksw.org/scms/means");
		NodeIterator objectsIter = locationsModel.listObjectsOfProperty(meansProperty);
		if(askEndPoint){
			while (objectsIter.hasNext()) {
				RDFNode object = objectsIter.nextNode();
				if(object.isResource()){
					if(isPlace(object)){
						resultModel.add( (Resource) subject , addedProperty, object);
						//					TODO add more data ??
						logger.info("<" + subject.toString() + "> <" + addedProperty + "> <" + object + ">");
					}	
				}
			}
		}else
		{
			while (objectsIter.hasNext()) {
				RDFNode object = objectsIter.nextNode();
				if(object.isResource()){

					resultModel.add( (Resource) subject , addedProperty, object);
					//					TODO add more data ??
					logger.info("<" + subject.toString() + "> <" + addedProperty + "> <" + object + ">");
				}
			}
		}
		return resultModel;
	}


	/**
	 * As a generalization of GeoLift
	 * @param namedEntityModel
	 * @param subject
	 * @param types
	 * @return model of all NEs contained in the input model
	 * @author sherif
	 */
	public Model getNE(Model namedEntityModel, RDFNode subject){
		Model resultModel = ModelFactory.createDefaultModel();
		Property meansProperty = ResourceFactory.createProperty("http://ns.aksw.org/scms/means");
		NodeIterator objectsIter = namedEntityModel.listObjectsOfProperty(meansProperty);
		while (objectsIter.hasNext()) {
			RDFNode object = objectsIter.nextNode();
			if(object.isResource()){
				resultModel.add( (Resource) subject , addedProperty, object);
				//					TODO add more data ??
				logger.info("<" + subject.toString() + "> <" + addedProperty + "> <" + object + ">");
			}
		}
		return resultModel;
	}

	/**
	 * @param uri
	 * @return wither is the input URI is a place of not
	 * @author sherif
	 */
	private boolean isPlace(RDFNode uri){
		boolean result=false;
		if(uri.toString().contains("http://ns.aksw.org/scms/"))
			return false;
		String queryString="ask {<" +uri.toString() + "> a <http://dbpedia.org/ontology/Place>}";
		logger.info("Asking DBpedia for: "+ queryString);
		Query query = QueryFactory.create(queryString);
		//		QueryExecution qexec = QueryExecutionFactory.sparqlService(DBpedia.endPoint, query);
		QueryExecution qexec = QueryExecutionFactory.sparqlService(DBpedia.endPoint, query);
		result = qexec.execAsk();
		logger.info("Answer: " + result);
		return result;
	}


	/**
	 * @param limit
	 * @return just a TEST 
	 * @author sherif
	 */
	public List<String> getDBpediaAbstaracts(Integer limit){
		List<String> result = new ArrayList<String>();

		String queryString = "SELECT distinct ?o WHERE {" +
				"?s a <http://dbpedia.org/ontology/Place>." +
				"?s <http://dbpedia.org/ontology/abstract> ?o } LIMIT " + limit.toString();
		Query query = QueryFactory.create(queryString);
		QueryExecution qexec = QueryExecutionFactory.sparqlService("http://dbpedia.org/sparql", query);
		ResultSet queryResults = qexec.execSelect();  
		while(queryResults.hasNext()){
			QuerySolution qs=queryResults.nextSolution();
			result.add( qs.getLiteral("o").toString());
		}
		qexec.close() ;
		return result;
	}

	/**
	 * @return Geo-spatial enriched model
	 * @author sherif
	 */
	public Model getEnrichrdTriples(){

		Model resultModel = ModelFactory.createDefaultModel();
		StmtIterator stItr = model.listStatements(null, literalProperty, (RDFNode) null);
		logger.info("--------------- Added triples through  NLP ---------------");
		while (stItr.hasNext()) {
			Statement st = stItr.nextStatement();
			RDFNode object = st.getObject();
			RDFNode subject = st.getSubject();
			if(object.isLiteral()){
				if(!object.asLiteral().toString().contains("^^")){ 
					Model namedEntityModel = getNamedEntityModel(object.toString().substring(0,object.toString().lastIndexOf("@")));
					if(!namedEntityModel.isEmpty()){
						if(NEType.toLowerCase().equals("all")){ // Extract all NE (Generalization of GeoLift)
							resultModel.add(getNE(namedEntityModel, subject));
						}else if(NEType.toLowerCase().equals("location")){
							resultModel.add(getNE(namedEntityModel, subject, SCMSANN.LOCATION));
						}else if(NEType.toLowerCase().equals("person")){
							resultModel.add(getNE(namedEntityModel, subject, SCMSANN.PERSON));
						}else if(NEType.toLowerCase().equals("organization")){
							resultModel.add(getNE(namedEntityModel, subject, SCMSANN.ORGANIZATION));
						}
					}				
				}
			}
		}
		resultModel.add(model);
		return resultModel;
	}


	public Model enrichModel(){
		return model.union(getEnrichrdTriples());
	}


	/* (non-Javadoc)
	 * @see org.aksw.geolift.modules.GeoLiftModule#process(com.hp.hpl.jena.rdf.model.Model, java.util.Map)
	 */
	public Model process(Model inputModel, Map<String, String> parameters){
		logger.info("--------------- NLP Module ---------------");
		model = inputModel;
		if( parameters.containsKey("input")){
			inputFile = parameters.get("input");
			model = Reader.readModel(inputFile);
		}	
		if( parameters.containsKey("literalProperty"))
			literalProperty = ResourceFactory.createProperty(parameters.get("literalProperty"));
		else{
			LiteralPropertyRanker lpr = new LiteralPropertyRanker(model)	;
			literalProperty = lpr.getTopRankedLiteralProperty();
			logger.info("Top ranked Literal Property: " + literalProperty); 
		}
		if( parameters.containsKey("addedGeoProperty"))
			addedProperty = ResourceFactory.createProperty("addedGeoProperty");
		if( parameters.containsKey("useFoxLight"))
			useFoxLight = parameters.get("useFoxLight").toLowerCase();
		if( parameters.containsKey("askEndPoint"))
			askEndPoint = parameters.get("askEndPoint").toLowerCase().equals("true")? true : false;
//		if( parameters.containsKey("foxType"))
//			foxType = parameters.get("foxType").toUpperCase();
//		if( parameters.containsKey("foxTask"))
//			foxTask = parameters.get("foxTask").toUpperCase();
//		if( parameters.containsKey("foxInput"))
//			foxInput = parameters.get("foxInput");
//		if( parameters.containsKey("foxOutput"))
//			foxOutput = parameters.get("foxOutput");
//		if( parameters.containsKey("foxUseNif"))
//			foxUseNif = parameters.get("foxUseNif").toLowerCase().equals("true")? true : false;
//		if( parameters.containsKey("foxReturnHtml"))
//			foxReturnHtml = parameters.get("foxReturnHtml").toLowerCase().equals("true")? true : false;
//		if( parameters.containsKey("extractAllNE"))
//			foxReturnHtml = parameters.get("extractAllNE").toLowerCase().equals("true")? true : false;
		if( parameters.containsKey("NEType"))
			NEType = parameters.get("NEType").toLowerCase();

		Model enrichedModel = getEnrichrdTriples();
		enrichedModel.add(inputModel);

		if( parameters.containsKey("output")){
			outputFile = parameters.get("output");
			FileWriter outFile = null;
			try {
				outFile = new FileWriter(outputFile);
			} catch (IOException e) {
				e.printStackTrace();
			}		
			enrichedModel.write(outFile,"TURTLE");
		}
		return enrichedModel;
	}

	/* (non-Javadoc)
	 * @see org.aksw.geolift.modules.GeoLiftModule#getParameters()
	 */
	public List<String> getParameters() {
		List<String> parameters = new ArrayList<String>();
		//		parameters.add("input");
		//		parameters.add("output");
		parameters.add("literalProperty");
		parameters.add("useFoxLight");
		parameters.add("askEndPoint");
//		parameters.add("foxType");
//		parameters.add("foxTask");
//		parameters.add("foxInput");
//		parameters.add("foxOutput");
//		parameters.add("foxUseNif");
//		parameters.add("foxReturnHtml");
		parameters.add("addedGeoProperty");
		parameters.add("NEType");
		return parameters;
	}

	/* (non-Javadoc)
	 * @see org.aksw.geolift.modules.GeoLiftModule#getNecessaryParameters()
	 */
	@Override
	public List<String> getNecessaryParameters() {
		List<String> parameters = new ArrayList<String>();
		return parameters;
	}
	
	
	/**
	 * Self configuration
	 * Set all parameters to default values, also extract all NEs
	 * @param source
	 * @param target
	 * @return Map of (key, value) pairs of self configured parameters
	 * @author sherif
	 */
	public Map<String, String> selfConfig(Model source, Model target) {
		
//		Set<Resource> uriObjects = getDiffUriObjects(source, target);
		
		Map<String, String> p = new HashMap<String, String>();
		p.put("NEType", "ALL");
		return p;
	}


	/**
	 * @param source
	 * @param target
	 * @return
	 * @author sherif
	 */
	private Set<Resource> getDiffUriObjects(Model source, Model target) {
		Set<Resource> uriObjects = new HashSet<Resource>();
		Model diff = target.remove(source);
		NodeIterator objects = diff.listObjects();
		while(objects.hasNext()){
			RDFNode o = objects.next();
			if(o.isURIResource()){
				uriObjects.add(o.asResource());
			}
		}
		return uriObjects;
	}

	public static void main(String args[]) throws IOException {
		NLPModule geoEnricher= new NLPModule();

		Map<String, String> parameters = new HashMap<String, String>();

		// set parameters from command line
		for(int i=0; i<args.length; i+=2){
			if(args[i].equals("-i") || args[i].toLowerCase().equals("--input")){
				parameters.put("input",   args[i+1]);
			}
			if(args[i].equals("-o") || args[i].toLowerCase().equals("--output")){
				parameters.put("output",   args[i+1]);
			}
			if(args[i].equals("-p") || args[i].toLowerCase().equals("--literalProperty")){
				parameters.put("literalProperty",   args[i+1]);
			}
			if(args[i].equals("-l") || args[i].toLowerCase().equals("--useFoxLight")){
				parameters.put("useFoxLight",   args[i+1]);
			}
			if(args[i].equals("-e") || args[i].toLowerCase().equals("--askEndPoint")){
				parameters.put("askEndPoint",   args[i+1]);
			}
			if(args[i].equals("-p") || args[i].toLowerCase().equals("--literalProperty")){
				parameters.put("LiteralProperty",   args[i+1]);}
			if(args[i].equals("-?") || args[i].toLowerCase().equals("--help")){
				logger.info(
						"Basic parameters:\n" +
								"\t-i --input: input file/URI" + "\n" +
								"\t-o --output: output file/URI" + "\n" +
								"\t-p --literalProperty: literal property used for NER" + "\n" +
								"\t-l --useFoxLight: foxlight: an implemented INER class name (e.g.: `org.aksw.fox.nertools.NEROpenNLP`) or `OFF`." + "\n" +
								"\t org.aksw.fox.nertools.NERIllinoisExtended"+ "\n" +
								"\t org.aksw.fox.nertools.NEROpenNLP"+ "\n" +
								"\t org.aksw.fox.nertools.NERBalie"+ "\n" +
								"\t org.aksw.fox.nertools.NERStanford" + "\n" +
								"\t-e --askEndPoint: { true | false}"+ "\n" +
								"Fox parameters (current version use always default values, which is the first one):\n"+
								"\t--foxType: { text | url }" + "\n" +
								"\t--foxTask: { NER }" + "\n" +
								"\t--foxInput: text or an url" + "\n" +
								"\t--foxOutput: { JSON-LD | N-Triples | RDF/{ JSON | XML } | Turtle | TriG | N-Quads}" + "\n" +
								"\t--foxUseNif: { false | true }" + "\n" +
						"\t--foxReturnHtml: { false | true }" );
				System.exit(0);
			}
		} 
		if(!parameters.containsKey("input")){
			logger.error("No input file/URI, Exit with error!!");
			System.exit(1);
		}

		Model enrichedModel = geoEnricher.process(null, parameters);

		if(!parameters.containsKey("output")){
			logger.info("Enriched MODEL:");
			logger.info("---------------");
			enrichedModel.write(System.out,"TTL");
		}
	}
}