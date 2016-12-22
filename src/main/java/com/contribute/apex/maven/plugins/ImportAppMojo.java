package com.contribute.apex.maven.plugins;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Import an APEX application in a target workspace.
 */
@Mojo(name = "import",
defaultPhase = LifecyclePhase.COMPILE)
public class ImportAppMojo extends AbstractMojo {

    /**
     * The database connection string used in the SQL*Plus login argument (e.g.
     * localhost:1521/orcl.company.com).
     */
    @Parameter(property = "import.connectionString",
    required = true)
    private String connectionString;
    /**
     * The database username used to login with SQL*Plus.
     */
    @Parameter(property = "import.username",
    required = true)
    private String username;
    /**
     * The database user's password.
     */
    @Parameter(property = "import.password",
    required = true)
    private String password;
    /**
     * The command to start the SQL*Plus executable. The default value is
     * 'sqlplus'.
     */
    @Parameter(property = "import.sqlplusCmd",
    defaultValue = "sqlplus")
    private String sqlplusCmd;
    /**
     * Allows you to set the ORACLE_HOME system environment variable.
     */
    @Parameter(property = "import.oracleHome")
    private String oracleHome;
    /**
     * The TNS_ADMIN environment variable allows you to specify the location of
     * the tnsnames.ora file.
     */
    @Parameter(property = "import.tnsAdmin")
    private String tnsAdmin;
    /**
     * Environment variable to specify the path used to search for libraries on
     * UNIX and Linux systems.
     */
    @Parameter(property = "import.libraryPath")
    private String libraryPath;
    /**
     * The relative path to the folder containing the application export
     * file(s).
     */
    @Parameter(property = "import.appExportLocation",
    required = true)
    private String appExportLocation;
    /**
     * The APEX workspace in which you want to import the application. Omit this
     * parameter to import the application in its original workspace.
     */
    @Parameter(property = "import.workspaceName")
    private String workspaceName;
    /**
     * The ID for the application to be imported. Omit this parameter to import
     * the application with its original ID.
     */
    @Parameter(property = "import.appId")
    private String appId;
    /**
     * Set the application alias.
     */
    @Parameter(property = "import.appAlias")
    private String appAlias;
    /**
     * Set the application name.
     */
    @Parameter(property = "import.appName")
    private String appName;
    /**
     * Set the application parsing schema.
     */
    @Parameter(property = "import.appParsingSchema")
    private String appParsingSchema;
    /**
     * Set the application image prefix.
     */
    @Parameter(property = "import.appImagePrefix")
    private String appImagePrefix;
    /**
     * Set the proxy server attributes for the application to be imported.
     */
    @Parameter(property = "import.appProxy")
    private String appProxy;
    /**
     * Automatically install or upgrade the application's supporting objects.
     */
    @Parameter(property = "import.autoInstallSupObj")
    private String autoInstallSupObj;
    /**
     * The offset value for the application import.
     */
    @Parameter(property = "import.appOffset")
    private String appOffset;
    private final String sqlFileExtension = ".sql";

    /**
     * The method called by Maven when the 'import' goal gets executed.
     */
    public void execute() throws MojoExecutionException, MojoFailureException {
        ProcessBuilder processBuilder;
        Process process;
        List<String> commandLineArguments = new ArrayList<String>();
        File scriptsToRunTmpFile;
        String workingDirectory;
        String sqlPlusOutput;

        try {
            scriptsToRunTmpFile = createScriptsToRunTmpFile();
            workingDirectory = scriptsToRunTmpFile.getAbsolutePath();
        } catch (IOException ex) {
            throw new MojoExecutionException("An unexpected error occurred while generating the .sql scripts", ex);
        }

        commandLineArguments.add(sqlplusCmd);
        // the -L option specifies not to reprompt for username or password if the initial connection didn't succeed.
        commandLineArguments.add("-L");
        commandLineArguments.add(getSqlPlusLoginArgument());
        commandLineArguments.add(getFriendlyPath(scriptsToRunTmpFile.getName()));

        processBuilder = new ProcessBuilder(commandLineArguments);
        setEnvironmentVariables(processBuilder.environment());
        // get the absolute path from the temporary file to set the working directory
        processBuilder.directory(new File(workingDirectory.substring(0, workingDirectory.lastIndexOf(File.separator))));
        getLog().debug("Working directory set: " + processBuilder.directory());
        processBuilder.redirectErrorStream(true);

        getLog().debug("Executing SQL*Plus: " + sqlplusCmd + " -L " + getSqlPlusLoginArgument() + " " + getFriendlyPath(scriptsToRunTmpFile.getName()));
        try {
            process = processBuilder.start();

            BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
            while ((sqlPlusOutput = stdInput.readLine()) != null) {
                getLog().info(sqlPlusOutput);
            }
            stdInput.close();

            BufferedReader stdError = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            while ((sqlPlusOutput = stdError.readLine()) != null) {
                getLog().info(sqlPlusOutput);
            }
            stdError.close();
            process.waitFor();

            getLog().debug("SQL*Plus process exit value: " + process.exitValue());
            if (process.exitValue() != 0) {
                throw new MojoExecutionException("SQL*Plus process returned an error code (" + process.exitValue() + ")");
            }
        } catch (IOException ex) {
            throw new MojoExecutionException("An unexpected error occurred while executing SQL*Plus", ex);
        } catch (InterruptedException ex) {
            throw new MojoExecutionException("An unexpected error occurred while executing SQL*Plus", ex);
        }
    }

