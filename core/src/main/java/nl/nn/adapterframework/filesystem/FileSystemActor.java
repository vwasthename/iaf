/*
   Copyright 2019-2021 WeAreFrank!

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package nl.nn.adapterframework.filesystem;

import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.commons.codec.binary.Base64InputStream;
import org.apache.commons.codec.binary.Base64OutputStream;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;

import lombok.Getter;
import nl.nn.adapterframework.configuration.ConfigurationException;
import nl.nn.adapterframework.configuration.ConfigurationWarning;
import nl.nn.adapterframework.configuration.ConfigurationWarnings;
import nl.nn.adapterframework.core.IConfigurable;
import nl.nn.adapterframework.core.IForwardTarget;
import nl.nn.adapterframework.core.INamedObject;
import nl.nn.adapterframework.core.ParameterException;
import nl.nn.adapterframework.core.PipeLineSession;
import nl.nn.adapterframework.core.TimeOutException;
import nl.nn.adapterframework.doc.DocumentedEnum;
import nl.nn.adapterframework.doc.EnumLabel;
import nl.nn.adapterframework.doc.IbisDoc;
import nl.nn.adapterframework.parameters.ParameterList;
import nl.nn.adapterframework.parameters.ParameterValueList;
import nl.nn.adapterframework.stream.IOutputStreamingSupport;
import nl.nn.adapterframework.stream.Message;
import nl.nn.adapterframework.stream.MessageOutputStream;
import nl.nn.adapterframework.stream.StreamingException;
import nl.nn.adapterframework.util.ClassUtils;
import nl.nn.adapterframework.util.EnumUtils;
import nl.nn.adapterframework.util.LogUtil;
import nl.nn.adapterframework.util.Misc;
import nl.nn.adapterframework.util.StreamUtil;
import nl.nn.adapterframework.util.XmlBuilder;

/**
 * Worker class for {@link FileSystemPipe} and {@link FileSystemSender}.
 * 
 * @ff.parameter action overrides attribute <code>action</code>
 * @ff.parameter filename overrides attribute <code>filename</code>. If not present, the input message is used.
 * @ff.parameter destination destination for action <code>rename</code> and <code>move</code>. Overrides attribute <code>destination</code>. 
 * @ff.parameter contents contents for action <code>write</code> and <code>append</code>.
 * @ff.parameter inputFolder folder for actions <code>list</code>, <code>mkdir</code> and <code>rmdir</code>. This is a sub folder of baseFolder. Overrides attribute <code>inputFolder</code>. If not present, the input message is used.
 * 
 * @author Gerrit van Brakel
 */
public class FileSystemActor<F, FS extends IBasicFileSystem<F>> implements IOutputStreamingSupport {
	protected Logger log = LogUtil.getLogger(this);

	public static final String ACTION_LIST="list";
	public static final String ACTION_INFO="info";
	public static final String ACTION_READ1="read";
	public static final String ACTION_READ2="download";
	public static final String ACTION_READ_DELETE="readDelete";
	public static final String ACTION_MOVE="move";
	public static final String ACTION_COPY="copy";
	public static final String ACTION_DELETE="delete";
	public static final String ACTION_MKDIR="mkdir";
	public static final String ACTION_RMDIR="rmdir";
	public static final String ACTION_WRITE1="write";
	public static final String ACTION_WRITE2="upload";
	public static final String ACTION_APPEND="append";
	public static final String ACTION_RENAME="rename";
	public static final String ACTION_FORWARD="forward";
	public static final String ACTION_LIST_ATTACHMENTS="listAttachments";

	public final String PARAMETER_ACTION="action";
	public final String PARAMETER_CONTENTS1="contents";
	public final String PARAMETER_CONTENTS2="file";
	public final String PARAMETER_FILENAME="filename";
	public final String PARAMETER_INPUTFOLDER="inputFolder";	// folder for actions list, mkdir and rmdir. This is a sub folder of baseFolder
	public final String PARAMETER_DESTINATION="destination";	// destination for action rename and move
	
	public final String BASE64_ENCODE="encode";
	public final String BASE64_DECODE="decode";
	
