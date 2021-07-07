package net.mpawsey.bothelper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import net.mpawsey.ladderbot.Player;

public class FileManager {

	
	// Reads the specified file line by line and returns a list of strings
	public static List<String> readFile(String file) {
		List<String> contents = new ArrayList<String>();
		try {
			String line;
			BufferedReader reader = new BufferedReader(
									new InputStreamReader(
									new FileInputStream(
									new File(file)), "UTF8"));
			
			try
			{
				while ((line = reader.readLine())!=null) {
					contents.add(line);
				}
			}
			finally
			{
				reader.close();
			}
			
		} catch (FileNotFoundException fnfe) {
			// The file is not at the specified location
			System.out.println("Unable to find "+file);
	
		} catch (SecurityException se) {
			// The bot does not have permission to read the file
			System.out.println("Unable to obtain read permissions for "+file);
		
		} catch (NullPointerException npe) {
			// The path specified is null or uninitiated
			System.out.println("Path to file null or empty");
			
		} catch (IOException ioe) {
			// Some other process may have the file open so it cannot be accessed
			System.out.println("An IO error occurred");
		}
		return contents;
	}
	
	// Writes each string in the list to the next line of a file
	public static boolean writeFile(String file, List<String> contents, boolean append) {
		try {
			// Empty the file
			if (!append) {
				PrintWriter writer = new PrintWriter(file);
				writer.close();				
			}
			BufferedWriter writer = new BufferedWriter(
									new OutputStreamWriter(
									new FileOutputStream(
									new File(file), true), "UTF8"));
			// Write each string to the file
			try
			{
				for (String content:contents) {
					writer.write(content);
					writer.newLine();
				}
				return true;
			}
			finally
			{
				writer.close();
			}
			
		} catch (FileNotFoundException fnfe) {
			// The file is not at the specified location
			System.out.println("Unable to find "+file);

		} catch (SecurityException se) {
			// The bot does not have permission to write to the file
			System.out.println("Unable to obtain write permissions for "+file);
		
		} catch (NullPointerException npe) {
			// The path specified is null or uninitiated
			System.out.println("Path to file null or empty");
			
		} catch (IOException ioe) {
			// Some other process may have the file open so it cannot be accessed
			System.out.println("An IO error occurred");
		}
		
		return false;
	}
	
	public static boolean writeFile(String file, String content, boolean append) {
		try {
			BufferedWriter writer = new BufferedWriter(
									new OutputStreamWriter(
									new FileOutputStream(
									new File(file), append), "UTF8"));
			
			try
			{
				writer.write(content);
				writer.newLine();
				return true;
			}
			finally
			{
				writer.close();
			}
		} catch (FileNotFoundException fnfe) {
			// The file is not at the specified location
			System.out.println("Unable to find "+file);
	
		} catch (SecurityException se) {
			// The bot does not have permission to write to the file
			System.out.println("Unable to obtain write permissions for "+file);
		
		} catch (NullPointerException npe) {
			// The path specified is null or uninitiated
			System.out.println("Path to file null or empty");
			
		} catch (IOException ioe) {
			// Some other process may have the file open so it cannot be accessed
			System.out.println("An IO error occurred");
		}
		return false;
	}
	
	// Checks whether the user has a certain permission
	public static boolean hasPermission(int permissionBit, String userId) {
		// Read the permissions file
		List<String> permissions = readFile("../Shared/permissions");
		// Find the user if they are in the file
		for (String permission:permissions) {
			if (permission.startsWith(userId)) {
				String[] user = permission.split("-");
				// Check the bit relating to the requested permission
				if (user[1].charAt(permissionBit)=='1') return true;
				return false;
			}
		}
		return false;
	}
	
	// Creates a new file
	public static boolean createFile(String path)
	{
		File file = new File(path);
		if (file.exists())
			return false;
		
		try {
			file.createNewFile();
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		
		return true;
	}
	
	// Writes to a file, backup file, or prints error
	public static void tryWriteToFile(String file, String content, boolean append, Runnable errorFunc)
	{
		if (!FileManager.writeFile(file, content, append))
		{
			String name = file + Player.ISO8601.format(new Date());
			if (FileManager.createFile(name))
			{
				if (!FileManager.writeFile(name, content, false))
					errorFunc.run();
			}
			else
				errorFunc.run();
		}
	}
	
	// Writes to a file, backup file, or prints error
	public static void tryWriteToFile(String file, List<String> contents, boolean append, Runnable errorFunc)
	{
		if (!FileManager.writeFile(file, contents, append))
		{
			String name = file + Player.ISO8601.format(new Date());
			if (FileManager.createFile(name))
			{
				if (!FileManager.writeFile(name, contents, false))
					errorFunc.run();
			}
			else
				errorFunc.run();
		}
	}
	
	public static boolean doesFileExist(String file)
	{
		return new File(file).exists();
	}
}