    /**
     * Transform a path to a SQL*Plus friendly path.
     *
     * @param path the full path to a file.
     * @return a SQL*Plus friendly path.
     */
    public String getFriendlyPath(String path) {
        String friendlyPath;

        friendlyPath = "@\"" + path +"\"";

        if (friendlyPath.contains(" ")) {
            getLog().debug("Path contains space character(s): " + friendlyPath);
        }

        return friendlyPath;
    }

    /**
     * Put together the SQL*Plus login argument based on the plugin parameters.
     *
     * @return the SQL*Plus login argument.
     */
    public String getSqlPlusLoginArgument() {
        return username + "/" + password + "@" + "\"" + connectionString + "\"";
    }

    /**
     * Set Oracle specific environment variables to successfully execute
     * SQL*Plus. http://docs.oracle.com/cd/B28359_01/server.111/b31189/ch2.htm
     *
     * @param environment a copy of the current process environment variables.
     */
    public void setEnvironmentVariables(Map environment) {
        if (oracleHome != null) {
            environment.put("ORACLE_HOME", oracleHome);
        }
        if (tnsAdmin != null) {
            environment.put("TNS_ADMIN", tnsAdmin);
        }
        if (libraryPath != null) {
            environment.put("LD_LIBRARY_PATH", libraryPath);
            environment.put("DYLD_LIBRARY_PATH", libraryPath);
            environment.put("LIBPATH", libraryPath);
            environment.put("SHLIB_PATH", libraryPath);
        }
        environment.put("NLS_LANG", "AMERICAN_AMERICA.UTF8");

        getLog().debug("Printing all environment variables:");
        for (Object key : environment.keySet()) {
            getLog().debug("  " + key.toString() + "=" + environment.get(key));
        }
    }

    /**
     * Create a temporary .sql file containing all the scripts required to
     * successfully import the APEX application.
     *
     * @return the temporary .sql file object.
     */
    public File createScriptsToRunTmpFile() throws IOException, MojoExecutionException, MojoFailureException {
        File scriptsToRunTmpFile;
        File setAppAttributesTmpFile = createSetAppAttributesTmpFile();
        File[] exportFiles = getAppExportFiles();
        BufferedWriter writer;
        String script;

        script = "whenever sqlerror exit 1\n";
        script = script + "set serveroutput on\n";
        script = script + getFriendlyPath(setAppAttributesTmpFile.getAbsolutePath()) + "\n";
        script = script + "set serveroutput off\n";
        for (File exportFile : exportFiles) {
            script = script + getFriendlyPath(exportFile.getAbsolutePath()) + "\n";
        }
        script = script + "exit";

        scriptsToRunTmpFile = File.createTempFile("scriptsToRun", sqlFileExtension);
        scriptsToRunTmpFile.deleteOnExit();

        writer = new BufferedWriter(new FileWriter(scriptsToRunTmpFile));
        writer.write(script);
        writer.close();

        getLog().debug("Generated temp file: " + scriptsToRunTmpFile.getName() + ". Printing out content:\n" + script);

        return scriptsToRunTmpFile;
    }