	public final FileSystemAction[] ACTIONS_BASIC= {FileSystemAction.LIST, FileSystemAction.INFO, FileSystemAction.READ, FileSystemAction.DOWNLOAD, FileSystemAction.READDELETE, FileSystemAction.MOVE, FileSystemAction.COPY, FileSystemAction.DELETE, FileSystemAction.MKDIR, FileSystemAction.RMDIR};
	public final FileSystemAction[] ACTIONS_WRITABLE_FS= {FileSystemAction.WRITE, FileSystemAction.UPLOAD, FileSystemAction.APPEND, FileSystemAction.RENAME};
	public final FileSystemAction[] ACTIONS_MAIL_FS= {FileSystemAction.FORWARD};

	private @Getter FileSystemAction action;
	private @Getter String filename;
	private @Getter String destination;
	private @Getter String inputFolder; // folder for action=list
	private @Getter boolean createFolder; // for action move, rename and list

	private @Getter String base64;
	private @Getter int rotateDays=0;
	private @Getter int rotateSize=0;
	private @Getter boolean overwrite=false;
	private @Getter int numberOfBackups=0;
	private @Getter String wildcard=null;
	private @Getter String excludeWildcard=null;
	private @Getter boolean removeNonEmptyFolder=false;
	private @Getter boolean writeLineSeparator=false;
	private @Getter String charset;

	private Set<FileSystemAction> actions = new LinkedHashSet<FileSystemAction>(Arrays.asList(ACTIONS_BASIC));
	
	private INamedObject owner;
	private FS fileSystem;
	private ParameterList parameterList;

	private byte[] eolArray=null;

	public enum FileSystemAction implements DocumentedEnum {
		/** list files in a folder/directory<br/>folder, taken from first available of:<ol><li>attribute <code>inputFolder</code></li><li>parameter <code>inputFolder</code></li><li>input message</li></ol> */
		@EnumLabel(ACTION_LIST) LIST,
		/** show info about a single file<br/>filename: taken from attribute <code>filename</code>, parameter <code>filename</code> or input message</li><li>root folder</li></ol> */
		@EnumLabel(ACTION_INFO) INFO,
		/** read a file, returns an InputStream<br/>filename: taken from attribute <code>filename</code>, parameter <code>filename</code> or input message<br/>&nbsp; */
		@EnumLabel(ACTION_READ1) READ,
		@EnumLabel(ACTION_READ2) DOWNLOAD,
		/** like read, but deletes the file after it has been read<br/>filename: taken from attribute <code>filename</code>, parameter <code>filename</code> or input message<br/> */
		@EnumLabel(ACTION_READ_DELETE) READDELETE,
		/** move a file to another folder<br/>filename: taken from attribute <code>filename</code>, parameter <code>filename</code> or input message<br/>destination: taken from attribute <code>destination</code> or parameter <code>destination</code> */
		@EnumLabel(ACTION_MOVE) MOVE,
		/** copy a file to another folder<br/>filename: taken from attribute <code>filename</code>, parameter <code>filename</code> or input message<br/>destination: taken from attribute <code>destination</code> or parameter <code>destination</code> */
		@EnumLabel(ACTION_COPY) COPY,
		/** delete a file<br/>filename: taken from attribute <code>filename</code>, parameter <code>filename</code> or input message<br/> */
		@EnumLabel(ACTION_DELETE) DELETE,
		/** create a folder/directory<br/>folder, taken from first available of:<ol><li>attribute <code>inputFolder</code></li><li>parameter <code>inputFolder</code></li><li>input message</li></ol><br/> */
		@EnumLabel(ACTION_MKDIR) MKDIR,
		/** remove a folder/directory<br/>folder, taken from first available of:<ol><li>attribute <code>inputFolder</code></li><li>parameter <code>inputFolder</code></li><li>input message</li></ol><br/> */
		@EnumLabel(ACTION_RMDIR) RMDIR,
		/** write contents to a file<br/>
 *  filename: taken from attribute <code>filename</code>, parameter <code>filename</code> or input message<br/>
 *  parameter <code>contents</code>: contents as either Stream, Bytes or String<br/>
 *  At least one of the parameters must be specified.<br/>
 *  The missing parameter defaults to the input message.<br/>
 *  For streaming operation, the parameter <code>filename</code> must be specified. */
		@EnumLabel(ACTION_WRITE1) WRITE,
		@EnumLabel(ACTION_WRITE2) UPLOAD,
		/** append contents to a file (only for filesystems that support 'append')<br/>
 *  filename: taken from attribute <code>filename</code>, parameter <code>filename</code> or input message<br/>
 *  parameter <code>contents</code>: contents as either Stream, Bytes or String<br/>
 *  At least one of the parameters must be specified.<br/>
 *  The missing parameter defaults to the input message.<br/>
 *  For streaming operation, the parameter <code>filename</code> must be specified. */
		@EnumLabel(ACTION_APPEND) APPEND,
		/** change the name of a file<br/>filename: taken from parameter <code>filename</code> or input message<br/>destination: taken from attribute <code>destination</code> or parameter <code>destination</code> */
		@EnumLabel(ACTION_RENAME) RENAME,
		/** (for MailFileSystems only:) forward an existing file to an email address<br/>filename: taken from parameter <code>filename</code> or input message<br/>destination (an email address in this case): taken from attribute <code>destination</code> or parameter <code>destination</code> */
		@EnumLabel(ACTION_FORWARD) FORWARD,

//		/** Specific to AmazonS3Sender */
//		@EnumLabel("createBucket") CREATEBUCKET,
//
//		/** Specific to AmazonS3Sender */
//		@EnumLabel("deleteBucket") DELETEBUCKET,
//
//		/** Specific to AmazonS3Sender */
//		@EnumLabel("restore") RESTORE,
//
//		/** Specific to AmazonS3Sender */
//		@EnumLabel("copyS3Object") COPYS3OBJECT,

