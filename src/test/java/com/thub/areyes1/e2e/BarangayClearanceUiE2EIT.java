/**
 * Class File Name: BarangayClearanceUiE2EIT.java
 * Description: End-to-end tests that launch the Swing app and drive it like a user.
 */

package com.thub.areyes1.e2e;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeFalse;

import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.awt.event.MouseEvent;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.AbstractButton;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

import net.sf.jasperreports.view.JasperViewer;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.thub.areyes1.main.BarangayClearanceMain;
import com.thub.areyes1.ui.BgyClearanceFrame;
import com.thub.areyes1.ui.BgyClearanceRegistrationDialog;

/**
 * Needs a display. Run with {@code xvfb-run -a mvn verify} on a machine without
 * one; in a headless JVM these tests are skipped, not failed.
 */
public class BarangayClearanceUiE2EIT {

	private static final long TIMEOUT_MS = 15000;

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private File db;

	@Before
	public void setUp() throws Exception {
		assumeFalse("no display available; run under xvfb-run", GraphicsEnvironment.isHeadless());
		db = E2eEnvironment.useFreshDatabase(tmp.getRoot());
	}

	@After
	public void tearDown() throws Exception {
		SwingUtilities.invokeAndWait(new Runnable() {
			public void run() {
				for (Window w : Window.getWindows()) {
					w.dispose();
				}
			}
		});
	}

	@Test
	public void launchListsExistingClearances() throws Exception {
		E2eEnvironment.insertRow(db, "Aling Nena Sari-Sari Store", "12 Rizal St.", 2001, "150.0");
		E2eEnvironment.insertRow(db, "Kusina ni Lola", "3 Mabini St.", 2002, "300.0");

		final JTable table = named(launchApp(), "clearanceTable", JTable.class);

		assertEquals(2, rowCount(table));
		assertEquals("Business Name", onEdt(new Callable<String>() {
			public String call() {
				return table.getColumnName(2);
			}
		}));
		// Rows are inserted at the top, so the newest record is first.
		assertEquals("Kusina ni Lola", cell(table, 0, 2));
		assertEquals("2002", cell(table, 0, 1));
		assertEquals("Aling Nena Sari-Sari Store", cell(table, 1, 2));
	}

	@Test
	public void registeringANewClearanceSavesItAndOpensTheReport() throws Exception {
		BgyClearanceFrame frame = launchApp();

		BgyClearanceRegistrationDialog dialog = clickNew(frame);
		click(named(dialog, "newRadio", AbstractButton.class));
		type(named(dialog, "controlNumberTxt", JTextComponent.class), "3001");
		type(named(dialog, "businessNameTxt", JTextComponent.class), "Tindahan ni Mang Tomas");
		type(named(dialog, "addressTxt", JTextComponent.class), "45 Bonifacio Ave.");
		type(named(dialog, "amountPaidTxt", JTextComponent.class), "275.50");
		click(named(dialog, "saveButton", AbstractButton.class));

		assertNotNull("report viewer should open after saving", waitForWindow(JasperViewer.class));
		assertFalse("registration dialog should close after saving", onEdt(isShowing(dialog)));

		List<Map<String, String>> rows = E2eEnvironment.rows(db);
		assertEquals(1, rows.size());
		assertEquals("Tindahan ni Mang Tomas", rows.get(0).get("name"));
		assertEquals("45 Bonifacio Ave.", rows.get(0).get("address"));
		assertEquals("3001", rows.get(0).get("control_no"));
		assertEquals("275.5", rows.get(0).get("amount_paid"));
		assertEquals("1", rows.get(0).get("new"));
	}

	@Test
	public void newlySavedClearanceAppearsInTheTable() throws Exception {
		E2eEnvironment.insertRow(db, "Existing Shop", "1 Main St.", 3100, "10.0");
		BgyClearanceFrame frame = launchApp();
		JTable table = named(frame, "clearanceTable", JTable.class);
		assertEquals(1, rowCount(table));

		BgyClearanceRegistrationDialog dialog = clickNew(frame);
		type(named(dialog, "controlNumberTxt", JTextComponent.class), "3101");
		type(named(dialog, "businessNameTxt", JTextComponent.class), "Brand New Shop");
		click(named(dialog, "saveButton", AbstractButton.class));

		waitForRowCount(table, 2);
		assertEquals("Brand New Shop", cell(table, 0, 2));
		assertEquals("3101", cell(table, 0, 1));
	}

