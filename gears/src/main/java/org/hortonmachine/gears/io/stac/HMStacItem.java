package org.hortonmachine.gears.io.stac;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.data.geojson.GeoJSONReader;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.hortonmachine.gears.libs.modules.HMRaster;
import org.hortonmachine.gears.libs.monitor.IHMProgressMonitor;
import org.hortonmachine.gears.utils.CrsUtilities;
import org.hortonmachine.gears.utils.RegionMap;
import org.hortonmachine.gears.utils.geometry.GeometryUtilities;
import org.hortonmachine.gears.utils.time.ETimeUtilities;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The items from a collection.
 * 
 * @author Andrea Antonello (www.hydrologis.com)
 *
 */
@SuppressWarnings("unchecked")
public class HMStacItem {
    private String id;
    private Geometry geometry;
    private SimpleFeature feature;
    private Integer epsg;
    private Date dateCet;
    private Date creationDateCet;

    HMStacItem( SimpleFeature feature ) throws Exception {
        this.feature = feature;
        Map<String, JsonNode> top = (Map<String, JsonNode>) feature.getUserData().get(GeoJSONReader.TOP_LEVEL_ATTRIBUTES);
        id = top.get("id").textValue();

        String dateCetStr = feature.getAttribute("datetime").toString();
        if (dateCetStr != null) {
            dateCet = HMStacUtils.dateFormatter.parse(dateCetStr);
        }
        String creationDateCetStr = feature.getAttribute("created").toString();
        if (creationDateCetStr != null) {
            creationDateCet = HMStacUtils.dateFormatter.parse(creationDateCetStr);
        }
        Object epsgObj = feature.getAttribute("proj:epsg");
        if (epsgObj instanceof Integer) {
            epsg = (Integer) epsgObj;
        }
        geometry = (Geometry) feature.getDefaultGeometry();
    }

    public String getId() {
        return id;
    }

    public String getTimestamp() {
        return ETimeUtilities.INSTANCE.TIME_FORMATTER_UTC.format(dateCet);
    }

    public String getCreationTimestamp() {
        return ETimeUtilities.INSTANCE.TIME_FORMATTER_UTC.format(creationDateCet);
    }

    public Geometry getGeometry() {
        return geometry;
    }

    public Integer getEpsg() {
        return epsg;
    }

    public List<HMStacAsset> getAssets() {
        List<HMStacAsset> assetsList = new ArrayList<>();
        Map<String, JsonNode> top = (Map<String, JsonNode>) feature.getUserData().get(GeoJSONReader.TOP_LEVEL_ATTRIBUTES);
        ObjectNode assets = (ObjectNode) top.get("assets");

        Iterator<JsonNode> assetsIterator = assets.elements();
        while( assetsIterator.hasNext() ) {
            JsonNode assetNode = assetsIterator.next();
            HMStacAsset hmAsset = new HMStacAsset(assetNode);
            if (hmAsset.isValid()) {
                assetsList.add(hmAsset);
            }
        }
        return assetsList;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("id = " + id).append("\n");
        sb.append("geom = " + geometry).append("\n");
        sb.append("timestamp = " + getTimestamp()).append("\n");
        sb.append("creation timestamp = " + getCreationTimestamp()).append("\n");
        String metadataSummary = getMetadataSummary(feature, "");
        sb.append(metadataSummary);
        return sb.toString();
    }

    public HMStacAsset getAssetForBand( String bandName ) {
        return getAssets().stream().filter(as -> as.getTitle().equals(bandName)).findFirst().get();
    }

    private String getMetadataSummary( SimpleFeature f, String indent ) {
        StringBuilder sb = new StringBuilder();
        sb.append(indent + getAttribute(f, "updated"));
        sb.append(indent + getAttribute(f, "platform"));
        sb.append(indent + getAttribute(f, "constellation"));
        sb.append(indent + getAttribute(f, "proj:epsg"));
        return sb.toString();
    }

    private String getAttribute( SimpleFeature f, String name ) {
        Object attribute = f.getAttribute(name);
        if (attribute != null) {
            return name + " = " + attribute.toString() + "\n";
        }
        return "";
    }

}