		/** Specific to FileSystemSenderWithAttachments */
		@EnumLabel(ACTION_LIST_ATTACHMENTS) LISTATTACHMENTS;

	}

	public void configure(FS fileSystem, ParameterList parameterList, IConfigurable owner) throws ConfigurationException {
		this.owner=owner;
		this.fileSystem=fileSystem;
		this.parameterList=parameterList;
		
		if (fileSystem instanceof IWritableFileSystem) {
			actions.addAll(Arrays.asList(ACTIONS_WRITABLE_FS));
		}
		if (fileSystem instanceof IMailFileSystem) {
			actions.addAll(Arrays.asList(ACTIONS_MAIL_FS));
		}

		if (parameterList!=null && parameterList.findParameter(PARAMETER_CONTENTS2) != null && parameterList.findParameter(PARAMETER_CONTENTS1) == null) {
			ConfigurationWarnings.add(owner, log, "parameter ["+PARAMETER_CONTENTS2+"] has been replaced with ["+PARAMETER_CONTENTS1+"]");
			parameterList.findParameter(PARAMETER_CONTENTS2).setName(PARAMETER_CONTENTS1);
		}

		if (action != null) {
			if (getAction() == FileSystemAction.DOWNLOAD) {
				ConfigurationWarnings.add(owner, log, "action ["+FileSystemAction.DOWNLOAD+"] has been replaced with ["+FileSystemAction.READ+"]");
				action=FileSystemAction.READ;
			}
			if (getAction()==FileSystemAction.UPLOAD) {
				ConfigurationWarnings.add(owner, log, "action ["+FileSystemAction.UPLOAD+"] has been replaced with ["+FileSystemAction.WRITE+"]");
				action=FileSystemAction.WRITE;
			}
			checkConfiguration(getAction());
		} else if (parameterList == null || parameterList.findParameter(PARAMETER_ACTION) == null) {
			throw new ConfigurationException(ClassUtils.nameOf(owner)+": either attribute [action] or parameter ["+PARAMETER_ACTION+"] must be specified");
		}

		if (StringUtils.isNotEmpty(getBase64()) && !(getBase64().equals(BASE64_ENCODE) || getBase64().equals(BASE64_DECODE))) {
			throw new ConfigurationException("attribute 'base64' can have value '"+BASE64_ENCODE+"' or '"+BASE64_DECODE+"' or can be left empty");
		}

		if (StringUtils.isNotEmpty(getInputFolder()) && parameterList!=null && parameterList.findParameter(PARAMETER_INPUTFOLDER) != null) {
			ConfigurationWarnings.add(owner, log, "inputFolder configured via attribute [inputFolder] as well as via parameter ["+PARAMETER_INPUTFOLDER+"], parameter will be ignored");
		}

		if (!(fileSystem instanceof IWritableFileSystem)) {
			if (getNumberOfBackups()>0) {
				throw new ConfigurationException("FileSystem ["+ClassUtils.nameOf(fileSystem)+"] does not support setting attribute 'numberOfBackups'");
			}
			if (getRotateDays()>0) {
				throw new ConfigurationException("FileSystem ["+ClassUtils.nameOf(fileSystem)+"] does not support setting attribute 'rotateDays'");
			}
		}
		eolArray = System.getProperty("line.separator").getBytes();
	}

