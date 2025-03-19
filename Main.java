import java.io.FileNotFoundException;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import javax.swing.JOptionPane;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.io.IOException;

public class Main 
{   
  private static String dbPath;
  private static String dbSubfolder;
  private static String fullDBPath = "jdbc:derby:";
  private static String serviceState = "";

  private static String backupPath = "";
  private static boolean changedBackupPath = false;
  private static String changedBUpPathString = "";
  private static boolean changedMirthDBDirPath = false;
  private static String changedMDBDirPath = "";
  
  private static ArrayList readConfig = new ArrayList();

    public static void main(String args[]) throws ClassNotFoundException, SQLException, URISyntaxException, IOException
    {
      String driver = "org.apache.derby.jdbc.EmbeddedDriver";
      Class.forName(driver);

      //calls connection to DB and applies it to the host for SQL queries
      determineDBLocation();
      
      // Get the URL of the JAR file
      String ranAt = System.getProperty("user.dir");
      
      System.out.println("RAN AT: " + ranAt);
      
      System.out.println("TARGET FILE: " + ranAt+"\\backupConfigurationSettings.mcc");
   
      File config = new File(ranAt+"\\backupConfigurationSettings.mcc");
      try(Scanner configReader = new Scanner(config))
      {
        while(configReader.hasNext())
        {
	      	String line = configReader.nextLine();
	      	String capturedValue = "";
	      	if(!line.contains("--"))
	      	{
	      		if(line.contains("DELETE BACKUP FILES OLDER THAN(Days):"))
	      		{
	      			if(line.contains(": "))
		      		{
		      			capturedValue = line.substring(line.indexOf(": ")).replace(": ", "");
			      		readConfig.add(capturedValue);
			      		System.out.println(capturedValue);
		      		}
	      			else
	      			{
	      				System.out.println("Avoided Bad Input");
	      				readConfig.add("30");
	      			}
	      		}
	      		if(line.contains("CURRENT BACKUP DIRECTORY:"))
	      		{
	      			if(line.contains(": "))
		      		{
		      			capturedValue = line.substring(line.indexOf(": ")).replace(": ", "");
			      		readConfig.add(capturedValue);
			      		System.out.println(capturedValue);
		      		}
	      			else
	      			{
	      				System.out.println("Avoided Bad Input");
	      				readConfig.add(ranAt+"\\");
	      			}
	      		}
	      	}
        }
        configReader.close();
      }
      
      //sets backup path:
      backupPath = readConfig.get(1).toString();
      //setBackupFolder();
      System.out.println(backupPath);
      
      String serviceState = Main.checkMirthService();
	  	if(serviceState == "STOPPED")
	  	{
	          channelExport.clearChannelFolder();
	          Main.setBackupFolder();
	          channelExport.isFullMirthExportCheck("YES");
	          
	          String host = Main.returnHost();
	          channelExport exportChannels = new channelExport();
	          channelExport.exportChannels(host);
	          channelExport exportMetadata = new channelExport();
	          try 
	          {
	              channelExport.exportMetadata(host);
	          } 
	          catch (FileNotFoundException e1) 
	          {
	              e1.printStackTrace();
	          }
	          channelExport.isFullMirthExportCheck("NO");
	
	          try 
	          {
	              fullConfigExport.exportChannelGroups(host);
	          } 
	          catch (SQLException e1) 
	          {
	              e1.printStackTrace();
	          }
	          //setLogWindow();
	          catch (FileNotFoundException e1)
	          {
	              e1.printStackTrace();
	          }
	          Main.deleteBuildingBlockFiles(); //RE-ENABLE ME: 
	  	}
	  	else if(serviceState == "STARTED")
	  	{
	  		System.out.println("SERVICE STARTED");
	  	}
	  	
	  	//Runs age check and cleans up necessary files
        File autoBackupDir = new File(readConfig.get(1).toString());
        File[] backupDirList = autoBackupDir.listFiles();
        ArrayList capturedPath = new ArrayList();
        ArrayList capturedPathAge = new ArrayList();
	  	        
        if (backupDirList != null) 
        {
            for (File backupDir : backupDirList) 
            {
                Path convertedBackupPath = backupDir.toPath();
                if(!convertedBackupPath.toString().contains("-backupConfigSettings-") || !convertedBackupPath.toString().contains("backupConfigurationSettings.mcc") || !convertedBackupPath.toString().contains("MCC-TARGET_ME.bat") || !convertedBackupPath.toString().contains("MCC-SIDEKICK.jar"))
                {
                	FileTime t = Files.getLastModifiedTime(convertedBackupPath);
                    
                    long ft = t.toMillis();
                    long currTime = System.currentTimeMillis();
                    long diff = ft - currTime;
                    long days = TimeUnit.MILLISECONDS.toDays(diff);
                    System.out.println("The folder " + convertedBackupPath + " is " + days*-1 + " days old.");
                    capturedPath.add(convertedBackupPath);
                    capturedPathAge.add(days);
                }
            }
        }
        
        for (int p = 0; p < capturedPath.size(); p++) 
        {
            Long curFileAgeLong = (Long) capturedPathAge.get(p); 
            int curFileAge = curFileAgeLong.intValue(); 
            int maxFileAge = Integer.parseInt((String) readConfig.get(0)); 

            if ((curFileAge * -1) >= maxFileAge) 
            {
                File index = new File(capturedPath.get(p).toString());
                File parentDir = index;
                System.out.println("parentDir File: " + parentDir);
                
                //Deletes files that only contain "Backup 2" and doesn't contain "-backupConfigSettings-"
                if(parentDir != null && parentDir.getName().contains("Backup 2") && !index.toString().contains("-backupConfigSettings-"))
                {
                	deleteRecursively(index); // Call the recursive deletion method
                }
            }
        }
        capturedPath.clear();
        capturedPathAge.clear();
        
    }
    
