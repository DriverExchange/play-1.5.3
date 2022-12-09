/**
 * Cobertura plugin for the Play! framework
 *
 * Copyright (C) 2009 Jonathan Clarke
 * Copyright (C) 2010 Julien Bille
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Cobertura; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307
 * USA
 */
package play.modules.cobertura;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Vector;

import net.sourceforge.cobertura.coveragedata.CoverageDataFileHandler;
import net.sourceforge.cobertura.coveragedata.ProjectData;
import net.sourceforge.cobertura.instrument.ClassInstrumenter;
import net.sourceforge.cobertura.util.FileLocker;

import org.apache.oro.text.GlobCompiler;
import org.apache.oro.text.regex.MalformedPatternException;
import org.apache.oro.text.regex.Pattern;
import org.apache.oro.text.regex.PatternCompiler;
import org.apache.oro.text.regex.PatternMatcher;
import org.apache.oro.text.regex.Perl5Matcher;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import play.Logger;
import play.Play;
import play.PlayPlugin;
import play.classloading.ApplicationClasses.ApplicationClass;
import play.mvc.Router;
import play.vfs.VirtualFile;

/**
 * <P>
 * This file implements a Cobertura module for the Play framework which provides
 * a plugin to support the Cobertura test coverage tool.
 * </P>
 * 
 * <P>
 * It ensures all Java classes are instrumented using Cobertura's
 * instrumentation.
 * </P>
 * 
 * <P>
 * The report is generated automatically when the application is stopped.
 * Cobertura's report is stored in the application's directory, in the directory
 * named "test-coverage-report".
 * </P>
 * 
 * <P>
 * The cobertura data file, 'cobertura.ser' is left intact, so you can generate
 * other formats of report, if you wish. By default, the Cobertura data file is
 * "cobertura.ser" in your Play! project directory. This can be done using the
 * Cobertura command-line tool or ant plugin, like this, from the root of your
 * application:
 * 
 * <pre>
 * $ cobertura-report.sh --destination  /tmp/cobertura-report app/
 * </pre>
 * 
 * </P>
 * 
 * @author Jonathan Clarke &lt;jonathan@phillipoux.net&gt;
 * @author Julien Bille &lt;julien.bille@gmail.com&gt;
 * 
 */
public class CoberturaPlugin extends PlayPlugin {

	public static final String DEFAULT_HAS_SHUTDOWN_HOOK = "true";
	public static final String DEFAULT_IGNORE = "DocViewerPlugin,controllers.TestRunner,controllers.Cobertura,controllers.PlayDocumentation,controllers.Secure,controllers.Secure$Security,controllers.Check";
	public static final String DEFAULT_REGEX_IGNORE = "*Test**,helpers.CheatSheetHelper*,controllers.CRUD*";
	
	// Where to store the report generated by Cobertura?
	public static final String TEST_DIRECTORY = "test-result"; 
	public static final String REPORT_DIRECTORY = "code-coverage"; 

	// Number of enhanced classes to save to cobertura's coverage data file
	private static int unsavedChanges = 0;

	// Do we need to refresh all classes on application restart?
	private static boolean refreshAllClasses = false;

	// Filesystem separator on this system
	public static String separator = System.getProperty("file.separator");

	// arguments to pass to Cobertura engine
	static Collection<Pattern> ignoreRegexes = new Vector<Pattern>();
	@SuppressWarnings("unchecked")
	static Collection ignoreBranchesRegexes = new Vector();
	static ProjectData projectData = null;
	public static File dataFile;
	
	
	/**
	 * Initializer for this plugin.
	 * 
	 * <P>
	 * When this plugin is loaded, initialize Cobertura configuration.
	 * </P>
	 * 
	 * @see play.PlayPlugin#onLoad()
	 */
	@Override
	public void onLoad() {
		// only use this plugin if we're in test mode
		if (!Play.runningInTestMode()) {
			return;
		}
		
		String playTmpValue = Play.configuration.getProperty("play.tmp");
		if(!"none".equals(playTmpValue)){
			Logger.warn("Actually play.tmp is set to %s. Set it to play.tmp=none", Play.configuration.getProperty("play.tmp"));
		}


		// set up Cobertura configuration
		initCobertura();

		String hasShutdownHookString = Play.configuration.getProperty("cobertura.hasShutdownHook", DEFAULT_HAS_SHUTDOWN_HOOK);
		boolean hasShutdownHook = Boolean.parseBoolean(hasShutdownHookString);
		
		if(hasShutdownHook){
			Logger.trace("Cobertura plugin: Add Cobertura Shutdown Hook");
			// register a shutdown hook so that the Cobertura coverage report
			// will be generated on shutdown
			Runtime.getRuntime().addShutdownHook(new CoberturaPluginShutdownThread());
		}else{
			Logger.debug("Cobertura plugin: Not add Cobertura Shutdown Hook. Work with explicit call");
		}
			
		Logger.trace("Cobertura plugin: loaded");
	}