	private void checkConfiguration(FileSystemAction action2) throws ConfigurationException {
		if (!actions.contains(action2))
			throw new ConfigurationException(ClassUtils.nameOf(owner)+": unknown or invalid action [" + action2 + "] supported actions are " + actions.toString() + "");

		//Check if necessary parameters are available
		actionRequiresAtLeastOneOfTwoParametersOrAttribute(owner, parameterList, action2, FileSystemAction.WRITE,  PARAMETER_CONTENTS1, PARAMETER_FILENAME, "filename", getFilename());
		actionRequiresAtLeastOneOfTwoParametersOrAttribute(owner, parameterList, action2, FileSystemAction.MOVE,    PARAMETER_DESTINATION, null, "destination", getDestination());
		actionRequiresAtLeastOneOfTwoParametersOrAttribute(owner, parameterList, action2, FileSystemAction.COPY,    PARAMETER_DESTINATION, null, "destination", getDestination());
		actionRequiresAtLeastOneOfTwoParametersOrAttribute(owner, parameterList, action2, FileSystemAction.RENAME,  PARAMETER_DESTINATION, null, "destination", getDestination());
		actionRequiresAtLeastOneOfTwoParametersOrAttribute(owner, parameterList, action2, FileSystemAction.FORWARD, PARAMETER_DESTINATION, null, "destination", getDestination());
	}
	
//	protected void actionRequiresParameter(INamedObject owner, ParameterList parameterList, String action, String parameter) throws ConfigurationException {
//		if (getActionEnum().equals(action) && (parameterList == null || parameterList.findParameter(parameter) == null)) {
//			throw new ConfigurationException("the "+action+" action requires the parameter ["+parameter+"] to be present");
//		}
//		actionRequiresAtLeastOneOfTwoParametersOrAttribute(owner, parameterList, action, parameter, null, null, null);
//	}

	protected void actionRequiresAtLeastOneOfTwoParametersOrAttribute(INamedObject owner, ParameterList parameterList, FileSystemAction configuredAction, FileSystemAction action, String parameter1, String parameter2, String attributeName, String attributeValue) throws ConfigurationException {
		if (configuredAction.equals(action)) {
			boolean parameter1Set = parameterList != null && parameterList.findParameter(parameter1) != null;
			boolean parameter2Set = parameterList != null && parameterList.findParameter(parameter2) != null;
			boolean attributeSet  = StringUtils.isNotEmpty(attributeValue);
			if (!parameter1Set && !parameter2Set && !attributeSet) {
				throw new ConfigurationException(ClassUtils.nameOf(owner)+": the ["+action+"] action requires the parameter ["+parameter1+"] "+(parameter2!=null?"or parameter ["+parameter2+"] ":"")+(attributeName!=null?"or the attribute ["+attributeName+"] ": "")+"to be present");
			}
		}
	}

	public void open() throws FileSystemException {
		if (StringUtils.isNotEmpty(getInputFolder()) && !fileSystem.folderExists(getInputFolder()) && getAction()!=FileSystemAction.MKDIR) {
			if (isCreateFolder()) {
				log.debug("creating inputFolder ["+getInputFolder()+"]");
				fileSystem.createFolder(getInputFolder());
			} else {
				F file = fileSystem.toFile(getInputFolder());
				if (file!=null && fileSystem.exists(file)) {
					throw new FileNotFoundException("inputFolder ["+getInputFolder()+"], canonical name ["+fileSystem.getCanonicalName(fileSystem.toFile(getInputFolder()))+"], does not exist as a folder, but is a file");
				}
				throw new FileNotFoundException("inputFolder ["+getInputFolder()+"], canonical name ["+fileSystem.getCanonicalName(fileSystem.toFile(getInputFolder()))+"], does not exist");
			}
		}
	}
	
//	@Override
//	public void close() throws SenderException {
//		try {
//			getFileSystem().close();
//		} catch (FileSystemException e) {
//			throw new SenderException("Cannot close fileSystem",e);
//		}
//	}
	
	private String determineFilename(Message input, ParameterValueList pvl) throws FileSystemException {
		if (StringUtils.isNotEmpty(getFilename())) {
			return getFilename();
		}
		if (pvl!=null && pvl.contains(PARAMETER_FILENAME)) {
			return pvl.getParameterValue(PARAMETER_FILENAME).asStringValue(null);
		}
		try {
			return input.asString();
		} catch (IOException e) {
			throw new FileSystemException(e);
		}
	}

