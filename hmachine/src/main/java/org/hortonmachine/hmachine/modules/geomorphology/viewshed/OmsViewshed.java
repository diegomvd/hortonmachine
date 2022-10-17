/*
 * This file is part of HortonMachine (http://www.hortonmachine.org)
 * (C) HydroloGIS - www.hydrologis.com 
 * 
 * The HortonMachine is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.hortonmachine.hmachine.modules.geomorphology.viewshed;

import static org.hortonmachine.gears.libs.modules.HMConstants.GEOMORPHOLOGY;

import java.awt.image.WritableRaster;
import java.util.List;

import javax.media.jai.iterator.RandomIter;
import javax.media.jai.iterator.WritableRandomIter;

import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridGeometry2D;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.hortonmachine.gears.libs.modules.HMConstants;
import org.hortonmachine.gears.libs.modules.HMModel;
import org.hortonmachine.gears.utils.RegionMap;
import org.hortonmachine.gears.utils.coverage.CoverageUtilities;
import org.hortonmachine.gears.utils.features.FeatureUtilities;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;

import oms3.annotations.Author;
import oms3.annotations.Description;
import oms3.annotations.Documentation;
import oms3.annotations.Execute;
import oms3.annotations.In;
import oms3.annotations.Keywords;
import oms3.annotations.Label;
import oms3.annotations.License;
import oms3.annotations.Name;
import oms3.annotations.Out;
import oms3.annotations.Status;

@Description(OmsViewshed.DESCRIPTION)
@Documentation(OmsViewshed.DOC)
@Author(name = OmsViewshed.AUTHOR, contact = OmsViewshed.CONTACT)
@Keywords(OmsViewshed.KEYWORDS)
@Label(OmsViewshed.LABEL)
@Name(OmsViewshed.KEYWORDS)
@Status(OmsViewshed.STATUS)
@License(OmsViewshed.LICENSE)
public class OmsViewshed extends HMModel {

    @Description(DESCR_inRaster)
    @In
    public GridCoverage2D inRaster = null;

    @Description(DESCR_inViewPoints)
    @In
    public SimpleFeatureCollection inViewPoints = null;

    @Description(DESCR_pField)
    @In
    public String pField = "elev";

    @Description(DESCR_outViewshed)
    @Out
    public GridCoverage2D outViewshed = null;

    public static final String DOC = "Calculate a viewshed raster, with values based on the visibility by the supplied view points.";
    public static final String DESCR_outViewshed = "Output viewshed raster.";
    public static final String DESCR_pField = "Name of the field containing the station's height above the elevation model";
    public static final String DESCR_inViewPoints = "Input viewpoints collection.";
    public static final String DESCR_inRaster = "Input elevation raster.";
    public static final String LICENSE = HMConstants.GPL3_LICENSE;
    public static final String LABEL = GEOMORPHOLOGY;
    public static final int STATUS = Status.EXPERIMENTAL;
    public static final String KEYWORDS = "viewshed";
    public static final String CONTACT = "jlindsay@uoguelph.ca";
    public static final String AUTHOR = "Dr. John Lindsay";
    public static final String DESCRIPTION = "Viewshed module";

    @Execute
    public void process() throws Exception {
        checkNull(inRaster);
        RegionMap regionMap = CoverageUtilities.getRegionParamsFromGridCoverage(inRaster);
        int cols = regionMap.getCols();
        int rows = regionMap.getRows();

        double novalue = HMConstants.getNovalue(inRaster);

        RandomIter inIter = CoverageUtilities.getRandomIterator(inRaster);

        WritableRaster outViewshedWR = CoverageUtilities.createWritableRaster(cols, rows, null, null, novalue);
        WritableRandomIter outViewshedIter = CoverageUtilities.getWritableRandomIterator(outViewshedWR);

        WritableRaster viewAngleWR = CoverageUtilities.createWritableRaster(cols, rows, null, null, novalue);
        WritableRandomIter viewAngleIter = CoverageUtilities.getWritableRandomIterator(viewAngleWR);
        WritableRaster maxViewAngleWR = CoverageUtilities.createWritableRaster(cols, rows, null, null, novalue);
        WritableRandomIter maxViewAngleIter = CoverageUtilities.getWritableRandomIterator(maxViewAngleWR);

        List<SimpleFeature> viewPoints = FeatureUtilities.featureCollectionToList(inViewPoints);

        GridGeometry2D gg = inRaster.getGridGeometry();
        try {
            pm.beginTask("Processing viewpoints...", viewPoints.size());
            for( SimpleFeature feature : viewPoints ) {
                Geometry geom = (Geometry) feature.getDefaultGeometry();
                Coordinate c = geom.getCoordinate();
                double tmpZ = Double.parseDouble(feature.getAttribute(pField).toString());
                c.z = tmpZ;
                double value = CoverageUtilities.getValue(inRaster, c.x, c.y);
                if (!HMConstants.isNovalue(value, novalue)) {
                    pm.message("Working on viewpoint: " + c);
                    double stationX = c.x;
                    double stationY = c.y;
                    double stationZ = value + c.z;

                    int[] stationColRow = CoverageUtilities.colRowFromCoordinate(c, gg, null);
                    int stationCol = stationColRow[0];
                    int stationRow = stationColRow[1];

                    for( int row = 0; row < rows; row++ ) {
                        for( int col = 0; col < cols; col++ ) {
                            double z = inIter.getSampleDouble(col, row, 0);
                            if (!HMConstants.isNovalue(z, novalue)) {
                                Coordinate worldC = CoverageUtilities.coordinateFromColRow(col, row, gg);
                                double x = worldC.x;
                                double y = worldC.y;
                                double dZ = z - stationZ;
                                double dist = Math.sqrt((x - stationX) * (x - stationX) + (y - stationY) * (y - stationY));
                                if (dist != 0.0) {
                                    double viewAngleValue = dZ / dist * 1000;
                                    viewAngleIter.setSample(col, row, 0, viewAngleValue);
                                }
                            } else {
                                viewAngleIter.setSample(col, row, 0, novalue);
                            }
                        }
                    }

                    // perform the simple scan lines.
                    for( int row = stationRow - 1; row <= stationRow + 1; row++ ) {
                        for( int col = stationCol - 1; col <= stationCol + 1; col++ ) {
                            maxViewAngleIter.setSample(col, row, 0, viewAngleIter.getSampleDouble(col, row, 0));
                        }
                    }

                    double maxVA = viewAngleIter.getSampleDouble(stationCol, stationRow - 1, 0);
                    for( int row = stationRow - 2; row >= 0; row-- ) {
                        double z = viewAngleIter.getSampleDouble(stationCol, row, 0);
                        if (!HMConstants.isNovalue(z, novalue)) {
                            if (z > maxVA) {
                                maxVA = z;
                            }
                            maxViewAngleIter.setSample(stationCol, row, 0, maxVA);
                        }
                    }

                    maxVA = viewAngleIter.getSampleDouble(stationCol, stationRow + 1, 0);
                    for( int row = stationRow + 2; row < rows; row++ ) {
                        double z = viewAngleIter.getSampleDouble(stationCol, row, 0);
                        if (!HMConstants.isNovalue(z, novalue)) {
                            if (z > maxVA) {
                                maxVA = z;
                            }
                            maxViewAngleIter.setSample(stationCol, row, 0, maxVA);
                        }
                    }

                    maxVA = viewAngleIter.getSampleDouble(stationCol + 1, stationRow, 0);
                    for( int col = stationCol + 2; col < cols - 1; col++ ) {
                        double z = viewAngleIter.getSampleDouble(col, stationRow, 0);
                        if (!HMConstants.isNovalue(z, novalue)) {
                            if (z > maxVA) {
                                maxVA = z;
                            }
                            maxViewAngleIter.setSample(col, stationRow, 0, maxVA);
                        }
                    }

                    maxVA = viewAngleIter.getSampleDouble(stationCol - 1, stationRow, 0);
                    for( int col = stationCol - 2; col >= 0; col-- ) {
                        double z = viewAngleIter.getSampleDouble(col, stationRow, 0);
                        if (!HMConstants.isNovalue(z, novalue)) {
                            if (z > maxVA) {
                                maxVA = z;
                            }
                            maxViewAngleIter.setSample(col, stationRow, 0, maxVA);
                        }
                    }

                    // solve the first triangular facet
                    int vertCount = 0;
                    for( int row = stationRow - 2; row >= 0; row-- ) {
                        vertCount++;
                        int horizCount = 0;
                        for( int col = stationCol + 1; col <= stationCol + vertCount; col++ ) {
                            if (col >= 0 && col < cols) {
                                double va = viewAngleIter.getSampleDouble(col, row, 0);
                                if (!HMConstants.isNovalue(va, novalue)) {
                                    horizCount++;
                                    double tva;
                                    if (horizCount != vertCount) {
                                        double t1 = maxViewAngleIter.getSampleDouble(col - 1, row + 1, 0);
                                        double t2 = maxViewAngleIter.getSampleDouble(col, row + 1, 0);
                                        tva = t2 + horizCount / vertCount * (t1 - t2);
                                    } else {
                                        tva = maxViewAngleIter.getSampleDouble(col - 1, row + 1, 0);
                                    }
                                    if (tva > va) {
                                        maxViewAngleIter.setSample(col, row, 0, tva);
                                    } else {
                                        maxViewAngleIter.setSample(col, row, 0, va);
                                    }
                                }
                            } else {
                                break;
                            }
                        }
                    }

                    // solve the second triangular facet
                    vertCount = 1;
                    for( int row = stationRow - 2; row >= 0; row-- ) {
                        vertCount++;
                        int horizCount = 0;
                        for( int col = stationCol - 1; col >= stationCol - vertCount; col-- ) {
                            if (col >= 0 && col < cols) {
                                double va = viewAngleIter.getSampleDouble(col, row, 0);
                                if (!HMConstants.isNovalue(va, novalue)) {
                                    horizCount++;
                                    double tva;
                                    if (horizCount != vertCount) {
                                        double t1 = maxViewAngleIter.getSampleDouble(col + 1, row + 1, 0);
                                        double t2 = maxViewAngleIter.getSampleDouble(col, row + 1, 0);
                                        tva = t2 + horizCount / vertCount * (t1 - t2);
                                    } else {
                                        tva = maxViewAngleIter.getSampleDouble(col + 1, row + 1, 0);
                                    }
                                    if (tva > va) {
                                        maxViewAngleIter.setSample(col, row, 0, tva);
                                    } else {
                                        maxViewAngleIter.setSample(col, row, 0, va);
                                    }
                                }
                            } else {
                                break;
                            }
                        }
                    }

                    // solve the third triangular facet
                    vertCount = 1;
                    for( int row = stationRow + 2; row < rows; row++ ) {
                        vertCount++;
                        int horizCount = 0;
                        for( int col = stationCol - 1; col >= stationCol - vertCount; col-- ) {
                            if (col >= 0 && col < cols) {
                                double va = viewAngleIter.getSampleDouble(col, row, 0);
                                if (!HMConstants.isNovalue(va, novalue)) {
                                    horizCount++;
                                    double tva;
                                    if (horizCount != vertCount) {
                                        double t1 = maxViewAngleIter.getSampleDouble(col + 1, row - 1, 0);
                                        double t2 = maxViewAngleIter.getSampleDouble(col, row - 1, 0);
                                        tva = t2 + horizCount / vertCount * (t1 - t2);
                                    } else {
                                        tva = maxViewAngleIter.getSampleDouble(col + 1, row - 1, 0);
                                    }
                                    if (tva > va) {
                                        maxViewAngleIter.setSample(col, row, 0, tva);
                                    } else {
                                        maxViewAngleIter.setSample(col, row, 0, va);
                                    }
                                }
                            } else {
                                break;
                            }
                        }
                    }

                    // solve the fourth triangular facet
                    vertCount = 1;
                    for( int row = stationRow + 2; row < rows; row++ ) {
                        vertCount++;
                        int horizCount = 0;
                        for( int col = stationCol + 1; col <= stationCol + vertCount; col++ ) {
                            if (col >= 0 && col < cols) {
                                double va = viewAngleIter.getSampleDouble(col, row, 0);
                                if (!HMConstants.isNovalue(va, novalue)) {
                                    horizCount++;
                                    double tva;
                                    if (horizCount != vertCount) {
                                        double t1 = maxViewAngleIter.getSampleDouble(col - 1, row - 1, 0);
                                        double t2 = maxViewAngleIter.getSampleDouble(col, row - 1, 0);
                                        tva = t2 + horizCount / vertCount * (t1 - t2);
                                    } else {
                                        tva = maxViewAngleIter.getSampleDouble(col - 1, row - 1, 0);
                                    }
                                    if (tva > va) {
                                        maxViewAngleIter.setSample(col, row, 0, tva);
                                    } else {
                                        maxViewAngleIter.setSample(col, row, 0, va);
                                    }
                                }
                            } else {
                                break;
                            }
                        }
                    }

                    // solve the fifth triangular facet
                    vertCount = 1;
                    for( int col = stationCol + 2; col < cols; col++ ) {
                        vertCount++;
                        int horizCount = 0;
                        for( int row = stationRow - 1; row >= stationRow - vertCount; row-- ) {
                            if (row >= 0 && row < rows) {
                                double va = viewAngleIter.getSampleDouble(col, row, 0);
                                if (!HMConstants.isNovalue(va, novalue)) {
                                    horizCount++;
                                    double tva;
                                    if (horizCount != vertCount) {
                                        double t1 = maxViewAngleIter.getSampleDouble(col - 1, row + 1, 0);
                                        double t2 = maxViewAngleIter.getSampleDouble(col - 1, row, 0);
                                        tva = t2 + horizCount / vertCount * (t1 - t2);
                                    } else {
                                        tva = maxViewAngleIter.getSampleDouble(col - 1, row + 1, 0);
                                    }
                                    if (tva > va) {
                                        maxViewAngleIter.setSample(col, row, 0, tva);
                                    } else {
                                        maxViewAngleIter.setSample(col, row, 0, va);
                                    }
                                }
                            } else {
                                break;
                            }
                        }
                    }

                    // solve the sixth triangular facet
                    vertCount = 1;
                    for( int col = stationCol + 2; col < cols; col++ ) {
                        vertCount++;
                        int horizCount = 0;
                        for( int row = stationRow + 1; row <= stationRow + vertCount; row++ ) {
                            if (row >= 0 && row < rows) {
                                double va = viewAngleIter.getSampleDouble(col, row, 0);
                                if (!HMConstants.isNovalue(va, novalue)) {
                                    horizCount++;
                                    double tva;
                                    if (horizCount != vertCount) {
                                        double t1 = maxViewAngleIter.getSampleDouble(col - 1, row - 1, 0);
                                        double t2 = maxViewAngleIter.getSampleDouble(col - 1, row, 0);
                                        tva = t2 + horizCount / vertCount * (t1 - t2);
                                    } else {
                                        tva = maxViewAngleIter.getSampleDouble(col - 1, row - 1, 0);
                                    }
                                    if (tva > va) {
                                        maxViewAngleIter.setSample(col, row, 0, tva);
                                    } else {
                                        maxViewAngleIter.setSample(col, row, 0, va);
                                    }
                                }
                            } else {
                                break;
                            }
                        }
                    }

                    // solve the seventh triangular facet
                    vertCount = 1;
                    for( int col = stationCol - 2; col >= 0; col-- ) {
                        vertCount++;
                        int horizCount = 0;
                        for( int row = stationRow + 1; row <= stationRow + vertCount; row++ ) {
                            if (row >= 0 && row < rows) {
                                double va = viewAngleIter.getSampleDouble(col, row, 0);
                                if (!HMConstants.isNovalue(va, novalue)) {
                                    horizCount++;
                                    double tva;
                                    if (horizCount != vertCount) {
                                        double t1 = maxViewAngleIter.getSampleDouble(col + 1, row - 1, 0);
                                        double t2 = maxViewAngleIter.getSampleDouble(col + 1, row, 0);
                                        tva = t2 + horizCount / vertCount * (t1 - t2);
                                    } else {
                                        tva = maxViewAngleIter.getSampleDouble(col + 1, row - 1, 0);
                                    }
                                    if (tva > va) {
                                        maxViewAngleIter.setSample(col, row, 0, tva);
                                    } else {
                                        maxViewAngleIter.setSample(col, row, 0, va);
                                    }
                                }
                            } else {
                                break;
                            }
                        }
                    }

                    // solve the eigth triangular facet
                    vertCount = 1;
                    for( int col = stationCol - 2; col >= 0; col-- ) {
                        vertCount++;
                        int horizCount = 0;
                        for( int row = stationRow - 1; row >= stationRow - vertCount; row-- ) {
                            if (row >= 0 && row < rows) {
                                double va = viewAngleIter.getSampleDouble(col, row, 0);
                                if (!HMConstants.isNovalue(va, novalue)) {
                                    horizCount++;
                                    double tva;
                                    if (horizCount != vertCount) {
                                        double t1 = maxViewAngleIter.getSampleDouble(col + 1, row + 1, 0);
                                        double t2 = maxViewAngleIter.getSampleDouble(col + 1, row, 0);
                                        tva = t2 + horizCount / vertCount * (t1 - t2);
                                    } else {
                                        tva = maxViewAngleIter.getSampleDouble(col + 1, row + 1, 0);
                                    }
                                    if (tva > va) {
                                        maxViewAngleIter.setSample(col, row, 0, tva);
                                    } else {
                                        maxViewAngleIter.setSample(col, row, 0, va);
                                    }
                                }
                            } else {
                                break;
                            }
                        }
                    }

                    for( int row = 0; row < rows; row++ ) {
                        for( int col = 0; col < cols; col++ ) {
                            double viewAngle = viewAngleIter.getSampleDouble(col, row, 0);
                            double maxViewAngle = maxViewAngleIter.getSampleDouble(col, row, 0);

                            if (maxViewAngle <= viewAngle && !HMConstants.isNovalue(viewAngle, novalue)) {
                                double viewshed = outViewshedIter.getSampleDouble(col, row, 0);
                                if (HMConstants.isNovalue(viewshed, novalue)) {
                                    viewshed = 0;
                                }
                                outViewshedIter.setSample(col, row, 0, viewshed + 1);
//                            } else if (HMConstants.isNovalue(viewAngle, novalue)) {
//                                outViewshedIter.setSample(col, row, 0, novalue);
                            }
                        }
                    }

                    pm.worked(1);
                } else {
                    pm.errorMessage("Ignoring viewpoint " + c + " since no elevation value available.");
                    pm.worked(1);
                    continue;
                }

            }
            pm.done();

        } finally {
            inIter.done();
            viewAngleIter.done();
            maxViewAngleIter.done();
            outViewshedIter.done();
        }

        outViewshed = CoverageUtilities.buildCoverageWithNovalue(KEYWORDS, outViewshedWR, regionMap,
                inRaster.getCoordinateReferenceSystem(), novalue);
//        dumpRaster(CoverageUtilities.buildCoverageWithNovalue("viewangle", viewAngleWR, regionMap,
//                inRaster.getCoordinateReferenceSystem(), novalue), "/home/hydrologis/data/DTM_calvello/viewangle.asc");
//        dumpRaster(
//                CoverageUtilities.buildCoverageWithNovalue("maxviewangle", maxViewAngleWR, regionMap,
//                        inRaster.getCoordinateReferenceSystem(), novalue),
//                "/home/hydrologis/data/DTM_calvello/viewangle_max.asc");
    }

}
