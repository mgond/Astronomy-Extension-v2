/******************************************************************************
 * Copyright 2011-2013 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
 *
 * This file is part of SITools2.
 *
 * SITools2 is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * SITools2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with SITools2. If not, see <http://www.gnu.org/licenses/>.
 *****************************************************************************/
package fr.cnes.sitools.extensions.astro.resource;

import fr.cnes.sitools.astro.representation.GeoJsonRepresentation;
import fr.cnes.sitools.astro.resolver.AbstractNameResolver;
import fr.cnes.sitools.astro.resolver.CDSNameResolver;
import fr.cnes.sitools.astro.resolver.CorotIdResolver;
import fr.cnes.sitools.astro.resolver.IMCCESsoResolver;
import fr.cnes.sitools.astro.resolver.NameResolverResponse;
import fr.cnes.sitools.common.resource.SitoolsParameterizedResource;
import fr.cnes.sitools.extensions.common.AstroCoordinate;
import fr.cnes.sitools.extensions.common.AstroCoordinate.CoordinateSystem;
import fr.cnes.sitools.plugins.resources.model.ResourceParameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.restlet.data.Disposition;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.data.Status;
import org.restlet.ext.wadl.DocumentationInfo;
import org.restlet.ext.wadl.MethodInfo;
import org.restlet.ext.wadl.OptionInfo;
import org.restlet.ext.wadl.ParameterInfo;
import org.restlet.ext.wadl.ParameterStyle;
import org.restlet.ext.wadl.RepresentationInfo;
import org.restlet.ext.wadl.ResponseInfo;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.ResourceException;

/**
 * Searchs on different name resolvers and returns one or several names.
 *
 * <p> In this current version, there are three name resolver<br/> services. The first one is based on CDS for stars and deep object. The
 * second one is based on solar system objects.<br/> And the last one is based on Corot </p>
 *
 * <p>
 * <pre>
 * Example of requests:
 * - /plugin/nameResolver/mars/GALACTIC?nameResolver=IMCCE : Get Mars coordinates in GALACTIC frame
 * - /plugin/nameResolver/m31/EQUATORIAL?nameResolver=CDS : Get M31 coordinates in EQUATORIAL frame
 * </pre> </p>
 *
 * @author Jean-Christophe Malapert <jean-christophe.malapert@cnes.fr>
 */
public class NameResolverResource extends SitoolsParameterizedResource {

  /**
   * Logger.
   */
  private static final Logger LOG = Logger.getLogger(GlobWebResource.class.getName());
  /**
   * Name resolver that is configured by the administrator.
   */
  private String nameResolver;
  /**
   * Object name as input parameter.
   */
  private String objectName;
  /**
   * Time as input parameter.
   */
  private String epoch;
  /**
   * Coordinate system as input parameter.
   */
  private CoordinateSystem coordSystem;