	private String determineDestination(ParameterValueList pvl) throws FileSystemException {
		if (StringUtils.isNotEmpty(getDestination())) {
			return getDestination();
		}
		if (pvl!=null && pvl.contains(PARAMETER_DESTINATION)) {
			String destination = pvl.getParameterValue(PARAMETER_DESTINATION).asStringValue(null);
			if (StringUtils.isEmpty(destination)) {
				throw new FileSystemException("parameter ["+PARAMETER_DESTINATION+"] does not specify destination");
			}
			return destination;
		}
		throw new FileSystemException("no destination specified");
	}

	private F getFile(Message input, ParameterValueList pvl) throws FileSystemException {
		String filename=determineFilename(input, pvl);
		return fileSystem.toFile(filename);
	}
	
	private String determineInputFoldername(Message input, ParameterValueList pvl) throws FileSystemException {
		if (StringUtils.isNotEmpty(getInputFolder())) {
			return getInputFolder();
		}
		if (pvl!=null && pvl.contains(PARAMETER_INPUTFOLDER)) {
			return pvl.getParameterValue(PARAMETER_INPUTFOLDER).asStringValue(null);
		}
		try {
			if (input==null || StringUtils.isEmpty(input.asString())) {
				return null;
			}
			return input.asString();
		} catch (IOException e) {
			throw new FileSystemException(e);
		}
	}
	
