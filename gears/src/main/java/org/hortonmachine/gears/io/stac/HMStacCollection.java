package org.hortonmachine.gears.io.stac;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.filter.text.cql2.CQL;
import org.geotools.filter.text.cql2.CQLException;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.geotools.stac.client.Collection;
import org.geotools.stac.client.CollectionExtent;
import org.geotools.stac.client.CollectionExtent.TemporalExtents;
import org.geotools.stac.client.STACClient;
import org.geotools.stac.client.SearchQuery;
import org.hortonmachine.gears.libs.modules.HMRaster;
import org.hortonmachine.gears.libs.modules.HMRaster.HMRasterWritableBuilder;
import org.hortonmachine.gears.libs.monitor.DummyProgressMonitor;
import org.hortonmachine.gears.libs.monitor.IHMProgressMonitor;
import org.hortonmachine.gears.utils.CrsUtilities;
import org.hortonmachine.gears.utils.RegionMap;
import org.hortonmachine.gears.utils.geometry.GeometryUtilities;
import org.hortonmachine.gears.utils.time.UtcTimeUtilities;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.union.CascadedPolygonUnion;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

@SuppressWarnings({"rawtypes"})
/**
 * A stac collection.
 * 
 * @author Andrea Antonello (www.hydrologis.com)
 *
 */
public class HMStacCollection {
    private STACClient stacClient;
    private Collection collection;
    private SearchQuery search;
    private IHMProgressMonitor pm;

    HMStacCollection( STACClient stacClient, Collection collection, IHMProgressMonitor pm ) {
        this.stacClient = stacClient;
        this.collection = collection;
        if (pm == null)
            pm = new DummyProgressMonitor();
        this.pm = pm;
    }

    public String getId() {
        return collection.getId();
    }

    public String getType() {
        return collection.getType();
    }

    public ReferencedEnvelope getSpatialBounds() {
        // CollectionExtent extent = c.getExtent();
        // The following spatial extent does not work well, leaving it for reference
        // SpatialExtents spatial = extent.getSpatial();
        // List<List<Double>> bbox = spatial.getBbox();
        // String crs = spatial.getCrs();

        // better to get the bounds from the collection directly
        return collection.getBounds();
    }

    public List<Date> getTemporalBounds() {
        CollectionExtent extent = collection.getExtent();
        TemporalExtents temporal = extent.getTemporal();
        List<List<Date>> interval = temporal.getInterval();
        return interval.get(0); // TODO check how to make this better
    }

    /**
     * Set temporal filter for search query;
     * 
     * @param startTimestamp
     * @param endTimestamp
     * @return the current collection.
     */
    public HMStacCollection setTimestampFilter( Date startTimestamp, Date endTimestamp ) {
        if (search == null)
            search = new SearchQuery();
        search.setDatetime(HMStacUtils.filterTimestampFormatter.format(startTimestamp) + "/"
                + HMStacUtils.filterTimestampFormatter.format(endTimestamp));
        return this;
    }

    /**
     * Set geometry intersection filter for search query;
     * 
     * @param intersectionGeometry
     * @return the current collection.
     */
    public HMStacCollection setGeometryFilter( Geometry intersectionGeometry ) {
        if (search == null)
            search = new SearchQuery();
        search.setIntersects(intersectionGeometry);
        return this;
    }

    /**
     * Set cql filter for search query;
     * 
     * @param cqlFilter
     * @return the current collection.
     * @throws CQLException
     */
    public HMStacCollection setCqlFilter( String cqlFilter ) throws CQLException {
        if (search == null)
            search = new SearchQuery();
        search.setFilter(CQL.toFilter(cqlFilter));
        return this;
    }

    public List<HMStacItem> searchItems() throws Exception {
        if (search == null)
            search = new SearchQuery();
        search.setCollections(Arrays.asList(getId()));

        SimpleFeatureCollection fc = stacClient.search(search, STACClient.SearchMode.GET);
        int size = fc.size();
        SimpleFeatureIterator iterator = fc.features();
        pm.beginTask("Extracting items...", size);
        List<HMStacItem> stacItems = new ArrayList<>();
        while( iterator.hasNext() ) {
            SimpleFeature f = iterator.next();
            HMStacItem item = new HMStacItem(f);
            if (item.getEpsg() != null) {
                stacItems.add(item);
            }
            pm.worked(1);
        }
        iterator.close();
        pm.done();
        return stacItems;
    }

