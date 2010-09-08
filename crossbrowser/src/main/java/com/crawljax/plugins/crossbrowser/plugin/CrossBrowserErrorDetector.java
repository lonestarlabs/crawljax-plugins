// Copyright 2010 Google Inc. All Rights Reserved.

package com.crawljax.plugins.crossbrowser.plugin;

import com.google.common.collect.Lists;

import com.crawljax.browser.WebDriverBackedEmbeddedBrowser;
import com.crawljax.core.CrawlSession;
import com.crawljax.core.CrawljaxException;
import com.crawljax.core.plugin.OnFireEventFailedPlugin;
import com.crawljax.core.plugin.OnRevisitStatePlugin;
import com.crawljax.core.plugin.PostCrawlingPlugin;
import com.crawljax.core.state.Eventable;
import com.crawljax.core.state.StateVertix;
import com.crawljax.oraclecomparator.OracleComparator;
import com.crawljax.oraclecomparator.StateComparator;
import com.crawljax.oraclecomparator.comparators.DateComparator;
import com.crawljax.plugins.crossbrowser.report.CrossBrowserReport;
import com.crawljax.plugins.crossbrowser.statecompare.DiffTextNodes;
import com.crawljax.plugins.crossbrowser.statecompare.TextNode;
import com.crawljax.plugins.crossbrowser.statecompare.TextNodeLoader;
import com.crawljax.plugins.errorreport.ErrorReport;

import org.apache.log4j.Logger;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.Augmenter;

import java.util.List;

/**
 * Plugin used in Crawljax to detect difference between two runs on a given browser.
 *
 * @version $Id$
 * @author slenselink@google.com (Stefan Lenselink)
 */
public class CrossBrowserErrorDetector
        implements OnRevisitStatePlugin, OnFireEventFailedPlugin, PostCrawlingPlugin {

	private final Logger LOGGER = Logger.getLogger(CrossBrowserErrorDetector.class);
	
	private final CrossBrowserReport report;

	// TODO(slenselink) remove the CrawlSession it's hacky
	private CrawlSession session;

	/**
	 * Create a new detector.
	 *
	 * @param report
	 *            the instance to report error to.
	 */
	public CrossBrowserErrorDetector(ErrorReport report) {
		this.report = new CrossBrowserReport(report);
	}

	@Override
	public void onRevisitState(CrawlSession session, StateVertix currentState) {
		this.session = session;
		String currentDom = currentState.getDom();
		
		StateComparator sc = new StateComparator(
		        Lists.newArrayList(new OracleComparator("stefan", new DateComparator())));
		
		// Strip out Date
		sc.compare(currentDom, currentDom, null);
		currentDom = sc.getStrippedNewDom();

		List<TextNode> orrigionalStipedStateList = TextNodeLoader.stripDom(currentDom);

		String newPageSource = "";
		try {
			newPageSource = session.getBrowser().getDom();
		} catch (CrawljaxException e) {
			LOGGER.warn("Received CrawljaxException while getting DOM from browser", e);
			return;
		}
		
		// Strip out Date
		sc.compare(newPageSource, newPageSource, null);
		newPageSource = sc.getStrippedNewDom();
		
		List<TextNode> newStripedStateList = TextNodeLoader.stripDom(newPageSource);

		if (!DiffTextNodes.makeLine(orrigionalStipedStateList).equals("")
		        && !DiffTextNodes.makeLine(newStripedStateList).equals(
		                DiffTextNodes.makeLine(orrigionalStipedStateList))) {
			// States differ
			// TODO remove lines when new crawljax changes gets committed...
			WebDriver driver = new Augmenter().augment(
			        ((WebDriverBackedEmbeddedBrowser) session.getBrowser()).getBrowser());
			report.addStateFailure(newPageSource, currentState, session.getCurrentCrawlPath(),
			        WebDriverBackedEmbeddedBrowser.withDriver(driver), orrigionalStipedStateList,
			        newStripedStateList);
		}
	}

	@Override
	public void onFireEventFailed(Eventable eventable, List<Eventable> pathToFailure) {
		// TODO(slenselink) We miss: the browser as an argument to the
		// onFireEventFailed call
		try {
			// TODO remove lines when new crawljax changes gets committed...
			WebDriver driver = new Augmenter().augment(
			        ((WebDriverBackedEmbeddedBrowser) session.getBrowser()).getBrowser());
			report.addEventFailure(
			        eventable, pathToFailure, WebDriverBackedEmbeddedBrowser.withDriver(driver));
		} catch (CrawljaxException e) {
			LOGGER.warn("Received CrawljaxException while adding EventFailure", e);
			return;
		}
	}

	@Override
	public void postCrawling(CrawlSession session) {
		// Forward Call
		report.genereate();
	}
	
	/**
	 * Generate the ErrorReport, during crossbrowser controller run the portCrawlingPlugins are not
	 * executed.
	 */
	public void genereateReport() {
		// forward call
		report.genereate();
	}
}