	public Object doAction(Message input, ParameterValueList pvl, PipeLineSession session) throws FileSystemException, TimeOutException {
		try {
			if(input != null) {
				input.closeOnCloseOf(session, getClass().getSimpleName()+" of a "+fileSystem.getClass().getSimpleName()); // don't know if the input will be used
			}

			FileSystemAction action;
			if (pvl != null && pvl.contains(PARAMETER_ACTION)) {
				try {
					action = EnumUtils.parse(FileSystemAction.class, pvl.getParameterValue(PARAMETER_ACTION).asStringValue(getAction()+""));
				} catch(IllegalArgumentException e) {
					throw new FileSystemException("unable to resolve the value of parameter ["+PARAMETER_ACTION+"]");
				}
				checkConfiguration(action);
			} else {
				action = getAction();
			}
			switch(action) {
				case DELETE: {
					return processAction(input, pvl, f -> { fileSystem.deleteFile(f); return f; });
				}
				case INFO: {
					F file=getFile(input, pvl);
					FileSystemUtils.checkSource(fileSystem, file, FileSystemAction.INFO);
					return FileSystemUtils.getFileInfo(fileSystem, file).toXML();
				}
				case READ: {
					F file=getFile(input, pvl);
					Message in = fileSystem.readFile(file, getCharset());
					if (StringUtils.isNotEmpty(getBase64())) {
						return new Base64InputStream(in.asInputStream(), getBase64().equals(BASE64_ENCODE));
					}
					return in;
				}
				case READDELETE: {
					F file=getFile(input, pvl);
					InputStream in = new FilterInputStream(fileSystem.readFile(file, getCharset()).asInputStream()) {

						@Override
						public void close() throws IOException {
							super.close();
							try {
								fileSystem.deleteFile(file);
							} catch (FileSystemException e) {
								throw new IOException("Could not delete file", e);
							}
						}

						@Override
						protected void finalize() throws Throwable {
							try {
								close();
							} catch (Exception e) {
								log.warn("Could not close file", e);
							}
							super.finalize();
						}
						
					};
					if (StringUtils.isNotEmpty(getBase64())) {
						in = new Base64InputStream(in, getBase64().equals(BASE64_ENCODE));
					}
					return in;
				}
				case LIST: {
					String folder = arrangeFolder(determineInputFoldername(input, pvl));
					XmlBuilder dirXml = new XmlBuilder("directory");
					try(Stream<F> stream = FileSystemUtils.getFilteredStream(fileSystem, folder, getWildcard(), getExcludeWildcard())) {
						int count = 0;
						Iterator<F> it = stream.iterator();
						while(it.hasNext()) {
							F file = it.next();
							dirXml.addSubElement(FileSystemUtils.getFileInfo(fileSystem, file));
							count++;
						}
						dirXml.addAttribute("count", count);
					}
					return dirXml.toXML();
				}
				case WRITE: {
					F file=getFile(input, pvl);
					if (fileSystem.exists(file)) {
						FileSystemUtils.prepareDestination((IWritableFileSystem<F>)fileSystem, file, isOverwrite(), getNumberOfBackups(), FileSystemAction.WRITE);
						file=getFile(input, pvl); // reobtain the file, as the object itself may have changed because of the rollover
					}
					try (OutputStream out = ((IWritableFileSystem<F>)fileSystem).createFile(file)) {
						writeContentsToFile(out, input, pvl);
					}
					return FileSystemUtils.getFileInfo(fileSystem, file).toXML();
				}
				case APPEND: {
					F file=getFile(input, pvl);
					if (getRotateDays()>0 && fileSystem.exists(file)) {
						FileSystemUtils.rolloverByDay((IWritableFileSystem<F>)fileSystem, file, getInputFolder(), getRotateDays());
						file=getFile(input, pvl); // reobtain the file, as the object itself may have changed because of the rollover
					}
					if (getRotateSize()>0 && fileSystem.exists(file)) {
						FileSystemUtils.rolloverBySize((IWritableFileSystem<F>)fileSystem, file, getRotateSize(), getNumberOfBackups());
						file=getFile(input, pvl); // reobtain the file, as the object itself may have changed because of the rollover
					}
					try (OutputStream out = ((IWritableFileSystem<F>)fileSystem).appendFile(file)) {
						writeContentsToFile(out, input, pvl);
					}
					return FileSystemUtils.getFileInfo(fileSystem, file).toXML();
				}
				case MKDIR: {
					String folder = determineInputFoldername(input, pvl);
					fileSystem.createFolder(folder);
					return folder;
				}
				case RMDIR: {
					String folder = determineInputFoldername(input, pvl);
					fileSystem.removeFolder(folder, isRemoveNonEmptyFolder());
					return folder;
				}
				case RENAME: {
					F source=getFile(input, pvl);
					String destinationName = determineDestination(pvl);
					F destination;
					if (destinationName.contains("/") || destinationName.contains("\\")) {
						destination = fileSystem.toFile(destinationName);
					} else {
						String sourceName = fileSystem.getCanonicalName(source);
						File sourceAsFile = new File(sourceName);
						String folderPath = sourceAsFile.getParent();
						destination = fileSystem.toFile(folderPath,destinationName);
					}
					F renamed = FileSystemUtils.renameFile((IWritableFileSystem<F>)fileSystem, source, destination, isOverwrite(), getNumberOfBackups());
					return fileSystem.getName(renamed);
				}
				case MOVE: {
					String destinationFolder = determineDestination(pvl);
					return processAction(input, pvl, f -> FileSystemUtils.moveFile(fileSystem, f, destinationFolder, isOverwrite(), getNumberOfBackups(), isCreateFolder()));
				}
				case COPY: {
					String destinationFolder = determineDestination(pvl);
					return processAction(input, pvl, f -> FileSystemUtils.copyFile(fileSystem, f, destinationFolder, isOverwrite(), getNumberOfBackups(), isCreateFolder()));
				}
				case FORWARD: {
					F file=getFile(input, pvl);
					FileSystemUtils.checkSource(fileSystem, file, FileSystemAction.FORWARD);
					String destinationAddress = determineDestination(pvl);
					((IMailFileSystem<F,?>)fileSystem).forwardMail(file, destinationAddress);
					return null;
				}
				default:
					throw new FileSystemException("action ["+getAction()+"] is not supported!");
			}
		} catch (Exception e) {
			throw new FileSystemException("unable to process ["+getAction()+"] action for File [" + determineFilename(input, pvl) + "]", e);
		}
	}

	
	private interface FileAction<F> {
		public F execute(F f) throws FileSystemException;
	}
	/**
	 * Helper method to process delete, move and copy actions.
	 * @throws FileSystemException 
	 * @throws IOException 
	 */
	private String processAction(Message input, ParameterValueList pvl, FileAction<F> action) throws FileSystemException, IOException {
		if(StringUtils.isNotEmpty(getWildcard()) || StringUtils.isNotEmpty(getExcludeWildcard())) { 
			String folder = arrangeFolder(determineInputFoldername(input, pvl));
			XmlBuilder dirXml = new XmlBuilder(getAction()+"FilesList");
			try(Stream<F> stream = FileSystemUtils.getFilteredStream(fileSystem, folder, getWildcard(), getExcludeWildcard())) {
				Iterator<F> it = stream.iterator();
				while(it.hasNext()) {
					F file = it.next();
					XmlBuilder item = FileSystemUtils.getFileInfo(fileSystem, file);
					if(action.execute(file) != null) {
						dirXml.addSubElement(item);
					}
				}
			}
			return dirXml.toXML();
		}
		F file=getFile(input, pvl);
		return fileSystem.getName(action.execute(file));
	}