	@Test
	public void renewalIsSavedAsNotNew() throws Exception {
		BgyClearanceRegistrationDialog dialog = clickNew(launchApp());
		click(named(dialog, "renewalRadio", AbstractButton.class));
		type(named(dialog, "businessNameTxt", JTextComponent.class), "Renewing Shop");
		click(named(dialog, "saveButton", AbstractButton.class));
		waitForWindow(JasperViewer.class);

		List<Map<String, String>> rows = E2eEnvironment.rows(db);
		assertEquals(1, rows.size());
		assertEquals("Renewing Shop", rows.get(0).get("name"));
		assertEquals("0", rows.get(0).get("new"));
	}

	@Test
	public void cancellingANewRegistrationSavesNothing() throws Exception {
		BgyClearanceFrame frame = launchApp();

		BgyClearanceRegistrationDialog dialog = clickNew(frame);
		type(named(dialog, "businessNameTxt", JTextComponent.class), "Never Saved");
		click(named(dialog, "cancelButton", AbstractButton.class));

		assertFalse(onEdt(isShowing(dialog)));
		assertEquals(0, E2eEnvironment.rows(db).size());
		assertEquals(0, rowCount(named(frame, "clearanceTable", JTable.class)));
	}

	@Test
	public void doubleClickingARowOpensItForEditing() throws Exception {
		E2eEnvironment.insertRow(db, "Persistent Pharmacy", "7 Luna St.", 4001, "99.0");
		JTable table = named(launchApp(), "clearanceTable", JTable.class);

		BgyClearanceRegistrationDialog dialog = doubleClickFirstRow(table);

		assertEquals("Persistent Pharmacy", text(named(dialog, "businessNameTxt", JTextComponent.class)));
		assertEquals("7 Luna St.", text(named(dialog, "addressTxt", JTextComponent.class)));
		assertEquals("99.0", text(named(dialog, "amountPaidTxt", JTextComponent.class)));
		click(named(dialog, "cancelButton", AbstractButton.class));
	}

	@Test
	public void editingARowUpdatesItInPlace() throws Exception {
		E2eEnvironment.insertRow(db, "Old Bakery Name", "7 Luna St.", 4002, "99.0");
		JTable table = named(launchApp(), "clearanceTable", JTable.class);

		BgyClearanceRegistrationDialog dialog = doubleClickFirstRow(table);
		type(named(dialog, "businessNameTxt", JTextComponent.class), "New Bakery Name");
		type(named(dialog, "amountPaidTxt", JTextComponent.class), "120");
		click(named(dialog, "saveButton", AbstractButton.class));
		waitForWindow(JasperViewer.class);

		List<Map<String, String>> rows = E2eEnvironment.rows(db);
		assertEquals("editing must not insert a new row", 1, rows.size());
		assertEquals("New Bakery Name", rows.get(0).get("name"));
		assertEquals("120.0", rows.get(0).get("amount_paid"));
		assertEquals("4002", rows.get(0).get("control_no"));
		waitForCell(table, 0, 2, "New Bakery Name");
	}

	@Test
	public void editingOnTheDesktopKeepsFieldsItDoesNotChange() throws Exception {
		E2eEnvironment.insertRow(db, "Hardware Hub", "9 Luna St.", 4003, "50.0");
		E2eEnvironment.execute(db, "UPDATE bgy_clearance SET activity = 'Hardware', capitalization = '120,000',"
				+ " or_number = 889900, corporation = 1, rented = 1");
		JTable table = named(launchApp(), "clearanceTable", JTable.class);

		BgyClearanceRegistrationDialog dialog = doubleClickFirstRow(table);
		assertEquals("Hardware", text(named(dialog, "typeOfActivityTxt", JTextComponent.class)));
		assertEquals("889900", text(named(dialog, "orNumberTxt", JTextComponent.class)));
		type(named(dialog, "amountPaidTxt", JTextComponent.class), "75");
		click(named(dialog, "saveButton", AbstractButton.class));
		waitForWindow(JasperViewer.class);

		Map<String, String> row = E2eEnvironment.rows(db).get(0);
		assertEquals("75.0", row.get("amount_paid"));
		assertEquals("Hardware", row.get("activity"));
		assertEquals("120,000", row.get("capitalization"));
		assertEquals("889900", row.get("or_number"));
		assertEquals("4003", row.get("control_no"));
		assertEquals("1", row.get("new"));
		assertEquals("1", row.get("corporation"));
		assertEquals("1", row.get("rented"));
	}

	// ---- helpers -------------------------------------------------------

