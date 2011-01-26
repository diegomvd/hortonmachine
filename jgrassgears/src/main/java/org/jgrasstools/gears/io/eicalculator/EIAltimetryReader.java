/*
 * JGrass - Free Open Source Java GIS http://www.jgrass.org 
 * (C) HydroloGIS - www.hydrologis.com 
 * 
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Library General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option) any
 * later version.
 * 
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Library General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU Library General Public License
 * along with this library; if not, write to the Free Foundation, Inc., 59
 * Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
package org.jgrasstools.gears.io.eicalculator;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import oms3.annotations.Author;
import oms3.annotations.Description;
import oms3.annotations.Execute;
import oms3.annotations.Finalize;
import oms3.annotations.In;
import oms3.annotations.Keywords;
import oms3.annotations.Label;
import oms3.annotations.License;
import oms3.annotations.Out;
import oms3.annotations.Role;
import oms3.annotations.Status;
import oms3.annotations.UI;

import org.jgrasstools.gears.libs.modules.JGTConstants;
import org.jgrasstools.gears.libs.modules.JGTModel;
import org.jgrasstools.gears.libs.monitor.IJGTProgressMonitor;
import org.jgrasstools.gears.libs.monitor.LogProgressMonitor;

@Description("Utility class for reading altimetry data from csv files.")
@Author(name = "Andrea Antonello", contact = "www.hydrologis.com")
@Keywords("IO, Reading")
@Label(JGTConstants.LIST_READER)
@Status(Status.CERTIFIED)
@License("http://www.gnu.org/licenses/gpl-3.0.html")
public class EIAltimetryReader extends JGTModel {
    @Description("The csv file to read from.")
    @UI(JGTConstants.FILE_UI_HINT)
    @In
    public String file = null;

    @Role(Role.PARAMETER)
    @Description("The csv separator.")
    @In
    public String pSeparator = ",";

    @Description("The progress monitor.")
    @In
    public IJGTProgressMonitor pm = new LogProgressMonitor();

    @Description("The read data.")
    @Out
    public List<EIAltimetry> outAltimetry;

    private BufferedReader csvReader;

    private void ensureOpen() throws IOException {
        if (csvReader == null)
            csvReader = new BufferedReader(new FileReader(file));
    }

    @Finalize
    public void close() throws IOException {
        csvReader.close();
    }

    @Execute
    public void read() throws IOException {
        if (!concatOr(outAltimetry == null, doReset)) {
            return;
        }
        ensureOpen();

        outAltimetry = new ArrayList<EIAltimetry>();
        String line = null;
        while( (line = csvReader.readLine()) != null ) {
            if (line.trim().length() == 0 || line.trim().startsWith("#")) {
                // jump empty lines and lines that start as comment
                continue;
            }
            String[] lineSplit = line.split(pSeparator);
            if (lineSplit.length > 4) {
                throw new IOException("Altimetry values are defined in 4 columns.");
            }

            EIAltimetry eiAltimetry = new EIAltimetry();
            eiAltimetry.basinId = Integer.parseInt(lineSplit[0].trim());
            eiAltimetry.altimetricBandId = Integer.parseInt(lineSplit[1].trim());
            eiAltimetry.elevationValue = Double.parseDouble(lineSplit[2].trim());
            eiAltimetry.bandRange = Double.parseDouble(lineSplit[3].trim());
            outAltimetry.add(eiAltimetry);
        }

    }

}