	private String arrangeFolder(String determinedFolderName) throws FileSystemException {
		if (determinedFolderName!=null && !determinedFolderName.equals(getInputFolder()) && !fileSystem.folderExists(determinedFolderName)) {
			if (isCreateFolder()) {
				fileSystem.createFolder(determinedFolderName);
			} else {
				F file = fileSystem.toFile(determinedFolderName);
				if (file!=null && fileSystem.exists(file)) {
					throw new FileNotFoundException("folder ["+determinedFolderName+"], does not exist as a folder, but is a file");
				}
				throw new FileNotFoundException("folder ["+determinedFolderName+"], does not exist");
			}
		}
		return determinedFolderName;
	}

	private void writeContentsToFile(OutputStream out, Message input, ParameterValueList pvl) throws IOException, FileSystemException {
		Object contents;
		if (pvl!=null && pvl.contains(PARAMETER_CONTENTS1)) {
			 contents=pvl.getParameterValue(PARAMETER_CONTENTS1).getValue();
		} else {
			contents=input;
		}
		if (StringUtils.isNotEmpty(getBase64())) {
			out = new Base64OutputStream(out, getBase64().equals(BASE64_ENCODE));
		}
		if (contents instanceof Message) {
			Misc.streamToStream(((Message)contents).asInputStream(), out);
		} else if (contents instanceof InputStream) {
			Misc.streamToStream((InputStream)contents, out);
		} else if (contents instanceof byte[]) {
			out.write((byte[])contents);
		} else if (contents instanceof String) {
			out.write(((String) contents).getBytes(StringUtils.isNotEmpty(getCharset()) ? getCharset() : StreamUtil.DEFAULT_INPUT_STREAM_ENCODING));
		} else {
			throw new FileSystemException("expected Message, InputStream, ByteArray or String but got [" + contents.getClass().getName() + "] instead");
		}
		if(isWriteLineSeparator()) {
			out.write(eolArray);
		}
	}
	
	
	protected boolean canProvideOutputStream() {
		return (getAction() == FileSystemAction.WRITE || getAction() == FileSystemAction.APPEND) && parameterList.findParameter(PARAMETER_FILENAME)!=null;
	}

	@Override
	public boolean supportsOutputStreamPassThrough() {
		return false;
	}

	@SuppressWarnings("resource")
	@Override
	public MessageOutputStream provideOutputStream(PipeLineSession session, IForwardTarget next) throws StreamingException {
		if (!canProvideOutputStream()) {
			return null;
		}
		ParameterValueList pvl=null;
		
		try {
			if (parameterList != null) {
				pvl = parameterList.getValues(null, session);
			}
		} catch (ParameterException e) {
			throw new StreamingException("caught exception evaluating parameters", e);
		}
		try {
			F file=getFile(null, pvl);
			OutputStream out;
			if (getAction() == FileSystemAction.APPEND) {
				out = ((IWritableFileSystem<F>)fileSystem).appendFile(file);
			} else {
				out = ((IWritableFileSystem<F>)fileSystem).createFile(file);
			}
			MessageOutputStream stream = new MessageOutputStream(owner, out, next);
			stream.setResponse(new Message(FileSystemUtils.getFileInfo(fileSystem, file).toXML()));
			return stream;
		} catch (FileSystemException | IOException e) {
			throw new StreamingException("cannot obtain OutputStream", e);
		}
	}



	protected void addActions(List<FileSystemAction> specificActions) {
		actions.addAll(specificActions);
	}

	@IbisDoc({"1", "If parameter ["+PARAMETER_ACTION+"] is set, then the attribute action value will be overridden with the value of the parameter.", "" })
	public void setAction(FileSystemAction action) {
		this.action = action;
	}

	@IbisDoc({"2", "Folder that is scanned for files when action="+ACTION_LIST+". When not set, the root is scanned", ""})
	public void setInputFolder(String inputFolder) {
		this.inputFolder = inputFolder;
	}

