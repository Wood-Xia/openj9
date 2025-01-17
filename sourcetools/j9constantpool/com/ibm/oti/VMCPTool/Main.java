/*******************************************************************************
 * Copyright (c) 2004, 2021 IBM Corp. and others
 *
 * This program and the accompanying materials are made available under
 * the terms of the Eclipse Public License 2.0 which accompanies this
 * distribution and is available at https://www.eclipse.org/legal/epl-2.0/
 * or the Apache License, Version 2.0 which accompanies this distribution and
 * is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * This Source Code may also be made available under the following
 * Secondary Licenses when the conditions for such availability set
 * forth in the Eclipse Public License, v. 2.0 are satisfied: GNU
 * General Public License, version 2 with the GNU Classpath
 * Exception [1] and GNU General Public License, version 2 with the
 * OpenJDK Assembly Exception [2].
 *
 * [1] https://www.gnu.org/software/classpath/license.html
 * [2] http://openjdk.java.net/legal/assembly-exception.html
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0 OR GPL-2.0 WITH Classpath-exception-2.0 OR LicenseRef-GPL-2.0 WITH Assembly-exception
 *******************************************************************************/
package com.ibm.oti.VMCPTool;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

@SuppressWarnings("nls")
public class Main implements Constants {

	static final class JCLRuntimeFlag {
		final String flagName;
		final int value;
		int useCount;

		public JCLRuntimeFlag(String flagName, int value) {
			this.flagName = flagName;
			this.value = value;
			this.useCount = 0;
		}

		public String cDefine() {
			StringBuilder buf = new StringBuilder();

			buf.append("#define JCL_RTFLAG_");

			for (int i = 0, length = flagName.length(); i < length; ++i) {
				char c = flagName.charAt(i);
				if (Character.isUpperCase(c)) {
					buf.append("_");
				}
				buf.append(Character.toUpperCase(c));
			}

			buf.append(" 0x").append(Integer.toHexString(value));

			return buf.toString();
		}
	}

	private static final String[] endianMacros = {
		"#ifdef J9VM_ENV_LITTLE_ENDIAN",
		"",
		"/* U_16 U_8 U_8 */",
		"#define WORD_BYTE_BYTE(a, b, c) ( ((U_32)a) | ((U_32)b << 16) | ((U_32)c << 24) )",
		"/* U_8 U_8 U_8 U_8 */",
		"#define BYTE_BYTE_BYTE_BYTE(a, b, c, d) ( ((U_32)a) | ((U_32)b << 8) | ((U_32)c << 16) | ((U_32)d << 24) )",
		"/* U_8 U_8 U_16 */",
		"#define BYTE_BYTE_WORD(a, b, c) ( ((U_32)a) | ((U_32)b << 8) | ((U_32)c << 16) )",
		"/* U_16 U_16 */",
		"#define WORD_WORD(a, b) ( ((U_32)a ) | ((U_32)b << 16 ) )",
		"",
		"#else /* J9VM_ENV_LITTLE_ENDIAN */",
		"",
		"/* U_16 U_8 U_8 */",
		"#define WORD_BYTE_BYTE(a, b, c) ( ((U_32)a << 16) | ((U_32)b << 8) | ((U_32)c) )",
		"/* U_8 U_8 U_8 U_8 */",
		"#define BYTE_BYTE_BYTE_BYTE(a, b, c, d) ( ((U_32)a << 24) | ((U_32)b << 16) | ((U_32)c << 8) | ((U_32)d) )",
		"/* U_8 U_8 U_16 */",
		"#define BYTE_BYTE_WORD(a, b, c) ( ((U_32)a << 24) | ((U_32)b << 16) | ((U_32)c) )",
		"/* U_16 U_16 */",
		"#define WORD_WORD(a, b) ( ((U_32)a << 16) | ((U_32)b) )",
		"",
		"#endif /* J9VM_ENV_LITTLE_ENDIAN */"
	};

	private static final String[] openDefinition = {
		"/* Autogenerated file */",
		"",
		"#include \"j9.h\"",
		"#include \"j9consts.h\""
	};