    /**
     * Read all the raster of a certain band from the items list and merge them to a single raster sized on the given region and resolution.
     * 
     * @param latLongRegionMap the region to use for the final raster.
     * @param bandName the name o the band to extract.
     * @param items the list of items containing the various assets to read from.
     * @return the final raster.
     * @throws Exception
     */
    public static HMRaster readRasterBandOnRegion( RegionMap latLongRegionMap, String bandName, List<HMStacItem> items,
            IHMProgressMonitor pm ) throws Exception {
        Integer srid = items.get(0).getEpsg();
        CoordinateReferenceSystem outputCrs = CrsUtilities.getCrsFromSrid(srid);
        ReferencedEnvelope roiEnvelope = new ReferencedEnvelope(latLongRegionMap.toEnvelope(), DefaultGeographicCRS.WGS84)
                .transform(outputCrs, true);
        Polygon latLongRegionGeometry = GeometryUtilities.createPolygonFromEnvelope(roiEnvelope);

        int cols = latLongRegionMap.getCols();
        int rows = latLongRegionMap.getRows();

        HMRaster outRaster = null;

        String fileName = null;
        pm.beginTask("Reading " + bandName + "...", items.size());
        for( HMStacItem item : items ) {
            int currentSrid = item.getEpsg();
            if (srid != currentSrid) {
                throw new IOException("Epsgs are different");
            }
            Geometry geometry = item.getGeometry();
            Geometry intersection = geometry.intersection(latLongRegionGeometry);
            Envelope readEnvelope = intersection.getEnvelopeInternal();

            CoordinateReferenceSystem dataCrs = CrsUtilities.getCrsFromSrid(currentSrid);
            ReferencedEnvelope roiEnv = new ReferencedEnvelope(readEnvelope, DefaultGeographicCRS.WGS84).transform(dataCrs, true);

            RegionMap readRegion = RegionMap.fromBoundsAndGrid(roiEnv.getMinX(), roiEnv.getMaxX(), roiEnv.getMinY(),
                    roiEnv.getMaxY(), cols, rows);

            HMStacAsset asset = item.getAssets().stream().filter(as -> as.getTitle().equals(bandName)).findFirst().get();
            int lastSlash = asset.getAssetUrl().lastIndexOf('/');
            fileName = asset.getAssetUrl().substring(lastSlash + 1);
            if (outRaster == null) {
                outRaster = new HMRasterWritableBuilder().setName(fileName).setRegion(latLongRegionMap).setCrs(outputCrs)
                        .setNoValue(asset.getNoValue()).build();
            }
            GridCoverage2D readRaster = asset.readRaster(readRegion);
            outRaster.mapRaster(null, HMRaster.fromGridCoverage(readRaster), true);
            pm.worked(1);
        }
        pm.done();

        return outRaster;
    }

    public static Geometry getCoveredArea( List<HMStacItem> items ) {
        List<Geometry> geometries = items.stream().map(item -> item.getGeometry()).collect(Collectors.toList());
        Geometry union = CascadedPolygonUnion.union(geometries);
        return union;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("id = ").append(getId()).append("\n");
        sb.append("type = ").append(getType()).append("\n");
        String boundsString = HMStacUtils.simplify(getSpatialBounds());
        sb.append("spatial extent: " + boundsString).append("\n");

        List<Date> temporalBounds = getTemporalBounds();
        int size = temporalBounds.size();
        if (size > 0) {
            String from = UtcTimeUtilities.quickToString(temporalBounds.get(0).getTime());
            String to = " - ";
            if (size > 1) {
                Date toDate = temporalBounds.get(size - 1);
                if (toDate != null) {
                    to = UtcTimeUtilities.quickToString(toDate.getTime());
                }
            }
            sb.append("temporal extent: from " + from + " to " + to);
        }
        return sb.toString();
    }

}