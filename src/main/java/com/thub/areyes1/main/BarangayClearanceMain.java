/**
 * Class File Name: BarangayClearanceMain.java
 * Author: alvinreyes
 * Date Generate: Jun 7, 2015
 * Description
 */

package com.thub.areyes1.main;


import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;


import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;

import com.thub.areyes1.config.AppConfig;
import com.thub.areyes1.ui.BgyClearanceFrame;

 
/**
 * The Class BarangayClearanceMain.
 */
@Configuration
public class BarangayClearanceMain {

	/**
	 * The main BarangayClearanceMain constructor.
	 */
	public BarangayClearanceMain() {
		AnnotationConfigApplicationContext appCtx = new AnnotationConfigApplicationContext(AppConfig.class);
		final BgyClearanceFrame bgyClearanceFrame = appCtx.getBean(BgyClearanceFrame.class);
		bgyClearanceFrame.setSpringApplicationContext(appCtx);

		// Swing components must only be touched on the event dispatch thread.
		// Load the table before showing the frame so the first layout sees it.
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				bgyClearanceFrame.getAllBgyClearances();
				bgyClearanceFrame.setLocationByPlatform(true);
				bgyClearanceFrame.setLocationRelativeTo(null);
				bgyClearanceFrame.setVisible(true);
			}
		});
	}
	
	/**
	 * This is the main class.
	 *
	 * @param args the arguments
	 */
	public static void main(String[] args) {
		
		try {
			UIManager.setLookAndFeel(
		            UIManager.getSystemLookAndFeelClassName());
			}catch(UnsupportedLookAndFeelException e) {
				e.printStackTrace();
			}catch(ClassNotFoundException cnfe) {
				cnfe.printStackTrace();
			}catch(IllegalAccessException iae) {
				iae.printStackTrace();
			}catch(InstantiationException ie) {
				ie.printStackTrace();
			}
		
		new BarangayClearanceMain();
	}
}