	/**
	 * Method called after starting or restarting the application.
	 * 
	 * <P>
	 * During startup, this method is called after all classes have been loaded.
	 * Therefore, we save Cobertura's coverage data to disk.
	 * </P>
	 * 
	 * @see play.PlayPlugin#onApplicationStart()
	 */
	@Override
	public void onApplicationStart() {
		// only use this plugin if we're in test mode
		if (!Play.runningInTestMode()) {
			return;
		}
		
		// now, all classes have been instrumented, so we save the data file
		saveCoverageData();
	}

	/**
	 * Handle detection of changes in the application.
	 * 
	 * <P>
	 * If any Java classes were changed, Play detects this and recompiles them.
	 * During the compilation our enhance() method is called, and we record how
	 * many classes were changed. After all compilation is finished, this method
	 * is called.
	 * </P>
	 * 
	 * <P>
	 * If any classes have been recompiled, we throw an exception to force
	 * reloading the application, because Cobertura doesn't support changes to
	 * code during execution.
	 * </P>
	 * 
	 * @throws Exception
	 * 
	 * @see play.PlayPlugin#detectChange()
	 */
	@Override
	public void detectChange() throws RuntimeException {
		if (unsavedChanges != 0) {
			// reset number of changes to avoid throwing Exceptions in a loop!
			unsavedChanges = 0;

			// but remember that we need to refresh all classes
			refreshAllClasses = true;

			throw new RuntimeException();
		}
	}

	/**
	 * Adjust Play configuration after it is read.
	 * 
	 * <P>
	 * In this method, we force Play's to not use a temporary directory.
	 * The rationale behind this is that the tmp directory is used to cache
	 * compiled bytecode. However, since Cobertura instruments this bytecode,
	 * caching it may cause problems when running in other modes.
	 * </P>
	 * 
	 * @see play.PlayPlugin#onConfigurationRead()
	 */
	@Override
	public void onConfigurationRead() {
		// only use this plugin if we're in test mode
		if (!Play.runningInTestMode()) {
			return;
		}
	}

	/**
	 * Called when restarting or stopping the application.
	 * 
	 * <P>
	 * If we detected a changed Java class being recompiled, this means
	 * Cobertura's coverage data will be partly expired. An exception is thrown
	 * because continuing in this situation would lead to erroneous coverage
	 * data.
	 * </P>
	 * 
	 * <P>
	 * Ideally, we should be able to delete all coverage data, and force the
	 * Play! framework to recompile all classes, thus regenerating new coverage
	 * data on all classes. This is not currently implemented.
	 * </P>
	 * 
	 * @throws Exception
	 * 
	 * @see play.PlayPlugin#onApplicationStop()
	 */
	@Override
	public void onApplicationStop() {
		if (refreshAllClasses) {
			// we should refresh all classes now, but we can't currently
			throw new RuntimeException();
		}
	}
	
    @Override
    public void onRoutesLoaded() {
        Router.addRoute("GET", "/@cobertura", "Cobertura.index");
        Router.addRoute("GET", "/@cobertura/generateReport", "Cobertura.generateReport");
        Router.addRoute("GET", "/@cobertura/clear", "Cobertura.clear");
        Router.addRoute("GET", "/test-result/", "staticDir:test-result");
    }
	