	private static final String[] openHeader = {
		"/* Autogenerated header */",
		"",
		"#ifndef J9VM_CONSTANT_POOL_H",
		"#define J9VM_CONSTANT_POOL_H",
		"",
		"/* @ddr_namespace: map_to_type=J9VmconstantpoolConstants */"
	};

	private static final String[] classMacros = {
		"#define J9VMCONSTANTPOOL_CLASSREF_AT(vm, index) ((J9RAMClassRef*)(&(vm)->jclConstantPool[(index)]))",
		"#define J9VMCONSTANTPOOL_CLASS_AT(vm, index) (J9VMCONSTANTPOOL_CLASSREF_AT(vm, index)->value == NULL \\",
		"\t? (vm)->internalVMFunctions->resolveKnownClass(vm, index) \\",
		"\t: J9VMCONSTANTPOOL_CLASSREF_AT(vm, index)->value)"
	};

	private static final String[] fieldMacros = {
		"#define J9VMCONSTANTPOOL_AT(vm, index, kind) ((kind*)&(vm)->jclConstantPool[index])",
		"#define J9VMCONSTANTPOOL_FIELDREF_AT(vm, index) J9VMCONSTANTPOOL_AT(vm, index, J9RAMFieldRef)",
		"#define J9VMCONSTANTPOOL_FIELD_OFFSET(vm, index) (J9JAVAVM_OBJECT_HEADER_SIZE(vm) + J9VMCONSTANTPOOL_FIELDREF_AT(vm, index)->valueOffset)",
		"",
		"#if !defined(J9VM_ENV_LITTLE_ENDIAN) && !defined(J9VM_ENV_DATA64)",
		"#define J9VMCONSTANTPOOL_ADDRESS_OFFSET(vm, index) J9VMCONSTANTPOOL_FIELD_OFFSET(vm, index) + sizeof(UDATA)",
		"#else",
		"#define J9VMCONSTANTPOOL_ADDRESS_OFFSET(vm, index) J9VMCONSTANTPOOL_FIELD_OFFSET(vm, index)",
		"#endif"
	};

	private static final String[] staticFieldMacros = {
		"#define J9VMCONSTANTPOOL_STATICFIELDREF_AT(vm, index) J9VMCONSTANTPOOL_AT(vm, index, J9RAMStaticFieldRef)",
		"#define J9VMCONSTANTPOOL_STATICFIELD_ADDRESS(vm, index) (J9RAMSTATICFIELDREF_VALUEADDRESS(J9VMCONSTANTPOOL_STATICFIELDREF_AT(vm, index)))",
	};

	private static final String[] staticMethodMacros = {
		"#define J9VMCONSTANTPOOL_STATICMETHODREF_AT(vm, index) J9VMCONSTANTPOOL_AT(vm, index, J9RAMStaticMethodRef)",
		"#define J9VMCONSTANTPOOL_STATICMETHOD_AT(vm, index) (J9VMCONSTANTPOOL_STATICMETHODREF_AT(vm, index)->method)"
	};

	private static final String[] virtualMethodMacros = {
		"#define J9VMCONSTANTPOOL_VIRTUALMETHODREF_AT(vm, index) J9VMCONSTANTPOOL_AT(vm, index, J9RAMVirtualMethodRef)",
		"#define J9VMCONSTANTPOOL_VIRTUALMETHOD_AT(vm, index) (J9VMCONSTANTPOOL_VIRTUALMETHODREF_AT(vm, index)->methodIndexAndArgCount)"
	};

	private static final String[] specialMethodMacros = {
		"#define J9VMCONSTANTPOOL_SPECIALMETHODREF_AT(vm, index) J9VMCONSTANTPOOL_AT(vm, index, J9RAMSpecialMethodRef)",
		"#define J9VMCONSTANTPOOL_SPECIALMETHOD_AT(vm, index) (J9VMCONSTANTPOOL_SPECIALMETHODREF_AT(vm, index)->method)"
	};

	private static final String[] interfaceMethodMacros = {
		"#define J9VMCONSTANTPOOL_INTERFACEMETHODREF_AT(vm, index) J9VMCONSTANTPOOL_AT(vm, index, J9RAMInterfaceMethodRef)",
		"#define J9VMCONSTANTPOOL_INTERFACEMETHOD_AT(vm, index) (J9VMCONSTANTPOOL_INTERFACEMETHODREF_AT(vm, index)->methodIndexAndArgCount)"
	};