    /**
     * Create a temporary .sql file containing an anonymous PL/SQL block that
     * sets application attributes by using the APEX_APPLICATION_INSTALL
     * package.
     * http://docs.oracle.com/cd/E37097_01/doc/doc.42/e35127/apex_app_inst.htm#AEAPI530
     *
     * @return the temporary .sql file object.
     * @throws IOException signals that an I/O exception of some sort has
     * occurred.
     */
    public File createSetAppAttributesTmpFile() throws IOException {
        File setAppAttributesTmpFile;
        BufferedWriter writer;
        String script;

        script = "declare\n"
                + "  l_workspace_id apex_workspaces.workspace_id%type;\n"
                + "begin\n"
                + "  null;\n\n";
        if (workspaceName != null) {
            script = script + ""
                    + "  select workspace_id\n"
                    + "  into l_workspace_id\n"
                    + "  from apex_workspaces\n"
                    + "  where upper(workspace) = upper('" + workspaceName + "');\n\n"
                    + ""
                    + "  apex_application_install.set_workspace_id(l_workspace_id);\n"
                    + "  dbms_output.put_line('workspace ID set: ' || l_workspace_id || ' (" + workspaceName.toUpperCase() + ")');\n";
        }
        if (appId != null) {
            script = script + ""
                    + "  apex_application_install.set_application_id(" + appId + ");\n"
                    + "  dbms_output.put_line('application ID set: " + appId + "');\n";
        }
        if (appAlias != null) {
            script = script + ""
                    + "  apex_application_install.set_application_alias('" + appAlias + "');\n"
                    + "  dbms_output.put_line('application alias set: " + appAlias + "');\n";
        }
        if (appName != null) {
            script = script + ""
                    + "  apex_application_install.set_application_name('" + appName + "');\n"
                    + "  dbms_output.put_line('application name set: " + appName + "');\n";
        }
        if (appParsingSchema != null) {
            script = script + ""
                    + "  apex_application_install.set_schema('" + appParsingSchema + "');\n"
                    + "  dbms_output.put_line('application parsing schema set: " + appParsingSchema + "');\n";
        }
        if (appImagePrefix != null) {
            script = script + ""
                    + "  apex_application_install.set_image_prefix('" + appImagePrefix + "');\n"
                    + "  dbms_output.put_line('application image prefix set: " + appImagePrefix + "');\n";
        }
        if (appProxy != null) {
            script = script + ""
                    + "  apex_application_install.set_proxy('" + appProxy + "');\n"
                    + "  dbms_output.put_line('application proxy attribute set: " + appProxy + "');\n";
        }
        if (autoInstallSupObj != null) {
            script = script + ""
                    + "  apex_application_install.set_auto_install_sup_obj(" + autoInstallSupObj + ");\n"
                    + "  dbms_output.put_line('application auto install supporting objects attribute set: " + autoInstallSupObj + "');\n";
        }
        if (appOffset != null) {
            script = script + ""
                    + "  apex_application_install.set_offset(" + appOffset + ");\n"
                    + "  dbms_output.put_line('application offset value set: " + appOffset + "');\n";
        } else {
            script = script + ""
                    + "  apex_application_install.generate_offset;\n";
        }
        script = script + ""
                + "exception\n"
                + "  when no_data_found then\n"
                + "    raise_application_error(-20000, 'Workspace ''" + workspaceName.toUpperCase() + "'' not found');\n"
                + "  when others then\n"
                + "    raise_application_error(-20000, 'An unexpected error occurred in the setAppAttributes script: ' || sqlerrm);\n"
                + "end;\n"
                + "/";

        setAppAttributesTmpFile = File.createTempFile("setAppAttributes", sqlFileExtension);
        setAppAttributesTmpFile.deleteOnExit();

        writer = new BufferedWriter(new FileWriter(setAppAttributesTmpFile));
        writer.write(script);
        writer.close();

        getLog().debug("Generated temp file: " + setAppAttributesTmpFile.getName() + ". Printing out content:\n" + script);

        return setAppAttributesTmpFile;
    }

    /**
     * Collect the APEX export files to be imported. Only .sql files are
     * included.
     *
     * @return a list of file objects.
     * @throws MojoExecutionException in case the specified appExportLocation
     * folder is invalid.
     * @throws MojoFailureException if read permissions on the appExportLocation
     * folder or export files are not granted or in case no .sql files were
     * found.
     */
    public File[] getAppExportFiles() throws MojoExecutionException, MojoFailureException {
        File exportFolder = new File(appExportLocation);
        File[] exportFiles;

        if (!exportFolder.exists()) {
            throw new MojoExecutionException("Unable to find the appExportLocation folder: " + exportFolder.getAbsolutePath());
        } else if (!exportFolder.isDirectory()) {
            throw new MojoExecutionException("The specified appExportLocation is not a folder: " + exportFolder.getAbsolutePath());
        } else if (!exportFolder.canRead()) {
            throw new MojoFailureException("No read permission on the appExportLocation folder: " + exportFolder.getAbsolutePath());
        }

        getLog().debug("Absolute path to the appExportLocation folder: " + exportFolder.getAbsolutePath());

        exportFiles = exportFolder.listFiles(new FileFilter() {
            public boolean accept(File file) {
                if (file.getName().toLowerCase().endsWith(sqlFileExtension)) {
                    getLog().debug("Included export file: " + file.getName());
                    return true;
                }
                return false;
            }
        });

        if (exportFiles.length == 0) {
            throw new MojoFailureException("No .sql scripts found in the appExportLocation folder: " + exportFolder.getAbsolutePath());
        }

        for (File exportFile : exportFiles) {
            if (!exportFile.canRead()) {
                throw new MojoFailureException("No read permission on export file: " + exportFile.getName());
            }
        }

        return exportFiles;
    }
}
