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
package org.hortonmachine.gears.io.remotesensing;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.hortonmachine.gears.io.netcdf.INetcdfUtils;
import org.hortonmachine.gears.io.netcdf.OmsNetcdf2GridCoverageConverter;
import org.hortonmachine.gears.io.rasterwriter.OmsRasterWriter;
import org.hortonmachine.gears.libs.exceptions.ModelsIOException;
import org.hortonmachine.gears.libs.modules.HMConstants;
import org.hortonmachine.gears.libs.modules.HMModel;
import org.hortonmachine.gears.modules.r.mosaic.OmsMosaic;
import org.hortonmachine.gears.utils.SldUtilities;
import org.hortonmachine.gears.utils.coverage.CoverageUtilities;
import org.hortonmachine.gears.utils.files.FileUtilities;
import org.locationtech.jts.geom.Envelope;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import oms3.annotations.Author;
import oms3.annotations.Description;
import oms3.annotations.Execute;
import oms3.annotations.In;
import oms3.annotations.Initialize;
import oms3.annotations.Keywords;
import oms3.annotations.Label;
import oms3.annotations.License;
import oms3.annotations.Name;
import oms3.annotations.Out;
import oms3.annotations.Status;
import oms3.annotations.UI;

@Description("Download MODIS data and patch dem together to coverages.")
@Author(name = "Antonello Andrea", contact = "http://www.hydrologis.com")
@Keywords("modis")
@Label(HMConstants.NETCDF)
@Name("omsmodisdownloader")
@Status(40)
@License("General Public License Version 3 (GPLv3)")
public class OmsModisDownloader extends HMModel implements INetcdfUtils {
    @Description(DESCR_pIncludePattern)
    @In
    public String pIncludePattern = null;

    @Description(DESCR_pExcludePattern)
    @In
    public String pExcludePattern = null;

    @Description(DESCR_pIntermediateDownloadFolder)
    @In
    public String pIntermediateDownloadFolder = null;

    @Description(DESCR_pDay)
    @In
    public String pDay = null;

    @Description(DESCR_pDownloadUrl)
    @In
    public String pDownloadUrl = "https://e4ftl01.cr.usgs.gov";

    @Description(DESCR_pUser)
    @In
    public String pUser = null;

    @Description(DESCR_pPassword)
    @In
    public String pPassword;

    @Description(DESCR_pProductPath)
    @UI("combo:MOLA,MOLT,MOTA")
    @In
    public String pProductPath = "MOTA";

    @Description(DESCR_pProduct)
    @In
    public String pProduct = null;

    @Description(DESCR_pVersion)
    @In
    public String pVersion = "006";

    @Description(DESCR_pRoiEnvelope)
    @In
    public org.locationtech.jts.geom.Envelope pRoi = null;

    @Description(DESCR_outRaster)
    @Out
    public GridCoverage2D outRaster;

    public static final String DESCR_pDay = "The download day in format YYYY-MM-DD.";
    public static final String DESCR_pDownloadUrl = "The url to download the data from.";
    public static final String DESCR_pProductPath = "The url path defining the type of product.";
    public static final String DESCR_pProduct = "The url part defining the product.";
    public static final String DESCR_pVersion = "The data version.";
    public static final String DESCR_pRoiEnvelope = "The envelope in to extract in EPSG:4326.";
    public static final String DESCR_pIncludePattern = "In case of no grid name, an inclusion pattern can be used.";
    public static final String DESCR_pExcludePattern = "In case of no grid name, an exclusion pattern can be used.";
    public static final String DESCR_outRaster = "The output raster, patched from the downoaded tiles.";
    public static final String DESCR_pUser = "The user registered to the modis website.";
    public static final String DESCR_pPassword = "The user registered to the modis website.";
    public static final String DESCR_pIntermediateDownloadFolder = "A folder in which to download the tiles to be patched.";

    private DecimalFormat df = new DecimalFormat("00");

