These jars ship with the project and are already on the NetBeans classpath
(nbproject/project.properties), so nothing has to be downloaded or added by hand.
The course rules forbid Maven: keep this Ant project and these files.

  mssql-jdbc-12.8.1.jre8.jar   SQL Server driver, jre8 build for JDK 8
  jstl-1.2.jar                 JSTL tags used by every JSP
  jbcrypt-0.4.jar              password hashing
  gson-2.10.1.jar              JSON for the device and dashboard APIs
  weka-3.8.6.jar               machine learning (RiskScorer and the tools)

Test-only jars are in lib/test (testng-6.9.12.jar, jcommander-1.71.jar) and are not put in the war.
To use other versions, replace the files and update the names in nbproject/project.properties;
after changing weka, run tool.ModelTrainer again so the model matches the library.