  /**
   * Initialize the name resolver, the epoch and the object name.
   */
  @Override
  public final void doInit() {
    super.doInit();
    // Get nameResolverInterface parameter from URL
    this.nameResolver = getRequest().getResourceRef().getQueryAsForm().getFirstValue("nameResolver");
    if (this.nameResolver == null || "".equals(this.nameResolver)) {
      ResourceParameter nameResolverParam = this.getModel().getParameterByName("nameResolver");
      this.nameResolver = nameResolverParam.getValue();
    }
    // Get epoch parameter from URL
    this.epoch = getRequest().getResourceRef().getQueryAsForm().getFirstValue("epoch");
    if (this.epoch == null || "".equals(this.epoch)) {
      ResourceParameter epochParam = this.getModel().getParameterByName("epoch");
      this.epoch = epochParam.getValue();
    }
    // Get object name
    this.objectName = String.valueOf(this.getRequestAttributes().get("objectName"));
    // Get and check coordinate system
    String cs = String.valueOf(this.getRequestAttributes().get("coordSystem"));
    if (!getRequest().getMethod().equals(Method.OPTIONS)) {
      if (cs.equalsIgnoreCase(CoordinateSystem.EQUATORIAL.name())) {
        this.coordSystem = CoordinateSystem.EQUATORIAL;
      } else if (cs.equalsIgnoreCase(CoordinateSystem.GALACTIC.name())) {
        this.coordSystem = CoordinateSystem.GALACTIC;
      } else {
        LOG.log(Level.WARNING, "Name resolver service - Wrong parameter: {0}", cs);
        throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST, cs + " must be either EQUATORIAL or GALACTIC");
      }
      // Check if all required parameters are set
      if (this.objectName.isEmpty() || cs.isEmpty()) {
        LOG.log(Level.WARNING, "Name resolver service - Wrong parameters");
        throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST, "Check your input parameters");
      }
    }
  }

  /**
   * Returns the representation based on CDS response.
   *
   * @return the representation
   */
  private Representation resolveCds() {
    LOG.finest(String.format("CDS name resolver is choosen with the following parameter %s", objectName));
    AbstractNameResolver cds = new CDSNameResolver(objectName, CDSNameResolver.NameResolverService.all);    
    NameResolverResponse response = cds.getResponse();    
    if (response.hasResult()) {
      LOG.log(Level.INFO, "CDS name resolver is selected for {0}.", objectName);
      getResponse().setStatus(Status.SUCCESS_OK);
      List<AstroCoordinate> coordinates = response.getAstroCoordinates();
      for (AstroCoordinate iter:coordinates) {
        iter.processTo(coordSystem);
      }
      String credits = response.getCredits();
      Map dataModel = getDataModel(credits, coordinates, this.coordSystem.name());
      return new GeoJsonRepresentation(dataModel);
    } else {
      LOG.log(Level.WARNING, null, response.getError());
      throw new ResourceException(response.getError().getStatus(), response.getError().getMessage());
    }
  }

  /**
   * Returns the representation based on IMCCE response.
   *
   * @return the representation
   */
  private Representation resolveIMCCE() {
    LOG.finest(String.format("IMCCE name resolver is choosen with the following parameter %s", objectName));
    AbstractNameResolver imcce = new IMCCESsoResolver(objectName, epoch);
    NameResolverResponse response = imcce.getResponse();    
    if (response.hasResult()) {
      LOG.log(Level.INFO, "IMCCE name resolver is selected for {0}.", objectName);
      getResponse().setStatus(Status.SUCCESS_OK);
      List<AstroCoordinate> coordinates = response.getAstroCoordinates();
      for (AstroCoordinate iter:coordinates) {
        iter.processTo(coordSystem);
      }
      String credits = response.getCredits();
      Map dataModel = getDataModel(credits, coordinates, this.coordSystem.name());
      return new GeoJsonRepresentation(dataModel);
    } else {
      LOG.log(Level.WARNING, null, response.getError());
      throw new ResourceException(response.getError().getStatus(), response.getError().getMessage());
    }    
  }

  /**
   * Returns the representation based on IAS response.
   *
   * @return the representation
   */
  private Representation resolveIAS() {
    AbstractNameResolver ias = new CorotIdResolver(objectName);
    NameResolverResponse response = ias.getResponse();    
    if (response.hasResult()) {
      LOG.log(Level.INFO, "Corot name resolver is selected for {0}", objectName);
      getResponse().setStatus(Status.SUCCESS_OK);
      List<AstroCoordinate> coordinates = response.getAstroCoordinates();
      for (AstroCoordinate iter:coordinates) {
        iter.processTo(coordSystem);
      }
      String credits = response.getCredits();
      Map dataModel = getDataModel(credits, coordinates, this.coordSystem.name());
      return new GeoJsonRepresentation(dataModel);
    } else {
      LOG.log(Level.WARNING, null, response.getError());
      throw new ResourceException(response.getError().getStatus(), response.getError().getMessage());
    }    
  }
  

  /**
   * Returns the GeoJSON of the object name after calling all resolvers until an object is found or all resolvers have been called.   
   * @return the GeoJSON of the object name
   */
  private Representation callChainedResolver() {
    Map dataModel;
    AbstractNameResolver cds = new CDSNameResolver(objectName, CDSNameResolver.NameResolverService.all);    
    AbstractNameResolver imcce = new IMCCESsoResolver(objectName, "now");
    AbstractNameResolver corot = new CorotIdResolver(objectName);
    cds.setNext(imcce);
    imcce.setNext(corot);
    NameResolverResponse response = cds.getResponse();
    if (!response.hasResult()) {
      throw new ResourceException(response.getError().getStatus(), response.getError().getMessage());
    }
    String credits = response.getCredits();
    List<AstroCoordinate> coordinates = response.getAstroCoordinates();
    for (AstroCoordinate iter:coordinates) {
      iter.processTo(coordSystem);
    }
    dataModel = getDataModel(credits, coordinates, this.coordSystem.name());
    return new GeoJsonRepresentation(dataModel);
  }

  /**
   * Returns the name resolver reponse.
   *
   * @return the representation
   */
  @Get
  public final Representation getNameResolverResponse() {
    Representation rep = null;

    if (this.nameResolver.equals("CDS")) {
      rep = resolveCds();
    } else if (this.nameResolver.equals("IMCCE")) {
      rep = resolveIMCCE();
    } else if (this.nameResolver.equals("IAS")) {
      rep = resolveIAS();
    } else if (this.nameResolver.equals("ALL")) {
      rep = callChainedResolver();
    }
    if (fileName != null && !"".equals(fileName)) {
      Disposition disp = new Disposition(Disposition.TYPE_ATTACHMENT);
      disp.setFilename(fileName);
      rep.setDisposition(disp);
    }
    return rep;
  }

  /**
   * Returns the data model for the JSON Representation of the name resolver.
   *
   * @param name credits
   * @param astroList results of the name resolver
   * @param referenceFrame ReferenceFrame
   * @return data model of the JSON representation
   * @throws JSONException
   */
  private Map getDataModel(final String name, final List<AstroCoordinate> astroList, final String referenceFrame) {

    Map dataModel = new HashMap();
    List<Map> features = new ArrayList<Map>();
    int i = 0;
    for (AstroCoordinate astroIter : astroList) {
      Map feature = new HashMap();

      Map properties = new HashMap();
      properties.put("identifier", name.concat(String.valueOf(i++)));
      properties.put("credits", name);
      Map<String, String> metadata = astroIter.getMatadata();
      Set<String> keys = metadata.keySet();
      Iterator<String> keyIter = keys.iterator();
      while (keyIter.hasNext()) {
        String key = keyIter.next();
        properties.put(key, metadata.get(key));
      }
      
      feature.put("properties", properties);

      Map geometry = new HashMap();
      geometry.put("type", "Point");
      geometry.put("coordinates", String.format("[%s,%s]", astroIter.getRaAsDecimal(), astroIter.getDecAsDecimal()));
      geometry.put("crs", (CoordinateSystem.EQUATORIAL.name().equals(referenceFrame)) ? CoordinateSystem.EQUATORIAL.name().concat(".ICRS") : CoordinateSystem.GALACTIC.name());
      feature.put("geometry", geometry);
      features.add(feature);
    }
    dataModel.put("features", features);
    dataModel.put("totalResults", features.size());
    return dataModel;
  }

  /**
   * General WADL description.
   */
  @Override
  public final void sitoolsDescribe() {
    setName("Name resolver service");
    setDescription("Provide a name resolver service to CDS, IMCCE, IAS or both");
  }

  /**
   * Descibes the GET method in WADL.
   *
   * @param info information
   */
  @Override
  protected final void describeGet(final MethodInfo info) {
    this.addInfo(info);
    info.setIdentifier("NameResolver");
    info.setDocumentation("Get the object's coordinates from its name and time for planets.");

    // objecName parameter
    List<ParameterInfo> parametersInfo = new ArrayList<ParameterInfo>();
    parametersInfo.add(new ParameterInfo("objectName", true, "String", ParameterStyle.TEMPLATE,
            "Object name to resolve"));

    // reference frame parameter
    ParameterInfo paramCoordSys = new ParameterInfo("coordSystem", true, "String", ParameterStyle.TEMPLATE,
            "Coordinate system in which the output is formated");
    List<OptionInfo> coordsysOption = new ArrayList<OptionInfo>();
    OptionInfo optionEquatorial = new OptionInfo("Equatorial system in ICRS");
    optionEquatorial.setValue(CoordinateSystem.EQUATORIAL.name());
    coordsysOption.add(optionEquatorial);
    OptionInfo optionGalactic = new OptionInfo("Galactic system");
    optionGalactic.setValue(CoordinateSystem.GALACTIC.name());
    coordsysOption.add(optionGalactic);
    paramCoordSys.setOptions(coordsysOption);
    parametersInfo.add(paramCoordSys);

    // Name resolver parameter
    ParameterInfo nameResolverParam = new ParameterInfo("nameResolver", false, "String", ParameterStyle.QUERY,
            "The selected name resolver");
    List<OptionInfo> nameResolverOption = new ArrayList<OptionInfo>();
    OptionInfo optionCDS = new OptionInfo("The CDS name resolver based on SIMBAD and NED");
    optionCDS.setValue("CDS");
    nameResolverOption.add(optionCDS);
    OptionInfo optionIAS = new OptionInfo("The IAS name resolver for Corot");
    optionIAS.setValue("IAS");
    nameResolverOption.add(optionIAS);
    nameResolverParam.setOptions(nameResolverOption);
    OptionInfo optionIMCCE = new OptionInfo("The IMCEE name resolver for solar system bodies");
    optionIMCCE.setValue("IMCCE");
    nameResolverOption.add(optionIMCCE);
    nameResolverParam.setOptions(nameResolverOption);
    OptionInfo optionAll = new OptionInfo("Query all name resolvers");
    optionAll.setValue("ALL");
    nameResolverOption.add(optionAll);
    String nameResolverFromModel = this.getModel().getParameterByName("nameResolver").getValue();
    String defaultNameResolver = (nameResolverFromModel != null && !nameResolverFromModel.isEmpty()) ? nameResolverFromModel
            : "CDS";
    nameResolverParam.setDefaultValue(defaultNameResolver);
    parametersInfo.add(nameResolverParam);

    // Time frame for IMCCE
    ParameterInfo time = new ParameterInfo("epoch", false, "String", ParameterStyle.QUERY,
            "Time frame for IMCCE name resolver. See documentation from IMCCE");
    String timeFromModel = this.getModel().getParameterByName("epoch").getValue();
    String defaultTime = (timeFromModel != null && !timeFromModel.isEmpty()) ? timeFromModel : "now";
    time.setDefaultValue(defaultTime);
    parametersInfo.add(time);

    // Set all parameters
    info.getRequest().setParameters(parametersInfo);

    // Response OK
    ResponseInfo responseOK = new ResponseInfo();
    List<RepresentationInfo> representationsInfo = new ArrayList<RepresentationInfo>();
    RepresentationInfo representationInfo = new RepresentationInfo(MediaType.APPLICATION_JSON);
    DocumentationInfo doc = new DocumentationInfo();
    doc.setTitle("Name Resolver representation");
    doc.setTextContent("<pre>{\n"
            + "totalResults: 1,\n"
            + "type: \"FeatureCollection\",\n"
            + "features: [\n"
            + "  geometry: {\n"
            + "    coordinates: [10.6847083,41.26875],\n"
            + "    type: \"Point\"\n"
            + "  },\n"
            + "properties: {\n"
            + "  crs: {\n"
            + "    type: \"name\",\n"
            + "    properties: {\n"
            + "      name: \"EQUATORIAL.ICRS\"\n"
            + "    }\n"
            + "  },\n"
            + "  credits: \"CDS\",\n"
            + "  identifier: \"CDS0\"\n"
            + "}\n"
            + "}]}</pre>");
    representationInfo.setDocumentation(doc);
    representationsInfo.add(representationInfo);
    responseOK.getRepresentations().add(representationInfo);
    responseOK.getStatuses().add(Status.SUCCESS_OK);

    // response bad request and internal error
    representationsInfo = new ArrayList<RepresentationInfo>();
    representationInfo = new RepresentationInfo(MediaType.TEXT_HTML);
    representationInfo.setDocumentation("SITools2 status error page");
    representationsInfo.add(representationInfo);

    ResponseInfo responseError = new ResponseInfo();
    responseError.getRepresentations().add(representationInfo);
    responseError.getStatuses().add(Status.SERVER_ERROR_INTERNAL);
    responseError.setDocumentation("Unexpected error");

    ResponseInfo responseBad = new ResponseInfo();
    responseBad.getRepresentations().add(representationInfo);
    responseBad.getStatuses().add(Status.CLIENT_ERROR_BAD_REQUEST);
    responseBad.setDocumentation(Status.CLIENT_ERROR_BAD_REQUEST.getDescription() + "- coordinate system is unknown");
    
    ResponseInfo responseNotFound = new ResponseInfo();
    responseNotFound.getRepresentations().add(representationInfo);
    responseNotFound.getStatuses().add(Status.CLIENT_ERROR_NOT_FOUND);
    responseNotFound.setDocumentation("object not found.");    

    ResponseInfo responseUnavailable = new ResponseInfo();
    responseUnavailable.getRepresentations().add(representationInfo);
    responseUnavailable.getStatuses().add(Status.SERVER_ERROR_SERVICE_UNAVAILABLE);
    responseUnavailable.setDocumentation(Status.SERVER_ERROR_SERVICE_UNAVAILABLE.getDescription());

    // Set responses
    List<ResponseInfo> responseInfo = new ArrayList<ResponseInfo>();
    responseInfo.add(responseOK);
    responseInfo.add(responseError);
    responseInfo.add(responseBad);
    responseInfo.add(responseUnavailable);
    responseInfo.add(responseNotFound);
    info.getResponses().addAll(responseInfo);
  }
}