    @Initialize
    @Execute
    public void process() throws Exception {
        checkNull(pDownloadUrl, pProductPath, pProduct, pVersion, pRoi, pUser, pPassword);

        if (pIntermediateDownloadFolder == null) {
            File temporaryFolder = FileUtilities.createTemporaryFolder("hm-modis-downloader");
            pIntermediateDownloadFolder = temporaryFolder.getAbsolutePath();
        }

        CookieHandler.setDefault(new CookieManager(null, CookiePolicy.ACCEPT_ALL));

        Authenticator.setDefault(new Authenticator(){
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(pUser, pPassword.toCharArray());
            }
        });

        String baseTilesDefUrl = "https://lpdaacsvc.cr.usgs.gov/services/tilemap?";
        String from = baseTilesDefUrl + "product=" + pProduct + "&longitude=" + pRoi.getMinX() + "&latitude=" + pRoi.getMinY();
        String to = baseTilesDefUrl + "product=" + pProduct + "&longitude=" + pRoi.getMaxX() + "&latitude=" + pRoi.getMaxY();

        pm.beginTask("Gathering tile ranges to download...", 2);
        int[] llHv = getTiles(from);
        pm.worked(1);
        int[] urHv = getTiles(to);
        pm.done();
        pm.message("\tHorizontal: " + llHv[0] + " -> " + urHv[0]);
        pm.message("\tVertical: " + urHv[1] + " -> " + llHv[1]);

        List<String> tilesList = new ArrayList<>();
        pm.message("Tiles selected for download:");
        for( int v = urHv[1]; v <= llHv[1]; v++ ) {
            for( int h = llHv[0]; h <= urHv[0]; h++ ) {
                String tile = "h" + df.format(h) + "v" + df.format(v);
                tilesList.add(tile);
                pm.message("\t" + tile);
            }
        }

        String daysListUrl = pDownloadUrl + "/" + pProductPath + "/" + pProduct + "." + pVersion;
        String daysListPage = getWebpageString(daysListUrl);
        String[] linesSplit = daysListPage.split("img src=");
        List<String> datesList = new ArrayList<>();
        for( String line : linesSplit ) {
            if (line.contains("\"/icons/folder.gif")) {
                String tmp = line.split("<a href=.{13}>")[1];
                String dateString = tmp.split("/</a>")[0];
                datesList.add(dateString);
            }
        }
        if (datesList.size() == 0) {
            throw new ModelsIOException("Could not retrieve the available dates at " + daysListUrl, this);

        }
        Collections.sort(datesList);

        pDay = pDay.replace("-", ".");
        int todayIndex = datesList.indexOf(pDay);
        if (todayIndex < 0) {
            throw new ModelsIOException("Date not available: " + pDay, this);
        }

        pm.message("Extracting day: " + pDay);
        String dayDataUrl = daysListUrl + "/" + pDay;
        String dayDataPage = getWebpageString(dayDataUrl);

        pm.beginTask("Extracting day " + pDay + "...", tilesList.size());
        List<File> downloadedFiles = new ArrayList<>();
        String[] tmp = dayDataPage.split("img src");
        for( String line : tmp ) {
            if (line.contains(pProduct) && containsOneOf(line, tilesList) && !line.contains(".jpg")) {
                String fileNameToDownload = line.trim().split("href=\"")[1].split("\"")[0];
                String downloadUrlPath = dayDataUrl + "/" + fileNameToDownload;

                String downloadPath = pIntermediateDownloadFolder + File.separator + fileNameToDownload;
                File downloadFile = new File(downloadPath);
                if (fileNameToDownload.endsWith(".hdf")) {
                    downloadedFiles.add(downloadFile);
                }
                if (!downloadFile.exists()) {
                    pm.message("Downloading: " + fileNameToDownload);
                    pm.message("from url: " + downloadUrlPath);

                    URL downloadUrl = new URL(downloadUrlPath);
                    try (BufferedInputStream inputStream = new BufferedInputStream(downloadUrl.openStream());
                            FileOutputStream fileOS = new FileOutputStream(downloadFile)) {
                        byte data[] = new byte[1024];
                        int byteContent;
                        while( (byteContent = inputStream.read(data, 0, 1024)) != -1 ) {
                            fileOS.write(data, 0, byteContent);
                        }
                    }
                } else {
                    pm.errorMessage("Not downloading " + fileNameToDownload + " because it already exists.");
                }
                pm.worked(1);
            }
        }