	private static final String[] closeHeader = {
		"#endif /* J9VM_CONSTANT_POOL_H */"
	};

	private static final String optionBuildSpecId = "-buildSpecId";
	private static final String optionHelp = "-help";
	private static final String optionVersion = "-version";
	private static final String optionRootDir = "-rootDir";
	private static final String optionConfigDir = "-configDir";
	private static final String optionOutputDir = "-outputDir";
	private static final String optionCmakeCache = "-cmakeCache";
	private static final String optionVerbose = "-verbose";
	private static final String constantPool = "vmconstantpool.xml";

	private static String buildSpecId;
	private static Integer version;
	private static String configDirectory;
	private static String rootDirectory = ".";
	private static String outputDirectory;
	private static String cmakeCache;
	private static boolean verbose;

	private static IFlagInfo flagInfo;
	private static TreeMap<String, JCLRuntimeFlag> runtimeFlagDefs;

	private static boolean parseOptions(String[] args) {
		boolean isValid = true;

		try {
			for (int i = 0; i < args.length; ++i) {
				String arg = args[i];
				if (optionHelp.equalsIgnoreCase(arg)) {
					return false;
				} else if (optionVersion.equalsIgnoreCase(arg)) {
					String versionValue = args[++i];
					try {
						version = Integer.valueOf(versionValue);
					} catch (NumberFormatException e) {
						System.err.printf("ERROR: argument for '%s' must be numeric (%s)\n", optionVersion, versionValue);
						isValid = false;
					}
				} else if (optionRootDir.equalsIgnoreCase(arg)) {
					rootDirectory = args[++i];
				} else if (optionConfigDir.equalsIgnoreCase(arg)) {
					configDirectory = args[++i];
				} else if (optionBuildSpecId.equalsIgnoreCase(arg)) {
					buildSpecId = args[++i];
				} else if (optionOutputDir.equalsIgnoreCase(arg)) {
					outputDirectory = args[++i];
				} else if (optionCmakeCache.equalsIgnoreCase(arg)) {
					cmakeCache = args[++i];
				} else if (optionVerbose.equalsIgnoreCase(arg)) {
					verbose = true;
				} else {
					System.err.printf("Unrecognized option '%s'\n", arg);
					return false;
				}
			}
		} catch (ArrayIndexOutOfBoundsException e) {
			return false;
		}

		if (version == null) {
			System.err.printf("ERROR: required argument '%s' not given\n", optionVersion);
			isValid = false;
		}

		if (rootDirectory == null) {
			System.err.printf("ERROR: required argument '%s' not given\n", optionRootDir);
			isValid = false;
		}

		if (cmakeCache == null) {
			if (configDirectory == null) {
				System.err.printf("ERROR: required argument '%s' not given\n", optionConfigDir);
				isValid = false;
			}
			if (buildSpecId == null) {
				System.err.printf("ERROR: required argument '%s' not given\n", optionBuildSpecId);
				isValid = false;
			}
		}

		return isValid;
	}

	private static void printHelp() {
		System.err.println(Main.class.getName() + ":");
		System.err.println();
		System.err.println("Usage:");

		String commonOptStr = "\t" + optionRootDir + " <directory> [" + optionOutputDir + " <directory>] ";
		String trailingOptStr = optionVersion + " <version>";

		System.err.println(commonOptStr + optionConfigDir + " <directory> " + optionBuildSpecId + "<specId> " + trailingOptStr);
		System.err.println(commonOptStr + optionCmakeCache + " <cacheFile> " + trailingOptStr);
	}

	public static void main(String[] args) throws Throwable {
		if (!parseOptions(args)) {
			printHelp();
			System.exit(1);
		}

		if (cmakeCache != null) {
			if (configDirectory != null) {
				System.err.println("Ignoring option " + optionConfigDir);
			}
			if (buildSpecId != null) {
				System.err.println("Ignoring option " + optionBuildSpecId);
			}
			flagInfo = new CmakeFlagInfo(cmakeCache);
		} else {
			flagInfo = new UmaFlagInfo(configDirectory, buildSpecId);
		}

		ConstantPool pool = parseConstantPool();

		pool.removeNonApplicableItems(version.intValue(), flagInfo.getAllSetFlags());

		writeHeader(pool);
		writeDefinition(pool, version.intValue(), flagInfo.getAllSetFlags());
	}

