/**
 * Class File Name: ReportUtil.java
 * Author: alvinreyes
 * Date Generate: Jun 15, 2015
 * Description
 */

package com.thub.areyes1.util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JRParameter;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import net.sf.jasperreports.engine.util.JRLoader;
import net.sf.jasperreports.view.JasperViewer;

import com.thub.areyes1.obj.BarangayClearance;

import static com.thub.areyes1.util.ReportConstants.*;

 
/**
 * This class is the utility class to generate jasper reports.
 * @author alvinreyes
 *
 */
public class ReportUtil {
	
	
	/**
	 * This method is used to call the generate the report and initialize the report viewer.
	 *
	 * @param bgyClearance the bgy clearance
	 */
	public static void generateReport(BarangayClearance bgyClearance) {
		
		try {
			//	Get the compiled report.
			FileInputStream fis = new FileInputStream(ReportUtil.class.getResource(CONST_REPORT_LOCATION + CONST_CLEARANCE_REP).getFile());
            BufferedInputStream bufferedInputStream = new BufferedInputStream(fis);
            
            //	Put up the parameters
            Map<String,Object> map = new HashMap<String, Object>();
            map.put("SAMPLE1", "SAMPLE1");
            map.put("SAMPLE2", "SAMPLE2");
            
            //	Load the jasper reports and fill it up with the parameters.
			JasperReport jasperReport = (JasperReport) JRLoader.loadObject(bufferedInputStream);
			JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, map);
			JasperViewer.viewReport(jasperPrint, false);
		      
		}catch(Exception e) {e.printStackTrace();}
		
	}
	
	/**
	 * This method is used to generate the jasper print report object only.
	 * The jasper print report object can be embedded on the swing component.
	 * 
	 * example:
	 *  JFrame frame = new JFrame("Report");
	 * 	frame.getContentPane().add(new JRViewer(jasperPrint));
	 *  frame.pack();
	 * 	frame.setVisible(true);
	 *
	 * @param bgyClearance the bgy clearance
	 * @return the jasper print
	 */
	public static JasperPrint generateJasperPrintReport(BarangayClearance bgyClearance) {
		
		try {
			InputStream in = openCompiledReport();
            BufferedInputStream bufferedInputStream = new BufferedInputStream(in);
			JasperReport jasperReport = (JasperReport) JRLoader.loadObject(bufferedInputStream);
			JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, withBlanks(jasperReport, bgyClearance.getData()), new JREmptyDataSource());
			
			return jasperPrint;
		      
		}catch(Exception e) {e.printStackTrace(); return null;}
		
	}

	/**
	 * Generates the clearance report as a PDF document.
	 *
	 * @param bgyClearance the bgy clearance
	 * @return the PDF bytes, or null if the report could not be generated
	 */
	public static byte[] generatePdfReport(BarangayClearance bgyClearance) {
		JasperPrint jasperPrint = generateJasperPrintReport(bgyClearance);
		if (jasperPrint == null) {
			return null;
		}
		try {
			return JasperExportManager.exportReportToPdf(jasperPrint);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Opens the compiled report from REPORT_LOCATION when it is set, otherwise
	 * from the copy bundled on the classpath.
	 *
	 * @return the report stream
	 * @throws IOException if the report cannot be found
	 */
	private static InputStream openCompiledReport() throws IOException {
		if (CONST_REPORT_LOCATION != null) {
			return new FileInputStream(new File(CONST_REPORT_LOCATION + CONST_CLEARANCE_REP));
		}
		InputStream in = ReportUtil.class.getResourceAsStream("/report/" + CONST_CLEARANCE_REP);
		if (in == null) {
			throw new FileNotFoundException("report/" + CONST_CLEARANCE_REP + " not found on the classpath");
		}
		return in;
	}

	/**
	 * The template prints unset String parameters as "null", so fill them with
	 * empty text instead.
	 *
	 * @param report the report
	 * @param data the clearance data
	 * @return a copy of the data with every text parameter present
	 */
	private static Map<String, Object> withBlanks(JasperReport report, Map<String, Object> data) {
		Map<String, Object> params = new HashMap<String, Object>(data);
		for (JRParameter p : report.getParameters()) {
			if (!p.isSystemDefined() && String.class.equals(p.getValueClass()) && params.get(p.getName()) == null) {
				params.put(p.getName(), "");
			}
		}
		return params;
	}

}