	@IbisDoc({"3", "when set to <code>true</code>, the folder to move or copy to is created if it does not exist", "false"})
	public void setCreateFolder(boolean createFolder) {
		this.createFolder = createFolder;
	}

	@IbisDoc({"4", "when set to <code>true</code>, for actions "+ACTION_MOVE+", "+ACTION_COPY+" or "+ACTION_RENAME+", the destination file is overwritten if it already exists", "false"})
	public void setOverwrite(boolean overwrite) {
		this.overwrite = overwrite;
	}

	@IbisDoc({"5", "filename to operate on. When not set, the parameter "+PARAMETER_FILENAME+" is used. When that is not set either, the input is used", ""})
	public void setFilename(String filename) {
		this.filename = filename;
	}

	@IbisDoc({"6", "destination for "+ACTION_MOVE+", "+ACTION_COPY+" or "+ACTION_RENAME+". If not set, the parameter "+PARAMETER_DESTINATION+" is used. When that is not set either, the input is used", ""})
	public void setDestination(String destination) {
		this.destination = destination;
	}


	@IbisDoc({"7", "for action="+ACTION_APPEND+": when set to a positive number, the file is rotated each day, and this number of files is kept. The inputFolder must point to the directory where the file resides", "0"})
	public void setRotateDays(int rotateDays) {
		this.rotateDays = rotateDays;
	}

	@IbisDoc({"8", "for action="+ACTION_APPEND+": when set to a positive number, the file is rotated when it has reached the specified size, and the number of files specified in numberOfBackups is kept. Size is specified in plain bytes, suffixes like 'K', 'M' or 'G' are not recognized. The inputFolder must point to the directory where the file resides", "0"})
	public void setRotateSize(int rotateSize) {
		this.rotateSize = rotateSize;
	}

	@IbisDoc({"9", "for the actions "+ACTION_WRITE1+" and "+ACTION_APPEND+", with rotateSize>0: the number of backup files that is kept. The inputFolder must point to the directory where the file resides", "0"})
	public void setNumberOfBackups(int numberOfBackups) {
		this.numberOfBackups = numberOfBackups;
	}

	@IbisDoc({"10", "Can be set to 'encode' or 'decode' for actions "+ACTION_READ1+", "+ACTION_WRITE1+" and "+ACTION_APPEND+". When set the stream is base64 encoded or decoded, respectively", ""})
	@Deprecated
	public void setBase64(String base64) {
		this.base64 = base64;
	}

	@Deprecated
	@ConfigurationWarning("attribute 'wildCard' has been renamed to 'wildcard'")
	public void setWildCard(String wildcard) {
		setWildcard(wildcard);
	}
	@IbisDoc({"11", "Filter of files to look for in inputFolder e.g. '*.inp'. Works with actions "+ACTION_MOVE+", "+ACTION_COPY+", "+ACTION_DELETE+" and "+ACTION_LIST, ""})
	public void setWildcard(String wildcard) {
		this.wildcard = wildcard;
	}

	@Deprecated
	@ConfigurationWarning("attribute 'excludeWildCard' has been renamed to 'excludeWildcard'")
	public void setExcludeWildCard(String excludeWildcard) {
		setExcludeWildcard(excludeWildcard);
	}
	@IbisDoc({"12", "Filter of files to be excluded when looking in inputFolder. Works with actions "+ACTION_MOVE+", "+ACTION_COPY+", "+ACTION_DELETE+" and "+ACTION_LIST, ""})
	public void setExcludeWildcard(String excludeWildcard) {
		this.excludeWildcard = excludeWildcard;
	}

	@IbisDoc({"13", "If set to true then the folder and the content of the non empty folder will be deleted."})
	public void setRemoveNonEmptyFolder(boolean removeNonEmptyFolder) {
		this.removeNonEmptyFolder = removeNonEmptyFolder;
	}

	@IbisDoc({"14", "If set to true then the system specific line separator will be appended to the file after executing the action. Works with actions "+ACTION_WRITE1+" and "+ACTION_APPEND, "false"})
	public void setWriteLineSeparator(boolean writeLineSeparator) {
		this.writeLineSeparator = writeLineSeparator;
	}

	@IbisDoc({"15", "Charset to be used for "+ACTION_READ1+" and "+ACTION_WRITE1+" action"})
	public void setCharset(String charset) {
		this.charset = charset;
	}
}