	private static BgyClearanceRegistrationDialog clickNew(BgyClearanceFrame frame) throws Exception {
		// "New" opens a modal dialog, which blocks, so click it asynchronously.
		final AbstractButton newButton = named(frame, "newButton", AbstractButton.class);
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				newButton.doClick();
			}
		});
		return waitForWindow(BgyClearanceRegistrationDialog.class);
	}

	private static BgyClearanceRegistrationDialog doubleClickFirstRow(final JTable table) throws Exception {
		// The edit dialog is modal too, so dispatch the double-click asynchronously.
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				table.setRowSelectionInterval(0, 0);
				table.setColumnSelectionInterval(0, 0);
				table.dispatchEvent(new MouseEvent(table, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0,
						5, 5, 2, false, MouseEvent.BUTTON1));
			}
		});
		return waitForWindow(BgyClearanceRegistrationDialog.class);
	}

	private static int rowCount(final JTable table) throws Exception {
		return onEdt(new Callable<Integer>() {
			public Integer call() {
				return table.getRowCount();
			}
		});
	}

	private static void waitForRowCount(final JTable table, final int expected) throws Exception {
		waitUntil("table to have " + expected + " rows", new Callable<Boolean>() {
			public Boolean call() {
				return table.getRowCount() == expected;
			}
		});
	}

	private static void waitForCell(final JTable table, final int row, final int col, final String expected)
			throws Exception {
		waitUntil("cell (" + row + "," + col + ") to be '" + expected + "'", new Callable<Boolean>() {
			public Boolean call() {
				return table.getRowCount() > row && expected.equals(String.valueOf(table.getValueAt(row, col)));
			}
		});
	}

	private static void waitUntil(String what, Callable<Boolean> condition) throws Exception {
		long deadline = System.currentTimeMillis() + TIMEOUT_MS;
		while (System.currentTimeMillis() < deadline) {
			if (onEdt(condition)) {
				return;
			}
			Thread.sleep(50);
		}
		throw new AssertionError("timed out waiting for " + what);
	}

	private static BgyClearanceFrame launchApp() throws Exception {
		// Same entry path as BarangayClearanceMain.main, minus the look-and-feel tweak.
		new BarangayClearanceMain();
		return waitForWindow(BgyClearanceFrame.class);
	}

	private static <T extends Window> T waitForWindow(final Class<T> type) throws Exception {
		long deadline = System.currentTimeMillis() + TIMEOUT_MS;
		while (System.currentTimeMillis() < deadline) {
			T found = onEdt(new Callable<T>() {
				public T call() {
					for (Window w : Window.getWindows()) {
						if (type.isInstance(w) && w.isShowing()) {
							return type.cast(w);
						}
					}
					return null;
				}
			});
			if (found != null) {
				return found;
			}
			Thread.sleep(50);
		}
		throw new AssertionError("timed out waiting for a visible " + type.getSimpleName());
	}

	private static <T extends Component> T named(final Container root, final String name, final Class<T> type)
			throws Exception {
		T found = onEdt(new Callable<T>() {
			public T call() {
				return find(root, name, type);
			}
		});
		if (found == null) {
			throw new AssertionError("no " + type.getSimpleName() + " named '" + name + "' in " + root.getClass());
		}
		return found;
	}

	private static <T extends Component> T find(Container root, String name, Class<T> type) {
		for (Component c : root.getComponents()) {
			if (name.equals(c.getName()) && type.isInstance(c)) {
				return type.cast(c);
			}
			if (c instanceof Container) {
				T nested = find((Container) c, name, type);
				if (nested != null) {
					return nested;
				}
			}
		}
		return null;
	}

	private static void type(final JTextComponent field, final String value) throws Exception {
		SwingUtilities.invokeAndWait(new Runnable() {
			public void run() {
				field.setText(value);
			}
		});
	}

	private static void click(final AbstractButton button) throws Exception {
		SwingUtilities.invokeAndWait(new Runnable() {
			public void run() {
				button.doClick();
			}
		});
	}

	private static String text(final JTextComponent field) throws Exception {
		return onEdt(new Callable<String>() {
			public String call() {
				return field.getText();
			}
		});
	}

	private static String cell(final JTable table, final int row, final int col) throws Exception {
		return onEdt(new Callable<String>() {
			public String call() {
				return String.valueOf(table.getValueAt(row, col));
			}
		});
	}

	private static Callable<Boolean> isShowing(final Component c) {
		return new Callable<Boolean>() {
			public Boolean call() {
				return c.isShowing();
			}
		};
	}

	private static <T> T onEdt(final Callable<T> task) throws Exception {
		final AtomicReference<T> result = new AtomicReference<T>();
		final AtomicReference<Exception> error = new AtomicReference<Exception>();
		try {
			SwingUtilities.invokeAndWait(new Runnable() {
				public void run() {
					try {
						result.set(task.call());
					} catch (Exception e) {
						error.set(e);
					}
				}
			});
		} catch (InvocationTargetException e) {
			throw new RuntimeException(e.getCause());
		}
		if (error.get() != null) {
			throw error.get();
		}
		return result.get();
	}
}
