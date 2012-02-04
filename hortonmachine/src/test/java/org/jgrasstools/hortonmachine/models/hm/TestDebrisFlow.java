///*
// * JGrass - Free Open Source Java GIS http://www.jgrass.org 
// * (C) HydroloGIS - www.hydrologis.com 
// * 
// * This library is free software; you can redistribute it and/or modify it under
// * the terms of the GNU Library General Public License as published by the Free
// * Software Foundation; either version 2 of the License, or (at your option) any
// * later version.
// * 
// * This library is distributed in the hope that it will be useful, but WITHOUT
// * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
// * FOR A PARTICULAR PURPOSE. See the GNU Library General Public License for more
// * details.
// * 
// * You should have received a copy of the GNU Library General Public License
// * along with this library; if not, write to the Free Foundation, Inc., 59
// * Temple Place, Suite 330, Boston, MA 02111-1307 USA
// */
//package org.jgrasstools.hortonmachine.models.hm;
//
//import org.geotools.coverage.grid.GridCoverage2D;
//import org.jgrasstools.gears.io.rasterreader.RasterReader;
//import org.jgrasstools.gears.io.rasterwriter.RasterWriter;
//import org.jgrasstools.gears.utils.HMTestCase;
//import org.jgrasstools.hortonmachine.modules.hydrogeomorphology.debrisflow.DebrisFlow;
///**
// * Test for the {@link DebrisFlow} module.
// * 
// * @author Andrea Antonello (www.hydrologis.com)
// */
//public class TestDebrisFlow extends HMTestCase {
//    public void testDebrisTrigger() throws Exception {
//
//        int m = 200;
//        int c = 50;
//        for( int i = 0; i < 10; i++ ) {
//            String inRasterPath = "C:/dati_gis/grassdata/utm_test_colata/test_colata2/cell/vegaia_pit_5.0_15.0";
//            String flowPath = "C:/dati_gis/grassdata/utm_test_colata/test_colata2/cell/mcs_" + i + "_5.0_15.0_" + m + "_" + c;
//            String depoPath = "C:/dati_gis/grassdata/utm_test_colata/test_colata2/cell/depo_" + i + "_5.0_15.0_" + m + "_" + c;
//            GridCoverage2D elev = RasterReader.readRaster(inRasterPath);
//            DebrisFlow dt = new DebrisFlow();
//            dt.inElev = elev;
//            dt.pMontecarlo = m;
//            dt.pMcoeff = c;
//            dt.pVolume = 25000;
//            dt.pEasting = 624826.2537;
//            dt.pNorthing = 5133433.7523;
//            dt.pm = pm;
//            dt.process();
//            GridCoverage2D outMcs = dt.outMcs;
//            GridCoverage2D outDepo = dt.outDepo;
//            RasterWriter.writeRaster(flowPath, outMcs);
//            RasterWriter.writeRaster(depoPath, outDepo);
//        }
//    }
//}