	/**
	 * Instrument the compiled Java classes bytecode with Cobertura to generate
	 * statistics on test coverage in the code.
	 * 
	 * <P>
	 * This method is called after each class is compiled by Play.
	 * </P>
	 * 
	 * @see play.PlayPlugin#enhance(play.classloading.ApplicationClasses.ApplicationClass)
	 */
	@Override
	public void enhance(ApplicationClass applicationClass) {
		// only use this plugin if we're in test mode
		if (!Play.runningInTestMode()) {
			return;
		}
		
		// - don't instrument specific classes define in cobertura.ignore
		String ignoreString = Play.configuration.getProperty("cobertura.ignore", DEFAULT_IGNORE);
		// - Don't instrument classes matching regex expression
		String ignoreRegexString = Play.configuration.getProperty("cobertura.ignore.regex", DEFAULT_REGEX_IGNORE);
		
		
		// Get regexes from config
		if(ignoreRegexString != null && ignoreRegexes.isEmpty()){
			String[] ignoreRegTab = ignoreRegexString.split(",");
			for (String ignoreReg : ignoreRegTab) {
				Pattern pattern = createRegexPattern(ignoreReg);
				ignoreRegexes.add(pattern);
			}
		}
		// Check for match against regex for class. If theres a match, don't instrument
		if(checkForRegexMatch(applicationClass)){
			Logger.debug("Ignore class %s", applicationClass.name);
			return;
		}
		
		if(ignoreString != null){
			String[] ignoreTab = ignoreString.split(",");
			for (String ignore : ignoreTab) {
				if(applicationClass.name.equals(ignore)){
					Logger.debug("Ignore class %s", applicationClass.name);
					return;
				}
					
			}
		}
		
		

		Logger.trace("Cobertura plugin: Instrumenting class %s", applicationClass.name);

		// instrument!
		// this is copied from net.sourceforge.cobertura.instrument.Main
		InputStream inputStream = null;
		ClassWriter cw;
		ClassInstrumenter cv;
		try {
			inputStream = new ByteArrayInputStream(applicationClass.enhancedByteCode);
			ClassReader cr = new ClassReader(inputStream);
			cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			cv = new ClassInstrumenter(projectData, cw, ignoreRegexes, ignoreBranchesRegexes);
			cr.accept(cv, 0);
		} catch (Throwable t) {
			Logger.error("Unable to instrument class " + applicationClass.name + " (" + t + ")", t);
			return;
		}

		// save back to class representation in Play!
		applicationClass.enhancedByteCode = cw.toByteArray();

		// record this enhanced class as an unsaved change
		unsavedChanges++;
	}

	private boolean checkForRegexMatch(ApplicationClass applicationClass) {
		PatternMatcher matcher = new Perl5Matcher();
		for(Pattern pattern : ignoreRegexes){
			if(matcher.matches(applicationClass.name, pattern)){
				return true;
			}
		}
		return false;
	}

	private Pattern createRegexPattern(String input) {
		 PatternCompiler compiler = new GlobCompiler();
		try {
			return compiler.compile(input);
		} catch (MalformedPatternException e) {
			Logger.error("Cannot interpet regex from property file" + e);
			throw new RuntimeException(e);
		}
	}

	/**
	 * Private method to set up arguments for Cobertura.
	 * 
	 * <P>
	 * This also resets the Cobertura data file, to clean up after previous
	 * executions.
	 * </P>
	 */
	public static void initCobertura() {
		Logger.debug("Cobertura plugin: initializing configuration and data");

		// store the absolute path to the data file we want to use
		// in a Java system property so that Cobertura uses it automatically
		String dataFilePath = Play.applicationPath + separator + TEST_DIRECTORY + separator + REPORT_DIRECTORY + separator + "cobertura.ser";
		//arguments.add(Play.applicationPath + separator + TEST_DIRECTORY + separator + REPORT_DIRECTORY);
		System.setProperty("net.sourceforge.cobertura.datafile", dataFilePath);
		dataFile = CoverageDataFileHandler.getDefaultDataFile();

		// if an old data file already exists,
		// remove it before initializing it as new
		if (dataFile.exists()) {
			dataFile.delete();
		}

		// initialize cobertura arguments

		// this is copied from cobertura's
		// net.sourceforge.cobertura.instrument.Main class
		if (dataFile.isFile())
			projectData = CoverageDataFileHandler.loadCoverageData(dataFile);
		//if (projectData == null)
		projectData = new ProjectData();
	}
	
	/**
	 * Save data that Cobertura has generated while instrumenting classes.
	 * 
	 * <P>
	 * We keep a count of how many classes have been instrumented but not yet
	 * changed, to save rewriting the file if we have nothing to save.
	 * </P>
	 */
	private void saveCoverageData() {
		if (unsavedChanges > 0) {
			// save data from instrumentation
			CoverageDataFileHandler.saveCoverageData(projectData, dataFile);

			Logger.trace("Cobertura plugin: saved coverage data for %d classes", unsavedChanges);

			// everything has been saved now
			unsavedChanges = 0;
		}
	}