	private static File getOutputFile(String directory, String fileName) throws FileNotFoundException {
		File dir;
		// If -outputDir was not set on commandline, default to outputting in the root directory
		if (outputDirectory == null) {
			dir = new File(rootDirectory, directory);
		} else {
			// Note: if -outputDir was specified we output all files in that directory (not in any sub directories)
			// so we ignore the directory argument
			dir = new File(outputDirectory);
		}

		File file = new File(dir, fileName);
		return file;
	}

	private static void printOn(PrintWriter out, String[] lines) {
		for (int i = 0; i < lines.length; ++i) {
			out.println(lines[i]);
		}
	}

	private static void writeDefinition(ConstantPool pool, int version, Set<String> flags) throws Throwable {
		System.out.println("Generating JCL constant pool definitions for Java " + version);
		StringWriter buffer = new StringWriter();
		PrintWriter out = new PrintWriter(buffer);
		printOn(out, openDefinition);
		out.println();
		printOn(out, endianMacros);
		out.println();

		pool.writeForClassLibrary(version, flags, out);

		out.flush();
		out.close();

		writeToDisk(getOutputFile("jcl", "j9vmconstantpool.c"), buffer.toString());
	}

	private static void writeHeader(ConstantPool pool) throws Throwable {
		System.out.print("Generating header file ");
		StringWriter buffer = new StringWriter();
		PrintWriter out = new PrintWriter(buffer);
		printOn(out, openHeader);
		out.println();

		out.println("/* Runtime flag definitions */");
		for (JCLRuntimeFlag flag : runtimeFlagDefs.values()) {
			out.println(flag.cDefine());
		}
		out.println();

		printOn(out, classMacros);
		out.println();
		printOn(out, fieldMacros);
		out.println();
		printOn(out, staticFieldMacros);
		out.println();
		printOn(out, staticMethodMacros);
		out.println();
		printOn(out, virtualMethodMacros);
		out.println();
		printOn(out, specialMethodMacros);
		out.println();
		printOn(out, interfaceMethodMacros);
		out.println();
		pool.writeMacros(out);
		out.println();
		out.println("#define J9VM_VMCONSTANTPOOL_SIZE " + pool.constantPoolSize());
		out.println();
		printOn(out, closeHeader);

		out.flush();
		out.close();
		writeToDisk(getOutputFile("oti", "j9vmconstantpool.h"), buffer.toString());
	}

	private static ConstantPool parseConstantPool() throws IOException {
		NodeList nodes;

		try {
			File dir = new File(rootDirectory, "oti");
			File file = new File(dir, constantPool);
			System.out.println("Reading constant pool from " + file.getPath());
			DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			Document document = builder.parse(file.toURI().toURL().toExternalForm());
			nodes = document.getDocumentElement().getChildNodes();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
			return null; // unreachable
		}

		Map<String, ClassRef> classes = new HashMap<>();
		final int nodeCount = nodes.getLength();

		// Find classref elements.
		for (int i = 0; i < nodeCount; ++i) {
			Node node = nodes.item(i);
			if (node.getNodeType() == Node.ELEMENT_NODE && node.getNodeName().equals(CLASSREF)) {
				Element element = (Element) node;
				if (element.hasAttribute("name")) {
					classes.put(element.getAttribute("name"), new ClassRef(element));
				} else {
					System.err.println("Missing name for " + CLASSREF + " element");
					System.exit(-1);
				}
			}
		}

		// Build constant pool
		ConstantPool pool = new ConstantPool();
		// TreeMap keeps flags ordered by name.
		runtimeFlagDefs = new TreeMap<>();

		int lastFlagValue = 0x1;
		JCLRuntimeFlag defaultFlag = new JCLRuntimeFlag("default", lastFlagValue);
		runtimeFlagDefs.put("default", defaultFlag);

		for (int i = 0; i < nodeCount; ++i) {
			Node node = nodes.item(i);
			if (node.getNodeType() == Node.ELEMENT_NODE) {
				PrimaryItem cpItem = cpItem((Element) node, classes);

				/* Extract and validate flags */
				Node flagsNode = node.getAttributes().getNamedItem("flags");
				if (null == flagsNode) {
					defaultFlag.useCount += 1;
				} else {
					/* non-null flags= attribute */
					String flagName = flagsNode.getTextContent();

					/* validate flags used in constantpool.xml */
					if (!flagInfo.isFlagValid(flagName)) {
						System.err.println("Invalid flag used ->" + flagName);
						System.exit(-1);
					}

					if (flagName.startsWith("!")) {
						// skip flag w/ starting !
						flagName = flagName.substring(1);
					}
					/* Find or create the flag object */
					JCLRuntimeFlag flag = runtimeFlagDefs.get(flagName);
					if (null == flag) {
						lastFlagValue <<= 1;
						flag = new JCLRuntimeFlag(flagName, lastFlagValue);
						runtimeFlagDefs.put(flagName, flag);
					}

					/* increment the useCount */
					flag.useCount += 1;
				}

				pool.add(cpItem);
			}
		}

		System.out.println("Found " + runtimeFlagDefs.size() + " flags used, declaring runtime constants.");

		for (JCLRuntimeFlag flag : runtimeFlagDefs.values()) {
			System.out.println("\t" + flag.cDefine() + " (useCount=" + flag.useCount + ")");
		}

		return pool;
	}

