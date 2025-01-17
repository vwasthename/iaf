package nl.nn.adapterframework.jdbc.migration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.Test;

import nl.nn.adapterframework.configuration.ConfigurationWarnings;
import nl.nn.adapterframework.jdbc.JdbcException;
import nl.nn.adapterframework.jdbc.JdbcTestBase;
import nl.nn.adapterframework.testutil.ConfigurationMessageEventListener;
import nl.nn.adapterframework.testutil.TestAppender;
import nl.nn.adapterframework.testutil.TestAssertions;
import nl.nn.adapterframework.testutil.TestConfiguration;
import nl.nn.adapterframework.testutil.TestFileUtils;
import nl.nn.adapterframework.util.AppConstants;
import nl.nn.adapterframework.util.JdbcUtil;
import nl.nn.adapterframework.util.MessageKeeper;

public class MigratorTest extends JdbcTestBase {
	private TestConfiguration configuration;
	private LiquibaseMigrator migrator = null;
	private String tableName="DUMMYTABLE";
//	private String rootLoggerName="nl.nn.adapterframework";
//	private String liquibaseLoggerName="liquibase";

	private TestConfiguration getConfiguration() {
		if(configuration == null) {
			configuration = new TestConfiguration();
		}
		return configuration;
	}

	@Override
	protected void prepareDatabase() throws Exception {
		//Ignore programmatic creation of Temp table, run Liquibase instead!
		removeTableIfPresent(tableName);
		removeTableIfPresent("DATABASECHANGELOG");
		removeTableIfPresent("DATABASECHANGELOGLOCK");

		migrator = getConfiguration().createBean(LiquibaseMigrator.class);
		migrator.setDatasourceName(getDataSourceName());
	}

	private void removeTableIfPresent(String table) throws JdbcException {
		if (dbmsSupport.isTablePresent(connection, table)) {
			JdbcUtil.executeStatement(connection, "DROP TABLE "+table);
		}
		assertFalse("table ["+tableName+"] should not exist prior to the test", dbmsSupport.isTablePresent(connection, table));
	}

	@Test
	public void testSimpleChangelogFile() throws Exception {
		AppConstants.getInstance().setProperty("liquibase.changeLogFile", "/Migrator/DatabaseChangelog.xml");
		migrator.update();

		MessageKeeper messageKeeper = configuration.getMessageKeeper();
		assertNotNull("no message logged to the messageKeeper", messageKeeper);
		assertEquals(2, messageKeeper.size()); //Configuration startup message + liquibase update
		assertEquals("Configuration [TestConfiguration] LiquiBase applied [2] change(s) and added tag [two:Niels Meijer]", messageKeeper.getMessage(1).getMessageText());
		assertFalse("table ["+tableName+"] should not exist", dbmsSupport.isTablePresent(connection, tableName));
	}

	@Test
	public void testFaultyChangelogFile() throws Exception {
		AppConstants.getInstance().setProperty("liquibase.changeLogFile", "/Migrator/DatabaseChangelogError.xml");
		migrator.update();

		ConfigurationWarnings warnings = configuration.getConfigurationWarnings();
		assertEquals(1, warnings.size());

		String warning = warnings.get(0);
		assertTrue(warning.contains("LiquibaseMigrator Error running LiquiBase update. Failed to execute [3] change(s)")); //Test ObjectName + Error
		assertTrue(warning.contains("Migration failed for change set Migrator/DatabaseChangelogError.xml::error::Niels Meijer")); //Test liquibase exception
		//H2 logs 'Table \"DUMMYTABLE\" already exists' Oracle throws 'ORA-00955: name is already used by an existing object'
		assertTrue("table ["+tableName+"] should exist", dbmsSupport.isTablePresent(connection, tableName));
	}

	@Test
	public void testSQLWriter() throws Exception {
		assumeTrue(getDataSourceName().equals("H2"));

		AppConstants.getInstance().setProperty("liquibase.changeLogFile", "/Migrator/DatabaseChangelog.xml");
		URL resource = MigratorTest.class.getResource("/Migrator/DatabaseChangelog_plus_changes.xml");
		InputStream file = resource.openStream();

		StringWriter writer = new StringWriter();
		migrator.update(writer, file);

		String sqlChanges = TestFileUtils.getTestFile("/Migrator/sql_changes.sql");

		String result = applyIgnores(writer.toString());
		result = removeComments(result);
		TestAssertions.assertEqualsIgnoreCRLF(sqlChanges, result);
	}

	private String removeComments(String file) throws IOException {
		BufferedReader buf = new BufferedReader(new StringReader(file));

		StringBuilder string = new StringBuilder();
		String line = buf.readLine();
		while (line != null) {
			if(line.startsWith("--")) {
				line = buf.readLine();
				continue;
			}
			string.append(line);
			line = buf.readLine();
			if (line != null) {
				string.append("\n");
			}
		}
		return string.toString();
	}

	private String applyIgnores(String sqlScript) {
		Pattern regex = Pattern.compile("(\\d+)\\'\\);");
		Matcher match = regex.matcher(sqlScript);
		if(match.find()) {
			String deploymentId = match.group(1);
			sqlScript = sqlScript.replace(deploymentId, "IGNORE");
		}
		else {
			fail("no match found");
			return null;
		}

		return sqlScript.replaceAll("(LOCKEDBY = ')(.*)(WHERE)", "LOCKEDBY = 'IGNORE', LOCKGRANTED = 'IGNORE' WHERE");
	}

	@Test
	public void testScriptExecutionLogs() throws Exception {
		AppConstants.getInstance().setProperty("liquibase.changeLogFile", "/Migrator/DatabaseChangelog.xml");
		TestAppender appender = TestAppender.newBuilder().useIbisPatternLayout("%level - %m").build();
		try {
			TestAppender.addToRootLogger(appender);
			migrator.validate();
			assertTrue(appender.contains("Successfully acquired change log lock")); //Validate Liquibase logs on INFO level

			Configurator.setRootLevel(Level.DEBUG); //Capture all loggers (at debug level)
			Configurator.setLevel("nl.nn", Level.WARN); //Exclude Frank!Framework loggers
			Configurator.setLevel("liquibase", Level.WARN); //Set all Liquibase loggers to WARN

			migrator.update();

			String msg = "LiquiBase applied [2] change(s) and added tag [two:Niels Meijer]";
			assertFalse(appender.contains(msg)); //Validate Liquibase doesn't log

			ConfigurationMessageEventListener configurationMessages = configuration.getBean("ConfigurationMessageListener", ConfigurationMessageEventListener.class);
			assertTrue(configurationMessages.contains(msg)); //Validate Liquibase did run
		} finally {
			TestAppender.removeAppender(appender);
			Configurator.reconfigure();
		}
	}
}
