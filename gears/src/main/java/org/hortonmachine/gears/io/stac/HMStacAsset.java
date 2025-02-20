package org.hortonmachine.gears.io.stac;

import java.util.Iterator;

import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.gce.geotiff.GeoTiffReader;
import org.hortonmachine.gears.libs.modules.HMConstants;
import org.hortonmachine.gears.utils.RegionMap;
import org.hortonmachine.gears.utils.coverage.CoverageUtilities;
import org.opengis.parameter.GeneralParameterValue;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.fasterxml.jackson.databind.JsonNode;

import it.geosolutions.imageio.core.BasicAuthURI;
import it.geosolutions.imageio.plugins.cog.CogImageReadParam;
import it.geosolutions.imageioimpl.plugins.cog.CogImageInputStreamSpi;
import it.geosolutions.imageioimpl.plugins.cog.CogImageReaderSpi;
import it.geosolutions.imageioimpl.plugins.cog.CogSourceSPIProvider;
import it.geosolutions.imageioimpl.plugins.cog.HttpRangeReader;

/**
 * An asset from a stac item.
 * 
 * @author Andrea Antonello (www.hydrologis.com)
 *
 */
public class HMStacAsset {

    private String title;
    private String type;
    private String nonValidReason;
    private boolean isValid = true;
    private String assetUrl;
    private double noValue = HMConstants.doubleNovalue;
    private double resolution;

    public HMStacAsset( JsonNode assetNode ) {
        JsonNode typeNode = assetNode.get("type");
        if (typeNode != null) {
            type = typeNode.textValue();
            // we only check cloud optimized datasets here
            JsonNode titleNode = assetNode.get("title");
            if (titleNode != null) {
                title = titleNode.textValue();
                if (type.toLowerCase().contains("profile=cloud-optimized")) {
                    JsonNode rasterBandNode = assetNode.get("raster:bands");
                    if (rasterBandNode != null && !rasterBandNode.isEmpty()) {
                        assetUrl = assetNode.get("href").textValue();

                        Iterator<JsonNode> rbIterator = rasterBandNode.elements();
                        while( rbIterator.hasNext() ) {
                            JsonNode rbNode = rbIterator.next();
                            JsonNode noValueNode = rbNode.get("nodata");
                            if (noValueNode != null) {
                                noValue = noValueNode.asDouble();
                            }
                            JsonNode resolNode = rbNode.get("spatial_resolution");
                            if (resolNode != null) {
                                resolution = resolNode.asDouble();
                            }
                        }
                    } else {
                        isValid = false;
                        nonValidReason = "raster bands metadata missing";
                    }
                } else {
                    isValid = false;
                    nonValidReason = "title information not available";
                }
            } else {
                isValid = false;
                nonValidReason = "not a COG";
            }
        } else {
            nonValidReason = "type information not available";
            isValid = false;
        }
    }

    /**
     * Read the asset's coverage into a local raster.
     * 
     * @param region and optional region to read from.
     * @param user an optional user in case of authentication.
     * @param password an optional password in case of authentication.
     * @return the read raster from the asset's url..
     * @throws Exception 
     */
    public GridCoverage2D readRaster( RegionMap region, String user, String password ) throws Exception {
        BasicAuthURI cogUri = new BasicAuthURI(assetUrl, false);
        if (user != null && password != null) {
            cogUri.setUser(user);
            cogUri.setPassword(password);
        }
        HttpRangeReader rangeReader = new HttpRangeReader(cogUri.getUri(), CogImageReadParam.DEFAULT_HEADER_LENGTH);
        CogSourceSPIProvider inputProvider = new CogSourceSPIProvider(cogUri, new CogImageReaderSpi(),
                new CogImageInputStreamSpi(), rangeReader.getClass().getName());
        GeoTiffReader reader = new GeoTiffReader(inputProvider);
        CoordinateReferenceSystem crs = reader.getCoordinateReferenceSystem();

        GeneralParameterValue[] generalParameter = null;
        if (region != null) {
            generalParameter = CoverageUtilities.createGridGeometryGeneralParameter(region, crs);
        }
        GridCoverage2D coverage = reader.read(generalParameter);
        return coverage;
    }

    public GridCoverage2D readRaster( RegionMap region ) throws Exception {
        return readRaster(region, null, null);
    }

    public String getTitle() {
        return title;
    }

    public String getType() {
        return type;
    }

    public boolean isValid() {
        return isValid;
    }

    public String getNonValidReason() {
        return nonValidReason;
    }

    public String getAssetUrl() {
        return assetUrl;
    }

    public double getNoValue() {
        return noValue;
    }

    public double getResolution() {
        return resolution;
    }
}