	private static PrimaryItem cpItem(Element e, Map<String, ClassRef> classes) {
		String type = e.getNodeName();
		if (CLASSREF.equals(type)) {
			return classes.get(e.getAttribute("name"));
		} else if (FIELDREF.equals(type)) {
			return new FieldRef(e, classes);
		} else if (STATICFIELDREF.equals(type)) {
			return new StaticFieldRef(e, classes);
		} else if (STATICMETHODREF.equals(type)) {
			return new StaticMethodRef(e, classes);
		} else if (VIRTUALMETHODREF.equals(type)) {
			return new VirtualMethodRef(e, classes);
		} else if (SPECIALMETHODREF.equals(type)) {
			return new SpecialMethodRef(e, classes);
		} else if (INTERFACEMETHODREF.equals(type)) {
			return new InterfaceMethodRef(e, classes);
		} else {
			System.err.println("Unrecognized node type: " + type);
			System.exit(-1);
			return null; // unreachable
		}
	}

	public static JCLRuntimeFlag getRuntimeFlag(String key) {
		return runtimeFlagDefs.get(key);
	}

	/**
	 * Checks if a file with given name exists on disk. Returns true if it does
	 * not exist. If the file exists, compares it with generated buffer and
	 * returns true if they are equal.
	 *
	 * @param fileName
	 * @param desiredContent
	 * @return boolean TRUE|FALSE
	 */
	private static boolean differentFromCopyOnDisk(String fileName, String desiredContent) {
		File fileOnDisk = new File(fileName);
		boolean returnValue = true;

		if (fileOnDisk.exists()) {
			StringBuilder fileBuffer = new StringBuilder();
			try {
				try (FileReader fr = new FileReader(fileOnDisk)) {
					char charArray[] = new char[1024];

					int numRead = -1;
					while ((numRead = fr.read(charArray)) != -1) {
						fileBuffer.append(charArray, 0, numRead);
					}

					if (desiredContent.equals(fileBuffer.toString())) {
						returnValue = false;
					}
				}
			} catch (IOException e) {
				// ignore
			}
		}

		return returnValue;
	}

	/**
	 * Compares the given buffer and file and if they are different it writes
	 * the buffer into a new file.
	 *
	 * @param file
	 * @param desiredContent
	 * @return
	 * @throws IOException
	 */
	private static void writeToDisk(File file, String desiredContent) throws IOException {
		if (differentFromCopyOnDisk(file.getPath(), desiredContent)) {
			System.out.println("** Writing " + file.getPath());
			file.delete();

			try (FileWriter fw = new FileWriter(file.getPath())) {
				fw.write(desiredContent);
			}
		} else if (verbose) {
			System.out.println("** Skipped writing [same as on file system]: " + file.getPath());
		}
	}

}
