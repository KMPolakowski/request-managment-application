package com.polakowski.requestmanagement.bdd;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

/**
 * Runs the executable specification.
 *
 * <p>The scenarios are written in the language the business analysts use and are checked against
 * the application service, so they document behaviour rather than wiring.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.polakowski.requestmanagement.bdd")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "summary")
class RequestLifecycleBddTest {
}