    private static void deleteRecursively(File file) 
    {       
        if (file.isDirectory()) 
        {
            String[] entries = file.list();
            if (entries != null) 
            {
                for (String entry : entries) 
                {
                    File currentFile = new File(file, entry);
                    deleteRecursively(currentFile);
                }
            }
        }
        file.delete();
    }

    //This code searches and finds the path that the Mirth Connect Service is pointing to
    //The BufferedReader reads the input stream, and finds the path
    private static void determineDBLocation()
    {
      if(changedMirthDBDirPath == true)
      {
        System.out.println("DB Path was Changed");
        dbPath = changedMDBDirPath;
      }
      else
      {
        try 
        {
          Runtime rt = Runtime.getRuntime();
          Process pr = rt.exec("sc qc \"Mirth Connect Service\"");
          BufferedReader input = new BufferedReader(new InputStreamReader(pr.getInputStream()));
          do 
          {
            dbPath = input.readLine();
          } 
          while (!dbPath.contains("BINARY_PATH_NAME") && !dbPath.contains("FAILED"));
        } 
        catch (IOException runtimeError) 
        {
          System.out.println(runtimeError);
        } 

        if (dbPath.contains("BINARY_PATH_NAME")) 
        {
          //creates path substring components
          int pos1 = dbPath.indexOf(":\\") - 1;
          int pos2 = dbPath.indexOf("\\mirthc") + 1;
          if (pos2 < 1)
          {
            pos2 = dbPath.indexOf("\\mcserv") + 1;
          }
          if (pos1 > 0 && pos2 > 0) 
          {
            dbPath = dbPath.substring(pos1, pos2);
          } 
          else 
          {
            System.out.println("Error in finding path");
            dbPath = "";
          } 
        } 
        else 
        {
          dbPath = "";
        } 
      }
      
      //This following section of code determines the subfolder for the DB filepath
      //It checks to see if the "appdata" folder exists. If not it connects right to "mirthdb"
      File dbSubPath = new File(dbPath+"appdata");
      if(dbSubPath.exists())
      {
        dbSubfolder = "appdata\\mirthdb;";
        fullDBPath = fullDBPath + dbPath + dbSubfolder;
      }
      else
      {
        dbSubfolder = "mirthdb";
        fullDBPath = fullDBPath + dbPath + dbSubfolder;
      }
      System.out.println("Full DB Path: " + fullDBPath);
    }

    //verifies if the Mirth service is running
    public static String checkMirthService()
    {
      String serviceStatus = "";
      try 
      {
        Runtime rt = Runtime.getRuntime();
        Process pr = rt.exec("sc query \"mirth connect service\"");
        BufferedReader input = new BufferedReader(new InputStreamReader(pr.getInputStream()));
        do 
        {
          serviceStatus = input.readLine();
        } 
        while (!serviceStatus.contains("STATE") && !serviceStatus.contains("FAILED"));
      } 
      catch (IOException runtimeError) 
      {
        System.out.println(runtimeError);
      } 

      //returns the status of the service
      if (!serviceStatus.contains("STOPPED"))
      {
        if (serviceStatus.contains("FAILED")) 
        {
          System.out.println("SERVICE NOT INSTALLED");
          serviceState = "FAILED";
        } 
        else 
        {
          System.out.println("SERVICE STARTED");
          serviceState = "STARTED";
        }
      }
      else
      {
        System.out.println("I AM STOPPED");
        serviceState = "STOPPED";
      }
      return serviceState;
    }

    public static String returnHost()
    {
      fullDBPath = "jdbc:derby:";
      determineDBLocation();
      String host = fullDBPath;
      return host;
    }

    public static String setBackupFolder()
    {
      backupPath = backupPath + "Backup " + getDateTime()+"\\";
      //backupPath = "Backup " + getDateTime()+"\\";
      return backupPath;
    }

    public static String getBackupFolder()
    {
      String backupPathGrab = backupPath;
      return backupPathGrab;
    }

    public static String getDateTime()
    {
        //Grabs the date/time and formats for log output
        SimpleDateFormat dateFormat;
        Date currentDate = new Date();

        dateFormat = new SimpleDateFormat("yyyy-MM-dd");

        return dateFormat.format(currentDate);
    }

    public static String deleteBuildingBlockFiles()
    {
      String[] files = {"\\channelCodeTemplatesBackup\\codeTemplates", "\\channelMetadataBackup", "\\fullMirthExport\\globalScripts", "\\fullMirthExport\\configurationFiles" , "\\fullMirthExport\\channelGroups", "\\fullMirthExport\\Alerts"};
      for(int i=0;i<files.length;i++)
      {
        File currentFile = new File(getBackupFolder()+files[i]);
        deleteDirectory(currentFile);
      }

      if(new File(getBackupFolder()+"\\fullMirthExport").list().length < 1)
      {
        deleteDirectory(new File(getBackupFolder()+"\\fullMirthExport"));
      }
      else
      {

      }
      return "files deleted";
    }

    public static boolean deleteDirectory(File directoryToBeDeleted) 
    {
      File[] allContents = directoryToBeDeleted.listFiles();
      if (allContents != null) 
      {
          for (File file : allContents) 
          {
              deleteDirectory(file);
          }
      }
      return directoryToBeDeleted.delete();
    }

}