        pm.done();

        // now convert files and patch them into a geotiff
        List<GridCoverage2D> coverages = new ArrayList<>();
        int fc = 0;
        for( File file : downloadedFiles ) {
            OmsNetcdf2GridCoverageConverter converter = new OmsNetcdf2GridCoverageConverter();
            converter.pm = pm;
            converter.inPath = file.getAbsolutePath();
            converter.pIncludePattern = pIncludePattern;
            converter.pExcludePattern = pExcludePattern;
            converter.initProcess();
            converter.process();
            coverages.add(converter.outRaster);
            OmsRasterWriter.writeRaster(pIntermediateDownloadFolder + File.separator + "final_" + fc + ".tif",
                    converter.outRaster);
            fc++;
        }

        OmsMosaic mosaic = new OmsMosaic();
        mosaic.inCoverages = coverages;
        mosaic.pm = pm;
        mosaic.process();
        outRaster = mosaic.outRaster;
        OmsRasterWriter.writeRaster(pIntermediateDownloadFolder + File.separator + "final_patched.tif", outRaster);

        outRaster = CoverageUtilities.clipCoverage(outRaster,
                new ReferencedEnvelope(pRoi, outRaster.getCoordinateReferenceSystem()));

    }

    private boolean containsOneOf( String string, List<String> list ) {
        for( String item : list ) {
            if (string.contains(item)) {
                return true;
            }
        }
        return false;
    }

    private int[] getTiles( String urlPath )
            throws MalformedURLException, ParserConfigurationException, SAXException, IOException {
        URL url = new URL(urlPath);
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(url.openStream());
        Element documentElement = doc.getDocumentElement();
        NodeList childNodes = documentElement.getChildNodes();
        String hStr = null;
        String vStr = null;
        for( int i = 0; i < childNodes.getLength(); i++ ) {
            Node node = childNodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element elem = (Element) node;
                String tagName = elem.getTagName();
                if (tagName.equals("horizontal")) {
                    hStr = elem.getTextContent();
                } else if (tagName.equals("vertical")) {
                    vStr = elem.getTextContent();
                }
                if (hStr != null && vStr != null) {
                    break;
                }
            }
        }
        return new int[]{Integer.parseInt(hStr), Integer.parseInt(vStr)};
    }

    private String getWebpageString( String urlString ) throws Exception {
        URL url = new URL(urlString);
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()))) {
            String line;
            while( (line = reader.readLine()) != null ) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    public static void main( String[] args ) throws Exception {
        OmsModisDownloader md = new OmsModisDownloader();
        md.pRoi = new Envelope(9.0, 16.0, 43.0, 50.0);
//        md.pRoi = new Envelope(9.0, 14.0, 43.0, 50.0);
        md.pProductPath = "MOTA";
        md.pProduct = "MCD43A4";
        md.pVersion = "006";
        md.pDay = "2022-09-26";
        md.pIncludePattern = "_Band7";
        md.pExcludePattern = "_Quality_Band7";

        md.pIntermediateDownloadFolder = "/home/hydrologis/TMP/KLAB/MODIS/downloads/";
        md.process();
        GridCoverage2D finalRaster = md.outRaster;
        OmsRasterWriter.writeRaster("/home/hydrologis/TMP/KLAB/MODIS/downloads/final.tif", finalRaster);
    }

}