	/**
	 * Generate reports from the Cobertura coverage data.
	 * 
	 * <P>
	 * This method generated both a HTML and a XML report.
	 * </P>
	 */
	public static void coberturaReport() {
		// check we have a data file to work from
		if (! new File(dataFile.getAbsolutePath()).exists()) {
			Logger.debug("Datafile does not exist");
			return;
		}
		
		// all we do is call the Cobertura reporting main method
		// to simulate a command line call
		List<String> arguments = new ArrayList<String>();
		arguments.add("--datafile");
		arguments.add(dataFile.getAbsolutePath());
		arguments.add("--destination");
		arguments.add(Play.applicationPath + separator + TEST_DIRECTORY + separator + REPORT_DIRECTORY);
		arguments.add("--format");
		arguments.add("placeholder");

		// get all folders containing source code
		for (VirtualFile pathElement : Play.javaPath) {
			arguments.add(pathElement.getRealFile().getAbsolutePath());
		}
		
		String[] argsArray = arguments.toArray(new String[arguments.size()]);
		
		try {
			// generate XML report
			argsArray[5] = "xml";
			net.sourceforge.cobertura.reporting.Main.main(argsArray);

			// generate HTML report
			argsArray[5] = "html";
			net.sourceforge.cobertura.reporting.Main.main(argsArray);
		} catch (Exception e) {
			Logger.error("Error generating the Cobertura report");
		}
	}

	public static String forceReportWriting(){
		Logger.debug("Cobertura plugin: generating test coverage report");
		
		// Special call for save datafile. See cobertura faq for more informations about that
		net.sourceforge.cobertura.coveragedata.ProjectData.saveGlobalProjectData();		
		
		// wait for 2 seconds to make sure that Cobertura has finished writing stats
		// this is a bit of a hack, since Cobertura waits for 1 second itself...
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		// we attempt to get a lock on the data file
		// this ensures that Cobertura has finished writing stats to it
		// which also happens on shutdown, in a parallel thread to this one
		FileLocker fileLocker = new FileLocker(play.modules.cobertura.CoberturaPlugin.dataFile);

		try {
			if (fileLocker.lock()) {
				Logger.trace("Call coberturaReport");
				CoberturaPlugin.coberturaReport();
			}else
				Logger.trace("Cannot call coberturaReport");
		} finally {
			fileLocker.release();
		}

		// let the user know about the report
		String url = new File(Play.applicationPath + separator 
				+ play.modules.cobertura.CoberturaPlugin.TEST_DIRECTORY + separator 
				+ play.modules.cobertura.CoberturaPlugin.REPORT_DIRECTORY +
				separator + "index.html").toURI().toString();
		
		Logger.info("Test coverage report has been generated: %s", url);		

		return url;
	}
	
	public static String forceInit(){
		unsavedChanges = 0;

		CoverageDataFileHandler.saveCoverageData(projectData, dataFile);
		Logger.info("Cobertura plugin: saved coverage data for %d classes", unsavedChanges);

		try {
			Play.classloader.detectChanges();
		} catch (Exception ex) {
			Logger.error("Restart Needed!");
		}

		return "";
	}
	
	/**
	 * Thread to be executed on JVM shutdown.
	 * 
	 * <P>
	 * This thread is run by the JVM when it is shutdown. For our needs, this
	 * means all testing is finished, and we can generate the coverage report.
	 * </P>
	 * 
	 * @author Jonathan Clarke &lt;jonathan@phillipoux.net&gt;
	 */
	public class CoberturaPluginShutdownThread extends Thread {
		public void run() {
			Logger.info("Cobertura plugin: generating test coverage report");

			// wait for 2 seconds to make sure that Cobertura has finished writing stats
			// this is a bit of a hack, since Cobertura waits for 1 second itself...
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
			}

			// we attempt to get a lock on the data file
			// this ensures that Cobertura has finished writing stats to it
			// which also happens on shutdown, in a parallel thread to this one
			FileLocker fileLocker = new FileLocker(dataFile);

			try {
				if (fileLocker.lock()) {
					CoberturaPlugin.coberturaReport();
				}
			} finally {
				fileLocker.release();
			}

			// let the user know about the report
			String url = new File(Play.applicationPath + separator + TEST_DIRECTORY + separator+ REPORT_DIRECTORY + separator + "index.html").toURI().toString();
			Logger.info("Test coverage report has been generated: %s", url);
		}
	}
}
